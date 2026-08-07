package com.loadoutlab.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Dev harness: renders every mood in the roster to a montage PNG under
 * build/mascot-preview/, so adding or tweaking an animation always gets an
 * eyeball check. Run it with {@code ./gradlew mascotPreview}.
 *
 * It STEPS each mood forward frame by frame (not by jumping to a time), so
 * stateful moods like the workout's slosh spring integrate exactly as they
 * would live. Frames are sampled evenly across a window long enough to
 * cover the longest cycle.
 */
public final class MascotPreview
{
	private static final int W = Mascot.SIZE.width;
	private static final int H = Mascot.SIZE.height;
	private static final int SCALE = 2;
	private static final int COLS = 4;
	private static final int ROWS = 4;
	private static final double WINDOW_SECONDS = 5.2; // covers the longest cycle
	private static final Color GAP = new Color(24, 24, 24);
	private static final Color CANVAS = new Color(40, 40, 40); // sidebar-ish

	private MascotPreview()
	{
	}

	public static void main(String[] args) throws Exception
	{
		if (!MascotArt.available())
		{
			System.err.println("mascot sprite not on the classpath - nothing to render");
			return;
		}
		File outDir = new File(args.length > 0 ? args[0] : "build/mascot-preview");
		outDir.mkdirs();
		for (MascotRoster mood : MascotRoster.values())
		{
			File out = new File(outDir, mood.name().toLowerCase() + ".png");
			ImageIO.write(montage(mood.create()), "png", out);
			System.out.println("wrote " + out.getPath());
		}
	}

	/** A COLS x ROWS grid of frames sampled across the window. */
	private static BufferedImage montage(Mascot mascot)
	{
		int frames = COLS * ROWS;
		int stepsPerFrame = (int) Math.round(WINDOW_SECONDS / frames / 0.033);
		BufferedImage montage = new BufferedImage(
			COLS * (W * SCALE + 8) + 8, ROWS * (H * SCALE + 8) + 8, BufferedImage.TYPE_INT_RGB);
		Graphics2D mg = montage.createGraphics();
		mg.setColor(GAP);
		mg.fillRect(0, 0, montage.getWidth(), montage.getHeight());

		mascot.setSize(W, H); // so a mood reading getWidth()/getHeight() still lays out
		mascot.onStart();
		double t = 0;
		BufferedImage frame = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
		for (int i = 0; i < frames; i++)
		{
			// Step forward to this sample point so stateful moods integrate.
			for (int s = 0; s < stepsPerFrame; s++)
			{
				t += 0.033;
				Graphics2D fg = frame.createGraphics();
				fg.setColor(CANVAS);
				fg.fillRect(0, 0, W, H);
				mascot.render(fg, t, W, H);
				fg.dispose();
			}
			int cx = 8 + (i % COLS) * (W * SCALE + 8);
			int cy = 8 + (i / COLS) * (H * SCALE + 8);
			mg.drawImage(frame, cx, cy, W * SCALE, H * SCALE, null);
		}
		mg.dispose();
		return montage;
	}
}
