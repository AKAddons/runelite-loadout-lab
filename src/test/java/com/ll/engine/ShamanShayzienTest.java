package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Andrew, 2026-09-17: "lizardman shamans need to have the level 5 shayzien
 * armor to protect from their poison (when outside of cox)". Wiki: a full
 * tier-5 set negates the acid attack. */
class ShamanShayzienTest {
	@Test
	@DisplayName("outside CoX the owned melee set wears all five tier-5 Shayzien pieces over better-statted armour; a missing piece frees only its slot")
	void fullSetRequired() {
		Tables data = new DataService().load();
		Monster temple = data.searchMonsters("Lizardman shaman", 5).stream()
			.filter(m -> "Lizardman Temple".equals(m.getVersion())).findFirst().orElseThrow();
		Map<Integer, Integer> owned = new HashMap<>();
		for (int id : new int[]{4151, 10828, 4131, 11832, 11834, 7462, 13379, 13381, 13380, 13377, 13378}) {
			owned.put(id, 1); // whip, helm of neitiznot, rune boots, bandos chest + tassets, barrows gloves, Shayzien (5) x5
		}
		Req request = TestRequests.of(temple, Style.MELEE, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0, CandidateMode.OWNED_ONLY, true, false,
			new Owned(owned, true), 1);
		List<Dps> results = new LoadoutOptimizer().optimize(data, request);
		assertFalse(results.isEmpty(), "a usable set exists");
		Kit worn = results.get(0).getLoadout();
		for (Slot slot : new Slot[]{Slot.HEAD, Slot.BODY, Slot.LEGS, Slot.HANDS, Slot.FEET}) {
			Gear piece = worn.get(slot);
			assertTrue(piece != null && piece.getLname().startsWith("shayzien"), slot + ": " + piece);
		}
		owned.remove(13378); // lose the boots: the set is incomplete
		List<Dps> without = new LoadoutOptimizer().optimize(data,
			TestRequests.of(temple, Style.MELEE, Levels.MAXED, PrayerBonuses.bestAvailable(Levels.MAXED),
				null, 0, CandidateMode.OWNED_ONLY, true, false, new Owned(owned, true), 1));
		// A missing piece never empties the card (the required-gear rule since
		// 2026-08-08: an unowned protection falls back to the unconstrained
		// hunt and the note explains the gap); the four owned pieces stay.
		assertFalse(without.isEmpty(), "a missing piece never empties the card");
		Kit partial = without.get(0).getLoadout();
		assertEquals("rune boots", partial.get(Slot.FEET).getLname());
		assertTrue(partial.get(Slot.HEAD).getLname().startsWith("shayzien"));
	}
}
