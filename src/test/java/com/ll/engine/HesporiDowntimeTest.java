package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hespori's flower rounds as a fixed downtime (ISS-01M672QT7BP4H2C04C0872H57N,
 * Andrew 2026-10-30: "the hespori kill time doesn't reflect the rounds of
 * plants you have to kill").
 */
class HesporiDowntimeTest {
	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	@Test
	@DisplayName("Hespori adds 18 s of flower rounds to every kill, keeps full uptime, and still prices its damage taken")
	void flowerRounds() {
		Monster hespori = data.searchMonsters("hespori", 1).get(0);
		assertEquals(18, BossIncomingOverrides.of(hespori).downtime, 1e-9);
		assertTrue(BossIncomingOverrides.of(hespori).downtimeNote.contains("flower"));
		assertEquals(1, BossIncomingOverrides.of(hespori).uptime, 1e-9);
		assertFalse(BossIncomingOverrides.heals(hespori));
		Incoming.Result in = Incoming.calculate(hespori, new Kit(Map.of()), 85, 89, 0);
		assertTrue(in.tot > 0, "the corpus styles still price Hespori: " + in.tot);
		Monster graardor = data.searchMonsters("general graardor", 1).get(0);
		assertEquals(0, BossIncomingOverrides.of(graardor).downtime, 1e-9);
	}
}
