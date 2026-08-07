package com.loadoutlab.optimizer;

import com.loadoutlab.engine.BoostProfile;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OwnedItems;

/**
 * The best stat boost assumed per style. Tradeable potions are ALWAYS
 * assumed (cheap consumables - like prayers, you bring them); the hearts
 * (untradeable drops) and the pricier divine variants gate on ownership.
 * Raid-scoped boosts (overloads, smelling salts) are deliberately not
 * auto-assumed.
 */
public final class BoostSelector
{
	private static final int SATURATED_HEART = 27641;
	private static final int IMBUED_HEART = 20724;
	/** All dose variants - owning any counts (GE-verified ids 2026-07-21). */
	private static final int[] DIVINE_SUPER_COMBAT = {23685, 23688, 23691, 23694};
	private static final int[] DIVINE_RANGING = {23733, 23736, 23739, 23742};
	private static final int[] DIVINE_MAGIC = {23745, 23748, 23751, 23754};

	private BoostSelector()
	{
	}

	/** The best boost in the GAME per style - the BiS ceiling assumption.
	 * F2P mode: members consumables do not exist on a free world - the
	 * only boosts are the attack/strength potion pair (audit A3.5). */
	public static BoostProfile ceilingFor(CombatStyle style, boolean f2p)
	{
		if (f2p)
		{
			return style == CombatStyle.MELEE ? BoostProfile.F2P_COMBAT : BoostProfile.NONE;
		}
		switch (style)
		{
			// Divine variants hold the boost at ceiling for the whole fight
			// - same numbers, better assumption (field ask 2026-07-21).
			case MELEE: return BoostProfile.DIVINE_SUPER_COMBAT;
			case RANGED: return BoostProfile.DIVINE_RANGING;
			case MAGIC: return BoostProfile.SATURATED_HEART;
			default: return BoostProfile.NONE;
		}
	}

	/** The best boost you can actually assume per style, given what you own.
	 * Risk-capped wilderness searches never assume a heart (field spec
	 * 2026-07-18): both hearts are tradeable and worth far more than any
	 * sane risk cap, so the assumption falls back to the magic potion. */
	public static BoostProfile bestFor(CombatStyle style, OwnedItems owned, boolean f2p,
		boolean noHearts)
	{
		if (f2p)
		{
			return style == CombatStyle.MELEE ? BoostProfile.F2P_COMBAT : BoostProfile.NONE;
		}
		switch (style)
		{
			case MELEE:
				return ownsAny(owned, DIVINE_SUPER_COMBAT)
					? BoostProfile.DIVINE_SUPER_COMBAT : BoostProfile.SUPER_COMBAT;
			case RANGED:
				return ownsAny(owned, DIVINE_RANGING)
					? BoostProfile.DIVINE_RANGING : BoostProfile.RANGING;
			case MAGIC:
				if (!noHearts && owned.owns(SATURATED_HEART))
				{
					return BoostProfile.SATURATED_HEART;
				}
				if (!noHearts && owned.owns(IMBUED_HEART))
				{
					return BoostProfile.IMBUED_HEART;
				}
				return ownsAny(owned, DIVINE_MAGIC)
					? BoostProfile.DIVINE_MAGIC : BoostProfile.MAGIC;
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
