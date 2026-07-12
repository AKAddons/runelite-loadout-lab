// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

public final class SpellStats
{
	private final String name;
	private final int maxHit;
	private final int magicLevel;
	private final String spellbook;
	private final String element;
	private final String nameFirstWord;
	private final String nameSecondWord;

	public SpellStats(String name, int maxHit, int magicLevel, String spellbook, String element)
	{
		this.name = name == null ? "" : name;
		this.maxHit = maxHit;
		this.magicLevel = magicLevel;
		this.spellbook = spellbook == null ? "" : spellbook;
		this.element = element == null ? "" : element;
		// The elemental-class max hit re-derived these per DPS trial via
		// split() - a String[] allocation in the optimizer's hottest loop.
		// Null unless the name is exactly two words ("Fire Surge").
		int space = this.name.indexOf(' ');
		boolean twoWords = space > 0 && this.name.indexOf(' ', space + 1) < 0
			&& space < this.name.length() - 1;
		this.nameFirstWord = twoWords ? this.name.substring(0, space) : null;
		this.nameSecondWord = twoWords ? this.name.substring(space + 1) : null;
	}

	/** First word of a two-word spell name, else null (cached). */
	public String getNameFirstWord()
	{
		return nameFirstWord;
	}

	/** Second word of a two-word spell name, else null (cached). */
	public String getNameSecondWord()
	{
		return nameSecondWord;
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
