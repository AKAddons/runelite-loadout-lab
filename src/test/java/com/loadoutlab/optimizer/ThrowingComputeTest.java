package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.*;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TonyDAnnunzio (YouTube 2026-10-18): "it just says computing now". A
 * throw inside the optimizer's worker used to drop the callback, leaving
 * the card on Computing for good. A compute that fails now answers with
 * an empty result so the card shows its empty state and Reload works.
 */
class ThrowingComputeTest
{
	@Test
	@DisplayName("a compute whose request the engine rejects still calls back, empty")
	void throwingComputeStillAnswers() throws Exception
	{
		LoadoutData data = new DataService().load();
		MonsterStats graardor = data.searchMonsters("general graardor", 1).get(0);
		OptimizerService service = new OptimizerService(data);
		try
		{
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
			// A null requirement profile blows up inside the candidate pool,
			// on the worker thread - the shape of any unexpected engine throw.
			ServiceCalls.bestPerStyle(service, graardor, PlayerLevels.MAXED, PlayerLevels.MAXED,
				PrayerUnlocks.ALL, null, new OwnedItems(Map.of(4151, 1), true), 1,
				false, false, "", Collections.emptySet(), -1,
				OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, Collections.emptySet(), 0,
				results -> { out.set(results); done.countDown(); });
			assertTrue(done.await(30, TimeUnit.SECONDS), "the compute must call back even when it throws");
			assertTrue(out.get().isEmpty(), "a failed compute answers empty: " + out.get().keySet());
		}
		finally
		{
			service.shutdown();
		}
	}
}
