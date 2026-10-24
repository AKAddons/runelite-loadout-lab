package com.loadoutlab.model;

import com.google.gson.Gson;
import com.loadoutlab.collection.MonsterProfileStore;
import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.testsupport.InMemoryConfigManager;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins tied to sims (ISS-01M5HJ84ADBHWB2302G66WW2G4, Andrew 2026-10-22:
 * "it is showing me a fang, i dont have fang and i don't have it simmed" -
 * "yeah i think the ladder pin is causing this"). A tried swap simmed AND
 * pinned the fang; removing the sim left the pin. The engine's commands
 * against the REAL profile store.
 */
class PinsTiedToSimsTest
{
	private static LoadoutData data;
	private MonsterProfileStore store;
	private final Set<Integer> owned = new HashSet<>();
	private final Set<Integer> globalSims = new HashSet<>();
	private CommandEngine engine;
	private int babaId;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static int id(String prefix)
	{
		return data.getGearItems().stream()
			.filter(g -> g.getNameLower().startsWith(prefix) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError("no " + prefix)).getId();
	}

	@BeforeEach
	void setUp()
	{
		store = new MonsterProfileStore(InMemoryConfigManager.create(), new Gson());
		store.loadScope("std.1");
		engine = new CommandEngine(data, new PageState(), (mob, p, onDone) ->
		{
		}, new CompanionLink());
		engine.setStoreOps(new TestStoreOps()
		{
			@Override
			public Map<String, Map<GearSlot, Integer>> allPins(int monsterId)
			{
				return store.allPins(monsterId);
			}

			@Override
			public void pin(int monsterId, String slot, int itemId)
			{
				store.pin(monsterId, MonsterProfileStore.ALL_SETS, GearSlot.valueOf(slot.toUpperCase()), itemId);
			}

			@Override
			public void unpin(int monsterId, String slot)
			{
				store.unpinEverywhere(monsterId, GearSlot.valueOf(slot.toUpperCase()));
			}

			@Override
			public void pin(int monsterId, String scope, GearSlot slot, int itemId)
			{
				store.pin(monsterId, scope, slot, itemId);
			}

			@Override
			public void unpin(int monsterId, String scope, GearSlot slot)
			{
				store.unpin(monsterId, scope, slot);
			}

			@Override
			public void simForMob(int monsterId, int itemId)
			{
				store.addSim(monsterId, itemId, String.valueOf(itemId));
			}

			@Override
			public void removeMobSim(int monsterId, int itemId)
			{
				store.removeMobSim(monsterId, itemId);
			}

			@Override
			public List<Map<String, Object>> mobSims(int monsterId)
			{
				List<Map<String, Object>> out = new ArrayList<>();
				store.allSims(monsterId).forEach((simId, name) ->
				{
					Map<String, Object> row = new LinkedHashMap<>();
					row.put("id", simId);
					row.put("name", name);
					out.add(row);
				});
				return out;
			}

			@Override
			public boolean toggleSim(int itemId)
			{
				if (!globalSims.remove(itemId))
				{
					globalSims.add(itemId);
				}
				return true;
			}
		});
		engine.setOwnedCheck(item -> owned.contains(item) || globalSims.contains(item));
		MonsterStats baba = data.searchMonsters("ba-ba", 1).get(0);
		babaId = baba.getId();
		assertTrue(engine.execute("select", Map.of("id", babaId)));
	}

	private Integer pinned(GearSlot slot)
	{
		return store.pinsFor(babaId, "MELEE").get(slot);
	}

	private Map<String, Object> fang()
	{
		return Map.of("slot", "weapon", "itemId", id("osmumten's fang"), "label", "Osmumten's fang");
	}

	@Test
	@DisplayName("removing the sim of a tried fang drops its pin too; one undo brings both back")
	void removeSimDropsPin()
	{
		int fang = id("osmumten's fang");
		assertTrue(engine.execute("ladder-try", fang()));
		assertEquals(Integer.valueOf(fang), pinned(GearSlot.WEAPON));
		assertTrue(engine.execute("remove-mob-sim", Map.of("itemId", fang, "label", "Osmumten's fang")));
		assertTrue(store.allSims(babaId).isEmpty());
		assertNull(pinned(GearSlot.WEAPON), "Andrew's Ba-Ba: the fang is gone with its sim");
		assertTrue(engine.execute("undo", Map.of()));
		assertTrue(store.allSims(babaId).containsKey(fang));
		assertEquals(Integer.valueOf(fang), pinned(GearSlot.WEAPON), "undo restores the pin");
	}

	@Test
	@DisplayName("an item you own keeps its pin when a sim of it goes")
	void ownedKeepsPin()
	{
		int fang = id("osmumten's fang");
		owned.add(fang);
		store.addSim(babaId, fang, "fang");
		store.pin(babaId, MonsterProfileStore.ALL_SETS, GearSlot.WEAPON, fang);
		assertTrue(engine.execute("remove-mob-sim", Map.of("itemId", fang, "label", "Osmumten's fang")));
		assertEquals(Integer.valueOf(fang), pinned(GearSlot.WEAPON));
	}

	@Test
	@DisplayName("Clear all sims drops their pins; undo restores them")
	void clearSimsDropsPins()
	{
		int fang = id("osmumten's fang");
		int torva = id("torva platebody");
		assertTrue(engine.execute("ladder-try", fang()));
		assertTrue(engine.execute("ladder-try", Map.of("slot", "body", "itemId", torva, "label", "Torva platebody")));
		assertTrue(engine.execute("clear-mob-list", Map.of("kind", "mobSims")));
		assertEquals(Map.of(), store.allPins(babaId));
		assertTrue(engine.execute("undo", Map.of()));
		assertEquals(Integer.valueOf(fang), pinned(GearSlot.WEAPON));
		assertEquals(Integer.valueOf(torva), pinned(GearSlot.BODY));
	}

	@Test
	@DisplayName("turning a global sim off drops its pin on the shown mob; undo restores")
	void globalSimOffDropsPin()
	{
		int fang = id("osmumten's fang");
		assertTrue(engine.execute("toggle-sim", Map.of("itemId", fang, "label", "Osmumten's fang")));
		assertTrue(engine.execute("pin", Map.of("slot", "weapon", "itemId", fang)));
		assertTrue(engine.execute("toggle-sim", Map.of("itemId", fang, "label", "Osmumten's fang")));
		assertFalse(globalSims.contains(fang));
		assertNull(pinned(GearSlot.WEAPON));
		assertTrue(engine.execute("undo", Map.of()));
		assertTrue(globalSims.contains(fang));
		assertEquals(Integer.valueOf(fang), pinned(GearSlot.WEAPON));
	}

	@Test
	@DisplayName("the pin list: pin an item into its own slot, remove one, clear all - each undoable")
	void pinList()
	{
		int fang = id("osmumten's fang");
		int torva = id("torva platebody");
		assertTrue(engine.execute("pin-for-mob", Map.of("itemId", fang, "label", "Osmumten's fang")));
		assertTrue(engine.execute("pin-for-mob", Map.of("itemId", torva, "label", "Torva platebody")));
		assertEquals(Integer.valueOf(fang), pinned(GearSlot.WEAPON), "into the item's own slot");
		assertEquals(Integer.valueOf(torva), pinned(GearSlot.BODY));

		assertTrue(engine.execute("remove-mob-pin",
			Map.of("itemId", fang, "label", "Osmumten's fang", "scope", "ALL", "slot", "weapon")));
		assertNull(pinned(GearSlot.WEAPON));
		assertTrue(engine.execute("undo", Map.of()));
		assertEquals(Integer.valueOf(fang), pinned(GearSlot.WEAPON));

		assertTrue(engine.execute("clear-mob-list", Map.of("kind", "mobPins")));
		assertEquals(Map.of(), store.allPins(babaId));
		assertTrue(engine.execute("undo", Map.of()));
		assertEquals(Integer.valueOf(torva), pinned(GearSlot.BODY));
		assertEquals(Integer.valueOf(fang), pinned(GearSlot.WEAPON));
	}
}
