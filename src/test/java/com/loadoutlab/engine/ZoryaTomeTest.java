package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import org.junit.Assert;
import org.junit.Test;

/**
 * Zorya's tome (the Fractured Archive, 2026-10-20; wiki): a two-handed
 * powered staff, 3-tick, base max hit 18 (flat, like the crystal staff),
 * 15% magic damage of its own.
 */
public class ZoryaTomeTest
{
	@Test
	public void threeTicksFromABaseOfEighteen()
	{
		LoadoutData data = new DataService().load();
		GearItem tome = data.getGear(34725);
		Assert.assertNotNull(tome);
		Assert.assertTrue(tome.isPoweredStaff());
		MonsterStats goblin = data.searchMonsters("goblin", 1).get(0);
		OptimizationRequest request = new OptimizationRequest(goblin, CombatStyle.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.NONE, null, 0, CandidateMode.ALL_STANDARD, false, false, OwnedItems.EMPTY,
			RequirementProfile.MAXED, 10);
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, tome);
		DpsResult result = new DpsCalculator().calculate(request, new Loadout(gear));
		Assert.assertEquals(3, result.getAttackSpeed());
		// floor(18 * 1.15): the base with the tome's own 15%.
		Assert.assertEquals(20, result.getMaxHit());
	}
}
