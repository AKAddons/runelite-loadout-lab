package com.loadoutlab.engine;

import org.junit.Assert;
import org.junit.Test;

/**
 * The assume-chip prayer picker (field direction 2026-07-21: "I don't use
 * Piety against every mob"): named tiers per style whose factors mirror
 * bestAvailable's cascade exactly.
 */
public class PrayerPickTest
{
	@Test
	public void picksReplaceOnlyTheChosenStylesFactors()
	{
		PrayerBonuses best = PrayerBonuses.bestAvailable(PlayerLevels.MAXED);
		PrayerBonuses chivalry = PrayerBonuses.forPick(CombatStyle.MELEE, "Chivalry", best);
		Assert.assertEquals(1.15, chivalry.getMeleeAccuracy(), 1e-9);
		Assert.assertEquals(1.18, chivalry.getMeleeStrength(), 1e-9);
		Assert.assertEquals("Chivalry", chivalry.nameFor(CombatStyle.MELEE));
		Assert.assertEquals("the other styles keep the fallback",
			best.getRangedAccuracy(), chivalry.getRangedAccuracy(), 1e-9);

		PrayerBonuses eagle = PrayerBonuses.forPick(CombatStyle.RANGED, "Eagle Eye", best);
		Assert.assertEquals(1.15, eagle.getRangedAccuracy(), 1e-9);
		Assert.assertEquals(1.15, eagle.getRangedStrength(), 1e-9);

		PrayerBonuses might = PrayerBonuses.forPick(CombatStyle.MAGIC, "Mystic Might", best);
		Assert.assertEquals(1.15, might.getMagicAccuracy(), 1e-9);
		Assert.assertEquals(2.0, might.getMagicDamagePercent(), 1e-9);

		// The lower melee combo tiers (field ask 2026-07-21): 1.10/1.10
		// and 1.05/1.05, mirroring bestAvailable's cascade exactly.
		PrayerBonuses mid = PrayerBonuses.forPick(CombatStyle.MELEE,
			"Superhuman Strength + Improved Reflexes", best);
		Assert.assertEquals(1.10, mid.getMeleeAccuracy(), 1e-9);
		Assert.assertEquals(1.10, mid.getMeleeStrength(), 1e-9);
		PrayerBonuses low = PrayerBonuses.forPick(CombatStyle.MELEE,
			"Burst of Strength + Clarity of Thought", best);
		Assert.assertEquals(1.05, low.getMeleeAccuracy(), 1e-9);
		Assert.assertEquals(1.05, low.getMeleeStrength(), 1e-9);

		// The lower ranged/magic tiers (field ask 2026-07-21 part 2),
		// mirroring the cascade: Hawk/Sharp Eye, Mystic Lore/Will.
		Assert.assertEquals(1.10, PrayerBonuses.forPick(CombatStyle.RANGED,
			"Hawk Eye", best).getRangedAccuracy(), 1e-9);
		Assert.assertEquals(1.05, PrayerBonuses.forPick(CombatStyle.RANGED,
			"Sharp Eye", best).getRangedStrength(), 1e-9);
		PrayerBonuses lore = PrayerBonuses.forPick(CombatStyle.MAGIC, "Mystic Lore", best);
		Assert.assertEquals(1.10, lore.getMagicAccuracy(), 1e-9);
		Assert.assertEquals(1.0, lore.getMagicDamagePercent(), 1e-9);
		PrayerBonuses will = PrayerBonuses.forPick(CombatStyle.MAGIC, "Mystic Will", best);
		Assert.assertEquals(1.05, will.getMagicAccuracy(), 1e-9);
		Assert.assertEquals(0.0, will.getMagicDamagePercent(), 1e-9);
	}

	@Test
	public void unknownPicksFallBackToDetect()
	{
		PrayerBonuses best = PrayerBonuses.bestAvailable(PlayerLevels.MAXED);
		Assert.assertSame(best, PrayerBonuses.forPick(CombatStyle.MELEE, "Retribution", best));
	}

	@Test
	public void everyStyleOffersItsNamedTiers()
	{
		Assert.assertEquals("Piety", PrayerBonuses.optionsFor(CombatStyle.MELEE)[0]);
		Assert.assertEquals("Rigour", PrayerBonuses.optionsFor(CombatStyle.RANGED)[0]);
		Assert.assertEquals("Augury", PrayerBonuses.optionsFor(CombatStyle.MAGIC)[0]);
	}

	@Test
	public void boostMembershipDrivesTheStyleMenus()
	{
		Assert.assertTrue(BoostProfile.SMELLING_SALTS.boosts('a'));
		Assert.assertTrue(BoostProfile.SMELLING_SALTS.boosts('m'));
		Assert.assertTrue(BoostProfile.SUPER_COMBAT.boosts('a'));
		Assert.assertFalse(BoostProfile.SUPER_COMBAT.boosts('r'));
		Assert.assertTrue(BoostProfile.RANGING.boosts('r'));
		Assert.assertFalse(BoostProfile.RANGING.boosts('m'));
	}
}
