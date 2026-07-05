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
