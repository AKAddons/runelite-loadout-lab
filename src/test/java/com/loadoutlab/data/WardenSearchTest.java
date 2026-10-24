package com.loadoutlab.data;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Searching a Warden phase (ISS-01M5HKWBZNJF0V8ZD58FH9XS5B, Andrew
 * 2026-10-22: "its hard to do an individual search for the phase 2 version
 * of elidinis/tumeken ward without searching for toa").
 */
class WardenSearchTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static List<String> rows(String query) {
		return data.searchMonsters(query, 20).stream()
			.map(m -> m.getName() + " (" + m.getVersion() + ")")
			.collect(Collectors.toList());
	}

	@Test
	@DisplayName("'tumeken warden', 'wardens' and 'elidinis active' find their rows - each word starts a word")
	void wordMatching() {
		assertTrue(rows("tumeken warden").contains("Tumeken's Warden (Active)"), rows("tumeken warden").toString());
		assertTrue(rows("wardens").contains("Elidinis' Warden (Active)"), rows("wardens").toString());
		assertEquals(List.of("Elidinis' Warden (Active)"), rows("elidinis active"));
	}

	@Test
	@DisplayName("'warden p2' finds exactly the phase-2 rows of both Wardens; p3 the phase-3 ones")
	void phaseWords() {
		assertEquals(Set.of("Elidinis' Warden (Active)", "Elidinis' Warden (Core-ejected)",
			"Tumeken's Warden (Active)", "Tumeken's Warden (Core-ejected)"), Set.copyOf(rows("warden p2")));
		assertEquals(Set.of("Elidinis' Warden (Active)", "Elidinis' Warden (Core-ejected)"),
			Set.copyOf(rows("elidinis p2")));
		assertEquals(Set.of("Tumeken's Warden (Active)", "Tumeken's Warden (Core-ejected)"),
			Set.copyOf(rows("tumeken phase 2")));
		List<String> p3 = rows("warden p3");
		assertTrue(p3.contains("Elidinis' Warden (Damaged)") && p3.contains("Tumeken's Warden (Enraged)"), p3.toString());
		assertTrue(p3.stream().noneMatch(r -> r.contains("Active")), p3.toString());
	}

	@Test
	@DisplayName("nothing found before is lost: 'core' still finds the Manticore, 'kril' still K'ril first")
	void noRegressions() {
		assertTrue(rows("core").contains("Manticore ()"), rows("core").toString());
		assertTrue(rows("kril").get(0).startsWith("K'ril Tsutsaroth"), rows("kril").toString());
		assertEquals(rows("elidinis").subList(0, 2), rows("elidinis").subList(0, 2), "stable order");
	}
}
