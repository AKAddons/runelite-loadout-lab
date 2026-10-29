package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A boss's heal off the player's gear (ISS-01M6415DTE84ZZ45C4YD3MZRBJ,
 * Andrew 2026-10-29: "1. A first" - gear-coupled heals): Blood Moon's
 * chained three-hit stack heals her 1x, 2x and 3x (clamped 30-50) of what
 * lands, so her heal rate is 2p + 8p^2 + 36.8p^3 per 3.6 s at land chance p
 * (scratchpad blood_moon_ttk.py, wiki Blood_Moon).
 */
class HealRuleTest {
	private static LoadoutData data;
	private static MonsterStats moon;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		moon = data.searchMonsters("blood moon", 1).get(0);
	}

	private static Loadout wearing(String... names) {
		Map<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		for (String name : names) {
			for (GearSlot slot : GearSlot.values()) {
				for (GearItem g : data.getGearItems(slot)) {
					if (g.nameLower.equals(name)) gear.put(g.slot, g);
				}
			}
		}
		return new Loadout(gear);
	}

	/** Her land chance against the set at Defence 123 (99 under Piety). */
	private static double landChance(Loadout set) {
		return IncomingDpsCalculator.accuracy(IncomingDpsCalculator.npcRoll(moon.offence.attackLevel, moon.offence.attackBonus),
			(123 + 8L) * (set.defensive.slash + 64));
	}

	private static double thirdHeal() {
		double sum = 0;
		for (int d = 0; d <= 20; d++) sum += Math.min(50, Math.max(30, 3 * d));
		return sum / 21;
	}

	@Test
	@DisplayName("Blood Moon's heal rate is 2p + 8p^2 + third x p^3 per 3.6 s, and tank armour cuts it")
	void bloodMoonHeals() {
		Loadout bandos = wearing("bandos chestplate", "bandos tassets", "dragon defender");
		Loadout naked = new Loadout(new EnumMap<>(GearSlot.class));
		for (Loadout set : new Loadout[]{bandos, naked}) {
			double p = landChance(set);
			double expected = (2 * p + 8 * p * p + thirdHeal() * p * p * p) / 3.6;
			IncomingDpsCalculator.Result r = IncomingDpsCalculator.calculate(moon, set, 123, 99, 0);
			assertEquals(expected, r.healDps, 1e-9, "heal/s at p=" + p);
			assertEquals(IncomingDpsCalculator.CURATED, r.coverage, "the curated three-hit stack prices her");
		}
		assertTrue(IncomingDpsCalculator.calculate(moon, bandos, 123, 99, 0).healDps
			< IncomingDpsCalculator.calculate(moon, naked, 123, 99, 0).healDps);
	}

	@Test
	@DisplayName("a chained stack's third hit lands at p^3: its row rates at p^3 x 10 per 3.6 s, and none of the three is prayable")
	void chainedHits() {
		Loadout naked = new Loadout(new EnumMap<>(GearSlot.class));
		double p = landChance(naked);
		IncomingDpsCalculator.Result r = IncomingDpsCalculator.calculate(moon, naked, 123, 99, 0);
		assertEquals(3, r.threats.size(), r.threats.toString());
		assertEquals(p * 4 / 2 / 3.6, r.threats.get(0).dps, 1e-9);
		assertEquals(p * p * 8 / 2 / 3.6, r.threats.get(1).dps, 1e-9);
		assertEquals(p * p * p * 20 / 2 / 3.6, r.threats.get(2).dps, 1e-9);
		assertNull(r.protectPrayer, "no protection prayer helps at the Moons");
		assertEquals(r.unprayedDps, r.totalDps, 1e-9);
	}

	@Test
	@DisplayName("a boss with no heal rule heals nothing, and its numbers are what they were")
	void noHealRule() {
		MonsterStats graardor = data.searchMonsters("general graardor", 1).get(0);
		IncomingDpsCalculator.Result r = IncomingDpsCalculator.calculate(graardor, wearing("bandos chestplate"), 99, 99, 0);
		assertEquals(0, r.healDps, 0);
		assertTrue(r.totalDps > 0);
	}
}
