package com.loadoutlab.engine;

import com.loadoutlab.data.*;
import java.util.*;
import java.util.function.IntPredicate;

/** The max cape sim (ISS-01M5QY13K6YHTYDW1CCYD30CSY, Andrew 2026-10-24:
 * "max cape should automatically unlock the fire/inferno, mage/ rage cape
 * combo versions"): the max cape, and every combo max cape whose other
 * half you own. The pairs live in max_capes.json. */
public class MaxCapeSims {
    static final Map<String, String> COMBOS = new LinkedHashMap<>();

    static {
        JsonResources.stringMap(JsonResources.object("max_capes.json"), "combos", COMBOS);
    }


    public static Set<Integer> ids(LoadoutData data, IntPredicate owns) {
        Set<Integer> out = new LinkedHashSet<>();
        GearItem max = cape(data, "max cape");
        if (max != null) out.add(max.id);
        for (var combo : COMBOS.entrySet()) {
            GearItem base = cape(data, combo.getKey());
            GearItem made = cape(data, combo.getValue());
            if (base != null && made != null && owns.test(base.id)) out.add(made.id);
        }
        return out;
    }

    /** Whether these requirements wear the max cape (a maxed account, or the sim's waiver). */
    public static boolean wearable(LoadoutData data, RequirementProfile requirements) {
        GearItem max = cape(data, "max cape");
        return max != null && requirements.canWear(max);
    }

    /** Every row of each base cape whose combo max cape exists: the
     * alternatives Best in game drops under the max cape options - a combo
     * keeps its base's perks and offence, so the two tie on dps
     * (ISS-01M5T53FBMHVMJTDPS5ZW4WS50). */
    public static Set<Integer> bases(LoadoutData data) {
        Set<Integer> out = new HashSet<>();
        for (var combo : COMBOS.entrySet()) {
            if (cape(data, combo.getValue()) == null) continue;
            for (GearItem item : data.getGearItems(GearSlot.CAPE)) {
                if (item.nameLower.equals(combo.getKey())) out.add(item.id);
            }
        }
        return out;
    }

    /** The wearable row of a cape by name (not a broken or locked twin). */
    static GearItem cape(LoadoutData data, String name) {
        for (GearItem item : data.getGearItems(GearSlot.CAPE)) {
            String version = item.versionLower;
            if (item.nameLower.equals(name) && item.standardGear && !data.isVariant(item.id)
                && !version.contains("broken") && !version.contains("locked")) {
                return item;
            }
        }
        return null;
    }
}
