package com.loadoutlab.data;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Loot containers (ISS-01M5JJ0Z4M988DR0M0ZGJHD139, Andrew 2026-10-22: "killerwatts have a
 * predominant herb drop table so i wish i had my herb sack before :)").
 */
class LootContainersTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static List<String> names(String mob, boolean wilderness, Integer... owned) {
		Set<Integer> bank = Set.of(owned);
		return LootContainers.picks(data.searchMonsters(mob, 1).get(0), bank::contains, wilderness).stream()
			.map(r -> (String) r.get("name")).collect(Collectors.toList());
	}

	@Test
	@DisplayName("killerwatts: the herb sack you own rides along, with how often herbs drop")
	void killerwattHerbSack() {
		List<Map<String, Object>> picks = LootContainers.picks(data.searchMonsters("killerwatt", 1).get(0),
			id -> id == 13226, false);
		assertEquals(1, picks.size(), picks.toString());
		assertEquals(13226, picks.get(0).get("id"));
		assertTrue(String.valueOf(picks.get(0).get("why")).contains("Herbs drop here"), picks.toString());
	}

	@Test
	@DisplayName("only containers you own, any variant (the open herb sack counts); none owned, none shown")
	void ownedOnly() {
		assertEquals(List.of(), names("killerwatt", false));
		assertEquals(List.of("Herb sack"), names("killerwatt", false, 24478));
		assertEquals(List.of(), names("killerwatt", false, 12020), "killerwatts drop too few gems for the gem bag");
	}

	@Test
	@DisplayName("a wilderness trip brings the looting bag you own")
	void lootingBag() {
		assertTrue(names("killerwatt", true, 11941).contains("Looting bag"));
		assertFalse(names("killerwatt", false, 11941).contains("Looting bag"));
	}
}
