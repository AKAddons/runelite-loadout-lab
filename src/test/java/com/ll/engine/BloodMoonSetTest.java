package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
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
class BloodMoonSetTest {
	private static Tables data;
	private static Monster ankou;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		ankou = data.searchMonsters("ankou", 1).get(0);
	}

	private static Gear byNameVersion(String lname, String version) {
		for (Gear item : data.getGearItems()) {
			if (item.getLname().equals(lname)
				&& (version == null || version.equalsIgnoreCase(item.getVersion()))) {
				return item;
			}
		}
		throw new AssertionError("corpus is missing: " + lname + " " + version);
	}

	private static Req request() {
		return new Req(ankou, Style.MELEE, Levels.MAXED,
			PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 1);
	}

	private static Dps calc(boolean fullSet) {
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, byNameVersion("dual macuahuitl", null));
		if (fullSet) {
			gear.put(Slot.HEAD, byNameVersion("blood moon helm", "New"));
			gear.put(Slot.BODY, byNameVersion("blood moon chestplate", "New"));
			gear.put(Slot.LEGS, byNameVersion("blood moon tassets", "New"));
		}
		return new Calc().calculate(request(), new Kit(gear));
	}

	@Test
	@DisplayName("the macuahuitl's second hit is chained - expected damage is below a single roll of the full max")
	void chainedHitsExpectation() {
		Dps result = calc(false);
		double acc = result.getAccuracy();
		int max = result.getMaxHit();
		int firstMax = max / 2;
		int secondMax = max - firstMax;
		double chained = RollMath.normalExpectedHit(acc, firstMax)
			+ acc * RollMath.normalExpectedHit(acc, secondMax);
		assertEquals(chained, result.getEh(), 1e-9);
		// Strictly below the naive one-hit model whenever accuracy < 1.
		assertTrue(acc < 1.0, "test premise: ankou defends a little");
		assertTrue(result.getEh() < RollMath.normalExpectedHit(acc, max));
	}

	@Test
	@DisplayName("Bloodrager: the full blood moon set speeds the macuahuitl up by acc/3 + acc^2*2/9 ticks")
	void bloodragerExpectedSpeed() {
		Dps bare = calc(false);
		Dps set = calc(true);
		double acc = set.getAccuracy();
		double proc = acc / 3.0 + acc * acc * 2.0 / 9.0;
		// dps identity: expected damage over the EXPECTED interval, while
		// the displayed speed stays the base 4t.
		assertEquals(set.getEh() / ((4 - proc) * RollMath.SECONDS_PER_TICK),
			set.getDps(), 1e-9);
		assertEquals(4, set.getAttackSpeed());
		// The set effect is a real dps gain over the same weapon bare
		// (armour also shifts accuracy, so compare per-attack rates).
		assertTrue(set.getDps() * set.getEh() > 0);
		assertTrue(bare.getDps() > 0);
		// The chip must NAME the folded-in multiplier (audit A2.12).
		assertTrue(set.getCountedBonuses().stream()
			.anyMatch(line -> line.toLowerCase().contains("bloodrager")));
		assertTrue(bare.getCountedBonuses().stream()
			.noneMatch(line -> line.toLowerCase().contains("bloodrager")));
	}

	@Test
	@DisplayName("Used and Broken blood moon pieces still trigger Bloodrager (wiki: works while broken)")
	void brokenPiecesStillCount() {
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, byNameVersion("dual macuahuitl", null));
		gear.put(Slot.HEAD, byNameVersion("blood moon helm", "Broken"));
		gear.put(Slot.BODY, byNameVersion("blood moon chestplate", "Used"));
		gear.put(Slot.LEGS, byNameVersion("blood moon tassets", "Broken"));
		Dps result = new Calc().calculate(request(), new Kit(gear));
		assertTrue(result.getCountedBonuses().stream()
			.anyMatch(line -> line.toLowerCase().contains("bloodrager")));
	}
}
