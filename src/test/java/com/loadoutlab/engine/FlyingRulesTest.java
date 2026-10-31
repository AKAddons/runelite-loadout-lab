package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class FlyingRulesTest {
	private static LoadoutData data;

	private static LoadoutData data() {
		if (data == null) {
			data = new DataService().load();
		}
		return data;
	}

	private static Gear byName(String name) {
		return data().getGearItems().stream()
			.filter(g -> g.getName().equalsIgnoreCase(name))
			.findFirst()
			.orElseThrow(() -> new AssertionError("missing item: " + name));
	}

	@Test
	public void meleeCannotReachFlyingMonstersExceptHalberds() {
		Monster kree = data().searchMonsters("kree'arra", 1).get(0);
		Assert.assertTrue(kree.hasAttribute("flying"));
		Assert.assertFalse(FlyingRules.canReach(kree, Style.MELEE, byName("Abyssal whip")));
		Assert.assertFalse(FlyingRules.canReach(kree, Style.MELEE, byName("Voidwaker")));
		Assert.assertTrue(FlyingRules.canReach(kree, Style.MELEE, byName("Crystal halberd")));
		Assert.assertTrue(FlyingRules.canReach(kree, Style.RANGED, byName("Twisted bow")));
	}

	@Test
	public void meleeVsDawnOnlyProducesHalberdSets() {
		Monster dawn = data().searchMonsters("dawn", 1).get(0);
		Assert.assertTrue(dawn.hasAttribute("flying"));

		// A whip-only owner has no melee answer for Dawn at all.
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		OptimizationRequest ownedRequest = new OptimizationRequest(
			dawn, Style.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), RequirementProfile.MAXED, 5);
		Assert.assertTrue(new LoadoutOptimizer().optimize(data(), ownedRequest).isEmpty());

		// Game best melee vs Dawn: a polearm or salamander, never a sword.
		OptimizationRequest gameRequest = new OptimizationRequest(
			dawn, Style.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
		List<DpsResult> results = new LoadoutOptimizer().optimize(data(), gameRequest);
		Assert.assertFalse(results.isEmpty());
		String category = results.get(0).getLoadout().getWeapon().getCategory();
		Assert.assertTrue("expected halberd/salamander, got " + category,
			"Polearm".equals(category) || "Salamander".equals(category));
	}
}
