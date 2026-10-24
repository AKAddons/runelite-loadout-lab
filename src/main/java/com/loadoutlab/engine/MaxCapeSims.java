package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.JsonResources;
import com.loadoutlab.data.LoadoutData;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/** The max cape sim (ISS-01M5QY13K6YHTYDW1CCYD30CSY, Andrew 2026-10-24:
 * "max cape should automatically unlock the fire/inferno, mage/ rage cape
 * combo versions"): the max cape, and every combo max cape whose other
 * half you own. The pairs live in max_capes.json. */
public final class MaxCapeSims {
	private static final Map<String, String> COMBOS = new LinkedHashMap<>();

	static {
		JsonResources.stringMap(JsonResources.object("max_capes.json"), "combos", COMBOS);
	}

	private MaxCapeSims() {
	}

	public static Set<Integer> ids(LoadoutData data, IntPredicate owns) {
		Set<Integer> out = new LinkedHashSet<>();
		GearItem max = cape(data, "max cape");
		if (max != null) {
			out.add(max.getId());
		}
		for (Map.Entry<String, String> combo : COMBOS.entrySet()) {
			GearItem base = cape(data, combo.getKey());
			GearItem made = cape(data, combo.getValue());
			if (base != null && made != null && owns.test(base.getId())) {
				out.add(made.getId());
			}
		}
		return out;
	}

	/** The wearable row of a cape by name (not a broken or locked twin). */
	private static GearItem cape(LoadoutData data, String name) {
		for (GearItem item : data.getGearItems(GearSlot.CAPE)) {
			String version = item.getVersionLower();
			if (item.getNameLower().equals(name) && item.isStandardGear() && !data.isVariant(item.getId())
				&& !version.contains("broken") && !version.contains("locked")) {
				return item;
			}
		}
		return null;
	}
}
