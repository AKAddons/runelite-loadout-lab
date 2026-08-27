package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.loadoutlab.testsupport.InMemoryConfigManager;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlwaysFilterStoreTest
{
	private static final String SCOPE = "std.1111";
	private ConfigManager configManager;
	private AlwaysFilterStore store;

	@BeforeEach
	void setUp()
	{
		configManager = InMemoryConfigManager.create();
		store = freshStore();
	}

	/** The plugin always loads a character scope before use; a store
	 * without one writes to the pre-0.4.1 unscoped key, so a reload
	 * through a second instance would read a different key entirely. */
	private AlwaysFilterStore freshStore()
	{
		AlwaysFilterStore fresh = new AlwaysFilterStore(configManager, new Gson());
		fresh.loadScope(SCOPE);
		return fresh;
	}

	@Test
	@DisplayName("always-filter items persist with their add-time names and survive a reload")
	void itemsPersistAcrossReload()
	{
		store.add(9790, "Construction cape");
		store.add(13280, "Max cape");
		assertEquals(Map.of(9790, "Construction cape", 13280, "Max cape"), store.all());

		AlwaysFilterStore reloaded = freshStore();
		assertEquals("Construction cape", reloaded.all().get(9790),
			"the list survives a config round-trip");
	}

	@Test
	@DisplayName("removing the last item unsets the config key entirely")
	void emptyListUnsetsConfig()
	{
		store.add(9790, "Construction cape");
		store.remove(9790);
		assertTrue(store.all().isEmpty());
		assertNull(configManager.getConfiguration("loadoutlab", SCOPE + ".alwaysFilterItems"),
			"an empty list leaves no config residue");
	}

	@Test
	@DisplayName("a null add-time name falls back to the item id")
	void nullNameFallsBack()
	{
		store.add(1234, null);
		assertEquals("item 1234", store.all().get(1234));
	}
}
