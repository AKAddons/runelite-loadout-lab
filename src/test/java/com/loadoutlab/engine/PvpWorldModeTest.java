package com.loadoutlab.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The client's world type picks the PvP mode: 0 off, 1 PvP, 2 High Risk. */
class PvpWorldModeTest
{
	@Test
	@DisplayName("a normal world is off, a PvP world is PvP, a High Risk world is High Risk")
	void worldTypeMapsToMode()
	{
		assertEquals(0, PvpRisk.worldMode(false, false));
		assertEquals(1, PvpRisk.worldMode(true, false));
		assertEquals(2, PvpRisk.worldMode(true, true));
		assertEquals(2, PvpRisk.worldMode(false, true), "High Risk alone still keeps nothing");
	}

	@Test
	@DisplayName("kept slots: 3, 4 with Protect Item, none on High Risk")
	void keptSlotsPerMode()
	{
		assertEquals(3, PvpRisk.keptSlots(1, false));
		assertEquals(4, PvpRisk.keptSlots(1, true));
		assertEquals(0, PvpRisk.keptSlots(2, true));
		assertEquals(-1, PvpRisk.keptSlots(0, true), "off is unconstrained");
	}
}
