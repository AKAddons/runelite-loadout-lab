package com.loadoutlab.engine;

import com.loadoutlab.data.MonsterStats;
import java.util.Locale;
import java.util.Set;

/**
 * Raids SUPPLY their own boosts (field spec 2026-07-18): Chambers of
 * Xeric brews overload (+) potions inside and Tombs of Amascut hands
 * out smelling salts, so a fight inside assumes the raid's boost for
 * every style - never the bank's super combat / ranging / heart.
 * Membership is name-keyed against the curated rosters.
 */
public final class RaidBoosts
{
	private static final Set<String> XERIC = Set.of(
		"tekton",
		"vasa nistirio",
		"vespula",
		"abyssal portal",
		"muttadile",
		"vanguard",
		"skeletal mystic",
		"ice demon",
		"lizardman shaman (chambers of xeric)",
		"guardian (chambers of xeric)",
		"great olm",
		"deathly ranger",
		"deathly mage");

	private static final Set<String> AMASCUT = Set.of(
		"ba-ba",
		"kephri",
		"akkha",
		"akkha's shadow",
		"zebak",
		"obelisk (tombs of amascut)",
		"elidinis' warden",
		"tumeken's warden",
		"arcane scarab",
		"agile scarab",
		"scarab (tombs of amascut)");

	private RaidBoosts()
	{
	}

	/** The raid-supplied boost for a fight inside, or null outside. */
	public static BoostProfile suppliedBoost(MonsterStats monster)
	{
		String name = monster.getName().toLowerCase(Locale.ROOT);
		if (XERIC.contains(name))
		{
			return BoostProfile.OVERLOAD_PLUS;
		}
		if (AMASCUT.contains(name))
		{
			return BoostProfile.SMELLING_SALTS;
		}
		return null;
	}
}
