package com.loadoutlab.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The thumbnail's wiki file name comes from the bundled corpus, never
 * from guessing at the wiki's naming conventions - that is what keeps a
 * monster picture to exactly ONE request. These exercise the resolution
 * against the real corpus; none of them touch the network.
 */
class MonsterIconsTest
{
	private final MonsterIcons icons = new MonsterIcons(null);

	@Test
	@DisplayName("a versioned mob resolves to its own per-form picture")
	void perFormImage()
	{
		assertEquals("Abyssal demon (Catacombs of Kourend).png",
			icons.wikiFile("Abyssal demon", "Catacombs of Kourend"));
		assertEquals("Zulrah (magma).png", icons.wikiFile("Zulrah", "Magma"));
		assertEquals("Zulrah (tanzanite).png", icons.wikiFile("Zulrah", "Tanzanite"));
	}

	@Test
	@DisplayName("no version falls back to the plain picture when the mob has one")
	void plainPreferredForBareName()
	{
		// The corpus lists the Catacombs form FIRST, so first-wins would
		// hand a plain "abyssal demon" search the wrong picture.
		assertEquals("Abyssal demon.png", icons.wikiFile("Abyssal demon", ""));
		assertEquals("Vorkath.png", icons.wikiFile("Vorkath", ""));
	}

	@Test
	@DisplayName("no version and no plain picture falls back to a form")
	void formFallbackWhenNoPlainImage()
	{
		// Zulrah is only ever a coloured form - any of them beats no icon.
		assertTrue(icons.wikiFile("Zulrah", "").startsWith("Zulrah ("),
			"expected a Zulrah form image");
	}

	@Test
	@DisplayName("an unknown version falls back to the mob's picture")
	void unknownVersionFallsBack()
	{
		assertEquals("Vorkath.png", icons.wikiFile("Vorkath", "Not a real form"));
	}

	@Test
	@DisplayName("lookup is case-insensitive")
	void caseInsensitive()
	{
		assertEquals("Vorkath.png", icons.wikiFile("vORKATH", ""));
	}

	@Test
	@DisplayName("a mob the corpus does not name gets no request at all")
	void missResolvesToNull()
	{
		assertNull(icons.wikiFile("Definitely not a monster", ""));
	}
}
