package com.loadoutlab.engine;

import com.loadoutlab.data.SpellRunes;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field report 2026-08-27 (Not on Hand's F2P test account): the plugin
 * recommended Death Charge - members, Magic 90, A Kingdom Divided, a
 * blood + death + soul rune per cast - and autocast spells whose runes the
 * bank does not hold.
 *
 * <p>The gate checks only the premium combat runes (blood, soul, death,
 * wrath). Elemental and mind/chaos-class runes are deliberately ungated -
 * any shop sells them, and requiring every air rune in the bank would be
 * noise, not honesty.
 */
class PremiumRuneGateTest
{
	private static final int BLOOD = 565;
	private static final int DEATH = 560;
	private static final int SOUL = 566;
	private static final int WRATH = 21880;

	private static OwnedItems bank(int... ids)
	{
		Map<Integer, Integer> owned = new HashMap<>();
		for (int id : ids)
		{
			owned.put(id, 1000);
		}
		return new OwnedItems(owned, true);
	}

	@Test
	@DisplayName("a runeless bank blocks blood, death, soul and wrath spells")
	void runelessBankBlocksPremiumSpells()
	{
		OwnedItems empty = bank();
		assertFalse(SpellRunes.premiumRunesOwned("Fire Wave", empty), "Fire Wave costs a blood rune");
		assertFalse(SpellRunes.premiumRunesOwned("Ice Barrage", empty), "Ice Barrage costs blood + death");
		assertFalse(SpellRunes.premiumRunesOwned("Fire Surge", empty), "Fire Surge costs a wrath rune");
		assertFalse(SpellRunes.premiumRunesOwned("Death Charge", empty), "Death Charge costs blood + death + soul");
	}

	@Test
	@DisplayName("cheap-rune spells always pass - shops sell their runes")
	void cheapSpellsAlwaysPass()
	{
		OwnedItems empty = bank();
		assertTrue(SpellRunes.premiumRunesOwned("Fire Strike", empty));
		assertTrue(SpellRunes.premiumRunesOwned("Fire Bolt", empty));
	}

	@Test
	@DisplayName("owning exactly the needed premium runes unlocks the spell")
	void owningTheRunesUnlocks()
	{
		assertTrue(SpellRunes.premiumRunesOwned("Fire Wave", bank(BLOOD)));
		assertFalse(SpellRunes.premiumRunesOwned("Ice Barrage", bank(BLOOD)),
			"Ice Barrage also needs death runes");
		assertTrue(SpellRunes.premiumRunesOwned("Ice Barrage", bank(BLOOD, DEATH)));
		assertTrue(SpellRunes.premiumRunesOwned("Fire Surge", bank(WRATH)));
		assertTrue(SpellRunes.premiumRunesOwned("Death Charge", bank(BLOOD, DEATH, SOUL)));
	}

	@Test
	@DisplayName("unknown spells and a null bank fail open")
	void failsOpen()
	{
		assertTrue(SpellRunes.premiumRunesOwned("Some Future Spell", bank()));
		assertTrue(SpellRunes.premiumRunesOwned("Fire Wave", null));
		assertTrue(SpellRunes.premiumRunesOwned(null, bank()));
	}
}
