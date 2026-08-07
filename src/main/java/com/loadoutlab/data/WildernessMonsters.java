package com.loadoutlab.data;

import java.util.Set;
import java.util.HashSet;

/**
 * Monsters fought in the Wilderness, where dying to a PKer risks your
 * gear. Verified vs the wiki (Items Kept on Death, 2026-07-07): death
 * keeps your 3 highest-GE/alch-value items (4 with Protect Item; 0/1
 * skulled); since the June 2026 rework most untradeables are kept in
 * the Wilderness, with combat-capable ones returning broken/mangled
 * for a coin repair above level 20. A low-risk set therefore wears NO
 * MORE tradeables than the kept count - value ranking then cannot
 * drop any of them - and untradeables everywhere else.
 * Curated list: the boss ring, escape-cave counterparts, KBD, the
 * Revenant Caves, and the non-boss monsters players actually fight in
 * the Wilderness (Slayer Cave dwellers, dragons, Krystilia-task and
 * moneymaker mobs) - many also exist elsewhere, but the low-risk
 * toggles default off, so offering them costs nothing outside.
 */
public final class WildernessMonsters
{
	/** Curated lists live in wilderness_monsters.json (hub token cap). */
	private static final Set<String> NAMES = new HashSet<>();
	/**
	 * The subset that exists ONLY in the Wilderness - fighting one of these
	 * IS being in the Wilderness, so the wilderness-weapon buff and the risk
	 * UI apply unconditionally. Everything else on the list also spawns
	 * elsewhere (Catacombs hellhounds, Taverley black dragons...), so those
	 * take the user's "In the Wilderness" toggle instead (audit A3.1).
	 * KBD note: the lair is reached through level-42 Wilderness; it stays
	 * exclusive so the risk UI covers the walk, matching the pre-toggle
	 * behaviour.
	 */
	private static final Set<String> EXCLUSIVE = new HashSet<>();

	static
	{
		com.google.gson.JsonObject root = JsonResources.object(
			"/com/loadoutlab/data/wilderness_monsters.json");
		JsonResources.strings(root, "names", NAMES);
		JsonResources.strings(root, "exclusive", EXCLUSIVE);
	}

	private WildernessMonsters()
	{
	}

	public static boolean isWilderness(MonsterStats monster)
	{
		return monster != null && monster.isWildernessMonster();
	}

	/** True when the monster exists nowhere but the Wilderness. */
	public static boolean isExclusive(MonsterStats monster)
	{
		return monster != null && EXCLUSIVE.contains(monster.getNameLower());
	}

	/** The raw name-list membership - MonsterStats caches this at build. */
	static boolean containsName(String nameLower)
	{
		return NAMES.contains(nameLower);
	}
}
