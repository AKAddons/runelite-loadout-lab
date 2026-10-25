package com.loadoutlab.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The stat sim chip stays narrow (ISS-01M5S1MXR1R6BV5FCWBREV6471, Andrew
 * 2026-10-25: "when you modify a lot of stats it's so wide") and reads yours
 * or custom (ISS-01M5T4Y0N8S474ACHVFBBCQTZJ: "by default it should say stats:
 * yours and if it's running modified stats it should say stats: custom");
 * the editor is a fixed three-column grid on its own row under the Inv slider.
 */
class StatsChipLabelTest {
	@Test
	@DisplayName("nothing simmed reads Stats: yours")
	void yours() {
		assertEquals("Stats: yours", RenderSurface.statsLabel(0, false));
	}

	@Test
	@DisplayName("simmed levels, the cape alone, or a full max sim read Stats: custom")
	void custom() {
		assertEquals("Stats: custom", RenderSurface.statsLabel(3, false));
		assertEquals("Stats: custom", RenderSurface.statsLabel(0, true));
		assertEquals("Stats: custom", RenderSurface.statsLabel(7, true));
	}
}
