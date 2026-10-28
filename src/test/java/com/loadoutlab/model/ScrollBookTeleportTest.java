package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A boss-teleport scroll held only in the master scroll book shows as the book
 * (ISS-01M5VDZXT4W4D5DP468R5R7NSW, Andrew 2026-10-25: "for teleport item for cerberus it has the scroll
 * but dwms knows its in the master scroll book ... it should be the master
 * scroll book that is shown as the item").
 */
class ScrollBookTeleportTest {
	private static final int KEY_MASTER = 13249;
	private static final int SCROLL_BOOK = 21389;
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static final class CaptureLink extends CompanionLink {
		Map<String, Object> published;

		@Override
		public void publishPage(Map page) {
			published = page;
		}
	}

	private static Map<?, ?> teleport(IntPredicate inBook) {
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state, (m, p, onDone) -> {
		}, link);
		engine.setStoreOps(new TestStoreOps());
		engine.setOwnedCheck(id -> id == KEY_MASTER);
		engine.setSupplyDefaults(() -> Map.of("bossTeleport", "DETECT_BEST"));
		engine.setUnlocks(v -> false);
		engine.setInScrollBook(inBook);
		MonsterStats cerberus = data.searchMonsters("cerberus", 1).get(0);
		engine.execute("select", Map.of("id", cerberus.getId()));
		engine.onResults(cerberus, Map.of());
		try {
			javax.swing.SwingUtilities.invokeAndWait(() -> {
			});
		} catch (Exception ex) {
			throw new AssertionError(ex);
		}
		Map<?, ?> entry = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
		List<Map<?, ?>> out = new ArrayList<>();
		for (Object node : (List<?>) entry.get("supplies")) {
			if ("bossTeleport".equals(((Map<?, ?>) node).get("category"))) out.add((Map<?, ?>) node);
		}
		return out.get(0);
	}

	@Test
	@DisplayName("a Key master teleport held only in the master scroll book shows as the book")
	void bookShown() {
		Map<?, ?> node = teleport(id -> id == KEY_MASTER);
		assertEquals(SCROLL_BOOK, ((Number) node.get("itemId")).intValue());
		assertTrue(String.valueOf(node.get("name")).startsWith("Master scroll book"), node.toString());
		assertEquals(List.of(SCROLL_BOOK), node.get("ids"), "the bank filter finds the book");
	}

	@Test
	@DisplayName("a loose Key master teleport shows as itself")
	void looseScroll() {
		Map<?, ?> node = teleport(id -> false);
		assertEquals(KEY_MASTER, ((Number) node.get("itemId")).intValue());
	}
}
