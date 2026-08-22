package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;

/**
 * Dizana's quiver carries the arrows, so the ammo SLOT is free for a
 * blessing - the blowpipe treatment (field report 2026-08-07: the
 * quiver "frees up the ammo slot and should be treated more like a
 * blowpipe"). Wiki rule: when the ammo slot holds a non-ammunition item,
 * the quiver's stored ammunition fires, with its full ranged strength.
 *
 * <p>The beam still picks the arrows IN the slot (right ammo per weapon,
 * right dps); fillDpsNeutralSlots then relocates them onto the loadout's
 * quiverAmmo field and lets the neutral sweep fill the freed slot with a
 * blessing. {@link #strength} keeps every recompute honest - re-show
 * paths price the relocated set identically to the pre-relocation one.
 */
public final class QuiverAmmo
{
	private QuiverAmmo()
	{
	}

	/** A Dizana's quiver in the cape slot (uncharged/charged/blessed -
	 * all variants hold ammunition; the max-cape build is out of corpus). */
	public static boolean wearsQuiver(Loadout loadout)
	{
		GearItem cape = loadout.get(GearSlot.CAPE);
		return cape != null && cape.getNameLower().contains("dizana's quiver");
	}

	/** The quiver only holds arrows and bolts - a ballista's javelins and
	 * a blowpipe's darts stay where they are. */
	public static boolean relocatable(Loadout loadout)
	{
		GearItem ammo = loadout.get(GearSlot.AMMO);
		GearItem weapon = loadout.getWeapon();
		return wearsQuiver(loadout) && ammo != null && weapon != null
			&& RangedAmmo.usesArrowsOrBolts(weapon)
			&& RangedAmmo.strengthApplies(ammo, weapon);
	}

	/** The quiver's OWN conditional bonus (official model, verified
	 * 2026-08-21 across the Duke/Jad/Zuk tbow reports: a CHARGED or
	 * blessed quiver with applicable ammo grants +10 ranged accuracy
	 * and +1 ranged strength on top of everything - our chain counted
	 * only the stored arrows' stats and ran every quiver set low). */
	private static boolean quiverBonusApplies(Loadout loadout)
	{
		GearItem cape = loadout.get(GearSlot.CAPE);
		if (cape == null)
		{
			return false;
		}
		String name = cape.getNameLower();
		boolean charged = name.contains("blessed dizana's quiver")
			|| name.contains("dizana's max cape")
			|| (name.contains("dizana's quiver")
				&& "charged".equalsIgnoreCase(cape.getVersion()));
		if (!charged)
		{
			return false;
		}
		GearItem weapon = loadout.getWeapon();
		GearItem ammo = loadout.getQuiverAmmo() != null
			? loadout.getQuiverAmmo() : loadout.get(GearSlot.AMMO);
		return weapon != null && ammo != null
			&& RangedAmmo.usesArrowsOrBolts(weapon)
			&& RangedAmmo.strengthApplies(ammo, weapon);
	}

	/** The quiver-carried ammo's ranged strength - added beside
	 * BlowpipeDarts.strength in the max-hit chain, so the arrows count
	 * from the quiver exactly as they did from the slot. */
	public static int strength(Loadout loadout)
	{
		GearItem carried = loadout.getQuiverAmmo();
		return (carried == null ? 0 : carried.getBonuses().getRangedStrength())
			+ (quiverBonusApplies(loadout) ? 1 : 0);
	}

	/** The quiver-carried ammo's ranged ACCURACY bonus - the quiver's
	 * extra slot is worn equipment, so its contents' full stats count
	 * (field report 2026-08-09: Seeking dragon arrows carry +20 ranged
	 * accuracy, and pricing only the strength made their relocation
	 * dps-negative, so the freed-slot blessing never happened). */
	public static int accuracy(Loadout loadout)
	{
		GearItem carried = loadout.getQuiverAmmo();
		return (carried == null ? 0 : carried.getOffensive().getRanged())
			+ (quiverBonusApplies(loadout) ? 10 : 0);
	}
}
