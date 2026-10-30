package com.loadoutlab.engine;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kill time (ISS-01M6415DTE84ZZ45C4YD3MZRBJ, Andrew 2026-10-29: "make the
 * time to kill as low as possible"; "P50 is what is displayed, the other
 * stuff is shown when hovered"; "it should be the statistical P90"; "90% of
 * runs fall between"): the expected time, and the table behind the card's
 * P05, median, P95 and Perfect.
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
	@DisplayName("the median sits within 3% of hp over net DPS on a uniform 0.7-accuracy case, P05 <= P50 <= P95 with P95 the slower end, and a heal stretches all three")
	void percentiles() {
		double dps = 0.7 * 20 / 2.4;
		KillTime t = KillTime.table(500, 0.7, 40, 4, dps, 0, 0);
		double expected = 500 / dps;
		assertEquals(expected, t.p50, expected * 0.03);
		assertTrue(t.p05 <= t.p50 && t.p50 <= t.p95, t.p05 + " " + t.p50 + " " + t.p95);
		assertTrue(t.p05 < t.p95, "a real band");
		assertTrue(t.perfect < t.p05, "nobody beats perfect");
		KillTime healed = KillTime.table(500, 0.7, 40, 4, dps, 1.5, 0);
		assertTrue(healed.p05 > t.p05 && healed.p50 > t.p50 && healed.p95 > t.p95);
		assertEquals(500 / (dps - 1.5), healed.p50, 500 / (dps - 1.5) * 0.03);
		assertEquals(t.perfect, healed.perfect, 1e-9, "Perfect never lets the boss heal");
	}

	@Test
	@DisplayName("the percentiles are statistical: 5% and 95% of the table's kill mass lie at or before them")
	void statistical() {
		KillTime t = KillTime.table(500, 0.7, 40, 4, 0.7 * 20 / 2.4, 0, 0);
		double before05 = 0, before95 = 0, at05 = 0, at95 = 0;
		for (int tick = 0; tick < t.pmf.length; tick++) {
			double s = tick * KillTime.TICK;
			if (s < t.p05) before05 += t.pmf[tick];
			if (s <= t.p05) at05 += t.pmf[tick];
			if (s < t.p95) before95 += t.pmf[tick];
			if (s <= t.p95) at95 += t.pmf[tick];
		}
		assertTrue(before05 < 0.05 && at05 >= 0.05, before05 + " " + at05);
		assertTrue(before95 < 0.95 && at95 >= 0.95, before95 + " " + at95);
	}

	@Test
	@DisplayName("a set that cannot outpace the heal reads never on every percentile, Perfect still finite")
	void never() {
		KillTime t = KillTime.table(500, 0.7, 40, 4, 1, 2, 0);
		assertTrue(Double.isInfinite(t.p50) && Double.isInfinite(t.p05) && Double.isInfinite(t.p95));
		assertEquals(31.2, t.perfect, 1e-9);
	}

	@Test
	@DisplayName("without a distribution, a hit model that is not one uniform roll is scaled to its mean and flagged approximate")
	void approximate() {
		double uniform = 0.7 * 20 / 2.4;
		KillTime exact = KillTime.table(500, 0.7, 40, 4, uniform, 0, 0);
		KillTime scythe = KillTime.table(500, 0.7, 40, 4, uniform * 1.6, 0, 0);
		assertTrue(scythe.approximate);
		assertFalse(exact.approximate);
		assertEquals(500 / (uniform * 1.6), scythe.p50, 500 / (uniform * 1.6) * 0.03);
	}

	@Test
	@DisplayName("with the set's own distribution the table is exact: a scythe's three rolls kill in a tighter band than one paced uniform roll of the same mean, Perfect at the three maxes")
	void distribution() {
		double[] one = HitDist.armoured(0.7, 0, 40, 0);
		double[] scythe = HitDist.plus(HitDist.plus(one, HitDist.armoured(0.7, 0, 20, 0)), HitDist.armoured(0.7, 0, 10, 0));
		double dps = HitDist.mean(scythe) / 3.0;
		KillTime exact = KillTime.table(500, scythe, 3.0, dps, 0, 0, false);
		KillTime paced = KillTime.table(500, 0.7, 40, 5, dps, 0, 0);
		assertFalse(exact.approximate);
		assertTrue(paced.approximate);
		// The median lands on an attack boundary (3 s apart) and a kill
		// overshoots the hp by part of a hit, so it sits within one attack
		// above the mean.
		assertTrue(exact.p50 >= 500 / dps - 1e-9 && exact.p50 <= 500 / dps + 3.0, exact.p50 + " vs " + 500 / dps);
		assertEquals(Math.ceil(500 / 70.0) * 3.0, exact.perfect, 1e-9);
		assertTrue(exact.p95 - exact.p05 < paced.p95 - paced.p05, exact.p05 + ".." + exact.p95 + " vs " + paced.p05 + ".." + paced.p95);
	}

	@Test
	@DisplayName("a trip is the members' tables convolved: median near the sum of their means, Perfect the sum of theirs, never when one member never dies")
	void trip() {
		double dps = 0.7 * 20 / 2.4;
		KillTime a = KillTime.table(500, 0.7, 40, 4, dps, 0, 0);
		KillTime b = KillTime.table(300, 0.7, 40, 4, dps, 0, 0);
		KillTime c = KillTime.table(400, 0.7, 40, 4, dps, 0, 0);
		KillTime trip = KillTime.sum(List.of(a, b, c));
		double mean = 1200 / dps;
		assertEquals(mean, trip.p50, mean * 0.03);
		assertEquals(a.perfect + b.perfect + c.perfect, trip.perfect, 1e-9);
		assertTrue(trip.p05 >= a.p05 + b.p05 + c.p05, "the sum's P05 is above the sum of P05s");
		assertTrue(trip.p95 <= a.p95 + b.p95 + c.p95, "and its P95 below the sum of P95s");
		KillTime never = KillTime.sum(List.of(a, KillTime.table(500, 0.7, 40, 4, 1, 2, 0)));
		assertTrue(Double.isInfinite(never.p50));
	}
}
