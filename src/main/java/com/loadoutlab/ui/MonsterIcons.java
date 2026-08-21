package com.loadoutlab.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import java.util.zip.GZIPInputStream;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import net.runelite.client.RuneLite;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Async monster thumbnails for the mob rows: the OSRS wiki's infobox
 * render (Special:FilePath/&lt;file&gt;.png), fetched off the game threads
 * through the client's shared OkHttp, cached in memory and on disk under
 * the RuneLite dir.
 *
 * <p>The wiki FILE NAME is never guessed. Every row of the bundled monster
 * corpus carries the wiki's own {@code image} value, so a thumbnail costs
 * exactly ONE request, a miss costs one and is remembered, and a cached
 * icon costs none. Every failure fails QUIET - a row with no picture just
 * keeps its text-only look.
 */
public final class MonsterIcons
{
	private static final org.slf4j.Logger log =
		org.slf4j.LoggerFactory.getLogger(MonsterIcons.class);

	private static final File DIR = new File(RuneLite.RUNELITE_DIR, "loadout-lab/npc-icons");
	private static final String WIKI = "https://oldschool.runescape.wiki/w/Special:FilePath/";
	private static final String CORPUS = "/com/loadoutlab/data/monsters.json.gz";
	private static final int MAX_WIDTH = 28;

	private final OkHttpClient http;
	private final Map<String, ImageIcon> cache = new ConcurrentHashMap<>();
	private final Set<String> failed = ConcurrentHashMap.newKeySet();
	/** Keys being loaded -> callbacks waiting on them (guarded by itself). */
	private final Map<String, List<Runnable>> waiting = new HashMap<>();
	/** "name|version" and bare "name|" -> the wiki file, built once on the
	 * worker thread the first time an icon is asked for. */
	private volatile Map<String, String> files;
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
	}

	/**
	 * The monster's icon at the given height, or null while it loads (or
	 * after it failed). When null, onReady fires later on the EDT once the
	 * icon is available - call get() again there for the cache hit.
	 */
	public ImageIcon get(String name, String version, int height, Runnable onReady)
	{
		String lower = key(name, version);
		String cacheKey = lower + "@" + height;
		ImageIcon hit = cache.get(cacheKey);
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
			List<Runnable> waiters = waiting.get(cacheKey);
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
			waiting.put(cacheKey, fresh);
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
					cache.put(cacheKey, new ImageIcon(
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
				waiters = waiting.remove(cacheKey);
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
		String wikiFile = wikiFile(name, version);
		if (wikiFile == null || wikiFile.isEmpty())
		{
			log.debug("no corpus image for '{}' ({})", name, version);
			return null;
		}
		BufferedImage img = fetch(wikiFile);
		if (img == null)
		{
			return null;
		}
		try (ByteArrayOutputStream out = new ByteArrayOutputStream())
		{
			ImageIO.write(img, "png", out);
			//noinspection ResultOfMethodCallIgnored
			DIR.mkdirs();
			Files.write(file.toPath(), out.toByteArray());
		}
		return img;
	}

	/**
	 * The corpus's wiki file for this mob: the per-form image when the row
	 * carries a version ("Abyssal demon (Catacombs of Kourend).png"), else
	 * the name's first image. Null when the corpus does not name one.
	 */
	String wikiFile(String name, String version)
	{
		Map<String, String> index = index();
		String exact = index.get(key(name, version));
		return exact != null ? exact : index.get(key(name, ""));
	}

	/** Built once, on the worker thread, from the bundled corpus. */
	private Map<String, String> index()
	{
		Map<String, String> local = files;
		if (local != null)
		{
			return local;
		}
		local = new HashMap<>();
		try (InputStream in = MonsterIcons.class.getResourceAsStream(CORPUS);
			InputStreamReader reader = new InputStreamReader(
				new GZIPInputStream(in), StandardCharsets.UTF_8))
		{
			JsonArray rows = new JsonParser().parse(reader).getAsJsonArray();
			for (JsonElement element : rows)
			{
				JsonObject row = element.getAsJsonObject();
				String name = string(row, "name");
				String image = string(row, "image");
				if (name.isEmpty() || image.isEmpty())
				{
					continue;
				}
				local.putIfAbsent(key(name, string(row, "version")), image);
				// The panel asks with an empty version whenever the corpus
				// holds one form, and with a normalized label when it holds
				// several - the bare name backs both. Prefer the plain
				// "<Name>.png" when the mob has one: a versioned mob's first
				// corpus row is often a side form (Abyssal demon's first row
				// is the Catacombs one), and the plain file is the right
				// picture for "just an abyssal demon".
				String bare = key(name, "");
				String plain = name + ".png";
				String held = local.get(bare);
				if (held == null
					|| (image.equalsIgnoreCase(plain) && !held.equalsIgnoreCase(plain)))
				{
					local.put(bare, image);
				}
			}
		}
		catch (Exception e)
		{
			// Fail soft, like every other bundled table: no index means no
			// thumbnails, never a broken panel.
			log.debug("monster image index unavailable", e);
		}
		files = local;
		return local;
	}

	private static String string(JsonObject row, String key)
	{
		JsonElement value = row.get(key);
		return value == null || value.isJsonNull() ? "" : value.getAsString();
	}

	private static String key(String name, String version)
	{
		return ((name == null ? "" : name) + "|" + (version == null ? "" : version))
			.toLowerCase(Locale.ROOT);
	}

	private BufferedImage fetch(String fileName) throws IOException
	{
		String encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
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
		worker.shutdown();
	}
}
