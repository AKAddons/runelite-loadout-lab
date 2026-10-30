// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import lombok.Getter;
import java.util.*;

@Getter
public class OwnedItems {
    public static final OwnedItems EMPTY = new OwnedItems(Map.of(), false);

    public final Map<Integer, Integer> quantities;
    public final boolean bankScanned;
    // The owned ids at hand - equipped, inventory or bank - or null when
    // unknown (every owned item counts as at hand). Storage (the costume
    // room, STASH, a cargo hold) is owned but a trip away.
    public final Set<Integer> atHand;

    public OwnedItems(Map<Integer, Integer> quantities, boolean bankScanned) { this(quantities, bankScanned, null); }

    public OwnedItems(Map<Integer, Integer> quantities, boolean bankScanned, Set<Integer> atHand) {
        this.quantities = new HashMap<>(quantities);
        this.bankScanned = bankScanned;
        this.atHand = atHand == null ? null : Set.copyOf(atHand);
    }

    public boolean owns(int itemId) { return quantities.getOrDefault(itemId, 0) > 0; }

    // Owned and not only in storage.
    public boolean atHand(int itemId) { return owns(itemId) && (atHand == null || atHand.contains(itemId)); }

    public int size() { return quantities.size(); }
}
