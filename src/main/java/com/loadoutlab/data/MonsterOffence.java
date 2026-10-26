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
public final class MonsterOffence {
    public static final MonsterOffence NONE = new MonsterOffence( 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 4, List.of());

    private final int attackLevel;
    private final int strengthLevel;
    private final int rangedLevel;
    private final int magicLevel;
    private final int attackBonus;
    private final int strengthBonus;
    private final int rangedBonus;
    private final int rangedStrengthBonus;
    private final int magicBonus;
    private final int magicStrengthBonus;
    /** Raw: 0 / -1 means the wiki lists no speed, and the incoming model
     * must say "unknown" rather than run the monster at one tick. */
    private final int speedTicks;
    private final List<String> styles;
    /** The wiki's listed max hits; NONE when the sheet gives none. */
    private final MaxHits listedMaxHits;

    public MonsterOffence( int attackLevel, int strengthLevel, int rangedLevel, int magicLevel, int attackBonus,
        int strengthBonus, int rangedBonus, int rangedStrengthBonus, int magicBonus, int magicStrengthBonus,
        int speedTicks, List<String> styles) {
        this(attackLevel, strengthLevel, rangedLevel, magicLevel, attackBonus, strengthBonus,
            rangedBonus, rangedStrengthBonus, magicBonus, magicStrengthBonus, speedTicks,
            styles == null ? List.of() : Collections.unmodifiableList(styles), MaxHits.NONE);
    }
}
