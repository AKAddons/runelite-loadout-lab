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
	/** All worn item names, lowercased and newline-joined - so the engine's
	 * name-fragment checks ("salve amulet", "void knight top"...) are one
	 * substring scan instead of an 11-slot map iteration per marker. Item
	 * names never contain a newline, so a needle cannot straddle two names. */
	private final String namesLower;
	/** namesLower minus "Inactive"-version items (uncharged crystal). */
	private final String activeNamesLower;

	public Loadout(Map<GearSlot, GearItem> gear)
	{
		this.gear = new EnumMap<>(GearSlot.class);
		this.gear.putAll(gear);

		StatBlock offensiveTotal = StatBlock.ZERO;
		StatBlock defensiveTotal = StatBlock.ZERO;
		StatBlock bonusTotal = StatBlock.ZERO;
		int totalCost = 0;
		StringBuilder names = new StringBuilder(160);
		StringBuilder activeNames = new StringBuilder(160);
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
			names.append(item.getNameLower()).append('\n');
			if (!"inactive".equalsIgnoreCase(item.getVersion()))
			{
				activeNames.append(item.getNameLower()).append('\n');
			}
		}
		this.offensive = offensiveTotal;
		this.defensive = defensiveTotal;
		this.bonuses = bonusTotal;
		this.cost = totalCost;
		this.namesLower = names.toString();
		this.activeNamesLower = activeNames.toString();
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

	/** Lowercased worn item names, newline-joined (see field doc). */
	String namesLower()
	{
		return namesLower;
	}

	/** namesLower() without "Inactive"-version items. */
	String activeNamesLower()
	{
		return activeNamesLower;
	}

	/** Items in this set you would risk in PvP (tradeables). */
	public int tradeableCount()
	{
		int count = 0;
		for (GearItem item : gear.values())
		{
			if (item != null && item.isTradeable())
			{
				count++;
			}
		}
		return count;
	}
}
