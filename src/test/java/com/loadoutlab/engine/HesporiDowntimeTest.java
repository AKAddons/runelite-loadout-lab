package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
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
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	@Test
	@DisplayName("Hespori adds 18 s of flower rounds to every kill, keeps full uptime, and still prices its damage taken")
	void flowerRounds() {
		MonsterStats hespori = data.searchMonsters("hespori", 1).get(0);
		assertEquals(18, BossIncomingOverrides.of(hespori).downtime, 1e-9);
		assertTrue(BossIncomingOverrides.of(hespori).downtimeNote.contains("flower"));
		assertEquals(1, BossIncomingOverrides.of(hespori).uptime, 1e-9);
		assertFalse(BossIncomingOverrides.heals(hespori));
		IncomingDpsCalculator.Result in = IncomingDpsCalculator.calculate(hespori, new Loadout(Map.of()), 85, 89, 0);
		assertTrue(in.totalDps > 0, "the corpus styles still price Hespori: " + in.totalDps);
		MonsterStats graardor = data.searchMonsters("general graardor", 1).get(0);
		assertEquals(0, BossIncomingOverrides.of(graardor).downtime, 1e-9);
	}
}
