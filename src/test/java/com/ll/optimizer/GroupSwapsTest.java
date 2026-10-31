package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.engine.Style;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Swaps on a group search (ISS-01M5JAGFEJ5QHXE37B37XM8HJJ, Andrew 2026-10-22:
 * "ladder view is disabled for group searches but i think it should be
 * included" - scored by "The whole group").
 */
class GroupSwapsTest {
	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static int id(String lname) {
		return data.getGearItems().stream()
			.filter(g -> g.getLname().equals(lname) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + lname)).getId();
	}

	@Test
	@DisplayName("a group search has swaps: one strip on every member, scored by the group's HP-weighted DPS")
	void groupStrip() throws Exception {
		List<Monster> mobs = List.of(data.searchMonsters("abyssal demon", 1).get(0),
			data.searchMonsters("black demon", 1).get(0));
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"abyssal whip", "dragon scimitar", "rune scimitar", "fighter torso",
			"dragon defender"}) {
			owned.put(id(name), 1);
		}
		Service service = new Service(data);
		try {
			Service.RosterResult roster = ServiceCalls.runRoster(service, mobs, owned, 0, Map.of());
			Service.Pick first = roster.pm.get(0).get(Style.MELEE);
			Service.Pick second = roster.pm.get(1).get(Style.MELEE);
			Service.Ladder weapon = first.ladder.get(Slot.WEAPON);
			assertNotNull(weapon, "the group's weapon slot has swaps");
			assertTrue(weapon.group, "scored across the group");
			assertSame(weapon, second.ladder.get(Slot.WEAPON), "every member shows the same strip");
			for (Service.Step rung : weapon.up) {
				assertTrue(rung.dps > weapon.bdps, rung.item.getName() + " helps the group");
			}
			for (Service.Step rung : weapon.down) {
				assertTrue(rung.owned && rung.dps < weapon.bdps, rung.item.getName() + " is yours and worse");
			}
			assertFalse(weapon.up.isEmpty());
		} finally {
			service.shutdown();
		}
	}
}
