package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kendyy, Discord 2026-10-20: "Barrows suggesting bolts rather than
 * blessing, but no crossbow and none of the recommended styles are range
 * ... it might want to suggest ghommals hilt 2 to negate the prayer
 * drain". Andrew: "pretty much everyone with that tier of the hilt will
 * opt to use it in barrows" - a default pin, not a dps pick. Wiki: no
 * prayer drain at Barrows while a Ghommal's hilt (tier 2+) is equipped;
 * the hilt-fitted avernic defender keeps the benefit.
 */
class BarrowsTest {
	private static Tables data;
	private static List<Monster> brothers;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		brothers = MonsterGroups.load(data).stream().filter(g -> g.getName().equals("Barrows"))
			.findFirst().orElseThrow().getMobs();
	}

	private static Map<Integer, Integer> bank(String... extra) {
		Map<Integer, Integer> owned = new HashMap<>();
		String[] base = {"abyssal whip", "dragon defender", "neitiznot faceguard", "fighter torso", "dragon platelegs",
			"barrows gloves", "dragon boots", "berserker ring (i)", "amulet of fury", "fire cape", "trident of the swamp",
			"ahrim's hood", "ahrim's robetop", "ahrim's robeskirt", "occult necklace", "imbued saradomin cape",
			"tormented bracelet", "eternal boots", "rune crossbow", "runite bolts", "rada's blessing 4", "dragon dagger"};
		for (String[] names : new String[][]{base, extra}) {
			for (String n : names) {
				Gear item = data.getGearItems().stream().filter(g -> g.getLname().equals(n) && g.isStandardGear())
					.findFirst().orElseThrow(() -> new AssertionError("missing " + n));
				owned.put(item.getId(), 1);
			}
		}
		return owned;
	}

	private static Service.RosterResult roster(Map<Integer, Integer> owned) throws Exception {
		Service service = new Service(data);
		try {
			return ServiceCalls.runRoster(service, brothers, owned, 4, Collections.emptyMap());
		} finally {
			service.shutdown();
		}
	}

	private static Gear shown(Service.RosterResult r, int mob, Style style, Slot slot) {
		Service.Pick sr = r.pm.get(mob).get(style);
		return sr == null || sr.owned.isEmpty() ? null : sr.owned.get(0).getLoadout().get(slot);
	}

	@Test
	@DisplayName("with a hilt of tier 2 or higher owned, every brother's one-handed set wears it")
	void theHiltIsTheDefaultShield() throws Exception {
		Service.RosterResult r = roster(bank("ghommal's hilt 2"));
		for (int j = 0; j < r.mobs.size(); j++) {
			for (Style style : new Style[]{Style.MELEE, Style.MAGIC}) {
				Gear shield = shown(r, j, style, Slot.SHIELD);
				assertNotNull(shield, r.mobs.get(j).getName() + " " + style);
				assertEquals("ghommal's hilt 2", shield.getLname(), r.mobs.get(j).getName() + " " + style);
			}
		}
	}

	@Test
	@DisplayName("without a hilt the sets are unchanged: the defender stays")
	void noHiltNoRule() throws Exception {
		Service.RosterResult r = roster(bank());
		for (int j = 0; j < r.mobs.size(); j++) {
			Gear shield = shown(r, j, Style.MELEE, Slot.SHIELD);
			assertNotNull(shield);
			assertEquals("dragon defender", shield.getLname(), r.mobs.get(j).getName());
		}
	}

	@Test
	@DisplayName("no brother is answered with ranged, so the magic card keeps its blessing, not the crossbow's bolts")
	void blessingNotBolts() throws Exception {
		Service.RosterResult r = roster(bank());
		for (int j = 0; j < r.mobs.size(); j++) {
			Gear ammo = shown(r, j, Style.MAGIC, Slot.AMMO);
			assertNotNull(ammo, r.mobs.get(j).getName());
			assertEquals("rada's blessing 4", ammo.getLname(), r.mobs.get(j).getName());
		}
	}
}
