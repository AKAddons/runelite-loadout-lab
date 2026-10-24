package com.loadoutlab.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wiki (Venator bow, read 2026-10-19): in a multi area the arrow bounces
 * up to twice; each bounce rolls its own accuracy with a max hit of
 * two-thirds of the first, floored. Accuracy cancels, so the chain is
 * worth 1 + 2 x E(floor(2m/3)) / E(m) single hits.
 */
class VenatorFactorTest {
	@Test
	@DisplayName("a 30 max hit chains to about 2.34 single hits: 20-max bounces at the same accuracy")
	void thirtyMax() {
		double e30 = 30 / 2.0 + 1.0 / 31;
		double e20 = 20 / 2.0 + 1.0 / 21;
		assertEquals(1 + 2 * e20 / e30, RollMath.venatorFactor(30), 1e-12);
		assertEquals(2.337, RollMath.venatorFactor(30), 0.001);
	}

	@Test
	@DisplayName("the bounce max floors (31 -> 20) and a zero max hit chains nothing")
	void flooringAndZero() {
		double e31 = 31 / 2.0 + 1.0 / 32;
		double e20 = 20 / 2.0 + 1.0 / 21;
		assertEquals(1 + 2 * e20 / e31, RollMath.venatorFactor(31), 1e-12);
		assertEquals(1.0, RollMath.venatorFactor(0), 1e-12);
	}

	@Test
	@DisplayName("the tool factors: area tools scale with the stack up to nine, the chain needs a second target")
	void toolFactors() {
		assertEquals(3, MultiTool.factor("area", 3, 30), 1e-12);
		assertEquals(9, MultiTool.factor("area", 12, 30), 1e-12);
		assertEquals(1, MultiTool.factor("area", 1, 30), 1e-12);
		assertEquals(1, MultiTool.factor("chain", 1, 30), 1e-12);
		assertEquals(RollMath.venatorFactor(30), MultiTool.factor("chain", 5, 30), 1e-12);
	}
}
