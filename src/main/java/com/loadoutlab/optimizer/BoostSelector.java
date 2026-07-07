package com.loadoutlab.optimizer;

import com.loadoutlab.engine.BoostProfile;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OwnedItems;

/**
 * The best stat boost assumed per style. Tradeable potions are ALWAYS
 * assumed (cheap consumables - like prayers, you bring them); only the
 * hearts (untradeable drops) gate on ownership. Raid-scoped boosts
 * (overloads, smelling salts) are deliberately not auto-assumed.
 */
public final class BoostSelector
{
	private static final int SATURATED_HEART = 27641;
	private static final int IMBUED_HEART = 20724;

	private BoostSelector()
	{
	}

	public static BoostProfile bestFor(CombatStyle style, OwnedItems owned)
	{
		switch (style)
		{
			case MELEE:
				return BoostProfile.SUPER_COMBAT;
			case RANGED:
				return BoostProfile.RANGING;
			case MAGIC:
				if (owned.owns(SATURATED_HEART))
				{
					return BoostProfile.SATURATED_HEART;
				}
				if (owned.owns(IMBUED_HEART))
				{
					return BoostProfile.IMBUED_HEART;
				}
				return BoostProfile.MAGIC;
			default:
				return BoostProfile.NONE;
		}
	}
}
