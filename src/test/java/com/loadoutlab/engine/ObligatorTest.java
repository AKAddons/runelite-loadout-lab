package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterDefences;
import com.loadoutlab.data.Monster;
import java.util.Collections;
import java.util.EnumMap;
import org.junit.Assert;
import org.junit.Test;

/**
 * The Obligator (the Fractured Archive, release post 2026-10-20): Lunge
 * (5 ticks) rolls damage twice, three times when crush is the target's
 * second-weakest melee defence, four when weakest, and keeps the best;
 * +20% damage on 2x2, +40% on 3x3 or bigger. Smash (3 ticks) drops the
 * re-rolls and the size bonus. Assumed: one accuracy roll (the post says
 * "rolls damage"), a defence tie is not weaker.
 */
public class ObligatorTest {
	private static Monster dummy(int size, int stab, int slash, int crush) {
		return new Monster(-50 - size, "Obligator Dummy", "", 300, 2000, size, 150, 1, 0,
			new MonsterDefences(stab, slash, crush, 100, 0, 60, 60, 60), null,
			Collections.emptyList(), false, "", 0);
	}

	private static DpsResult swing(Monster target) {
		LoadoutData data = new DataService().load();
		EnumMap<GearSlot, Gear> worn = new EnumMap<>(GearSlot.class);
		worn.put(GearSlot.WEAPON, data.getGear(34715));
		OptimizationRequest request = new OptimizationRequest(target, Style.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.NONE, null, 0, CandidateMode.ALL_STANDARD, false, false, OwnedItems.EMPTY,
			RequirementProfile.MAXED, 10);
		return new DpsCalculator().calculate(request, new Loadout(worn));
	}

	@Test
	public void bestOfOneIsTheOrdinaryHit() {
		for (int max : new int[]{1, 7, 42}) {
			Assert.assertEquals(RollMath.normalExpectedHit(0.7, max), RollMath.bestOfExpected(0.7, max, 1, 0), 1e-9);
			Assert.assertEquals(RollMath.expectedHitWithFlatArmour(0.7, 0, max, 3), RollMath.bestOfExpected(0.7, max, 1, 3), 1e-9);
			Assert.assertEquals(RollMath.expectedHitWithFlatArmour(0.7, 0, max, -5), RollMath.bestOfExpected(0.7, max, 1, -5), 1e-9);
		}
		Assert.assertTrue(RollMath.bestOfExpected(1, 40, 4, 0) > RollMath.bestOfExpected(1, 40, 2, 0));
		// Best of two on 0..2: P(max=0)=1/9 (counts 1), P(1)=3/9, P(2)=5/9.
		Assert.assertEquals((1 + 3 + 10) / 9.0, RollMath.bestOfExpected(1, 2, 2, 0), 1e-9);
	}

	@Test
	public void lungeRollsFourTimesWithTheSizeBonusOnABigCrushWeakTarget() {
		DpsResult small = swing(dummy(1, 0, 0, 300));
		DpsResult big = swing(dummy(3, 200, 200, 10));
		Assert.assertEquals(5, big.getAttackSpeed());
		Assert.assertTrue(big.getAttackType(), big.getAttackType().contains("lunge"));
		Assert.assertEquals(small.getMaxHit() * 7 / 5, big.getMaxHit());
		Assert.assertEquals(RollMath.bestOfExpected(big.getAccuracy(), big.getMaxHit(), 4, 0), big.getExpectedHit(), 1e-9);
	}

	@Test
	public void crushSecondWeakestRollsThreeTimesAndATwoByTwoTakesTwentyPercent() {
		DpsResult small = swing(dummy(1, 0, 0, 300));
		DpsResult mid = swing(dummy(2, 200, 0, 100));
		Assert.assertTrue(mid.getAttackType(), mid.getAttackType().contains("lunge"));
		Assert.assertEquals(small.getMaxHit() * 6 / 5, mid.getMaxHit());
		Assert.assertEquals(RollMath.bestOfExpected(mid.getAccuracy(), mid.getMaxHit(), 3, 0), mid.getExpectedHit(), 1e-9);
	}

	@Test
	public void theGameBestSearchReachesItOnABigCrushWeakBoss() {
		// Cerberus (5x5, crush the weakest melee defence): the Lunge beats
		// every other melee weapon (pinned 11.7 vs 9.8 in the 2026-10-20
		// sweep), so the unpinned search must reach it - the raw-stat
		// shortlist used to cut it before the damage step.
		LoadoutData data = new DataService().load();
		Monster cerberus = data.searchMonsters("cerberus", 1).get(0);
		OptimizationRequest request = TestRequests.of(cerberus, Style.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0, CandidateMode.ALL_STANDARD,
			true, false, OwnedItems.EMPTY, 1);
		String weapon = new LoadoutOptimizer().optimize(data, request).get(0).getLoadout().getWeapon().getName();
		Assert.assertEquals("Obligator", weapon);
	}

	@Test
	public void smashWinsWhereTheLungeHasLittleToRoll() {
		DpsResult small = swing(dummy(1, 0, 0, 300));
		Assert.assertEquals(3, small.getAttackSpeed());
		Assert.assertTrue(small.getAttackType(), small.getAttackType().contains("smash"));
		Assert.assertEquals(RollMath.normalExpectedHit(small.getAccuracy(), small.getMaxHit()), small.getExpectedHit(), 1e-9);
	}
}
