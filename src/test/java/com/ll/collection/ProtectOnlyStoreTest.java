package com.ll.collection;

import com.google.gson.Gson;
import com.ll.testsupport.InMemoryConfigManager;
import net.runelite.client.config.ConfigManager;
import org.junit.Assert;
import org.junit.Test;

public class ProtectOnlyStoreTest {
	@Test
	public void togglePersistsAcrossInstances() {
		ConfigManager cfg = InMemoryConfigManager.create();
		IdSetStore store = new IdSetStore(cfg, new Gson(), "protectOnlyItems");
		Assert.assertTrue("toggle on returns true", store.toggle(11802)); // dragon warhammer
		Assert.assertTrue(store.contains(11802));

		IdSetStore reloaded = new IdSetStore(cfg, new Gson(), "protectOnlyItems");
		Assert.assertTrue("survives a reload", reloaded.contains(11802));
		Assert.assertFalse("toggle off returns false", reloaded.toggle(11802));
		Assert.assertFalse(reloaded.contains(11802));
		Assert.assertTrue(new IdSetStore(cfg, new Gson(), "protectOnlyItems").snapshot().isEmpty());
	}
}
