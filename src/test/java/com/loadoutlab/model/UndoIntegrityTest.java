package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Undo integrity, from the pre-release adversarial pass 2026-08-22.
 * The snapshot restore carried only the PAGE, so the results cache
 * kept the pre-undo answer and the next republish drew the wrong
 * mob; and a refused revert consumed history depth the snapshot
 * deques never followed, putting every later restore out of phase.
 */
class UndoIntegrityTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
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

	private static String firstMobLabel(Map<String, Object> page)
	{
		List<?> entries = (List<?>) page.get("entries");
		if (entries == null || entries.isEmpty())
		{
			return null;
		}
		List<?> mobs = (List<?>) ((Map<?, ?>) entries.get(0)).get("mobs");
		return mobs == null || mobs.isEmpty()
			? null : (String) ((Map<?, ?>) mobs.get(0)).get("label");
	}

	@Test
	@DisplayName("after undo, a republish draws the restored mob - not the undone one")
	void undoRestoresTheResultsWithThePage()
	{
		CaptureLink link = new CaptureLink();
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) ->
			{
			},
			link);

		assertTrue(engine.execute("select", Map.of("query", "abyssal demon")));
		engine.onResults(data.searchMonsters("abyssal demon", 1).get(0), Map.of());
		flushEdt();
		assertTrue(engine.execute("select", Map.of("query", "zulrah")));
		engine.onResults(data.searchMonsters("zulrah", 1).get(0), Map.of());
		flushEdt();

		assertTrue(engine.execute("undo", Map.of()));
		flushEdt();
		assertTrue(firstMobLabel(link.published).toLowerCase().contains("abyssal"),
			"undo restored the demon page");

		// A pure VIEW param - it republishes from the results cache.
		assertTrue(engine.execute("set-param", Map.of("param", "thralls", "value", true)));
		flushEdt();
		assertTrue(firstMobLabel(link.published).toLowerCase().contains("abyssal"),
			"the republish must draw the RESTORED mob, not the undone one: "
				+ firstMobLabel(link.published));
	}

	@Test
	@DisplayName("a refused revert keeps the snapshot stacks in phase")
	void refusedRevertKeepsPhase()
	{
		CaptureLink link = new CaptureLink();
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) ->
			{
			},
			link);
		engine.setStoreOps(new TestStoreOps(1));

		assertTrue(engine.execute("select", Map.of("query", "abyssal demon")));
		engine.onResults(data.searchMonsters("abyssal demon", 1).get(0), Map.of());
		flushEdt();

		// Accepted once, refused on the revert - CommandHistory drops it.
		assertTrue(engine.execute("toggle-exclusion", Map.of("itemId", 4151)));
		assertFalse(engine.execute("undo", Map.of()), "the refused revert reports failure");

		// The next undo must reach the SELECT, clearing the selection.
		assertTrue(engine.execute("undo", Map.of()));
		flushEdt();
		assertNull(state.mob(), "the select was undone");
		assertNull(firstMobLabel(link.published),
			"the page must be the pre-select one, not an orphaned snapshot");
	}

	@Test
	@DisplayName("undoing a select also undoes the params it seeded")
	void undoRestoresSeededParams()
	{
		CaptureLink link = new CaptureLink();
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) ->
			{
			},
			link);

		assertTrue(engine.execute("select", Map.of("query", "abyssal demon")));
		assertEquals(Boolean.FALSE, state.paramsNode().get("inWilderness"));

		// A wilderness-exclusive forces the param on at selection.
		assertTrue(engine.execute("select", Map.of("query", "callisto")));
		assertEquals(Boolean.TRUE, state.paramsNode().get("inWilderness"),
			"the exclusive forced the wilderness param");

		assertTrue(engine.execute("undo", Map.of()));
		flushEdt();
		assertEquals(Boolean.FALSE, state.paramsNode().get("inWilderness"),
			"undo must take the seeded params back with the selection");
	}


	private static final class CaptureLink extends CompanionLink
	{
		Map<String, Object> published;
		private Map<String, Object> last;

		@Override
		public void publishPage(Map<String, Object> page)
		{
			published = page;
			last = page;
		}

		@Override
		public Map<String, Object> lastPage()
		{
			return last;
		}
	}
}
