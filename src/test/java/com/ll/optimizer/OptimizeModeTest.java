package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.CandidateMode;
import com.ll.engine.Style;
import com.ll.engine.Dps;
import com.ll.engine.Incoming;
import com.ll.engine.LoadoutOptimizer;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.PrayerBonuses;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
import com.ll.engine.TestRequests;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The dev-mode defensive modes (ISS-01M5G5CZC9SK46499FACRSJ8ZT), Andrew's
 * definitions: Tanky = the minimum DTPS, Balanced = the best DTPS/DPS -
 * least damage taken per damage dealt. Graardor ranged is the July
 * reference case; the pool is every buyable item (budget, no bank).
 */
class OptimizeModeTest {
	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static Service.Pick run(String mob, Style style,
		Service.OptimizeMode mode) throws Exception {
		Monster monster = data.searchMonsters(mob, 1).get(0);
		Service service = new Service(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, Service.Pick>> out = new AtomicReference<>();
			com.ll.engine.ComputeParams p = new com.ll.engine.ComputeParams();
			p.specWeapon = false;
			p.upgradeBudgetGp = 2_000_000_000;
			p.optimizeMode = mode.name();
			service.bestPerStyle(monster, Levels.MAXED, Levels.MAXED, Prayers.ALL,
				RequirementProfile.MAXED, Owned.EMPTY, 1, p,
				Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), null, 0,
				Collections.<Integer>emptySet(), result -> {
					out.set(result);
					done.countDown();
				});
			assertTrue(done.await(300, TimeUnit.SECONDS), "compute finished");
			return out.get().get(style);
		} finally {
			service.shutdown();
		}
	}

	private static double dtps(String mob, Service.Pick result) {
		return Incoming.calculate(data.searchMonsters(mob, 1).get(0),
			result.owned.get(0).getLoadout(), 99, 99, 0).tot;
	}

	@Test
	@DisplayName("a defense-weighted beam can pick pure-defence gear (ring of suffering over archers ring)")
	void weightedBeamSeesDefenceGear() {
		Monster graardor = data.searchMonsters("general graardor", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(861, 1);    // magic shortbow
		owned.put(892, 500);  // rune arrows
		owned.put(11771, 1);  // archers ring (i)
		owned.put(19710, 1);  // ring of suffering (i)
		Req base = TestRequests.of(graardor, Style.RANGED, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED, Prayers.ALL), null, 0,
			CandidateMode.OWNED_ONLY, true, false, new Owned(data.canonicalizeOwned(owned), true), 1);
		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		Gear offence = optimizer.optimize(data, base).get(0).getLoadout().get(Slot.RING);
		Dps tanky = optimizer.optimize(data, base.withDefenseWeight(50)).get(0);
		assertEquals("Archers ring (i)", offence.getName());
		assertEquals("Ring of suffering (i)", tanky.getLoadout().get(Slot.RING).getName());
	}

	@Test
	@DisplayName("Tanky takes the least damage, Balanced has the best DTPS/DPS, Max DPS deals the most")
	void modesHonourTheirObjectives() throws Exception {
		String mob = "general graardor";
		Service.Pick max = run(mob, Style.RANGED, Service.OptimizeMode.MAX_DPS);
		Service.Pick balanced = run(mob, Style.RANGED, Service.OptimizeMode.BALANCED);
		Service.Pick tanky = run(mob, Style.RANGED, Service.OptimizeMode.TANKY);
		assertNull(max.modeTrade, "Max DPS makes no trade");
		double dMax = max.owned.get(0).getDps();
		assertTrue(dMax >= balanced.owned.get(0).getDps() - 1e-9);
		assertTrue(dMax >= tanky.owned.get(0).getDps() - 1e-9);
		assertTrue(dtps(mob, tanky) <= dtps(mob, max) + 1e-9);
		assertTrue(dtps(mob, tanky) <= dtps(mob, balanced) + 1e-9);
		double rMax = dtps(mob, max) / dMax;
		double rBalanced = dtps(mob, balanced) / balanced.owned.get(0).getDps();
		double rTanky = dtps(mob, tanky) / tanky.owned.get(0).getDps();
		assertTrue(rBalanced <= rMax + 1e-9, "Balanced's DTPS/DPS beats Max DPS's");
		assertTrue(rBalanced <= rTanky + 1e-9, "and Tanky's");
		assertTrue(dtps(mob, tanky) < dtps(mob, max) - 1e-6, "Graardor ranged has a real trade to make");
		assertNotNull(tanky.modeTrade);
		assertNull(tanky.modeTrade.reason);
		assertTrue(tanky.modeTrade.dmgCutPct > 0, "the card can show the damage cut");
		assertTrue(tanky.modeTrade.dpsLossPct >= 0, "and the DPS cost");
		assertEquals(Service.OptimizeMode.TANKY, tanky.modeTrade.mode);
	}

	@Test
	@DisplayName("a partly modeled monster keeps Max DPS and says why")
	void partialCoverageKeepsMaxDps() throws Exception {
		Service.Pick max = run("blue dragon", Style.MELEE, Service.OptimizeMode.MAX_DPS);
		Service.Pick tanky = run("blue dragon", Style.MELEE, Service.OptimizeMode.TANKY);
		assertNotNull(tanky.modeTrade);
		assertEquals("partial damage data", tanky.modeTrade.reason);
		assertEquals(max.owned.get(0).getDps(), tanky.owned.get(0).getDps(), 1e-9);
	}

	@Test
	@DisplayName("when the max-DPS set takes ~0 damage, every mode keeps it")
	void zeroDamageKeepsMaxDps() throws Exception {
		// One magic attack, prayed off entirely.
		Service.Pick max = run("aberrant spectre", Style.MELEE, Service.OptimizeMode.MAX_DPS);
		Service.Pick balanced = run("aberrant spectre", Style.MELEE, Service.OptimizeMode.BALANCED);
		assertNotNull(balanced.modeTrade);
		assertEquals("takes ~0 damage", balanced.modeTrade.reason);
		assertEquals(max.owned.get(0).getDps(), balanced.owned.get(0).getDps(), 1e-9);
	}
}
