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

/**
 * Soul stacks (wiki, mirrored from the official calc): each stack adds
 * 6% of the boosted strength level, flat, AFTER the prayer factor -
 * "does not stack multiplicatively with prayers" - to a cap of +30% at
 * five stacks. Stacks build one per swing (even misses), persist
 * between kills, and only decay after 30s idle, so a sustained grind
 * sits at five stacks; the engine prices that steady state.
 * (Field report 2026-08-09: simmed Soulreaper axe lost to Noxious
 * halberd on Thermy because the axe was priced as a plain axe.)
 */
public class SoulreaperAxeTest
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

	private static GearItem byName(String nameLower)
	{
		return data().getGearItems().stream()
			.filter(g -> g.getNameLower().equals(nameLower) && g.isStandardGear())
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing: " + nameLower));
	}

	@Test
	public void fiveSoulStacksBeatTheNoxiousHalberdOnThermy()
	{
		MonsterStats thermy = data().searchMonsters("thermonuclear smoke devil", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(byName("soulreaper axe").getId(), 1);
		owned.put(byName("noxious halberd").getId(), 1);
		OptimizationRequest request = new OptimizationRequest(
			thermy, CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), RequirementProfile.MAXED, 1);
		List<DpsResult> results = new LoadoutOptimizer().optimize(data(), request);
		Assert.assertFalse(results.isEmpty());
		String weapon = results.get(0).getLoadout().get(GearSlot.WEAPON).labelLower();
		Assert.assertTrue("at 5 stacks the axe outhits the halberd: " + weapon,
			weapon.contains("soulreaper axe"));
	}

	@Test
	public void theStackBonusIsFlatPostPrayerOffTheBoostedLevel()
	{
		// The exact wiki-calc chain: effective strength gains
		// floor(level * 30/100) on top of the prayer-multiplied level -
		// never level * prayer * 1.30 (that would over-price the axe).
		MonsterStats thermy = data().searchMonsters("thermonuclear smoke devil", 1).get(0);
		OptimizationRequest request = new OptimizationRequest(
			thermy, CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, byName("soulreaper axe"));
		DpsResult result = new DpsCalculator().calculate(request, new Loadout(gear));
		Assert.assertNotNull(result);

		int level = request.getLevels().getStrength();
		double prayer = request.getPrayers().getMeleeStrength();
		// The engine reports the winning stance; the axe's strength
		// bonus is 125.
		int stance = stanceBonus(result);
		int expectedEffective = RollMath.effectiveLevel(level, prayer, stance) + level * 30 / 100;
		Assert.assertEquals(RollMath.maxHitFromEffective(expectedEffective, 125),
			result.getMaxHit());
	}

	@Test
	public void otherAxesDoNotInheritTheStackBonus()
	{
		MonsterStats thermy = data().searchMonsters("thermonuclear smoke devil", 1).get(0);
		OptimizationRequest request = new OptimizationRequest(
			thermy, CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, byName("dragon axe"));
		DpsResult result = new DpsCalculator().calculate(request, new Loadout(gear));
		Assert.assertNotNull(result);
		int level = request.getLevels().getStrength();
		double prayer = request.getPrayers().getMeleeStrength();
		int plainEffective = RollMath.effectiveLevel(level, prayer, stanceBonus(result));
		Assert.assertEquals(RollMath.maxHitFromEffective(plainEffective,
				byName("dragon axe").getBonuses().getStrength()),
			result.getMaxHit());
	}

	/** The strength-stance bonus of the variant the engine chose. */
	private static int stanceBonus(DpsResult result)
	{
		return result.getAttackType().contains("aggressive") ? 3
			: result.getAttackType().contains("controlled") ? 1 : 0;
	}
}
