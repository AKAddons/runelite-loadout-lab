package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.*;
import net.runelite.client.config.ConfigManager;

@lombok.RequiredArgsConstructor
public abstract class IdSetStore implements ScopedStore {
    static final String CONFIG_GROUP = "loadoutlab";

    private final ConfigManager configManager;
    private final Gson gson;
    final Set<Integer> ids = new LinkedHashSet<>();
    /** "std" / "seasonal", plus ".<accountHash>" once a character is known.
     * Field report 2026-08-25: switching from a main to a test account showed
     * the SAME exclude and sim lists, because these keys were global while the
     * bank ledger next door was already per-character. */
    String worldScope;

    /** The unscoped key this store used before 0.4.1 - read once so an
     * existing install keeps its list when the key gains the account hash. */
    abstract String legacyKey();

    String key() { return ScopedKeys.key(worldScope, legacyKey()); }

    public synchronized void loadScope(String scope) {
        this.worldScope = scope;
        load();
    }

    public synchronized void reload() { load(); }

    final void load() {
        ids.clear();
        Set<Integer> stored = ScopedKeys.parse(gson, ScopedKeys.read(configManager, CONFIG_GROUP, worldScope, legacyKey()),
            new TypeToken<Set<Integer>>(){}.getType());
        if (stored != null) ids.addAll(stored);
    }

    final void save() { configManager.setConfiguration(CONFIG_GROUP, key(), gson.toJson(ids)); }

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

    public synchronized Set<Integer> snapshot() { return Collections.unmodifiableSet(new LinkedHashSet<>(ids)); }
}
