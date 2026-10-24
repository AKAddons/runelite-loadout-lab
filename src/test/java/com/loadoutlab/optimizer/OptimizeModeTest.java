package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CandidateMode;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.IncomingDpsCalculator;
import com.loadoutlab.engine.LoadoutOptimizer;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerBonuses;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import com.loadoutlab.engine.TestRequests;
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
class OptimizeModeTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static OptimizerService.StyleResult run(String mob, CombatStyle style,
		OptimizerService.OptimizeMode mode) throws Exception
	{
		MonsterStats monster = data.searchMonsters(mob, 1).get(0);
		OptimizerService service = new OptimizerService(data);
		try
		{
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
			com.loadoutlab.engine.ComputeParams p = new com.loadoutlab.engine.ComputeParams();
			p.specWeapon = false;
			p.upgradeBudgetGp = 2_000_000_000;
			p.optimizeMode = mode.name();
			service.bestPerStyle(monster, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
				RequirementProfile.MAXED, OwnedItems.EMPTY, 1, p,
				Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), null, 0,
				Collections.<Integer>emptySet(), result ->
				{
					out.set(result);
					done.countDown();
				});
			assertTrue(done.await(300, TimeUnit.SECONDS), "compute finished");
			return out.get().get(style);
		}
		finally
		{
			service.shutdown();
		}
	}

	private static double dtps(String mob, OptimizerService.StyleResult result)
	{
		return IncomingDpsCalculator.calculate(data.searchMonsters(mob, 1).get(0),
			result.owned.get(0).getLoadout(), 99, 99).totalDps;
	}

	@Test
	@DisplayName("a defense-weighted beam can pick pure-defence gear (ring of suffering over archers ring)")
	void weightedBeamSeesDefenceGear()
	{
		MonsterStats graardor = data.searchMonsters("general graardor", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(861, 1);    // magic shortbow
		owned.put(892, 500);  // rune arrows
		owned.put(11771, 1);  // archers ring (i)
		owned.put(19710, 1);  // ring of suffering (i)
		OptimizationRequest base = TestRequests.of(graardor, CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL), null, 0,
			CandidateMode.OWNED_ONLY, true, false, new OwnedItems(data.canonicalizeOwned(owned), true), 1);
		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		GearItem offence = optimizer.optimize(data, base).get(0).getLoadout().get(GearSlot.RING);
		DpsResult tanky = optimizer.optimize(data, base.withDefenseWeight(50)).get(0);
		assertEquals("Archers ring (i)", offence.getName());
		assertEquals("Ring of suffering (i)", tanky.getLoadout().get(GearSlot.RING).getName());
	}

	@Test
	@DisplayName("Tanky takes the least damage, Balanced has the best DTPS/DPS, Max DPS deals the most")
	void modesHonourTheirObjectives() throws Exception
	{
		String mob = "general graardor";
		OptimizerService.StyleResult max = run(mob, CombatStyle.RANGED, OptimizerService.OptimizeMode.MAX_DPS);
		OptimizerService.StyleResult balanced = run(mob, CombatStyle.RANGED, OptimizerService.OptimizeMode.BALANCED);
		OptimizerService.StyleResult tanky = run(mob, CombatStyle.RANGED, OptimizerService.OptimizeMode.TANKY);
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
		assertEquals(OptimizerService.OptimizeMode.TANKY, tanky.modeTrade.mode);
	}

	@Test
	@DisplayName("a partly modeled monster keeps Max DPS and says why")
	void partialCoverageKeepsMaxDps() throws Exception
	{
		OptimizerService.StyleResult max = run("blue dragon", CombatStyle.MELEE, OptimizerService.OptimizeMode.MAX_DPS);
		OptimizerService.StyleResult tanky = run("blue dragon", CombatStyle.MELEE, OptimizerService.OptimizeMode.TANKY);
		assertNotNull(tanky.modeTrade);
		assertEquals("partial damage data", tanky.modeTrade.reason);
		assertEquals(max.owned.get(0).getDps(), tanky.owned.get(0).getDps(), 1e-9);
	}

	@Test
	@DisplayName("when the max-DPS set takes ~0 damage, every mode keeps it")
	void zeroDamageKeepsMaxDps() throws Exception
	{
		// One magic attack, prayed off entirely.
		OptimizerService.StyleResult max = run("aberrant spectre", CombatStyle.MELEE, OptimizerService.OptimizeMode.MAX_DPS);
		OptimizerService.StyleResult balanced = run("aberrant spectre", CombatStyle.MELEE, OptimizerService.OptimizeMode.BALANCED);
		assertNotNull(balanced.modeTrade);
		assertEquals("takes ~0 damage", balanced.modeTrade.reason);
		assertEquals(max.owned.get(0).getDps(), balanced.owned.get(0).getDps(), 1e-9);
	}
}
