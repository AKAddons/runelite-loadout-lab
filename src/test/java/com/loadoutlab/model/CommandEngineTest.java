package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.optimizer.OptimizerService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shared command layer of ADR-0008: contract commands mutate the
 * core-owned PageState, trigger computes, and publish pages that carry
 * the params node.
 */
class CommandEngineTest
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

		CaptureLink()
		{
			super(null, "test");
		}

		@Override
		public void publishPage(Map<String, Object> page)
		{
			published = page;
		}
	}

	@Test
	@DisplayName("select finds the mob and triggers a compute with the state's params")
	void selectComputes()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		AtomicReference<Boolean> taskFlag = new AtomicReference<>();
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) ->
			{
				computed.set(mob);
				taskFlag.set(onTask);
			}, new CaptureLink());

		assertTrue(engine.execute("set-param", Map.of("param", "onTask", "value", true)),
			"params may be staged before a mob is selected");
		assertNull(computed.get(), "no mob yet - nothing to compute");
		assertTrue(engine.execute("select", Map.of("query", "zulrah")));
		assertNotNull(computed.get());
		assertTrue(computed.get().getName().toLowerCase().contains("zulrah"));
		assertEquals(Boolean.TRUE, taskFlag.get(), "the staged param rode the compute");
	}

	@Test
	@DisplayName("unknown commands, params, and empty queries are refused")
	void refusals()
	{
		CommandEngine engine = new CommandEngine(data, new PageState(),
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) -> fail("must not compute"),
			new CaptureLink());
		assertFalse(engine.execute("no-such-command", Map.of()));
		assertFalse(engine.execute("select", Map.of("query", "")));
		assertFalse(engine.execute("select", null));
		assertFalse(engine.execute("set-param", Map.of("param", "notAParam", "value", 1)));
		assertFalse(engine.execute(null, null));
	}

	@Test
	@DisplayName("view params republish the held results without recomputing")
	void viewParamsDoNotRecompute()
	{
		CaptureLink link = new CaptureLink();
		java.util.concurrent.atomic.AtomicInteger computes = new java.util.concurrent.atomic.AtomicInteger();
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) -> computes.incrementAndGet(),
			link);
		assertTrue(engine.execute("select", Map.of("query", "zulrah")));
		assertEquals(1, computes.get());
		MonsterStats mob = data.searchMonsters("zulrah", 1).get(0);
		engine.onResults(mob, Map.of());
		link.published = null;

		assertTrue(engine.execute("set-param", Map.of("param", "viewingBis", "value", true)));
		assertEquals(1, computes.get(), "a view param never recomputes");
		assertNotNull(link.published, "the held results republished under the new view");
		Map<?, ?> entry = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
		assertEquals(Boolean.TRUE, ((Map<?, ?>) entry.get("params")).get("viewingBis"));
	}

	@Test
	@DisplayName("engine commands are undoable and history rides the page")
	void undoRedo()
	{
		CaptureLink link = new CaptureLink();
		java.util.List<String> computedMobs = new java.util.ArrayList<>();
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) -> computedMobs.add(mob.getName()),
			link);
		assertTrue(engine.execute("select", Map.of("query", "zulrah")));
		assertTrue(engine.execute("select", Map.of("query", "vorkath")));
		assertEquals(2, computedMobs.size());

		assertTrue(engine.execute("undo", Map.of()), "undo re-selects zulrah");
		assertTrue(computedMobs.get(2).toLowerCase().contains("zulrah"));
		assertTrue(engine.execute("redo", Map.of()), "redo returns to vorkath");
		assertTrue(computedMobs.get(3).toLowerCase().contains("vorkath"));

		// A view-param flip is undoable too, and the published page
		// carries the history node.
		MonsterStats mob = data.searchMonsters("vorkath", 1).get(0);
		engine.onResults(mob, Map.of());
		assertTrue(engine.execute("set-param", Map.of("param", "viewingBis", "value", true)));
		assertTrue(engine.execute("undo", Map.of()));
		Map<?, ?> entries0 = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
		assertEquals(Boolean.FALSE, ((Map<?, ?>) entries0.get("params")).get("viewingBis"));
		Map<?, ?> history = (Map<?, ?>) link.published.get("history");
		assertEquals(Boolean.TRUE, history.get("canRedo"));
		assertEquals("View on", history.get("redoLabel"));
	}

	@Test
	@DisplayName("results publish as a page carrying the params node")
	void resultsCarryParams()
	{
		CaptureLink link = new CaptureLink();
		PageState state = new PageState();
		state.setParam("specWeapon", true);
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, raid, onDone) ->
			{
			}, link);
		MonsterStats mob = data.searchMonsters("zulrah", 1).get(0);
		engine.onResults(mob, Map.<CombatStyle, OptimizerService.StyleResult>of());

		assertNotNull(link.published);
		assertEquals(RenderModel.VERSION, link.published.get("v"));
		List<?> entries = (List<?>) link.published.get("entries");
		Map<?, ?> entry = (Map<?, ?>) entries.get(0);
		Map<?, ?> params = (Map<?, ?>) entry.get("params");
		assertEquals(Boolean.TRUE, params.get("specWeapon"));
		assertEquals(Boolean.FALSE, params.get("onTask"));
	}
}
