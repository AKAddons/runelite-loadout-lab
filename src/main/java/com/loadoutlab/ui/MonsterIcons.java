package com.loadoutlab.ui;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import net.runelite.client.RuneLite;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Async monster thumbnails for the mob rows: the OSRS wiki's infobox
 * render (Special:FilePath/&lt;Name&gt;.png), fetched off the game
 * threads through the client's shared OkHttp, cached in memory and on
 * disk under the RuneLite dir. Every failure fails QUIET - a row with
 * no picture just keeps its text-only look.
 */
public final class MonsterIcons
{
	private static final org.slf4j.Logger log =
		org.slf4j.LoggerFactory.getLogger(MonsterIcons.class);

	private static final File DIR = new File(RuneLite.RUNELITE_DIR, "loadout-lab/npc-icons");
	private static final String WIKI = "https://oldschool.runescape.wiki/w/Special:FilePath/";
	private static final int MAX_WIDTH = 28;

	private final OkHttpClient http;
	private final Map<String, ImageIcon> cache = new ConcurrentHashMap<>();
	private final Set<String> failed = ConcurrentHashMap.newKeySet();
	/** Keys being loaded -> callbacks waiting on them (guarded by itself). */
	private final Map<String, List<Runnable>> waiting = new HashMap<>();
	private final ExecutorService worker = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "loadout-lab-monster-icons");
		t.setDaemon(true);
		// A background fetch must never compete with the client thread.
		t.setPriority(Thread.MIN_PRIORITY + 1);
		return t;
	});

	public MonsterIcons(OkHttpClient http)
	{
		this.http = http;
		//noinspection ResultOfMethodCallIgnored
		DIR.mkdirs();
	}

	/**
	 * The monster's icon at the given height, or null while it loads (or
	 * after it failed). When null, onReady fires later on the EDT once
	 * the icon is available - call get() again there for the cache hit.
	 * The version narrows the wiki file when a per-form image exists
	 * ("Vanguard (melee).png"); fallbacks strip it and any parenthetical.
	 */
	public ImageIcon get(String name, String version, int height, Runnable onReady)
	{
		String lower = (name + "|" + (version == null ? "" : version)).toLowerCase(Locale.ROOT);
		String key = lower + "@" + height;
		ImageIcon hit = cache.get(key);
		if (hit != null)
		{
			return hit;
		}
		if (failed.contains(lower))
		{
			return null;
		}
		synchronized (waiting)
		{
			List<Runnable> waiters = waiting.get(key);
			if (waiters != null)
			{
				if (onReady != null)
				{
					waiters.add(onReady);
				}
				return null;
			}
			List<Runnable> fresh = new ArrayList<>();
			if (onReady != null)
			{
				fresh.add(onReady);
			}
			waiting.put(key, fresh);
		}
		worker.execute(() ->
		{
			try
			{
				BufferedImage img = load(name, version, lower);
				if (img == null)
				{
					failed.add(lower);
				}
				else
				{
					double scale = Math.min(height / (double) img.getHeight(),
						MAX_WIDTH / (double) img.getWidth());
					int w = Math.max(1, (int) Math.round(img.getWidth() * scale));
					int h = Math.max(1, (int) Math.round(img.getHeight() * scale));
					cache.put(key, new ImageIcon(
						img.getScaledInstance(w, h, Image.SCALE_SMOOTH)));
				}
			}
			catch (Exception e)
			{
				failed.add(lower);
			}
			List<Runnable> waiters;
			synchronized (waiting)
			{
				waiters = waiting.remove(key);
			}
			if (waiters != null && !waiters.isEmpty())
			{
				SwingUtilities.invokeLater(() -> waiters.forEach(Runnable::run));
			}
		});
		return null;
	}

	private BufferedImage load(String name, String version, String lower) throws IOException
	{
		File file = new File(DIR, lower.replaceAll("[^a-z0-9-]", "_") + ".png");
		if (file.isFile())
		{
			BufferedImage disk = ImageIO.read(file);
			if (disk != null)
			{
				return disk;
			}
		}
		// Wiki file-name candidates, most specific first: the per-form
		// image ("Vanguard (melee).png"), the plain name, then the wiki's
		// multi-version conventions verified live 2026-07-18 - sentence
		// case and a " (1)" suffix ("Skeletal mystic (1).png",
		// "Lizardman shaman (1).png") - and the corpus parenthetical
		// stripped ("Lizardman shaman (Chambers of Xeric)" -> base name).
		List<String> forms = new ArrayList<>();
		if (version != null && !version.isEmpty())
		{
			forms.add(name + " (" + version.toLowerCase(Locale.ROOT) + ")");
		}
		forms.add(name);
		int paren = name.indexOf(" (");
		if (paren > 0)
		{
			forms.add(name.substring(0, paren));
		}
		List<String> candidates = new ArrayList<>();
		for (String form : forms)
		{
			String sentence = form.length() > 1
				? form.charAt(0) + form.substring(1).toLowerCase(Locale.ROOT) : form;
			for (String variant : new String[]{form, form + " (1)", sentence, sentence + " (1)"})
			{
				if (!candidates.contains(variant))
				{
					candidates.add(variant);
				}
			}
		}
		// Last resort: form-switching bosses file per combat style
		// ("Nylocas Vasilias (melee).png", verified live 2026-07-18).
		for (String suffix : new String[]{" (melee)", " (ranged)", " (magic)"})
		{
			String variant = (paren > 0 ? name.substring(0, paren) : name) + suffix;
			if (!candidates.contains(variant))
			{
				candidates.add(variant);
			}
		}
		for (String candidate : candidates)
		{
			BufferedImage img = fetch(candidate);
			if (img != null)
			{
				try (java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream())
				{
					ImageIO.write(img, "png", out);
					Files.write(file.toPath(), out.toByteArray());
				}
				return img;
			}
		}
		log.debug("no wiki image for '{}' (tried {})", name, candidates);
		return null;
	}

	private BufferedImage fetch(String fileName) throws IOException
	{
		String encoded = URLEncoder.encode(fileName + ".png", "UTF-8").replace("+", "%20");
		Request request = new Request.Builder()
			.url(WIKI + encoded + "?width=96")
			.build();
		try (Response response = http.newCall(request).execute())
		{
			if (!response.isSuccessful() || response.body() == null)
			{
				return null;
			}
			return ImageIO.read(new ByteArrayInputStream(response.body().bytes()));
		}
	}

	public void shutdown()
	{
		worker.shutdownNow();
	}
}
