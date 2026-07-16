// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

public final class DpsResult
{
	private final Loadout loadout;
	private final double dps;
	private final double accuracy;
	private final double expectedHit;
	private final int maxHit;
	private final int attackSpeed;
	private final String attackType;
	private final long attackRoll;
	private final long defenceRoll;
	private final int purchaseCost;
	private final String spellName;
	/** Conditional bonuses the calculator actually counted for this set
	 * (salve, wilderness weapon, crystal set...) - user assurance that
	 * the situational math is happening. */
	private final java.util.List<String> countedBonuses;
	/** True when the optimizer could not satisfy a dragonfire shield
	 * constraint (no protective shield in the pool) and fell back to the
	 * unconstrained set - the panel must say "assumes a super antifire". */
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
			attackRoll, defenceRoll, purchaseCost, spellName, java.util.Collections.emptyList(), false);
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
		java.util.List<String> countedBonuses,
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
			? java.util.Collections.emptyList() : countedBonuses;
		this.antifireAssumed = antifireAssumed;
	}

	public java.util.List<String> getCountedBonuses()
	{
		return countedBonuses;
	}

	public DpsResult withCountedBonuses(java.util.List<String> bonuses)
	{
		return new DpsResult(loadout, dps, accuracy, expectedHit, maxHit, attackSpeed,
			attackType, attackRoll, defenceRoll, purchaseCost, spellName,
			java.util.List.copyOf(bonuses), antifireAssumed);
	}

	public boolean isAntifireAssumed()
	{
		return antifireAssumed;
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

	public Loadout getLoadout()
	{
		return loadout;
	}

	public double getDps()
	{
		return dps;
	}

	public double getAccuracy()
	{
		return accuracy;
	}

	public double getExpectedHit()
	{
		return expectedHit;
	}

	public int getMaxHit()
	{
		return maxHit;
	}

	public int getAttackSpeed()
	{
		return attackSpeed;
	}

	public String getAttackType()
	{
		return attackType;
	}

	public long getAttackRoll()
	{
		return attackRoll;
	}

	public long getDefenceRoll()
	{
		return defenceRoll;
	}

	public int getPurchaseCost()
	{
		return purchaseCost;
	}

	/** The autocast spell, or null for powered staves / non-magic - the
	 * calculator passes "" internally for the powered path, which made
	 * null-keyed consumers (the panel's built-in-spell line) miss. */
	public String getSpellName()
	{
		return spellName == null || spellName.isEmpty() ? null : spellName;
	}
}
