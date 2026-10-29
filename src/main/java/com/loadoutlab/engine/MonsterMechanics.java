package com.loadoutlab.engine;

import com.google.gson.JsonObject;
import com.loadoutlab.data.*;
import java.util.*;
import static com.loadoutlab.engine.CombatStyle.*;

/**
 * Per-NPC combat mechanics the stat sheets cannot express: style
 * immunities, the NPCs whose magic defence uses their Defence level, and
 * damage-modifier rules. The id lists are vendored from the official
 * calculator's constants (game facts, regenerated with the data - see
 * scripts/official-harness/README.md); the weapon rules are wiki-sourced.
 */
public class MonsterMechanics {
    static final JsonObject ROOT = JsonResources.gz("/com/loadoutlab/data/npc_mechanics.json.gz").getAsJsonObject();

    /** A file id list as a bitset: a Set<Integer> read boxed per trial. */
    static final class Ids {
        final BitSet bits = new BitSet();

        boolean contains(int id) { return id >= 0 && bits.get(id); }
    }

    static Ids ids(String key) {
        Ids ids = new Ids();
        for (var id : ROOT.getAsJsonArray(key)) ids.bits.set(id.getAsInt());
        return ids;
    }

    static final Ids IMMUNE_MAGIC = ids("immuneMagic"), IMMUNE_RANGED = ids("immuneRanged"),
        IMMUNE_MELEE = ids("immuneMelee"), IMMUNE_ANCIENTS = ids("immuneAncients"), SALAMANDER_ONLY_MELEE = ids("immuneNonSalamanderMelee"),
        MAGIC_DEFENCE_BY_DEF_LEVEL = ids("usesDefenceLevelForMagic"), ZULRAH = ids("zulrah"), VESPULA = ids("vespula"),
        GUARDIANS = ids("guardians"), TEKTON = ids("tekton"), ICE_DEMON = ids("iceDemon"),
        RESPIRATORY_SYSTEMS = ids("respiratorySystems"), SALARIN = ids("salarin"), NIBBLERS = ids("nibblers"),
        WARDEN_CORES = ids("wardenCores"), FRESH_BAR = ids("freshBar"), TOA_INVOCATION_SCALED = ids("toaInvocationScaled");



    /** "immune_melee" and its kin, built once - the per-set checks used to
     * concatenate and lower-case them every trial. */
    static final String[] IMMUNE_ATTR = Arrays.stream(CombatStyle.values())
        .map(s -> "immune_" + Names.lower(s.name())).toArray(String[]::new);

    /** Full immunity check for a concrete loadout (mirrors the official calc). */
    static final Set<String> ZOGRES = Set.of("zogre", "skogre", "slash bash");

    /** Gear a monster's own rule needs, kept past the pool's score cut: a
     * pickaxe for Slagilith, the comp ogre bow and brutal arrows for zogres,
     * ice arrows for the Fire Warrior (ISS-01M5RVDTMR5XG16HM6G2JTF7FF). */
    public static boolean keepInPool(MonsterStats monster, GearItem item) {
        if (monster == null) return false;
        String name = monster.nameLower;
        String it = item.nameLower;
        return "slagilith".equals(name) && "Pickaxe".equals(item.category)
            || ZOGRES.contains(name) && (it.equals("comp ogre bow") || it.contains("brutal"))
            || "fire warrior of lesarkus".equals(name) && it.equals("ice arrows");
    }

    public static boolean isImmune(MonsterStats monster, CombatStyle style, Loadout loadout, SpellStats spell) {
        if (monster == null) return false;
        String name = monster.nameLower;
        String element = spell == null || spell.element == null ? "" : spell.element;
        GearItem ammo = loadout.get(GearSlot.AMMO);
        String ammoName = ammo == null ? "" : ammo.nameLower;
        // Reference isImmune (Fareed, the Fire Warrior) and the wiki (Kamil:
        // fire spells only, no powered staves) - ISS-01M5RVDTMR5XG16HM6G2JTF7FF.
        if ("fareed".equals(name) && (style == MAGIC && !"water".equals(element) || style == RANGED && !ammoName.contains("arrow"))
            || "kamil".equals(name) && style == MAGIC && !"fire".equals(element)
            || "fire warrior of lesarkus".equals(name) && (style != RANGED || !"ice arrows".equals(ammoName))) {
            return true;
        }
        if (VampyreRules.immune(monster, style, loadout)) return true;
        // Data-driven immunity (synthetic group variants) gates the
        // calculator too - no loadout reaches a shielded style. One
        // exception: a PRAYER-based melee immunity (KQ's airborne form) is
        // pierced by Verac's set - the flail gates entry here, and the
        // calculator prices set-complete-or-zero.
        if (monster.hasAttribute(IMMUNE_ATTR[style.ordinal()])) {
            boolean veracPierce = style == MELEE && monster.hasAttribute("prayer_immunity")
                && loadout.getWeapon() != null && loadout.getWeapon().nameLower.contains("verac");
            if (!veracPierce) return true;
        }
        // Boat combat (wiki, verified 2026-08-31): "only ranged and magic
        // can be used; melee weapons cannot attack enemies, even if they
        // are in range to do so." A sea monster simply has no melee style
        // card - the same shape as Zulrah's rejection. (The salamander
        // scorch curiosity is deliberately unmodeled.)
        if (style == MELEE && NavalCombat.isNaval(monster.name)) {
            return true;
        }
        int id = monster.id;
        GearItem weapon = loadout.getWeapon();
        String category = weapon == null ? "" : weapon.category;
        if (style == MAGIC && IMMUNE_MAGIC.contains(id)) return true;
        // By the real id, so phase variants inherit it (Archive Guardian).
        if (style == MAGIC && spell != null && "ancient".equals(spell.spellbook) && IMMUNE_ANCIENTS.contains(monster.profileId())) {
            return true;
        }
        // Salarin the twisted (wiki): "can only be damaged by Strike
        // spells, ring of recoil damage, and dynamite(p)". Melee and
        // ranged never land; magic lands only as a Strike cast.
        if (SALARIN.contains(id)) return style != MAGIC || !salarinDamagingSpell(spell);
        if (style == RANGED && IMMUNE_RANGED.contains(id)) return true;
        if (style == MELEE && meleeBarred(id, category)) return true;
        return monster.hasAttribute("leafy") && !leafBladed(style, loadout, spell);
    }

    /** The id-list melee bars: the melee-immune rows (Zulrah can be reached
     * with a polearm), Vespula (immune despite the polearm rule for flying),
     * the salamander-only rows and the Guardians' pickaxe rule. */
    static boolean meleeBarred(int id, String category) {
        return IMMUNE_MELEE.contains(id) && !(ZULRAH.contains(id) && "Polearm".equals(category)) || VESPULA.contains(id)
            || SALAMANDER_ONLY_MELEE.contains(id) && !"Salamander".equals(category)
            || GUARDIANS.contains(id) && !"Pickaxe".equals(category);
    }

    /** Leafy monsters (turoths/kurasks): leaf-bladed melee, broad ammo, or Magic Dart. */
    static boolean leafBladed(CombatStyle style, Loadout loadout, SpellStats spell) {
        GearItem weapon = loadout.getWeapon();
        String weaponName = weapon == null ? "" : weapon.nameLower;
        if (style == MELEE) return weaponName.startsWith("leaf-bladed");
        if (style == RANGED) {
            GearItem ammo = loadout.get(GearSlot.AMMO);
            String ammoName = ammo == null ? "" : ammo.nameLower;
            return ammoName.contains("broad");
        }
        return spell != null && "Magic Dart".equals(spell.name);
    }

    /** The weapon-level bars a worn weapon faces at this monster (vampyre
     * tiers, flying, rat bone) - the calculator's gate and the pool share it. */
    public static boolean weaponUsable(MonsterStats monster, CombatStyle style, GearItem weapon) {
        return VampyreRules.canDamage(monster, style, weapon) && FlyingRules.canReach(monster, style, weapon)
            && RatBoneRules.canUse(monster, weapon);
    }

    /** Weapon-level pre-filter for candidate selection (conservative: only
     * prunes when the weapon alone decides; ammo/spell-dependent cases pass). */
    public static boolean weaponCanEverWork(MonsterStats monster, CombatStyle style, GearItem weapon) {
        if (monster == null || style != MELEE) return true;
        int id = monster.id;
        // Salarin: strike spells only - no melee weapon ever works.
        return !(meleeBarred(id, weapon == null ? "" : weapon.category)
            || RESPIRATORY_SYSTEMS.contains(id) && !ventMeleeLands(weapon) || SALARIN.contains(id)
            || monster.hasAttribute("leafy") && !weapon.nameLower.startsWith("leaf-bladed"));
    }

    /** The walk between the Sire's four vents, amortised per kill - the
     * tunable estimate behind the ranged-demonbane-first ORDER the tests
     * pin (field decision 2026-08-06). */
    static final int VENT_MELEE_WALK_TICKS = 5;

    /** Ticks a MELEE attacker spends walking between the Sire's four
     * vents, amortised per kill - and a demonbane kill IS one attack, so
     * it lands straight on the attack interval. The vents sit apart
     * across the arena; ranged and magic one-shot each from one spot,
     * which is why the bow "drastically cuts the time between kills". */
    public static int meleeReachPenaltyTicks(MonsterStats monster) {
        return isRespiratorySystem(monster) ? VENT_MELEE_WALK_TICKS : 0;
    }

    /** The vents shrug off standard melee - "magic, ranged or a halberd"
     * per the wiki, plus the melee demonbane one-shot. weaponCanEverWork
     * prunes the pool with this and damageFactor zeroes re-shows with it,
     * so pool and math can never disagree. */
    static boolean ventMeleeLands(GearItem weapon) {
        return "Polearm".equals(weapon == null ? "" : weapon.category) || isMeleeDemonbane(weapon);
    }

    /** The Inferno's nibblers - the mob whose whole role is being
     * barraged: three spawn per wave and one AoE cast clears the set. */
    public static boolean isNibbler(MonsterStats monster) {
        return monster != null && NIBBLERS.contains(monster.id);
    }

    /** Salarin the twisted - strike-spells-only, flat damage. */
    public static boolean isSalarin(MonsterStats monster) {
        return monster != null && SALARIN.contains(monster.id);
    }

    /** Salarin's whitelist - the only casts that damage him. isImmune and
     * the optimizer's spell pool enforce the same fact through this. */
    public static boolean salarinDamagingSpell(SpellStats spell) {
        return spell != null && "Strike".equals(spell.nameSecondWord);
    }

    /** The Wardens' ejected core (ToA P2): melee "will always deal their
     * max hit" against it (wiki) - DpsCalculator and SpecialAttack apply
     * the same certainty rule. */
    /** One fight per seed, totem, casket or entry (Hespori, Skotizo, the
     * Mimic, Jad, Zuk, the Hunllefs, Obor, Bryophyta): the spec bar opens
     * full, so burst specs count there (DES-01M5914JFX6HHTNSAP0HANN5GA). */
    public static boolean startsWithFullBar(MonsterStats monster) {
        return monster != null && FRESH_BAR.contains(monster.id);
    }

    public static boolean isWardenCore(MonsterStats monster) {
        return monster != null && WARDEN_CORES.contains(monster.id) && monster.versionStartsWith("Core-ejected");
    }

    /** ToA rows whose defence scales with the raid's invocation level
     * (vendored from the official calc, which excludes the Kephri
     * overlords). profileId maps the group's synthetic phase variants
     * back to their real rows. */
    /** HP times the group's kit weight - the shared-kit currency. Zero for
     * a spec-only member (the Warden's Core, ISS-01M5QEFMBSZXAHV84ZXF1YZHWY,
     * Andrew 2026-10-24: "we dont care about he backup melee weapon for it"):
     * its main weapon has no say in any kit, swap or strip. */
    public static double kitWeight(MonsterStats monster) {
        return monster != null && monster.hasAttribute("spec_only") ? 0 : specWeight(monster);
    }

    /** HP times the group's weight - the trip's spec currency, a spec-only
     * member at full weight ("we should only be measuring the spec hits of DDS"). */
    public static double specWeight(MonsterStats monster) {
        String w = monster == null ? null : monster.attributeValue("weight:");
        return Math.max(1, monster == null ? 1 : monster.hitpoints) * (w == null ? 1 : Integer.parseInt(w));
    }

    /** The spec weapon family a group stipulates for this mob, or null. */
    public static String specStipulation(MonsterStats monster) {
        return monster == null ? null : monster.attributeValue("spec:");
    }

    public static boolean isToaInvocationScaled(MonsterStats monster) {
        return monster != null && TOA_INVOCATION_SCALED.contains(monster.profileId());
    }

    /** The monster priced at a raid level: a factored copy for scaled ToA
     * rows (level 0 clears an earlier factor), the monster itself
     * otherwise. */
    public static MonsterStats atToaInvocation(MonsterStats monster, int level) {
        return isToaInvocationScaled(monster) ? monster.withToaInvocation(level) : monster;
    }

    /** The Sire's vents (per-monster rules live on the id set). */
    public static boolean isRespiratorySystem(MonsterStats monster) {
        return monster != null && RESPIRATORY_SYSTEMS.contains(monster.id);
    }

    /** Demonbane in EVERY style, holy water excluded: the class that
     * destroys a respiratory system on any landed hit. The Scorching bow
     * and Purging staff belong here too - field report 2026-08-05: the
     * melee-only reading ranked a blowpipe above the bow that one-shots.
     * The vocabulary itself is precomputed on GearItem (one list for this,
     * TormentedDemonRules, and the optimizer's demon bump). */
    public static boolean isDemonbane(GearItem weapon) { return weapon != null && weapon.demonbane; }

    /** Melee demonbane - the class that destroys a respiratory system on
     * any successful hit (wiki: "demonbane weapons (other than holy water)
     * will instantly kill the systems"). */
    static boolean isMeleeDemonbane(GearItem weapon) { return weapon != null && weapon.meleeDemonbane; }

    /** Some NPCs' magic defence rolls use their Defence level, not Magic. */
    public static boolean magicDefenceUsesDefenceLevel(MonsterStats monster) {
        return monster != null && MAGIC_DEFENCE_BY_DEF_LEVEL.contains(monster.id);
    }

    /**
     * Damage scale from per-monster rules (applied like the vampyre factor):
     * Corporeal Beast halves everything except stab spears/halberds/fang and
     * magic; Kraken takes 1/7 from ranged; Tekton 1/5 from magic; the CoX Ice
     * demon 1/3 unless fire spells or demonbane; Slagilith 1/3 without a
     * pickaxe; zogres quarter damage (Crumble Undead: half).
     */
    public static double damageFactor(MonsterStats monster, CombatStyle style, Loadout loadout, String attackType, SpellStats spell) {
        if (monster == null) return 1.0;
        String name = monster.nameLower;
        GearItem weapon = loadout.getWeapon();
        String weaponName = weapon == null ? "" : weapon.nameLower;
        // The Sire's vents: standard melee does NOTHING (wiki). The pool
        // already filters on the SAME predicate in weaponCanEverWork, but
        // re-shows (the kit pass, shared-set evaluation) call calculate()
        // directly, so the math must agree or a whip line renders a
        // phantom dps against them.
        if (style == MELEE && RESPIRATORY_SYSTEMS.contains(monster.id) && !ventMeleeLands(weapon)) {
            return 0.0;
        }
        if ("corporeal beast".equals(name) && !corpbane(style, weaponName, attackType)) return 0.5;
        if (("kraken".equals(name) || "cave kraken".equals(name)) && style == RANGED) {
            return 1.0 / 7.0;
        }
        if (TEKTON.contains(monster.id) && style == MAGIC) return 0.2;
        if (ICE_DEMON.contains(monster.id)) {
            boolean fire = spell != null && "fire".equals(spell.element);
            if (!fire) return 1.0 / 3.0;
        }
        if ("slagilith".equals(name) && !"Pickaxe".equals(weapon == null ? "" : weapon.category)) {
            return 1.0 / 3.0;
        }
        if (ZOGRES.contains(name)) {
            if (spell != null && "Crumble Undead".equals(spell.name)) return 0.5;
            // Brutal arrows from a comp ogre bow land in full (reference; ISS-01M5RVDTMR5XG16HM6G2JTF7FF).
            GearItem ammo = loadout.get(GearSlot.AMMO);
            boolean brutal = style == RANGED && weaponName.equals("comp ogre bow") && ammo != null
                && ammo.nameLower.contains("brutal");
            return brutal ? 1.0 : 0.25;
        }
        return 1.0;
    }

    static boolean corpbane(CombatStyle style, String weaponName, String attackType) {
        if (style == MAGIC) return true;
        if (style != MELEE || attackType == null || !attackType.startsWith("stab")) return false;
        return weaponName.contains("osmumten's fang") || weaponName.endsWith("halberd")
            || (weaponName.contains("spear") && !weaponName.equals("blue moon spear"));
    }
}
