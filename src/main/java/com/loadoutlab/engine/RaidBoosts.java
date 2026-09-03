package com.loadoutlab.engine;

import com.loadoutlab.data.JsonResources;
import com.loadoutlab.data.MonsterStats;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;

/**
 * Raids SUPPLY their own boosts (field spec 2026-07-18): Chambers of
 * Xeric brews overload (+) potions inside and Tombs of Amascut hands
 * out smelling salts, so a fight inside assumes the raid's boost for
 * every style - never the bank's super combat / ranging / heart.
 * Membership is name-keyed against the curated rosters.
 */
public final class RaidBoosts
{
	/** Rosters live in raid_boosts.json (hub token cap). */
	private static final Set<String> XERIC = new HashSet<>();
	private static final Set<String> AMASCUT = new HashSet<>();
	private static final Set<String> BLOOD = new HashSet<>();

	static
	{
		com.google.gson.JsonObject root = JsonResources.object("/com/loadoutlab/data/raid_boosts.json");
		JsonResources.strings(root, "xeric", XERIC);
		JsonResources.strings(root, "amascut", AMASCUT);
		JsonResources.strings(root, "blood", BLOOD);
	}

	private RaidBoosts()
	{
	}

	/** Which raid this monster belongs to ("cox", "toa", "tob") or null:
	 * raid monsters live nowhere else, so a single search keys the raid's
	 * compute animation (Andrew 2026-09-02). */
	public static String raidKey(MonsterStats monster)
	{
		String name = monster.getName().toLowerCase(Locale.ROOT);
		return XERIC.contains(name) ? "cox" : AMASCUT.contains(name) ? "toa"
			: BLOOD.contains(name) ? "tob" : null;
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
