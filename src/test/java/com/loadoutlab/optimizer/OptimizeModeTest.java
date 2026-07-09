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
			service.bestPerStyle(graardor, PlayerLevels.MAXED, PlayerLevels.MAXED,
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

	private static double ratio(OptimizerService.StyleResult result)
	{
		return result.owned.get(0).getDps() / Math.max(incoming(result), 1e-9);
	}

	@Test
	public void balancedRatioNeverRegressesBelowTheMaxDpsRatio() throws Exception
	{
		// Field report: a knee heuristic once picked 2.87/1.12 (ratio 2.56)
		// over the 4.5/1.5 max-dps set (ratio 3.0). The modes now MAXIMIZE
		// the out/in ratio with max-dps in the candidate set, so the chosen
		// ratio is >= the max-dps ratio by construction.
		OptimizerService.StyleResult max = ranged(OptimizerService.OptimizeMode.MAX_DPS);
		OptimizerService.StyleResult balanced = ranged(OptimizerService.OptimizeMode.BALANCED);
		Assert.assertNull(max.modeNote);
		Assert.assertTrue(ratio(balanced) >= ratio(max) - 1e-9);
		Assert.assertTrue(balanced.owned.get(0).getDps() >= 0.75 * max.owned.get(0).getDps() - 1e-9);
	}

	@Test
	public void tankyRatioIsAtLeastBalancedAndKeepsHalfTheDps() throws Exception
	{
		OptimizerService.StyleResult max = ranged(OptimizerService.OptimizeMode.MAX_DPS);
		OptimizerService.StyleResult balanced = ranged(OptimizerService.OptimizeMode.BALANCED);
		OptimizerService.StyleResult tanky = ranged(OptimizerService.OptimizeMode.TANKY);
		// The looser dps floor can only widen the ratio search space.
		Assert.assertTrue(ratio(tanky) >= ratio(balanced) - 1e-9);
		Assert.assertTrue(ratio(tanky) >= ratio(max) - 1e-9);
		Assert.assertTrue(tanky.owned.get(0).getDps() >= 0.5 * max.owned.get(0).getDps() - 1e-9);
	}
}
