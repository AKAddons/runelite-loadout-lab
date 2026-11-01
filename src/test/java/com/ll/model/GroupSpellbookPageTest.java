package com.ll.model;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The page tells the player the group's book (ISS-01M5PB4R0MP149NXWNFSEEHVP8):
 * on a RAID every mob plate names it with the reason, the thrall fold is off
 * for the whole roster when the book is not Arceuus, and the trip packs no
 * thrall runes. A +Mob group that is not a raid is one kit per trip
 * (ISS-01M6B3RN6784EAJ1KXC3A72N6X, Andrew 2026-11-01: "it is okay to suggest
 * arceuss for melee and standard for magic in this scenario"): the demanded
 * book pins the magic cards only, the plates stay free, the fold and the
 * thrall runes stay for the melee and ranged sets.
 */
class GroupSpellbookPageTest {
	private static final int BOOK_OF_THE_DEAD = 25818;

	@SuppressWarnings("unchecked")
	private static boolean packsThrallBook(Map<String, Object> mob) {
		for (Map<String, Object> rune : (List<Map<String, Object>>) mob.get("utilityRunes")) {
			if (((Number) rune.get("id")).intValue() == BOOK_OF_THE_DEAD) return true;
		}
		return false;
	}

	@Test
	@DisplayName("a raid published with a standard group book names it on every mob plate, drops the thrall fold and packs no thrall runes")
	@SuppressWarnings("unchecked")
	void groupBookOnThePage() throws Exception {
		Tables data = new DataService().load();
		PageState state = new PageState();
		CompanionLink link = new CompanionLink();
		CommandEngine engine = new CommandEngine(data, state, (mob, p, onDone) -> {
		}, link);
		engine.setStoreOps(new TestStoreOps());
		engine.setOwnedCheck(id -> false);
		state.setParam("thralls", true);
		Monster a = data.searchMonsters("ba-ba", 1).get(0);
		Monster b = data.searchMonsters("kephri", 1).get(0);
		state.selectRoster(List.of(a, b), "Tombs of Amascut", true);
		String reason = "Elidinis' Warden P2 (magic only) wants Earth Surge - the group runs the standard book";

		engine.onRosterResults(List.of(a, b), List.of(Map.of(), Map.of()), "standard", reason);
		javax.swing.SwingUtilities.invokeAndWait(() -> {
		}); // republish lands on the EDT

		Map<String, Object> page = link.lastPage();
		assertNotNull(page, "a page was published");
		Map<String, Object> entry = ((List<Map>) page.get("entries")).get(0);
		List<Map> mobs = (List<Map>) entry.get("mobs");
		assertEquals(2, mobs.size());
		for (Map<String, Object> mob : mobs) {
			assertEquals("standard", mob.get("fightBook"), mob.get("name") + " plate names the group's book");
			assertEquals(reason, mob.get("fightBookReason"), mob.get("name") + " plate carries the reason");
			assertFalse(packsThrallBook(mob), mob.get("name") + " packs no thrall runes on a standard-book raid");
		}
		assertFalse(entry.containsKey("thralls"), "no thrall fold on a standard-book raid");
	}

	@Test
	@DisplayName("a +Mob group that is not a raid keeps its plates and thrall fold on a demanded standard book: the melee and ranged sets still run Arceuus with the thrall runes packed")
	@SuppressWarnings("unchecked")
	void nonRaidPinsMagicOnly() throws Exception {
		Tables data = new DataService().load();
		PageState state = new PageState();
		CompanionLink link = new CompanionLink();
		CommandEngine engine = new CommandEngine(data, state, (mob, p, onDone) -> {
		}, link);
		engine.setStoreOps(new TestStoreOps());
		engine.setOwnedCheck(id -> false);
		state.setParam("thralls", true);
		Monster a = data.searchMonsters("ba-ba", 1).get(0);
		Monster b = data.searchMonsters("kephri", 1).get(0);
		state.selectRoster(List.of(a, b), "Custom roster", false);
		String reason = "Kephri wants Fire Surge - the group runs the standard book";

		engine.onRosterResults(List.of(a, b), List.of(Map.of(), Map.of()), "standard", reason);
		javax.swing.SwingUtilities.invokeAndWait(() -> {
		});

		Map<String, Object> entry = ((List<Map>) link.lastPage().get("entries")).get(0);
		for (Map<String, Object> mob : (List<Map>) entry.get("mobs")) {
			assertEquals(null, mob.get("fightBook"), mob.get("name") + " plate is not pinned: the book rides the magic cards");
			assertTrue(packsThrallBook(mob), mob.get("name") + " still packs the thrall runes for the melee and ranged sets");
		}
		assertTrue(entry.containsKey("thralls"), "the thrall fold stays for the melee and ranged sets");
	}

	@Test
	@DisplayName("an Arceuus group book keeps the thrall fold and names Arceuus on the plates")
	@SuppressWarnings("unchecked")
	void arceuusKeepsTheFold() throws Exception {
		Tables data = new DataService().load();
		PageState state = new PageState();
		CompanionLink link = new CompanionLink();
		CommandEngine engine = new CommandEngine(data, state, (mob, p, onDone) -> {
		}, link);
		engine.setStoreOps(new TestStoreOps());
		engine.setOwnedCheck(id -> false);
		state.setParam("thralls", true);
		Monster a = data.searchMonsters("ba-ba", 1).get(0);
		Monster b = data.searchMonsters("kephri", 1).get(0);
		state.selectRoster(List.of(a, b), "Custom roster", false);

		engine.onRosterResults(List.of(a, b), List.of(Map.of(), Map.of()), "arceuus",
			"Arceuus for the group - thralls up, magic sets cast from powered staves");
		javax.swing.SwingUtilities.invokeAndWait(() -> {
		});

		Map<String, Object> entry = ((List<Map>) link.lastPage().get("entries")).get(0);
		assertTrue(entry.containsKey("thralls"), "the thrall fold stays on an Arceuus raid");
		for (Map<String, Object> mob : (List<Map>) entry.get("mobs")) {
			assertEquals("arceuus", mob.get("fightBook"));
		}
	}
}
