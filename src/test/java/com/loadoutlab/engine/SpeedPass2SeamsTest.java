package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lever 5 and the active mask (ISS-01M62X98ZMFKDE48JJ7KFT1FQ4, Andrew 2026-10-28:
 * "and go ahead and execute lever 5"): a calculator cuts each rule list to the
 * rules its monster's gate allows, once per monster, and a set keeps its active
 * marker mask like its plain one.
 */
class SpeedPass2SeamsTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static GearItem weapon(String nameLower, String versionLower) {
		return data.getGearItems(GearSlot.WEAPON).stream()
			.filter(g -> g.nameLower.equals(nameLower) && (versionLower == null || versionLower.equals(g.versionLower)))
			.findFirst().get();
	}

	private static Loadout wielding(GearItem item) {
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, item);
		return new Loadout(gear);
	}

	@Test
	@DisplayName("one calculator re-cuts its rule lists when the monster changes: a lance on a dragon, then Graardor, then the dragon")
	void ruleCutFollowsTheMonster() {
		MonsterStats dragon = data.searchMonsters("vorkath", 1).get(0);
		MonsterStats graardor = data.searchMonsters("general graardor", 1).get(0);
		Loadout lance = wielding(weapon("dragon hunter lance", null));
		DpsCalculator shared = new DpsCalculator();
		for (MonsterStats mob : List.of(dragon, graardor, dragon)) {
			OptimizationRequest request = new OptimizationRequest(mob, CombatStyle.MELEE, PlayerLevels.MAXED,
				PrayerBonuses.NONE, null, 0, CandidateMode.ALL_STANDARD, false, false, OwnedItems.EMPTY,
				RequirementProfile.MAXED, 10);
			DpsResult fresh = new DpsCalculator().calculate(request, lance);
			DpsResult reused = shared.calculate(request, lance);
			assertEquals(fresh.dps, reused.dps, 1e-12, mob.name + ": the reused calculator matches a fresh one");
			assertEquals(mob == dragon, reused.countedBonuses.toString().contains("dragon hunter lance"),
				mob.name + ": the lance's dragon rule applies only on the dragon");
		}
	}

	@Test
	@DisplayName("a set keeps its active mask: an inactive Bow of Faerdhinen is worn but never active, asked twice")
	void activeMaskIsKept() {
		Loadout bow = wielding(weapon("bow of faerdhinen", "inactive"));
		for (int ask = 0; ask < 2; ask++) {
			assertTrue(bow.wears("bow of faerdhinen", false), "worn");
			assertFalse(bow.wears("bow of faerdhinen", true), "not active");
		}
		assertNotNull(bow.activeMarks, "the active mask is built once and kept");
	}
}
