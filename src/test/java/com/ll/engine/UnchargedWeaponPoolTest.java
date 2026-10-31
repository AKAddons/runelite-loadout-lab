package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A weapon that cannot attack must never be RECOMMENDED (2026-07 player
 * audit A3.4): charged wilderness weapons are untradeable so only their
 * dead Uncharged twins pass the budget-affordability gate, and the budget
 * pool used to tell players to buy a Webweaver bow (Uncharged) - an item
 * that cannot fire at all until fed 1,000 ether. badVersion() is a pool
 * FILTER for the weapon slot now, not just a dedupe tie-break.
 */
class UnchargedWeaponPoolTest {
	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static boolean bad(String ver) {
		return ver.contains("uncharged") || ver.contains("broken")
			|| ver.contains("locked") || ver.contains("inactive");
	}

	@Test
	@DisplayName("a 500m budget never recommends buying an uncharged/broken weapon")
	void budgetNeverBuysDeadWeapons() {
		Monster graardor = data.searchMonsters("general graardor", 1).get(0);
		for (Style style : new Style[]{Style.MELEE, Style.RANGED, Style.MAGIC}) {
			Req request = new Req(graardor, style,
				Levels.MAXED, PrayerBonuses.bestAvailable(Levels.MAXED),
				null, 500_000_000, CandidateMode.BUDGET, true, false,
				Owned.EMPTY, RequirementProfile.MAXED, 3);
			List<Dps> results = new LoadoutOptimizer().optimize(data, request);
			for (Dps result : results) {
				if (result.getLoadout().getWeapon() == null) {
					continue;
				}
				String version = result.getLoadout().getWeapon().getVer();
				assertFalse(bad(version),
					style + " recommended a weapon that cannot attack: "
						+ result.getLoadout().getWeapon().getLname() + " (" + version + ")");
			}
		}
	}

	@Test
	@DisplayName("game-best pools never surface an uncharged weapon either")
	void allStandardNeverPicksDeadWeapons() {
		Monster revDragon = data.searchMonsters("revenant dragon", 1).get(0);
		for (Style style : new Style[]{Style.MELEE, Style.RANGED}) {
			Req request = new Req(revDragon, style,
				Levels.MAXED, PrayerBonuses.bestAvailable(Levels.MAXED),
				null, 0, CandidateMode.ALL_STANDARD, true, false,
				Owned.EMPTY, RequirementProfile.MAXED, 3);
			for (Dps result : new LoadoutOptimizer().optimize(data, request)) {
				if (result.getLoadout().getWeapon() == null) {
					continue;
				}
				assertFalse(bad(result.getLoadout().getWeapon().getVer()),
					style + " picked " + result.getLoadout().getWeapon().getLname());
			}
		}
	}
}
