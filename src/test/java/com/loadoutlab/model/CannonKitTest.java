package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.TripSupplies;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The cannon supply (miko, Discord 2026-10-17): chosen in the grey chip's
 * Cannon submenu it rides the Supplies row as five cells - base, stand,
 * barrels, furnace, cannonballs - and the bank filter with them. A raid
 * selection never carries it (no cannons in a raid).
 */
class CannonKitTest
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

	private List<List<Integer>> cannonCells = new ArrayList<>();

	private List<Integer> cannonIds(boolean raid)
	{
		cannonCells.clear();
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state,
			(m, f2p, onTask, wild, lock, tradeables, risk, antifire, sunfire, dc, spec,
				boosts, prayers, budget, swaps, onDone) ->
			{
			},
			link);
		engine.setStoreOps(new TestStoreOps());
		engine.setOwnedCheck(id -> true);
		engine.setSupplyDefaults(() -> Map.of(TripSupplies.CANNON, "DWARF_MULTICANNON"));
		if (raid)
		{
			// A real raid select: the group data flags it, the engine sets
			// the param - the same path RaidSelectionTest proves.
			engine.execute("select", Map.of("query", "Tombs of Amascut"));
			List<MonsterStats> mobs = state.rosterMobs();
			List<Map<com.loadoutlab.engine.CombatStyle, com.loadoutlab.optimizer.OptimizerService.StyleResult>> empty =
				new ArrayList<>();
			for (int i = 0; i < mobs.size(); i++)
			{
				empty.add(Map.of());
			}
			engine.onRosterResults(mobs, empty);
		}
		else
		{
			MonsterStats mob = data.searchMonsters("abyssal demon", 1).get(0);
			engine.execute("select", Map.of("id", mob.getId()));
			engine.onResults(mob, Map.of());
		}
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
		List<Integer> ids = new ArrayList<>();
		for (Object node : (List<?>) entry.get("supplies"))
		{
			Map<?, ?> supply = (Map<?, ?>) node;
			if (TripSupplies.CANNON.equals(supply.get("category")))
			{
				ids.add(((Number) supply.get("itemId")).intValue());
				List<Integer> cell = new ArrayList<>();
				for (Object o : (List<?>) supply.get("ids"))
				{
					cell.add(((Number) o).intValue());
				}
				cannonCells.add(cell);
			}
		}
		return ids;
	}

	@Test
	@DisplayName("chosen, the cannon is five supply cells in part order")
	void cannonIsFiveCells()
	{
		assertEquals(List.of(6, 8, 10, 12, 2), cannonIds(false));
	}

	@Test
	@DisplayName("each cell lights up its variants in the filter: the ornamented part, granite balls")
	void cellsCarryVariants()
	{
		cannonIds(false);
		assertEquals(List.of(6, 26520), cannonCells.get(0), "base and the (or) base");
		assertEquals(List.of(2, 21728), cannonCells.get(4), "cannonballs and granite cannonballs");
	}

	@Test
	@DisplayName("a raid selection never carries the cannon")
	void raidsSkipTheCannon()
	{
		assertTrue(cannonIds(true).isEmpty());
	}
}
