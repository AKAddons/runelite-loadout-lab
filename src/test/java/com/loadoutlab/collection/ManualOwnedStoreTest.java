package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.loadoutlab.testsupport.InMemoryConfigManager;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ManualOwnedStoreTest
{
	private ConfigManager configManager;
	private ManualOwnedStore store;

	@BeforeEach
	void setUp()
	{
		configManager = InMemoryConfigManager.create();
		store = new ManualOwnedStore(configManager, new Gson());
		store.loadScope("std.1111");
	}

	@Test
	@DisplayName("marking an item stored elsewhere survives a new session on the same account")
	void togglePersistsAcrossInstances()
	{
		assertTrue(store.toggle(4151));
		assertTrue(store.isStored(4151));

		ManualOwnedStore next = new ManualOwnedStore(configManager, new Gson());
		next.loadScope("std.1111");
		assertTrue(next.isStored(4151));
		assertFalse(next.toggle(4151));
		assertFalse(next.isStored(4151));
	}

	@Test
	@DisplayName("stored-elsewhere lists never leak between accounts or world types")
	void scopesAreIsolated()
	{
		store.toggle(4151);

		store.loadScope("std.2222");
		assertTrue(store.snapshot().isEmpty(), "account B starts empty");
		store.toggle(11802);

		store.loadScope("seasonal.1111");
		assertTrue(store.snapshot().isEmpty(), "seasonal scope starts empty");

		store.loadScope("std.1111");
		assertTrue(store.isStored(4151));
		assertFalse(store.isStored(11802), "account B's item must not leak into A");
	}

	@Test
	@DisplayName("merging into the ledger view adds quantity-1 entries without losing real counts")
	void mergeIntoAddsWithoutLosingCounts()
	{
		store.toggle(12006);
		store.toggle(4151);

		Map<Integer, Integer> owned = new HashMap<>(Map.of(4151, 1, 861, 2));
		Map<Integer, Integer> merged = store.mergeInto(owned);

		assertEquals(1, merged.get(12006), "stored-only item joins at quantity 1");
		assertEquals(2, merged.get(861), "ledger counts pass through untouched");
		assertTrue(merged.get(4151) >= 1, "an item both banked and stored stays owned");
	}

	@Test
	@DisplayName("a corrupt persisted entry starts the list fresh instead of failing")
	void corruptEntryDegradesGracefully()
	{
		configManager.setConfiguration("loadoutlab", "std.1111.manualOwned", "{not json!");
		ManualOwnedStore fresh = new ManualOwnedStore(configManager, new Gson());
		fresh.loadScope("std.1111");
		assertTrue(fresh.snapshot().isEmpty());
	}

	@Test
	@DisplayName("clear empties the list and the empty state persists")
	void clearEmptiesAndPersists()
	{
		store.toggle(4151);
		store.clear();

		ManualOwnedStore next = new ManualOwnedStore(configManager, new Gson());
		next.loadScope("std.1111");
		assertTrue(next.snapshot().isEmpty());
	}
}
