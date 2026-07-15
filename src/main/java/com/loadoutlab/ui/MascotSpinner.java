package com.loadoutlab.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import static com.loadoutlab.ui.MascotArt.JUICE;
import static com.loadoutlab.ui.MascotArt.LIMB;
import static com.loadoutlab.ui.MascotArt.SCALE;

/**
 * The bottle mascot, working out - dancing, really - while the optimizer
 * thinks, under a disco ball off in the upper corner. Its own chunky L-legs
 * (the LL in Loadout Lab) do the bouncing: feet planted, thigh segments
 * squash and stretch at the knee as the body bobs, little arms pump and
 * snap. Sprite slices and the leg renderer live in MascotArt (shared with
 * MascotChef). Unlike the deterministic moods it keeps a bit of state (the
 * juice slosh spring), reset in onStart.
 */
class MascotSpinner extends Mascot
{
	private static final Color CORD = new Color(70, 70, 80);
	// Disco lights that twinkle on the ball and scatter around it.
	private static final Color[] GLINT = {
		new Color(240, 120, 180), new Color(120, 210, 240),
		new Color(240, 224, 120), new Color(150, 230, 150),
	};
	// Juice surface: a damped spring forced by the bottle's motion, so the
	// liquid lags behind each step and sloshes back. Tilt is in sprite rows
	// (positive = piled up on the left).
	private double sloshTilt;
	private double sloshVel;
	private int lastBodyX = Integer.MIN_VALUE;

	@Override
	protected void onStart()
	{
		sloshTilt = 0;
		sloshVel = 0;
		lastBodyX = Integer.MIN_VALUE;
	}

	@Override
	protected void render(Graphics2D g2, double t, int w, int h)
	{
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		// The disco ball, hung off-centre above the dancer, behind him - draw
		// it first so he grooves in front of it.
		drawDiscoBall(g2, t, w - 44, 17, 8);
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
		int centerX = (w - bodyW) / 2;
		int bodyX = centerX + (int) Math.round((leftDx + rightDx) / 2.0);
		int groundY = h - 4;
		int shinH = 3 * SCALE;
		int thighBase = 3 * SCALE;
		int dip = (int) Math.round((1.0 - arc) * 2.0);
		int bodyBottomY = groundY - shinH - thighBase + dip;
		int bodyY = bodyBottomY - 10 * SCALE;

		int leftLift = leftStepping ? (int) Math.round(arc * 6.0) : 0;
		int rightLift = leftStepping ? 0 : (int) Math.round(arc * 6.0);
		// Hips stay on the flask; feet land where the step takes them.
		MascotArt.drawLeg(g2, MascotArt.LEFT_THIGH, MascotArt.LEFT_SHIN, bodyX + 5 * SCALE,
			centerX + 5 * SCALE + (int) Math.round(leftDx), bodyBottomY, groundY - leftLift, shinH);
		MascotArt.drawLeg(g2, MascotArt.RIGHT_THIGH, MascotArt.RIGHT_SHIN, bodyX + 10 * SCALE,
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
			MascotArt.drawSnapStar(g2, ltx - 4, lty - 6, (phase - 0.5) / 0.5);
		}
		else if (phase >= 2.5 && phase < 3.0)
		{
			MascotArt.drawSnapStar(g2, rtx + 4, rty - 6, (phase - 2.5) / 0.5);
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

		// The neck bubble has two spots and flips the moment the bottle
		// steps into a far position: it lurches left when the body arrives
		// at its left extreme (start of count 2), right when it gets home.
		int bubbleDx = count >= 2 ? 0 : 1;
		g2.fillRect(bodyX + (7 + bubbleDx) * SCALE, bodyY + 4 * SCALE, SCALE, SCALE);

		// Body over the limbs and juice (the walls cover the liquid's edges).
		g2.drawImage(MascotArt.BODY, bodyX, bodyY, bodyW, 10 * SCALE, null);
	}

	/**
	 * A mirror-ball: a facet grid clipped to a circle, spherically shaded
	 * with a highlight column that sweeps across as it spins, coloured glints
	 * twinkling on its face, and a few sparkles of thrown light around it.
	 */
	private static void drawDiscoBall(Graphics2D g2, double t, int cx, int cy, int r)
	{
		// Cord up to the ceiling.
		g2.setColor(CORD);
		g2.fillRect(cx - 1, 0, 2, cy - r);

		java.awt.Shape oldClip = g2.getClip();
		g2.setClip(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
		int cell = 3;
		double hlX = cx + Math.sin(t * 2.5) * r * 0.9; // the spinning highlight
		for (int gy = cy - r; gy <= cy + r; gy += cell)
		{
			for (int gx = cx - r; gx <= cx + r; gx += cell)
			{
				double fx = gx + cell / 2.0;
				double fy = gy + cell / 2.0;
				double dxn = (fx - cx) / r;
				double dyn = (fy - cy) / r;
				double edge = Math.max(0, 1 - (dxn * dxn + dyn * dyn)); // 1 centre, 0 rim
				double hl = Math.max(0, 1 - Math.abs(fx - hlX) / (r * 0.6));
				double b = 0.30 + edge * 0.30 + hl * 0.55;
				int v = (int) Math.min(235, 70 + b * 165);
				g2.setColor(new Color(v, Math.min(255, v + 8), Math.min(255, v + 22)));
				g2.fillRect(gx, gy, cell - 1, cell - 1); // the 1px gap = facet lines
			}
		}
		g2.setClip(oldClip);

		// Coloured glints twinkling across the facets.
		for (int i = 0; i < 3; i++)
		{
			double gp = t * 3.0 + i * 2.3;
			if (Math.sin(gp) > 0.4)
			{
				int gx = cx + (int) Math.round(Math.cos(gp * 1.7 + i) * r * 0.55);
				int gy = cy + (int) Math.round(Math.sin(gp * 1.3 + i) * r * 0.55);
				g2.setColor(GLINT[i % GLINT.length]);
				g2.fillRect(gx, gy, 2, 2);
			}
		}
		// A dark rim to seat the ball.
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setColor(CORD);
		g2.drawOval(cx - r, cy - r, 2 * r, 2 * r);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		// Sparkles of thrown light around it, twinkling on and off.
		for (int i = 0; i < 5; i++)
		{
			if (Math.sin(t * 2.2 + i * 1.7) > 0.55)
			{
				int sx = cx + (int) Math.round(Math.cos(i * 2.1) * (r + 6 + (i % 3) * 5));
				int sy = cy + (int) Math.round(Math.sin(i * 1.5) * (r + 5 + (i % 2) * 6));
				g2.setColor(GLINT[i % GLINT.length]);
				g2.fillRect(sx, sy - 1, 1, 3);
				g2.fillRect(sx - 1, sy, 3, 1);
			}
		}
	}
}
