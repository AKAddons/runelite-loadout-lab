package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.MonsterDefences;
import com.loadoutlab.data.MonsterOffence;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.StatBlock;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import org.junit.Assert;
import org.junit.Test;

public class BossIncomingOverridesTest
{
	/** Graardor-shaped stats: atk 280 (+120), str 350 (+43), rng 350, 6t.
	 * The NPC formula derives a ranged max hit of 58 from these. */
	private static MonsterOffence offence(List<String> styles)
	{
		return new MonsterOffence(280, 350, 350, 80,
			120, 43, 100, 40, 0, 0, 6, styles);
	}

	private static MonsterStats named(String name, List<String> styles)
	{
		return new MonsterStats(1, name, "", 600, 255, 4, 250, 80, 0,
			MonsterDefences.ZERO, offence(styles),
			java.util.Collections.emptyList(), false, "", 0);
	}

	private static Loadout armour(int stab, int slash, int crush, int magic, int ranged)
	{
		GearItem body = new GearItem(1, "Test platebody", "", GearSlot.BODY, "", 0,
			false, true, true, true, 0,
			StatBlock.ZERO,
			new StatBlock(stab, slash, crush, magic, ranged, 0, 0, 0, 0),
			StatBlock.ZERO, null);
		return new Loadout(Map.of(GearSlot.BODY, body));
	}

	private static IncomingDpsCalculator.StyleThreat threat(
		IncomingDpsCalculator.Result result, String style)
	{
		for (IncomingDpsCalculator.StyleThreat t : result.threats)
		{
			if (t.style.toLowerCase(Locale.ROOT).equals(style))
			{
				return t;
			}
		}
		Assert.fail("no " + style + " threat in " + result.threats.size() + " threats");
		return null;
	}

	@Test
	public void overrideReplacesTheDerivedMaxHitWithGraardorsScripted35Ranged()
	{
		IncomingDpsCalculator.Result result = IncomingDpsCalculator.calculate(
			named("General Graardor", Arrays.asList("Crush", "Ranged")),
			armour(200, 200, 200, 0, 150), 99, 99);
		Assert.assertTrue(result.fullyModeled);
		Assert.assertNotNull(result.overrideNote);
		Assert.assertEquals(35, threat(result, "ranged").maxHit);
		Assert.assertEquals(60, threat(result, "crush").maxHit);
		// Crush owns 2/3 of the cadence, so it is the prayed style.
		Assert.assertEquals("Protect from Melee", result.protectPrayer);
		Assert.assertTrue(threat(result, "crush").blocked);
	}

	@Test
	public void krakenTypelessMagicAlwaysHitsButPraysToZero()
	{
		// Heavy magic defence on purpose: typeless attacks skip the defence
		// roll, so the armour must NOT reduce the unprayed number - only
		// Protect from Magic (the qualifier's prayer) blocks it, entirely.
		IncomingDpsCalculator.Result result = IncomingDpsCalculator.calculate(
			named("Kraken", Arrays.asList("Typeless Magic")),
			armour(0, 0, 0, 400, 0), 99, 99);
		Assert.assertTrue(result.fullyModeled);
		Assert.assertEquals("Protect from Magic", result.protectPrayer);
		// 28 max at 4t: (28/2) / (4 * 0.6s) - the curated speed, not the
		// sheet's.
		Assert.assertEquals(14.0 / 2.4, result.unprayedDps, 1e-9);
		Assert.assertEquals(0.0, result.totalDps, 1e-9);
		Assert.assertTrue(threat(result, "typeless magic").blocked);
	}

	@Test
	public void partialPierceAttacksAlwaysContributeSomething()
	{
		// Corporeal Beast: magic carries TRUE max 65 with prayerFactor
		// 0.667 - whichever prayer is chosen, the prayed total must equal
		// the per-threat identity sum and stay strictly positive.
		IncomingDpsCalculator.Result result = IncomingDpsCalculator.calculate(
			named("Corporeal Beast", Arrays.asList("Crush", "Magic")),
			armour(200, 200, 200, 0, 150), 99, 99);
		IncomingDpsCalculator.StyleThreat magic = threat(result, "magic");
		Assert.assertEquals(65, magic.maxHit);
		double identity = 0;
		for (IncomingDpsCalculator.StyleThreat threat : result.threats)
		{
			identity += threat.dps * threat.share * (threat.blocked ? threat.prayerFactor : 1);
		}
		Assert.assertEquals(identity, result.totalDps, 1e-9);
		Assert.assertTrue(result.totalDps > 0);
		Assert.assertTrue(result.totalDps < result.unprayedDps);
	}

	@Test
	public void rotationSharesWeightEachStylesContributionToTheTotal()
	{
		IncomingDpsCalculator.Result result = IncomingDpsCalculator.calculate(
			named("General Graardor", Arrays.asList("Crush", "Ranged")),
			armour(200, 200, 200, 0, 150), 99, 99);
		IncomingDpsCalculator.StyleThreat crush = threat(result, "crush");
		IncomingDpsCalculator.StyleThreat ranged = threat(result, "ranged");
		Assert.assertEquals(0.667, crush.share, 1e-9);
		Assert.assertEquals(0.333, ranged.share, 1e-9);
		Assert.assertEquals(crush.dps * 0.667 + ranged.dps * 0.333,
			result.unprayedDps, 1e-9);
		// Crush is blocked, so the total is the ranged share alone.
		Assert.assertEquals(ranged.dps * 0.333, result.totalDps, 1e-9);
	}

	@Test
	public void typelessChipDamageIsModeledAsAlwaysHittingAndNeverBlocked()
	{
		// King Black Dragon: dragonfire (65 and 50) is typeless - accuracy 1
		// against any armour, contributing at its share regardless of prayer.
		IncomingDpsCalculator.Result result = IncomingDpsCalculator.calculate(
			named("King Black Dragon", Arrays.asList("Stab", "Dragonfire")),
			armour(200, 200, 200, 0, 150), 99, 99);
		Assert.assertTrue(result.fullyModeled);
		Assert.assertEquals("Protect from Melee", result.protectPrayer);
		IncomingDpsCalculator.StyleThreat fire = threat(result, "typeless");
		Assert.assertEquals(65, fire.maxHit);
		// Always hits: dps is maxHit/2 per 6t sheet speed with no accuracy term.
		Assert.assertEquals((65 / 2.0) / (6 * 0.6), fire.dps, 1e-9);
		Assert.assertTrue(result.totalDps > 0);
	}

	@Test
	public void monstersWithoutAnOverrideKeepTheUniformV1Model()
	{
		IncomingDpsCalculator.Result result = IncomingDpsCalculator.calculate(
			named("Test Boss", Arrays.asList("Crush", "Ranged")),
			armour(200, 200, 200, 0, 150), 99, 99);
		Assert.assertNull(result.overrideNote);
		// v1 derives the ranged max hit (58) from the stat sheet.
		Assert.assertEquals(58, threat(result, "ranged").maxHit);
		Assert.assertEquals(0.5, threat(result, "crush").share, 1e-9);
	}

	@Test
	public void everySeededBossIsPresentAndItsSharesSumToAtMostOne()
	{
		List<String> expected = Arrays.asList(
			"general graardor", "k'ril tsutsaroth", "kree'arra",
			"commander zilyana", "zulrah", "vorkath", "cerberus", "callisto",
			"artio", "vet'ion", "calvar'ion", "venenatis", "spindel",
			"chaos elemental", "king black dragon", "alchemical hydra",
			"corporeal beast");
		for (String name : expected)
		{
			BossIncomingOverrides.BossOverride override = BossIncomingOverrides
				.overridesFor(named(name, List.of("Melee")));
			Assert.assertNotNull(name, override);
			double shares = 0;
			for (BossIncomingOverrides.Attack attack : override.getAttacks())
			{
				Assert.assertTrue(name, attack.getMaxHit() > 0);
				Assert.assertTrue(name, attack.getShare() > 0);
				if ("typeless".equals(attack.getStyle()))
				{
					Assert.assertFalse(name, attack.isPrayable());
				}
				shares += attack.getShare();
			}
			Assert.assertTrue(name + " shares " + shares, shares <= 1.0 + 1e-9);
		}
		Assert.assertEquals(expected.size(), BossIncomingOverrides.names().size());
	}

	@Test
	public void fullyPrayerPiercingBossesRecommendNoProtectionPrayer()
	{
		// Vet'ion's lightning and shield bash are dodge-based, not prayable:
		// no prayer recommendation, and praying changes nothing.
		IncomingDpsCalculator.Result result = IncomingDpsCalculator.calculate(
			named("Vet'ion", Arrays.asList("Slash", "Magic")),
			armour(200, 200, 200, 0, 150), 99, 99);
		Assert.assertNull(result.protectPrayer);
		Assert.assertEquals(result.unprayedDps, result.totalDps, 1e-9);
		Assert.assertTrue(result.totalDps > 0);
	}

	@Test
	public void partialPrayerPierceCountsTheThroughDamage()
	{
		// Corp's magic: TRUE max 65, Protect from Magic blocks only 1/3
		// (factor 0.667). Prayed total must include the pierce-through;
		// unprayed must use the full 65-based dps.
		LoadoutData data = new DataService().load();
		MonsterStats corp = data.searchMonsters("corporeal beast", 1).get(0);
		// Heavy melee armour + zero magic defence: the magic saving must
		// dominate, so Protect from Magic gets prayed and the pierce shows.
		IncomingDpsCalculator.Result result = IncomingDpsCalculator.calculate(
			corp, tankMeleeArmour(), 99, 99);
		Assert.assertEquals("Protect from Magic", result.protectPrayer);
		Assert.assertTrue(result.totalDps > 0);
		Assert.assertTrue(result.totalDps < result.unprayedDps);
		boolean sawPierce = false;
		for (IncomingDpsCalculator.StyleThreat threat : result.threats)
		{
			if (threat.blocked && threat.prayerFactor > 0)
			{
				sawPierce = true;
				Assert.assertEquals(65, threat.maxHit);
				Assert.assertEquals(0.667, threat.prayerFactor, 1e-9);
			}
		}
		Assert.assertTrue("corp magic must be a partial pierce", sawPierce);
	}

	@Test
	public void prayerChoiceMaximizesSavingsNotRawSize()
	{
		// Callisto: melee 55 but only half-blocked; magic 50 fully
		// blockable. Praying magic SAVES more than praying melee even
		// though the melee hit is bigger - the chosen prayer must be the
		// bigger saving.
		LoadoutData data = new DataService().load();
		MonsterStats callisto = data.searchMonsters("callisto", 1).get(0);
		IncomingDpsCalculator.Result result = IncomingDpsCalculator.calculate(
			callisto, new Loadout(java.util.Map.of()), 99, 99);
		Assert.assertEquals("Protect from Magic", result.protectPrayer);
	}

	private static Loadout tankMeleeArmour()
	{
		com.loadoutlab.data.GearItem body = new com.loadoutlab.data.GearItem(
			99, "Tank platebody", "", com.loadoutlab.data.GearSlot.BODY, "", 0,
			false, true, true, true, 0,
			com.loadoutlab.data.StatBlock.ZERO,
			new com.loadoutlab.data.StatBlock(500, 500, 500, 0, 0, 0, 0, 0, 0),
			com.loadoutlab.data.StatBlock.ZERO, null);
		return new Loadout(java.util.Map.of(com.loadoutlab.data.GearSlot.BODY, body));
	}
}
