package com.ll.data;

import com.ll.engine.CandidateMode;
import com.ll.engine.Style;
import com.ll.engine.LoadoutOptimizer;
import com.ll.engine.Req;
import com.ll.engine.Owned;
import com.ll.engine.Levels;
import com.ll.engine.PrayerBonuses;
import com.ll.engine.TestRequests;
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
	private static Tables data;
	private static MonsterGroups.MonsterGroup tfa;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		tfa = MonsterGroups.load(data).stream()
			.filter(g -> g.getName().equals("The Fractured Archive"))
			.findFirst().orElseThrow();
	}

	private static Monster guardian() {
		return tfa.getMobs().stream().filter(m -> m.getName().equals("Archive Guardian")).findFirst().orElseThrow();
	}

	@Test
	@DisplayName("the raid group lists the encounters that have stats: the Guardian and Xarth in two phases, the Dagon'hai trio, Ocellus in three")
	void groupRoster() {
		assertTrue(tfa.isRaid());
		// The two Monks share one stat block, so the corpus folds them into one row.
		assertEquals(14, tfa.getMobs().size());
		Set<String> names = tfa.getMobs().stream().map(Monster::getName).collect(Collectors.toSet());
		for (String name : new String[]{"Archive Guardian", "Ezekiel", "Bork", "The Smorks",
			"Dreadnoth the Jellied", "Moia", "Xarth", "Ocellus", "Dagon'hai Elder", "Dagon'hai Monk"}) {
			assertTrue(names.contains(name), name);
		}
		assertEquals(3, tfa.getMobs().stream().filter(m -> m.getName().equals("Ocellus")).count());
	}

	@Test
	@DisplayName("the Archive Guardian reads 550 hitpoints and an 80% fire weakness")
	void guardianStats() {
		Monster guardian = guardian();
		assertEquals(550, guardian.getHitpoints());
		assertEquals("fire", guardian.getWeaknessElement());
		assertEquals(80, guardian.getWeaknessSeverity());
	}

	@Test
	@DisplayName("every style computes a set against the Guardian")
	void everyStyleComputes() {
		for (Style style : Style.values()) {
			Req request = TestRequests.of(guardian(), style, Levels.MAXED,
				PrayerBonuses.bestAvailable(Levels.MAXED), null, 0, CandidateMode.ALL_STANDARD,
				true, false, Owned.EMPTY, 1);
			assertFalse(new LoadoutOptimizer().optimize(data, request).isEmpty(), style.name());
		}
	}

	@Test
	@DisplayName("the tradeable rewards are priced so the game-best pool admits them; a charged weapon is priced as the uncharged twin you buy")
	void rewardsArePricedAndTradeableByVersion() {
		for (int id : new int[]{34681, 34719, 34717, 34727, 34684, 34702}) {
			Gear item = data.getGear(id);
			assertTrue(item.isTradeable(), item.getName());
			assertNotNull(item.getEstimatedPrice(), item.getName() + " needs a price for ALL_STANDARD");
		}
		// Charged versions only (ISS-01M5QQV2TGQ5X730T0JJ75S1RD): the uncharged
		// twin is a variant, and the charged one carries its price and trades.
		assertTrue(data.isVariant(34717), "Obligator (uncharged) stands behind the charged one");
		assertTrue(data.getGear(34715).isTradeable(), "Obligator (charged) is bought as the uncharged one");
		assertEquals(data.getGear(34717).getEstimatedPrice(), data.getGear(34715).getEstimatedPrice(), "at its price");
		assertTrue(data.getGear(34725).isTradeable(), "Zorya's tome (charged) the same way");
		Gear xbow = data.getGear(34681);
		Req request = TestRequests.of(guardian(), Style.RANGED, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0, CandidateMode.ALL_STANDARD,
			true, false, Owned.EMPTY, 1);
		java.util.Map<Slot, Integer> pin = new java.util.EnumMap<>(Slot.class);
		pin.put(Slot.WEAPON, xbow.getId());
		assertFalse(new LoadoutOptimizer().optimize(data, request.withPinnedItems(pin)).isEmpty(),
			"the crossbows find their bolts without a pinned ammo slot");
	}

	@Test
	@DisplayName("the rewards are gear rows: the Obligator a crush two-hander, the tome a powered staff, the crossbows and bolts, the Rondache")
	void rewardsInTheCorpus() {
		Gear obligator = data.getGear(34715);
		assertNotNull(obligator);
		assertEquals("Obligator", obligator.getName());
		assertTrue(obligator.isTwoh());
		assertEquals(120, obligator.getOffensive().getCrush());
		Gear tome = data.getGear(34725);
		assertNotNull(tome);
		assertTrue(tome.isPoweredStaff(), "Zorya's tome is a powered staff");
		assertTrue(tome.isTwoh());
		assertNotNull(data.getGear(34681), "Ascension crossbows");
		assertNotNull(data.getGear(34719), "Rondache");
		for (int bolts : new int[]{34684, 34690, 34696, 34702, 34708}) {
			assertEquals(Slot.AMMO, data.getGear(bolts).getSlot(), "bolts " + bolts);
		}
	}
}
