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

	/** Page assembly marshals to the EDT (the deadlock fix); drain it
	 * before asserting on what was published. */
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

	@Test
	@DisplayName("select finds the mob and triggers a compute with the state's params")
	void selectComputes()
	{
		AtomicReference<MonsterStats> computed = new AtomicReference<>();
		AtomicReference<Boolean> taskFlag = new AtomicReference<>();
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, onDone) ->
			{
				computed.set(mob);
				taskFlag.set(onTask);
			}, new CaptureLink());

		assertTrue(engine.execute("set-param", Map.of("param", "onTask", "value", true)),
			"params may be staged before a mob is selected");
		assertNull(computed.get(), "no mob yet - nothing to compute");
		assertTrue(engine.execute("select", Map.of("query", "abyssal demon")));
		assertNotNull(computed.get());
		assertTrue(computed.get().getName().toLowerCase().contains("abyssal"));
		assertEquals(Boolean.TRUE, taskFlag.get(), "the staged param rode the compute");
	}

	@Test
	@DisplayName("unknown commands, params, and empty queries are refused")
	void refusals()
	{
		CommandEngine engine = new CommandEngine(data, new PageState(),
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, onDone) -> fail("must not compute"),
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
				boosts, prayers, budget, swaps, onDone) -> computes.incrementAndGet(),
			link);
		assertTrue(engine.execute("select", Map.of("query", "abyssal demon")));
		assertEquals(1, computes.get());
		MonsterStats mob = data.searchMonsters("zulrah", 1).get(0);
		engine.onResults(mob, Map.of());
		link.published = null;

		assertTrue(engine.execute("set-param", Map.of("param", "viewingBis", "value", true)));
		assertEquals(1, computes.get(), "a view param never recomputes");
		flushEdt();
		assertNotNull(link.published, "the held results republished under the new view");
		Map<?, ?> entry = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
		assertEquals(Boolean.TRUE, ((Map<?, ?>) entry.get("params")).get("viewingBis"));
	}

	@Test
	@DisplayName("a group query opens the roster; undo returns to the single mob")
	void groupSelect()
	{
		java.util.List<String> rosterRuns = new java.util.ArrayList<>();
		java.util.List<String> singleRuns = new java.util.ArrayList<>();
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, onDone) -> singleRuns.add(mob.getName()),
			new CaptureLink());
		engine.setRosterCompute((mobs, f2p, onTask, wild, lock, tradeables, risk,
			antifire, dc, spec, boosts, prayers, budget, swaps, onDone) ->
			rosterRuns.add(mobs.size() + " mobs"));

		assertTrue(engine.execute("select", Map.of("query", "abyssal demon")));
		int singles = singleRuns.size();
		assertTrue(engine.execute("select", Map.of("query", "dagannoth kings")),
			"a curated group name opens the roster");
		assertEquals(1, rosterRuns.size());
		assertTrue(rosterRuns.get(0).startsWith("3"), "the DKs roster carries its three kings");
		assertTrue(engine.execute("undo", Map.of()), "undo returns to the single mob");
		assertEquals(singles + 1, singleRuns.size());
	}

	@Test
	@DisplayName("mob-scoped exclude/sim work on a roster via the lensed mob")
	void mobScopedAddsWorkOnRosters()
	{
		// The lensedMob-fallback family, third member (field report
		// 2026-08-20: the card trio's add-by-search silently no-oped on
		// the Maggot King roster): set-note and the REMOVE commands got
		// the fallback earlier; exclude-for-mob/sim-for-mob still read
		// bare state.mob(), null on rosters.
		java.util.List<String> captured = new java.util.ArrayList<>();
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, onDone) ->
			{
			},
			new CaptureLink());
		engine.setRosterCompute((mobs, f2p, onTask, wild, lock, tradeables, risk,
			antifire, dc, spec, boosts, prayers, budget, swaps, onDone) ->
			{
			});
		engine.setStoreOps(new CommandEngine.StoreOps()
		{
			public boolean toggleExclusion(int itemId)
			{
				return true;
			}

			public boolean toggleSim(int itemId)
			{
				return true;
			}

			public void toggleAlwaysFilter(int itemId)
			{
			}

			public void setSupplyDefault(String category, String choice)
			{
			}

			public void pin(int monsterId, String slot, int itemId)
			{
			}

			public void unpin(int monsterId, String slot)
			{
			}

			public void showInBank(java.util.Set<Integer> itemIds)
			{
			}

			public void filterBank(java.util.Set<Integer> itemIds, int[] layout)
			{
			}

			public String pinnedSpell(int monsterId)
			{
				return null;
			}

			public int pinnedSpec(int monsterId)
			{
				return -1;
			}

			public List<Map<String, Object>> mobExclusions(int monsterId)
			{
				return java.util.Collections.emptyList();
			}

			public List<Map<String, Object>> mobSims(int monsterId)
			{
				return java.util.Collections.emptyList();
			}

			public List<Map<String, Object>> mobFilters(int monsterId)
			{
				return java.util.Collections.emptyList();
			}

			public void setPinnedSpell(int monsterId, String spellName)
			{
			}

			public void setPinnedSpec(int monsterId, int itemId)
			{
			}

			public String note(int monsterId)
			{
				return null;
			}

			public void setNote(int monsterId, String note)
			{
			}

			public void excludeForMob(int monsterId, String scope, int itemId)
			{
				captured.add("exclude:" + monsterId + ":" + itemId);
			}

			public void simForMob(int monsterId, int itemId)
			{
				captured.add("sim:" + monsterId + ":" + itemId);
			}

			public void removeMobExclusion(int monsterId, String scope, int itemId)
			{
				captured.add("removeExclude:" + monsterId + ":" + itemId);
			}

			public void removeMobSim(int monsterId, int itemId)
			{
				captured.add("removeSim:" + monsterId + ":" + itemId);
			}

			public void addMobFilter(int monsterId, int itemId)
			{
			}

			public void removeMobFilter(int monsterId, String scope, int itemId)
			{
			}

			public void setSupplyOverride(int profileId, String category, String choice)
			{
			}

			public Map<String, String> supplyOverrides(int profileId)
			{
				return java.util.Collections.emptyMap();
			}
		});
		assertTrue(engine.execute("select", Map.of("query", "dagannoth kings")),
			"the roster opens");
		assertTrue(engine.execute("exclude-for-mob", Map.of("itemId", 4151)),
			"exclude-for-mob must reach the lensed mob on a roster");
		assertTrue(engine.execute("sim-for-mob", Map.of("itemId", 11832)),
			"sim-for-mob must reach the lensed mob on a roster");
		assertEquals(2, captured.size());
		assertTrue(captured.get(0).startsWith("exclude:"));
		assertTrue(captured.get(1).startsWith("sim:"));

		// Back reverses the adds (field ask 2026-08-20: "we want the
		// back to reverse that") - undo lands on the store, not the
		// selection.
		assertTrue(engine.execute("undo", Map.of()));
		assertEquals(3, captured.size());
		assertTrue(captured.get(2).startsWith("removeSim:"), captured.get(2));
		assertTrue(engine.execute("undo", Map.of()));
		assertEquals(4, captured.size());
		assertTrue(captured.get(3).startsWith("removeExclude:"), captured.get(3));
	}

	@Test
	@DisplayName("undo restores the captured page instantly - no recompute")
	void undoRestoresSnapshotWithoutComputing()
	{
		CaptureLink link = new CaptureLink();
		java.util.concurrent.atomic.AtomicInteger computes = new java.util.concurrent.atomic.AtomicInteger();
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, onDone) -> computes.incrementAndGet(),
			link);
		assertTrue(engine.execute("select", Map.of("query", "abyssal demon")));
		MonsterStats demon = data.searchMonsters("abyssal demon", 1).get(0);
		engine.onResults(demon, Map.of());
		flushEdt();
		Map<String, Object> pageA = link.published;
		assertNotNull(pageA);

		assertTrue(engine.execute("set-param", Map.of("param", "onTask", "value", true)));
		int computesAfterParam = computes.get();
		engine.onResults(demon, Map.of());
		flushEdt();
		link.published = null;

		assertTrue(engine.execute("undo", Map.of()));
		flushEdt();
		assertEquals(computesAfterParam, computes.get(),
			"undo publishes the snapshot - it never recomputes");
		assertNotNull(link.published, "the snapshot page republished");
		assertEquals(pageA.get("entries"), link.published.get("entries"),
			"the restored page is the one captured before the command");
		assertEquals(Boolean.FALSE, state.paramsNode().get("onTask"),
			"the state reverted with it");

		link.published = null;
		assertTrue(engine.execute("redo", Map.of()));
		flushEdt();
		assertEquals(computesAfterParam, computes.get(),
			"redo restores the forward snapshot without computing");
		assertNotNull(link.published);
		assertEquals(Boolean.TRUE, state.paramsNode().get("onTask"));
	}

	@Test
	@DisplayName("switching the roster lens is a view gesture - never in history")
	void lensSwitchNeverRecords()
	{
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, onDone) ->
			{
			},
			new CaptureLink());
		engine.setRosterCompute((mobs, f2p, onTask, wild, lock, tradeables, risk,
			antifire, dc, spec, boosts, prayers, budget, swaps, onDone) ->
			{
			});
		assertTrue(engine.execute("select", Map.of("query", "dagannoth kings")));
		assertTrue(engine.execute("set-param", Map.of("param", "lensIndex", "value", 2)),
			"the lens still switches");
		assertEquals(2, state.paramsNode().get("lensIndex"));
		assertTrue(engine.execute("undo", Map.of()),
			"undo must skip the lens switch entirely");
		// The undo undid the SELECT (the only recorded action), never a
		// lens hop (field ask 2026-08-20: mob switching is not an action).
		assertNull(state.mob(), "back landed on the pre-select state");
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
				boosts, prayers, budget, swaps, onDone) -> computedMobs.add(mob.getName()),
			link);
		assertTrue(engine.execute("select", Map.of("query", "abyssal demon")));
		assertTrue(engine.execute("select", Map.of("query", "black demon")));
		assertEquals(2, computedMobs.size());

		assertTrue(engine.execute("undo", Map.of()), "undo re-selects the abyssal demon");
		assertTrue(computedMobs.get(2).toLowerCase().contains("abyssal"));
		assertTrue(engine.execute("redo", Map.of()), "redo returns to the black demon");
		assertTrue(computedMobs.get(3).toLowerCase().contains("black demon"));

		// A view-param flip is undoable too, and the published page
		// carries the history node.
		MonsterStats mob = data.searchMonsters("vorkath", 1).get(0);
		engine.onResults(mob, Map.of());
		assertTrue(engine.execute("set-param", Map.of("param", "viewingBis", "value", true)));
		assertTrue(engine.execute("undo", Map.of()));
		flushEdt();
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
				boosts, prayers, budget, swaps, onDone) ->
			{
			}, link);
		MonsterStats mob = data.searchMonsters("zulrah", 1).get(0);
		engine.onResults(mob, Map.<CombatStyle, OptimizerService.StyleResult>of());

		flushEdt();
		assertNotNull(link.published);
		assertEquals(RenderModel.VERSION, link.published.get("v"));
		List<?> entries = (List<?>) link.published.get("entries");
		Map<?, ?> entry = (Map<?, ?>) entries.get(0);
		Map<?, ?> params = (Map<?, ?>) entry.get("params");
		assertEquals(Boolean.TRUE, params.get("specWeapon"));
		assertEquals(Boolean.FALSE, params.get("onTask"));
	}
}
