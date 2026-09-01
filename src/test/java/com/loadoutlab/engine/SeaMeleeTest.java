package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Boat combat's first rule (wiki; Andrew's catch 2026-08-31 - "are you sure
 * you can use a noxious halberd on boat?"): melee cannot attack at sea. A
 * sea monster is melee-immune through the same mechanism Zulrah uses, so no
 * melee card is ever produced for one.
 */
class SeaMeleeTest
{
	private static LoadoutData data;

	@BeforeAll
	static void load()
	{
		data = new DataService().load();
	}

	@Test
	@DisplayName("every roster sea monster rejects melee; land mobs are untouched")
	void seaMonstersRejectMelee()
	{
		Loadout bare = new Loadout(new EnumMap<>(com.loadoutlab.data.GearSlot.class));
		for (String name : com.loadoutlab.data.NavalCombat.navalNames())
		{
			MonsterStats mob = data.searchMonsters(name, 1).get(0);
			assertTrue(MonsterMechanics.isImmune(mob, CombatStyle.MELEE, bare, null),
				name + " must reject melee at sea");
			assertFalse(MonsterMechanics.isImmune(mob, CombatStyle.RANGED, bare, null),
				name + " must still take ranged");
		}
		MonsterStats graardor = data.searchMonsters("general graardor", 1).get(0);
		assertFalse(MonsterMechanics.isImmune(graardor, CombatStyle.MELEE, bare, null));
	}
}
