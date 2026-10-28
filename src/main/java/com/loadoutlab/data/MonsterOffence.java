package com.loadoutlab.data;

import java.util.*;

/**
 * A monster's offensive sheet - everything needed to compute the damage it
 * does to the PLAYER: attack levels, aggressive bonuses, attack speed, and
 * the styles it attacks with (wiki infobox strings such as "Crush",
 * "Ranged", "Magic", "Typeless", "Dragonfire").
 */
import lombok.*;

@Getter
@AllArgsConstructor
public class MonsterOffence {
    public static final MonsterOffence NONE = new MonsterOffence( 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 4, List.of());

    public final int attackLevel;
    public final int strengthLevel;
    public final int rangedLevel;
    public final int magicLevel;
    public final int attackBonus;
    public final int strengthBonus;
    public final int rangedBonus;
    public final int rangedStrengthBonus;
    public final int magicBonus;
    public final int magicStrengthBonus;
    /** Raw: 0 / -1 means the wiki lists no speed, and the incoming model
     * must say "unknown" rather than run the monster at one tick. */
    public final int speedTicks;
    public final List<String> styles;
    /** The wiki's listed max hits; NONE when the sheet gives none. */
    public final MaxHits listedMaxHits;

    public MonsterOffence( int attackLevel, int strengthLevel, int rangedLevel, int magicLevel, int attackBonus,
        int strengthBonus, int rangedBonus, int rangedStrengthBonus, int magicBonus, int magicStrengthBonus,
        int speedTicks, List<String> styles) {
        this(attackLevel, strengthLevel, rangedLevel, magicLevel, attackBonus, strengthBonus,
            rangedBonus, rangedStrengthBonus, magicBonus, magicStrengthBonus, speedTicks,
            styles == null ? List.of() : styles, MaxHits.NONE);
    }
}
