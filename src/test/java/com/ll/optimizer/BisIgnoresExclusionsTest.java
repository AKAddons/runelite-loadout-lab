package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.ComputeParams;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
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
	private static Service.Pick melee(Tables data, Monster mob,
		Map<Style, Set<Integer>> excluded) throws Exception {
		Service service = new Service(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, Service.Pick>> out = new AtomicReference<>();
			ComputeParams p = new ComputeParams();
			p.specWeapon = false;
			service.bestPerStyle(mob, Levels.MAXED, Levels.MAXED, Prayers.ALL,
				RequirementProfile.MAXED, new Owned(Map.of(4151, 1), true), 1, p,
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
		Tables data = new DataService().load();
		Monster demon = data.searchMonsters("abyssal demon", 1).get(0);
		int bisWeapon = melee(data, demon, Map.of()).overallBest.getLoadout().getWeapon().getId();
		Service.Pick excluded = melee(data, demon, Map.of(Style.MELEE, Set.of(bisWeapon)));
		assertEquals(bisWeapon, excluded.overallBest.getLoadout().getWeapon().getId(),
			"the ceiling ignores the exclusion");
	}
}
