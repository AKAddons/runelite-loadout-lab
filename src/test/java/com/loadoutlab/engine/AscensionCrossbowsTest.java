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
 * The Ascension crossbows (the Fractured Archive, 2026-10-20; wiki): a
 * two-handed 3-tick crossbow that fires only ascension bolts (plain,
 * diamond, onyx and their enchanted forms), which fit no other weapon.
 */
public class AscensionCrossbowsTest
{
	private static final int CROSSBOWS = 34681;
	private static final int[] ASCENSION_BOLTS = {34684, 34690, 34696, 34702, 34708};

	@Test
	public void onlyAscensionBoltsFitAndTheyFitNothingElse()
	{
		LoadoutData data = new DataService().load();
		GearItem crossbows = data.getGear(CROSSBOWS);
		Assert.assertNotNull(crossbows);
		for (int id : ASCENSION_BOLTS)
		{
			Assert.assertTrue("bolts " + id, RangedAmmo.compatible(data.getGear(id), crossbows));
		}
		Assert.assertFalse("no ammo", RangedAmmo.compatible(null, crossbows));
		GearItem runiteBolts = data.getGear(9144);
		GearItem runeCrossbow = data.getGear(9185);
		Assert.assertNotNull(runiteBolts);
		Assert.assertNotNull(runeCrossbow);
		Assert.assertFalse("runite bolts", RangedAmmo.compatible(runiteBolts, crossbows));
		Assert.assertFalse("a rune crossbow", RangedAmmo.compatible(data.getGear(34684), runeCrossbow));
	}

	@Test
	public void rapidFiresAtTwoTicks()
	{
		LoadoutData data = new DataService().load();
		MonsterStats goblin = data.searchMonsters("goblin", 1).get(0);
		OptimizationRequest request = new OptimizationRequest(goblin, CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.NONE, null, 0, CandidateMode.ALL_STANDARD, false, false, OwnedItems.EMPTY,
			RequirementProfile.MAXED, 10);
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, data.getGear(CROSSBOWS));
		gear.put(GearSlot.AMMO, data.getGear(34702));
		DpsResult result = new DpsCalculator().calculate(request, new Loadout(gear));
		Assert.assertEquals(2, result.getAttackSpeed());
		Assert.assertTrue(result.getAttackType(), result.getAttackType().contains("rapid"));
	}
}
