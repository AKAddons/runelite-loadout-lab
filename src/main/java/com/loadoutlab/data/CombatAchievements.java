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

    // A task row as Gson reads it: absent sets stay null (no restriction).
    static class Row {
        int id, levelCap;
        String name, tier, desc, version, attackType, spellbook;
        Set<String> mobs, styles, spells, banned, weapons, weaponCategories;
        List<JsonArray> rules;
        boolean demonbaneWeapon, bannedDemonbane, oneHanded, unarmed, noWeaponMagic, noPrayerRestore, poisonWeapon, f2p;
        JsonObject mobWeapons;
    }

    static class Root {
        List<Row> tasks = List.of();
    }

    static {
        for (Row t : read("combat_achievements.json", Root.class).tasks) {
            List<SetRules.Rule> rules = new ArrayList<>();
            if (t.rules != null) for (JsonArray a : t.rules) rules.add(new SetRules.Rule(a.get(0).getAsString(), a.get(1).getAsString(), a.get(2).getAsDouble()));
            Set<CombatStyle> allowed = null;
            if (t.styles != null) {
                allowed = EnumSet.noneOf(CombatStyle.class);
                for (String s : t.styles) allowed.add(CombatStyle.valueOf(s.toUpperCase(Locale.ROOT)));
            }
            SetRules set = SetRules.builder().rules(rules).banned(t.banned == null ? Set.of() : t.banned)
                .weapons(t.weapons).weaponCategories(t.weaponCategories).demonbaneWeapon(t.demonbaneWeapon)
                .bannedDemonbane(t.bannedDemonbane).oneHanded(t.oneHanded).unarmed(t.unarmed)
                .noWeaponMagic(t.noWeaponMagic).styles(allowed).attackType(t.attackType)
                .noPrayerRestore(t.noPrayerRestore).spellbook(t.spellbook).spells(t.spells == null ? null : List.copyOf(t.spells))
                .levelCap(t.levelCap).poisonWeapon(t.poisonWeapon).f2p(t.f2p).build();
            BY_ID.put(t.id, new Task(t.id, t.name, t.tier, t.desc, t.mobs, t.version == null ? "" : t.version, set, t.mobWeapons));
        }
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
