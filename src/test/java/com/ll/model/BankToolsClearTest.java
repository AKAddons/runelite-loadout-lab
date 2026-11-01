package com.ll.model;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Field report 2026-11-01: "Is there a way to clear the bank selection
 * when not in use?" Andrew: "it should be clear when there is no active
 * search". The Filter bank and Show in bank toggles live on the card, so
 * every road to the empty page used to leave the bank filtered. */
class BankToolsClearTest {
	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	/** Records every bank call: null means "clear". */
	private static final class BankOps extends TestStoreOps {
		final List<Set<Integer>> filters = new ArrayList<>();
		final List<Set<Integer>> shows = new ArrayList<>();

		@Override
		public void filterBank(Set<Integer> itemIds, int[] layout) {
			filters.add(itemIds);
		}

		@Override
		public void showInBank(Set<Integer> itemIds) {
			shows.add(itemIds);
		}

		Set<Integer> lastFilter() {
			return filters.isEmpty() ? Set.of(-1) : filters.get(filters.size() - 1);
		}

		Set<Integer> lastShow() {
			return shows.isEmpty() ? Set.of(-1) : shows.get(shows.size() - 1);
		}
	}

	private static final class CaptureLink extends CompanionLink {
		Map<String, Object> page;

		@Override
		public void publishPage(Map page) {
			this.page = page;
		}

		@Override
		public Map<String, Object> lastPage() {
			return page;
		}
	}

	private static void flush() {
		try {
			javax.swing.SwingUtilities.invokeAndWait(() -> {
			});
		} catch (Exception ex) {
			throw new AssertionError(ex);
		}
	}

	private BankOps ops;
	private CaptureLink link;
	private CommandEngine engine;

	/** A searched mob with both bank tools switched on from its card. */
	private Monster searchedWithBankToolsOn() {
		PageState state = new PageState();
		link = new CaptureLink();
		engine = new CommandEngine(data, state, (mob, p, onDone) -> {
		}, link);
		ops = new BankOps();
		engine.setStoreOps(ops);
		Monster cerberus = data.searchMonsters("cerberus", 1).get(0);
		assertTrue(engine.execute("select", Map.of("id", cerberus.getId())));
		engine.onResults(cerberus, Map.of());
		flush();
		assertTrue(engine.execute("bank-filter", Map.of("ids", List.of(11802, 13239), "layout", Arrays.asList(11802, -1, 13239))));
		assertTrue(engine.execute("bank-show", Map.of("ids", List.of(11802))));
		assertEquals(Set.of(11802, 13239), ops.lastFilter(), "the filter went on");
		assertEquals(Set.of(11802), ops.lastShow(), "the highlight went on");
		return cerberus;
	}

	private void assertBankToolsCleared(String road) {
		flush();
		assertTrue(((List<?>) link.page.get("entries")).isEmpty(), road + ": the idle page");
		assertNull(ops.lastFilter(), road + ": the bank filter is cleared");
		assertNull(ops.lastShow(), road + ": the bank highlight is cleared");
	}

	@Test
	@DisplayName("cancelling the search clears the bank filter and highlight")
	void cancelClears() {
		searchedWithBankToolsOn();
		assertTrue(engine.execute("cancel", Map.of()));
		assertBankToolsCleared("cancel");
	}

	@Test
	@DisplayName("removing the last mob clears the bank filter and highlight")
	void removeLastMobClears() {
		searchedWithBankToolsOn();
		assertTrue(engine.execute("remove-mob", Map.of("index", 0)));
		assertBankToolsCleared("remove the last mob");
	}

	@Test
	@DisplayName("undoing the first search clears the bank filter and highlight")
	void undoFirstSearchClears() {
		searchedWithBankToolsOn();
		assertTrue(engine.execute("undo", Map.of()));
		assertBankToolsCleared("undo");
	}

	@Test
	@DisplayName("a live search keeps the bank tools: a republish does not clear them")
	void republishKeeps() {
		searchedWithBankToolsOn();
		int filters = ops.filters.size();
		assertTrue(engine.execute("republish", Map.of()));
		flush();
		assertEquals(filters, ops.filters.size(), "no bank call on a redraw");
		assertEquals(Set.of(11802, 13239), ops.lastFilter(), "the filter stays while the search is live");
	}
}
