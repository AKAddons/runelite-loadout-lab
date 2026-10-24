package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
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
		LoadoutData data = new DataService().load();
		MonsterStats wyvern = data.searchMonsters("ancient wyvern", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		for (int id : new int[]{20997, 11212, 9185, 9144, 9731}) {
			owned.put(id, 1); // twisted bow + dragon arrows, rune crossbow + runite bolts, mind shield
		}
		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request(data, wyvern, owned));
		assertFalse(results.isEmpty(), "a usable set exists");
		Loadout worn = results.get(0).getLoadout();
		assertEquals("rune crossbow", worn.getWeapon().getNameLower(), "the two-handed bow leaves the card");
		assertEquals("mind shield", worn.get(GearSlot.SHIELD).getNameLower());

		owned.remove(9731);
		List<DpsResult> without = new LoadoutOptimizer().optimize(data, request(data, wyvern, owned));
		assertFalse(without.isEmpty(), "no breath shield owned never empties the card");
	}

	private static OptimizationRequest request(LoadoutData data, MonsterStats mob, Map<Integer, Integer> owned) {
		return TestRequests.of(mob, CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0, CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), 1);
	}
}
