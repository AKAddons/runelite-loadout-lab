package com.loadoutlab.render;

import java.util.List;
import java.util.Map;
import javax.swing.JLabel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Arceuus chips follow the card you view (ISS-01M5K9KFJH7EZ0GMAKNGZ4K2JG,
 * Andrew 2026-10-22: "when i search for the whisperer it gives me earth surge
 * as my spell -> it tells me that thralls and death charge aen't usable in
 * this configuration however it keeps the chips for them active. it should
 * just disable the chips in this case").
 */
class StandDownChipsTest
{
	private static Map<String, Object> page(String tab)
	{
		Map<String, Object> magic = Map.of("tabDps", 5.0,
			"yours", Map.of("kitBacked", true, "arceuusStandDown", "Earth Surge autocasts from the standard book"));
		Map<String, Object> melee = Map.of("tabDps", 3.0, "yours", Map.of("kitBacked", true));
		Map<String, Object> mob = Map.of("styles", Map.of("magic", magic, "melee", melee));
		return Map.of("entries", List.of(Map.of("params", Map.of("selectedTab", tab, "lensIndex", 0),
			"mobs", List.of(mob))));
	}

	@Test
	@DisplayName("viewing the magic card that stands down, the chips get its reason; viewing melee, none")
	void reasonFollowsTheViewedCard()
	{
		assertEquals("Earth Surge autocasts from the standard book", RenderSurface.viewedStandDown(page("magic")));
		assertNull(RenderSurface.viewedStandDown(page("melee")), "thralls still work with a melee set");
		assertEquals("Earth Surge autocasts from the standard book", RenderSurface.viewedStandDown(page("")),
			"auto opens the recommended card - here the magic one");
	}

	@Test
	@DisplayName("a stood-down chip is shown disabled with the reason, and does nothing")
	void disabledChip()
	{
		JLabel chip = RenderSurface.disabledPill("Thralls", "Stands down: Earth Surge autocasts from the standard book");
		assertFalse(chip.isEnabled());
		assertTrue(chip.getToolTipText().contains("Stands down"));
		assertTrue(java.util.Arrays.stream(chip.getMouseListeners())
			.allMatch(l -> l instanceof javax.swing.ToolTipManager), "no click - only the tooltip listens");
	}
}
