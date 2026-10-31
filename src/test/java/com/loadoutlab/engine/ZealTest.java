package com.loadoutlab.engine;

import org.junit.Assert;
import org.junit.Test;

/**
 * Zeal (the Fractured Archive, 2026-10-20; wiki): 84 Prayer, 75 Attack
 * and 75 Strength, +25% accuracy / +28% strength, the tier above Piety.
 * Unlocked by a raid reward (VarbitID.PRAYER_ZEAL_UNLOCKED).
 */
public class ZealTest {
	private static final PrayerUnlocks NO_ZEAL = new PrayerUnlocks(true, true, true, true, true, false);

	@Test
	public void zealIsTheMeleeTierAbovePietyWithTheUnlock() {
		PrayerBonuses all = PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL);
		Assert.assertEquals(1.25, all.getMeleeAccuracy(), 1e-9);
		Assert.assertEquals(1.28, all.getMeleeStrength(), 1e-9);
		Assert.assertEquals("Zeal", all.nameFor(Style.MELEE));
	}

	@Test
	public void withoutTheUnlockPietyStands() {
		PrayerBonuses noZeal = PrayerBonuses.bestAvailable(PlayerLevels.MAXED, NO_ZEAL);
		Assert.assertEquals(1.20, noZeal.getMeleeAccuracy(), 1e-9);
		Assert.assertEquals(1.23, noZeal.getMeleeStrength(), 1e-9);
		Assert.assertEquals("Piety", noZeal.nameFor(Style.MELEE));

		// The five-field form (older profiles, the harness) has no Zeal.
		PrayerBonuses legacy = PrayerBonuses.bestAvailable(PlayerLevels.MAXED,
			new PrayerUnlocks(true, true, true, true, true));
		Assert.assertEquals("Piety", legacy.nameFor(Style.MELEE));
		Assert.assertFalse(PrayerUnlocks.F2P.zeal());
		Assert.assertTrue(PrayerUnlocks.ALL.zeal());
		Assert.assertNotEquals("the cache key tells the two apart",
			PrayerUnlocks.ALL.key(), NO_ZEAL.key());
	}

	@Test
	public void theLevelGatesHoldPrayerAttackAndStrength() {
		Assert.assertEquals("Piety", PrayerBonuses.bestAvailable(
			new PlayerLevels(99, 99, 99, 99, 99, 83, 99), PrayerUnlocks.ALL).nameFor(Style.MELEE));
		Assert.assertEquals("Zeal", PrayerBonuses.bestAvailable(
			new PlayerLevels(99, 99, 99, 99, 99, 84, 99), PrayerUnlocks.ALL).nameFor(Style.MELEE));
		Assert.assertEquals("Piety", PrayerBonuses.bestAvailable(
			new PlayerLevels(74, 99, 99, 99, 99, 99, 99), PrayerUnlocks.ALL).nameFor(Style.MELEE));
		Assert.assertEquals("Piety", PrayerBonuses.bestAvailable(
			new PlayerLevels(99, 74, 99, 99, 99, 99, 99), PrayerUnlocks.ALL).nameFor(Style.MELEE));
	}

	@Test
	public void thePickerOffersZealFirst() {
		Assert.assertEquals("Zeal", PrayerBonuses.optionsFor(Style.MELEE)[0]);
		Assert.assertEquals("Piety", PrayerBonuses.optionsFor(Style.MELEE)[1]);
		PrayerBonuses best = PrayerBonuses.bestAvailable(PlayerLevels.MAXED, NO_ZEAL);
		PrayerBonuses zeal = PrayerBonuses.forPick(Style.MELEE, "Zeal", best);
		Assert.assertEquals(1.25, zeal.getMeleeAccuracy(), 1e-9);
		Assert.assertEquals(1.28, zeal.getMeleeStrength(), 1e-9);
		Assert.assertEquals("Zeal", zeal.nameFor(Style.MELEE));
	}
}
