package com.loadoutlab.collection;

import com.google.gson.Gson;
import net.runelite.client.config.ConfigManager;

public class ExclusionStore extends IdSetStore
{
	static final String KEY = "excludedItems";

	public ExclusionStore(ConfigManager configManager, Gson gson)
	{
		super(configManager, gson);
		load();
	}

	@Override
	String key()
	{
		return KEY;
	}

	public boolean isExcluded(int itemId)
	{
		return contains(itemId);
	}
}
