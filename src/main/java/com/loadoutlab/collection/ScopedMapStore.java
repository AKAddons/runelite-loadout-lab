package com.loadoutlab.collection;

import com.google.gson.Gson;
import java.lang.reflect.Type;
import java.util.*;
import net.runelite.client.config.ConfigManager;

/**
 * A per-character map persisted as one JSON config entry: the always-filter
 * list and the supply defaults share this exact shape (scoped key, load on
 * scope change, unset when empty, corrupt JSON starts fresh).
 */
abstract class ScopedMapStore<K, V> implements ScopedStore {
    static final String CONFIG_GROUP = "loadoutlab";

    private final ConfigManager configManager;
    private final Gson gson;
    private final String configKey;
    private final Type type;
    private String worldScope;
    final Map<K, V> items = new LinkedHashMap<>();

    ScopedMapStore(ConfigManager configManager, Gson gson, String configKey, Type type) {
        this.configManager = configManager;
        this.gson = gson;
        this.configKey = configKey;
        this.type = type;
        load();
    }

    private String key() { return ScopedKeys.key(worldScope, configKey); }

    /** Per-character scope ("std"/"seasonal" plus ".<accountHash>"). */
    public synchronized void loadScope(String scope) {
        this.worldScope = scope;
        load();
    }

    /** Re-read from config - the active RuneLite profile may have changed. */
    public synchronized void reload() { load(); }

    public synchronized void clear() {
        items.clear();
        save();
    }

    private void load() {
        items.clear();
        Map<K, V> stored = ScopedKeys.parse(gson, ScopedKeys.read(configManager, CONFIG_GROUP, worldScope, configKey), type);
        if (stored != null) items.putAll(stored);
    }

    void save() {
        if (items.isEmpty()) {
            configManager.unsetConfiguration(CONFIG_GROUP, key());
            return;
        }
        configManager.setConfiguration(CONFIG_GROUP, key(), gson.toJson(items));
    }
}
