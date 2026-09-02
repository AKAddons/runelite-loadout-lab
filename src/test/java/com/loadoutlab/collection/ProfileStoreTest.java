package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.loadoutlab.engine.PrayerBonuses;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.testsupport.InMemoryConfigManager;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field report (Not on Hand, 2026-09-01): "its suggesting piety now and im
 * 45 prayer lol... maybe it did that before my account was logged in".
 * The bank persisted per character; the levels did not, so a logged-out
 * compute priced a maxed stranger wearing that bank. This pins the store
 * the plugin restores from while logged out.
 */
class ProfileStoreTest
{
	private ConfigManager configManager;

	@BeforeEach
	void setUp()
	{
		configManager = InMemoryConfigManager.create();
	}

	private ProfileStore store(String scope)
	{
		ProfileStore store = new ProfileStore(configManager, new Gson());
		store.loadScope(scope);
		return store;
	}

	/** Not on Hand's shape: 45 Prayer, no King's Ransom, no scroll unlocks. */
	private static Map<Skill, Integer> fortyFivePrayer()
	{
		Map<Skill, Integer> real = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			real.put(skill, 70);
		}
		real.put(Skill.PRAYER, 45);
		real.put(Skill.HITPOINTS, 75);
		return real;
	}

	@Test
	@DisplayName("a character never seen has no snapshot - the MAXED fallback stays theirs")
	void neverSeenIsNull()
	{
		ProfileStore store = store("std.1111");
		assertNull(store.profile());
		assertNull(store.levels());
		assertNull(store.unlocks());
	}

	@Test
	@DisplayName("the snapshot survives a restart, and a 45-prayer account never sees Piety")
	void snapshotRoundTrips()
	{
		Set<String> quests = new HashSet<>();
		quests.add(Quest.DRAGON_SLAYER_I.name());
		store("std.1111").save(fortyFivePrayer(), quests,
			new PrayerUnlocks(false, true, false, true, false));

		// A fresh store instance is what a client restart gives the plugin.
		ProfileStore restarted = store("std.1111");
		assertEquals(45, restarted.levels().getPrayer());
		assertEquals(75, restarted.levels().getHitpoints());
		assertTrue(restarted.profile().getCompletedQuests().contains("DRAGON_SLAYER_I"));
		assertFalse(restarted.profile().getCompletedQuests().contains(Quest.KINGS_RANSOM.name()));
		PrayerUnlocks unlocks = restarted.unlocks();
		assertFalse(unlocks.piety(), "King's Ransom bit leaked");
		assertTrue(unlocks.rigour());
		assertFalse(unlocks.augury());
		assertTrue(unlocks.deadeye());
		assertFalse(unlocks.mysticVigour());

		// The field report, end to end: the restored levels gate Piety out.
		PrayerBonuses best = PrayerBonuses.bestAvailable(restarted.levels(), unlocks);
		assertTrue(best.getMeleeStrength() < 1.23, "Piety (x1.23 strength) was assumed at 45 Prayer");
	}

	@Test
	@DisplayName("two characters keep separate snapshots")
	void snapshotsDoNotBleedBetweenCharacters()
	{
		ProfileStore store = store("std.1111");
		store.save(fortyFivePrayer(), new HashSet<>(), PrayerUnlocks.F2P);

		store.loadScope("std.2222");
		assertNull(store.levels(), "the alt inherited the main's levels");

		store.loadScope("std.1111");
		assertEquals(45, store.levels().getPrayer(), "the main lost its snapshot on return");
	}

	@Test
	@DisplayName("lastScope names the character seen last, for a logged-out start")
	void lastScopeFollowsTheLatestLogin()
	{
		ProfileStore store = new ProfileStore(configManager, new Gson());
		assertNull(store.lastScope(), "a fresh install has no last character");
		store.loadScope("std.1111");
		store.loadScope("seasonal.1111");
		assertEquals("seasonal.1111", new ProfileStore(configManager, new Gson()).lastScope());
	}

	@Test
	@DisplayName("a corrupt entry counts as never seen rather than failing the plugin")
	void corruptEntryIsNeverSeen()
	{
		configManager.setConfiguration("loadoutlab", "std.1111.profile", "{not json");
		assertNull(store("std.1111").levels());
	}
}
