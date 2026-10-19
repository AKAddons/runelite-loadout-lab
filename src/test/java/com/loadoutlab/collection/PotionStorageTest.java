package com.loadoutlab.collection;

import com.loadoutlab.data.TripSupplies;
import com.loadoutlab.engine.DragonfireRules;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Andrew (client test 2026-10-19): his super antifires live in the bank's
 * potion storage, which is varbits, so the scan never saw them and the
 * dragonfire plate's Detect best said shield. The storage now counts as
 * owned, by the doses each varbit reports.
 */
class PotionStorageTest
{
	private static final int SUPER_ANTIFIRE_VARBIT = 11075;
	private static final int ANTIFIRE_VARBIT = 11040;
	private static final int SUPER_ANTIFIRE_4 = 21978;
	private static final int ANTIFIRE_4 = 2452;

	@Test
	@DisplayName("doses in storage become owned 4-dose potions; empty slots are absent")
	void dosesBecomeOwnedPotions()
	{
		Map<Integer, Integer> owned = PotionStorage.owned(varbit ->
			varbit == SUPER_ANTIFIRE_VARBIT ? 3 : varbit == ANTIFIRE_VARBIT ? 8 : 0);
		assertEquals(1, owned.get(SUPER_ANTIFIRE_4), "three doses count as one potion");
		assertEquals(2, owned.get(ANTIFIRE_4), "eight doses, two potions");
		assertEquals(2, owned.size(), "nothing else is stored: " + owned);
		assertTrue(PotionStorage.tracks(SUPER_ANTIFIRE_VARBIT));
		assertFalse(PotionStorage.tracks(11034), "agility potions are not a loadout input");
	}

	@Test
	@DisplayName("the table covers every potion a feature detects: antifire, supplies, boosts")
	void tableCoversTheDetectedPotions()
	{
		var items = PotionStorage.BY_VARBIT.values();
		for (int id : new int[]{DragonfireRules.SUPER_ANTIFIRE_IDS[0], DragonfireRules.SUPER_ANTIFIRE_IDS[4],
			DragonfireRules.REGULAR_ANTIFIRE_IDS[0], DragonfireRules.REGULAR_ANTIFIRE_IDS[4]})
		{
			assertTrue(items.contains(id), "antifire tier missing from potion_storage.json: " + id);
		}
		for (String category : new String[]{TripSupplies.PRAYER_RESTORE, TripSupplies.ANTIVENOM,
			TripSupplies.ANTIPOISON, TripSupplies.SURGE})
		{
			for (TripSupplies.Option option : TripSupplies.options(category))
			{
				if (option.detect && option.ids.length > 0 && !option.name.contains("Blighted"))
				{
					assertTrue(items.contains(option.ids[0]),
						category + " option missing from potion_storage.json: " + option.name);
				}
			}
		}
		for (int id : new int[]{12695, 23685, 2444, 23733, 3040, 23745, 22461, 24635, 22449, 24623})
		{
			assertTrue(items.contains(id), "boost missing from potion_storage.json: " + id);
		}
	}
}
