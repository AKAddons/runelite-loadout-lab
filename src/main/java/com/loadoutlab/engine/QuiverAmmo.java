package com.loadoutlab.engine;

import com.loadoutlab.data.*;
import static com.loadoutlab.engine.RangedAmmo.*;
import static com.loadoutlab.data.GearSlot.*;

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
public class QuiverAmmo {

    /** A Dizana's quiver in the cape slot (uncharged/charged/blessed -
     * all variants hold ammunition), or Dizana's max cape, which keeps the
     * blessed quiver's perks (wiki; ISS-01M5T53FBMHVMJTDPS5ZW4WS50). */
    public static boolean wearsQuiver(Loadout loadout) {
        GearItem cape = loadout.get(CAPE);
        return cape != null && (cape.nameLower.contains("dizana's quiver") || cape.nameLower.contains("dizana's max cape"));
    }

    /** The quiver only holds arrows and bolts - a ballista's javelins and
     * a blowpipe's darts stay where they are. */
    public static boolean relocatable(Loadout loadout) {
        GearItem ammo = loadout.get(AMMO);
        GearItem weapon = loadout.getWeapon();
        return wearsQuiver(loadout) && ammo != null && weapon != null && usesArrowsOrBolts(weapon)
            && strengthApplies(ammo, weapon);
    }

    /** The quiver's OWN conditional bonus (official model, verified
     * 2026-08-21 across the Duke/Jad/Zuk tbow reports: a CHARGED or
     * blessed quiver with applicable ammo grants +10 ranged accuracy
     * and +1 ranged strength on top of everything - our chain counted
     * only the stored arrows' stats and ran every quiver set low). */
    static boolean quiverBonusApplies(Loadout loadout) {
        GearItem cape = loadout.get(CAPE);
        if (cape == null) return false;
        String name = cape.nameLower;
        boolean charged = name.contains("blessed dizana's quiver") || name.contains("dizana's max cape")
            || (name.contains("dizana's quiver") && "charged".equals(cape.versionLower));
        if (!charged) return false;
        GearItem weapon = loadout.getWeapon();
        GearItem ammo = loadout.quiverAmmo != null ? loadout.quiverAmmo : loadout.get(AMMO);
        return weapon != null && ammo != null && usesArrowsOrBolts(weapon) && strengthApplies(ammo, weapon);
    }

    /** The quiver-carried ammo's ranged strength - added beside
     * BlowpipeDarts.strength in the max-hit chain, so the arrows count
     * from the quiver exactly as they did from the slot. */
    public static int strength(Loadout loadout) {
        GearItem carried = loadout.quiverAmmo;
        return (carried == null ? 0 : carried.bonuses.rangedStrength) + (quiverBonusApplies(loadout) ? 1 : 0);
    }

    /** The quiver-carried ammo's ranged ACCURACY bonus - the quiver's
     * extra slot is worn equipment, so its contents' full stats count
     * (field report 2026-08-09: Seeking dragon arrows carry +20 ranged
     * accuracy, and pricing only the strength made their relocation
     * dps-negative, so the freed-slot blessing never happened). */
    public static int accuracy(Loadout loadout) {
        GearItem carried = loadout.quiverAmmo;
        return (carried == null ? 0 : carried.offensive.ranged) + (quiverBonusApplies(loadout) ? 10 : 0);
    }
}
