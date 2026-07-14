package com.loadoutlab.ui;

import com.loadoutlab.data.GearSlot;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The classic (in-game worn-equipment tab) gear layout is pure positional
 * data (LoadoutLabPanel.CLASSIC_ORDER). These lock the arrangement the user
 * asked for so a slot can't silently go missing or land in the wrong cell.
 */
class ClassicLayoutTest
{
	@Test
	@DisplayName("classic layout is a 5x3 grid (15 cells)")
	void isFiveByThree()
	{
		assertEquals(15, LoadoutLabPanel.CLASSIC_ORDER.length,
			"5 rows of 3 columns");
	}

	@Test
	@DisplayName("the spec cell sits in the empty slot directly left of the legs")
	void specSitsLeftOfLegs()
	{
		assertEquals(9, LoadoutLabPanel.CLASSIC_SPEC_INDEX,
			"row 4, first column");
		assertNull(LoadoutLabPanel.CLASSIC_ORDER[LoadoutLabPanel.CLASSIC_SPEC_INDEX],
			"the spec cell takes a structurally-empty corner - it must not displace a real slot");
		assertEquals(GearSlot.LEGS, LoadoutLabPanel.CLASSIC_ORDER[LoadoutLabPanel.CLASSIC_SPEC_INDEX + 1],
			"legs sit immediately to the spec cell's right");
	}

	@Test
	@DisplayName("head is centred on the top row, corners blank")
	void topRowMatchesEquipmentTab()
	{
		assertNull(LoadoutLabPanel.CLASSIC_ORDER[0], "top-left blank");
		assertEquals(GearSlot.HEAD, LoadoutLabPanel.CLASSIC_ORDER[1], "head centred");
		assertNull(LoadoutLabPanel.CLASSIC_ORDER[2], "top-right blank");
		assertNull(LoadoutLabPanel.CLASSIC_ORDER[11], "the last blank corner (right of legs)");
	}

	@Test
	@DisplayName("every equipment slot appears exactly once - same coverage as the compact grid")
	void coversEverySlotOnce()
	{
		Set<GearSlot> classic = EnumSet.noneOf(GearSlot.class);
		int slotCount = 0;
		for (GearSlot slot : LoadoutLabPanel.CLASSIC_ORDER)
		{
			if (slot == null)
			{
				continue;
			}
			slotCount++;
			assertTrue(classic.add(slot), "duplicate slot in classic layout: " + slot);
		}
		assertEquals(LoadoutLabPanel.GRID_ORDER.length, slotCount,
			"classic layout renders the same number of slots as the compact grid");
		assertEquals(EnumSet.copyOf(Arrays.asList(LoadoutLabPanel.GRID_ORDER)), classic,
			"classic layout must show exactly the slots the compact grid shows");
	}
}
