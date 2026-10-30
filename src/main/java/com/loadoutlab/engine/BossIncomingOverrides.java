package com.loadoutlab.engine;

import com.loadoutlab.data.*;
import lombok.*;
import com.google.gson.*;
import java.util.*;

// Curated per-boss incoming-attack overrides - the D-2 layer on top of the
// uniform v1 model in IncomingDpsCalculator. The stat sheet gets scripted
// max hits wrong (Graardor's ranged slam is 15-35, the NPC formula derives
// 58), rotations are not uniform, some attacks pierce protection prayers,
// and Typeless/Dragonfire attacks are otherwise unmodeled. Each entry in
// boss_incoming.json (keyed by lowercase display name, or name|version)
// lists the boss's real attacks: style, scripted max hit, hits per attack,
// rotation share, whether the matching protection prayer blocks it, whether
// the standard play prays it on cue (flicked) and how much still leaks, an
// optional per-attack speed. Numeric facts are wiki-sourced; see each
// entry's note. The "_unpriced" block names the monsters the wiki cannot
// price yet, with the reason the card shows instead of a number.
public class BossIncomingOverrides {
    static final String RESOURCE = "boss_incoming.json";

    // One curated attack in the boss's rotation.
    @AllArgsConstructor
    @Getter
    public static class Attack {
        public final String style;
        public final int maxHit;
        final double share;
        final boolean prayable;
        // Fraction of damage that gets THROUGH the matching protection
        // prayer: 0 = fully blocked, 0.5 = half pierces (Callisto melee,
        // Corp magic), 1 = prayer does nothing. maxHit is always the TRUE
        // unprayed value.
        final double prayerFactor;
        // 0 means "use the stat sheet's attack speed".
        public final int speedTicks;
        // Dodgeable by design (Zulrah's magma slam): the standard play
        // takes zero, so the attack is excluded from both dps totals and
        // kept in the threat list for the tooltip - the same philosophy as
        // assuming the protection prayer is up.
        final boolean avoidable;
        // The card's row label when the style alone would not tell two
        // rows apart ("ranged volley", "melee (x2)"); "" = the style.
        public final String name;
        // Hits per attack (three projectiles a volley, a 42 x2 melee):
        // the rate scales with it, the card's max hit does not.
        final int hits;
        // Telegraphed: the standard play prays it on cue whatever single
        // prayer it camps (ISS-01M5PBSZBBBAS9Y46X04MVMHZB).
        final boolean flicked;
        // The fraction of a flicked attack that still lands under standard
        // play - a mixed volley's odd projectile, a late swap.
        final double leak;
        // Dragonfire by protection layers (Vorkath): key "none" / "shield" /
        // "antifire" / "super" / "shield+antifire" / "shield+super" ->
        // {max without, max with Protect from Magic}; null for other attacks.
        final Map<String, int[]> layers;
        // A landed hit of damage d heals the boss clamp(factor x d, min,
        // max) - Blood Moon's 1x/2x/3x stack (ISS-01M6415DTE84ZZ45C4YD3MZRBJ);
        // null when the attack heals nothing.
        final double[] heal;
        // Lands only if the previous attack in the list landed (one miss
        // ends the stack); it rides that attack's share and speed.
        final boolean chain;
    }

    // The full curated picture for one boss.
    @Getter
    public static class BossOverride {
        final List<Attack> attacks;
        final String note;
        // Some attack heals the boss off what lands: the sets rank by net dps.
        final boolean heals;

        BossOverride(List<Attack> attacks, String note) {
            this.attacks = attacks;
            this.note = note;
            boolean h = false;
            for (Attack attack : attacks) h |= attack.heal != null;
            heals = h;
        }
    }

    static final Map<String, BossOverride> OVERRIDES = new HashMap<>();
    // Monsters the wiki cannot price yet, with the reason the card shows.
    static final Map<String, String> UNPRICED = new HashMap<>();

    static {
        JsonObject root = JsonResources.objectOrThrow(RESOURCE);
        for (var entry : root.entrySet()) {
            if ("_unpriced".equals(entry.getKey())) {
                for (var why : entry.getValue().getAsJsonObject().entrySet()) {
                    UNPRICED.put(Names.lower(why.getKey()), why.getValue().getAsString());
                }
            } else if (!entry.getKey().startsWith("_")) OVERRIDES.put(Names.lower(entry.getKey()), parse(entry.getValue().getAsJsonObject()));
        }
    }

    // Shape-only read of one boss entry. The DATA validation that used to
    // throw from here (style whitelist, typeless-must-not-be-prayable, sane
    // maxHit/share/speed, shares summing to at most 1.0, non-empty attack
    // list) now lives in BossIncomingOverridesTest's all-overrides sweep -
    // test source is free of the hub token cap, and the sweep is coupled to a
    // hardcoded boss list so a newly added boss cannot skip it. Only the
    // "resource missing" throw stays here: that is a packaging failure, not
    // bad data.
    static BossOverride parse(JsonObject json) {
        List<Attack> attacks = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("attacks")) {
            JsonObject a = element.getAsJsonObject();
            boolean prayable = !a.has("prayable") || a.get("prayable").getAsBoolean();
            // Legacy semantics preserved: prayable = full block (0 through),
            // unprayable = full pierce (1 through), unless a factor is given.
            double prayerFactor = a.has("prayerFactor") ? a.get("prayerFactor").getAsDouble() : (prayable ? 0.0 : 1.0);
            attacks.add(new Attack( Names.lower(a.get("style").getAsString()), a.get("maxHit").getAsInt(),
                a.get("share").getAsDouble(), prayable, prayerFactor, a.has("speedTicks") ? a.get("speedTicks").getAsInt() : 0,
                a.has("avoidable") && a.get("avoidable").getAsBoolean(), a.has("name") ? a.get("name").getAsString() : "",
                a.has("hits") ? Math.max(1, a.get("hits").getAsInt()) : 1,
                a.has("flicked") && a.get("flicked").getAsBoolean(), a.has("leak") ? a.get("leak").getAsDouble() : 0.0,
                a.has("layers") ? layers(a.getAsJsonObject("layers")) : null,
                a.has("heal") ? heal(a.getAsJsonObject("heal")) : null, a.has("chain") && a.get("chain").getAsBoolean()));
        }
        return new BossOverride(attacks, json.has("note") ? json.get("note").getAsString() : "");
    }

    static double[] heal(JsonObject json) {
        return new double[]{json.get("factor").getAsDouble(), json.has("min") ? json.get("min").getAsDouble() : 0,
            json.has("max") ? json.get("max").getAsDouble() : Double.POSITIVE_INFINITY};
    }

    static Map<String, int[]> layers(JsonObject json) {
        Map<String, int[]> map = new HashMap<>();
        for (var e : json.entrySet()) {
            JsonArray pair = e.getValue().getAsJsonArray();
            map.put(e.getKey(), new int[]{pair.get(0).getAsInt(), pair.get(1).getAsInt()});
        }
        return map;
    }


    // The curated override for this monster, or null to use the v1 model.
    // Keyed by display name so every version/phase row shares one entry.
    public static BossOverride overridesFor(MonsterStats monster) { return lookup(OVERRIDES, monster); }

    // True when the monster's curated attacks heal it off what lands.
    public static boolean heals(MonsterStats monster) {
        BossOverride override = overridesFor(monster);
        return override != null && override.heals;
    }

    // Why the card shows no estimate for this monster, or null when the
    // curated data or the sheet prices it.
    public static String unpricedReason(MonsterStats monster) { return lookup(UNPRICED, monster); }

    // Version-keyed first ("zulrah|serpentine"): multi-form bosses fight
    // differently per form, and the roster lens shows one form at a time -
    // each must carry ITS OWN attacks and protect prayer (field report
    // 2026-07-17: every Zulrah form said pray mage). The bare name stays as
    // the whole-fight fallback.
    static <T> T lookup(Map<String, T> map, MonsterStats monster) {
        if (monster == null) return null;
        String version = monster.version;
        if (version != null && !version.isEmpty()) {
            T byForm = map.get((monster.nameLower + "|" + version).toLowerCase(Locale.ROOT));
            if (byForm != null) return byForm;
        }
        return map.get(monster.nameLower);
    }

    // All curated boss names (lowercase) - test and tooling surface.
    public static Set<String> names() { return Collections.unmodifiableSet(OVERRIDES.keySet()); }
}
