package com.loadoutlab.data;

import com.loadoutlab.engine.CandidateMode;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.LoadoutOptimizer;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerBonuses;
import com.loadoutlab.engine.TestRequests;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Fractured Archive (released 2026-10-20) lands in one pass: the
 * encounters with published stats as a raid group, the rewards as gear
 * rows (scripts/wiki_intake.py, ids from runelite-api 1.13.3 gameval).
 */
class TfaIntakeTest {
	private static LoadoutData data;
	private static MonsterGroups.MonsterGroup tfa;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		tfa = MonsterGroups.load(data).stream()
			.filter(g -> g.getName().equals("The Fractured Archive"))
			.findFirst().orElseThrow();
	}

	private static MonsterStats guardian() {
		return tfa.getMobs().stream().filter(m -> m.getName().equals("Archive Guardian")).findFirst().orElseThrow();
	}

	@Test
	@DisplayName("the raid group lists the encounters that have stats: the Guardian and Xarth in two phases, the Dagon'hai trio, Ocellus in three")
	void groupRoster() {
		assertTrue(tfa.isRaid());
		// The two Monks share one stat block, so the corpus folds them into one row.
		assertEquals(14, tfa.getMobs().size());
		Set<String> names = tfa.getMobs().stream().map(MonsterStats::getName).collect(Collectors.toSet());
		for (String name : new String[]{"Archive Guardian", "Ezekiel", "Bork", "The Smorks",
			"Dreadnoth the Jellied", "Moia", "Xarth", "Ocellus", "Dagon'hai Elder", "Dagon'hai Monk"}) {
			assertTrue(names.contains(name), name);
		}
		assertEquals(3, tfa.getMobs().stream().filter(m -> m.getName().equals("Ocellus")).count());
	}

	@Test
	@DisplayName("the Archive Guardian reads 550 hitpoints and an 80% fire weakness")
	void guardianStats() {
		MonsterStats guardian = guardian();
		assertEquals(550, guardian.getHitpoints());
		assertEquals("fire", guardian.getWeaknessElement());
		assertEquals(80, guardian.getWeaknessSeverity());
	}

	@Test
	@DisplayName("every style computes a set against the Guardian")
	void everyStyleComputes() {
		for (CombatStyle style : CombatStyle.values()) {
			OptimizationRequest request = TestRequests.of(guardian(), style, PlayerLevels.MAXED,
				PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0, CandidateMode.ALL_STANDARD,
				true, false, OwnedItems.EMPTY, 1);
			assertFalse(new LoadoutOptimizer().optimize(data, request).isEmpty(), style.name());
		}
	}

	@Test
	@DisplayName("the tradeable rewards are priced so the game-best pool admits them; the charged weapons are untradeable")
	void rewardsArePricedAndTradeableByVersion() {
		for (int id : new int[]{34681, 34719, 34717, 34727, 34684, 34702}) {
			GearItem item = data.getGear(id);
			assertTrue(item.isTradeable(), item.getName());
			assertNotNull(item.getEstimatedPrice(), item.getName() + " needs a price for ALL_STANDARD");
		}
		assertFalse(data.getGear(34715).isTradeable(), "Obligator (charged)");
		assertFalse(data.getGear(34725).isTradeable(), "Zorya's tome (charged)");
		GearItem xbow = data.getGear(34681);
		OptimizationRequest request = TestRequests.of(guardian(), CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0, CandidateMode.ALL_STANDARD,
			true, false, OwnedItems.EMPTY, 1);
		java.util.Map<GearSlot, Integer> pin = new java.util.EnumMap<>(GearSlot.class);
		pin.put(GearSlot.WEAPON, xbow.getId());
		assertFalse(new LoadoutOptimizer().optimize(data, request.withPinnedItems(pin)).isEmpty(),
			"the crossbows find their bolts without a pinned ammo slot");
	}

	@Test
	@DisplayName("the rewards are gear rows: the Obligator a crush two-hander, the tome a powered staff, the crossbows and bolts, the Rondache")
	void rewardsInTheCorpus() {
		GearItem obligator = data.getGear(34715);
		assertNotNull(obligator);
		assertEquals("Obligator", obligator.getName());
		assertTrue(obligator.isTwoHanded());
		assertEquals(120, obligator.getOffensive().getCrush());
		GearItem tome = data.getGear(34725);
		assertNotNull(tome);
		assertTrue(tome.isPoweredStaff(), "Zorya's tome is a powered staff");
		assertTrue(tome.isTwoHanded());
		assertNotNull(data.getGear(34681), "Ascension crossbows");
		assertNotNull(data.getGear(34719), "Rondache");
		for (int bolts : new int[]{34684, 34690, 34696, 34702, 34708}) {
			assertEquals(GearSlot.AMMO, data.getGear(bolts).getSlot(), "bolts " + bolts);
		}
	}
}
