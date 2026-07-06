package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class VampyreRulesTest
{
	private static LoadoutData data;

	private static LoadoutData data()
	{
		if (data == null)
		{
			data = new DataService().load();
		}
		return data;
	}

	private static MonsterStats monster(String query)
	{
		return data().searchMonsters(query, 1).get(0);
	}

	private static GearItem byName(String name)
	{
		return data().getGearItems().stream()
			.filter(g -> g.getName().equalsIgnoreCase(name))
			.findFirst()
			.orElseThrow(() -> new AssertionError("missing item: " + name));
	}

	@Test
	public void regularWeaponsCannotDamageVyrewatchSentinels()
	{
		MonsterStats sentinel = monster("vyrewatch sentinel");
		Assert.assertTrue(sentinel.hasAttribute("vampyre3"));
		Assert.assertFalse(VampyreRules.canDamage(sentinel, byName("Abyssal whip")));
		Assert.assertTrue(VampyreRules.canDamage(sentinel, byName("Blisterwood flail")));
		Assert.assertTrue(VampyreRules.canDamage(sentinel, byName("Ivandis flail")));
	}

	@Test
	public void optimizerNeverSuggestsNonVyreWeaponsAgainstSentinels()
	{
		MonsterStats sentinel = monster("vyrewatch sentinel");
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1); // abyssal whip - must NOT be suggested
		OptimizationRequest request = new OptimizationRequest(
			sentinel,
			CombatStyle.MELEE,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			true,
			false,
			new OwnedItems(owned, true),
			RequirementProfile.MAXED,
			5);
		Assert.assertTrue("a whip cannot hit a tier-3 vampyre",
			new LoadoutOptimizer().optimize(data(), request).isEmpty());
	}

	@Test
	public void gameBestAgainstSentinelsUsesAVyreWeapon()
	{
		MonsterStats sentinel = monster("vyrewatch sentinel");
		OptimizationRequest request = new OptimizationRequest(
			sentinel,
			CombatStyle.MELEE,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.ALL_STANDARD,
			true,
			false,
			OwnedItems.EMPTY,
			RequirementProfile.MAXED,
			1);
		List<DpsResult> results = new LoadoutOptimizer().optimize(data(), request);
		Assert.assertFalse(results.isEmpty());
		String weapon = results.get(0).getLoadout().getWeapon().getName().toLowerCase();
		Assert.assertTrue("expected a vyre-baneful weapon, got: " + weapon,
			VampyreRules.canDamage(sentinel, results.get(0).getLoadout().getWeapon()));
	}

	@Test
	public void tormentedDemonRangedBisUsesRealStatGearDespiteGuaranteedHits()
	{
		// Vs guaranteed-hit monsters all accuracy gear ties on DPS; the
		// attack-roll tie-break must still pick the strongest gear, and the
		// Avernic treads combos (wrongly non-standard upstream) must be in.
		MonsterStats td = data().searchMonsters("tormented demon", 1).get(0);
		OptimizationRequest request = new OptimizationRequest(
			td, CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
		DpsResult best = new LoadoutOptimizer().optimize(data(), request).get(0);
		GearItem feet = best.getLoadout().get(com.loadoutlab.data.GearSlot.FEET);
		GearItem hands = best.getLoadout().get(com.loadoutlab.data.GearSlot.HANDS);
		Assert.assertNotNull(feet);
		Assert.assertTrue("expected avernic treads, got " + feet.getName(),
			feet.getName().toLowerCase().startsWith("avernic treads"));
		Assert.assertNotNull(hands);
		Assert.assertEquals("Zaryte vambraces", hands.getName());
	}

	@Test
	public void juvinatesHalveNonSilverDamageOnly()
	{
		MonsterStats juvinate = monster("vampyre juvinate");
		Assert.assertTrue(juvinate.hasAttribute("vampyre2"));
		Assert.assertEquals(0.5, VampyreRules.damageFactor(juvinate, byName("Abyssal whip")), 0.0001);
		Assert.assertEquals(1.0, VampyreRules.damageFactor(juvinate, byName("Blisterwood flail")), 0.0001);
		// Tier 1 and normal monsters: full damage from anything.
		Assert.assertEquals(1.0, VampyreRules.damageFactor(monster("feral vampyre"), byName("Abyssal whip")), 0.0001);
		Assert.assertEquals(1.0, VampyreRules.damageFactor(monster("goblin"), byName("Abyssal whip")), 0.0001);
	}
}
