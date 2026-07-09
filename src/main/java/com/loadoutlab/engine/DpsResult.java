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
		return new DpsResult(loadout, dps, accuracy, expectedHit, maxHit, attackSpeed, attackType, attackRoll, defenceRoll, purchaseCost, spellName);
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

	public String getSpellName()
	{
		return spellName;
	}
}
