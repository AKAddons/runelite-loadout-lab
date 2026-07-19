package com.loadoutlab.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.Icon;

/**
 * Verbatim copies of the eight square painted icon classes as they existed at
 * commit 0c93ff9, before the SizedIcon base class was introduced. Only the
 * class name and constructor name were changed (Legacy prefix); every paint
 * body is byte-identical to the original. Used by SizedIconPixelTest to prove
 * the refactor changed no pixels.
 */
final class LegacyIcons
{
	private LegacyIcons()
	{
	}

	static final class LegacyNoPrayerIcon implements Icon
	{
		private final int size;

		LegacyNoPrayerIcon(int size)
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

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(c.getForeground());
			g2.setStroke(new BasicStroke(Math.max(1.3f, size / 9f),
				BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			double m = 1.5;
			double d = size - 2 * m - 1;
			g2.draw(new java.awt.geom.Ellipse2D.Double(x + m, y + m, d, d));
			double off = (d / 2.0) / Math.sqrt(2);
			double cx = x + size / 2.0;
			double cy = y + size / 2.0;
			g2.draw(new java.awt.geom.Line2D.Double(cx - off, cy + off, cx + off, cy - off));
			g2.dispose();
		}
	}

	static final class LegacyCloseIcon implements Icon
	{
		private final int size;

		LegacyCloseIcon(int size)
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

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.setColor(c.isEnabled() ? new Color(180, 180, 220) : new Color(96, 96, 108));
				g2.drawLine(x + 1, y + 1, x + size - 1, y + size - 1);
				g2.drawLine(x + size - 1, y + 1, x + 1, y + size - 1);
			}
			finally
			{
				g2.dispose();
			}
		}
	}

	static final class LegacyDotsIcon implements Icon
	{
		private final int size;

		LegacyDotsIcon(int size)
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

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(c.getForeground());
			double r = Math.max(1.2, size / 9.0);
			double cy = y + size / 2.0;
			for (int i = 0; i < 3; i++)
			{
				double cx = x + size * (0.22 + 0.28 * i);
				g2.fill(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
			}
			g2.dispose();
		}
	}

	static final class LegacyReloadIcon implements Icon
	{
		private final int size;

		LegacyReloadIcon(int size)
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

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			g2.translate(x, y);
			g2.setColor(c.getForeground());
			double cx = size / 2.0;
			double cy = size / 2.0;
			double r = size / 2.0 - 2.0;
			// 0 deg points up, sweeps clockwise; a gap is left for the head.
			double startDeg = 50;
			double sweepDeg = 265;
			java.awt.geom.Path2D.Double arc = new java.awt.geom.Path2D.Double();
			int steps = 48;
			for (int i = 0; i <= steps; i++)
			{
				double a = Math.toRadians(startDeg + sweepDeg * i / steps);
				double px = cx + r * Math.sin(a);
				double py = cy - r * Math.cos(a);
				if (i == 0)
				{
					arc.moveTo(px, py);
				}
				else
				{
					arc.lineTo(px, py);
				}
			}
			g2.setStroke(new BasicStroke(Math.max(1.4f, size / 9f),
				BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.draw(arc);
			// Arrowhead at the swept end, pointing along the clockwise tangent.
			double aEnd = Math.toRadians(startDeg + sweepDeg);
			double ex = cx + r * Math.sin(aEnd);
			double ey = cy - r * Math.cos(aEnd);
			double tx = Math.cos(aEnd);
			double ty = Math.sin(aEnd);
			double nx = -ty;
			double ny = tx;
			double h = size * 0.32;
			double w = size * 0.22;
			java.awt.geom.Path2D.Double head = new java.awt.geom.Path2D.Double();
			head.moveTo(ex + tx * h, ey + ty * h);
			head.lineTo(ex - tx * h * 0.2 + nx * w, ey - ty * h * 0.2 + ny * w);
			head.lineTo(ex - tx * h * 0.2 - nx * w, ey - ty * h * 0.2 - ny * w);
			head.closePath();
			g2.fill(head);
			g2.dispose();
		}
	}

	static final class LegacyCrosshairIcon implements Icon
	{
		private final int size;

		LegacyCrosshairIcon(int size)
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

	static final class LegacyHitsplatIcon implements Icon
	{
		private final int size;

		LegacyHitsplatIcon(int size)
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

	static final class LegacyInfoIcon implements Icon
	{
		private final int size;

		LegacyInfoIcon(int size)
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

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(new Color(200, 170, 110));
				g2.drawOval(x, y, size - 1, size - 1);
				int cx = x + size / 2;
				g2.fillRect(cx - 1, y + 3, 2, 2);
				g2.fillRect(cx - 1, y + 6, 2, size - 9);
			}
			finally
			{
				g2.dispose();
			}
		}
	}

	static final class LegacyPlusStarIcon implements Icon
	{
		private final int size;

		LegacyPlusStarIcon(int size)
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

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setColor(new Color(208, 178, 102));
				int mid = size / 2;
				int arm = Math.max(3, size / 3);
				g2.fillRect(x + mid - 1, y + mid - arm, 3, 2 * arm + 1);
				g2.fillRect(x + mid - arm, y + mid - 1, 2 * arm + 1, 3);
			}
			finally
			{
				g2.dispose();
			}
		}
	}
}
