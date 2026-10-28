package com.loadoutlab.engine;

import com.loadoutlab.data.MonsterStats;

/** Style-level immunity, independent of the weapon - a test probe of the
 * immunity data (it left main source in slim 5: nothing there asked it). */
public final class Immunity {
    private Immunity() {
    }

    public static boolean styleImmune(MonsterStats monster, CombatStyle style) {
        if (monster == null) return false;
        // Data-driven immunity (synthetic group variants - the tormented
        // demon shield phases): the attribute wins before the id lists.
        if (monster.hasAttribute(MonsterMechanics.IMMUNE_ATTR[style.ordinal()])) return true;
        switch (style) {
            case MAGIC: return MonsterMechanics.IMMUNE_MAGIC.contains(monster.getId());
            case RANGED: return MonsterMechanics.IMMUNE_RANGED.contains(monster.getId());
            case MELEE: return MonsterMechanics.IMMUNE_MELEE.contains(monster.getId()) && !MonsterMechanics.ZULRAH.contains(monster.getId());
            default: return false;
        }
    }
}
