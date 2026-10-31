package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Monster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.loadoutlab.engine.Style.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The style's prayer raises Defence in the damage-taken model
 * (ISS-01M6415DFFW34P8QY468GA74KY, Andrew 2026-10-29: "Tabs + defence fix").
 * The offensive prayers run beside the overheads in game; the factors are
 * osrs-dps-calc's Prayer.ts (factorDefence, factorDefenceMagic) and the
 * wiki's Zeal page.
 */
class PrayerDefenceTest {
	private static void levels(Style style, String prayer, int defence, int magic) {
		assertArrayEquals(new int[]{defence, magic}, PrayerBonuses.defending(style, prayer, 99, 99), style + " " + prayer);
	}

	private static OptimizationRequest request(Monster mob, Style style) {
		return new OptimizationRequest(mob, style, PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null, 0, CandidateMode.ALL_STANDARD, false, false, OwnedItems.EMPTY, RequirementProfile.MAXED, 10);
	}

	@Test
	@DisplayName("each prayer raises Defence and Magic by its own factor, floored; no prayer leaves both alone")
	void factors() {
		levels(MELEE, "Zeal", 123, 99);
		levels(MELEE, "Piety", 123, 99);
		levels(MELEE, "Chivalry", 118, 99);
		levels(MELEE, "Ultimate Strength + Incredible Reflexes", 99, 99);
		levels(RANGED, "Rigour", 123, 99);
		levels(RANGED, "Deadeye", 103, 99);
		levels(RANGED, "Eagle Eye", 99, 99);
		levels(MAGIC, "Augury", 123, 123);
		levels(MAGIC, "Mystic Vigour", 103, 116);
		levels(MAGIC, "Mystic Might", 99, 113);
		levels(MAGIC, "Mystic Lore", 99, 108);
		levels(MAGIC, "Mystic Will", 99, 103);
		levels(MELEE, "", 99, 99);
		levels(MELEE, null, 99, 99);
		levels(MELEE, "Not a prayer", 99, 99);
		assertArrayEquals(new int[]{50, 1}, PrayerBonuses.defending(MAGIC, "Augury", 40, 1), "low levels floor too");
	}

	@Test
	@DisplayName("the defense-weighted beam builds its constants at the levels the request's prayer gives")
	void beamUsesThePrayer() {
		Monster moon = new DataService().load().searchMonsters("blood moon", 1).get(0);
		OptimizationRequest melee = request(moon, MELEE);
		assertEquals("Zeal", melee.prayers.nameFor(MELEE), "maxed with every unlock");
		assertEquals(123, IncomingDpsCalculator.Prepared.of(melee).defenceLevel, "Zeal: +25% Defence");
		assertEquals(99, IncomingDpsCalculator.Prepared.of(melee.withPrayers(PrayerBonuses.NONE)).defenceLevel, "prayerless");
		IncomingDpsCalculator.Prepared magic = IncomingDpsCalculator.Prepared.of(request(moon, MAGIC));
		assertEquals(123, magic.defenceLevel, "Augury: +25% Defence");
		assertEquals(IncomingDpsCalculator.magicEffectiveLevel(123, 123), magic.magicEffective, "and +25% Magic");
	}
}
