package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A wilderness answer must say what it costs on death. Field report
 * 2026-08-22 (Artio): the CARD carried the risk line and the copied
 * report never mentioned risk at all, so a wildy answer read as
 * riskless on paper.
 */
class WildernessReportTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static String reportFor(String query)
	{
		CaptureLink link = new CaptureLink();
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, f2p, onTask, wild, lock, tradeables, risk, antifire, dc, spec,
				boosts, prayers, budget, swaps, onDone) ->
			{
			},
			link);
		assertTrue(engine.execute("select", Map.of("query", query)));
		MonsterStats mob = state.mob();
		assertNotNull(mob, query + " selected");
		engine.onResults(mob, styles(mob));
		flushEdt();
		assertNotNull(link.published, "a page published");
		String report = (String) link.published.get("reportText");
		assertNotNull(report, "the page carries a report");
		return report;
	}

	private static Map<com.loadoutlab.engine.CombatStyle,
		com.loadoutlab.optimizer.OptimizerService.StyleResult> styles(MonsterStats mob)
	{
		return styles(data, mob);
	}

	/** Real per-style results for a mob - shared with WikiCalcExportTest. */
	static Map<com.loadoutlab.engine.CombatStyle,
		com.loadoutlab.optimizer.OptimizerService.StyleResult> styles(LoadoutData data, MonsterStats mob)
	{
		com.loadoutlab.optimizer.OptimizerService service =
			new com.loadoutlab.optimizer.OptimizerService(data);
		try
		{
			java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
			java.util.concurrent.atomic.AtomicReference<Map<com.loadoutlab.engine.CombatStyle,
				com.loadoutlab.optimizer.OptimizerService.StyleResult>> out =
				new java.util.concurrent.atomic.AtomicReference<>();
			com.loadoutlab.optimizer.ServiceCalls.bestPerStyle(service, mob,
				com.loadoutlab.engine.PlayerLevels.MAXED,
				com.loadoutlab.engine.PlayerLevels.MAXED,
				com.loadoutlab.engine.PrayerUnlocks.ALL,
				com.loadoutlab.engine.RequirementProfile.MAXED,
				com.loadoutlab.engine.OwnedItems.EMPTY, 0,
				false, false, "",
				new java.util.EnumMap<>(com.loadoutlab.engine.CombatStyle.class), -1,
				com.loadoutlab.engine.OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, false,
				java.util.Collections.emptySet(), 0,
				java.util.Collections.emptyMap(), null, 0, java.util.Collections.emptySet(),
				r ->
				{
					out.set(r);
					done.countDown();
				});
			assertTrue(done.await(300, java.util.concurrent.TimeUnit.SECONDS));
			return out.get();
		}
		catch (InterruptedException ex)
		{
			throw new AssertionError(ex);
		}
		finally
		{
			service.shutdown();
		}
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
	@DisplayName("a wilderness boss report states the per-death risk")
	void wildernessReportCarriesRisk()
	{
		String report = reportFor("artio");
		assertTrue(report.contains("Risk:"),
			"the wildy report must price a death:\n" + report);
		assertTrue(report.contains("Risk cap:"),
			"the params must show the cap state:\n" + report);
	}

	@Test
	@DisplayName("a non-wilderness report stays quiet about risk")
	void tameReportHasNoRiskLine()
	{
		String report = reportFor("general graardor");
		assertFalse(report.contains("Risk:"), "no risk talk outside the wilderness");
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
