package com.loadoutlab.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.Timer;

import static com.loadoutlab.ui.MascotArt.JUICE;
import static com.loadoutlab.ui.MascotArt.SCALE;

/**
 * The bottle mascot at the World Cup, doing keepy-uppies while the
 * optimizer thinks: four alternating foot juggles, a bigger pop up onto
 * his head, a wobbling balance there (the ball counter-rolls as the body
 * sways under it), then the drop back onto the foot that starts the
 * juggle again. Deterministic like the chef - the whole frame is a pure
 * function of time - and on its own clocks: a kick beat and a wobble
 * frequency that belong to this mood alone. Even the neck bubble plays
 * along: it tracks the ball like an eye instead of flipping on a step
 * count. Stars pop for the header-settle and the clean drop-catch.
 */
class MascotStriker extends JComponent
{
	private static final Color BALL = new Color(240, 240, 240);
	private static final Color BALL_PATCH = new Color(38, 38, 44);
	/** Ball radius in px (drawn 2R+1 wide). */
	private static final int R = 5;
	// The World Cup backdrop: a dim globe turning behind the whole routine.
	// Muted tones so it stays scenery - the ball is the star.
	private static final Color OCEAN = new Color(44, 58, 82);
	private static final Color LAND = new Color(62, 96, 66);
	private static final Color GLOBE_RIM = new Color(74, 88, 108);
	/** Globe radius and its own slow lap - unrelated to every other clock. */
	private static final int GLOBE_R = 26;
	private static final double GLOBE_SPIN = 9.7;
	/** Continent blobs as {latitude y-offset, longitude deg, width, height}
	 * - chunky pixel landmasses, wrapping as the globe turns. */
	private static final int[][] CONTINENTS = {
		{-12, 20, 10, 5}, {-4, 70, 8, 6}, {6, 130, 12, 5}, {-10, 200, 7, 4},
		{4, 260, 9, 6}, {12, 310, 8, 4}, {14, 90, 4, 2}, {-16, 160, 4, 2},
	};

	// The routine's own clocks. One cycle: 4 kick flights, the pop up to
	// the head, the balance, the drop back down.
	private static final double KICK = 0.62;
	private static final double POP = 0.55;
	private static final double BALANCE = 1.7;
	private static final double DROP = 0.35;
	private static final double JUGGLE = 4 * KICK;
	private static final double CYCLE = JUGGLE + POP + BALANCE + DROP;
	/** Balance sway lap - unrelated to the kick beat on purpose. */
	private static final double WOBBLE = 0.7;

	private final Timer timer = new Timer(33, e -> repaint());
	private long startedAt;

	MascotStriker()
	{
		setPreferredSize(new Dimension(16 * SCALE + 96, 16 * SCALE + 32));
		setMaximumSize(new Dimension(Integer.MAX_VALUE, 16 * SCALE + 32));
		setAlignmentX(LEFT_ALIGNMENT);
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
		if (!MascotArt.available())
		{
			return;
		}
		Graphics2D g2 = (Graphics2D) g.create();
		paintFrame(g2, (System.currentTimeMillis() - startedAt) / 1000.0,
			getWidth(), getHeight());
		g2.dispose();
	}

	/** The whole scene at time t - static and deterministic so the preview
	 * harness can render exact frames of it. */
	static void paintFrame(Graphics2D g2, double t, int w, int h)
	{
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		double u = t % CYCLE;
		int bodyW = 16 * SCALE;
		int centerX = (w - bodyW) / 2;
		int groundY = h - 4;
		int shinH = 3 * SCALE;

		// The globe turns behind the whole show, first so everything plays
		// in front of it.
		drawGlobe(g2, t, centerX + bodyW / 2, groundY - 40);

		// Ball contact points: on top of each planted foot.
		int leftBallX = centerX + 5 * SCALE + 2 * SCALE;
		int rightBallX = centerX + 10 * SCALE + 2 * SCALE;
		int contactY = groundY - shinH - 5 - R;

		// Balance sway: the body leans under the ball, eased in and out so
		// the settle and the drop are calm moments.
		double lean = 0;
		boolean balancing = u >= JUGGLE + POP && u < JUGGLE + POP + BALANCE;
		if (balancing)
		{
			double s = (u - JUGGLE - POP) / BALANCE;
			double env = Math.min(1.0, Math.min(s * 4.0, (1.0 - s) * 4.0));
			lean = 2.5 * env * Math.sin(u * 2 * Math.PI / WOBBLE);
		}
		int bodyX = centerX + (int) Math.round(lean);

		// Body dips a touch at each foot contact - absorbing the ball.
		double dipAmt = contactPulse(u, 0) + contactPulse(u, KICK)
			+ contactPulse(u, 2 * KICK) + contactPulse(u, 3 * KICK)
			+ contactPulse(u, JUGGLE);
		int bodyBottomY = groundY - shinH - 3 * SCALE + (int) Math.round(dipAmt * 2);
		int bodyY = bodyBottomY - 10 * SCALE;
		int headTopY = bodyY - R - 1;

		// --- The ball: piecewise flights, all endpoints shared -------------
		double ballX;
		double ballY;
		double spin; // accumulated roll, radians
		if (u < JUGGLE)
		{
			int k = (int) (u / KICK);
			double s = (u - k * KICK) / KICK;
			double fromX = k % 2 == 0 ? leftBallX : rightBallX;
			double toX = k % 2 == 0 ? rightBallX : leftBallX;
			ballX = fromX + (toX - fromX) * s;
			// Low hops - keepy-uppies live at shin height, not belly height.
			ballY = contactY - 9 * 4 * s * (1 - s);
			spin = k * Math.PI + s * Math.PI;
		}
		else if (u < JUGGLE + POP)
		{
			// The pop: from the left foot up onto the head.
			double s = (u - JUGGLE) / POP;
			double e = s * s * (3 - 2 * s);
			ballX = leftBallX + (centerX + 7 * SCALE + 2 - leftBallX) * e;
			ballY = contactY + (headTopY - contactY) * e - 10 * 4 * s * (1 - s);
			spin = 4 * Math.PI + s * 2 * Math.PI;
		}
		else if (balancing)
		{
			// On the head: the ball counter-rolls against the sway - the
			// body does the balancing underneath it.
			ballX = centerX + 7 * SCALE + 2 - lean * 1.2;
			ballY = headTopY;
			spin = 6 * Math.PI + Math.sin(u * 2 * Math.PI / WOBBLE) * 0.4;
		}
		else
		{
			// The drop: rolls off and falls back to the left foot.
			double s = (u - JUGGLE - POP - BALANCE) / DROP;
			ballX = centerX + 7 * SCALE + 2 + (leftBallX - centerX - 7 * SCALE - 2) * s;
			ballY = headTopY + (contactY - headTopY) * s * s;
			spin = 6 * Math.PI + s * Math.PI / 2;
		}

		// --- Legs: planted, the receiving foot rising to meet the ball -----
		double liftL = contactPulse(u, 0) + contactPulse(u, 2 * KICK) + contactPulse(u, JUGGLE);
		double liftR = contactPulse(u, KICK) + contactPulse(u, 3 * KICK);
		MascotArt.drawLeg(g2, MascotArt.LEFT_THIGH, MascotArt.LEFT_SHIN,
			bodyX + 5 * SCALE, centerX + 5 * SCALE, bodyBottomY,
			groundY - (int) Math.round(liftL * 5), shinH);
		MascotArt.drawLeg(g2, MascotArt.RIGHT_THIGH, MascotArt.RIGHT_SHIN,
			bodyX + 10 * SCALE, centerX + 10 * SCALE, bodyBottomY,
			groundY - (int) Math.round(liftR * 5), shinH);

		// --- Arms out for balance, tilting like a tightrope walker's, and
		// flapping a touch with each kick's absorb ---------------------------
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		int shoulderY = bodyY + 8 * SCALE;
		int armBaseY = shoulderY - (balancing ? 7 : 2);
		int tilt = (int) Math.round(lean * 1.6);
		MascotArt.drawBentArm(g2, bodyX + 3 * SCALE, shoulderY,
			bodyX - 8, armBaseY - tilt + (int) Math.round(liftL * 3), 3, false);
		MascotArt.drawBentArm(g2, bodyX + 13 * SCALE, shoulderY,
			bodyX + bodyW + 8, armBaseY + tilt + (int) Math.round(liftR * 3), 3, false);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		// --- Belly juice sloshes with the routine, not a spring ------------
		g2.setColor(JUICE);
		int juiceBottom = bodyY + 9 * SCALE;
		double tiltJuice = balancing ? -lean * 0.5
			: Math.sin(u * Math.PI / KICK) * 0.7;
		for (int i = 0; i < 8; i++)
		{
			double lever = (i + 0.5) / 8.0 - 0.5;
			int surfY = bodyY + (int) Math.round((7.0 + tiltJuice * lever * 2.0) * SCALE);
			surfY = Math.max(bodyY + 6 * SCALE, Math.min(juiceBottom - SCALE, surfY));
			g2.fillRect(bodyX + (4 + i) * SCALE, surfY, SCALE, juiceBottom - surfY);
		}
		// The neck bubble is an eye: the pupil leans toward the ball from
		// its socket in the glass neck - down and side to side through the
		// juggle, straight up during the balance. Drawn under the body, so
		// the neck walls mask it at the extremes like a real eyeball.
		double homeX = bodyX + 7.5 * SCALE;
		double homeY = bodyY + 4.5 * SCALE;
		double ex = ballX - homeX;
		double ey = ballY - homeY;
		double el = Math.max(1e-6, Math.sqrt(ex * ex + ey * ey));
		g2.fillRect((int) Math.round(homeX + ex / el * 3.5 - SCALE / 2.0),
			(int) Math.round(homeY + ey / el * 3.0 - SCALE / 2.0), SCALE, SCALE);

		// Body over limbs and juice.
		g2.drawImage(MascotArt.BODY, bodyX, bodyY, bodyW, 10 * SCALE, null);

		// --- The ball itself, over everything (it plays out front) ---------
		drawBall(g2, (int) Math.round(ballX), (int) Math.round(ballY), spin);

		// Stars: one for the header-settle, one for the clean drop-catch.
		if (balancing && u - (JUGGLE + POP) < 0.35)
		{
			MascotArt.drawSnapStar(g2, (int) ballX + R + 7, (int) ballY - 4,
				(u - JUGGLE - POP) / 0.35);
		}
		if (u < 0.3)
		{
			MascotArt.drawSnapStar(g2, leftBallX - R - 7, contactY - 2, u / 0.3);
		}
	}

	/** A smooth 0..1..0 pulse in a short window around a contact moment,
	 * wrapping across the cycle boundary (the drop-catch IS the first
	 * kick's contact). */
	private static double contactPulse(double u, double tc)
	{
		double d = Math.abs(u - tc);
		d = Math.min(d, CYCLE - d);
		double win = 0.14;
		return d >= win ? 0 : Math.cos(d / win * Math.PI / 2);
	}

	/**
	 * A dim globe rotating on its own slow clock: ocean disc, then each
	 * continent projected onto the front hemisphere - x rides sin of its
	 * drifting longitude (bounded by the disc at its latitude), width
	 * foreshortens toward the limb, and the disc clip catches any spill.
	 */
	private static void drawGlobe(Graphics2D g2, double t, int cx, int cy)
	{
		java.awt.Shape oldClip = g2.getClip();
		g2.setColor(OCEAN);
		g2.fillOval(cx - GLOBE_R, cy - GLOBE_R, 2 * GLOBE_R, 2 * GLOBE_R);
		g2.clip(new java.awt.geom.Ellipse2D.Double(
			cx - GLOBE_R, cy - GLOBE_R, 2 * GLOBE_R, 2 * GLOBE_R));
		g2.setColor(LAND);
		for (int[] blob : CONTINENTS)
		{
			double lon = Math.toRadians(blob[1]) + t * 2 * Math.PI / GLOBE_SPIN;
			double c = Math.cos(lon);
			if (c < 0.1)
			{
				continue; // around the back
			}
			double latR = Math.sqrt(Math.max(0, GLOBE_R * GLOBE_R - blob[0] * blob[0]));
			int px = cx + (int) Math.round(Math.sin(lon) * latR * 0.9);
			int bw = Math.max(2, (int) Math.round(blob[2] * (0.4 + 0.6 * c)));
			g2.fillRect(px - bw / 2, cy + blob[0] - blob[3] / 2, bw, blob[3]);
		}
		g2.setClip(oldClip);
		g2.setColor(GLOBE_RIM);
		g2.drawOval(cx - GLOBE_R, cy - GLOBE_R, 2 * GLOBE_R, 2 * GLOBE_R);
	}

	/** The ball: a white disc with three dark patches riding its roll. */
	private static void drawBall(Graphics2D g2, int cx, int cy, double spin)
	{
		g2.setColor(BALL);
		g2.fillOval(cx - R, cy - R, 2 * R + 1, 2 * R + 1);
		g2.setColor(BALL_PATCH);
		for (int i = 0; i < 3; i++)
		{
			double a = spin + i * 2.094;
			int px = cx + (int) Math.round(Math.cos(a) * (R - 1.5));
			int py = cy + (int) Math.round(Math.sin(a) * (R - 1.5));
			g2.fillRect(px - 1, py - 1, 2, 2);
		}
	}
}
