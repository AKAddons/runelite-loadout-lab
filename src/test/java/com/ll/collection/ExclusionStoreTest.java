package com.ll.collection;

import com.google.gson.Gson;
import com.ll.testsupport.InMemoryConfigManager;
import net.runelite.client.config.ConfigManager;
import org.junit.Assert;
import org.junit.Test;

public class ExclusionStoreTest {
	@Test
	public void togglePersistsAcrossInstancesAndClears() {
		ConfigManager cfg = InMemoryConfigManager.create();
		IdSetStore store = new IdSetStore(cfg, new Gson(), "excludedItems");
		Assert.assertTrue(store.toggle(11230));
		Assert.assertTrue(store.contains(11230));

		IdSetStore next = new IdSetStore(cfg, new Gson(), "excludedItems");
		Assert.assertTrue(next.contains(11230));
		Assert.assertFalse(next.toggle(11230));
		Assert.assertFalse(next.contains(11230));

		next.toggle(4151);
		next.clear();
		Assert.assertTrue(new IdSetStore(cfg, new Gson(), "excludedItems").snapshot().isEmpty());
	}
}
