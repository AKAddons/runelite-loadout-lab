package com.loadoutlab.ui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * The little helm mascot, bobbing while the optimizer thinks. Pixel art
 * scaled with nearest-neighbour so it stays crisp; the animation timer
 * only runs while the component is showing (addNotify/removeNotify), so
 * an idle panel costs nothing.
 */
class MascotSpinner extends JComponent
{
	private static final int SCALE = 3;
	private static final BufferedImage MASCOT = load();

	private final Timer timer = new Timer(33, e -> repaint());
	private long startedAt;

	MascotSpinner()
	{
		int size = MASCOT == null ? 0 : MASCOT.getWidth() * SCALE + 16;
		setPreferredSize(new Dimension(size, size));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, size));
		setAlignmentX(LEFT_ALIGNMENT);
	}

	private static BufferedImage load()
	{
		try
		{
			return ImageIO.read(MascotSpinner.class.getResourceAsStream("/com/loadoutlab/icon.png"));
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	static boolean available()
	{
		return MASCOT != null;
	}

	@Override
	public void addNotify()
	{
		super.addNotify();
		startedAt = System.currentTimeMillis();
		timer.start();
	}

	@Override
	public void removeNotify()
	{
		timer.stop();
		super.removeNotify();
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		if (MASCOT == null)
		{
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		double t = (System.currentTimeMillis() - startedAt) / 1000.0;
		int size = MASCOT.getWidth() * SCALE;
		int x = 8;
		int y = 8 + (int) Math.round(Math.sin(t * 4.0) * 4.0);
		double tilt = Math.sin(t * 2.6) * Math.toRadians(9);
		g2.rotate(tilt, x + size / 2.0, y + size / 2.0);
		g2.drawImage(MASCOT, x, y, size, size, null);
		g2.dispose();
	}
}
