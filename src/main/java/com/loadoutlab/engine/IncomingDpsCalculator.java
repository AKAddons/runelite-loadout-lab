package com.loadoutlab.engine;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import com.loadoutlab.data.*;
import java.util.*;
import java.util.List;

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
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
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
		/** How much of the monster the number covers: curated, full,
		 * partial (some attacks not counted) or none (unknown). */
		public final String coverage;
		/** The attacks the number leaves out, for the card's tooltip. */
		public final List<String> unmodeled;

		Result(double totalDps, double unprayedDps, String protectPrayer,
			List<StyleThreat> threats, String overrideNote, String coverage, List<String> unmodeled)
		{
			this.totalDps = totalDps;
			this.unprayedDps = unprayedDps;
			this.protectPrayer = protectPrayer;
			this.threats = Collections.unmodifiableList(threats);
			this.fullyModeled = unmodeled.isEmpty() && !NONE.equals(coverage);
			this.overrideNote = overrideNote;
			this.coverage = coverage;
			this.unmodeled = Collections.unmodifiableList(unmodeled);
		}
	}

	public static final String CURATED = "curated";
	public static final String FULL = "full";
	public static final String PARTIAL = "partial";
	public static final String NONE = "none";

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
	/** "None" / "N/A": the monster does not attack this way at all. */
	private static final int KIND_NONE = -2;

	/**
	 * Everything about the incoming model that does NOT depend on the worn
	 * set, hoisted once per optimize: override lookup, style parsing, npc
	 * max hits and attack rolls, rotation shares, prayer names. The
	 * defense-weighted beam then scores each candidate loadout with
	 * totalDps() - the same number calculate() reports (asserted in
	 * IncomingDpsCalculatorTest), without the per-call threat/Result
	 * allocations; calculate() itself is result() on the same machinery.
	 */
	public static final class Prepared
	{
		private final int defenceLevel;
		/** Player magic defence level term: floor(70% Magic) + floor(30% Defence) + 8. */
		private final int magicEffective;
		/** Per style/attack: KIND_*, npc attack roll, (v1) max hit, and
		 * the speed its rate uses (the sheet's for every sheet style). */
		private final int[] kinds;
		private final int[] attackRolls;
		private final int[] maxHits;
		private final int[] speeds;
		/** Priced: it has a rate the card can show. Live: counted in the
		 * totals. A sheet style is both when modeled at a known speed; an
		 * override attack is priced at a known speed and live when the
		 * standard play does not dodge it. */
		private final boolean[] priced;
		private final boolean[] live;
		private final boolean[] prayable;
		/** The card's name per style/attack, and the prayer answering it. */
		private final String[] labels;
		private final String[] prayerOf;
		// v1 sheet model (override == null): modeled styles share the cadence.
		private final double share;
		// Curated override model: per-attack constants.
		private final boolean overridden;
		private final String note;
		private final double[] shares;
		private final double[] prayerFactors;
		private final boolean[] avoidable;
		/** Per attack: index into the distinct prayer list (first-occurrence
		 * order), and that list. */
		private final int[] prayerIndex;
		private final String[] prayerNames;
		/** Revenants deal NO damage through a charged ethereum bracelet -
		 * the beam's defense scoring must see that, or Tanky/Balanced can
		 * never choose the bracelet (field report: not recommended). */
		private final boolean revenant;

		Prepared(MonsterStats monster, BossIncomingOverrides.BossOverride override,
			int defenceLevel, int magicLevel)
		{
			this.revenant = monster.isRevenantMonster();
			this.defenceLevel = defenceLevel;
			this.magicEffective = magicEffectiveLevel(defenceLevel, magicLevel);
			MonsterOffence off = monster.getOffence();
			this.overridden = override != null;
			int speedTicks = off.getSpeedTicks();
			List<String> styles = off.getStyles();
			List<BossIncomingOverrides.Attack> attacks = overridden ? override.getAttacks() : null;
			int n = overridden ? attacks.size() : styles.size();
			this.kinds = new int[n];
			this.attackRolls = new int[n];
			this.maxHits = new int[n];
			this.speeds = new int[n];
			this.live = new boolean[n];
			this.labels = new String[n];
			this.prayerOf = new String[n];
			if (!overridden)
			{
				this.priced = live;
				this.prayable = new boolean[n];
				// Only modeled attacks share the cadence: an unmodeled style is
				// named on the card, never a 0-damage slice that dilutes the rest.
				int modeled = modeledCount(styles, off);
				this.share = modeled == 0 ? 0 : 1.0 / modeled;
				boolean single = singleFamily(styles);
				for (int i = 0; i < n; i++)
				{
					int kind = kindOf(styles.get(i));
					kinds[i] = kind;
					// No listed speed (0 / -1) is no rate at all - never one tick.
					live[i] = kind >= 0 && speedTicks > 0;
					prayable[i] = prayable(styles.get(i));
					attackRolls[i] = attackRollFor(kind, off);
					maxHits[i] = maxHitFor(kind, off, single);
					speeds[i] = speedTicks;
					labels[i] = shown(styles.get(i));
					prayerOf[i] = protectPrayerFor(styles.get(i));
				}
				this.note = null;
				this.shares = null;
				this.prayerFactors = null;
				this.avoidable = null;
				this.prayerIndex = null;
				this.prayerNames = null;
			}
			else
			{
				this.shares = new double[n];
				this.prayerFactors = new double[n];
				this.prayerIndex = new int[n];
				this.priced = new boolean[n];
				this.avoidable = new boolean[n];
				this.prayable = null;
				this.share = 0;
				this.note = override.getNote();
				// Distinct prayers in first-occurrence order.
				List<String> prayers = new ArrayList<>();
				for (int i = 0; i < n; i++)
				{
					BossIncomingOverrides.Attack attack = attacks.get(i);
					String style = attack.getStyle();
					int kind = "typeless".equals(style) ? KIND_TYPELESS : kindOf(style);
					kinds[i] = kind;
					attackRolls[i] = kind == KIND_TYPELESS ? 0 : attackRollFor(kind, off);
					maxHits[i] = attack.getMaxHit();
					shares[i] = attack.getShare();
					prayerFactors[i] = attack.getPrayerFactor();
					speeds[i] = attack.getSpeedTicks() > 0 ? attack.getSpeedTicks() : speedTicks;
					// Avoidable attacks are dodged by the standard play, and an
					// unknown speed is no rate at all - the totals count neither.
					avoidable[i] = attack.isAvoidable();
					priced[i] = speeds[i] > 0;
					live[i] = !avoidable[i] && priced[i];
					labels[i] = displayStyle(style);
					String prayer = protectPrayerFor(style);
					prayerOf[i] = prayer;
					int index = prayers.indexOf(prayer);
					if (index < 0)
					{
						index = prayers.size();
						prayers.add(prayer);
					}
					prayerIndex[i] = index;
				}
				this.prayerNames = prayers.toArray(new String[0]);
			}
		}

		/** Each `which` attack's dps if it attacked every cycle - the ONE
		 * rate path, for the beam's totals and the card's rows alike.
		 * Typeless attacks have no defence roll: they always hit. */
		private void fillDps(Loadout loadout, double[] dps, boolean[] which)
		{
			StatBlock def = loadout.getDefensive();
			for (int i = 0; i < kinds.length; i++)
			{
				if (!which[i])
				{
					continue;
				}
				double accuracy = kinds[i] == KIND_TYPELESS ? 1.0
					: accuracyOfKind(kinds[i], attackRolls[i], def, defenceLevel, magicEffective);
				dps[i] = accuracy * (maxHits[i] / 2.0) / (speeds[i] * 0.6);
			}
		}

		/** v1 uniform model: the nastiest prayable modeled style, or -1 -
		 * the one the protection prayer blocks. */
		private int bestBlockable(double[] dps)
		{
			int best = -1;
			double bestDps = -1;
			for (int i = 0; i < kinds.length; i++)
			{
				if (live[i] && prayable[i] && dps[i] > bestDps)
				{
					bestDps = dps[i];
					best = i;
				}
			}
			return best;
		}

		/**
		 * Override model: the prayer that SAVES the most (its index in the
		 * distinct list, -1 when none saves). An attack's saving is dps x
		 * share x (1 - prayerFactor) - partial pierces (Corp magic, Callisto
		 * melee) save less than their raw size suggests; prayable=false
		 * attacks always land, and typeless chip damage is encoded in the
		 * curated maxHit. Ties go to the prayer whose first positive saving
		 * came first in the curated attack order (deterministic).
		 */
		private int bestPrayer(double[] dps)
		{
			double[] saved = new double[prayerNames.length];
			int[] order = new int[saved.length];
			int count = 0;
			for (int i = 0; i < kinds.length; i++)
			{
				// An avoidable attack is dodged by the standard play - no saving.
				double saving = live[i] ? dps[i] * shares[i] * (1 - prayerFactors[i]) : 0;
				if (saving > 0)
				{
					int p = prayerIndex[i];
					if (saved[p] == 0)
					{
						order[count++] = p;
					}
					saved[p] += saving;
				}
			}
			int best = -1;
			double bestSaved = 0;
			for (int k = 0; k < count; k++)
			{
				if (saved[order[k]] > bestSaved)
				{
					bestSaved = saved[order[k]];
					best = order[k];
				}
			}
			return best;
		}

		/** The fraction of attack i that gets through the picked prayer. */
		private double through(int i, int prayer)
		{
			return prayer >= 0 && prayerIndex[i] == prayer && prayerFactors[i] < 1 ? prayerFactors[i] : 1;
		}

		/**
		 * The prayed incoming dps of calculate() for this loadout - same
		 * math, same order, none of the display allocations.
		 */
		public double totalDps(Loadout loadout)
		{
			if (revenant && wearsChargedEthereum(loadout))
			{
				return 0;
			}
			double[] dps = new double[kinds.length];
			fillDps(loadout, dps, live);
			double total = 0;
			if (!overridden)
			{
				int blocked = bestBlockable(dps);
				for (int i = 0; i < kinds.length; i++)
				{
					if (live[i] && i != blocked)
					{
						total += dps[i] * share;
					}
				}
				return total;
			}
			int prayer = bestPrayer(dps);
			for (int i = 0; i < kinds.length; i++)
			{
				if (live[i])
				{
					total += dps[i] * shares[i] * through(i, prayer);
				}
			}
			return total;
		}

		/** The display answer for this loadout: every threat as the card
		 * lists it, the prayer, both totals, the coverage and the names the
		 * number leaves out. The same rates totalDps() scores. */
		private Result result(Loadout loadout)
		{
			if (!overridden && kinds.length == 0)
			{
				return new Result(0, 0, null, List.of(), null, NONE, List.of());
			}
			double[] dps = new double[kinds.length];
			fillDps(loadout, dps, priced);
			List<StyleThreat> threats = new ArrayList<>();
			List<String> unmodeled = new ArrayList<>();
			String prayer = null;
			double total = 0;
			double unprayed = 0;
			if (!overridden)
			{
				// Uniform rotation: each modeled style owns 1/m of the cadence;
				// the nastiest prayable one is prayed away.
				int blocked = bestBlockable(dps);
				prayer = blocked < 0 ? null : prayerOf[blocked];
				int modeled = 0;
				for (int i = 0; i < kinds.length; i++)
				{
					if (kinds[i] == KIND_NONE)
					{
						continue;
					}
					if (!live[i])
					{
						unmodeled.add(labels[i]);
						threats.add(new StyleThreat(labels[i], 0, 0, false, false, 0, 1));
						continue;
					}
					modeled++;
					boolean isBlocked = i == blocked;
					threats.add(new StyleThreat(labels[i], dps[i], maxHits[i], true, isBlocked, share,
						isBlocked ? 0 : 1));
					unprayed += dps[i] * share;
					if (!isBlocked)
					{
						total += dps[i] * share;
					}
				}
				// A monster whose styles are all "None" does not attack: modeled, 0.
				String coverage = !unmodeled.isEmpty() ? (modeled == 0 ? NONE : PARTIAL) : FULL;
				return new Result(total, unprayed, prayer, threats, null, coverage, unmodeled);
			}
			// D-2 path: the curated attack list replaces the uniform model;
			// the protection prayer blocks the class that saves the most.
			int best = bestPrayer(dps);
			prayer = best < 0 ? null : prayerNames[best];
			for (int i = 0; i < kinds.length; i++)
			{
				if (!priced[i])
				{
					unmodeled.add(labels[i]);
					threats.add(new StyleThreat(labels[i], 0, maxHits[i], false, false, 0, 1));
					continue;
				}
				if (avoidable[i])
				{
					// In the threat list for the tooltip (what it WOULD hit),
					// out of both totals - the standard play dodges it.
					threats.add(new StyleThreat(labels[i] + " (avoidable - assumed dodged)",
						dps[i], maxHits[i], true, false, shares[i], 1));
					continue;
				}
				double through = through(i, best);
				unprayed += dps[i] * shares[i];
				total += dps[i] * shares[i] * through;
				threats.add(new StyleThreat(labels[i], dps[i], maxHits[i], true, through < 1, shares[i], through));
			}
			return new Result(total, unprayed, prayer, threats, note,
				unmodeled.isEmpty() ? CURATED : PARTIAL, unmodeled);
		}
	}

	/** Corpus style text, cleaned: lower case, no DEL noise, single spaces. */
	private static String clean(String raw)
	{
		return Names.lower(raw.replace('\u007f', ' ')).trim().replaceAll("\\s+", " ");
	}

	/** The style as the card shows it - the corpus spelling, minus noise. */
	private static String shown(String raw)
	{
		return raw.replace('\u007f', ' ').trim();
	}

	/** "Typeless X" and "X (100% prayer penetration)" roll X's accuracy,
	 * but no protection prayer stops them (wiki: Typeless damage). */
	private static boolean prayable(String raw)
	{
		String style = clean(raw);
		return !style.startsWith("typeless") && !style.contains("prayer penetration");
	}

	/**
	 * The KIND_* for a sheet/override style string (never "typeless").
	 * Corpus spellings are normalised first (ISS-01M5FZ3KYCGEMMX1A45D3J9NQ9):
	 * "Typeless slash" rolls slash, "Melee (Slash)" names its type,
	 * "Magic (special)" is magic, "None"/"N/A" do not attack.
	 */
	private static int kindOf(String rawStyle)
	{
		String style = clean(rawStyle).replace("(100% prayer penetration)", "").trim();
		if (style.isEmpty() || style.equals("none") || style.equals("n/a"))
		{
			return KIND_NONE;
		}
		if (style.startsWith("typeless "))
		{
			style = style.substring("typeless ".length());
		}
		style = style.startsWith("melee (")
			? style.substring("melee (".length()).replace(")", "").replace("?", "").trim()
			: style.replaceAll("\\s*\\(.*\\)$", "");
		if (style.equals("single and multi-target ranged"))
		{
			style = "ranged";
		}
		switch (style)
		{
			case "stab": return KIND_STAB;
			case "slash": return KIND_SLASH;
			case "crush": return KIND_CRUSH;
			case "melee": return KIND_MELEE_GENERIC;
			case "ranged":
			case "range":
			// The inverse hybrids roll vs RANGED defence (wiki: Ranged
			// magic "less likely to hit players wearing armour with high
			// Ranged defence bonuses"; Ranged melee likewise) - the
			// delivery only decides the prayer, mapped separately.
			case "ranged magic":
			case "ranged melee": return KIND_RANGED;
			case "magic":
			case "magical ranged":
			case "magical melee":
			// The NMZ Moss Guardians' corpus spelling of magical melee.
			case "magic melee": return KIND_MAGIC;
			default: return KIND_UNMODELED;
		}
	}

	private static int attackRollFor(int kind, MonsterOffence off)
	{
		MaxHits.Family family = familyOf(kind);
		return family == MaxHits.Family.MELEE ? npcRoll(off.getAttackLevel(), off.getAttackBonus())
			: family == MaxHits.Family.RANGED ? npcRoll(off.getRangedLevel(), off.getRangedBonus())
			: family == MaxHits.Family.MAGIC ? npcRoll(off.getMagicLevel(), off.getMagicBonus()) : 0;
	}

	/** The listed-max family of each modeled KIND_* (indexed by kind). */
	private static final MaxHits.Family[] FAMILY = {MaxHits.Family.MELEE, MaxHits.Family.MELEE,
		MaxHits.Family.MELEE, MaxHits.Family.MELEE, MaxHits.Family.RANGED, MaxHits.Family.MAGIC};

	/** The listed-max family of a modeled KIND_*, or null. */
	private static MaxHits.Family familyOf(int kind)
	{
		return kind >= 0 && kind < FAMILY.length ? FAMILY[kind] : null;
	}

	/** True when every attacking style is one family - only then can an
	 * unlabelled listed max ("50") be trusted for it; a dragon's plain 50
	 * might be its dragonfire. */
	private static boolean singleFamily(List<String> styles)
	{
		MaxHits.Family only = null;
		boolean seen = false;
		for (String style : styles)
		{
			int kind = kindOf(style);
			if (kind == KIND_NONE)
			{
				continue;
			}
			MaxHits.Family family = familyOf(kind);
			if (family == null || seen && family != only)
			{
				return false;
			}
			only = family;
			seen = true;
		}
		return seen;
	}

	/** Sheet styles the model can price: a known kind at a known speed. */
	private static int modeledCount(List<String> styles, MonsterOffence off)
	{
		int modeled = 0;
		for (String style : styles)
		{
			if (kindOf(style) >= 0 && off.getSpeedTicks() > 0)
			{
				modeled++;
			}
		}
		return modeled;
	}

	/** The wiki's listed max for this kind's family (or the unlabelled one
	 * when the monster attacks in one family only), else the formula -
	 * the formula misses magic and str-0 sheets badly (Vardorvis 1 vs 37). */
	private static int maxHitFor(int kind, MonsterOffence off, boolean singleFamily)
	{
		MaxHits.Family family = familyOf(kind);
		if (family != null)
		{
			MaxHits listed = off.getListedMaxHits();
			int max = listed.of(family);
			if (max < 0 && singleFamily)
			{
				max = listed.of(MaxHits.Family.GENERAL);
			}
			if (max > 0)
			{
				return max;
			}
		}
		return family == MaxHits.Family.MELEE ? npcMaxHit(off.getStrengthLevel(), off.getStrengthBonus())
			: family == MaxHits.Family.RANGED ? npcMaxHit(off.getRangedLevel(), off.getRangedStrengthBonus())
			: family == MaxHits.Family.MAGIC ? npcMaxHit(off.getMagicLevel(), off.getMagicStrengthBonus()) : 0;
	}

	/** Player magic defence level: floor(70% Magic) + floor(30% Defence) + 8
	 * (osrs-dps-calc NPCVsPlayerCalc; the +8 sits OUTSIDE the 30%, where the
	 * old (Defence+9) term read magic damage taken 4-7% high). Stance 0. */
	private static int magicEffectiveLevel(int defenceLevel, int magicLevel)
	{
		return (int) (magicLevel * 0.7) + (int) (defenceLevel * 0.3) + 8;
	}

	/** The defensive bonus a KIND_* attack rolls against. */
	private static int defenceBonusOf(int kind, StatBlock def)
	{
		switch (kind)
		{
			case KIND_STAB: return def.getStab();
			case KIND_SLASH: return def.getSlash();
			case KIND_CRUSH: return def.getCrush();
			case KIND_MELEE_GENERIC:
				// Generic "Melee": assume the boss hits your weakest side.
				return Math.min(def.getStab(), Math.min(def.getSlash(), def.getCrush()));
			case KIND_RANGED: return def.getRanged();
			default: return def.getMagic();
		}
	}

	/**
	 * The monster's chance to hit with this KIND_* vs the loadout - the ONE
	 * accuracy path, shared by the display calculate() and the beam's
	 * Prepared. The level-term guard must stay an explicit STAB..RANGED range
	 * check: KIND_UNMODELED is -1, so a "kind < KIND_RANGED" test would hand
	 * it the melee (Defence+8) term instead of the magic term the old
	 * string-keyed else-branch gave it.
	 */
	private static double accuracyOfKind(int kind, int attackRoll, StatBlock def,
		int defenceLevel, int magicEffective)
	{
		// Defence + 8 + stance (wiki DPS/Melee); the optimizer assumes an
		// offensive stance, which adds no defence.
		long level = kind >= KIND_STAB && kind <= KIND_RANGED ? defenceLevel + 8 : magicEffective;
		return accuracy(attackRoll, level * (defenceBonusOf(kind, def) + 64));
	}

	public static Result calculate(MonsterStats monster, Loadout loadout,
		int defenceLevel, int magicLevel)
	{
		// Bracelet of ethereum (charged): revenants deal NO damage while it
		// is worn - an absolute rule that outranks every other model.
		if (monster.isRevenantMonster() && wearsChargedEthereum(loadout))
		{
			return new Result(0, 0, null, List.of(),
				"Bracelet of ethereum blocks all revenant damage", FULL, List.of());
		}
		return new Prepared(monster, BossIncomingOverrides.overridesFor(monster), defenceLevel, magicLevel)
			.result(loadout);
	}

	private static boolean wearsChargedEthereum(Loadout loadout)
	{
		GearItem hands = loadout.get(GearSlot.HANDS);
		return hands != null
			&& "bracelet of ethereum".equals(hands.getNameLower())
			&& "charged".equals(hands.getVersionLower());
	}

	private static String displayStyle(String style)
	{
		return style.isEmpty() ? style
			: Character.toUpperCase(style.charAt(0)) + style.substring(1);
	}

	/** The prayer follows the attack's DELIVERY, never its accuracy roll
	 * (wiki: Magical melee / Magical ranged / Ranged magic / Ranged melee;
	 * field report 2026-08-08, grZ: a Pyrefiend's magical melee was told
	 * Protect from Magic - Protect from MELEE blocks it):
	 *   magical/magic melee -> Melee (rolls vs magic def, swings melee)
	 *   magical ranged      -> Missiles (rolls vs magic def, projectile)
	 *   ranged magic        -> Magic (rolls vs ranged def, a cast -
	 *                          Kree'arra, Spinolyps, Torchers)
	 *   ranged melee        -> Melee (rolls vs ranged def, swings melee) */
	private static String protectPrayerFor(String rawStyle)
	{
		String style = clean(rawStyle);
		if (style.contains("melee"))
		{
			return "Protect from Melee";
		}
		if (style.startsWith("magical ranged"))
		{
			return "Protect from Missiles";
		}
		if (style.equals("ranged magic") || style.startsWith("magic"))
		{
			return "Protect from Magic";
		}
		if (style.startsWith("ranged") || style.equals("range"))
		{
			return "Protect from Missiles";
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
