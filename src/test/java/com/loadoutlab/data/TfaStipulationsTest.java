package com.loadoutlab.data;

import com.loadoutlab.engine.CandidateMode;
import com.loadoutlab.engine.Style;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.Loadout;
import com.loadoutlab.engine.LoadoutOptimizer;
import com.loadoutlab.engine.MonsterMechanics;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerBonuses;
import com.loadoutlab.engine.TestRequests;
import java.util.EnumMap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Andrew, client test 2026-10-20: "all of my bis recommendations for TFA
 * are melee". The published defences favour crush nearly everywhere, but
 * two phases cannot be meleed (wiki, read 2026-10-20): the Archive
 * Guardian flies until its shield breaks (ranged, magic or a halberd),
 * and Xarth's first phase is fought from behind the pillars. The Guardian
 * is also immune to Ancient Magicks.
 */
class TfaStipulationsTest {
	private static LoadoutData data;
	private static List<Monster> tfa;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		tfa = MonsterGroups.load(data).stream()
			.filter(g -> g.getName().equals("The Fractured Archive"))
			.findFirst().orElseThrow().getMobs();
	}

	private static Monster member(String label) {
		return tfa.stream().filter(m -> label.equals(m.getVersion()))
			.findFirst().orElseThrow(() -> new AssertionError("no member " + label));
	}

	private static List<DpsResult> best(Monster mob, Style style) {
		OptimizationRequest request = TestRequests.of(mob, style, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0, CandidateMode.ALL_STANDARD,
			true, false, OwnedItems.EMPTY, 3);
		return new LoadoutOptimizer().optimize(data, request);
	}

	@Test
	@DisplayName("the roster carries the Guardian's two phases, Xarth's two phases and the Dagon'hai Elder and Monk: fourteen rows")
	void fourteenRows() {
		// The Dagon'hai joined 2026-10-22 (ISS-01M5JBYYKPE7192Z24NTR432M8).
		assertEquals(14, tfa.size());
	}

	@Test
	@DisplayName("the shielded Guardian flies: a melee set must be a halberd")
	void shieldFlies() {
		Monster shield = member("Shield (flying)");
		assertTrue(shield.hasAttribute("flying"));
		for (DpsResult r : best(shield, Style.MELEE)) {
			assertEquals("Polearm", r.getLoadout().getWeapon().getCategory(), r.getLoadout().getWeapon().getName());
		}
	}

	@Test
	@DisplayName("the grounded Guardian takes any melee weapon")
	void groundedTakesMelee() {
		Monster grounded = member("Grounded");
		assertFalse(grounded.hasAttribute("flying"));
		List<DpsResult> melee = best(grounded, Style.MELEE);
		assertFalse(melee.isEmpty());
		assertNotEquals("Polearm", melee.get(0).getLoadout().getWeapon().getCategory());
	}

	@Test
	@DisplayName("Xarth's first phase has no melee card; his second does")
	void xarthPhases() {
		assertTrue(com.loadoutlab.engine.Immunity.styleImmune(member("P1 (from the pillars)"), Style.MELEE));
		assertFalse(com.loadoutlab.engine.Immunity.styleImmune(member("P2"), Style.MELEE));
		assertFalse(best(member("P2"), Style.MELEE).isEmpty());
	}

	@Test
	@DisplayName("the Guardian takes no Ancient Magicks in either phase")
	void guardianShrugsOffAncients() {
		SpellStats barrage = data.getSpells().stream().filter(s -> s.getName().equals("Ice Barrage")).findFirst().orElseThrow();
		SpellStats surge = data.getSpells().stream().filter(s -> s.getName().equals("Fire Surge")).findFirst().orElseThrow();
		EnumMap<GearSlot, Gear> worn = new EnumMap<>(GearSlot.class);
		worn.put(GearSlot.WEAPON, data.getGear(21006));
		Loadout kodai = new Loadout(worn);
		for (String phase : new String[]{"Shield (flying)", "Grounded"}) {
			Monster guardian = member(phase);
			assertTrue(MonsterMechanics.isImmune(guardian, Style.MAGIC, kodai, barrage), phase);
			assertFalse(MonsterMechanics.isImmune(guardian, Style.MAGIC, kodai, surge), phase);
		}
	}

	@Test
	@DisplayName("Ocellus is a demon in phases 1 and 3 only - the wiki leaves phase 2's attributes blank")
	void ocellusDemonPhases() {
		for (Monster m : tfa) {
			if (m.getName().equals("Ocellus")) {
				assertEquals(!"Phase 2".equals(m.getVersion()), m.hasAttribute("demon"), m.getVersion());
			}
		}
	}

	@Test
	@DisplayName("Ocellus's third phase reads its own 5,250 hitpoints")
	void ocellusThree() {
		assertEquals(5250, tfa.stream().filter(m -> "Phase 3".equals(m.getVersion()) && m.getName().equals("Ocellus"))
			.findFirst().orElseThrow().getHitpoints());
	}
}
