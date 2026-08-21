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
		List<List<String>> moods = AsciiLoader.load();
		assertFalse(moods.isEmpty());
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
