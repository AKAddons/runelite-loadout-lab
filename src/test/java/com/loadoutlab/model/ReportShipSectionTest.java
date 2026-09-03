package com.loadoutlab.model;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Andrew, 2026-09-03: "does the copy report output for a sea creature
 * have all of the debug information needed for new stuff?" - it had
 * none of the ship. */
class ReportShipSectionTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static Map<String, Object> ship()
	{
		return Map.of("station", "helm", "ammo", "rune", "ammoDetected", true, "dps", 4.02,
			"cannons", List.of(
				Map.of("tier", "rune", "firedBy", "crew", "dps", 4.02, "maxHit", 20),
				Map.of("tier", "dragon", "firedBy", "player", "dps", 0.0, "maxHit", 0,
					"blocked", "Needs 75 Ranged to operate")),
			"incoming", Map.of("keel", "rune", "maxHit", 5, "dtps", 1.04),
			"byMob", List.of(4.02));
	}

	@Test
	@DisplayName("a sea report carries the keel, the damage taken, every cannon with its reason, the station, the per-mob cannon dps and the supplies")
	void seaReportCarriesTheShip()
	{
		MonsterStats shark = data.searchMonsters("hammerhead shark", 1).get(0);
		String report = ReportBuilder.build("test", new PageState(), List.of(shark), List.of(Map.of()), 0,
			null, null, ship(), List.of(Map.of("name", "Ship repair kit")));
		assertTrue(report.contains("Ship: rune keel"), report);
		assertTrue(report.contains("damage taken"), report);
		assertTrue(report.contains("cannon 1: rune, crew, 4.02 dps"), report);
		assertTrue(report.contains("cannon 2: dragon, you, blocked: Needs 75 Ranged to operate"), report);
		assertTrue(report.contains("Station: helm"), report);
		assertTrue(report.contains("ammo: rune (detected)"), report);
		assertTrue(report.contains("Cannons vs this mob: +4.02 dps"), report);
		assertTrue(report.contains("Supplies: Ship repair kit"), report);
	}

	@Test
	@DisplayName("a land report has no ship section")
	void landReportStaysClean()
	{
		MonsterStats graardor = data.searchMonsters("general graardor", 1).get(0);
		String report = ReportBuilder.build("test", new PageState(), List.of(graardor), List.of(Map.of()), 0,
			null, null, null, List.of());
		assertFalse(report.contains("Ship:"), report);
		assertFalse(report.contains("Cannons"), report);
	}
}
