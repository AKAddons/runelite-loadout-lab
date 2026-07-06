package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class SpecialAttackTest
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

	private static GearItem byName(String name)
	{
		return data().getGearItems().stream()
			.filter(g -> g.getName().equalsIgnoreCase(name) && g.isStandardGear())
			.findFirst()
			.orElseThrow(() -> new AssertionError("missing item: " + name));
	}

	private static OptimizationRequest request(CombatStyle style)
	{
		MonsterStats monster = data().searchMonsters("goblin", 1).get(0);
		return new OptimizationRequest(
			monster,
			style,
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
	}

	private static DpsResult calculate(CombatStyle style, GearItem... items)
	{
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		for (GearItem item : items)
		{
			gear.put(item.getSlot(), item);
		}
		return new DpsCalculator().calculate(request(style), new Loadout(gear));
	}

	@Test
	public void voidwakerSpecAveragesExactlyTheMaxHit()
	{
		GearItem voidwaker = byName("Voidwaker");
		DpsResult base = calculate(CombatStyle.MELEE, voidwaker);
		SpecialAttack spec = SpecialAttack.match(voidwaker, CombatStyle.MELEE);
		Assert.assertNotNull(spec);
		// Guaranteed hit, uniform 50-150% of max: the mean IS the max hit,
		// independent of the target's defence.
		Assert.assertEquals(base.getMaxHit(),
			spec.expectedDamage(base, request(CombatStyle.MELEE).getMonster(), PlayerLevels.MAXED), 0.0001);
	}

	@Test
	public void dragonDaggerSpecBeatsTwoUnboostedHits()
	{
		GearItem dagger = byName("Dragon dagger");
		DpsResult base = calculate(CombatStyle.MELEE, dagger);
		SpecialAttack spec = SpecialAttack.match(dagger, CombatStyle.MELEE);
		Assert.assertNotNull(spec);
		// Two hits, both accuracy and damage boosted 15%: strictly better
		// than two normal expected hits.
		Assert.assertTrue(spec.expectedDamage(base, request(CombatStyle.MELEE).getMonster(), PlayerLevels.MAXED)
			> 2 * base.getExpectedHit());
	}

	@Test
	public void clawsCascadeApproachesOneAndAHalfMaxAtGuaranteedAccuracy()
	{
		GearItem claws = byName("Dragon claws");
		SpecialAttack spec = SpecialAttack.match(claws, CombatStyle.MELEE);
		Assert.assertNotNull(spec);
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, claws);
		// Fabricated rolls: astronomically accurate -> first cascade tier
		// dominates, expected total ~ 1.5 * max - 1.
		DpsResult sureHit = new DpsResult(new Loadout(gear), 0, 0, 0, 40, 4, "melee: slash",
			1_000_000_000L, 1L);
		double expected = spec.expectedDamage(sureHit, null, PlayerLevels.MAXED);
		Assert.assertEquals(1.5 * 40 - 1, expected, 0.5);
	}

	@Test
	public void magicShortbowImbuedCostsLessThanRegular()
	{
		SpecialAttack imbued = SpecialAttack.match(byName("Magic shortbow (i)"), CombatStyle.RANGED);
		SpecialAttack regular = SpecialAttack.match(byName("Magic shortbow"), CombatStyle.RANGED);
		Assert.assertNotNull(imbued);
		Assert.assertNotNull(regular);
		Assert.assertEquals(50, imbued.getEnergyCost());
		Assert.assertEquals(55, regular.getEnergyCost());
	}

	@Test
	public void sustainedSpecDpsScalesWithRegenAndCost()
	{
		SpecialAttack dds = SpecialAttack.match(byName("Dragon dagger"), CombatStyle.MELEE);
		Assert.assertNotNull(dds);
		// Net 20 damage per spec at 25% cost: 10%/30s regen = 1 spec per 75s.
		Assert.assertEquals(20.0 / 75.0, dds.sustainedDpsBonus(30, 10, false), 1e-9);
		// Lightbearer doubles the regen.
		Assert.assertEquals(2 * 20.0 / 75.0, dds.sustainedDpsBonus(30, 10, true), 1e-9);
		// A spec weaker than the auto it replaces adds nothing.
		Assert.assertEquals(0.0, dds.sustainedDpsBonus(5, 10, false), 1e-9);
	}

	@Test
	public void specWeaponsOnlyMatchTheirOwnStyle()
	{
		Assert.assertNull(SpecialAttack.match(byName("Dragon dagger"), CombatStyle.RANGED));
		Assert.assertNull(SpecialAttack.match(byName("Dark bow"), CombatStyle.MELEE));
		Assert.assertNull(SpecialAttack.match(byName("Abyssal whip"), CombatStyle.MELEE));
	}
}
