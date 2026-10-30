package com.loadoutlab.engine;

import com.google.gson.*;
import com.loadoutlab.data.*;
import java.util.*;

// God Wars Dungeon protection (ISS-01M5G9KPDQWBR014Z10F79B29T). Followers
// leave you alone while you WEAR an item of their god (wiki, God Wars
// Dungeon); each general's walk needs the wiki route table's gods - its
// corner of the main chamber plus its encampment (Andrew 2026-10-21:
// "Follow the wiki's route table"). The Ancient chip adds Zaros: the Nex
// respite area's banker. Data: gwd_protection.json (scripts/gen_gwd_protection.py).
public class GwdProtection {
    public static final String ZAROS = "zaros";

    // Monster name (lower case) -> the gods its route needs, in table order.
    static final Map<String, List<String>> ROUTE_BY_MONSTER = new HashMap<>();
    // Item name (lower case) -> the gods it protects against.
    static final Map<String, Set<String>> GODS_BY_ITEM = new HashMap<>();

    static {
        JsonObject root = JsonResources.objectOrThrow("gwd_protection.json");
        for (var route : root.getAsJsonObject("routes").entrySet()) {
            List<String> gods = new ArrayList<>();
            JsonResources.strings(route.getValue().getAsJsonObject(), "gods", gods);
            List<String> monsters = new ArrayList<>();
            JsonResources.strings(route.getValue().getAsJsonObject(), "monsters", monsters);
            for (String monster : monsters) ROUTE_BY_MONSTER.put(monster, gods);
        }
        for (var god : root.getAsJsonObject("items").entrySet()) {
            for (JsonElement name : god.getValue().getAsJsonArray()) {
                GODS_BY_ITEM.computeIfAbsent(name.getAsString(), n -> new TreeSet<>()).add(god.getKey());
            }
        }
    }


    // True for the generals and their bodyguards.
    public static boolean isGwd(MonsterStats monster) {
        return monster != null && ROUTE_BY_MONSTER.containsKey(monster.nameLower);
    }

    // The gods this monster's route needs (Zaros added for the Ancient chip);
    // empty anywhere else.
    public static List<String> godsFor(MonsterStats monster, boolean ancient) {
        List<String> route = monster == null ? null : ROUTE_BY_MONSTER.get(monster.nameLower);
        if (route == null) return List.of();
        if (!ancient) return route;
        List<String> gods = new ArrayList<>(route);
        gods.add(ZAROS);
        return gods;
    }

    public static Set<String> godsOf(GearItem item) {
        Set<String> gods = item == null ? null : GODS_BY_ITEM.get(item.nameLower);
        return gods == null ? Set.of() : gods;
    }

    // Every god the worn set already protects against.
    public static Set<String> coveredGods(Loadout loadout) {
        Set<String> covered = new TreeSet<>();
        for (GearItem item : loadout.getGear().values()) covered.addAll(godsOf(item));
        return covered;
    }
}
