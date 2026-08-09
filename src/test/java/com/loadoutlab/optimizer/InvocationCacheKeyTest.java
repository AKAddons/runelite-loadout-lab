package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.MonsterMechanics;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Test;

/**
 * The single-mob cache key must carry the ToA invocation level (found
 * by a token-diet agent 2026-08-09: optimizeKey had it, baseKeyFor did
 * not - so flipping the Invocation chip on a Warden card served the
 * PREVIOUS level's cached results). The defence scaling
 * (x(250+invo)/250) guarantees the two levels' numbers must differ.
 */
public class InvocationCacheKeyTest
{
	private static Map<CombatStyle, OptimizerService.StyleResult> best(
		OptimizerService service, MonsterStats monster) throws Exception
	{
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out =
			new AtomicReference<>();
		ServiceCalls.bestPerStyle(service, monster,
			PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
			RequirementProfile.MAXED, OwnedItems.EMPTY, 1,
			false, false, "", new EnumMap<>(CombatStyle.class), -1,
			com.loadoutlab.engine.OptimizationRequest.DEFAULT_RISK_BUDGET_GP,
			false, false, Collections.emptySet(), 0,
			Collections.emptyMap(), null, Collections.emptySet(),
			r -> { out.set(r); done.countDown(); });
		Assert.assertTrue("compute timed out", done.await(120, TimeUnit.SECONDS));
		return out.get();
	}

	@Test
	public void flippingTheInvocationLevelNeverServesStaleResults() throws Exception
	{
		LoadoutData data = new DataService().load();
		MonsterStats warden = data.searchMonsters("tumeken's warden", 6).stream()
			.filter(m -> MonsterMechanics.isToaInvocationScaled(m))
			.findFirst().orElseThrow();
		OptimizerService service = new OptimizerService(data);
		try
		{
			double atZero = best(service, MonsterMechanics.atToaInvocation(warden, 0))
				.get(CombatStyle.RANGED).overallBest.getDps();
			double at300 = best(service, MonsterMechanics.atToaInvocation(warden, 300))
				.get(CombatStyle.RANGED).overallBest.getDps();
			Assert.assertNotEquals("invocation 300 must not serve the cached"
					+ " invocation-0 numbers (defence x2.2 has to move the dps)",
				atZero, at300, 1e-9);
			Assert.assertTrue("higher invocation means lower dps",
				at300 < atZero);
		}
		finally
		{
			service.shutdown();
		}
	}
}
