package com.loadoutlab.collection;

import java.util.*;
import java.util.function.IntUnaryOperator;

// The rune pouch as a ledger source (field report 2026-10-10: runes
// moved into the pouch vanished and Fire Wave fell off the magic set).
// The pouch is not an item container: each slot is a TYPE varbit (a key
// into the client's RUNEPOUCH_RUNE enum, 0 = empty) and a QUANTITY varbit.
// Pure over the two value arrays so the plugin's varbit reads stay thin.
public class RunePouchScan {

// itemId -> quantity for every filled slot; an empty or zero slot adds nothing.
public static Map<Integer, Integer> items(int[] types, int[] quantities, IntUnaryOperator typeToItem) {
    Map<Integer, Integer> out = new HashMap<>();
    for (int i = 0; i < types.length && i < quantities.length; i++) {
        if (types[i] > 0 && quantities[i] > 0) {
            int item = typeToItem.applyAsInt(types[i]);
            if (item > 0) out.merge(item, quantities[i], Integer::sum);
        }
    }
    return out;
}
}
