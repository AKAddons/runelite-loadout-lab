package com.loadoutlab.collection;

import com.google.gson.Gson;
import java.util.Map;
import net.runelite.client.config.ConfigManager;

public class ManualOwnedStore extends IdSetStore
{

	public ManualOwnedStore(ConfigManager configManager, Gson gson)
	{
		super(configManager, gson);
	}

	@Override
	String legacyKey()
	{
		// Already scoped before 0.4.1, so its pre-scope key never existed as a
		// bare "manualOwned" - the base's legacy fallback simply finds nothing.
		return "manualOwned";
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
