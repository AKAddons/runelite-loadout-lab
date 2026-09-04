package com.loadoutlab.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Colour per cell at zero main-source cost (Andrew 2026-09-03: "i'd like
 * each pixel to be color customizable at basically zero cost"): frames
 * carry font runs as markup, the loader draws them as HTML. */
class LoaderHtmlTest
{
	@Test
	@DisplayName("a frame full of colour runs draws as HTML fast enough for the animation clock")
	void colouredFrameDrawsQuickly() throws Exception
	{
		StringBuilder frame = new StringBuilder();
		for (int r = 0; r < 12; r++)
		{
			for (int c = 0; c < 31; c++)
			{
				frame.append("<font color=#").append(String.format("%06x", (r * 31 + c) * 2654435 & 0xffffff))
					.append(">").append(c % 2 == 0 ? "▓" : "⣿").append("</font>");
			}
			frame.append('\n');
		}
		AsciiLoader loader = new AsciiLoader();
		long[] best = {Long.MAX_VALUE};
		javax.swing.SwingUtilities.invokeAndWait(() ->
		{
			for (int i = 0; i < 12; i++)
			{
				long t0 = System.nanoTime();
				loader.show(frame.toString());
				best[0] = Math.min(best[0], System.nanoTime() - t0);
			}
		});
		assertTrue(best[0] < 60_000_000L, "a 372-run frame took " + best[0] / 1_000_000 + " ms; the clock ticks every 140");
		assertTrue(loader.getDocument() instanceof javax.swing.text.html.HTMLDocument, "an HTML document, so the runs colour the glyphs");
	}
}
