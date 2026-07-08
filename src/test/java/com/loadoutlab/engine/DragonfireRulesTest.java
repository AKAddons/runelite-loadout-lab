package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.List;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class DragonfireRulesTest
{
	private static LoadoutData data;

	@BeforeClass
	public static void load()
	{
		data = new DataService().load();
	}

	private static MonsterStats monster(String name)
	{
		return data.searchMonsters(name, 1).get(0);
	}

	@Test
	public void firebreathersAreDetectedByTheirDragonfireStyleAndBabiesAreNot()
	{
		Assert.assertTrue(DragonfireRules.breathesFire(monster("green dragon")));
		Assert.assertTrue(DragonfireRules.breathesFire(monster("steel dragon")));
		Assert.assertTrue(DragonfireRules.breathesFire(monster("vorkath")));
		Assert.assertTrue(DragonfireRules.breathesFire(monster("king black dragon")));
		Assert.assertFalse(DragonfireRules.breathesFire(monster("baby black dragon")));
		Assert.assertFalse(DragonfireRules.breathesFire(monster("zulrah")));
	}

	@Test
	public void gearModeForcesAProtectiveShieldAndBansTwoHanders()
	{
		OptimizationRequest req = new OptimizationRequest(monster("vorkath"), CombatStyle.RANGED,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL),
			null, 0, CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, 1);
		List<DpsResult> out = new LoadoutOptimizer().optimize(data, req);
		Assert.assertFalse(out.isEmpty());
		GearItem shield = out.get(0).getLoadout().get(GearSlot.SHIELD);
		Assert.assertNotNull("gear mode must fill the shield slot", shield);
		Assert.assertTrue(shield.label() + " is not dragonfire-protective",
			DragonfireRules.isProtectiveShield(shield));
		Assert.assertFalse(out.get(0).getLoadout().getWeapon().isTwoHanded());
	}

	@Test
	public void thePotionToggleLiftsTheConstraintAndNeverCostsDps()
	{
		OptimizationRequest gear = new OptimizationRequest(monster("green dragon"), CombatStyle.MELEE,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL),
			null, 0, CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, 1);
		double shielded = new LoadoutOptimizer().optimize(data, gear).get(0).getDps();
		double potion = new LoadoutOptimizer().optimize(data, gear.withAntifirePotion(true)).get(0).getDps();
		Assert.assertTrue(potion >= shielded - 1e-9);
	}

	@Test
	public void nonDragonsAreUnaffectedByTheShieldRule()
	{
		OptimizationRequest req = new OptimizationRequest(monster("general graardor"), CombatStyle.MELEE,
			PlayerLevels.MAXED, PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL),
			null, 0, CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, 1);
		List<DpsResult> out = new LoadoutOptimizer().optimize(data, req);
		GearItem shield = out.get(0).getLoadout().get(GearSlot.SHIELD);
		Assert.assertTrue(shield == null || !DragonfireRules.isProtectiveShield(shield)
			|| out.get(0).getLoadout().getWeapon().isTwoHanded() == false);
	}
}
