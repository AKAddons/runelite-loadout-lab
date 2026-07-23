package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;

/**
 * The GLOBAL always-filter list (the grey member of the exclude/sim/filter
 * trio, field spec 2026-07-20): items that join EVERY filtered bank view -
 * teleport capes, boss teles, whatever the player always wants at hand.
 * Names are captured at add time (id resolution later needs the client
 * thread), matching the mob-profile filter items.
 */
public class AlwaysFilterStore
{
	static final String CONFIG_GROUP = "loadoutlab";
	static final String KEY = "alwaysFilterItems";

	private final ConfigManager configManager;
	private final Gson gson;
	private final Map<Integer, String> items = new LinkedHashMap<>();

	public AlwaysFilterStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
		load();
	}

	/** Re-read from config - the active RuneLite profile may have changed. */
	public synchronized void reload()
	{
		load();
	}

	private void load()
	{
		items.clear();
		String json = configManager.getConfiguration(CONFIG_GROUP, KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			Map<Integer, String> stored = gson.fromJson(json,
				new TypeToken<Map<Integer, String>>(){}.getType());
			if (stored != null)
			{
				items.putAll(stored);
			}
		}
		catch (RuntimeException ex)
		{
			// Corrupt entry: start fresh rather than failing the plugin.
		}
	}

	public synchronized Map<Integer, String> all()
	{
		return Collections.unmodifiableMap(new LinkedHashMap<>(items));
	}

	public synchronized void add(int itemId, String name)
	{
		items.put(itemId, name == null ? ("item " + itemId) : name);
		save();
	}

	public synchronized void remove(int itemId)
	{
		items.remove(itemId);
		save();
	}

	private void save()
	{
		if (items.isEmpty())
		{
			configManager.unsetConfiguration(CONFIG_GROUP, KEY);
			return;
		}
		configManager.setConfiguration(CONFIG_GROUP, KEY, gson.toJson(items));
	}
}
