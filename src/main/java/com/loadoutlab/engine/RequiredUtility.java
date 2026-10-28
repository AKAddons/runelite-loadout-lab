package com.loadoutlab.engine;

import com.loadoutlab.data.*;
import java.util.*;

/**
 * Utility items a fight REQUIRES that pure dps ranking would never
 * pick: Zulrah's snakelings demand a recoil source (ring of recoil,
 * a recoil-charged Ring of suffering, or Echo boots). The optimizer
 * satisfies the requirement with the least-dps-loss option after the
 * main search; owning the UNCHARGED suffering counts as access to the
 * charged form (recoil rings are pocket change).
 */
public class RequiredUtility {
    static final int RING_OF_RECOIL = 2550;
    static final int SUFFERING_RECOIL = 20655;
    static final int SUFFERING_UNCHARGED = 19550;
    static final int SUFFERING_I_RECOIL = 20657;
    static final int SUFFERING_I_UNCHARGED = 19710;
    static final int ECHO_BOOTS = 28945;

    /** Satisfying item id -> the owned id that grants access to it. */
    static final Map<Integer, Integer> RECOIL_ACCESS = new LinkedHashMap<>();

    static {
        RECOIL_ACCESS.put(SUFFERING_I_RECOIL, SUFFERING_I_UNCHARGED);
        RECOIL_ACCESS.put(SUFFERING_RECOIL, SUFFERING_UNCHARGED);
        RECOIL_ACCESS.put(ECHO_BOOTS, ECHO_BOOTS);
        RECOIL_ACCESS.put(RING_OF_RECOIL, RING_OF_RECOIL);
    }


    public static boolean requiresRecoil(MonsterStats monster) {
        return monster != null && "zulrah".equalsIgnoreCase(monster.name);
    }

    public static boolean hasRecoil(Loadout loadout) {
        for (GearItem item : loadout.getGear().values()) {
            if (item != null && RECOIL_ACCESS.containsKey(item.id)) return true;
        }
        return false;
    }

    /** Recoil items this request may equip, best-first preference. */
    public static List<GearItem> recoilCandidates(LoadoutData data, OptimizationRequest request) {
        List<GearItem> result = new ArrayList<>();
        for (var entry : RECOIL_ACCESS.entrySet()) {
            GearItem item = data.getGear(entry.getKey());
            if (item == null || request.isExcluded(item.id) || !request.requirementProfile.canEquip(item.requirements)) {
                continue;
            }
            boolean accessible = request.candidateMode == CandidateMode.ALL_STANDARD
                || request.ownedItems.owns(entry.getKey()) || request.ownedItems.owns(entry.getValue())
                || request.isDream(entry.getKey());
            if (accessible) result.add(item);
        }
        return result;
    }
}
