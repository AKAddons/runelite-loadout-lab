package com.ll.model;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
import com.ll.optimizer.Service;
import com.ll.optimizer.ServiceCalls;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Andrew 2026-09-02: the raid boosts live in the boost picker, not on a
 * chip. The picker already lists Overload (+) and Smelling salts; the
 * "Raid boost" chip was a second control for the same choice, so it goes
 * and DETECT on a raid mob resolves to the raid's own boost.
 */
class RaidBoostPickerTest {
	@Test
	@DisplayName("raidBoost is no longer a page param - the picker owns the choice")
	void raidBoostIsNotAPageParam() {
		PageState state = new PageState();
		assertFalse(state.setParam("raidBoost", false), "an unknown param is ignored");
		assertFalse(state.pn().containsKey("raidBoost"));
	}

	@Test
	@DisplayName("DETECT on a raid mob assumes the raid boost; outside a raid it never does")
	void detectAssumesTheRaidBoost() throws Exception {
		Tables data = new DataService().load();
		Service service = new Service(data);
		try {
			assertTrue(boostLabel(service, data, "tekton").contains("Overload (+)"),
				"CoX supplies overload (+)");
			assertFalse(boostLabel(service, data, "vorkath").contains("Overload (+)"),
				"no raid, no raid boost");
		} finally {
			service.shutdown();
		}
	}

	private static String boostLabel(Service service, Tables data, String name)
		throws Exception {
		Monster mob = data.searchMonsters(name, 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1); // abyssal whip: a melee set exists, no potions banked
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<Style, Service.Pick>> got = new AtomicReference<>();
		ServiceCalls.bestPerStyle(service, mob, Levels.MAXED, Levels.MAXED,
			Prayers.ALL, RequirementProfile.MAXED, new Owned(owned, true),
			owned.hashCode(), false, false, "", Collections.emptySet(), -1,
			Req.DEFAULT_RISK_BUDGET_GP, false, Collections.emptySet(), 0,
			results -> {
				got.set(results);
				done.countDown();
			});
		assertTrue(done.await(120, TimeUnit.SECONDS));
		String label = got.get().get(Style.MELEE).boostLabel;
		return label == null ? "" : label;
	}
}
