package com.loadoutlab.engine;

import lombok.Getter;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.MonsterStats;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;

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
		/** One accuracy roll; a hit deals EXACTLY damageMultiplier * max, no damage roll (sunspear). */
		FIXED_FRACTION,
		/** Four independent accuracy rolls; damage tier scales with successes (crimson kisten). */
		MULTI_ROLL_TIERED,
		/** Claws-style cascade with tier means 1.25/1.00/0.75 of max (burning claws). */
		CASCADE_CLAWS,
	}

	private static final int DARK_BOW_CAP = 48;

	private final String[] namePrefixes;
	@Getter
	private final String displayName;
	@Getter
	private final CombatStyle style;
	@Getter
	private final Kind kind;
	@Getter
	private final int energyCost;
	private final double accuracyMultiplier;
	private final double damageMultiplier;
	@Getter
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

	/** Wiki-verified entries live in special_attacks.json (hub token cap:
	 * data rides resources, not source). Order matters: more specific
	 * prefixes ("magic shortbow (i)") come first in the file. */
	private static final List<SpecialAttack> REGISTRY = loadRegistry();

	private static List<SpecialAttack> loadRegistry()
	{
		List<SpecialAttack> specs = new ArrayList<>();
		try
		{
			for (com.google.gson.JsonElement element : com.loadoutlab.data.JsonResources.array(
				"/com/loadoutlab/data/special_attacks.json"))
			{
				com.google.gson.JsonObject row = element.getAsJsonObject();
				List<String> prefixes = new ArrayList<>();
				com.loadoutlab.data.JsonResources.strings(row, "prefixes", prefixes);
				specs.add(new SpecialAttack(prefixes.toArray(new String[0]),
					row.get("name").getAsString(),
					CombatStyle.valueOf(row.get("style").getAsString()),
					Kind.valueOf(row.get("kind").getAsString()),
					row.get("cost").getAsInt(),
					row.get("accuracy").getAsDouble(),
					row.get("damage").getAsDouble(),
					row.get("note").getAsString(),
					row.get("drainFraction").getAsDouble(),
					row.get("drainsByDamage").getAsBoolean()));
			}
		}
		catch (Exception e)
		{
			// A malformed table must never take the client down; the spec
			// tests fail loudly on an empty registry instead.
			return Collections.emptyList();
		}
		return specs;
	}


	/** The definition for this weapon at this combat style, or null. */
	public static SpecialAttack match(GearItem item, CombatStyle style)
	{
		SpecialAttack spec = match(item);
		return spec != null && spec.style == style ? spec : null;
	}

	/** Style-free match: the spec swap is its own weapon switch, so every
	 * spec weapon is a candidate for ANY set (field request 2026-07-17 -
	 * "sometimes you want to use magic + chally"). */
	public static SpecialAttack match(GearItem item)
	{
		if (item == null || item.getSlot() != GearSlot.WEAPON)
		{
			return null;
		}
		String name = item.getNameLower();
		for (SpecialAttack spec : REGISTRY)
		{
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
			case FIXED_FRACTION:
				// No damage roll: a hit deals exactly damageMultiplier * max.
				return hitChance * damageMultiplier * max;
			case MULTI_ROLL_TIERED:
				return multiRollTieredExpected(hitChance, max);
			case CASCADE_CLAWS:
				return cascadeClawsExpected(hitChance, max);
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

	/**
	 * Crimson kisten Brutal Swing: four independent accuracy rolls; with
	 * k >= 1 successes damage is uniform in [(50+20k)%, (90+20k)%] of max
	 * (mean (0.7 + 0.2k) * max); zero successes deal nothing.
	 */
	private static double multiRollTieredExpected(double p, int max)
	{
		// Binomial coefficients C(4, k) for k = 0..4.
		final int[] choose = {1, 4, 6, 4, 1};
		double expected = 0;
		for (int k = 1; k <= 4; k++)
		{
			expected += choose[k] * Math.pow(p, k) * Math.pow(1 - p, 4 - k)
				* (0.7 + 0.2 * k) * max;
		}
		return expected;
	}

	/**
	 * Burning claws cascade: the first successful roll (of up to three)
	 * sets the tier - uniform damage with means 1.25/1.00/0.75 of max; a
	 * full miss deals 0/1/2 with 20/40/40% odds (expected 1.2). The burn
	 * damage-over-time is described in the note, not counted here.
	 */
	private static double cascadeClawsExpected(double p, int max)
	{
		double miss = 1 - p;
		return p * (1.25 * max)
			+ miss * p * (1.00 * max)
			+ miss * miss * p * (0.75 * max)
			+ miss * miss * miss * 1.2;
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
		return ammo != null && ammo.getNameLower().startsWith("dragon arrow");
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
	 * Volatile spec max: spell max is min(floor(58 * level / 99) + 1, 58)
	 * - 58 from level 98 up - then the loadout's magic damage bonus
	 * (tenths of a percent) scales it. Wiki anchors: level 84 gives spell
	 * max 50 (57 with the staff's 15% bonus); level 99 gives 58 (66).
	 */
	private static int volatileMax(DpsResult base, PlayerLevels levels)
	{
		int spellMax = Math.min((int) Math.floor(58.0 * levels.getMagic() / 99.0) + 1, 58);
		double gearBonus = 1 + base.getLoadout().getBonuses().getMagicDamage() / 1000.0;
		return (int) Math.floor(spellMax * gearBonus);
	}

	private static double mean(int maxHit)
	{
		return maxHit / 2.0;
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
