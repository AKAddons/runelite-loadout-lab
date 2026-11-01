package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class BlowpipeTest {
	@Test
	public void blowpipeWithDragonDartsBeatsBareThrownDarts() {
		Tables data = new DataService().load();
		Monster hydra = data.searchMonsters("alchemical hydra", 1).get(0);
		Req game = new Req(
			hydra, Style.RANGED, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 3);
		List<Dps> results = new LoadoutOptimizer().optimize(data, game);
		Assert.assertFalse(results.isEmpty());
		for (Dps r : results) {
			Assert.assertFalse("bare thrown darts must never outrank a loaded blowpipe: "
					+ r.getLoadout().getWeapon().getName(),
				r.getLoadout().getWeapon().getName().endsWith(" dart"));
		}
	}

	@Test
	public void ownedBlowpipeUsesTheBestOwnedDartTierAndNeedsDarts() {
		Tables data = new DataService().load();
		Monster hydra = data.searchMonsters("alchemical hydra", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(12926, 1);  // toxic blowpipe (charged)
		owned.put(811, 500);  // rune darts
		Req request = new Req(
			hydra, Style.RANGED, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new Owned(owned, true), RequirementProfile.MAXED, 1);
		Dps withRune = new LoadoutOptimizer().optimize(data, request).get(0);
		Assert.assertTrue(withRune.getAttackType().contains("rune darts"));

		owned.put(11230, 500); // dragon darts: better tier takes over
		Dps withDragon = new LoadoutOptimizer().optimize(data, new Req(
			hydra, Style.RANGED, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new Owned(owned, true), RequirementProfile.MAXED, 1)).get(0);
		Assert.assertTrue(withDragon.getAttackType().contains("dragon darts"));
		Assert.assertTrue(withDragon.getDps() > withRune.getDps());
	}
}
