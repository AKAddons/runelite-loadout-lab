package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Candidate pool semantics: candidates = (owned + dreams + within-budget
 * purchases) - exclusions.
 */
public class DreamAndBudgetTest
{
	private static LoadoutData data;
	private static MonsterStats zulrah;

	@BeforeClass
	public static void load()
	{
		data = new DataService().load();
		// Zulrah: magic 300, the twisted bow's home turf - a dreamed tbow
		// must dominate here (vs a low-magic monster it may honestly lose).
		zulrah = data.searchMonsters("zulrah", 1).get(0);
	}

	private static OptimizationRequest owned(Map<Integer, Integer> owned, int budgetGp)
	{
		return new OptimizationRequest(zulrah, CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL),
			null, budgetGp,
			budgetGp > 0 ? CandidateMode.OWNED_OR_BUDGET : CandidateMode.OWNED_ONLY,
			true, false, new OwnedItems(data.canonicalizeOwned(owned), true), 1);
	}

	@Test
	public void aDreamedTwistedBowJoinsAnOwnedOnlySearch()
	{
		// Owned: magic shortbow + rune arrows. Dreamed: a twisted bow.
		OptimizationRequest plain = owned(Map.of(861, 1, 892, 100), 0);
		OptimizationRequest dreaming = plain.withDreamItems(Set.of(20997)); // twisted bow
		DpsResult without = new LoadoutOptimizer().optimize(data, plain).get(0);
		DpsResult with = new LoadoutOptimizer().optimize(data, dreaming).get(0);
		Assert.assertEquals("Twisted bow", with.getLoadout().getWeapon().getName());
		Assert.assertTrue(with.getDps() > without.getDps());
	}

	@Test
	public void anExcludedDreamItemStaysOut()
	{
		OptimizationRequest req = owned(Map.of(861, 1, 892, 100), 0)
			.withDreamItems(Set.of(20997))
			.withExcludedItems(Set.of(20997));
		DpsResult best = new LoadoutOptimizer().optimize(data, req).get(0);
		Assert.assertNotEquals("Twisted bow", best.getLoadout().getWeapon().getName());
	}

	@Test
	public void aBudgetShopsUpgradesWithinTotalSpend()
	{
		// Own a maple shortbow + adamant arrows (rune arrows outclass the
		// bow's ammo tier); 1M budget must buy real upgrades but keep the
		// TOTAL purchase cost within budget.
		OptimizationRequest req = owned(Map.of(853, 1, 890, 100), 1_000_000);
		DpsResult best = new LoadoutOptimizer().optimize(data, req).get(0);
		OptimizationRequest plain = owned(Map.of(853, 1, 890, 100), 0);
		DpsResult without = new LoadoutOptimizer().optimize(data, plain).get(0);
		Assert.assertTrue(best.getDps() > without.getDps());
		Assert.assertTrue("spent " + best.getPurchaseCost(),
			best.getPurchaseCost() <= 1_000_000);
		// And something was actually bought.
		Assert.assertTrue(best.getPurchaseCost() > 0);
	}

	@Test
	public void dreamItemsDoNotChargeTheUpgradeBudget()
	{
		// 100k budget + dreamed tbow: the dream is pretend-owned (free),
		// the budget still shops small upgrades around it.
		OptimizationRequest req = owned(Map.of(892, 100), 100_000)
			.withDreamItems(Set.of(20997));
		DpsResult best = new LoadoutOptimizer().optimize(data, req).get(0);
		Assert.assertEquals("Twisted bow", best.getLoadout().getWeapon().getName());
		Assert.assertTrue(best.getPurchaseCost() <= 100_000);
	}
}
