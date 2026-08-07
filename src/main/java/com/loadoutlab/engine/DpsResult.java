// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;
import lombok.Getter;
import java.util.List;
import java.util.Collections;

public final class DpsResult
{
	@Getter
	private final Loadout loadout;
	@Getter
	private final double dps;
	@Getter
	private final double accuracy;
	@Getter
	private final double expectedHit;
	@Getter
	private final int maxHit;
	@Getter
	private final int attackSpeed;
	@Getter
	private final String attackType;
	@Getter
	private final long attackRoll;
	@Getter
	private final long defenceRoll;
	@Getter
	private final int purchaseCost;
	private final String spellName;
	/** Conditional bonuses the calculator actually counted for this set
	 * (salve, wilderness weapon, crystal set...) - user assurance that
	 * the situational math is happening. */
	@Getter
	private final List<String> countedBonuses;
	/** True when the optimizer could not satisfy a dragonfire shield
	 * constraint (no protective shield in the pool) and fell back to the
	 * unconstrained set - the panel must say "assumes a super antifire". */
	@Getter
	private final boolean antifireAssumed;

	public DpsResult(
		Loadout loadout,
		double dps,
		double accuracy,
		double expectedHit,
		int maxHit,
		int attackSpeed,
		String attackType,
		long attackRoll,
		long defenceRoll)
	{
		this(loadout, dps, accuracy, expectedHit, maxHit, attackSpeed, attackType, attackRoll, defenceRoll, loadout == null ? 0 : loadout.getCost(), "");
	}

	public DpsResult(
		Loadout loadout,
		double dps,
		double accuracy,
		double expectedHit,
		int maxHit,
		int attackSpeed,
		String attackType,
		long attackRoll,
		long defenceRoll,
		int purchaseCost,
		String spellName)
	{
		this(loadout, dps, accuracy, expectedHit, maxHit, attackSpeed, attackType,
			attackRoll, defenceRoll, purchaseCost, spellName, Collections.emptyList(), false);
	}

	private DpsResult(
		Loadout loadout,
		double dps,
		double accuracy,
		double expectedHit,
		int maxHit,
		int attackSpeed,
		String attackType,
		long attackRoll,
		long defenceRoll,
		int purchaseCost,
		String spellName,
		List<String> countedBonuses,
		boolean antifireAssumed)
	{
		this.loadout = loadout;
		this.dps = dps;
		this.accuracy = accuracy;
		this.expectedHit = expectedHit;
		this.maxHit = maxHit;
		this.attackSpeed = attackSpeed;
		this.attackType = attackType;
		this.attackRoll = attackRoll;
		this.defenceRoll = defenceRoll;
		this.purchaseCost = Math.max(0, purchaseCost);
		this.spellName = spellName == null ? "" : spellName;
		this.countedBonuses = countedBonuses == null
			? Collections.emptyList() : countedBonuses;
		this.antifireAssumed = antifireAssumed;
	}

	public DpsResult withCountedBonuses(List<String> bonuses)
	{
		return new DpsResult(loadout, dps, accuracy, expectedHit, maxHit, attackSpeed,
			attackType, attackRoll, defenceRoll, purchaseCost, spellName,
			List.copyOf(bonuses), antifireAssumed);
	}

	public DpsResult withAntifireAssumed(boolean assumed)
	{
		return new DpsResult(loadout, dps, accuracy, expectedHit, maxHit, attackSpeed,
			attackType, attackRoll, defenceRoll, purchaseCost, spellName,
			countedBonuses, assumed);
	}

	/**
	 * The higher-dps of two nullable results; the first wins ties. The
	 * one comparison rule for "keep the better candidate" - previously
	 * duplicated by DpsCalculator and LoadoutOptimizer.
	 */
	static DpsResult better(DpsResult first, DpsResult second)
	{
		if (second == null)
		{
			return first;
		}
		if (first == null || second.getDps() > first.getDps())
		{
			return second;
		}
		return first;
	}

	public DpsResult withPurchaseCost(int purchaseCost)
	{
		return new DpsResult(loadout, dps, accuracy, expectedHit, maxHit, attackSpeed,
			attackType, attackRoll, defenceRoll, purchaseCost, spellName, countedBonuses,
			antifireAssumed);
	}

	/** The autocast spell, or null for powered staves / non-magic - the
	 * calculator passes "" internally for the powered path, which made
	 * null-keyed consumers (the panel's built-in-spell line) miss. */
	public String getSpellName()
	{
		return spellName == null || spellName.isEmpty() ? null : spellName;
	}
}
