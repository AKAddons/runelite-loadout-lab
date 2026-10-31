package com.loadoutlab.engine;

import com.google.gson.*;
import com.loadoutlab.data.*;
import java.util.*;
import lombok.*;

// Melee combat styles available per weapon category - game facts from the
// wiki's weapon-type pages (cross-checked against the official calculator,
// 2026-07-05). The upstream best-dps engine assumed every melee weapon has
// every attack type at every stance, which invented styles weapons don't
// have: a whip has no aggressive stance, and a Blunt weapon cannot slash -
// that one let a barronite mace "slash" a Grey golem (slash defence 1,
// everything else 300) for a 3x accuracy error against the official calc.
// <p>Stances: accurate = +3 attack, aggressive = +3 strength,
// controlled = +1 both. Defensive is omitted - it adds no offence, so it
// can never be the best-DPS pick.
// <p>The table itself lives in weapon_styles.json (hub token cap: data goes
// in resources, not source): 25 wiki weapon categories plus the "*" fallback
// row; a new category (the Obligator's Extending Chain Mace, 2026-10-30) is
// added by hand from the wiki's Module:CombatStyles.
public class WeaponStyles {
// One selectable style: the attack type plus its stance bonuses.
@RequiredArgsConstructor
static class MeleeStyle {
    final String attackType;
    final int attackStance;
    final int strengthStance;
}

static final String RESOURCE = "weapon_styles.json";
// Resource key of the unknown-category fallback row - not a real weapon
// category, so it can never collide with one.
static final String FALLBACK = "*";

static final Map<String, List<MeleeStyle>> BY_CATEGORY = load();

// Every attack type x stance a full search would try - the fallback.
static final List<MeleeStyle> ALL = BY_CATEGORY.getOrDefault(FALLBACK, List.of());

static Map<String, List<MeleeStyle>> load() {
    Map<String, List<MeleeStyle>> table = new HashMap<>();
    JsonObject root = JsonResources.object(RESOURCE);
    if (root == null) return table;
    for (var entry : root.entrySet()) {
        List<MeleeStyle> styles = new ArrayList<>();
        for (JsonElement element : entry.getValue().getAsJsonArray()) {
            JsonObject style = element.getAsJsonObject();
            styles.add(new MeleeStyle(style.get("attackType").getAsString(), style.get("attackStance").getAsInt(),
                style.get("strengthStance").getAsInt()));
        }
        table.put(entry.getKey(), styles);
    }
    return table;
}


// The melee styles this weapon actually offers (unknown category: all).
static List<MeleeStyle> melee(Gear weapon) {
    // Null weapon = bare fists, which is a real category row. Looking it up
    // through the same map (rather than returning it directly) is what
    // keeps a missing resource from returning null here and NPE-ing
    // DpsCalculator: an absent row falls back to ALL, and ALL itself
    // defaults to an empty list.
    List<MeleeStyle> styles = BY_CATEGORY.get( weapon == null ? "Unarmed" : weapon.category);
    return styles != null ? styles : ALL;
}
}
