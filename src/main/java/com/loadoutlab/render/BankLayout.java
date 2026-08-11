package com.loadoutlab.render;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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
	static Map<String, Object> build(Map<String, Object> card)
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
		out.put("ids", new java.util.ArrayList<>(ids));
		out.put("layout", layout);
		return out;
	}
}
