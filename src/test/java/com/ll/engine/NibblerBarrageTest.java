package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The nibblers' role is being barraged: three per wave, one AoE cast
 * clears the set. The magic answer is LOCKED to Ice/Blood Barrage, and
 * the trio multiplier (3x - one cast hits all three) is what makes the
 * barrage win the row on honest math instead of a blowpipe headlining
 * (field decision 2026-08-06).
 */
class NibblerBarrageTest {
	private static Tables data;
	private static Monster nibbler;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		nibbler = data.searchMonsters("jal-nib", 1).get(0);
		assertEquals(7691, nibbler.getId());
	}

	private static Req req(Style style) {
		return TestRequests.of(nibbler, style, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, Owned.EMPTY, 1);
	}

	@Test
	@DisplayName("the magic autocast is locked to the barrages")
	void magicLockedToBarrages() {
		List<Dps> out = new LoadoutOptimizer().optimize(data, req(Style.MAGIC));
		assertFalse(out.isEmpty());
		String spell = out.get(0).getSpellName();
		assertNotNull(spell);
		assertTrue("Ice Barrage".equals(spell) || "Blood Barrage".equals(spell),
			"nibblers are a barrage target, picked: " + spell);
	}

	@Test
	@DisplayName("the trio multiplier makes the barrage the row's best answer")
	void barrageWinsTheRow() {
		Dps magic = new LoadoutOptimizer().optimize(data, req(Style.MAGIC)).get(0);
		Dps ranged = new LoadoutOptimizer().optimize(data, req(Style.RANGED)).get(0);
		assertTrue(magic.getDps() > ranged.getDps(),
			"one cast hits all three - the barrage must out-rank single-target ranged: "
				+ magic.getDps() + " vs " + ranged.getDps());
		assertTrue(magic.getCountedBonuses().stream().anyMatch(b -> b.contains("trio")),
			"the 3x must be a COUNTED assumption, visible in the assurance line");
	}
}
