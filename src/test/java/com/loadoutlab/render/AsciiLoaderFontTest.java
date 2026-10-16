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
	@DisplayName("the art draws in DejaVu Sans Mono (Menlo's parent: the shades the frames were tuned on) and Braille in Cascadia Mono")
	void bundledFontsLoad()
	{
		// Andrew (2026-10-15): "looks better shape wise but the color tone is off in the
		// potion" - Cascadia's medium shade inks 21% of its cell, DejaVu's 48%.
		Font art = AsciiLoader.moodFont();
		assertEquals("DejaVu Sans Mono", art.getFamily(), "the art face, not a platform fallback");
		assertEquals(12, art.getSize());
		assertEquals("Cascadia Mono", AsciiLoader.brailleFont().getFamily());
		assertTrue(shadeInk(art, '\u2592') >= 0.40, "the medium shade must read dark: " + shadeInk(art, '\u2592'));
	}

	/** Ink coverage of one glyph's cell at 12 pt, 0..1. */
	private static double shadeInk(Font font, char glyph)
	{
		BufferedImage img = new BufferedImage(20, 24, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = img.createGraphics();
		g.setFont(font);
		g.setColor(java.awt.Color.WHITE);
		FontMetrics m = g.getFontMetrics();
		g.drawString(String.valueOf(glyph), 2, m.getAscent() + 2);
		g.dispose();
		long ink = 0;
		for (int y = 0; y < img.getHeight(); y++)
		{
			for (int x = 0; x < img.getWidth(); x++)
			{
				ink += img.getRGB(x, y) >>> 24;
			}
		}
		return ink / 255.0 / (m.charWidth(glyph) * (m.getAscent() + m.getDescent()));
	}

	@Test
	@DisplayName("every distinct character of the frames resource is drawable, at one advance width")
	void framesFitTheFont() throws Exception
	{
		Font font = AsciiLoader.moodFont();
		Font braille = AsciiLoader.brailleFont();
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
		FontMetrics brailleMetrics = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB).createGraphics().getFontMetrics(braille);
		for (int cp : glyphs)
		{
			// Braille cells draw in the Braille face; everything else in the art face.
			boolean isBraille = cp >= 0x2800 && cp <= 0x28FF;
			Font face = isBraille ? braille : font;
			int w = (isBraille ? brailleMetrics : metrics).charWidth(cp);
			if (!face.canDisplay(cp))
			{
				missing.append(String.format(" U+%04X", cp));
			}
			else if (w != width)
			{
				uneven.append(String.format(" U+%04X=%d", cp, w));
			}
		}
		assertEquals("", missing.toString(), "glyphs the font cannot draw");
		assertEquals("", uneven.toString(), "glyphs drawn at a width other than " + width);
	}

	@Test
	@DisplayName("a frame's lines end at the same x whatever glyph class they use - the pre element draws in the bundled face")
	void preDrawsInTheBundledFace() throws Exception
	{
		// Andrew (2026-10-15), after the bundled font: "the animation of the flask
		// is still bad". Swing's stylesheet gives pre its own font-family rule,
		// which overrode the component font the pane was given.
		AsciiLoader loader = new AsciiLoader();
		loader.show("ABCDEFGHIJ\n" + "█".repeat(10) + "\n" + "⣿".repeat(10) + "\n" + "░".repeat(10));
		loader.setSize(400, 200);
		String text = loader.getDocument().getText(0, loader.getDocument().getLength());
		Set<Integer> ends = new LinkedHashSet<>();
		Set<Integer> heights = new LinkedHashSet<>();
		int start = 0;
		for (int i = 0; i <= text.length(); i++)
		{
			if (i == text.length() || text.charAt(i) == '\n')
			{
				if (i > start)
				{
					ends.add((int) loader.modelToView2D(i).getX());
					heights.add((int) loader.modelToView2D(start).getHeight());
				}
				start = i + 1;
			}
		}
		assertEquals(1, ends.size(), "line ends at x = " + ends);
		assertEquals(1, heights.size(), "a Braille row must not be taller than the others: " + heights);
	}
}
