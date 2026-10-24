package com.loadoutlab.model;

import com.loadoutlab.data.AssumeIcons;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Andrew, client test 2026-10-20: the game-best melee card did not show
 * Zeal. runelite-api 1.13.3 names no Zeal prayer sprite, so the assume
 * cell would fall to the "no prayer" book; the wiki's icon ships as a
 * bundled image and the model names it where the sprite is missing.
 */
class ZealIconTest {
	@Test
	@DisplayName("Zeal has no game sprite but a bundled image the card can draw")
	void zealWearsTheBundledIcon() {
		assertEquals(-1, AssumeIcons.prayerSprite("Zeal"));
		assertEquals("prayer-zeal.png", AssumeIcons.prayerImage("Zeal"));
		assertNull(AssumeIcons.prayerImage("Piety"), "a prayer with a sprite needs no image");
		assertNotNull(ZealIconTest.class.getResource("/com/loadoutlab/render/prayer-zeal.png"), "the image ships beside the renderer");
		Map<String, Object> node = RenderModel.assumeNode("Zeal + Divine super combat");
		assertEquals("prayer-zeal.png", node.get("prayerImage"));
		assertEquals("Zeal + Divine super combat", node.get("text"));
	}
}
