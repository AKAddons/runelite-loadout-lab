package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.*;
import net.runelite.client.config.ConfigManager;

/**
 * The GLOBAL always-filter list (the grey member of the exclude/sim/filter
 * trio, field spec 2026-07-20): items that join EVERY filtered bank view -
 * teleport capes, boss teles, whatever the player always wants at hand.
 * Names are captured at add time (id resolution later needs the client
 * thread), matching the mob-profile filter items. Per character since
 * 0.4.1 (a main and a test account had shared this list).
 */
public class AlwaysFilterStore extends ScopedMapStore<Integer, String> {
    static final String KEY = "alwaysFilterItems";

    public AlwaysFilterStore(ConfigManager configManager, Gson gson) {
        super(configManager, gson, KEY, new TypeToken<Map<Integer, String>>(){}.getType());
    }

    public synchronized Map<Integer, String> all() { return new LinkedHashMap<>(items); }

    public synchronized void add(int itemId, String name) {
        items.put(itemId, name == null ? ("item " + itemId) : name);
        save();
    }

    public synchronized void remove(int itemId) {
        items.remove(itemId);
        save();
    }
}
