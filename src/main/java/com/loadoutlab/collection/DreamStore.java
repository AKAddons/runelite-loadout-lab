package com.loadoutlab.collection;

import com.google.gson.Gson;
import net.runelite.client.config.ConfigManager;

public class DreamStore extends IdSetStore
{
	static final String KEY = "dreamItems";

	public DreamStore(ConfigManager configManager, Gson gson)
	{
		super(configManager, gson);
		load();
	}

	@Override
	String key()
	{
		return KEY;
	}

	public boolean isDreamed(int itemId)
	{
		return contains(itemId);
	}
}
