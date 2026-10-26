package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.optimizer.OptimizerService;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hallowfell as the melee multi tool (ISS-01M5TPEWE9AQM3B55ZH02PHGE4, Andrew 2026-10-25: "can we also
 * consider hallowfell as a melee aoe option for multimob"). Wiki passive: up
 * to two more unique targets at half the max hit, rounded down, each its own
 * accuracy roll.
 */
class HallowfellMultiTest {
	@Test
	@DisplayName("the cleave factor: 1 alone, two extra targets at most, each at half the max hit rounded down")
	void factor() {
		assertEquals(1.0, MultiTool.factor("cleave", 1, 40), 1e-9);
		assertEquals(1.5, MultiTool.factor("cleave", 2, 40), 1e-9);
		assertEquals(2.0, MultiTool.factor("cleave", 3, 40), 1e-9);
		assertEquals(2.0, MultiTool.factor("cleave", 9, 40), 1e-9, "two extras cap");
		assertEquals(1 + 2 * 20 / 41.0, MultiTool.factor("cleave", 3, 41), 1e-9, "half of 41 rounds down to 20");
	}

	@Test
	@DisplayName("with the Multi card on at abyssal demons, owning Hallowfell, the hallowfell tool's Yours wears it")
	void toolWearsHallowfell() throws Exception {
		LoadoutData data = new DataService().load();
		MonsterStats demon = data.searchMonsters("abyssal demon", 1).get(0);
		ComputeParams p = new ComputeParams();
		p.multiView = true;
		OptimizerService service = new OptimizerService(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
			service.bestPerStyle(demon, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
				RequirementProfile.MAXED, new OwnedItems(Map.of(MultiTool.HALLOWFELL_SWORD, 1), true), 1, p,
				Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), null, 0,
				Collections.<Integer>emptySet(), result -> {
					out.set(result);
					done.countDown();
				});
			assertTrue(done.await(300, TimeUnit.SECONDS));
			OptimizerService.StyleResult tool = out.get().values().iterator().next().multiTools.get("hallowfell");
			assertNotNull(tool, "the hallowfell tool is on the Multi card");
			GearItem weapon = tool.owned.get(0).getLoadout().get(GearSlot.WEAPON);
			assertEquals(MultiTool.HALLOWFELL_SWORD, weapon.getId());
		} finally {
			service.shutdown();
		}
	}
}
