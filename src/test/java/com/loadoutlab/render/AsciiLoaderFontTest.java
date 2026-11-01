package com.loadoutlab.render;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Andrew (2026-10-15), first run on a Windows client: "oof the animation is
 * fucked up" - the flask mood's rows slid sideways. Java's logical Monospaced
 * font there draws ASCII in Courier New at one width and borrows block elements
 * and shades from Segoe UI Symbol at others, so the plugin bundles a face that
 * has the whole alphabet at one width and every platform draws the same.
 */
class AsciiLoaderFontTest {
	@Test
	@DisplayName("the art draws in the bundled DejaVu Sans Mono at 9 pt, not a platform fallback")
	void bundledFontLoads() {
		assertEquals("DejaVu Sans Mono", AsciiLoader.CELL_FONT.getFamily());
		assertEquals(9, AsciiLoader.CELL_FONT.getSize());
	}

	@Test
	@DisplayName("every distinct character of every pool is drawable, at one advance width that fills its cell")
	void framesFitTheFont() throws Exception {
		Font font = AsciiLoader.CELL_FONT;
		Set<Integer> glyphs = new LinkedHashSet<>();
		File[] files = new File("src/main/resources/com/loadoutlab/render/moods").listFiles();
		assertNotNull(files);
		for (File file : files) {
			String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
			text.replaceAll("(?m)^==.*$", "").replaceAll("<[^>]*>", "").codePoints().filter(cp -> cp > ' ').forEach(glyphs::add);
		}
		assertTrue(glyphs.size() > 50, "the frames use a real alphabet: " + glyphs.size());
		FontMetrics m = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics().getFontMetrics(font);
		int advance = m.charWidth('M');
		assertEquals(AsciiLoader.CELL_W, advance, "the cell is one advance wide");
		for (int cp : glyphs) {
			assertEquals(-1, font.canDisplayUpTo(new String(Character.toChars(cp))), "the face lacks U+" + Integer.toHexString(cp));
			assertEquals(advance, m.charWidth(cp), "U+" + Integer.toHexString(cp) + " is off the grid");
		}
	}
}
