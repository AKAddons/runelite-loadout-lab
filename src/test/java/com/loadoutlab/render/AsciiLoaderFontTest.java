package com.loadoutlab.render;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Andrew (2026-10-15), first run on a Windows client: "oof the animation is
 * fucked up" - the flask mood's rows slid sideways. Java's logical Monospaced
 * font there draws ASCII in Courier New at one width and borrows Braille,
 * block elements and shades from Segoe UI Symbol at others. The moods are
 * drawn for one advance width (the Mac's Menlo), so the plugin bundles a face
 * that has the whole alphabet at one width (DejaVu Sans Mono, Menlo's
 * ancestor, turned out to have no Braille) and every platform draws the same.
 */
class AsciiLoaderFontTest
{
	private static final String FRAMES = "/com/loadoutlab/render/loader_frames.txt";

	@Test
	@DisplayName("the bundled mood font loads and is Cascadia Mono")
	void bundledFontLoads()
	{
		Font font = AsciiLoader.moodFont();
		assertEquals("Cascadia Mono", font.getFamily(), "the bundled face, not a platform fallback");
		assertEquals(12, font.getSize());
	}

	@Test
	@DisplayName("every distinct character of the frames resource is drawable, at one advance width")
	void framesFitTheFont() throws Exception
	{
		Font font = AsciiLoader.moodFont();
		Set<Integer> glyphs = new LinkedHashSet<>();
		try (InputStream in = AsciiLoaderFontTest.class.getResourceAsStream(FRAMES))
		{
			assertNotNull(in, "frames resource");
			String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			text.codePoints().filter(cp -> cp > ' ' && cp != '\n' && cp != '\r').forEach(glyphs::add);
		}
		// Frames are HTML: tags and entities are not glyphs.
		glyphs.removeIf(cp -> cp == '<' || cp == '>' || cp == '&' || cp == ';' || cp == '=' || cp == '\'' || cp == '"');
		assertTrue(glyphs.size() > 50, "the frames use a real alphabet: " + glyphs.size());
		FontMetrics metrics = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB).createGraphics().getFontMetrics(font);
		int width = metrics.charWidth('M');
		StringBuilder missing = new StringBuilder();
		StringBuilder uneven = new StringBuilder();
		for (int cp : glyphs)
		{
			if (!font.canDisplay(cp))
			{
				missing.append(String.format(" U+%04X", cp));
			}
			else if (metrics.charWidth(cp) != width)
			{
				uneven.append(String.format(" U+%04X=%d", cp, metrics.charWidth(cp)));
			}
		}
		assertEquals("", missing.toString(), "glyphs the font cannot draw");
		assertEquals("", uneven.toString(), "glyphs drawn at a width other than " + width);
	}
}
