package com.loadoutlab.data;

import com.google.gson.*;
import java.util.*;

// Per-boss defence floors for drain valuation (competitor audit
// 2026-08-08): the official calculator hard-stops DWH/BGS/maul
// stacking per boss, and without the floor our drain-fishing model
// overvalues drain specs - at Nex (base 260, floor 250) a DWH is worth
// almost nothing, at Verzik and Vardorvis exactly nothing. Id arrays
// in defence_floors.json are copied verbatim from the wiki calc's
// constants - the same weirdgloop ids our corpus carries.
public class DefenceFloors {
    // Sentinel: the floor IS the boss's base defence (undrainable).
    static final int BASE = -1;

    static final Map<Integer, Integer> BY_ID = new HashMap<>();

    static {
        JsonObject root = JsonResources.objectOrThrow( "defence_floors.json");
        for (JsonElement e : root.getAsJsonArray("rules")) {
            JsonObject row = e.getAsJsonObject();
            int floor = "base".equals(row.get("floor").getAsString()) ? BASE : row.get("floor").getAsInt();
            for (JsonElement id : row.getAsJsonArray("ids")) BY_ID.put(id.getAsInt(), floor);
        }
        if (BY_ID.isEmpty()) throw new IllegalStateException("defence_floors.json loaded empty");
    }


    // The defence level a drain cannot pass for this monster - 0 when
    // unfloored. Synthetic group rows resolve through profileId().
    public static int floorFor(MonsterStats monster) {
        if (monster == null) return 0;
        Integer floor = BY_ID.get(monster.profileId());
        if (floor == null) return 0;
        return floor == BASE ? monster.defence : floor;
    }
}
