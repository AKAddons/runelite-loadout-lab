package com.loadoutlab.engine;

import com.loadoutlab.data.*;
import lombok.*;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.util.*;

// Curated incoming-damage sheets for bosses the corpus describes badly
// (boss_incoming.json): each boss's attacks with their share of the
// rotation, max hit, speed, prayer behaviour, layered damage, the heal
// they give the boss off what lands, and a note. Gson fills the rows;
// the defaults a key can leave out are the field initialisers.
public class BossIncomingOverrides {
    static final String RESOURCE = "boss_incoming.json";

    @Getter
    public static class Attack {
        public String style;
        public int maxHit, speedTicks, hits = 1;
        double share, leak;
        boolean prayable = true, avoidable, flicked, chain;
        // Null in the data = 0 when prayable, 1 when not.
        Double prayerFactor;
        public String name = "";
        Map<String, int[]> layers;
        Heal heal;
    }

    // The boss heals factor x the damage landed, at least min, at most max.
    public static class Heal {
        double factor, min, max = Double.POSITIVE_INFINITY;
    }

    @Getter
    public static class BossOverride {
        List<Attack> attacks = List.of();
        String note = "";
        // The share of the fight the boss can be hit; 1 = always.
        double uptime = 1;
        transient boolean heals;
    }

    static final Map<String, BossOverride> OVERRIDES = new HashMap<>();
    static final Map<String, String> UNPRICED = new HashMap<>();

    static {
        Map<String, JsonElement> root = JsonResources.read(RESOURCE, new TypeToken<Map<String, JsonElement>>() { }.getType());
        for (var entry : root.entrySet()) {
            if ("_unpriced".equals(entry.getKey())) {
                for (var why : entry.getValue().getAsJsonObject().entrySet()) {
                    UNPRICED.put(Names.lower(why.getKey()), why.getValue().getAsString());
                }
            } else if (!entry.getKey().startsWith("_")) OVERRIDES.put(Names.lower(entry.getKey()), parse(entry.getValue().getAsJsonObject()));
        }
    }

    static BossOverride parse(JsonObject json) {
        BossOverride o = JsonResources.gson().fromJson(json, BossOverride.class);
        for (Attack a : o.attacks) {
            a.style = Names.lower(a.style);
            a.hits = Math.max(1, a.hits);
            if (a.prayerFactor == null) a.prayerFactor = a.prayable ? 0.0 : 1.0;
            o.heals |= a.heal != null;
        }
        return o;
    }


    public static BossOverride overridesFor(MonsterStats monster) { return lookup(OVERRIDES, monster); }

    public static boolean heals(MonsterStats monster) {
        BossOverride override = overridesFor(monster);
        return override != null && override.heals;
    }

    public static String unpricedReason(MonsterStats monster) { return lookup(UNPRICED, monster); }

    public static double uptime(MonsterStats monster) {
        BossOverride override = overridesFor(monster);
        return override == null ? 1 : override.uptime;
    }

    static <T> T lookup(Map<String, T> map, MonsterStats monster) {
        if (monster == null) return null;
        String version = monster.version;
        if (version != null && !version.isEmpty()) {
            T byForm = map.get((monster.nameLower + "|" + version).toLowerCase(Locale.ROOT));
            if (byForm != null) return byForm;
        }
        return map.get(monster.nameLower);
    }

    public static Set<String> names() { return Collections.unmodifiableSet(OVERRIDES.keySet()); }
}
