package com.loadoutlab.data;

import com.google.gson.*;
import java.util.*;

// The mobs fought in multi-combat areas: multi_mobs.json, curated from the
// wiki location tables, the crowdsourced multicombat tiles and the area
// pages (docs/data/multi-mobs.md), keyed name + version as the loader
// sees them. A listed mob gets the Multi switch on its card.
public class MultiMobs {
    static final Map<String, List<String>> LOCATIONS = new HashMap<>();

    static {
        JsonObject root = JsonResources.objectOrThrow("/com/loadoutlab/data/multi_mobs.json");
        for (JsonElement e : root.getAsJsonArray("mobs")) {
            JsonObject mob = e.getAsJsonObject();
            List<String> locations = new ArrayList<>();
            for (JsonElement l : mob.getAsJsonArray("locations")) {
                locations.add(l.getAsJsonObject().get("location").getAsString());
            }
            LOCATIONS.put(key(mob.get("name").getAsString(), mob.get("version").getAsString()), locations);
        }
    }


    static String key(String name, String version) {
        return Names.lower(name) + "#" + Names.lower(version == null ? "" : version);
    }

    public static boolean isMultiAble(MonsterStats mob) {
        return mob != null && LOCATIONS.containsKey(key(mob.name, mob.version));
    }

    // The multi locations the mob is fought in, for the switch's tooltip.
    public static List<String> locations(MonsterStats mob) {
        return mob == null ? List.of() : LOCATIONS.getOrDefault(key(mob.name, mob.version), List.of());
    }
}
