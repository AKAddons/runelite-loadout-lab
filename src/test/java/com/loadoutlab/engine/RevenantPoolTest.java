package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OPTIMIZER-LEVEL coverage for the revenant conditionals - the layer the
 * unit tests missed (field report: the DPS math was verified correct but
 * the candidate pool never surfaced the items, so nothing on screen
 * changed). These run the real search end to end, the same path the
 * panel and the headless query render.
 */
class RevenantPoolTest
{
	private static LoadoutData data;
	private static MonsterStats revDemon;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		revDemon = data.searchMonsters("revenant demon", 1).get(0);
	}

	private static OptimizationRequest request(CandidateMode mode, OwnedItems owned)
	{
		return new OptimizationRequest(revDemon, CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			mode, true, false, owned, RequirementProfile.MAXED, 1);
	}

	@Test
	@DisplayName("game best at revenants reaches for a charged wilderness bow")
	void gameBestFindsTheWildernessBow()
	{
		List<DpsResult> results = new LoadoutOptimizer()
			.optimize(data, request(CandidateMode.ALL_STANDARD, OwnedItems.EMPTY));
		assertFalse(results.isEmpty());

		DpsResult best = results.get(0);
		String weapon = best.getLoadout().get(GearSlot.WEAPON).getNameLower();
		String version = best.getLoadout().get(GearSlot.WEAPON).getVersionLower();
		assertTrue(weapon.contains("webweaver") || weapon.contains("craw's"),
			"expected a wilderness bow, got: " + weapon);
		assertEquals("charged", version,
			"only the charged version fires the +50% passive");
		assertTrue(best.getDps() > 12.0,
			"the buffed ceiling must clear the old 11.58 plateau, got " + best.getDps());
	}

	@Test
	@DisplayName("an owned charged craw's bow wins the owned search at revenants")
	void ownedCrawsBowGetsPicked()
	{
		// Craw's bow charged (22550) vs a magic shortbow (861) + amethyst
		// arrows (21326): without the pool fix the charged bow never even
		// reached the DPS calculator.
		OwnedItems owned = new OwnedItems(
			Map.of(22550, 1, 861, 1, 21326, 100), true);
		List<DpsResult> results = new LoadoutOptimizer()
			.optimize(data, request(CandidateMode.OWNED_ONLY, owned));
		assertFalse(results.isEmpty());
		String weapon = results.get(0).getLoadout().get(GearSlot.WEAPON).getNameLower();
		assertTrue(weapon.contains("craw's"), "expected craw's bow, got: " + weapon);
	}

	@Test
	@DisplayName("the risk cap is the dial: a tight cap drops the salve's rebuild errand, a loose one keeps it")
	void riskCapDialsTheSalveOut()
	{
		OptimizationRequest tight = request(CandidateMode.ALL_STANDARD, OwnedItems.EMPTY)
			.withMaxTradeables(3).withRiskBudgetGp(25_000);
		OptimizationRequest loose = request(CandidateMode.ALL_STANDARD, OwnedItems.EMPTY)
			.withMaxTradeables(3).withRiskBudgetGp(5_000_000);

		List<DpsResult> tightResults = new LoadoutOptimizer().optimize(data, tight);
		List<DpsResult> looseResults = new LoadoutOptimizer().optimize(data, loose);
		assertFalse(tightResults.isEmpty());
		assertFalse(looseResults.isEmpty());

		com.loadoutlab.data.GearItem tightNeck =
			tightResults.get(0).getLoadout().get(GearSlot.NECK);
		com.loadoutlab.data.GearItem looseNeck =
			looseResults.get(0).getLoadout().get(GearSlot.NECK);
		assertTrue(tightNeck == null || !tightNeck.getNameLower().contains("salve"),
			"a 25k cap cannot afford the salve's 150k rebuild errand, got: "
				+ (tightNeck == null ? "empty" : tightNeck.getNameLower()));
		assertNotNull(looseNeck);
		assertTrue(looseNeck.getNameLower().contains("salve")
				|| looseNeck.getNameLower().contains("avarice"),
			"a loose cap keeps the conditional neck, got: " + looseNeck.getNameLower());
	}

	@Test
	@DisplayName("the ethereum bracelet and avarice survive the candidate pool at revenants")
	void conditionalItemsSurviveThePool()
	{
		// Zero-stat hands (ethereum) died at the zero-score prune; the
		// score boosts must keep both alive ONLY at revenants.
		OptimizationRequest atRevs = request(CandidateMode.ALL_STANDARD, OwnedItems.EMPTY);
		OptimizationRequest atGoblin = new OptimizationRequest(
			data.searchMonsters("goblin", 1).get(0), CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, RequirementProfile.MAXED, 1);

		com.loadoutlab.data.GearItem ethereum = null;
		com.loadoutlab.data.GearItem avarice = null;
		for (com.loadoutlab.data.GearItem item : data.getGearItems())
		{
			if (item.getNameLower().equals("bracelet of ethereum")
				&& "charged".equals(item.getVersionLower()))
			{
				ethereum = item;
			}
			if (item.getNameLower().equals("amulet of avarice"))
			{
				avarice = item;
			}
		}
		assertNotNull(ethereum);
		assertNotNull(avarice);

		assertTrue(LoadoutOptimizer.candidateScoreForTest(atRevs, ethereum) > 0,
			"ethereum must survive the zero-score prune at revenants");
		assertTrue(LoadoutOptimizer.candidateScoreForTest(atRevs, avarice)
			> LoadoutOptimizer.candidateScoreForTest(atGoblin, avarice) + 4_000,
			"avarice gets its boost only at revenants");
	}
}
