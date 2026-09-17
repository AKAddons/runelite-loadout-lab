package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Andrew, 2026-09-17: "lizardman shamans need to have the level 5 shayzien
 * armor to protect from their poison (when outside of cox)". Wiki: a full
 * tier-5 set negates the acid attack. */
class ShamanShayzienTest
{
	@Test
	@DisplayName("outside CoX the owned melee set wears all five tier-5 Shayzien pieces over better-statted armour; a missing piece frees only its slot")
	void fullSetRequired()
	{
		LoadoutData data = new DataService().load();
		MonsterStats temple = data.searchMonsters("Lizardman shaman", 5).stream()
			.filter(m -> "Lizardman Temple".equals(m.getVersion())).findFirst().orElseThrow();
		Map<Integer, Integer> owned = new HashMap<>();
		for (int id : new int[]{4151, 10828, 4131, 11832, 11834, 7462, 13379, 13381, 13380, 13377, 13378})
		{
			owned.put(id, 1); // whip, helm of neitiznot, rune boots, bandos chest + tassets, barrows gloves, Shayzien (5) x5
		}
		OptimizationRequest request = TestRequests.of(temple, CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0, CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), 1);
		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request);
		assertFalse(results.isEmpty(), "a usable set exists");
		Loadout worn = results.get(0).getLoadout();
		for (GearSlot slot : new GearSlot[]{GearSlot.HEAD, GearSlot.BODY, GearSlot.LEGS, GearSlot.HANDS, GearSlot.FEET})
		{
			GearItem piece = worn.get(slot);
			assertTrue(piece != null && piece.getNameLower().startsWith("shayzien"), slot + ": " + piece);
		}
		owned.remove(13378); // lose the boots: the set is incomplete
		List<DpsResult> without = new LoadoutOptimizer().optimize(data,
			TestRequests.of(temple, CombatStyle.MELEE, PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
				null, 0, CandidateMode.OWNED_ONLY, true, false, new OwnedItems(owned, true), 1));
		// A missing piece never empties the card (the required-gear rule since
		// 2026-08-08: an unowned protection falls back to the unconstrained
		// hunt and the note explains the gap); the four owned pieces stay.
		assertFalse(without.isEmpty(), "a missing piece never empties the card");
		Loadout partial = without.get(0).getLoadout();
		assertEquals("rune boots", partial.get(GearSlot.FEET).getNameLower());
		assertTrue(partial.get(GearSlot.HEAD).getNameLower().startsWith("shayzien"));
	}
}
