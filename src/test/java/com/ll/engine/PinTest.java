package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
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
	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static Req ranged(CandidateMode mode, Owned owned) {
		return new Req(
			data.searchMonsters("goblin", 1).get(0), Style.RANGED,
			Levels.MAXED, PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			mode, true, false, owned, RequirementProfile.MAXED, 1);
	}

	@Test
	@DisplayName("a pinned bracelet of slaughter holds the hands slot against better dps gloves")
	void pinnedSlaughterBraceletWins() {
		Owned owned = new Owned(
			Map.of(861, 1, 21326, 100, 7462, 1, 21183, 1), true);
		List<Dps> results = new LoadoutOptimizer().optimize(data,
			ranged(CandidateMode.OWNED_ONLY, owned)
				.withPinnedItems(Map.of(Slot.HANDS, 21183)));
		assertFalse(results.isEmpty());
		Gear hands = results.get(0).getLoadout().get(Slot.HANDS);
		assertNotNull(hands);
		assertEquals("bracelet of slaughter", hands.getLname(),
			"got: " + hands.getLname());
	}

	@Test
	@DisplayName("an UNOWNED pin still rides in owned-only mode - 'I am bringing this'")
	void unownedPinAppears() {
		Owned owned = new Owned(Map.of(861, 1, 21326, 100), true);
		List<Dps> results = new LoadoutOptimizer().optimize(data,
			ranged(CandidateMode.OWNED_ONLY, owned)
				.withPinnedItems(Map.of(Slot.HANDS, 21183)));
		assertFalse(results.isEmpty());
		Gear hands = results.get(0).getLoadout().get(Slot.HANDS);
		assertNotNull(hands);
		assertEquals("bracelet of slaughter", hands.getLname());
	}

	@Test
	@DisplayName("a pinned salve rides a low-risk set - the never-risk veto yields to the pin")
	void pinnedSalveSurvivesLowRisk() {
		MonsterStatsHolder rev = new MonsterStatsHolder();
		rev.monster = data.searchMonsters("revenant demon", 1).get(0);
		Gear salve = null;
		for (Gear item : data.getGearItems()) {
			if (item.getLname().equals("salve amulet(ei)")) {
				salve = item;
			}
		}
		assertNotNull(salve);

		Req constrained = new Req(
			rev.monster, Style.RANGED, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, Owned.EMPTY,
			RequirementProfile.MAXED, 1)
			.withMaxTr(3).withRiskBudgetGp(25_000)
			.withPinnedItems(Map.of(Slot.NECK, salve.getId()));

		List<Dps> results = new LoadoutOptimizer().optimize(data, constrained);
		assertFalse(results.isEmpty());
		Gear neck = results.get(0).getLoadout().get(Slot.NECK);
		assertNotNull(neck);
		assertEquals("salve amulet(ei)", neck.getLname(),
			"the pin must override the rebuild veto and the cap, got: " + neck.getLname());
	}

	@Test
	@DisplayName("a pinned weapon locks the weapon line")
	void pinnedWeaponForced() {
		List<Dps> results = new LoadoutOptimizer().optimize(data,
			ranged(CandidateMode.ALL_STANDARD, Owned.EMPTY)
				.withPinnedItems(Map.of(Slot.WEAPON, 861)));
		assertFalse(results.isEmpty());
		Gear weapon = results.get(0).getLoadout().get(Slot.WEAPON);
		assertEquals("magic shortbow", weapon.getLname(),
			"got: " + weapon.getLname());
	}

	private static final class MonsterStatsHolder {
		com.ll.data.Monster monster;
	}
}
