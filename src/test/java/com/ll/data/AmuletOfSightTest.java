package com.ll.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The amulet of sight (The Writing's on the Wall, 3 November 2026) enters the corpus on
 * release day through the wiki intake (ISS-01M6HTWB6MJHHQKN0XSFDGQMHX, Andrew 2026-11-03:
 * "a new item, amulet of sight came out today can we make sure we are are ingesting it").
 */
class AmuletOfSightTest {
    private static final int AMULET_OF_SIGHT = 34762;   // gameval ItemID.AMULET_OF_SIGHT
    private static final int AMULET_OF_ACCURACY = 1478;

    @Test
    @DisplayName("the amulet of sight is a priced, standard +10 ranged attack neck that out-aims the amulet of accuracy")
    void amuletOfSight() {
        Tables data = new DataService().load();
        Gear amulet = data.getGear(AMULET_OF_SIGHT);
        assertNotNull(amulet, "ItemID.AMULET_OF_SIGHT 34762 is in the corpus");
        assertEquals(Slot.NECK, amulet.slot);
        assertEquals(10, amulet.offensive.ranged);
        assertEquals(0, amulet.offensive.stab);
        assertEquals(0, amulet.offensive.magic);
        assertTrue(amulet.standardGear, "standard gear");
        assertTrue(amulet.tradeable, "tradeable");
        assertTrue(amulet.getPriceOrZero() > 0, "priced");
        assertFalse(data.isVariant(AMULET_OF_SIGHT));
        assertTrue(amulet.offensive.ranged > data.getGear(AMULET_OF_ACCURACY).offensive.ranged);
    }
}
