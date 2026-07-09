package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.MonsterDefences;
import com.loadoutlab.data.MonsterOffence;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.StatBlock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class IncomingDpsCalculatorTest
{
	/** Graardor-shaped melee stats: atk 280 (+120), str 350 (+43), 6t. */
	private static MonsterOffence offence(List<String> styles)
	{
		return new MonsterOffence(280, 350, 350, 80,
			120, 43, 100, 40, 0, 0, 6, styles);
	}

	private static MonsterStats monster(List<String> styles)
	{
		return new MonsterStats(1, "Test Boss", "", 600, 255, 4, 250, 80, 0,
			MonsterDefences.ZERO, offence(styles),
			java.util.Collections.emptyList(), false, "", 0);
	}

	/** A single armour piece carrying the whole set's defensive block. */
	private static Loadout armour(int stab, int slash, int crush, int magic, int ranged)
	{
		GearItem body = new GearItem(1, "Test platebody", "", GearSlot.BODY, "", 0,
			false, true, true, true, 0,
			StatBlock.ZERO,
			new StatBlock(stab, slash, crush, magic, ranged, 0, 0, 0, 0),
			StatBlock.ZERO, null);
		return new Loadout(Map.of(GearSlot.BODY, body));
	}

	@Test
	public void singleStyleMonsterIsFullyBlockedByTheProtectionPrayer()
	{
		IncomingDpsCalculator.Result result = IncomingDpsCalculator.calculate(
			monster(List.of("Crush")), armour(200, 200, 200, 0, 150), 99, 99);
		Assert.assertEquals("Protect from Melee", result.protectPrayer);
		Assert.assertEquals(0.0, result.totalDps, 1e-9);
		Assert.assertTrue(result.unprayedDps > 0);
		Assert.assertTrue(result.fullyModeled);
		Assert.assertTrue(result.threats.get(0).blocked);
	}

	@Test
	public void chipStyleContributesItsRotationShareWhilePrayingTheWorst()
	{
		// Hand-computed: crush roll 289*184=53176 vs def (99+9)*(200+64)=28512
		// -> acc 0.73189, max hit (359*107+320)/640=60, 6t
		// -> crush dps 0.73189*30/3.6 = 6.0991.
		// Ranged roll 359*164=58876 vs 108*214=23112 -> acc 0.80373,
		// max (359*104+320)/640=58 -> dps 0.80373*29/3.6 = 6.4745 (worst).
		// Protect Missiles; chip = crush share 6.0991/2 = 3.0495.
		IncomingDpsCalculator.Result result = IncomingDpsCalculator.calculate(
			monster(Arrays.asList("Crush", "Ranged")), armour(200, 200, 200, 0, 150), 99, 99);
		Assert.assertEquals("Protect from Missiles", result.protectPrayer);
		Assert.assertEquals(3.0495, result.totalDps, 0.005);
		// Unprayed adds the blocked ranged share: 3.0495 + 6.4745/2 = 6.2868.
		Assert.assertEquals(6.2868, result.unprayedDps, 0.005);
		Assert.assertEquals(60, result.threats.get(0).maxHit);
	}

	@Test
	public void aPreparedCalculatorMatchesTheFullCalculationExactly()
	{
		// The defense-weighted beam scores thousands of candidate sets per
		// optimize; prepare() hoists the monster-side constants and must
		// reproduce calculate() bit-for-bit on both model paths.
		MonsterStats sheet = monster(Arrays.asList("Crush", "Ranged", "Magic", "Dragonfire"));
		Loadout[] armours = {
			armour(0, 0, 0, 0, 0),
			armour(200, 180, 210, 40, 150),
			armour(-20, 300, 5, 120, 60),
		};
		IncomingDpsCalculator.Prepared prepared = IncomingDpsCalculator.prepare(sheet, 82, 91);
		for (Loadout loadout : armours)
		{
			IncomingDpsCalculator.Result full = IncomingDpsCalculator.calculate(sheet, loadout, 82, 91);
			Assert.assertEquals(full.totalDps, prepared.totalDps(loadout), 0.0);
		}
	}

	@Test
	public void aPreparedCalculatorMatchesOnCuratedOverrideBosses()
	{
		com.loadoutlab.data.LoadoutData data = new com.loadoutlab.data.DataService().load();
		for (String name : new String[]{"Callisto", "Zulrah", "Corporeal Beast"})
		{
			MonsterStats boss = data.searchMonsters(name, 1).get(0);
			IncomingDpsCalculator.Prepared prepared = IncomingDpsCalculator.prepare(boss, 85, 89);
			for (Loadout loadout : new Loadout[]{
				armour(0, 0, 0, 0, 0), armour(220, 240, 230, 80, 200)})
			{
				IncomingDpsCalculator.Result full = IncomingDpsCalculator.calculate(boss, loadout, 85, 89);
				Assert.assertEquals(full.totalDps, prepared.totalDps(loadout), 0.0);
			}
		}
	}

	@Test
	public void betterArmourMeansLessIncomingDps()
	{
		double tank = IncomingDpsCalculator.calculate(
			monster(Arrays.asList("Crush", "Ranged")), armour(300, 300, 300, 0, 300), 99, 99).totalDps;
		double naked = IncomingDpsCalculator.calculate(
			monster(Arrays.asList("Crush", "Ranged")), armour(0, 0, 0, 0, 0), 99, 99).totalDps;
		Assert.assertTrue(tank + " < " + naked, tank < naked);
	}

	@Test
	public void magicDefenceUsesMagicLevelAndMagicDefBonus()
	{
		MonsterStats mage = monster(List.of("Magic", "Ranged"));
		double lowMagic = IncomingDpsCalculator.calculate(mage, armour(0, 0, 0, 0, 0), 99, 1).totalDps;
		double highMagic = IncomingDpsCalculator.calculate(mage, armour(0, 0, 0, 0, 0), 99, 99).totalDps;
		Assert.assertTrue(highMagic <= lowMagic);
		double magicArmour = IncomingDpsCalculator.calculate(mage, armour(0, 0, 0, 120, 0), 99, 99).totalDps;
		Assert.assertTrue(magicArmour < highMagic);
	}

	@Test
	public void unmodeledStylesAreSurfacedNotSilentlyDropped()
	{
		IncomingDpsCalculator.Result result = IncomingDpsCalculator.calculate(
			monster(Arrays.asList("Typeless")), armour(200, 200, 200, 0, 150), 99, 99);
		Assert.assertFalse(result.fullyModeled);
		Assert.assertNull(result.protectPrayer);
		Assert.assertEquals(0.0, result.totalDps, 1e-9);
		Assert.assertFalse(result.threats.get(0).modeled);
	}

	@Test
	public void genericMeleeAssumesTheWeakestDefensiveSide()
	{
		// Generic "Melee" must use min(stab, slash, crush) = 50 here,
		// so it hits harder than a typed Crush attack against crush 300.
		double generic = IncomingDpsCalculator.calculate(
			monster(List.of("Melee", "Ranged")), armour(50, 300, 300, 0, 0), 99, 99).totalDps;
		double typed = IncomingDpsCalculator.calculate(
			monster(List.of("Crush", "Ranged")), armour(50, 300, 300, 0, 0), 99, 99).totalDps;
		Assert.assertTrue(generic >= typed);
	}
}
