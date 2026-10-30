package com.loadoutlab.engine;

import com.loadoutlab.data.*;
import java.util.*;
import com.google.gson.JsonObject;
import static com.loadoutlab.data.JsonResources.*;

// Raids SUPPLY their own boosts (field spec 2026-07-18): Chambers of
// Xeric brews overload (+) potions inside and Tombs of Amascut hands
// out smelling salts, so a fight inside assumes the raid's boost for
// every style - never the bank's super combat / ranging / heart.
// Membership is name-keyed against the curated rosters.
public class RaidBoosts {
    // Rosters live in raid_boosts.json (hub token cap).
    static final Set<String> XERIC = new HashSet<>();
    static final Set<String> AMASCUT = new HashSet<>();
    static final Set<String> BLOOD = new HashSet<>();
    static final Set<String> ARCHIVE = new HashSet<>();

    static {
        JsonObject root = object("raid_boosts.json");
        strings(root, "xeric", XERIC);
        strings(root, "amascut", AMASCUT);
        strings(root, "blood", BLOOD);
        strings(root, "archive", ARCHIVE);
    }


    // Which raid this monster belongs to ("cox", "toa", "tob") or null:
    // raid monsters live nowhere else, so a single search keys the raid's
    // compute animation (Andrew 2026-09-02).
    public static String raidKey(MonsterStats monster) {
        String name = monster.nameLower;
        return XERIC.contains(name) ? "cox" : AMASCUT.contains(name) ? "toa"
            : BLOOD.contains(name) ? "tob" : ARCHIVE.contains(name) ? "tfa" : null;
    }

    // The raid-supplied boost for a fight inside, or null outside.
    public static BoostProfile suppliedBoost(MonsterStats monster) {
        String name = monster.nameLower;
        if (XERIC.contains(name)) return BoostProfile.OVERLOAD_PLUS;
        if (AMASCUT.contains(name)) return BoostProfile.SMELLING_SALTS;
        return null;
    }
}
