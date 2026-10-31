// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

import lombok.*;

@AllArgsConstructor
@Getter
public class StatBlock {
public static final StatBlock ZERO = new StatBlock(0, 0, 0, 0, 0, 0, 0, 0, 0);

public final int stab;
public final int slash;
public final int crush;
public final int magic;
public final int ranged;
public final int strength;
public final int rangedStrength;
public final int magicDamage;
public final int prayer;

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
