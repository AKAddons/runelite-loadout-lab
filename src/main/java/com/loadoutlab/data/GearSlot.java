// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;


import lombok.Getter;

@Getter
public enum GearSlot {
HEAD("head"), CAPE("cape"), NECK("neck"), AMMO("ammo"), WEAPON("weapon"), BODY("body"), SHIELD("shield"),
LEGS("legs"), HANDS("hands"), FEET("feet"), RING("ring");

public final String jsonName;

GearSlot(String jsonName) { this.jsonName = jsonName; }


static GearSlot fromJson(String value) {
    if (value == null) return null;

    String normalized = Names.lower(value);
    for (GearSlot slot : values()) if (slot.jsonName.equals(normalized)) return slot;
    return null;
}
}
