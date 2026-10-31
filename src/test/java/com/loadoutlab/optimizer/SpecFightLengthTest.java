package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import com.loadoutlab.engine.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * blank________ (YouTube 2026-10-18): "it suggests the crystal hally
 * over dragon claws for hespori". The spec model prices sustained energy
 * (regen over the kill); a fight you enter once with a full bar - one
 * Hespori per seed - gets the opening 100% as well, so burst specs win
 * there while a grind keeps the sustained answer. Design:
 * DES-01M5914JFX6HHTNSAP0HANN5GA.
 */
class SpecFightLengthTest {
	private static final int WHIP = 4151;
	private static final int DRAGON_CLAWS = 13652;
	private static final int CRYSTAL_HALBERD = 23987;
	private static LoadoutData data;
	private static OptimizerService service;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		service = new OptimizerService(data);
	}

	@AfterAll
	static void stop() {
		service.shutdown();
	}

	private static OptimizerService.StyleResult melee(String mobName, int pinnedSpec) throws Exception {
		Monster mob = data.searchMonsters(mobName, 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(WHIP, 1);
		owned.put(DRAGON_CLAWS, 1);
		owned.put(CRYSTAL_HALBERD, 1);
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<Style, OptimizerService.StyleResult>> out = new AtomicReference<>();
		ServiceCalls.bestPerStyle(service, mob, PlayerLevels.MAXED, PlayerLevels.MAXED,
			PrayerUnlocks.ALL, RequirementProfile.MAXED, new OwnedItems(owned, true),
			owned.hashCode() + 31 * pinnedSpec + mobName.hashCode(),
			false, false, "", Collections.emptyMap(), -1,
			OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, false, Collections.emptySet(), 0,
			Collections.emptyMap(), null, pinnedSpec, Collections.emptySet(),
			results -> { out.set(results); done.countDown(); });
		assertTrue(done.await(120, TimeUnit.SECONDS));
		return out.get().get(Style.MELEE);
	}

	private static String specName(OptimizerService.StyleResult result) {
		return result.spec == null ? "none" : result.spec.getDisplayName();
	}

	@Test
	@DisplayName("Hespori is one fight per seed: the full opening bar prices two claw specs, not a fraction")
	void hesporiCountsTheOpeningBar() throws Exception {
		OptimizerService.StyleResult auto = melee("hespori", 0);
		double claws = melee("hespori", DRAGON_CLAWS).specDpsAdded;
		double halberd = melee("hespori", CRYSTAL_HALBERD).specDpsAdded;
		// Sustained regen alone priced the whole fight under 0.21 dps added
		// (the report's card); the opening bar fits two claw specs.
		assertTrue(claws > 0.5, "claws with a fresh bar: " + claws);
		// And the arithmetic still favours the sweep on a 3x3 target: two
		// 1.1x hits for 30% beat the claws' cascade (about 1.4x max when the
		// first lands) for 50% - the wiki's best-in-slot row is a consensus
		// order, not this sum. The card shows the number so the player can
		// see why.
		assertEquals("Halberd sweep", specName(auto),
			"claws " + claws + " vs halberd " + halberd + " dps added");
		assertTrue(halberd > claws);
	}

	@Test
	@DisplayName("a grind keeps the sustained answer: abyssal demons still price regen only")
	void grindIsUnchanged() throws Exception {
		OptimizerService.StyleResult auto = melee("abyssal demon", 0);
		double claws = melee("abyssal demon", DRAGON_CLAWS).specDpsAdded;
		double halberd = melee("abyssal demon", CRYSTAL_HALBERD).specDpsAdded;
		// The sustained model's pick, pinned so the fresh bar never leaks
		// into a grind (the value it reports is the regen-only one).
		assertEquals(claws >= halberd ? "Dragon claws" : "Halberd sweep", specName(auto),
			"claws " + claws + " vs halberd " + halberd);
		assertTrue(Math.max(claws, halberd) < 0.5,
			"a 20-second grind kill fits a fraction of a spec: " + claws + " / " + halberd);
	}

	@Test
	@DisplayName("the energy budget adds the opening bar for a fresh-bar fight and nothing else")
	void freshBarAddsOneHundred() {
		assertEquals(OptimizerService.specEnergyOverKill(60, false, 0, false) + 100,
			OptimizerService.specEnergyOverKill(60, false, 0, true), 1e-9);
		assertTrue(MonsterMechanics.startsWithFullBar(data.searchMonsters("hespori", 1).get(0)));
		assertFalse(MonsterMechanics.startsWithFullBar(data.searchMonsters("abyssal demon", 1).get(0)));
	}
}
