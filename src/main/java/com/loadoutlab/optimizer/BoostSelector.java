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

	/** The best boost in the GAME per style - the BiS ceiling assumption. */
	public static BoostProfile ceilingFor(CombatStyle style)
	{
		return ceilingFor(style, false);
	}

	/** F2P mode: members consumables do not exist on a free world - the
	 * only boosts are the attack/strength potion pair (audit A3.5). */
	public static BoostProfile ceilingFor(CombatStyle style, boolean f2p)
	{
		if (f2p)
		{
			return style == CombatStyle.MELEE ? BoostProfile.F2P_COMBAT : BoostProfile.NONE;
		}
		switch (style)
		{
			case MELEE: return BoostProfile.SUPER_COMBAT;
			case RANGED: return BoostProfile.RANGING;
			case MAGIC: return BoostProfile.SATURATED_HEART;
			default: return BoostProfile.NONE;
		}
	}

	public static BoostProfile bestFor(CombatStyle style, OwnedItems owned)
	{
		return bestFor(style, owned, false);
	}

	public static BoostProfile bestFor(CombatStyle style, OwnedItems owned, boolean f2p)
	{
		if (f2p)
		{
			return style == CombatStyle.MELEE ? BoostProfile.F2P_COMBAT : BoostProfile.NONE;
		}
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
