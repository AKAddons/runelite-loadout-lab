package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The leaf-bladed battleaxe deals +17.5% damage to turoths and kurasks
 * (wiki-verified passive, stacks with the slayer helmet; 2026-07 player
 * audit A2.4). Damage only - accuracy is untouched. Per the pool lesson,
 * the conditional also needs a candidate-score boost and an
 * optimizer-level test proving the axe actually gets PICKED.
 */
class LeafBladedTest {
	private static Tables data;
	private static Monster kurask;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		kurask = data.searchMonsters("kurask", 1).get(0);
		assertTrue(kurask.hasAttribute("leafy"), "test premise: kurask is leafy");
	}

	private static Gear byName(String lname) {
		for (Gear item : data.getGearItems()) {
			if (item.getLname().equals(lname)) {
				return item;
			}
		}
		throw new AssertionError("corpus is missing: " + lname);
	}

	private static Dps calc(Monster monster, Gear weapon) {
		Req request = new Req(monster,
			Style.MELEE, Levels.MAXED, PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, true,
			Owned.EMPTY, RequirementProfile.MAXED, 1);
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, weapon);
		return new Calc().calculate(request, new Kit(gear));
	}

	@Test
	@DisplayName("the battleaxe hits 17.5% harder at a kurask than its raw stats say")
	void damageBoostApplies() {
		Gear axe = byName("leaf-bladed battleaxe");
		Gear sword = byName("leaf-bladed sword");

		// Same weapon vs a non-leafy target of any defence: max hit is a pure
		// function of strength gear, so the leafy bump shows in maxHit alone.
		Monster goblin = data.searchMonsters("goblin", 1).get(0);
		int plainMax = calc(goblin, axe).getMaxHit();
		int leafyMax = calc(kurask, axe).getMaxHit();
		assertEquals(plainMax * 47 / 40, leafyMax,
			"kurask max hit must be the plain max hit boosted 17.5%");

		// The sword carries no such passive - identical max hit both places.
		assertEquals(calc(goblin, sword).getMaxHit(), calc(kurask, sword).getMaxHit());
	}

	@Test
	@DisplayName("the optimizer PICKS the battleaxe at kurask (pool lesson)")
	void optimizerPicksTheAxe() {
		Req request = new Req(kurask,
			Style.MELEE, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, true,
			Owned.EMPTY, RequirementProfile.MAXED, 3);
		List<Dps> results = new LoadoutOptimizer().optimize(data, request);
		assertFalse(results.isEmpty());
		assertEquals("leaf-bladed battleaxe",
			results.get(0).getLoadout().getWeapon().getLname(),
			"the +17.5% must make the axe the melee pick, not just a dps footnote");
	}
}
