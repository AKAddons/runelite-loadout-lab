package com.ll.render;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Andrew (2026-10-31): "more realistic to the bosses" with "more
 * texture/variety of effects and patterns". Hi-res moods live in their own
 * files, read when that boss computes, and paint cell by cell: Swing's HTML
 * view leaves 2 px under every row at 9 pt, which stripes a solid body. */
class HiResMoodTest {
	private static String[] keys() {
		java.io.File[] files = new java.io.File("src/main/resources/com/ll/render/moods").listFiles();
		assertNotNull(files);
		return java.util.Arrays.stream(files).map(f -> f.getName().replace(".txt", "")).toArray(String[]::new);
	}

	@Test
	@DisplayName("each hi-res boss has a steady 43x24 pool in the palette, free of markup characters")
	void poolsAreSteady() {
		for (String key : keys()) {
			List<List<String>> pool = AsciiLoader.pool(key);
			assertNotNull(pool, key);
			assertFalse(pool.isEmpty(), key);
			for (List<String> frames : pool) {
				assertTrue(frames.size() > 1, "a mood animates");
				for (String frame : frames) {
					String[] rows = frame.split("\n", -1);
					assertEquals(AsciiLoader.ROWS, rows.length, key + " rows");
					for (String row : rows) {
						String glyphs = row.replaceAll("<[^>]*>", "");
						assertEquals(AsciiLoader.COLS, glyphs.length(), key + " columns");
						for (char c : glyphs.toCharArray()) {
							boolean block = c >= 0x2580 && c <= 0x259F;
							assertTrue((c >= 32 && c < 127 && c != '&' && c != '<' && c != '>') || block || c == 0x2261 || c == 0xA5,
								key + " glyph outside the palette: " + (int) c);
						}
					}
				}
			}
		}
	}

	@Test
	@DisplayName("a key without a hi-res file has no hi-res pool")
	void missingFileIsNull() {
		assertNull(AsciiLoader.pool("no such boss"));
		assertNull(AsciiLoader.pool(null));
	}

	@Test
	@DisplayName("a boss plays its own pool, and a search with no art plays the season's")
	void playsOwnOrSeason() {
		AsciiLoader loader = new AsciiLoader();
		try {
			loader.setKey("vorkath");
			loader.setRunning(true);
			assertEquals(AsciiLoader.pool("vorkath").get(0), loader.frames);
			assertEquals(new Dimension(216, 241), loader.getPreferredSize(), "the sidebar fits 224 px");
			loader.setKey(null);
			loader.setRunning(true);
			assertTrue(AsciiLoader.pool(AsciiLoader.seasonal(java.time.LocalDate.now())).contains(loader.frames));
		} finally {
			loader.setRunning(false);
		}
	}

	@Test
	@DisplayName("a painted frame closes up: a column of full blocks leaves no blank row")
	void rowsCloseUp() {
		String row = "<font color=#ffffff>█</font>";
		BufferedImage img = paint(row + "\n" + row + "\n" + row, 5, 30);
		for (int y = 0; y < 30; y++) {
			assertTrue((img.getRGB(2, y) & 0xff) > 128, "blank row at y=" + y);
		}
	}

	@Test
	@DisplayName("runs take their own colours and columns advance one cell a glyph")
	void coloursAndColumns() {
		BufferedImage img = paint("<font color=#ff0000>█</font> <font color=#00ff00>█</font>", 15, 10);
		assertEquals(0xff0000, img.getRGB(2, 5) & 0xffffff);
		assertEquals(0, img.getRGB(7, 5) & 0xffffff, "a space inks nothing");
		assertEquals(0x00ff00, img.getRGB(12, 5) & 0xffffff);
	}

	@Test
	@DisplayName("the longest film reads in, and its heaviest frame paints, well inside the clock")
	void paintsInTime() {
		long readStart = System.nanoTime();
		List<List<String>> pool = AsciiLoader.pool("tfa");
		long readMs = (System.nanoTime() - readStart) / 1_000_000;
		System.out.println("tfa pool read in " + readMs + " ms");
		assertTrue(readMs < 500, "the pool took " + readMs + " ms to read on the EDT");
		String heaviest = "";
		for (String frame : pool.get(0)) {
			if (frame.length() > heaviest.length()) heaviest = frame;
		}
		long best = Long.MAX_VALUE;
		for (int i = 0; i < 12; i++) {
			long t0 = System.nanoTime();
			paint(heaviest, 215, 240);
			best = Math.min(best, System.nanoTime() - t0);
		}
		assertTrue(best < 30_000_000L, "a frame took " + best / 1_000_000 + " ms; the clock ticks every 140");
	}

	@Test
	@DisplayName("the rim's right side inks about as strongly as its left, inside the component (it was clipped)")
	void rimIsEven() {
		String frame = AsciiLoader.pool("vorkath").get(0).get(0);
		Dimension size = new AsciiLoader().getPreferredSize();
		BufferedImage img = paint(frame, size.width, size.height);
		int y = size.height / 2;
		int left = strongest(img, y, 0, 2), right = strongest(img, y, size.width - 3, size.width);
		System.out.println("rim ink left " + left + " right " + right);
		assertTrue(right * 2 >= left && left * 2 >= right, "left " + left + " right " + right);
	}

	private static int strongest(BufferedImage img, int y, int x0, int x1) {
		int best = 0;
		for (int x = x0; x < x1; x++) {
			int rgb = img.getRGB(x, y);
			best = Math.max(best, Math.max(rgb >> 16 & 255, Math.max(rgb >> 8 & 255, rgb & 255)));
		}
		return best;
	}

	private static BufferedImage paint(String frame, int w, int h) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		AsciiLoader.paintCells(g, frame);
		g.dispose();
		return img;
	}
}
