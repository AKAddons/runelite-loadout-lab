package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterGroups;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Raid stipulations ride the group data (Andrew 2026-09-10: "it's probably
 * ok to have specific raid-specific stipulations for each grouping").
 * N's ToA reports (Discord 2026-09-09) are the bank shape here. */
class ToaStipulationsTest
{
	private static LoadoutData data;
	private static List<MonsterStats> toa;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		toa = MonsterGroups.load(data).stream().filter(g -> g.getName().equals("Tombs of Amascut"))
			.findFirst().orElseThrow().getMobs();
	}

	private static Map<Integer, Integer> bank()
	{
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"neitiznot faceguard", "fire cape", "amulet of rancour", "osmumten's fang",
			"crystal helm", "crystal body", "crystal legs", "barrows gloves", "primordial boots", "berserker ring (i)",
			"ava's assembler", "necklace of anguish", "bow of faerdhinen", "saradomin d'hide boots", "ring of shadows",
			"tumeken's shadow", "ahrim's robetop", "ahrim's robeskirt", "occult necklace", "imbued zamorak cape",
			"tormented bracelet", "eternal boots", "bandos chestplate", "bandos godsword", "dragon dagger", "noxious halberd"})
		{
			owned.put(byName(name).getId(), 1);
		}
		return owned;
	}

	private static GearItem byName(String nameLower)
	{
		GearItem best = null;
		for (GearItem g : data.getGearItems())
		{
			if (g.getNameLower().equals(nameLower) && g.isStandardGear()
				&& (best == null || "Charged".equals(g.getVersion()) || "Active".equals(g.getVersion())
					|| "Undamaged".equals(g.getVersion()) || "Normal".equals(g.getVersion())))
			{
				best = g;
			}
		}
		if (best == null)
		{
			throw new AssertionError("missing from the gear data: " + nameLower);
		}
		return best;
	}

	private static OptimizerService.StyleResult result(OptimizerService.RosterResult roster, String nickPart, CombatStyle style)
	{
		for (int j = 0; j < roster.mobs.size(); j++)
		{
			if (roster.mobs.get(j).label().contains(nickPart))
			{
				return roster.perMob.get(j).get(style);
			}
		}
		throw new AssertionError("no roster mob matching " + nickPart);
	}

	@Test
	@DisplayName("the Wardens' spec is the dragon dagger when one is banked, even with a godsword beside it")
	void wardensSpecIsTheDagger() throws Exception
	{
		OptimizerService service = new OptimizerService(data);
		try
		{
			OptimizerService.RosterResult roster = ServiceCalls.runRoster(service, toa, bank(), 16, Collections.emptyMap());
			OptimizerService.StyleResult core = result(roster, "Core", CombatStyle.MELEE);
			assertNotNull(core.specWeapon, "the core dump has a spec");
			assertTrue(core.specWeapon.getNameLower().contains("dragon dagger"), core.specWeapon.getName());
			// The kit carries ONE spec weapon for the trip, so the Wardens'
			// dagger rides every card - Ba-Ba included.
			OptimizerService.StyleResult baba = result(roster, "Ba-Ba", CombatStyle.MELEE);
			assertNotNull(baba.specWeapon);
			assertTrue(baba.specWeapon.getNameLower().contains("dragon dagger"), baba.specWeapon.getName());
		}
		finally
		{
			service.shutdown();
		}
	}

	@Test
	@DisplayName("at a ten-slot inventory the shared kit still dresses Elidinis' Warden P2 in the shadow set: the P2 Wardens weigh 3x")
	void wardenP2DecidesTheKit() throws Exception
	{
		OptimizerService service = new OptimizerService(data);
		try
		{
			OptimizerService.RosterResult roster = ServiceCalls.runRoster(service, toa, bank(), 10, Collections.emptyMap());
			OptimizerService.StyleResult p2 = result(roster, "Elidinis' Warden", CombatStyle.MAGIC);
			String worn = p2.owned.get(0).getLoadout().getGear().values().stream()
				.map(GearItem::getNameLower).collect(java.util.stream.Collectors.joining(", "));
			assertTrue(worn.contains("occult necklace"), worn);
			assertTrue(worn.contains("ahrim's robeskirt"), worn);
		}
		finally
		{
			service.shutdown();
		}
	}
}
