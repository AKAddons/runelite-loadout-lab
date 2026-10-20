package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kendyy, Discord 2026-10-20: "Barrows suggesting bolts rather than
 * blessing, but no crossbow and none of the recommended styles are range
 * ... it might want to suggest ghommals hilt 2 to negate the prayer
 * drain". Andrew: "pretty much everyone with that tier of the hilt will
 * opt to use it in barrows" - a default pin, not a dps pick. Wiki: no
 * prayer drain at Barrows while a Ghommal's hilt (tier 2+) is equipped;
 * the hilt-fitted avernic defender keeps the benefit.
 */
class BarrowsTest
{
	private static LoadoutData data;
	private static List<MonsterStats> brothers;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		brothers = MonsterGroups.load(data).stream().filter(g -> g.getName().equals("Barrows"))
			.findFirst().orElseThrow().getMobs();
	}

	private static Map<Integer, Integer> bank(String... extra)
	{
		Map<Integer, Integer> owned = new HashMap<>();
		String[] base = {"abyssal whip", "dragon defender", "neitiznot faceguard", "fighter torso", "dragon platelegs",
			"barrows gloves", "dragon boots", "berserker ring (i)", "amulet of fury", "fire cape", "trident of the swamp",
			"ahrim's hood", "ahrim's robetop", "ahrim's robeskirt", "occult necklace", "imbued saradomin cape",
			"tormented bracelet", "eternal boots", "rune crossbow", "runite bolts", "rada's blessing 4", "dragon dagger"};
		for (String[] names : new String[][]{base, extra})
		{
			for (String n : names)
			{
				GearItem item = data.getGearItems().stream().filter(g -> g.getNameLower().equals(n) && g.isStandardGear())
					.findFirst().orElseThrow(() -> new AssertionError("missing " + n));
				owned.put(item.getId(), 1);
			}
		}
		return owned;
	}

	private static OptimizerService.RosterResult roster(Map<Integer, Integer> owned) throws Exception
	{
		OptimizerService service = new OptimizerService(data);
		try
		{
			return ServiceCalls.runRoster(service, brothers, owned, 4, Collections.emptyMap());
		}
		finally
		{
			service.shutdown();
		}
	}

	private static GearItem shown(OptimizerService.RosterResult r, int mob, CombatStyle style, GearSlot slot)
	{
		OptimizerService.StyleResult sr = r.perMob.get(mob).get(style);
		return sr == null || sr.owned.isEmpty() ? null : sr.owned.get(0).getLoadout().get(slot);
	}

	@Test
	@DisplayName("with a hilt of tier 2 or higher owned, every brother's one-handed set wears it")
	void theHiltIsTheDefaultShield() throws Exception
	{
		OptimizerService.RosterResult r = roster(bank("ghommal's hilt 2"));
		for (int j = 0; j < r.mobs.size(); j++)
		{
			for (CombatStyle style : new CombatStyle[]{CombatStyle.MELEE, CombatStyle.MAGIC})
			{
				GearItem shield = shown(r, j, style, GearSlot.SHIELD);
				assertNotNull(shield, r.mobs.get(j).getName() + " " + style);
				assertEquals("ghommal's hilt 2", shield.getNameLower(), r.mobs.get(j).getName() + " " + style);
			}
		}
	}

	@Test
	@DisplayName("without a hilt the sets are unchanged: the defender stays")
	void noHiltNoRule() throws Exception
	{
		OptimizerService.RosterResult r = roster(bank());
		for (int j = 0; j < r.mobs.size(); j++)
		{
			GearItem shield = shown(r, j, CombatStyle.MELEE, GearSlot.SHIELD);
			assertNotNull(shield);
			assertEquals("dragon defender", shield.getNameLower(), r.mobs.get(j).getName());
		}
	}

	@Test
	@DisplayName("no brother is answered with ranged, so the magic card keeps its blessing, not the crossbow's bolts")
	void blessingNotBolts() throws Exception
	{
		OptimizerService.RosterResult r = roster(bank());
		for (int j = 0; j < r.mobs.size(); j++)
		{
			GearItem ammo = shown(r, j, CombatStyle.MAGIC, GearSlot.AMMO);
			assertNotNull(ammo, r.mobs.get(j).getName());
			assertEquals("rada's blessing 4", ammo.getNameLower(), r.mobs.get(j).getName());
		}
	}
}
