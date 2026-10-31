package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.LoadoutData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Obligator's weapon category after the October gear refresh
 * (ISS-01M66SMVQGFPX82Z48CBJZD0Z5): weirdgloop moved it from Blunt to the
 * wiki's Extending Chain Mace, a crush-only category the styles table
 * had never heard of, so it fell to the nine-row fallback.
 */
class ObligatorStylesTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	@Test
	@DisplayName("the Obligator's Extending Chain Mace category offers crush accurate and aggressive, not the fallback")
	void crushOnly() {
		Gear obligator = data.getGear(34715);
		assertNotNull(obligator, "Obligator (Charged)");
		assertEquals("Extending Chain Mace", obligator.category);
		var styles = WeaponStyles.melee(obligator);
		assertEquals(2, styles.size(), styles.toString());
		for (WeaponStyles.MeleeStyle style : styles) assertEquals("crush", style.attackType);
	}
}
