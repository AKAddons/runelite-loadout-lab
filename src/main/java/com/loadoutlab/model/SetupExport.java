package com.loadoutlab.model;

import java.util.*;
import java.util.function.IntPredicate;
import static com.loadoutlab.data.JsonResources.map;

/** A shown card as an Inventory Setups add-setup (ISS-01M5V8P2430MQ61C5QKHHRQR6Z,
 * Andrew 2026-10-25: "a button that creates a new inventory setup under a
 * "Loadout Lab" section"). */
public final class SetupExport {
	/** RuneLite's EquipmentInventorySlot order; "" marks slots no card fills. */
	private static final List<String> SLOTS = List.of("head", "cape", "neck", "weapon", "body", "shield", "", "legs", "",
		"hands", "feet", "", "ring", "ammo");

	private SetupExport() {
	}

	/** The payload: 14 worn ids (-1 empty), then 28 inventory slots from the
	 * trip's stacks in order - a stackable once at its quantity, anything else a
	 * slot per item, each id once - and the utility items as additional filtered
	 * items (ISS-01M5VBB3E4DM65MHHYWB3A8GSP: "stuff like the magic cape"), and the
	 * rune pouch's runes in slot order (ISS-01M5VC3V5DCEEDGVNMC37VHJ2K). */
	public static Map<String, Object> payload(String name, Map<String, Integer> worn, List<int[]> stacks,
		List<Integer> extra, Map<Integer, Integer> pouch, IntPredicate stackable) {
		var eq = new ArrayList<Integer>();
		for (String slot : SLOTS) eq.add(worn.getOrDefault(slot, 0) > 0 ? worn.get(slot) : -1);
		var ids = new ArrayList<Integer>();
		var qty = new ArrayList<Integer>();
		var seen = new HashSet<Integer>();
		for (int[] s : stacks) {
			if (s[0] <= 0 || !seen.add(s[0])) continue;
			boolean one = stackable.test(s[0]);
			for (int i = 0; i < (one ? 1 : Math.max(1, s[1])) && ids.size() < 28; i++) {
				ids.add(s[0]);
				qty.add(one ? Math.max(1, s[1]) : 1);
			}
		}
		while (ids.size() < 28) {
			ids.add(-1);
			qty.add(0);
		}
		var more = new ArrayList<>(extra);
		more.removeIf(id -> id <= 0);
		return map("setup", name, "equipmentItemIds", eq, "inventoryItemIds", ids, "inventoryQuantities", qty,
			"additionalItemIds", more, "runePouchItemIds", new ArrayList<>(pouch.keySet()),
			"runePouchQuantities", new ArrayList<>(pouch.values()), "section", "Loadout Lab");
	}
}
