package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
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
public class RequiredGearOptimizerTest
{
	private static LoadoutData data;

	@BeforeClass
	public static void load()
	{
		data = new DataService().load();
	}

	private static Map<CombatStyle, OptimizerService.StyleResult> best(
		MonsterStats monster, Map<Integer, Integer> owned,
		Map<CombatStyle, Map<GearSlot, Integer>> pinnedByStyle) throws Exception
	{
		OptimizerService service = new OptimizerService(data);
		try
		{
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out =
				new AtomicReference<>();
			ServiceCalls.bestPerStyle(service, monster,
				PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
				RequirementProfile.MAXED, new OwnedItems(owned, true), 1,
				false, false, "", new EnumMap<>(CombatStyle.class), -1,
				com.loadoutlab.engine.OptimizationRequest.DEFAULT_RISK_BUDGET_GP,
				false, false, Collections.emptySet(), 0,
				pinnedByStyle, null, Collections.emptySet(),
				r -> { out.set(r); done.countDown(); });
			Assert.assertTrue("compute timed out", done.await(120, TimeUnit.SECONDS));
			return out.get();
		}
		finally
		{
			service.shutdown();
		}
	}

	private static GearItem shieldOf(OptimizerService.StyleResult sr)
	{
		return sr.owned.get(0).getLoadout().get(GearSlot.SHIELD);
	}

	@Test
	public void basiliskForcesTheGazeShieldOverADpsShield() throws Exception
	{
		MonsterStats basilisk = data.searchMonsters("basilisk", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);    // whip
		owned.put(12954, 1);   // dragon defender - the raw dps pick
		owned.put(4156, 1);    // mirror shield
		OptimizerService.StyleResult melee =
			best(basilisk, owned, new EnumMap<>(CombatStyle.class)).get(CombatStyle.MELEE);
		Assert.assertNotNull(melee);
		GearItem shield = shieldOf(melee);
		Assert.assertNotNull("the gaze slot may not ride empty", shield);
		Assert.assertEquals("the requirement outranks raw dps",
			4156, shield.getId());
		// The BiS ceiling wears it too - without it the fight is fiction.
		GearItem bisShield = melee.overallBest.getLoadout().get(GearSlot.SHIELD);
		Assert.assertNotNull(bisShield);
		Assert.assertTrue("BiS complies: was " + bisShield.label(),
			bisShield.getNameLower().contains("mirror")
				|| bisShield.getNameLower().contains("v's shield"));
	}

	@Test
	public void aPinOutranksTheRequirement() throws Exception
	{
		MonsterStats basilisk = data.searchMonsters("basilisk", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		owned.put(12954, 1);
		owned.put(4156, 1);
		Map<GearSlot, Integer> pin = new EnumMap<>(GearSlot.class);
		pin.put(GearSlot.SHIELD, 12954);
		Map<CombatStyle, Map<GearSlot, Integer>> pinnedByStyle =
			new EnumMap<>(CombatStyle.class);
		for (CombatStyle s : CombatStyle.concreteValues())
		{
			pinnedByStyle.put(s, pin);
		}
		OptimizerService.StyleResult melee =
			best(basilisk, owned, pinnedByStyle).get(CombatStyle.MELEE);
		Assert.assertEquals("the player's explicit pin wins",
			12954, shieldOf(melee).getId());
	}

	@Test
	public void missingProtectionStillAnswersWithTheBestSet() throws Exception
	{
		// All-or-nothing honesty (the dragonfire rule): owning no
		// acceptable item must not blank the card - the note explains.
		MonsterStats basilisk = data.searchMonsters("basilisk", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		owned.put(12954, 1);
		OptimizerService.StyleResult melee =
			best(basilisk, owned, new EnumMap<>(CombatStyle.class)).get(CombatStyle.MELEE);
		Assert.assertNotNull(melee);
		Assert.assertFalse(melee.owned.isEmpty());
		Assert.assertEquals("unforced - the defender stands",
			12954, shieldOf(melee).getId());
	}

	@Test
	public void harpiesNeedTheLitLanternToBeHarmedAtAll() throws Exception
	{
		MonsterStats harpie = data.searchMonsters("harpie bug swarm", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		owned.put(12954, 1);
		owned.put(7053, 1);    // bug lantern (lit)
		OptimizerService.StyleResult melee =
			best(harpie, owned, new EnumMap<>(CombatStyle.class)).get(CombatStyle.MELEE);
		Assert.assertNotNull(melee);
		Assert.assertEquals("the lantern is the only legal shield",
			7053, shieldOf(melee).getId());
	}
}
