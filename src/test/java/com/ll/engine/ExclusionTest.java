package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

public class ExclusionTest {
	private static Tables data;

	private static Tables data() {
		if (data == null) {
			data = new DataService().load();
		}
		return data;
	}

	@Test
	public void excludingDragonDartsDropsTheBlowpipeToTheNextOwnedTier() {
		Monster hydra = data().searchMonsters("alchemical hydra", 1).get(0);
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(12926, 1);   // toxic blowpipe
		owned.put(11230, 500); // dragon darts - the protected rares
		owned.put(25849, 500); // amethyst darts - the everyday ammo
		Req request = new Req(
			hydra, Style.RANGED, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new Owned(owned, true), RequirementProfile.MAXED, 1);

		Dps unrestricted = new LoadoutOptimizer().optimize(data(), request).get(0);
		Assert.assertTrue(unrestricted.getAttackType().contains("dragon darts"));

		Dps protectedDarts = new LoadoutOptimizer().optimize(data(),
			request.withExcludedItems(Set.of(11230))).get(0);
		Assert.assertTrue(protectedDarts.getAttackType().contains("amethyst darts"));
		Assert.assertTrue(unrestricted.getDps() > protectedDarts.getDps());
	}

	@Test
	public void excludedItemsNeverAppearInAnySlot() {
		Monster goblin = data().searchMonsters("goblin", 1).get(0);
		Req request = new Req(
			goblin, Style.MELEE, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 1);
		Dps best = new LoadoutOptimizer().optimize(data(), request).get(0);
		int weaponId = best.getLoadout().getWeapon().getId();

		Dps without = new LoadoutOptimizer().optimize(data(),
			request.withExcludedItems(Set.of(weaponId))).get(0);
		Assert.assertNotEquals(weaponId, without.getLoadout().getWeapon().getId());
		for (Slot slot : Slot.values()) {
			if (without.getLoadout().get(slot) != null) {
				Assert.assertNotEquals(weaponId, without.getLoadout().get(slot).getId());
			}
		}
	}
}
