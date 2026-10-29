package com.loadoutlab.engine;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kill time (ISS-01M6415DTE84ZZ45C4YD3MZRBJ, Andrew 2026-10-29: "make the
 * time to kill as low as possible"; "P50 is what is displayed, the other
 * stuff is shown when hovered"; "the P100 would be interesting too
 * (mechanically perfect, all max hits)"): the expected time, and the table
 * behind the card's P10, P50, P90 and Perfect.
 */
class KillTimeTest {
	@Test
	@DisplayName("the expected kill time is hp over net DPS: the heal comes off, the sustained spec goes on, nothing left reads never")
	void expected() {
		assertEquals(100, KillTime.of(500, 5, 0, 0), 1e-9);
		assertEquals(125, KillTime.of(500, 5, 1, 0), 1e-9);
		assertEquals(100, KillTime.of(500, 4, 1, 2), 1e-9);
		assertTrue(Double.isInfinite(KillTime.of(500, 1, 1, 0)));
		assertTrue(Double.isInfinite(KillTime.of(500, 0, 0, 0)));
	}

	@Test
	@DisplayName("Perfect is every attack at its max and no heal: 500 hp, max 40, 4 ticks = 13 attacks = 31.2 s")
	void perfect() {
		KillTime t = KillTime.table(500, 0.7, 40, 4, 0.7 * 20 / 2.4, 0, 0);
		assertEquals(31.2, t.perfect, 1e-9);
		assertFalse(t.approximate);
	}

	@Test
	@DisplayName("P50 sits within 3% of hp over net DPS on a uniform 0.7-accuracy case, P10 <= P50 <= P90, and a heal stretches all three")
	void percentiles() {
		double dps = 0.7 * 20 / 2.4;
		KillTime t = KillTime.table(500, 0.7, 40, 4, dps, 0, 0);
		double expected = 500 / dps;
		assertEquals(expected, t.p50, expected * 0.03);
		assertTrue(t.p10 <= t.p50 && t.p50 <= t.p90, t.p10 + " " + t.p50 + " " + t.p90);
		assertTrue(t.p10 < t.p90, "a real band");
		assertTrue(t.perfect < t.p10, "nobody beats perfect");
		KillTime healed = KillTime.table(500, 0.7, 40, 4, dps, 1.5, 0);
		assertTrue(healed.p10 > t.p10 && healed.p50 > t.p50 && healed.p90 > t.p90);
		assertEquals(500 / (dps - 1.5), healed.p50, 500 / (dps - 1.5) * 0.03);
		assertEquals(t.perfect, healed.perfect, 1e-9, "Perfect never lets the boss heal");
	}

	@Test
	@DisplayName("a set that cannot outpace the heal reads never on every percentile, Perfect still finite")
	void never() {
		KillTime t = KillTime.table(500, 0.7, 40, 4, 1, 2, 0);
		assertTrue(Double.isInfinite(t.p50) && Double.isInfinite(t.p10) && Double.isInfinite(t.p90));
		assertEquals(31.2, t.perfect, 1e-9);
	}

	@Test
	@DisplayName("a hit model that is not one uniform roll (the dps disagrees with accuracy x max / 2) is scaled to its mean and flagged approximate")
	void approximate() {
		double uniform = 0.7 * 20 / 2.4;
		KillTime exact = KillTime.table(500, 0.7, 40, 4, uniform, 0, 0);
		KillTime scythe = KillTime.table(500, 0.7, 40, 4, uniform * 1.6, 0, 0);
		assertTrue(scythe.approximate);
		assertFalse(exact.approximate);
		assertEquals(500 / (uniform * 1.6), scythe.p50, 500 / (uniform * 1.6) * 0.03);
	}

	@Test
	@DisplayName("a trip is the members' tables convolved: P50 near the sum of their means, Perfect the sum of theirs, never when one member never dies")
	void trip() {
		double dps = 0.7 * 20 / 2.4;
		KillTime a = KillTime.table(500, 0.7, 40, 4, dps, 0, 0);
		KillTime b = KillTime.table(300, 0.7, 40, 4, dps, 0, 0);
		KillTime c = KillTime.table(400, 0.7, 40, 4, dps, 0, 0);
		KillTime trip = KillTime.sum(List.of(a, b, c));
		double mean = 1200 / dps;
		assertEquals(mean, trip.p50, mean * 0.03);
		assertEquals(a.perfect + b.perfect + c.perfect, trip.perfect, 1e-9);
		assertTrue(trip.p10 >= a.p10 + b.p10 + c.p10, "the sum's P10 is above the sum of P10s");
		assertTrue(trip.p90 <= a.p90 + b.p90 + c.p90, "and its P90 below the sum of P90s");
		KillTime never = KillTime.sum(List.of(a, KillTime.table(500, 0.7, 40, 4, 1, 2, 0)));
		assertTrue(Double.isInfinite(never.p50));
	}
}
