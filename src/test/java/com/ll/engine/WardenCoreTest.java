package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.EnumMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Wardens' ejected core (ToA P2, wiki-verified 2026-08-06): "Players
 * will always deal their max hit when using melee against the Warden's
 * core" - no accuracy roll, no damage roll. The certainty is the whole
 * reason the phase is a spec dump (field report 2026-08-06: "ToA is
 * missing the crucial heart phase with the DDS spec").
 */
class WardenCoreTest {
	private static Tables data;
	private static Monster core;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		core = data.searchMonsters("tumeken's warden", 8).stream()
			.filter(m -> m.getVersion().startsWith("Core-ejected"))
			.findFirst().orElseThrow();
		assertTrue(MonsterMechanics.isWardenCore(core), "test premise: the core row");
		assertEquals(1, core.getSize(), "the ejected core is a 1x1 target - the"
			+ " Warden-frame size 5 handed the chally sweep a phantom second hit"
			+ " (field report 2026-08-06)");
	}

	private static Gear byName(String lname) {
		for (Gear item : data.getGearItems()) {
			if (item.getLname().equals(lname)) {
				return item;
			}
		}
		throw new AssertionError("corpus is missing: " + lname);
	}

	private static Kit wielding(String lname) {
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, byName(lname));
		return new Kit(gear);
	}

	private static Req req(Monster monster) {
		return TestRequests.of(monster,
			Style.MELEE, Levels.MAXED, PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false, Owned.EMPTY, 1);
	}

	@Test
	@DisplayName("melee at the core always deals its max: accuracy 1, expectation = max")
	void meleeAlwaysMaxes() {
		Dps whip = new Calc().calculate(req(core), wielding("abyssal whip"));
		assertNotNull(whip);
		assertEquals(1.0, whip.getAccuracy(), 1e-9, "no accuracy roll at the core");
		assertEquals(whip.getMaxHit(), whip.getEh(), 1e-9,
			"no damage roll either - every hit is the max");
		assertEquals(whip.getMaxHit() / (whip.getAttackSpeed() * 0.6),
			whip.getDps(), 1e-9);
		assertTrue(whip.getCountedBonuses().stream().anyMatch(b -> b.contains("core")),
			"the certainty must be a COUNTED assumption, visible in the assurance line");
	}

	@Test
	@DisplayName("the DDS spec prices as both boosted hits at max - the classic dump")
	void ddsSpecIsTwoMaxHits() {
		Dps base = new Calc().calculate(req(core), wielding("dragon dagger"));
		assertNotNull(base);
		SpecialAttack spec = SpecialAttack.match(byName("dragon dagger"));
		assertNotNull(spec, "the corpus must carry the DDS spec");
		double expected = spec.expectedDamage(base, core, Levels.MAXED);
		assertEquals(2.0 * (int) (base.getMaxHit() * 1.15), expected, 1e-9,
			"two independent hits, both at their boosted max, both landing");
	}

	@Test
	@DisplayName("the always-max rule stays at the core - a goblin still rolls normally")
	void ruleIsScopedToTheCore() {
		Monster goblin = data.searchMonsters("goblin", 1).get(0);
		Dps base = new Calc().calculate(req(goblin), wielding("dragon dagger"));
		assertNotNull(base);
		SpecialAttack spec = SpecialAttack.match(byName("dragon dagger"));
		double expected = spec.expectedDamage(base, goblin, Levels.MAXED);
		assertTrue(expected < 2.0 * (int) (base.getMaxHit() * 1.15),
			"away from the core the rolls are real again");
	}

}
