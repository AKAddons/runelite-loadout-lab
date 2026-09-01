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
		List<List<String>> sea = new java.util.ArrayList<>();
		AsciiLoader.load(land, sea);
		assertFalse(land.isEmpty());
		assertFalse(sea.isEmpty(), "the sea pool ships with at least one mood");
		List<List<String>> moods = new java.util.ArrayList<>(land);
		moods.addAll(sea);
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
