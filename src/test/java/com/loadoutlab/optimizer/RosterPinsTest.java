package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellStats;
import com.loadoutlab.engine.CombatStyle;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** A mixed roster reads each member's OWN pins (ISS-01M4Y2VJ8DWBBKFR1HP9NR45NG).
 * Apathy (Discord, 2026-10-09): "Pinning a new spell sdhould change the
 * spell of the set." Until this issue computeRoster handed the optimizer
 * the FIRST member's pins, pinned spell and pinned spec for everyone. */
class RosterPinsTest
{
	private static final int WHIP = 4151;
	private static final int DRAGON_SCIMITAR = 4587;
	private static LoadoutData data;
	private static MonsterStats graardor;
	private static MonsterStats fireGiant;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
		graardor = data.searchMonsters("general graardor", 1).get(0);
		fireGiant = data.searchMonsters("fire giant", 1).get(0);
	}

	@Test
	@DisplayName("a spell pinned on the second member's profile is cast against that member only")
	void spellPinnedOnTheSecondMember() throws Exception
	{
		SpellStats fireBolt = spell("Fire Bolt");
		Map<Integer, SpellStats> spellByMob =
			Collections.singletonMap(fireGiant.profileId(), fireBolt);
		OptimizerService service = new OptimizerService(data);
		try
		{
			OptimizerService.RosterResult roster = ServiceCalls.runRosterPinned(service,
				Arrays.asList(graardor, fireGiant), owned(), 1,
				Collections.emptyMap(), spellByMob, Collections.emptyMap());
			assertEquals("Fire Bolt", spellAgainst(roster, 1), "the fire giant's magic set casts the pin");
			assertNotEquals("Fire Bolt", spellAgainst(roster, 0), "Graardor's magic set keeps its own spell");
		}
		finally
		{
			service.shutdown();
		}
	}

	@Test
	@DisplayName("an item pinned on the second member's weapon slot is worn against that member only")
	void itemPinnedOnTheSecondMember() throws Exception
	{
		Map<GearSlot, Integer> weapon = new EnumMap<>(GearSlot.class);
		weapon.put(GearSlot.WEAPON, DRAGON_SCIMITAR);
		Map<CombatStyle, Map<GearSlot, Integer>> melee = new EnumMap<>(CombatStyle.class);
		melee.put(CombatStyle.MELEE, weapon);
		Map<Integer, Map<CombatStyle, Map<GearSlot, Integer>>> pinsByMob =
			Collections.singletonMap(fireGiant.profileId(), melee);
		OptimizerService service = new OptimizerService(data);
		try
		{
			OptimizerService.RosterResult roster = ServiceCalls.runRosterPinned(service,
				Arrays.asList(graardor, fireGiant), owned(), 1,
				pinsByMob, Collections.emptyMap(), Collections.emptyMap());
			assertEquals(DRAGON_SCIMITAR, weaponAgainst(roster, 1), "the fire giant wears the pin");
			assertEquals(WHIP, weaponAgainst(roster, 0), "Graardor keeps the whip");
		}
		finally
		{
			service.shutdown();
		}
	}

	private static Map<Integer, Integer> owned()
	{
		Map<Integer, Integer> owned = new HashMap<>();
		owned.put(WHIP, 1);
		owned.put(DRAGON_SCIMITAR, 1);
		owned.put(1387, 1);     // staff of fire
		owned.put(12002, 1);    // occult necklace
		owned.put(554, 1000);   // fire
		owned.put(562, 1000);   // chaos
		owned.put(560, 1000);   // death
		owned.put(565, 1000);   // blood
		return owned;
	}

	private static SpellStats spell(String name)
	{
		return data.getSpells().stream().filter(s -> s.getName().equals(name))
			.findFirst().orElseThrow();
	}

	private static String spellAgainst(OptimizerService.RosterResult roster, int member)
	{
		return roster.perMob.get(member).get(CombatStyle.MAGIC).owned.get(0).getSpellName();
	}

	private static int weaponAgainst(OptimizerService.RosterResult roster, int member)
	{
		return roster.perMob.get(member).get(CombatStyle.MELEE).owned.get(0)
			.getLoadout().get(GearSlot.WEAPON).getId();
	}
}
