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
	// Lowercase views, precomputed once: the engine matches items by name
	// fragment on every candidate evaluation, and per-call toLowerCase was
	// the single hottest path in the whole optimize (64% of CPU samples).
	private final String nameLower;
	private final String versionLower;
	private final String categoryLower;
	private final String labelLower;
	private final String nameVersionLower;
	private final boolean poweredStaff;

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
		this.nameLower = this.name.toLowerCase(Locale.ROOT);
		this.versionLower = this.version.toLowerCase(Locale.ROOT);
		this.categoryLower = this.category.toLowerCase(Locale.ROOT);
		this.labelLower = label().toLowerCase(Locale.ROOT);
		this.nameVersionLower = (this.name + " " + this.version).toLowerCase(Locale.ROOT);
		// Precomputed once: the optimizer asks this on every trial, and the
		// name-fragment chain was the single hottest call in the profile.
		// DpsCalculator.isPoweredStaff stays the API - it delegates here.
		this.poweredStaff = categoryLower.contains("powered staff")
			|| nameLower.contains("trident")
			|| nameLower.contains("thammaron")
			|| nameLower.contains("accursed sceptre")
			|| nameLower.contains("sanguinesti")
			|| nameLower.contains("tumeken")
			|| nameLower.contains("warped sceptre")
			|| nameLower.contains("eye of ayak")
			|| nameLower.contains("bone staff");
	}

	/** Powered staff (built-in spell, no autocast) - see DpsCalculator.isPoweredStaff. */
	public boolean isPoweredStaff()
	{
		return poweredStaff;
	}

	public boolean isWeaponFor(com.loadoutlab.engine.CombatStyle style)
	{
		if (slot != GearSlot.WEAPON)
		{
			return false;
		}

		String normalized = categoryLower;
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

	/** label() lowercased (Locale.ROOT), precomputed. */
	public String labelLower()
	{
		return labelLower;
	}

	/** getName() lowercased (Locale.ROOT), precomputed. */
	public String getNameLower()
	{
		return nameLower;
	}

	/** getVersion() lowercased (Locale.ROOT), precomputed. */
	public String getVersionLower()
	{
		return versionLower;
	}

	/**
	 * Poison tier of this version: Poison++ 3, Poison+ 2, Poison 1,
	 * everything else 0. Poison is free damage the DPS model does not
	 * price, so suggestion tie-breaks prefer the higher tier (field
	 * request: always recommend dragon dagger p++ over plain).
	 */
	public int poisonTier()
	{
		if (versionLower.contains("unpoison"))
		{
			return 0;
		}
		if (versionLower.contains("poison++"))
		{
			return 3;
		}
		if (versionLower.contains("poison+"))
		{
			return 2;
		}
		return versionLower.contains("poison") ? 1 : 0;
	}

	/** getCategory() lowercased (Locale.ROOT), precomputed. */
	public String getCategoryLower()
	{
		return categoryLower;
	}

	private String normalizedLabel()
	{
		return nameVersionLower;
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
