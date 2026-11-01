package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.data.Spell;
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
	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static Gear weapon(String lname, String ver) {
		return data.getGearItems(Slot.WEAPON).stream()
			.filter(g -> g.lname.equals(lname) && (ver == null || ver.equals(g.ver)))
			.findFirst().get();
	}

	private static Kit wielding(Gear item) {
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, item);
		return new Kit(gear);
	}

	@Test
	@DisplayName("one calculator re-cuts its rule lists when the monster changes: a lance on a dragon, then Graardor, then the dragon")
	void ruleCutFollowsTheMonster() {
		Monster dragon = data.searchMonsters("vorkath", 1).get(0);
		Monster graardor = data.searchMonsters("general graardor", 1).get(0);
		Kit lance = wielding(weapon("dragon hunter lance", null));
		Calc shared = new Calc();
		for (Monster mob : List.of(dragon, graardor, dragon)) {
			Req request = new Req(mob, Style.MELEE, Levels.MAXED,
				PrayerBonuses.NONE, null, 0, CandidateMode.ALL_STANDARD, false, false, Owned.EMPTY,
				RequirementProfile.MAXED, 10);
			Dps fresh = new Calc().calculate(request, lance);
			Dps reused = shared.calculate(request, lance);
			assertEquals(fresh.dps, reused.dps, 1e-12, mob.name + ": the reused calculator matches a fresh one");
			assertEquals(mob == dragon, reused.countedBonuses.toString().contains("dragon hunter lance"),
				mob.name + ": the lance's dragon rule applies only on the dragon");
		}
	}

	@Test
	@DisplayName("one calculator meeting a hundred monsters in turn answers each exactly as a fresh one does")
	void ruleCutsAcrossManyMonsters() {
		Kit lance = wielding(weapon("dragon hunter lance", null));
		Calc shared = new Calc();
		int met = 0;
		for (Monster mob : data.monsters) {
			if (met == 100) break;
			Req request = new Req(mob, Style.MELEE, Levels.MAXED,
				PrayerBonuses.NONE, null, 0, CandidateMode.ALL_STANDARD, false, false, Owned.EMPTY,
				RequirementProfile.MAXED, 10);
			Dps fresh = new Calc().calculate(request, lance);
			Dps reused = shared.calculate(request, lance);
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
		Spell surge = new Spell("Fire Surge", 24, 95, "standard", "fire");
		assertEquals("magic: Fire Surge", surge.attack);
		assertEquals("amulet of fire", surge.amulet);
		Spell bare = new Spell(null, 0, 1, null, null);
		assertEquals("magic: ", bare.attack, "a nameless spell");
		assertEquals("amulet of ", bare.amulet, "an elementless spell");
		Spell spell = data.spells.stream().filter(s -> s.name.equals("Fire Surge")).findFirst().get();
		Monster mob = data.searchMonsters("general graardor", 1).get(0);
		Kit staff = wielding(weapon("staff of fire", null));
		Req request = new Req(mob, Style.MAGIC, Levels.MAXED,
			PrayerBonuses.NONE, spell, 0, CandidateMode.ALL_STANDARD, false, false, Owned.EMPTY,
			RequirementProfile.MAXED, 10);
		Dps result = new Calc().calculate(request, staff);
		assertSame(spell.attack, result.attackType, "the label is the spell's own string, not a fresh one");
	}

	@Test
	@DisplayName("an inactive Bow of Faerdhinen is worn but never active, asked twice each way")
	void inactiveBowIsWornNotActive() {
		Kit bow = wielding(weapon("bow of faerdhinen", "inactive"));
		for (int ask = 0; ask < 2; ask++) {
			assertTrue(bow.wears("bow of faerdhinen", false), "worn");
			assertFalse(bow.wears("bow of faerdhinen", true), "not active");
		}
	}
}
