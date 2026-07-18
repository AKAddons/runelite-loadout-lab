package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gauntlet fights are locked to raid-crafted gear (field report
 * 2026-07-18: the Corrupted Hunllef card recommended the player's
 * mainland bank - impossible inside). Every recommendation vs a
 * Crystalline/Corrupted mob must wear ONLY the matching family's
 * basic/attuned/perfected tiers, regardless of what is owned.
 */
class GauntletRulesTest
{
	private static LoadoutData data;
	private static final LoadoutOptimizer optimizer = new LoadoutOptimizer();

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static void assertGauntletOnly(String mobName, String family)
	{
		MonsterStats mob = data.searchMonsters(mobName, 1).get(0);
		// A stacked mainland bank that must be IGNORED inside.
		Map<Integer, Integer> owned = new HashMap<>();
		for (int id : new int[]{4151, 29589, 29591, 21326, 29594, 19553, 13239, 22981, 11832, 11834})
		{
			owned.put(id, 1);
		}
		for (CombatStyle style : CombatStyle.concreteValues())
		{
			OptimizationRequest request = new OptimizationRequest(mob, style,
				PlayerLevels.MAXED, PrayerBonuses.NONE, null, 0,
				CandidateMode.OWNED_ONLY, true, false,
				new OwnedItems(owned, true), RequirementProfile.MAXED, 1);
			List<DpsResult> best = optimizer.optimize(data, request);
			if (best.isEmpty() || best.get(0) == null)
			{
				continue; // a style with no in-raid answer is honest
			}
			for (GearItem item : best.get(0).getLoadout().getGear().values())
			{
				if (item == null)
				{
					continue;
				}
				assertTrue(GauntletRules.allowed(family, item),
					mobName + " " + style + " recommended mainland gear: " + item.getNameLower());
			}
			GearItem weapon = best.get(0).getLoadout().getWeapon();
			assertNotNull(weapon, mobName + " " + style + " has no in-raid weapon");
		}
	}

	@Test
	@DisplayName("the Corrupted Hunllef wears only corrupted raid tiers")
	void corruptedHunllef()
	{
		assertGauntletOnly("Corrupted Hunllef", "corrupted");
	}

	@Test
	@DisplayName("the Crystalline Hunllef wears only crystal raid tiers")
	void crystallineHunllef()
	{
		assertGauntletOnly("Crystalline Hunllef", "crystal");
	}
}
