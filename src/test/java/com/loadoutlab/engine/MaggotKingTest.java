package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterGroups;
import com.loadoutlab.data.MonsterStats;
import org.junit.Assert;
import org.junit.Test;

/**
 * Maggot King phase model (field report grZ 2026-08-20: "checking
 * maggot king far, it does recommend melee, which is impossible";
 * wiki-verified: "it cannot be attacked with melee until it falls
 * below ~980 hitpoints (65%)" - the Far phase IS the out-of-reach
 * state, he closes in for the Nearby/Roaring rows). Royal Titans
 * pattern: the Far row is melee-immune, the group rosters all three
 * corpus versions so the fight reads as one result.
 */
public class MaggotKingTest
{
	private static LoadoutData data;

	private static LoadoutData data()
	{
		if (data == null)
		{
			data = new DataService().load();
		}
		return data;
	}

	private static MonsterStats version(String version)
	{
		for (MonsterStats monster : data().getMonsters())
		{
			if ("Maggot King".equalsIgnoreCase(monster.getName())
				&& version.equalsIgnoreCase(monster.getVersion()))
			{
				return monster;
			}
		}
		throw new AssertionError("no Maggot King (" + version + ") row");
	}

	@Test
	public void theFarPhaseIsMeleeImmuneTheOthersAreNot()
	{
		MonsterStats far = version("Far");
		Assert.assertTrue("Far is out of melee reach",
			MonsterMechanics.styleImmune(far, CombatStyle.MELEE));
		Assert.assertFalse(MonsterMechanics.styleImmune(far, CombatStyle.RANGED));
		Assert.assertFalse(MonsterMechanics.styleImmune(far, CombatStyle.MAGIC));
		Assert.assertFalse("he closes in - Nearby takes melee",
			MonsterMechanics.styleImmune(version("Nearby"), CombatStyle.MELEE));
		Assert.assertFalse("the punish window takes melee",
			MonsterMechanics.styleImmune(version("Roaring"), CombatStyle.MELEE));
	}

	/** The version cache-collision (field report 2026-08-20, grZ + Andrew):
	 * all three MK versions share id 15742 with IDENTICAL melee stats, and
	 * both optimizer cache keys carried only getId() - so whichever version
	 * computed first served its answer to the siblings. Nearby-then-Far on
	 * ONE service is the deterministic poisoning: Far's melee must still
	 * come back empty (it is immune), not Nearby's cached 5.92. */
	@Test
	public void aWarmSiblingCacheNeverLendsFarAMeleeSet() throws Exception
	{
		com.loadoutlab.optimizer.OptimizerService service =
			new com.loadoutlab.optimizer.OptimizerService(data());
		try
		{
			Assert.assertNotNull("Nearby answers melee",
				singleStyles(service, version("Nearby"))
					.get(CombatStyle.MELEE).overallBest);
			com.loadoutlab.optimizer.OptimizerService.StyleResult far =
				singleStyles(service, version("Far")).get(CombatStyle.MELEE);
			Assert.assertTrue("Far melee owned must stay empty on a warm cache",
				far == null || far.owned == null || far.owned.isEmpty());
			Assert.assertTrue("Far melee BiS must stay empty on a warm cache",
				far == null || far.overallBest == null);
		}
		finally
		{
			service.shutdown();
		}
	}

	private static java.util.Map<CombatStyle, com.loadoutlab.optimizer.OptimizerService.StyleResult>
		singleStyles(com.loadoutlab.optimizer.OptimizerService service, MonsterStats mob)
		throws Exception
	{
		java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
		java.util.concurrent.atomic.AtomicReference<java.util.Map<CombatStyle,
			com.loadoutlab.optimizer.OptimizerService.StyleResult>> out =
			new java.util.concurrent.atomic.AtomicReference<>();
		com.loadoutlab.optimizer.ServiceCalls.bestPerStyle(service, mob,
			PlayerLevels.MAXED, PlayerLevels.MAXED, PrayerUnlocks.ALL,
			RequirementProfile.MAXED, OwnedItems.EMPTY, 0,
			false, false, "", new java.util.EnumMap<>(CombatStyle.class), -1,
			com.loadoutlab.engine.OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, false,
			java.util.Collections.emptySet(), 0,
			java.util.Collections.emptyMap(), null, 0, java.util.Collections.emptySet(),
			r ->
			{
				out.set(r);
				done.countDown();
			});
		Assert.assertTrue(done.await(300, java.util.concurrent.TimeUnit.SECONDS));
		return out.get();
	}

	@Test
	public void theGroupRostersAllThreePhases()
	{
		for (MonsterGroups.MonsterGroup group : MonsterGroups.load(data()))
		{
			if ("Maggot King".equals(group.getName()))
			{
				Assert.assertEquals(3, group.getMobs().size());
				long meleeImmune = group.getMobs().stream()
					.filter(m -> MonsterMechanics.styleImmune(m, CombatStyle.MELEE))
					.count();
				Assert.assertEquals("exactly the Far phase is out of reach",
					1, meleeImmune);
				return;
			}
		}
		throw new AssertionError("Maggot King group missing");
	}
}
