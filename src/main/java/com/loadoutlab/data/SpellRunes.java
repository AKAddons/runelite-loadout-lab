package com.loadoutlab.data;

import java.util.*;

import static com.loadoutlab.data.JsonResources.*;
import com.loadoutlab.engine.OwnedItems;

// Runes per cast for every corpus spell (wiki-fetched, see the
// resource's note). Elemental staves cover their element: the worn
// weapon and shield names decide which requirements drop - the combo
// staves (smoke/mist/dust/mud/lava/steam) cover two, the Kodai wand
// covers water, the tomes cover their book's element.
public class SpellRunes {
static final Map<String, Integer> RUNE_ITEMS = new LinkedHashMap<>();
// Sunfire rune (gameval ItemID.SUNFIRERUNE).
public static final int SUNFIRE_RUNE = 28929;
static final Map<String, Map<String, Integer>> BY_SPELL = new LinkedHashMap<>();

static class Root {
    Map<String, Integer> runeItems = Map.of();
    Map<String, Map<String, Integer>> bySpell = Map.of();
}

static {
    Root root = read("spell_runes.json", Root.class);
    RUNE_ITEMS.putAll(root.runeItems);
    BY_SPELL.putAll(root.bySpell);
}


// Runes a player cannot conjure from pocket change: the high-level
// combat runes. Elemental/mind/chaos-class runes are deliberately not
// checked - any shop sells them.
static final Set<String> PREMIUM = Set.of("blood", "soul", "death", "wrath");

// True when the spell needs no blood/soul/death/wrath runes, or the
// player owns every one it needs (field report 2026-08-27: a runeless
// account was told to autocast blood-rune spells). Unknown spells fail
// OPEN - the corpus, not this gate, decides what exists.
public static boolean premiumRunesOwned(String spellName, OwnedItems owned) {
    Map<String, Integer> cost = spellName == null ? null : BY_SPELL.get(spellName);
    if (cost == null || owned == null) return true;
    return cost.keySet().stream().noneMatch(rune -> PREMIUM.contains(rune)
        && RUNE_ITEMS.get(rune) != null && !owned.owns(RUNE_ITEMS.get(rune)));
}

// The elements a worn item covers, by name (empty = none): the first
// combo staff / Kodai fragment of STAVES that the name carries, else -
// "staff of air", "air battlestaff", "mystic fire staff", "tome of fire"
// - the element word is the signal, but only on staves and tomes (an
// "earth warrior" style name never reaches here: gear names only).
static List<String> covers(String nameLower) {
    List<String> out = new ArrayList<>(2);
    if (nameLower == null) return out;
    for (String[] staff : STAVES) {
        if (nameLower.contains(staff[0])) {
            out.addAll(Arrays.asList(staff).subList(1, staff.length));
            return out;
        }
    }
    for (String element : new String[]{"air", "water", "earth", "fire"}) if (nameLower.contains(element) && (nameLower.contains("staff") || nameLower.contains("tome"))) out.add(element);
    return out;
}

// [{id, name, qty}] for one cast of the spell, minus what the worn
// weapon/shield provide; null when the spell is unknown.
public static List<Map> costFor(String spellName, String weaponNameLower, String shieldNameLower) {
    Map<String, Integer> runes = spellName == null ? null : BY_SPELL.get(spellName);
    if (runes == null) return null;
    List<String> covered = covers(weaponNameLower);
    covered.addAll(covers(shieldNameLower));
    List<Map> out = new ArrayList<>();
    for (var rune : runes.entrySet()) {
        if (covered.contains(rune.getKey())) continue;
        Integer itemId = RUNE_ITEMS.get(rune.getKey());
        if (itemId == null) continue;
        out.add(map("id", itemId, "name", Names.cap(rune.getKey()) + " rune", "qty", rune.getValue()));
    }
    return out;
}

// Sunfire runes stand in for fire runes (wiki): the cost's fire stack
// becomes the same count of sunfire runes, everything else as it was.
// A cost with no fire runes (a fire staff or tome covers them) comes
// back unchanged; null stays null.
public static List<Map> withSunfire(List<Map> runes) {
    if (runes == null) return null;
    Integer fire = RUNE_ITEMS.get("fire");
    List<Map> out = new ArrayList<>(runes.size());
    for (Map rune : runes) {
        if (fire != null && fire.equals(rune.get("id"))) {
            Map swap = new LinkedHashMap<>(rune);
            swap.put("id", SUNFIRE_RUNE);
            swap.put("name", "Sunfire rune");
            out.add(swap);
        } else out.add(rune);
    }
    return out;
}

// Combo-rune fold: where a combination rune covers two of the
// cost's elements, ONE stack of it (the larger quantity) replaces
// both - a combo rune counts as one of each element per cast.
static final String[][] COMBOS;
// The combo staves and the Kodai wand: [name fragment, elements covered...].
static final String[][] STAVES;

static class Combos {
    String[][] combos, staves;
}

static {
    Combos root = read("combo_runes.json", Combos.class);
    COMBOS = root.combos;
    STAVES = root.staves;
}

// The combination-rune item ids - the ownership detection behind
// the panel's "Detect" preference reads these.
// A rune the pouch holds - a basic, combination or sunfire rune; never a
// seed box or a slayer item riding the utility list (ISS-01M5VH8H5MBRK0BFA6B8E68Z7T).
public static boolean isRune(int id) {
    return RUNE_ITEMS.containsValue(id) || id == SUNFIRE_RUNE || Arrays.stream(comboRuneIds()).anyMatch(c -> c == id);
}

public static int[] comboRuneIds() {
    return Arrays.stream(COMBOS).mapToInt(combo -> Integer.parseInt(combo[3])).toArray();
}

public static List<Map> combineCombos(List<Map> runes) {
    if (runes == null) return null;
    Map<String, Map> byName = new LinkedHashMap<>();
    for (Map rune : runes) byName.put(String.valueOf(rune.get("name")).replace(" rune", "") .toLowerCase(Locale.ROOT), rune);
    for (String[] combo : COMBOS) {
        Map a = byName.get(combo[1]);
        Map b = byName.get(combo[2]);
        if (a == null || b == null) continue;
        int qty = Math.max(((Number) a.get("qty")).intValue(), ((Number) b.get("qty")).intValue());
        Map merged = map("id", Integer.parseInt(combo[3]), "name", Names.cap(combo[0]) + " rune", "qty", qty);
        Object why = a.get("why") != null ? a.get("why") : b.get("why");
        if (why != null) merged.put("why", why);
        byName.remove(combo[1]);
        byName.remove(combo[2]);
        byName.put(combo[0], merged);
    }
    return new ArrayList<>(byName.values());
}
}
