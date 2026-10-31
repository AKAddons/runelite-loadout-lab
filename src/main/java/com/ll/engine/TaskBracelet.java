package com.ll.engine;

import com.ll.data.*;
import java.util.*;

// The slayer-task bracelet preference (field report 2026-10-17: "include
// ... Expeditious bracelet for all setups"; Andrew: an option for slayer
// tasks, reachable from the settings panel and the bracelet slot). On task
// every style card's hands slot is pinned to it unless the mob pins its
// own hands slot; off task, and on raids (never a task context), nothing
// changes. Unpriced like any pin - the model cannot value a task bracelet.
public enum TaskBracelet {
NONE(0), EXPEDITIOUS_BRACELET(21177), BRACELET_OF_SLAUGHTER(21183);

int itemId;

TaskBracelet(int itemId) { this.itemId = itemId; }

// The mob's pins with this bracelet in HANDS for every concrete style
// that has no hands pin of its own; the input is never mutated. Only a
// slayer monster on task counts - the task param can outlive the chip
// (Andrew 2026-10-18: Orca wore the bracelet).
public static Map<Style, Map<Slot, Integer>> overlay(
    Map<Style, Map<Slot, Integer>> pins, Monster monster, boolean onTask, TaskBracelet choice) {
    if (!onTask || monster == null || !monster.slayerMonster || choice == null || choice == NONE) return pins;
    var out = new EnumMap<Style, Map<Slot, Integer>>(Style.class);
    out.putAll(pins);
    for (Style style : Style.concreteValues()) {
        var mine = new EnumMap<Slot, Integer>(Slot.class);
        Map<Slot, Integer> had = pins.get(style);
        if (had != null) mine.putAll(had);
        mine.putIfAbsent(Slot.HANDS, choice.itemId);
        out.put(style, mine);
    }
    return out;
}
}
