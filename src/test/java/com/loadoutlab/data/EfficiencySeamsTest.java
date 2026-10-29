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
	@DisplayName("gear name markers are bits: a lower-case marker matches by name fragment, any other trips the -ea guard")
	void nameMarkersAreBits() {
		GearItem salve = data.gearItems.stream().filter(g -> g.nameLower.equals("salve amulet(ei)")).findFirst().get();
		assertTrue(salve.nameHas(GearItem.markerBit("salve amulet")));
		assertTrue(salve.nameHas(GearItem.markerBit("salve amulet(ei)")));
		assertFalse(salve.nameHas(GearItem.markerBit("salve amulet(i)")));
		assertThrows(AssertionError.class, () -> GearItem.markerBit("Salve amulet"));
	}
}
