package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import com.loadoutlab.data.RequiredGear;
import com.loadoutlab.engine.*;
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
 * Elling (Discord 2026-10-30): "Hespori isnt putting a fast range weapon to
 * kill the sprouts"; AKAddons: "i'll make sure it puts a 2 tick range weapon
 * in" (ISS-01M65N6WS5N2V9TQC0MJ03NKY8). A swap rule may name a kind: the best
 * owned ranged weapon that fires at 2 ticks on rapid rides the inventory.
 */
class HesporiFastSwapTest {
	private static LoadoutData data;
	private static Monster hespori;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		hespori = data.searchMonsters("hespori", 1).get(0);
	}

	private static int id(String name) {
		return data.getGearItems().stream().filter(g -> g.getNameLower().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError(name)).getId();
	}

	private static Map<Style, OptimizerService.StyleResult> search(Map<Integer, Integer> owned) throws Exception {
		ComputeParams p = new ComputeParams();
		p.maxSwaps = 2;
		OptimizerService service = new OptimizerService(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, OptimizerService.StyleResult>> out = new AtomicReference<>();
			service.bestPerStyle(hespori, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
				RequirementProfile.MAXED, new OwnedItems(owned, true), 7, p, Collections.emptyMap(),
				Collections.emptySet(), Collections.emptyMap(), null, 0, Collections.<Integer>emptySet(), r -> {
					out.set(r);
					done.countDown();
				});
			assertTrue(done.await(300, TimeUnit.SECONDS));
			return out.get();
		} finally {
			service.shutdown();
		}
	}

	private static boolean fast(Gear g) { return g.speed == 3 && g.isWeaponFor(Style.RANGED); }

	@Test
	@DisplayName("Hespori's swap rule is the fast ranged kind; Cerberus' stays an item")
	void rule() {
		assertEquals("fastRanged", RequiredGear.swaps(hespori).get(0).category);
		assertNull(RequiredGear.swaps(data.searchMonsters("cerberus", 1).get(0)).get(0).category);
	}

	@Test
	@DisplayName("a melee bank with rune knives and adamant darts carries the better of them to Hespori, with the rule's note; the BiS side carries the game's best 2-tick weapon")
	void knivesRide() throws Exception {
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"abyssal whip", "avernic defender", "bandos chestplate", "bandos tassets", "rune knife", "adamant dart"}) {
			owned.put(id(name), 1);
		}
		OptimizerService.StyleResult melee = search(owned).get(Style.MELEE);
		List<Gear> fastOnes = melee.bench.stream().filter(HesporiFastSwapTest::fast).collect(java.util.stream.Collectors.toList());
		assertEquals(1, fastOnes.size(), "one fast ranged weapon rides: " + melee.bench);
		assertEquals(id("rune knife"), fastOnes.get(0).id, "the knives out-hit the darts");
		assertTrue(melee.swapNotes.get(id("rune knife")).contains("sprouts"), melee.swapNotes.toString());
		assertTrue(melee.gameBench.stream().anyMatch(HesporiFastSwapTest::fast), "BiS carries one too: " + melee.gameBench);
	}

	@Test
	@DisplayName("a bank with no fast ranged weapon carries none")
	void nothingToCarry() throws Exception {
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"abyssal whip", "avernic defender", "bandos chestplate", "bandos tassets"}) {
			owned.put(id(name), 1);
		}
		OptimizerService.StyleResult melee = search(owned).get(Style.MELEE);
		assertTrue(melee.bench.stream().noneMatch(HesporiFastSwapTest::fast), melee.bench.toString());
	}
}
