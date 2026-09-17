package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lostmind (Discord 2026-09-17): "no usable set" on every style for one mob
 * and a report that could not show why. The report now prints what is
 * customised for each mob and names the global excludes. */
class ReportProfileSectionTest
{
	@Test
	@DisplayName("the report names the global excludes and, per mob, its pins, exclusions, sims, note and skipped degradables")
	void perMobCustomisations()
	{
		LoadoutData data = new DataService().load();
		MonsterStats shaman = data.searchMonsters("Lizardman shaman", 1).get(0);
		Map<String, Object> counts = Map.of("excludedItems", List.of(Map.of("id", 4151, "name", "Abyssal whip")));
		Map<String, Object> mob = Map.of(
			"id", shaman.getId(),
			"pins", Map.of("ALL", Map.of("head", "Slayer helmet (i)")),
			"mobExclusions", List.of(Map.of("id", 11802, "name", "Armadyl godsword", "scope", "MELEE")),
			"mobSims", List.of(Map.of("id", 26219, "name", "Osmumten's fang")),
			"note", "pray range",
			"skipDegradable", true);
		String report = ReportBuilder.build("test", new PageState(), List.of(shaman), List.of(Map.of()), 0,
			counts, null, null, List.of(), false, List.of(mob));
		assertTrue(report.contains("Excluded: Abyssal whip"), report);
		assertTrue(report.contains("Pins: head=Slayer helmet (i)"), report);
		assertTrue(report.contains("Excluded here: Armadyl godsword (MELEE)"), report);
		assertTrue(report.contains("Simmed here: Osmumten's fang"), report);
		assertTrue(report.contains("Note: pray range"), report);
		assertTrue(report.contains("Degradable gear skipped"), report);
	}
}
