package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.WildernessMonsters;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
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
class SpecOffhandTest
{
	private static final int SCYTHE = 22325;
	private static final int VOIDWAKER = 27690;
	private static final int AVERNIC = 22322;

	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static Map<Integer, Integer> owned()
	{
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(SCYTHE, 1);
		owned.put(VOIDWAKER, 1);
		owned.put(AVERNIC, 1);
		return owned;
	}

	private static boolean carries(List<GearItem> bench, int id)
	{
		return bench.stream().anyMatch(i -> i.getId() == id);
	}

	private static OptimizerService.StyleResult single(int maxSwaps) throws Exception
	{
		MonsterStats mob = data.searchMonsters("general graardor", 1).get(0);
		OptimizerService service = new OptimizerService(data);
		try
		{
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
			service.bestPerStyle(mob, PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
				RequirementProfile.MAXED, new OwnedItems(owned(), true), 1, false, false, "",
				Collections.emptyMap(), -1, OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, 0, true,
				Collections.emptyMap(), Collections.emptyMap(), WildernessMonsters.isExclusive(mob),
				Collections.emptySet(), 0, maxSwaps, Collections.emptyMap(), null, 0,
				Collections.<Integer>emptySet(), result ->
				{
					out.set(result);
					done.countDown();
				});
			assertTrue(done.await(120, TimeUnit.SECONDS), "compute finished");
			return out.get().get(CombatStyle.MELEE);
		}
		finally
		{
			service.shutdown();
		}
	}

	private static OptimizerService.StyleResult roster(int maxSwaps, String... mobs) throws Exception
	{
		OptimizerService service = new OptimizerService(data);
		try
		{
			MonsterStats[] stats = Arrays.stream(mobs)
				.map(m -> data.searchMonsters(m, 1).get(0)).toArray(MonsterStats[]::new);
			return ServiceCalls.runRoster(service, Arrays.asList(stats), owned(), maxSwaps,
				Collections.emptyMap()).perMob.get(0).get(CombatStyle.MELEE);
		}
		finally
		{
			service.shutdown();
		}
	}

	private static void assertOffhandAtTwo(OptimizerService.StyleResult one, OptimizerService.StyleResult two)
	{
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
	void singleMob() throws Exception
	{
		assertOffhandAtTwo(single(1), single(2));
	}

	@Test
	@DisplayName("a roster does the same, and the offhand costs its inventory slot")
	void rosterPath() throws Exception
	{
		assertOffhandAtTwo(roster(1, "general graardor", "k'ril tsutsaroth"),
			roster(2, "general graardor", "k'ril tsutsaroth"));
	}
}
