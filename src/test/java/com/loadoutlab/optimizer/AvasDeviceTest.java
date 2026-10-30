package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterGroups;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Andrew 2026-10-30: "the engine should be favoring to use something like an
 * ava's assembler (that collects ammo) for ammo that can be caught that way
 * (crossbows/arrows) ... in some sets like barrows where it uses predominantly
 * magic with a range switch, the assembler isn't suggested until like 6 swap
 * items are added" (ISS-01M65NK11F5ZJZZG2590VJPKJG). The device rides the
 * ranged bundle whose ammo it retrieves.
 */
class AvasDeviceTest {
	private static LoadoutData data;
	private static List<MonsterStats> barrows;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		barrows = MonsterGroups.load(data).stream().filter(g -> "Barrows".equals(g.getName())).findFirst().orElseThrow().getMobs();
	}

	private static int id(String name) {
		return data.getGearItems().stream().filter(g -> g.getNameLower().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError(name)).getId();
	}

	@Test
	@DisplayName("a Barrows kit of magic gear with a twisted bow switch carries the assembler within three swaps, and the bench says why")
	void assemblerRidesTheSwitch() throws Exception {
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"mystic hat", "mystic robe top", "mystic robe bottom", "trident of the seas",
			"imbued saradomin cape", "occult necklace", "wizard boots", "barrows gloves", "twisted bow", "dragon arrow",
			"ava's assembler"}) {
			owned.put(id(name), 1);
		}
		int assembler = id("ava's assembler");
		OptimizerService service = new OptimizerService(data);
		try {
			OptimizerService.RosterResult result = ServiceCalls.runRoster(service, barrows, owned, 3, Collections.emptyMap());
			boolean rode = false;
			for (Map<CombatStyle, OptimizerService.StyleResult> styles : result.perMob) {
				for (OptimizerService.StyleResult r : styles.values()) {
					if (r == null || !r.ownedKitBacked) continue;
					for (GearItem g : r.bench) {
						if (g.getId() == assembler) {
							rode = true;
							assertTrue(r.bench.size() <= 3, "within three swaps: " + r.bench);
							assertTrue(String.valueOf(r.swapNotes.get(assembler)).contains("retrieves"), r.swapNotes.toString());
						}
					}
				}
			}
			StringBuilder seen = new StringBuilder();
			for (int i = 0; i < result.perMob.size(); i++) {
				for (var e : result.perMob.get(i).entrySet()) {
					if (e.getValue() != null && e.getValue().ownedKitBacked && !e.getValue().owned.isEmpty()) {
						seen.append(result.mobs.get(i).getName()).append(' ').append(e.getKey()).append(" cape=")
							.append(e.getValue().owned.get(0).loadout.get(com.loadoutlab.data.GearSlot.CAPE)).append(" bench=").append(e.getValue().bench).append("; ");
					}
				}
			}
			assertTrue(rode, "the assembler rides the bow switch somewhere in the trip: " + seen);
		} finally {
			service.shutdown();
		}
	}
}
