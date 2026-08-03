package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import org.junit.Assert;
import org.junit.Test;

public class GolembaneTest
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

	private static GearItem weapon(String weaponName)
	{
		return data().getGearItems().stream()
			.filter(g -> g.getName().equalsIgnoreCase(weaponName) && g.isStandardGear())
			.findFirst().orElseThrow(() -> new AssertionError("missing " + weaponName));
	}

	private static DpsResult calculate(MonsterStats monster, String weaponName)
	{
		GearItem weapon = weapon(weaponName);
		OptimizationRequest request = new OptimizationRequest(
			monster, CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, weapon);
		return new DpsCalculator().calculate(request, new Loadout(gear));
	}

	@Test
	public void graniteHammerGolembaneBeatsTheTentacleAgainstDusk()
	{
		// Aug 2025: the granite hammer deals 30% extra damage AND accuracy
		// vs golem-type monsters (Dusk, Dawn, gargoyles...).
		MonsterStats dusk = data().searchMonsters("dusk", 1).get(0);
		Assert.assertTrue(dusk.hasAttribute("golem"));
		Assert.assertTrue(calculate(dusk, "Granite hammer").getDps()
			> calculate(dusk, "Abyssal tentacle").getDps());
	}

	@Test
	public void golembaneDoesNotApplyToNonGolems()
	{
		MonsterStats goblin = data().searchMonsters("goblin", 1).get(0);
		// Against a non-golem the hammer is just its stat sheet: the
		// tentacle wins comfortably.
		Assert.assertTrue(calculate(goblin, "Abyssal tentacle").getDps()
			> calculate(goblin, "Granite hammer").getDps());
	}

	@Test
	public void optimizerPicksTheHammerAtAGolem()
	{
		// The pool lesson, which the model tests above do NOT cover: golembane
		// lives in the DPS model, not in the hammer's raw stats (crush +57 /
		// str +56), so the weapon-pool cut dropped it before the bonus could
		// ever be applied. Field-found 2026-08-01 at the Mad Angel - a
		// Zamorakian hasta was suggested and the owned melee answer came in
		// ~2 dps low (6.58 vs 8.58).
		MonsterStats madAngel = data().searchMonsters("mad angel", 1).get(0);
		Assert.assertTrue(madAngel.hasAttribute("golem"));

		// The FULL standard pool is the point: the cut keeps 24 weapons by
		// raw score, and the hammer only survives it because of the golem
		// boost. A two-weapon owned pool would never reach the cut and would
		// re-prove the DPS model instead of the pool behaviour.
		OptimizationRequest request = new OptimizationRequest(
			madAngel, CombatStyle.MELEE, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED), null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 3);
		java.util.List<DpsResult> results = new LoadoutOptimizer().optimize(data(), request);
		Assert.assertFalse(results.isEmpty());
		Assert.assertEquals("granite hammer",
			results.get(0).getLoadout().getWeapon().getNameLower());
	}

	@Test
	public void barroniteMaceGetsItsFifteenPercentAgainstGolems()
	{
		// Grey golem: golem-typed with zero flat armour, so the max-hit
		// ratio is exactly the 15% golembane factor.
		MonsterStats golem = data().searchMonsters("grey golem", 1).get(0);
		MonsterStats goblin = data().searchMonsters("goblin", 1).get(0);
		int vsGolem = calculate(golem, "Barronite mace").getMaxHit();
		int vsGoblin = calculate(goblin, "Barronite mace").getMaxHit();
		Assert.assertEquals((int) (vsGoblin * 23L / 20), vsGolem);
	}
}
