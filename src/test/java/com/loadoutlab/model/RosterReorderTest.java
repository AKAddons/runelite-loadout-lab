package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Andrew 2026-09-02: "the ability to reorder monsters in the list for a
 * group search". Order is presentation: the roster list reorders, the
 * lens follows the moved row, undo restores both. */
class RosterReorderTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static final class CaptureLink extends CompanionLink
	{
		@Override
		public void publishPage(Map<String, Object> page)
		{
		}
	}

	private static List<String> labels(PageState state)
	{
		List<String> out = new ArrayList<>();
		for (MonsterStats mob : state.rosterMobs())
		{
			out.add(mob.label());
		}
		return out;
	}

	@Test
	@DisplayName("a roster row moves up or down, the lens follows it, and undo puts it back")
	void moveUpAndDown()
	{
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, onDone) ->
			{
			},
			new CaptureLink());
		engine.setStoreOps(new TestStoreOps());
		// The full group name: aliases route through monster search, not the group lookup.
		assertTrue(engine.execute("select", Map.of("query", "Tombs of Amascut")));
		assertNotNull(state.rosterMobs(), "a group select sets a roster");
		List<String> before = labels(state);
		assertTrue(before.size() > 3, "a roster");
		assertTrue(engine.execute("set-param", Map.of("param", "lensIndex", "value", 1)));

		// Row 1 moves up: it swaps with row 0 and the lens follows it.
		assertTrue(engine.execute("move-mob", Map.of("index", 1, "delta", -1)));
		List<String> moved = labels(state);
		assertEquals(before.get(1), moved.get(0));
		assertEquals(before.get(0), moved.get(1));
		assertEquals(0, ((Number) state.paramsNode().get("lensIndex")).intValue());
		assertEquals(before.subList(2, before.size()), moved.subList(2, moved.size()),
			"only the two rows swap");

		// Out of range is a no-op, not a crash.
		assertFalse(engine.execute("move-mob", Map.of("index", 0, "delta", -1)));
		assertFalse(engine.execute("move-mob", Map.of("index", before.size() - 1, "delta", 1)));

		// Undo restores the order and the lens.
		assertTrue(engine.execute("undo", Map.of()));
		assertEquals(before, labels(state));
		assertEquals(1, ((Number) state.paramsNode().get("lensIndex")).intValue());
	}
}
