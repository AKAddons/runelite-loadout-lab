package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dragonfire honesty (2026-07 player audit A3.2): when the shield-required
 * constraint CANNOT be satisfied (no protective shield in the pool), the
 * beam used to half-apply it - every two-handed weapon banned AND the
 * returned set still had an empty shield, with pure-damage items (Ultor,
 * Avernic) unrecoverable by the neutral fill. A maxed player's Vorkath
 * magic card showed a 2.71-dps Sanguinesti while their Shadow computed
 * ~7.5. Now the constraint is all-or-nothing: unsatisfiable means the
 * UNCONSTRAINED set comes back flagged antifire-assumed, so the panel can
 * say "needs a super antifire" instead of quietly recommending nonsense.
 */
class DragonfireHonestyTest {
	private static Tables data;
	private static Monster vorkath;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		vorkath = data.searchMonsters("vorkath", 1).get(0);
		assertEquals("", vorkath.getVersion(), "test premise: everyday Vorkath owns the bare name");
		assertTrue(DragonfireRules.breathesFire(vorkath));
	}

	private static Gear byName(String lname, String ver) {
		for (Gear item : data.getGearItems()) {
			if (item.getLname().equals(lname)
				&& (ver == null || ver.equals(item.getVer()))) {
				return item;
			}
		}
		throw new AssertionError("corpus is missing: " + lname);
	}

	private static Req owning(Style style, Gear... items) {
		Map<Integer, Integer> owned = new HashMap<>();
		for (Gear item : items) {
			owned.put(item.getId(), 1);
		}
		return new Req(vorkath, style, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new Owned(owned, true), RequirementProfile.MAXED, 3);
	}

	@Test
	@DisplayName("no protective shield owned: the 2h Shadow comes back, flagged antifire-assumed")
	void unsatisfiableConstraintFallsBackHonestly() {
		Gear shadow = byName("tumeken's shadow", "charged");
		List<Dps> results = new LoadoutOptimizer().optimize(data, owning(Style.MAGIC, shadow));
		assertFalse(results.isEmpty(), "the shadow line must not die");
		assertEquals("tumeken's shadow", results.get(0).getLoadout().getWeapon().getLname());
		assertTrue(results.get(0).isAntifireAssumed(),
			"the result must confess it assumes a super antifire");
	}

	@Test
	@DisplayName("a protective shield owned: the constraint applies and nothing is assumed")
	void satisfiableConstraintStaysHonest() {
		Gear sang = byName("sanguinesti staff", "charged");
		Gear antiDragon = byName("anti-dragon shield", null);
		List<Dps> results = new LoadoutOptimizer().optimize(
			data, owning(Style.MAGIC, sang, antiDragon));
		assertFalse(results.isEmpty());
		Dps best = results.get(0);
		assertNotNull(best.getLoadout().get(com.ll.data.Slot.SHIELD),
			"protection must be worn");
		assertTrue(DragonfireRules.isProtectiveShield(
			best.getLoadout().get(com.ll.data.Slot.SHIELD)));
		assertFalse(best.isAntifireAssumed());
	}

	@Test
	@DisplayName("an explicit antifire potion is the user's own assumption - not flagged")
	void explicitPotionIsNotFlagged() {
		Gear shadow = byName("tumeken's shadow", "charged");
		List<Dps> results = new LoadoutOptimizer().optimize(
			data, owning(Style.MAGIC, shadow).withAntifirePotion(true));
		assertFalse(results.isEmpty());
		assertEquals("tumeken's shadow", results.get(0).getLoadout().getWeapon().getLname());
		assertFalse(results.get(0).isAntifireAssumed());
	}
}
