package com.loadoutlab.model;

import com.google.gson.Gson;
import com.loadoutlab.collection.MonsterProfileStore;
import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.testsupport.InMemoryConfigManager;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Andrew 2026-10-22, Araxxor: "i got rid of my pin/ownership of obligator
 * and it is still comign in as a result" - he used right-click > Unpin
 * weapon. The engine's commands against the REAL profile store.
 */
class UnpinRoundTripTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	/** The plugin's StoreOps wiring for pins, over a real store. */
	private static TestStoreOps wired(MonsterProfileStore store)
	{
		return new TestStoreOps()
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
		};
	}

	private static int obligator()
	{
		return data.getGearItems().stream()
			.filter(g -> g.getNameLower().startsWith("obligator") && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError("no obligator")).getId();
	}

	@Test
	@DisplayName("Unpin weapon on Araxxor removes a pin made by Pin or by trying a swap")
	void unpinRemovesThePin()
	{
		MonsterProfileStore store = new MonsterProfileStore(InMemoryConfigManager.create(), new Gson());
		store.loadScope("std.1");
		CommandEngine engine = new CommandEngine(data, new PageState(), (mob, p, onDone) ->
		{
		}, new CompanionLink());
		engine.setStoreOps(wired(store));
		engine.setOwnedCheck(id -> true);
		MonsterStats araxxor = data.searchMonsters("araxxor", 1).get(0);
		assertTrue(engine.execute("select", Map.of("id", araxxor.getId())));

		for (String how : new String[]{"pin", "ladder-try"})
		{
			assertTrue(engine.execute(how, Map.of("slot", "weapon", "itemId", obligator(), "label", "Obligator")));
			assertEquals(Integer.valueOf(obligator()), store.pinsFor(araxxor.getId(), "MELEE").get(GearSlot.WEAPON), how);
			assertTrue(engine.execute("unpin", Map.of("slot", "weapon")));
			assertEquals(Map.of(), store.allPins(araxxor.getId()), "unpinned after " + how);
		}
	}

	@Test
	@DisplayName("on a roster view, pin / try / unpin act on the row the card shows")
	void rosterViewPins()
	{
		MonsterProfileStore store = new MonsterProfileStore(InMemoryConfigManager.create(), new Gson());
		store.loadScope("std.1");
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state, (mob, p, onDone) ->
		{
		}, new CompanionLink());
		engine.setStoreOps(wired(store));
		engine.setOwnedCheck(id -> true);
		MonsterStats araxxor = data.searchMonsters("araxxor", 1).get(0);
		MonsterStats demon = data.searchMonsters("abyssal demon", 1).get(0);
		state.selectRoster(java.util.List.of(araxxor, demon), "Custom roster");

		for (String how : new String[]{"pin", "ladder-try"})
		{
			assertTrue(engine.execute(how, Map.of("slot", "weapon", "itemId", obligator(), "label", "Obligator")), how);
			assertEquals(Integer.valueOf(obligator()), store.pinsFor(araxxor.getId(), "MELEE").get(GearSlot.WEAPON), how);
			assertTrue(engine.execute("unpin", Map.of("slot", "weapon")), "unpin after " + how);
			assertEquals(Map.of(), store.allPins(araxxor.getId()), "unpinned after " + how);
		}
	}
}
