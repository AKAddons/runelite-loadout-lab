package com.ll.data;

import java.util.*;

// A monster's offensive sheet - everything needed to compute the damage it
// does to the PLAYER: attack levels, aggressive bonuses, attack speed, and
// the styles it attacks with (wiki infobox strings such as "Crush",
// "Ranged", "Magic", "Typeless", "Dragonfire").
import lombok.*;

@Getter
@AllArgsConstructor
public class MonsterOffence {
static final MonsterOffence NONE = new MonsterOffence( 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 4, List.of());

public int attackLevel;
public int strengthLevel;
public int rangedLevel;
public int mag;
public int attackBonus;
public int strengthBonus;
public int rangedBonus;
public int rangedStrengthBonus;
public int magicBonus;
public int magicStrengthBonus;
// Raw: 0 / -1 means the wiki lists no speed, and the incoming model
// must say "unknown" rather than run the monster at one tick.
public int speedTicks;
public List<String> styles;
// The wiki's listed max hits; NONE when the sheet gives none.
public MaxHits listedMaxHits;

public MonsterOffence( int attackLevel, int strengthLevel, int rangedLevel, int mag, int attackBonus,
    int strengthBonus, int rangedBonus, int rangedStrengthBonus, int magicBonus, int magicStrengthBonus,
    int speedTicks, List<String> styles) {
    this(attackLevel, strengthLevel, rangedLevel, mag, attackBonus, strengthBonus,
        rangedBonus, rangedStrengthBonus, magicBonus, magicStrengthBonus, speedTicks,
        styles == null ? List.of() : styles, MaxHits.NONE);
}
}
