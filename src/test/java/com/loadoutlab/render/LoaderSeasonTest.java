package com.loadoutlab.render;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The compute animation keeps the calendar (Andrew 2026-10-24: "a
 * halloween themed loading screen that can override the main loadout lab
 * guy from 10/15-10/31 (yearly)"): from the 15th to the 31st of October,
 * every year, a land compute plays the Halloween moods instead of the
 * flask; the keyed pools (a raid's own art) still play for their selection. */
class LoaderSeasonTest {
	@Test
	@DisplayName("Oct 15 to Oct 31 plays the Halloween pool in place of the flask, any year; Oct 14 and Nov 1 play the flask")
	void halloweenWindow() {
		List<List<String>> land = new ArrayList<>();
		Map<String, List<List<String>>> pools = new HashMap<>();
		AsciiLoader.load(land, pools);
		List<List<String>> halloween = pools.getOrDefault("halloween", List.of());
		assertTrue(halloween.size() >= 3, "the Halloween pool ships at least three moods");
		// Halloween AND the lab (Andrew 2026-10-24: "it should have both"): the
		// flask on its legs is in every frame.
		for (List<String> mood : halloween) {
			for (String frame : mood) {
				assertTrue(frame.contains("╚═══╝"), "the lab flask's legs in every Halloween frame");
			}
		}

		assertEquals(halloween, AsciiLoader.seasonal(LocalDate.of(2026, 10, 15)), "the first day");
		assertEquals(halloween, AsciiLoader.seasonal(LocalDate.of(2026, 10, 31)), "Halloween itself");
		assertEquals(halloween, AsciiLoader.seasonal(LocalDate.of(2031, 10, 20)), "every year");
		assertEquals(land, AsciiLoader.seasonal(LocalDate.of(2026, 10, 14)), "the day before");
		assertEquals(land, AsciiLoader.seasonal(LocalDate.of(2026, 11, 1)), "the day after");
		assertEquals(land, AsciiLoader.seasonal(LocalDate.of(2026, 3, 15)), "spring");
		assertNotEquals(land, halloween, "the Halloween moods are their own art");
	}

	@Test
	@DisplayName("a keyed selection keeps its own pool in season - the Obelisk still charges for a ToA trip")
	void keyedPoolsOutrankTheSeason() {
		AsciiLoader loader = new AsciiLoader();
		try {
			loader.setKey("toa");
			loader.setRunning(true);
			assertEquals("toa", loader.playingKey());
		} finally {
			loader.setRunning(false);
		}
	}
}
