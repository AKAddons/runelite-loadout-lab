package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Tables;
import com.ll.data.Monster;
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
class SeaMeleeTest {
	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	@Test
	@DisplayName("every roster sea monster rejects melee; land mobs are untouched")
	void seaMonstersRejectMelee() {
		Kit bare = new Kit(new EnumMap<>(com.ll.data.Slot.class));
		for (String name : com.ll.data.NavalCombat.NAVAL) {
			Monster mob = data.searchMonsters(name, 1).get(0);
			assertTrue(MonsterMechanics.isImmune(mob, Style.MELEE, bare, null),
				name + " must reject melee at sea");
			assertFalse(MonsterMechanics.isImmune(mob, Style.RANGED, bare, null),
				name + " must still take ranged");
		}
		Monster graardor = data.searchMonsters("general graardor", 1).get(0);
		assertFalse(MonsterMechanics.isImmune(graardor, Style.MELEE, bare, null));
	}
}
