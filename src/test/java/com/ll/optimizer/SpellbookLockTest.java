package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
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
class SpellbookLockTest {
	@Test
	@DisplayName("a Lunar lock leaves the magic card to the powered staves: the trident wins over a standard-book staff of fire that would otherwise outcast it")
	void lunarLock() throws Exception {
		Tables data = new DataService().load();
		Monster zulrah = data.searchMonsters("Zulrah", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(11905, 1); // trident of the seas
		owned.put(1387, 1); // staff of fire
		owned.put(12002, 1); // occult necklace
		Service service = new Service(data);
		try {
			assertEquals(11905, weapon(service, zulrah, owned, "lunar"), "lunar: built-in only");
			assertEquals(11905, weapon(service, zulrah, owned, ""), "auto at 99 Magic: the trident still wins");
			assertEquals(1387, weapon(service, zulrah, owned, "standard", 11905), "standard lock with the trident excluded: the staff of fire casts");
		} finally {
			service.shutdown();
		}
	}

	private static int weapon(Service service, Monster monster, Map<Integer, Integer> owned,
		String lock, Integer... excluded) throws Exception {
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<Style, Service.Pick>> out = new AtomicReference<>();
		ServiceCalls.bestPerStyle(service, monster, Levels.MAXED, Levels.MAXED,
			Prayers.ALL, RequirementProfile.MAXED, new Owned(owned, true), 1, false, false, lock,
			java.util.Set.of(excluded), -1, Req.DEFAULT_RISK_BUDGET_GP, false, Collections.emptySet(), 0,
			results -> {
				out.set(results);
				done.countDown();
			});
		assertTrue(done.await(120, TimeUnit.SECONDS));
		return out.get().get(Style.MAGIC).owned.get(0).getLoadout().getWeapon().getId();
	}
}
