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

	private static final String SCOPE = "std.1111";
	private ConfigManager configManager;
	private MonsterProfileStore store;

	@BeforeEach
	void setUp()
	{
		configManager = InMemoryConfigManager.create();
		store = freshStore();
	}

	/** The plugin always loads a character scope before use; a store without
	 * one writes to the pre-0.4.1 unscoped key, so an assertion on a scoped
	 * key would pass without testing anything. */
	private MonsterProfileStore freshStore()
	{
		MonsterProfileStore fresh = new MonsterProfileStore(configManager, new Gson());
		fresh.loadScope(SCOPE);
		return fresh;
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

		assertEquals(Set.of(385, 12695), filterItemsFor(store, 415, "MELEE"));
		assertEquals(Set.of(385, 2444), filterItemsFor(store, 415, "RANGED"));
		assertEquals(Set.of(385), filterItemsFor(store, 415, "MAGIC"));
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

		store.removeMobExclusion(415, "MELEE", 4151);
		assertEquals(Set.of(4151), store.exclusionsFor(415, "MELEE"),
			"the all-sets exclusion still applies after the style one is removed");

		store.removeMobExclusion(415, ALL, 4151);
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

		MonsterProfileStore reloaded = freshStore();
		assertEquals(Set.of(4151, 12926), reloaded.exclusionsFor(415, "RANGED"));
		assertEquals(Map.of(ALL, Set.of(4151), "RANGED", Set.of(12926)),
			reloaded.allExclusions(415));
	}

	@Test
	@DisplayName("a profile holding only exclusions is not pruned; removing them prunes it")
	void exclusionsKeepTheProfileAlive()
	{
		store.exclude(415, ALL, 4151);
		MonsterProfileStore reloaded = freshStore();
		assertEquals(Set.of(4151), reloaded.exclusionsFor(415, "MELEE"));

		reloaded.removeMobExclusion(415, ALL, 4151);
		MonsterProfileStore emptied = freshStore();
		assertTrue(emptied.allExclusions(415).isEmpty());
	}

	@Test
	@DisplayName("the whole profile survives a new session, scopes intact")
	void profilePersistsAcrossSessions()
	{
		store.pin(415, ALL, GearSlot.HANDS, 21183);
		store.setNote(415, "bring antidote++, pray melee after the spec");
		store.addFilterItem(415, "MELEE", 12695, "Super combat potion(4)");

		MonsterProfileStore next = freshStore();
		assertEquals(Map.of(GearSlot.HANDS, 21183), next.pinsFor(415, "MELEE"));
		assertEquals("bring antidote++, pray melee after the spec", next.note(415));
		assertEquals(Set.of(12695), filterItemsFor(next, 415, "MELEE"));
		assertEquals("Super combat potion(4)",
			next.allFilterItems(415).get("MELEE").get(12695));
	}

	@Test
	@DisplayName("the pinned spell persists per mob and clears to auto")
	void pinnedSpellPersists()
	{
		store.setPinnedSpell(415, "Wind Bolt");
		assertEquals("Wind Bolt", freshStore()
			.pinnedSpell(415));
		assertEquals("", store.pinnedSpell(9999), "other mobs stay on auto");
		store.setPinnedSpell(415, "");
		assertEquals("", store.pinnedSpell(415));
	}

	@Test
	@DisplayName("a spec-only profile survives save (not judged empty and pruned)")
	void pinnedSpecPersists()
	{
		// Field report 2026-08-09: pinning a spec did nothing because the
		// save() empty-check omitted the spec field, so a spec-only profile
		// was pruned in the same call that set it (the exact 2026-07-18 sims
		// bug, recurring). Read back from a FRESH store = the config
		// round-trip, the path the compute actually reads.
		store.setPinnedSpec(415, 28922);
		assertEquals(28922, freshStore()
			.pinnedSpec(415), "the spec pin must survive save + reload");
		assertEquals(0, store.pinnedSpec(9999), "other mobs stay on auto");
		store.setPinnedSpec(415, 0);
		assertEquals(0, store.pinnedSpec(415), "clearing returns to auto");
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
		store.removeMobFilter(415, "MELEE", 385);

		String json = configManager.getConfiguration("loadoutlab", SCOPE + ".monsterProfiles");
		assertEquals("{}", json, "empty profiles must not accumulate as husks");
	}

	@Test
	@DisplayName("corrupt config degrades to no profiles")
	void corruptDegrades()
	{
		configManager.setConfiguration("loadoutlab", SCOPE + ".monsterProfiles", "{not json!");
		MonsterProfileStore fresh = freshStore();
		assertTrue(fresh.pinsFor(415, "MELEE").isEmpty());
		assertEquals("", fresh.note(415));
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

		MonsterProfileStore reloaded = freshStore();
		assertEquals(Map.of(22324, "Ghrazi rapier"), reloaded.allSims(239),
			"the sim survives a config round-trip");

		store.removeMobSim(239, 22324);
		assertTrue(store.allSims(239).isEmpty());
		assertEquals("{}", configManager.getConfiguration("loadoutlab", SCOPE + ".monsterProfiles"),
			"an emptied sims profile prunes back to nothing");
	}

	@Test
	@DisplayName("supply overrides persist per mob and clear back to the global default")
	void supplyOverridesPersistAndClear()
	{
		store.setSupplyOverride(8781, "prayerRestore", "SANFEW_SERUM");
		store.setSupplyOverride(8781, "food", "NONE");
		assertEquals(Map.of("prayerRestore", "SANFEW_SERUM", "food", "NONE"),
			store.supplyOverrides(8781));
		assertTrue(store.supplyOverrides(9999).isEmpty(), "another mob keeps the defaults");

		MonsterProfileStore reloaded = freshStore();
		assertEquals("SANFEW_SERUM", reloaded.supplyOverrides(8781).get("prayerRestore"),
			"overrides survive a config round-trip");

		store.setSupplyOverride(8781, "prayerRestore", null);
		assertEquals(Map.of("food", "NONE"), store.supplyOverrides(8781),
			"a null choice returns the category to the global default");
	}

	@Test
	@DisplayName("a supplies-only profile survives the save (the every-field prune rule)")
	void suppliesOnlyProfilePersists()
	{
		// The Sim-here field bug's lesson: every Stored field must join
		// save()'s empty check, or a supplies-only profile would be erased
		// by the very call that set it.
		store.setSupplyOverride(8781, "antivenom", "ANTIVENOM_PLUS");
		MonsterProfileStore reloaded = freshStore();
		assertEquals(Map.of("antivenom", "ANTIVENOM_PLUS"), reloaded.supplyOverrides(8781));

		store.setSupplyOverride(8781, "antivenom", "");
		assertEquals("{}", configManager.getConfiguration("loadoutlab", SCOPE + ".monsterProfiles"),
			"clearing the only override prunes the profile away");
	}

	@Test
	@DisplayName("skipping degradable gear for a mob folds the degradable ids into every scope's exclusions, keeps the manage list clean, and survives a reload")
	void skipDegradable()
	{
		MonsterProfileStore store = new MonsterProfileStore(configManager, new Gson());
		store.setDegradableIds(Set.of(4716, 12006));
		assertFalse(store.skipDegradable(7, false), "unset follows the panel default");
		assertTrue(store.skipDegradable(7, true), "unset follows the panel default");
		store.setSkipDegradable(7, true);
		assertTrue(store.exclusionsFor(7, "MELEE").containsAll(Set.of(4716, 12006)));
		assertTrue(store.exclusionsFor(7, MonsterProfileStore.ALL_SETS).contains(4716));
		assertTrue(store.allExclusions(7).isEmpty(), "the manage menu lists only hand-picked exclusions");
		assertTrue(store.exclusionsFor(8, "MELEE").isEmpty(), "other mobs are untouched");
		MonsterProfileStore fresh = new MonsterProfileStore(configManager, new Gson());
		fresh.setDegradableIds(Set.of(4716));
		assertTrue(fresh.skipDegradable(7, false), "persisted");
		assertTrue(fresh.exclusionsFor(7, "RANGED").contains(4716));
		store.setSkipDegradable(7, false);
		assertFalse(store.skipDegradable(7, true));
		assertTrue(store.exclusionsFor(7, "MELEE").isEmpty());
	}

	/** ALL plus the style, as the card sees it. */
	private static Set<Integer> filterItemsFor(MonsterProfileStore store, int monsterId, String style)
	{
		Set<Integer> ids = new java.util.LinkedHashSet<>();
		Map<String, Map<Integer, String>> all = store.allFilterItems(monsterId);
		for (String scope : new String[]{MonsterProfileStore.ALL_SETS, style})
		{
			if (all.get(scope) != null)
			{
				ids.addAll(all.get(scope).keySet());
			}
		}
		return ids;
	}

	@Test
	@DisplayName("clear() empties every profile in the scope and persists the wipe (the reset-customisations control)")
	void clearWipesTheScope()
	{
		MonsterProfileStore store = new MonsterProfileStore(configManager, new Gson());
		store.setNote(415, "keep away");
		store.setSkipDegradable(416, true);
		store.clear();
		assertEquals("", store.note(415) == null ? "" : store.note(415));
		assertFalse(store.skipDegradable(416, false));
		MonsterProfileStore fresh = new MonsterProfileStore(configManager, new Gson());
		assertTrue(fresh.note(415) == null || fresh.note(415).isEmpty(), "the wipe persisted");
	}
}
