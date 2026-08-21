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
		// The document must survive gson under the JDK module system - the
		// private Collections$*/List.of types throw InaccessibleObjectException
		// (field bug 2026-07-23: the button silently died on serialize).
		assertDoesNotThrow(() -> new com.google.gson.Gson().toJson(payload));
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

		// The one multi-ordinal row - pins the JSON array parse in
		// wiki_calc_ids.json's potionIds.
		Map<String, Object> twoPotions = WikiCalcLink.payload(monster, best, -1,
			"Attack & strength potions", PlayerLevels.MAXED, PlayerLevels.MAXED,
			false, false);
		Map<String, Object> twoLoadout =
			((List<Map<String, Object>>) twoPotions.get("loadouts")).get(0);
		assertEquals(List.of(1, 10),
			((Map<String, Object>) twoLoadout.get("buffs")).get("potions"),
			"attack & strength potions = their ordinals 1 and 10");
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("the blowpipe's dart rides the weapon's itemVars, never the ammo slot")
	void blowpipeDartRidesItemVars()
	{
		// Their harness note (2026-08-06): an ammo-slot dart is a
		// hand-thrown weapon; field report 2026-08-21: the cow payload
		// carried a dartless blowpipe (the blessing owns the ammo slot).
		MonsterStats monster = data.searchMonsters("cow", 1).get(0);
		OptimizationRequest request = TestRequests.of(monster, CombatStyle.RANGED,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null,
			0, com.loadoutlab.engine.CandidateMode.ALL_STANDARD, false, false,
			OwnedItems.EMPTY, 1);
		DpsResult best = new LoadoutOptimizer().optimize(data, request).get(0);

		int dragonDart = 11230;
		Map<String, Object> payload = WikiCalcLink.payload(monster, best, dragonDart,
			"Rigour + Divine ranging potion", PlayerLevels.MAXED, PlayerLevels.MAXED,
			false, false);
		Map<String, Object> equipment = (Map<String, Object>)
			((List<Map<String, Object>>) payload.get("loadouts")).get(0).get("equipment");
		Map<String, Object> weapon = (Map<String, Object>) equipment.get("weapon");
		Map<String, Object> vars = (Map<String, Object>) weapon.get("itemVars");
		assertNotNull(vars, "the dart travels on the weapon");
		assertEquals(dragonDart, vars.get("blowpipeDartId"));
		Map<String, Object> ammo = (Map<String, Object>) equipment.get("ammo");
		if (ammo != null)
		{
			assertNotEquals(dragonDart, ammo.get("id"),
				"the ammo slot never carries the dart");
		}
	}

	@SuppressWarnings("unchecked")
	@Test
	@DisplayName("a powered staff exports Accurate - their category has no Autocast")
	void poweredStaffCastsAccurate()
	{
		// Field report 2026-08-21: trident vs cow opened at 5 ticks
		// (Longrange fallback) - exactly 4/5 of our dps. A powered
		// staff's attackType wears the autocast prefix but carries no
		// spell name; the payload must land stance Accurate.
		MonsterStats monster = data.searchMonsters("cow", 1).get(0);
		OptimizationRequest request = TestRequests.of(monster, CombatStyle.MAGIC,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null,
			0, com.loadoutlab.engine.CandidateMode.ALL_STANDARD, false, false,
			OwnedItems.EMPTY, 1);
		DpsResult best = new LoadoutOptimizer().optimize(data, request).get(0);
		assertTrue(best.getSpellName() == null || best.getSpellName().isEmpty(),
			"the magic best vs a cow is a powered staff (no spell)");

		Map<String, Object> payload = WikiCalcLink.payload(monster, best, -1,
			"Augury + Saturated heart", PlayerLevels.MAXED, PlayerLevels.MAXED,
			false, false);
		Map<String, String> style = (Map<String, String>)
			((List<Map<String, Object>>) payload.get("loadouts")).get(0).get("style");
		assertEquals("magic", style.get("type"));
		assertEquals("Accurate", style.get("stance"));
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

	@Test
	@DisplayName("the payload carries the invocation and maps synthetic ids to real rows")
	void invocationAndSyntheticIds()
	{
		MonsterStats warden = data.searchMonsters("tumeken's warden", 8).stream()
			.filter(m -> "Enraged".equals(m.getVersion()))
			.findFirst().orElseThrow();
		MonsterStats at300 = warden.withToaInvocation(300);
		OptimizationRequest request = TestRequests.of(at300, CombatStyle.RANGED,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null, 0, com.loadoutlab.engine.CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, 1);
		DpsResult best = new LoadoutOptimizer().optimize(data, request).get(0);

		Map<String, Object> payload = WikiCalcLink.payload(at300, best, -1,
			"Rigour + Divine ranging potion", PlayerLevels.MAXED, PlayerLevels.MAXED,
			false, false);
		@SuppressWarnings("unchecked")
		Map<String, Object> monsterDoc = (Map<String, Object>) payload.get("monster");
		@SuppressWarnings("unchecked")
		Map<String, Object> inputs = (Map<String, Object>) monsterDoc.get("inputs");
		assertEquals(300, inputs.get("toaInvocationLevel"),
			"the calculator must open at the card's raid level");

		// A synthetic group phase exports its REAL id - the calc only
		// knows real rows.
		MonsterStats synthetic = warden.immuneVariant(
			com.loadoutlab.data.MonsterStats.SYNTHETIC_ID_BASE + warden.getId() * 10 + 1,
			"P2 (test)", "immune_melee");
		Map<String, Object> syntheticPayload = WikiCalcLink.payload(synthetic, best, -1,
			"Rigour", PlayerLevels.MAXED, PlayerLevels.MAXED, false, false);
		@SuppressWarnings("unchecked")
		Map<String, Object> syntheticDoc = (Map<String, Object>) syntheticPayload.get("monster");
		assertEquals(warden.getId(), syntheticDoc.get("id"));
	}
}
