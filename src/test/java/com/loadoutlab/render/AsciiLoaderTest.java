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
		// Raid pools (Andrew 2026-09-02: "hit raids first"): the Obelisk is
		// hand-drawn; Verzik, Olm and Zulrah are Braille renders of the real
		// wiki sprites ("make these look like the actual monster sprites").
		for (String key : new String[]{"toa", "tob", "cox", "zulrah", "cerberus", "brutus", "dbrutus", "madangel", "guardians", "kraken", "thermy", "vetion", "kbd", "kq", "muspah", "jad", "zuk", "dks", "barrows", "moons", "tormented", "gorilla", "sire", "sol", "hunllef", "huey", "nex", "titans", "maggot", "yama", "scurrius", "obor", "bryophyta", "crab", "duke", "vardorvis", "leviathan", "whisperer", "callisto", "artio", "venenatis", "spindel", "scorpia", "chaos", "drgreen", "drblue", "drred", "drblack", "drmetal", "vorkath", "hydra", "araxxor", "fanatic", "graardor", "kree", "kril", "zilyana", "corp", "archaeologist", "doom", "nightmare", "mimic", "shaman", "hespori", "sarachnis", "gryphon", "skotizo"})
		{
			assertFalse(pools.getOrDefault(key, List.of()).isEmpty(), key + " pool has a mood");
		}
		// Three sea moods (sail, cannon, kraken - Andrew 2026-09-02) at the
		// land flask's 19x9, so the compute block keeps one shape.
		assertEquals(3, sea.size(), "sail, cannon, kraken");
		boolean wideMood = false;
		for (List<String> frames : sea)
		{
			String[] lines = frames.get(0).split("\n");
			assertEquals(12, lines.length, "sea frames are 12 rows like every mood since pass eight");
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
					line = line.replaceAll("<[^>]*>", "");
					assertEquals(first[0].replaceAll("<[^>]*>", "").length(), line.length(), "frame width drifted");
					for (char c : line.toCharArray())
					{
						// ASCII, or the Braille block: the sprite renders use
						// U+2800 dots for detail. Braille is the ONE exception
						// to the ASCII rule, pending Andrew's tofu check in the
						// client (Swing on macOS Tahoe boxed symbol glyphs once).
						boolean braille = c >= 0x2800 && c <= 0x28FF;
						boolean block = c >= 0x2580 && c <= 0x259F;  // solid fills by brightness
						// CP437 flavour (Andrew 2026-09-02): double box strokes and
						// corners, the triple bar, and yen for scale texture.
						boolean cp437 = (c >= 0x2550 && c <= 0x256C) || c == 0x2261 || c == 0xA5;
						assertTrue((c >= 32 && c < 127) || braille || block || cp437,
							"frame char outside the palette: " + (int) c);
					}
				}
			}
		}
	}

	@Test
	@DisplayName("the loader is an HTML pane, so a frame's cells can carry their own colours")
	void loaderDrawsHtml()
	{
		javax.swing.text.JTextComponent pane = new AsciiLoader();
		assertTrue(pane instanceof javax.swing.JEditorPane, "frames are HTML (Andrew 2026-09-03: colour per pixel at zero cost)");
	}
}
