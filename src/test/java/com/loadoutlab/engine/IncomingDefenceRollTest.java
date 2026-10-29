package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.MonsterDefences;
import com.loadoutlab.data.MonsterOffence;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The player's defence roll (ISS-01M5G5CZC9SK46499FACRSJ8ZT): level =
 * Defence + 8 + stance for melee/ranged (wiki DPS/Melee), and
 * floor(0.7 Magic) + floor(0.3 Defence) + 8 against magic - the
 * official calculator's NPCVsPlayerCalc. The old Defence + 9 sat the +9
 * inside the 30% and read magic damage taken 4-7% high.
 */
class IncomingDefenceRollTest {
	private static final Loadout NAKED = new Loadout(new EnumMap<>(GearSlot.class));

	@Test
	@DisplayName("matches the official calculator's own Abyssal demon vectors")
	void abyssalDemonVectors() {
		MonsterStats demon = new DataService().load().searchMonsters("abyssal demon", 1).get(0);
		// Roll (97+9)*64 = 6784 vs 576 at Defence 1 and 6848 at Defence 99
		// (osrs-dps-calc DamageTaken.test.ts: 1.596 and 0.825 dps).
		assertEquals(1.5957, IncomingDpsCalculator.calculate(demon, NAKED, 1, 1, 0).unprayedDps, 0.0005);
		assertEquals(0.8254, IncomingDpsCalculator.calculate(demon, NAKED, 99, 1, 0).unprayedDps, 0.0005);
	}

	@Test
	@DisplayName("magic defence is 70% Magic plus 30% Defence plus 8, each floored")
	void magicDefenceLevel() {
		// Magic 99, Defence 70: 69 + 21 + 8 = 98 -> roll 98 * 64 = 6272.
		MonsterStats mage = new MonsterStats(1, "Test mage", "", 300, 255, 1, 1, 200, 0,
			MonsterDefences.ZERO, new MonsterOffence(1, 1, 1, 200, 0, 0, 0, 0, 0, 0, 4,
				List.of("Magic")),
			java.util.Collections.emptyList(), false, "", 0);
		// Npc roll (200+9)*64 = 13376 > 6272: acc = 1 - 6274/(2*13377).
		double acc = 1 - 6274.0 / (2 * 13377.0);
		int max = (int) (((long) 209 * 64 + 320) / 640);
		double expected = acc * (max / 2.0) / 2.4;
		assertEquals(expected, IncomingDpsCalculator.calculate(mage, NAKED, 70, 99, 0).unprayedDps, 1e-9);
	}
}
