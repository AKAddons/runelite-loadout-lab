package com.loadoutlab.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * The little helm mascot, working out while the optimizer thinks: arms
 * pump up and down and the legs bounce at the knee (feet planted, knees
 * absorb the bob). Pixel art scaled with nearest-neighbour so it stays
 * crisp; the animation timer only runs while the component is showing
 * (addNotify/removeNotify), so an idle panel costs nothing.
 */
class MascotSpinner extends JComponent
{
	private static final int SCALE = 3;
	private static final BufferedImage MASCOT = load();
	private static final Color LIMB = new Color(140, 200, 140);

	private final Timer timer = new Timer(33, e -> repaint());
	private long startedAt;

	MascotSpinner()
	{
		int size = MASCOT == null ? 0 : MASCOT.getWidth() * SCALE;
		// room for arms either side and legs below
		setPreferredSize(new Dimension(size + 44, size + 34));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, size + 34));
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
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		double t = (System.currentTimeMillis() - startedAt) / 1000.0;
		double phase = Math.sin(t * 5.0);
		int size = MASCOT.getWidth() * SCALE;
		int groundY = getHeight() - 6;
		// The body bobs; the knees absorb it (feet stay planted).
		int bodyX = 22;
		int bodyY = groundY - 18 - size + (int) Math.round(phase * 4.0);

		g2.setColor(LIMB);
		g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

		// Legs: hip -> knee -> planted foot; knees kick outward as the body dips.
		int hipY = bodyY + size - 2;
		int bend = 3 + (int) Math.round((phase + 1) * 3.0); // more bend when low
		leg(g2, bodyX + size / 3, hipY, bodyX + size / 3 - bend, groundY, true);
		leg(g2, bodyX + 2 * size / 3, hipY, bodyX + 2 * size / 3 + bend, groundY, false);

		// Arms: shoulders at the body sides, pumping up and down together.
		int shoulderY = bodyY + size / 2;
		double armAngle = Math.toRadians(30 + phase * 45); // up-down sweep
		int armLen = 14;
		int dx = (int) Math.round(Math.cos(armAngle) * armLen);
		int dy = (int) Math.round(Math.sin(armAngle) * armLen);
		g2.drawLine(bodyX + 1, shoulderY, bodyX + 1 - dx, shoulderY - dy);
		g2.drawLine(bodyX + size - 1, shoulderY, bodyX + size - 1 + dx, shoulderY - dy);

		// Body on top of the limbs.
		g2.drawImage(MASCOT, bodyX, bodyY, size, size, null);
		g2.dispose();
	}

	private static void leg(Graphics2D g2, int hipX, int hipY, int kneeOutX, int groundY, boolean left)
	{
		int kneeY = (hipY + groundY) / 2;
		int footX = hipX + (left ? -2 : 2);
		g2.drawLine(hipX, hipY, kneeOutX, kneeY);
		g2.drawLine(kneeOutX, kneeY, footX, groundY);
		// little foot stub
		g2.drawLine(footX, groundY, footX + (left ? -5 : 5), groundY);
	}
}
