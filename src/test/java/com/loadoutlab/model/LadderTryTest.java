package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Picking a ladder rung sims it and wears it (ISS-01M5GNMHHCD8M56JD4GAZBFV7Y,
 * Andrew 2026-10-21: "Sim + pin for this mob") - one undo step that puts
 * everything back.
 */
class LadderTryTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	/** Records every sim/pin call; starts with an optional ALL-scope pin. */
	private static final class RecordingOps extends TestStoreOps
	{
		final List<String> calls = new ArrayList<>();
		final Map<GearSlot, Integer> pins = new EnumMap<>(GearSlot.class);
		int spec;

		@Override
		public int pinnedSpec(int monsterId)
		{
			return spec;
		}

		@Override
		public void setPinnedSpec(int monsterId, int itemId)
		{
			calls.add("spec " + itemId);
		}

		@Override
		public Map<String, Map<GearSlot, Integer>> allPins(int monsterId)
		{
			return Map.of("ALL", new EnumMap<>(pins.isEmpty() ? new EnumMap<>(GearSlot.class) : pins));
		}

		@Override
		public void pin(int monsterId, String scope, GearSlot slot, int itemId)
		{
			calls.add("pin " + slot.name().toLowerCase() + " " + itemId);
		}

		@Override
		public void unpin(int monsterId, String scope, GearSlot slot)
		{
			calls.add("unpin " + slot.name().toLowerCase());
		}

		@Override
		public void simForMob(int monsterId, int itemId)
		{
			calls.add("sim " + itemId);
		}

		@Override
		public void removeMobSim(int monsterId, int itemId)
		{
			calls.add("unsim " + itemId);
		}
	}

	private static CommandEngine engine(RecordingOps ops, boolean owned)
	{
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state,
			(mob, p, onDone) ->
			{
			},
			new CompanionLink());
		engine.setStoreOps(ops);
		engine.setOwnedCheck(id -> owned);
		MonsterStats mob = data.searchMonsters("abyssal demon", 1).get(0);
		assertTrue(engine.execute("select", Map.of("id", mob.getId())));
		return engine;
	}

	private static Map<String, Object> tentacle()
	{
		return Map.of("slot", "weapon", "itemId", 12006, "label", "Abyssal tentacle");
	}

	@Test
	@DisplayName("an unowned rung is simmed and pinned in one step; undo takes both back")
	void simAndWear()
	{
		RecordingOps ops = new RecordingOps();
		CommandEngine engine = engine(ops, false);
		assertTrue(engine.execute("ladder-try", tentacle()));
		assertEquals(List.of("sim 12006", "pin weapon 12006"), ops.calls);
		ops.calls.clear();
		assertTrue(engine.execute("undo", Map.of()));
		assertEquals(List.of("unsim 12006", "unpin weapon"), ops.calls);
	}

	@Test
	@DisplayName("an owned rung is only pinned, and undo restores the slot's earlier pin")
	void ownedRestoresPreviousPin()
	{
		RecordingOps ops = new RecordingOps();
		ops.pins.put(GearSlot.WEAPON, 4151);
		CommandEngine engine = engine(ops, true);
		assertTrue(engine.execute("ladder-try", tentacle()));
		assertEquals(List.of("pin weapon 12006"), ops.calls, "no sim for an item you own");
		ops.calls.clear();
		assertTrue(engine.execute("undo", Map.of()));
		assertEquals(List.of("pin weapon 4151"), ops.calls, "the whip pin comes back");
	}

	@Test
	@DisplayName("trying a spec step sims it and makes it this mob's spec; undo restores the earlier spec")
	void specStep()
	{
		RecordingOps ops = new RecordingOps();
		ops.spec = 13652;
		CommandEngine engine = engine(ops, false);
		assertTrue(engine.execute("ladder-try", Map.of("slot", "spec", "itemId", 11802, "label", "Armadyl godsword")));
		assertEquals(List.of("sim 11802", "spec 11802"), ops.calls);
		ops.calls.clear();
		assertTrue(engine.execute("undo", Map.of()));
		assertEquals(List.of("unsim 11802", "spec 13652"), ops.calls, "the claws come back as the spec");
	}
}
