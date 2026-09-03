package com.loadoutlab.render;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.model.CommandEngine;
import com.loadoutlab.model.CompanionLink;
import com.loadoutlab.model.PageState;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field report (Andrew, 2026-09-02, first run of the raid moods): "getting
 * regular result for tob, toa, cox, and zulrah". The loader picks its pool
 * by peeking the page when the COMPUTING status arrives - so the page the
 * surface can see at that moment must already carry the new selection
 * (and a roster's name), not the previous page.
 */
class RaidMoodRoutingTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	/** Records the page visible at the moment the computing status lands. */
	private static final class RecordingLink extends CompanionLink
	{
		Map<String, Object> last;
		Map<String, Object> atComputing;

		@Override
		public void publishPage(Map<String, Object> page)
		{
			last = page;
		}

		@Override
		public void publishStatus(boolean computing)
		{
			if (computing)
			{
				atComputing = last;
			}
		}
	}

	private static String keyFor(String query) throws Exception
	{
		PageState state = new PageState();
		RecordingLink link = new RecordingLink();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, onDone) ->
			{
			},
			link);
		engine.setRosterCompute((mobs, f2p, onTask, wild, lock, tradeables, risk, antifire,
			dc, spec, boosts, prayers, budget, swaps, onDone) ->
		{
		});
		// Commands arrive on the EDT in the client; the pending publish is
		// synchronous there, which is the order this test pins.
		javax.swing.SwingUtilities.invokeAndWait(() ->
			assertTrue(engine.execute("select", Map.of("query", query)), query));
		assertNotNull(link.atComputing, "a page is visible when computing starts: " + query);
		return RenderSurface.moodKey(link.atComputing);
	}

	@Test
	@DisplayName("the page seen when computing starts already routes the raid, Zulrah, sea or land")
	void routesFromTheFreshSelection() throws Exception
	{
		assertEquals("cox", keyFor("Chambers of Xeric"));
		assertEquals("tob", keyFor("Theatre of Blood (Entry)"));
		assertEquals("toa", keyFor("Tombs of Amascut"));
		assertEquals("zulrah", keyFor("Zulrah"));
		assertEquals("sea", keyFor("Hammerhead shark"));
		assertNull(keyFor("General Graardor"));
	}
}
