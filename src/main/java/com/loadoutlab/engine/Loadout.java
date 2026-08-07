// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import lombok.Getter;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.StatBlock;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public final class Loadout
{
	private final EnumMap<GearSlot, GearItem> gear;
	@Getter
	private final StatBlock offensive;
	@Getter
	private final StatBlock defensive;
	@Getter
	private final StatBlock bonuses;
	@Getter
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
		this(copyOf(gear), true);
	}

	/**
	 * Adopt the given map WITHOUT copying: the caller hands over ownership
	 * and must never mutate it afterward. The optimizer builds a fresh
	 * EnumMap per beam trial - the public constructor's defensive copy
	 * doubled that work across hundreds of thousands of trials.
	 */
	public static Loadout adopting(EnumMap<GearSlot, GearItem> gear)
	{
		return new Loadout(gear, true);
	}

	private static EnumMap<GearSlot, GearItem> copyOf(Map<GearSlot, GearItem> gear)
	{
		EnumMap<GearSlot, GearItem> copy = new EnumMap<>(GearSlot.class);
		copy.putAll(gear);
		return copy;
	}

	/** The marker keeps this overload from ever being chosen by a plain
	 * {@code new Loadout(enumMap)} - adoption must be an explicit opt-in. */
	private Loadout(EnumMap<GearSlot, GearItem> gear, boolean adoptMarker)
	{
		this.gear = gear;

		// Summed into an int array, not StatBlock.plus chains - the chain
		// allocated ~3 blocks per worn item per trial (top allocation site).
		int[] off = new int[9];
		int[] def = new int[9];
		int[] bon = new int[9];
		int totalCost = 0;
		StringBuilder names = new StringBuilder(160);
		StringBuilder activeNames = new StringBuilder(160);
		for (GearItem item : this.gear.values())
		{
			if (item == null)
			{
				continue;
			}
			add(off, item.getOffensive());
			add(def, item.getDefensive());
			add(bon, item.getBonuses());
			totalCost += item.getPriceOrZero();
			names.append(item.getNameLower()).append('\n');
			if (!"inactive".equalsIgnoreCase(item.getVersion()))
			{
				activeNames.append(item.getNameLower()).append('\n');
			}
		}
		this.offensive = block(off);
		this.defensive = block(def);
		this.bonuses = block(bon);
		this.cost = totalCost;
		this.namesLower = names.toString();
		this.activeNamesLower = activeNames.toString();
	}

	private static void add(int[] acc, StatBlock block)
	{
		acc[0] += block.getStab();
		acc[1] += block.getSlash();
		acc[2] += block.getCrush();
		acc[3] += block.getMagic();
		acc[4] += block.getRanged();
		acc[5] += block.getStrength();
		acc[6] += block.getRangedStrength();
		acc[7] += block.getMagicDamage();
		acc[8] += block.getPrayer();
	}

	private static StatBlock block(int[] acc)
	{
		return new StatBlock(acc[0], acc[1], acc[2], acc[3], acc[4],
			acc[5], acc[6], acc[7], acc[8]);
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
}
