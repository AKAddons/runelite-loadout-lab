package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.JsonResources;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MobLocations;
import com.loadoutlab.data.Monster;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Shortest Path link (ISS-01M5TCXDV8HF2V5C627SZEY2W3, Andrew 2026-10-24:
 * "link shortest path to boss mobs at least and maybe for other slayer mobs
 * too"; 2026-10-25: "all mobs if possible", "hidden"); above the footer from
 * the pick on (ISS-01M5TE7QAZYZQMTPA1JZ0BQN1Z: "displayed statically while loading").
 */
class ShortestPathLinkTest {
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

	/** Whether the page offers the path link for one mob - while pending
	 * (before the results) and after - and the names the link walked to. */
	private static boolean[] card(String query, boolean available, List<String> walked) {
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state, (m, p, onDone) -> {
		}, link);
		engine.setStoreOps(new TestStoreOps());
		engine.setPathLink(() -> available, walked::add);
		Monster mob = data.searchMonsters(query, 1).get(0);
		engine.execute("select", Map.of("id", mob.getId()));
		flush();
		boolean pending = Boolean.TRUE.equals(link.published.get("pathable"));
		engine.onResults(mob, Map.of());
		flush();
		engine.execute("path-to", Map.of());
		return new boolean[]{pending, Boolean.TRUE.equals(link.published.get("pathable"))};
	}

	private static void flush() {
		try {
			javax.swing.SwingUtilities.invokeAndWait(() -> {
			});
		} catch (Exception ex) {
			throw new AssertionError(ex);
		}
	}

	@Test
	@DisplayName("Zulrah's point is Zul-Andra; a mob nobody placed has none")
	void decodes() {
		int[] zulrah = MobLocations.of("Zulrah");
		assertNotNull(zulrah);
		assertEquals(2193, zulrah[0] & 0x7FFF, "Shortest Path's packing: x | y << 15 | plane << 30");
		assertEquals(3060, zulrah[0] >> 15 & 0x7FFF);
		assertEquals(0, zulrah[0] >>> 30);
		assertTrue(MobLocations.of("abyssal demon").length > 1, "every spawn, the nearest walked to");
		assertNull(MobLocations.of("no such mob"));
	}

	@Test
	@DisplayName("instanced fights path to their entrance: every ToA boss shares the Tombs of Amascut pin")
	void instancesUseTheirEntrance() {
		int[] akkha = MobLocations.of("Akkha");
		int[] warden = MobLocations.of("Tumeken's Warden");
		assertEquals(1, akkha.length);
		assertArrayEquals(akkha, warden);
		assertTrue((akkha[0] >> 15 & 0x7FFF) < 5000, "the entrance on the surface, not the raid's own region");
	}

	@Test
	@DisplayName("every location key is a corpus mob name")
	void keysAreMobs() {
		Set<String> names = data.getMonsters().stream().map(m -> m.getName().toLowerCase()).collect(Collectors.toSet());
		for (String key : JsonResources.object("mob_locations.json").keySet()) {
			assertTrue(key.startsWith("_") || names.contains(key), key + " is no corpus mob");
		}
	}

	@Test
	@DisplayName("with Shortest Path on, a placed mob's card links and the click walks to it")
	void linksWhenAvailable() {
		List<String> walked = new ArrayList<>();
		assertArrayEquals(new boolean[]{true, true}, card("zulrah", true, walked), "while loading and after");
		assertEquals(List.of("Zulrah"), walked);
	}

	@Test
	@DisplayName("hidden without Shortest Path, and for a mob with no place")
	void hiddenOtherwise() {
		List<String> walked = new ArrayList<>();
		assertArrayEquals(new boolean[]{false, false}, card("zulrah", false, walked));
		assertTrue(walked.isEmpty(), "no walk without the plugin");
		assertArrayEquals(new boolean[]{false, false}, card("a corpse", true, walked));
		assertTrue(walked.isEmpty(), "no walk to nowhere");
	}
}
