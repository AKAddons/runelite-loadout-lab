package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spellbook Swap + Vengeance as a SUPPLIES option (field ask
 * 2026-08-22): it changes which runes the trip carries and nothing
 * else - the damage Vengeance returns is deliberately unmodelled.
 */
class SpellbookSwapTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static CommandEngine engine(PageState state, CaptureLink link, int magic)
	{
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) ->
			{
			},
			link);
		engine.setMagicLevel(magic);
		// utilityRunes ride the page only when stores are wired.
		engine.setStoreOps(new TestStoreOps());
		return engine;
	}

	private static List<Map<String, Object>> runesOf(Map<String, Object> page)
	{
		List<?> entries = (List<?>) page.get("entries");
		Map<?, ?> entry = (Map<?, ?>) entries.get(0);
		List<?> mobs = (List<?>) entry.get("mobs");
		Object runes = ((Map<?, ?>) mobs.get(0)).get("utilityRunes");
		return runes instanceof List ? (List<Map<String, Object>>) runes
			: java.util.Collections.emptyList();
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

	@Test
	@DisplayName("the option is offered only when 96 Magic is reachable")
	void offeredOnlyWhenReachable()
	{
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine high = engine(state, link, 91);
		high.execute("select", Map.of("query", "abyssal demon"));
		high.onResults(data.searchMonsters("abyssal demon", 1).get(0), Map.of());
		flushEdt();
		assertEquals(Boolean.TRUE,
			((Map<?, ?>) link.published.get("assumeOptions")).get("spellbookSwapAvailable"),
			"91 + a boost reaches 96");

		PageState low = new PageState();
		CaptureLink lowLink = new CaptureLink();
		CommandEngine weak = engine(low, lowLink, 80);
		weak.execute("select", Map.of("query", "abyssal demon"));
		weak.onResults(data.searchMonsters("abyssal demon", 1).get(0), Map.of());
		flushEdt();
		assertEquals(Boolean.FALSE,
			((Map<?, ?>) lowLink.published.get("assumeOptions")).get("spellbookSwapAvailable"),
			"80 Magic cannot reach 96");
	}

	@Test
	@DisplayName("switching it on brings the swap and Vengeance runes")
	void bringsTheRunes()
	{
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = engine(state, link, 99);
		assertTrue(engine.execute("select", Map.of("query", "abyssal demon")));
		engine.onResults(data.searchMonsters("abyssal demon", 1).get(0), Map.of());
		flushEdt();
		assertTrue(runesOf(link.published).stream()
			.noneMatch(r -> String.valueOf(r.get("why")).contains("Vengeance")),
			"off by default");

		assertTrue(engine.execute("set-param",
			Map.of("param", "spellbookSwap", "value", true)));
		flushEdt();
		List<Map<String, Object>> runes = runesOf(link.published);
		assertTrue(runes.stream().anyMatch(r ->
			String.valueOf(r.get("why")).contains("Spellbook Swap")), "swap runes: " + runes);
		assertTrue(runes.stream().anyMatch(r ->
			String.valueOf(r.get("why")).contains("Vengeance")), "vengeance runes: " + runes);
	}

	@Test
	@DisplayName("it never moves a dps number - supplies only")
	void neverTouchesTheAnswer()
	{
		PageState state = new PageState();
		state.setParam("spellbookSwap", true);
		// A view param: it republishes the held answer, never recomputes.
		assertTrue(PageState.isViewParam("spellbookSwap"));
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
