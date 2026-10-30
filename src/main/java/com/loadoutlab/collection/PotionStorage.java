package com.loadoutlab.collection;

import com.google.gson.JsonObject;
import com.loadoutlab.data.JsonResources;
import java.util.*;
import java.util.function.IntUnaryOperator;

// The bank's potion storage is varbits, not an item container: one
// POTIONSTORE_VILE_SIZE_* varbit per potion holding the doses stored.
// potion_storage.json maps each varbit the plugin cares about to the
// 4-dose item it detects (Andrew 2026-10-19: super antifires kept there
// were invisible, so Detect best said shield).
public class PotionStorage {
    // varbit id -> 4-dose item id.
    static final Map<Integer, Integer> BY_VARBIT = new LinkedHashMap<>();
    // The master scroll book's scrolls (ISS-01M5VDZXT4W4D5DP468R5R7NSW): shown as the book when held only there.
    static final Set<Integer> SCROLLS = new HashSet<>();

    static {
        JsonObject root = JsonResources.objectOrThrow("potion_storage.json");
        JsonResources.ints(root, "_scrolls", SCROLLS);
        for (String key : root.keySet()) {
            if (!key.startsWith("_")) BY_VARBIT.put(Integer.parseInt(key), root.get(key).getAsInt());
        }
    }


    public static boolean tracks(int varbitId) { return BY_VARBIT.containsKey(varbitId); }

    public static boolean isScroll(int itemId) { return SCROLLS.contains(itemId); }

    // item id -> 4-dose equivalents, for every stored potion with doses.
    public static Map<Integer, Integer> owned(IntUnaryOperator dosesOf) {
        Map<Integer, Integer> owned = new HashMap<>();
        BY_VARBIT.forEach((varbit, item) -> {
            int doses = dosesOf.applyAsInt(varbit);
            if (doses > 0) owned.merge(item, (doses + 3) / 4, Integer::sum);
        });
        return owned;
    }
}
