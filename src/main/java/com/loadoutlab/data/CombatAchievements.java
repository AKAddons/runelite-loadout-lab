package com.loadoutlab.data;

import com.google.gson.*;
import com.loadoutlab.engine.*;
import java.util.*;
import lombok.*;
import static com.loadoutlab.data.JsonResources.*;

// Combat achievement tasks whose restrictions shape a set (combat_achievements.json,
// ISS-01M5XC7XYMMEN3Q4QC6CGZFVPK): each task's rules as SetRules, by id and by mob.
// The file's names are lowercase already and its keys are SetRules' field names.
public class CombatAchievements {
    @Value
    public static class Task {
        int id;
        String name, tier, desc;
        Set<String> mobs;
        // A fragment the mob's version must contain, or "".
        String version;
        SetRules rules;
        // Per-mob weapon lists (Betrayal: each moon's own drop), or null.
        JsonObject mobWeapons;

        // The rules on these mobs: a per-mob weapon list narrows to theirs
        // (a group of them allows any of their weapons).
        public SetRules rulesFor(Collection<MonsterStats> on) {
            if (mobWeapons == null) return rules;
            Set<String> weapons = new HashSet<>();
            for (MonsterStats m : on) strings(mobWeapons, m.nameLower, weapons);
            if (weapons.isEmpty()) mobWeapons.keySet().forEach(m -> strings(mobWeapons, m, weapons));
            return rules.toBuilder().weapons(weapons).build();
        }

        public boolean isFor(MonsterStats mob) {
            return mobs.contains(mob.nameLower) && (version.isEmpty() || Names.lower(mob.version).contains(version));
        }
    }

    static final Map<Integer, Task> BY_ID = new LinkedHashMap<>();

    static {
        for (JsonElement e : objectOrThrow("combat_achievements.json").getAsJsonArray("tasks")) {
            JsonObject t = e.getAsJsonObject();
            List<SetRules.Rule> rules = new ArrayList<>();
            if (t.has("rules")) for (JsonElement r : t.getAsJsonArray("rules")) {
                JsonArray a = r.getAsJsonArray();
                rules.add(new SetRules.Rule(a.get(0).getAsString(), a.get(1).getAsString(), a.get(2).getAsDouble()));
            }
            Set<String> styles = set(t, "styles"), spells = set(t, "spells");
            Set<CombatStyle> allowed = null;
            if (styles != null) {
                allowed = EnumSet.noneOf(CombatStyle.class);
                for (String s : styles) allowed.add(CombatStyle.valueOf(s.toUpperCase(Locale.ROOT)));
            }
            SetRules set = SetRules.builder().rules(rules).banned(t.has("banned") ? set(t, "banned") : Set.of())
                .weapons(set(t, "weapons")).weaponCategories(set(t, "weaponCategories")).demonbaneWeapon(flag(t, "demonbaneWeapon"))
                .bannedDemonbane(flag(t, "bannedDemonbane")).oneHanded(flag(t, "oneHanded")).unarmed(flag(t, "unarmed"))
                .noWeaponMagic(flag(t, "noWeaponMagic")).styles(allowed).attackType(text(t, "attackType"))
                .noPrayerRestore(flag(t, "noPrayerRestore")).spellbook(text(t, "spellbook")).spells(spells == null ? null : List.copyOf(spells))
                .levelCap(t.has("levelCap") ? t.get("levelCap").getAsInt() : 0).poisonWeapon(flag(t, "poisonWeapon")).f2p(flag(t, "f2p")).build();
            int id = t.get("id").getAsInt();
            String version = text(t, "version");
            BY_ID.put(id, new Task(id, text(t, "name"), text(t, "tier"), text(t, "desc"), set(t, "mobs"), version == null ? "" : version, set,
                t.getAsJsonObject("mobWeapons")));
        }
    }


    static boolean flag(JsonObject t, String key) { return t.has(key) && t.get(key).getAsBoolean(); }

    static String text(JsonObject t, String key) { return t.has(key) ? t.get(key).getAsString() : null; }

    // The string array at key, or null when absent.
    static Set<String> set(JsonObject t, String key) {
        if (!t.has(key)) return null;
        Set<String> out = new LinkedHashSet<>();
        strings(t, key, out);
        return out;
    }

    public static Task byId(int id) { return BY_ID.get(id); }

    // Every task, in file order.
    public static Collection<Task> all() { return BY_ID.values(); }

    // The tasks for a monster (its name, and its version where a task names one).
    public static List<Task> tasksFor(MonsterStats mob) {
        List<Task> out = new ArrayList<>();
        for (Task t : BY_ID.values()) if (t.isFor(mob)) out.add(t);
        return out;
    }
}
