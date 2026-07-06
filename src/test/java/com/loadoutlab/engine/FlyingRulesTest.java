package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class FlyingRulesTest
{
	private static LoadoutData data;

	private static LoadoutData data()
	{
		if (data == null)
		{
			data = new DataService().load();
		}
		return data;
	}

	private static GearItem byName(String name)
	{
		return data().getGearItems().stream()
			.filter(g -> g.getName().equalsIgnoreCase(name))
			.findFirst()
			.orElseThrow(() -> new AssertionError("missing item: " + name));
	}

	@Test
	public void meleeCannotReachFlyingMonstersExceptHalberds()
	{
		MonsterStats kree = data().searchMonsters("kree'arra", 1).get(0);
		Assert.assertTrue(kree.hasAttribute("flying"));
		Assert.assertFalse(FlyingRules.canReach(kree, CombatStyle.MELEE, byName("Abyssal whip")));
		Assert.assertFalse(FlyingRules.canReach(kree, CombatStyle.MELEE, byName("Voidwaker")));
		Assert.assertTrue(FlyingRules.canReach(kree, CombatStyle.MELEE, byName("Crystal halberd")));
		Assert.assertTrue(FlyingRules.canReach(kree, CombatStyle.RANGED, byName("Twisted bow")));
	}

	@Test
	public void meleeVsDawnOnlyProducesHalberdSets()
	{
		MonsterStats dawn = data().searchMonsters("dawn", 1).get(0);
		Assert.assertTrue(dawn.hasAttribute("flying"));

		// A whip-only owner has no melee answer for Dawn at all.
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(4151, 1);
		OptimizationRequest ownedRequest = new OptimizationRequest(
			dawn, CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new OwnedItems(owned, true), RequirementProfile.MAXED, 5);
		Assert.assertTrue(new LoadoutOptimizer().optimize(data(), ownedRequest).isEmpty());

		// Game best melee vs Dawn: a polearm or salamander, never a sword.
		OptimizationRequest gameRequest = new OptimizationRequest(
			dawn, CombatStyle.MELEE, PlayerLevels.MAXED,
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
