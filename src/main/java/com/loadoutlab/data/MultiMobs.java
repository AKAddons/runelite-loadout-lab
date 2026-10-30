package com.loadoutlab.data;

import com.google.gson.*;
import java.util.*;

// The mobs fought in multi-combat areas: multi_mobs.json, curated from the
// wiki location tables, the crowdsourced multicombat tiles and the area
// pages (docs/data/multi-mobs.md), keyed name + version as the loader
// sees them. A listed mob gets the Multi switch on its card.
public class MultiMobs {
    static final Map<String, List<String>> LOCATIONS = new HashMap<>();

    static class Root {
        List<Mob> mobs = List.of();
    }

    static class Mob {
        String name, version;
        List<Loc> locations = List.of();
    }

    static class Loc {
        String location;
    }

    static {
        for (Mob mob : JsonResources.read("multi_mobs.json", Root.class).mobs) {
            List<String> locations = new ArrayList<>();
            for (Loc l : mob.locations) locations.add(l.location);
            LOCATIONS.put(key(mob.name, mob.version), locations);
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
