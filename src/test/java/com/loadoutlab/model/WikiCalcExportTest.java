package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field dispute 2026-09-02 (Obelisk melee 3.88 here, 6.468 in the wiki
 * calc): the official harness proved the ENGINE byte-exact at raid level
 * 0 and 300; the LINK was handing the calculator the unscaled mob, so it
 * opened at raid level 0 while the card sat at 300 - the same shape as
 * the Warden dispute. The exported mob must carry the card's invocation.
 */
class WikiCalcExportTest
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

	private static MonsterStats exported(String query, int invocation) throws Exception
	{
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, onDone) ->
			{
			},
			new CaptureLink());
		engine.setStoreOps(new TestStoreOps());
		assertTrue(engine.execute("select", Map.of("query", query)));
		MonsterStats mob = state.mob();
		assertNotNull(mob, query + " selected");
		state.setParam("toaInvocation", invocation);
		engine.onResults(mob, WildernessReportTest.styles(data, mob));
		javax.swing.SwingUtilities.invokeAndWait(() ->
		{
		});
		AtomicReference<MonsterStats> got = new AtomicReference<>();
		engine.setWikiCalcOpener((m, shown, dartId, assumes, onTask, inWilderness) -> got.set(m));
		// The helper computes with an empty bank: the game-best side is the one with a set.
		assertTrue(engine.execute("wiki-calc", Map.of("bis", true)), "the link fires");
		assertNotNull(got.get(), "the opener saw a mob");
		return got.get();
	}

	@Test
	@DisplayName("the wiki-calc link opens a ToA mob at the card's raid level, a land mob at 0")
	void linkCarriesTheCardsInvocation() throws Exception
	{
		assertEquals(300, exported("obelisk", 300).getToaInvocationLevel());
		assertEquals(0, exported("vorkath", 300).getToaInvocationLevel());
	}
}
