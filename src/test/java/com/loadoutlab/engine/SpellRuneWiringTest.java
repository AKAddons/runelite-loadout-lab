package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellRunes;
import com.loadoutlab.data.SpellStats;
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
class SpellRuneWiringTest
{
	private static LoadoutData data;
	private static MonsterStats zulrah;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		zulrah = data.searchMonsters("zulrah", 1).get(0);
	}

	private static OptimizationRequest request(CandidateMode mode, OwnedItems owned)
	{
		return new OptimizationRequest(
			zulrah, CombatStyle.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.NONE, null, 0,
			mode, true, false,
			owned, RequirementProfile.MAXED, 1);
	}

	@Test
	@DisplayName("a runeless bank's own spell pool holds no premium-rune spell")
	void ownedPoolDropsPremiumSpells()
	{
		List<SpellStats> pool = LoadoutOptimizer.spellsFor(data,
			request(CandidateMode.OWNED_ONLY, OwnedItems.EMPTY));
		assertFalse(pool.isEmpty(), "the pool should still hold shop-rune spells");
		for (SpellStats spell : pool)
		{
			assertTrue(SpellRunes.premiumRunesOwned(spell.getName(), OwnedItems.EMPTY),
				spell.getName() + " needs premium runes the bank does not hold");
		}
	}

	@Test
	@DisplayName("owning the runes restores the barrage-class spells")
	void owningRunesRestoresThePool()
	{
		Map<Integer, Integer> runes = new HashMap<>();
		runes.put(565, 1000); // blood
		runes.put(560, 1000); // death
		runes.put(566, 1000); // soul
		runes.put(21880, 1000); // wrath
		List<SpellStats> pool = LoadoutOptimizer.spellsFor(data,
			request(CandidateMode.OWNED_ONLY, new OwnedItems(runes, true)));
		boolean premium = false;
		for (SpellStats spell : pool)
		{
			premium |= !SpellRunes.premiumRunesOwned(spell.getName(), OwnedItems.EMPTY);
		}
		assertTrue(premium, "with the runes banked, premium spells should be back");
	}

	@Test
	@DisplayName("the ceiling keeps every spell - it prices the game, not the bank")
	void ceilingIsUngated()
	{
		List<SpellStats> pool = LoadoutOptimizer.spellsFor(data,
			request(CandidateMode.ALL_STANDARD, OwnedItems.EMPTY));
		boolean premium = false;
		for (SpellStats spell : pool)
		{
			premium |= !SpellRunes.premiumRunesOwned(spell.getName(), OwnedItems.EMPTY);
		}
		assertTrue(premium, "the BiS side must not be rune-gated");
	}
}
