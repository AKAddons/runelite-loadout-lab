package com.loadoutlab.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Pixel-exact golden for the mascot animations: renders every mood frame
 * by frame and prints one SHA-256 per mood over all frames' pixels.
 *
 * This exists for one purpose - the mascot suite is ~19k of the plugin's
 * token budget and the only large reduction left is to re-express the
 * animations as data. Such a rewrite is a hand transcription of a lot of
 * arithmetic, so it needs a gate that fails on a single moved pixel; a
 * montage PNG eyeball check cannot provide that.
 *
 *   ./gradlew -q mascotFrameHash > mascot-hashes.txt   (diff across changes)
 *
 * Frames are STEPPED (not sampled by jumping to a time) so the stateful
 * moods integrate exactly as they do live, and every mood is rendered on
 * the fixed Mascot.SIZE canvas - render() reads the component width, so a
 * variable canvas would make the hash meaningless. No mood uses randomness
 * or a clock (verified), which is what makes this reproducible at all.
 */
public final class MascotFrameHash
{
	private static final int W = Mascot.SIZE.width;
	private static final int H = Mascot.SIZE.height;
	/** Long enough to cover the longest cycle in the roster with margin. */
	private static final double WINDOW_SECONDS = 12.0;
	private static final double STEP = 0.033;
	private static final Color CANVAS = new Color(40, 40, 40);

	private MascotFrameHash()
	{
	}

	public static void main(String[] args) throws Exception
	{
		if (!MascotArt.available())
		{
			System.err.println("mascot sprite not on the classpath - nothing to hash");
			return;
		}
		for (String line : hashes())
		{
			System.out.println(line);
		}
	}

	/** "MOOD sha256" per mood, in roster order. */
	static List<String> hashes() throws Exception
	{
		List<String> out = new ArrayList<>();
		for (MascotRoster mood : MascotRoster.values())
		{
			out.add(mood.name() + " " + hash(mood.create()));
		}
		return out;
	}

	private static String hash(Mascot mascot) throws Exception
	{
		mascot.setSize(W, H);
		mascot.onStart();
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		BufferedImage frame = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
		int[] pixels = new int[W * H];
		for (double t = 0; t < WINDOW_SECONDS; t += STEP)
		{
			Graphics2D g2 = frame.createGraphics();
			g2.setColor(CANVAS);
			g2.fillRect(0, 0, W, H);
			mascot.render(g2, t, W, H);
			g2.dispose();
			frame.getRGB(0, 0, W, H, pixels, 0, W);
			for (int pixel : pixels)
			{
				digest.update((byte) (pixel >> 16));
				digest.update((byte) (pixel >> 8));
				digest.update((byte) pixel);
			}
		}
		StringBuilder sb = new StringBuilder();
		for (byte b : digest.digest())
		{
			sb.append(String.format("%02x", b));
		}
		return sb.toString();
	}
}
