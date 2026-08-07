package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterGroups;
import com.loadoutlab.data.MonsterStats;
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
class ToaInvocationTest
{
	private static LoadoutData data;
	private static MonsterStats enraged;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		enraged = data.searchMonsters("tumeken's warden", 8).stream()
			.filter(m -> "Enraged".equals(m.getVersion()))
			.findFirst().orElseThrow();
		assertTrue(MonsterMechanics.isToaInvocationScaled(enraged));
	}

	private static GearItem byName(String nameLower, String versionLower)
	{
		for (GearItem g : data.getGearItems())
		{
			if (g.getNameLower().equals(nameLower)
				&& (versionLower == null || g.getVersionLower().equals(versionLower)))
			{
				return g;
			}
		}
		throw new AssertionError("corpus is missing: " + nameLower);
	}

	private static OptimizationRequest req(MonsterStats monster, CandidateMode mode,
		OwnedItems owned)
	{
		return TestRequests.of(monster, CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			mode, true, false, owned, 1);
	}

	@Test
	@DisplayName("the defence roll scales by exactly (250+invocation)/250, truncated")
	void defenceRollScalesExactly()
	{
		java.util.EnumMap<com.loadoutlab.data.GearSlot, GearItem> gear =
			new java.util.EnumMap<>(com.loadoutlab.data.GearSlot.class);
		gear.put(com.loadoutlab.data.GearSlot.WEAPON, byName("bow of faerdhinen", "charged"));
		Loadout bofa = new Loadout(gear);

		DpsResult at0 = new DpsCalculator().calculate(
			req(enraged, CandidateMode.ALL_STANDARD, OwnedItems.EMPTY), bofa);
		DpsResult at300 = new DpsCalculator().calculate(
			req(enraged.withToaInvocation(300), CandidateMode.ALL_STANDARD, OwnedItems.EMPTY), bofa);
		assertEquals(at0.getDefenceRoll() * 550 / 250, at300.getDefenceRoll(),
			"long math, multiply before divide - matches the official trackFactor");
		assertEquals(at0.getAttackRoll(), at300.getAttackRoll(),
			"invocation touches the DEFENCE side only");
		assertTrue(at300.getAccuracy() < at0.getAccuracy());
	}

	@Test
	@DisplayName("the field report both ways: blowpipe wins invocation 0, bowfa wins 300")
	void bowfaOvertakesTheBlowpipeAtRealRaidLevels()
	{
		Map<Integer, Integer> owned = new HashMap<>();
		for (GearItem g : new GearItem[]{
			byName("toxic blowpipe", "charged"), byName("bow of faerdhinen", "charged"),
			byName("crystal helm", "active"), byName("crystal body", "active"),
			byName("crystal legs", "active"),
			byName("masori mask (f)", null), byName("masori body (f)", null),
			byName("masori chaps (f)", null), byName("necklace of anguish", null),
			byName("zaryte vambraces", null), byName("dragon dart", "unpoisoned")})
		{
			owned.put(g.getId(), 10000);
		}
		OwnedItems bank = new OwnedItems(owned, true);

		List<DpsResult> at0 = new LoadoutOptimizer().optimize(data,
			req(enraged, CandidateMode.OWNED_ONLY, bank));
		assertFalse(at0.isEmpty());
		assertTrue(at0.get(0).getLoadout().getWeapon().getNameLower().startsWith("toxic blowpipe"),
			"invocation 0: the blowpipe genuinely edges it, was: "
				+ at0.get(0).getLoadout().getWeapon().label());

		List<DpsResult> at300 = new LoadoutOptimizer().optimize(data,
			req(enraged.withToaInvocation(300), CandidateMode.OWNED_ONLY, bank));
		assertFalse(at300.isEmpty());
		assertTrue(at300.get(0).getLoadout().getWeapon().getNameLower().startsWith("bow of faerdhinen"),
			"invocation 300: the scaling flips it to the bowfa, was: "
				+ at300.get(0).getLoadout().getWeapon().label());
	}

	@Test
	@DisplayName("the group's synthetic warden phases scale too (profileId mapping)")
	void syntheticVariantsScale()
	{
		MonsterStats p2 = MonsterGroups.load(data).stream()
			.filter(g -> "Tombs of Amascut".equals(g.getName()))
			.flatMap(g -> g.getMobs().stream())
			.filter(m -> m.getId() >= MonsterStats.SYNTHETIC_ID_BASE)
			.findFirst().orElseThrow();
		assertTrue(MonsterMechanics.isToaInvocationScaled(p2),
			"immuneVariant rows carry synthetic ids - profileId maps them back");
		assertEquals(300,
			MonsterMechanics.atToaInvocation(p2, 300).getToaInvocationLevel());
	}

	@Test
	@DisplayName("non-ToA monsters and level 0 are no-ops; re-applying replaces, never compounds")
	void scalingIsScopedAndSetNotCompounded()
	{
		MonsterStats goblin = data.searchMonsters("goblin", 1).get(0);
		assertSame(goblin, MonsterMechanics.atToaInvocation(goblin, 300),
			"a goblin has no raid level");
		assertSame(enraged, MonsterMechanics.atToaInvocation(enraged, 0),
			"level 0 on an unfactored row stays the same instance");
		MonsterStats rescaled = enraged.withToaInvocation(300).withToaInvocation(150);
		assertEquals(150, rescaled.getToaInvocationLevel(), "SET semantics");
		assertEquals(0, MonsterMechanics.atToaInvocation(
				enraged.withToaInvocation(300), 0).getToaInvocationLevel(),
			"cycling the chip back to 0 clears the factor");
	}
}
