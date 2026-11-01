package com.ll.collection;

import com.google.gson.Gson;
import java.lang.reflect.Type;
import java.util.*;
import net.runelite.client.config.ConfigManager;

// A per-character map persisted as one JSON config entry: the always-filter
// list and the supply defaults share this exact shape (scoped key, load on
// scope change, unset when empty, corrupt JSON starts fresh).
abstract class ScopedMapStore<K, V> implements ScopedStore {
static final String CONFIG_GROUP = "loadoutlab";

ConfigManager cfg;
Gson gson;
String configKey;
Type type;
String ws;
Map<K, V> items = new LinkedHashMap<>();

ScopedMapStore(ConfigManager cfg, Gson gson, String configKey, Type type) {
    this.cfg = cfg;
    this.gson = gson;
    this.configKey = configKey;
    this.type = type;
    load();
}

String key() { return ScopedKeys.key(ws, configKey); }

// Per-character scope ("std"/"seasonal" plus ".<accountHash>").
public synchronized void loadScope(String scope) {
    ws = scope;
    load();
}

// Re-read from config - the active RuneLite profile may have changed.
public synchronized void reload() { load(); }

public synchronized void clear() {
    items.clear();
    save();
}

void load() {
    items.clear();
    Map<K, V> stored = ScopedKeys.parse(gson, ScopedKeys.read(cfg, CONFIG_GROUP, ws, configKey), type);
    if (stored != null) items.putAll(stored);
}

void save() {
    if (items.isEmpty()) {
        cfg.unsetConfiguration(CONFIG_GROUP, key());
        return;
    }
    cfg.setConfiguration(CONFIG_GROUP, key(), gson.toJson(items));
}
}
