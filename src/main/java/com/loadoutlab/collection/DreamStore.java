package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.client.config.ConfigManager;

/**
 * "Green" items: unowned gear the player wants CONSIDERED as owned -
 * dreaming about an upgrade before buying or grinding it. Same
 * persistence shape as ExclusionStore; exclusions (red) always win
 * over dreams (green) because they filter earlier.
 */
public class DreamStore
{
	static final String CONFIG_GROUP = "loadoutlab";
	static final String KEY = "dreamItems";

	private final ConfigManager configManager;
	private final Gson gson;
	private final Set<Integer> dreamed = new LinkedHashSet<>();

	public DreamStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
		load();
	}

	private void load()
	{
		dreamed.clear();
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
				dreamed.addAll(stored);
			}
		}
		catch (RuntimeException ex)
		{
			// Corrupt entry: start fresh rather than failing the plugin.
		}
	}

	/** Toggles the item; returns true when it is now dreamed. */
	public synchronized boolean toggle(int itemId)
	{
		boolean nowDreamed = !dreamed.remove(itemId) && dreamed.add(itemId);
		configManager.setConfiguration(CONFIG_GROUP, KEY, gson.toJson(dreamed));
		return nowDreamed;
	}

	public synchronized void clear()
	{
		dreamed.clear();
		configManager.setConfiguration(CONFIG_GROUP, KEY, gson.toJson(dreamed));
	}

	public synchronized boolean isDreamed(int itemId)
	{
		return dreamed.contains(itemId);
	}

	public synchronized Set<Integer> snapshot()
	{
		return Collections.unmodifiableSet(new LinkedHashSet<>(dreamed));
	}
}
