package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Poison tiers stat-tie but are not equal in game (field request: always
 * recommend dragon dagger p++ over plain). Optimizer-level: the real
 * search must pick the p++ when tiers tie.
 */
class PoisonTierTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	@Test
	@DisplayName("poison tiers parse from corpus versions, unpoisoned included")
	void tiersParse()
	{
		int[] tiers = new int[4];
		for (GearItem item : data.getGearItems())
		{
			if (item.getNameLower().equals("dragon dagger"))
			{
				tiers[item.poisonTier()]++;
			}
		}
		assertArrayEquals(new int[]{1, 1, 1, 1}, tiers,
			"expected exactly one dagger per tier 0..3");
	}

	@Test
	@DisplayName("owning every dagger tier, the search recommends the p++")
	void ownedDaggersPickThePlusPlus()
	{
		// 1215 unpoisoned, 1231 p, 5680 p+, 5698 p++.
		OwnedItems owned = new OwnedItems(
			Map.of(1215, 1, 1231, 1, 5680, 1, 5698, 1), true);
		OptimizationRequest request = new OptimizationRequest(
			data.searchMonsters("goblin", 1).get(0), CombatStyle.MELEE,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false, owned, RequirementProfile.MAXED, 1);

		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request);
		assertFalse(results.isEmpty());
		GearItem weapon = results.get(0).getLoadout().get(GearSlot.WEAPON);
		assertEquals("dragon dagger", weapon.getNameLower());
		assertEquals(3, weapon.poisonTier(),
			"expected the p++ tier, got version: " + weapon.getVersionLower());
	}
}
