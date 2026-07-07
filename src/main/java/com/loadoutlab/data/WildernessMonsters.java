package com.loadoutlab.data;

import java.util.Locale;
import java.util.Set;

/**
 * Monsters fought in the Wilderness, where dying to a PKer risks your
 * gear: you keep your 3 most valuable items (4 with Protect Item, fewer
 * if skulled) and untradeables are kept in a repairable state - so a
 * low-risk set is untradeables everywhere plus at most 3-4 tradeables.
 * Curated list (wiki-verified 2026-07-07): the boss ring, their escape
 * cave counterparts, and the Revenant Caves.
 */
public final class WildernessMonsters
{
	private static final Set<String> NAMES = Set.of(
		"callisto",
		"artio",
		"vet'ion",
		"calvar'ion",
		"venenatis",
		"spindel",
		"chaos elemental",
		"chaos fanatic",
		"crazy archaeologist",
		"scorpia",
		"king black dragon",
		"revenant imp",
		"revenant goblin",
		"revenant pyrefiend",
		"revenant hobgoblin",
		"revenant cyclops",
		"revenant hellhound",
		"revenant demon",
		"revenant ork",
		"revenant dark beast",
		"revenant knight",
		"revenant dragon",
		"revenant maledictus");

	private WildernessMonsters()
	{
	}

	public static boolean isWilderness(MonsterStats monster)
	{
		return monster != null && NAMES.contains(monster.getName().toLowerCase(Locale.ROOT));
	}
}
