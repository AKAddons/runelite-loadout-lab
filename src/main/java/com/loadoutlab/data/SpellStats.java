// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

public final class SpellStats
{
	private final String name;
	private final int maxHit;
	private final int magicLevel;
	private final String spellbook;
	private final String element;

	public SpellStats(String name, int maxHit, int magicLevel, String spellbook, String element)
	{
		this.name = name == null ? "" : name;
		this.maxHit = maxHit;
		this.magicLevel = magicLevel;
		this.spellbook = spellbook == null ? "" : spellbook;
		this.element = element == null ? "" : element;
	}

	public String getName()
	{
		return name;
	}

	public int getMaxHit()
	{
		return maxHit;
	}

	public int getMagicLevel()
	{
		return magicLevel;
	}

	public String getSpellbook()
	{
		return spellbook;
	}

	public String getElement()
	{
		return element;
	}

	@Override
	public String toString()
	{
		return name;
	}
}
