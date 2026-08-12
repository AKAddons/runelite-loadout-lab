package com.loadoutlab.render;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

/** The classic pill border (ported verbatim from the panel). */
final class RoundedBorder extends javax.swing.border.AbstractBorder
{
	private final Color color;
	private final int vPad;
	private final int hPad;

	RoundedBorder(Color color, int vPad, int hPad)
	{
		this.color = color;
		this.vPad = vPad;
		this.hPad = hPad;
	}

	@Override
	public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
	{
		Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(color);
			g2.drawRoundRect(x, y, width - 1, height - 1, 8, 8);
		}
		finally
		{
			g2.dispose();
		}
	}

	@Override
	public Insets getBorderInsets(Component c)
	{
		return new Insets(vPad, hPad, vPad, hPad);
	}

	@Override
	public Insets getBorderInsets(Component c, Insets insets)
	{
		insets.set(vPad, hPad, vPad, hPad);
		return insets;
	}
}
