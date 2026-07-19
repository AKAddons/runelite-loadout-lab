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
	private static final String ALL = MonsterProfileStore.ALL;

	private ConfigManager configManager;
	private MonsterProfileStore store;

	@BeforeEach
	void setUp()
	{
		configManager = InMemoryConfigManager.create();
		store = new MonsterProfileStore(configManager, new Gson());
	}

	@Test
	@DisplayName("pins are per monster AND per scope: a style pin overlays the all-sets pin")
	void pinsScopeAndOverlay()
	{
		store.pin(415, ALL, GearSlot.HANDS, 21183);
		store.pin(415, "RANGED", GearSlot.HANDS, 7462);

		assertEquals(Integer.valueOf(21183), store.pinsFor(415, "MELEE").get(GearSlot.HANDS),
			"melee inherits the all-sets pin");
		assertEquals(Integer.valueOf(7462), store.pinsFor(415, "RANGED").get(GearSlot.HANDS),
			"the ranged-scoped pin wins its own card");
		assertTrue(store.pinsFor(9999, "MELEE").isEmpty(), "another mob starts clean");

		store.unpin(415, "RANGED", GearSlot.HANDS);
		assertEquals(Integer.valueOf(21183), store.pinsFor(415, "RANGED").get(GearSlot.HANDS),
			"removing the style pin falls back to all-sets");
	}

	@Test
	@DisplayName("filter items merge all-sets with the style scope")
	void filterItemsMerge()
	{
		store.addFilterItem(415, ALL, 385, "Shark");
		store.addFilterItem(415, "MELEE", 12695, "Super combat potion(4)");
		store.addFilterItem(415, "RANGED", 2444, "Ranging potion(4)");

		assertEquals(Set.of(385, 12695), store.filterItemsFor(415, "MELEE"));
		assertEquals(Set.of(385, 2444), store.filterItemsFor(415, "RANGED"));
		assertEquals(Set.of(385), store.filterItemsFor(415, "MAGIC"));
	}

	@Test
	@DisplayName("per-mob exclusions merge all-sets with the style scope, per monster")
	void exclusionsMergeScopes()
	{
		store.exclude(415, ALL, 4151);
		store.exclude(415, "MELEE", 11802);
		store.exclude(9999, ALL, 12006);

		assertEquals(Set.of(4151, 11802), store.exclusionsFor(415, "MELEE"),
			"the melee card excludes the all-sets item AND the melee-only one");
		assertEquals(Set.of(4151), store.exclusionsFor(415, "RANGED"),
			"other cards only inherit the all-sets exclusion");
		assertEquals(Set.of(12006), store.exclusionsFor(9999, "MELEE"),
			"exclusions are per monster");
		assertTrue(store.exclusionsFor(1, "MELEE").isEmpty(), "unknown mob starts clean");
	}

	@Test
	@DisplayName("removing a per-mob exclusion restores the item for that scope only")
	void exclusionRemoval()
	{
		store.exclude(415, ALL, 4151);
		store.exclude(415, "MELEE", 4151);

		store.removeExclusion(415, "MELEE", 4151);
		assertEquals(Set.of(4151), store.exclusionsFor(415, "MELEE"),
			"the all-sets exclusion still applies after the style one is removed");

		store.removeExclusion(415, ALL, 4151);
		assertTrue(store.exclusionsFor(415, "MELEE").isEmpty());
		assertTrue(store.allExclusions(415).isEmpty(),
			"raw view empties once every scope is removed");
	}

	@Test
	@DisplayName("per-mob exclusions survive a reload (config round-trip)")
	void exclusionsPersist()
	{
		store.exclude(415, ALL, 4151);
		store.exclude(415, "RANGED", 12926);

		MonsterProfileStore reloaded = new MonsterProfileStore(configManager, new Gson());
		assertEquals(Set.of(4151, 12926), reloaded.exclusionsFor(415, "RANGED"));
		assertEquals(Map.of(ALL, Set.of(4151), "RANGED", Set.of(12926)),
			reloaded.allExclusions(415));
	}

	@Test
	@DisplayName("a profile holding only exclusions is not pruned; removing them prunes it")
	void exclusionsKeepTheProfileAlive()
	{
		store.exclude(415, ALL, 4151);
		MonsterProfileStore reloaded = new MonsterProfileStore(configManager, new Gson());
		assertEquals(Set.of(4151), reloaded.exclusionsFor(415, "MELEE"));

		reloaded.removeExclusion(415, ALL, 4151);
		MonsterProfileStore emptied = new MonsterProfileStore(configManager, new Gson());
		assertTrue(emptied.allExclusions(415).isEmpty());
	}

	@Test
	@DisplayName("the whole profile survives a new session, scopes intact")
	void profilePersistsAcrossSessions()
	{
		store.pin(415, ALL, GearSlot.HANDS, 21183);
		store.setNote(415, "bring antidote++, pray melee after the spec");
		store.addFilterItem(415, "MELEE", 12695, "Super combat potion(4)");

		MonsterProfileStore next = new MonsterProfileStore(configManager, new Gson());
		assertEquals(Map.of(GearSlot.HANDS, 21183), next.pinsFor(415, "MELEE"));
		assertEquals("bring antidote++, pray melee after the spec", next.noteFor(415));
		assertEquals(Set.of(12695), next.filterItemsFor(415, "MELEE"));
		assertEquals("Super combat potion(4)",
			next.allFilterItems(415).get("MELEE").get(12695));
	}

	@Test
	@DisplayName("the pinned spell persists per mob and clears to auto")
	void pinnedSpellPersists()
	{
		store.setPinnedSpell(415, "Wind Bolt");
		assertEquals("Wind Bolt", new MonsterProfileStore(configManager, new Gson())
			.pinnedSpellFor(415));
		assertEquals("", store.pinnedSpellFor(9999), "other mobs stay on auto");
		store.setPinnedSpell(415, "");
		assertEquals("", store.pinnedSpellFor(415));
	}

	@Test
	@DisplayName("clearing every field prunes the profile from config entirely")
	void emptyProfilesPrune()
	{
		store.pin(415, ALL, GearSlot.HANDS, 21183);
		store.setNote(415, "note");
		store.setPinnedSpell(415, "Wind Bolt");
		store.addFilterItem(415, "MELEE", 385, "Shark");
		store.unpin(415, ALL, GearSlot.HANDS);
		store.setNote(415, "  ");
		store.setPinnedSpell(415, null);
		store.removeFilterItem(415, "MELEE", 385);

		String json = configManager.getConfiguration("loadoutlab", "monsterProfiles");
		assertEquals("{}", json, "empty profiles must not accumulate as husks");
	}

	@Test
	@DisplayName("corrupt config degrades to no profiles")
	void corruptDegrades()
	{
		configManager.setConfiguration("loadoutlab", "monsterProfiles", "{not json!");
		MonsterProfileStore fresh = new MonsterProfileStore(configManager, new Gson());
		assertTrue(fresh.pinsFor(415, "MELEE").isEmpty());
		assertEquals("", fresh.noteFor(415));
	}

	@Test
	@DisplayName("a sims-only profile survives the save (the Sim here field bug)")
	void simsOnlyProfilePersists()
	{
		// Field bug 2026-07-18: save()'s empty-profile prune did not count
		// sims, so a profile holding ONLY a sim was erased by the very call
		// that added it - "Sim here" appeared to do nothing.
		store.addSim(239, 22324, "Ghrazi rapier");
		assertEquals(Map.of(22324, "Ghrazi rapier"), store.allSims(239));
		assertEquals(Set.of(22324), store.simsFor(239));

		MonsterProfileStore reloaded = new MonsterProfileStore(configManager, new Gson());
		assertEquals(Map.of(22324, "Ghrazi rapier"), reloaded.allSims(239),
			"the sim survives a config round-trip");

		store.removeSim(239, 22324);
		assertTrue(store.allSims(239).isEmpty());
		assertEquals("{}", configManager.getConfiguration("loadoutlab", "monsterProfiles"),
			"an emptied sims profile prunes back to nothing");
	}
}
