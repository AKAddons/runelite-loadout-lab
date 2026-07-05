// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class MonsterStats
{
	private final int id;
	private final String name;
	private final String version;
	private final int combatLevel;
	private final int hitpoints;
	private final int size;
	private final int defence;
	private final int magic;
	private final int offensiveMagic;
	private final MonsterDefences defensive;
	private final List<String> attributes;
	private final boolean slayerMonster;
	private final String weaknessElement;
	private final int weaknessSeverity;

	public MonsterStats(
		int id,
		String name,
		String version,
		int combatLevel,
		int hitpoints,
		int defence,
		int magic,
		MonsterDefences defensive,
		List<String> attributes)
	{
		this(id, name, version, combatLevel, hitpoints, 1, defence, magic, 0, defensive, attributes, false, "", 0);
	}

	public MonsterStats(
		int id,
		String name,
		String version,
		int combatLevel,
		int hitpoints,
		int size,
		int defence,
		int magic,
		int offensiveMagic,
		MonsterDefences defensive,
		List<String> attributes,
		boolean slayerMonster,
		String weaknessElement,
		int weaknessSeverity)
	{
		this.id = id;
		this.name = name == null ? "" : name;
		this.version = version == null ? "" : version;
		this.combatLevel = combatLevel;
		this.hitpoints = hitpoints;
		this.size = Math.max(1, size);
		this.defence = defence;
		this.magic = magic;
		this.offensiveMagic = offensiveMagic;
		this.defensive = defensive == null ? MonsterDefences.ZERO : defensive;
		this.attributes = attributes == null ? Collections.emptyList() : Collections.unmodifiableList(attributes);
		this.slayerMonster = slayerMonster;
		this.weaknessElement = weaknessElement == null ? "" : weaknessElement.toLowerCase(Locale.ROOT);
		this.weaknessSeverity = Math.max(0, weaknessSeverity);
	}

	public boolean hasAttribute(String attribute)
	{
		String normalized = attribute.toLowerCase(Locale.ROOT);
		for (String value : attributes)
		{
			if (value != null && value.toLowerCase(Locale.ROOT).equals(normalized))
			{
				return true;
			}
		}
		return false;
	}

	public String label()
	{
		String suffix = version.isEmpty() ? "" : " (" + version + ")";
		return name + suffix;
	}

	public String searchText()
	{
		return (name + " " + version + " " + id).toLowerCase(Locale.ROOT);
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

	public int getCombatLevel()
	{
		return combatLevel;
	}

	public int getHitpoints()
	{
		return hitpoints;
	}

	public int getSize()
	{
		return size;
	}

	public int getDefence()
	{
		return defence;
	}

	public int getMagic()
	{
		return magic;
	}

	public int getOffensiveMagic()
	{
		return offensiveMagic;
	}

	public MonsterDefences getDefensive()
	{
		return defensive;
	}

	public List<String> getAttributes()
	{
		return attributes;
	}

	public boolean isSlayerMonster()
	{
		return slayerMonster;
	}

	public String getWeaknessElement()
	{
		return weaknessElement;
	}

	public int getWeaknessSeverity()
	{
		return weaknessSeverity;
	}
}
