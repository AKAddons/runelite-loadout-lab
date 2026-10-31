package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import com.loadoutlab.data.TripSupplies;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The cannon supply (miko, Discord 2026-10-17): chosen in the grey chip's
 * Cannon submenu it rides the Supplies row as five cells - base, stand,
 * barrels, furnace, cannonballs - and the bank filter with them. A raid
 * selection never carries it (no cannons in a raid).
 */
class CannonKitTest {
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

	private List<List<Integer>> cannonCells = new ArrayList<>();

	private List<Integer> cannonIds(boolean raid) {
		return cannonIds(raid ? "Tombs of Amascut" : null, "abyssal demon");
	}

	/** group: a curated group to select (null = the single mob). */
	private List<Integer> cannonIds(String group, String mobQuery) {
		cannonCells.clear();
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state,
			(m, p, onDone) -> {
			},
			link);
		engine.setStoreOps(new TestStoreOps());
		engine.setOwnedCheck(id -> true);
		engine.setSupplyDefaults(() -> Map.of(TripSupplies.CANNON, "DWARF_MULTICANNON"));
		if (group != null) {
			// A real group select: the group data flags a raid, the engine
			// sets the param - the same path RaidSelectionTest proves.
			engine.execute("select", Map.of("query", group));
			List<Monster> mobs = state.rosterMobs();
			List<Map<com.loadoutlab.engine.Style, com.loadoutlab.optimizer.OptimizerService.StyleResult>> empty =
				new ArrayList<>();
			for (int i = 0; i < mobs.size(); i++) {
				empty.add(Map.of());
			}
			engine.onRosterResults(mobs, empty);
		} else {
			Monster mob = data.searchMonsters(mobQuery, 1).get(0);
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
		List<Integer> ids = new ArrayList<>();
		for (Object node : (List<?>) entry.get("supplies")) {
			Map<?, ?> supply = (Map<?, ?>) node;
			if (TripSupplies.CANNON.equals(supply.get("category"))) {
				ids.add(((Number) supply.get("itemId")).intValue());
				List<Integer> cell = new ArrayList<>();
				for (Object o : (List<?>) supply.get("ids")) {
					cell.add(((Number) o).intValue());
				}
				cannonCells.add(cell);
			}
		}
		return ids;
	}

	@Test
	@DisplayName("chosen, the cannon is five supply cells in part order")
	void cannonIsFiveCells() {
		assertEquals(List.of(6, 8, 10, 12, 2), cannonIds(false));
	}

	@Test
	@DisplayName("each cell lights up its variants in the filter: the ornamented part, granite balls")
	void cellsCarryVariants() {
		cannonIds(false);
		assertEquals(List.of(6, 26520), cannonCells.get(0), "base and the (or) base");
		assertEquals(List.of(2, 21728), cannonCells.get(4), "cannonballs and granite cannonballs");
	}

	@Test
	@DisplayName("no cannon where it cannot be set up or does nothing: Barrows, Vorkath, a cannon-immune gorilla")
	void prohibitedOrImmuneSkipsTheCannon() {
		// Andrew, client test 2026-10-20: "it's suggesting dwarf multicannon
		// for barrows even though it's not allowed on that boss".
		assertTrue(cannonIds(null, "dharok the wretched").isEmpty(), "a Barrows brother");
		assertTrue(cannonIds("Barrows", null).isEmpty(), "the Barrows roster");
		assertTrue(cannonIds(null, "vorkath").isEmpty(), "Ungael is a prohibited area");
		assertTrue(cannonIds(null, "demonic gorilla").isEmpty(), "cannon-immune (wiki)");
		// Exact names only: the wiki's immune "Black demon (The Scar)" page
		// must not take the cannon from every black demon.
		assertEquals(List.of(6, 8, 10, 12, 2), cannonIds(null, "black demon"));
	}

	@Test
	@DisplayName("a raid selection never carries the cannon")
	void raidsSkipTheCannon() {
		assertTrue(cannonIds(true).isEmpty());
	}
}
