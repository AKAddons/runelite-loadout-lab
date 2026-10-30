package com.loadoutlab.data;

import com.google.gson.*;
import java.util.*;
import java.util.stream.Collectors;
import static com.loadoutlab.data.JsonResources.*;
import static com.loadoutlab.data.Names.*;

// Curated monster groups (M-3): a group is the ROSTER of distinct mob
// types featured in the content - Fight Caves, Inferno, Zulrah's forms,
// the Dagannoth Kings... - searchable beside the monsters and expanding
// into one multi-mob result (one shared set per style, HP-weighted).
// Members resolve against LOADED corpus rows by exact name (+ version
// when the name is ambiguous) - never raw wiki names, because the
// stat-key collapse merges versions (Zuk Normal/Enraged, Tz-Kek's two
// levels). A member that fails to resolve fails loudly in tests, not
// silently at runtime: the group simply omits it and carries on.
public class MonsterGroups {
    static final String RESOURCE = "monster_groups.json";

    // One curated group, resolved to loaded rows (the loader hands in
    // unmodifiable mob and alias lists).
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class MonsterGroup {
        final String name;
        final String note;
        final List<MonsterStats> mobs;
        final List<String> aliases;
        final int inventory;
        final boolean comingSoon;
        // A raid selection: shared supplied boosts, big inventories, and
        // never a slayer-task context (Andrew, 2026-08-31).
        final boolean raid;

        // "Fight Caves - 7 mobs" for list rows (the coming-soon note
        // renders in the RESULT card, not here - field spec 2026-07-18).
        public String label() { return name + " - " + mobs.size() + " mobs"; }
    }


    // One curated member as Gson reads it: a plain name, or a synthetic
    // phase (immuneTo, one style or several; without; a label), with the raid
    // stipulations beside it.
    static class Member {
        String name, version, label, nick, spec;
        JsonElement immuneTo;
        List<String> without;
        boolean prayerImmunity, specOnly;
        Boolean trip;
        Integer weight;
    }

    static class Row {
        String name, note = "";
        List<Member> members = List.of();
        List<String> aliases = List.of();
        int inventory;
        boolean comingSoon, raid;
    }

    // Load and resolve every curated group against the dataset.
    public static List<MonsterGroup> load(LoadoutData data) {
        List<MonsterGroup> groups = new ArrayList<>();
        try {
            for (Row row : read(RESOURCE, Row[].class)) {
                List<MonsterStats> mobs = new ArrayList<>();
                for (Member member : row.members) {
                    MonsterStats resolved = resolve(data, member.name, member.version);
                    if (resolved == null) continue;
                    if (member.immuneTo != null) {
                        // A synthetic per-phase variant (TD shields, KQ
                        // forms, Nylocas forms, Warden P2): same sheet, new
                        // id + label, one or SEVERAL immunity attributes.
                        // The id ordinal is the style bitmask (melee 1,
                        // ranged 2, magic 4) so every combo stays distinct.
                        List<String> styles = new ArrayList<>();
                        if (member.immuneTo.isJsonArray()) {
                            for (JsonElement e : member.immuneTo.getAsJsonArray()) {
                                styles.add(lower(e.getAsString()));
                            }
                        } else styles.add(lower(member.immuneTo.getAsString()));
                        int mask = 0;
                        List<String> attributes = new ArrayList<>();
                        if (member.prayerImmunity) {
                            // The immunity is a protection PRAYER, not a
                            // shield: Verac's set pierces it (KQ forms).
                            attributes.add("prayer_immunity");
                        }
                        var joined = new StringBuilder();
                        for (String style : styles) {
                            mask |= styleBit(style);
                            attributes.add("immune_" + style);
                            if (joined.length() > 0) joined.append(" + ");
                            joined.append(cap(style));
                        }
                        String label = member.label != null ? member.label : joined + " immune";
                        resolved = resolved.immuneVariant(
                            MonsterStats.SYNTHETIC_ID_BASE + resolved.id * 10 + mask, label, attributes);
                    } else if (member.without != null) {
                        // A phase that loses an attribute; id bit 8.
                        List<String> removed = new ArrayList<>();
                        for (String e : member.without) removed.add(lower(e));
                        resolved = resolved.withoutAttributes( MonsterStats.SYNTHETIC_ID_BASE + resolved.id * 10 + 8,
                            member.label, removed);
                    } else if (member.label != null) resolved = resolved.withVersion(member.label);
                    if (member.nick != null) resolved = resolved.withDisplayName(member.nick);
                    // Raid stipulations (Andrew 2026-09-10): a kit weight (the
                    // fight that decides the raid) and a spec weapon family.
                    List<String> extra = new ArrayList<>();
                    if (member.weight != null) extra.add("weight:" + member.weight);
                    if (member.spec != null) extra.add("spec:" + lower(member.spec));
                    // A spec-only member (the Warden's Core, ISS-01M5QEFMBSZXAHV84ZXF1YZHWY):
                    // its main weapon has no say in the kit; its spec hits still count.
                    if (member.specOnly) extra.add("spec_only");
                    // An add the trip's kill time leaves out (the Titans' elementals; Andrew 2026-10-30).
                    if (Boolean.FALSE.equals(member.trip)) extra.add("no_trip");
                    if (!extra.isEmpty()) resolved = resolved.withAttributes(extra);
                    mobs.add(resolved);
                }
                List<String> aliases = new ArrayList<>();
                for (String a : row.aliases) aliases.add(lower(a));
                if (!mobs.isEmpty()) {
                    groups.add(new MonsterGroup(row.name, row.note, mobs, aliases, row.inventory, row.comingSoon, row.raid));
                }
            }
        } catch (Exception e) {
            // A malformed curation must never take the panel down.
            return groups;
        }
        return groups;
    }

    static int styleBit(String style) {
        switch (style) {
            case "melee": return 1;
            case "ranged": return 2;
            default: return 4; // magic
        }
    }

    // Exact-name match; version disambiguates when given, otherwise the
    // name must be unique (ambiguity = curation bug, caught by tests).
    static MonsterStats resolve(LoadoutData data, String name, String version) {
        // Two hits = ambiguous: the curation must name a version.
        List<MonsterStats> matches = data.monsters.stream().filter(m -> m.name.equalsIgnoreCase(name)
            && (version == null || version.equalsIgnoreCase(m.version))).limit(2).collect(Collectors.toList());
        return matches.size() == 1 ? matches.get(0) : null;
    }

    // Name-contains search over the groups, hits in curated order.
    public static List<MonsterGroup> search(List<MonsterGroup> groups, String query, int limit) {
        String text = query == null ? "" : lower(query.trim());
        if (text.length() < 2) return new ArrayList<>();
        // Player vocabulary ("jad", "zuk", "dks") reaches the group through its aliases.
        return groups.stream().filter(group -> lower(group.name).contains(text)
            || group.aliases.stream().anyMatch(alias -> alias.contains(text) || text.contains(alias)))
            .limit(limit).collect(Collectors.toList());
    }
}
