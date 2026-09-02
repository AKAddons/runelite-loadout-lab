package com.loadoutlab.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Designer pass 2026-09-02: the sailing line was clipped to "~1.0 (max
 * 5:" - the stat column fits about 12 characters, the width of "Ranged
 * rapid". The ship line is as terse as the land incoming line. */
class ShipStatLineTest
{
	@Test
	@DisplayName("the ship damage line fits the stat column")
	void fitsTheColumn()
	{
		assertEquals("~1.0 max 5", ResultCards.shipDamageText(1.04, 5));
		assertEquals("no damage", ResultCards.shipDamageText(0, 0));
		assertTrue(ResultCards.shipDamageText(12.3, 40).length() <= 12);
	}
}
