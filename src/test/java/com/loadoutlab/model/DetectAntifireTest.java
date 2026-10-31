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
 * Andrew (client test 2026-10-18): "can we have a 'detect best' on that"
 * - the dragonfire plate. Detection already runs at selection (the best
 * owned antifire tier); the plate's menu gains Detect best, which runs
 * it again for the current selection after a manual pick.
 */
class DetectAntifireTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	@Test
	@DisplayName("Detect best on the dragonfire plate re-runs the owned-tier detection for the selection")
	void detectAntifireReseeds() {
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, p, onDone) -> {
			},
			new CompanionLink());
		engine.setAntifireResolver(mobs -> 2);
		engine.setStoreOps(new TestStoreOps());
		engine.setOwnedCheck(id -> true);
		Monster vorkath = data.searchMonsters("vorkath", 1).get(0);
		engine.execute("select", Map.of("id", vorkath.getId()));
		assertEquals(2, state.paramsNode().get("antifireMode"), "selection seeds the detected tier");
		engine.execute("set-param", Map.of("param", "antifireMode", "value", 0));
		assertEquals(0, state.paramsNode().get("antifireMode"), "a manual pick sticks");
		assertTrue(engine.execute("detect-antifire", Map.of()));
		assertEquals(2, state.paramsNode().get("antifireMode"), "Detect best re-runs the detection");
	}

	@Test
	@DisplayName("the assumed antifire potion rides the Supplies row, every dose")
	void assumedPotionIsASupply() {
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, p, onDone) -> {
			},
			link);
		engine.setAntifireResolver(mobs -> 2);
		engine.setStoreOps(new TestStoreOps());
		engine.setOwnedCheck(id -> true);
		Monster vorkath = data.searchMonsters("vorkath", 1).get(0);
		engine.execute("select", Map.of("id", vorkath.getId()));
		Map<?, ?> potion = antifireSupply(engine, link, vorkath);
		assertNotNull(potion, "the super antifire must ride the supplies");
		assertEquals(21978, ((Number) potion.get("itemId")).intValue());
		assertTrue(((List<?>) potion.get("ids")).contains(22209), "extended doses match the filter too");
		engine.execute("set-param", Map.of("param", "antifireMode", "value", 0));
		assertNull(antifireSupply(engine, link, vorkath), "shield mode brings no potion");
	}

	private static Map<?, ?> antifireSupply(CommandEngine engine, CaptureLink link, Monster mob) {
		engine.onResults(mob, Map.of());
		try {
			javax.swing.SwingUtilities.invokeAndWait(() -> {
			});
		} catch (Exception ex) {
			throw new AssertionError(ex);
		}
		Map<?, ?> entry = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
		for (Object node : (List<?>) entry.get("supplies")) {
			if ("antifire".equals(((Map<?, ?>) node).get("category"))) {
				return (Map<?, ?>) node;
			}
		}
		return null;
	}

	private static final class CaptureLink extends CompanionLink {
		Map<String, Object> published;

		@Override
		public void publishPage(Map page) {
			published = page;
		}
	}
}
