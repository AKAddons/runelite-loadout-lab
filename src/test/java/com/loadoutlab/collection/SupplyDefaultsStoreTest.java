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
	private ConfigManager configManager;
	private SupplyDefaultsStore store;

	@BeforeEach
	void setUp()
	{
		configManager = InMemoryConfigManager.create();
		store = new SupplyDefaultsStore(configManager, new Gson());
	}

	@Test
	@DisplayName("every category is Detect best (always on) until changed")
	void detectBestIsTheUniversalDefault()
	{
		assertEquals("DETECT_BEST", store.choice("food"));
		assertEquals("DETECT_BEST", store.choice("antivenom"));
		assertNull(configManager.getConfiguration("loadoutlab", "supplyDefaults"),
			"defaults leave no config residue");
	}

	@Test
	@DisplayName("a changed choice persists; returning to Detect best removes it")
	void choicesPersistAndClear()
	{
		store.setChoice("prayerRestore", "SANFEW_SERUM");
		store.setChoice("surge", "NONE");
		SupplyDefaultsStore reloaded = new SupplyDefaultsStore(configManager, new Gson());
		assertEquals("SANFEW_SERUM", reloaded.choice("prayerRestore"));
		assertEquals("NONE", reloaded.choice("surge"));

		store.setChoice("prayerRestore", "DETECT_BEST");
		store.setChoice("surge", null);
		assertEquals("DETECT_BEST", store.choice("prayerRestore"));
		assertNull(configManager.getConfiguration("loadoutlab", "supplyDefaults"),
			"an all-default store unsets its config key");
	}
}
