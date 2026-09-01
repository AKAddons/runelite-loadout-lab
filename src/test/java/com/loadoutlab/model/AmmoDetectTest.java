package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Detect best in bank" for cannonballs (Andrew 2026-08-31), under the
 * 0.4.1 ownership rule: the resolved ball is the best BANKED tier every
 * carried cannon can fire, and a bank with no cannonballs prices every
 * cannon at zero with the reason - never an assumed ball.
 */
class AmmoDetectTest
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

	private static Map<String, Object> shipWith(Set<Integer> bank,
		Map<String, Object> shipParams)
	{
		PageState state = new PageState();
		for (Map.Entry<String, Object> e : shipParams.entrySet())
		{
			state.setParam(e.getKey(), e.getValue());
		}
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) ->
			{
			},
			link);
		engine.setRangedLevel(99);
		engine.setOwnedCheck(bank::contains);
		MonsterStats shark = data.searchMonsters("hammerhead shark", 1).get(0);
		engine.execute("select", Map.of("id", shark.getId()));
		engine.onResults(shark, Map.of());
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
		Map<?, ?> entry = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
		@SuppressWarnings("unchecked")
		Map<String, Object> ship = (Map<String, Object>) entry.get("ship");
		return ship;
	}

	@Test
	@DisplayName("detect resolves to the best banked ball every cannon can fire")
	void detectResolvesFromTheBank()
	{
		// Bank holds mithril (31910) and rune (31914) balls; a dragon +
		// mithril pair can share at most mithril - rune is banked but the
		// mithril cannon cannot fire it.
		Map<String, Object> ship = shipWith(Set.of(31910, 31914),
			Map.of("cannonCount", 2, "cannon1Material", "dragon",
				"cannon2Material", "mithril"));
		assertEquals("mithril", ship.get("ammo"));
		assertEquals(Boolean.TRUE, ship.get("ammoDetected"));
		assertNull(ship.get("ammoBlocked"));
	}

	@Test
	@DisplayName("a dry bank fires nothing - the reason rides the node")
	void dryBankBlocks()
	{
		Map<String, Object> ship = shipWith(Set.of(),
			Map.of("cannonCount", 1, "cannon1Material", "dragon",
				"cannon1Operator", "you"));
		assertEquals("No cannonballs banked (up to dragon)", ship.get("ammoBlocked"));
		Map<?, ?> c1 = (Map<?, ?>) ((List<?>) ship.get("cannons")).get(0);
		assertEquals(0, c1.get("maxHit"));
		assertEquals(0.0, (Double) ship.get("dps"), 1e-9);
	}

	@Test
	@DisplayName("an explicit tier pick bypasses detection entirely")
	void explicitPickBypasses()
	{
		Map<String, Object> ship = shipWith(Set.of(),
			Map.of("cannonCount", 1, "cannon1Material", "dragon",
				"cannonAmmo", "dragon", "cannon1Operator", "you"));
		assertEquals("dragon", ship.get("ammo"));
		assertEquals(Boolean.FALSE, ship.get("ammoDetected"));
		Map<?, ?> c1 = (Map<?, ?>) ((List<?>) ship.get("cannons")).get(0);
		assertEquals(57, c1.get("maxHit"), "the explicit pick prices even with a dry bank");
	}

	@Test
	@DisplayName("ship params persist per character through the scoped store")
	void shipParamsPersist()
	{
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) ->
			{
			},
			link);
		java.util.Map<String, String> saved = new java.util.HashMap<>();
		engine.setStoreOps(new TestStoreOps()
		{
			@Override
			public void setSupplyDefault(String category, String choice)
			{
				saved.put(category, choice);
			}
		});
		engine.execute("set-param", Map.of("param", "cannon1Material", "value", "dragon"));
		engine.execute("set-param", Map.of("param", "cannon1Operator", "value", "you"));
		assertEquals("dragon", saved.get("ship.cannon1Material"));
		assertEquals("you", saved.get("ship.cannon1Operator"));
		// The SETTLED value persists, clamps applied: crew2 on a dragon
		// cannon stores as the auto-raised crew4.
		engine.execute("set-param", Map.of("param", "cannon1Operator", "value", "crew2"));
		assertEquals("crew4", saved.get("ship.cannon1Operator"));
	}

	@Test
	@DisplayName("ship damage taken rides the node per keel - even with no cannons")
	void shipIncomingPerKeel()
	{
		// Hammerhead: keel row 7,6,5,4,3,1,0,0; speed 4 ticks (2.4s).
		Map<String, Object> ship = shipWith(Set.of(),
			Map.of("cannonCount", 0, "shipKeel", "regular"));
		assertNotNull(ship, "a sea lens carries the ship node for DTPS alone");
		Map<?, ?> in = (Map<?, ?>) ship.get("incoming");
		assertEquals("regular", in.get("keel"));
		assertEquals(7, in.get("maxHit"));
		assertEquals(7 / 2.0 / 2.4, (Double) in.get("dtps"), 1e-9);

		Map<?, ?> rosewood = (Map<?, ?>) shipWith(Set.of(),
			Map.of("cannonCount", 0, "shipKeel", "rosewood")).get("incoming");
		assertEquals(0, rosewood.get("maxHit"),
			"a rosewood keel cannot be hit by a hammerhead (wiki table)");
		assertEquals(0.0, (Double) rosewood.get("dtps"), 1e-9);
	}

	@Test
	@DisplayName("an explicit pick downranks at resolution and springs back")
	void explicitPickDownranksAtResolution()
	{
		// Dragon pick on a rune cannon resolves to rune...
		Map<String, Object> ship = shipWith(Set.of(),
			Map.of("cannonCount", 1, "cannon1Material", "rune",
				"cannonAmmo", "dragon", "cannon1Operator", "you"));
		assertEquals("rune", ship.get("ammo"));
		// ...and the pick itself never mutated: back on a dragon cannon the
		// dragon ball returns.
		ship = shipWith(Set.of(),
			Map.of("cannonCount", 1, "cannon1Material", "dragon",
				"cannonAmmo", "dragon", "cannon1Operator", "you"));
		assertEquals("dragon", ship.get("ammo"));
	}

	@Test
	@DisplayName("thralls cannot be cast on a boat - they add nothing at sea")
	void thrallsVetoedAtSea()
	{
		PageState state = new PageState();
		state.setParam("thralls", true);
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) ->
			{
			},
			link);
		engine.setMagicLevel(99);
		MonsterStats shark = data.searchMonsters("hammerhead shark", 1).get(0);
		engine.execute("select", Map.of("id", shark.getId()));
		engine.onResults(shark, Map.of());
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
		Map<?, ?> entry = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
		assertNull(entry.get("thralls"),
			"thrall resurrections cannot be cast on a boat (confirmed 2026-08-31)");

		// The land path keeps its fold.
		MonsterStats graardor = data.searchMonsters("general graardor", 1).get(0);
		engine.execute("select", Map.of("id", graardor.getId()));
		engine.onResults(graardor, Map.of());
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
		entry = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
		assertNotNull(entry.get("thralls"), "the land fold must survive the veto");
	}

	@Test
	@DisplayName("repair kits recommend only when the mob can damage YOUR keel")
	void repairKitsFollowTheKeel()
	{
		java.util.function.BiFunction<String, String, Boolean> kitShown = (mobName, keel) ->
		{
			PageState state = new PageState();
			state.setParam("shipKeel", keel);
			CaptureLink link = new CaptureLink();
			CommandEngine engine = new CommandEngine(data, state,
				(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
					boosts, prayers, budget, swaps, raid, onDone) ->
				{
				},
				link);
			engine.setStoreOps(new TestStoreOps());
			engine.setOwnedCheck(id -> id == 31982); // a rosewood kit banked
			engine.setSupplyDefaults(() -> Map.of(
				com.loadoutlab.data.TripSupplies.SHIP_REPAIR_KIT, "DETECT_BEST"));
			MonsterStats mob = data.searchMonsters(mobName, 1).get(0);
			engine.execute("select", Map.of("id", mob.getId()));
			engine.onResults(mob, Map.of());
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
			Map<?, ?> entry = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
			for (Object node : (List<?>) entry.get("supplies"))
			{
				if ("shipRepairKit".equals(((Map<?, ?>) node).get("category")))
				{
					return true;
				}
			}
			return false;
		};
		// A hammerhead dents a regular keel (max 7): kits recommended.
		assertTrue(kitShown.apply("hammerhead shark", "regular"));
		// The same shark cannot touch a dragon keel (max 0): no kits.
		assertFalse(kitShown.apply("hammerhead shark", "dragon"),
			"a mob doing 0 damage to your keel needs no repair kits");
		// Land mobs never see the category.
		assertFalse(kitShown.apply("general graardor", "regular"));
	}
}
