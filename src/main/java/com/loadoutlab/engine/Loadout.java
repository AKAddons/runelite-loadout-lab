// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import lombok.Getter;
import com.loadoutlab.data.*;
import java.util.*;

@Getter
public class Loadout {
    final EnumMap<GearSlot, GearItem> gear;
    public final StatBlock offensive;
    public final StatBlock defensive;
    public final StatBlock bonuses;
    public final int cost;
    // The worn items (a null is an empty slot): the name-fragment checks
    // scan these, so a trial builds no joined name string (that was 27% of
    // the optimizer's allocation).
    final GearItem[] worn;
    // GearItem.marks of the worn items, built on the first check.
    volatile int[] marks;

    public Loadout(Map<GearSlot, GearItem> gear) { this(copyOf(gear), true); }

    // Adopt the given map WITHOUT copying: the caller hands over ownership
    // and must never mutate it afterward. The optimizer builds a fresh
    // EnumMap per beam trial - the public constructor's defensive copy
    // doubled that work across hundreds of thousands of trials.
    public static Loadout adopting(EnumMap<GearSlot, GearItem> gear) { return new Loadout(gear, true); }

    static EnumMap<GearSlot, GearItem> copyOf(Map<GearSlot, GearItem> gear) {
        var copy = new EnumMap<GearSlot, GearItem>(GearSlot.class);
        copy.putAll(gear);
        return copy;
    }

    // The marker keeps this overload from ever being chosen by a plain
    // {@code new Loadout(enumMap)} - adoption must be an explicit opt-in.
    Loadout(EnumMap<GearSlot, GearItem> gear, boolean adoptMarker) {
        this.gear = gear;

        // Summed into an int array, not StatBlock.plus chains - the chain
        // allocated ~3 blocks per worn item per trial (top allocation site).
        int[] off = new int[9];
        int[] def = new int[9];
        int[] bon = new int[9];
        int totalCost = 0;
        for (GearItem item : this.gear.values()) {
            if (item == null) continue;
            add(off, item.offensive);
            add(def, item.defensive);
            add(bon, item.bonuses);
            totalCost += item.getPriceOrZero();
        }
        offensive = block(off);
        defensive = block(def);
        bonuses = block(bon);
        cost = totalCost;
        worn = gear.values().toArray(new GearItem[0]);
    }

    static void add(int[] acc, StatBlock block) {
        acc[0] += block.stab;
        acc[1] += block.slash;
        acc[2] += block.crush;
        acc[3] += block.magic;
        acc[4] += block.ranged;
        acc[5] += block.strength;
        acc[6] += block.rangedStrength;
        acc[7] += block.magicDamage;
        acc[8] += block.prayer;
    }

    static StatBlock block(int[] acc) {
        return new StatBlock(acc[0], acc[1], acc[2], acc[3], acc[4], acc[5], acc[6], acc[7], acc[8]);
    }

    // Ammunition carried IN a Dizana's quiver rather than the ammo slot
    // (see QuiverAmmo). Set once by the display post-pass on final
    // results - never inside the beam - so the field stays off the hot
    // path. Deliberately OUTSIDE the stat sums: QuiverAmmo.strength adds
    // it in the max-hit chain, mirroring the blowpipe's loaded dart.
    public GearItem quiverAmmo;

    // A copy with ammunition moved into the quiver (see QuiverAmmo).
    public Loadout withQuiverAmmo(GearItem carried) {
        var copy = new Loadout(copyOf(gear), true);
        copy.quiverAmmo = carried;
        return copy;
    }

    // A copy with one slot changed (null empties it); the quiver's
    // carried ammo rides along.
    public Loadout with(GearSlot slot, GearItem item) {
        EnumMap<GearSlot, GearItem> copy = copyOf(gear);
        if (item == null) {
            copy.remove(slot);
        } else copy.put(slot, item);
        var out = new Loadout(copy, true);
        out.quiverAmmo = quiverAmmo;
        return out;
    }

    // The worn items, empty slots skipped.
    public List<GearItem> items() {
        List<GearItem> items = new ArrayList<>(gear.size());
        for (GearItem item : gear.values()) {
            if (item != null) items.add(item);
        }
        return items;
    }

    // The worn item ids.
    public Set<Integer> ids() {
        Set<Integer> ids = new HashSet<>();
        for (GearItem item : items()) ids.add(item.id);
        return ids;
    }

    public GearItem get(GearSlot slot) { return gear.get(slot); }

    public GearItem getWeapon() { return gear.get(GearSlot.WEAPON); }

    public Map<GearSlot, GearItem> getGear() { return Collections.unmodifiableMap(gear); }

    // A worn item's name holds the lower-case marker; active = "Inactive"
    // versions (uncharged crystal) never match.
    boolean wears(String marker, boolean active) {
        int bit = GearItem.markerBit(marker);
        int[] m = active ? GearItem.marks(worn, true) : marks;
        if (m == null || m[0] <= bit) marks = m = GearItem.marks(worn, false);
        return (m[1 + (bit >> 5)] >>> bit & 1) != 0;
    }
}
