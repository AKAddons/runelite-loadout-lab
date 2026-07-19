package com.loadoutlab.command;

import com.google.gson.Gson;
import com.loadoutlab.collection.DreamStore;
import com.loadoutlab.collection.ExclusionStore;
import com.loadoutlab.collection.ManualOwnedStore;
import com.loadoutlab.collection.MonsterProfileStore;
import com.loadoutlab.collection.ProtectOnlyStore;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.testsupport.InMemoryConfigManager;
import net.runelite.client.config.ConfigManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The store-backed commands: each one must capture its before-state at
 * construction and round-trip cleanly through execute -> undo -> redo
 * against the REAL stores (in-memory ConfigManager fake). These are the
 * semantics the undo/redo buttons stand on.
 */
class CommandsTest
{
	private static final int MOB = 2042; // Zulrah, as good an id as any
	private static final int WHIP = 4151;
	private static final int FANG = 26219;

	private ConfigManager cfg;
	private CommandHistory history;

	@BeforeEach
	void setUp()
	{
		cfg = InMemoryConfigManager.create();
		history = new CommandHistory();
	}

	@Test
	@DisplayName("exclusion toggle: execute excludes, undo restores, redo re-excludes")
	void exclusionRoundTrip()
	{
		ExclusionStore store = new ExclusionStore(cfg, new Gson());
		Command cmd = Commands.toggleExclusion(store, WHIP, "Abyssal whip");
		assertEquals("Exclude Abyssal whip", cmd.getDescription());

		assertTrue(history.execute(cmd));
		assertTrue(store.isExcluded(WHIP));
		assertTrue(history.undo());
		assertFalse(store.isExcluded(WHIP));
		assertTrue(history.redo());
		assertTrue(store.isExcluded(WHIP));
	}

	@Test
	@DisplayName("exclusion toggle on an excluded item reads as Include and un-excludes")
	void exclusionToggleOff()
	{
		ExclusionStore store = new ExclusionStore(cfg, new Gson());
		store.toggle(WHIP);
		Command cmd = Commands.toggleExclusion(store, WHIP, "Abyssal whip");
		assertEquals("Include Abyssal whip", cmd.getDescription());
		history.execute(cmd);
		assertFalse(store.isExcluded(WHIP));
		history.undo();
		assertTrue(store.isExcluded(WHIP));
	}

	@Test
	@DisplayName("clear exclusions is ONE undo entry and undo restores every item")
	void clearExclusionsRestoresAll()
	{
		ExclusionStore store = new ExclusionStore(cfg, new Gson());
		store.toggle(WHIP);
		store.toggle(FANG);
		store.toggle(11230);

		assertTrue(history.execute(Commands.clearExclusions(store)));
		assertTrue(store.snapshot().isEmpty());
		assertEquals(1, history.undoSize());

		assertTrue(history.undo());
		assertTrue(store.isExcluded(WHIP));
		assertTrue(store.isExcluded(FANG));
		assertTrue(store.isExcluded(11230));

		assertTrue(history.redo());
		assertTrue(store.snapshot().isEmpty());
	}

	@Test
	@DisplayName("clear exclusions on an empty store is a no-op and never lands on the stack")
	void clearEmptyNoop()
	{
		ExclusionStore store = new ExclusionStore(cfg, new Gson());
		assertFalse(history.execute(Commands.clearExclusions(store)));
		assertFalse(history.canUndo());
	}

	@Test
	@DisplayName("dream, stored-elsewhere and protect-only toggles all round-trip")
	void otherToggles()
	{
		DreamStore dreams = new DreamStore(cfg, new Gson());
		history.execute(Commands.toggleDream(dreams, FANG, "Osmumten's fang"));
		assertTrue(dreams.isDreamed(FANG));
		history.undo();
		assertFalse(dreams.isDreamed(FANG));

		ManualOwnedStore stored = new ManualOwnedStore(cfg, new Gson());
		stored.loadScope("test-account");
		history.execute(Commands.toggleStored(stored, WHIP, "Abyssal whip"));
		assertTrue(stored.isStored(WHIP));
		history.undo();
		assertFalse(stored.isStored(WHIP));

		ProtectOnlyStore protect = new ProtectOnlyStore(cfg, new Gson());
		history.execute(Commands.toggleProtectOnly(protect, WHIP, "Abyssal whip"));
		assertTrue(protect.isProtectOnly(WHIP));
		history.undo();
		assertFalse(protect.isProtectOnly(WHIP));
	}

	@Test
	@DisplayName("pin with no prior pin: undo empties the slot")
	void pinFreshSlot()
	{
		MonsterProfileStore mobs = new MonsterProfileStore(cfg, new Gson());
		history.execute(Commands.pin(mobs, MOB, "MELEE", GearSlot.WEAPON, FANG, "Osmumten's fang"));
		assertEquals(FANG, mobs.allPins(MOB).get("MELEE").get(GearSlot.WEAPON));

		history.undo();
		assertTrue(mobs.allPins(MOB).isEmpty() || mobs.allPins(MOB).getOrDefault("MELEE", java.util.Map.of()).get(GearSlot.WEAPON) == null);

		history.redo();
		assertEquals(FANG, mobs.allPins(MOB).get("MELEE").get(GearSlot.WEAPON));
	}

	@Test
	@DisplayName("pin over an existing pin: undo restores the PRIOR item, not an empty slot")
	void pinOverExisting()
	{
		MonsterProfileStore mobs = new MonsterProfileStore(cfg, new Gson());
		mobs.pin(MOB, "MELEE", GearSlot.WEAPON, WHIP);

		history.execute(Commands.pin(mobs, MOB, "MELEE", GearSlot.WEAPON, FANG, "Osmumten's fang"));
		assertEquals(FANG, mobs.allPins(MOB).get("MELEE").get(GearSlot.WEAPON));

		history.undo();
		assertEquals(WHIP, mobs.allPins(MOB).get("MELEE").get(GearSlot.WEAPON));
	}

	@Test
	@DisplayName("unpin: undo restores the pin; unpinning an empty slot is a no-op")
	void unpinRestores()
	{
		MonsterProfileStore mobs = new MonsterProfileStore(cfg, new Gson());
		mobs.pin(MOB, "ALL", GearSlot.HEAD, 11665);

		history.execute(Commands.unpin(mobs, MOB, "ALL", GearSlot.HEAD, "Void melee helm"));
		assertNull(mobs.allPins(MOB).getOrDefault("ALL", java.util.Map.of()).get(GearSlot.HEAD));
		history.undo();
		assertEquals(11665, mobs.allPins(MOB).get("ALL").get(GearSlot.HEAD));

		assertFalse(history.execute(Commands.unpin(mobs, MOB, "ALL", GearSlot.LEGS, "nothing")),
			"unpinning an empty slot must be a no-op that never lands on the stack");
	}

	@Test
	@DisplayName("note: undo restores the previous text; saving identical text is a no-op")
	void noteRestores()
	{
		MonsterProfileStore mobs = new MonsterProfileStore(cfg, new Gson());
		mobs.setNote(MOB, "bring antivenom");

		history.execute(Commands.setNote(mobs, MOB, "bring antivenom + house tabs"));
		assertEquals("bring antivenom + house tabs", mobs.noteFor(MOB));
		history.undo();
		assertEquals("bring antivenom", mobs.noteFor(MOB));

		assertFalse(history.execute(Commands.setNote(mobs, MOB, "bring antivenom")),
			"no-op note save must not land on the stack");
	}

	@Test
	@DisplayName("pinned spell: undo restores the previous spell")
	void spellRestores()
	{
		MonsterProfileStore mobs = new MonsterProfileStore(cfg, new Gson());
		history.execute(Commands.setPinnedSpell(mobs, MOB, "Ice Barrage"));
		assertEquals("Ice Barrage", mobs.pinnedSpellFor(MOB));
		history.undo();
		assertTrue(mobs.pinnedSpellFor(MOB) == null || mobs.pinnedSpellFor(MOB).isEmpty());
	}

	@Test
	@DisplayName("mob exclusion: exclude and remove both round-trip")
	void mobExclusionRoundTrip()
	{
		MonsterProfileStore mobs = new MonsterProfileStore(cfg, new Gson());
		history.execute(Commands.excludeForMob(mobs, MOB, "RANGED", WHIP, "Abyssal whip"));
		assertTrue(mobs.allExclusions(MOB).get("RANGED").contains(WHIP));
		history.undo();
		assertTrue(mobs.allExclusions(MOB).isEmpty()
			|| !mobs.allExclusions(MOB).getOrDefault("RANGED", java.util.Set.of()).contains(WHIP));

		mobs.exclude(MOB, "RANGED", WHIP);
		history.execute(Commands.removeMobExclusion(mobs, MOB, "RANGED", WHIP, "Abyssal whip"));
		assertFalse(mobs.allExclusions(MOB).getOrDefault("RANGED", java.util.Set.of()).contains(WHIP));
		history.undo();
		assertTrue(mobs.allExclusions(MOB).get("RANGED").contains(WHIP));
	}

	@Test
	@DisplayName("filter item: undo of a removal restores the item WITH its original name")
	void filterItemKeepsName()
	{
		MonsterProfileStore mobs = new MonsterProfileStore(cfg, new Gson());
		history.execute(Commands.addFilterItem(mobs, MOB, "ALL", 12695, "Super combat potion(4)"));
		assertEquals("Super combat potion(4)", mobs.allFilterItems(MOB).get("ALL").get(12695));
		history.undo();
		assertTrue(mobs.allFilterItems(MOB).isEmpty()
			|| !mobs.allFilterItems(MOB).getOrDefault("ALL", java.util.Map.of()).containsKey(12695));

		mobs.addFilterItem(MOB, "ALL", 12695, "Super combat potion(4)");
		history.execute(Commands.removeFilterItem(mobs, MOB, "ALL", 12695));
		assertFalse(mobs.allFilterItems(MOB).getOrDefault("ALL", java.util.Map.of()).containsKey(12695));
		history.undo();
		assertEquals("Super combat potion(4)", mobs.allFilterItems(MOB).get("ALL").get(12695),
			"the persisted display name must survive the undo");
	}

	@Test
	@DisplayName("a whole-group sim is one compound: every member gets it, one undo clears it")
	void wholeGroupSimIsOneCompound()
	{
		// Mirrors the plugin's simForMobs override (field request
		// 2026-07-18: sim/exclude for the whole group).
		MonsterProfileStore mobs = new MonsterProfileStore(cfg, new Gson());
		int[] group = {2042, 2043, 2044};
		history.beginCompound("Sim for the whole group: Abyssal whip");
		for (int id : group)
		{
			history.execute(Commands.simForMob(mobs, id, WHIP, "Abyssal whip"));
		}
		history.endCompound();
		for (int id : group)
		{
			assertEquals(java.util.Set.of(WHIP), mobs.simsFor(id));
		}

		assertTrue(history.undo(), "the compound undoes as one entry");
		for (int id : group)
		{
			assertTrue(mobs.simsFor(id).isEmpty(), "one undo clears every member");
		}
		assertFalse(history.undo(), "nothing else on the stack");

		assertTrue(history.redo(), "and redoes as one");
		for (int id : group)
		{
			assertEquals(java.util.Set.of(WHIP), mobs.simsFor(id));
		}
	}
}
