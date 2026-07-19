package com.loadoutlab.collection;

import com.google.gson.Gson;
import java.util.Map;
import net.runelite.client.config.ConfigManager;

public class ManualOwnedStore extends IdSetStore
{
	private String worldScope = "std";

	public ManualOwnedStore(ConfigManager configManager, Gson gson)
	{
		super(configManager, gson);
	}

	@Override
	String key()
	{
		return worldScope + ".manualOwned";
	}

	public synchronized void loadScope(String scope)
	{
		this.worldScope = scope;
		load();
	}

	public boolean isStored(int itemId)
	{
		return contains(itemId);
	}

	public synchronized Map<Integer, Integer> mergeInto(Map<Integer, Integer> owned)
	{
		for (int id : ids)
		{
			owned.merge(id, 1, Integer::sum);
		}
		return owned;
	}
}
