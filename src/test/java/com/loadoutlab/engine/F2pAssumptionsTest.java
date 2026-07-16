package com.loadoutlab.engine;

import com.loadoutlab.optimizer.BoostSelector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F2P mode used to assume members consumables and prayers (2026-07 player
 * audit A3.5): Super combat / Ranging potion / Saturated heart labels on
 * F2P cards, and game-best prayers of Piety/Rigour/Augury that do not
 * exist on a free world. F2P assumptions now cap at the real F2P book
 * (everything through Mystic Might) and F2P potions (attack + strength).
 * Also locks A2.12: sub-31 prayer tiers are NAMED, not silently applied.
 */
class F2pAssumptionsTest
{
	@Test
	@DisplayName("the F2P prayer ceiling is the 1.15 tier, by name")
	void f2pPrayerCeiling()
	{
		PrayerBonuses f2p = PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.F2P);
		assertEquals("Incredible Reflexes + Ultimate Strength", f2p.nameFor(CombatStyle.MELEE));
		assertEquals("Eagle Eye", f2p.nameFor(CombatStyle.RANGED));
		assertEquals("Mystic Might", f2p.nameFor(CombatStyle.MAGIC));
	}

	@Test
	@DisplayName("F2P boosts: attack+strength potions for melee, nothing for ranged/magic")
	void f2pBoosts()
	{
		assertEquals(BoostProfile.F2P_COMBAT, BoostSelector.bestFor(CombatStyle.MELEE, OwnedItems.EMPTY, true));
		assertEquals(BoostProfile.NONE, BoostSelector.bestFor(CombatStyle.RANGED, OwnedItems.EMPTY, true));
		assertEquals(BoostProfile.NONE, BoostSelector.bestFor(CombatStyle.MAGIC, OwnedItems.EMPTY, true));
		assertEquals(BoostProfile.F2P_COMBAT, BoostSelector.ceilingFor(CombatStyle.MELEE, true));
		// Members behaviour untouched.
		assertEquals(BoostProfile.SUPER_COMBAT, BoostSelector.bestFor(CombatStyle.MELEE, OwnedItems.EMPTY, false));
	}

	@Test
	@DisplayName("attack/strength potions boost +3 plus 10% of the level")
	void f2pCombatPotionMath()
	{
		PlayerLevels boosted = BoostProfile.F2P_COMBAT.apply(PlayerLevels.MAXED, null);
		assertEquals(99 + 3 + 9, boosted.getAttack());
		assertEquals(99 + 3 + 9, boosted.getStrength());
		assertEquals(99, boosted.getRanged(), "no F2P ranged boost exists");
		assertEquals(99, boosted.getMagic(), "no F2P magic boost exists");
	}

	@Test
	@DisplayName("sub-31 prayer tiers are named in the assumes label (audit A2.12)")
	void lowTierPrayersAreNamed()
	{
		PlayerLevels low = new PlayerLevels(99, 99, 99, 99, 99, 16, 99);
		PrayerBonuses prayers = PrayerBonuses.bestAvailable(low, PrayerUnlocks.ALL);
		assertEquals("Improved Reflexes + Superhuman Strength", prayers.nameFor(CombatStyle.MELEE));
		assertEquals("Sharp Eye", prayers.nameFor(CombatStyle.RANGED));
		assertEquals("Mystic Will", prayers.nameFor(CombatStyle.MAGIC));

		PlayerLevels mid = new PlayerLevels(99, 99, 99, 99, 99, 27, 99);
		PrayerBonuses midPrayers = PrayerBonuses.bestAvailable(mid, PrayerUnlocks.ALL);
		assertEquals("Hawk Eye", midPrayers.nameFor(CombatStyle.RANGED));
		assertEquals("Mystic Lore", midPrayers.nameFor(CombatStyle.MAGIC));
	}
}
