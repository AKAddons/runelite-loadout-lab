package com.loadoutlab.render;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;

/** The classic painted stat icons (ported verbatim from the panel):
 * the hitsplat for max hit, the crosshair for accuracy, and the
 * fixed-width wrapper that gives every stat line one hard left edge. */
final class StatIcons
{
	private StatIcons()
	{
	}

	/**
	 * Shared shape for the square painted icons: they all carry a single
	 * pixel size used as both width and height. Subclasses keep only their
	 * paintIcon body. Deliberately sets NO rendering hints - antialiasing
	 * stays exactly where each subclass sets it today, because some painted
	 * icons in this file draw aliased on purpose.
	 */
	abstract static class SizedIcon implements Icon
	{
		/** Icon edge length in pixels; readable by subclass paint code. */
		protected final int size;

		SizedIcon(int size)
		{
			this.size = size;
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
	}


	/** A painted crosshair for the accuracy line (glyph-safe) - the
	 * Attack staticon read as the same sword as the style icon. */
	static final class CrosshairIcon extends SizedIcon
	{
		CrosshairIcon(int size)
		{
			super(size);
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(new Color(200, 170, 90));
				int pad = 2;
				g2.drawOval(x + pad, y + pad, size - 2 * pad - 1, size - 2 * pad - 1);
				int mid = size / 2;
				g2.drawLine(x + mid, y, x + mid, y + 3);
				g2.drawLine(x + mid, y + size - 4, x + mid, y + size - 1);
				g2.drawLine(x, y + mid, x + 3, y + mid);
				g2.drawLine(x + size - 4, y + mid, x + size - 1, y + mid);
				g2.fillOval(x + mid - 1, y + mid - 1, 3, 3);
			}
			finally
			{
				g2.dispose();
			}
		}
	}


	/** A painted red hitsplat for the max-hit line (glyph-safe). */
	static final class HitsplatIcon extends SizedIcon
	{
		HitsplatIcon(int size)
		{
			super(size);
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(new Color(150, 30, 30));
				g2.fillRoundRect(x, y + 1, size - 1, size - 2, 5, 5);
				g2.setColor(new Color(200, 60, 60));
				g2.drawRoundRect(x, y + 1, size - 2, size - 3, 5, 5);
			}
			finally
			{
				g2.dispose();
			}
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
