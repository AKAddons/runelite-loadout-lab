package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.data.WildernessMonsters;
import com.ll.engine.Style;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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

/** N, Discord 2026-09-26: "When inv==2 and primary wep is 2h, it doesn't
 * recommend a shield/defender for the offhand on the spec switch". A
 * one-handed spec weapon swapped in for a two-hander leaves the shield
 * slot empty; with a second inventory slot the switch brings an offhand. */
class SpecOffhandTest {
	private static final int SCYTHE = 22325;
	private static final int VOIDWAKER = 27690;
	private static final int AVERNIC = 22322;

	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static Map<Integer, Integer> owned() {
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(SCYTHE, 1);
		owned.put(VOIDWAKER, 1);
		owned.put(AVERNIC, 1);
		return owned;
	}

	private static boolean carries(List<Gear> bench, int id) {
		return bench.stream().anyMatch(i -> i.getId() == id);
	}

	private static Service.Pick single(int maxSwaps) throws Exception {
		Monster mob = data.searchMonsters("dagannoth rex", 1).get(0);
		Service service = new Service(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, Service.Pick>> out = new AtomicReference<>();
			com.ll.engine.ComputeParams p = new com.ll.engine.ComputeParams();
			p.inWilderness = WildernessMonsters.isExclusive(mob);
			p.maxSwaps = maxSwaps;
			service.bestPerStyle(mob, Levels.MAXED, Levels.MAXED, Prayers.ALL,
				RequirementProfile.MAXED, new Owned(owned(), true), 1, p,
				Collections.emptyMap(), Collections.emptySet(), Collections.emptyMap(), null, 0,
				Collections.<Integer>emptySet(), result -> {
					out.set(result);
					done.countDown();
				});
			assertTrue(done.await(120, TimeUnit.SECONDS), "compute finished");
			return out.get().get(Style.MELEE);
		} finally {
			service.shutdown();
		}
	}

	private static Service.Pick roster(int maxSwaps, String... mobs) throws Exception {
		Service service = new Service(data);
		try {
			Monster[] stats = Arrays.stream(mobs)
				.map(m -> data.searchMonsters(m, 1).get(0)).toArray(Monster[]::new);
			return ServiceCalls.runRoster(service, Arrays.asList(stats), owned(), maxSwaps,
				Collections.emptyMap()).pm.get(0).get(Style.MELEE);
		} finally {
			service.shutdown();
		}
	}

	private static void assertOffhandAtTwo(Service.Pick one, Service.Pick two) {
		assertEquals(SCYTHE, two.owned.get(0).getLoadout().getWeapon().getId(), "the two-hander is the main hand");
		assertEquals(VOIDWAKER, two.specWeapon.getId(), "the one-handed spec weapon");
		assertTrue(carries(two.bench, VOIDWAKER), "the spec weapon is carried");
		assertTrue(carries(two.bench, AVERNIC), "inventory 2 brings the defender for the spec switch");
		assertFalse(carries(one.bench, AVERNIC), "inventory 1 has room for the weapon only");
		assertTrue(two.specExpectedDamage > one.specExpectedDamage,
			"the defender's strength raises the spec's damage");
	}

	@Test
	@DisplayName("a single mob: inventory 2 adds the offhand to a one-handed spec switch off a two-hander")
	void singleMob() throws Exception {
		assertOffhandAtTwo(single(1), single(2));
	}

	@Test
	@DisplayName("a roster does the same, and the offhand costs its inventory slot")
	void rosterPath() throws Exception {
		assertOffhandAtTwo(roster(1, "general graardor", "k'ril tsutsaroth"),
			roster(2, "general graardor", "k'ril tsutsaroth"));
	}
}
