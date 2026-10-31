// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.ll.engine;

import lombok.Getter;
import java.util.*;

@Getter
public class Owned {
public static final Owned EMPTY = new Owned(Map.of(), false);

public Map<Integer, Integer> quantities;
public boolean bankScanned;
// The owned ids at hand - equipped, inventory or bank - or null when
// unknown (every owned item counts as at hand). Storage (the costume
// room, STASH, a cargo hold) is owned but a trip away.
public Set<Integer> ah;

public Owned(Map<Integer, Integer> quantities, boolean bankScanned) { this(quantities, bankScanned, null); }

public Owned(Map<Integer, Integer> quantities, boolean bankScanned, Set<Integer> ah) {
    this.quantities = new HashMap<>(quantities);
    this.bankScanned = bankScanned;
    this.ah = ah == null ? null : Set.copyOf(ah);
}

public boolean owns(int itemId) { return quantities.getOrDefault(itemId, 0) > 0; }

// Owned and not only in storage.
public boolean ah(int itemId) { return owns(itemId) && (ah == null || ah.contains(itemId)); }

int size() { return quantities.size(); }
}
