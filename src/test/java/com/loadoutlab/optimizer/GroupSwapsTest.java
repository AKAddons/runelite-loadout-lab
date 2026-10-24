package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Swaps on a group search (ISS-01M5JAGFEJ5QHXE37B37XM8HJJ, Andrew 2026-10-22:
 * "ladder view is disabled for group searches but i think it should be
 * included" - scored by "The whole group").
 */
class GroupSwapsTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	private static int id(String nameLower)
	{
		return data.getGearItems().stream()
			.filter(g -> g.getNameLower().equals(nameLower) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + nameLower)).getId();
	}

	@Test
	@DisplayName("a group search has swaps: one strip on every member, scored by the group's HP-weighted DPS")
	void groupStrip() throws Exception
	{
		List<MonsterStats> mobs = List.of(data.searchMonsters("abyssal demon", 1).get(0),
			data.searchMonsters("black demon", 1).get(0));
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"abyssal whip", "dragon scimitar", "rune scimitar", "fighter torso",
			"dragon defender"})
		{
			owned.put(id(name), 1);
		}
		OptimizerService service = new OptimizerService(data);
		try
		{
			OptimizerService.RosterResult roster = ServiceCalls.runRoster(service, mobs, owned, 0, Map.of());
			OptimizerService.StyleResult first = roster.perMob.get(0).get(CombatStyle.MELEE);
			OptimizerService.StyleResult second = roster.perMob.get(1).get(CombatStyle.MELEE);
			OptimizerService.Ladder weapon = first.ladder.get(GearSlot.WEAPON);
			assertNotNull(weapon, "the group's weapon slot has swaps");
			assertTrue(weapon.group, "scored across the group");
			assertSame(weapon, second.ladder.get(GearSlot.WEAPON), "every member shows the same strip");
			for (OptimizerService.Rung rung : weapon.up)
			{
				assertTrue(rung.dps > weapon.baseDps, rung.item.getName() + " helps the group");
			}
			for (OptimizerService.Rung rung : weapon.down)
			{
				assertTrue(rung.owned && rung.dps < weapon.baseDps, rung.item.getName() + " is yours and worse");
			}
			assertFalse(weapon.up.isEmpty());
		}
		finally
		{
			service.shutdown();
		}
	}
}
