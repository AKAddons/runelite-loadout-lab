package com.loadoutlab.engine;

import com.loadoutlab.data.*;
import java.util.*;
import lombok.*;

/**
 * Rules a set must keep (ISS-01M5XA7NVZYGVR3EVTM05XHXKP): thresholds over the
 * worn set's totals, banned item names and an optional weapon allow-list - a
 * combat achievement's restrictions or the player's own ("at least 12
 * prayer"). Names match case-insensitively on the item name, every version.
 */
@Getter
public final class SetRules {
    /** {stat, op, value}: op is >=, <=, > or <. */
    @Value
    public static class Rule {
        String stat;
        String op;
        double value;

        public boolean holds(double total) {
            switch (op) {
                case ">=": return total >= value;
                case "<=": return total <= value;
                case ">": return total > value;
                default: return total < value;
            }
        }

        /** Whether some total in [lo, hi] could still hold. */
        boolean reachable(double lo, double hi) { return op.startsWith(">") ? holds(hi) : holds(lo); }
    }

    private final List<Rule> rules;
    private final Set<String> banned;
    /** Null = any weapon. */
    private final Set<String> weapons;

    private SetRules(List<Rule> rules, Set<String> banned, Set<String> weapons) {
        this.rules = List.copyOf(rules);
        this.banned = lower(banned);
        this.weapons = weapons == null ? null : lower(weapons);
    }

    public static SetRules of(List<Rule> rules, Set<String> banned, Set<String> weapons) {
        return new SetRules(rules == null ? List.of() : rules, banned == null ? Set.of() : banned, weapons);
    }

    private static Set<String> lower(Set<String> names) {
        Set<String> out = new HashSet<>();
        for (String n : names) out.add(Names.lower(n));
        return out;
    }

    /** May this item be worn at all (bans, and the weapon allow-list for weapons)? */
    public boolean allows(GearItem item) {
        if (item == null) return true;
        if (banned.contains(item.getNameLower())) return false;
        return weapons == null || item.getSlot() != GearSlot.WEAPON || weapons.contains(item.getNameLower());
    }

    /** One item's contribution to a stat. */
    public static double of(GearItem item, String stat) {
        if (item == null) return 0;
        StatBlock off = item.getOffensive(), def = item.getDefensive(), bon = item.getBonuses();
        switch (stat) {
            case "weight": return item.getWeight();
            case "str": return bon.getStrength();
            case "ranged_str": return bon.getRangedStrength();
            case "magic_str": return bon.getMagicDamage();
            case "prayer": return bon.getPrayer();
            default:
                return stat.startsWith("def_") ? def.getAttackBonus(stat.substring(4)) : off.getAttackBonus(stat);
        }
    }

    public static double total(Loadout loadout, String stat) {
        double sum = 0;
        for (GearItem item : loadout.getGear().values()) sum += of(item, stat);
        return sum;
    }

    /** Every rule holds on this finished set. */
    public boolean holds(Loadout loadout) {
        for (Rule rule : rules) {
            if (!rule.holds(total(loadout, rule.getStat()))) return false;
        }
        return true;
    }
}
