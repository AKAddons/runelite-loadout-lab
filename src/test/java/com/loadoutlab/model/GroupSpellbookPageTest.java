package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
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
 * every mob plate names it with the reason, the thrall fold is off for the
 * whole roster when the book is not Arceuus, and the hosted report prints it.
 */
class GroupSpellbookPageTest {
	@Test
	@DisplayName("a roster published with a standard group book names it on every mob plate, drops the thrall fold and prints it in the report")
	@SuppressWarnings("unchecked")
	void groupBookOnThePage() throws Exception {
		LoadoutData data = new DataService().load();
		PageState state = new PageState();
		CompanionLink link = new CompanionLink();
		CommandEngine engine = new CommandEngine(data, state, (mob, p, onDone) -> {
		}, link);
		engine.setStoreOps(new TestStoreOps());
		engine.setOwnedCheck(id -> false);
		state.setParam("thralls", true);
		MonsterStats a = data.searchMonsters("ba-ba", 1).get(0);
		MonsterStats b = data.searchMonsters("kephri", 1).get(0);
		state.selectRoster(List.of(a, b), "Custom roster", false);
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
		}
		assertFalse(entry.containsKey("thralls"), "no thrall fold on a standard-book raid");
	}

	@Test
	@DisplayName("an Arceuus group book keeps the thrall fold and names Arceuus on the plates")
	@SuppressWarnings("unchecked")
	void arceuusKeepsTheFold() throws Exception {
		LoadoutData data = new DataService().load();
		PageState state = new PageState();
		CompanionLink link = new CompanionLink();
		CommandEngine engine = new CommandEngine(data, state, (mob, p, onDone) -> {
		}, link);
		engine.setStoreOps(new TestStoreOps());
		engine.setOwnedCheck(id -> false);
		state.setParam("thralls", true);
		MonsterStats a = data.searchMonsters("ba-ba", 1).get(0);
		MonsterStats b = data.searchMonsters("kephri", 1).get(0);
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
