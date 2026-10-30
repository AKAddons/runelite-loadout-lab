package com.loadoutlab.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * God item chips default to at least one (ISS-01M672BSSXVJG37FHKSPD18PQ4,
 * Andrew 2026-10-30: "99% of cases people want just >= 1").
 */
class ReqValueTest {
	@Test
	@DisplayName("an empty box on a god stat means 1; a typed value stays; an empty box on any other stat stays empty")
	void godDefault() {
		assertEquals("1", RenderSurface.reqValue("god:zamorak", ""));
		assertEquals("2", RenderSurface.reqValue("god:zamorak", "2"));
		assertEquals("1", RenderSurface.reqValue("god:any", ""));
		assertEquals("", RenderSurface.reqValue("stab", ""));
		assertEquals("12", RenderSurface.reqValue("prayer", "12"));
	}

	@Test
	@DisplayName("a completed task's chip hides under the setting unless it is picked")
	void caChip() {
		assertTrue(RenderSurface.showCaChip(false, false, true), "not done: shown");
		assertFalse(RenderSurface.showCaChip(true, false, true), "done and hidden");
		assertTrue(RenderSurface.showCaChip(true, true, true), "done but picked: shown so it can be dropped");
		assertTrue(RenderSurface.showCaChip(true, false, false), "setting off: shown");
	}
}
