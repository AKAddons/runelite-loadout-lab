package com.loadoutlab.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import static com.loadoutlab.ui.MascotArt.JUICE;
import static com.loadoutlab.ui.MascotArt.LIMB;
import static com.loadoutlab.ui.MascotArt.SCALE;

/**
 * The bottle mascot skateboarding while the optimizer thinks: a push-off from
 * the middle sends him rolling to the right, off the edge and back in from the
 * left, coasting and slowing until he reaches the centre again - then he kicks
 * off and does it all over. Always rolling right, fast off the push and slow
 * into the middle (an ease-out on a wrapping travel). Deterministic in t.
 */
class MascotSkater extends Mascot
{
	private static final Color DECK = new Color(196, 134, 74);      // wood plank
	private static final Color GRIP = new Color(48, 44, 54);        // grip tape on top
	private static final Color DECK_STRIPE = new Color(150, 96, 190); // a bright graphic
	private static final Color TRUCK = new Color(150, 150, 160);
	private static final Color WHEEL = new Color(232, 226, 210);
	private static final Color WHEEL_HUB = new Color(120, 110, 96);
	private static final Color SPEED = new Color(150, 200, 140);
	private static final double LOOP_PERIOD = 3.0;

	@Override
	protected void render(Graphics2D g2, double t, int w, int h)
	{
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		int bodyW = 16 * SCALE;
		int boardW = 16 * SCALE;

		// Ease-out travel that wraps: fast off the centre push, slowing as it
		// coasts back to the centre. rideCX is the board/mascot centre.
		double u = (t / LOOP_PERIOD) % 1.0;
		double ease = 1 - (1 - u) * (1 - u);
		double wrap = w + boardW;
		double rideCX = w / 2.0 + ease * wrap;
		if (rideCX > w + boardW / 2.0)
		{
			rideCX -= wrap; // jumped fully off the right, re-enter from the left
		}
		int cx = (int) Math.round(rideCX);
		double speed = 2 * (1 - u); // 2 at the push, 0 into the centre

		int groundY = h - 4;
		int wheelR = 2;
		int deckY = groundY - 2 * wheelR - 3;   // deck top rides on the wheels
		int deckLeft = cx - boardW / 2;
		int bodyLeft = cx - bodyW / 2;
		int feetY = deckY;
		int shinH = 3 * SCALE;                   // the full L-foot - the LL branding
		int bodyBottomY = feetY - 6 * SCALE;     // upright L-legs, only a light crouch
		int bodyY = bodyBottomY - 10 * SCALE;

		// A little balancing wobble as he settles just after the push and the
		// board starts to coast - a damped sway of the torso over the planted
		// feet, with the arms swinging opposite to catch it.
		double wob = 0;
		if (u >= 0.10 && u < 0.45)
		{
			double wp = (u - 0.10) / 0.35;
			wob = Math.sin(wp * Math.PI * 4.0) * Math.exp(-wp * 3.5);
		}
		int wobX = (int) Math.round(wob * 3);
		int bodyX = bodyLeft + wobX;             // the swaying torso; feet stay planted

		// --- Speed lines that fade IN and OUT with the speed --------------
		float lineAlpha = (float) Math.min(0.5, speed * 0.3);
		if (lineAlpha > 0.02f)
		{
			java.awt.Composite old = g2.getComposite();
			g2.setComposite(java.awt.AlphaComposite.getInstance(
				java.awt.AlphaComposite.SRC_OVER, lineAlpha));
			g2.setColor(SPEED);
			int len = 3 + (int) Math.round(speed * 4);
			for (int i = 0; i < 3; i++)
			{
				g2.fillRect(bodyLeft - len - i * 4, bodyY + (4 + i * 2) * SCALE, len, 1);
			}
			g2.setComposite(old);
		}

		// --- Skateboard: spinning wheels on trucks, then the deck ---------
		double roll = rideCX / 3.0;
		int wheelLX = deckLeft + 4 * SCALE;
		int wheelRX = deckLeft + boardW - 4 * SCALE;
		g2.setColor(TRUCK);
		g2.fillRect(wheelLX - 1, deckY + 3, 3, groundY - wheelR - deckY - 3); // trucks
		g2.fillRect(wheelRX - 1, deckY + 3, 3, groundY - wheelR - deckY - 3);
		drawWheel(g2, wheelLX, groundY - wheelR, wheelR, roll);
		drawWheel(g2, wheelRX, groundY - wheelR, wheelR, roll);
		// The deck: a wood plank with upturned nose and tail, grip on top and
		// a bright graphic stripe underneath.
		g2.setColor(DECK);
		g2.fillRect(deckLeft, deckY, boardW, 4);
		g2.fillRect(deckLeft - 3, deckY - 3, 4, 4);          // tail kick (left)
		g2.fillRect(deckLeft + boardW - 1, deckY - 3, 4, 4); // nose kick (right)
		g2.setColor(GRIP);
		g2.fillRect(deckLeft, deckY, boardW, 1);
		g2.setColor(DECK_STRIPE);
		g2.fillRect(deckLeft + 2, deckY + 3, boardW - 4, 1);

		// The kick is a whole-body move: the torso pitches forward and the
		// hips DROP and TILT as he lunges into the push, rising back as he
		// recovers. bend ramps up over the reach/power stroke and eases back.
		double bend = 0;
		if (u >= 0.78)
		{
			bend = Math.min(1.0, (u - 0.78) / 0.14);
		}
		else if (u < 0.16)
		{
			bend = Math.max(0.0, 1.0 - u / 0.16);
		}
		double rot = bend * Math.toRadians(15);            // forward torso pitch
		int drop = (int) Math.round(bend * 5);             // hips drop into the lunge
		int pivotX = bodyX + 8 * SCALE;
		int pivotY = bodyBottomY + drop;
		double cosb = Math.cos(rot);
		double sinb = Math.sin(rot);
		// The hip attach points ride the rotation+drop, so the legs and the
		// tilted pelvis stay connected to the leaning torso.
		int leftHipX = pivotX + (int) Math.round((bodyX + 5 * SCALE - pivotX) * cosb);
		int leftHipY = pivotY + (int) Math.round((bodyX + 5 * SCALE - pivotX) * sinb);
		int rightHipX = pivotX + (int) Math.round((bodyX + 10 * SCALE - pivotX) * cosb);
		int rightHipY = pivotY + (int) Math.round((bodyX + 10 * SCALE - pivotX) * sinb);

		// --- L-legs: from the (tilted, dropped) hips down to the feet ------
		// Front (right) foot planted on the deck.
		MascotArt.drawLeg(g2, MascotArt.RIGHT_THIGH, MascotArt.RIGHT_SHIN,
			rightHipX, bodyLeft + 10 * SCALE, rightHipY, feetY, shinH);
		// Back (left) foot: reaches down to plant, then SWEEPS back with
		// acceleration - the L-foot keeps its heading; the leg and hip pivot.
		int footBase = bodyLeft + 5 * SCALE;
		int lFootX = footBase;
		int lFootY = feetY;
		if (u >= 0.80 && u < 0.90)
		{
			double rp = (u - 0.80) / 0.10;                 // reach down, plant just ahead
			lFootX = footBase + 1;
			lFootY = feetY + (int) Math.round(rp * (groundY - feetY));
		}
		else if (u >= 0.90)
		{
			double ps = (u - 0.90) / 0.10;                 // power stroke - accelerates back
			lFootX = footBase + 1 - (int) Math.round(ps * ps * 10);
			lFootY = groundY;
		}
		else if (u < 0.12)
		{
			double rs = u / 0.12;                          // lift the foot back onto the deck
			lFootX = footBase - (int) Math.round((1 - rs) * 9);
			lFootY = groundY - (int) Math.round(rs * (groundY - feetY));
		}
		MascotArt.drawLeg(g2, MascotArt.LEFT_THIGH, MascotArt.LEFT_SHIN,
			leftHipX, lFootX, leftHipY, lFootY, shinH);

		// --- Torso, arms, liquid, eye - all rotate+drop with the lunge ----
		java.awt.geom.AffineTransform saved = g2.getTransform();
		g2.translate(pivotX, pivotY);
		g2.rotate(rot);
		g2.translate(-pivotX, -pivotY);
		g2.translate(0, drop);

		// Arms out for balance.
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.setColor(LIMB);
		int shoulderY = bodyY + 8 * SCALE;
		int armC = -wobX * 2;   // hands swing opposite the torso to catch balance
		int rsx = bodyX + 13 * SCALE;
		g2.drawLine(rsx, shoulderY, rsx + 6 + armC, shoulderY - 2);
		g2.drawLine(rsx + 6 + armC, shoulderY - 2, rsx + 10 + armC, shoulderY - 5); // front
		int lsx = bodyX + 3 * SCALE;
		g2.drawLine(lsx, shoulderY, lsx - 6 + armC, shoulderY - 1);
		g2.drawLine(lsx - 6 + armC, shoulderY - 1, lsx - 9 + armC, shoulderY + 4);  // back
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

		// Liquid, sloshing hard: the push slams it to the back and it splashes
		// UP, then it rocks fore-and-aft and settles over the coast. A damped
		// oscillation kicked each push, plus a forward lean as he decelerates.
		double slosh = Math.cos(u * 2 * Math.PI * 1.6) * Math.exp(-u * 3.0) * 2.6;
		double tilt = slosh - 0.5;   // + = piled at the back (left)
		g2.setColor(JUICE);
		int juiceBottom = bodyY + 9 * SCALE;
		for (int i = 0; i < 8; i++)
		{
			double lever = (i + 0.5) / 8.0 - 0.5;   // -0.5 back (left) .. +0.5 front
			int surfY = bodyY + (int) Math.round((7.0 + tilt * lever * 2.4) * SCALE);
			surfY = Math.max(bodyY + 5 * SCALE, Math.min(juiceBottom - SCALE, surfY));
			g2.fillRect(bodyX + (4 + i) * SCALE, surfY, SCALE, juiceBottom - surfY);
		}
		// The eye drops to watch the push, lifts back straight ahead riding.
		double eyeDown;
		if (u >= 0.80)
		{
			eyeDown = Math.min(1.0, (u - 0.80) / 0.06);
		}
		else if (u < 0.18)
		{
			eyeDown = Math.max(0.0, 1.0 - u / 0.18);
		}
		else
		{
			eyeDown = 0.0;
		}
		int eyeY = bodyY + 4 * SCALE + (int) Math.round(eyeDown * SCALE);
		g2.fillRect(bodyX + 8 * SCALE, eyeY, SCALE, SCALE);

		// The body itself.
		g2.drawImage(MascotArt.BODY, bodyX, bodyY, bodyW, 10 * SCALE, null);
		g2.setTransform(saved);

		// --- Signature snap-stars: a little burst off the ground where he
		// shoves off. World-fixed near the centre (where the push lands), so
		// the board zooms away and leaves the sparks behind. --------------
		if (u >= 0.95 || u < 0.10)
		{
			double age = (u >= 0.95 ? (u - 0.95) : (u + 0.05)) / 0.15;
			if (age <= 1.0)
			{
				MascotArt.drawSnapStar(g2, w / 2 - 6 * SCALE, groundY - 3, age);
				MascotArt.drawSnapStar(g2, w / 2 - 9 * SCALE, groundY - 7, Math.min(1.0, age + 0.25));
			}
		}
	}

	/** A skate wheel with a hub mark that spins with the distance rolled. */
	private static void drawWheel(Graphics2D g2, int cx, int cy, int r, double roll)
	{
		g2.setColor(WHEEL);
		g2.fillOval(cx - r, cy - r, 2 * r + 1, 2 * r + 1);
		g2.setColor(WHEEL_HUB);
		int hx = cx + (int) Math.round(Math.cos(roll) * (r - 1));
		int hy = cy + (int) Math.round(Math.sin(roll) * (r - 1));
		g2.fillRect(hx, hy, 1, 1);
	}
}
