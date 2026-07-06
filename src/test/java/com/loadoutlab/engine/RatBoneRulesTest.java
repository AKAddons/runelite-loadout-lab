package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class RatBoneRulesTest
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

	private static OptimizationRequest request(MonsterStats monster, CombatStyle style)
	{
		return new OptimizationRequest(
			monster, style, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
	}

	private static DpsResult calculate(MonsterStats monster, CombatStyle style, int weaponId)
	{
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, data().getGear(weaponId));
		return new DpsCalculator().calculate(request(monster, style), new Loadout(gear));
	}

	@Test
	public void boneStaffCannotBeUsedAgainstNonRats()
	{
		MonsterStats goblin = data().searchMonsters("goblin", 1).get(0);
		Assert.assertNull(calculate(goblin, CombatStyle.MAGIC, 28796));
		// And a player who owns one is never told to bring it.
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(28796, 1);
		OptimizationRequest ownedMagic = new OptimizationRequest(
			goblin, CombatStyle.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), RequirementProfile.MAXED, 5);
		for (DpsResult result : new LoadoutOptimizer().optimize(data(), ownedMagic))
		{
			Assert.assertNotEquals(28796, result.getLoadout().getWeapon().getId());
		}
	}

	@Test
	public void boneStaffHitsThirtyEightAgainstScurriusAtMaxed()
	{
		MonsterStats scurrius = data().searchMonsters("scurrius", 1).get(0);
		Assert.assertTrue(scurrius.hasAttribute("rat"));
		// Prayer-less, to match the wiki's max-hit table exactly.
		OptimizationRequest request = new OptimizationRequest(
			scurrius, CombatStyle.MAGIC, PlayerLevels.MAXED,
			PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, data().getGear(28796));
		DpsResult result = new DpsCalculator().calculate(request, new Loadout(gear));
		Assert.assertNotNull(result);
		// Wiki max-hit table: 38 at 99 Magic (trident scaling + rat bonus).
		Assert.assertEquals(38, result.getMaxHit());
	}

	@Test
	public void boneMaceGetsTheRatBonusOnlyAgainstRats()
	{
		MonsterStats scurrius = data().searchMonsters("scurrius", 1).get(0);
		MonsterStats goblin = data().searchMonsters("goblin", 1).get(0);
		Assert.assertEquals(10, RatBoneRules.flatMaxHitBonus(scurrius, data().getGear(28792)));
		Assert.assertEquals(0, RatBoneRules.flatMaxHitBonus(goblin, data().getGear(28792)));
		Assert.assertFalse(RatBoneRules.canUse(goblin, data().getGear(28792)));
	}

	@Test
	public void gameBestMagicAgainstNonRatsIsNeverTheBoneStaff()
	{
		MonsterStats goblin = data().searchMonsters("goblin", 1).get(0);
		List<DpsResult> results = new LoadoutOptimizer().optimize(data(), request(goblin, CombatStyle.MAGIC));
		Assert.assertFalse(results.isEmpty());
		Assert.assertNotEquals(28796, results.get(0).getLoadout().getWeapon().getId());
	}
}
