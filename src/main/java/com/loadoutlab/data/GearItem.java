// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

import java.util.Locale;

public final class GearItem
{
	private final int id;
	private final String name;
	private final String version;
	private final GearSlot slot;
	private final String category;
	private final int speed;
	private final boolean twoHanded;
	private final boolean standardGear;
	private final boolean tradeable;
	private final boolean members;
	private final Integer estimatedPrice;
	private final StatBlock offensive;
	private final StatBlock defensive;
	private final StatBlock bonuses;
	private final GearRequirements requirements;

	public GearItem(
		int id,
		String name,
		String version,
		GearSlot slot,
		String category,
		int speed,
		boolean twoHanded,
		boolean standardGear,
		boolean tradeable,
		boolean members,
		Integer estimatedPrice,
		StatBlock offensive,
		StatBlock defensive,
		StatBlock bonuses,
		GearRequirements requirements)
	{
		this.id = id;
		this.name = name == null ? "" : name;
		this.version = version == null ? "" : version;
		this.slot = slot;
		this.category = category == null ? "" : category;
		this.speed = speed;
		this.twoHanded = twoHanded;
		this.standardGear = standardGear;
		this.tradeable = tradeable;
		this.members = members;
		this.estimatedPrice = estimatedPrice;
		this.offensive = offensive == null ? StatBlock.ZERO : offensive;
		this.defensive = defensive == null ? StatBlock.ZERO : defensive;
		this.bonuses = bonuses == null ? StatBlock.ZERO : bonuses;
		this.requirements = requirements == null ? GearRequirements.NONE : requirements;
	}

	public boolean isWeaponFor(com.loadoutlab.engine.CombatStyle style)
	{
		if (slot != GearSlot.WEAPON)
		{
			return false;
		}

		String normalized = category.toLowerCase(Locale.ROOT);
		switch (style)
		{
			case RANGED:
				return normalized.contains("bow")
					|| normalized.contains("crossbow")
					|| normalized.contains("thrown")
					|| normalized.contains("chinchompa")
					|| normalized.contains("salamander")
					|| offensive.getRanged() > 0;
			case MAGIC:
				return normalized.contains("staff")
					|| normalized.contains("wand")
					|| offensive.getMagic() > 0;
			case MELEE:
			default:
				return !normalized.contains("bow")
					&& !normalized.contains("crossbow")
					&& !normalized.contains("thrown")
					&& !normalized.contains("chinchompa")
					&& !normalized.contains("staff")
					&& !normalized.contains("wand");
		}
	}

	public double roughScore(com.loadoutlab.engine.CombatStyle style)
	{
		switch (style)
		{
			case MELEE:
				return Math.max(offensive.getStab(), Math.max(offensive.getSlash(), offensive.getCrush()))
					+ bonuses.getStrength() * 2.5
					+ Math.max(0, bonuses.getPrayer()) * 0.25;
			case RANGED:
				return offensive.getRanged()
					+ bonuses.getRangedStrength() * 2.5
					+ Math.max(0, bonuses.getPrayer()) * 0.25;
			case MAGIC:
			default:
				return offensive.getMagic()
					+ bonuses.getMagicDamage() * 3.0
					+ Math.max(0, bonuses.getPrayer()) * 0.25;
		}
	}

	public boolean isSlayerHead()
	{
		if (slot != GearSlot.HEAD)
		{
			return false;
		}
		String normalized = normalizedLabel();
		return normalized.contains("black mask") || normalized.contains("slayer helmet");
	}

	public boolean isImbuedSlayerHead()
	{
		return isSlayerHead() && normalizedLabel().contains("(i)");
	}

	public boolean isMembers()
	{
		return members;
	}

	public String label()
	{
		return version.isEmpty() ? name : name + " (" + version + ")";
	}

	private String normalizedLabel()
	{
		return (name + " " + version).toLowerCase(Locale.ROOT);
	}

	public int getPriceOrZero()
	{
		return estimatedPrice == null ? 0 : Math.max(0, estimatedPrice);
	}

	public int getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public String getVersion()
	{
		return version;
	}

	public GearSlot getSlot()
	{
		return slot;
	}

	public String getCategory()
	{
		return category;
	}

	public int getSpeed()
	{
		return speed;
	}

	public boolean isTwoHanded()
	{
		return twoHanded;
	}

	public boolean isStandardGear()
	{
		return standardGear;
	}

	public boolean isTradeable()
	{
		return tradeable;
	}

	public Integer getEstimatedPrice()
	{
		return estimatedPrice;
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

	public GearRequirements getRequirements()
	{
		return requirements;
	}
}
