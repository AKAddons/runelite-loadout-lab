package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Moons of Peril (ISS-01M67SZDHM3YPB3MTEAHJK5ASS, Andrew 2026-10-30: "suggesting
 * dragon warhammer on moons of peril and i dont' think it works on those bosse"; wiki
 * Strategies: "Defence-reducing special attacks and Death Charge have no effect on
 * the Moons of Peril").
 */
class MoonsDeathChargeTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	@Test
	@DisplayName("a Moon computes with Death Charge off whatever the param says, on the single and roster paths, and lists no Death Charge runes; Vardorvis keeps it")
	void moonsDropDeathCharge() {
		AtomicInteger charge = new AtomicInteger(-1);
		CommandEngine engine = new CommandEngine(data, new PageState(),
			(mob, p, onDone) -> charge.set(p.deathCharge), new CompanionLink());
		engine.setRosterCompute((mobs, p, onDone) -> charge.set(p.deathCharge));
		engine.setStoreOps(new TestStoreOps());
		assertTrue(engine.execute("set-param", Map.of("param", "deathCharge", "value", 1)));
		for (String moon : List.of("blood moon", "blue moon", "eclipse moon")) {
			assertTrue(engine.execute("select", Map.of("query", moon)));
			assertEquals(0, charge.get(), moon);
			for (Map rune : engine.utilityRunesFor(Map.of("name", data.searchMonsters(moon, 1).get(0).name))) {
				assertFalse(String.valueOf(rune.get("why")).contains("Death Charge"), moon + ": " + rune);
			}
		}
		assertTrue(engine.execute("select", Map.of("query", "vardorvis")));
		assertEquals(1, charge.get(), "Vardorvis: the param stands");
	}
}
