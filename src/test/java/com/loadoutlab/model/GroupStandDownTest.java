package com.loadoutlab.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A group's book that is not Arceuus stands thralls and Death Charge down on
 * EVERY card - melee and ranged too, since the whole raid runs that book
 * (ISS-01M5PB4R0MP149NXWNFSEEHVP8, Andrew 2026-10-24: "that's something you
 * can't swap mid-raid").
 */
class GroupStandDownTest {
	private static Map<String, Object> card(String spell) {
		Map<String, Object> card = new LinkedHashMap<>();
		card.put("spell", spell);
		card.put("dps", 7.0);
		return card;
	}

	private static List<Object> mobs() {
		List<Object> mobs = new ArrayList<>();
		for (String spell : new String[]{"Earth Surge", null}) {
			Map<String, Object> magic = new LinkedHashMap<>();
			magic.put("yours", card(spell));
			magic.put("bis", card(null));
			Map<String, Object> melee = new LinkedHashMap<>();
			melee.put("yours", card(null));
			Map<String, Object> styles = new LinkedHashMap<>();
			styles.put("melee", melee);
			styles.put("magic", magic);
			Map<String, Object> mob = new LinkedHashMap<>();
			mob.put("styles", styles);
			mobs.add(mob);
		}
		return mobs;
	}

	@SuppressWarnings("unchecked")
	private static String reason(List<Object> mobs, int mob, String style, String side) {
		Map<String, Object> styles = (Map<String, Object>) ((Map<?, ?>) mobs.get(mob)).get("styles");
		Map<String, Object> card = (Map<String, Object>) ((Map<?, ?>) styles.get(style)).get(side);
		return (String) card.get("arceuusStandDown");
	}

	@Test
	@DisplayName("a standard group book marks every card of every mob with the group's reason")
	void standardMarksEverything() {
		List<Object> mobs = mobs();
		String reason = "Elidinis' Warden P2 (magic only) wants Earth Surge - the group runs the standard book";
		CommandEngine.groupStandDown(mobs, "standard", reason);
		for (int mob = 0; mob < 2; mob++) {
			assertEquals(reason, reason(mobs, mob, "melee", "yours"));
			assertEquals(reason, reason(mobs, mob, "magic", "yours"));
			assertEquals(reason, reason(mobs, mob, "magic", "bis"));
		}
	}

	@Test
	@DisplayName("an Arceuus group book, or no group book, marks nothing")
	void arceuusMarksNothing() {
		List<Object> mobs = mobs();
		CommandEngine.groupStandDown(mobs, "arceuus", "Arceuus for the group - thralls up");
		CommandEngine.groupStandDown(mobs, "", "");
		for (int mob = 0; mob < 2; mob++) {
			assertNull(reason(mobs, mob, "melee", "yours"));
			assertNull(reason(mobs, mob, "magic", "yours"));
		}
	}
}
