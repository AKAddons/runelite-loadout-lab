package com.loadoutlab.engine;

import com.loadoutlab.data.*;
import java.util.*;
import lombok.*;
import java.util.regex.Pattern;

// Rules a set must keep (ISS-01M5XA7NVZYGVR3EVTM05XHXKP): thresholds over the
// worn set's totals, banned item names and an optional weapon allow-list - a
// combat achievement's restrictions or the player's own ("at least 12
// prayer"). Names match case-insensitively on the item name, every version.
// The task-wide kinds (ISS-01M5ZQH7H4ZS95N6274QQ7EAJB): the styles allowed,
// weapon categories, demonbane, one-handed, no weapon, one melee attack type,
// no prayer, one spellbook, spell families and a level cap.
@Getter
@ToString
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SetRules {
    // {stat, op, value}: op is >=, <=, > or <.
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

        // Whether some total in [lo, hi] could still hold.
        boolean reachable(double lo, double hi) { return op.startsWith(">") ? holds(hi) : holds(lo); }
    }

    @Builder.Default
    final List<Rule> rules = List.of();
    @Builder.Default
    final Set<String> banned = Set.of();
    // Weapon names allowed; null = no name list.
    final Set<String> weapons;
    // Weapon categories allowed (lowercase: "spear", "salamander"); null = none.
    final Set<String> weaponCategories;
    // A demonbane weapon is allowed (with the lists above, any one match does).
    final boolean demonbaneWeapon;
    final boolean bannedDemonbane;
    final boolean oneHanded;
    // No weapon at all: melee punches, magic casts manually.
    final boolean unarmed;
    // Magic may search an empty weapon slot, casting manually.
    final boolean noWeaponMagic;
    // The combat styles allowed (other styles get no set); null = every style.
    final Set<CombatStyle> styles;
    // The one melee attack type ("stab", "crush"), or null.
    public final String attackType;
    // No prayer-restoring supplies (The Praying Mantis: pray, never restore).
    public final boolean noPrayerRestore;
    // The one spellbook ("arceuus"), or null.
    public final String spellbook;
    // Fragments a spell's name must contain (any one), or null.
    public final List<String> spells;
    // No item may need a level above this; 0 = no cap.
    final int levelCap;
    // The weapon must poison or envenom (A Slow Death, ISS-01M60HTYZT9DTZKDSFAGRNNK53).
    final boolean poisonWeapon;
    // A free-to-play world: the page's F2P lock rides the task.
    public final boolean f2p;
    // Further weapon allow-lists and spell families, each of which must also
    // hold - what and() keeps apart, since one list is an OR within itself.
    @Builder.Default
    final List<SetRules> also = List.of();

    public static SetRules of(List<Rule> rules, Set<String> banned, Set<String> weapons) {
        return builder().rules(rules).banned(lower(banned)).weapons(weapons == null ? null : lower(weapons)).build();
    }

    // "prayer>=12;def_ranged>=100" -> rules, in order; a part that does not parse is skipped.
    public static List<Rule> parse(String text) {
        List<Rule> out = new ArrayList<>();
        if (text == null) return out;
        for (String part : text.split(";")) {
            var m = Pattern.compile("\\s*([a-z_:]+)\\s*(>=|<=|>|<)\\s*(-?[0-9.]+)\\s*").matcher(part);
            if (m.matches() && (STATS.contains(m.group(1)) || m.group(1).startsWith("god:"))) out.add(new Rule(m.group(1), m.group(2), Double.parseDouble(m.group(3))));
        }
        return out;
    }

    // The stats a rule may name.
    public static final List<String> STATS = List.of("stab", "slash", "crush", "magic", "ranged", "def_stab", "def_slash",
        "def_crush", "def_magic", "def_ranged", "str", "ranged_str", "magic_str", "prayer", "weight");

    // These rules plus more thresholds (everything else kept).
    public SetRules plus(List<Rule> more) {
        List<Rule> all = new ArrayList<>(rules);
        all.addAll(more);
        return toBuilder().rules(List.copyOf(all)).build();
    }

    static Set<String> lower(Set<String> names) {
        Set<String> out = new HashSet<>();
        for (String n : names) out.add(Names.lower(n));
        return out;
    }

    // These rules AND another set's (ISS-01M601Z57QM43W7SHNHCRMWDCE: two tasks at once, Andrew 2026-10-27:
    // "the challenge cards in the requirements seem to operate on an OR basis
    // but can and should be able to be AND"): thresholds and bans add up, the
    // styles narrow, and each weapon list must hold on its own.
    public SetRules and(SetRules o) {
        if (o == null) return this;
        List<Rule> all = new ArrayList<>(rules);
        all.addAll(o.rules);
        Set<String> bans = new HashSet<>(banned);
        bans.addAll(o.banned);
        Set<CombatStyle> both = styles == null ? o.styles : o.styles == null ? styles : EnumSet.copyOf(styles);
        if (styles != null && o.styles != null) both.retainAll(o.styles);
        List<SetRules> parts = new ArrayList<>(also);
        parts.addAll(o.also);
        boolean mine = hasWeaponList(), theirs = o.hasWeaponList();
        if (theirs && mine || o.spells != null && spells != null) parts.add(o.weaponsAndSpellsOnly());
        int cap = levelCap == 0 ? o.levelCap : o.levelCap == 0 ? levelCap : Math.min(levelCap, o.levelCap);
        var b = toBuilder().rules(List.copyOf(all)).banned(bans).styles(both).also(List.copyOf(parts))
            .bannedDemonbane(bannedDemonbane || o.bannedDemonbane).oneHanded(oneHanded || o.oneHanded)
            .unarmed(unarmed || o.unarmed).noPrayerRestore(noPrayerRestore || o.noPrayerRestore)
            .attackType(attackType != null ? attackType : o.attackType).spellbook(spellbook != null ? spellbook : o.spellbook)
            .levelCap(cap).poisonWeapon(poisonWeapon || o.poisonWeapon).f2p(f2p || o.f2p)
            // Empty hands only when some task asks for them and no weapon list forbids them.
            .noWeaponMagic((noWeaponMagic || o.noWeaponMagic) && (noWeaponMagic || !mine) && (o.noWeaponMagic || !theirs));
        if (!mine && theirs) b.weapons(o.weapons).weaponCategories(o.weaponCategories).demonbaneWeapon(o.demonbaneWeapon);
        if (spells == null && o.spells != null) b.spells(o.spells);
        return b.build();
    }

    boolean hasWeaponList() { return weapons != null || weaponCategories != null || demonbaneWeapon; }

    SetRules weaponsAndSpellsOnly() {
        return builder().weapons(weapons).weaponCategories(weaponCategories).demonbaneWeapon(demonbaneWeapon).spells(spells).build();
    }

    // May this style have a set at all?
    public boolean allowsStyle(CombatStyle style) { return styles == null || styles.contains(style); }

    // May this item be worn at all (bans, the level cap, and the weapon rules for weapons)?
    public boolean allows(GearItem item) {
        if (item == null) return true;
        if (banned.contains(item.nameLower)) return false;
        if (levelCap > 0 && item.requirements.getSkills().values().stream().anyMatch(level -> level > levelCap)) return false;
        if (item.slot != GearSlot.WEAPON) return true;
        if (unarmed || bannedDemonbane && item.demonbane || oneHanded && item.twoHanded) return false;
        if (attackType != null && WeaponStyles.melee(item).stream().noneMatch(s -> s.attackType.equals(attackType))) return false;
        if (poisonWeapon && !poisons(item)) return false;
        for (SetRules part : also) if (!part.allows(item)) return false;
        if (weapons == null && weaponCategories == null && !demonbaneWeapon) return true;
        return weapons != null && weapons.contains(item.nameLower)
            || weaponCategories != null && weaponCategories.contains(item.categoryLower)
            || demonbaneWeapon && item.demonbane;
    }

    // A poisoned version, or a charged venom weapon (toxic blowpipe, toxic staff).
    public static boolean poisons(GearItem weapon) {
        if (weapon.poisonTier() > 0) return true;
        String version = Names.lower(weapon.version);
        return weapon.nameLower.startsWith("toxic ") && !version.contains("uncharged") && !version.contains("empty");
    }

    // May magic cast with no weapon?
    public boolean emptyHanded(CombatStyle style) {
        return unarmed && style != CombatStyle.RANGED || noWeaponMagic && style == CombatStyle.MAGIC;
    }

    // May this spell be cast?
    public boolean allowsSpell(String spellNameLower) {
        return (spells == null || spells.stream().anyMatch(spellNameLower::contains))
            && also.stream().allMatch(part -> part.allowsSpell(spellNameLower));
    }

    // One item's contribution to a stat: a bonus, weight, price, "has:<name>"
    // (1 when the name starts with it) or "slot:<slot>" (1 in that slot).
    public static double of(GearItem item, String stat) {
        if (item == null) return 0;
        if (stat.startsWith("has:")) return item.nameLower.startsWith(stat.substring(4)) ? 1 : 0;
        if (stat.startsWith("slot:")) return item.slot.name().equalsIgnoreCase(stat.substring(5)) ? 1 : 0;
        // "god:<name>" (or god:any): 1 when the item protects from that god in
        // the God Wars dungeon (Andrew 2026-10-30: "a restriction for each of
        // the god types ... also an any god item").
        if (stat.startsWith("god:")) {
            Set<String> gods = GwdProtection.godsOf(item);
            return ("god:any".equals(stat) ? !gods.isEmpty() : gods.contains(stat.substring(4))) ? 1 : 0;
        }
        StatBlock off = item.offensive, def = item.defensive, bon = item.bonuses;
        switch (stat) {
            case "weight": return item.weight;
            case "price": return item.getRulePrice();
            case "str": return bon.strength;
            case "ranged_str": return bon.rangedStrength;
            case "magic_str": return bon.magicDamage;
            case "prayer": return bon.prayer;
            default:
                return stat.startsWith("def_") ? def.getAttackBonus(stat.substring(4)) : off.getAttackBonus(stat);
        }
    }

    public static double total(Loadout loadout, String stat) {
        double sum = 0;
        for (GearItem item : loadout.getGear().values()) sum += of(item, stat);
        return sum;
    }

    // Every item is allowed and every rule holds on this finished set.
    public boolean holds(Loadout loadout) {
        for (GearItem item : loadout.getGear().values()) {
            if (!allows(item)) return false;
        }
        if (poisonWeapon && loadout.getWeapon() == null) return false;
        for (Rule rule : rules) {
            if (!rule.holds(total(loadout, rule.getStat()))) return false;
        }
        return true;
    }
}
