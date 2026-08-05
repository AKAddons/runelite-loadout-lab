package com.loadoutlab.ui;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The assumed-boost inventory chip resolves the potion id from the boost
 * label by substring, and every "Divine X" label CONTAINS its base label -
 * so the divine branches must be checked first or they are dead code and
 * the chip silently shows the REGULAR potion (field report 2026-08-05).
 */
class BoostConsumablesTest
{
	private static Set<Integer> idsFor(String label)
	{
		Set<Integer> ids = new LinkedHashSet<>();
		LoadoutLabPanel.addBoostConsumables(label, ids);
		return ids;
	}

	@Test
	@DisplayName("a divine assumption shows the divine potion, not its base")
	void divineLabelsResolveToDivineIds()
	{
		assertEquals(Set.of(23685), idsFor("Piety + Divine super combat"),
			"Divine super combat must chip 23685, not the regular 12695");
		assertEquals(Set.of(23733), idsFor("Rigour + Divine ranging potion"));
		assertEquals(Set.of(23745), idsFor("Augury + Divine magic potion"));
	}

	@Test
	@DisplayName("the base potions still resolve to themselves")
	void baseLabelsUnchanged()
	{
		assertEquals(Set.of(12695), idsFor("Piety + Super combat"));
		assertEquals(Set.of(2444), idsFor("Eagle Eye + Ranging potion"));
		assertEquals(Set.of(3040), idsFor("Mystic Might + Magic potion"));
	}

	@Test
	@DisplayName("every chipped id carries a name for its tooltip")
	void everyIdIsNamed()
	{
		// The sprite alone cannot carry the divine/regular distinction at
		// 24px - the tooltip names the potion, so the name table must
		// cover every id the chain can add.
		for (int id : new int[]{20996, 20992, 23685, 12695, 2428, 113, 11722,
			23733, 2444, 11726, 23745, 3040, 27641, 20724, 2452})
		{
			assertTrue(LoadoutLabPanel.CONSUMABLE_NAMES.containsKey(id),
				"unnamed consumable id " + id);
		}
	}
}
