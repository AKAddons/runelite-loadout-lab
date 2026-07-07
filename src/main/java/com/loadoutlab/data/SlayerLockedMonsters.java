package com.loadoutlab.data;

import java.util.Locale;
import java.util.Set;

/**
 * Slayer bosses that can ONLY be fought while on the corresponding
 * slayer task (wiki-verified 2026-07-07: "may only be killed while on
 * a ... Slayer task") - for these the on-task toggle is forced on, the
 * opposite of unassignable monsters where it is forced off.
 */
public final class SlayerLockedMonsters
{
	private static final Set<String> NAMES = Set.of(
		"kraken",
		"cave kraken",
		"cerberus",
		"abyssal sire",
		"thermonuclear smoke devil",
		"alchemical hydra",
		"araxxor",
		"dusk",
		"dawn");

	private SlayerLockedMonsters()
	{
	}

	public static boolean isTaskOnly(MonsterStats monster)
	{
		return monster != null && NAMES.contains(monster.getName().toLowerCase(Locale.ROOT));
	}
}
