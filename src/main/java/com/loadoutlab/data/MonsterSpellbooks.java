package com.loadoutlab.data;

import com.google.gson.*;
import java.util.*;

/**
 * Fight-level spellbook requirements (wiki-verified, curated): some
 * fights want a specific book for their MECHANICS regardless of the
 * gear set - the Abyssal Sire's Shadow Barrage stun is the founding
 * case (field report 2026-08-15). Matched by lowercase name-contains
 * so phase and form suffixes ride along.
 */
public class MonsterSpellbooks {
    /** name-fragment -> [book, reason] */
    static final Map<String, String[]> BY_NAME = new LinkedHashMap<>();

    static {
        JsonObject root = JsonResources.object( "monster_spellbooks.json");
        if (root != null && root.has("byNameContains")) {
            for (var e : root.getAsJsonObject("byNameContains").entrySet()) {
                JsonObject node = e.getValue().getAsJsonObject();
                BY_NAME.put(Names.lower(e.getKey()), new String[]{
                    node.get("book").getAsString(), node.get("reason").getAsString(),
                    node.has("spell") ? node.get("spell").getAsString() : null});
            }
        }
    }


    /** The fight's required book ("ancient"...) or null. */
    public static String bookFor(MonsterStats monster) {
        String[] entry = lookup(monster);
        return entry == null ? null : entry[0];
    }

    /** Why the fight wants it, or null. */
    public static String reasonFor(MonsterStats monster) {
        String[] entry = lookup(monster);
        return entry == null ? null : entry[1];
    }

    /** The utility cast the fight wants (its runes ride the trip). */
    public static String spellFor(MonsterStats monster) {
        String[] entry = lookup(monster);
        return entry == null ? null : entry[2];
    }

    static String[] lookup(MonsterStats monster) {
        if (monster == null) return null;
        String name = monster.nameLower;
        return BY_NAME.entrySet().stream().filter(e -> name.contains(e.getKey())) .map(Map.Entry::getValue).findFirst().orElse(null);
    }
}
