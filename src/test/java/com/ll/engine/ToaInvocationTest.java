package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Tables;
import com.ll.data.MonsterGroups;
import com.ll.data.Monster;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToA invocation scaling (official engine rule, verified against the
 * weirdgloop engine 2026-08-07): defence rolls x (250+invocation)/250,
 * truncated, for every invocation-scaled ToA row. Field report
 * 2026-08-06: at invocation 0 a blowpipe edges the bowfa at the Enraged
 * Warden by ~1%, but at any real raid level the scaling craters the
 * blowpipe's small attack roll and the bowfa wins - modeling zero
 * silently told the wrong story.
 */
class ToaInvocationTest {
	private static Tables data;
	private static Monster enraged;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		enraged = data.searchMonsters("tumeken's warden", 8).stream()
			.filter(m -> "Enraged".equals(m.getVersion()))
			.findFirst().orElseThrow();
		assertTrue(MonsterMechanics.toaScaled(enraged));
	}

	private static Gear byName(String lname, String ver) {
		for (Gear g : data.getGearItems()) {
			if (g.getLname().equals(lname)
				&& (ver == null || g.getVer().equals(ver))) {
				return g;
			}
		}
		throw new AssertionError("corpus is missing: " + lname);
	}

	private static Req req(Monster monster, CandidateMode mode,
		Owned owned) {
		return TestRequests.of(monster, Style.RANGED, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			mode, true, false, owned, 1);
	}

	@Test
	@DisplayName("the defence roll scales by exactly (250+invocation)/250, truncated")
	void defenceRollScalesExactly() {
		java.util.EnumMap<com.ll.data.Slot, Gear> gear =
			new java.util.EnumMap<>(com.ll.data.Slot.class);
		gear.put(com.ll.data.Slot.WEAPON, byName("bow of faerdhinen", "charged"));
		Kit bofa = new Kit(gear);

		Dps at0 = new Calc().calculate(
			req(enraged, CandidateMode.ALL_STANDARD, Owned.EMPTY), bofa);
		Dps at300 = new Calc().calculate(
			req(enraged.withToaInvocation(300), CandidateMode.ALL_STANDARD, Owned.EMPTY), bofa);
		assertEquals(at0.getDr() * 550 / 250, at300.getDr(),
			"long math, multiply before divide - matches the official trackFactor");
		assertEquals(at0.getAr(), at300.getAr(),
			"invocation touches the DEFENCE side only");
		assertTrue(at300.getAccuracy() < at0.getAccuracy());
	}

	@Test
	@DisplayName("the field report both ways: blowpipe wins invocation 0, bowfa wins 300")
	void bowfaOvertakesTheBlowpipeAtRealRaidLevels() {
		Map<Integer, Integer> owned = new HashMap<>();
		for (Gear g : new Gear[]{
			byName("toxic blowpipe", "charged"), byName("bow of faerdhinen", "charged"),
			byName("crystal helm", "active"), byName("crystal body", "active"),
			byName("crystal legs", "active"),
			byName("masori mask (f)", null), byName("masori body (f)", null),
			byName("masori chaps (f)", null), byName("necklace of anguish", null),
			byName("zaryte vambraces", null), byName("dragon dart", "unpoisoned")}) {
			owned.put(g.getId(), 10000);
		}
		Owned bank = new Owned(owned, true);

		List<Dps> at0 = new LoadoutOptimizer().optimize(data,
			req(enraged, CandidateMode.OWNED_ONLY, bank));
		assertFalse(at0.isEmpty());
		assertTrue(at0.get(0).getLoadout().getWeapon().getLname().startsWith("toxic blowpipe"),
			"invocation 0: the blowpipe genuinely edges it, was: "
				+ at0.get(0).getLoadout().getWeapon().label());

		List<Dps> at300 = new LoadoutOptimizer().optimize(data,
			req(enraged.withToaInvocation(300), CandidateMode.OWNED_ONLY, bank));
		assertFalse(at300.isEmpty());
		assertTrue(at300.get(0).getLoadout().getWeapon().getLname().startsWith("bow of faerdhinen"),
			"invocation 300: the scaling flips it to the bowfa, was: "
				+ at300.get(0).getLoadout().getWeapon().label());
	}

	@Test
	@DisplayName("the group's synthetic warden phases scale too (profileId mapping)")
	void syntheticVariantsScale() {
		Monster p2 = MonsterGroups.load(data).stream()
			.filter(g -> "Tombs of Amascut".equals(g.getName()))
			.flatMap(g -> g.getMobs().stream())
			.filter(m -> m.getId() >= Monster.SYNTHETIC_ID_BASE)
			.findFirst().orElseThrow();
		assertTrue(MonsterMechanics.toaScaled(p2),
			"immuneVariant rows carry synthetic ids - profileId maps them back");
		assertEquals(300,
			MonsterMechanics.atToaInvocation(p2, 300).toaLevel());
	}

	@Test
	@DisplayName("non-ToA monsters and level 0 are no-ops; re-applying replaces, never compounds")
	void scalingIsScopedAndSetNotCompounded() {
		Monster goblin = data.searchMonsters("goblin", 1).get(0);
		assertSame(goblin, MonsterMechanics.atToaInvocation(goblin, 300),
			"a goblin has no raid level");
		assertSame(enraged, MonsterMechanics.atToaInvocation(enraged, 0),
			"level 0 on an unfactored row stays the same instance");
		Monster rescaled = enraged.withToaInvocation(300).withToaInvocation(150);
		assertEquals(150, rescaled.toaLevel(), "SET semantics");
		assertEquals(0, MonsterMechanics.atToaInvocation(
				enraged.withToaInvocation(300), 0).toaLevel(),
			"cycling the chip back to 0 clears the factor");
	}
}
