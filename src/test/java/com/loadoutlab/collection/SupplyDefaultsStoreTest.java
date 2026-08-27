package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.loadoutlab.testsupport.InMemoryConfigManager;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SupplyDefaultsStoreTest
{
	private static final String SCOPE = "std.1111";
	private ConfigManager configManager;
	private SupplyDefaultsStore store;

	@BeforeEach
	void setUp()
	{
		configManager = InMemoryConfigManager.create();
		store = freshStore();
	}

	/** The plugin always loads a character scope before use; a store
	 * without one writes to the pre-0.4.1 unscoped key, so a reload
	 * through a second instance would read a different key entirely. */
	private SupplyDefaultsStore freshStore()
	{
		SupplyDefaultsStore fresh = new SupplyDefaultsStore(configManager, new Gson());
		fresh.loadScope(SCOPE);
		return fresh;
	}

	@Test
	@DisplayName("every category is Detect best (always on) until changed")
	void detectBestIsTheUniversalDefault()
	{
		assertEquals("DETECT_BEST", store.choice("food"));
		assertEquals("DETECT_BEST", store.choice("antivenom"));
		assertNull(configManager.getConfiguration("loadoutlab", SCOPE + ".supplyDefaults"),
			"defaults leave no config residue");
	}

	@Test
	@DisplayName("a changed choice persists; returning to Detect best removes it")
	void choicesPersistAndClear()
	{
		store.setChoice("prayerRestore", "SANFEW_SERUM");
		store.setChoice("surge", "NONE");
		SupplyDefaultsStore reloaded = freshStore();
		assertEquals("SANFEW_SERUM", reloaded.choice("prayerRestore"));
		assertEquals("NONE", reloaded.choice("surge"));

		store.setChoice("prayerRestore", "DETECT_BEST");
		store.setChoice("surge", null);
		assertEquals("DETECT_BEST", store.choice("prayerRestore"));
		assertNull(configManager.getConfiguration("loadoutlab", SCOPE + ".supplyDefaults"),
			"an all-default store unsets its config key");
	}
}
