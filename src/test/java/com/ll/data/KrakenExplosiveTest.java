package com.ll.data;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Kraken carries a fishing explosive (ISS-01M6EXEHVR0KABTTKM5697SZ10, Andrew 2026-11-02:
 * "kraken needs fishing explosives"; wiki: "To start the fight, players can use a fishing
 * explosive on the large whirlpool").
 */
class KrakenExplosiveTest {
	private static final int FISHING_EXPLOSIVE = 6664;
	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static List<Map> carried(String mob, boolean owns) {
		return RequiredGear.carriedPicks(data.searchMonsters(mob, 1).get(0), id -> owns && id == FISHING_EXPLOSIVE);
	}

	@Test
	@DisplayName("the Kraken carries a fishing explosive to wake it - owned when you own one, flagged when you don't")
	void krakenExplosive() {
		Map<String, Object> owned = carried("kraken", true).get(0);
		assertEquals(FISHING_EXPLOSIVE, owned.get("id"));
		assertEquals(Boolean.TRUE, owned.get("owned"));
		Map<String, Object> unowned = carried("kraken", false).get(0);
		assertEquals(FISHING_EXPLOSIVE, unowned.get("id"));
		assertEquals(Boolean.FALSE, unowned.get("owned"));
		assertTrue(String.valueOf(unowned.get("why")).contains("not owned"), "flagged for the trip");
	}

	@Test
	@DisplayName("a cave kraken carries none - its whirlpool is fought directly")
	void caveKrakenNone() {
		assertTrue(carried("cave kraken", true).stream()
			.noneMatch(r -> Integer.valueOf(FISHING_EXPLOSIVE).equals(r.get("id"))));
	}
}
