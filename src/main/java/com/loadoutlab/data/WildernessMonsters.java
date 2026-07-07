package com.loadoutlab.data;

import java.util.Locale;
import java.util.Set;

/**
 * Monsters fought in the Wilderness, where dying to a PKer risks your
 * gear. Verified vs the wiki (Items Kept on Death, 2026-07-07): death
 * keeps your 3 highest-GE/alch-value items (4 with Protect Item; 0/1
 * skulled); since the June 2026 rework most untradeables are kept in
 * the Wilderness, with combat-capable ones returning broken/mangled
 * for a coin repair above level 20. A low-risk set therefore wears NO
 * MORE tradeables than the kept count - value ranking then cannot
 * drop any of them - and untradeables everywhere else.
 * Curated list: the boss ring, escape-cave counterparts, KBD, and the
 * Revenant Caves.
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
