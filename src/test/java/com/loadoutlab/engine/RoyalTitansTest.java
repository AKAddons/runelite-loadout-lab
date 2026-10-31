package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterGroups;
import com.loadoutlab.data.Monster;
import java.util.EnumMap;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

/**
 * Royal Titans phase model (field report 2026-08-10: "even the noxious
 * halberd can't reach them in phase 1"; wiki-verified): the group
 * carries synthetic "(Out of reach)" forms per titan - melee-immune,
 * with the wiki's hidden +500% (6x) ranged accuracy that lasts while
 * they are out of melee distance. The near-phase base rows keep all
 * three styles; the roster kit must therefore seat a ranged answer.
 */
public class RoyalTitansTest {
	private static LoadoutData data;

	private static LoadoutData data() {
		if (data == null) {
			data = new DataService().load();
		}
		return data;
	}

	private static Monster farTitan(String name) {
		for (MonsterGroups.MonsterGroup group : MonsterGroups.load(data())) {
			if (!"Royal Titans".equals(group.getName())) {
				continue;
			}
			for (Monster mob : group.getMobs()) {
				if (mob.getName().toLowerCase().contains(name)
					&& mob.label().contains("Out of reach")) {
					return mob;
				}
			}
		}
		throw new AssertionError("no out-of-reach form for " + name);
	}

	@Test
	public void farFormsAreMeleeImmuneNearFormsAreNot() {
		Monster far = farTitan("branda");
		Assert.assertTrue(com.loadoutlab.engine.Immunity.styleImmune(far, Style.MELEE));
		Assert.assertFalse(com.loadoutlab.engine.Immunity.styleImmune(far, Style.RANGED));
		Monster near = data().searchMonsters("branda the fire queen", 1).get(0);
		Assert.assertFalse("the near-phase base row keeps melee",
			com.loadoutlab.engine.Immunity.styleImmune(near, Style.MELEE));
		Assert.assertTrue(com.loadoutlab.engine.Immunity.styleImmune(farTitan("eldric"), Style.MELEE));
	}

	@Test
	public void outOfReachGrantsSixTimesRangedAccuracyOnly() {
		Monster near = data().searchMonsters("branda the fire queen", 1).get(0);
		Monster far = farTitan("branda");
		EnumMap<GearSlot, Gear> gear = new EnumMap<>(GearSlot.class);
		for (Gear g : data().getGearItems()) {
			if (g.getNameLower().equals("twisted bow") && g.isStandardGear()) {
				gear.put(GearSlot.WEAPON, g);
			}
			if (g.getNameLower().equals("dragon arrow") && g.isStandardGear()) {
				gear.put(GearSlot.AMMO, g);
			}
		}
		Loadout loadout = new Loadout(gear);
		DpsResult atNear = calc(near, loadout);
		DpsResult atFar = calc(far, loadout);
		Assert.assertEquals("the multiplier is accuracy-only",
			atNear.getMaxHit(), atFar.getMaxHit());
		Assert.assertEquals("attack roll is exactly 6x while out of reach",
			atNear.getAttackRoll() * 6, atFar.getAttackRoll());
		Assert.assertTrue("6x must bite against +700 ranged defence",
			atFar.getAccuracy() > atNear.getAccuracy() * 2);
	}

	private static DpsResult calc(Monster mob, Loadout loadout) {
		OptimizationRequest request = TestRequests.of(mob, Style.RANGED,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, 1);
		DpsResult result = new DpsCalculator().calculate(request, loadout);
		Assert.assertNotNull(result);
		return result;
	}

	@Test
	public void theGroupCarriesSixForms() {
		for (MonsterGroups.MonsterGroup group : MonsterGroups.load(data())) {
			if ("Royal Titans".equals(group.getName())) {
				Assert.assertEquals(6, group.getMobs().size());
				List<Monster> mobs = group.getMobs();
				long far = mobs.stream().filter(m -> m.label().contains("Out of reach")).count();
				Assert.assertEquals(2, far);
				return;
			}
		}
		throw new AssertionError("Royal Titans group missing");
	}
}
