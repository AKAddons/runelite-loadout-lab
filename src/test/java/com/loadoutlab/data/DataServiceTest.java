// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

import org.junit.Assert;
import org.junit.Test;

public class DataServiceTest
{
	@Test
	public void loadsMergedGearAndMonsterDataset()
	{
		LoadoutData data = new DataService().load();
		Assert.assertTrue(data.getGearItems().size() > 5000);
		Assert.assertTrue(data.getMonsters().size() > 2500);
		Assert.assertTrue(data.getSpells().size() > 20);
		Assert.assertFalse(data.searchMonsters("zulrah", 10).isEmpty());
	}

	@Test
	public void impossiblePvpVariantsAreNotStandardGear()
	{
		LoadoutData data = new DataService().load();
		GearItem vesta = data.getGear(22616);
		Assert.assertNotNull(vesta);
		Assert.assertFalse(vesta.isStandardGear());
	}

	@Test
	public void snapshotContainsPostMay2026Content()
	{
		LoadoutData data = new DataService().load();
		// Blood Moon Rises wave (June 2026): the Maggot King and the
		// Necklace of rupture, which outclasses the Necklace of anguish.
		Assert.assertFalse(data.searchMonsters("maggot king", 5).isEmpty());
		GearItem rupture = data.getGear(33639);
		Assert.assertNotNull(rupture);
		Assert.assertTrue(rupture.getBonuses().getRangedStrength()
			> data.getGear(19547).getBonuses().getRangedStrength());
	}

	@Test
	public void leaguesRewardsAreExcludedFromTheCorpus()
	{
		LoadoutData data = new DataService().load();
		for (GearItem item : data.getGearItems())
		{
			String name = item.getName().toLowerCase();
			Assert.assertFalse("leagues-only gear must not be suggestible: " + item.getName(),
				name.startsWith("echo ") || name.contains("trailblazer"));
		}
		// Spot-check the stat-relevant offender: Echo venator bow (charged).
		Assert.assertNull(data.getGear(30434));
	}

	@Test
	public void effectOnlySpellsAreExcludedFromTheCorpus()
	{
		LoadoutData data = new DataService().load();
		for (SpellStats spell : data.getSpells())
		{
			Assert.assertNotEquals("unselectable effect spells must not be castable",
				"Flames of Cerberus", spell.getName());
			Assert.assertNotEquals("unselectable effect spells must not be castable",
				"King's Ice Barrage", spell.getName());
		}
	}

	@Test
	public void loadsEquipmentRequirements()
	{
		LoadoutData data = new DataService().load();
		GearItem whip = data.getGear(4151);
		GearItem bandosChestplate = data.getGear(11832);
		Assert.assertNotNull(whip);
		Assert.assertNotNull(bandosChestplate);
		Assert.assertEquals(70, whip.getRequirements().getSkills().get("attack").intValue());
		Assert.assertEquals(65, bandosChestplate.getRequirements().getSkills().get("defence").intValue());
	}
}
