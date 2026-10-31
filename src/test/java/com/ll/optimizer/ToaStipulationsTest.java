package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Tables;
import com.ll.data.MonsterGroups;
import com.ll.data.Monster;
import com.ll.engine.Style;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Raid stipulations ride the group data (Andrew 2026-09-10: "it's probably
 * ok to have specific raid-specific stipulations for each grouping").
 * N's ToA reports (Discord 2026-09-09) are the bank shape here. */
class ToaStipulationsTest {
	private static Tables data;
	private static List<Monster> toa;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		toa = MonsterGroups.load(data).stream().filter(g -> g.getName().equals("Tombs of Amascut"))
			.findFirst().orElseThrow().getMobs();
	}

	private static Map<Integer, Integer> bank() {
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"neitiznot faceguard", "fire cape", "amulet of rancour", "osmumten's fang",
			"crystal helm", "crystal body", "crystal legs", "barrows gloves", "primordial boots", "berserker ring (i)",
			"ava's assembler", "necklace of anguish", "bow of faerdhinen", "saradomin d'hide boots", "ring of shadows",
			"tumeken's shadow", "ahrim's robetop", "ahrim's robeskirt", "occult necklace", "imbued zamorak cape",
			"tormented bracelet", "eternal boots", "bandos chestplate", "bandos godsword", "dragon dagger", "noxious halberd"}) {
			owned.put(byName(name).getId(), 1);
		}
		return owned;
	}

	private static Gear byName(String lname) {
		Gear best = null;
		for (Gear g : data.getGearItems()) {
			if (g.getLname().equals(lname) && g.isStandardGear()
				&& (best == null || "Charged".equals(g.getVersion()) || "Active".equals(g.getVersion())
					|| "Undamaged".equals(g.getVersion()) || "Normal".equals(g.getVersion()))) {
				best = g;
			}
		}
		if (best == null) {
			throw new AssertionError("missing from the gear data: " + lname);
		}
		return best;
	}

	private static Service.Pick result(Service.RosterResult roster, String nickPart, Style style) {
		for (int j = 0; j < roster.mobs.size(); j++) {
			if (roster.mobs.get(j).label().contains(nickPart)) {
				return roster.pm.get(j).get(style);
			}
		}
		throw new AssertionError("no roster mob matching " + nickPart);
	}

	@Test
	@DisplayName("the Wardens' spec is the dragon dagger when one is banked, even with a godsword beside it")
	void wardensSpecIsTheDagger() throws Exception {
		Service service = new Service(data);
		try {
			Service.RosterResult roster = ServiceCalls.runRoster(service, toa, bank(), 16, Collections.emptyMap());
			Service.Pick core = result(roster, "Core", Style.MELEE);
			assertNotNull(core.specWeapon, "the core dump has a spec");
			assertTrue(core.specWeapon.getLname().contains("dragon dagger"), core.specWeapon.getName());
			// The kit carries ONE spec weapon for the trip, so the Wardens'
			// dagger rides every card - Ba-Ba included.
			Service.Pick baba = result(roster, "Ba-Ba", Style.MELEE);
			assertNotNull(baba.specWeapon);
			assertTrue(baba.specWeapon.getLname().contains("dragon dagger"), baba.specWeapon.getName());
		} finally {
			service.shutdown();
		}
	}

	@Test
	@DisplayName("at a ten-slot inventory the shared kit still dresses Elidinis' Warden P2 in the shadow set: the P2 Wardens weigh 3x")
	void wardenP2DecidesTheKit() throws Exception {
		Service service = new Service(data);
		try {
			Service.RosterResult roster = ServiceCalls.runRoster(service, toa, bank(), 10, Collections.emptyMap());
			Service.Pick p2 = result(roster, "Elidinis' Warden", Style.MAGIC);
			String worn = p2.owned.get(0).getLoadout().getGear().values().stream()
				.map(Gear::getLname).collect(java.util.stream.Collectors.joining(", "));
			assertTrue(worn.contains("occult necklace"), worn);
			assertTrue(worn.contains("ahrim's robeskirt"), worn);
		} finally {
			service.shutdown();
		}
	}
}
