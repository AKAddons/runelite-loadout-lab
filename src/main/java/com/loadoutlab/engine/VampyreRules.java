package com.loadoutlab.engine;

import com.loadoutlab.data.JsonResources;
import java.util.ArrayList;
import java.util.List;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.MonsterStats;

/**
 * Vampyre tier weapon rules, verified against the OSRS Wiki 2026-07-05:
 * tier 1 ("vampyre1") takes full damage from everything; tier 2
 * ("vampyre2", juvinates) takes HALF damage from non-silver weapons; tier 3
 * ("vampyre3", vyrewatch/sentinels/ancient feral vyres) takes NO damage
 * except from a specific weapon set - so suggesting a whip vs a Vyrewatch
 * Sentinel is not a weaker answer, it is a wrong one.
 *
 * <p>Not modeled: silver bolts making a crossbow effective vs tier 2.
 */
public final class VampyreRules
{
	/** The only weapons that harm tier-3 vampyres (Vyrewatch Sentinel page). */
	private static final String[] TIER3_WEAPONS = weapons("tier3");

	/** Silver weaponry: full damage vs tier 2 (regular weapons deal half). */
	private static final String[] SILVER_WEAPONS = weapons("silver");

	private VampyreRules()
	{
	}

	/** False when this weapon cannot damage the monster at all (tier 3). */
	public static boolean canDamage(MonsterStats monster, GearItem weapon)
	{
		if (monster == null || !monster.hasAttribute("vampyre3"))
		{
			return true;
		}
		return matches(weapon, TIER3_WEAPONS);
	}

	/** Damage scale: 0.5 for non-silver weapons vs tier 2, otherwise 1. */
	public static double damageFactor(MonsterStats monster, GearItem weapon)
	{
		if (monster == null || !monster.hasAttribute("vampyre2") || matches(weapon, SILVER_WEAPONS))
		{
			return 1.0;
		}
		return 0.5;
	}

	private static String[] weapons(String key)
	{
		List<String> names = new ArrayList<>();
		JsonResources.strings(JsonResources.objectOrThrow("vampyre_weapons.json"), key, names);
		return names.toArray(new String[0]);
	}

	private static boolean matches(GearItem weapon, String[] prefixes)
	{
		if (weapon == null)
		{
			return false;
		}
		String name = weapon.getNameLower();
		for (String prefix : prefixes)
		{
			if (name.startsWith(prefix))
			{
				return true;
			}
		}
		return false;
	}
}
