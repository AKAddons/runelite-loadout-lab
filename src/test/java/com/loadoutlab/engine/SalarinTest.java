package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterNotes;
import com.loadoutlab.data.MonsterStats;
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
class SalarinTest
{
	private static LoadoutData data;
	private static MonsterStats salarin;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		salarin = data.searchMonsters("salarin", 1).get(0);
		assertEquals(304, salarin.getId());
	}

	private static OptimizationRequest req(CombatStyle style)
	{
		return TestRequests.of(salarin, style, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, 1);
	}

	@Test
	@DisplayName("melee and ranged never land")
	void meleeAndRangedAreImmune()
	{
		assertTrue(new LoadoutOptimizer().optimize(data, req(CombatStyle.MELEE)).isEmpty());
		assertTrue(new LoadoutOptimizer().optimize(data, req(CombatStyle.RANGED)).isEmpty());
	}

	@Test
	@DisplayName("the magic answer is a Strike at a flat 12, gear be damned")
	void magicIsAFlatStrike()
	{
		List<DpsResult> out = new LoadoutOptimizer().optimize(data, req(CombatStyle.MAGIC));
		assertFalse(out.isEmpty(), "strikes must survive the elemental prune");
		DpsResult best = out.get(0);
		assertNotNull(best.getSpellName());
		assertTrue(best.getSpellName().endsWith("Strike"),
			"picked: " + best.getSpellName());
		assertEquals(12, best.getMaxHit(), "flat 12 at maxed magic");
		assertEquals(12.0, best.getExpectedHit(), 1e-9, "guaranteed, no roll");
		assertNotNull(MonsterNotes.noteFor(salarin));
	}
}
