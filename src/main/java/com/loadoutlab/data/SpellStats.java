// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

import lombok.Getter;

public final class SpellStats
{
	@Getter
	private final String name;
	@Getter
	private final int maxHit;
	@Getter
	private final int magicLevel;
	@Getter
	private final String spellbook;
	@Getter
	private final String element;
	@Getter
	private final String nameFirstWord;
	@Getter
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

	/** Second word of a two-word spell name, else null (cached). */






	@Override
	public String toString()
	{
		return name;
	}
}
