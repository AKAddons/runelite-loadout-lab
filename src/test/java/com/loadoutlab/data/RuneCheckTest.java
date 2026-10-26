package com.loadoutlab.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Only runes go in the rune pouch (ISS-01M5VH8H5MBRK0BFA6B8E68Z7T, Andrew 2026-10-26: "the seed box is
 * showing as rune in a rune pouch"): the utility list also carries loot
 * containers and slayer items.
 */
class RuneCheckTest {
	@Test
	@DisplayName("fire, soul, sunfire and combination runes are runes")
	void runes() {
		assertTrue(SpellRunes.isRune(554), "fire rune");
		assertTrue(SpellRunes.isRune(566), "soul rune");
		assertTrue(SpellRunes.isRune(SpellRunes.SUNFIRE_RUNE), "sunfire rune");
		assertTrue(SpellRunes.isRune(SpellRunes.comboRuneIds()[0]), "a combination rune");
	}

	@Test
	@DisplayName("a seed box, herb sack, bonecrusher or Book of the Dead is not")
	void notRunes() {
		for (int id : new int[]{13639, 13226, 13116, 25818}) assertFalse(SpellRunes.isRune(id), "id " + id);
	}
}
