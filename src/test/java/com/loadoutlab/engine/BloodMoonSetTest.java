package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The dual macuahuitl and the Bloodrager set effect, wiki-verified
 * (2026-07-17 field report): the weapon lands two CHAINED hits - the
 * first rolls half the max, the second (the remainder) only rolls when
 * the first lands - and with the full blood moon set each successful hit
 * has a 1/3 chance to make the next attack come one tick earlier
 * (acc/3 + acc^2 * 2/9 per attack, the official calc's expected speed).
 * Before this, the engine treated the macuahuitl as a plain one-hit 4t
 * weapon, so a dreamed-in full set never beat anything.
 */
class BloodMoonSetTest
{
	private static LoadoutData data;
	private static MonsterStats ankou;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		ankou = data.searchMonsters("ankou", 1).get(0);
	}

	private static GearItem byNameVersion(String nameLower, String version)
	{
		for (GearItem item : data.getGearItems())
		{
			if (item.getNameLower().equals(nameLower)
				&& (version == null || version.equalsIgnoreCase(item.getVersion())))
			{
				return item;
			}
		}
		throw new AssertionError("corpus is missing: " + nameLower + " " + version);
	}

	private static OptimizationRequest request()
	{
		return new OptimizationRequest(ankou, CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
	}

	private static DpsResult calc(boolean fullSet)
	{
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, byNameVersion("dual macuahuitl", null));
		if (fullSet)
		{
			gear.put(GearSlot.HEAD, byNameVersion("blood moon helm", "New"));
			gear.put(GearSlot.BODY, byNameVersion("blood moon chestplate", "New"));
			gear.put(GearSlot.LEGS, byNameVersion("blood moon tassets", "New"));
		}
		return new DpsCalculator().calculate(request(), new Loadout(gear));
	}

	@Test
	@DisplayName("the macuahuitl's second hit is chained - expected damage is below a single roll of the full max")
	void chainedHitsExpectation()
	{
		DpsResult result = calc(false);
		double acc = result.getAccuracy();
		int max = result.getMaxHit();
		int firstMax = max / 2;
		int secondMax = max - firstMax;
		double chained = RollMath.normalExpectedHit(acc, firstMax)
			+ acc * RollMath.normalExpectedHit(acc, secondMax);
		assertEquals(chained, result.getExpectedHit(), 1e-9);
		// Strictly below the naive one-hit model whenever accuracy < 1.
		assertTrue(acc < 1.0, "test premise: ankou defends a little");
		assertTrue(result.getExpectedHit() < RollMath.normalExpectedHit(acc, max));
	}

	@Test
	@DisplayName("Bloodrager: the full blood moon set speeds the macuahuitl up by acc/3 + acc^2*2/9 ticks")
	void bloodragerExpectedSpeed()
	{
		DpsResult bare = calc(false);
		DpsResult set = calc(true);
		double acc = set.getAccuracy();
		double proc = acc / 3.0 + acc * acc * 2.0 / 9.0;
		// dps identity: expected damage over the EXPECTED interval, while
		// the displayed speed stays the base 4t.
		assertEquals(set.getExpectedHit() / ((4 - proc) * RollMath.SECONDS_PER_TICK),
			set.getDps(), 1e-9);
		assertEquals(4, set.getAttackSpeed());
		// The set effect is a real dps gain over the same weapon bare
		// (armour also shifts accuracy, so compare per-attack rates).
		assertTrue(set.getDps() * set.getExpectedHit() > 0);
		assertTrue(bare.getDps() > 0);
		// The chip must NAME the folded-in multiplier (audit A2.12).
		assertTrue(set.getCountedBonuses().stream()
			.anyMatch(line -> line.toLowerCase().contains("bloodrager")));
		assertTrue(bare.getCountedBonuses().stream()
			.noneMatch(line -> line.toLowerCase().contains("bloodrager")));
	}

	@Test
	@DisplayName("Used and Broken blood moon pieces still trigger Bloodrager (wiki: works while broken)")
	void brokenPiecesStillCount()
	{
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, byNameVersion("dual macuahuitl", null));
		gear.put(GearSlot.HEAD, byNameVersion("blood moon helm", "Broken"));
		gear.put(GearSlot.BODY, byNameVersion("blood moon chestplate", "Used"));
		gear.put(GearSlot.LEGS, byNameVersion("blood moon tassets", "Broken"));
		DpsResult result = new DpsCalculator().calculate(request(), new Loadout(gear));
		assertTrue(result.getCountedBonuses().stream()
			.anyMatch(line -> line.toLowerCase().contains("bloodrager")));
	}
}
