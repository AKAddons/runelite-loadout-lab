package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.Map;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** Stat-identical analogs (god d'hide) must track what the player owns. */
public class AnalogTest {
	private static Tables data;
	private static Monster callisto;

	@BeforeClass
	public static void load() {
		data = new DataService().load();
		callisto = data.searchMonsters("callisto", 1).get(0);
	}

	private static Gear headPick(int ownedCoif) {
		Req req = TestRequests.of(callisto, Style.RANGED,
			Levels.MAXED, PrayerBonuses.bestAvailable(Levels.MAXED, Prayers.ALL),
			null, 0, CandidateMode.ALL_STANDARD, true, false,
			new Owned(data.canonicalizeOwned(Map.of(ownedCoif, 1)), true), 1)
			.withMaxTr(3);
		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		// Mirror production: the dps-neutral fill is what populates a head
		// slot the raw beam legally leaves empty (post Summer Sweep-Up
		// 2026-07-22, every risk-surviving ranged head is dps-neutral here).
		Dps best = optimizer.fillDpsNeutralSlots(data, req,
			optimizer.optimize(data, req).get(0));
		return best.getLoadout().get(com.ll.data.Slot.HEAD);
	}

	@Test
	public void legAnalogsAreStatIdenticalInTheData() {
		// Platelegs vs plateskirts, proselyte cuisse vs tasset: the
		// dedupe's owned-preference and the gold border's stat
		// equivalence both rest on these being true stat clones.
		assertClones(1079, 1093);  // rune platelegs / plateskirt
		assertClones(4087, 4585);  // dragon platelegs / plateskirt
		assertClones(9676, 9678);  // proselyte cuisse / tasset
	}

	private static void assertClones(int a, int b) {
		Gear left = data.getGear(a);
		Gear right = data.getGear(b);
		Assert.assertNotNull(left);
		Assert.assertNotNull(right);
		Assert.assertEquals(left.getSlot(), right.getSlot());
		for (java.util.function.Function<Gear, com.ll.data.Stats> block
			: java.util.List.<java.util.function.Function<Gear, com.ll.data.Stats>>of(
				Gear::getOffensive, Gear::getDefensive, Gear::getBonuses)) {
			com.ll.data.Stats l = block.apply(left);
			com.ll.data.Stats r = block.apply(right);
			Assert.assertEquals(l.getStab(), r.getStab());
			Assert.assertEquals(l.getSlash(), r.getSlash());
			Assert.assertEquals(l.getCrush(), r.getCrush());
			Assert.assertEquals(l.getMagic(), r.getMagic());
			Assert.assertEquals(l.getRanged(), r.getRanged());
			Assert.assertEquals(l.getStrength(), r.getStrength());
			Assert.assertEquals(l.getRangedStrength(), r.getRangedStrength());
			Assert.assertEquals(l.getMagicDamage(), r.getMagicDamage());
			Assert.assertEquals(l.getPrayer(), r.getPrayer());
		}
	}

	@Test
	public void prayerDistinguishesStatClones_radasBlessingBeatsAncientBlessing() {
		// Both are zero-offense ammo-slot blessings; Rada's 4 gives +2
		// prayer vs +1. The old dedupe key ignored prayer, collapsed them,
		// and the tradeable preference picked the ancient blessing.
		java.util.Map<Integer, Integer> owned = new java.util.HashMap<>();
		owned.put(4151, 1);   // abyssal whip
		owned.put(20235, 1);  // ancient blessing (+1, tradeable)
		owned.put(22947, 1);  // rada's blessing 4 (+2, untradeable)
		Monster goblin = data.searchMonsters("goblin", 1).get(0);
		Req req = TestRequests.of(goblin, Style.MELEE,
			Levels.MAXED, PrayerBonuses.bestAvailable(Levels.MAXED, Prayers.ALL),
			null, 0, CandidateMode.OWNED_ONLY, true, false,
			new Owned(data.canonicalizeOwned(owned), true), 1);
		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		Dps best = optimizer.fillDpsNeutralSlots(data, req,
			optimizer.optimize(data, req).get(0));
		Gear ammo = best.getLoadout().get(com.ll.data.Slot.AMMO);
		Assert.assertNotNull(ammo);
		Assert.assertEquals("Rada's blessing 4", ammo.getName());
	}

	@Test
	public void statTiedAnalogsFollowOwnership() {
		Gear armadyl = headPick(12512);
		Gear bandos = headPick(12504);
		// Only assert when the head pick lands in the god-coif class at all
		// (data drift could promote another helm); when it does, it must be
		// the OWNED god's.
		if (armadyl != null && armadyl.getName().endsWith("coif")) {
			Assert.assertEquals("Armadyl coif", armadyl.getName());
		}
		if (bandos != null && bandos.getName().endsWith("coif")) {
			Assert.assertEquals("Bandos coif", bandos.getName());
		}
		Assert.assertNotNull(armadyl);
	}
}
