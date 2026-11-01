package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
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
	private static Gear item(Tables data, String name, String version) {
		return data.getGearItems().stream().filter(g -> g.getName().equalsIgnoreCase(name)
			&& (version == null || version.equals(g.getVersion())) && g.isStandardGear())
			.findFirst().orElseThrow(() -> new AssertionError(name));
	}

	@Test
	public void theFireCapeWinsAWithinHalfAPercentTieOnPrayerAndDefence() {
		Tables data = new DataService().load();
		Monster ezekiel = data.searchMonsters("ezekiel", 1).get(0);
		EnumMap<Slot, Gear> worn = new EnumMap<>(Slot.class);
		worn.put(Slot.HEAD, item(data, "Neitiznot faceguard", null));
		worn.put(Slot.CAPE, item(data, "Mythical cape", null));
		worn.put(Slot.NECK, item(data, "Amulet of torture", null));
		worn.put(Slot.WEAPON, data.getGear(34715));
		worn.put(Slot.BODY, item(data, "Bandos chestplate", null));
		worn.put(Slot.LEGS, item(data, "Blood moon tassets", "New"));
		worn.put(Slot.HANDS, item(data, "Ferocious gloves", null));
		worn.put(Slot.FEET, item(data, "Primordial boots", null));
		worn.put(Slot.RING, item(data, "Berserker ring (i)", null));
		Map<Integer, Integer> owned = new HashMap<>();
		for (Gear g : worn.values()) {
			owned.put(g.getId(), 1);
		}
		owned.put(item(data, "Fire cape", "Normal").getId(), 1);
		// The report's assumptions: Piety (no Zeal) + Divine super combat.
		Levels boosted = Levels.MAXED.boosted(BoostProfile.DIVINE_SUPER_COMBAT, Levels.MAXED);
		Req request = TestRequests.of(ezekiel, Style.MELEE, boosted,
			PrayerBonuses.bestAvailable(boosted, new Prayers(true, false, false, true, false)), null, 0,
			CandidateMode.OWNED_ONLY, true, false, new Owned(owned, true), 1);
		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		// Everything is owned: the search hands the polish a set that cost
		// nothing to buy (the raw calculator prices every piece).
		Dps start = new Calc().calculate(request, new Kit(worn)).withPurchaseCost(0);
		Dps polished = optimizer.fillDpsNeutralSlots(data, request, start);
		Assert.assertEquals("Fire cape", polished.getLoadout().get(Slot.CAPE).getName());
		Assert.assertTrue("a real, small loss: " + polished.getDps() + " vs " + start.getDps(),
			polished.getDps() < start.getDps() && polished.getDps() >= start.getDps() * 0.995);
	}

	@Test
	public void thePolishNeverCostsMoreThanHalfAPercentInTotal() {
		Tables data = new DataService().load();
		for (String q : new String[]{"ezekiel", "vorkath", "general graardor", "zulrah"}) {
			Monster mob = data.searchMonsters(q, 1).get(0);
			for (Style style : new Style[]{Style.MELEE, Style.RANGED, Style.MAGIC}) {
				Req request = TestRequests.of(mob, style, Levels.MAXED,
					PrayerBonuses.bestAvailable(Levels.MAXED), null, 0, CandidateMode.ALL_STANDARD, true, false,
					Owned.EMPTY, 1);
				LoadoutOptimizer optimizer = new LoadoutOptimizer();
				java.util.List<Dps> best = optimizer.optimize(data, request);
				if (best.isEmpty()) {
					continue;
				}
				Dps polished = optimizer.fillDpsNeutralSlots(data, request, best.get(0));
				Assert.assertTrue(q + " " + style, polished.getDps() >= best.get(0).getDps() * 0.995 - 1e-9);
			}
		}
	}
}
