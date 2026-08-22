package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Field asks 2026-08-22: removing the last mob returns to the start
 * state, and a fresh search opens on Yours. */
class SelectionResetTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static CommandEngine engine(PageState state, CaptureLink link)
	{
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) ->
			{
			},
			link);
		engine.setRosterCompute((mobs, f2p, onTask, wild, lock, tradeables, risk,
			antifire, dc, spec, boosts, prayers, budget, swaps, raid, onDone) ->
			{
			});
		return engine;
	}

	@Test
	@DisplayName("removing the only mob returns to the start state")
	void removingTheLastMobClears()
	{
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = engine(state, link);
		assertTrue(engine.execute("select", Map.of("query", "abyssal demon")));
		assertNotNull(state.mob(), "a single mob is selected");

		assertTrue(engine.execute("remove-mob", Map.of("index", 0)),
			"the X works on a single-mob view");
		flushEdt();
		assertNull(state.mob(), "nothing is selected any more");
		assertFalse(state.hasSelection());
		assertTrue(((List<?>) link.published.get("entries")).isEmpty(),
			"the page is the empty start state");
	}

	@Test
	@DisplayName("a fresh search opens on Yours, not BiS")
	void freshSearchOpensOnYours()
	{
		PageState state = new PageState();
		CommandEngine engine = engine(state, new CaptureLink());
		assertTrue(engine.execute("select", Map.of("query", "abyssal demon")));
		assertTrue(engine.execute("set-param", Map.of("param", "viewingBis", "value", true)));
		assertEquals(Boolean.TRUE, state.paramsNode().get("viewingBis"));

		assertTrue(engine.execute("select", Map.of("query", "black demon")));
		assertEquals(Boolean.FALSE, state.paramsNode().get("viewingBis"),
			"the next search comes back to Yours");
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

	private static final class CaptureLink extends CompanionLink
	{
		Map<String, Object> published;

		@Override
		public void publishPage(Map<String, Object> page)
		{
			published = page;
		}
	}
}
