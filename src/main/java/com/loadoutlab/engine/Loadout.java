// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.StatBlock;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class Loadout
{
	private final EnumMap<GearSlot, GearItem> gear;
	private final StatBlock offensive;
	private final StatBlock defensive;
	private final StatBlock bonuses;
	private final int cost;

	public Loadout(Map<GearSlot, GearItem> gear)
	{
		this.gear = new EnumMap<>(GearSlot.class);
		this.gear.putAll(gear);

		StatBlock offensiveTotal = StatBlock.ZERO;
		StatBlock defensiveTotal = StatBlock.ZERO;
		StatBlock bonusTotal = StatBlock.ZERO;
		int totalCost = 0;
		for (GearItem item : this.gear.values())
		{
			if (item == null)
			{
				continue;
			}
			offensiveTotal = offensiveTotal.plus(item.getOffensive());
			defensiveTotal = defensiveTotal.plus(item.getDefensive());
			bonusTotal = bonusTotal.plus(item.getBonuses());
			totalCost += item.getPriceOrZero();
		}
		this.offensive = offensiveTotal;
		this.defensive = defensiveTotal;
		this.bonuses = bonusTotal;
		this.cost = totalCost;
	}

	public GearItem get(GearSlot slot)
	{
		return gear.get(slot);
	}

	public GearItem getWeapon()
	{
		return gear.get(GearSlot.WEAPON);
	}

	public Map<GearSlot, GearItem> getGear()
	{
		return Collections.unmodifiableMap(gear);
	}

	public StatBlock getOffensive()
	{
		return offensive;
	}

	public StatBlock getDefensive()
	{
		return defensive;
	}

	public StatBlock getBonuses()
	{
		return bonuses;
	}

	public int getCost()
	{
		return cost;
	}
}
