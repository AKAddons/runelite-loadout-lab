package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.MonsterStats;
import java.util.Arrays;
import java.util.List;

/**
 * Special-attack definitions and expected-damage math.
 *
 * <p>Every entry's cost/modifiers were verified against the OSRS Wiki
 * "Special attacks" page (and individual weapon pages) on 2026-07-05.
 * Accuracy modifiers multiply the ATTACK ROLL (not the final hit chance),
 * matching in-game mechanics; damage modifiers scale the max hit.
 *
 * <p>Expected damage is per single use of the special attack, against the
 * monster the base result was computed for. Utility effects (defence
 * drains, heals, bolt procs) are described in {@link #getNote()} but not
 * folded into the number.
 */
public final class SpecialAttack
{
	public enum Kind
	{
		/** One hit: accuracy and/or damage multipliers. */
		SINGLE,
		/** Two fully independent hits (dragon dagger, dragon knife). */
		DOUBLE_INDEPENDENT,
		/** Two hits that share one accuracy outcome (abyssal dagger). */
		LINKED_DOUBLE,
		/** Guaranteed damage, uniform 50-150% of max (voidwaker). */
		VOIDWAKER,
		/** Up to 4 accuracy rolls; damage tier by which roll landed (dragon claws). */
		CLAWS,
		/** Two hits, boosted damage roll with a minimum per hit, capped at 48 (dark bow). */
		DARK_BOW,
		/** Two arrows; custom prayer-less max-hit formula, 10/7 accuracy (magic shortbow). */
		MSB_SNAPSHOT,
		/** +10% damage; a second hit at 75% accuracy vs large monsters (halberds). */
		HALBERD_SWEEP,
		/** An instant extra normal attack (granite maul). */
		EXTRA_ATTACK,
		/** Level-scaled unique damage roll (volatile nightmare staff). */
		VOLATILE,
	}

	private static final int DARK_BOW_CAP = 48;

	private final String[] namePrefixes;
	private final String displayName;
	private final CombatStyle style;
	private final Kind kind;
	private final int energyCost;
	private final double accuracyMultiplier;
	private final double damageMultiplier;
	private final String note;
	/** Defence drained on a damaging hit, as a fraction of CURRENT defence
	 * (DWH 0.30, elder maul 0.35); 0 for non-drain specs. BGS drains by
	 * damage dealt - modeled via {@link #drainsByDamage}. */
	private final double defenceDrainFraction;
	private final boolean drainsByDamage;

	private SpecialAttack(String[] namePrefixes, String displayName, CombatStyle style, Kind kind,
		int energyCost, double accuracyMultiplier, double damageMultiplier, String note)
	{
		this(namePrefixes, displayName, style, kind, energyCost, accuracyMultiplier, damageMultiplier, note, 0, false);
	}

	private SpecialAttack(String[] namePrefixes, String displayName, CombatStyle style, Kind kind,
		int energyCost, double accuracyMultiplier, double damageMultiplier, String note,
		double defenceDrainFraction, boolean drainsByDamage)
	{
		this.defenceDrainFraction = defenceDrainFraction;
		this.drainsByDamage = drainsByDamage;
		this.namePrefixes = namePrefixes;
		this.displayName = displayName;
		this.style = style;
		this.kind = kind;
		this.energyCost = energyCost;
		this.accuracyMultiplier = accuracyMultiplier;
		this.damageMultiplier = damageMultiplier;
		this.note = note;
	}

	/** Order matters: more specific prefixes (e.g. "magic shortbow (i)") come first. */
	private static final List<SpecialAttack> REGISTRY = Arrays.asList(
		// Melee
		new SpecialAttack(p("dragon dagger"), "Dragon dagger", CombatStyle.MELEE,
			Kind.DOUBLE_INDEPENDENT, 25, 1.15, 1.15, ""),
		new SpecialAttack(p("abyssal dagger"), "Abyssal dagger", CombatStyle.MELEE,
			Kind.LINKED_DOUBLE, 25, 1.25, 0.85, "second hit shares the first's accuracy roll"),
		new SpecialAttack(p("dragon claws"), "Dragon claws", CombatStyle.MELEE,
			Kind.CLAWS, 50, 1.0, 1.0, ""),
		new SpecialAttack(p("voidwaker"), "Voidwaker", CombatStyle.MELEE,
			Kind.VOIDWAKER, 50, 1.0, 1.0, "guaranteed hit"),
		new SpecialAttack(p("armadyl godsword"), "Armadyl godsword", CombatStyle.MELEE,
			Kind.SINGLE, 50, 2.0, 1.375, ""),
		new SpecialAttack(p("bandos godsword"), "Bandos godsword", CombatStyle.MELEE,
			Kind.SINGLE, 50, 2.0, 1.21, "drains combat stats by damage dealt", 0, true),
		new SpecialAttack(p("saradomin godsword"), "Saradomin godsword", CombatStyle.MELEE,
			Kind.SINGLE, 50, 2.0, 1.10, "heals HP 50% / Prayer 25% of damage"),
		new SpecialAttack(p("ancient godsword"), "Ancient godsword", CombatStyle.MELEE,
			Kind.SINGLE, 50, 2.0, 1.10, "plus 25 delayed blood-sacrifice damage"),
		new SpecialAttack(p("dragon warhammer"), "Dragon warhammer", CombatStyle.MELEE,
			Kind.SINGLE, 50, 1.0, 1.50, "lowers Defence 30% on a damaging hit", 0.30, false),
		new SpecialAttack(p("elder maul"), "Elder maul", CombatStyle.MELEE,
			Kind.SINGLE, 50, 1.25, 1.0, "lowers Defence 35% on a damaging hit", 0.35, false),
		new SpecialAttack(p("granite maul"), "Granite maul", CombatStyle.MELEE,
			Kind.EXTRA_ATTACK, 60, 1.0, 1.0, "instant extra attack"),
		new SpecialAttack(p("crystal halberd", "dragon halberd"), "Halberd sweep", CombatStyle.MELEE,
			Kind.HALBERD_SWEEP, 30, 1.0, 1.10, "second hit only vs large (2x2+) monsters"),
		// Ranged
		new SpecialAttack(p("dark bow"), "Dark bow", CombatStyle.RANGED,
			Kind.DARK_BOW, 55, 1.0, 1.30, "with dragon arrows: +50% damage, min 8 per hit"),
		new SpecialAttack(p("magic shortbow (i)"), "Magic shortbow (i)", CombatStyle.RANGED,
			Kind.MSB_SNAPSHOT, 50, 10.0 / 7.0, 1.0, "max hit ignores prayers and void"),
		new SpecialAttack(p("magic shortbow"), "Magic shortbow", CombatStyle.RANGED,
			Kind.MSB_SNAPSHOT, 55, 10.0 / 7.0, 1.0, "max hit ignores prayers and void"),
		new SpecialAttack(p("dragon knife"), "Dragon knife", CombatStyle.RANGED,
			Kind.DOUBLE_INDEPENDENT, 25, 1.0, 1.0, ""),
		new SpecialAttack(p("toxic blowpipe"), "Toxic blowpipe", CombatStyle.RANGED,
			Kind.SINGLE, 50, 2.0, 1.50, "heals 50% of damage dealt"),
		new SpecialAttack(p("zaryte crossbow"), "Zaryte crossbow", CombatStyle.RANGED,
			Kind.SINGLE, 75, 2.0, 1.0, "guaranteed bolt proc on hit (proc damage not modeled)"),
		// Magic
		new SpecialAttack(p("volatile nightmare staff"), "Volatile nightmare staff", CombatStyle.MAGIC,
			Kind.VOLATILE, 55, 1.5, 1.0, "rune-free; damage scales with Magic level"),
		new SpecialAttack(p("accursed sceptre"), "Accursed sceptre", CombatStyle.MAGIC,
			Kind.SINGLE, 50, 1.5, 1.0, "lowers target Defence and Magic 15%"));

	private static String[] p(String... prefixes)
	{
		return prefixes;
	}

	/** The definition for this weapon at this combat style, or null. */
	public static SpecialAttack match(GearItem item, CombatStyle style)
	{
		if (item == null || item.getSlot() != GearSlot.WEAPON)
		{
			return null;
		}
		String name = item.getName().toLowerCase();
		for (SpecialAttack spec : REGISTRY)
		{
			if (spec.style != style)
			{
				continue;
			}
			for (String prefix : spec.namePrefixes)
			{
				if (name.startsWith(prefix))
				{
					return spec;
				}
			}
		}
		return null;
	}

	/**
	 * Expected damage of ONE special attack, given the normal-attack result
	 * for the same loadout (source of max hit and attack/defence rolls).
	 */
	public double expectedDamage(DpsResult base, MonsterStats monster, PlayerLevels levels)
	{
		long attackRoll = base.getAttackRoll();
		long defenceRoll = base.getDefenceRoll();
		double hitChance = RollMath.normalAccuracy(
			(long) (attackRoll * accuracyMultiplier), defenceRoll);
		int max = base.getMaxHit();

		switch (kind)
		{
			case SINGLE:
				return hitChance * mean((int) (max * damageMultiplier));
			case DOUBLE_INDEPENDENT:
				return 2 * hitChance * mean((int) (max * damageMultiplier));
			case LINKED_DOUBLE:
				// One accuracy outcome for both hits.
				return hitChance * 2 * mean((int) (max * damageMultiplier));
			case VOIDWAKER:
				// Uniform 50-150% of max, no accuracy roll: averages exactly max.
				return max;
			case CLAWS:
				return clawsExpected(hitChance, max);
			case DARK_BOW:
				return darkBowExpected(hitChance, max, usesDragonArrows(base));
			case MSB_SNAPSHOT:
				return 2 * hitChance * mean(snapshotMax(base, levels));
			case HALBERD_SWEEP:
			{
				double first = hitChance * mean((int) (max * damageMultiplier));
				if (monster != null && monster.getSize() > 1)
				{
					double second = RollMath.normalAccuracy((long) (attackRoll * 0.75), defenceRoll);
					first += second * mean((int) (max * damageMultiplier));
				}
				return first;
			}
			case EXTRA_ATTACK:
				return base.getExpectedHit();
			case VOLATILE:
			default:
				return hitChance * mean(volatileMax(base, levels));
		}
	}

	/**
	 * Dragon claws cascade: accuracy is rolled up to four times; the tier of
	 * the successful roll sets the damage-roll modifier (100/75/50/25% of
	 * max added to the minimum). Expected totals per tier: 1.5max-1,
	 * 1.25max, max, 0.75max; a full miss averages ~1.
	 */
	private static double clawsExpected(double p, int max)
	{
		double miss = 1 - p;
		return p * (1.5 * max - 1)
			+ miss * p * (1.25 * max)
			+ miss * miss * p * max
			+ miss * miss * miss * p * (0.75 * max)
			+ Math.pow(miss, 4) * 1.0;
	}

	/** Dark bow: two hits, each rolled 0..boostedMax then clamped [min, 48]. */
	private static double darkBowExpected(double p, int max, boolean dragonArrows)
	{
		int boosted = (int) (max * (dragonArrows ? 1.5 : 1.3));
		int min = dragonArrows ? 8 : 5;
		double sum = 0;
		for (int d = 0; d <= boosted; d++)
		{
			sum += Math.min(DARK_BOW_CAP, Math.max(min, d));
		}
		return 2 * p * (sum / (boosted + 1));
	}

	private static boolean usesDragonArrows(DpsResult base)
	{
		GearItem ammo = base.getLoadout().get(GearSlot.AMMO);
		return ammo != null && ammo.getName().toLowerCase().startsWith("dragon arrow");
	}

	/**
	 * Snapshot max hit ignores prayers and void:
	 * floor(0.5 + (visible ranged + 10) * (ammo ranged str + 64) / 640).
	 */
	private static int snapshotMax(DpsResult base, PlayerLevels levels)
	{
		GearItem ammo = base.getLoadout().get(GearSlot.AMMO);
		int ammoStrength = ammo == null ? 0 : ammo.getBonuses().getRangedStrength();
		return (int) Math.floor(0.5 + (levels.getRanged() + 10) * (ammoStrength + 64) / 640.0);
	}

	/**
	 * Volatile spec max: ~58 at 99 Magic before gear, scaled by the
	 * loadout's magic damage bonus (tenths of a percent). Level scaling
	 * approximated linearly - wiki gives 49 at 82 through 97 fully geared.
	 */
	private static int volatileMax(DpsResult base, PlayerLevels levels)
	{
		double gearBonus = 1 + base.getLoadout().getBonuses().getMagicDamage() / 1000.0;
		return (int) Math.floor(levels.getMagic() * 58.0 / 99.0 * gearBonus);
	}

	private static double mean(int maxHit)
	{
		return maxHit / 2.0;
	}

	/**
	 * Sustained DPS added by weaving this spec on cooldown: net gain per
	 * spec (the spec replaces one normal attack) times specs per second
	 * from energy regen - 10% per 30s, doubled by the Lightbearer.
	 */
	public double sustainedDpsBonus(double specExpectedDamage, double replacedAutoExpected, boolean lightbearer)
	{
		double regenPercentPerSecond = lightbearer ? 10.0 / 15.0 : 10.0 / 30.0;
		double specsPerSecond = regenPercentPerSecond / energyCost;
		return Math.max(0, specExpectedDamage - replacedAutoExpected) * specsPerSecond;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public CombatStyle getStyle()
	{
		return style;
	}

	public int getEnergyCost()
	{
		return energyCost;
	}

	public String getNote()
	{
		return note;
	}

	public Kind getKind()
	{
		return kind;
	}

	/** Chance this spec lands a damaging hit, from the base result's rolls. */
	public double landChance(DpsResult base)
	{
		if (kind == Kind.VOIDWAKER)
		{
			return 1.0;
		}
		return RollMath.normalAccuracy(
			(long) (base.getAttackRoll() * accuracyMultiplier), base.getDefenceRoll());
	}

	/**
	 * The monster's Defence level after ONE successful use of this spec, or
	 * the unchanged level for non-drain specs. BGS drains by damage dealt.
	 */
	public int drainedDefence(int currentDefence, double specExpectedDamage)
	{
		if (drainsByDamage)
		{
			return Math.max(0, currentDefence - (int) specExpectedDamage);
		}
		if (defenceDrainFraction > 0)
		{
			return currentDefence - (int) (currentDefence * defenceDrainFraction);
		}
		return currentDefence;
	}

	public boolean drainsDefence()
	{
		return drainsByDamage || defenceDrainFraction > 0;
	}
}
