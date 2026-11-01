package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
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
class RevenantPoolTest {
	private static Tables data;
	private static Monster revDemon;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		revDemon = data.searchMonsters("revenant demon", 1).get(0);
	}

	private static Req request(CandidateMode mode, Owned owned) {
		return new Req(revDemon, Style.RANGED, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			mode, true, false, owned, RequirementProfile.MAXED, 1);
	}

	@Test
	@DisplayName("game best at revenants reaches for a charged wilderness bow")
	void gameBestFindsTheWildernessBow() {
		List<Dps> results = new LoadoutOptimizer()
			.optimize(data, request(CandidateMode.ALL_STANDARD, Owned.EMPTY));
		assertFalse(results.isEmpty());

		Dps best = results.get(0);
		String weapon = best.getLoadout().get(Slot.WEAPON).getLname();
		String version = best.getLoadout().get(Slot.WEAPON).getVer();
		assertTrue(weapon.contains("webweaver") || weapon.contains("craw's"),
			"expected a wilderness bow, got: " + weapon);
		assertEquals("charged", version,
			"only the charged version fires the +50% passive");
		assertTrue(best.getDps() > 12.0,
			"the buffed ceiling must clear the old 11.58 plateau, got " + best.getDps());
	}

	@Test
	@DisplayName("an owned charged craw's bow wins the owned search at revenants")
	void ownedCrawsBowGetsPicked() {
		// Craw's bow charged (22550) vs a magic shortbow (861) + amethyst
		// arrows (21326): without the pool fix the charged bow never even
		// reached the DPS calculator.
		Owned owned = new Owned(
			Map.of(22550, 1, 861, 1, 21326, 100), true);
		List<Dps> results = new LoadoutOptimizer()
			.optimize(data, request(CandidateMode.OWNED_ONLY, owned));
		assertFalse(results.isEmpty());
		String weapon = results.get(0).getLoadout().get(Slot.WEAPON).getLname();
		assertTrue(weapon.contains("craw's"), "expected craw's bow, got: " + weapon);
	}

	@Test
	@DisplayName("low-risk sets NEVER risk a rebuild item, no matter the cap")
	void lowRiskNeverRisksARebuildItem() {
		for (int cap : new int[]{25_000, 5_000_000}) {
			Req constrained = request(CandidateMode.ALL_STANDARD, Owned.EMPTY)
				.withMaxTr(3).withRiskBudgetGp(cap);
			List<Dps> results = new LoadoutOptimizer().optimize(data, constrained);
			assertFalse(results.isEmpty());
			com.ll.data.Gear neck =
				results.get(0).getLoadout().get(Slot.NECK);
			assertTrue(neck == null || !neck.getLname().contains("salve"),
				"an unprotectable salve may never ride a low-risk set (cap " + cap
					+ "), got: " + (neck == null ? "empty" : neck.getLname()));
		}
	}

	@Test
	@DisplayName("the ethereum bracelet and avarice survive the candidate pool at revenants")
	void conditionalItemsSurviveThePool() {
		// Zero-stat hands (ethereum) died at the zero-score prune; the
		// score boosts must keep both alive ONLY at revenants.
		Req atRevs = request(CandidateMode.ALL_STANDARD, Owned.EMPTY);
		Req atGoblin = new Req(
			data.searchMonsters("goblin", 1).get(0), Style.RANGED, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, Owned.EMPTY, RequirementProfile.MAXED, 1);

		com.ll.data.Gear ethereum = null;
		com.ll.data.Gear avarice = null;
		for (com.ll.data.Gear item : data.getGearItems()) {
			if (item.getLname().equals("bracelet of ethereum")
				&& "charged".equals(item.getVer())) {
				ethereum = item;
			}
			if (item.getLname().equals("amulet of avarice")) {
				avarice = item;
			}
		}
		assertNotNull(ethereum);
		assertNotNull(avarice);

		assertTrue(LoadoutOptimizer.candidateScore(atRevs, ethereum, false) > 0,
			"ethereum must survive the zero-score prune at revenants");
		assertTrue(LoadoutOptimizer.candidateScore(atRevs, avarice, false)
			> LoadoutOptimizer.candidateScore(atGoblin, avarice, false) + 4_000,
			"avarice gets its boost only at revenants");
	}
}
