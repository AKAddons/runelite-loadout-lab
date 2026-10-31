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
// Monster name (lower case) -> the gods its route needs, in table order.
static final Map<String, List<String>> ROUTE_BY_MONSTER = new HashMap<>();
// Item name (lower case) -> the gods it protects against.
static final Map<String, Set<String>> GODS_BY_ITEM = new HashMap<>();

static class Root {
    Map<String, Route> routes = Map.of();
    Map<String, List<String>> items = Map.of();
}

static class Route {
    List<String> gods = List.of(), monsters = List.of();
}

static {
    Root root = JsonResources.read("gwd_protection.json", Root.class);
    for (Route route : root.routes.values()) for (String monster : route.monsters) ROUTE_BY_MONSTER.put(monster, route.gods);
    root.items.forEach((god, names) -> {
        for (String name : names) GODS_BY_ITEM.computeIfAbsent(name, n -> new TreeSet<>()).add(god);
    });
}


public static List<String> godsFor(Monster monster) {
    List<String> route = monster == null ? null : ROUTE_BY_MONSTER.get(monster.nameLower);
    return route == null ? List.of() : route;
}

// The god chips in the requirements param ("god:<god>>=1", the editor's
// form): the trip's protection list, worn when free, else carried
// (ISS-01M67DQ9ATHSQSJA7QGDKAQGPT, Andrew 2026-10-30: "chips as protection").
public static List<String> chips(String requirements) {
    List<String> gods = new ArrayList<>();
    if (requirements != null) for (String part : requirements.split(";")) {
        String p = part.trim();
        if (!p.startsWith("god:")) continue;
        String god = p.substring(4).replaceAll("[^a-z].*", "");
        if (!god.isEmpty() && !gods.contains(god)) gods.add(god);
    }
    return gods;
}

// A route's chips, as the editor writes them.
public static String chipsFor(List<String> gods) {
    StringBuilder out = new StringBuilder();
    for (String god : gods) out.append(out.length() == 0 ? "" : ";").append("god:").append(god).append(">=1");
    return out.toString();
}

public static Set<String> godsOf(Gear item) {
    Set<String> gods = item == null ? null : GODS_BY_ITEM.get(item.nameLower);
    return gods == null ? Set.of() : gods;
}

// Every god the worn set already protects against.
static Set<String> coveredGods(Loadout loadout) {
    Set<String> covered = new TreeSet<>();
    for (Gear item : loadout.getGear().values()) covered.addAll(godsOf(item));
    return covered;
}
}
