package com.loadoutlab.engine;

import com.loadoutlab.data.GearSlot;
import java.util.*;
import java.util.Map;

/**
 * The slayer-task bracelet preference (miko, Discord 2026-10-17: "include
 * ... Expeditious bracelet for all setups"; Andrew: an option for slayer
 * tasks, reachable from the settings panel and the bracelet slot). On task
 * every style card's hands slot is pinned to it unless the mob pins its
 * own hands slot; off task, and on raids (never a task context), nothing
 * changes. Unpriced like any pin - the model cannot value a task bracelet.
 */
public enum TaskBracelet
{
	NONE(0),
	EXPEDITIOUS_BRACELET(21177),
	BRACELET_OF_SLAUGHTER(21183);

	public final int itemId;

	TaskBracelet(int itemId)
	{
		this.itemId = itemId;
	}

	/** The mob's pins with this bracelet in HANDS for every concrete style
	 * that has no hands pin of its own; the input is never mutated. */
	public static Map<CombatStyle, Map<GearSlot, Integer>> overlay(
		Map<CombatStyle, Map<GearSlot, Integer>> pins, boolean onTask, TaskBracelet choice)
	{
		if (!onTask || choice == null || choice == NONE)
		{
			return pins;
		}
		EnumMap<CombatStyle, Map<GearSlot, Integer>> out = new EnumMap<>(CombatStyle.class);
		out.putAll(pins);
		for (CombatStyle style : CombatStyle.concreteValues())
		{
			EnumMap<GearSlot, Integer> mine = new EnumMap<>(GearSlot.class);
			Map<GearSlot, Integer> had = pins.get(style);
			if (had != null)
			{
				mine.putAll(had);
			}
			mine.putIfAbsent(GearSlot.HANDS, choice.itemId);
			out.put(style, mine);
		}
		return out;
	}
}
