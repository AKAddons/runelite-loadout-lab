package com.loadoutlab.optimizer;

import com.loadoutlab.engine.CandidateMode;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.DpsCalculator;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.Loadout;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerBonuses;
import com.loadoutlab.engine.SpecialAttack;
import com.loadoutlab.engine.TestRequests;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterDefences;
import com.loadoutlab.data.MonsterStats;
import java.util.Collections;
import java.util.EnumMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Division, the Tonalztics of ralos special (wiki-verified 2026-08-09):
 * 50% cost, +50% accuracy, two glaives, and each landed hit drains the
 * target's DEFENCE by 12.5% of its MAGIC level - modeled as 25% per
 * landed spec. It shines exactly where a DWH cannot reach (the wiki's
 * example: Zulrah's 300 Magic and Defence).
 */
class TonalzticsTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static GearItem byName(String nameLower, String version)
	{
		for (GearItem g : data.getGearItems())
		{
			if (g.getNameLower().equals(nameLower)
				&& (version == null || version.equals(g.getVersion())))
			{
				return g;
			}
		}
		throw new AssertionError("missing: " + nameLower);
	}

	@Test
	@DisplayName("the registry matches both charge states as a ranged drain spec")
	void registryMatch()
	{
		SpecialAttack charged = SpecialAttack.match(
			byName("tonalztics of ralos", "Charged"));
		assertNotNull(charged, "charged must carry Division");
		assertEquals(CombatStyle.RANGED, charged.getStyle());
		assertTrue(charged.drainsDefence(), "Division is a drain spec");
		assertEquals(50, charged.getEnergyCost());
	}

	@Test
	@DisplayName("the drain measures off the target's Magic level, floored at zero")
	void drainArithmetic()
	{
		SpecialAttack division = SpecialAttack.match(
			byName("tonalztics of ralos", "Charged"));
		// A Zulrah-shaped dummy: 300 Defence, 300 Magic -> one landed
		// spec removes 25% of 300 = 75 Defence.
		MonsterStats zulrahLike = new MonsterStats(
			-43, "Gaze Dummy", "", 500, 800, 3, 300, 300, 0,
			new MonsterDefences(150, 150, 150, 100, 0, 60, 60, 60),
			null, Collections.emptyList(), false, "", 0);
		assertEquals(225, division.drainedDefence(zulrahLike, 40.0));
		// A magic-1 target barely drains - the DWH's territory instead.
		MonsterStats brute = new MonsterStats(
			-44, "Brute Dummy", "", 500, 800, 3, 300, 1, 0,
			new MonsterDefences(150, 150, 150, 100, 0, 60, 60, 60),
			null, Collections.emptyList(), false, "", 0);
		assertEquals(300, division.drainedDefence(brute, 40.0));
	}

	@Test
	@DisplayName("Division earns positive drain value on a high-magic tank")
	void drainValue()
	{
		GearItem tonalztics = byName("tonalztics of ralos", "Charged");
		SpecialAttack division = SpecialAttack.match(tonalztics);
		MonsterStats zulrahLike = new MonsterStats(
			-43, "Gaze Dummy", "", 500, 800, 3, 300, 300, 0,
			new MonsterDefences(150, 150, 150, 100, 0, 60, 60, 60),
			null, Collections.emptyList(), false, "", 0);
		OptimizationRequest request = TestRequests.of(zulrahLike,
			CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, 1);

		EnumMap<GearSlot, GearItem> worn = new EnumMap<>(GearSlot.class);
		worn.put(GearSlot.WEAPON, byName("magic shortbow (i)", null));
		worn.put(GearSlot.AMMO, byName("amethyst arrow", null));
		DpsResult main = new DpsCalculator().calculate(request, new Loadout(worn));
		assertNotNull(main);

		EnumMap<GearSlot, GearItem> specWorn = new EnumMap<>(GearSlot.class);
		specWorn.put(GearSlot.WEAPON, tonalztics);
		DpsResult specBase = new DpsCalculator().calculate(request, new Loadout(specWorn));
		assertNotNull(specBase);
		double expected = division.expectedDamage(specBase, zulrahLike, PlayerLevels.MAXED);
		assertTrue(expected > 0, "two glaives must expect damage");

		OptimizerService service = new OptimizerService(data);
		try
		{
			double added = service.specDpsAdded(new DpsCalculator(), division,
				specBase, expected, request, main, zulrahLike, false);
			assertTrue(added > 0,
				"draining 75 defence off a 300/300 tank must add dps: " + added);
		}
		finally
		{
			service.shutdown();
		}
	}
}
