package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import com.loadoutlab.optimizer.OptimizerService;
import com.loadoutlab.optimizer.ServiceCalls;
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
class RaidBoostPickerTest
{
	@Test
	@DisplayName("raidBoost is no longer a page param - the picker owns the choice")
	void raidBoostIsNotAPageParam()
	{
		PageState state = new PageState();
		assertFalse(state.setParam("raidBoost", false), "an unknown param is ignored");
		assertFalse(state.paramsNode().containsKey("raidBoost"));
	}

	@Test
	@DisplayName("DETECT on a raid mob assumes the raid boost; outside a raid it never does")
	void detectAssumesTheRaidBoost() throws Exception
	{
		LoadoutData data = new DataService().load();
		OptimizerService service = new OptimizerService(data);
		try
		{
			assertTrue(boostLabel(service, data, "tekton").contains("Overload (+)"),
				"CoX supplies overload (+)");
			assertFalse(boostLabel(service, data, "vorkath").contains("Overload (+)"),
				"no raid, no raid boost");
		}
		finally
		{
			service.shutdown();
		}
	}

	private static String boostLabel(OptimizerService service, LoadoutData data, String name)
		throws Exception
	{
		MonsterStats mob = data.searchMonsters(name, 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1); // abyssal whip: a melee set exists, no potions banked
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> got = new AtomicReference<>();
		ServiceCalls.bestPerStyle(service, mob, PlayerLevels.MAXED, PlayerLevels.MAXED,
			PrayerUnlocks.ALL, RequirementProfile.MAXED, new OwnedItems(owned, true),
			owned.hashCode(), false, false, "", Collections.emptySet(), -1,
			OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, Collections.emptySet(), 0,
			results ->
			{
				got.set(results);
				done.countDown();
			});
		assertTrue(done.await(120, TimeUnit.SECONDS));
		String label = got.get().get(CombatStyle.MELEE).boostLabel;
		return label == null ? "" : label;
	}
}
