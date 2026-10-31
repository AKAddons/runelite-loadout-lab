package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

public class RatBoneRulesTest {
	private static Tables data;

	private static Tables data() {
		if (data == null) {
			data = new DataService().load();
		}
		return data;
	}

	private static Req request(Monster monster, Style style) {
		return new Req(
			monster, style, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 1);
	}

	private static Dps calculate(Monster monster, Style style, int weaponId) {
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, data().getGear(weaponId));
		return new Calc().calculate(request(monster, style), new Kit(gear));
	}

	@Test
	public void boneStaffCannotBeUsedAgainstNonRats() {
		Monster goblin = data().searchMonsters("goblin", 1).get(0);
		Assert.assertNull(calculate(goblin, Style.MAGIC, 28796));
		// And a player who owns one is never told to bring it.
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(28796, 1);
		Req ownedMagic = new Req(
			goblin, Style.MAGIC, Levels.MAXED,
			PrayerBonuses.bestAvailable(Levels.MAXED), null, 0,
			CandidateMode.OWNED_ONLY, true, false,
			new Owned(owned, true), RequirementProfile.MAXED, 5);
		for (Dps result : new LoadoutOptimizer().optimize(data(), ownedMagic)) {
			Assert.assertNotEquals(28796, result.getLoadout().getWeapon().getId());
		}
	}

	@Test
	public void boneStaffHitsThirtyEightAgainstScurriusAtMaxed() {
		Monster scurrius = data().searchMonsters("scurrius", 1).get(0);
		Assert.assertTrue(scurrius.hasAttribute("rat"));
		// Prayer-less, to match the wiki's max-hit table exactly.
		Req request = new Req(
			scurrius, Style.MAGIC, Levels.MAXED,
			PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			Owned.EMPTY, RequirementProfile.MAXED, 1);
		EnumMap<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, data().getGear(28796));
		Dps result = new Calc().calculate(request, new Kit(gear));
		Assert.assertNotNull(result);
		// Wiki max-hit table: 38 at 99 Magic (trident scaling + rat bonus).
		Assert.assertEquals(38, result.getMaxHit());
	}

	@Test
	public void boneMaceGetsTheRatBonusOnlyAgainstRats() {
		Monster scurrius = data().searchMonsters("scurrius", 1).get(0);
		Monster goblin = data().searchMonsters("goblin", 1).get(0);
		Assert.assertEquals(10, RatBoneRules.flatMaxHitBonus(scurrius, data().getGear(28792)));
		Assert.assertEquals(0, RatBoneRules.flatMaxHitBonus(goblin, data().getGear(28792)));
		Assert.assertFalse(RatBoneRules.canUse(goblin, data().getGear(28792)));
	}

	@Test
	public void gameBestMagicAgainstNonRatsIsNeverTheBoneStaff() {
		Monster goblin = data().searchMonsters("goblin", 1).get(0);
		List<Dps> results = new LoadoutOptimizer().optimize(data(), request(goblin, Style.MAGIC));
		Assert.assertFalse(results.isEmpty());
		Assert.assertNotEquals(28796, results.get(0).getLoadout().getWeapon().getId());
	}
}
