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
