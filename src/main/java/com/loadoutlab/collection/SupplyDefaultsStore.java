package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Map;
import net.runelite.client.config.ConfigManager;

/**
 * The GLOBAL trip-supply defaults, set from the grey filter chip's menu
 * (field direction 2026-07-20: "set from the button instead of the panel").
 * Every category defaults to DETECT_BEST - always on - so only categories
 * the player changed are persisted; clearing back to Detect best removes
 * the entry.
 */
public class SupplyDefaultsStore extends ConfigJsonStore<String, String>
{
	static final String KEY = "supplyDefaults";
	public static final String DETECT_BEST = "DETECT_BEST";

	public SupplyDefaultsStore(ConfigManager configManager, Gson gson)
	{
		super(configManager, gson, KEY, new TypeToken<Map<String, String>>(){}.getType());
	}

	/** The category's default: DETECT_BEST (always on) unless changed. */
	public synchronized String choice(String category)
	{
		return map.getOrDefault(category, DETECT_BEST);
	}

	public synchronized void setChoice(String category, String choice)
	{
		if (choice == null || choice.isEmpty() || DETECT_BEST.equals(choice))
		{
			map.remove(category);
		}
		else
		{
			map.put(category, choice);
		}
		save();
	}
}
