package com.loadoutlab.data;

import com.google.gson.*;
import java.util.*;
import java.util.function.IntPredicate;

import static com.loadoutlab.data.JsonResources.*;

/**
 * Loot containers worth carrying (ISS-01M5JJ0Z4M988DR0M0ZGJHD139, Andrew 2026-10-22: "for
 * monsters that are predomant droppers of things that can be bags, suggest
 * to bring bags in inventory" - "killerwatts have a predominant herb drop
 * table so i wish i had my herb sack before"). Which monsters drop enough
 * of what a container holds comes from loot_containers.json, generated from
 * the wiki's drop data by scripts/gen_loot_containers.py. Suggested only
 * when owned.
 */
public class LootContainers {
    @lombok.AllArgsConstructor
    static class Container {
        final String name;
        final int[] ids;
        final String holds;
        /** What it does with them - "holds them" for a bag; the bonecrusher,
         * ash sanctifier and soul bearer (ISS-01M5TNBSP28AAHJTYWAWJHHZKB) say their own. */
        final String does;
        final Map<String, Double> perKill;
    }

    static final List<Container> CONTAINERS = new ArrayList<>();
    static Container lootingBag;

    static {
        JsonObject root = objectOrThrow("loot_containers.json");
        for (JsonElement e : root.getAsJsonArray("containers")) {
            JsonObject row = e.getAsJsonObject();
            Map<String, Double> perKill = new HashMap<>();
            row.getAsJsonObject("monsters").entrySet() .forEach(m -> perKill.put(m.getKey(), m.getValue().getAsDouble()));
            CONTAINERS.add(new Container(string(row, "name"), ints(row.getAsJsonArray("ids")),
                string(row, "holds"), row.has("does") ? string(row, "does") : "holds them", perKill));
        }
        JsonObject bag = root.getAsJsonObject("lootingBag");
        lootingBag = new Container(string(bag, "name"), ints(bag.getAsJsonArray("ids")), "loot", "", Map.of());
        if (CONTAINERS.isEmpty()) throw new IllegalStateException("loot_containers.json loaded empty");
    }


    /** The containers this monster's drops are worth that you own, each a
     * trip-kit row: id (the one you own), name, qty, why. The looting bag
     * rides on a wilderness trip. */
    public static List<Map> picks(MonsterStats monster, IntPredicate owns, boolean wilderness) {
        List<Map> out = new ArrayList<>();
        if (monster == null || owns == null) return out;
        String key = monster.nameLower;
        for (Container container : CONTAINERS) {
            Double rate = container.perKill.get(key);
            int id = rate == null ? -1 : ownedId(container, owns);
            if (id > 0) {
                out.add(row(id, container.name, Names.cap(container.holds) + " drop here (" + often(rate) + ") - the "
                    + Names.lower(container.name) + " " + container.does));
            }
        }
        if (wilderness || monster.isWildernessMonster()) {
            int id = ownedId(lootingBag, owns);
            if (id > 0) out.add(row(id, lootingBag.name, "Wilderness: the looting bag stores loot as you fight"));
        }
        return out;
    }

    /** Whether the monster drops enough of what a container holds, by its
     * holds word ("ashes": the Demonic Offering book, ISS-01M5TQ977R0MXBMSQ3VBHS29GB). */
    public static boolean drops(MonsterStats monster, String holds) {
        String key = monster.nameLower;
        return CONTAINERS.stream().anyMatch(c -> c.holds.equals(holds) && c.perKill.containsKey(key));
    }

    static int ownedId(Container container, IntPredicate owns) {
        return Arrays.stream(container.ids).filter(owns).findFirst().orElse(-1);
    }

    /** "about 1 per 4 kills", "about 1 per kill" (bones, ashes), or "about
     * 2 per kill" for the generous. */
    static String often(double perKill) {
        long every = Math.round(1 / perKill);
        return perKill >= 1 ? String.format("about %.0f per kill", perKill)
            : every <= 1 ? "about 1 per kill" : "about 1 per " + every + " kills";
    }

    static Map row(int id, String name, String why) {
        return map("id", id, "name", name, "qty", 1, "why", why, "owned", true);
    }
}
