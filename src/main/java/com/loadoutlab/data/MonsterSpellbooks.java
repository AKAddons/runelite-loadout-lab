package com.loadoutlab.data;

import com.google.gson.*;
import java.util.*;

// Fight-level spellbook requirements (wiki-verified, curated): some
// fights want a specific book for their MECHANICS regardless of the
// gear set - the Abyssal Sire's Shadow Barrage stun is the founding
// case (field report 2026-08-15). Matched by lowercase name-contains
// so phase and form suffixes ride along.
public class MonsterSpellbooks {
// name-fragment -> [book, reason]
static final Map<String, String[]> BY_NAME = new LinkedHashMap<>();

static class Root {
    Map<String, Node> byNameContains = Map.of();
}

static class Node {
    String book, reason, spell;
}

static {
    JsonResources.read("monster_spellbooks.json", Root.class).byNameContains
        .forEach((name, node) -> BY_NAME.put(Names.lower(name), new String[]{node.book, node.reason, node.spell}));
}


// The fight's required book ("ancient"...) or null.
public static String bookFor(Monster monster) {
    String[] entry = lookup(monster);
    return entry == null ? null : entry[0];
}

// Why the fight wants it, or null.
public static String reasonFor(Monster monster) {
    String[] entry = lookup(monster);
    return entry == null ? null : entry[1];
}

// The utility cast the fight wants (its runes ride the trip).
public static String spellFor(Monster monster) {
    String[] entry = lookup(monster);
    return entry == null ? null : entry[2];
}

static String[] lookup(Monster monster) {
    if (monster == null) return null;
    String name = monster.nameLower;
    return BY_NAME.entrySet().stream().filter(e -> name.contains(e.getKey())) .map(Map.Entry::getValue).findFirst().orElse(null);
}
}
