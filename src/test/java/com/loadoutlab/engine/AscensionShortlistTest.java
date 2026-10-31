package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Test;

/**
 * Andrew, Copy report 2026-10-20 (Gemstone Crab, ~1,000-item bank, the
 * crossbows and diamond ascension bolts simmed): the blowpipe still won.
 * The weapon shortlist ranks on raw stats (24 weapons), and the
 * crossbows' strength lives in their bolts (+34 ranged attack, no ranged
 * strength of their own), so a full bank cut them before the damage step.
 */
public class AscensionShortlistTest {
	@Test
	public void theCrossbowsMakeTheRangedShortlistInAFullBank() {
		LoadoutData data = new DataService().load();
		Monster crab = data.searchMonsters("gemstone crab", 1).get(0);
		OptimizationRequest request = TestRequests.of(crab, Style.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0, CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, 1);
		List<Gear> ranked = data.getGearItems(GearSlot.WEAPON).stream()
			.filter(g -> g.isStandardGear() && g.isWeaponFor(Style.RANGED))
			.sorted(Comparator.comparingDouble((Gear g) -> LoadoutOptimizer.candidateScore(request, g, false)).reversed())
			.collect(Collectors.toList());
		int at = ranked.indexOf(data.getGear(34681));
		Assert.assertTrue("ranked " + at + " of " + ranked.size(), at >= 0 && at < 24);
	}
}
