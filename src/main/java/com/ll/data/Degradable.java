package com.ll.data;

import com.google.gson.JsonObject;
import java.util.*;

// Gear that costs to use: wears down or burns charges per hit/cast
// (degradable_gear.json; definition Andrew 2026-09-08).
public class Degradable {
static final List<String> FAMILIES = new ArrayList<>();
static final List<String> EXCEPT = new ArrayList<>();

static {
    JsonObject root = Json.objectOrThrow("degradable_gear.json");
    Json.strings(root, "families", FAMILIES);
    Json.strings(root, "except", EXCEPT);
}

public static boolean matches(String lname) {
    return EXCEPT.stream().noneMatch(lname::contains) && FAMILIES.stream().anyMatch(lname::contains);
}

public static Set<Integer> ids(Tables data) {
    Set<Integer> ids = new HashSet<>();
    data.gearItems.forEach(item -> {
        if (matches(item.lname)) ids.add(item.id);
    });
    return ids;
}
}
