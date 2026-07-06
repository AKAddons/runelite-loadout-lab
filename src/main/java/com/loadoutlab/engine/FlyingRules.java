package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.MonsterStats;

/**
 * Flying monsters (Kree'arra and the aviansies, Dawn, Vespula...) cannot be
 * reached by melee except with halberds or salamanders - wiki-verified
 * 2026-07-05 (Kree'arra page; halberds allowed since the 25 June 2025 game
 * update). The restriction covers melee special attacks too (e.g. the
 * Voidwaker's magic-damage spec still cannot be used).
 *
 * <p>Ranged and magic are unaffected.
 */
public final class FlyingRules
{
	private FlyingRules()
	{
	}

	/** False when a melee weapon cannot reach this monster. */
	public static boolean canReach(MonsterStats monster, CombatStyle style, GearItem weapon)
	{
		if (style != CombatStyle.MELEE || monster == null || !monster.hasAttribute("flying"))
		{
			return true;
		}
		if (weapon == null)
		{
			return false; // fists reach even less far than swords
		}
		String category = weapon.getCategory() == null ? "" : weapon.getCategory();
		return "Polearm".equals(category) || "Salamander".equals(category);
	}
}
