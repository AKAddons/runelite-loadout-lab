package com.loadoutlab;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.LoadoutOptimizer;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerBonuses;
import com.loadoutlab.engine.TestRequests;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The wiki-calc shortlink payload must speak osrs-dps-calc's
 * ImportableData shape exactly (verified against their source,
 * IMPORT_VERSION 10): serializationVersion, one loadout with skills /
 * boost deltas / {id} equipment / prayer+potion enum ordinals, and a
 * monster resolved by id with mergeable inputs.
 */
class WikiCalcLinkTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("a melee result maps to their document: version 10, ids, enums, deltas")
	void meleePayloadShape()
	{
		MonsterStats monster = data.searchMonsters("general graardor", 1).get(0);
		OptimizationRequest request = TestRequests.of(monster, CombatStyle.MELEE,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null,
			0, com.loadoutlab.engine.CandidateMode.ALL_STANDARD, false, false,
			OwnedItems.EMPTY, 1);
		DpsResult best = new LoadoutOptimizer().optimize(data, request).get(0);

		Map<String, Object> payload = WikiCalcLink.payload(monster, best, -1,
			"Piety + Divine super combat", PlayerLevels.MAXED, PlayerLevels.MAXED,
			true, false);

		assertEquals(10, payload.get("serializationVersion"));
		Map<String, Object> monsterDoc = (Map<String, Object>) payload.get("monster");
		assertEquals(monster.getId(), monsterDoc.get("id"));
		assertNotNull(((Map<String, Object>) monsterDoc.get("inputs")).get("defenceReductions"),
			"their import iterates defenceReductions - it must exist");

		Map<String, Object> loadout =
			((List<Map<String, Object>>) payload.get("loadouts")).get(0);
		assertEquals(List.of(13), loadout.get("prayers"), "Piety = their ordinal 13");
		Map<String, Object> buffs = (Map<String, Object>) loadout.get("buffs");
		assertEquals(List.of(14), buffs.get("potions"),
			"divine super combat maps to their SUPER_COMBAT (no divine entry)");
		assertEquals(true, buffs.get("onSlayerTask"));

		Map<String, Integer> boosts = (Map<String, Integer>) loadout.get("boosts");
		assertEquals(19, boosts.get("atk"), "super combat at 99: floor(5 + 99*0.15)");
		assertEquals(0, boosts.get("ranged"));

		Map<String, Object> equipment = (Map<String, Object>) loadout.get("equipment");
		assertTrue(equipment.containsKey("weapon"));
		int weaponId = (Integer)
			((Map<String, Object>) equipment.get("weapon")).get("id");
		assertEquals(best.getLoadout().getWeapon().getId(), weaponId);
	}

	@Test
	@DisplayName("attack-type strings map to their {type, stance} pairs")
	void styleMapping()
	{
		assertEquals(Map.of("type", "slash", "stance", "Aggressive"),
			WikiCalcLink.styleOf("slash (aggressive)"));
		assertEquals(Map.of("type", "stab", "stance", "Controlled"),
			WikiCalcLink.styleOf("stab (controlled)"));
		assertEquals(Map.of("type", "ranged", "stance", "Rapid"),
			WikiCalcLink.styleOf("ranged rapid - dragon dart"));
		assertEquals(Map.of("type", "magic", "stance", "Autocast"),
			WikiCalcLink.styleOf("magic: Fire Surge"));
		assertEquals(Map.of("type", "magic", "stance", "Accurate"),
			WikiCalcLink.styleOf("magic"));
		assertNull(WikiCalcLink.styleOf(null));
	}
}
