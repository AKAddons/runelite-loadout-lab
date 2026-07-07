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
	// The flask with the animated bits carved out: the juice band (rows
	// 7-8) is redrawn each frame so it can slosh, and the static corner
	// star (cols 12-14, rows 0-2) is replaced by snap stars at the hands.
	private static final BufferedImage BODY = prepareBody(slice(0, 0, 16, 10));
	private static final BufferedImage LEFT_THIGH = slice(5, 10, 2, 3);
	private static final BufferedImage RIGHT_THIGH = slice(10, 10, 2, 3);
	private static final BufferedImage LEFT_SHIN = slice(5, 13, 4, 3);
	private static final BufferedImage RIGHT_SHIN = slice(10, 13, 4, 3);
	private static final Color LIMB = new Color(140, 200, 140);
	private static final Color JUICE = new Color(208, 178, 102);

	private final Timer timer = new Timer(33, e -> repaint());
	private long startedAt;
	// Juice surface: a damped spring forced by the bottle's motion, so the
	// liquid lags behind each step and sloshes back. Tilt is in sprite rows
	// (positive = piled up on the left).
	private double sloshTilt;
	private double sloshVel;
	private int lastBodyX = Integer.MIN_VALUE;

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

	private static BufferedImage prepareBody(BufferedImage body)
	{
		if (body == null)
		{
			return null;
		}
		BufferedImage copy = new BufferedImage(body.getWidth(), body.getHeight(),
			BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = copy.createGraphics();
		g.drawImage(body, 0, 0, null);
		g.dispose();
		for (int y = 7; y <= 8; y++)
		{
			for (int x = 4; x <= 11; x++)
			{
				copy.setRGB(x, y, 0);
			}
		}
		for (int y = 0; y <= 2; y++)
		{
			for (int x = 12; x <= 14; x++)
			{
				copy.setRGB(x, y, 0);
			}
		}
		copy.setRGB(7, 4, 0); // the neck bubble - redrawn drifting each frame
		return copy;
	}

	/** A pixel plus-star that pops in and fades: age runs 0..1. */
	private static void drawSnapStar(Graphics2D g2, int cx, int cy, double age)
	{
		java.awt.Composite old = g2.getComposite();
		g2.setComposite(java.awt.AlphaComposite.getInstance(
			java.awt.AlphaComposite.SRC_OVER, (float) Math.max(0.0, 1.0 - age)));
		g2.setColor(JUICE);
		int arm = (int) Math.round(SCALE * (1.0 + age * 1.5));
		g2.fillRect(cx - SCALE / 2, cy - arm, SCALE, 2 * arm + SCALE);
		g2.fillRect(cx - arm, cy - SCALE / 2, 2 * arm + SCALE, SCALE);
		g2.setComposite(old);
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
		sloshTilt = 0;
		sloshVel = 0;
		lastBodyX = Integer.MIN_VALUE;
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
		final double A = 13.0; // travel per foot (wide enough to avoid foot clipping)

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

		// Arms up, groove in the forearms: upper arms hold a raised pose,
		// forearms wave gently left-right on the beat.
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(LIMB);
		g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		int shoulderY = bodyY + 8 * SCALE;
		int upperLen = 8;
		int foreLen = 10;
		double upperAngle = Math.toRadians(55); // raised, mostly static
		double wave = Math.sin(beat * Math.PI) * Math.toRadians(16);
		// Left arm
		int lsx = bodyX + 3 * SCALE;
		int lex = lsx - (int) Math.round(Math.cos(upperAngle) * upperLen);
		int ley = shoulderY - (int) Math.round(Math.sin(upperAngle) * upperLen);
		double lfa = Math.toRadians(90) + wave;
		int ltx = lex - (int) Math.round(Math.cos(lfa) * foreLen);
		int lty = ley - (int) Math.round(Math.sin(lfa) * foreLen);
		g2.drawLine(lsx, shoulderY, lex, ley);
		g2.drawLine(lex, ley, ltx, lty);
		// Right arm (forearm waves opposite for the groove)
		int rsx = bodyX + 13 * SCALE;
		int rex = rsx + (int) Math.round(Math.cos(upperAngle) * upperLen);
		int rey = shoulderY - (int) Math.round(Math.sin(upperAngle) * upperLen);
		double rfa = Math.toRadians(90) - wave;
		int rtx = rex + (int) Math.round(Math.cos(rfa) * foreLen);
		int rty = rey - (int) Math.round(Math.sin(rfa) * foreLen);
		g2.drawLine(rsx, shoulderY, rex, rey);
		g2.drawLine(rex, rey, rtx, rty);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		// Finger snaps: one snap every other wave extreme (half the waves
		// snap), a star pops beside that hand - alternating sides.
		double phase = beat % 4.0;
		if (phase >= 0.5 && phase < 1.0)
		{
			drawSnapStar(g2, ltx - 4, lty - 6, (phase - 0.5) / 0.5);
		}
		else if (phase >= 2.5 && phase < 3.0)
		{
			drawSnapStar(g2, rtx + 4, rty - 6, (phase - 2.5) / 0.5);
		}

		// Juice under the glass: spring the surface against the bottle's
		// motion, then fill the belly column by column so the tilted
		// surface stays chunky pixel steps.
		double push = lastBodyX == Integer.MIN_VALUE ? 0 : bodyX - lastBodyX;
		lastBodyX = bodyX;
		sloshVel += -sloshTilt * 0.20 - sloshVel * 0.12 + push * 0.45;
		sloshTilt = Math.max(-1.4, Math.min(1.4, sloshTilt + sloshVel));
		g2.setColor(JUICE);
		int juiceBottom = bodyY + 9 * SCALE;
		for (int i = 0; i < 8; i++)
		{
			double lever = (i + 0.5) / 8.0 - 0.5;
			int surfY = bodyY + (int) Math.round((7.0 + sloshTilt * lever * 2.0) * SCALE);
			surfY = Math.max(bodyY + 6 * SCALE, Math.min(juiceBottom - SCALE, surfY));
			g2.fillRect(bodyX + (4 + i) * SCALE, surfY, SCALE, juiceBottom - surfY);
		}

		// The neck bubble drifts one pixel left and right with the sway.
		int bubbleDx = (int) Math.round(Math.sin(beat * Math.PI));
		g2.fillRect(bodyX + (7 + bubbleDx) * SCALE, bodyY + 4 * SCALE, SCALE, SCALE);

		// Body over the limbs and juice (the walls cover the liquid's edges).
		g2.drawImage(BODY, bodyX, bodyY, bodyW, 10 * SCALE, null);
		g2.dispose();
	}
}
