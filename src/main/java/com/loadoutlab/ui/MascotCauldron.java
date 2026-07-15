package com.loadoutlab.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import static com.loadoutlab.ui.MascotArt.JUICE;
import static com.loadoutlab.ui.MascotArt.SCALE;

/**
 * The bottle mascot at Halloween, brewing while the optimizer thinks: witch
 * hat on, one hand working a ladle round a bubbling toxic-green cauldron over
 * an eerie flame, the other tossing an eyeball in with a splash, green smoke
 * curling up and a bat flapping past. A reskin of the chef POSE, but on its
 * own clocks - stir lap, toss period and bat fly-by all deliberately
 * incommensurate - so it reads as its own mood, not a recolour. Deterministic
 * (a pure function of t) so the preview harness can reproduce frames.
 */
class MascotCauldron extends Mascot
{
	private static final Color CAULDRON = new Color(38, 38, 46);
	private static final Color CAULDRON_RIM = new Color(70, 70, 84);
	private static final Color BREW = new Color(84, 178, 78);
	private static final Color BREW_GLOW = new Color(150, 232, 120);
	private static final Color SMOKE = new Color(150, 205, 150);
	// A purple hat with a black band pops off the dark background far better
	// than the old near-black hat did.
	private static final Color HAT = new Color(132, 76, 172);
	private static final Color HAT_SHADE = new Color(96, 52, 130);
	private static final Color HAT_BAND = new Color(22, 20, 28);
	private static final Color HAT_BUCKLE = new Color(214, 190, 96);
	private static final Color EYE_WHITE = new Color(234, 234, 226);
	private static final Color EYE_IRIS = new Color(150, 60, 60);
	private static final Color BAT = new Color(26, 24, 34);
	// The jack-o'-lantern on the ground.
	private static final Color PUMPKIN = new Color(226, 120, 42);
	private static final Color PUMPKIN_RIB = new Color(188, 92, 28);
	private static final Color PUMPKIN_STEM = new Color(108, 132, 74);
	private static final Color PUMPKIN_FACE = new Color(255, 208, 96);
	// Eerie fire: green with a purple lick.
	private static final Color[] FLAME = {
		new Color(120, 220, 90), new Color(70, 200, 140), new Color(150, 96, 200),
	};

	/** The mood's own clocks - none a multiple of another. */
	private static final double STIR_PERIOD = 1.0;
	private static final double TOSS_PERIOD = 3.1;
	private static final double BAT_PERIOD = 5.3;

	@Override
	protected void render(Graphics2D g2, double t, int w, int h)
	{
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		int bodyW = 16 * SCALE;
		int centerX = (w - bodyW) / 2;
		int groundY = h - 4;
		int shinH = 3 * SCALE;
		double stir = t * 2 * Math.PI / STIR_PERIOD;
		int dip = (int) Math.round(Math.sin(stir) + 1.0);
		int bodyBottomY = groundY - shinH - 3 * SCALE + dip;
		int bodyY = bodyBottomY - 10 * SCALE;
		int bodyX = centerX;

		java.awt.Composite opaque = g2.getComposite();

		// --- Bat fly-by, far behind everything ----------------------------
		double bp = (t / BAT_PERIOD) % 1.0;
		if (bp < 0.55)
		{
			int bx = (int) Math.round(w - bp / 0.55 * (w + 12)) + 6;
			int by = bodyY - 6 * SCALE + (int) Math.round(Math.sin(bp * 12) * 4);
			int flap = (int) Math.round(Math.sin(t * 22) * 3);
			g2.setColor(BAT);
			g2.fillRect(bx - 1, by - 1, 3, 3); // body
			g2.fillRect(bx - 6, by - flap, 5, 2); // left wing
			g2.fillRect(bx + 2, by - flap, 5, 2); // right wing
		}

		// --- Cauldron over its flame, left of the brewer ------------------
		int cauldW = 9 * SCALE;
		int cauldH = 5 * SCALE;
		int cauldX = bodyX - cauldW - 4;
		int cauldBottom = groundY - 3;
		int cauldTop = cauldBottom - cauldH;
		int cauldCX = cauldX + cauldW / 2;
		// Flame: green tongues with the odd purple lick, each flickering.
		for (int i = 0; i < 4; i++)
		{
			double flick = Math.sin(t * 9 + i * 2.1) * Math.sin(t * 6.1 + i * 1.3);
			int fh = 3 + (int) Math.round(Math.abs(flick) * 5);
			int fx = cauldX + 2 + i * (cauldW - 6) / 3;
			g2.setColor(FLAME[Math.floorMod((int) (t * 11) + i, FLAME.length)]);
			g2.fillRect(fx, groundY - fh, 3, fh);
		}
		// Two little feet, then the bulbous body, then the lip.
		g2.setColor(CAULDRON);
		g2.fillRect(cauldX + 2, cauldBottom - 1, 2, 3);
		g2.fillRect(cauldX + cauldW - 4, cauldBottom - 1, 2, 3);
		g2.fillRoundRect(cauldX, cauldTop, cauldW, cauldH, 8, 8);
		g2.setColor(CAULDRON_RIM);
		g2.fillRect(cauldX - 2, cauldTop, cauldW + 4, 2);
		g2.fillRect(cauldX - 4, cauldTop + 4, 3, 2); // handle nub

		// Brew surface + a soft glow that breathes.
		g2.setColor(BREW);
		g2.fillOval(cauldX + 2, cauldTop + 1, cauldW - 4, 4);
		g2.setColor(BREW_GLOW);
		if (Math.sin(t * 3.7) > 0.2)
		{
			g2.fillRect(cauldCX - 3, cauldTop + 2, 2, 2);
		}
		if (Math.sin(t * 2.9 + 1.7) > 0.4)
		{
			g2.fillRect(cauldCX + 2, cauldTop + 2, 2, 2);
		}
		// Bubbles rise up the surface and pop into a little green ring.
		for (int k = 0; k < 3; k++)
		{
			double age = (t * 1.3 + k / 3.0) % 1.0;
			int bxp = cauldCX - 4 + k * 4;
			if (age < 0.75)
			{
				int byp = cauldTop + 2 - (int) Math.round(age * 3);
				g2.setColor(BREW_GLOW);
				g2.fillRect(bxp, byp, 2, 2);
			}
			else
			{
				int r = (int) Math.round((age - 0.75) / 0.25 * 3);
				g2.setColor(BREW);
				g2.drawOval(bxp - r, cauldTop - r - 1, 2 * r + 2, 2 * r + 2);
			}
		}
		// Green smoke curling up and fading.
		for (int k = 0; k < 3; k++)
		{
			double age = (t * 0.4 + k / 3.0) % 1.0;
			int sy = cauldTop - 3 - (int) Math.round(age * 18);
			int sx = cauldCX - 2 + (int) Math.round(Math.sin((age * 4 + k) * 2.0) * 4);
			g2.setComposite(java.awt.AlphaComposite.getInstance(
				java.awt.AlphaComposite.SRC_OVER, (float) (0.55 * (1.0 - age))));
			g2.setColor(SMOKE);
			int sz = 4 - (int) (age * 2);
			g2.fillRect(sx, sy, sz, sz);
		}
		g2.setComposite(opaque);

		// --- A jack-o'-lantern on the ground, off to the right ------------
		drawPumpkin(g2, t, bodyX + bodyW + 12, groundY);

		// --- Legs planted, hips riding the bob ----------------------------
		MascotArt.drawLeg(g2, MascotArt.LEFT_THIGH, MascotArt.LEFT_SHIN,
			bodyX + 5 * SCALE, centerX + 5 * SCALE, bodyBottomY, groundY, shinH);
		MascotArt.drawLeg(g2, MascotArt.RIGHT_THIGH, MascotArt.RIGHT_SHIN,
			bodyX + 10 * SCALE, centerX + 10 * SCALE, bodyBottomY, groundY, shinH);

		// --- Arms ---------------------------------------------------------
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		int shoulderY = bodyY + 8 * SCALE;

		// Left hand circles the ladle round the brew.
		int ladleHandX = cauldCX + (int) Math.round(Math.cos(stir) * 5);
		int ladleHandY = cauldTop - 9 + (int) Math.round(Math.sin(stir) * 2);
		MascotArt.drawBentArm(g2, bodyX + 3 * SCALE, shoulderY, ladleHandX, ladleHandY, 4, true);

		// Right hand tosses an ingredient into the pot on the toss clock:
		// hold, flick up, and the eyeball arcs over into the brew.
		double tp = (t / TOSS_PERIOD) % 1.0;
		boolean airborne = tp >= 0.18 && tp < 0.5;
		int restHandX = bodyX + bodyW + 3;
		int restHandY = bodyY + 9 * SCALE;
		int tossHandY = tp < 0.18 ? restHandY - (int) Math.round(tp / 0.18 * 5) : restHandY;
		MascotArt.drawBentArm(g2, bodyX + 13 * SCALE, shoulderY, restHandX,
			airborne ? restHandY - 3 : tossHandY, 3, false);

		// Ladle shaft after the arm so the grip reads.
		int ladleTipX = cauldCX + (int) Math.round(Math.cos(stir) * (cauldW / 2.0 - 4));
		g2.setColor(CAULDRON_RIM);
		g2.drawLine(ladleHandX, ladleHandY, ladleTipX, cauldTop + 2);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		// The eyeball: held, then arcs from the hand into the cauldron.
		if (tp < 0.18)
		{
			drawEyeball(g2, restHandX, tossHandY - 4, stir);
		}
		else if (airborne)
		{
			double s = (tp - 0.18) / 0.32;
			int ex = (int) Math.round(restHandX + (cauldCX - restHandX) * s);
			int ey = (int) Math.round((restHandY - 4) + (cauldTop - (restHandY - 4)) * s
				- Math.sin(s * Math.PI) * 16);
			drawEyeball(g2, ex, ey, stir + s * 8);
		}
		else if (tp < 0.78)
		{
			// The eye hits the brew: a big poof erupts from the cauldron.
			double s = (tp - 0.5) / 0.28;
			drawPoof(g2, cauldCX, cauldTop, s);
			// Signature amber plus-stars burst over the pot as the brew takes
			// the eye - the Halloween echo of the chef's well-seasoned sparkle.
			for (int i = 0; i < 2; i++)
			{
				int sx = cauldCX + (i == 0 ? -6 : 6);
				int sy = cauldTop - 5 - (int) Math.round(s * 7);
				MascotArt.drawSnapStar(g2, sx, sy, Math.min(1.0, s * 1.3));
			}
		}
		g2.setComposite(opaque);

		// --- Belly juice, gently simmering in the warm ---------------------
		g2.setColor(JUICE);
		int juiceBottom = bodyY + 9 * SCALE;
		for (int i = 0; i < 8; i++)
		{
			int lift = Math.sin(t * 3.1 + i * 1.9) > 0.75 ? 1 : 0;
			int surfY = bodyY + 7 * SCALE - lift;
			g2.fillRect(bodyX + (4 + i) * SCALE, surfY, SCALE, juiceBottom - surfY);
		}
		int bubbleDx = Math.sin(stir) > 0 ? 1 : 0;
		g2.fillRect(bodyX + (7 + bubbleDx) * SCALE, bodyY + 4 * SCALE, SCALE, SCALE);

		// Body over limbs and juice, then the witch hat on the cork.
		g2.drawImage(MascotArt.BODY, bodyX, bodyY, bodyW, 10 * SCALE, null);
		drawWitchHat(g2, bodyX, bodyY);
	}

	/**
	 * The eye-meets-brew eruption: a bright green flash at contact, then a
	 * mushrooming ring of smoke-and-glow puffs that expand up and out and
	 * fade. age runs 0..1.
	 */
	private static void drawPoof(Graphics2D g2, int cx, int cy, double age)
	{
		java.awt.Composite old = g2.getComposite();
		// Contact flash - a quick bright bloom the first instant.
		if (age < 0.25)
		{
			g2.setComposite(java.awt.AlphaComposite.getInstance(
				java.awt.AlphaComposite.SRC_OVER, (float) (1.0 - age / 0.25)));
			g2.setColor(BREW_GLOW);
			int r = 4 + (int) Math.round(age / 0.25 * 5);
			g2.fillOval(cx - r, cy - r - 1, 2 * r, 2 * r);
		}
		// A curling plume: puffs erupt in quick succession and climb straight
		// up, each swinging further side to side (an animated S-curl) and
		// fading as it rises - a vertical swirl of smoke, not a mushroom.
		int puffs = 10;
		for (int i = 0; i < puffs; i++)
		{
			// Each puff is born a little after the last, so the plume streams
			// upward rather than blooming all at once.
			double life = age * 1.35 - i * 0.09;
			if (life <= 0 || life > 1)
			{
				continue;
			}
			int py = cy - 1 - (int) Math.round(life * 26);
			// Curl widens toward the top and drifts over time, so the column
			// snakes as it rises.
			double curl = Math.sin(life * 7.0 + i * 1.2) * (1.5 + life * 5.0);
			int px = cx + (int) Math.round(curl);
			int size = Math.max(2, (int) Math.round(5 * (1.0 - life * 0.5)));
			g2.setComposite(java.awt.AlphaComposite.getInstance(
				java.awt.AlphaComposite.SRC_OVER, (float) Math.max(0.0, 0.9 * (1.0 - life))));
			g2.setColor(i % 2 == 0 ? SMOKE : BREW_GLOW);
			g2.fillRect(px - size / 2, py - size / 2, size, size);
		}
		g2.setComposite(old);
	}

	/** A gently-looking eyeball: white ball, dark-red iris, tiny pupil that
	 * drifts so it looks around. */
	private static void drawEyeball(Graphics2D g2, int cx, int cy, double look)
	{
		g2.setColor(EYE_WHITE);
		g2.fillOval(cx - 3, cy - 3, 6, 6);
		int px = cx + (int) Math.round(Math.cos(look) * 1.5);
		int py = cy + (int) Math.round(Math.sin(look) * 1.5);
		g2.setColor(EYE_IRIS);
		g2.fillRect(px - 1, py - 1, 2, 2);
	}

	/** A pointed witch hat pulled DOWN onto the flask so the brim rests on
	 * its shoulders and the band sits over the cork: a purple brim and cone
	 * (shaded down the right for form) leaning into a bent tip, a black band
	 * with a gold buckle. */
	private static void drawWitchHat(Graphics2D g2, int bodyX, int bodyY)
	{
		// Drop the whole hat down a block so it covers the top of the potion.
		int brimY = bodyY + SCALE;
		// A wide brim that overhangs the shoulders (classic witch silhouette).
		g2.setColor(HAT);
		g2.fillRect(bodyX + 2 * SCALE, brimY, 12 * SCALE, SCALE);
		g2.setColor(HAT_SHADE);
		g2.fillRect(bodyX + 9 * SCALE, brimY, 5 * SCALE, SCALE); // shaded right of brim
		// A tall cone, tapering hard to a point and leaning left into a bent
		// tip; a darker strip down its right gives it round form.
		int baseW = 8 * SCALE;
		int levels = 8;
		for (int i = 0; i < levels; i++)
		{
			int lw = Math.max(SCALE, baseW - i * (baseW - SCALE) / (levels - 1));
			// Lean accelerates near the tip so it hooks over.
			int lean = i + (i > 5 ? (i - 5) * 2 : 0);
			int lx = bodyX + 4 * SCALE + (baseW - lw) / 2 - lean;
			int ly = brimY - (i + 1) * SCALE;
			g2.setColor(HAT);
			g2.fillRect(lx, ly, lw, SCALE);
			if (lw >= 2 * SCALE)
			{
				int shW = Math.max(SCALE, lw / 3);
				g2.setColor(HAT_SHADE);
				g2.fillRect(lx + lw - shW, ly, shW, SCALE);
			}
		}
		// Black band with a gold buckle, seated just above the brim (over the
		// cork).
		g2.setColor(HAT_BAND);
		g2.fillRect(bodyX + 4 * SCALE, brimY - SCALE, 8 * SCALE, SCALE);
		g2.setColor(HAT_BUCKLE);
		g2.fillRect(bodyX + 7 * SCALE, brimY - SCALE, 2, SCALE);
	}

	/** A jack-o'-lantern squatting on the ground: ribbed orange body, a green
	 * stem, and a carved face glowing with the flicker of the candle inside. */
	private static void drawPumpkin(Graphics2D g2, double t, int cx, int groundY)
	{
		int pw = 12;
		int ph = 10;
		int px = cx - pw / 2;
		int py = groundY - ph;
		// Stem.
		g2.setColor(PUMPKIN_STEM);
		g2.fillRect(cx - 1, py - 2, 3, 3);
		// Body, with ribs for roundness.
		g2.setColor(PUMPKIN);
		g2.fillRoundRect(px, py, pw, ph, 7, 7);
		g2.setColor(PUMPKIN_RIB);
		g2.drawLine(cx - 3, py + 2, cx - 3, py + ph - 2);
		g2.drawLine(cx + 3, py + 2, cx + 3, py + ph - 2);
		// Carved face, glowing and flickering like the candle inside.
		float glow = (float) (0.65 + 0.35 * Math.sin(t * 8.0));
		g2.setColor(new Color(PUMPKIN_FACE.getRed(), PUMPKIN_FACE.getGreen(), PUMPKIN_FACE.getBlue(),
			Math.max(0, Math.min(255, (int) (glow * 255)))));
		// Triangle eyes.
		g2.fillRect(cx - 4, py + 3, 3, 2);
		g2.fillRect(cx + 1, py + 3, 3, 2);
		// A jagged grin.
		g2.fillRect(cx - 4, py + 6, 9, 1);
		g2.fillRect(cx - 3, py + 7, 2, 1);
		g2.fillRect(cx + 1, py + 7, 2, 1);
	}
}
