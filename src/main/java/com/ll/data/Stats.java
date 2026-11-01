// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.ll.data;

import lombok.*;

@AllArgsConstructor
@Getter
public class Stats {
public static final Stats ZERO = new Stats(0, 0, 0, 0, 0, 0, 0, 0, 0);

public int stab;
public int slash;
public int crush;
public int magic;
public int ranged;
public int strength;
public int rangedStrength;
public int magicDamage;
public int prayer;

public int getAttackBonus(String type) {
    switch (type) {
        case "stab":
            return stab;
        case "slash":
            return slash;
        case "crush":
            return crush;
        case "magic":
            return magic;
        case "ranged":
            return ranged;
        default:
            return 0;
    }
}









}
