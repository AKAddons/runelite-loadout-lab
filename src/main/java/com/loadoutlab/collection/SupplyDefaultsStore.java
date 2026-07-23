package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.LinkedHashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;

/**
 * The GLOBAL trip-supply defaults, set from the grey filter chip's menu
 * (field direction 2026-07-20: "set from the button instead of the panel").
 * Every category defaults to DETECT_BEST - always on - so only categories
 * the player changed are persisted; clearing back to Detect best removes
 * the entry.
 */
public class SupplyDefaultsStore
{
	static final String CONFIG_GROUP = "loadoutlab";
	static final String KEY = "supplyDefaults";
	public static final String DETECT_BEST = "DETECT_BEST";

	private final ConfigManager configManager;
	private final Gson gson;
	private final Map<String, String> choices = new LinkedHashMap<>();

	public SupplyDefaultsStore(ConfigManager configManager, Gson gson)
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
		choices.clear();
		String json = configManager.getConfiguration(CONFIG_GROUP, KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			Map<String, String> stored = gson.fromJson(json,
				new TypeToken<Map<String, String>>(){}.getType());
			if (stored != null)
			{
				choices.putAll(stored);
			}
		}
		catch (RuntimeException ex)
		{
			// Corrupt entry: start fresh rather than failing the plugin.
		}
	}

	/** The category's default: DETECT_BEST (always on) unless changed. */
	public synchronized String choice(String category)
	{
		return choices.getOrDefault(category, DETECT_BEST);
	}

	public synchronized void setChoice(String category, String choice)
	{
		if (choice == null || choice.isEmpty() || DETECT_BEST.equals(choice))
		{
			choices.remove(category);
		}
		else
		{
			choices.put(category, choice);
		}
		save();
	}

	private void save()
	{
		if (choices.isEmpty())
		{
			configManager.unsetConfiguration(CONFIG_GROUP, KEY);
			return;
		}
		configManager.setConfiguration(CONFIG_GROUP, KEY, gson.toJson(choices));
	}
}
