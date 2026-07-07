package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.WildernessMonsters;
import java.util.List;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class RiskConstraintTest
{
	private static LoadoutData data;
	private static MonsterStats callisto;

	@BeforeClass
	public static void load()
	{
		data = new DataService().load();
		callisto = data.searchMonsters("callisto", 1).get(0);
	}

	private static OptimizationRequest request(CombatStyle style, int maxTradeables)
	{
		return new OptimizationRequest(callisto, style, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL),
			null, 0, CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, 1)
			.withMaxTradeables(maxTradeables);
	}

	@Test
	public void wildernessBossesAreDetectedAndRegularBossesAreNot()
	{
		Assert.assertTrue(WildernessMonsters.isWilderness(callisto));
		Assert.assertTrue(WildernessMonsters.isWilderness(
			data.searchMonsters("revenant dragon", 1).get(0)));
		Assert.assertFalse(WildernessMonsters.isWilderness(
			data.searchMonsters("zulrah", 1).get(0)));
	}

	@Test
	public void taskOnlySlayerBossesAreDetectedAndOthersAreNot()
	{
		Assert.assertTrue(com.loadoutlab.data.SlayerLockedMonsters.isTaskOnly(
			data.searchMonsters("alchemical hydra", 1).get(0)));
		Assert.assertTrue(com.loadoutlab.data.SlayerLockedMonsters.isTaskOnly(
			data.searchMonsters("araxxor", 1).get(0)));
		Assert.assertTrue(com.loadoutlab.data.SlayerLockedMonsters.isTaskOnly(
			data.searchMonsters("thermy", 1).get(0)));
		Assert.assertFalse(com.loadoutlab.data.SlayerLockedMonsters.isTaskOnly(callisto));
		Assert.assertFalse(com.loadoutlab.data.SlayerLockedMonsters.isTaskOnly(
			data.searchMonsters("zulrah", 1).get(0)));
	}

	@Test
	public void riskCappedSetKeepsEveryValuableAndOnlyRisksThrowawayGear()
	{
		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		for (CombatStyle style : new CombatStyle[]{CombatStyle.MELEE, CombatStyle.RANGED})
		{
			List<DpsResult> capped = optimizer.optimize(data, request(style, 3));
			Assert.assertFalse(capped.isEmpty());
			DpsResult best = optimizer.fillDpsNeutralSlots(data, request(style, 3), capped.get(0));
			int valuables = 0;
			for (com.loadoutlab.data.GearItem item : best.getLoadout().getGear().values())
			{
				if (item != null && item.isTradeable()
					&& item.getPriceOrZero() > OptimizationRequest.THROWAWAY_GP)
				{
					valuables++;
				}
			}
			Assert.assertTrue(style + " wore " + valuables + " valuables", valuables <= 3);
			// Everything past the 3 kept slots must be throwaway-cheap: the
			// valuables all rank into the kept slots by price.
			PvpRisk.Assessment risk = PvpRisk.assess(best.getLoadout(), null, 3);
			for (com.loadoutlab.data.GearItem lost : risk.lost)
			{
				Assert.assertTrue(lost.label() + " (" + lost.getPriceOrZero() + ") is not throwaway",
					lost.getPriceOrZero() <= OptimizationRequest.THROWAWAY_GP);
			}
		}
	}

	@Test
	public void protectItemBuysAFourthRiskSlotAndAtLeastAsMuchDps()
	{
		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		double three = optimizer.optimize(data, request(CombatStyle.MELEE, 3)).get(0).getDps();
		List<DpsResult> four = optimizer.optimize(data, request(CombatStyle.MELEE, 4));
		Assert.assertTrue(four.get(0).getLoadout().tradeableCount() <= 4);
		Assert.assertTrue(four.get(0).getDps() >= three - 1e-9);
	}

	@Test
	public void unconstrainedIsAtLeastAsStrongAsTheCappedSet()
	{
		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		double capped = optimizer.optimize(data, request(CombatStyle.RANGED, 3)).get(0).getDps();
		double free = optimizer.optimize(data, request(CombatStyle.RANGED, -1)).get(0).getDps();
		Assert.assertTrue(free >= capped - 1e-9);
	}
}
