package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** Zulrah demands a recoil source; the optimizer must actually bring one. */
public class RequiredUtilityTest {
	private static Tables data;
	private static Monster zulrah;
	private static final java.util.Set<Integer> RECOIL_IDS =
		java.util.Set.of(2550, 20655, 20657, 28945);

	@BeforeClass
	public static void load() {
		data = new DataService().load();
		zulrah = data.searchMonsters("zulrah", 1).get(0);
	}

	private static Dps best(Req request) {
		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		Dps result = optimizer.optimize(data, request).get(0);
		result = optimizer.fillDpsNeutralSlots(data, request, result);
		return optimizer.ensureRequiredUtility(data, request, result);
	}

	private static boolean hasRecoil(Dps result) {
		for (Gear item : result.getLoadout().getGear().values()) {
			if (item != null && RECOIL_IDS.contains(item.getId())) {
				return true;
			}
		}
		return false;
	}

	@Test
	public void theGameBestZulrahSetCarriesARecoilSource() {
		Req req = TestRequests.of(zulrah, Style.RANGED,
			Levels.MAXED, PrayerBonuses.bestAvailable(Levels.MAXED, Prayers.ALL),
			null, 0, CandidateMode.ALL_STANDARD, true, false, Owned.EMPTY, 1);
		Assert.assertTrue(hasRecoil(best(req)));
	}

	@Test
	public void anUnchargedSufferingCountsAsAccessToTheRecoilForm() {
		// Own tbow + arrows + an UNCHARGED suffering (i): the set must show
		// the Recoil-charged form (20657) in the ring slot.
		Req req = TestRequests.of(zulrah, Style.RANGED,
			Levels.MAXED, PrayerBonuses.bestAvailable(Levels.MAXED, Prayers.ALL),
			null, 0, CandidateMode.OWNED_ONLY, true, false,
			new Owned(data.canonicalizeOwned(Map.of(20997, 1, 11212, 200, 19710, 1)), true), 1);
		Dps result = best(req);
		Gear ring = result.getLoadout().get(Slot.RING);
		Assert.assertNotNull(ring);
		Assert.assertEquals(20657, ring.getId());
	}

	@Test
	public void owningNoRecoilSourceLeavesTheSetUnchanged() {
		Req req = TestRequests.of(zulrah, Style.RANGED,
			Levels.MAXED, PrayerBonuses.bestAvailable(Levels.MAXED, Prayers.ALL),
			null, 0, CandidateMode.OWNED_ONLY, true, false,
			new Owned(data.canonicalizeOwned(Map.of(20997, 1, 11212, 200)), true), 1);
		Assert.assertFalse(hasRecoil(best(req)));
	}

	@Test
	public void nonZulrahMonstersAreUntouchedByTheRecoilRule() {
		Monster graardor = data.searchMonsters("general graardor", 1).get(0);
		Assert.assertFalse(RequiredUtility.requiresRecoil(graardor));
		Assert.assertTrue(RequiredUtility.requiresRecoil(zulrah));
	}
}
