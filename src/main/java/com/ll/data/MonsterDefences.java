// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.ll.data;

import lombok.*;

@AllArgsConstructor
public class MonsterDefences {
public static final MonsterDefences ZERO = new MonsterDefences(0, 0, 0, 0, 0, 0, 0, 0, 0);

int stab;
int slash;
int crush;
int magic;
int ranged;
@Getter
int flatArmour;
int light;
int standard;
int heavy;

public MonsterDefences(int stab, int slash, int crush, int magic, int ranged, int light, int standard, int heavy) {
    this(stab, slash, crush, magic, ranged, 0, light, standard, heavy);
}

public int get(String type) {
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
        case "light":
            return light;
        case "standard":
            return standard;
        case "heavy":
            return heavy;
        default:
            return 0;
    }
}

}
