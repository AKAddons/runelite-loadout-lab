package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Tormented Demons' 20% reduction and its bypass classes (wiki:
 * "reduces damage done by non-demonbane weapons/spells and non-abyssal
 * weapons by 20%"). Pinned after the 2026-08-06 simplify pass moved the
 * demonbane vocabulary onto Gear - the restructure must not change
 * who bypasses. Verified 2026-08-06: the Purging staff is an AUTOCAST
 * demonbane-spell staff, so on the magic side the SPELL governs - a
 * demonbane cast bypasses, a plain elemental cast through any staff
 * does not.
 */
class TormentedDemonRulesTest {
	private static LoadoutData data;
	private static Monster demon;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		demon = data.searchMonsters("tormented demon", 1).get(0);
		assertTrue(TormentedDemonRules.applies(demon), "test premise: the TD row");
	}

	private static Gear byName(String nameLower) {
		for (Gear item : data.getGearItems()) {
			if (item.getNameLower().equals(nameLower)) {
				return item;
			}
		}
		throw new AssertionError("corpus is missing: " + nameLower);
	}

	private static double factor(Style style, String weapon, String spellName) {
		return TormentedDemonRules.damageFactor(demon, style,
			weapon == null ? null : byName(weapon), spellName);
	}

	@Test
	@DisplayName("demonbane and abyssal melee bypass the reduction; plain melee pays it")
	void meleeBypasses() {
		assertEquals(1.0, factor(Style.MELEE, "arclight", null), 1e-9);
		assertEquals(1.0, factor(Style.MELEE, "burning claws", null), 1e-9,
			"the claws are demonbane - the pre-restructure list carried them");
		assertEquals(1.0, factor(Style.MELEE, "abyssal whip", null), 1e-9);
		assertEquals(0.8, factor(Style.MELEE, "dragon scimitar", null), 1e-9);
	}

	@Test
	@DisplayName("the Scorching bow bypasses on the ranged side; a blowpipe pays")
	void rangedBypasses() {
		assertEquals(1.0, factor(Style.RANGED, "scorching bow", null), 1e-9);
		assertEquals(0.8, factor(Style.RANGED, "toxic blowpipe", null), 1e-9);
	}

	@Test
	@DisplayName("magic bypasses by the SPELL being demonbane, not the staff held")
	void magicGoesBySpell() {
		assertEquals(1.0, factor(Style.MAGIC, "purging staff", "Dark Demonbane"), 1e-9);
		assertEquals(0.8, factor(Style.MAGIC, "purging staff", "Fire Surge"), 1e-9,
			"a plain cast is not demonbane damage, whatever stick throws it");
		assertEquals(0.8, factor(Style.MAGIC, "kodai wand", null), 1e-9);
	}

	@Test
	@DisplayName("the reduction exists only at Tormented Demons")
	void scopedToTds() {
		Monster goblin = data.searchMonsters("goblin", 1).get(0);
		assertEquals(1.0, TormentedDemonRules.damageFactor(goblin,
			Style.MELEE, byName("dragon scimitar"), null), 1e-9);
	}
}
