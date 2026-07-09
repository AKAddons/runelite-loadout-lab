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
	public void balancedNeverTakesMoreDamageAndNeverDealsMoreThanMaxDps() throws Exception
	{
		OptimizerService.StyleResult max = ranged(OptimizerService.OptimizeMode.MAX_DPS);
		OptimizerService.StyleResult balanced = ranged(OptimizerService.OptimizeMode.BALANCED);
		Assert.assertNull(max.modeNote);
		Assert.assertTrue(balanced.owned.get(0).getDps() <= max.owned.get(0).getDps() + 1e-9);
		Assert.assertTrue(incoming(balanced) <= incoming(max) + 1e-9);
	}

	@Test
	public void tankyTakesAtMostBalancedDamageAndKeepsHalfTheDps() throws Exception
	{
		OptimizerService.StyleResult max = ranged(OptimizerService.OptimizeMode.MAX_DPS);
		OptimizerService.StyleResult tanky = ranged(OptimizerService.OptimizeMode.TANKY);
		Assert.assertTrue(incoming(tanky) <= incoming(max) + 1e-9);
		Assert.assertTrue(tanky.owned.get(0).getDps() >= 0.5 * max.owned.get(0).getDps() - 1e-9);
	}
}
