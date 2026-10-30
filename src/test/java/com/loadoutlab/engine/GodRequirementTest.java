package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Andrew 2026-10-30: "in the '+ requirement' area can we add a restriction
 * for each of the god types ... also an any god item as well"; "quad
 * protection" (ISS-01M66044ZG8JMM32ERT03SEPZ5). A "god:<name>" stat counts
 * the items that protect from that god.
 */
class GodRequirementTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static Loadout wearing(String... names) {
		Map<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		for (String name : names) {
			for (GearSlot slot : GearSlot.values()) {
				for (GearItem g : data.getGearItems(slot)) {
					if (g.nameLower.equals(name) && g.isStandardGear() && !gear.containsKey(g.slot)) gear.put(g.slot, g);
				}
			}
		}
		return new Loadout(gear);
	}

	@Test
	@DisplayName("a Zamorak item counts one for god:zamorak and god:any, nothing for god:saradomin; a godless set counts nothing; the parsed rule reads as the editor writes it")
	void godStats() {
		Loadout zammy = wearing("zamorak d'hide body", "rune platelegs");
		Loadout plain = wearing("rune platebody", "rune platelegs");
		assertEquals(1, SetRules.total(zammy, "god:zamorak"), 1e-9);
		assertEquals(1, SetRules.total(zammy, "god:any"), 1e-9);
		assertEquals(0, SetRules.total(zammy, "god:saradomin"), 1e-9);
		assertEquals(0, SetRules.total(plain, "god:any"), 1e-9);
		List<SetRules.Rule> rules = SetRules.parse("god:zamorak>=1;god:any>=1");
		assertEquals(2, rules.size());
		assertEquals("god:zamorak", rules.get(0).getStat());
	}
}
