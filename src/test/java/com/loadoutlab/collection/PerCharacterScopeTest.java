package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.loadoutlab.testsupport.InMemoryConfigManager;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Field report 2026-08-25: "when i switched from my main to my test account, i
 * saw that the exclude list/sim list is the same. this should be unique per
 * character."
 *
 * <p>The bank ledger and the manual-owned list were already per-character; six
 * other stores wrote to a bare, global key. This pins the fix for the whole
 * family, plus the one-time migration that stops an existing install losing
 * its lists when the key gains the account hash.
 */
class PerCharacterScopeTest
{
	private ConfigManager configManager;
	private Gson gson;

	@BeforeEach
	void setUp()
	{
		configManager = InMemoryConfigManager.create();
		gson = new Gson();
	}

	@Test
	@DisplayName("two characters keep separate exclude, sim and protect lists")
	void listsDoNotBleedBetweenCharacters()
	{
		ExclusionStore exclusions = new ExclusionStore(configManager, gson);
		DreamStore dreams = new DreamStore(configManager, gson);
		ProtectOnlyStore protect = new ProtectOnlyStore(configManager, gson);

		exclusions.loadScope("std.1111");
		dreams.loadScope("std.1111");
		protect.loadScope("std.1111");
		exclusions.toggle(11832);   // bandos chestplate
		dreams.toggle(20997);       // twisted bow
		protect.toggle(4151);       // whip
		assertTrue(exclusions.isExcluded(11832));
		assertTrue(dreams.isDreamed(20997));
		assertTrue(protect.isProtectOnly(4151));

		// Switch character - the same store objects, a new scope.
		exclusions.loadScope("std.2222");
		dreams.loadScope("std.2222");
		protect.loadScope("std.2222");
		assertFalse(exclusions.isExcluded(11832), "the alt inherited the main's excludes");
		assertFalse(dreams.isDreamed(20997), "the alt inherited the main's sims");
		assertFalse(protect.isProtectOnly(4151), "the alt inherited the main's protect list");

		// ...and switching back finds the main's lists intact.
		exclusions.loadScope("std.1111");
		dreams.loadScope("std.1111");
		protect.loadScope("std.1111");
		assertTrue(exclusions.isExcluded(11832), "the main lost its excludes on return");
		assertTrue(dreams.isDreamed(20997), "the main lost its sims on return");
		assertTrue(protect.isProtectOnly(4151), "the main lost its protect list on return");
	}

	@Test
	@DisplayName("an existing install carries its pre-0.4.1 list into the first character")
	void legacyKeyMigratesOnce()
	{
		// What a pre-0.4.1 install looks like: a bare, unscoped key.
		configManager.setConfiguration("loadoutlab", "excludedItems", "[11832,4151]");

		ExclusionStore exclusions = new ExclusionStore(configManager, gson);
		exclusions.loadScope("std.1111");
		assertTrue(exclusions.isExcluded(11832), "the pre-0.4.1 list was dropped on upgrade");
		assertTrue(exclusions.isExcluded(4151), "the pre-0.4.1 list was dropped on upgrade");
	}

	@Test
	@DisplayName("seasonal and standard worlds are separate scopes for the same account")
	void seasonalIsItsOwnScope()
	{
		ExclusionStore exclusions = new ExclusionStore(configManager, gson);
		exclusions.loadScope("std.1111");
		exclusions.toggle(11832);

		exclusions.loadScope("seasonal.1111");
		assertFalse(exclusions.isExcluded(11832),
			"a leagues account inherited the main game's excludes");
	}

	@Test
	@DisplayName("monster profiles and supply defaults scope per character too")
	void profilesAndSuppliesScope()
	{
		MonsterProfileStore profiles = new MonsterProfileStore(configManager, gson);
		SupplyDefaultsStore supplies = new SupplyDefaultsStore(configManager, gson);

		profiles.loadScope("std.1111");
		supplies.loadScope("std.1111");
		profiles.setNote(2215, "bring a dds");
		supplies.setChoice("food", "shark");
		assertEquals("bring a dds", profiles.noteFor(2215));
		assertEquals("shark", supplies.choice("food"));

		profiles.loadScope("std.2222");
		supplies.loadScope("std.2222");
		// noteFor returns "" for absent, never null.
		assertEquals("", profiles.noteFor(2215), "the alt inherited the main's mob notes");
		assertNotEquals("shark", supplies.choice("food"),
			"the alt inherited the main's supply defaults");
	}
}
