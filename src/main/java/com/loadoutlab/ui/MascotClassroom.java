package com.loadoutlab.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * A two-character classroom gag while the optimizer thinks (side profile):
 * our flask mascot sits facing right, and switches between reading a phone he
 * holds ABOVE the desk and hiding it BELOW while pretending to take notes; a
 * darker potion "teacher" with a grey mustache works a chalkboard mounted on
 * the wall in perspective. Two phases loop:
 *   A - the teacher faces the board; the student reads the phone up top.
 *   B - the teacher WHIRLS to face the class (his LL feet swing round); the
 *       student drops the phone under the desk and scribbles notes instead.
 * Deterministic in t.
 */
class MascotClassroom extends Mascot
{
	private static final Color DESK = new Color(156, 108, 62);
	private static final Color DESK_DARK = new Color(120, 82, 46);
	private static final Color CHAIR = new Color(132, 92, 52);
	private static final Color BOARD = new Color(40, 60, 48);
	private static final Color BOARD_FRAME = new Color(150, 104, 58);
	private static final Color CHALK = new Color(226, 230, 222);
	private static final Color FLOOR = new Color(64, 58, 52);
	private static final Color PHONE = new Color(28, 28, 36);
	private static final Color PHONE_GLOW = new Color(150, 210, 255);
	private static final Color STACHE = new Color(206, 202, 192);   // a distinguished grey
	private static final Color PENCIL = new Color(210, 170, 70);
	private static final Color DARK = new Color(34, 32, 42);
	private static final Color LIMB = MascotArt.LIMB;
	private static final Color JUICE = MascotArt.JUICE;
	private static final Color POTION = new Color(120, 90, 170);
	private static final Color TEACHER_LIMB = new Color(86, 106, 94);
	private static final BufferedImage TEACHER_BODY = tint(MascotArt.BODY);

	private static final int SBW = 40;
	private static final int SBH = 25;
	// The teacher runs ~20% larger than the seated student.
	private static final int TBW = 48;
	private static final int TBH = 30;
	private static final double PERIOD = 5.4;

	@Override
	protected void render(Graphics2D g2, double t, int w, int h)
	{
		g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		int groundY = h - 4;

		double c = (t / PERIOD) % 1.0;
		double turn;
		double look;   // 1 = reading the phone up top, 0 = notes, phone hidden
		if (c < 0.45)
		{
			turn = 0;
			look = 1;
		}
		else if (c < 0.54)
		{
			double s = (c - 0.45) / 0.09;
			turn = smooth(s);
			look = Math.max(0, 1 - s * 2.4);
		}
		else if (c < 0.86)
		{
			turn = 1;
			look = 0;
		}
		else
		{
			double s = (c - 0.86) / 0.14;
			turn = 1 - smooth(s);
			look = Math.max(0, (s - 0.45) / 0.55);
		}

		g2.setColor(FLOOR);
		g2.fillRect(0, groundY, w, 1);
		drawChalkboard(g2, t, w - 40, 6);
		drawTeacher(g2, t, w - 54, groundY, turn);

		// --- Student (left), narrow desk to his right --------------------
		int studentCX = 30;
		int seatY = groundY - 14;
		int deskLeft = studentCX + SBW / 2 + 1;
		int deskRight = deskLeft + 16;
		int deskTopY = seatY - 10;

		drawChair(g2, studentCX, seatY, groundY);
		int lean = drawStudent(g2, t, studentCX, seatY, look);
		int bodyLeftX = studentCX - SBW / 2 + lean;

		// The phone: up near his face when out (held close, reading), then
		// dropped below AND tucked back toward his lap when hidden.
		int phoneX = deskLeft + 1 - (int) Math.round((1 - look) * 5);
		int phoneY = deskTopY - 11 + (int) Math.round((1 - look) * 20);

		// The eye, on the flask's RIGHT face, ALWAYS following the phone - it
		// rides up while he reads and down while it is tucked under the desk.
		int eyeX = bodyLeftX + SBW - 9;
		int eyeSocketY = seatY - SBH + (int) (SBH * 0.34);
		int eyeY = eyeSocketY + Math.max(-3, Math.min(6, (phoneY - eyeSocketY) / 4));
		g2.setColor(DARK);
		g2.fillRect(eyeX, eyeY, 3, 3);

		// Both arms root on the flask's belly (its widest part), toward the
		// front/right - so they read as coming off the body, not floating.
		int shoulderX = bodyLeftX + (int) (SBW * 0.56);
		int shoulderY = seatY - SBH + (int) (SBH * 0.58);

		// The far (note) hand: idle while reading; scribbles on the desk when
		// the phone is down. Drawn before the desk so the desk hides its wrist.
		if (look < 0.55)
		{
			double amt = 1 - look / 0.55;
			int penX = deskLeft + 5 + (int) Math.round(Math.sin(t * 13) * 2 * amt);
			int penY = deskTopY - 1;
			g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.setColor(LIMB);
			g2.drawLine(shoulderX, shoulderY + 1, (shoulderX + penX) / 2 + 2, penY - 3);
			g2.drawLine((shoulderX + penX) / 2 + 2, penY - 3, penX, penY - 1);
			g2.setColor(PENCIL);
			g2.drawLine(penX, penY - 3, penX, penY);   // the pencil
		}

		// Desk in front, so the under-desk phone is hidden from the teacher.
		g2.setColor(DESK);
		g2.fillRect(deskLeft, deskTopY, deskRight - deskLeft, 3);
		g2.setColor(DESK_DARK);
		g2.fillRect(deskLeft, deskTopY + 3, deskRight - deskLeft, 1);
		g2.fillRect(deskRight - 4, deskTopY + 4, 3, groundY - deskTopY - 4);
		g2.fillRect(deskLeft + 2, deskTopY + 4, 3, groundY - deskTopY - 4);
		// Notes appearing on the desk while he scribbles.
		if (look < 0.5)
		{
			g2.setColor(DARK);
			g2.fillRect(deskLeft + 3, deskTopY + 1, 5, 1);
			g2.fillRect(deskLeft + 3, deskTopY - 1, 8, 1);
		}

		// The phone hand: arm attached at the shoulder, elbowing to the phone.
		g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.setColor(LIMB);
		g2.fillOval(shoulderX - 3, shoulderY - 3, 6, 6);   // shoulder joint on the body
		int elbowX = (shoulderX + phoneX) / 2 + 3;
		int elbowY = (shoulderY + phoneY) / 2;
		g2.drawLine(shoulderX, shoulderY, elbowX, elbowY);
		g2.drawLine(elbowX, elbowY, phoneX + 2, phoneY + 2);
		drawPhone(g2, phoneX, phoneY, look);

		// Signature stars drift UP off the screen and fade while he's playing -
		// little bubbles of enjoyment rising, not a camera flash at the phone.
		if (look > 0.6)
		{
			for (int i = 0; i < 2; i++)
			{
				double sp = (t * 0.7 + i * 0.5) % 1.0;
				int sx = phoneX + 3 + (int) Math.round(Math.sin(sp * 5 + i * 2) * 3);
				int sy = phoneY - 5 - (int) Math.round(sp * 15);
				MascotArt.drawSnapStar(g2, sx, sy, sp);
			}
		}
	}

	private static double smooth(double s)
	{
		s = Math.max(0, Math.min(1, s));
		return s * s * (3 - 2 * s);
	}

	/** The student flask, seated, facing right. Returns the forward lean. */
	private static int drawStudent(Graphics2D g2, double t, int cx, int seatY, double look)
	{
		int lean = (int) Math.round(look * 3);
		int bodyX = cx - SBW / 2 + lean;
		int bodyY = seatY - SBH;

		// Dangling L-legs from the seat (feet hang; the LL foot kicks right).
		g2.setColor(LIMB);
		g2.fillRect(cx - 7, seatY, 5, 7);
		g2.fillRect(cx - 7, seatY + 7, 8, 5);
		g2.fillRect(cx + 3, seatY, 5, 6);
		g2.fillRect(cx + 3, seatY + 6, 8, 5);

		// Juice filling the lower flask (no gap to the base), then the sprite.
		drawFluid(g2, JUICE, bodyX + 7, SBW - 14, bodyY + (int) (SBH * 0.70), bodyY + SBH, t, 3.4);
		g2.drawImage(MascotArt.BODY, bodyX, bodyY, SBW, SBH, null);
		// Plug the front-facing neck bubble (the eye is drawn in render, on the
		// right face, tracking the phone).
		g2.setColor(LIMB);
		g2.fillRect(bodyX + SBW / 2 - 1, bodyY + 8, 2, 2);
		return lean;
	}

	private static void drawChair(Graphics2D g2, int cx, int seatY, int groundY)
	{
		int cw = SBW - 12;   // a narrower chair than the body is wide
		g2.setColor(CHAIR);
		g2.fillRect(cx - cw / 2, seatY, cw, 3);                       // seat
		g2.fillRect(cx - cw / 2, seatY - 21, 3, 23);                 // tall backrest
		g2.fillRect(cx - cw / 2, seatY + 3, 3, groundY - seatY - 3);
		g2.fillRect(cx + cw / 2 - 3, seatY + 3, 3, groundY - seatY - 3);
	}

	private static void drawTeacher(Graphics2D g2, double t, int cx, int groundY, double turn)
	{
		int bodyX = cx - TBW / 2;
		int bodyY = groundY - TBH - 11;
		int dir = turn < 0.5 ? 1 : -1;
		double squash = 1 - Math.sin(turn * Math.PI) * 0.5;
		int drawW = (int) Math.round(TBW * squash);
		int dx = (TBW - drawW) / 2;

		// Standing L-legs. He TURNS by the L-feet changing direction.
		int legTopY = bodyY + TBH;
		int spread = Math.max(5, drawW / 4);
		int legBackX = cx - spread - 1;
		int legFrontX = cx + spread - 4;
		int footBack = dir > 0 ? legBackX : legBackX + 5 - 8;
		int footFront = dir > 0 ? legFrontX : legFrontX + 5 - 8;
		g2.setColor(TEACHER_LIMB);
		g2.fillRect(legBackX, legTopY, 5, groundY - legTopY - 5);
		g2.fillRect(footBack, groundY - 5, 8, 5);
		g2.fillRect(legFrontX, legTopY, 5, groundY - legTopY - 5);
		g2.fillRect(footFront, groundY - 5, 8, 5);

		// Potion filling the lower flask, then the tinted body.
		drawFluid(g2, POTION, bodyX + dx + 8, drawW - 16, bodyY + (int) (TBH * 0.70), bodyY + TBH, t, 2.6);
		g2.drawImage(TEACHER_BODY, bodyX + dx, bodyY, drawW, TBH, null);

		if (drawW > TBW / 2)
		{
			// Eyes as dark spots on the glass, then the grey mustache below.
			g2.setColor(DARK);
			g2.fillRect(cx + dir * 3 - 1, bodyY + 8, 2, 2);
			g2.fillRect(cx + dir * 9 - 1, bodyY + 8, 2, 2);
			g2.setColor(STACHE);
			int mx = cx - 11;
			g2.fillRect(mx, bodyY + 13, 22, 3);
			g2.fillRect(mx - 2, bodyY + 11, 3, 3);     // curl up (left)
			g2.fillRect(mx + 21, bodyY + 11, 3, 3);    // curl up (right)
		}

		// Chalk arm in HIS colour, animated CONTINUOUSLY across the turn so it
		// SWEEPS rather than snapping between two poses. The shoulder swings
		// through the body's midline (rooted low on the bulb - the flask tapers
		// up top, so a shoulder out by the full width would float beside the
		// glass); the hand travels from the board (up-right) round to a gesture
		// at the class (down-left) as `turn` runs 0..1. A slight extra reach at
		// the ends and a tucked middle give it life instead of a straight lerp.
		g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g2.setColor(TEACHER_LIMB);
		int shX = cx + (int) Math.round((1 - 2 * turn) * 7);
		int shY = bodyY + 18;
		int boardHandX = cx + 24, boardHandY = bodyY + 4;
		int classHandX = cx - 17, classHandY = bodyY + 25;
		int handX = (int) Math.round(boardHandX + (classHandX - boardHandX) * turn);
		int handY = (int) Math.round(boardHandY + (classHandY - boardHandY) * turn);
		// Elbow bends UP out of the shoulder-hand line, easing to nothing at the
		// edge-on midpoint so the forearm folds in as he spins.
		int bend = (int) Math.round(4 * (1 - Math.sin(turn * Math.PI)));
		int elbowX = (shX + handX) / 2;
		int elbowY = (shY + handY) / 2 - 2 - bend;
		g2.fillOval(shX - 3, shY - 3, 6, 6);   // shoulder joint, on the glass
		g2.drawLine(shX, shY, elbowX, elbowY);
		g2.drawLine(elbowX, elbowY, handX, handY);
		if (turn < 0.25)
		{
			g2.setColor(CHALK);
			g2.fillRect(handX, handY - 1, 2, 2);
		}
	}

	/** The chalkboard on the RIGHT wall in one-point perspective: the near
	 * edge is on the RIGHT (tall) and it recedes toward the vanishing point to
	 * the left (the far, left edge is shorter). The chalk follows the slope. */
	private static void drawChalkboard(Graphics2D g2, double t, int x, int y)
	{
		int bw = 36;
		int bh = 38;
		int slant = 6;   // convergence toward the left (into the room)
		// top edge slopes UP to the right; bottom edge slopes DOWN to the right.
		double topSlope = -(double) slant / bw;
		double botSlope = (double) slant / bw;
		Polygon frame = new Polygon(
			new int[]{x, x + bw, x + bw, x},
			new int[]{y + slant, y, y + bh, y + bh - slant}, 4);
		g2.setColor(BOARD_FRAME);
		g2.fillPolygon(frame);
		Polygon slate = new Polygon(
			new int[]{x + 2, x + bw - 2, x + bw - 2, x + 2},
			new int[]{y + slant + 2, y + 2, y + bh - 2, y + bh - slant - 2}, 4);
		g2.setColor(BOARD);
		g2.fillPolygon(slate);

		// Chalk lines parallel to the top edge, shorter toward the far (left).
		g2.setColor(CHALK);
		for (int row = 0; row < 3; row++)
		{
			int lx = x + 4 + row * 3;                     // start further right as it recedes
			int lw = 24 - row * 5;
			int ly = y + slant + 5 + row * 4;
			g2.drawLine(lx, ly + (int) Math.round((lx - x) * topSlope),
				lx + lw, ly + (int) Math.round((lx + lw - x) * topSlope));
		}
		// Vertical tally marks along the bottom - leaning with the board plane
		// (parallel to its slanted sides) and shorter toward the far (left) edge.
		g2.setStroke(new BasicStroke(1f));
		for (int i = 0; i < 6; i++)
		{
			int bx = x + 6 + i * 3;
			int by = y + bh - 4 + (int) Math.round((bx - x) * botSlope);
			int len = 5 + i;                                 // taller toward the near (right)
			int lean = (int) Math.round(len * topSlope);     // top shifts with the slope
			g2.drawLine(bx, by, bx + lean, by - len);
		}
	}

	private static void drawPhone(Graphics2D g2, int x, int y, double look)
	{
		// Tilt it like a handheld game held over the desk - the screen angles
		// down toward the surface, not held upright like a camera.
		java.awt.geom.AffineTransform saved = g2.getTransform();
		g2.rotate(Math.toRadians(42), x, y + 4);   // pivot at the grip (near hand)
		g2.setColor(PHONE);
		g2.fillRect(x, y, 5, 9);
		java.awt.Composite old = g2.getComposite();
		g2.setComposite(java.awt.AlphaComposite.getInstance(
			java.awt.AlphaComposite.SRC_OVER, (float) (0.12 + 0.33 * look)));
		g2.setColor(PHONE_GLOW);
		g2.fillRect(x - 2, y - 2, 9, 13);
		g2.setComposite(old);
		g2.setColor(PHONE_GLOW);
		g2.fillRect(x + 1, y + 1, 3, 7);
		g2.setTransform(saved);
	}

	/** A fluid filling the flask from a gently undulating surface down to the
	 * base - no gap between the liquid and the bottom of the glass. */
	private static void drawFluid(Graphics2D g2, Color col, int x, int wf, int surfNominalY, int bottomY, double t, double speed)
	{
		g2.setColor(col);
		int cw = 2;
		int cols = Math.max(1, wf / cw);
		for (int i = 0; i < cols; i++)
		{
			int surfY = surfNominalY + (int) Math.round(Math.sin(t * speed + i * 0.9) * 1.6);
			g2.fillRect(x + i * cw, surfY, cw, bottomY - surfY);
		}
	}

	private static BufferedImage tint(BufferedImage src)
	{
		if (src == null)
		{
			return null;
		}
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < src.getHeight(); y++)
		{
			for (int x = 0; x < src.getWidth(); x++)
			{
				int p = src.getRGB(x, y);
				int a = p >>> 24;
				if (a < 16)
				{
					continue;
				}
				int r = (int) (((p >> 16) & 255) * 0.55);
				int g = (int) (((p >> 8) & 255) * 0.48);
				int b = (int) ((p & 255) * 0.62);
				out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
			}
		}
		return out;
	}
}
