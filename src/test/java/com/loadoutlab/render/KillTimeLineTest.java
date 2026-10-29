package com.loadoutlab.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The card's Kill line and a group's Trip line (ISS-01M6415DTE84ZZ45C4YD3MZRBJ,
 * Andrew 2026-10-29: "P50 is what is displayed, the other stuff is shown when
 * hovered"; "TTK for moons of peril, barrows should be the combined kill time
 * of all 3 bosses"). */
class KillTimeLineTest {
	private static Map<String, Object> kill(double p10, double p50, double p90, double perfect, boolean approximate, double heal) {
		Map<String, Object> m = new HashMap<>();
		m.put("p10", p10);
		m.put("p50", p50);
		m.put("p90", p90);
		m.put("perfect", perfect);
		m.put("approximate", approximate);
		m.put("healDps", heal);
		return m;
	}

	@Test
	@DisplayName("the Kill line shows the P50 as m:ss, seconds under a minute, never when the set cannot kill; the hover carries P10, P90, Perfect, the heal and the approximate flag")
	void killLine() {
		String[] line = ResultCards.killLine(kill(108, 133, 161, 52, false, 0));
		assertEquals("~2:13", line[0]);
		assertEquals("Kill time (P50) 2:13 - P10 1:48, P90 2:41, Perfect 0:52", line[1]);
		assertEquals("~43 s", ResultCards.killLine(kill(30, 43, 60, 20, false, 0))[0]);
		String[] healed = ResultCards.killLine(kill(108, 133, 161, 52, true, 1.23));
		assertTrue(healed[1].endsWith("Perfect 0:52 (approximate). Heals ~1.2/s off this set"), healed[1]);
		String[] never = ResultCards.killLine(kill(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
			Double.POSITIVE_INFINITY, 52, false, 3));
		assertEquals("never", never[0]);
		assertTrue(never[1].startsWith("This set cannot outpace the heal"), never[1]);
	}

	@Test
	@DisplayName("the Trip line is the group's combined P50, its hover the band, Perfect and each member; one member that never dies makes the trip never and names it")
	void tripLine() {
		Map<String, Object> trip = kill(360, 400, 450, 200, false, 0);
		trip.put("members", List.of(Map.of("name", "Blood Moon", "p50", 133.0), Map.of("name", "Blue Moon", "p50", 108.0),
			Map.of("name", "Eclipse Moon", "p50", 159.0)));
		String[] line = ResultCards.tripLine(trip);
		assertEquals("Trip ~6:40", line[0]);
		assertEquals("Trip kill time (P50) 6:40 - P10 6:00, P90 7:30, Perfect 3:20. Blood Moon 2:13, Blue Moon 1:48, Eclipse Moon 2:39", line[1]);
		Map<String, Object> never = kill(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 200, false, 0);
		never.put("never", "Eclipse Moon");
		never.put("members", List.of());
		String[] nl = ResultCards.tripLine(never);
		assertEquals("Trip never", nl[0]);
		assertTrue(nl[1].contains("Eclipse Moon"), nl[1]);
	}
}
