package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A group step sims for the whole group (Andrew 2026-10-22: "if you upgrade
 * your melee chest in a group you should replace the melee chest for the
 * whole group but not pin a melee chest for mob that has a range swap").
 */
class SimForGroupTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	@Test
	@DisplayName("sim-for-group sims the item on every group member - no pin - and one undo takes them all back")
	void simsEveryMember() {
		List<String> calls = new ArrayList<>();
		PageState state = new PageState();
		CommandEngine engine = new CommandEngine(data, state, (mob, p, onDone) -> {
		}, new CompanionLink());
		engine.setStoreOps(new TestStoreOps() {
			@Override
			public void simForMob(int monsterId, int itemId) {
				calls.add("sim " + monsterId + " " + itemId);
			}

			@Override
			public void removeMobSim(int monsterId, int itemId) {
				calls.add("unsim " + monsterId + " " + itemId);
			}

			@Override
			public void pin(int monsterId, String scope, GearSlot slot, int itemId) {
				calls.add("pin");
			}
		});
		engine.setOwnedCheck(id -> false);
		MonsterStats a = data.searchMonsters("abyssal demon", 1).get(0);
		MonsterStats b = data.searchMonsters("black demon", 1).get(0);
		state.selectRoster(List.of(a, b), "Custom roster", false);

		assertTrue(engine.execute("sim-for-group", Map.of("itemId", 26384, "label", "Torva platebody")));
		assertEquals(List.of("sim " + a.getId() + " 26384", "sim " + b.getId() + " 26384"), calls);
		calls.clear();
		assertTrue(engine.execute("undo", Map.of()));
		assertEquals(List.of("unsim " + a.getId() + " 26384", "unsim " + b.getId() + " 26384"), calls);
	}
}
