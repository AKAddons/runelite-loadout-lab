package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** Zulrah demands a recoil source; the optimizer must actually bring one. */
public class RequiredUtilityTest
{
	private static LoadoutData data;
	private static MonsterStats zulrah;
	private static final java.util.Set<Integer> RECOIL_IDS =
		java.util.Set.of(2550, 20655, 20657, 28945);

	@BeforeClass
	public static void load()
	{
		data = new DataService().load();
		zulrah = data.searchMonsters("zulrah", 1).get(0);
	}

	private static DpsResult best(OptimizationRequest request)
	{
		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		DpsResult result = optimizer.optimize(data, request).get(0);
		result = optimizer.fillDpsNeutralSlots(data, request, result);
		return optimizer.ensureRequiredUtility(data, request, result);
	}

	private static boolean hasRecoil(DpsResult result)
	{
		for (GearItem item : result.getLoadout().getGear().values())
		{
			if (item != null && RECOIL_IDS.contains(item.getId()))
			{
				return true;
			}
		}
		return false;
	}

	@Test
	public void theGameBestZulrahSetCarriesARecoilSource()
	{
		OptimizationRequest req = new OptimizationRequest(zulrah, CombatStyle.RANGED,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL),
			null, 0, CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, 1);
		Assert.assertTrue(hasRecoil(best(req)));
	}

	@Test
	public void anUnchargedSufferingCountsAsAccessToTheRecoilForm()
	{
		// Own tbow + arrows + an UNCHARGED suffering (i): the set must show
		// the Recoil-charged form (20657) in the ring slot.
		OptimizationRequest req = new OptimizationRequest(zulrah, CombatStyle.RANGED,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL),
			null, 0, CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(data.canonicalizeOwned(Map.of(20997, 1, 11212, 200, 19710, 1)), true), 1);
		DpsResult result = best(req);
		GearItem ring = result.getLoadout().get(GearSlot.RING);
		Assert.assertNotNull(ring);
		Assert.assertEquals(20657, ring.getId());
	}

	@Test
	public void owningNoRecoilSourceLeavesTheSetUnchanged()
	{
		OptimizationRequest req = new OptimizationRequest(zulrah, CombatStyle.RANGED,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL),
			null, 0, CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(data.canonicalizeOwned(Map.of(20997, 1, 11212, 200)), true), 1);
		Assert.assertFalse(hasRecoil(best(req)));
	}

	@Test
	public void nonZulrahMonstersAreUntouchedByTheRecoilRule()
	{
		MonsterStats graardor = data.searchMonsters("general graardor", 1).get(0);
		Assert.assertFalse(RequiredUtility.requiresRecoil(graardor));
		Assert.assertTrue(RequiredUtility.requiresRecoil(zulrah));
	}
}
