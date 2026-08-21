package com.loadoutlab.render;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.Icon;

/** The classic stat icons, baked from the original painted art (see git
 * history for the painters): the hitsplat for max hit, the crosshair for
 * accuracy, and the fixed-width wrapper that gives every stat line one
 * hard left edge. */
final class StatIcons
{
	private StatIcons()
	{
	}

	/** Baked images by resource name. A missing or unreadable resource
	 * caches a fully transparent placeholder so paint degrades to blank -
	 * it never throws inside a Swing paint pass. */
	private static final Map<String, BufferedImage> BAKED = new ConcurrentHashMap<>();

	private static BufferedImage baked(String name, int size)
	{
		return BAKED.computeIfAbsent(name, n ->
		{
			try (InputStream in = StatIcons.class.getResourceAsStream(n))
			{
				if (in != null)
				{
					BufferedImage img = ImageIO.read(in);
					if (img != null)
					{
						return img;
					}
				}
			}
			catch (IOException ignored)
			{
				// fall through to the transparent placeholder
			}
			return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		});
	}

	/**
	 * Shared shape for the square baked icons: a single pixel size used as
	 * both width and height, drawn from a PNG baked at exactly that size
	 * from the original paint code.
	 */
	abstract static class SizedIcon implements Icon
	{
		/** Icon edge length in pixels. */
		protected final int size;
		private final BufferedImage image;

		SizedIcon(String bakedName, int size)
		{
			this.size = size;
			this.image = baked("stat-" + bakedName + "-" + size + ".png", size);
		}

		@Override
		public int getIconWidth()
		{
			return size;
		}

		@Override
		public int getIconHeight()
		{
			return size;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			g.drawImage(image, x, y, size, size, null);
		}
	}


	/** The baked crosshair for the accuracy line (glyph-safe). */
	static final class CrosshairIcon extends SizedIcon
	{
		CrosshairIcon(int size)
		{
			super("crosshair", size);
		}
	}


	/** The baked red hitsplat for the max-hit line (glyph-safe). */
	static final class HitsplatIcon extends SizedIcon
	{
		HitsplatIcon(int size)
		{
			super("hitsplat", size);
		}
	}


	/** A fixed-width box that centres its delegate - every stat-panel
	 * line's icon occupies the SAME column width, so the values start on
	 * one hard left edge (field spec: a strong visual column). */
	static final class FixedWidthIcon implements Icon
	{
		static final int WIDTH = 20;
		static final int HEIGHT = 16;
		final Icon delegate;

		FixedWidthIcon(Icon delegate)
		{
			this.delegate = delegate;
		}

		@Override
		public int getIconWidth()
		{
			return WIDTH;
		}

		@Override
		public int getIconHeight()
		{
			return Math.max(HEIGHT, delegate.getIconHeight());
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			delegate.paintIcon(c,
				g,
				x + Math.max(0, (WIDTH - delegate.getIconWidth()) / 2),
				y + Math.max(0, (getIconHeight() - delegate.getIconHeight()) / 2));
		}
	}

}
