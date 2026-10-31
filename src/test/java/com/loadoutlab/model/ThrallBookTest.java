package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import com.loadoutlab.data.NavalCombat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * mrapw (Discord 2026-10-18): "cox seems to be missing the botd given
 * thralls is selected?" The guide promised the book of the dead in the
 * trip cells; the code only added the resurrection runes. A thrall is
 * cast from the book with any weapon, so the book rides the utility
 * stacks (Supplies row, bank filter, setup layout) whenever the fold is on.
 */
class ThrallBookTest {
	private static final int BOOK_OF_THE_DEAD = 25818;
	private static final int BLOOD_RUNE = 565;
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

	private List<Integer> utilityIds(Monster mob, boolean thralls) {
		PageState state = new PageState();
		CaptureLink link = new CaptureLink();
		CommandEngine engine = new CommandEngine(data, state,
			(m, p, onDone) -> {
			},
			link);
		engine.setStoreOps(new TestStoreOps());
		engine.setOwnedCheck(id -> true);
		engine.setMagicLevel(99);
		engine.execute("select", Map.of("id", mob.getId()));
		state.setParam("thralls", thralls);
		engine.onResults(mob, Map.of());
		try {
			javax.swing.SwingUtilities.invokeAndWait(() -> {
			});
		} catch (Exception ex) {
			throw new AssertionError(ex);
		}
		Map<?, ?> entry = (Map<?, ?>) ((List<?>) link.published.get("entries")).get(0);
		Map<?, ?> mobNode = (Map<?, ?>) ((List<?>) entry.get("mobs")).get(0);
		List<Integer> ids = new ArrayList<>();
		Object runes = mobNode.get("utilityRunes");
		for (Object rune : runes instanceof List ? (List<?>) runes : List.of()) {
			ids.add(((Number) ((Map<?, ?>) rune).get("id")).intValue());
		}
		return ids;
	}

	private static Monster land() {
		return data.searchMonsters("general graardor", 1).get(0);
	}

	@Test
	@DisplayName("thralls on: the Book of the Dead rides the utility stacks once, beside the runes")
	void bookRidesWithThralls() {
		List<Integer> ids = utilityIds(land(), true);
		assertTrue(ids.contains(BLOOD_RUNE), "the fixture must have the thrall fold live: " + ids);
		assertEquals(1, Collections.frequency(ids, BOOK_OF_THE_DEAD), "one book: " + ids);
	}

	@Test
	@DisplayName("thralls off: no book")
	void noBookWithoutThralls() {
		assertFalse(utilityIds(land(), false).contains(BOOK_OF_THE_DEAD));
	}

	@Test
	@DisplayName("a sea mob never carries the book - thralls cannot be cast on a boat")
	void noBookAtSea() {
		Monster sea = data.getMonsters().stream()
			.filter(m -> NavalCombat.isNaval(m.getName())).findFirst().orElseThrow(AssertionError::new);
		assertFalse(utilityIds(sea, true).contains(BOOK_OF_THE_DEAD));
	}
}
