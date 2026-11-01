package com.ll.optimizer;

import com.google.gson.Gson;
import com.ll.collection.MonsterProfileStore;
import com.ll.data.DataService;
import com.ll.data.Degradable;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
import com.ll.testsupport.InMemoryConfigManager;
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
class DegradableChipTest {
	@Test
	@DisplayName("skipping degradable gear drops blood moon tassets for dragon platelegs on the gemstone crab; allowing brings the tassets back")
	void chip() throws Exception {
		Tables data = new DataService().load();
		Monster crab = data.searchMonsters("Gemstone Crab", 1).get(0);
		MonsterProfileStore store = new MonsterProfileStore(InMemoryConfigManager.create(), new Gson());
		store.setDegradableIds(Degradable.ids(data));
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1); // abyssal whip
		owned.put(29025, 1); // blood moon tassets
		owned.put(4087, 1); // dragon platelegs
		Service service = new Service(data);
		try {
			assertEquals(29025, legs(service, crab, owned, store.exclusionsFor(crab.getId(), "MELEE")), "allowed: tassets");
			store.setSkipDegradable(crab.getId(), true);
			assertEquals(4087, legs(service, crab, owned, store.exclusionsFor(crab.getId(), "MELEE")), "skipped: platelegs");
			store.setSkipDegradable(crab.getId(), false);
			assertEquals(29025, legs(service, crab, owned, store.exclusionsFor(crab.getId(), "MELEE")), "allowed again");
		} finally {
			service.shutdown();
		}
	}

	private static int legs(Service service, Monster monster, Map<Integer, Integer> owned,
		Set<Integer> excluded) throws Exception {
		CountDownLatch done = new CountDownLatch(1);
		AtomicReference<Map<Style, Service.Pick>> out = new AtomicReference<>();
		ServiceCalls.bestPerStyle(service, monster, Levels.MAXED, Levels.MAXED,
			Prayers.ALL, RequirementProfile.MAXED, new Owned(owned, true), 1, false, false, "",
			excluded, -1, Req.DEFAULT_RISK_BUDGET_GP, false, Collections.emptySet(), 0,
			results -> {
				out.set(results);
				done.countDown();
			});
		assertTrue(done.await(120, TimeUnit.SECONDS));
		return out.get().get(Style.MELEE).owned.get(0).getLoadout().get(Slot.LEGS).getId();
	}
}
