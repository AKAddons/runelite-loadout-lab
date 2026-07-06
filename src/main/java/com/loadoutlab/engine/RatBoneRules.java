package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.MonsterStats;

/**
 * Rat bone weapons (bone mace / bone shortbow / bone staff, from Scurrius)
 * - wiki-verified 2026-07-05: they CANNOT be used against anything that is
 * not a rat ("You can only use this weapon against rats."), and against
 * rats they add a flat +10 to the max hit. The bone staff's powered-staff
 * formula already includes the +10 (its wiki max-hit table: 38 at 99
 * Magic = trident-of-the-seas scaling + 10), so only the mace and
 * shortbow take the flat bonus here.
 */
public final class RatBoneRules
{
	private RatBoneRules()
	{
	}

	/** False when this weapon literally cannot be swung at this monster. */
	public static boolean canUse(MonsterStats monster, GearItem weapon)
	{
		return !isRatBone(weapon) || isRat(monster);
	}

	/** Flat max-hit bonus vs rats for the non-staff rat bone weapons. */
	public static int flatMaxHitBonus(MonsterStats monster, GearItem weapon)
	{
		if (weapon == null || !isRat(monster))
		{
			return 0;
		}
		String name = weapon.getName().toLowerCase();
		return name.startsWith("bone mace") || name.startsWith("bone shortbow") ? 10 : 0;
	}

	private static boolean isRatBone(GearItem weapon)
	{
		if (weapon == null)
		{
			return false;
		}
		String name = weapon.getName().toLowerCase();
		return name.startsWith("bone mace")
			|| name.startsWith("bone shortbow")
			|| name.startsWith("bone staff");
	}

	private static boolean isRat(MonsterStats monster)
	{
		return monster != null && monster.hasAttribute("rat");
	}
}
