package com.loadoutlab.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The card's kill-time lines (ISS-01M6415DTE84ZZ45C4YD3MZRBJ, ISS-01M655THHQ6JBS470AFAH0NX3T,
 * ISS-01M65N6VS9BCKXT73KPSE9WYDE; Andrew 2026-10-29/30: "P50 is what is
 * displayed, the other stuff is shown when hovered"; "Median: XX:XX"; "shorten
 * the '90% of runs fall between'", picked "Usually a-b"; "estimate damage
 * taken for kill"). */
class KillTimeLineTest {
	private static Map<String, Object> kill(double p05, double p50, double p95, double perfect, boolean approximate, double heal) {
		Map<String, Object> m = new HashMap<>();
		m.put("p05", p05);
		m.put("p50", p50);
		m.put("p95", p95);
		m.put("perfect", perfect);
		m.put("approximate", approximate);
		m.put("healDps", heal);
		return m;
	}

	@Test
	@DisplayName("the Kill line shows the median as m:ss, seconds under a minute, never when the set cannot kill; the hover reads Median, Usually a-b and Perfect on three lines, then the approximate note and the heal")
	void killLine() {
		String[] line = ResultCards.killLine(kill(108, 133, 161, 52, false, 0));
		assertEquals("~2:13", line[0]);
		assertEquals("<html>Median 2:13<br>Usually 1:48-2:41<br>Perfect 0:52</html>", line[1]);
		assertEquals("~43 s", ResultCards.killLine(kill(30, 43, 60, 20, false, 0))[0]);
		String[] healed = ResultCards.killLine(kill(108, 133, 161, 52, true, 1.23));
		assertTrue(healed[1].endsWith("Perfect 0:52<br>Band approximate: this hit model has no per-attack form<br>Heals ~1.2/s off this set</html>"), healed[1]);
		String[] never = ResultCards.killLine(kill(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
			Double.POSITIVE_INFINITY, 52, false, 3));
		assertEquals("never", never[0]);
		assertTrue(never[1].contains("This set cannot outpace the heal"), never[1]);
	}

	@Test
	@DisplayName("the DTPS hover gains the damage taken over the median kill, nothing without a kill time or when the set never kills")
	void damagePerKill() {
		assertEquals(". ~266 damage per kill", ResultCards.perKill(kill(108, 133, 161, 52, false, 0), 2.0));
		assertEquals("", ResultCards.perKill(null, 2.0));
		assertEquals("", ResultCards.perKill(kill(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 52, false, 0), 2.0));
	}

	@Test
	@DisplayName("the Trip line is the combined median; its hover the three lines, each counted member and the damage over the trip; never when a member never dies")
	void tripLine() {
		Map<String, Object> trip = kill(360, 400, 450, 200, false, 0);
		trip.put("members", List.of(Map.of("name", "Blood Moon", "p50", 133.0), Map.of("name", "Blue Moon", "p50", 108.0),
			Map.of("name", "Eclipse Moon", "p50", 159.0)));
		trip.put("damage", 812.4);
		String[] line = ResultCards.tripLine(trip);
		assertEquals("Trip ~6:40", line[0]);
		assertEquals("<html>Median 6:40<br>Usually 6:00-7:30<br>Perfect 3:20<br>Blood Moon 2:13, Blue Moon 1:48, Eclipse Moon 2:39<br>~812 damage per trip</html>", line[1]);
		Map<String, Object> never = kill(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 200, false, 0);
		never.put("never", "Eclipse Moon");
		never.put("members", List.of());
		String[] nl = ResultCards.tripLine(never);
		assertEquals("Trip never", nl[0]);
		assertTrue(nl[1].contains("Eclipse Moon"), nl[1]);
	}
}
