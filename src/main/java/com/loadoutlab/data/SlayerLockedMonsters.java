package com.loadoutlab.data;

import java.util.HashSet;
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
	private static final Set<String> NAMES = new HashSet<>();

	static
	{
		JsonResources.strings(JsonResources.objectOrThrow("slayer_locked_monsters.json"), "names", NAMES);
	}

	private SlayerLockedMonsters()
	{
	}

	public static boolean isTaskOnly(MonsterStats monster)
	{
		return monster != null && NAMES.contains(monster.getName().toLowerCase(Locale.ROOT));
	}
}
