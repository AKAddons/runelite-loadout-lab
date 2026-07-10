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

	/**
	 * Fabricated result with hand-picked rolls. normalAccuracy gives
	 * exact hit chances for these: (1_000_000_000, 1) is ~1,
	 * (1, 0) is exactly 0.5, (0, 1) is exactly 0.
	 */
	private static DpsResult fabricated(GearItem weapon, int maxHit, long attackRoll, long defenceRoll)
	{
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, weapon);
		return new DpsResult(new Loadout(gear), 0, 0, 0, maxHit, 4, "melee: slash",
			attackRoll, defenceRoll);
	}

	@Test
	public void sunspearSpecDealsExactlySeventyPercentOfMaxOnAHit()
	{
		GearItem sunspear = byName("Sunspear");
		SpecialAttack spec = SpecialAttack.match(sunspear, CombatStyle.MELEE);
		Assert.assertNotNull(spec);
		Assert.assertEquals(SpecialAttack.Kind.FIXED_FRACTION, spec.getKind());
		// Guaranteed accuracy: a hit is always exactly 70% of max, no damage roll.
		Assert.assertEquals(0.70 * 40,
			spec.expectedDamage(fabricated(sunspear, 40, 1_000_000_000L, 1L), null, PlayerLevels.MAXED), 1e-5);
		// Exactly half the hits land: half of 70% of max.
		Assert.assertEquals(0.35 * 40,
			spec.expectedDamage(fabricated(sunspear, 40, 1L, 0L), null, PlayerLevels.MAXED), 1e-9);
	}

	@Test
	public void crimsonKistenLandsAllFourRollsAtGuaranteedAccuracyForOneAndAHalfMax()
	{
		GearItem kisten = byName("Crimson kisten");
		SpecialAttack spec = SpecialAttack.match(kisten, CombatStyle.MELEE);
		Assert.assertNotNull(spec);
		Assert.assertEquals(SpecialAttack.Kind.MULTI_ROLL_TIERED, spec.getKind());
		// k = 4 tier: uniform 130-170% of max, mean (0.7 + 0.8) * max.
		Assert.assertEquals(1.5 * 40,
			spec.expectedDamage(fabricated(kisten, 40, 1_000_000_000L, 1L), null, PlayerLevels.MAXED), 1e-5);
	}

	@Test
	public void crimsonKistenDealsNothingWhenEveryRollMisses()
	{
		GearItem kisten = byName("Crimson kisten");
		SpecialAttack spec = SpecialAttack.match(kisten, CombatStyle.MELEE);
		Assert.assertNotNull(spec);
		Assert.assertEquals(0.0,
			spec.expectedDamage(fabricated(kisten, 40, 0L, 1L), null, PlayerLevels.MAXED), 1e-9);
	}

	@Test
	public void crimsonKistenMidAccuracyMatchesTheBinomialClosedForm()
	{
		GearItem kisten = byName("Crimson kisten");
		SpecialAttack spec = SpecialAttack.match(kisten, CombatStyle.MELEE);
		Assert.assertNotNull(spec);
		// p = 0.5 exactly: sum over k of C(4,k) p^k (1-p)^(4-k) (0.7 + 0.2k) max
		// = (4*0.9 + 6*1.1 + 4*1.3 + 1*1.5) / 16 * max = 16.9 / 16 * max.
		Assert.assertEquals(16.9 / 16.0 * 40,
			spec.expectedDamage(fabricated(kisten, 40, 1L, 0L), null, PlayerLevels.MAXED), 1e-9);
	}

	@Test
	public void burningClawsFirstCascadeTierAveragesFiveQuartersOfMaxAtGuaranteedAccuracy()
	{
		GearItem claws = byName("Burning claws");
		SpecialAttack spec = SpecialAttack.match(claws, CombatStyle.MELEE);
		Assert.assertNotNull(spec);
		Assert.assertEquals(SpecialAttack.Kind.CASCADE_CLAWS, spec.getKind());
		Assert.assertEquals(1.25 * 40,
			spec.expectedDamage(fabricated(claws, 40, 1_000_000_000L, 1L), null, PlayerLevels.MAXED), 1e-5);
	}

	@Test
	public void burningClawsFullMissStillAveragesOnePointTwo()
	{
		GearItem claws = byName("Burning claws");
		SpecialAttack spec = SpecialAttack.match(claws, CombatStyle.MELEE);
		Assert.assertNotNull(spec);
		// All three rolls miss: 20% 0 / 40% 1 / 40% 2 = 1.2 expected.
		Assert.assertEquals(1.2,
			spec.expectedDamage(fabricated(claws, 40, 0L, 1L), null, PlayerLevels.MAXED), 1e-9);
	}

	@Test
	public void burningClawsMidAccuracyMatchesTheCascadeClosedForm()
	{
		GearItem claws = byName("Burning claws");
		SpecialAttack spec = SpecialAttack.match(claws, CombatStyle.MELEE);
		Assert.assertNotNull(spec);
		// p = 0.5 exactly: 0.5*1.25*40 + 0.25*1.0*40 + 0.125*0.75*40 + 0.125*1.2.
		Assert.assertEquals(0.5 * 1.25 * 40 + 0.25 * 40 + 0.125 * 0.75 * 40 + 0.125 * 1.2,
			spec.expectedDamage(fabricated(claws, 40, 1L, 0L), null, PlayerLevels.MAXED), 1e-9);
	}

	@Test
	public void newAuditSpecWeaponsResolveFromTheRegistry()
	{
		SpecialAttack zgs = SpecialAttack.match(byName("Zamorak godsword"), CombatStyle.MELEE);
		Assert.assertNotNull(zgs);
		Assert.assertEquals("Zamorak godsword", zgs.getDisplayName());
		Assert.assertEquals(SpecialAttack.Kind.SINGLE, zgs.getKind());
		Assert.assertEquals(50, zgs.getEnergyCost());

		SpecialAttack sunspear = SpecialAttack.match(byName("Sunspear"), CombatStyle.MELEE);
		Assert.assertNotNull(sunspear);
		Assert.assertEquals("Sunspear", sunspear.getDisplayName());
		Assert.assertEquals(50, sunspear.getEnergyCost());

		SpecialAttack kisten = SpecialAttack.match(byName("Crimson kisten"), CombatStyle.MELEE);
		Assert.assertNotNull(kisten);
		Assert.assertEquals("Crimson kisten", kisten.getDisplayName());
		Assert.assertEquals(50, kisten.getEnergyCost());

		SpecialAttack burningClaws = SpecialAttack.match(byName("Burning claws"), CombatStyle.MELEE);
		Assert.assertNotNull(burningClaws);
		Assert.assertEquals("Burning claws", burningClaws.getDisplayName());
		Assert.assertEquals(30, burningClaws.getEnergyCost());
	}

	@Test
	public void ornateGraniteMaulSpecCostsFiftyWhileThePlainMaulCostsSixty()
	{
		SpecialAttack ornate = SpecialAttack.match(byName("Granite maul (ornate handle)"), CombatStyle.MELEE);
		SpecialAttack plain = SpecialAttack.match(byName("Granite maul"), CombatStyle.MELEE);
		Assert.assertNotNull(ornate);
		Assert.assertNotNull(plain);
		Assert.assertEquals(SpecialAttack.Kind.EXTRA_ATTACK, ornate.getKind());
		Assert.assertEquals(SpecialAttack.Kind.EXTRA_ATTACK, plain.getKind());
		Assert.assertEquals(50, ornate.getEnergyCost());
		Assert.assertEquals(60, plain.getEnergyCost());
	}

	@Test
	public void accursedSceptreSpecBoostsDamageByHalf()
	{
		GearItem sceptre = byName("Accursed sceptre");
		SpecialAttack spec = SpecialAttack.match(sceptre, CombatStyle.MAGIC);
		Assert.assertNotNull(spec);
		// Rolls (2, 0) with the 1.5x accuracy boost give attack roll 3:
		// hit chance 1 - 2/8 = 0.75 exactly; damage rolls 0..(1.5 * 40).
		Assert.assertEquals(0.75 * ((int) (40 * 1.5)) / 2.0,
			spec.expectedDamage(fabricated(sceptre, 40, 2L, 0L), null, PlayerLevels.MAXED), 1e-9);
	}

	@Test
	public void volatileSpellMaxMatchesTheWikiAnchorsAtEightyFourAndNinetyNineMagic()
	{
		GearItem staff = byName("Volatile nightmare staff");
		SpecialAttack spec = SpecialAttack.match(staff, CombatStyle.MAGIC);
		Assert.assertNotNull(spec);
		DpsResult sureHit = fabricated(staff, 30, 1_000_000_000L, 1L);
		// Level 84: spell max min(floor(58*84/99) + 1, 58) = 50; the staff's
		// 15% magic damage takes it to 57. Expected = mean = 28.5.
		PlayerLevels magic84 = new PlayerLevels(99, 99, 99, 99, 84, 99, 99);
		Assert.assertEquals(57 / 2.0, spec.expectedDamage(sureHit, null, magic84), 1e-4);
		// Level 99: spell max caps at 58; 15% gear takes it to 66. Mean 33.
		Assert.assertEquals(66 / 2.0, spec.expectedDamage(sureHit, null, PlayerLevels.MAXED), 1e-4);
		// A boosted 98 already hits the 58 cap.
		PlayerLevels magic98 = new PlayerLevels(99, 99, 99, 99, 98, 99, 99);
		Assert.assertEquals(66 / 2.0, spec.expectedDamage(sureHit, null, magic98), 1e-4);
	}
}
