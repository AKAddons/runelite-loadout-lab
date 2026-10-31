package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterDefences;
import com.loadoutlab.data.MonsterOffence;
import com.loadoutlab.data.Monster;
import com.loadoutlab.engine.Style;
import com.loadoutlab.engine.ComputeParams;
import com.loadoutlab.engine.IncomingDpsCalculator;
import com.loadoutlab.engine.Loadout;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A card's damage taken rolls the boss against the levels its assumed prayer
 * gives (ISS-01M6415DFFW34P8QY468GA74KY, Andrew 2026-10-29: "Tabs + defence
 * fix"): the same prayer the card's dps assumes, the player's pick first.
 */
class IncomingPrayerTest {
	private static final Loadout NAKED = new Loadout(new EnumMap<>(GearSlot.class));
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

	private static OptimizerService.ComputeContext context(Map<Style, String> prayerPicks) {
		ComputeParams p = new ComputeParams();
		p.prayerPicks = prayerPicks;
		return service.buildContext(PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL, RequirementProfile.MAXED,
			OwnedItems.EMPTY, 1, p, Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(),
			Collections.emptyMap(), Collections.emptyMap(), Collections.<Integer>emptySet());
	}

	@Test
	@DisplayName("Blood Moon's damage taken on a melee card is the calculator's at Defence 123 under Zeal, 118 under a Chivalry pick, 99 prayerless")
	void meleeCards() {
		Monster moon = data.searchMonsters("blood moon", 1).get(0);
		double zeal = OptimizerService.incomingFor(moon, NAKED, context(Map.of()), Style.MELEE).totalDps;
		double chivalry = OptimizerService.incomingFor(moon, NAKED,
			context(Map.of(Style.MELEE, "Chivalry")), Style.MELEE).totalDps;
		double none = OptimizerService.incomingFor(moon, NAKED,
			context(Map.of(Style.MELEE, "NONE")), Style.MELEE).totalDps;
		assertEquals(IncomingDpsCalculator.calculate(moon, NAKED, 123, 99, 0).totalDps, zeal, 1e-12);
		assertEquals(IncomingDpsCalculator.calculate(moon, NAKED, 118, 99, 0).totalDps, chivalry, 1e-12);
		assertEquals(IncomingDpsCalculator.calculate(moon, NAKED, 99, 99, 0).totalDps, none, 1e-12);
		assertTrue(zeal < chivalry && chivalry < none, zeal + " < " + chivalry + " < " + none);
	}

	@Test
	@DisplayName("a mage's spells on a magic card roll against Augury's Magic and Defence: 99 and 99 read 123 and 123")
	void auguryCard() {
		Monster mage = new Monster(1, "Test mage", "", 300, 255, 1, 1, 200, 0,
			MonsterDefences.ZERO, new MonsterOffence(1, 1, 1, 200, 0, 0, 0, 0, 0, 0, 4, List.of("Magic")),
			Collections.emptyList(), false, "", 0);
		// Unprayed: Protect from Magic blocks a lone magic style outright.
		double augury = OptimizerService.incomingFor(mage, NAKED, context(Map.of()), Style.MAGIC).unprayedDps;
		assertEquals(IncomingDpsCalculator.calculate(mage, NAKED, 123, 123, 0).unprayedDps, augury, 1e-12);
		assertTrue(augury < IncomingDpsCalculator.calculate(mage, NAKED, 99, 99, 0).unprayedDps);
	}
}
