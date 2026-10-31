package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import com.loadoutlab.engine.Style;
import com.loadoutlab.engine.ComputeParams;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Best in game is the true ceiling (ISS-01M5JM0FATFKQ93PS3Z5MV54M1, Andrew 2026-10-22: "in the
 * old client, i was able to generate a better "BiS" set than what came up in
 * search" - his Obligator exclusion was holding the ceiling down; answer
 * "BiS ignores exclusions (Recommended)").
 */
class BisIgnoresExclusionsTest {
	private static OptimizerService.StyleResult melee(LoadoutData data, Monster mob,
		Map<Style, Set<Integer>> excluded) throws Exception {
		OptimizerService service = new OptimizerService(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, OptimizerService.StyleResult>> out = new AtomicReference<>();
			ComputeParams p = new ComputeParams();
			p.specWeapon = false;
			service.bestPerStyle(mob, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
				RequirementProfile.MAXED, new OwnedItems(Map.of(4151, 1), true), 1, p,
				excluded, Collections.emptySet(), Collections.emptyMap(), null, 0,
				Collections.<Integer>emptySet(), result -> {
					out.set(result);
					done.countDown();
				});
			assertTrue(done.await(300, TimeUnit.SECONDS));
			return out.get().get(Style.MELEE);
		} finally {
			service.shutdown();
		}
	}

	@Test
	@DisplayName("excluding the best weapon leaves Best in game wearing it - exclusions shape Yours only")
	void exclusionsLeaveTheCeiling() throws Exception {
		LoadoutData data = new DataService().load();
		Monster demon = data.searchMonsters("abyssal demon", 1).get(0);
		int bisWeapon = melee(data, demon, Map.of()).overallBest.getLoadout().getWeapon().getId();
		OptimizerService.StyleResult excluded = melee(data, demon, Map.of(Style.MELEE, Set.of(bisWeapon)));
		assertEquals(bisWeapon, excluded.overallBest.getLoadout().getWeapon().getId(),
			"the ceiling ignores the exclusion");
	}
}
