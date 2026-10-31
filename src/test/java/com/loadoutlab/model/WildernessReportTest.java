package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A wilderness answer must say what it costs on death. Field report
 * 2026-08-22 (Artio): the CARD carried the risk line and the copied
 * report never mentioned risk at all, so a wildy answer read as
 * riskless on paper. The report went in 0.7.2 (Andrew 2026-10-29); the
 * card's own risk node keeps the contract.
 */
class WildernessReportTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	/** Whether any shown card on the page prices a death. */
	static boolean priced(Map<?, ?> page) {
		for (Object e : (List<?>) page.get("entries")) {
			for (Object m : (List<?>) ((Map<?, ?>) e).get("mobs")) {
				Object styles = ((Map<?, ?>) m).get("styles");
				if (!(styles instanceof Map)) continue;
				for (Object s : ((Map<?, ?>) styles).values()) {
					for (String side : new String[]{"yours", "bis"}) {
						Object card = ((Map<?, ?>) s).get(side);
						if (card instanceof Map && ((Map<?, ?>) card).containsKey("risk")) return true;
					}
				}
			}
		}
		return false;
	}

	private static Map<String, Object> pageFor(String query) {
		CaptureLink link = new CaptureLink();
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, p, onDone) -> {
			},
			link);
		assertTrue(engine.execute("select", Map.of("query", query)));
		Monster mob = state.mob();
		assertNotNull(mob, query + " selected");
		engine.onResults(mob, styles(mob));
		flushEdt();
		assertNotNull(link.published, "a page published");
		return link.published;
	}

	private static Map<com.loadoutlab.engine.Style,
		com.loadoutlab.optimizer.OptimizerService.StyleResult> styles(Monster mob) {
		return styles(data, mob);
	}

	/** Real per-style results for a mob - shared with WikiCalcExportTest. */
	static Map<com.loadoutlab.engine.Style,
		com.loadoutlab.optimizer.OptimizerService.StyleResult> styles(LoadoutData data, Monster mob) {
		com.loadoutlab.optimizer.OptimizerService service =
			new com.loadoutlab.optimizer.OptimizerService(data);
		try {
			java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
			java.util.concurrent.atomic.AtomicReference<Map<com.loadoutlab.engine.Style,
				com.loadoutlab.optimizer.OptimizerService.StyleResult>> out =
				new java.util.concurrent.atomic.AtomicReference<>();
			com.loadoutlab.optimizer.ServiceCalls.bestPerStyle(service, mob,
				com.loadoutlab.engine.PlayerLevels.MAXED,
				com.loadoutlab.engine.PlayerLevels.MAXED,
				com.loadoutlab.engine.PrayerUnlocks.ALL,
				com.loadoutlab.engine.RequirementProfile.MAXED,
				com.loadoutlab.engine.OwnedItems.EMPTY, 0,
				false, false, "",
				new java.util.EnumMap<>(com.loadoutlab.engine.Style.class), -1,
				com.loadoutlab.engine.OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, false,
				java.util.Collections.emptySet(), 0,
				java.util.Collections.emptyMap(), null, 0, java.util.Collections.emptySet(),
				r -> {
					out.set(r);
					done.countDown();
				});
			assertTrue(done.await(300, java.util.concurrent.TimeUnit.SECONDS));
			return out.get();
		} catch (InterruptedException ex) {
			throw new AssertionError(ex);
		} finally {
			service.shutdown();
		}
	}

	private static void flushEdt() {
		try {
			javax.swing.SwingUtilities.invokeAndWait(() -> {
			});
		} catch (Exception ex) {
			throw new AssertionError(ex);
		}
	}

	@Test
	@DisplayName("a wilderness boss card states the per-death risk")
	void wildernessCardCarriesRisk() {
		assertTrue(priced(pageFor("artio")), "the wildy card must price a death");
	}

	@Test
	@DisplayName("a non-wilderness card stays quiet about risk")
	void tameCardHasNoRiskLine() {
		assertFalse(priced(pageFor("general graardor")), "no risk talk outside the wilderness");
	}

	private static final class CaptureLink extends CompanionLink {
		Map<String, Object> published;

		@Override
		public void publishPage(Map page) {
			published = page;
		}
	}
}
