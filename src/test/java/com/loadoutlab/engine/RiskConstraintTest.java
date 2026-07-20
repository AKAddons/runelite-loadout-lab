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
		return TestRequests.of(callisto, style, PlayerLevels.MAXED,
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
	public void aRichBankNeverStarvesTheBeamOfCheapCompliantSets()
	{
		// Field bug 2026-07-18: KBD magic, wildy 75k cap, anti-dragon shield
		// excluded. The bank's expensive magic gear filled the score-cut
		// beam with partials that all bust the cap once the forced
		// protective shield lands, and the compute returned NO set while a
		// cheap compliant mystic/battlestaff line existed. The RISK_RESERVE
		// low-risk tail keeps such a line alive. (The precise starvation
		// needed the field bank's mid-tier breadth - this bank guards the
		// invariant: rich flood + tiny cap must still yield a set.)
		MonsterStats kbd = data.searchMonsters("king black dragon", 1).get(0);
		java.util.Map<Integer, Integer> owned = new java.util.HashMap<>();
		// The cheap compliant line...
		for (String name : new String[]{"mystic hat", "mystic robe top", "mystic robe bottom",
			"mystic gloves", "mystic boots", "battlestaff", "ancient wyvern shield",
			"anti-dragon shield"})
		{
			owned.put(gear(name).getId(), 1);
		}
		// ...buried under the expensive flood that dominates the beam. The
		// head/body/legs breadth matters: with >= 5 pricey options in each,
		// the all-expensive partial combinations alone overflow the beam
		// width, so a score-only cut carries not a single cheap line to the
		// forced-shield slot (where every all-expensive line dies).
		for (String name : new String[]{"virtus mask", "virtus robe top", "virtus robe bottom",
			"ancestral hat", "ancestral robe top", "ancestral robe bottom",
			"ahrim's hood", "ahrim's robetop", "ahrim's robeskirt",
			"blue moon helm", "blue moon chestplate", "blue moon tassets",
			"infinity hat", "infinity top", "infinity bottoms",
			"dagon'hai hat", "dagon'hai robe top", "dagon'hai robe bottom",
			"occult necklace", "amulet of torture", "amulet of fury",
			"tormented bracelet", "eternal boots", "magus ring",
			"kodai wand", "nightmare staff", "dragon hunter wand"})
		{
			owned.put(gear(name).getId(), 1);
		}
		// No prayer: augury saturates magic accuracy vs KBD, ties the
		// expensive partials with the cheap ones, and the less-risk
		// tie-break would rescue the cheap line on its own. Bare accuracy
		// keeps the ordering strict - the shape that starved the field beam.
		OptimizationRequest request = new OptimizationRequest(kbd, CombatStyle.MAGIC,
			PlayerLevels.MAXED, PrayerBonuses.NONE,
			null, 0, CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), RequirementProfile.MAXED, 1)
			.withMaxTradeables(3).withRiskBudgetGp(75_000).withInWilderness(true)
			.withExcludedItems(java.util.Set.of(gear("anti-dragon shield").getId()));
		List<DpsResult> best = new LoadoutOptimizer().optimize(data, request);
		Assert.assertFalse("a compliant cheap set exists - the beam must find one", best.isEmpty());
		Loadout found = best.get(0).getLoadout();
		Assert.assertEquals("ancient wyvern shield",
			found.get(com.loadoutlab.data.GearSlot.SHIELD).getName().toLowerCase());
		Assert.assertTrue("found set must respect the cap",
			PvpRisk.riskGp(found, null, 3) <= 75_000);
	}

	private static com.loadoutlab.data.GearItem gear(String name)
	{
		for (com.loadoutlab.data.GearItem item : data.getGearItems())
		{
			if (name.equalsIgnoreCase(item.getName()))
			{
				return item;
			}
		}
		throw new AssertionError("no such gear: " + name);
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
		OptimizationRequest req = TestRequests.of(callisto, CombatStyle.MELEE,
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
		OptimizationRequest req = TestRequests.of(goblin, CombatStyle.MELEE,
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
		Assert.assertTrue(LoadoutTestSupport.tradeableCount(four.get(0).getLoadout()) <= 4);
		Assert.assertTrue(four.get(0).getDps() >= three - 1e-9);
	}

	@Test
	public void aProtectOnlyItemIsNeverLeftInTheLostPile()
	{
		// Flag a mid-value tradeable "only bring if protected on death". In a
		// low-risk set it must come back either protected (a kept slot) or
		// not at all - never in the dropped pile.
		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		// Amulet of fury: a plausible tradeable the optimizer would otherwise
		// risk in a melee set at Callisto.
		int fury = 6585;
		OptimizationRequest base = request(CombatStyle.MELEE, 3);
		DpsResult unflagged = optimizer.fillDpsNeutralSlots(data, base,
			optimizer.optimize(data, base).get(0));

		OptimizationRequest flagged = base.withProtectOnlyItems(java.util.Set.of(fury));
		List<DpsResult> out = optimizer.optimize(data, flagged);
		Assert.assertFalse(out.isEmpty());
		DpsResult best = optimizer.fillDpsNeutralSlots(data, flagged, out.get(0));

		PvpRisk.Assessment risk = PvpRisk.assess(best.getLoadout(), null, 3);
		boolean droppedFlagged = risk.lost.stream().anyMatch(g -> g.getId() == fury);
		Assert.assertFalse("a protect-only item must never be dropped", droppedFlagged);
		// Sanity: the flag actually bit (either the item is protected, or it
		// was dropped from the set - both are acceptable, but the set must
		// remain valid and its risk within budget).
		Assert.assertTrue(risk.riskGp <= OptimizationRequest.DEFAULT_RISK_BUDGET_GP);
		Assert.assertNotNull(unflagged);
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
