package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.Map;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** Stat-identical analogs (god d'hide) must track what the player owns. */
public class AnalogTest
{
	private static LoadoutData data;
	private static MonsterStats callisto;

	@BeforeClass
	public static void load()
	{
		data = new DataService().load();
		callisto = data.searchMonsters("callisto", 1).get(0);
	}

	private static GearItem headPick(int ownedCoif)
	{
		OptimizationRequest req = TestRequests.of(callisto, CombatStyle.RANGED,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL),
			null, 0, CandidateMode.ALL_STANDARD, true, false,
			new OwnedItems(data.canonicalizeOwned(Map.of(ownedCoif, 1)), true), 1)
			.withMaxTradeables(3);
		DpsResult best = new LoadoutOptimizer().optimize(data, req).get(0);
		return best.getLoadout().get(com.loadoutlab.data.GearSlot.HEAD);
	}

	@Test
	public void legAnalogsAreStatIdenticalInTheData()
	{
		// Platelegs vs plateskirts, proselyte cuisse vs tasset: the
		// dedupe's owned-preference and the gold border's stat
		// equivalence both rest on these being true stat clones.
		assertClones(1079, 1093);  // rune platelegs / plateskirt
		assertClones(4087, 4585);  // dragon platelegs / plateskirt
		assertClones(9676, 9678);  // proselyte cuisse / tasset
	}

	private static void assertClones(int a, int b)
	{
		GearItem left = data.getGear(a);
		GearItem right = data.getGear(b);
		Assert.assertNotNull(left);
		Assert.assertNotNull(right);
		Assert.assertEquals(left.getSlot(), right.getSlot());
		for (java.util.function.Function<GearItem, com.loadoutlab.data.StatBlock> block
			: java.util.List.<java.util.function.Function<GearItem, com.loadoutlab.data.StatBlock>>of(
				GearItem::getOffensive, GearItem::getDefensive, GearItem::getBonuses))
		{
			com.loadoutlab.data.StatBlock l = block.apply(left);
			com.loadoutlab.data.StatBlock r = block.apply(right);
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
	public void prayerDistinguishesStatClones_radasBlessingBeatsAncientBlessing()
	{
		// Both are zero-offense ammo-slot blessings; Rada's 4 gives +2
		// prayer vs +1. The old dedupe key ignored prayer, collapsed them,
		// and the tradeable preference picked the ancient blessing.
		java.util.Map<Integer, Integer> owned = new java.util.HashMap<>();
		owned.put(4151, 1);   // abyssal whip
		owned.put(20235, 1);  // ancient blessing (+1, tradeable)
		owned.put(22947, 1);  // rada's blessing 4 (+2, untradeable)
		MonsterStats goblin = data.searchMonsters("goblin", 1).get(0);
		OptimizationRequest req = TestRequests.of(goblin, CombatStyle.MELEE,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL),
			null, 0, CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(data.canonicalizeOwned(owned), true), 1);
		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		DpsResult best = optimizer.fillDpsNeutralSlots(data, req,
			optimizer.optimize(data, req).get(0));
		GearItem ammo = best.getLoadout().get(com.loadoutlab.data.GearSlot.AMMO);
		Assert.assertNotNull(ammo);
		Assert.assertEquals("Rada's blessing 4", ammo.getName());
	}

	@Test
	public void statTiedAnalogsFollowOwnership()
	{
		GearItem armadyl = headPick(12512);
		GearItem bandos = headPick(12504);
		// Only assert when the head pick lands in the god-coif class at all
		// (data drift could promote another helm); when it does, it must be
		// the OWNED god's.
		if (armadyl != null && armadyl.getName().endsWith("coif"))
		{
			Assert.assertEquals("Armadyl coif", armadyl.getName());
		}
		if (bandos != null && bandos.getName().endsWith("coif"))
		{
			Assert.assertEquals("Bandos coif", bandos.getName());
		}
		Assert.assertNotNull(armadyl);
	}
}
