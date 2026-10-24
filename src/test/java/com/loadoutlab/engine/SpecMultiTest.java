package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.LoadoutData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Andrew (client test 2026-10-19): "for spec, dragon 2h is a really good
 * multi spec". Wiki: Powerstab hits every target within one square of the
 * player (60% energy, no modifiers); Annihilate hits up to nine in a 3x3;
 * Shield Bash up to ten in an 11x11 (+20% accuracy, 50%); the halberd
 * sweep a 3x1 line on 1x1 targets. A spec's factor at a stack is the
 * stack capped at its targets; a single-target spec stays at one.
 */
class SpecMultiTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static SpecialAttack spec(String nameLower) {
		GearItem item = data.getGearItems().stream().filter(g -> g.getNameLower().equals(nameLower))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing: " + nameLower));
		SpecialAttack spec = SpecialAttack.match(item);
		assertNotNull(spec, nameLower + " has no special attack on record");
		return spec;
	}

	@Test
	@DisplayName("Powerstab scales with the stack up to nine; the sweep caps at three; the claws stay at one")
	void factors() {
		SpecialAttack d2h = spec("dragon 2h sword");
		assertEquals(60, d2h.getEnergyCost());
		assertEquals(3, d2h.multiFactor(3), 1e-12);
		assertEquals(9, d2h.multiFactor(12), 1e-12);
		assertEquals(1, d2h.multiFactor(1), 1e-12);
		assertEquals(3, spec("crystal halberd").multiFactor(9), 1e-12, "a 3x1 line");
		assertEquals(9, spec("dragon crossbow").multiFactor(9), 1e-12);
		assertEquals(10, spec("dinh's bulwark").multiFactor(12), 1e-12);
		assertEquals(1, spec("dragon claws").multiFactor(9), 1e-12, "single-target");
	}
}
