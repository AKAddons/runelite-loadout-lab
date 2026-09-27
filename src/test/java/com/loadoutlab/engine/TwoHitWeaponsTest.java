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
 * N, Discord 2026-09-26 (glacial temotli): "is flat armor taken into
 * account? ... an additional 4 damage every hit seems pretty big boost
 * to dps". Wiki (Multi-hit weapons, 2026-09-27): the twin weapons and
 * Torag's hammers roll TWO INDEPENDENT hits on a halved max; the dark
 * bow fires two full arrows; charged Tonalztics throw twice at 75%.
 * Flat armour applies to each hitsplat.
 */
public class TwoHitWeaponsTest
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

	private static GearItem byName(String nameLower, String version)
	{
		return data().getGearItems().stream()
			.filter(g -> g.getNameLower().equals(nameLower) && g.isStandardGear()
				&& (version == null || version.equalsIgnoreCase(g.getVersion())))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing: " + nameLower));
	}

	private static DpsResult swing(String monster, CombatStyle style, GearItem weapon, String ammo)
	{
		MonsterStats mob = data().searchMonsters(monster, 1).get(0);
		OptimizationRequest request = TestRequests.of(mob, style,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, 1);
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, weapon);
		if (ammo != null)
		{
			gear.put(GearSlot.AMMO, byName(ammo, null));
		}
		DpsResult result = new DpsCalculator().calculate(request, new Loadout(gear));
		Assert.assertNotNull(result);
		return result;
	}

	private static void assertTwoIndependentHits(String weapon, String version)
	{
		MonsterStats moon = data().searchMonsters("blue moon", 1).get(0);
		int armour = moon.getDefensive().getFlatArmour();
		Assert.assertTrue("blue moon carries negative flat armour", armour < 0);
		DpsResult result = swing("blue moon", CombatStyle.MELEE, byName(weapon, version), null);
		int rawMax = result.getMaxHit() + armour;
		int firstRaw = rawMax / 2;
		int secondRaw = rawMax - firstRaw;
		double acc = result.getAccuracy();
		double expected = RollMath.expectedHitWithFlatArmour(acc, 0, firstRaw, armour)
			+ RollMath.expectedHitWithFlatArmour(acc, 0, secondRaw, armour);
		Assert.assertEquals(weapon + ": two independent hitsplats, each with the flat bonus",
			expected, result.getExpectedHit(), 1e-9);
	}

	@Test
	public void theTwinWeaponsRollTwoIndependentHits()
	{
		assertTwoIndependentHits("glacial temotli", null);
		assertTwoIndependentHits("sulphur blades", null);
		assertTwoIndependentHits("earthbound tecpatl", null);
		assertTwoIndependentHits("torag's hammers", "Undamaged");
	}

	@Test
	public void theDarkBowFiresTwoFullArrows()
	{
		DpsResult bow = swing("goblin", CombatStyle.RANGED, byName("dark bow", "Regular"), "dragon arrow");
		double one = RollMath.expectedHitWithFlatArmour(bow.getAccuracy(), 0, bow.getMaxHit(), 0);
		Assert.assertEquals("two arrows, each a full roll", 2 * one, bow.getExpectedHit(), 1e-9);
	}

	@Test
	public void chargedTonalzticsThrowTwiceAtThreeQuarters()
	{
		DpsResult charged = swing("goblin", CombatStyle.RANGED, byName("tonalztics of ralos", "Charged"), null);
		DpsResult uncharged = swing("goblin", CombatStyle.RANGED, byName("tonalztics of ralos", "Uncharged"), null);
		Assert.assertEquals("both versions roll 75% of the max", uncharged.getMaxHit(), charged.getMaxHit());
		double one = RollMath.expectedHitWithFlatArmour(charged.getAccuracy(), 0, charged.getMaxHit(), 0);
		Assert.assertEquals("charged throws twice", 2 * one, charged.getExpectedHit(), 1e-9);
		Assert.assertEquals("uncharged throws once", one, uncharged.getExpectedHit(), 1e-9);
	}
}
