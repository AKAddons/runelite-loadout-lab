package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellStats;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lever 5 (ISS-01M62X98ZMFKDE48JJ7KFT1FQ4, Andrew 2026-10-28: "and go ahead
 * and execute lever 5"): a calculator cuts each rule list to the rules its
 * monster's gate allows, once per monster. The active-mask half measured no
 * faster and was dropped. Lever 6 (ISS-01M6320S1ZYTC9H74X8Y1Y2P09): a spell's
 * attack label and amulet marker are built once, not per magic trial.
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
	@DisplayName("one calculator meeting a hundred monsters in turn answers each exactly as a fresh one does")
	void ruleCutsAcrossManyMonsters() {
		Loadout lance = wielding(weapon("dragon hunter lance", null));
		DpsCalculator shared = new DpsCalculator();
		int met = 0;
		for (MonsterStats mob : data.monsters) {
			if (met == 100) break;
			OptimizationRequest request = new OptimizationRequest(mob, CombatStyle.MELEE, PlayerLevels.MAXED,
				PrayerBonuses.NONE, null, 0, CandidateMode.ALL_STANDARD, false, false, OwnedItems.EMPTY,
				RequirementProfile.MAXED, 10);
			DpsResult fresh = new DpsCalculator().calculate(request, lance);
			DpsResult reused = shared.calculate(request, lance);
			if (fresh == null) {
				assertNull(reused, mob.name);
				continue;
			}
			met++;
			assertEquals(fresh.dps, reused.dps, 1e-12, mob.name);
		}
		assertEquals(100, met);
	}

	@Test
	@DisplayName("a spell carries its attack label and amulet marker, built once, and a magic result uses them")
	void spellStringsBuiltOnce() {
		SpellStats surge = new SpellStats("Fire Surge", 24, 95, "standard", "fire");
		assertEquals("magic: Fire Surge", surge.attack);
		assertEquals("amulet of fire", surge.amulet);
		SpellStats bare = new SpellStats(null, 0, 1, null, null);
		assertEquals("magic: ", bare.attack, "a nameless spell");
		assertEquals("amulet of ", bare.amulet, "an elementless spell");
		SpellStats spell = data.spells.stream().filter(s -> s.name.equals("Fire Surge")).findFirst().get();
		MonsterStats mob = data.searchMonsters("general graardor", 1).get(0);
		Loadout staff = wielding(weapon("staff of fire", null));
		OptimizationRequest request = new OptimizationRequest(mob, CombatStyle.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.NONE, spell, 0, CandidateMode.ALL_STANDARD, false, false, OwnedItems.EMPTY,
			RequirementProfile.MAXED, 10);
		DpsResult result = new DpsCalculator().calculate(request, staff);
		assertSame(spell.attack, result.attackType, "the label is the spell's own string, not a fresh one");
	}

	@Test
	@DisplayName("an inactive Bow of Faerdhinen is worn but never active, asked twice each way")
	void inactiveBowIsWornNotActive() {
		Loadout bow = wielding(weapon("bow of faerdhinen", "inactive"));
		for (int ask = 0; ask < 2; ask++) {
			assertTrue(bow.wears("bow of faerdhinen", false), "worn");
			assertFalse(bow.wears("bow of faerdhinen", true), "not active");
		}
	}
}
