package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A group scores its shared magic set with the best autocast
 * (ISS-01M5K9KFJH7EZ0GMAKNGZ4K2JG, Andrew 2026-10-22: "i'm in a weird loop
 * for barrows: it is showing trident as my weapon, however it is showing
 * the smoke battlestaff is a 0.49 dps upgrade in the ladder").
 */
class GroupAutocastTest {
	@Test
	@DisplayName("Barrows brothers as a group wear the smoke staff with its autocast, like Ahrim alone - not the trident")
	void smokeStaffBeatsTheTrident() throws Exception {
		LoadoutData data = new DataService().load();
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"virtus mask", "imbued zamorak cape", "occult necklace", "rada's blessing 4",
			"trident of the seas", "ahrim's robetop", "ghommal's hilt 4", "ahrim's robeskirt", "tormented bracelet",
			"aranea boots", "seers ring (i)", "smoke battlestaff", "mystic smoke staff"}) {
			int id = data.getGearItems().stream()
				.filter(g -> g.getNameLower().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
				.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + name)).getId();
			owned.put(id, 1);
		}
		for (int rune : new int[]{21880, 560, 565, 566, 554, 556, 555, 557}) {
			owned.put(rune, 10000);
		}
		List<MonsterStats> brothers = List.of(data.searchMonsters("ahrim the blighted", 1).get(0),
			data.searchMonsters("dharok the wretched", 1).get(0));
		OptimizerService service = new OptimizerService(data);
		try {
			OptimizerService.RosterResult roster = ServiceCalls.runRoster(service, brothers, owned, 0, Map.of());
			OptimizerService.StyleResult magic = roster.perMob.get(0).get(CombatStyle.MAGIC);
			String weapon = magic.owned.get(0).getLoadout().getWeapon().getName();
			assertTrue(weapon.toLowerCase().contains("smoke"), "the group wears " + weapon);
			assertNotNull(magic.owned.get(0).getSpellName(), "with its autocast");
		} finally {
			service.shutdown();
		}
	}
}
