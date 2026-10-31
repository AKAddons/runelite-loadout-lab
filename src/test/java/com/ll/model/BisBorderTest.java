package com.ll.model;

import com.ll.data.DataService;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.data.WildernessMonsters;
import com.ll.engine.Style;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
import com.ll.optimizer.Service;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Andrew, field 2026-09-27 (Frost Nagua): "i'm seeing my occult necklace
 * count as bis but when i sim a amulet of fire the dps goes up". The gold
 * border compared slots with the game-best set, which casts from a Shadow;
 * on a Fire Bolt card the fire amulet beats the occult. The border now
 * stands only where nothing in the game beats the item IN YOUR SET. */
class BisBorderTest {
	private static final int OCCULT = 12002;
	private static final int PURGING_STAFF = 29594;
	private static final int SHADOW = 27275;

	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static Service.Pick magicAtFrostNagua(Map<Integer, Integer> owned) throws Exception {
		Monster mob = data.searchMonsters("frost nagua", 1).get(0);
		Service service = new Service(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, Service.Pick>> out = new AtomicReference<>();
			com.ll.engine.ComputeParams p = new com.ll.engine.ComputeParams();
			p.inWilderness = WildernessMonsters.isExclusive(mob);
			service.bestPerStyle(mob, Levels.MAXED, Levels.MAXED, Prayers.ALL,
				RequirementProfile.MAXED, new Owned(owned, true), 1, p,
				Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), null, 0,
				Collections.<Integer>emptySet(), result -> {
					out.set(result);
					done.countDown();
				});
			assertTrue(done.await(120, TimeUnit.SECONDS), "compute finished");
			return out.get().get(Style.MAGIC);
		} finally {
			service.shutdown();
		}
	}

	@SuppressWarnings("unchecked")
	private static boolean neckBordered(Service.Pick result, Map<Integer, Integer> owned) {
		Map<String, Object> card = RenderModel.card(result, false, -1, owned::containsKey,
			Collections.emptySet());
		Map<String, Object> gear = (Map<String, Object>) card.get("gear");
		Map<String, Object> neck = (Map<String, Object>) gear.get("neck");
		return Boolean.TRUE.equals(neck.get("bisMatch"));
	}

	@Test
	@DisplayName("an occult on a Fire Bolt card loses the border: a fire amulet beats it in that set")
	void boltCard() throws Exception {
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(PURGING_STAFF, 1);
		owned.put(OCCULT, 1);
		Service.Pick result = magicAtFrostNagua(owned);
		assertEquals(OCCULT, result.owned.get(0).getLoadout().get(Slot.NECK).getId());
		assertEquals(OCCULT, result.overallBest.getLoadout().get(Slot.NECK).getId(),
			"game best (a Shadow) wears the occult");
		assertTrue(result.beaten.contains(Slot.NECK), "the neck is beaten in the Fire Bolt set");
		assertFalse(neckBordered(result, owned), "no gold border on the occult");
	}

	@Test
	@DisplayName("the same occult under a Shadow keeps the border")
	void shadowCard() throws Exception {
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(SHADOW, 1);
		owned.put(OCCULT, 1);
		Service.Pick result = magicAtFrostNagua(owned);
		assertEquals(SHADOW, result.owned.get(0).getLoadout().getWeapon().getId());
		assertFalse(result.beaten.contains(Slot.NECK));
		assertTrue(neckBordered(result, owned), "nothing beats the occult here");
	}
}
