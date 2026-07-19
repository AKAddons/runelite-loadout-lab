package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.IncomingDpsCalculator;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** D-4: the frontier modes trade dps for less damage taken, never more. */
public class OptimizeModeTest
{
	private static LoadoutData data;
	private static MonsterStats graardor;

	@BeforeClass
	public static void load()
	{
		data = new DataService().load();
		graardor = data.searchMonsters("general graardor", 1).get(0);
	}

	private static OptimizerService.StyleResult ranged(OptimizerService.OptimizeMode mode)
		throws Exception
	{
		OptimizerService service = new OptimizerService(data);
		try
		{
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
			com.loadoutlab.optimizer.ServiceCalls.bestPerStyle(service, graardor, PlayerLevels.MAXED, PlayerLevels.MAXED,
				PrayerUnlocks.ALL, RequirementProfile.MAXED,
				OwnedItems.EMPTY, 1, false, false, "",
				java.util.Collections.emptySet(), -1,
				com.loadoutlab.engine.OptimizationRequest.DEFAULT_RISK_BUDGET_GP,
				false, java.util.Collections.emptySet(), 2_000_000_000, mode,
				results ->
				{
					out.set(results);
					done.countDown();
				});
			Assert.assertTrue(done.await(240, TimeUnit.SECONDS));
			return out.get().get(CombatStyle.RANGED);
		}
		finally
		{
			service.shutdown();
		}
	}

	private static double incoming(OptimizerService.StyleResult result)
	{
		return IncomingDpsCalculator.calculate(graardor,
			result.owned.get(0).getLoadout(), 99, 99).totalDps;
	}

	@Test
	public void defenseWeightedRunsCanPickPureDefenseGear() throws Exception
	{
		// Own a basic ranged kit plus an archers ring (i) (offense) and a
		// ring of suffering (i) (pure defense, zero offense). At lambda=0
		// the archers ring wins; with a heavy defense weight the beam must
		// be ABLE to pick the suffering - it used to be filtered from the
		// candidate pool entirely for scoring zero offense.
		com.loadoutlab.data.LoadoutData d = data;
		java.util.Map<Integer, Integer> owned = new java.util.HashMap<>();
		owned.put(861, 1);    // magic shortbow
		owned.put(892, 500);  // rune arrows
		owned.put(11771, 1);  // archers ring (i)
		owned.put(19710, 1);  // ring of suffering (i)
		com.loadoutlab.engine.OptimizationRequest base = new com.loadoutlab.engine.OptimizationRequest(
			graardor, CombatStyle.RANGED, PlayerLevels.MAXED,
			com.loadoutlab.engine.PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL),
			null, 0, com.loadoutlab.engine.CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(d.canonicalizeOwned(owned), true), 1);
		com.loadoutlab.engine.LoadoutOptimizer optimizer = new com.loadoutlab.engine.LoadoutOptimizer();
		com.loadoutlab.data.GearItem offenseRing = optimizer.optimize(d, base).get(0)
			.getLoadout().get(com.loadoutlab.data.GearSlot.RING);
		com.loadoutlab.engine.DpsResult tanky = optimizer.optimize(d,
			base.withDefenseWeight(50)).get(0);
		com.loadoutlab.data.GearItem tankyRing = tanky.getLoadout().get(com.loadoutlab.data.GearSlot.RING);
		Assert.assertNotNull(offenseRing);
		Assert.assertEquals("Archers ring (i)", offenseRing.getName());
		Assert.assertNotNull(tankyRing);
		Assert.assertEquals("Ring of suffering (i)", tankyRing.getName());
	}

	/** Balanced's own metric: the dps-favored ratio. */
	private static double ratio(OptimizerService.StyleResult result)
	{
		return OptimizerService.balancedScore(result.owned.get(0).getDps(), incoming(result));
	}

	@Test
	public void theThreeModesHonorTheirPureObjectives() throws Exception
	{
		// MAX_DPS: most output. TANKY: least intake, no dps floor.
		// BALANCED: best out/in ratio, >= BOTH other modes' ratios by
		// construction (field report: a knee heuristic once picked ratio
		// 2.56 over the max-dps set's 3.0).
		OptimizerService.StyleResult max = ranged(OptimizerService.OptimizeMode.MAX_DPS);
		OptimizerService.StyleResult balanced = ranged(OptimizerService.OptimizeMode.BALANCED);
		OptimizerService.StyleResult tanky = ranged(OptimizerService.OptimizeMode.TANKY);
		Assert.assertNull(max.modeTrade);
		// Max dps deals the most.
		Assert.assertTrue(max.owned.get(0).getDps() >= balanced.owned.get(0).getDps() - 1e-9);
		Assert.assertTrue(max.owned.get(0).getDps() >= tanky.owned.get(0).getDps() - 1e-9);
		// Tanky takes the least.
		Assert.assertTrue(incoming(tanky) <= incoming(max) + 1e-9);
		Assert.assertTrue(incoming(tanky) <= incoming(balanced) + 1e-9);
		// Balanced owns the best (dps-favored) ratio.
		Assert.assertTrue(ratio(balanced) >= ratio(max) - 1e-9);
		Assert.assertTrue(ratio(balanced) >= ratio(tanky) - 1e-9);
	}
}
