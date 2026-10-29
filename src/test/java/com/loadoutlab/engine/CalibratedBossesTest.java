package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 2026-10-24 survey's 31 "DTPS: ?" bosses (ISS-01M5PBSZBBBAS9Y46X04MVMHZB,
 * Andrew: "all 31; skip hunleff"): the ones the wiki prices now carry a number
 * (curated, sheet-priced, or a non-attacker's 0); the ones it cannot price yet
 * say why instead of a bare question mark. Rows are found by name and
 * hitpoints: the loader folds identical spawns and drops the "Post-quest"
 * label, so the wiki's version strings are not the corpus's.
 */
class CalibratedBossesTest {
	private static LoadoutData data;

	/** name, hitpoints, and the version word to prefer when several rows share the hp ("" = any). */
	private static final Object[][] PRICED = {
		{"The Whisperer", 900, ""}, {"The Whisperer", 2700, ""}, {"The Whisperer", 660, ""},
		{"The Leviathan", 900, ""}, {"The Leviathan", 2700, ""}, {"The Leviathan", 720, ""},
		{"Phantom Muspah", 850, "Teleporting"}, {"Sol Heredit", 1500, ""},
		{"The Hueycoatl", 2500, "Normal"}, {"The Hueycoatl", 2500, "Shielded"},
		{"Tormented Demon", 600, ""}, {"Mysterious Figure", 450, ""}, {"Dagon'hai Elder", 640, ""},
		// (Zalcano's weakened form is not a searchable corpus row: nothing to price.)
		{"Great Olm", 600, "claw"},
	};

	private static final Object[][] UNPRICED = {
		{"Ocellus", 2500, "Phase 1"}, {"Ocellus", 2500, "Phase 2"}, {"Ocellus", 5250, ""},
		{"Maggot King", 1500, "Far"}, {"Maggot King", 1500, "Nearby"}, {"Maggot King", 1500, "Roaring"},
		{"Moia", 785, ""}, {"Xarth", 2600, ""}, {"Dreadnoth the Jellied", 2100, ""}, {"Skotizo", 450, ""},
		{"Dagon'hai Monk", 640, ""},
	};

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static MonsterStats find(Object[] mob) {
		String name = (String) mob[0];
		int hp = (Integer) mob[1];
		String version = (String) mob[2];
		List<MonsterStats> rows = data.searchMonsters(name, 40);
		MonsterStats byVersion = null;
		MonsterStats byHp = null;
		for (MonsterStats m : rows) {
			if (!m.getName().equalsIgnoreCase(name) || m.getHitpoints() != hp) {
				continue;
			}
			if (!version.isEmpty() && m.getVersion().toLowerCase().contains(version.toLowerCase()) && byVersion == null) {
				byVersion = m;
			}
			if (byHp == null) {
				byHp = m;
			}
		}
		MonsterStats found = byVersion != null ? byVersion : byHp;
		assertTrue(found != null, "corpus is missing " + name + " " + hp + " hp " + version);
		return found;
	}

	private static IncomingDpsCalculator.Result incoming(Object[] mob) {
		return IncomingDpsCalculator.calculate(find(mob), new Loadout(Map.of()), 99, 99, 0);
	}

	@Test
	@DisplayName("every boss the wiki prices has a damage-taken number: curated, sheet-priced, or a non-attacker's 0")
	void pricedBossesHaveNumbers() {
		for (Object[] mob : PRICED) {
			IncomingDpsCalculator.Result result = incoming(mob);
			assertNotEquals(IncomingDpsCalculator.NONE, result.coverage,
				mob[0] + " " + mob[1] + " hp reads ? - " + result.overrideNote);
		}
	}

	@Test
	@DisplayName("every boss the wiki cannot price yet says why instead of a bare question mark")
	void unpricedBossesSayWhy() {
		for (Object[] mob : UNPRICED) {
			IncomingDpsCalculator.Result result = incoming(mob);
			assertEquals(IncomingDpsCalculator.NONE, result.coverage, mob[0] + " " + mob[1] + " hp");
			assertTrue(result.overrideNote != null && result.overrideNote.startsWith("No estimate"),
				mob[0] + " " + mob[1] + " hp: " + result.overrideNote);
		}
	}
}
