package com.ll.engine;

import com.ll.optimizer.BoostSelector;
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
class F2pAssumptionsTest {
	@Test
	@DisplayName("the F2P prayer ceiling is the 1.15 tier, by name")
	void f2pPrayerCeiling() {
		PrayerBonuses f2p = PrayerBonuses.bestAvailable(Levels.MAXED, Prayers.F2P);
		assertEquals("Incredible Reflexes + Ultimate Strength", f2p.nameFor(Style.MELEE));
		assertEquals("Eagle Eye", f2p.nameFor(Style.RANGED));
		assertEquals("Mystic Might", f2p.nameFor(Style.MAGIC));
	}

	@Test
	@DisplayName("F2P boosts: attack+strength potions for melee, nothing for ranged/magic")
	void f2pBoosts() {
		assertEquals(BoostProfile.F2P_COMBAT, BoostSelector.bestFor(Style.MELEE, Owned.EMPTY, true, false));
		assertEquals(BoostProfile.NONE, BoostSelector.bestFor(Style.RANGED, Owned.EMPTY, true, false));
		assertEquals(BoostProfile.NONE, BoostSelector.bestFor(Style.MAGIC, Owned.EMPTY, true, false));
		assertEquals(BoostProfile.F2P_COMBAT, BoostSelector.ceilingFor(Style.MELEE, true));
		// Members with an EMPTY bank now assume nothing either (2026-08-25):
		// this line used to expect SUPER_COMBAT, which was the same defect the
		// ironman field report caught. The F2P assertions above are unaffected -
		// F2P_COMBAT is a mode ceiling, not an owned item.
		assertEquals(BoostProfile.NONE, BoostSelector.bestFor(Style.MELEE, Owned.EMPTY, false, false));
	}

	@Test
	@DisplayName("attack/strength potions boost +3 plus 10% of the level")
	void f2pCombatPotionMath() {
		Levels boosted = BoostProfile.F2P_COMBAT.apply(Levels.MAXED, null);
		assertEquals(99 + 3 + 9, boosted.getAttack());
		assertEquals(99 + 3 + 9, boosted.getStrength());
		assertEquals(99, boosted.getRanged(), "no F2P ranged boost exists");
		assertEquals(99, boosted.getMagic(), "no F2P magic boost exists");
	}

	@Test
	@DisplayName("sub-31 prayer tiers are named in the assumes label (audit A2.12)")
	void lowTierPrayersAreNamed() {
		Levels low = new Levels(99, 99, 99, 99, 99, 16, 99);
		PrayerBonuses prayers = PrayerBonuses.bestAvailable(low, Prayers.ALL);
		assertEquals("Improved Reflexes + Superhuman Strength", prayers.nameFor(Style.MELEE));
		assertEquals("Sharp Eye", prayers.nameFor(Style.RANGED));
		assertEquals("Mystic Will", prayers.nameFor(Style.MAGIC));

		Levels mid = new Levels(99, 99, 99, 99, 99, 27, 99);
		PrayerBonuses midPrayers = PrayerBonuses.bestAvailable(mid, Prayers.ALL);
		assertEquals("Hawk Eye", midPrayers.nameFor(Style.RANGED));
		assertEquals("Mystic Lore", midPrayers.nameFor(Style.MAGIC));
	}
}
