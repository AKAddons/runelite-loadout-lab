package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Thralls re-seed when the bank lands (ISS-01M66SMW3WR8JHTW6FQQC7MAP6,
 * Elling 2026-10-30: ToA on the Warden's book, the Thralls chip off because
 * the Book of the Dead was not in the owned scan at selection time).
 */
class ThrallsReseedTest {
	private static LoadoutData data;
	private PageState state;
	private CaptureLink link;
	private CommandEngine engine;
	private final Map<String, Object> seeds = new LinkedHashMap<>();

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static final class CaptureLink extends CompanionLink {
		Map published;

		@Override
		public void publishPage(Map page) {
			published = page;
		}
	}

	@BeforeEach
	void setUp() {
		state = new PageState();
		link = new CaptureLink();
		engine = new CommandEngine(data, state, (mob, p, onDone) -> {
		}, link);
		engine.setAssumptionSeeder(() -> new LinkedHashMap<>(seeds));
	}

	private MonsterStats mob(String name) {
		return data.searchMonsters(name, 1).get(0);
	}

	private Object thralls() {
		return state.paramsNode().get("thralls");
	}

	@Test
	@DisplayName("a Book of the Dead the bank scan finds after the selection turns thralls on at the refresh")
	void bankLandsLater() {
		seeds.put("thralls", false);
		seeds.put("thrallsWhy", "Book of the Dead not seen in your bank");
		engine.execute("select", Map.of("id", mob("general graardor").getId()));
		assertEquals(false, thralls());
		seeds.put("thralls", true);
		seeds.put("thrallsWhy", null);
		engine.execute("refresh", Map.of());
		assertEquals(true, thralls());
	}

	@Test
	@DisplayName("a chip the player turned off stays off through the refresh, and seeds again on the next selection")
	void playerWins() {
		seeds.put("thralls", true);
		engine.execute("select", Map.of("id", mob("general graardor").getId()));
		assertEquals(true, thralls());
		engine.execute("set-param", Map.of("param", "thralls", "value", false));
		engine.execute("refresh", Map.of());
		assertEquals(false, thralls());
		engine.execute("select", Map.of("id", mob("zulrah").getId()));
		assertEquals(true, thralls());
	}

	@Test
	@DisplayName("the reason the seed is off reaches the page for the chip's hover")
	void whyOnThePage() throws Exception {
		seeds.put("thralls", false);
		seeds.put("thrallsWhy", "Book of the Dead not seen in your bank");
		MonsterStats graardor = mob("general graardor");
		engine.execute("select", Map.of("id", graardor.getId()));
		engine.onResults(graardor, Map.of());
		SwingUtilities.invokeAndWait(() -> {
		});
		Map entry = (Map) ((List) link.published.get("entries")).get(0);
		assertEquals("Book of the Dead not seen in your bank", entry.get("thrallsWhy"));
	}
}
