package com.ll.collection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.*;
import net.runelite.client.config.ConfigManager;

// A scoped set of item ids: the exclusions ("excludedItems"), the dream
// items ("dreamItems") and the protect-only items ("protectOnlyItems").
public class IdSetStore implements ScopedStore {
static final String CONFIG_GROUP = "loadoutlab";

ConfigManager cfg;
Gson gson;
Set<Integer> ids = new LinkedHashSet<>();
// "std" / "seasonal", plus ".<accountHash>" once a character is known.
// Field report 2026-08-25: switching from a main to a test account showed
// the SAME exclude and sim lists, because these keys were global while the
// bank ledger next door was already per-character.
String ws;

// The unscoped key this store used before 0.4.1 - read once so an
// existing install keeps its list when the key gains the account hash.
String lk;

public IdSetStore(ConfigManager cfg, Gson gson, String lk) {
    this.cfg = cfg;
    this.gson = gson;
    this.lk = lk;
    load();
}

String key() { return ScopedKeys.key(ws, lk); }

public synchronized void loadScope(String scope) {
    ws = scope;
    load();
}

public synchronized void reload() { load(); }

final void load() {
    ids.clear();
    Set<Integer> stored = ScopedKeys.parse(gson, ScopedKeys.read(cfg, CONFIG_GROUP, ws, lk),
        new TypeToken<Set<Integer>>(){}.getType());
    if (stored != null) ids.addAll(stored);
}

final void save() { cfg.setConfiguration(CONFIG_GROUP, key(), gson.toJson(ids)); }

public synchronized boolean toggle(int itemId) {
    boolean added = !ids.remove(itemId) && ids.add(itemId);
    save();
    return added;
}

public synchronized void clear() {
    ids.clear();
    save();
}

public synchronized boolean contains(int itemId) { return ids.contains(itemId); }

public synchronized Set<Integer> snapshot() { return new LinkedHashSet<>(ids); }
}
