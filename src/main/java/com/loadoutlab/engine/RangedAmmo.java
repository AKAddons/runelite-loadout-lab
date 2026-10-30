// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import com.google.gson.*;
import com.loadoutlab.data.*;
import java.util.*;
import static com.loadoutlab.data.JsonResources.*;

// Weapon/ammo compatibility. The id tables (arrow/bolt tiers, javelins,
// fixed weapon-ammo pairs) live in ranged_ammo.json - hub token cap.
public class RangedAmmo {
    static final int DORGESHUUN_CROSSBOW = 8880;
    static final Map<Integer, Integer> ARROW_TIER_BY_WEAPON = new HashMap<>();
    static final Map<Integer, Integer> BOLT_TIER_BY_WEAPON = new HashMap<>();
    static final Map<Integer, Integer> ARROW_TIER_BY_AMMO = new HashMap<>();
    static final Map<Integer, Integer> BOLT_TIER_BY_AMMO = new HashMap<>();
    static final Set<Integer> JAVELIN_AMMO = new HashSet<>();
    static final Set<Integer> JAVELIN_WEAPONS = new HashSet<>();
    static final Map<Integer, Set<Integer>> PAIRS = new HashMap<>();
    static final Set<Integer> DORGESHUUN_EXTRA_BOLTS = new HashSet<>();

    static class Root {
        Map<Integer, Integer> arrowTierByWeapon = Map.of(), boltTierByWeapon = Map.of(), arrowTierByAmmo = Map.of(), boltTierByAmmo = Map.of();
        Set<Integer> javelinAmmo = Set.of(), javelinWeapons = Set.of(), dorgeshuunExtraBolts = Set.of();
        Map<Integer, Set<Integer>> pairs = Map.of();
    }

    static {
        Root root = read("ranged_ammo.json", Root.class);
        ARROW_TIER_BY_WEAPON.putAll(root.arrowTierByWeapon);
        BOLT_TIER_BY_WEAPON.putAll(root.boltTierByWeapon);
        ARROW_TIER_BY_AMMO.putAll(root.arrowTierByAmmo);
        BOLT_TIER_BY_AMMO.putAll(root.boltTierByAmmo);
        JAVELIN_AMMO.addAll(root.javelinAmmo);
        JAVELIN_WEAPONS.addAll(root.javelinWeapons);
        DORGESHUUN_EXTRA_BOLTS.addAll(root.dorgeshuunExtraBolts);
        PAIRS.putAll(root.pairs);
    }


    // Sims plus the exclusive ammo of every simmed weapon (a crossbow
    // that fires only its own bolts is no sim without them).
    public static Set<Integer> withPairedAmmo(Set<Integer> sims) {
        Set<Integer> out = new HashSet<>(sims);
        for (int id : sims) out.addAll(PAIRS.getOrDefault(id, Set.of()));
        return out;
    }

    // Arrows and bolts come back to an Ava's device (attractor, accumulator,
    // assembler, their max-cape forms, Dizana's quiver); thrown ammo does not.
    public static boolean retrievable(GearItem ammo) {
        return ammo != null && (ARROW_TIER_BY_AMMO.containsKey(ammo.id) || BOLT_TIER_BY_AMMO.containsKey(ammo.id));
    }

    public static boolean avasDevice(GearItem cape) {
        String n = cape == null ? "" : cape.nameLower;
        return n.contains("assembler") || n.contains("accumulator") || n.contains("attractor") || n.contains("dizana's quiver");
    }

    public static boolean compatible(GearItem ammo, GearItem weapon) {
        if (ammo == null) return !weaponUsesProjectileAmmo(weapon);
        if (projectileMatches(ammo, weapon)) return true;
        return !weaponUsesProjectileAmmo(weapon) && passiveAmmoSlotItem(ammo);
    }

    static boolean strengthApplies(GearItem ammo, GearItem weapon) {
        return ammo != null && projectileMatches(ammo, weapon);
    }

    // Arrow or bolt weapons only - the classes Dizana's quiver can hold
    // (javelins, darts and chinchompas stay in their own slots).
    static boolean usesArrowsOrBolts(GearItem weapon) {
        return weapon != null && (ARROW_TIER_BY_WEAPON.containsKey(weapon.id)
                || BOLT_TIER_BY_WEAPON.containsKey(weapon.id) || weapon.id == DORGESHUUN_CROSSBOW);
    }

    // A blessing (or other passive ammo-slot item) may ride the freed
    // slot when the quiver carries the projectiles.
    static boolean passiveForQuiver(GearItem item) { return passiveAmmoSlotItem(item); }

    static boolean weaponUsesProjectileAmmo(GearItem weapon) {
        return usesArrowsOrBolts(weapon)
            || weapon != null && (JAVELIN_WEAPONS.contains(weapon.id) || PAIRS.containsKey(weapon.id));
    }

    static boolean projectileMatches(GearItem ammo, GearItem weapon) {
        if (ammo == null || weapon == null) return false;
        int ammoId = ammo.id;
        int weaponId = weapon.id;
        if (weaponId == DORGESHUUN_CROSSBOW) {
            int tier = BOLT_TIER_BY_AMMO.getOrDefault(ammoId, 0);
            return (tier > 0 && tier <= 16) || DORGESHUUN_EXTRA_BOLTS.contains(ammoId);
        }
        Set<Integer> pair = PAIRS.get(weaponId);
        if (pair != null) return pair.contains(ammoId);
        if (JAVELIN_WEAPONS.contains(weaponId)) return JAVELIN_AMMO.contains(ammoId);
        int arrowCap = ARROW_TIER_BY_WEAPON.getOrDefault(weaponId, 0);
        if (arrowCap > 0) {
            int tier = ARROW_TIER_BY_AMMO.getOrDefault(ammoId, 0);
            return tier > 0 && tier <= arrowCap;
        }
        int boltCap = BOLT_TIER_BY_WEAPON.getOrDefault(weaponId, 0);
        if (boltCap > 0) {
            int tier = BOLT_TIER_BY_AMMO.getOrDefault(ammoId, 0);
            return tier > 0 && tier <= boltCap;
        }
        return false;
    }

    static boolean passiveAmmoSlotItem(GearItem ammo) {
        return ammo.bonuses.rangedStrength <= 0 && ammo.offensive.ranged <= 0;
    }
}
