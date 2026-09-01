package com.loadoutlab.render;

import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * The compute animation, ASCII edition (the pixel mascots retired in
 * the merge-back; field ask 2026-08-20): frames live in
 * loader_frames.txt - a token-free resource - and this class only
 * cycles them. '--' separates frames, '==' separates moods, one
 * random mood per compute (the mascot-roster contract). The timer
 * runs ONLY while computing and dies on unmount (the mascot-era
 * lesson: a hidden component's timer still burns the EDT).
 */
final class AsciiLoader extends javax.swing.JTextArea
{
	private static final String RESOURCE = "/com/loadoutlab/render/loader_frames.txt";
	private static final List<List<String>> MOODS = new ArrayList<>();
	/** Sea moods ("==sea" sections): played for ship-eligible selections
	 * only, so a kraken never greets Graardor. Land computes use MOODS. */
	private static final List<List<String>> SEA_MOODS = new ArrayList<>();

	static
	{
		load(MOODS, SEA_MOODS);
	}

	private boolean sea;

	void setSea(boolean sea)
	{
		this.sea = sea;
	}

	private final javax.swing.Timer timer = new javax.swing.Timer(140, e -> advance());
	private List<String> frames = MOODS.get(0);
	private int tick;

	AsciiLoader()
	{
		setEditable(false);
		setFocusable(false);
		setOpaque(false);
		setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		setForeground(ResultCards.ACCENT);
	}

	void setRunning(boolean running)
	{
		if (running && !timer.isRunning())
		{
			List<List<String>> pool = sea && !SEA_MOODS.isEmpty() ? SEA_MOODS : MOODS;
			frames = pool.get(java.util.concurrent.ThreadLocalRandom.current()
				.nextInt(pool.size()));
			tick = 0;
			advance();
			timer.start();
		}
		else if (!running)
		{
			timer.stop();
		}
	}

	@Override
	public void removeNotify()
	{
		timer.stop();
		super.removeNotify();
	}

	private void advance()
	{
		setText(frames.get(tick++ % frames.size()));
	}

	/** Package for the parse test. Fail-soft: a missing or empty
	 * resource degrades to the plain text line, never a crash. A mood
	 * separator of "==sea" routes that mood to the SEA pool. */
	static void load(List<List<String>> land, List<List<String>> seaPool)
	{
		List<String> frames = new ArrayList<>();
		StringBuilder frame = new StringBuilder();
		boolean seaMood = false;
		try (java.io.BufferedReader reader = new java.io.BufferedReader(
			new java.io.InputStreamReader(
				AsciiLoader.class.getResourceAsStream(RESOURCE),
				java.nio.charset.StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.startsWith("#"))
				{
					continue;
				}
				boolean mood = line.startsWith("==");
				// Frame separators are EXACTLY "--": sea art legitimately
				// starts lines with dashes (waves), and a prefix match ate
				// them as separators.
				if (mood || line.equals("--"))
				{
					if (frame.length() > 0)
					{
						frames.add(frame.toString());
						frame.setLength(0);
					}
					if (mood && !frames.isEmpty())
					{
						(seaMood ? seaPool : land).add(frames);
						frames = new ArrayList<>();
					}
					if (mood)
					{
						seaMood = line.startsWith("==sea");
					}
					continue;
				}
				if (frame.length() > 0)
				{
					frame.append('\n');
				}
				frame.append(line);
			}
		}
		catch (Exception e)
		{
			// fall through to the fail-soft default below
		}
		if (frame.length() > 0)
		{
			frames.add(frame.toString());
		}
		if (!frames.isEmpty())
		{
			(seaMood ? seaPool : land).add(frames);
		}
		if (land.isEmpty())
		{
			land.add(List.of("Computing..."));
		}
	}
}
