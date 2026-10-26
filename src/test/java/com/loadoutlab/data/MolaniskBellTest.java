package com.loadoutlab.data;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Molanisks bring a slayer bell (ISS-01M5TWMH6S39DRE2HT1JK8MMMT, Andrew 2026-10-25: "not seeing slayer
 * bell suggested for molanisks"; wiki: "A Slayer bell is necessary to initiate
 * combat with them").
 */
class MolaniskBellTest {
	private static final int SLAYER_BELL = 10952;
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static List<Map<String, Object>> carried(String mob, boolean ownsBell) {
		return RequiredGear.carriedPicks(data.searchMonsters(mob, 1).get(0), id -> ownsBell && id == SLAYER_BELL);
	}

	@Test
	@DisplayName("a molanisk carries the slayer bell - owned when you own one, flagged when you don't")
	void molaniskBell() {
		Map<String, Object> owned = carried("molanisk", true).get(0);
		assertEquals(SLAYER_BELL, owned.get("id"));
		assertEquals(Boolean.TRUE, owned.get("owned"));
		Map<String, Object> unowned = carried("molanisk", false).get(0);
		assertEquals(SLAYER_BELL, unowned.get("id"));
		assertEquals(Boolean.FALSE, unowned.get("owned"));
	}

	@Test
	@DisplayName("a gargoyle carries no bell")
	void gargoyleNoBell() {
		assertTrue(carried("gargoyle", true).stream().noneMatch(r -> Integer.valueOf(SLAYER_BELL).equals(r.get("id"))));
	}
}
