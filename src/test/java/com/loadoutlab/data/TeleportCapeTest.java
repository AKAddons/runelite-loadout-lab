package com.loadoutlab.data;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The teleport cape supply (ISS-01M5JJ0Z4M988DR0M0ZGJHD139, Andrew 2026-10-22: "we should add a
 * "teleport cape" option to the general supplies list: Detect Best: Ardougne
 * Cape -> Quest Cape -> Diary Cape -> Consruction Cape -> Max Cape / Alt:
 * Crafting Cape"). Ids from runelite-api 1.13.3 gameval ItemID.
 */
class TeleportCapeTest
{
	private static final String CATEGORY = "teleportCape";

	private static String detect(Integer... owned)
	{
		Set<Integer> bank = Set.of(owned);
		TripSupplies.Option pick = TripSupplies.detectBest(CATEGORY, bank::contains);
		return pick == null ? null : pick.key;
	}

	@Test
	@DisplayName("a general supply, best first: Max cape, Construction, Diary, Quest point, Ardougne cloak; the Crafting cape a choice")
	void tiers()
	{
		assertTrue(List.of(TripSupplies.CATEGORY_KEYS).contains(CATEGORY));
		assertEquals("Teleport cape", TripSupplies.label(CATEGORY));
		assertEquals(List.of("MAX_CAPE", "CONSTRUCTION_CAPE", "ACHIEVEMENT_DIARY_CAPE", "QUEST_POINT_CAPE",
			"ARDOUGNE_CLOAK", "CRAFTING_CAPE"),
			TripSupplies.options(CATEGORY).stream().map(o -> o.key).collect(Collectors.toList()));
		assertTrue(TripSupplies.options(CATEGORY).stream().allMatch(o -> o.utility),
			"neither worn nor carried for the fight: the bank's utility row");
		assertFalse(TripSupplies.option(CATEGORY, "CRAFTING_CAPE").detect, "the alternative is never auto-picked");
	}

	@Test
	@DisplayName("Detect Best takes the best cape you own - any Ardougne cloak tier, trimmed capes, combined max capes")
	void detectBest()
	{
		assertEquals("ARDOUGNE_CLOAK", detect(13122));
		assertEquals("QUEST_POINT_CAPE", detect(13122, 9813));
		assertEquals("ACHIEVEMENT_DIARY_CAPE", detect(13068, 13069));
		assertEquals("CONSTRUCTION_CAPE", detect(19476, 9790));
		assertEquals("MAX_CAPE", detect(9789, 21285), "the Infernal max cape keeps the max cape's teleports");
		assertNull(detect(9780), "a Crafting cape alone is a choice, not a detect");
		assertNull(detect());
	}
}
