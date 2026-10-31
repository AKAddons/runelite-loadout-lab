package com.loadoutlab.engine;

import com.loadoutlab.data.*;

// The Gauntlet locks fights to gear CRAFTED INSIDE the run (field spec
// 2026-07-18): mainland gear never enters, ownership is irrelevant, and
// the normal and corrupted versions each have their own item family.
// Detection keys off the bestiary's name prefixes - every Gauntlet mob
// is "Crystalline ..." and every Corrupted Gauntlet mob "Corrupted ...".
public class GauntletRules {

// "crystal" inside the Gauntlet, "corrupted" inside the Corrupted
// Gauntlet, null anywhere else.
static String family(Monster monster) {
    String name = monster.nameLower;
    if (name.startsWith("corrupted ")) return "corrupted";
    if (name.startsWith("crystalline ")) return "crystal";
    return null;
}

// Only the family's raid-crafted tiers exist inside.
static boolean allowed(String family, Gear item) {
    String name = item.nameLower;
    if (!name.startsWith(family + " ")) return false;
    return name.endsWith("(basic)") || name.endsWith("(attuned)") || name.endsWith("(perfected)");
}
}
