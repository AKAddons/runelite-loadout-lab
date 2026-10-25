package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.TripSupplies;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Boss teleports (ISS-01M5RMNK62507Q42KKCJ9A7AEE, Kendyy and Andrew on
 * Discord 2026-10-24: "specific teleport items like the giantsoul amulet for
 * royal titans/obor/bryophta. zulrah has the specific zulrah teleport (or
 * master scroll book)"; Andrew's answers: "in the inventory for a boss", the
 * best one you own, a curated wiki-checked list).
 */
class BossTeleportTest {
	private static final String CATEGORY = "bossTeleport";
	private static final int ZUL_ANDRA = 12938;
	private static final int GIANTSOUL = 30638;
	private static final int RING_OF_SHADOWS = 28327;
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static final class CaptureLink extends CompanionLink {
		Map<String, Object> published;

		@Override
		public void publishPage(Map<String, Object> page) {
			published = page;
		}
	}

	/** The boss-teleport supply nodes of one trip: a mob by name, or a curated group. */
	private static List<Map<?, ?>> teleports(String query, boolean group, IntPredicate owns,
		Map<String, String> defaults, IntPredicate unlocked) {
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state, (m, p, onDone) -> {
		}, link);
		engine.setStoreOps(new TestStoreOps());
		engine.setOwnedCheck(owns);
		engine.setSupplyDefaults(() -> defaults);
		engine.setUnlocks(unlocked);
		if (group) {
			engine.execute("select", Map.of("query", query));
			List<MonsterStats> mobs = state.rosterMobs();
			List<Map<com.loadoutlab.engine.CombatStyle, com.loadoutlab.optimizer.OptimizerService.StyleResult>> empty =
				new ArrayList<>();
			for (int i = 0; i < mobs.size(); i++) {
				empty.add(Map.of());
			}
			engine.onRosterResults(mobs, empty);
		} else {
			MonsterStats mob = data.searchMonsters(query, 1).get(0);
			engine.execute("select", Map.of("id", mob.getId()));
			engine.onResults(mob, Map.of());
		}
		try {
			javax.swing.SwingUtilities.invokeAndWait(() -> {
			});
		} catch (Exception ex) {
			throw new AssertionError(ex);
		}
		Map<?, ?> entry = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
		List<Map<?, ?>> out = new ArrayList<>();
		for (Object node : (List<?>) entry.get("supplies")) {
			if (CATEGORY.equals(((Map<?, ?>) node).get("category"))) {
				out.add((Map<?, ?>) node);
			}
		}
		return out;
	}

	private static List<Map<?, ?>> teleports(String mob, IntPredicate owns) {
		return teleports(mob, false, owns, Map.of(CATEGORY, "DETECT_BEST"), v -> false);
	}

	private static int itemId(Map<?, ?> node) {
		return ((Number) node.get("itemId")).intValue();
	}

	@Test
	@DisplayName("Zulrah with a Zul-andra teleport owned packs it - an inventory supply, not the utility strip")
	void zulrahPacksItsScroll() {
		List<Map<?, ?>> nodes = teleports("zulrah", id -> id == ZUL_ANDRA);
		assertEquals(1, nodes.size());
		assertEquals(ZUL_ANDRA, itemId(nodes.get(0)));
		assertEquals("Boss teleport", TripSupplies.label(CATEGORY));
		assertTrue(TripSupplies.options(CATEGORY).stream().noneMatch(o -> o.utility), "carried in the inventory");
	}

	@Test
	@DisplayName("the Royal Titans pair packs one giantsoul amulet")
	void royalTitansPackOneAmulet() {
		List<Map<?, ?>> nodes = teleports("Royal Titans", true, id -> id == GIANTSOUL,
			Map.of(CATEGORY, "DETECT_BEST"), v -> false);
		assertEquals(1, nodes.size(), "one amulet for the pair");
		assertEquals(GIANTSOUL, itemId(nodes.get(0)));
	}

	@Test
	@DisplayName("a mob no teleport serves gets no boss teleport, owning everything")
	void nonBossGetsNone() {
		assertTrue(teleports("abyssal demon", id -> true).isEmpty());
	}

	@Test
	@DisplayName("Zulrah's menu offers only the teleports that serve Zulrah")
	void menuListsTheBossOptionsOnly() {
		List<?> options = (List<?>) teleports("zulrah", id -> id == ZUL_ANDRA).get(0).get("options");
		assertEquals(List.of("ZUL_ANDRA_TELEPORT"),
			options.stream().map(o -> ((Map<?, ?>) o).get("key")).collect(Collectors.toList()));
	}

	@Test
	@DisplayName("a stored giantsoul choice falls back to the best Zulrah teleport you own")
	void choiceForAnotherBossFallsBack() {
		List<Map<?, ?>> nodes = teleports("zulrah", false, id -> id == ZUL_ANDRA || id == GIANTSOUL,
			Map.of(CATEGORY, "GIANTSOUL_AMULET"), v -> false);
		assertEquals(ZUL_ANDRA, itemId(nodes.get(0)));
	}

	@Test
	@DisplayName("Duke Sucellus gets the ring of shadows only once its Ghorrock teleport is unlocked")
	void ringNeedsItsTablet() {
		assertTrue(teleports("duke sucellus", false, id -> id == RING_OF_SHADOWS,
			Map.of(CATEGORY, "DETECT_BEST"), v -> false).isEmpty(), "ring owned, Ghorrock still locked");
		List<Map<?, ?>> nodes = teleports("duke sucellus", false, id -> id == RING_OF_SHADOWS,
			Map.of(CATEGORY, "DETECT_BEST"), v -> v == 15142);
		assertEquals(RING_OF_SHADOWS, itemId(nodes.get(0)));
	}

	@Test
	@DisplayName("scrolls stored in the master scroll book read as owned - Zul-andra and Key master; the ring's set unlocks read back")
	void scrollBookCountsAsOwned() {
		assertEquals(Set.of(ZUL_ANDRA, 13249),
			com.loadoutlab.collection.PotionStorage.owned(v -> v == 5683 ? 3 : v == 5684 ? 1 : 0).keySet());
		assertTrue(com.loadoutlab.collection.PotionStorage.tracks(5683), "a scroll stored or used re-reads ownership");
		assertEquals(Map.of(), com.loadoutlab.collection.PotionStorage.owned(v -> 0));
		assertEquals(Set.of(15142), TripSupplies.unlocked(v -> v == 15142 ? 1 : 0));
	}

	@Test
	@DisplayName("every boss a teleport names exists in the monster corpus")
	void everyBossExists() {
		Set<String> names = data.getMonsters().stream().map(m -> m.getName().toLowerCase()).collect(Collectors.toSet());
		for (TripSupplies.Option option : TripSupplies.options(CATEGORY)) {
			assertTrue(option.monsters.length > 0, option.key + " names its bosses");
			for (String boss : option.monsters) {
				assertTrue(names.contains(boss), option.key + " names " + boss + ", which the corpus lacks");
			}
		}
	}
}
