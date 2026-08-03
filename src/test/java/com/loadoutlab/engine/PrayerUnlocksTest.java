package com.loadoutlab.engine;

import org.junit.Assert;
import org.junit.Test;

public class PrayerUnlocksTest
{
	@Test
	public void lockedPrayersFallBackToTheBestUnlockedTier()
	{
		PlayerLevels maxed = PlayerLevels.MAXED;

		// No King's Ransom: no Piety - falls to Ultimate Strength tier.
		PrayerBonuses noKr = PrayerBonuses.bestAvailable(maxed,
			new PrayerUnlocks(false, true, true, true, true));
		Assert.assertEquals(1.15, noKr.getMeleeAccuracy(), 1e-9);
		Assert.assertEquals(1.15, noKr.getMeleeStrength(), 1e-9);

		// No Rigour scroll but Deadeye unlocked: 18/18.
		PrayerBonuses noRigour = PrayerBonuses.bestAvailable(maxed,
			new PrayerUnlocks(true, false, true, true, true));
		Assert.assertEquals(1.18, noRigour.getRangedAccuracy(), 1e-9);
		Assert.assertEquals(1.18, noRigour.getRangedStrength(), 1e-9);

		// Neither Rigour nor Deadeye: Eagle Eye tier.
		PrayerBonuses eagleEye = PrayerBonuses.bestAvailable(maxed,
			new PrayerUnlocks(true, false, true, false, true));
		Assert.assertEquals(1.15, eagleEye.getRangedAccuracy(), 1e-9);

		// No Augury but Mystic Vigour: 1.18 accuracy, 3% damage.
		PrayerBonuses noAugury = PrayerBonuses.bestAvailable(maxed,
			new PrayerUnlocks(true, true, false, true, true));
		Assert.assertEquals(1.18, noAugury.getMagicAccuracy(), 1e-9);
		Assert.assertEquals(3.0, noAugury.getMagicDamagePercent(), 1e-9);

		// Everything unlocked: Augury WINS over Mystic Vigour, it never
		// stacks with it - all magic prayers share one prayer group, so
		// only one can be active (the wiki calc engine happily stacks
		// illegal combos; the game does not).
		PrayerBonuses all = PrayerBonuses.bestAvailable(maxed, PrayerUnlocks.ALL);
		Assert.assertEquals(1.20, all.getMeleeAccuracy(), 1e-9);
		Assert.assertEquals(1.25, all.getMagicAccuracy(), 1e-9);
		Assert.assertEquals(4.0, all.getMagicDamagePercent(), 1e-9);
		Assert.assertEquals("Augury", all.nameFor(CombatStyle.MAGIC));
	}
}
