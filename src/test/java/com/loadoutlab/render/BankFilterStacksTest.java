package com.loadoutlab.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Andrew, 2026-09-02: "not seeing all of the supplies in the filter
 * view" - the filter carried gear, bench and runes; the Supplies and
 * Inventory rows draw more than that. */
class BankFilterStacksTest
{
	private static List<Integer> ids(List<Map<String, Object>> stacks)
	{
		List<Integer> out = new ArrayList<>();
		for (Map<String, Object> s : stacks)
		{
			out.add(Model.id(s, "id"));
		}
		return out;
	}

	@Test
	@DisplayName("the filter carries every supply dose, the assumed potion, the cape, the pouch, the runes and the mob's own filter list")
	void everythingTheRowsDraw()
	{
		Map<String, Object> mob = Map.of("castingPouch", 27281, "castingCape", 9763,
			"mobFilters", List.of(Map.of("id", 9789, "name", "Construction cape", "scope", "all")));
		Map<String, Object> assume = Map.of("boostItem", 23685, "boostSupplied", false);
		List<Map<String, Object>> supplies = List.of(
			Map.of("category", "prayerRestore", "itemId", 3024, "ids", List.of(3024, 3026, 3028, 3030)),
			Map.of("category", "food", "itemId", 29143, "ids", List.of(29143)));
		List<Integer> got = ids(BankLayout.tripStacks(mob, assume, supplies,
			List.of(Map.of("id", 565)), List.of(Map.of("id", 560))));
		assertEquals(List.of(565, 560, 27281, 9763, 23685, 3024, 3026, 3028, 3030, 29143, 9789), got);
	}

	@Test
	@DisplayName("a raid-supplied boost, and the cape and pouch of a trip that casts nothing, stay out")
	void gated()
	{
		Map<String, Object> mob = Map.of("castingPouch", 27281, "castingCape", 9763);
		Map<String, Object> assume = Map.of("boostItem", 23685, "boostSupplied", true);
		assertEquals(List.of(), ids(BankLayout.tripStacks(mob, assume, List.of(), List.of(), List.of())));
	}
}
