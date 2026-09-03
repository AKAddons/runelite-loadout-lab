package com.loadoutlab.render;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * The bank-tag layout for a card (ported from the classic panel's
 * buildBankLayout): the equipment cross in cols 0-2 of the 8-wide bank
 * grid (spec weapon in the empty cell left of the legs), the carried
 * inventory 4-wide in cols 4-7. Consumable/supply strips join when
 * those nodes reach the model. Returns {ids, layout} ready for the
 * bank-filter seam.
 */
final class BankLayout
{
	/** The worn-equipment silhouette, rows of 3; null = blank corner,
	 * index 9 = the spec cell. Mirrors the classic CLASSIC_ORDER. */
	private static final String[] CROSS = {
		null, "head", null,
		"cape", "neck", "ammo",
		"weapon", "body", "shield",
		null, "legs", null,
		"hands", "feet", "ring",
	};
	private static final int SPEC_INDEX = 9;

	private BankLayout()
	{
	}

	/** ids + positions for the shown card; null when the card is empty. */
	/** Everything the Supplies and Inventory rows draw, as stacks for the
	 * filter (Andrew 2026-09-02: "not seeing all of the supplies in the
	 * filter view"): the trip's runes, the pouch and casting cape when it
	 * casts, the assumed potion unless the raid supplies it, every dose of
	 * each supply pick, and the mob's own "~" filter list. */
	static List<Map<String, Object>> tripStacks(Map<String, Object> mob, Map<String, Object> cardAssume,
		List<Map<String, Object>> supplies, List<Map<String, Object>> castRunes,
		List<Map<String, Object>> utilityRunes)
	{
		LinkedHashSet<Integer> ids = new LinkedHashSet<>();
		for (Map<String, Object> rune : castRunes)
		{
			ids.add(Model.id(rune, "id"));
		}
		for (Map<String, Object> rune : utilityRunes)
		{
			ids.add(Model.id(rune, "id"));
		}
		boolean casts = !ids.isEmpty();
		for (String key : new String[]{"castingPouch", "castingCape"})
		{
			int id = Model.id(mob, key);
			if (id > 0 && casts)
			{
				ids.add(id);
			}
		}
		int boost = cardAssume == null || Model.flag(cardAssume, "boostSupplied") ? 0
			: Model.id(cardAssume, "boostItem");
		if (boost > 0)
		{
			ids.add(boost);
		}
		for (Map<String, Object> supply : supplies)
		{
			Object all = supply.get("ids");
			if (all instanceof List && !((List<?>) all).isEmpty())
			{
				for (Object o : (List<?>) all)
				{
					ids.add(((Number) o).intValue());
				}
			}
			else
			{
				ids.add(Model.id(supply, "itemId"));
			}
		}
		for (Map<String, Object> item : Model.list(mob, "mobFilters"))
		{
			ids.add(Model.id(item, "id"));
		}
		List<Map<String, Object>> out = new ArrayList<>();
		for (int id : ids)
		{
			out.add(Map.of("id", id));
		}
		return out;
	}

	static Map<String, Object> build(Map<String, Object> card)
	{
		return build(card, java.util.Collections.emptyList());
	}

	/** extraStacks: rune stacks (autocast + the trip's utility casts)
	 * that join the carried block, so Filter bank shows them too. */
	static Map<String, Object> build(Map<String, Object> card,
		List<Map<String, Object>> extraStacks)
	{
		Map<String, Object> gear = Model.map(card, "gear");
		if (gear == null)
		{
			return null;
		}
		Map<Integer, Integer> place = new LinkedHashMap<>();
		LinkedHashSet<Integer> ids = new LinkedHashSet<>();
		Map<String, Object> spec = Model.map(card, "spec");
		for (int i = 0; i < CROSS.length; i++)
		{
			int pos = (i / 3) * 8 + (i % 3);
			Map<String, Object> item = i == SPEC_INDEX
				? (spec == null ? null : Model.map(spec, "weapon"))
				: CROSS[i] == null ? null : Model.map(gear, CROSS[i]);
			if (item != null)
			{
				int id = Model.id(item, "id");
				place.put(pos, id);
				ids.add(id);
			}
		}
		LinkedHashSet<Integer> carried = new LinkedHashSet<>();
		Map<String, Object> quiver = Model.map(card, "quiverAmmo");
		if (quiver != null)
		{
			carried.add(Model.id(quiver, "id"));
		}
		for (Map<String, Object> item : Model.list(card, "bench"))
		{
			carried.add(Model.id(item, "id"));
		}
		for (Map<String, Object> item : Model.list(card, "runes"))
		{
			carried.add(Model.id(item, "id"));
		}
		for (Map<String, Object> item : extraStacks)
		{
			carried.add(Model.id(item, "id"));
		}
		carried.removeAll(ids);
		int k = 0;
		for (int id : carried)
		{
			place.put((k / 4) * 8 + 4 + (k % 4), id);
			ids.add(id);
			k++;
		}
		int maxPos = 0;
		for (int pos : place.keySet())
		{
			maxPos = Math.max(maxPos, pos);
		}
		int[] layout = new int[maxPos + 1];
		java.util.Arrays.fill(layout, -1);
		for (Map.Entry<Integer, Integer> entry : place.entrySet())
		{
			layout[entry.getKey()] = entry.getValue();
		}
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("ids", new ArrayList<>(ids));
		out.put("layout", layout);
		return out;
	}
}
