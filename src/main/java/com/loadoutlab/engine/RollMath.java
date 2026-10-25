// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;
import static java.lang.Math.*;

public final class RollMath {
	static final double SECONDS_PER_TICK = 0.6;

	private RollMath() {
	}

	public static double normalAccuracy(long attackRoll, long defenceRoll) {
		double atk = attackRoll;
		double def = defenceRoll;
		if (atk < 0) { atk = min(0, atk + 2); }
		if (def < 0) { def = min(0, def + 2); }
		if (atk >= 0 && def >= 0) {
			return atk > def
				? 1.0 - ((def + 2.0) / (2.0 * (atk + 1.0)))
				: atk / (2.0 * (def + 1.0));
		}
		if (atk >= 0) { return 1.0 - 1.0 / ((-def + 1.0) * (atk + 1.0)); }
		if (def >= 0) { return 0.0; }
		double swappedAtk = -def;
		double swappedDef = -atk;
		return swappedAtk > swappedDef
			? 1.0 - ((swappedDef + 2.0) / (2.0 * (swappedAtk + 1.0)))
			: swappedAtk / (2.0 * (swappedDef + 1.0));
	}

	public static int effectiveLevel(int level, double prayer, int stanceBonus) {
		return (int) floor(level * prayer) + stanceBonus + 8;
	}

	public static int maxHitFromEffective(int effectiveLevel, int strengthBonus) {
		return max(0, (int) floor((effectiveLevel * (strengthBonus + 64.0) + 320.0) / 640.0));
	}

	public static long attackRoll(int effectiveLevel, int attackBonus) {
		return (long) effectiveLevel * (attackBonus + 64L);
	}

	public static long defenceRoll(int effectiveLevel, int defenceBonus) {
		return (long) effectiveLevel * (defenceBonus + 64L);
	}

	public static double normalExpectedHit(double accuracy, int maxHit) {
		if (maxHit <= 0 || accuracy <= 0.0) { return 0.0; }
		return accuracy * (maxHit / 2.0 + 1.0 / (maxHit + 1.0));
	}

	/** The venator bow's chain in multi (wiki): two bounces, each rolled on
	 * its own accuracy at a max of two-thirds of the first, floored - so
	 * the whole arrow is worth 1 + 2 x E(floor(2m/3)) / E(m) single hits
	 * (accuracy cancels). One target: 1. */
	public static double venatorFactor(int maxHit) {
		if (maxHit <= 0) { return 1.0; }
		return 1.0 + 2.0 * normalExpectedHit(1.0, maxHit * 2 / 3) / normalExpectedHit(1.0, maxHit);
	}

	/** Expected hit under the monster's FLAT ARMOUR, official-calc
	 * semantics (flatAddTransformer, verified 2026-08-21): POSITIVE
	 * armour clamps EVERY roll to max(0, k - armour) - the low rolls
	 * become zeros, which the old shifted-bounds model missed
	 * (Eclipse Moon overpriced ~16%, grey golem ~15%). Negative
	 * armour (Blood/Blue Moon's flat bonus) is an exact bound shift,
	 * delegated so armour-free numbers stay bit-identical. Bounds are
	 * RAW (pre-armour). */
	public static double expectedHitWithFlatArmour(double accuracy, int minHit, int maxHit,
		int armour) {
		if (armour <= 0) {
			// NEGATIVE armour is a flat damage BONUS, and their
			// transformer applies it after the accurate-zero rule (a
			// landed 0 counts as 1). Shifting the bounds instead
			// silently dropped that bump - every Blood/Blue Moon set ran
			// 0.1-0.5% low (field 2026-08-21). Riding the bonus on top
			// of the normal expectation keeps the bump AND the shift.
			return expectedHit(accuracy, minHit, maxHit) + accuracy * -armour;
		}
		if (maxHit <= 0 || accuracy <= 0.0) { return 0.0; }
		int lo = max(0, min(minHit, maxHit));
		// sum(max(0, k - armour)) over k in [lo, maxHit], closed form: the
		// rolls at or under the armour contribute nothing, the rest are a
		// run of consecutive integers. Walking the rolls gave the same
		// total, but this runs on EVERY melee and ranged trial.
		long first = max((long) lo, (long) armour + 1);
		long sum = first > maxHit ? 0
			: triangle(maxHit - armour) - triangle(first - armour - 1);
		return accuracy * ((double) sum / (maxHit - lo + 1));
	}

	/** The best of several damage rolls on one landed hit (the Obligator's
	 * Lunge): P(best = v) weights each roll's damage under the same flat
	 * armour rule as expectedHitWithFlatArmour; best of one is that hit. */
	public static double bestOfExpected(double accuracy, int maxHit, int rolls, int armour) {
		if (maxHit <= 0 || accuracy <= 0.0) { return 0.0; }
		double total = 0;
		double below = 0;
		for (int v = 0; v <= maxHit; v++) {
			double atMost = pow((v + 1.0) / (maxHit + 1.0), rolls);
			total += (atMost - below) * (armour > 0 ? max(0, v - armour) : max(1, v) - armour);
			below = atMost;
		}
		return accuracy * total;
	}

	/** 0 + 1 + ... + n; n below zero is an empty run. */
	private static long triangle(long n) { return n <= 0 ? 0 : n * (n + 1) / 2; }

	public static double expectedHit(double accuracy, int minHit, int maxHit) {
		if (maxHit <= 0 || accuracy <= 0.0) { return 0.0; }
		if (minHit <= 0) { return normalExpectedHit(accuracy, maxHit); }
		int minimum = min(minHit, maxHit);
		return accuracy * ((minimum + maxHit) / 2.0);
	}

	/** Confliction gauntlets (official BaseCalc): casting one-handed
	 * grants a SECOND accuracy roll, resolved as
	 * double / (1 + double - single) - the same double-roll the fang
	 * uses, folded so the extra roll cannot exceed certainty. */
	public static double conflictionAccuracy(long attackRoll, long defenceRoll) {
		double single = normalAccuracy(attackRoll, defenceRoll);
		double doubleRoll = fangAccuracy(attackRoll, defenceRoll);
		double denominator = 1 + doubleRoll - single;
		return denominator <= 0 ? single : doubleRoll / denominator;
	}

	public static double fangAccuracy(long attackRoll, long defenceRoll) {
		double atk = attackRoll;
		double def = defenceRoll;
		if (atk < 0) { atk = min(0, atk + 2); }
		if (def < 0) { def = min(0, def + 2); }
		if (atk >= 0 && def >= 0) {
			return atk > def
				? 1.0 - (def + 2.0) * (2.0 * def + 3.0) / (atk + 1.0) / (atk + 1.0) / 6.0
				: atk * (4.0 * atk + 5.0) / 6.0 / (atk + 1.0) / (def + 1.0);
		}
		if (atk >= 0) { return 1.0 - 1.0 / (-def + 1.0) / (atk + 1.0); }
		if (def >= 0) { return 0.0; }
		double reversedAttack = -def;
		double reversedDefence = -atk;
		return reversedAttack < reversedDefence
			? reversedAttack * (reversedDefence * 6.0 - 2.0 * reversedAttack + 5.0) / 6.0 / (reversedDefence + 1.0) / (reversedDefence + 1.0)
			: 1.0 - (reversedDefence + 2.0) * (2.0 * reversedDefence + 3.0) / 6.0 / (reversedDefence + 1.0) / (reversedAttack + 1.0);
	}
}
