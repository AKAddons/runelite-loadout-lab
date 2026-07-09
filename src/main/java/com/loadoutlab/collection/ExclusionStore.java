package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.client.config.ConfigManager;

/**
 * Items the player has excluded from suggestions ("protect my dragon
 * darts") - a persistent, global id set stored in the plugin's config
 * group. Kept intentionally scope-free in v1; per-monster exclusions are
 * on the roadmap.
 */
public class ExclusionStore
{
	static final String CONFIG_GROUP = "loadoutlab";
	static final String KEY = "excludedItems";

	private final ConfigManager configManager;
	private final Gson gson;
	private final Set<Integer> excluded = new LinkedHashSet<>();

	public ExclusionStore(ConfigManager configManager, Gson gson)
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
		excluded.clear();
		String json = configManager.getConfiguration(CONFIG_GROUP, KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			Set<Integer> stored = gson.fromJson(json, new TypeToken<Set<Integer>>(){}.getType());
			if (stored != null)
			{
				excluded.addAll(stored);
			}
		}
		catch (RuntimeException ex)
		{
			// Corrupt entry: start fresh rather than failing the plugin.
		}
	}

	/** Toggles the item; returns true when it is now excluded. */
	public synchronized boolean toggle(int itemId)
	{
		boolean nowExcluded = !excluded.remove(itemId) && excluded.add(itemId);
		configManager.setConfiguration(CONFIG_GROUP, KEY, gson.toJson(excluded));
		return nowExcluded;
	}

	public synchronized void clear()
	{
		excluded.clear();
		configManager.setConfiguration(CONFIG_GROUP, KEY, gson.toJson(excluded));
	}

	public synchronized boolean isExcluded(int itemId)
	{
		return excluded.contains(itemId);
	}

	public synchronized Set<Integer> snapshot()
	{
		return Collections.unmodifiableSet(new LinkedHashSet<>(excluded));
	}
}
