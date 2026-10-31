package com.loadoutlab.engine;

import com.loadoutlab.data.*;

// Flying monsters (Kree'arra and the aviansies, Dawn, Vespula...) cannot be
// reached by melee except with halberds or salamanders - wiki-verified
// 2026-07-05 (Kree'arra page; halberds allowed since the 25 June 2025 game
// update). The restriction covers melee special attacks too (e.g. the
// Voidwaker's magic-damage spec still cannot be used).
// <p>Ranged and magic are unaffected.
public class FlyingRules {

// False when a melee weapon cannot reach this monster.
static boolean canReach(Monster monster, Style style, Gear weapon) {
    if (style != Style.MELEE || monster == null || !monster.hasAttribute("flying")) return true;
    if (weapon == null) return false; // fists reach even less far than swords
    String category = weapon.category == null ? "" : weapon.category;
    return "Polearm".equals(category) || "Salamander".equals(category);
}
}
