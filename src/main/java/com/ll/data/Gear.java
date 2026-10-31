// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.ll.data;

import static java.lang.Math.*;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.concurrent.*;

import lombok.Getter;
import com.ll.engine.Style;
import static com.ll.data.Json.*;

@Getter
public class Gear {
// The name vocabulary behind poweredStaff, the demonbane class and
// isWeaponFor lives in gear_words.json (hub token cap); the lists are
// walked once per item at load, or once per pool build.
static final List<String> POWERED = new ArrayList<>();
static final List<String> MELEE_DEMONBANE = new ArrayList<>();
static final List<String> RANGED_WEAPON = new ArrayList<>();
static final List<String> MAGIC_WEAPON = new ArrayList<>();
static final List<String> NOT_MELEE = new ArrayList<>();
static final List<String> MELEE_STAFF = new ArrayList<>();

static {
    JsonObject words = objectOrThrow("gear_words.json");
    strings(words, "poweredStaff", POWERED);
    strings(words, "meleeDemonbane", MELEE_DEMONBANE);
    strings(words, "rangedWeapon", RANGED_WEAPON);
    strings(words, "magicWeapon", MAGIC_WEAPON);
    strings(words, "notMelee", NOT_MELEE);
    strings(words, "meleeStaff", MELEE_STAFF);
}

public int id;
public String name;
public String version;
public Slot slot;
public String category;
public int speed;
public boolean twoh;
public boolean standardGear;
public boolean tradeable;
boolean members;
public Integer estimatedPrice;
public Stats offensive;
public Stats defensive;
public Stats bonuses;
public GearRequirements requirements;
// Lowercase views, precomputed once: the engine matches items by name
// fragment on every candidate evaluation, and per-call toLowerCase was
// the single hottest path in the whole optimize (64% of CPU samples).
public String lname;
public String ver;
public String categoryLower;
String labelLower;
String nameVersionLower;
// Powered staff (built-in spell, no autocast) - see Calc.isPoweredStaff.
public boolean poweredStaff;
public boolean meleeDemonbane;
public boolean demonbane;
// Kilograms, from the corpus (negative for weight-reducing gear); set once at load.
public double weight;
// What an imbued item or slayer helmet is made from, for a task's price
// rule alone (DataService.priceComponents); 0 = nothing to count.
int componentPrice;

// Name markers by bit, assigned on first sight. marks = [markers
// covered, bits...], rebuilt when asked a newer one: a bit test beat the
// per-set substring scans (29% of a search's CPU).
static final Map<String, Integer> MARKERS = new ConcurrentHashMap<>();
static final List<String> MARKER_LIST = new CopyOnWriteArrayList<>();
volatile int[] marks = {0};

public static int markerBit(String marker) {
    Integer bit = MARKERS.get(marker);
    if (bit == null) synchronized (MARKERS) {
        assert marker.equals(Names.lower(marker)) : marker;
        bit = MARKERS.computeIfAbsent(marker, k -> {
            MARKER_LIST.add(k);
            return MARKER_LIST.size() - 1;
        });
    }
    return bit;
}

// The items' marks ORed: a set answers every marker check with one bit
// test (each spell re-asks them all of the same set). Active skips
// "Inactive" versions (uncharged crystal).
public static int[] marks(Gear[] items, boolean active) {
    int n = MARKER_LIST.size();
    int[] m = new int[2 + n / 32];
    m[0] = n;
    for (Gear item : items) {
        if (item == null || active && "inactive".equals(item.ver)) continue;
        int[] im = item.marks;
        if (im[0] < n) {
            im = new int[m.length];
            im[0] = n;
            for (int i = 0; i < n; i++) if (item.lname.contains(MARKER_LIST.get(i))) im[1 + (i >> 5)] |= 1 << i;
            item.marks = im;
        }
        for (int w = 1; w < m.length; w++) m[w] |= im[w];
    }
    return m;
}

public Gear( int id, String name, String version, Slot slot, String category, int speed, boolean twoh,
    boolean standardGear, boolean tradeable, boolean members, Integer estimatedPrice, Stats offensive,
    Stats defensive, Stats bonuses, GearRequirements requirements) {
    this.id = id;
    this.name = name == null ? "" : name;
    this.version = version == null ? "" : version;
    this.slot = slot;
    this.category = category == null ? "" : category;
    this.speed = speed;
    this.twoh = twoh;
    this.standardGear = standardGear;
    this.tradeable = tradeable;
    this.members = members;
    this.estimatedPrice = estimatedPrice;
    this.offensive = offensive == null ? Stats.ZERO : offensive;
    this.defensive = defensive == null ? Stats.ZERO : defensive;
    this.bonuses = bonuses == null ? Stats.ZERO : bonuses;
    this.requirements = requirements == null ? GearRequirements.NONE : requirements;
    lname = Names.lower(this.name);
    ver = Names.lower(this.version);
    categoryLower = Names.lower(this.category);
    labelLower = Names.lower(label());
    nameVersionLower = (this.name + " " + this.version).toLowerCase(Locale.ROOT);
    // Precomputed once: the optimizer asks this on every trial, and the
    // name-fragment chain was the single hottest call in the profile.
    // Calc.isPoweredStaff stays the API - it delegates here.
    poweredStaff = categoryLower.contains("powered staff") || POWERED.stream().anyMatch(lname::contains);
    // The demonbane weapon class (wiki), one vocabulary for everyone:
    // MonsterMechanics (the Sire's vents), TormentedDemonRules, and the
    // optimizer's demon pool bump all ask this in hot loops - same
    // precompute rationale as poweredStaff. Holy water is not gear.
    meleeDemonbane = MELEE_DEMONBANE.stream().anyMatch(lname::contains);
    demonbane = meleeDemonbane || lname.startsWith("scorching bow") || lname.startsWith("purging staff");
}

// This item as a tradeable at a price: a charged weapon priced as the
// uncharged twin you buy and charge (ISS-01M5QQV2TGQ5X730T0JJ75S1RD).
void setWeight(double weight) { this.weight = weight; }

Gear priced(int price) {
    Gear copy = new Gear(id, name, version, slot, category, speed, twoh, standardGear, true, members, price,
        offensive, defensive, bonuses, requirements);
    copy.weight = weight;
    return copy;
}

public boolean isWeaponFor(Style style) {
    if (slot != Slot.WEAPON) return false;
    switch (style) {
        case RANGED:
            return RANGED_WEAPON.stream().anyMatch(categoryLower::contains) || offensive.ranged > 0;
        case MAGIC:
            return MAGIC_WEAPON.stream().anyMatch(categoryLower::contains) || offensive.magic > 0;
        case MELEE:
        default:
            return NOT_MELEE.stream().noneMatch(categoryLower::contains) || MELEE_STAFF.stream().anyMatch(lname::startsWith);
    }
}

public double roughScore(Style style) {
    switch (style) {
        case MELEE:
            return max(offensive.stab, max(offensive.slash, offensive.crush))
                + bonuses.strength * 2.5 + max(0, bonuses.prayer) * 0.25;
        case RANGED:
            return offensive.ranged + bonuses.rangedStrength * 2.5 + max(0, bonuses.prayer) * 0.25;
        case MAGIC:
        default:
            return offensive.magic + bonuses.magicDamage * 3.0 + max(0, bonuses.prayer) * 0.25;
    }
}

public boolean isSlayerHead() {
    if (slot != Slot.HEAD) return false;
    return nameVersionLower.contains("black mask") || nameVersionLower.contains("slayer helmet");
}

public boolean imbuedHead() { return isSlayerHead() && nameVersionLower.contains("(i)"); }

public String label() { return version.isEmpty() ? name : name + " (" + version + ")"; }

// label() lowercased (Locale.ROOT), precomputed.
public String labelLower() { return labelLower; }

// Poison tier of this version: Poison++ 3, Poison+ 2, Poison 1,
// everything else 0. Poison is free damage the DPS model does not
// price, so suggestion tie-breaks prefer the higher tier (field
// request: always recommend dragon dagger p++ over plain).
public int poisonTier() {
    if (ver.contains("unpoison")) return 0;
    if (ver.contains("poison++")) return 3;
    if (ver.contains("poison+")) return 2;
    return ver.contains("poison") ? 1 : 0;
}

public int getPriceOrZero() { return estimatedPrice == null ? 0 : max(0, estimatedPrice); }

public int getRulePrice() { return max(getPriceOrZero(), componentPrice); }
}
