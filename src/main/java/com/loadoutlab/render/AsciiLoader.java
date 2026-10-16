package com.loadoutlab.render;

import java.awt.Font;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.io.BufferedReader;
import javax.swing.Timer;

/**
 * The compute animation, ASCII edition (the pixel mascots retired in
 * the merge-back; field ask 2026-08-20): frames live in
 * loader_frames.txt - a token-free resource - and this class only
 * cycles them. '--' separates frames, '==' separates moods, one
 * random mood per compute (the mascot-roster contract). The timer
 * runs ONLY while computing and dies on unmount (the mascot-era
 * lesson: a hidden component's timer still burns the EDT).
 */
final class AsciiLoader extends javax.swing.JEditorPane
{
	private static final String RESOURCE = "/com/loadoutlab/render/loader_frames.txt";
	private static final List<List<String>> MOODS = new ArrayList<>();
	/** Keyed pools ("==sea ...", "==toa ...", "==tob", "==cox", "==zulrah"
	 * sections): played only for the matching selection, so a kraken never
	 * greets Graardor and the Obelisk charges only for a ToA trip. Land
	 * computes use MOODS. */
	private static final Map<String, List<List<String>>> POOLS = new HashMap<>();
	static
	{
		load(MOODS, POOLS);
	}
	private String key;

	/** The mood face, bundled: Java's logical Monospaced draws ASCII in one
	 * font and borrows Braille, blocks and shades from another at other
	 * widths on Windows (Andrew 2026-10-15: the flask's rows slid sideways).
	 * Cascadia Mono (OFL) carries ASCII, box drawing, blocks, shades and all
	 * 256 Braille cells at one width (DejaVu Sans Mono, Menlo's ancestor, has
	 * no Braille). Missing resource = the logical font as before. */
	private static final Font MOOD_FONT = loadMoodFont();

	static Font moodFont()
	{
		return MOOD_FONT;
	}

	private static Font loadMoodFont()
	{
		try (java.io.InputStream in = AsciiLoader.class.getResourceAsStream("CascadiaMono.ttf"))
		{
			Font font = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(12f);
			// Registered so the HTML stylesheet can name it: Swing's own rule for
			// pre (font-family: Monospaced) overrides the component font, which
			// is why setFont alone changed nothing (Andrew 2026-10-15, still bad).
			java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
			return font;
		}
		catch (Exception e)
		{
			return new Font(Font.MONOSPACED, Font.PLAIN, 12);
		}
	}

	/** The pool key for the pending selection, or null for land. */
	void setKey(String key)
	{
		this.key = key;
	}

	private final Timer timer = new Timer(140, e -> advance());
	private List<String> frames = MOODS.get(0);
	private int tick;

	AsciiLoader()
	{
		// Frames are HTML: a cell may carry its own <font color> run, so a
		// mood can be coloured per glyph with no main-source cost (Andrew
		// 2026-09-03). Plain frames draw in the accent as before.
		setContentType("text/html");
		putClientProperty(HONOR_DISPLAY_PROPERTIES, true);
		setEditable(false);
		setFocusable(false);
		setOpaque(false);
		setFont(moodFont());
		setForeground(ResultCards.ACCENT);
	}

	/** One frame onto the pane: fixed pitch, no margins, markup honoured. */
	void show(String frame)
	{
		// The family goes on the pre itself, unquoted (Swing keeps quotes).
		setText("<html><body style='margin:0'><pre style='margin:0;font-family:" + MOOD_FONT.getFamily()
			+ "'>" + frame + "</pre></body></html>");
	}

	/** The key the current frames were picked for (a sentinel while idle). */
	private String playingKey = "-";

	String playingKey()
	{
		return playingKey;
	}

	void setRunning(boolean running)
	{
		// A search started while the last one still animates re-picks from
		// the NEW selection's pool (field report 2026-09-02: an interrupted
		// search kept the first search's animation).
		boolean repick = running && !Objects.equals(key, playingKey);
		if (running && (!timer.isRunning() || repick))
		{
			List<List<String>> keyed = key == null ? null : POOLS.get(key);
			List<List<String>> pool = keyed != null && !keyed.isEmpty() ? keyed : MOODS;
			frames = pool.get(java.util.concurrent.ThreadLocalRandom.current()
				.nextInt(pool.size()));
			playingKey = key;
			tick = 0;
			advance();
			timer.start();
		}
		else if (!running)
		{
			timer.stop();
			playingKey = "-";
		}
	}

	public void removeNotify()
	{
		timer.stop();
		super.removeNotify();
	}

	private void advance()
	{
		show(frames.get(tick++ % frames.size()));
	}

	/** Package for the parse test. Fail-soft: a missing or empty
	 * resource degrades to the plain text line, never a crash. A mood
	 * separator of "==<key> ..." routes that mood to the keyed pool. */
	private static List<List<String>> poolFor(List<List<String>> land,
		Map<String, List<List<String>>> pools, String key)
	{
		return key == null ? land : pools.computeIfAbsent(key, k -> new ArrayList<>());
	}

	static void load(List<List<String>> land, Map<String, List<List<String>>> pools)
	{
		List<String> frames = new ArrayList<>();
		StringBuilder frame = new StringBuilder();
		String moodKey = null;
		try (BufferedReader reader = new BufferedReader(
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
						poolFor(land, pools, moodKey).add(frames);
						frames = new ArrayList<>();
					}
					if (mood)
					{
						String name = line.substring(2).trim();
						int space = name.indexOf(' ');
						moodKey = name.isEmpty() ? null : space > 0 ? name.substring(0, space) : name;
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
			poolFor(land, pools, moodKey).add(frames);
		}
		if (land.isEmpty())
		{
			land.add(List.of("Computing..."));
		}
	}
}
