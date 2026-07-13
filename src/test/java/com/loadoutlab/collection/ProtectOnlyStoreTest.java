package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.loadoutlab.testsupport.InMemoryConfigManager;
import net.runelite.client.config.ConfigManager;
import org.junit.Assert;
import org.junit.Test;

public class ProtectOnlyStoreTest
{
	@Test
	public void togglePersistsAcrossInstances()
	{
		ConfigManager configManager = InMemoryConfigManager.create();
		ProtectOnlyStore store = new ProtectOnlyStore(configManager, new Gson());
		Assert.assertTrue("toggle on returns true", store.toggle(11802)); // dragon warhammer
		Assert.assertTrue(store.isProtectOnly(11802));

		ProtectOnlyStore reloaded = new ProtectOnlyStore(configManager, new Gson());
		Assert.assertTrue("survives a reload", reloaded.isProtectOnly(11802));
		Assert.assertFalse("toggle off returns false", reloaded.toggle(11802));
		Assert.assertFalse(reloaded.isProtectOnly(11802));
		Assert.assertTrue(new ProtectOnlyStore(configManager, new Gson()).snapshot().isEmpty());
	}
}
