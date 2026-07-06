package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.loadoutlab.testsupport.InMemoryConfigManager;
import net.runelite.client.config.ConfigManager;
import org.junit.Assert;
import org.junit.Test;

public class ExclusionStoreTest
{
	@Test
	public void togglePersistsAcrossInstancesAndClears()
	{
		ConfigManager configManager = InMemoryConfigManager.create();
		ExclusionStore store = new ExclusionStore(configManager, new Gson());
		Assert.assertTrue(store.toggle(11230));
		Assert.assertTrue(store.isExcluded(11230));

		ExclusionStore next = new ExclusionStore(configManager, new Gson());
		Assert.assertTrue(next.isExcluded(11230));
		Assert.assertFalse(next.toggle(11230));
		Assert.assertFalse(next.isExcluded(11230));

		next.toggle(4151);
		next.clear();
		Assert.assertTrue(new ExclusionStore(configManager, new Gson()).snapshot().isEmpty());
	}
}
