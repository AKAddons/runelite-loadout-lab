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
 * The bonecrusher, ash sanctifier and soul bearer ride along like the herb
 * sack (ISS-01M5TNBSP28AAHJTYWAWJHHZKB, Andrew 2026-10-25: "the soul bearer,
 * ash sanctifier, and bone crusher should all have options to be brought for
 * certain mobs that qualify for those drops").
 */
class PrayerCarryTest {
	private static final int BONECRUSHER = 13116;
	private static final int BONECRUSHER_NECKLACE = 22986;
	private static final int ASH_SANCTIFIER = 25781;
	private static final int SOUL_BEARER = 19634;
	private static final Set<Integer> ALL = Set.of(BONECRUSHER, ASH_SANCTIFIER, SOUL_BEARER);
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static List<Map<String, Object>> picks(String mob, Set<Integer> bank) {
		return LootContainers.picks(data.searchMonsters(mob, 1).get(0), bank::contains, false);
	}

	private static List<String> names(List<Map<String, Object>> picks) {
		return picks.stream().map(r -> (String) r.get("name")).collect(Collectors.toList());
	}

	@Test
	@DisplayName("an abyssal demon brings the ash sanctifier and the soul bearer, not the bonecrusher")
	void abyssalDemon() {
		List<String> names = names(picks("abyssal demon", ALL));
		assertTrue(names.contains("Ash sanctifier"), names.toString());
		assertTrue(names.contains("Soul bearer"), names.toString());
		assertFalse(names.contains("Bonecrusher"), "abyssal demons drop ashes: " + names);
	}

	@Test
	@DisplayName("a black dragon brings the bonecrusher - or the necklace - which crushes its bones")
	void blackDragon() {
		Map<String, Object> crusher = picks("black dragon", ALL).stream()
			.filter(r -> "Bonecrusher".equals(r.get("name"))).findFirst().orElseThrow(AssertionError::new);
		assertTrue(String.valueOf(crusher.get("why")).contains("crushes them"), crusher.toString());
		assertTrue(String.valueOf(crusher.get("why")).contains("about 1 per kill"), crusher.toString());
		assertTrue(names(picks("black dragon", Set.of(BONECRUSHER_NECKLACE))).contains("Bonecrusher"), "the necklace crushes too");
	}

	@Test
	@DisplayName("none owned, none shown; the herb sack still holds them")
	void ownedOnlyAndBagsUnchanged() {
		assertTrue(picks("black dragon", Set.of()).isEmpty());
		String why = String.valueOf(picks("killerwatt", Set.of(13226)).get(0).get("why"));
		assertTrue(why.endsWith("holds them"), why);
	}
}
