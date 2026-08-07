package com.loadoutlab.collection;

import com.google.gson.Gson;
import net.runelite.client.config.ConfigManager;

public class ProtectOnlyStore extends IdSetStore
{
	static final String KEY = "protectOnlyItems";

	public ProtectOnlyStore(ConfigManager configManager, Gson gson)
	{
		super(configManager, gson);
		load();
	}

	@Override
	String key()
	{
		return KEY;
	}

	public boolean isProtectOnly(int itemId)
	{
		return contains(itemId);
	}
}
