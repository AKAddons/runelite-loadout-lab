package com.ll.engine;

import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.MonsterDefences;
import com.ll.data.MonsterOffence;
import com.ll.data.Monster;
import com.ll.data.Stats;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class IncomingDpsCalculatorTest {
	/** Graardor-shaped melee stats: atk 280 (+120), str 350 (+43), 6t. */
	private static MonsterOffence offence(List<String> styles) {
		return new MonsterOffence(280, 350, 350, 80,
			120, 43, 100, 40, 0, 0, 6, styles);
	}

	private static Monster monster(List<String> styles) {
		return new Monster(1, "Test Boss", "", 600, 255, 4, 250, 80, 0,
			MonsterDefences.ZERO, offence(styles),
			java.util.Collections.emptyList(), false, "", 0);
	}

	/** A single armour piece carrying the whole set's defensive block. */
	private static Kit armour(int stab, int slash, int crush, int magic, int ranged) {
		Gear body = new Gear(1, "Test platebody", "", Slot.BODY, "", 0,
			false, true, true, true, 0,
			Stats.ZERO,
			new Stats(stab, slash, crush, magic, ranged, 0, 0, 0, 0),
			Stats.ZERO, null);
		return new Kit(Map.of(Slot.BODY, body));
	}

	@Test
	public void singleStyleMonsterIsFullyBlockedByTheProtectionPrayer() {
		Incoming.Result result = Incoming.calculate(
			monster(List.of("Crush")), armour(200, 200, 200, 0, 150), 99, 99, 0);
		Assert.assertEquals("Protect from Melee", result.protectPrayer);
		Assert.assertEquals(0.0, result.tot, 1e-9);
		Assert.assertTrue(result.unprayedDps > 0);
		Assert.assertTrue(result.fullyModeled);
		Assert.assertTrue(result.threats.get(0).blocked);
	}

	@Test
	public void hybridStylesProtectByDeliveryNotRoll() {
		// Wiki (Magical melee / Magical ranged pages): the accuracy roll
		// is vs magic defence, but the PRAYER follows the delivery - a
		// Pyrefiend's magical melee is blocked by Protect from MELEE
		// (field report 2026-08-08, grZ: "this is wrong should be melee
		// protect"), magical ranged by Protect from MISSILES.
		Incoming.Result melee = Incoming.calculate(
			monster(List.of("Magical melee")), armour(200, 200, 200, 0, 150), 99, 99, 0);
		Assert.assertEquals("Protect from Melee", melee.protectPrayer);
		Incoming.Result ranged = Incoming.calculate(
			monster(List.of("Magical ranged")), armour(200, 200, 200, 0, 150), 99, 99, 0);
		Assert.assertEquals("Protect from Missiles", ranged.protectPrayer);
		// The inverse hybrids: ranged magic (Kree'arra, Spinolyps) is a
		// CAST that rolls vs ranged defence - Protect from Magic blocks
		// it; ranged melee (dust devils) is melee-blocked.
		Incoming.Result cast = Incoming.calculate(
			monster(List.of("Ranged magic")), armour(200, 200, 200, 0, 150), 99, 99, 0);
		Assert.assertEquals("Protect from Magic", cast.protectPrayer);
		Incoming.Result swing = Incoming.calculate(
			monster(List.of("Ranged melee")), armour(200, 200, 200, 0, 150), 99, 99, 0);
		Assert.assertEquals("Protect from Melee", swing.protectPrayer);
	}

	@Test
	public void chipStyleContributesItsRotationShareWhilePrayingTheWorst() {
		// Hand-computed: crush roll 289*184=53176 vs def (99+8)*(200+64)=28248
		// -> acc 0.73438, max hit (359*107+320)/640=60, 6t
		// -> crush dps 0.73438*30/3.6 = 6.1198.
		// Ranged roll 359*164=58876 vs 107*214=22898 -> acc 0.80553,
		// max (359*104+320)/640=58 -> dps 0.80553*29/3.6 = 6.4890 (worst).
		// Protect Missiles; chip = crush share 6.1198/2 = 3.0599.
		Incoming.Result result = Incoming.calculate(
			monster(Arrays.asList("Crush", "Ranged")), armour(200, 200, 200, 0, 150), 99, 99, 0);
		Assert.assertEquals("Protect from Missiles", result.protectPrayer);
		Assert.assertEquals(3.0599, result.tot, 0.0005);
		// Unprayed adds the blocked ranged share: 3.0599 + 6.4890/2 = 6.3044.
		Assert.assertEquals(6.3044, result.unprayedDps, 0.0005);
		Assert.assertEquals(60, result.threats.get(0).maxHit);
	}

	@Test
	public void aPreparedCalculatorMatchesTheFullCalculationExactly() {
		// The defense-weighted beam scores thousands of candidate sets per
		// optimize; prepare() hoists the monster-side constants and must
		// reproduce calculate() bit-for-bit on both model paths.
		Monster sheet = monster(Arrays.asList("Crush", "Ranged", "Magic", "Dragonfire"));
		Kit[] armours = {
			armour(0, 0, 0, 0, 0),
			armour(200, 180, 210, 40, 150),
			armour(-20, 300, 5, 120, 60),
		};
		Incoming.Prepared prepared = new Incoming.Prepared(sheet, BossIncomingOverrides.overridesFor(sheet), 82, 91, 0);
		for (Kit loadout : armours) {
			Incoming.Result full = Incoming.calculate(sheet, loadout, 82, 91, 0);
			Assert.assertEquals(full.tot, prepared.tot(loadout), 0.0);
		}
	}

	@Test
	public void aPreparedCalculatorMatchesOnCuratedOverrideBosses() {
		com.ll.data.Tables data = new com.ll.data.DataService().load();
		for (String name : new String[]{"Callisto", "Zulrah", "Corporeal Beast"}) {
			Monster boss = data.searchMonsters(name, 1).get(0);
			Incoming.Prepared prepared = new Incoming.Prepared(boss, BossIncomingOverrides.overridesFor(boss), 85, 89, 0);
			for (Kit loadout : new Kit[]{
				armour(0, 0, 0, 0, 0), armour(220, 240, 230, 80, 200)}) {
				Incoming.Result full = Incoming.calculate(boss, loadout, 85, 89, 0);
				Assert.assertEquals(full.tot, prepared.tot(loadout), 0.0);
			}
		}
	}

	@Test
	public void aPreparedCalculatorMatchesAcrossTheWholeCorpus() {
		// Layer 1 (ISS-01M5FZ3KYCGEMMX1A45D3J9NQ9) changed the sheet model's
		// max hits, styles, speeds and shares - the beam's scorer must still
		// agree with the display path on every monster we ship.
		com.ll.data.Tables data = new com.ll.data.DataService().load();
		Kit[] armours = {armour(0, 0, 0, 0, 0), armour(220, 240, 230, 80, 200)};
		for (Monster mob : data.getMonsters()) {
			Incoming.Prepared prepared = new Incoming.Prepared(mob, BossIncomingOverrides.overridesFor(mob), 85, 89, 0);
			for (Kit loadout : armours) {
				Incoming.Result full = Incoming.calculate(mob, loadout, 85, 89, 0);
				Assert.assertEquals(mob.getName() + " " + mob.getVersion(),
					full.tot, prepared.tot(loadout), 0.0);
			}
		}
	}

	@Test
	public void betterArmourMeansLessIncomingDps() {
		double tank = Incoming.calculate(
			monster(Arrays.asList("Crush", "Ranged")), armour(300, 300, 300, 0, 300), 99, 99, 0).tot;
		double naked = Incoming.calculate(
			monster(Arrays.asList("Crush", "Ranged")), armour(0, 0, 0, 0, 0), 99, 99, 0).tot;
		Assert.assertTrue(tank + " < " + naked, tank < naked);
	}

	@Test
	public void magicDefenceUsesMagicLevelAndMagicDefBonus() {
		Monster mage = monster(List.of("Magic", "Ranged"));
		double lowMagic = Incoming.calculate(mage, armour(0, 0, 0, 0, 0), 99, 1, 0).tot;
		double highMagic = Incoming.calculate(mage, armour(0, 0, 0, 0, 0), 99, 99, 0).tot;
		Assert.assertTrue(highMagic <= lowMagic);
		double magicArmour = Incoming.calculate(mage, armour(0, 0, 0, 120, 0), 99, 99, 0).tot;
		Assert.assertTrue(magicArmour < highMagic);
	}

	@Test
	public void unmodeledStylesAreSurfacedNotSilentlyDropped() {
		Incoming.Result result = Incoming.calculate(
			monster(Arrays.asList("Typeless")), armour(200, 200, 200, 0, 150), 99, 99, 0);
		Assert.assertFalse(result.fullyModeled);
		Assert.assertNull(result.protectPrayer);
		Assert.assertEquals(0.0, result.tot, 1e-9);
		Assert.assertFalse(result.threats.get(0).modeled);
	}

	@Test
	public void genericMeleeAssumesTheWeakestDefensiveSide() {
		// Generic "Melee" must use min(stab, slash, crush) = 50 here,
		// so it hits harder than a typed Crush attack against crush 300.
		double generic = Incoming.calculate(
			monster(List.of("Melee", "Ranged")), armour(50, 300, 300, 0, 0), 99, 99, 0).tot;
		double typed = Incoming.calculate(
			monster(List.of("Crush", "Ranged")), armour(50, 300, 300, 0, 0), 99, 99, 0).tot;
		Assert.assertTrue(generic >= typed);
	}
}
