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

	/** Thigh hangs from the flask hip, shin sits at the FOOT's spot; the
	 * knee (thigh drawn toward the foot, squashing on lift) keeps the leg
	 * connected top and bottom. */
	private static void drawLeg(Graphics2D g2, BufferedImage thigh, BufferedImage shin,
		int hipX, int footX, int hipY, int footY, int shinH)
	{
		int shinTopY = footY - shinH;
		int thighH = Math.max(4, shinTopY - hipY);
		int kneeX = (hipX + footX) / 2;
		g2.drawImage(thigh, kneeX, hipY, 2 * SCALE, thighH, null);
		g2.drawImage(shin, footX, shinTopY, 4 * SCALE, shinH, null);
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
		// The real 2-step, a 4-count: right foot steps LEFT, left foot
		// steps LEFT, left foot steps RIGHT, right foot steps RIGHT. Each
		// stepping foot lifts, arcs to its NEW spot, and plants; the body
		// follows the midpoint of the feet.
		double beat = t * 1.6;
		int count = (int) Math.floor(beat) % 4;
		double raw = beat - Math.floor(beat);
		double eased = raw * raw * (3 - 2 * raw); // smoothstep across the step
		double arc = Math.sin(raw * Math.PI);
		final double A = 8.0; // travel per foot

		// Foot x-offsets (from their home columns) at the START of each
		// count, per the user's choreography.
		double[] leftStart = {0, 0, -A, 0};
		double[] leftEnd = {0, -A, 0, 0};
		double[] rightStart = {0, -A, -A, -A};
		double[] rightEnd = {-A, -A, -A, 0};
		boolean leftStepping = count == 1 || count == 2;
		double leftDx = leftStart[count] + (leftEnd[count] - leftStart[count]) * eased;
		double rightDx = rightStart[count] + (rightEnd[count] - rightStart[count]) * eased;

		int bodyW = 16 * SCALE;
		int centerX = (getWidth() - bodyW) / 2;
		int bodyX = centerX + (int) Math.round((leftDx + rightDx) / 2.0);
		int groundY = getHeight() - 4;
		int shinH = 3 * SCALE;
		int thighBase = 3 * SCALE;
		int dip = (int) Math.round((1.0 - arc) * 2.0);
		int bodyBottomY = groundY - shinH - thighBase + dip;
		int bodyY = bodyBottomY - 10 * SCALE;

		int leftLift = leftStepping ? (int) Math.round(arc * 6.0) : 0;
		int rightLift = leftStepping ? 0 : (int) Math.round(arc * 6.0);
		// Hips stay on the flask; feet land where the step takes them.
		drawLeg(g2, LEFT_THIGH, LEFT_SHIN, bodyX + 5 * SCALE,
			centerX + 5 * SCALE + (int) Math.round(leftDx), bodyBottomY, groundY - leftLift, shinH);
		drawLeg(g2, RIGHT_THIGH, RIGHT_SHIN, bodyX + 10 * SCALE,
			centerX + 10 * SCALE + (int) Math.round(rightDx), bodyBottomY, groundY - rightLift, shinH);

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
