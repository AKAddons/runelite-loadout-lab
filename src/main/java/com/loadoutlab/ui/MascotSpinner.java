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
 * The bottle mascot, working out while the optimizer thinks. Its own
 * chunky L-legs (the LL in Loadout Lab) do the bouncing: feet planted,
 * thigh segments squash and stretch at the knee as the body bobs, and
 * little arms pump up and down. The sprite is sliced into body/thigh/
 * shin segments so the art animates rather than gaining extra limbs.
 * Nearest-neighbour scaling keeps the pixel art crisp; the timer only
 * runs while showing.
 */
class MascotSpinner extends JComponent
{
	private static final int SCALE = 3;
	private static final BufferedImage MASCOT = load();
	// Sprite slices (16x16 grid): bottle body rows 0-9; per leg a 2px-wide
	// thigh column (rows 10-12) and a 4px-wide L-foot shin (rows 13-15).
	private static final BufferedImage BODY = slice(0, 0, 16, 10);
	private static final BufferedImage LEFT_THIGH = slice(5, 10, 2, 3);
	private static final BufferedImage RIGHT_THIGH = slice(10, 10, 2, 3);
	private static final BufferedImage LEFT_SHIN = slice(5, 13, 4, 3);
	private static final BufferedImage RIGHT_SHIN = slice(10, 13, 4, 3);
	private static final Color LIMB = new Color(140, 200, 140);

	private final Timer timer = new Timer(33, e -> repaint());
	private long startedAt;

	MascotSpinner()
	{
		setPreferredSize(new Dimension(16 * SCALE + 44, 16 * SCALE + 22));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 16 * SCALE + 22));
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

	private static BufferedImage slice(int x, int y, int w, int h)
	{
		return MASCOT == null ? null : MASCOT.getSubimage(x, y, w, h);
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
		// The 2-step: sway side to side at ~1 step/sec with TWO bounces per
		// step; weight shifts onto the leading leg each step.
		double beat = t * 1.1;
		double sway = Math.sin(beat * Math.PI);
		double bounce = Math.abs(Math.sin(beat * 2 * Math.PI));

		int bodyW = 16 * SCALE;
		int bodyX = (getWidth() - bodyW) / 2 + (int) Math.round(sway * 6.0);
		int groundY = getHeight() - 4;
		int shinH = 3 * SCALE;
		int shinTopY = groundY - shinH;
		int thighBase = 3 * SCALE;
		// Weight on the leading leg: its knee compresses, the trailing leg
		// stretches; the bounce dips both.
		int lean = (int) Math.round(sway * 2.5);
		int dip = (int) Math.round(bounce * 3.0);
		int leftThighH = thighBase - dip - lean;
		int rightThighH = thighBase - dip + lean;
		int bodyBottomY = shinTopY - Math.max(leftThighH, rightThighH);
		int bodyY = bodyBottomY - 10 * SCALE;

		// The L-feet stay planted (they do not sway with the body).
		int feetX = (getWidth() - bodyW) / 2;
		g2.drawImage(LEFT_SHIN, feetX + 5 * SCALE, shinTopY, 4 * SCALE, shinH, null);
		g2.drawImage(RIGHT_SHIN, feetX + 10 * SCALE, shinTopY, 4 * SCALE, shinH, null);
		// Thighs bridge from the swaying body down to the planted shins.
		g2.drawImage(LEFT_THIGH, feetX + 5 * SCALE + lean / 2, shinTopY - leftThighH, 2 * SCALE, leftThighH, null);
		g2.drawImage(RIGHT_THIGH, feetX + 10 * SCALE + lean / 2, shinTopY - rightThighH, 2 * SCALE, rightThighH, null);

		// Arms alternate on the beat - one up while the other is down.
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(LIMB);
		g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		int shoulderY = bodyY + 8 * SCALE;
		int armLen = 13;
		double leftAngle = Math.toRadians(35 + Math.sin(beat * 2 * Math.PI) * 40);
		double rightAngle = Math.toRadians(35 - Math.sin(beat * 2 * Math.PI) * 40);
		g2.drawLine(bodyX + 3 * SCALE, shoulderY,
			bodyX + 3 * SCALE - (int) Math.round(Math.cos(leftAngle) * armLen),
			shoulderY - (int) Math.round(Math.sin(leftAngle) * armLen));
		g2.drawLine(bodyX + 13 * SCALE, shoulderY,
			bodyX + 13 * SCALE + (int) Math.round(Math.cos(rightAngle) * armLen),
			shoulderY - (int) Math.round(Math.sin(rightAngle) * armLen));
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		// Body over the limbs.
		g2.drawImage(BODY, bodyX, bodyY, bodyW, 10 * SCALE, null);
		g2.dispose();
	}
}
