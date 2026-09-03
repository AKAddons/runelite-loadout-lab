package com.loadoutlab.render;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AsciiLoaderTest
{
	@Test
	@DisplayName("the frames resource parses into steady-sized moods")
	void framesParseSteady()
	{
		List<List<String>> land = new java.util.ArrayList<>();
		java.util.Map<String, List<List<String>>> pools = new java.util.HashMap<>();
		AsciiLoader.load(land, pools);
		assertFalse(land.isEmpty());
		List<List<String>> sea = pools.getOrDefault("sea", List.of());
		assertFalse(sea.isEmpty(), "the sea pool ships with at least one mood");
		// Raid pools (Andrew 2026-09-02: "hit raids first"): the Obelisk
		// ships; Verzik, Olm and Zulrah return as renders of the real
		// sprites ("only the obelisk looks acceptable"). An empty pool
		// falls back to the flask, so their keys need no frames yet.
		assertFalse(pools.getOrDefault("toa", List.of()).isEmpty(), "toa pool has a mood");
		// Three sea moods (sail, cannon, kraken - Andrew 2026-09-02) at the
		// land flask's 19x9, so the compute block keeps one shape.
		assertEquals(3, sea.size(), "sail, cannon, kraken");
		boolean wideMood = false;
		for (List<String> frames : sea)
		{
			String[] lines = frames.get(0).split("\n");
			assertEquals(9, lines.length, "sea frames are 9 rows like the land flask");
			assertTrue(lines[0].length() >= 19, "sea frames are at least the flask's width");
			wideMood |= lines[0].length() > 19;
		}
		// The cannon mood spreads out so the shot has a real trajectory
		// (Andrew 2026-09-02); moods may differ in width, one plays per compute.
		assertTrue(wideMood, "the cannon mood is wider than the flask");
		List<List<String>> moods = new java.util.ArrayList<>(land);
		for (List<List<String>> pool : pools.values())
		{
			moods.addAll(pool);
		}
		for (List<String> frames : moods)
		{
			assertTrue(frames.size() > 1, "a mood animates - two frames minimum");
			String[] first = frames.get(0).split("\n");
			for (String frame : frames)
			{
				String[] lines = frame.split("\n");
				// Same height and width every frame, or the centred
				// block wobbles mid-animation.
				assertEquals(first.length, lines.length, "frame height drifted");
				for (String line : lines)
				{
					assertEquals(first[0].length(), line.length(), "frame width drifted");
					for (char c : line.toCharArray())
					{
						assertTrue(c >= 32 && c < 127, "non-ASCII frame char: " + (int) c);
					}
				}
			}
		}
	}
}
