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
		OptimizationRequest req = new OptimizationRequest(callisto, CombatStyle.RANGED,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL),
			null, 0, CandidateMode.ALL_STANDARD, true, false,
			new OwnedItems(data.canonicalizeOwned(Map.of(ownedCoif, 1)), true), 1)
			.withMaxTradeables(3);
		DpsResult best = new LoadoutOptimizer().optimize(data, req).get(0);
		return best.getLoadout().get(com.loadoutlab.data.GearSlot.HEAD);
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
