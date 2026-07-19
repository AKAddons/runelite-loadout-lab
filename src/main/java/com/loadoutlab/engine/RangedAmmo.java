// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.JsonResources;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Weapon/ammo compatibility. The id tables (arrow/bolt tiers, javelins,
 * fixed weapon-ammo pairs) live in ranged_ammo.json - hub token cap. */
public final class RangedAmmo
{
	private static final int DORGESHUUN_CROSSBOW = 8880;
	private static final Map<Integer, Integer> ARROW_TIER_BY_WEAPON = new HashMap<>();
	private static final Map<Integer, Integer> BOLT_TIER_BY_WEAPON = new HashMap<>();
	private static final Map<Integer, Integer> ARROW_TIER_BY_AMMO = new HashMap<>();
	private static final Map<Integer, Integer> BOLT_TIER_BY_AMMO = new HashMap<>();
	private static final Set<Integer> JAVELIN_AMMO = new HashSet<>();
	private static final Set<Integer> JAVELIN_WEAPONS = new HashSet<>();
	private static final Map<Integer, Set<Integer>> PAIRS = new HashMap<>();
	private static final Set<Integer> DORGESHUUN_EXTRA_BOLTS = new HashSet<>();

	static
	{
		JsonObject root = JsonResources.object("/com/loadoutlab/data/ranged_ammo.json");
		JsonResources.intMap(root, "arrowTierByWeapon", ARROW_TIER_BY_WEAPON);
		JsonResources.intMap(root, "boltTierByWeapon", BOLT_TIER_BY_WEAPON);
		JsonResources.intMap(root, "arrowTierByAmmo", ARROW_TIER_BY_AMMO);
		JsonResources.intMap(root, "boltTierByAmmo", BOLT_TIER_BY_AMMO);
		JsonResources.ints(root, "javelinAmmo", JAVELIN_AMMO);
		JsonResources.ints(root, "javelinWeapons", JAVELIN_WEAPONS);
		JsonResources.ints(root, "dorgeshuunExtraBolts", DORGESHUUN_EXTRA_BOLTS);
		if (root != null)
		{
			for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("pairs").entrySet())
			{
				Set<Integer> ammo = new HashSet<>();
				for (JsonElement a : e.getValue().getAsJsonArray())
				{
					ammo.add(a.getAsInt());
				}
				PAIRS.put(Integer.parseInt(e.getKey()), ammo);
			}
		}
	}

	private RangedAmmo()
	{
	}

	public static boolean compatible(GearItem ammo, GearItem weapon)
	{
		if (ammo == null)
		{
			return !weaponUsesProjectileAmmo(weapon);
		}
		if (projectileMatches(ammo, weapon))
		{
			return true;
		}
		return !weaponUsesProjectileAmmo(weapon) && passiveAmmoSlotItem(ammo);
	}

	static boolean strengthApplies(GearItem ammo, GearItem weapon)
	{
		return ammo != null && projectileMatches(ammo, weapon);
	}

	private static boolean weaponUsesProjectileAmmo(GearItem weapon)
	{
		if (weapon == null)
		{
			return false;
		}
		int weaponId = weapon.getId();
		return ARROW_TIER_BY_WEAPON.containsKey(weaponId)
			|| BOLT_TIER_BY_WEAPON.containsKey(weaponId)
			|| weaponId == DORGESHUUN_CROSSBOW
			|| JAVELIN_WEAPONS.contains(weaponId)
			|| PAIRS.containsKey(weaponId);
	}

	private static boolean projectileMatches(GearItem ammo, GearItem weapon)
	{
		if (ammo == null || weapon == null)
		{
			return false;
		}
		int ammoId = ammo.getId();
		int weaponId = weapon.getId();
		if (weaponId == DORGESHUUN_CROSSBOW)
		{
			int tier = BOLT_TIER_BY_AMMO.getOrDefault(ammoId, 0);
			return (tier > 0 && tier <= 16) || DORGESHUUN_EXTRA_BOLTS.contains(ammoId);
		}
		Set<Integer> pair = PAIRS.get(weaponId);
		if (pair != null)
		{
			return pair.contains(ammoId);
		}
		if (JAVELIN_WEAPONS.contains(weaponId))
		{
			return JAVELIN_AMMO.contains(ammoId);
		}
		int arrowCap = ARROW_TIER_BY_WEAPON.getOrDefault(weaponId, 0);
		if (arrowCap > 0)
		{
			int tier = ARROW_TIER_BY_AMMO.getOrDefault(ammoId, 0);
			return tier > 0 && tier <= arrowCap;
		}
		int boltCap = BOLT_TIER_BY_WEAPON.getOrDefault(weaponId, 0);
		if (boltCap > 0)
		{
			int tier = BOLT_TIER_BY_AMMO.getOrDefault(ammoId, 0);
			return tier > 0 && tier <= boltCap;
		}
		return false;
	}

	private static boolean passiveAmmoSlotItem(GearItem ammo)
	{
		return ammo.getBonuses().getRangedStrength() <= 0 && ammo.getOffensive().getRanged() <= 0;
	}
}
