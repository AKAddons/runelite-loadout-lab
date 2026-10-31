// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.ll.data;

import lombok.Getter;

@Getter
public class Spell {
public String name;
public int maxHit;
public int mag;
public String spellbook;
public String element;
public String nameFirstWord;
public String word2;
// "magic: Fire Surge" (a magic result's attack type) and "amulet of fire"
// (its +2 marker), built once: per trial they were 13% of a roster's
// allocation, and a fresh marker re-hashed on every check.
public String attack, amulet;

public Spell(String name, int maxHit, int mag, String spellbook, String element) {
    this.name = name == null ? "" : name;
    this.maxHit = maxHit;
    this.mag = mag;
    this.spellbook = spellbook == null ? "" : spellbook;
    this.element = element == null ? "" : element;
    // The elemental-class max hit re-derived these per DPS trial via
    // split() - a String[] allocation in the optimizer's hottest loop.
    // Null unless the name is exactly two words ("Fire Surge").
    int space = this.name.indexOf(' ');
    boolean twoWords = space > 0 && this.name.indexOf(' ', space + 1) < 0 && space < this.name.length() - 1;
    nameFirstWord = twoWords ? this.name.substring(0, space) : null;
    word2 = twoWords ? this.name.substring(space + 1) : null;
    attack = "magic: " + this.name;
    amulet = "amulet of " + this.element;
}

// First word of a two-word spell name, else null (cached).

// Second word of a two-word spell name, else null (cached).






public String toString() { return name; }
}
