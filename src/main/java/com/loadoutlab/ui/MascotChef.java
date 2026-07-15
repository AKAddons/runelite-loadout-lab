package com.loadoutlab.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import static com.loadoutlab.ui.MascotArt.JUICE;
import static com.loadoutlab.ui.MascotArt.LIMB;
import static com.loadoutlab.ui.MascotArt.SCALE;

/**
 * The bottle mascot, cooking while the optimizer thinks: toque on, feet
 * planted, left hand stirring a soup pot over a flickering fire, right
 * hand flipping a pancake in a pan. The whole frame is a pure function of
 * elapsed time (no spring state like the workout's slosh), which keeps
 * every motion loopable and lets the preview harness render exact frames.
 * Shares the sprite slices and leg renderer with MascotSpinner via
 * MascotArt.
 */
class MascotChef extends Mascot
{
	// Props palette. The soup is deliberately NOT the juice amber - the
	// mascot is not cooking itself.
	private static final Color POT = new Color(72, 72, 82);
	private static final Color POT_RIM = new Color(116, 116, 128);
	private static final Color SOUP = new Color(198, 88, 48);
	private static final Color SOUP_BUBBLE = new Color(236, 140, 92);
	private static final Color SPOON = new Color(172, 122, 62);
	private static final Color STEAM = new Color(228, 228, 232);
	private static final Color TOQUE = new Color(246, 246, 248);
	private static final Color TOQUE_SHADE = new Color(206, 206, 216);
	private static final Color PANCAKE_PALE = new Color(234, 198, 122);
	private static final Color PANCAKE_BROWN = new Color(198, 140, 62);
	private static final Color[] FIRE = {
		new Color(224, 74, 34), new Color(255, 148, 32), new Color(255, 208, 92),
	};
	/** One full pancake flip every this many seconds. */
	private static final double FLIP_PERIOD = 2.6;
	/** Stir lap time - a touch off the flip period so the two hands never
	 * fall into lockstep. */
	private static final double STIR_PERIOD = 0.9;

	@Override
	protected void render(Graphics2D g2, double t, int w, int h)
	{
		paintFrame(g2, t, w, h);
	}

	/** The whole scene at time t - static and deterministic so the preview
	 * harness can render exact frames of it. */
	static void paintFrame(Graphics2D g2, double t, int w, int h)
	{
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		int bodyW = 16 * SCALE;
		int centerX = (w - bodyW) / 2;
		int groundY = h - 4;
		int shinH = 3 * SCALE;
		// A gentle work-bob: down-up while the hands do the talking.
		int dip = (int) Math.round((Math.sin(t * 2 * Math.PI / STIR_PERIOD) + 1.0));
		int bodyBottomY = groundY - shinH - 3 * SCALE + dip;
		int bodyY = bodyBottomY - 10 * SCALE;
		int bodyX = centerX;

		// --- The pot, over its fire, left of the cook ---------------------
		int potW = 8 * SCALE;
		int potH = 4 * SCALE + 2;
		// Snug to the cook - any farther and the stirring arm has to
		// stretch into a noodle to reach the middle of the pot.
		int potX = bodyX - potW - 5;
		int potBottom = groundY - 4;
		int potTop = potBottom - potH;
		// Fire: four tongues lapping the pot base, each with its own
		// flicker; colour cycles so the flame shimmers.
		for (int i = 0; i < 4; i++)
		{
			double flick = Math.sin(t * 8 + i * 2.4) * Math.sin(t * 5.3 + i * 1.7);
			int fh = 3 + (int) Math.round(Math.abs(flick) * 4);
			int fx = potX + 2 + i * (potW - 7) / 3;
			g2.setColor(FIRE[Math.floorMod((int) (t * 10) + i, 3)]);
			g2.fillRect(fx, groundY - fh, 3, fh);
		}
		g2.setColor(POT);
		g2.fillRect(potX, potTop, potW, potH);
		g2.setColor(POT_RIM);
		g2.fillRect(potX - 2, potTop, potW + 4, 2);
		g2.fillRect(potX - 4, potTop + 5, 4, 2); // handle nub
		g2.setColor(SOUP);
		g2.fillRect(potX + 2, potTop + 2, potW - 4, 3);
		// Simmer blips, two spots surfacing on their own beats.
		g2.setColor(SOUP_BUBBLE);
		if (Math.sin(t * 4.1) > 0.55)
		{
			g2.fillRect(potX + 5, potTop + 2, 2, 2);
		}
		if (Math.sin(t * 3.3 + 2.1) > 0.55)
		{
			g2.fillRect(potX + potW - 8, potTop + 2, 2, 2);
		}
		// Steam: three puffs on a loop, wobbling as they rise and fade.
		java.awt.Composite opaque = g2.getComposite();
		for (int k = 0; k < 3; k++)
		{
			double age = (t * 0.45 + k / 3.0) % 1.0;
			int sy = potTop - 3 - (int) Math.round(age * 16);
			int sx = potX + potW / 2 - 2 + (int) Math.round(Math.sin((age * 3 + k) * 2.2) * 3);
			g2.setComposite(java.awt.AlphaComposite.getInstance(
				java.awt.AlphaComposite.SRC_OVER, (float) (0.7 * (1.0 - age))));
			g2.setColor(STEAM);
			int sz = 4 - (int) (age * 2);
			g2.fillRect(sx, sy, sz, sz);
		}
		g2.setComposite(opaque);

		// --- Legs planted, hips riding the bob -----------------------------
		MascotArt.drawLeg(g2, MascotArt.LEFT_THIGH, MascotArt.LEFT_SHIN,
			bodyX + 5 * SCALE, centerX + 5 * SCALE, bodyBottomY, groundY, shinH);
		MascotArt.drawLeg(g2, MascotArt.RIGHT_THIGH, MascotArt.RIGHT_SHIN,
			bodyX + 10 * SCALE, centerX + 10 * SCALE, bodyBottomY, groundY, shinH);

		// --- Arms ----------------------------------------------------------
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		int shoulderY = bodyY + 8 * SCALE;

		// Left hand circles above the pot; the spoon it holds traces the
		// soup in counter-phase so the shaft visibly leans through the lap.
		double stir = t * 2 * Math.PI / STIR_PERIOD;
		int handLX = potX + potW / 2 + (int) Math.round(Math.cos(stir) * 5);
		int handLY = potTop - 10 + (int) Math.round(Math.sin(stir) * 2);
		MascotArt.drawBentArm(g2, bodyX + 3 * SCALE, shoulderY, handLX, handLY, 4, true);

		// The pan hand: steady sizzle-jiggle, then the flip - a quick dip,
		// a pop upward, and a settle to catch.
		double p = (t / FLIP_PERIOD) % 1.0;
		double panDy;
		if (p < 0.05)
		{
			panDy = 2 * (p / 0.05); // windup: dip
		}
		else if (p < 0.10)
		{
			panDy = 2 - 5 * ((p - 0.05) / 0.05); // launch: pop up
		}
		else if (p < 0.20)
		{
			panDy = -3 + 3 * ((p - 0.10) / 0.10); // recover to level
		}
		else if (p >= 0.62 && p < 0.68)
		{
			panDy = 2 * (1.0 - (p - 0.62) / 0.06); // catch: absorb and settle
		}
		else if (p >= 0.58 && p < 0.62)
		{
			panDy = 2 * ((p - 0.58) / 0.04); // give under the landing
		}
		else
		{
			panDy = Math.sin(t * 12) * 0.8; // idle sizzle shake
		}
		int handRX = bodyX + bodyW + 7;
		int handRY = bodyY + 9 * SCALE + (int) Math.round(panDy);
		MascotArt.drawBentArm(g2, bodyX + 13 * SCALE, shoulderY, handRX, handRY, 3, false);

		// Spoon after the arm so the grip reads: hand down into the soup.
		int tipX = potX + potW / 2 + (int) Math.round(Math.cos(stir) * (potW / 2.0 - 5));
		g2.setColor(SPOON);
		g2.drawLine(handLX, handLY, tipX, potTop + 3);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		// Every third stir lap the seasoning lands just right and the soup
		// twinkles - the star alternates sides of the pot so it wanders.
		double lap = t / STIR_PERIOD;
		double lapFrac = lap % 1.0;
		if ((int) Math.floor(lap) % 3 == 2 && lapFrac < 0.55)
		{
			int starX = potX + potW / 2 - 5 + ((int) Math.floor(lap) % 2) * 10;
			MascotArt.drawSnapStar(g2, starX, potTop - 8, lapFrac / 0.55);
		}

		// --- Pan and pancake ------------------------------------------------
		int panLeft = handRX + 10;
		int panTopY = handRY - 4;
		g2.setColor(POT_RIM);
		g2.fillRect(handRX + 2, handRY - 1, 10, 2); // handle
		g2.fillRect(panLeft, panTopY, 20, 2); // bright rim so it reads on dark
		g2.setColor(POT);
		g2.fillRect(panLeft, panTopY + 2, 20, 3);
		int cakeCX = panLeft + 10;
		// The pancake is two-faced - pale batter one side, browned the
		// other - drawn as two layers so both read while it spins. The toss
		// turns it end over end one and a half times (an odd count of
		// half-turns, so it genuinely lands flipped), and the landed side
		// stays up through the catch until the next toss.
		int flips = (int) Math.floor(t / FLIP_PERIOD);
		boolean airborne = p >= 0.10 && p < 0.62;
		if (airborne)
		{
			double a = (p - 0.10) / 0.52;
			double cy = panTopY - 4 - Math.sin(a * Math.PI) * 24;
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			java.awt.geom.AffineTransform saved = g2.getTransform();
			g2.rotate(a * 3 * Math.PI, cakeCX, cy);
			drawCake(g2, cakeCX, (int) Math.round(cy), flips % 2 == 0);
			g2.setTransform(saved);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		}
		else
		{
			// After the catch the toss's half-turns have flipped it.
			boolean paleUp = (flips + (p >= 0.62 ? 1 : 0)) % 2 == 0;
			drawCake(g2, cakeCX, panTopY - 2, paleUp);
		}
		// A perfect catch earns a star, popping just past the pan.
		if (p >= 0.62 && p < 0.78)
		{
			MascotArt.drawSnapStar(g2, panLeft + 22, panTopY - 9, (p - 0.62) / 0.16);
		}
		// A sizzle wisp off the pan between flips.
		if (!airborne && Math.sin(t * 7) > 0.3)
		{
			g2.setComposite(java.awt.AlphaComposite.getInstance(
				java.awt.AlphaComposite.SRC_OVER, 0.4f));
			g2.setColor(STEAM);
			g2.fillRect(cakeCX + 4, panTopY - 7, 2, 2);
			g2.setComposite(opaque);
		}

		// --- Belly juice: barely simmering, it is warm in here --------------
		g2.setColor(JUICE);
		int juiceBottom = bodyY + 9 * SCALE;
		for (int i = 0; i < 8; i++)
		{
			int lift = Math.sin(t * 3.1 + i * 1.9) > 0.75 ? 1 : 0;
			int surfY = bodyY + 7 * SCALE - lift;
			g2.fillRect(bodyX + (4 + i) * SCALE, surfY, SCALE, juiceBottom - surfY);
		}
		// Neck bubble drifts with the stir instead of the step.
		int bubbleDx = Math.sin(stir) > 0 ? 1 : 0;
		g2.fillRect(bodyX + (7 + bubbleDx) * SCALE, bodyY + 4 * SCALE, SCALE, SCALE);

		// Body over limbs and juice, then the toque over the cork: a proper
		// chef's hat - straight cylinder rising off the cork, a puffy tuft
		// ballooning over the top of it.
		g2.drawImage(MascotArt.BODY, bodyX, bodyY, bodyW, 10 * SCALE, null);
		g2.setColor(TOQUE);
		g2.fillRect(bodyX + 5 * SCALE, bodyY - 3 * SCALE, 6 * SCALE, 5 * SCALE); // cylinder
		g2.fillRect(bodyX + 4 * SCALE + 1, bodyY - 4 * SCALE, 8 * SCALE - 2, SCALE); // tuft overhang
		g2.fillRect(bodyX + 5 * SCALE + 1, bodyY - 5 * SCALE, 6 * SCALE - 2, SCALE); // tuft dome
		g2.setColor(TOQUE_SHADE);
		// One shaded side wall makes the cylinder round; the brim underside
		// seats it on the cork.
		g2.fillRect(bodyX + 10 * SCALE, bodyY - 3 * SCALE, SCALE, 5 * SCALE);
		g2.fillRect(bodyX + 10 * SCALE, bodyY - 4 * SCALE, SCALE - 1, SCALE); // tuft's shaded cheek
		g2.fillRect(bodyX + 5 * SCALE, bodyY + 2 * SCALE - 2, 6 * SCALE, 2);
	}

	/** The pancake as two stacked layers - the up face and the down face -
	 * centred on (cx, cy), 14 wide by 4 thick. */
	private static void drawCake(Graphics2D g2, int cx, int cy, boolean paleUp)
	{
		g2.setColor(paleUp ? PANCAKE_PALE : PANCAKE_BROWN);
		g2.fillRect(cx - 7, cy - 2, 14, 2);
		g2.setColor(paleUp ? PANCAKE_BROWN : PANCAKE_PALE);
		g2.fillRect(cx - 7, cy, 14, 2);
	}
}
