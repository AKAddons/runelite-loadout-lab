package com.loadoutlab.collection;

import com.google.gson.Gson;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;

/**
 * Base for the loadoutlab config-JSON map stores: profile-aware reload,
 * defensive load (a corrupt entry starts fresh rather than failing the
 * plugin), and unset-when-empty save. Subclasses supply the config key
 * and the gson type; the IdSetStore family covers the Set-shaped stores.
 */
abstract class ConfigJsonStore<K, V>
{
	static final String CONFIG_GROUP = "loadoutlab";

	protected final ConfigManager configManager;
	protected final Gson gson;
	protected final Map<K, V> map = new LinkedHashMap<>();
	private final String key;
	private final Type type;

	ConfigJsonStore(ConfigManager configManager, Gson gson, String key, Type type)
	{
		this.configManager = configManager;
		this.gson = gson;
		this.key = key;
		this.type = type;
		load();
	}

	/** Re-read from config - the active RuneLite profile may have changed. */
	public synchronized void reload()
	{
		load();
	}

	private void load()
	{
		map.clear();
		String json = configManager.getConfiguration(CONFIG_GROUP, key);
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			Map<K, V> stored = gson.fromJson(json, type);
			if (stored != null)
			{
				map.putAll(stored);
			}
		}
		catch (RuntimeException ex)
		{
			// Corrupt entry: start fresh rather than failing the plugin.
		}
	}

	/** Persist the map; an empty one leaves no config residue. */
	void save()
	{
		if (map.isEmpty())
		{
			configManager.unsetConfiguration(CONFIG_GROUP, key);
			return;
		}
		saveJson(gson.toJson(map));
	}

	/** Write raw JSON under this store's key (bespoke save paths). */
	final void saveJson(String json)
	{
		configManager.setConfiguration(CONFIG_GROUP, key, json);
	}
}
