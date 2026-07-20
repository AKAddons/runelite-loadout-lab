package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.client.config.ConfigManager;

public abstract class IdSetStore
{
	static final String CONFIG_GROUP = "loadoutlab";

	private final ConfigManager configManager;
	private final Gson gson;
	final Set<Integer> ids = new LinkedHashSet<>();

	IdSetStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	abstract String key();

	public synchronized void reload()
	{
		load();
	}

	final void load()
	{
		ids.clear();
		String json = configManager.getConfiguration(CONFIG_GROUP, key());
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			Set<Integer> stored = gson.fromJson(json, new TypeToken<Set<Integer>>(){}.getType());
			if (stored != null)
			{
				ids.addAll(stored);
			}
		}
		catch (RuntimeException ex)
		{
			// Corrupt entry: start fresh rather than failing the plugin.
		}
	}

	final void save()
	{
		configManager.setConfiguration(CONFIG_GROUP, key(), gson.toJson(ids));
	}

	public synchronized boolean toggle(int itemId)
	{
		boolean added = !ids.remove(itemId) && ids.add(itemId);
		save();
		return added;
	}

	public synchronized void clear()
	{
		ids.clear();
		save();
	}

	public synchronized boolean contains(int itemId)
	{
		return ids.contains(itemId);
	}

	public synchronized Set<Integer> snapshot()
	{
		return Collections.unmodifiableSet(new LinkedHashSet<>(ids));
	}
}
