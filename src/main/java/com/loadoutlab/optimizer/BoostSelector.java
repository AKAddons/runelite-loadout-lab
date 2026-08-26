package com.loadoutlab.optimizer;

import com.loadoutlab.engine.BoostProfile;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OwnedItems;

/**
 * The best stat boost assumed per style, given what the player actually owns.
 *
 * <p>Field report 2026-08-24 (a fresh ironman): the card claimed "Assumes:
 * Ranging potion" for an account with no ranging potion in the bank. The old
 * rule was "tradeable potions are ALWAYS assumed - cheap consumables, like
 * prayers you just bring them", which holds for a main with a GE but is simply
 * false for an ironman, and contradicts the panel's own "detect best in bank"
 * setting. EVERY boost now gates on ownership and the ladder falls through to
 * NONE.
 *
 * <p>Raid-scoped boosts (overloads, smelling salts) remain deliberately
 * un-assumed. Ids verified against net.runelite.api.ItemID 2026-08-25; the
 * divine arrays below match its DIVINE_* constants exactly.
 */
public final class BoostSelector
{
	private static final int SATURATED_HEART = 27641;
	private static final int IMBUED_HEART = 20724;
	/** All dose variants - owning any counts (GE-verified ids 2026-07-21). */
	private static final int[] DIVINE_SUPER_COMBAT = {23685, 23688, 23691, 23694};
	private static final int[] DIVINE_RANGING = {23733, 23736, 23739, 23742};
	private static final int[] DIVINE_MAGIC = {23745, 23748, 23751, 23754};
	/** Base (non-divine) potions - these used to be assumed unconditionally. */
	private static final int[] SUPER_COMBAT = {12695, 12697, 12699, 12701};
	private static final int[] RANGING = {2444, 169, 171, 173};
	private static final int[] MAGIC = {3040, 3042, 3044, 3046};
	/** Bastion and battlemage carry the SAME offensive boost as the ranging /
	 * magic potions plus a super-defence half, so owning one means you bring
	 * it - they rank above the plain family, divine or not. Their own divine
	 * variants share those numbers exactly, so both id sets map to the one
	 * profile rather than inventing a duplicate constant. */
	private static final int[] BASTION = {22461, 22464, 22467, 22470};
	private static final int[] DIVINE_BASTION = {24635, 24638, 24641, 24644};
	private static final int[] BATTLEMAGE = {22449, 22452, 22455, 22458};
	private static final int[] DIVINE_BATTLEMAGE = {24623, 24626, 24629, 24632};

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
		// Each ladder walks best -> worst and ends at NONE: an unowned boost is
		// never assumed, so a low-level account sees the numbers it can
		// actually reach today.
		switch (style)
		{
			case MELEE:
				if (ownsAny(owned, DIVINE_SUPER_COMBAT))
				{
					return BoostProfile.DIVINE_SUPER_COMBAT;
				}
				return ownsAny(owned, SUPER_COMBAT)
					? BoostProfile.SUPER_COMBAT : BoostProfile.NONE;
			case RANGED:
				if (ownsAny(owned, DIVINE_BASTION))
				{
					return BoostProfile.DIVINE_BASTION;
				}
				if (ownsAny(owned, BASTION))
				{
					return BoostProfile.BASTION;
				}
				if (ownsAny(owned, DIVINE_RANGING))
				{
					return BoostProfile.DIVINE_RANGING;
				}
				return ownsAny(owned, RANGING)
					? BoostProfile.RANGING : BoostProfile.NONE;
			case MAGIC:
				if (!noHearts && owned.owns(SATURATED_HEART))
				{
					return BoostProfile.SATURATED_HEART;
				}
				if (!noHearts && owned.owns(IMBUED_HEART))
				{
					return BoostProfile.IMBUED_HEART;
				}
				if (ownsAny(owned, DIVINE_BATTLEMAGE))
				{
					return BoostProfile.DIVINE_BATTLEMAGE;
				}
				if (ownsAny(owned, BATTLEMAGE))
				{
					return BoostProfile.BATTLEMAGE;
				}
				if (ownsAny(owned, DIVINE_MAGIC))
				{
					return BoostProfile.DIVINE_MAGIC;
				}
				return ownsAny(owned, MAGIC)
					? BoostProfile.MAGIC : BoostProfile.NONE;
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
