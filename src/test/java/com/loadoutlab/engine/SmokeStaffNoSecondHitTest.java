package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import com.loadoutlab.data.SpellStats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The smoke battlestaff and the mystic smoke staff grant the Twinflame's +10%
 * accuracy and damage on standard spells (wiki) - and NOTHING else. Only the
 * Twinflame staff splits a Bolt/Blast/Wave into a second 40% hit. The engine
 * had handed that second hit to the smoke staves too, so an Earth Wave off a
 * smoke battlestaff read ~28% high and the swaps strip called it an upgrade
 * over the Purging staff the optimizer rightly wore (Andrew 2026-10-24: "it
 * literally says smoke battlestaff is better, why isn't it showing it in the
 * sim as my highest dps").
 */
class SmokeStaffNoSecondHitTest {
	@Test
	@DisplayName("a smoke battlestaff's Earth Wave gets the +10% but no second hit; the Twinflame's does, so it hits harder")
	void onlyTheTwinflameSplits() {
		LoadoutData data = new DataService().load();
		Monster giant = data.searchMonsters("fire giant", 1).get(0);
		SpellStats wave = data.getSpells().stream().filter(s -> s.getName().equals("Earth Wave")).findFirst()
			.orElseThrow(() -> new AssertionError("no Earth Wave"));
		Map<Integer, Integer> owned = new HashMap<>();
		for (int rune : new int[]{554, 555, 556, 557, 560, 565, 566, 21880}) {
			owned.put(rune, 10000);
		}
		OptimizationRequest request = new OptimizationRequest(giant, Style.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL), null, 0, CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), RequirementProfile.MAXED, 3).withSpell(wave);

		DpsResult smoke = cast(data, request, "smoke battlestaff");
		DpsResult mystic = cast(data, request, "mystic smoke staff");
		DpsResult twinflame = cast(data, request, "twinflame staff");

		for (DpsResult staff : new DpsResult[]{smoke, mystic}) {
			assertTrue(staff.getCountedBonuses().stream().anyMatch(b -> b.contains("+10% accuracy and damage")),
				"the smoke staves keep the +10%: " + staff.getCountedBonuses());
			assertFalse(staff.getCountedBonuses().stream().anyMatch(b -> b.contains("second hit")),
				"no second hit for a smoke staff: " + staff.getCountedBonuses());
		}
		assertTrue(twinflame.getCountedBonuses().stream().anyMatch(b -> b.contains("second hit")),
			"the Twinflame splits: " + twinflame.getCountedBonuses());
		assertTrue(twinflame.getExpectedHit() > smoke.getExpectedHit() * 1.15,
			"the Twinflame's Wave hits well over a smoke staff's: " + twinflame.getExpectedHit() + " vs " + smoke.getExpectedHit());
		assertEquals(smoke.getMaxHit(), twinflame.getMaxHit(), "same max hit: the split rides on expected damage, not the max");
	}

	private static DpsResult cast(LoadoutData data, OptimizationRequest request, String staffName) {
		Gear staff = data.getGearItems(GearSlot.WEAPON).stream()
			.filter(g -> g.getNameLower().equals(staffName) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + staffName));
		DpsResult result = new DpsCalculator().calculate(request, new Loadout(Map.of(GearSlot.WEAPON, staff)));
		assertTrue(result != null, staffName + " casts Earth Wave");
		return result;
	}
}
