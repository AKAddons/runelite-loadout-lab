package com.loadoutlab.engine;

import com.loadoutlab.data.MonsterOffence;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.StatBlock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

		StyleThreat(String style, double dps, int maxHit, boolean modeled, boolean blocked, double share)
		{
			this.style = style;
			this.dps = dps;
			this.maxHit = maxHit;
			this.modeled = modeled;
			this.blocked = blocked;
			this.share = share;
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

	public static Result calculate(MonsterStats monster, Loadout loadout,
		int defenceLevel, int magicLevel)
	{
		BossIncomingOverrides.BossOverride override = BossIncomingOverrides.overridesFor(monster);
		if (override != null)
		{
			return calculateWithOverride(monster, override, loadout, defenceLevel, magicLevel);
		}

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
		List<StyleThreat> threats = new ArrayList<>();
		int bestPrayable = -1;
		double bestContribution = -1;
		for (BossIncomingOverrides.Attack attack : override.getAttacks())
		{
			String style = attack.getStyle();
			// Typeless attacks have no defence roll: they always hit.
			double accuracy = "typeless".equals(style) ? 1.0
				: accuracyFor(style, off, def, defenceLevel, magicLevel);
			int speed = attack.getSpeedTicks() > 0 ? attack.getSpeedTicks() : off.getSpeedTicks();
			double dps = accuracy * (attack.getMaxHit() / 2.0) / (speed * 0.6);
			if (attack.isPrayable() && dps * attack.getShare() > bestContribution)
			{
				bestContribution = dps * attack.getShare();
				bestPrayable = threats.size();
			}
			threats.add(new StyleThreat(displayStyle(style), dps, attack.getMaxHit(),
				true, false, attack.getShare()));
		}

		String prayer = null;
		if (bestPrayable >= 0)
		{
			StyleThreat blocked = threats.get(bestPrayable);
			threats.set(bestPrayable, new StyleThreat(
				blocked.style, blocked.dps, blocked.maxHit, true, true, blocked.share));
			prayer = protectPrayerFor(override.getAttacks().get(bestPrayable).getStyle());
		}

		double total = 0;
		double unprayed = 0;
		for (StyleThreat threat : threats)
		{
			unprayed += threat.dps * threat.share;
			if (!threat.blocked)
			{
				total += threat.dps * threat.share;
			}
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
