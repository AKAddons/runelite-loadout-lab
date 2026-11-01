// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.ll.data;


import lombok.Getter;

@Getter
public enum Slot {
HEAD("head"), CAPE("cape"), NECK("neck"), AMMO("ammo"), WEAPON("weapon"), BODY("body"), SHIELD("shield"),
LEGS("legs"), HANDS("hands"), FEET("feet"), RING("ring");

public String jsonName;

Slot(String jsonName) { this.jsonName = jsonName; }


static Slot fromJson(String value) {
    if (value == null) return null;

    String normalized = Names.lower(value);
    for (Slot slot : values()) if (slot.jsonName.equals(normalized)) return slot;
    return null;
}
}
