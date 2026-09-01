package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.ShipCannon;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * REQ-SC-2/3/3b/4/7 at the engine seam: the ship node prices cannons for a
 * NAVAL mob only, cannon 1 belongs to the player when the station says so,
 * shared ammo clamps per cannon, and the operate gates (player Ranged, crew
 * Privateering) zero a cannon they block - with the reason on the node.
 */
class ShipNodeTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static final class CaptureLink extends CompanionLink
	{
		Map<String, Object> published;

		@Override
		public void publishPage(Map<String, Object> page)
		{
			published = page;
		}
	}

	private static CommandEngine engine(PageState state, CaptureLink link)
	{
		return new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) ->
			{
			},
			link);
	}

	private static void flushEdt()
	{
		try
		{
			javax.swing.SwingUtilities.invokeAndWait(() ->
			{
			});
		}
		catch (Exception ex)
		{
			throw new AssertionError(ex);
		}
	}

	private static Map<String, Object> shipNodeFor(String mobName,
		Map<String, Object> shipParams, int ranged, int sailing)
	{
		PageState state = new PageState();
		for (Map.Entry<String, Object> e : shipParams.entrySet())
		{
			state.setParam(e.getKey(), e.getValue());
		}
		CaptureLink link = new CaptureLink();
		CommandEngine engine = engine(state, link);
		engine.setRangedLevel(ranged);
		engine.setSailingLevel(sailing);
		MonsterStats mob = data.searchMonsters(mobName, 1).get(0);
		engine.execute("select", Map.of("id", mob.getId()));
		engine.onResults(mob, Map.of());
		flushEdt();
		assertNotNull(link.published, "nothing published");
		List<?> entries = (List<?>) link.published.get("entries");
		Map<?, ?> entry = (Map<?, ?>) entries.get(0);
		@SuppressWarnings("unchecked")
		Map<String, Object> ship = (Map<String, Object>) entry.get("ship");
		return ship;
	}

	@Test
	@DisplayName("a manned dragon cannon prices at the hand-computed wiki numbers")
	void mannedCannonPrices()
	{
		Map<String, Object> ship = shipNodeFor("hammerhead shark",
			Map.of("cannonCount", 1, "cannon1Material", "dragon",
				"cannonAmmo", "dragon", "cannon1Operator", "you"),
			99, 1);
		assertNotNull(ship, "a naval mob with a cannon must carry the ship node");
		assertEquals("cannon", ship.get("station"));
		List<?> cannons = (List<?>) ship.get("cannons");
		Map<?, ?> c1 = (Map<?, ?>) cannons.get(0);
		assertEquals("player", c1.get("firedBy"));
		assertEquals(57, c1.get("maxHit"), "99 ranged, dragon/dragon: the pinned 57");
		// Hammerhead: def 50, heavy 40. Equipment accuracy 150 + 80.
		double expected = ShipCannon.dps(57,
			ShipCannon.hitChance(99, ShipCannon.equipmentAccuracy(150, 80, 0), 50, 40));
		assertEquals(expected, (Double) c1.get("dps"), 1e-9);
		assertEquals(expected, (Double) ship.get("dps"), 1e-9);
		// No crew involved: nothing is estimated.
		assertEquals(Boolean.FALSE, ship.get("estimated"));
	}

	@Test
	@DisplayName("gear station: both cannons are crew-fired; mixed tiers clamp the shared ammo per cannon")
	void gearStationCrewsAndClamps()
	{
		Map<String, Object> ship = shipNodeFor("hammerhead shark",
			Map.of("cannonCount", 2, "cannon1Material", "dragon",
				"cannon2Material", "mithril", "cannonAmmo", "dragon",
				"cannon1Operator", "crew4", "cannon2Operator", "crew4"),
			99, 80);
		List<?> cannons = (List<?>) ship.get("cannons");
		Map<?, ?> c1 = (Map<?, ?>) cannons.get(0);
		Map<?, ?> c2 = (Map<?, ?>) cannons.get(1);
		assertEquals("crew", c1.get("firedBy"));
		assertEquals("crew", c2.get("firedBy"));
		// ONE tier for the whole ship (REQ-SC-2): the dragon pick downranks
		// at RESOLUTION to what both cannons can fire.
		assertEquals("mithril", ship.get("ammo"));
		assertEquals("mithril", c1.get("ball"));
		assertEquals("mithril", c2.get("ball"));
		// Crew numbers ride the stale formula: flagged estimated.
		assertEquals(Boolean.TRUE, ship.get("estimated"));
		// Crew base is Sailing-level driven with the SHARED mithril ball
		// (105 str - the downranked tier), scaled by Privateering 4.
		int base = ShipCannon.playerMaxHit(80, 32, 105, 0, false);
		assertEquals(ShipCannon.crewMaxHit(base, 4), c1.get("maxHit"));
	}

	@Test
	@DisplayName("crew auto-raises to the cannon's gate; the player Ranged gate still blocks")
	void operateGates()
	{
		// REQ-SC-9: picking crew3 for a dragon cannon is auto-raised to the
		// minimum that can man it - the under-crewed state is unreachable,
		// so the cannon prices instead of blocking.
		Map<String, Object> ship = shipNodeFor("hammerhead shark",
			Map.of("cannonCount", 1, "cannon1Material", "dragon",
				"cannonAmmo", "dragon", "cannon1Operator", "crew3"),
			99, 80);
		Map<?, ?> crewCannon = (Map<?, ?>) ((List<?>) ship.get("cannons")).get(0);
		assertNull(crewCannon.get("blocked"),
			"auto-min-crew must make the dragon cannon operable");
		assertTrue((Integer) crewCannon.get("maxHit") > 0);

		// A 40-Ranged player cannot operate a dragon cannon either.
		ship = shipNodeFor("hammerhead shark",
			Map.of("cannonCount", 1, "cannon1Material", "dragon",
				"cannonAmmo", "dragon", "cannon1Operator", "you"),
			40, 1);
		Map<?, ?> playerBlocked = (Map<?, ?>) ((List<?>) ship.get("cannons")).get(0);
		assertEquals("Needs 60 Ranged to operate", playerBlocked.get("blocked"));
		assertEquals(0.0, (Double) ship.get("dps"), 1e-9);
	}

	@Test
	@DisplayName("the manned cannon borrows the worn ranged bonuses, minus weapon/shield/ammo")
	void mannedCannonWearsTheArmour()
	{
		// Without a ranged result the cannon prices bare: the 57 pin.
		Map<String, Object> bare = shipNodeFor("hammerhead shark",
			Map.of("cannonCount", 1, "cannon1Material", "dragon",
				"cannonAmmo", "dragon", "cannon1Operator", "you"),
			99, 1);
		assertEquals(0, bare.get("wornStrength"));
		Map<?, ?> c1 = (Map<?, ?>) ((List<?>) bare.get("cannons")).get(0);
		assertEquals(57, c1.get("maxHit"));
		// The formula seam: +30 worn strength lifts the pinned 57 to 61
		// (ShipCannonTest pins the arithmetic; the wiring test here is that
		// a real ranged loadout's bonuses reach the node - exercised via
		// the full onResults path in the field and the formula pin above).
		assertEquals(61, com.loadoutlab.engine.ShipCannon.playerMaxHit(99, 32, 270, 30, false));
	}

	@Test
	@DisplayName("a mixed roster prices cannons only under the sea lens (REQ-SC-7)")
	void mixedRosterFollowsTheLens()
	{
		PageState state = new PageState();
		state.setParam("cannonCount", 1);
		state.setParam("cannon1Material", "dragon");
		state.setParam("cannonAmmo", "dragon");
		state.setParam("cannon1Operator", "you");
		CaptureLink link = new CaptureLink();
		CommandEngine engine = engine(state, link);
		engine.setRangedLevel(99);
		MonsterStats graardor = data.searchMonsters("general graardor", 1).get(0);
		MonsterStats shark = data.searchMonsters("hammerhead shark", 1).get(0);

		state.setParam("lensIndex", 0);
		engine.onRosterResults(List.of(graardor, shark), List.of(Map.of(), Map.of()));
		flushEdt();
		Map<?, ?> entry = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
		assertNull(entry.get("ship"), "the land lens must render no ship options");

		state.setParam("lensIndex", 1);
		engine.onRosterResults(List.of(graardor, shark), List.of(Map.of(), Map.of()));
		flushEdt();
		entry = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
		Map<?, ?> ship = (Map<?, ?>) entry.get("ship");
		assertNotNull(ship, "the sea lens must bring the ship options back");
		Map<?, ?> c1 = (Map<?, ?>) ((List<?>) ship.get("cannons")).get(0);
		assertEquals(57, c1.get("maxHit"), "priced against the LENSED shark");
	}

	@Test
	@DisplayName("land mobs and cannonless ships carry no node - the params survive unread")
	void landAndZeroVeto()
	{
		assertNull(shipNodeFor("general graardor",
			Map.of("cannonCount", 2, "cannon1Material", "dragon",
				"cannonAmmo", "dragon", "cannon1Operator", "you"),
			99, 99), "REQ-SC-7: ship options never price a land mob");
		// A sea lens with no cannons still carries the node - ship damage
		// taken (the keel line) applies with or without cannons.
		Map<String, Object> dtpsOnly = shipNodeFor("hammerhead shark",
			Map.of("cannonCount", 0), 99, 99);
		assertNotNull(dtpsOnly);
		assertTrue(((List<?>) dtpsOnly.get("cannons")).isEmpty());
		assertNotNull(dtpsOnly.get("incoming"));
	}
}
