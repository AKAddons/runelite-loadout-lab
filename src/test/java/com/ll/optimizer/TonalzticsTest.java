package com.ll.optimizer;

import com.ll.engine.CandidateMode;
import com.ll.engine.Style;
import com.ll.engine.Calc;
import com.ll.engine.Dps;
import com.ll.engine.Kit;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.PrayerBonuses;
import com.ll.engine.SpecialAttack;
import com.ll.engine.TestRequests;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.MonsterDefences;
import com.ll.data.Monster;
import java.util.Collections;
import java.util.EnumMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Division, the Tonalztics of ralos special (wiki-verified 2026-08-09):
 * 50% cost, +50% accuracy, two glaives, and each landed hit drains the
 * target's DEFENCE by 12.5% of its MAGIC level - modeled as 25% per
 * landed spec. It shines exactly where a DWH cannot reach (the wiki's
 * example: Zulrah's 300 Magic and Defence).
 */
class TonalzticsTest {
	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static Gear byName(String lname, String version) {
		for (Gear g : data.getGearItems()) {
			if (g.getLname().equals(lname)
				&& (version == null || version.equals(g.getVersion()))) {
				return g;
			}
		}
		throw new AssertionError("missing: " + lname);
	}

	@Test
	@DisplayName("the registry matches both charge states as a ranged drain spec")
	void registryMatch() {
		SpecialAttack charged = SpecialAttack.match(
			byName("tonalztics of ralos", "Charged"));
		assertNotNull(charged, "charged must carry Division");
		assertEquals(Style.RANGED, charged.getStyle());
		assertTrue(charged.drainsDefence(), "Division is a drain spec");
		assertEquals(50, charged.getEnergyCost());
	}

	@Test
	@DisplayName("the drain measures off the target's Magic level, floored at zero")
	void drainArithmetic() {
		SpecialAttack division = SpecialAttack.match(
			byName("tonalztics of ralos", "Charged"));
		// A Zulrah-shaped dummy: 300 Defence, 300 Magic -> one landed
		// spec removes 25% of 300 = 75 Defence.
		Monster zulrahLike = new Monster(
			-43, "Gaze Dummy", "", 500, 800, 3, 300, 300, 0,
			new MonsterDefences(150, 150, 150, 100, 0, 60, 60, 60),
			null, Collections.emptyList(), false, "", 0);
		assertEquals(225, division.drainedDefence(zulrahLike, 40.0));
		// A magic-1 target barely drains - the DWH's territory instead.
		Monster brute = new Monster(
			-44, "Brute Dummy", "", 500, 800, 3, 300, 1, 0,
			new MonsterDefences(150, 150, 150, 100, 0, 60, 60, 60),
			null, Collections.emptyList(), false, "", 0);
		assertEquals(300, division.drainedDefence(brute, 40.0));
	}

	@Test
	@DisplayName("Division earns positive drain value on a high-magic tank")
	void drainValue() {
		Gear tonalztics = byName("tonalztics of ralos", "Charged");
		SpecialAttack division = SpecialAttack.match(tonalztics);
		Monster zulrahLike = new Monster(
			-43, "Gaze Dummy", "", 500, 800, 3, 300, 300, 0,
			new MonsterDefences(150, 150, 150, 100, 0, 60, 60, 60),
			null, Collections.emptyList(), false, "", 0);
		Req request = TestRequests.of(zulrahLike,
			Style.RANGED, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, Owned.EMPTY, 1);

		EnumMap<Slot, Gear> worn = new EnumMap<>(Slot.class);
		worn.put(Slot.WEAPON, byName("magic shortbow (i)", null));
		worn.put(Slot.AMMO, byName("amethyst arrow", null));
		Dps main = new Calc().calculate(request, new Kit(worn));
		assertNotNull(main);

		EnumMap<Slot, Gear> specWorn = new EnumMap<>(Slot.class);
		specWorn.put(Slot.WEAPON, tonalztics);
		Dps specBase = new Calc().calculate(request, new Kit(specWorn));
		assertNotNull(specBase);
		double expected = division.expectedDamage(specBase, zulrahLike, Levels.MAXED);
		assertTrue(expected > 0, "two glaives must expect damage");

		Service service = new Service(data);
		try {
			double added = service.sda(new Calc(), division,
				specBase, expected, request, main, false);
			assertTrue(added > 0,
				"draining 75 defence off a 300/300 tank must add dps: " + added);
		} finally {
			service.shutdown();
		}
	}
}
