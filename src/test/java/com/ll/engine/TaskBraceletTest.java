package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
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
	private static final Tables DATA = new DataService().load();
	private static final Monster DEMON = DATA.searchMonsters("abyssal demon", 1).get(0);
	private static final Monster ORCA = DATA.searchMonsters("orca", 1).get(0);

	private static final int EXPEDITIOUS = 21177;
	private static final int BARROWS_GLOVES = 7462;

	private static Map<Style, Map<Slot, Integer>> meleeHandsPinned() {
		EnumMap<Style, Map<Slot, Integer>> pins = new EnumMap<>(Style.class);
		EnumMap<Slot, Integer> melee = new EnumMap<>(Slot.class);
		melee.put(Slot.HANDS, BARROWS_GLOVES);
		pins.put(Style.MELEE, melee);
		return pins;
	}

	@Test
	@DisplayName("on task: every concrete style's hands slot is pinned to the bracelet")
	void onTaskPinsEveryStyle() {
		Map<Style, Map<Slot, Integer>> out =
			TaskBracelet.overlay(Map.of(), DEMON, true, TaskBracelet.EXPEDITIOUS_BRACELET);
		for (Style style : Style.concreteValues()) {
			assertEquals(EXPEDITIOUS, out.get(style).get(Slot.HANDS), style.name());
		}
	}

	@Test
	@DisplayName("the mob's own hands pin wins on its style; the others still get the bracelet")
	void mobPinWins() {
		Map<Style, Map<Slot, Integer>> in = meleeHandsPinned();
		Map<Style, Map<Slot, Integer>> out =
			TaskBracelet.overlay(in, DEMON, true, TaskBracelet.BRACELET_OF_SLAUGHTER);
		assertEquals(BARROWS_GLOVES, out.get(Style.MELEE).get(Slot.HANDS));
		assertEquals(21183, out.get(Style.RANGED).get(Slot.HANDS));
		assertEquals(21183, out.get(Style.MAGIC).get(Slot.HANDS));
		assertNull(in.get(Style.RANGED), "the input map must not be mutated");
	}

	@Test
	@DisplayName("a monster that is never a slayer task gets no bracelet, task param or not (Orca, 2026-10-18)")
	void nonSlayerMonsterIsUntouched() {
		Map<Style, Map<Slot, Integer>> in = meleeHandsPinned();
		assertFalse(ORCA.isSlayerMonster(), "the fixture needs a non-slayer monster");
		assertSame(in, TaskBracelet.overlay(in, ORCA, true, TaskBracelet.EXPEDITIOUS_BRACELET));
		assertSame(in, TaskBracelet.overlay(in, null, true, TaskBracelet.EXPEDITIOUS_BRACELET));
	}

	@Test
	@DisplayName("off task, or with None chosen, the pins pass through untouched")
	void offTaskOrNoneIsIdentity() {
		Map<Style, Map<Slot, Integer>> in = meleeHandsPinned();
		assertSame(in, TaskBracelet.overlay(in, DEMON, false, TaskBracelet.EXPEDITIOUS_BRACELET));
		assertSame(in, TaskBracelet.overlay(in, DEMON, true, TaskBracelet.NONE));
		assertSame(in, TaskBracelet.overlay(in, DEMON, true, null));
	}
}
