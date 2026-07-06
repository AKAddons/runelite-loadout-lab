// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellStats;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.Skill;
import org.junit.Assert;
import org.junit.Test;

public class LoadoutOptimizerTest
{
	@Test
	public void optimizerReturnsLegalStandardGear()
	{
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("zulrah", 1).get(0);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.RANGED,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			10_000_000,
			CandidateMode.BUDGET,
			false,
			false,
			OwnedItems.EMPTY,
			10);

		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertTrue(results.get(0).getDps() > 0.0);
		Assert.assertTrue(results.get(0).getPurchaseCost() <= 10_000_000);
		results.get(0).getLoadout().getGear().values().forEach(item -> Assert.assertTrue(item.isStandardGear()));
	}

	@Test
	public void anyStyleReturnsBestConcreteSetups()
	{
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("zulrah", 1).get(0);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.ANY,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			10_000_000,
			CandidateMode.OWNED_OR_BUDGET,
			false,
			false,
			OwnedItems.EMPTY,
			10);

		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertTrue(results.stream().allMatch(result -> result.getDps() > 0.0));
	}

	@Test
	public void ownedGearDoesNotConsumePurchaseBudget()
	{
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			false,
			new OwnedItems(owned, true),
			10);

		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertEquals(0, results.get(0).getPurchaseCost());
		Assert.assertTrue(results.get(0).getLoadout().getCost() > 0);
	}

	@Test
	public void untradeablesMustBeCachedAndEnabled()
	{
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		owned.put(6570, 1);

		OptimizationRequest disabled = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			false,
			new OwnedItems(owned, true),
			10);
		OptimizationRequest enabled = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			true,
			false,
			new OwnedItems(owned, true),
			10);

		DpsResult withoutUntradeable = new LoadoutOptimizer().optimize(data, disabled).get(0);
		DpsResult withUntradeable = new LoadoutOptimizer().optimize(data, enabled).get(0);
		Assert.assertNull(withoutUntradeable.getLoadout().get(GearSlot.CAPE));
		Assert.assertEquals(6570, withUntradeable.getLoadout().get(GearSlot.CAPE).getId());
	}

	@Test
	public void useMyLevelsBlocksGearAboveCurrentLevel()
	{
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			new PlayerLevels(1, 99, 99, 99, 99, 99, 99),
			PrayerBonuses.NONE,
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			false,
			new OwnedItems(owned, true),
			requirements(1, Collections.emptySet()),
			10);

		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request);
		Assert.assertTrue(results.isEmpty());
	}

	@Test
	public void questRequirementsBlockLockedQuestGear()
	{
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4587, 1);
		OptimizationRequest locked = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			false,
			new OwnedItems(owned, true),
			requirements(99, Collections.emptySet()),
			10);
		Set<String> quests = new HashSet<>();
		quests.add(Quest.MONKEY_MADNESS_I.name());
		OptimizationRequest unlocked = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			false,
			new OwnedItems(owned, true),
			requirements(99, quests),
			10);

		Assert.assertTrue(new LoadoutOptimizer().optimize(data, locked).isEmpty());
		Assert.assertFalse(new LoadoutOptimizer().optimize(data, unlocked).isEmpty());
	}

	@Test
	public void magicAutomaticallyChoosesBestAvailableSpell()
	{
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(1381, 1);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.MAGIC,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			false,
			new OwnedItems(owned, true),
			RequirementProfile.MAXED,
			10);

		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertFalse(results.get(0).getSpellName().isEmpty());
		Assert.assertTrue(results.get(0).getAttackType().startsWith("magic: "));
	}

	@Test
	public void autoMagicUsesPoweredStaffAttackInsteadOfCastingSpell()
	{
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(27277, 1);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.MAGIC,
			PlayerLevels.MAXED,
			PrayerBonuses.NONE,
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			false,
			new OwnedItems(owned, true),
			RequirementProfile.MAXED,
			10);

		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertTrue(results.get(0).getLoadout().getWeapon().getName().contains("Tumeken"));
		Assert.assertTrue(results.get(0).getSpellName().isEmpty());
		Assert.assertEquals(34, results.get(0).getMaxHit());
	}

	@Test
	public void thrownWeaponsDoNotBorrowProjectileAmmoStrength()
	{
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		GearItem knife = data.getGear(22804);
		GearItem javelin = data.getGear(19484);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.RANGED,
			PlayerLevels.MAXED,
			PrayerBonuses.NONE,
			null,
			0,
			CandidateMode.ALL_STANDARD,
			false,
			false,
			OwnedItems.EMPTY,
			RequirementProfile.MAXED,
			10);
		EnumMap<GearSlot, GearItem> knifeOnly = new EnumMap<>(GearSlot.class);
		knifeOnly.put(GearSlot.WEAPON, knife);
		EnumMap<GearSlot, GearItem> knifeWithJavelin = new EnumMap<>(GearSlot.class);
		knifeWithJavelin.put(GearSlot.WEAPON, knife);
		knifeWithJavelin.put(GearSlot.AMMO, javelin);

		DpsCalculator calculator = new DpsCalculator();
		Assert.assertEquals(
			calculator.calculate(request, new Loadout(knifeOnly)).getMaxHit(),
			calculator.calculate(request, new Loadout(knifeWithJavelin)).getMaxHit());
	}

	@Test
	public void huntersSunlightCrossbowOnlyUsesAntlerBolts()
	{
		LoadoutData data = new DataService().load();
		GearItem crossbow = data.getGear(28869);
		GearItem sunlightBolts = data.getGear(28872);
		GearItem moonlightBolts = data.getGear(28878);
		GearItem runiteBolts = data.getGear(9144);

		Assert.assertFalse(RangedAmmo.compatible(null, crossbow));
		Assert.assertTrue(RangedAmmo.compatible(sunlightBolts, crossbow));
		Assert.assertTrue(RangedAmmo.compatible(moonlightBolts, crossbow));
		Assert.assertFalse(RangedAmmo.compatible(runiteBolts, crossbow));
	}

	@Test
	public void magicDamageBonusUsesTenthsOfAPercent()
	{
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		SpellStats earthSurge = data.getSpells().stream()
			.filter(spell -> spell.getName().equals("Earth Surge"))
			.findFirst()
			.orElseThrow(AssertionError::new);
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, data.getGear(11791));
		gear.put(GearSlot.HEAD, data.getGear(26241));
		gear.put(GearSlot.NECK, data.getGear(12002));
		gear.put(GearSlot.BODY, data.getGear(26243));
		gear.put(GearSlot.LEGS, data.getGear(26245));
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.MAGIC,
			PlayerLevels.MAXED,
			PrayerBonuses.NONE,
			earthSurge,
			0,
			CandidateMode.ALL_STANDARD,
			false,
			false,
			OwnedItems.EMPTY,
			RequirementProfile.MAXED,
			10);

		Assert.assertEquals(28, new DpsCalculator().calculate(request, new Loadout(gear)).getMaxHit());
	}

	@Test
	public void autoMagicDoesNotUseDemonbaneOnNonDemon()
	{
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.MAGIC,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			10_000_000,
			CandidateMode.OWNED_OR_BUDGET,
			false,
			false,
			OwnedItems.EMPTY,
			RequirementProfile.MAXED,
			10);

		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertFalse(results.get(0).getSpellName().contains("Demonbane"));
	}

	@Test
	public void superCombatBoostRaisesMeleeDps()
	{
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		PlayerLevels base = new PlayerLevels(70, 70, 70, 70, 70, 70, 70);
		OptimizationRequest normal = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			base,
			PrayerBonuses.NONE,
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			false,
			new OwnedItems(owned, true),
			RequirementProfile.MAXED,
			10);
		OptimizationRequest boosted = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			base.boosted(BoostProfile.SUPER_COMBAT, base),
			PrayerBonuses.NONE,
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			false,
			new OwnedItems(owned, true),
			RequirementProfile.MAXED,
			10);

		Assert.assertTrue(new LoadoutOptimizer().optimize(data, boosted).get(0).getDps()
			> new LoadoutOptimizer().optimize(data, normal).get(0).getDps());
	}

	@Test
	public void bestAvailablePrayersUseExactRangedAndMagicFactors()
	{
		PrayerBonuses prayers = PrayerBonuses.bestAvailable(PlayerLevels.MAXED);
		Assert.assertEquals(1.20, prayers.getRangedAccuracy(), 0.00001);
		Assert.assertEquals(1.23, prayers.getRangedStrength(), 0.00001);
		Assert.assertEquals(1.25, prayers.getMagicAccuracy(), 0.00001);
		Assert.assertEquals(4.0, prayers.getMagicDamagePercent(), 0.00001);
	}

	@Test
	public void slayerHeadIsIgnoredForNonSlayerMonster()
	{
		LoadoutData data = new DataService().load();
		// Abomination is not an assignable slayer monster. (Goblins ARE -
		// the upstream fixture used one and only passed because the old
		// snapshot had is_slayer_monster stripped from the data.)
		MonsterStats monster = data.searchMonsters("abomination", 1).get(0);
		Assert.assertFalse(monster.isSlayerMonster());
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		owned.put(8901, 1);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			true,
			new OwnedItems(owned, true),
			RequirementProfile.MAXED,
			10);

		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertNull(results.get(0).getLoadout().get(GearSlot.HEAD));
	}

	@Test
	public void slayerTaskCanChooseMeleeSlayerHead()
	{
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("aberrant spectre", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		owned.put(8901, 1);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			true,
			new OwnedItems(owned, true),
			RequirementProfile.MAXED,
			10);

		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertNotNull(results.get(0).getLoadout().get(GearSlot.HEAD));
		Assert.assertTrue(results.get(0).getLoadout().get(GearSlot.HEAD).isSlayerHead());
	}

	@Test
	public void slayerTaskCanChooseImbuedRangedSlayerHead()
	{
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("aberrant spectre", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(861, 1);
		owned.put(11864, 1);
		owned.put(11865, 1);
		// Fix over upstream best-dps: the magic shortbow (861) requires ammo, and the
		// upstream test owned no arrows, so no ranged loadout could ever be produced.
		// Own amethyst arrows (21326) so the ammo-requiring weapon yields loadouts.
		owned.put(21326, 100);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.RANGED,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			true,
			true,
			new OwnedItems(owned, true),
			RequirementProfile.MAXED,
			10);

		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertNotNull(results.get(0).getLoadout().get(GearSlot.HEAD));
		Assert.assertTrue(results.get(0).getLoadout().get(GearSlot.HEAD).isImbuedSlayerHead());
	}

	@Test
	public void slayerTaskCanChooseImbuedMagicSlayerHead()
	{
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("aberrant spectre", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(1381, 1);
		owned.put(11865, 1);
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.MAGIC,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			true,
			true,
			new OwnedItems(owned, true),
			RequirementProfile.MAXED,
			10);

		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertNotNull(results.get(0).getLoadout().get(GearSlot.HEAD));
		Assert.assertTrue(results.get(0).getLoadout().get(GearSlot.HEAD).isImbuedSlayerHead());
	}

	@Test
	public void suggestionsNeverUseOrnamentVariants()
	{
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		// Own ONLY the ornamented whip: the canonicalized ownership credits
		// the base whip, and the suggestion shows the base version.
		Map<Integer, Integer> owned = data.canonicalizeOwned(Map.of(12773, 1));
		OptimizationRequest request = new OptimizationRequest(
			monster,
			CombatStyle.MELEE,
			PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED),
			null,
			0,
			CandidateMode.OWNED_ONLY,
			false,
			false,
			new OwnedItems(owned, true),
			RequirementProfile.MAXED,
			5);

		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertEquals("Abyssal whip", results.get(0).getLoadout().getWeapon().getName());
		for (DpsResult result : results)
		{
			for (GearItem item : result.getLoadout().getGear().values())
			{
				Assert.assertFalse("variant suggested: " + item.getName(), data.isVariant(item.getId()));
			}
		}
	}

	@Test
	public void allStandardIncludesUnownedUntradeables()
	{
		// Fix over upstream best-dps: ALL_STANDARD is the "game best" ceiling
		// query - untradeables (fire cape etc.) must count without ownership,
		// or the ceiling silently means "best tradeable set".
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		OptimizationRequest request = new OptimizationRequest(
			monster,
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

		List<DpsResult> results = new LoadoutOptimizer().optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		GearItem cape = results.get(0).getLoadout().get(GearSlot.CAPE);
		Assert.assertNotNull(cape);
		Assert.assertFalse("the best melee cape in the game is untradeable", cape.isTradeable());
	}

	@Test
	public void dpsNeutralSlotsFillWithPrayerGearWithoutChangingDps()
	{
		LoadoutData data = new DataService().load();
		MonsterStats monster = data.searchMonsters("goblin", 1).get(0);
		GearItem blessing = data.getGearItems().stream()
			.filter(g -> g.getName().equalsIgnoreCase("Holy blessing"))
			.findFirst().orElseThrow(AssertionError::new);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);            // whip - the DPS pick
		owned.put(blessing.getId(), 1); // +1 prayer, zero offence
		OptimizationRequest request = new OptimizationRequest(
			monster,
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
			3);

		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		List<DpsResult> results = optimizer.optimize(data, request);
		Assert.assertFalse(results.isEmpty());
		DpsResult bare = results.get(0);
		Assert.assertNull("a blessing adds no DPS, so the search leaves ammo empty",
			bare.getLoadout().get(GearSlot.AMMO));

		DpsResult filled = optimizer.fillDpsNeutralSlots(data, request, bare);
		Assert.assertEquals(blessing.getId(), filled.getLoadout().get(GearSlot.AMMO).getId());
		Assert.assertEquals(bare.getDps(), filled.getDps(), 1e-9);
	}

	private static RequirementProfile requirements(int level, Set<String> quests)
	{
		EnumMap<Skill, Integer> levels = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			if (skill != Skill.OVERALL)
			{
				levels.put(skill, level);
			}
		}
		return new RequirementProfile(levels, quests);
	}
}
