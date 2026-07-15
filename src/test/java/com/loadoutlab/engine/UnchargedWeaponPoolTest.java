package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
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
class UnchargedWeaponPoolTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static boolean bad(String versionLower)
	{
		return versionLower.contains("uncharged") || versionLower.contains("broken")
			|| versionLower.contains("locked") || versionLower.contains("inactive");
	}

	@Test
	@DisplayName("a 500m budget never recommends buying an uncharged/broken weapon")
	void budgetNeverBuysDeadWeapons()
	{
		MonsterStats graardor = data.searchMonsters("general graardor", 1).get(0);
		for (CombatStyle style : new CombatStyle[]{CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC})
		{
			OptimizationRequest request = new OptimizationRequest(graardor, style,
				PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
				null, 500_000_000, CandidateMode.BUDGET, true, false,
				OwnedItems.EMPTY, RequirementProfile.MAXED, 3);
			List<DpsResult> results = new LoadoutOptimizer().optimize(data, request);
			for (DpsResult result : results)
			{
				if (result.getLoadout().getWeapon() == null)
				{
					continue;
				}
				String version = result.getLoadout().getWeapon().getVersionLower();
				assertFalse(bad(version),
					style + " recommended a weapon that cannot attack: "
						+ result.getLoadout().getWeapon().getNameLower() + " (" + version + ")");
			}
		}
	}

	@Test
	@DisplayName("game-best pools never surface an uncharged weapon either")
	void allStandardNeverPicksDeadWeapons()
	{
		MonsterStats revDragon = data.searchMonsters("revenant dragon", 1).get(0);
		for (CombatStyle style : new CombatStyle[]{CombatStyle.MELEE, CombatStyle.RANGED})
		{
			OptimizationRequest request = new OptimizationRequest(revDragon, style,
				PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
				null, 0, CandidateMode.ALL_STANDARD, true, false,
				OwnedItems.EMPTY, RequirementProfile.MAXED, 3);
			for (DpsResult result : new LoadoutOptimizer().optimize(data, request))
			{
				if (result.getLoadout().getWeapon() == null)
				{
					continue;
				}
				assertFalse(bad(result.getLoadout().getWeapon().getVersionLower()),
					style + " picked " + result.getLoadout().getWeapon().getNameLower());
			}
		}
	}
}
