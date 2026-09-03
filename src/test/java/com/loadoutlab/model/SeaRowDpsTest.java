package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.NavalCombat;
import com.loadoutlab.engine.CombatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Andrew, 2026-09-03: "the line item dps output should show the player +
 * cannon output for sea monsters" - every sea mob in a roster carries its
 * own cannon dps, not just the lensed one. */
class SeaRowDpsTest
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

	private static List<MonsterStats> twoSeaMobs()
	{
		List<MonsterStats> out = new ArrayList<>();
		for (MonsterStats m : data.getMonsters())
		{
			if (NavalCombat.isNaval(m.getName())
				&& (out.isEmpty() || out.get(0).getDefence() != m.getDefence()))
			{
				out.add(m);
			}
			if (out.size() == 2)
			{
				break;
			}
		}
		assertEquals(2, out.size(), "two sea mobs with different defence");
		return out;
	}

	private static Map<String, Object> entryFor(List<MonsterStats> mobs, String operator)
	{
		PageState state = new PageState();
		state.setParam("cannonCount", 1);
		state.setParam("cannon1Material", "dragon");
		state.setParam("cannonAmmo", "dragon");
		state.setParam("cannon1Operator", operator);
		state.setParam("lensIndex", 0);
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, onDone) ->
			{
			},
			link);
		engine.setRangedLevel(99);
		engine.setSailingLevel(99);
		List<Map<CombatStyle, com.loadoutlab.optimizer.OptimizerService.StyleResult>> results = new ArrayList<>();
		for (int i = 0; i < mobs.size(); i++)
		{
			results.add(Map.of());
		}
		engine.onRosterResults(mobs, results);
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
		@SuppressWarnings("unchecked")
		Map<String, Object> entry = (Map<String, Object>) ((List<?>) link.published.get("entries")).get(0);
		return entry;
	}

	private static double shipDps(Map<String, Object> entry, int i)
	{
		Map<?, ?> mob = (Map<?, ?>) ((List<?>) entry.get("mobs")).get(i);
		Object v = mob.get("shipDps");
		return v instanceof Number ? ((Number) v).doubleValue() : Double.NaN;
	}

	@Test
	@DisplayName("every sea mob in the roster carries its own cannon dps; the lensed one matches the ship total")
	void eachSeaMobPriced()
	{
		List<MonsterStats> mobs = twoSeaMobs();
		Map<String, Object> entry = entryFor(mobs, "crew1");
		double first = shipDps(entry, 0);
		double second = shipDps(entry, 1);
		assertTrue(first > 0, "lensed mob: " + first);
		assertTrue(second > 0, "un-lensed mob: " + second);
		assertNotEquals(first, second, "different defence, different accuracy");
		Map<?, ?> ship = (Map<?, ?>) entry.get("ship");
		assertEquals(((Number) ship.get("dps")).doubleValue(), first, 1e-9, "the lensed mob is the ship total");
	}

	@Test
	@DisplayName("a land mob carries no cannon dps, and a manned cannon zeroes the rows")
	void landAndManned()
	{
		List<MonsterStats> sea = twoSeaMobs();
		MonsterStats land = data.searchMonsters("general graardor", 1).get(0);
		Map<String, Object> mixed = entryFor(List.of(sea.get(0), land), "crew1");
		assertTrue(shipDps(mixed, 0) > 0);
		assertEquals(0.0, shipDps(mixed, 1), "a land mob takes no cannon fire");
		Map<String, Object> manned = entryFor(sea, "you");
		assertEquals(0.0, shipDps(manned, 0), "manning a cannon: the gear is not attacking, the row keeps the set");
	}
}
