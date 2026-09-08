package com.loadoutlab.optimizer;

import com.google.gson.Gson;
import com.loadoutlab.collection.MonsterProfileStore;
import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Degradable;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import com.loadoutlab.testsupport.InMemoryConfigManager;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Degrades chip (Mike, Discord 2026-09-07): skipping degradable gear
 * removes it from the mob's candidate pool through the per-mob exclusion
 * seam, so the crab's melee card swaps blood moon tassets for the dragon
 * platelegs it would otherwise lose to by one strength. */
class DegradableChipTest
{
	@Test
	@DisplayName("skipping degradable gear drops blood moon tassets for dragon platelegs on the gemstone crab; allowing brings the tassets back")
	void chip() throws Exception
	{
		LoadoutData data = new DataService().load();
		MonsterStats crab = data.searchMonsters("Gemstone Crab", 1).get(0);
		MonsterProfileStore store = new MonsterProfileStore(InMemoryConfigManager.create(), new Gson());
		store.setDegradableIds(Degradable.ids(data));
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1); // abyssal whip
		owned.put(29025, 1); // blood moon tassets
		owned.put(4087, 1); // dragon platelegs
		OptimizerService service = new OptimizerService(data);
		try
		{
			assertEquals(29025, legs(service, crab, owned, store.exclusionsFor(crab.getId(), "MELEE")), "allowed: tassets");
			store.setSkipDegradable(crab.getId(), true);
			assertEquals(4087, legs(service, crab, owned, store.exclusionsFor(crab.getId(), "MELEE")), "skipped: platelegs");
			store.setSkipDegradable(crab.getId(), false);
			assertEquals(29025, legs(service, crab, owned, store.exclusionsFor(crab.getId(), "MELEE")), "allowed again");
		}
		finally
		{
			service.shutdown();
		}
	}

	private static int legs(OptimizerService service, MonsterStats monster, Map<Integer, Integer> owned,
		Set<Integer> excluded) throws Exception
	{
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
		ServiceCalls.bestPerStyle(service, monster, PlayerLevels.MAXED, PlayerLevels.MAXED,
			PrayerUnlocks.ALL, RequirementProfile.MAXED, new OwnedItems(owned, true), 1, false, false, "",
			excluded, -1, OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, Collections.emptySet(), 0,
			results ->
			{
				out.set(results);
				done.countDown();
			});
		assertTrue(done.await(120, TimeUnit.SECONDS));
		return out.get().get(CombatStyle.MELEE).owned.get(0).getLoadout().get(GearSlot.LEGS).getId();
	}
}
