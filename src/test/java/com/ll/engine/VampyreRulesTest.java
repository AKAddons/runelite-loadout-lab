package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class VampyreRulesTest {
	private static Tables data;

	private static Tables data() {
		if (data == null) {
			data = new DataService().load();
		}
		return data;
	}

	private static Monster monster(String query) {
		return data().searchMonsters(query, 1).get(0);
	}

	private static Gear byName(String name) {
		return data().getGearItems().stream()
			.filter(g -> g.getName().equalsIgnoreCase(name))
			.findFirst()
			.orElseThrow(() -> new AssertionError("missing item: " + name));
	}

	@Test
	public void regularWeaponsCannotDamageVyrewatchSentinels() {
		Monster sentinel = monster("vyrewatch sentinel");
		Assert.assertTrue(sentinel.hasAttribute("vampyre3"));
		Assert.assertFalse(VampyreRules.canDamage(sentinel, Style.MELEE, byName("Abyssal whip")));
		Assert.assertTrue(VampyreRules.canDamage(sentinel, Style.MELEE, byName("Blisterwood flail")));
		Assert.assertTrue(VampyreRules.canDamage(sentinel, Style.MELEE, byName("Ivandis flail")));
	}

	@Test
	public void optimizerNeverSuggestsNonVyreWeaponsAgainstSentinels() {
		Monster sentinel = monster("vyrewatch sentinel");
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1); // abyssal whip - must NOT be suggested
		Req request = new Req(
			sentinel,
			Style.MELEE,
			Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			true,
			false,
			new Owned(owned, true),
			RequirementProfile.MAXED,
			5);
		Assert.assertTrue("a whip cannot hit a tier-3 vampyre",
			new LoadoutOptimizer().optimize(data(), request).isEmpty());
	}

	@Test
	public void gameBestAgainstSentinelsUsesAVyreWeapon() {
		Monster sentinel = monster("vyrewatch sentinel");
		Req request = new Req(
			sentinel,
			Style.MELEE,
			Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED),
			null,
			0,
			CandidateMode.ALL_STANDARD,
			true,
			false,
			Owned.EMPTY,
			RequirementProfile.MAXED,
			1);
		List<Dps> results = new LoadoutOptimizer().optimize(data(), request);
		Assert.assertFalse(results.isEmpty());
		String weapon = results.get(0).getLoadout().getWeapon().getName().toLowerCase();
		Assert.assertTrue("expected a vyre-baneful weapon, got: " + weapon,
			VampyreRules.canDamage(sentinel, Style.MELEE, results.get(0).getLoadout().getWeapon()));
	}

	@Test
	public void tormentedDemonRangedBisUsesRealStatGearDespiteGuaranteedHits() {
		// Vs guaranteed-hit monsters all accuracy gear ties on DPS; the
		// attack-roll tie-break must still pick the strongest gear, and the
		// Avernic treads combos (wrongly non-standard upstream) must be in.
		Monster td = data().searchMonsters("tormented demon", 1).get(0);
		Req request = new Req(
			td, Style.RANGED, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 1);
		Dps best = new LoadoutOptimizer().optimize(data(), request).get(0);
		Gear feet = best.getLoadout().get(com.ll.data.Slot.FEET);
		Gear hands = best.getLoadout().get(com.ll.data.Slot.HANDS);
		Assert.assertNotNull(feet);
		Assert.assertTrue("expected avernic treads, got " + feet.getName(),
			feet.getName().toLowerCase().startsWith("avernic treads"));
		Assert.assertNotNull(hands);
		Assert.assertEquals("Zaryte vambraces", hands.getName());
	}

	/** The 2026-07-05 rule halved non-silver weapons vs tier 2; the
	 * official calc makes them immune unless Efaritay's aid is worn
	 * (ISS-01M5RM2A7FFR9XWKATPX877TDD - VampyreParityTest pins the maths). */
	@Test
	public void juvinatesNeedSilverAVampyrebaneWeaponOrEfaritaysAid() {
		Monster juvinate = monster("vampyre juvinate");
		Assert.assertTrue(juvinate.hasAttribute("vampyre2"));
		Assert.assertTrue(VampyreRules.immune(juvinate, Style.MELEE, wearing("Abyssal whip")));
		Assert.assertFalse(VampyreRules.immune(juvinate, Style.MELEE, wearing("Abyssal whip", "Efaritay's aid")));
		Assert.assertFalse(VampyreRules.immune(juvinate, Style.MELEE, wearing("Blisterwood flail")));
		Assert.assertFalse(VampyreRules.immune(juvinate, Style.MELEE, wearing("Silverlight")));
		// Tier 1 and normal monsters: anything lands.
		Assert.assertFalse(VampyreRules.immune(monster("feral vampyre"), Style.MELEE, wearing("Abyssal whip")));
		Assert.assertFalse(VampyreRules.immune(monster("goblin"), Style.MELEE, wearing("Abyssal whip")));
	}

	private static Kit wearing(String... names) {
		Map<com.ll.data.Slot, Gear> gear = new java.util.EnumMap<>(com.ll.data.Slot.class);
		for (String name : names) {
			Gear item = byName(name);
			gear.put(item.getSlot(), item);
		}
		return new Kit(gear);
	}
}
