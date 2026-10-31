package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.Style;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.Prayers;
import com.ll.engine.RequirementProfile;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Required slayer protection is enforced at the OPTIMIZER level (the
 * pool lesson: engine conditionals are invisible unless the item
 * survives the candidate pool) - grZ field request 2026-08-08: "some
 * tasks where u need to equip something. would be cool if it was
 * autopinned". Every registry row wiki-verified before encoding.
 */
public class RequiredGearOptimizerTest {
	private static Tables data;

	@BeforeClass
	public static void load() {
		data = new DataService().load();
	}

	private static Map<Style, Service.Pick> best(
		Monster monster, Map<Integer, Integer> owned,
		Map<Style, Map<Slot, Integer>> pinnedByStyle) throws Exception {
		Service service = new Service(data);
		try {
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<Style, Service.Pick>> out =
				new AtomicReference<>();
			ServiceCalls.bestPerStyle(service, monster,
				Levels.MAXED, Levels.MAXED, Prayers.ALL,
				RequirementProfile.MAXED, new Owned(owned, true), 1,
				false, false, "", new EnumMap<>(Style.class), -1,
				com.ll.engine.Req.DEFAULT_RISK_BUDGET_GP,
				false, false, Collections.emptySet(), 0,
				pinnedByStyle, null, Collections.emptySet(),
				r -> { out.set(r); done.countDown(); });
			Assert.assertTrue("compute timed out", done.await(120, TimeUnit.SECONDS));
			return out.get();
		} finally {
			service.shutdown();
		}
	}

	private static Gear shieldOf(Service.Pick sr) {
		return sr.owned.get(0).getLoadout().get(Slot.SHIELD);
	}

	@Test
	public void basiliskForcesTheGazeShieldOverADpsShield() throws Exception {
		Monster basilisk = data.searchMonsters("basilisk", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);    // whip
		owned.put(12954, 1);   // dragon defender - the raw dps pick
		owned.put(4156, 1);    // mirror shield
		Service.Pick melee =
			best(basilisk, owned, new EnumMap<>(Style.class)).get(Style.MELEE);
		Assert.assertNotNull(melee);
		Gear shield = shieldOf(melee);
		Assert.assertNotNull("the gaze slot may not ride empty", shield);
		Assert.assertEquals("the requirement outranks raw dps",
			4156, shield.getId());
		// The BiS ceiling wears it too - without it the fight is fiction.
		Gear bisShield = melee.overallBest.getLoadout().get(Slot.SHIELD);
		Assert.assertNotNull(bisShield);
		Assert.assertTrue("BiS complies: was " + bisShield.label(),
			bisShield.getLname().contains("mirror")
				|| bisShield.getLname().contains("v's shield"));
	}

	@Test
	public void aPinOutranksTheRequirement() throws Exception {
		Monster basilisk = data.searchMonsters("basilisk", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		owned.put(12954, 1);
		owned.put(4156, 1);
		Map<Slot, Integer> pin = new EnumMap<>(Slot.class);
		pin.put(Slot.SHIELD, 12954);
		Map<Style, Map<Slot, Integer>> pinnedByStyle =
			new EnumMap<>(Style.class);
		for (Style s : Style.concreteValues()) {
			pinnedByStyle.put(s, pin);
		}
		Service.Pick melee =
			best(basilisk, owned, pinnedByStyle).get(Style.MELEE);
		Assert.assertEquals("the player's explicit pin wins",
			12954, shieldOf(melee).getId());
	}

	@Test
	public void missingProtectionStillAnswersWithTheBestSet() throws Exception {
		// Owning no acceptable item still answers - and since 2026-10-22
		// (Andrew: "show them as necessary even when the player doesn't own
		// those items") the set wears the mirror shield, marked not owned.
		Monster basilisk = data.searchMonsters("basilisk", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		owned.put(12954, 1);
		Service.Pick melee =
			best(basilisk, owned, new EnumMap<>(Style.class)).get(Style.MELEE);
		Assert.assertNotNull(melee);
		Assert.assertFalse(melee.owned.isEmpty());
		Assert.assertEquals("the gaze shield is worn, unowned - not the defender",
			4156, shieldOf(melee).getId());
	}

	@Test
	public void harpiesNeedTheLitLanternToBeHarmedAtAll() throws Exception {
		Monster harpie = data.searchMonsters("harpie bug swarm", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		owned.put(12954, 1);
		owned.put(7053, 1);    // bug lantern (lit)
		Service.Pick melee =
			best(harpie, owned, new EnumMap<>(Style.class)).get(Style.MELEE);
		Assert.assertNotNull(melee);
		Assert.assertEquals("the lantern is the only legal shield",
			7053, shieldOf(melee).getId());
	}
}
