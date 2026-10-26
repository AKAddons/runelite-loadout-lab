package com.loadoutlab.data;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The efficiency pass's seams (ISS-01M5V29J67YYZRH28Q6PQ0JDCJ, Andrew 2026-10-25:
 * "the calculations are taking quite a while now"): the per-trial checks read
 * values computed once instead of lower-casing and looking up per set.
 */
class EfficiencySeamsTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	@Test
	@DisplayName("a monster's vampyre tier is stored: Vanstrom 3, a Vampyre Juvinate 2, Count Draynor 1, Graardor 0")
	void vampyreTierStored() {
		assertEquals(3, data.searchMonsters("vanstrom klause", 1).get(0).getVampyreTier());
		assertEquals(2, data.searchMonsters("vampyre juvinate", 1).get(0).getVampyreTier());
		assertEquals(1, data.searchMonsters("count draynor", 1).get(0).getVampyreTier());
		assertEquals(0, data.searchMonsters("general graardor", 1).get(0).getVampyreTier());
	}

	@Test
	@DisplayName("Names.key lower-cases once and hands back the same string after")
	void keyMemoised() {
		String first = Names.key("Elite Void Top");
		assertEquals("elite void top", first);
		assertSame(first, Names.key("Elite Void Top"));
	}
}
