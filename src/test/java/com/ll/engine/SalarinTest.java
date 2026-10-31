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
 * Salarin the twisted (wiki): "can only be damaged by Strike spells,
 * ring of recoil damage, and dynamite(p)" - a flat 9-12 by highest
 * strike unlocked, unaffected by gear. The one mob where optimizing
 * equipment is meaningless, which the answer must SAY rather than
 * pretend otherwise.
 */
class SalarinTest {
	private static Tables data;
	private static Monster salarin;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		salarin = data.searchMonsters("salarin", 1).get(0);
		assertEquals(304, salarin.getId());
	}

	private static Req req(Style style) {
		return TestRequests.of(salarin, style, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, Owned.EMPTY, 1);
	}

	@Test
	@DisplayName("melee and ranged never land")
	void meleeAndRangedAreImmune() {
		assertTrue(new LoadoutOptimizer().optimize(data, req(Style.MELEE)).isEmpty());
		assertTrue(new LoadoutOptimizer().optimize(data, req(Style.RANGED)).isEmpty());
	}

	@Test
	@DisplayName("the magic answer is a Strike at a flat 12, plus the elemental amulet's 2")
	void magicIsAFlatStrike() {
		List<Dps> out = new LoadoutOptimizer().optimize(data, req(Style.MAGIC));
		assertFalse(out.isEmpty(), "strikes must survive the elemental prune");
		Dps best = out.get(0);
		assertNotNull(best.getSpellName());
		assertTrue(best.getSpellName().endsWith("Strike"),
			"picked: " + best.getSpellName());
		// Wiki: "The sole exception is the Elemental amulet, which increases
		// the player's max hit by 2." The ceiling wears it since the pool
		// stopped merging it into the amulet of magic (2026-09-27); before,
		// this read a flat 12 only because the amulet never reached the beam.
		assertEquals("elemental amulet",
			best.getLoadout().get(com.ll.data.Slot.NECK).getLname());
		assertEquals(14, best.getMaxHit(), "flat 12 at maxed magic, +2 from the amulet");
		assertEquals(14.0, best.getEh(), 1e-9, "guaranteed, no roll");
	}
}
