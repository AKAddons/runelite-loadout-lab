package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.testsupport.InMemoryConfigManager;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MonsterProfileStoreTest
{
	private ConfigManager configManager;
	private MonsterProfileStore store;

	@BeforeEach
	void setUp()
	{
		configManager = InMemoryConfigManager.create();
		store = new MonsterProfileStore(configManager, new Gson());
	}

	@Test
	@DisplayName("pins are per monster: the slaughter bracelet pinned at one mob never leaks to another")
	void pinsArePerMonster()
	{
		store.pin(415, GearSlot.HANDS, 21183);
		assertEquals(Map.of(GearSlot.HANDS, 21183), store.pinsFor(415));
		assertTrue(store.pinsFor(9999).isEmpty(), "another mob starts clean");

		store.pin(415, GearSlot.HANDS, 21177);
		assertEquals(Integer.valueOf(21177), store.pinsFor(415).get(GearSlot.HANDS),
			"re-pinning a slot replaces it");
		store.unpin(415, GearSlot.HANDS);
		assertTrue(store.pinsFor(415).isEmpty());
	}

	@Test
	@DisplayName("the whole profile - pins, note, filter items - survives a new session")
	void profilePersistsAcrossSessions()
	{
		store.pin(415, GearSlot.HANDS, 21183);
		store.setNote(415, "bring antidote++, pray melee after the spec");
		store.addFilterItem(415, 385, "Shark");

		MonsterProfileStore next = new MonsterProfileStore(configManager, new Gson());
		assertEquals(Map.of(GearSlot.HANDS, 21183), next.pinsFor(415));
		assertEquals("bring antidote++, pray melee after the spec", next.noteFor(415));
		assertEquals(Set.of(385), next.filterItemsFor(415));
		assertEquals("Shark", next.filterItemNamesFor(415).get(385));
	}

	@Test
	@DisplayName("clearing every field prunes the profile from config entirely")
	void emptyProfilesPrune()
	{
		store.pin(415, GearSlot.HANDS, 21183);
		store.setNote(415, "note");
		store.addFilterItem(415, 385, "Shark");
		store.unpin(415, GearSlot.HANDS);
		store.setNote(415, "  ");
		store.removeFilterItem(415, 385);

		String json = configManager.getConfiguration("loadoutlab", "monsterProfiles");
		assertEquals("{}", json, "empty profiles must not accumulate as husks");
	}

	@Test
	@DisplayName("corrupt config degrades to no profiles")
	void corruptDegrades()
	{
		configManager.setConfiguration("loadoutlab", "monsterProfiles", "{not json!");
		MonsterProfileStore fresh = new MonsterProfileStore(configManager, new Gson());
		assertTrue(fresh.pinsFor(415).isEmpty());
		assertEquals("", fresh.noteFor(415));
	}
}
