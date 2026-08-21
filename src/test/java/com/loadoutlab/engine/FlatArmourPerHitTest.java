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
 * Flat armour applies PER HITSPLAT (official calc: a hit-distribution
 * transformer on accurate hits). Blue Moon's -5 rewards multi-hit
 * weapons twice (wiki: "weapons which deal damage via multiple
 * hitsplats are particularly effective here" - field report
 * 2026-08-10: "double hits on blue moon is good"); Eclipse Moon's +6
 * punishes them twice. The old order split the macuahuitl's max AFTER
 * armour, crediting the -5 only once.
 */
public class FlatArmourPerHitTest
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

	private static GearItem byName(String nameLower)
	{
		return data().getGearItems().stream()
			.filter(g -> g.getNameLower().equals(nameLower) && g.isStandardGear())
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing: " + nameLower));
	}

	private static DpsResult macuahuitlVs(String monster)
	{
		MonsterStats mob = data().searchMonsters(monster, 1).get(0);
		OptimizationRequest request = TestRequests.of(mob, CombatStyle.MELEE,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, 1);
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, byName("dual macuahuitl"));
		DpsResult result = new DpsCalculator().calculate(request, new Loadout(gear));
		Assert.assertNotNull(result);
		return result;
	}

	@Test
	public void blueMoonCreditsTheMinusFiveOnBothHitsplats()
	{
		DpsResult result = macuahuitlVs("blue moon");
		// Displayed max is the combined roll +5 once; the raw combined
		// max is that minus 5, split into the two chained hitsplats, each
		// then rolling 5..half+5 (accurate hits only).
		int rawMax = result.getMaxHit() - 5;
		int firstRaw = rawMax / 2;
		int secondRaw = rawMax - firstRaw;
		double acc = result.getAccuracy();
		double perHitExpected = RollMath.expectedHit(acc, 5, firstRaw + 5)
			+ acc * RollMath.expectedHit(acc, 5, secondRaw + 5);
		Assert.assertEquals("each hitsplat carries its own +5",
			perHitExpected, result.getExpectedHit(), 1e-9);
	}

	@Test
	public void eclipseMoonChargesTheArmourOnBothHitsplats()
	{
		MonsterStats mob = data().searchMonsters("eclipse moon", 1).get(0);
		int armour = mob.getDefensive().getFlatArmour();
		Assert.assertTrue("eclipse forms carry positive flat armour", armour > 0);
		DpsResult result = macuahuitlVs("eclipse moon");
		int rawMax = result.getMaxHit() + armour;
		int firstRaw = rawMax / 2;
		int secondRaw = rawMax - firstRaw;
		double acc = result.getAccuracy();
		// Per-roll clamp, NOT a bound shift (official-calc verified
		// 2026-08-21: the shifted model overpriced Eclipse ~16% - the
		// noxhalberd-eclipsemoon harness vector now matches to 0.00%).
		double perHitExpected = RollMath.expectedHitWithFlatArmour(acc, 0, firstRaw, armour)
			+ acc * RollMath.expectedHitWithFlatArmour(acc, 0, secondRaw, armour);
		Assert.assertEquals("each hitsplat pays the armour on every roll",
			perHitExpected, result.getExpectedHit(), 1e-9);
	}
}
