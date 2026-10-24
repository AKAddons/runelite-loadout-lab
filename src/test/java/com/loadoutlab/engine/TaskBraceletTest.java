package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * miko (Discord 2026-10-17) wanted the Expeditious bracelet in every
 * setup; Andrew made it a slayer-task option. The overlay turns the
 * preference into hands pins the optimizer already honours.
 */
class TaskBraceletTest {
	private static final LoadoutData DATA = new DataService().load();
	private static final MonsterStats DEMON = DATA.searchMonsters("abyssal demon", 1).get(0);
	private static final MonsterStats ORCA = DATA.searchMonsters("orca", 1).get(0);

	private static final int EXPEDITIOUS = 21177;
	private static final int BARROWS_GLOVES = 7462;

	private static Map<CombatStyle, Map<GearSlot, Integer>> meleeHandsPinned() {
		EnumMap<CombatStyle, Map<GearSlot, Integer>> pins = new EnumMap<>(CombatStyle.class);
		EnumMap<GearSlot, Integer> melee = new EnumMap<>(GearSlot.class);
		melee.put(GearSlot.HANDS, BARROWS_GLOVES);
		pins.put(CombatStyle.MELEE, melee);
		return pins;
	}

	@Test
	@DisplayName("on task: every concrete style's hands slot is pinned to the bracelet")
	void onTaskPinsEveryStyle() {
		Map<CombatStyle, Map<GearSlot, Integer>> out =
			TaskBracelet.overlay(Map.of(), DEMON, true, TaskBracelet.EXPEDITIOUS_BRACELET);
		for (CombatStyle style : CombatStyle.concreteValues()) {
			assertEquals(EXPEDITIOUS, out.get(style).get(GearSlot.HANDS), style.name());
		}
	}

	@Test
	@DisplayName("the mob's own hands pin wins on its style; the others still get the bracelet")
	void mobPinWins() {
		Map<CombatStyle, Map<GearSlot, Integer>> in = meleeHandsPinned();
		Map<CombatStyle, Map<GearSlot, Integer>> out =
			TaskBracelet.overlay(in, DEMON, true, TaskBracelet.BRACELET_OF_SLAUGHTER);
		assertEquals(BARROWS_GLOVES, out.get(CombatStyle.MELEE).get(GearSlot.HANDS));
		assertEquals(21183, out.get(CombatStyle.RANGED).get(GearSlot.HANDS));
		assertEquals(21183, out.get(CombatStyle.MAGIC).get(GearSlot.HANDS));
		assertNull(in.get(CombatStyle.RANGED), "the input map must not be mutated");
	}

	@Test
	@DisplayName("a monster that is never a slayer task gets no bracelet, task param or not (Orca, 2026-10-18)")
	void nonSlayerMonsterIsUntouched() {
		Map<CombatStyle, Map<GearSlot, Integer>> in = meleeHandsPinned();
		assertFalse(ORCA.isSlayerMonster(), "the fixture needs a non-slayer monster");
		assertSame(in, TaskBracelet.overlay(in, ORCA, true, TaskBracelet.EXPEDITIOUS_BRACELET));
		assertSame(in, TaskBracelet.overlay(in, null, true, TaskBracelet.EXPEDITIOUS_BRACELET));
	}

	@Test
	@DisplayName("off task, or with None chosen, the pins pass through untouched")
	void offTaskOrNoneIsIdentity() {
		Map<CombatStyle, Map<GearSlot, Integer>> in = meleeHandsPinned();
		assertSame(in, TaskBracelet.overlay(in, DEMON, false, TaskBracelet.EXPEDITIOUS_BRACELET));
		assertSame(in, TaskBracelet.overlay(in, DEMON, true, TaskBracelet.NONE));
		assertSame(in, TaskBracelet.overlay(in, DEMON, true, null));
	}
}
