package com.loadoutlab.optimizer;

import com.loadoutlab.engine.BoostProfile;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OwnedItems;

/**
 * The best stat boost the player actually OWNS, per style - assumed like
 * prayers are assumed (you drink what you bring). Ids are gameval-verified;
 * all dose sizes count. Raid-scoped boosts (overloads, smelling salts) are
 * deliberately not auto-assumed.
 */
public final class BoostSelector
{
	private static final int[] SUPER_COMBAT = {12695, 12697, 12699, 12701, 23685, 23688, 23691, 23694};
	private static final int[] RANGING = {2444, 169, 171, 173, 23733, 23736, 23739, 23742,
		22461, 22464, 22467, 22470, 24635, 24638, 24641, 24644};
	private static final int SATURATED_HEART = 27641;
	private static final int IMBUED_HEART = 20724;
	private static final int[] MAGIC_POTION = {3040, 3042, 3044, 3046, 23745, 23748, 23751, 23754};

	private BoostSelector()
	{
	}

	public static BoostProfile bestFor(CombatStyle style, OwnedItems owned)
	{
		switch (style)
		{
			case MELEE:
				return ownsAny(owned, SUPER_COMBAT) ? BoostProfile.SUPER_COMBAT : BoostProfile.NONE;
			case RANGED:
				return ownsAny(owned, RANGING) ? BoostProfile.RANGING : BoostProfile.NONE;
			case MAGIC:
				if (owned.owns(SATURATED_HEART))
				{
					return BoostProfile.SATURATED_HEART;
				}
				if (owned.owns(IMBUED_HEART))
				{
					return BoostProfile.IMBUED_HEART;
				}
				return ownsAny(owned, MAGIC_POTION) ? BoostProfile.MAGIC : BoostProfile.NONE;
			default:
				return BoostProfile.NONE;
		}
	}

	private static boolean ownsAny(OwnedItems owned, int[] ids)
	{
		for (int id : ids)
		{
			if (owned.owns(id))
			{
				return true;
			}
		}
		return false;
	}
}
