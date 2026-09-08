package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Discord (Apathy, 2026-09-06): camping Lunar for Cure Me at Zulrah -
 * the magic card should then offer built-in staves only, since no Lunar
 * spell autocasts. The lock is the engine's job; the card's book picker
 * sets it (restored 2026-09-08 - the merge-back lost the control). */
class SpellbookLockTest
{
	@Test
	@DisplayName("a Lunar lock leaves the magic card to the powered staves: the trident wins over a standard-book staff of fire that would otherwise outcast it")
	void lunarLock() throws Exception
	{
		LoadoutData data = new DataService().load();
		MonsterStats zulrah = data.searchMonsters("Zulrah", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(11905, 1); // trident of the seas
		owned.put(1387, 1); // staff of fire
		owned.put(12002, 1); // occult necklace
		OptimizerService service = new OptimizerService(data);
		try
		{
			assertEquals(11905, weapon(service, zulrah, owned, "lunar"), "lunar: built-in only");
			assertEquals(11905, weapon(service, zulrah, owned, ""), "auto at 99 Magic: the trident still wins");
			assertEquals(1387, weapon(service, zulrah, owned, "standard", 11905), "standard lock with the trident excluded: the staff of fire casts");
		}
		finally
		{
			service.shutdown();
		}
	}

	private static int weapon(OptimizerService service, MonsterStats monster, Map<Integer, Integer> owned,
		String lock, Integer... excluded) throws Exception
	{
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
		ServiceCalls.bestPerStyle(service, monster, PlayerLevels.MAXED, PlayerLevels.MAXED,
			PrayerUnlocks.ALL, RequirementProfile.MAXED, new OwnedItems(owned, true), 1, false, false, lock,
			java.util.Set.of(excluded), -1, OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, Collections.emptySet(), 0,
			results ->
			{
				out.set(results);
				done.countDown();
			});
		assertTrue(done.await(120, TimeUnit.SECONDS));
		return out.get().get(CombatStyle.MAGIC).owned.get(0).getLoadout().getWeapon().getId();
	}
}
