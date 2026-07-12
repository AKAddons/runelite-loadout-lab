package com.loadoutlab.engine;

import com.loadoutlab.data.MonsterOffence;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.StatBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * The other direction: how hard the monster hits YOU in a given set.
 * Standard NPC combat math (effective level +9, roll = eff * (bonus+64),
 * max hit = (effStr * (bonus+64) + 320) / 640) against the loadout's
 * defensive bonuses and the player's real Defence/Magic levels.
 *
 * v1 model and its stated assumptions:
 * - The player runs the protection prayer against the nastiest modeled
 *   style, which blocks that style completely (true for standard NPCs;
 *   partial-block bosses need per-boss overrides later).
 * - Multi-style monsters rotate uniformly across their listed styles, so
 *   each style owns an equal share of the attack cadence and the blocked
 *   style's share is dead time. Real rotations (Zulrah phases, Vorkath
 *   specials) are a curated-override layer we build up over time.
 * - No defensive stance, boost, or defence-raising prayer on the player -
 *   protection is assumed to occupy the prayer slot.
 * - Styles the sheet cannot express (Typeless, Dragonfire, Curse...) are
 *   surfaced as unmodeled rather than silently dropped.
 *
 * D-2: when BossIncomingOverrides has a curated entry for the monster, its
 * attack list replaces the uniform model - scripted max hits, real rotation
 * shares, prayer-pierce flags, and typeless chip damage. Accuracy is still
 * rolled from the stat sheet's offensive stats vs the loadout; an override
 * maxHit only replaces the damage term (typeless attacks always hit).
 */
public final class IncomingDpsCalculator
{
	/** One attack style's threat: its dps if it attacked every cycle. */
	public static final class StyleThreat
	{
		public final String style;
		public final double dps;
		public final int maxHit;
		public final boolean modeled;
		public final boolean blocked;
		/** This attack's slice of the rotation (1/n in the uniform model). */
		public final double share;
		/** When blocked: the fraction that still gets THROUGH the prayer
		 * (0 = fully blocked, 0.5 = half pierces). 1 when not blocked. */
		public final double prayerFactor;

		StyleThreat(String style, double dps, int maxHit, boolean modeled, boolean blocked, double share)
		{
			this(style, dps, maxHit, modeled, blocked, share, blocked ? 0.0 : 1.0);
		}

		StyleThreat(String style, double dps, int maxHit, boolean modeled, boolean blocked,
			double share, double prayerFactor)
		{
			this.style = style;
			this.dps = dps;
			this.maxHit = maxHit;
			this.modeled = modeled;
			this.blocked = blocked;
			this.share = share;
			this.prayerFactor = prayerFactor;
		}
	}

	public static final class Result
	{
		/** Expected incoming dps with the protection prayer up. */
		public final double totalDps;
		/** Expected incoming dps with NO protection prayer running. */
		public final double unprayedDps;
		/** The prayer to run, e.g. "Protect from Missiles", or null. */
		public final String protectPrayer;
		public final List<StyleThreat> threats;
		/** False when any listed style is beyond the stat-sheet model. */
		public final boolean fullyModeled;
		/** The curated override's source note, or null in the v1 model. */
		public final String overrideNote;

		Result(double totalDps, double unprayedDps, String protectPrayer,
			List<StyleThreat> threats, boolean fullyModeled, String overrideNote)
		{
			this.totalDps = totalDps;
			this.unprayedDps = unprayedDps;
			this.protectPrayer = protectPrayer;
			this.threats = Collections.unmodifiableList(threats);
			this.fullyModeled = fullyModeled;
			this.overrideNote = overrideNote;
		}
	}

	private IncomingDpsCalculator()
	{
	}

	// Style kinds for the precomputed accuracy path (Prepared).
	private static final int KIND_STAB = 0;
	private static final int KIND_SLASH = 1;
	private static final int KIND_CRUSH = 2;
	private static final int KIND_MELEE_GENERIC = 3;
	private static final int KIND_RANGED = 4;
	private static final int KIND_MAGIC = 5;
	private static final int KIND_TYPELESS = 6;
	private static final int KIND_UNMODELED = -1;

	/**
	 * Everything about the incoming model that does NOT depend on the worn
	 * set, hoisted once per optimize: override lookup, style parsing, npc
	 * max hits and attack rolls, rotation shares, prayer names. The
	 * defense-weighted beam then scores each candidate loadout with
	 * totalDps() - the same number calculate() reports (asserted in
	 * IncomingDpsCalculatorTest), without the per-call threat/Result
	 * allocations. calculate() stays the authoritative display path.
	 */
	public static final class Prepared
	{
		private final int defenceLevel;
		/** Player magic defence level term: 70% Magic + 30% (Defence+9). */
		private final int magicEffective;
		/** Per style/attack: KIND_*, npc attack roll, and (v1) max hit. */
		private final int[] kinds;
		private final int[] attackRolls;
		private final int[] maxHits;
		// v1 sheet model (override == null): uniform rotation.
		private final double share;
		private final int speedTicks;
		// Curated override model: per-attack constants.
		private final boolean overridden;
		private final double[] shares;
		private final double[] prayerFactors;
		private final int[] speeds;
		/** Per attack: index into the distinct prayer list, or -1. */
		private final int[] prayerIndex;
		private final int prayerCount;

		private Prepared(MonsterStats monster, BossIncomingOverrides.BossOverride override,
			int defenceLevel, int magicLevel)
		{
			this.defenceLevel = defenceLevel;
			this.magicEffective = (int) (magicLevel * 0.7) + (int) ((defenceLevel + 9) * 0.3);
			MonsterOffence off = monster.getOffence();
			this.overridden = override != null;
			if (override == null)
			{
				List<String> styles = off.getStyles();
				this.kinds = new int[styles.size()];
				this.attackRolls = new int[styles.size()];
				this.maxHits = new int[styles.size()];
				this.share = styles.isEmpty() ? 0 : 1.0 / styles.size();
				this.speedTicks = off.getSpeedTicks();
				for (int i = 0; i < styles.size(); i++)
				{
					int kind = kindOf(styles.get(i));
					kinds[i] = kind;
					attackRolls[i] = attackRollFor(kind, off);
					maxHits[i] = maxHitFor(kind, off);
				}
				this.shares = null;
				this.prayerFactors = null;
				this.speeds = null;
				this.prayerIndex = null;
				this.prayerCount = 0;
			}
			else
			{
				List<BossIncomingOverrides.Attack> attacks = override.getAttacks();
				this.kinds = new int[attacks.size()];
				this.attackRolls = new int[attacks.size()];
				this.maxHits = new int[attacks.size()];
				this.shares = new double[attacks.size()];
				this.prayerFactors = new double[attacks.size()];
				this.speeds = new int[attacks.size()];
				this.prayerIndex = new int[attacks.size()];
				this.share = 0;
				this.speedTicks = off.getSpeedTicks();
				// Distinct prayers in first-occurrence order - mirrors the
				// insertion-ordered savedByPrayer map in the full path.
				List<String> prayers = new ArrayList<>();
				for (int i = 0; i < attacks.size(); i++)
				{
					BossIncomingOverrides.Attack attack = attacks.get(i);
					String style = attack.getStyle();
					int kind = "typeless".equals(style) ? KIND_TYPELESS : kindOf(style);
					kinds[i] = kind;
					attackRolls[i] = kind == KIND_TYPELESS ? 0 : attackRollFor(kind, off);
					maxHits[i] = attack.getMaxHit();
					shares[i] = attack.getShare();
					prayerFactors[i] = attack.getPrayerFactor();
					speeds[i] = attack.getSpeedTicks() > 0 ? attack.getSpeedTicks() : off.getSpeedTicks();
					String prayer = protectPrayerFor(style);
					int index = prayers.indexOf(prayer);
					if (index < 0)
					{
						index = prayers.size();
						prayers.add(prayer);
					}
					prayerIndex[i] = index;
				}
				this.prayerCount = prayers.size();
			}
		}

		/**
		 * The prayed incoming dps of calculate() for this loadout - same
		 * math, same order, none of the display allocations.
		 */
		public double totalDps(Loadout loadout)
		{
			StatBlock def = loadout.getDefensive();
			if (!overridden)
			{
				// v1 uniform model: pray the nastiest modeled style away.
				int bestBlockable = -1;
				double bestBlockableDps = -1;
				double[] dps = new double[kinds.length];
				for (int i = 0; i < kinds.length; i++)
				{
					if (kinds[i] == KIND_UNMODELED)
					{
						continue;
					}
					dps[i] = accuracyOf(kinds[i], attackRolls[i], def)
						* (maxHits[i] / 2.0) / (speedTicks * 0.6);
					if (dps[i] > bestBlockableDps)
					{
						bestBlockableDps = dps[i];
						bestBlockable = i;
					}
				}
				double total = 0;
				for (int i = 0; i < kinds.length; i++)
				{
					if (kinds[i] != KIND_UNMODELED && i != bestBlockable)
					{
						total += dps[i] * share;
					}
				}
				return total;
			}
			// Override model: block the prayer with the largest saving.
			double[] dpsPer = new double[kinds.length];
			double[] saved = new double[prayerCount];
			for (int i = 0; i < kinds.length; i++)
			{
				double accuracy = kinds[i] == KIND_TYPELESS ? 1.0
					: accuracyOf(kinds[i], attackRolls[i], def);
				dpsPer[i] = accuracy * (maxHits[i] / 2.0) / (speeds[i] * 0.6);
				double saving = dpsPer[i] * shares[i] * (1 - prayerFactors[i]);
				if (saving > 0)
				{
					saved[prayerIndex[i]] += saving;
				}
			}
			int bestPrayer = -1;
			double bestSaved = 0;
			for (int p = 0; p < prayerCount; p++)
			{
				if (saved[p] > bestSaved)
				{
					bestSaved = saved[p];
					bestPrayer = p;
				}
			}
			double total = 0;
			for (int i = 0; i < kinds.length; i++)
			{
				boolean prayedClass = bestPrayer >= 0
					&& prayerIndex[i] == bestPrayer && prayerFactors[i] < 1;
				total += dpsPer[i] * shares[i] * (prayedClass ? prayerFactors[i] : 1);
			}
			return total;
		}

		private double accuracyOf(int kind, int attackRoll, StatBlock def)
		{
			long defenceRoll;
			switch (kind)
			{
				case KIND_STAB:
					defenceRoll = (long) (defenceLevel + 9) * (def.getStab() + 64);
					break;
				case KIND_SLASH:
					defenceRoll = (long) (defenceLevel + 9) * (def.getSlash() + 64);
					break;
				case KIND_CRUSH:
					defenceRoll = (long) (defenceLevel + 9) * (def.getCrush() + 64);
					break;
				case KIND_MELEE_GENERIC:
					defenceRoll = (long) (defenceLevel + 9)
						* (Math.min(def.getStab(), Math.min(def.getSlash(), def.getCrush())) + 64);
					break;
				case KIND_RANGED:
					defenceRoll = (long) (defenceLevel + 9) * (def.getRanged() + 64);
					break;
				default:
					defenceRoll = (long) magicEffective * (def.getMagic() + 64);
			}
			return accuracy(attackRoll, defenceRoll);
		}
	}

	public static Prepared prepare(MonsterStats monster, int defenceLevel, int magicLevel)
	{
		return new Prepared(monster,
			BossIncomingOverrides.overridesFor(monster), defenceLevel, magicLevel);
	}

	/** The KIND_* for a sheet/override style string (never "typeless"). */
	private static int kindOf(String rawStyle)
	{
		String style = rawStyle.toLowerCase(Locale.ROOT);
		switch (style)
		{
			case "stab": return KIND_STAB;
			case "slash": return KIND_SLASH;
			case "crush": return KIND_CRUSH;
			case "melee": return KIND_MELEE_GENERIC;
			case "ranged":
			case "range": return KIND_RANGED;
			case "magic":
			case "magical ranged":
			case "magical melee": return KIND_MAGIC;
			default: return KIND_UNMODELED;
		}
	}

	private static int attackRollFor(int kind, MonsterOffence off)
	{
		switch (kind)
		{
			case KIND_STAB:
			case KIND_SLASH:
			case KIND_CRUSH:
			case KIND_MELEE_GENERIC:
				return npcRoll(off.getAttackLevel(), off.getAttackBonus());
			case KIND_RANGED:
				return npcRoll(off.getRangedLevel(), off.getRangedBonus());
			case KIND_MAGIC:
				return npcRoll(off.getMagicLevel(), off.getMagicBonus());
			default:
				return 0;
		}
	}

	private static int maxHitFor(int kind, MonsterOffence off)
	{
		switch (kind)
		{
			case KIND_STAB:
			case KIND_SLASH:
			case KIND_CRUSH:
			case KIND_MELEE_GENERIC:
				return npcMaxHit(off.getStrengthLevel(), off.getStrengthBonus());
			case KIND_RANGED:
				return npcMaxHit(off.getRangedLevel(), off.getRangedStrengthBonus());
			case KIND_MAGIC:
				return npcMaxHit(off.getMagicLevel(), off.getMagicStrengthBonus());
			default:
				return 0;
		}
	}

	public static Result calculate(MonsterStats monster, Loadout loadout,
		int defenceLevel, int magicLevel)
	{
		// Bracelet of ethereum (charged): revenants deal NO damage while it
		// is worn - an absolute rule that outranks every other model.
		if (monster.getName().toLowerCase(java.util.Locale.ROOT).startsWith("revenant")
			&& wearsChargedEthereum(loadout))
		{
			return new Result(0, 0, null, Collections.emptyList(), true,
				"Bracelet of ethereum blocks all revenant damage");
		}
		BossIncomingOverrides.BossOverride override = BossIncomingOverrides.overridesFor(monster);
		if (override != null)
		{
			return calculateWithOverride(monster, override, loadout, defenceLevel, magicLevel);
		}
		return calculateFromStatSheet(monster, loadout, defenceLevel, magicLevel);
	}

	private static boolean wearsChargedEthereum(Loadout loadout)
	{
		com.loadoutlab.data.GearItem hands = loadout.get(com.loadoutlab.data.GearSlot.HANDS);
		return hands != null
			&& "bracelet of ethereum".equals(hands.getNameLower())
			&& "charged".equals(hands.getVersionLower());
	}

	private static Result calculateFromStatSheet(MonsterStats monster, Loadout loadout,
		int defenceLevel, int magicLevel)
	{

		MonsterOffence off = monster.getOffence();
		List<String> styles = off.getStyles();
		if (styles.isEmpty())
		{
			return new Result(0, 0, null, Collections.emptyList(), false, null);
		}

		StatBlock def = loadout.getDefensive();
		List<StyleThreat> threats = new ArrayList<>();
		boolean fullyModeled = true;
		int bestBlockable = -1;
		double bestBlockableDps = -1;
		double share = 1.0 / styles.size();
		for (String style : styles)
		{
			StyleThreat threat = threatFor(style, off, def, defenceLevel, magicLevel, share);
			if (!threat.modeled)
			{
				fullyModeled = false;
			}
			else if (threat.dps > bestBlockableDps)
			{
				bestBlockableDps = threat.dps;
				bestBlockable = threats.size();
			}
			threats.add(threat);
		}

		String prayer = null;
		if (bestBlockable >= 0)
		{
			StyleThreat blocked = threats.get(bestBlockable);
			threats.set(bestBlockable, new StyleThreat(
				blocked.style, blocked.dps, blocked.maxHit, true, true, blocked.share));
			prayer = protectPrayerFor(blocked.style);
		}

		// Uniform rotation: each listed style owns 1/n of the cadence.
		double total = 0;
		double unprayed = 0;
		for (StyleThreat threat : threats)
		{
			if (!threat.modeled)
			{
				continue;
			}
			unprayed += threat.dps * threat.share;
			if (!threat.blocked)
			{
				total += threat.dps * threat.share;
			}
		}
		return new Result(total, unprayed, prayer, threats, fullyModeled, null);
	}

	/**
	 * D-2 path: the curated attack list replaces the uniform model. Prayer
	 * blocks the prayable attack with the largest CONTRIBUTION (dps x share);
	 * prayable=false attacks always land - partial-block and typeless chip
	 * damage is encoded in the curated maxHit.
	 */
	private static Result calculateWithOverride(MonsterStats monster,
		BossIncomingOverrides.BossOverride override, Loadout loadout,
		int defenceLevel, int magicLevel)
	{
		MonsterOffence off = monster.getOffence();
		StatBlock def = loadout.getDefensive();
		List<BossIncomingOverrides.Attack> attacks = override.getAttacks();
		double[] dpsPer = new double[attacks.size()];
		// Which protection prayer SAVES the most? An attack's saving is
		// dps x share x (1 - prayerFactor) - partial pierces (Corp magic,
		// Callisto melee) save less than their raw size suggests.
		// Insertion-ordered so tied savings resolve deterministically
		// (attack order in the curated data wins).
		Map<String, Double> savedByPrayer = new java.util.LinkedHashMap<>();
		for (int i = 0; i < attacks.size(); i++)
		{
			BossIncomingOverrides.Attack attack = attacks.get(i);
			String style = attack.getStyle();
			// Typeless attacks have no defence roll: they always hit.
			double accuracy = "typeless".equals(style) ? 1.0
				: accuracyFor(style, off, def, defenceLevel, magicLevel);
			int speed = attack.getSpeedTicks() > 0 ? attack.getSpeedTicks() : off.getSpeedTicks();
			dpsPer[i] = accuracy * (attack.getMaxHit() / 2.0) / (speed * 0.6);
			double saving = dpsPer[i] * attack.getShare() * (1 - attack.getPrayerFactor());
			if (saving > 0)
			{
				savedByPrayer.merge(protectPrayerFor(style), saving, Double::sum);
			}
		}
		String prayer = null;
		double bestSaved = 0;
		for (Map.Entry<String, Double> entry : savedByPrayer.entrySet())
		{
			if (entry.getValue() > bestSaved)
			{
				bestSaved = entry.getValue();
				prayer = entry.getKey();
			}
		}

		List<StyleThreat> threats = new ArrayList<>();
		double total = 0;
		double unprayed = 0;
		for (int i = 0; i < attacks.size(); i++)
		{
			BossIncomingOverrides.Attack attack = attacks.get(i);
			boolean prayedClass = prayer != null
				&& prayer.equals(protectPrayerFor(attack.getStyle()))
				&& attack.getPrayerFactor() < 1;
			double through = prayedClass ? attack.getPrayerFactor() : 1;
			unprayed += dpsPer[i] * attack.getShare();
			total += dpsPer[i] * attack.getShare() * through;
			threats.add(new StyleThreat(displayStyle(attack.getStyle()), dpsPer[i],
				attack.getMaxHit(), true, prayedClass, attack.getShare(),
				prayedClass ? attack.getPrayerFactor() : 1));
		}
		return new Result(total, unprayed, prayer, threats, true, override.getNote());
	}

	private static String displayStyle(String style)
	{
		return style.isEmpty() ? style
			: Character.toUpperCase(style.charAt(0)) + style.substring(1);
	}

	private static StyleThreat threatFor(String rawStyle, MonsterOffence off,
		StatBlock def, int defenceLevel, int magicLevel, double share)
	{
		String style = rawStyle.toLowerCase(Locale.ROOT);
		int maxHit;
		if (style.equals("stab") || style.equals("slash") || style.equals("crush") || style.equals("melee"))
		{
			maxHit = npcMaxHit(off.getStrengthLevel(), off.getStrengthBonus());
		}
		else if (style.equals("ranged") || style.equals("range"))
		{
			maxHit = npcMaxHit(off.getRangedLevel(), off.getRangedStrengthBonus());
		}
		else if (style.equals("magic") || style.equals("magical ranged") || style.equals("magical melee"))
		{
			maxHit = npcMaxHit(off.getMagicLevel(), off.getMagicStrengthBonus());
		}
		else
		{
			return new StyleThreat(rawStyle, 0, 0, false, false, share);
		}

		double accuracy = accuracyFor(style, off, def, defenceLevel, magicLevel);
		double dps = accuracy * (maxHit / 2.0) / (off.getSpeedTicks() * 0.6);
		return new StyleThreat(rawStyle, dps, maxHit, true, false, share);
	}

	/** The monster's chance to hit with this style vs the loadout - shared
	 * by the v1 sheet model and the curated overrides (which keep the sheet
	 * accuracy and only replace the damage term). */
	private static double accuracyFor(String style, MonsterOffence off,
		StatBlock def, int defenceLevel, int magicLevel)
	{
		int attackRoll;
		long defenceRoll;
		if (style.equals("stab") || style.equals("slash") || style.equals("crush") || style.equals("melee"))
		{
			attackRoll = npcRoll(off.getAttackLevel(), off.getAttackBonus());
			defenceRoll = (long) (defenceLevel + 9) * (meleeDefBonus(style, def) + 64);
		}
		else if (style.equals("ranged") || style.equals("range"))
		{
			attackRoll = npcRoll(off.getRangedLevel(), off.getRangedBonus());
			defenceRoll = (long) (defenceLevel + 9) * (def.getRanged() + 64);
		}
		else
		{
			attackRoll = npcRoll(off.getMagicLevel(), off.getMagicBonus());
			// Player magic defence: 70% Magic + 30% Defence for the level term.
			int effective = (int) (magicLevel * 0.7) + (int) ((defenceLevel + 9) * 0.3);
			defenceRoll = (long) effective * (def.getMagic() + 64);
		}
		return accuracy(attackRoll, defenceRoll);
	}

	private static int meleeDefBonus(String style, StatBlock def)
	{
		switch (style)
		{
			case "stab": return def.getStab();
			case "slash": return def.getSlash();
			case "crush": return def.getCrush();
			default:
				// Generic "Melee": assume the boss hits your weakest side.
				return Math.min(def.getStab(), Math.min(def.getSlash(), def.getCrush()));
		}
	}

	private static String protectPrayerFor(String rawStyle)
	{
		String style = rawStyle.toLowerCase(Locale.ROOT);
		if (style.equals("ranged") || style.equals("range"))
		{
			return "Protect from Missiles";
		}
		if (style.startsWith("magic"))
		{
			return "Protect from Magic";
		}
		return "Protect from Melee";
	}

	private static int npcRoll(int level, int bonus)
	{
		return (level + 9) * (bonus + 64);
	}

	private static int npcMaxHit(int level, int bonus)
	{
		return (int) (((long) (level + 9) * (bonus + 64) + 320) / 640);
	}

	private static double accuracy(long attackRoll, long defenceRoll)
	{
		if (attackRoll > defenceRoll)
		{
			return 1.0 - (defenceRoll + 2.0) / (2.0 * (attackRoll + 1.0));
		}
		return attackRoll / (2.0 * (defenceRoll + 1.0));
	}
}
