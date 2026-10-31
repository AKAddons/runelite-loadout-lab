package com.loadoutlab.data;

import com.google.gson.JsonObject;
import java.util.*;

// Gear that costs to use: wears down or burns charges per hit/cast
// (degradable_gear.json; definition Andrew 2026-09-08).
public class Degradable {
static final List<String> FAMILIES = new ArrayList<>();
static final List<String> EXCEPT = new ArrayList<>();

static {
    JsonObject root = JsonResources.objectOrThrow("degradable_gear.json");
    JsonResources.strings(root, "families", FAMILIES);
    JsonResources.strings(root, "except", EXCEPT);
}

public static boolean matches(String nameLower) {
    return EXCEPT.stream().noneMatch(nameLower::contains) && FAMILIES.stream().anyMatch(nameLower::contains);
}

public static Set<Integer> ids(LoadoutData data) {
    Set<Integer> ids = new HashSet<>();
    data.gearItems.forEach(item -> {
        if (matches(item.nameLower)) ids.add(item.id);
    });
    return ids;
}
}
