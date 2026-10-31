package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pinned items: user preference wins the slot (bracelet of slaughter
 * class - value the model cannot price), the optimizer builds the best
 * set AROUND the pin. Optimizer-level by mandate: these run the real
 * search, the same path the panel renders.
 */
class PinTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static OptimizationRequest ranged(CandidateMode mode, OwnedItems owned) {
		return new OptimizationRequest(
			data.searchMonsters("goblin", 1).get(0), Style.RANGED,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			mode, true, false, owned, RequirementProfile.MAXED, 1);
	}

	@Test
	@DisplayName("a pinned bracelet of slaughter holds the hands slot against better dps gloves")
	void pinnedSlaughterBraceletWins() {
		OwnedItems owned = new OwnedItems(
			Map.of(861, 1, 21326, 100, 7462, 1, 21183, 1), true);
		List<DpsResult> results = new LoadoutOptimizer().optimize(data,
			ranged(CandidateMode.OWNED_ONLY, owned)
				.withPinnedItems(Map.of(GearSlot.HANDS, 21183)));
		assertFalse(results.isEmpty());
		Gear hands = results.get(0).getLoadout().get(GearSlot.HANDS);
		assertNotNull(hands);
		assertEquals("bracelet of slaughter", hands.getNameLower(),
			"got: " + hands.getNameLower());
	}

	@Test
	@DisplayName("an UNOWNED pin still rides in owned-only mode - 'I am bringing this'")
	void unownedPinAppears() {
		OwnedItems owned = new OwnedItems(Map.of(861, 1, 21326, 100), true);
		List<DpsResult> results = new LoadoutOptimizer().optimize(data,
			ranged(CandidateMode.OWNED_ONLY, owned)
				.withPinnedItems(Map.of(GearSlot.HANDS, 21183)));
		assertFalse(results.isEmpty());
		Gear hands = results.get(0).getLoadout().get(GearSlot.HANDS);
		assertNotNull(hands);
		assertEquals("bracelet of slaughter", hands.getNameLower());
	}

	@Test
	@DisplayName("a pinned salve rides a low-risk set - the never-risk veto yields to the pin")
	void pinnedSalveSurvivesLowRisk() {
		MonsterStatsHolder rev = new MonsterStatsHolder();
		rev.monster = data.searchMonsters("revenant demon", 1).get(0);
		Gear salve = null;
		for (Gear item : data.getGearItems()) {
			if (item.getNameLower().equals("salve amulet(ei)")) {
				salve = item;
			}
		}
		assertNotNull(salve);

		OptimizationRequest constrained = new OptimizationRequest(
			rev.monster, Style.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY,
			RequirementProfile.MAXED, 1)
			.withMaxTradeables(3).withRiskBudgetGp(25_000)
			.withPinnedItems(Map.of(GearSlot.NECK, salve.getId()));

		List<DpsResult> results = new LoadoutOptimizer().optimize(data, constrained);
		assertFalse(results.isEmpty());
		Gear neck = results.get(0).getLoadout().get(GearSlot.NECK);
		assertNotNull(neck);
		assertEquals("salve amulet(ei)", neck.getNameLower(),
			"the pin must override the rebuild veto and the cap, got: " + neck.getNameLower());
	}

	@Test
	@DisplayName("a pinned weapon locks the weapon line")
	void pinnedWeaponForced() {
		List<DpsResult> results = new LoadoutOptimizer().optimize(data,
			ranged(CandidateMode.ALL_STANDARD, OwnedItems.EMPTY)
				.withPinnedItems(Map.of(GearSlot.WEAPON, 861)));
		assertFalse(results.isEmpty());
		Gear weapon = results.get(0).getLoadout().get(GearSlot.WEAPON);
		assertEquals("magic shortbow", weapon.getNameLower(),
			"got: " + weapon.getNameLower());
	}

	private static final class MonsterStatsHolder {
		com.loadoutlab.data.Monster monster;
	}
}
