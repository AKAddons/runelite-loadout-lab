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
		// Non-boss wilderness mobs get the low-risk toggles too.
		Assert.assertTrue(WildernessMonsters.isWilderness(
			data.searchMonsters("green dragon", 1).get(0)));
		Assert.assertTrue(WildernessMonsters.isWilderness(
			data.searchMonsters("dust devil", 1).get(0)));
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
	public void riskCappedSetsTotalDroppableValueStaysWithinTheBudget()
	{
		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		for (CombatStyle style : new CombatStyle[]{CombatStyle.MELEE, CombatStyle.RANGED})
		{
			List<DpsResult> capped = optimizer.optimize(data, request(style, 3));
			Assert.assertFalse(capped.isEmpty());
			DpsResult best = optimizer.fillDpsNeutralSlots(data, request(style, 3), capped.get(0));
			// The kept 3 are immune; the TOTAL of everything else must fit
			// the budget - not a per-item floor. Untradeable repair/mangle
			// fees count too, so a budget-fitting set can carry NO
			// mangle-class untradeable (each of those alone costs 500k).
			PvpRisk.Assessment risk = PvpRisk.assess(best.getLoadout(), null, 3);
			Assert.assertTrue(style + " risks " + risk.riskGp,
				risk.riskGp <= OptimizationRequest.DEFAULT_RISK_BUDGET_GP);
			for (PvpRisk.Charge charge : risk.untradeableCharges)
			{
				Assert.assertTrue(charge.item.label() + " costs " + charge.costGp,
					charge.costGp <= OptimizationRequest.DEFAULT_RISK_BUDGET_GP);
			}
		}
	}

	@Test
	public void realUntradeablesCarryTheirWikiDeathFees()
	{
		Assert.assertEquals(500_000, costOf("infernal cape"));
		Assert.assertEquals(150_000, costOf("fire cape"));
		Assert.assertEquals(500_000, costOf("elite void top"));
		Assert.assertEquals(35_000, costOf("rune defender"));
	}

	@Test
	public void curatedConvertsArePricedAtWhatTheKillerGets()
	{
		// Charged items drop uncharged for the killer (wiki, 2026-07).
		Assert.assertEquals(10_700_000, costOf("toxic blowpipe"));
		Assert.assertEquals(4, categoryOf("toxic blowpipe"));
		Assert.assertEquals(3_900_000, costOf("serpentine helm"));
		// Unprotected death degrades it into a kraken tentacle.
		Assert.assertEquals(250_000, costOf("abyssal tentacle"));
		Assert.assertEquals(4, categoryOf("abyssal tentacle"));
		// Reclassified: an unlocked ancient sceptre drops an ancient
		// staff for the killer (wiki Ancient sceptre) - it was a
		// mangle-class 500k before this audit.
		Assert.assertEquals(60_000, costOf("ancient sceptre"));
		Assert.assertEquals(4, categoryOf("ancient sceptre"));
	}

	@Test
	public void curatedCheapReclaimsNoLongerPayTheUnknownDefault()
	{
		// Quest and shop reclaims (wiki-priced), not the 500k fallback.
		Assert.assertEquals(100_000, costOf("sunspear"));
		Assert.assertEquals(2, categoryOf("sunspear"));
		Assert.assertEquals(140_000, costOf("lunar torso"));
		Assert.assertEquals(99_000, costOf("strength cape"));
		// Always kept on a PvP death per its wiki page: free to wear.
		Assert.assertEquals(0, costOf("max cape"));
		Assert.assertEquals(1, categoryOf("max cape"));
	}

	private static long costOf(String name)
	{
		return UntradeableDeathCosts.costFor(untradeable(name));
	}

	private static int categoryOf(String name)
	{
		return UntradeableDeathCosts.categoryFor(untradeable(name));
	}

	private static com.loadoutlab.data.GearItem untradeable(String name)
	{
		for (com.loadoutlab.data.GearItem item : data.getGearItems())
		{
			if (name.equalsIgnoreCase(item.getName()) && !item.isTradeable())
			{
				return item;
			}
		}
		throw new AssertionError("no untradeable item named " + name);
	}

	@Test
	public void dpsTiesBreakTowardLessRisk_gloryBeatsTheCrumblingDamnedAmulet()
	{
		// Amulet of the damned and amulet of glory have IDENTICAL stats.
		// The damned crumbles to dust on death (34k every time); the glory
		// rides a kept slot for free. In risk mode the tie must go to the
		// glory - the old purchase-cost tie-break picked the damned because
		// an owned untradeable reads as cost 0.
		java.util.Map<Integer, Integer> owned = new java.util.HashMap<>();
		owned.put(4151, 1);  // abyssal whip
		owned.put(1712, 1);  // amulet of glory (4)
		owned.put(12851, 1); // amulet of the damned (full)
		OptimizationRequest req = new OptimizationRequest(callisto, CombatStyle.MELEE,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL),
			null, 0, CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(data.canonicalizeOwned(owned), true), 1).withMaxTradeables(3);
		List<DpsResult> out = new LoadoutOptimizer().optimize(data, req);
		Assert.assertFalse(out.isEmpty());
		com.loadoutlab.data.GearItem neck = out.get(0).getLoadout().get(com.loadoutlab.data.GearSlot.NECK);
		Assert.assertNotNull(neck);
		Assert.assertEquals("Amulet of glory", neck.getName());
	}

	@Test
	public void theDamnedAmuletLosesStatTiesOutsideTheWildernessToo()
	{
		// It crumbles on ANY death (PvM included); its barrows set bonus is
		// not modeled, so with a glory owned it should never be suggested.
		java.util.Map<Integer, Integer> owned = new java.util.HashMap<>();
		owned.put(4151, 1);
		owned.put(1712, 1);
		owned.put(12851, 1);
		MonsterStats goblin = data.searchMonsters("goblin", 1).get(0);
		OptimizationRequest req = new OptimizationRequest(goblin, CombatStyle.MELEE,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL),
			null, 0, CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(data.canonicalizeOwned(owned), true), 1);
		List<DpsResult> out = new LoadoutOptimizer().optimize(data, req);
		Assert.assertFalse(out.isEmpty());
		com.loadoutlab.data.GearItem neck = out.get(0).getLoadout().get(com.loadoutlab.data.GearSlot.NECK);
		if (neck != null)
		{
			Assert.assertEquals("Amulet of glory", neck.getName());
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

	@Test
	public void aTighterRiskBudgetIsHonoredAndNeverBeatsTheDefault()
	{
		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		List<DpsResult> tight = optimizer.optimize(data,
			request(CombatStyle.MELEE, 3).withRiskBudgetGp(10_000));
		Assert.assertFalse(tight.isEmpty());
		PvpRisk.Assessment risk = PvpRisk.assess(tight.get(0).getLoadout(), null, 3);
		Assert.assertTrue("risks " + risk.riskGp, risk.riskGp <= 10_000);
		// A tighter budget can only shrink the candidate space.
		double withDefault = optimizer.optimize(data, request(CombatStyle.MELEE, 3)).get(0).getDps();
		Assert.assertTrue(tight.get(0).getDps() <= withDefault + 1e-9);
	}
}
