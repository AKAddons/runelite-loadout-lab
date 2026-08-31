package com.loadoutlab.engine;

import com.loadoutlab.data.NavalCombat;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ship-cannon formulas, pinned against hand-computed values from the wiki's
 * documented formula (Boat combat / Cannons, 2026-08-31):
 * floor((level * (cannonStr + ballStr + wornStr + 64) + 320) / 640).
 */
class ShipCannonTest
{
	@Test
	@DisplayName("player max hit matches the wiki formula at hand-computed points")
	void playerMaxHitPins()
	{
		// 99 ranged, dragon cannon (32) + dragon ball (270), nothing worn:
		// (99 * 366 + 320) / 640 = 57.11 -> 57.
		assertEquals(57, ShipCannon.playerMaxHit(99, 32, 270, 0, false));
		// Perildance bitter: +1 flat.
		assertEquals(58, ShipCannon.playerMaxHit(99, 32, 270, 0, true));
		// Worn ranged strength joins the strength term: +30 worn ->
		// (99 * 396 + 320) / 640 = 61.75 -> 61.
		assertEquals(61, ShipCannon.playerMaxHit(99, 32, 270, 30, false));
		// A fresh account on a bronze/bronze setup cannot hit:
		// (1 * 115 + 320) / 640 = 0.68 -> 0.
		assertEquals(0, ShipCannon.playerMaxHit(1, 8, 43, 0, false));
		// Mid levels: 50 ranged, steel/steel, +20 worn ->
		// (50 * 170 + 320) / 640 = 13.78 -> 13.
		assertEquals(13, ShipCannon.playerMaxHit(50, 12, 74, 20, false));
	}

	@Test
	@DisplayName("crew scaling is the documented (priv + 13) / 20 - and flagged stale")
	void crewScalingPins()
	{
		assertEquals(48, ShipCannon.crewMaxHit(57, 4)); // 57 * 17/20 = 48.45
		assertEquals(39, ShipCannon.crewMaxHit(57, 1)); // 57 * 14/20 = 39.9
		assertEquals(0, ShipCannon.crewMaxHit(0, 4));
		// The wiki flags this formula out-of-date; the data carries the flag
		// so the UI marks crew numbers as estimates. If the flag is ever
		// cleared, the corrected formula must land WITH that change.
		assertTrue(NavalCombat.crewFormulaStale(),
			"crewFormulaStale cleared - did the corrected formula land here?");
	}

	@Test
	@DisplayName("cannon DPS averages the uniform roll over the 7-tick cycle")
	void dpsShape()
	{
		// Max 57 at guaranteed hits: 28.5 damage per 4.2s cycle.
		assertEquals(57 / 2.0 / 4.2, ShipCannon.dps(57, 1.0), 1e-9);
		// Halved hit chance halves it; zero chance is zero.
		assertEquals(57 / 4.0 / 4.2, ShipCannon.dps(57, 0.5), 1e-9);
		assertEquals(0.0, ShipCannon.dps(57, 0.0), 1e-9);
	}

	@Test
	@DisplayName("a cannon fires balls at or below its tier - granite rides above mithril")
	void ballTierClamps()
	{
		assertTrue(NavalCombat.canFire("dragon", "dragon"));
		assertTrue(NavalCombat.canFire("dragon", "bronze"));
		assertFalse(NavalCombat.canFire("bronze", "iron"));
		// Granite sits between mithril and adamant in the firing order: a
		// mithril cannon cannot fire it, an adamant cannon can.
		assertFalse(NavalCombat.canFire("mithril", "granite"));
		assertTrue(NavalCombat.canFire("adamant", "granite"));
	}

	@Test
	@DisplayName("shared ammo caps at the LOWER cannon's tier (REQ-SC-2)")
	void sharedAmmoCap()
	{
		assertEquals("mithril", NavalCombat.bestSharedBall(List.of("dragon", "mithril")));
		assertEquals("dragon", NavalCombat.bestSharedBall(List.of("dragon", "dragon")));
		assertEquals("bronze", NavalCombat.bestSharedBall(List.of("bronze")));
		assertNull(NavalCombat.bestSharedBall(List.of()));
	}

	@Test
	@DisplayName("the data tables carry the wiki's current numbers")
	void dataPins()
	{
		// Dragon ball at the post-2026-03-18 +270, never the launch +174.
		assertEquals(270, NavalCombat.ball("dragon").strength);
		assertEquals(31916, NavalCombat.ball("dragon").itemId);
		// Steel ball is the classic Cannonball, item 2.
		assertEquals(2, NavalCombat.ball("steel").itemId);
		// Rune and dragon cannons demand Privateering 4 of their crew.
		assertEquals(4, NavalCombat.cannon("rune").privateering);
		assertEquals(4, NavalCombat.cannon("dragon").privateering);
		// Seven cannons, eight balls, uniform 7-tick rate.
		assertEquals(7, NavalCombat.cannons().size());
		assertEquals(8, NavalCombat.balls().size());
		assertEquals(7, NavalCombat.ATTACK_TICKS);
	}
}
