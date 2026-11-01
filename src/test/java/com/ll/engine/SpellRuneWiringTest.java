package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.data.SpellRunes;
import com.ll.data.Spell;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wiring net for the premium-rune spell gate: PremiumRuneGateTest proves the
 * predicate, this proves the optimizer's pool actually consults it - on the
 * OWNED side only, and before the mob whitelists so a nibbler lock cannot
 * resurrect a spell the bank cannot pay for.
 */
class SpellRuneWiringTest {
	private static Tables data;
	private static Monster zulrah;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		zulrah = data.searchMonsters("zulrah", 1).get(0);
	}

	private static Req request(CandidateMode mode, Owned owned) {
		return new Req(
			zulrah, Style.MAGIC, Levels.MAXED,
			PrayerBonuses.NONE, null, 0,
			mode, true, false,
			owned, RequirementProfile.MAXED, 1);
	}

	@Test
	@DisplayName("a runeless bank's own spell pool holds no premium-rune spell")
	void ownedPoolDropsPremiumSpells() {
		List<Spell> pool = LoadoutOptimizer.spellsFor(data,
			request(CandidateMode.OWNED_ONLY, Owned.EMPTY));
		assertFalse(pool.isEmpty(), "the pool should still hold shop-rune spells");
		for (Spell spell : pool) {
			assertTrue(SpellRunes.premiumRunesOwned(spell.getName(), Owned.EMPTY),
				spell.getName() + " needs premium runes the bank does not hold");
		}
	}

	@Test
	@DisplayName("owning the runes restores the barrage-class spells")
	void owningRunesRestoresThePool() {
		Map<Integer, Integer> runes = new HashMap<>();
		runes.put(565, 1000); // blood
		runes.put(560, 1000); // death
		runes.put(566, 1000); // soul
		runes.put(21880, 1000); // wrath
		List<Spell> pool = LoadoutOptimizer.spellsFor(data,
			request(CandidateMode.OWNED_ONLY, new Owned(runes, true)));
		boolean premium = false;
		for (Spell spell : pool) {
			premium |= !SpellRunes.premiumRunesOwned(spell.getName(), Owned.EMPTY);
		}
		assertTrue(premium, "with the runes banked, premium spells should be back");
	}

	@Test
	@DisplayName("the ceiling keeps every spell - it prices the game, not the bank")
	void ceilingIsUngated() {
		List<Spell> pool = LoadoutOptimizer.spellsFor(data,
			request(CandidateMode.ALL_STANDARD, Owned.EMPTY));
		boolean premium = false;
		for (Spell spell : pool) {
			premium |= !SpellRunes.premiumRunesOwned(spell.getName(), Owned.EMPTY);
		}
		assertTrue(premium, "the BiS side must not be rune-gated");
	}
}
