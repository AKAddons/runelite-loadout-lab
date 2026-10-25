package com.loadoutlab.render;

import com.loadoutlab.data.JsonResources;
import java.util.*;
import static com.loadoutlab.render.Model.*;

/**
 * The bank-tag layout for a card (ported from the classic panel's
 * buildBankLayout): the equipment cross in cols 0-2 of the 8-wide bank
 * grid (spec weapon in the empty cell left of the legs), the carried
 * inventory 4-wide in cols 4-7. Consumable/supply strips join when
 * those nodes reach the model. Returns {ids, layout} ready for the
 * bank-filter seam.
 */
final class BankLayout {
	private BankLayout() {
	}

	/** ids + positions for the shown card; null when the card is empty. */
	/** Everything the Supplies and Inventory rows draw, as stacks for the
	 * filter (Andrew 2026-09-02: "not seeing all of the supplies in the
	 * filter view"): the trip's runes, the pouch and casting cape when it
	 * casts, the assumed potion unless the raid supplies it, every dose of
	 * each supply pick, and the mob's own "~" filter list. */
	static List<Map<String, Object>> tripStacks(Map<String, Object> mob, Map<String, Object> cardAssume,
		List<Map<String, Object>> supplies, List<Map<String, Object>> castRunes,
		List<Map<String, Object>> utilityRunes) {
		LinkedHashSet<Integer> ids = new LinkedHashSet<>();
		for (List<Map<String, Object>> runes : List.of(castRunes, utilityRunes)) {
			for (Map<String, Object> rune : runes) { ids.add(id(rune, "id")); }
		}
		boolean casts = !ids.isEmpty();
		for (String key : new String[]{"castingPouch", "castingCape"}) {
			int id = id(mob, key);
			if (id > 0 && casts) { ids.add(id); }
		}
		int boost = cardAssume == null || flag(cardAssume, "boostSupplied") ? 0
			: id(cardAssume, "boostItem");
		if (boost > 0) { ids.add(boost); }
		for (Map<String, Object> supply : supplies) {
			Object all = supply.get("ids");
			if (all instanceof List && !((List<?>) all).isEmpty()) {
				for (Object o : (List<?>) all) { ids.add(((Number) o).intValue()); }
			} else {
				ids.add(id(supply, "itemId"));
			}
		}
		for (Map<String, Object> item : list(mob, "mobFilters")) { ids.add(id(item, "id")); }
		List<Map<String, Object>> out = new ArrayList<>();
		for (int id : ids) { out.add(Map.of("id", id)); }
		return out;
	}

	/** extraStacks: rune stacks (autocast + the trip's utility casts)
	 * that join the carried block, so Filter bank shows them too. */
	static Map<String, Object> build(Map<String, Object> card,
		List<Map<String, Object>> extraStacks) {
		Map<String, Object> gear = map(card, "gear");
		if (gear == null) { return null; }
		Map<Integer, Integer> place = new LinkedHashMap<>();
		var ids = new LinkedHashSet<Integer>();
		// The card's silhouette (ResultCards.CROSS), rows of 3: the spec
		// weapon fills the cell left of the legs, the quiver corner and the
		// blank corners stay empty here.
		for (int i = 0; i < ResultCards.CROSS.length; i++) {
			String slot = ResultCards.CROSS[i];
			int pos = (i / 3) * 8 + (i % 3);
			Map<String, Object> item = "#spec".equals(slot)
				? map(map(card, "spec"), "weapon")
				: slot == null || slot.startsWith("#") ? null : map(gear, slot);
			if (item != null) {
				int id = id(item, "id");
				place.put(pos, id);
				ids.add(id);
			}
		}
		var carried = new LinkedHashSet<Integer>();
		Map<String, Object> quiver = map(card, "quiverAmmo");
		if (quiver != null) { carried.add(id(quiver, "id")); }
		for (List<Map<String, Object>> stacks : List.of(list(card, "bench"), list(card, "runes"), extraStacks)) {
			for (Map<String, Object> item : stacks) { carried.add(id(item, "id")); }
		}
		carried.removeAll(ids);
		int k = 0;
		for (int id : carried) {
			place.put((k / 4) * 8 + 4 + (k % 4), id);
			ids.add(id);
			k++;
		}
		int maxPos = 0;
		for (int pos : place.keySet()) { maxPos = Math.max(maxPos, pos); }
		int[] layout = new int[maxPos + 1];
		Arrays.fill(layout, -1);
		for (var entry : place.entrySet()) { layout[entry.getKey()] = entry.getValue(); }
		return JsonResources.map("ids", new ArrayList<>(ids), "layout", layout);
	}
}
