package com.ll.optimizer;

import com.ll.data.DataService;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.data.Spell;
import com.ll.engine.Style;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** A mixed roster reads each member's OWN pins (ISS-01M4Y2VJ8DWBBKFR1HP9NR45NG).
 * Apathy (Discord, 2026-10-09): "Pinning a new spell sdhould change the
 * spell of the set." Until this issue computeRoster handed the optimizer
 * the FIRST member's pins, pinned spell and pinned spec for everyone. */
class RosterPinsTest {
	private static final int WHIP = 4151;
	private static final int DRAGON_SCIMITAR = 4587;
	private static Tables data;
	private static Monster graardor;
	private static Monster fireGiant;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		graardor = data.searchMonsters("general graardor", 1).get(0);
		fireGiant = data.searchMonsters("fire giant", 1).get(0);
	}

	@Test
	@DisplayName("a spell pinned on the second member's profile is cast against that member only")
	void spellPinnedOnTheSecondMember() throws Exception {
		Spell fireBolt = spell("Fire Bolt");
		Map<Integer, Spell> spellOf =
			Collections.singletonMap(fireGiant.profileId(), fireBolt);
		Service service = new Service(data);
		try {
			Service.RosterResult roster = ServiceCalls.runRosterPinned(service,
				Arrays.asList(graardor, fireGiant), owned(), 1,
				Collections.emptyMap(), spellOf, Collections.emptyMap());
			assertEquals("Fire Bolt", spellAgainst(roster, 1), "the fire giant's magic set casts the pin");
			assertNotEquals("Fire Bolt", spellAgainst(roster, 0), "Graardor's magic set keeps its own spell");
		} finally {
			service.shutdown();
		}
	}

	@Test
	@DisplayName("an item pinned on the second member's weapon slot is worn against that member only")
	void itemPinnedOnTheSecondMember() throws Exception {
		Map<Slot, Integer> weapon = new EnumMap<>(Slot.class);
		weapon.put(Slot.WEAPON, DRAGON_SCIMITAR);
		Map<Style, Map<Slot, Integer>> melee = new EnumMap<>(Style.class);
		melee.put(Style.MELEE, weapon);
		Map<Integer, Map<Style, Map<Slot, Integer>>> pinsByMob =
			Collections.singletonMap(fireGiant.profileId(), melee);
		Service service = new Service(data);
		try {
			Service.RosterResult roster = ServiceCalls.runRosterPinned(service,
				Arrays.asList(graardor, fireGiant), owned(), 1,
				pinsByMob, Collections.emptyMap(), Collections.emptyMap());
			assertEquals(DRAGON_SCIMITAR, weaponAgainst(roster, 1), "the fire giant wears the pin");
			assertEquals(WHIP, weaponAgainst(roster, 0), "Graardor keeps the whip");
		} finally {
			service.shutdown();
		}
	}

	private static Map<Integer, Integer> owned() {
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(WHIP, 1);
		owned.put(DRAGON_SCIMITAR, 1);
		owned.put(1387, 1);     // staff of fire
		owned.put(12002, 1);    // occult necklace
		owned.put(554, 1000);   // fire
		owned.put(562, 1000);   // chaos
		owned.put(560, 1000);   // death
		owned.put(565, 1000);   // blood
		return owned;
	}

	private static Spell spell(String name) {
		return data.getSpells().stream().filter(s -> s.getName().equals(name))
			.findFirst().orElseThrow();
	}

	private static String spellAgainst(Service.RosterResult roster, int member) {
		return roster.pm.get(member).get(Style.MAGIC).owned.get(0).getSpellName();
	}

	private static int weaponAgainst(Service.RosterResult roster, int member) {
		return roster.pm.get(member).get(Style.MELEE).owned.get(0)
			.getLoadout().get(Slot.WEAPON).getId();
	}
}
