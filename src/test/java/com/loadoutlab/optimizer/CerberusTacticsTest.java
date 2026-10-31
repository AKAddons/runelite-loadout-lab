package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import com.loadoutlab.data.RequiredGear;
import com.loadoutlab.engine.*;
import com.loadoutlab.model.CommandEngine;
import com.loadoutlab.model.CompanionLink;
import com.loadoutlab.model.PageState;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cerberus' summoned souls (ISS-01M5VCXYGF51P5NJCDNC1QJZD1, Andrew 2026-10-25: "the ability to combat
 * the ghost ability by using ward of arceuss and the spectral spirit shield are
 * things we should be considering by default. maybe bump the inventory default
 * swaps on cerberus up if we need"). Wiki: a soul drains 30 prayer points when
 * prayed against, 15 with the spectral spirit shield, 20 with Ward, 10 with both.
 */
class CerberusTacticsTest {
	private static final int SPECTRAL = 12821;
	private static LoadoutData data;
	private static Monster cerberus;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		cerberus = data.searchMonsters("cerberus", 1).get(0);
	}

	private static int id(String name) {
		return data.getGearItems().stream()
			.filter(g -> g.getNameLower().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError(name)).getId();
	}

	@Test
	@DisplayName("Cerberus' data: the spectral spirit shield is its swap, and Ward of Arceuus rides; Zulrah has neither")
	void rules() {
		assertEquals(List.of(SPECTRAL), RequiredGear.swapIds(cerberus));
		assertTrue(RequiredGear.swapNote(SPECTRAL).contains("summoned souls"));
		assertTrue(RequiredGear.ward(cerberus));
		Monster zulrah = data.searchMonsters("zulrah", 1).get(0);
		assertTrue(RequiredGear.swapIds(zulrah).isEmpty());
		assertFalse(RequiredGear.ward(zulrah));
	}

	@Test
	@DisplayName("owning the spectral spirit shield, Yours melee at Cerberus carries it in the inventory beside the set")
	void shieldRidesTheBench() throws Exception {
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"emberlight", "avernic defender", "bandos chestplate", "bandos tassets",
			"spectral spirit shield"}) {
			owned.put(id(name), 1);
		}
		ComputeParams p = new ComputeParams();
		p.maxSwaps = 2;
		OptimizerService service = new OptimizerService(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, OptimizerService.StyleResult>> out = new AtomicReference<>();
			service.bestPerStyle(cerberus, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
				RequirementProfile.MAXED, new OwnedItems(owned, true), 7, p, Collections.emptyMap(),
				Collections.emptySet(), Collections.emptyMap(), null, 0, Collections.<Integer>emptySet(), r -> {
					out.set(r);
					done.countDown();
				});
			assertTrue(done.await(300, TimeUnit.SECONDS));
			List<Gear> bench = out.get().get(Style.MELEE).bench;
			assertTrue(bench.stream().anyMatch(g -> g.getId() == SPECTRAL), "the shield rides: " + bench);
		} finally {
			service.shutdown();
		}
	}

	@Test
	@DisplayName("a simmed spectral spirit shield rides as the swap too (ISS-01M5VG8J7FAJVEHHK2KKFEN0A9: 'i have spectral spirit shield simmed and it is not telling me to bring it')")
	void simmedShieldRides() throws Exception {
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"emberlight", "avernic defender", "bandos chestplate", "bandos tassets"}) {
			owned.put(id(name), 1);
		}
		ComputeParams p = new ComputeParams();
		p.maxSwaps = 2;
		OptimizerService service = new OptimizerService(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, OptimizerService.StyleResult>> out = new AtomicReference<>();
			service.bestPerStyle(cerberus, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
				RequirementProfile.MAXED, new OwnedItems(owned, true), 8, p, Collections.emptyMap(),
				java.util.Set.of(SPECTRAL), Collections.emptyMap(), null, 0, Collections.<Integer>emptySet(), r -> {
					out.set(r);
					done.countDown();
				});
			assertTrue(done.await(300, TimeUnit.SECONDS));
			List<Gear> bench = out.get().get(Style.MELEE).bench;
			assertTrue(bench.stream().anyMatch(g -> g.getId() == SPECTRAL), "the simmed shield rides: " + bench);
		} finally {
			service.shutdown();
		}
	}

	@Test
	@DisplayName("a Cerberus search opens with two inventory swaps, Vorkath two (its slayer's staff); Zulrah keeps one")
	void swapDefault() {
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state, (m, ps, onDone) -> {
		}, new CompanionLink());
		engine.execute("select", Map.of("id", cerberus.getId()));
		assertEquals(2, ((Number) state.paramsNode().get("maxSwaps")).intValue());
		engine.execute("select", Map.of("id", data.searchMonsters("vorkath", 1).get(0).getId()));
		assertEquals(2, ((Number) state.paramsNode().get("maxSwaps")).intValue());
		engine.execute("select", Map.of("id", data.searchMonsters("zulrah", 1).get(0).getId()));
		assertEquals(1, ((Number) state.paramsNode().get("maxSwaps")).intValue());
	}
}
