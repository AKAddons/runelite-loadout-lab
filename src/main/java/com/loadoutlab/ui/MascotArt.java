package com.loadoutlab.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/**
 * Shared pixel-art plumbing for the mascot loading animations: the 16x16
 * sprite sliced into body and leg segments, the palette, and the leg
 * renderer. MascotSpinner (working out) and MascotChef (cooking) both draw
 * from here so the creature stays one creature instead of two drifting
 * copies of the slicing arithmetic.
 */
final class MascotArt
{
	static final int SCALE = 3;
	private static final BufferedImage MASCOT = load();
	// Sprite slices (16x16 grid): bottle body rows 0-9; per leg a 2px-wide
	// thigh column (rows 10-12) and a 4px-wide L-foot shin (rows 13-15).
	// The flask with the animated bits carved out: the juice band (rows
	// 7-8) is redrawn each frame so it can move, and the static corner
	// star (cols 12-14, rows 0-2) is replaced by per-animation effects.
	static final BufferedImage BODY = prepareBody(slice(0, 0, 16, 10));
	static final BufferedImage LEFT_THIGH = slice(5, 10, 2, 3);
	static final BufferedImage RIGHT_THIGH = slice(10, 10, 2, 3);
	static final BufferedImage LEFT_SHIN = slice(5, 13, 4, 3);
	static final BufferedImage RIGHT_SHIN = slice(10, 13, 4, 3);
	static final Color LIMB = new Color(140, 200, 140);
	static final Color JUICE = new Color(208, 178, 102);

	private MascotArt()
	{
	}

	static boolean available()
	{
		return MASCOT != null;
	}

	/** A pixel plus-star in the mascot's amber that pops in and fades:
	 * age runs 0..1. The workout snaps them at its fingers; the chef pops
	 * them on a perfect catch and over a well-seasoned pot. */
	static void drawSnapStar(Graphics2D g2, int cx, int cy, double age)
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

	/**
	 * Two-segment arm from shoulder to hand, elbow offset a fixed few
	 * pixels perpendicular to the reach - a gentle working bend that stays
	 * in proportion however far the hand ranges (full IK made the little
	 * arms balloon into wings). Both reaches the moods use are
	 * near-horizontal, so a screen-space vertical offset at the midpoint
	 * is the bend: up for a busy arm, sagging down for a loaded one.
	 */
	static void drawBentArm(Graphics2D g2, int sx, int sy, int hx, int hy,
		int bulge, boolean elbowUp)
	{
		int ex = (sx + hx) / 2;
		int ey = (sy + hy) / 2 + (elbowUp ? -bulge : bulge);
		g2.setColor(LIMB);
		g2.drawLine(sx, sy, ex, ey);
		g2.drawLine(ex, ey, hx, hy);
	}

	/** Thigh hangs from the flask hip, shin sits at the FOOT's spot; the
	 * knee (thigh drawn toward the foot, squashing on lift) keeps the leg
	 * connected top and bottom. */
	static void drawLeg(Graphics2D g2, BufferedImage thigh, BufferedImage shin,
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

	private static BufferedImage load()
	{
		try
		{
			return ImageIO.read(MascotArt.class.getResourceAsStream("/com/loadoutlab/icon.png"));
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
}
