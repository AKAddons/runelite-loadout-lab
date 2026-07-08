package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Quest rewards join the candidate pool ONLY when the upgrade budget is on
 * (OWNED_OR_BUDGET) - the budget is the "show me obtainable gear" switch -
 * and they never charge the budget: they cost effort, not gp.
 */
public class QuestRewardPoolTest
{
	private static final int WHIP = 4151;
	private static final int BARROWS_GLOVES = 7462;

	private static LoadoutData data;
	private static MonsterStats graardor;

	@BeforeClass
	public static void load()
	{
		data = new DataService().load();
		// A meleeable monster where barrows gloves' offensive bonuses matter.
		graardor = data.searchMonsters("graardor", 1).get(0);
	}

	private static OptimizationRequest owned(Map<Integer, Integer> owned, int budgetGp)
	{
		return new OptimizationRequest(graardor, CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL),
			null, budgetGp,
			budgetGp > 0 ? CandidateMode.OWNED_OR_BUDGET : CandidateMode.OWNED_ONLY,
			true, false, new OwnedItems(data.canonicalizeOwned(owned), true), 1);
	}

	private static String handsName(DpsResult result)
	{
		GearItem hands = result.getLoadout().get(GearSlot.HANDS);
		return hands == null ? "" : hands.getName();
	}

	@Test
	public void barrowsGlovesJoinTheBudgetSearchWithoutBeingOwned()
	{
		// Owned: just a whip. With a budget on, the untradeable barrows
		// gloves (Recipe for Disaster) are obtainable and dominate the
		// hands slot for melee.
		DpsResult best = new LoadoutOptimizer()
			.optimize(data, owned(Map.of(WHIP, 1), 100_000)).get(0);
		Assert.assertEquals("Barrows gloves", handsName(best));
	}

	@Test
	public void withoutABudgetQuestRewardsStayOutOfAnOwnedOnlySearch()
	{
		DpsResult best = new LoadoutOptimizer()
			.optimize(data, owned(Map.of(WHIP, 1), 0)).get(0);
		Assert.assertNotEquals("Barrows gloves", handsName(best));
	}

	@Test
	public void questRewardsNeverChargeTheUpgradeBudget()
	{
		// A 1 gp budget cannot BUY anything real, but quest rewards are
		// free against it: the tradeable helm of neitiznot (The Fremennik
		// Isles, ~50k on the GE) must still surface, and the total
		// purchase cost must stay within the 1 gp budget - proof its
		// price was never charged.
		DpsResult best = new LoadoutOptimizer()
			.optimize(data, owned(Map.of(WHIP, 1), 1)).get(0);
		GearItem head = best.getLoadout().get(GearSlot.HEAD);
		Assert.assertNotNull(head);
		Assert.assertEquals("Helm of neitiznot", head.getName());
		Assert.assertTrue("spent " + best.getPurchaseCost(), best.getPurchaseCost() <= 1);
	}

	@Test
	public void anExcludedQuestRewardStaysOut()
	{
		OptimizationRequest req = owned(Map.of(WHIP, 1), 100_000)
			.withExcludedItems(Set.of(BARROWS_GLOVES));
		DpsResult best = new LoadoutOptimizer().optimize(data, req).get(0);
		Assert.assertNotEquals("Barrows gloves", handsName(best));
	}
}
