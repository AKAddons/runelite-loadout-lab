package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterGroups;
import com.loadoutlab.data.Monster;
import com.loadoutlab.engine.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Free BiS per style (ISS-01M60YKX8YAMV6QQX3XCYGJDBF, Andrew 2026-10-28:
 * "zulrah: the "bis" for melee is far off" - his pick "Free BiS per style"):
 * the BiS side is each style's game-wide best, not a swap kit under the
 * inventory budget, so the bench size never moves the ceiling.
 */
class FreeBisTest {
	private static OptimizerService.RosterResult roster(LoadoutData data, List<Monster> mobs, int bench) throws Exception {
		OptimizerService service = new OptimizerService(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<OptimizerService.RosterResult> out = new AtomicReference<>();
			ServiceCalls.bestPerStyleAcross(service, mobs, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
				RequirementProfile.MAXED, OwnedItems.EMPTY, 0, false, false, "", Collections.emptyMap(), -1,
				OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, false, Collections.emptySet(), 0, bench,
				Collections.emptyMap(), null, Collections.emptySet(), r -> {
					out.set(r);
					done.countDown();
				});
			assertTrue(done.await(300, TimeUnit.SECONDS), "roster answered");
			return out.get();
		} finally {
			service.shutdown();
		}
	}

	@Test
	@DisplayName("the Zulrah roster's BiS per style is the same at Inventory 1, 4 and 8")
	void benchNeverMovesTheCeiling() throws Exception {
		LoadoutData data = new DataService().load();
		List<Monster> mobs = null;
		for (MonsterGroups.MonsterGroup g : MonsterGroups.load(data)) {
			if (g.getName().toLowerCase().contains("zulrah")) mobs = g.getMobs();
		}
		assertNotNull(mobs, "the Zulrah group");
		Map<String, Double> base = null;
		for (int bench : new int[]{1, 4, 8}) {
			OptimizerService.RosterResult r = roster(data, mobs, bench);
			Map<String, Double> bis = new TreeMap<>();
			for (int i = 0; i < r.mobs.size(); i++) {
				for (var e : r.perMob.get(i).entrySet()) {
					if (e.getValue().overallBest != null) {
						bis.put(r.mobs.get(i).label() + " " + e.getKey(), Math.round(e.getValue().overallBest.dps * 1000) / 1000.0);
					}
				}
			}
			if (base == null) {
				base = bis;
			} else {
				assertEquals(base, bis, "BiS at Inventory " + bench + " matches Inventory 1");
			}
		}
	}
}
