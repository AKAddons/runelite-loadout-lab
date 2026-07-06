package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import org.junit.Assert;
import org.junit.Test;

public class CrystalSetTest
{
	@Test
	public void fullCrystalSetBoostsTheBofaByThirtyAccFifteenDamage()
	{
		LoadoutData data = new DataService().load();
		MonsterStats graardor = data.searchMonsters("general graardor", 1).get(0);
		OptimizationRequest request = new OptimizationRequest(
			graardor, CombatStyle.RANGED, PlayerLevels.MAXED,
			PrayerBonuses.NONE, null, 0,
			CandidateMode.ALL_STANDARD, true, false,
			OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
		DpsResult bare = calc(data, request, "Bow of faerdhinen", null);
		DpsResult withSet = calc(data, request, "Bow of faerdhinen",
			new String[]{"Crystal helm", "Crystal body", "Crystal legs"});
		// Accuracy multiplier 26/20 on the roll; damage max x46/40.
		Assert.assertTrue(withSet.getAttackRoll() > bare.getAttackRoll() * 13 / 10 - 5);
		Assert.assertTrue(withSet.getMaxHit() >= bare.getMaxHit() * 46 / 40 - 1);
		Assert.assertTrue(withSet.getDps() > bare.getDps() * 1.15);
	}

	private static DpsResult calc(LoadoutData data, OptimizationRequest request, String weapon, String[] armour)
	{
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.put(GearSlot.WEAPON, byName(data, weapon));
		if (armour != null)
		{
			for (String name : armour)
			{
				GearItem item = byName(data, name);
				gear.put(item.getSlot(), item);
			}
		}
		return new DpsCalculator().calculate(request, new Loadout(gear));
	}

	private static GearItem byName(LoadoutData data, String name)
	{
		return data.getGearItems().stream()
			.filter(g -> g.getName().equalsIgnoreCase(name) && g.isStandardGear())
			.findFirst().orElseThrow(() -> new AssertionError("missing " + name));
	}
}
