package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
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
public class FlatArmourPerHitTest {
	private static Tables data;

	private static Tables data() {
		if (data == null) {
			data = new DataService().load();
		}
		return data;
	}

	private static Gear byName(String lname) {
		return data().getGearItems().stream()
			.filter(g -> g.getLname().equals(lname) && g.isStandardGear())
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing: " + lname));
	}

	private static Dps macuahuitlVs(String monster) {
		Monster mob = data().searchMonsters(monster, 1).get(0);
		Req request = TestRequests.of(mob, Style.MELEE,
			Levels.MAXED, PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, Owned.EMPTY, 1);
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, byName("dual macuahuitl"));
		Dps result = new Calc().calculate(request, new Kit(gear));
		Assert.assertNotNull(result);
		return result;
	}

	@Test
	public void blueMoonCreditsTheMinusFiveOnBothHitsplats() {
		Dps result = macuahuitlVs("blue moon");
		// Each hitsplat carries its own +5, applied AFTER the accurate-
		// zero rule (a landed 0 counts as 1) - repinned 2026-08-21 from
		// the old bound-shift model, which dropped that bump and ran
		// 0.5% low; the harness vector macuahuitl-bluemoon is now exact.
		int rawMax = result.getMaxHit() - 5;
		int firstRaw = rawMax / 2;
		int secondRaw = rawMax - firstRaw;
		double acc = result.getAccuracy();
		double perHitExpected = RollMath.expectedHitWithFlatArmour(acc, 0, firstRaw, -5)
			+ acc * RollMath.expectedHitWithFlatArmour(acc, 0, secondRaw, -5);
		Assert.assertEquals("each hitsplat carries its own +5",
			perHitExpected, result.getEh(), 1e-9);
	}

	@Test
	public void eclipseMoonChargesTheArmourOnBothHitsplats() {
		Monster mob = data().searchMonsters("eclipse moon", 1).get(0);
		int armour = mob.getDefensive().getFlatArmour();
		Assert.assertTrue("eclipse forms carry positive flat armour", armour > 0);
		Dps result = macuahuitlVs("eclipse moon");
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
			perHitExpected, result.getEh(), 1e-9);
	}
}
