package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Muuffin (Discord 2026-10-19): "I got Rada's blessing 3 but the loadouts
 * prioritize the god blessings over it ... They give the exact same
 * stats, but Rada's has teleports". The two stat-tie (+1 prayer), and
 * with both owned the family collapse fell to the tradeable god
 * blessing. A Rada's blessing wins that tie.
 */
class RadasBlessingTest {
	private static final int RADAS_3 = 22945;
	private static final int HOLY_BLESSING = 20220;
	private static Tables data;
	private static Monster goblin;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		goblin = data.searchMonsters("goblin", 1).get(0);
	}

	private static void own(Map<Integer, Integer> owned, String lname) {
		Gear item = data.getGearItems().stream()
			.filter(g -> g.getLname().equals(lname) && g.isStandardGear())
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing: " + lname));
		owned.put(item.getId(), 1);
	}

	private static Gear ammoOf(int... blessings) {
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"rune scimitar", "rune full helm", "rune platebody", "rune platelegs"}) {
			own(owned, name);
		}
		for (int id : blessings) {
			owned.put(id, 1);
		}
		Req request = new Req(
			goblin, Style.MELEE, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new Owned(owned, true), RequirementProfile.MAXED, 1);
		List<Dps> results = new LoadoutOptimizer().optimize(data, request);
		assertFalse(results.isEmpty());
		return results.get(0).getLoadout().get(Slot.AMMO);
	}

	@Test
	@DisplayName("with Rada's blessing 3 and a holy blessing owned, the set carries Rada's")
	void radasWinsTheTie() {
		Gear ammo = ammoOf(RADAS_3, HOLY_BLESSING);
		assertNotNull(ammo, "a blessing rides the ammo slot");
		assertEquals(RADAS_3, ammo.getId(), "got " + ammo.getName());
	}

	@Test
	@DisplayName("with only the holy blessing owned, it still rides")
	void holyAloneStillRides() {
		Gear ammo = ammoOf(HOLY_BLESSING);
		assertNotNull(ammo);
		assertEquals(HOLY_BLESSING, ammo.getId(), "got " + ammo.getName());
	}
}
