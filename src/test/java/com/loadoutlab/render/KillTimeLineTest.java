package com.loadoutlab.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The card's kill-time lines (ISS-01M6415DTE84ZZ45C4YD3MZRBJ, ISS-01M655THHQ6JBS470AFAH0NX3T;
 * Andrew 2026-10-29: "P50 is what is displayed, the other stuff is shown when
 * hovered"; "Median: XX:XX / 90% of runs fall between: XX:XX - XX:XX
 * (P05-P95)"; "list individual and group kill times in the card
 * consecutively"). */
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
	@DisplayName("the Kill line shows the median as m:ss, seconds under a minute, never when the set cannot kill; the hover reads Median, the P05-P95 band and Perfect on three lines, then the heal and the approximate note")
	void killLine() {
		String[] line = ResultCards.killLine(kill(108, 133, 161, 52, false, 0));
		assertEquals("~2:13", line[0]);
		assertEquals("<html>Median 2:13<br>90% of runs fall between 1:48 and 2:41 (P05-P95)<br>Perfect 0:52</html>", line[1]);
		assertEquals("~43 s", ResultCards.killLine(kill(30, 43, 60, 20, false, 0))[0]);
		String[] healed = ResultCards.killLine(kill(108, 133, 161, 52, true, 1.23));
		assertTrue(healed[1].endsWith("Perfect 0:52<br>Band approximate: this hit model has no per-attack form<br>Heals ~1.2/s off this set</html>"), healed[1]);
		String[] never = ResultCards.killLine(kill(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
			Double.POSITIVE_INFINITY, 52, false, 3));
		assertEquals("never", never[0]);
		assertTrue(never[1].contains("This set cannot outpace the heal"), never[1]);
	}

	@Test
	@DisplayName("a member line is the mob's first word and its median, the full name heading its hover; the Trip line is the combined median, never when a member never dies")
	void memberAndTripLines() {
		Map<String, Object> moon = kill(108, 133, 161, 52, false, 0);
		moon.put("name", "Blood Moon");
		String[] ml = ResultCards.memberLine(moon);
		assertEquals("Blood ~2:13", ml[0]);
		assertEquals("<html>Blood Moon<br>Median 2:13<br>90% of runs fall between 1:48 and 2:41 (P05-P95)<br>Perfect 0:52</html>", ml[1]);
		Map<String, Object> jad = kill(30, 43, 60, 20, false, 0);
		jad.put("name", "TzTok-Jad - lvl 702");
		assertEquals("TzTok-Jad ~43 s", ResultCards.memberLine(jad)[0]);
		Map<String, Object> trip = kill(360, 400, 450, 200, false, 0);
		trip.put("members", List.of(moon));
		String[] line = ResultCards.tripLine(trip);
		assertEquals("Trip ~6:40", line[0]);
		assertEquals("<html>The whole trip<br>Median 6:40<br>90% of runs fall between 6:00 and 7:30 (P05-P95)<br>Perfect 3:20</html>", line[1]);
		Map<String, Object> never = kill(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, 200, false, 0);
		never.put("never", "Eclipse Moon");
		never.put("members", List.of());
		String[] nl = ResultCards.tripLine(never);
		assertEquals("Trip never", nl[0]);
		assertTrue(nl[1].contains("Eclipse Moon"), nl[1]);
	}
}
