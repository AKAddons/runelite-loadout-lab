package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Andrew, 2026-10-15: "for the ancient wyvern it's not recommending wyvern
 * shield". Wiki (Skeletal Wyvern): an elemental, mind, dragonfire shield or
 * ward, or the ancient wyvern shield cuts the icy breath's max hit to 10;
 * Protect from Magic alone only brings it to 20. */
class WyvernShieldTest {
	@Test
	@DisplayName("the ancient wyvern's owned ranged set wears a breath shield with a one-handed weapon over a two-handed bow; without one the card keeps an answer")
	void breathShieldWins() {
		Tables data = new DataService().load();
		Monster wyvern = data.searchMonsters("ancient wyvern", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		for (int id : new int[]{20997, 11212, 9185, 9144, 9731}) {
			owned.put(id, 1); // twisted bow + dragon arrows, rune crossbow + runite bolts, mind shield
		}
		List<Dps> results = new LoadoutOptimizer().optimize(data, request(data, wyvern, owned));
		assertFalse(results.isEmpty(), "a usable set exists");
		Kit worn = results.get(0).getLoadout();
		assertEquals("rune crossbow", worn.getWeapon().getLname(), "the two-handed bow leaves the card");
		assertEquals("mind shield", worn.get(Slot.SHIELD).getLname());

		owned.remove(9731);
		List<Dps> without = new LoadoutOptimizer().optimize(data, request(data, wyvern, owned));
		assertFalse(without.isEmpty(), "no breath shield owned never empties the card");
	}

	private static Req request(Tables data, Monster mob, Map<Integer, Integer> owned) {
		return TestRequests.of(mob, Style.RANGED, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0, CandidateMode.OWNED_ONLY, true, false,
			new Owned(owned, true), 1);
	}
}
