package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/**
 * Andrew, Copy report 2026-10-20 (the Fractured Archive): "why is it
 * simming mythical cape over fire cape". The Mythical cape's +6 crush
 * beat the fire cape's +3 strength by ~0.1% with the Obligator - a real
 * but meaningless gap. Andrew: near-ties should prefer prayer and defence.
 * The dps-neutral polish now accepts a higher-utility swap while the set
 * stays within 0.5% of its original dps (the budget is the whole set's,
 * so swaps cannot stack past it).
 */
public class NearTieTest {
	private static GearItem item(LoadoutData data, String name, String version) {
		return data.getGearItems().stream().filter(g -> g.getName().equalsIgnoreCase(name)
			&& (version == null || version.equals(g.getVersion())) && g.isStandardGear())
			.findFirst().orElseThrow(() -> new AssertionError(name));
	}

	@Test
	public void theFireCapeWinsAWithinHalfAPercentTieOnPrayerAndDefence() {
		LoadoutData data = new DataService().load();
		MonsterStats ezekiel = data.searchMonsters("ezekiel", 1).get(0);
		EnumMap<GearSlot, GearItem> worn = new EnumMap<>(GearSlot.class);
		worn.put(GearSlot.HEAD, item(data, "Neitiznot faceguard", null));
		worn.put(GearSlot.CAPE, item(data, "Mythical cape", null));
		worn.put(GearSlot.NECK, item(data, "Amulet of torture", null));
		worn.put(GearSlot.WEAPON, data.getGear(34715));
		worn.put(GearSlot.BODY, item(data, "Bandos chestplate", null));
		worn.put(GearSlot.LEGS, item(data, "Blood moon tassets", "New"));
		worn.put(GearSlot.HANDS, item(data, "Ferocious gloves", null));
		worn.put(GearSlot.FEET, item(data, "Primordial boots", null));
		worn.put(GearSlot.RING, item(data, "Berserker ring (i)", null));
		Map<Integer, Integer> owned = new HashMap<>();
		for (GearItem g : worn.values()) {
			owned.put(g.getId(), 1);
		}
		owned.put(item(data, "Fire cape", "Normal").getId(), 1);
		// The report's assumptions: Piety (no Zeal) + Divine super combat.
		PlayerLevels boosted = PlayerLevels.MAXED.boosted(BoostProfile.DIVINE_SUPER_COMBAT, PlayerLevels.MAXED);
		OptimizationRequest request = TestRequests.of(ezekiel, CombatStyle.MELEE, boosted,
			PrayerBonuses.bestAvailable(boosted, new PrayerUnlocks(true, false, false, true, false)), null, 0,
			CandidateMode.OWNED_ONLY, true, false, new OwnedItems(owned, true), 1);
		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		// Everything is owned: the search hands the polish a set that cost
		// nothing to buy (the raw calculator prices every piece).
		DpsResult start = new DpsCalculator().calculate(request, new Loadout(worn)).withPurchaseCost(0);
		DpsResult polished = optimizer.fillDpsNeutralSlots(data, request, start);
		Assert.assertEquals("Fire cape", polished.getLoadout().get(GearSlot.CAPE).getName());
		Assert.assertTrue("a real, small loss: " + polished.getDps() + " vs " + start.getDps(),
			polished.getDps() < start.getDps() && polished.getDps() >= start.getDps() * 0.995);
	}

	@Test
	public void thePolishNeverCostsMoreThanHalfAPercentInTotal() {
		LoadoutData data = new DataService().load();
		for (String q : new String[]{"ezekiel", "vorkath", "general graardor", "zulrah"}) {
			MonsterStats mob = data.searchMonsters(q, 1).get(0);
			for (CombatStyle style : new CombatStyle[]{CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC}) {
				OptimizationRequest request = TestRequests.of(mob, style, PlayerLevels.MAXED,
					PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0, CandidateMode.ALL_STANDARD, true, false,
					OwnedItems.EMPTY, 1);
				LoadoutOptimizer optimizer = new LoadoutOptimizer();
				java.util.List<DpsResult> best = optimizer.optimize(data, request);
				if (best.isEmpty()) {
					continue;
				}
				DpsResult polished = optimizer.fillDpsNeutralSlots(data, request, best.get(0));
				Assert.assertTrue(q + " " + style, polished.getDps() >= best.get(0).getDps() * 0.995 - 1e-9);
			}
		}
	}
}
