package com.ll.engine;

import com.ll.data.*;
import static com.ll.engine.RangedAmmo.*;
import static com.ll.data.Slot.*;

// Dizana's quiver carries the arrows, so the ammo SLOT is free for a
// blessing - the blowpipe treatment (field report 2026-08-07: the
// quiver "frees up the ammo slot and should be treated more like a
// blowpipe"). Wiki rule: when the ammo slot holds a non-ammunition item,
// the quiver's stored ammunition fires, with its full ranged strength.
// <p>The beam still picks the arrows IN the slot (right ammo per weapon,
// right dps); fillDpsNeutralSlots then relocates them onto the loadout's
// quiverAmmo field and lets the neutral sweep fill the freed slot with a
// blessing. {@link #strength} keeps every recompute honest - re-show
// paths price the relocated set identically to the pre-relocation one.
public class QuiverAmmo {

// A Dizana's quiver in the cape slot (uncharged/charged/blessed -
// all variants hold ammunition), or Dizana's max cape, which keeps the
// blessed quiver's perks (wiki; ISS-01M5T53FBMHVMJTDPS5ZW4WS50).
static boolean wearsQuiver(Kit loadout) {
    Gear cape = loadout.get(CAPE);
    return cape != null && (cape.lname.contains("dizana's quiver") || cape.lname.contains("dizana's max cape"));
}

// The quiver only holds arrows and bolts - a ballista's javelins and
// a blowpipe's darts stay where they are.
static boolean relocatable(Kit loadout) {
    Gear ammo = loadout.get(AMMO);
    Gear weapon = loadout.getWeapon();
    return wearsQuiver(loadout) && ammo != null && weapon != null && usesArrowsOrBolts(weapon)
        && strengthApplies(ammo, weapon);
}

// The quiver's OWN conditional bonus (official model, verified
// 2026-08-21 across the Duke/Jad/Zuk tbow reports: a CHARGED or
// blessed quiver with applicable ammo grants +10 ranged accuracy
// and +1 ranged strength on top of everything - our chain counted
// only the stored arrows' stats and ran every quiver set low).
static boolean quiverBonusApplies(Kit loadout) {
    Gear cape = loadout.get(CAPE);
    if (cape == null) return false;
    String name = cape.lname;
    boolean charged = name.contains("blessed dizana's quiver") || name.contains("dizana's max cape")
        || (name.contains("dizana's quiver") && "charged".equals(cape.ver));
    if (!charged) return false;
    Gear weapon = loadout.getWeapon();
    Gear ammo = loadout.quiverAmmo != null ? loadout.quiverAmmo : loadout.get(AMMO);
    return weapon != null && ammo != null && usesArrowsOrBolts(weapon) && strengthApplies(ammo, weapon);
}

// The quiver-carried ammo's ranged strength - added beside
// BlowpipeDarts.strength in the max-hit chain, so the arrows count
// from the quiver exactly as they did from the slot.
static int strength(Kit loadout) {
    Gear carried = loadout.quiverAmmo;
    return (carried == null ? 0 : carried.bonuses.rangedStrength) + (quiverBonusApplies(loadout) ? 1 : 0);
}

// The quiver-carried ammo's ranged ACCURACY bonus - the quiver's
// extra slot is worn equipment, so its contents' full stats count
// (field report 2026-08-09: Seeking dragon arrows carry +20 ranged
// accuracy, and pricing only the strength made their relocation
// dps-negative, so the freed-slot blessing never happened).
static int accuracy(Kit loadout) {
    Gear carried = loadout.quiverAmmo;
    return (carried == null ? 0 : carried.offensive.ranged) + (quiverBonusApplies(loadout) ? 10 : 0);
}
}
