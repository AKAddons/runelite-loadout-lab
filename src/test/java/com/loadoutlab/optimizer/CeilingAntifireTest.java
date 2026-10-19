package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
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
 * Andrew (client test 2026-10-19): "best should always align with the
 * highest dps option". The Best-in-game side is the ceiling, so it
 * always assumes a super antifire against a fire-breather and never
 * spends its shield slot on protection; Yours keeps the owned-tier
 * detection and the manual pick.
 */
class CeilingAntifireTest
{
	@Test
	@DisplayName("the game-best card at Vorkath assumes a super antifire: no protective shield")
	void ceilingAssumesSuperAntifire() throws Exception
	{
		LoadoutData data = new DataService().load();
		MonsterStats vorkath = data.searchMonsters("vorkath", 1).get(0);
		OptimizerService service = new OptimizerService(data);
		try
		{
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
			ServiceCalls.bestPerStyle(service, vorkath, PlayerLevels.MAXED, PlayerLevels.MAXED,
				PrayerUnlocks.ALL, RequirementProfile.MAXED, new OwnedItems(Map.of(4151, 1), true), 7,
				false, false, "", Collections.emptySet(), -1,
				OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, Collections.emptySet(), 0,
				results -> { out.set(results); done.countDown(); });
			assertTrue(done.await(120, TimeUnit.SECONDS));
			OptimizerService.StyleResult melee = out.get().get(CombatStyle.MELEE);
			assertNotNull(melee);
			assertNotNull(melee.overallBest, "a game-best melee set must exist");
			var shield = melee.overallBest.getLoadout().get(GearSlot.SHIELD);
			assertNotNull(shield, "the ceiling still wears a shield slot item");
			assertFalse(DragonfireRules.isProtectiveShield(shield),
				"the ceiling spends the slot on dps, not protection: " + shield.getNameLower());
		}
		finally
		{
			service.shutdown();
		}
	}
}
