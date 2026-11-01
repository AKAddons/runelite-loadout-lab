package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.MonsterGroups;
import com.ll.data.Monster;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KQ's phase immunity is a protection PRAYER, and Verac's set pierces it
 * (wiki: 25% guaranteed hit, +1 damage, "includes monsters that make use
 * of protection prayers"). Field report 2026-08-06: pinning full Verac's
 * rendered "No usable owned set found" because the synthetic phase
 * encoded the prayer as absolute immunity - the classic full-Verac's KQ
 * kill was impossible in the model.
 */
class KalphiteVeracTest {
	private static Tables data;
	private static Monster airborne;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		airborne = MonsterGroups.load(data).stream()
			.filter(g -> "Kalphite Queen".equals(g.getName()))
			.flatMap(g -> g.getMobs().stream())
			.filter(m -> m.getVersion().contains("no mel")
				|| m.hasAttribute("immune_melee"))
			.findFirst().orElseThrow();
		assertTrue(airborne.hasAttribute("prayer_immunity"),
			"test premise: the airborne form's immunity is prayer-based");
	}

	private static Kit verac(boolean full) {
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		for (Gear g : data.getGearItems()) {
			String n = g.getLname();
			if (!g.isStandardGear()) {
				continue;
			}
			if (n.startsWith("verac's flail")) {
				gear.put(Slot.WEAPON, g);
			}
			if (full && n.startsWith("verac's helm")) {
				gear.put(Slot.HEAD, g);
			}
			if (full && n.startsWith("verac's brassard")) {
				gear.put(Slot.BODY, g);
			}
			if (full && n.startsWith("verac's plateskirt")) {
				gear.put(Slot.LEGS, g);
			}
		}
		return new Kit(gear);
	}

	private static Req req() {
		return TestRequests.of(airborne, Style.MELEE,
			Levels.MAXED, PrayerBonuses.bestAvailable(Levels.MAXED),
			null, 0, CandidateMode.ALL_STANDARD, true, false, Owned.EMPTY, 1);
	}

	@Test
	@DisplayName("full Verac's pierces the airborne prayer at exactly the proc rate")
	void fullSetPierces() {
		Dps r = new Calc().calculate(req(), verac(true));
		assertNotNull(r, "the flail must gate melee entry through the prayer");
		assertTrue(r.getDps() > 0, "the classic full-Verac's kill must be possible");
		assertEquals(0.25, r.getAccuracy(), 1e-9, "only the proc lands");
		assertEquals(0.25 * RollMath.normalExpectedHit(1.0, r.getMaxHit()),
			r.getEh(), 0.05, "expectation is the proc quarter, +1 damage");
	}

	@Test
	@DisplayName("the flail without the set lands nothing - but stays a zero, not a null")
	void partialSetIsZeroNotNull() {
		Dps r = new Calc().calculate(req(), verac(false));
		assertNotNull(r, "partial Verac states must survive for the beam to finish the set");
		assertEquals(0.0, r.getDps(), 1e-9);
	}

	@Test
	@DisplayName("a whip is still simply immune")
	void whipStaysImmune() {
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		for (Gear g : data.getGearItems()) {
			if (g.getLname().equals("abyssal whip")) {
				gear.put(Slot.WEAPON, g);
			}
		}
		assertNull(new Calc().calculate(req(), new Kit(gear)));
	}

	@Test
	@DisplayName("the optimizer assembles the set on its own when the pieces are owned")
	void optimizerAssemblesTheSet() {
		java.util.Map<Integer, Integer> owned = new java.util.HashMap<>();
		for (Gear g : data.getGearItems()) {
			String n = g.getLname();
			if (g.isStandardGear() && (n.startsWith("verac's") || n.equals("abyssal whip")
				|| n.equals("bandos chestplate") || n.equals("bandos tassets"))) {
				owned.put(g.getId(), 1);
			}
		}
		Req request = TestRequests.of(airborne,
			Style.MELEE, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false, new Owned(owned, true), 1);
		List<Dps> out = new LoadoutOptimizer().optimize(data, request);
		assertFalse(out.isEmpty(), "an owned Verac's set must produce a melee answer");
		assertTrue(Calc.fullVeracSet(out.get(0).getLoadout()),
			"the answer must be the assembled set, was: " + out.get(0).getLoadout().getGear());
		assertTrue(out.get(0).getDps() > 0);
	}
}
