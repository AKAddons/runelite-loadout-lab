package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.testsupport.InMemoryConfigManager;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PinStoreTest
{
	private ConfigManager configManager;
	private PinStore store;

	@BeforeEach
	void setUp()
	{
		configManager = InMemoryConfigManager.create();
		store = new PinStore(configManager, new Gson());
	}

	@Test
	@DisplayName("a pin persists across sessions and re-pinning a slot replaces it")
	void pinPersistsAndReplaces()
	{
		store.pin(GearSlot.HANDS, 21183);
		store.pin(GearSlot.NECK, 12018);
		store.pin(GearSlot.HANDS, 21177);

		PinStore next = new PinStore(configManager, new Gson());
		assertEquals(Integer.valueOf(21177), next.pinnedFor(GearSlot.HANDS));
		assertEquals(Integer.valueOf(12018), next.pinnedFor(GearSlot.NECK));
		assertEquals(2, next.snapshot().size());
	}

	@Test
	@DisplayName("unpin and clear empty out and persist")
	void unpinAndClearPersist()
	{
		store.pin(GearSlot.HANDS, 21183);
		store.unpin(GearSlot.HANDS);
		assertNull(store.pinnedFor(GearSlot.HANDS));

		store.pin(GearSlot.RING, 2550);
		store.clear();
		assertTrue(new PinStore(configManager, new Gson()).snapshot().isEmpty());
	}

	@Test
	@DisplayName("corrupt or unknown-slot config entries degrade to no pins")
	void corruptEntriesDegrade()
	{
		configManager.setConfiguration("loadoutlab", "pinnedItems", "{not json!");
		assertTrue(new PinStore(configManager, new Gson()).snapshot().isEmpty());

		configManager.setConfiguration("loadoutlab", "pinnedItems",
			"{\"NOT_A_SLOT\":123,\"HANDS\":21183}");
		PinStore mixed = new PinStore(configManager, new Gson());
		assertEquals(1, mixed.snapshot().size());
		assertEquals(Integer.valueOf(21183), mixed.pinnedFor(GearSlot.HANDS));
	}
}
