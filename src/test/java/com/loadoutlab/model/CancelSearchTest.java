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

/** Andrew, 2026-09-04: "a cancel button while we are in the computing
 * phase ... bring it immediately back to the neutral state". */
class CancelSearchTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static final class CaptureLink extends CompanionLink
	{
		Map<String, Object> page;
		Boolean status;

		@Override
		public void publishPage(Map<String, Object> page)
		{
			this.page = page;
		}

		@Override
		public void publishStatus(boolean computing)
		{
			status = computing;
		}
	}

	private static void flush()
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

	@Test
	@DisplayName("cancel mid-compute returns to the start state, and a result that lands afterwards is dropped")
	void cancelDropsTheLateResult()
	{
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, onDone) ->
			{
			},
			link);
		engine.setStoreOps(new TestStoreOps());
		MonsterStats cerberus = data.searchMonsters("cerberus", 1).get(0);
		assertTrue(engine.execute("select", Map.of("id", cerberus.getId())));
		flush();
		assertNotNull(state.mob(), "a search is in flight");

		assertTrue(engine.execute("cancel", Map.of()));
		flush();
		assertNull(state.mob(), "back to the start state");
		assertEquals(Boolean.FALSE, link.status, "the computing notice is gone");
		assertTrue(((List<?>) link.page.get("entries")).isEmpty(), "the idle page");

		engine.onResults(cerberus, Map.of());
		flush();
		assertTrue(((List<?>) link.page.get("entries")).isEmpty(), "the late result never lands");

		assertTrue(engine.execute("select", Map.of("id", cerberus.getId())));
		engine.onResults(cerberus, Map.of());
		flush();
		assertFalse(((List<?>) link.page.get("entries")).isEmpty(), "a fresh search takes results again");
	}
}
