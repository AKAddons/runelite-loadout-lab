package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.loadoutlab.testsupport.InMemoryConfigManager;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field report 2026-08-26, on the 0.4.1 build: "on test pure i can't add
 * anything to my sim/exclude list right now."
 *
 * <p>PerCharacterScopeTest passes because it starts from a FRESH config. Every
 * real install upgrading from 0.4.0 has the pre-scope key already written, and
 * the plugin reloads every store on each GameState.LOGGED_IN - which fires on
 * every region change, and at least once before getAccountHash() is populated.
 * These tests recreate those two conditions.
 */
class ScopeRegressionTest
{
	private ConfigManager configManager;
	private Gson gson;

	private static final String MAIN = "std.1111";
	private static final String ALT = "std.2222";

	@BeforeEach
	void setUp()
	{
		configManager = InMemoryConfigManager.create();
		gson = new Gson();
		// A real 0.4.0 install: the main's list under the unscoped key.
		configManager.setConfiguration("loadoutlab", "excludedItems", "[11832,4151]");
	}

	@Test
	@DisplayName("an upgraded install does not hand the main's list to a second character")
	void legacyListDoesNotLeakToTheAlt()
	{
		ExclusionStore store = new ExclusionStore(configManager, gson);

		store.loadScope(MAIN);
		assertTrue(store.isExcluded(11832), "the main lost its pre-0.4.1 list");

		store.loadScope(ALT);
		assertFalse(store.isExcluded(11832),
			"the alt inherited the main's pre-0.4.1 exclude list");
	}

	@Test
	@DisplayName("whichever character logs in first claims the pre-0.4.1 list")
	void adoptionFollowsLoginOrder()
	{
		// The pre-0.4.1 entry records no owner, so there is no signal for which
		// account it belonged to - only one of them can have it, and the first
		// to load wins. Documented because it makes the UPGRADE order matter
		// even though steady-state scoping does not.
		ExclusionStore store = new ExclusionStore(configManager, gson);

		store.loadScope(ALT);
		assertTrue(store.isExcluded(11832), "the first character in did not adopt");

		store.loadScope(MAIN);
		assertFalse(store.isExcluded(11832),
			"the second character in must start clean, not inherit a copy");
	}

	@Test
	@DisplayName("an item added on the alt survives the next region change")
	void addOnTheAltSurvivesAReload()
	{
		ExclusionStore store = new ExclusionStore(configManager, gson);
		store.loadScope(ALT);

		store.toggle(20997); // twisted bow
		assertTrue(store.isExcluded(20997), "the toggle did not take");

		// GameState.LOGGED_IN fires on every region change and reloads the scope.
		store.loadScope(ALT);
		assertTrue(store.isExcluded(20997),
			"walking through a loading zone dropped the item the alt just added");
	}

	@Test
	@DisplayName("an item added before any character is known is adopted, not abandoned")
	void addBeforeTheHashArrivesIsNotLost()
	{
		// getAccountHash() is -1 until a character is known, and LOGGED_IN can
		// fire before then. The plugin no longer reloads at that point, so the
		// store is still unscoped and writes to the pre-0.4.1 key - which the
		// first character to load then adopts. The earlier cut scoped these
		// writes to a bare "std" bucket that nothing ever read again.
		ExclusionStore store = new ExclusionStore(configManager, gson);
		store.toggle(20997);
		assertTrue(store.isExcluded(20997));

		store.loadScope(ALT);
		assertTrue(store.isExcluded(20997),
			"the add was abandoned once the character was identified");
	}
}
