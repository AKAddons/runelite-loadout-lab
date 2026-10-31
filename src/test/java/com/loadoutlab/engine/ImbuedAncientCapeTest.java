package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The imbued ancient cape (Pete Kayer's Challenges, 2026-10-07) after the
 * October gear refresh (ISS-01M66SMVQGFPX82Z48CBJZD0Z5, Elling 2026-10-30:
 * "it doesnt read my Imbued god cape").
 */
class ImbuedAncientCapeTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	@Test
	@DisplayName("the imbued ancient cape is a Zarosian magic cape in the corpus, with its max cape combo and Perdu's fee")
	void inTheCorpus() {
		Gear cape = MaxCapeSims.cape(data, "imbued ancient cape");
		assertNotNull(cape, "imbued ancient cape (Normal) in the cape slot");
		assertEquals(GearSlot.CAPE, cape.slot);
		assertEquals(15, cape.offensive.magic);
		assertEquals(20, cape.bonuses.magicDamage);
		assertTrue(GwdProtection.godsOf(cape).contains("zaros"), GwdProtection.godsOf(cape).toString());
		assertEquals("imbued ancient max cape", MaxCapeSims.COMBOS.get("imbued ancient cape"));
		assertNotNull(MaxCapeSims.cape(data, "imbued ancient max cape"), "imbued ancient max cape in the corpus");
		assertEquals(96000L, UntradeableDeathCosts.costFor(cape));
	}
}
