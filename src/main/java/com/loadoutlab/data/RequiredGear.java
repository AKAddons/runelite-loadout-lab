package com.loadoutlab.data;

import com.google.gson.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.loadoutlab.data.JsonResources.*;
import java.util.function.IntPredicate;

// Mandatory protective slayer gear per monster (field request
// 2026-08-08: "some tasks where u need to equip something. would be
// cool if it was autopinned"): a mirror shield vs a basilisk's gaze, a
// lit bug lantern to harm harpies at all. The optimizer enforces the
// slot exactly like the dragonfire shield - the pool collapses to the
// acceptable items and non-complying weapon lines die - whenever the
// request can actually field one; a user pin on the slot outranks.
// Data-driven from required_gear.json (a resource - token-free), every
// row wiki-verified before encoding.
public class RequiredGear {
    // One monster family's requirement: the slot, the acceptable item
    // specs (corpus names, optionally name#Version), and the why.
    @lombok.RequiredArgsConstructor
    public static class Rule {
        public final GearSlot slot;
        public final String note;
        final List<String> itemSpecs;
        // A waiver key that lifts the rule (KOUREND_ELITE), or null.
        public final String waivedBy;
        // A convention, not a must (the Barrows hilt, the Shayzien set):
        // enforced from what you own, never forced unowned.
        public final boolean soft;
        // Resolved once per corpus - the corpus never changes post-load.
        volatile Set<Integer> resolved;

        // The acceptable item ids, resolved against the loaded corpus
        // (standard-gear rows only; variants fold via the candidate
        // pool's own canonicalization).
        public Set<Integer> ids(LoadoutData data) {
            Set<Integer> out = resolved;
            if (out != null) return out;
            out = new LinkedHashSet<>();
            for (String spec : itemSpecs) {
                String name = spec;
                String version = null;
                int hash = spec.indexOf('#');
                if (hash >= 0) {
                    name = spec.substring(0, hash);
                    version = spec.substring(hash + 1);
                }
                for (GearItem item : data.gearItems) {
                    if (item.nameLower.equals(name) && item.standardGear
                        && (version == null || version.equals(item.version))) {
                        out.add(item.id);
                    }
                }
            }
            resolved = out;
            return out;
        }
    }

    static final Map<String, List<Rule>> BY_NAME = new HashMap<>();

    // The elite Kourend & Kebos diary (varbit KOUREND_DIARY_ELITE_COMPLETE):
    // once its rewards are claimed Karuulm's floor no longer burns (wiki,
    // Karuulm Slayer Dungeon) - the boots rule's waiver.
    public static final String KOUREND_ELITE = "kourendElite";

    // A carried slayer item the kill needs - the finishing blow (rock
    // hammer, fungicide, salt, ice cooler): acceptable items best first,
    // each with its ids (not gear, so ids rather than corpus names).
    @lombok.RequiredArgsConstructor
    public static class Carried {
        public final String note;
        final List<String> names;
        final List<int[]> ids;
        // A swap picked by kind rather than id: "fastRanged" = the best 2-tick
        // ranged weapon (ISS-01M65N6WS5N2V9TQC0MJ03NKY8, Hespori's sprouts).
        public final String category;
    }

    static final Map<String, List<Carried>> CARRIED_BY_NAME = new HashMap<>();
    static final Map<String, List<Carried>> SWAPS_BY_NAME = new HashMap<>();
    static final Set<String> WARD = new HashSet<>();

    // required_gear.json as Gson reads it: slot rules, the ward list, the
    // carried finishing items and the fight swaps.
    static class Root {
        List<RuleRow> rules = List.of();
        List<String> ward = List.of();
        List<CarriedRow> carried = List.of(), swaps = List.of();
    }

    static class RuleRow {
        String slot, note = "", waivedBy;
        List<String> items = List.of(), monsters = List.of();
        boolean soft;
    }

    static class CarriedRow {
        List<String> monsters = List.of();
        List<ItemRow> items = List.of();
        String note = "", category;
    }

    static class ItemRow {
        String name;
        int[] ids;
    }

    static {
        // Fail LOUD (the name_rules pattern): a silently empty table would
        // drop every requirement with no visible error.
        Root root = read("required_gear.json", Root.class);
        for (RuleRow row : root.rules) {
            var rule = new Rule(GearSlot.valueOf(row.slot), row.note, row.items, row.waivedBy, row.soft);
            for (String m : row.monsters) BY_NAME.computeIfAbsent(m, k -> new ArrayList<>()).add(rule);
        }
        if (BY_NAME.isEmpty()) throw new IllegalStateException("required_gear.json loaded empty");
        WARD.addAll(root.ward);
        for (var table : List.of(root.carried, root.swaps)) {
            for (CarriedRow row : table) {
                List<String> names = new ArrayList<>();
                List<int[]> ids = new ArrayList<>();
                for (ItemRow item : row.items) {
                    names.add(item.name);
                    ids.add(item.ids);
                }
                var carried = new Carried(row.note, names, ids, row.category);
                for (String m : row.monsters) (table == root.swaps ? SWAPS_BY_NAME : CARRIED_BY_NAME).computeIfAbsent(m, k -> new ArrayList<>()).add(carried);
            }
        }
    }

    // The fight's swap rules (ISS-01M5VCXYGF51P5NJCDNC1QJZD1): Cerberus' spectral spirit shield.
    public static List<Carried> swaps(MonsterStats monster) {
        return monster == null ? List.of() : SWAPS_BY_NAME.getOrDefault(monster.nameLower, List.of());
    }

    // Every swap id for the monster, each rule's best first.
    public static List<Integer> swapIds(MonsterStats monster) {
        List<Integer> out = new ArrayList<>();
        for (Carried rule : swaps(monster)) {
            for (int[] ids : rule.ids) for (int id : ids) out.add(id);
        }
        return out;
    }

    // What a carried swap is for, or "carried swap" for any other bench item.
    public static String swapNote(int id) {
        for (List<Carried> rules : SWAPS_BY_NAME.values()) {
            for (Carried rule : rules) {
                for (int[] ids : rule.ids) for (int i : ids) if (i == id) return rule.note;
            }
        }
        return "carried swap";
    }

    // A fight whose prayer drain is demonic: Ward of Arceuus rides the trip (ISS-01M5VCXYGF51P5NJCDNC1QJZD1).
    public static boolean ward(MonsterStats monster) { return monster != null && WARD.contains(monster.nameLower); }

    // The requirement for this monster, or null. Name-keyed like the
    // notes, so RequiredGearTest pins every key against loaded rows.
    // Every rule for the monster (a full armour set is one rule per slot).
    public static List<Rule> rulesFor(MonsterStats monster) {
        return monster == null ? List.of() : BY_NAME.getOrDefault(monster.nameLower, List.of());
    }

    // The rules still standing once the player's waivers lift theirs.
    public static List<Rule> rulesFor(MonsterStats monster, Set<String> waivers) {
        return rulesFor(monster).stream().filter(rule -> rule.waivedBy == null || waivers == null
            || !waivers.contains(rule.waivedBy)).collect(Collectors.toList());
    }

    // slot -> acceptable ids, empty when nothing is required.
    public static Map<GearSlot, Set<Integer>> requiredIds(MonsterStats monster, LoadoutData data) {
        return requiredIds(monster, data, Set.of());
    }

    // slot -> acceptable ids, the waived rules lifted.
    public static Map<GearSlot, Set<Integer>> requiredIds(MonsterStats monster, LoadoutData data, Set<String> waivers) {
        Map<GearSlot, Set<Integer>> out = new EnumMap<>(GearSlot.class);
        for (Rule rule : rulesFor(monster, waivers)) out.put(rule.slot, rule.ids(data));
        return out;
    }

    // The carried slayer items this monster's kill needs, one per rule:
    // the best one you own, else the best one there is, flagged unowned
    // (Andrew 2026-10-22: "show them as necessary even when the player
    // doesn't own those items"). Each row: id, name, qty, why, owned.
    public static List<Map> carriedPicks(MonsterStats monster, IntPredicate owns) {
        List<Map> out = new ArrayList<>();
        if (monster == null) return out;
        for (Carried rule : CARRIED_BY_NAME.getOrDefault(monster.nameLower, List.of())) {
            int pick = -1;
            for (int i = 0; i < rule.ids.size() && pick < 0; i++) {
                for (int id : rule.ids.get(i)) {
                    if (owns.test(id)) {
                        pick = i;
                        break;
                    }
                }
            }
            boolean owned = pick >= 0;
            int at = owned ? pick : 0;
            out.add(map("id", rule.ids.get(at)[0], "name", rule.names.get(at), "qty", 1,
                "why", rule.note + (owned ? "" : " - not owned: get one before the trip"), "owned", owned));
        }
        return out;
    }
}
