package com.loadoutlab.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Andrew (client test 2026-10-19): Drakes suggested Water Surge while
 * thralls and Death Charge were on - an invalid state, since both are
 * cast from Arceuus and a standard autocast needs the standard book.
 * The rule he picked: the card stands down. A card whose autocast is on
 * another book carries an arceuusStandDown reason; the render shows its
 * number without the thrall fold and says why. Melee and ranged cards,
 * powered staves and Arceuus spells are untouched.
 */
class ArceuusStandDownTest {
	private static final Map<String, Object> BOOKS = Map.of(
		"Water Surge", "standard", "Ice Barrage", "ancient", "Dark Demonbane", "arceuus");

	private static Map<String, Object> card(String spell) {
		Map<String, Object> card = new LinkedHashMap<>();
		card.put("spell", spell);
		card.put("dps", 9.56);
		return card;
	}

	private static List<Object> page(String yoursSpell, String bisSpell) {
		Map<String, Object> magic = new LinkedHashMap<>();
		magic.put("yours", card(yoursSpell));
		magic.put("bis", card(bisSpell));
		Map<String, Object> melee = new LinkedHashMap<>();
		melee.put("yours", card(null));
		Map<String, Object> styles = new LinkedHashMap<>();
		styles.put("melee", melee);
		styles.put("magic", magic);
		Map<String, Object> mob = new LinkedHashMap<>();
		mob.put("styles", styles);
		List<Object> mobs = new ArrayList<>();
		mobs.add(mob);
		return mobs;
	}

	@SuppressWarnings("unchecked")
	private static String reason(List<Object> mobs, String style, String side) {
		Map<String, Object> styles = (Map<String, Object>) ((Map<?, ?>) mobs.get(0)).get("styles");
		Map<String, Object> card = (Map<String, Object>) ((Map<?, ?>) styles.get(style)).get(side);
		return (String) card.get("arceuusStandDown");
	}

	@Test
	@DisplayName("a standard or Ancients autocast stands thralls and Death Charge down, with the book named")
	void otherBooksStandDown() {
		List<Object> mobs = page("Water Surge", "Ice Barrage");
		CommandEngine.arceuusStandDown(mobs, BOOKS, true);
		assertEquals("Water Surge autocasts from the standard book", reason(mobs, "magic", "yours"));
		assertEquals("Ice Barrage autocasts from the ancient book", reason(mobs, "magic", "bis"));
		assertNull(reason(mobs, "melee", "yours"), "a melee card has no autocast");
	}

	@Test
	@DisplayName("an Arceuus spell, a powered staff and an unknown spell keep the fold")
	void arceuusAndStavesKeepIt() {
		List<Object> mobs = page("Dark Demonbane", null);
		CommandEngine.arceuusStandDown(mobs, BOOKS, true);
		assertNull(reason(mobs, "magic", "yours"));
		assertNull(reason(mobs, "magic", "bis"));
		List<Object> unknown = page("Mystery Bolt", null);
		CommandEngine.arceuusStandDown(unknown, BOOKS, true);
		assertNull(reason(unknown, "magic", "yours"), "no book on record, nothing to contradict");
	}

	@Test
	@DisplayName("with neither thralls nor Death Charge on, no card is marked")
	void nothingWantedNothingMarked() {
		List<Object> mobs = page("Water Surge", "Water Surge");
		CommandEngine.arceuusStandDown(mobs, BOOKS, false);
		assertNull(reason(mobs, "magic", "yours"));
		assertNull(reason(mobs, "magic", "bis"));
	}
}
