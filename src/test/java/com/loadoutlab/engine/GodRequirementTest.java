package com.loadoutlab.engine;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * God chips are the trip's protection, not a worn-set rule
 * (ISS-01M67DQ9ATHSQSJA7QGDKAQGPT, Andrew 2026-10-30: "chips as protection").
 */
class GodRequirementTest {
	@Test
	@DisplayName("god chips parse to the protection list once each and never to a worn-set rule; a route writes the editor's form")
	void chips() {
		assertEquals(List.of("zamorak", "zaros"), GwdProtection.chips("stab>=10;god:zamorak>=1;god:zaros>=1;god:zamorak>=1"));
		assertTrue(GwdProtection.chips("").isEmpty());
		assertTrue(GwdProtection.chips(null).isEmpty());
		assertEquals("god:armadyl>=1;god:bandos>=1", GwdProtection.chipsFor(List.of("armadyl", "bandos")));
		assertEquals("", GwdProtection.chipsFor(List.of()));
		List<SetRules.Rule> rules = SetRules.parse("god:zamorak>=1;prayer>=12");
		assertEquals(1, rules.size());
		assertEquals("prayer", rules.get(0).getStat());
	}
}
