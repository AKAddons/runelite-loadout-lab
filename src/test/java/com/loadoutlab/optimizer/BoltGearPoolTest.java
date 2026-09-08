package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Field report (Andrew, 2026-09-08): simmed chaos gauntlets never beat
 * the tormented bracelet on a Fire Bolt card. The per-slot pool keeps the
 * top ten by raw magic stats and the gauntlets score zero there - their
 * value lives in the DPS model, like the leaf-bladed battleaxe. Same for
 * the element amulets, which tie a crowd of glories at +10. */
class BoltGearPoolTest
{
	private static LoadoutData data;
	private static MonsterStats goblin;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		goblin = data.searchMonsters("Goblin", 1).get(0);
	}

	@Test
	@DisplayName("chaos gauntlets reach the beam past ten better-statted gloves and win the Fire Bolt card")
	void gauntlets() throws Exception
	{
		Map<Integer, Integer> owned = own("staff of fire", "occult necklace", "chaos gauntlets", "tormented bracelet",
			"barrows gloves", "infinity gloves", "dragon gloves", "rune gloves", "lunar gloves", "bloodbark gauntlets",
			"adamant gloves", "swampbark gauntlets", "regen bracelet", "mystic gloves");
		assertEquals(777, slot(owned, "Fire Bolt", GearSlot.HANDS).getId(), "chaos gauntlets: 16 max hit beats 13");
	}

	@Test
	@DisplayName("an amulet of air reaches the beam past ten +10 necks and wins the Wind Bolt card")
	void elementAmulet() throws Exception
	{
		Map<Integer, Integer> owned = own("staff of air", "occult necklace", "amulet of air", "amulet of fury",
			"amulet of glory", "amulet of magic", "amulet of magic (t)", "dragonbone necklace", "bonecrusher necklace",
			"3rd age amulet", "amulet of the damned", "amulet of power", "amulet of glory (t)");
		assertEquals(34407, slot(owned, "Wind Bolt", GearSlot.NECK).getId(), "amulet of air: 14 max hit beats 13");
	}

	private static Map<Integer, Integer> own(String... names)
	{
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : names)
		{
			owned.put(byName(name).getId(), 1);
		}
		return owned;
	}

	private static GearItem byName(String nameLower)
	{
		for (GearItem g : data.getGearItems())
		{
			if (g.getNameLower().equals(nameLower))
			{
				return g;
			}
		}
		throw new AssertionError("missing from the gear data: " + nameLower);
	}

	private static GearItem slot(Map<Integer, Integer> owned, String spellName, GearSlot slot) throws Exception
	{
		SpellStats spell = data.getSpells().stream().filter(s -> s.getName().equals(spellName)).findFirst().orElseThrow();
		OptimizerService service = new OptimizerService(data);
		try
		{
			CountDownLatch done = new CountDownLatch(1);
			AtomicReference<Map<CombatStyle, OptimizerService.StyleResult>> out = new AtomicReference<>();
			ServiceCalls.bestPerStyle(service, goblin, PlayerLevels.MAXED, PlayerLevels.MAXED,
				PrayerUnlocks.ALL, RequirementProfile.MAXED, new OwnedItems(owned, true), 1, false, false, "",
				Collections.emptySet(), -1, OptimizationRequest.DEFAULT_RISK_BUDGET_GP, false, Collections.emptySet(), 0,
				Collections.emptyMap(), spell, results ->
				{
					out.set(results);
					done.countDown();
				});
			assertTrue(done.await(120, TimeUnit.SECONDS));
			return out.get().get(CombatStyle.MAGIC).owned.get(0).getLoadout().get(slot);
		}
		finally
		{
			service.shutdown();
		}
	}
}
