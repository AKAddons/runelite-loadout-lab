package com.loadoutlab.data;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Skotizo (ISS-01M5V4ED6FT1TFH1KDCCC6J0G4, Andrew 2026-10-25: "skotizo should have a dark totem in the
 * inventory"; "its shortest path also isn't routing to a good place"): the
 * totem opens the lair from the altar in the centre of the Catacombs of
 * Kourend, where the path now leads.
 */
class SkotizoTest {
	@Test
	@DisplayName("Skotizo's trip supplies carry a dark totem, flagged when you don't own one")
	void darkTotem() {
		LoadoutData data = new DataService().load();
		Map<String, Object> totem = RequiredGear.carriedPicks(data.searchMonsters("skotizo", 1).get(0), id -> false).get(0);
		assertEquals(19685, totem.get("id"));
		assertEquals(Boolean.FALSE, totem.get("owned"));
	}

	@Test
	@DisplayName("Skotizo's path leads to the catacombs altar at 1664, 10048")
	void pathToAltar() {
		int[] points = MobLocations.of("Skotizo");
		assertEquals(1, points.length);
		assertEquals(1664, points[0] & 0x7FFF);
		assertEquals(10048, points[0] >> 15 & 0x7FFF);
		assertEquals(0, points[0] >>> 30);
	}
}
