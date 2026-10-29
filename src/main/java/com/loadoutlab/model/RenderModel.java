package com.loadoutlab.model;

import com.loadoutlab.data.*;
import com.loadoutlab.engine.*;
import java.util.*;
import java.util.function.*;
import com.loadoutlab.engine.IncomingDpsCalculator.*;
import com.loadoutlab.optimizer.OptimizerService.*;
import com.loadoutlab.engine.PvpRisk.Assessment;
import static com.loadoutlab.data.AssumeIcons.*;
import static java.lang.String.*;
import static com.loadoutlab.engine.MultiTool.*;

/**
 * The generic render-model of ADR-0008: everything a UI needs to draw,
 * as JSON-safe maps (the Companion loads in its OWN classloader - no
 * shared classes cross the seam, only this structure). Renderers - the
 * Companion's rich panel, Core's bare text surface, any future UI -
 * consume this and never re-derive game logic.
 *
 * <p>v1 is the vertical slice straight off the optimizer's results:
 * per-mob per-style cards for both sides, spec, incoming, bench.
 * Entry parameter state, chips, and the pre-resolved display strings
 * join field-by-field as the panel port peels them out of Swing
 * (docs/COMPANION_CONTRACT.md tracks the full shape).
 */
public class RenderModel {
    /** Contract version stamped on every page; additive evolution only. */
    public static final int VERSION = 1;


    /** A node from key/value pairs, in argument order (the JSON stays
     * canonical: every builder below lists its keys in the contract's order). */
    static Map map(Object... kv) { return JsonResources.map(kv); }

    /** The side a card shows: the game-wide best, or the first owned set
     * (null for none, or for no result). */
    static DpsResult shown(StyleResult result, boolean bis) {
        return result == null ? null : bis ? result.overallBest
            : result.owned == null || result.owned.isEmpty() ? null : result.owned.get(0);
    }

    public static Map page(List<Map> entries) {
        return map("v", VERSION, "entries", entries);
    }

    /** One result entry: its mobs and, per mob, the per-style cards.
     * Single-mob pages are the one-mob case of the same shape. */
    public static Map entry(List<MonsterStats> mobs, List<Map<CombatStyle, StyleResult>> perMob) {
        return entry(mobs, perMob, -1, null, Set.of(), null);
    }

    /** Per-build provenance lookup: an item's storage NAME when a fetch
     * trip is needed (the classic source dots), "" when at hand.
     * riskKeptSlots >= 0 adds a wilderness risk node per card; ownership +
     * simmed ids flag every gear cell for the classic border language
     * (gold owned-BiS / green assumed / grey). */
    static final ThreadLocal<IntFunction<String>> LOCATION = new ThreadLocal<>();

    public static Map entry(List<MonsterStats> mobs,
        List<Map<CombatStyle, StyleResult>> perMob, int rks, IntPredicate owned, Set<Integer> simmed,
        IntFunction<String> lo) {
        LOCATION.set(lo);
        try {
            return buildEntry(mobs, perMob, rks, owned, simmed);
        } finally {
            LOCATION.remove();
        }
    }

    static Map buildEntry(List<MonsterStats> mobs,
        List<Map<CombatStyle, StyleResult>> perMob, int rks, IntPredicate owned, Set<Integer> simmed) {
        List<Object> mn = new ArrayList<>();
        for (int i = 0; i < mobs.size(); i++) {
            Map node = mob(mobs.get(i));
            MOB.set(i);
            try {
                node.put("styles", styles(perMob.get(i), rks, owned, simmed));
                Map multi = multi(perMob.get(i), rks, owned, simmed);
                if (multi != null) node.put("multi", multi);
            } finally {
                MOB.remove();
            }
            mn.add(node);
        }
        return map("mobs", mn);
    }

    /** The roster index of the mob whose card is being built: a group
     * ladder reads that mob's own numbers (ISS-01M5PE4TZXTGJAQA1YXNRKPEDW). */
    static final ThreadLocal<Integer> MOB = new ThreadLocal<>();

    static Map mob(MonsterStats mob) {
        return map("id", mob.id, "profileId", mob.profileId(), "name", mob.name,
            "version", mob.version, "label", mob.label(), "hp", mob.hitpoints,
            "level", mob.combatLevel, "invocationScaled", MonsterMechanics.isToaInvocationScaled(mob),
            "breathesFire", DragonfireRules.breathesFire(mob), "wilderness", WildernessMonsters.isWilderness(mob),
            "naval", NavalCombat.isNaval(mob.name), "raid", RaidBoosts.raidKey(mob),
            "taskOnly", SlayerLockedMonsters.isTaskOnly(mob), "fightBook", MonsterSpellbooks.bookFor(mob),
            "fightBookReason", MonsterSpellbooks.reasonFor(mob), "slayerMonster", mob.slayerMonster,
            "multiAble", MultiMobs.isMultiAble(mob), "multiLocations", MultiMobs.locations(mob),
            "wildernessExclusive", WildernessMonsters.isExclusive(mob), "gwd", GwdProtection.isGwd(mob));
    }

    static Map styles(Map<CombatStyle, StyleResult> results,
        int rks, IntPredicate owned, Set<Integer> simmed) {
        Map styles = new LinkedHashMap<>();
        for (CombatStyle style : CombatStyle.concreteValues()) {
            StyleResult result = results == null ? null : results.get(style);
            if (result == null) continue;
            styles.put(style.name().toLowerCase(), styleNode(style, result, rks, owned, simmed));
        }
        return styles;
    }

    /** The Multi card rows as style nodes keyed by tool, unscaled (the
     * engine's decoration applies the stack); null without the switch. */
    static Map multi(Map<CombatStyle, StyleResult> results,
        int rks, IntPredicate owned, Set<Integer> simmed) {
        Map<String, StyleResult> tools = null;
        for (StyleResult r : results == null ? List.<StyleResult>of() : results.values()) {
            if (r != null && r.multiTools != null) tools = r.multiTools;
        }
        if (tools == null) return null;
        Map out = new LinkedHashMap<>();
        for (MultiTool tool : MultiTool.values()) {
            StyleResult r = tools.get(tool.key);
            if (r != null) {
                Map node = styleNode(tool.style, r, rks, owned, simmed);
                node.put("multiKind", tool.kind);
                // The two ranged tools wear their own item icon on the strip
                // (Andrew 2026-10-19); the barrage keeps the magic sprite.
                node.put("styleItem", tool == CHINS ? CHIN_IDS[0] : tool == VENATOR ? VENATOR_BOW
                    : tool == HALLOWFELL ? HALLOWFELL_SWORD : 0);
                out.put(tool.key, node);
            }
        }
        return out;
    }

    static Map styleNode(CombatStyle style, StyleResult result,
        int rks, IntPredicate owned, Set<Integer> simmed) {
        DpsResult os = shown(result, false);
        // Tab facts after the sides: the sprite and each side's SHOWN dps
        // (set + spec) so a renderer can label the strip without re-deriving.
        return map("yours", card(result, false, rks, owned, simmed), "bis", card(result, true, rks, owned, simmed),
            "boostLabel", result.boostLabel, "bisBoostLabel", result.gameBoostLabel,
            "assume", assumeNode(result.boostLabel), "bisAssume", assumeNode(result.gameBoostLabel), "styleSprite", styleSprite(style),
            "tabDps", os == null ? 0 : os.dps + result.specDpsAdded,
            "bisTabDps", result.overallBest == null ? 0 : result.overallBest.dps + result.gameSpecDpsAdded);
    }

    /** A slot ladder for the card (ISS-01M5GE9EJSX6YWCKMSYEWKZ132): each rung
     * with its change in the mode's own terms and a signed gain (positive =
     * better under the mode) that sizes the strip's bars; base = the worn
     * item's own value in the same terms. */
    public static Map ladderNode(Ladder ladder) { return ladderNode(ladder, MOB.get()); }

    /** The ladder for mob `mob` of a roster (null = a solo card): a group
     * ladder shows that mob's own base and changes and names the group
     * figure beside them (Andrew 2026-10-24: "3.97 but in the card it is
     * showing 5.54 which is a confusing mismatch"). */
    public static Map ladderNode(Ladder ladder, Integer mob) {
        boolean own = ladder.basePerMob != null && mob != null && mob < ladder.basePerMob.length;
        double base = own ? ladder.basePerMob[mob] : ladder.baseDps;
        Map node = map("base", ladder.mode == OptimizeMode.TANKY
            ? format("%.2f taken/s", ladder.baseDtps) : format("%.2f DPS", base));
        if (own) node.put("groupBase", format("group %.2f DPS", ladder.baseDps));
        // Balanced weighs both, so it shows both (Andrew 2026-10-22: "show
        // in the ladder view both the dps gain and the dtps change").
        if (ladder.mode == OptimizeMode.BALANCED) node.put("base2", format("%.2f taken/s", ladder.baseDtps));
        Integer at = own ? mob : null;
        node.putAll(map("group", ladder.group, "up", rungs(ladder, ladder.up, at), "down", rungs(ladder, ladder.down, at)));
        // The steps past the strip, for its "more" pages (ISS-01M5QD7PNMCMNRZD92RPRMHWRP).
        if (!ladder.upMore.isEmpty()) node.put("upMore", rungs(ladder, ladder.upMore, at));
        if (!ladder.downMore.isEmpty()) node.put("downMore", rungs(ladder, ladder.downMore, at));
        if (ladder.all != null) {
            // Every option the strip could show, best first (the "All weapons" list).
            List<Object> all = new ArrayList<>();
            for (Rung rung : ladder.all) {
                double dps = at != null && rung.perMob != null ? rung.perMob[at] : rung.dps;
                all.add(map("id", rung.item.id, "name", rung.item.name, "owned", rung.owned,
                    "dps", dps, "delta", format("%+.2f DPS", dps - base)));
            }
            node.put("all", all);
        }
        if (ladder.powered != null || ladder.autocast != null) {
            // The magic weapon slot's two strips (ISS-01M5PKSQBAP823XNC13QBW8830):
            // powered staves and autocast staves, each a ladder of its own.
            Map kinds = new LinkedHashMap<>();
            if (ladder.powered != null) kinds.put("powered", ladderNode(ladder.powered, mob));
            if (ladder.autocast != null) kinds.put("autocast", ladderNode(ladder.autocast, mob));
            node.put("kinds", kinds);
        }
        return node;
    }

    /** The rung's improvement over the worn item under the ladder's mode. */
    static double ladderGain(Ladder ladder, Rung rung) {
        switch (ladder.mode) {
            case TANKY:
                return ladder.baseDtps - rung.dtps;
            case BALANCED:
                double base = ladder.baseDps <= 0 ? 0 : ladder.baseDtps / ladder.baseDps;
                double ratio = rung.dps <= 0 ? base : rung.dtps / rung.dps;
                return base - ratio;
            default:
                return rung.dps - ladder.baseDps;
        }
    }

    static List<Object> rungs(Ladder ladder, List<Rung> rungs, Integer mob) {
        List<Object> out = new ArrayList<>();
        for (Rung rung : rungs) {
            // A group step reads this mob's own change; the group's ranks it.
            boolean own = mob != null && rung.perMob != null && mob < rung.perMob.length;
            double dps = own ? rung.perMob[mob] : rung.dps;
            double base = own ? ladder.basePerMob[mob] : ladder.baseDps;
            Map node = map("id", rung.item.id, "name", rung.item.name,
                "owned", rung.owned, "price", rung.item.getPriceOrZero(),
                "delta", own ? format("%+.2f DPS", dps - base) : ladderDelta(ladder, rung));
            if (own) node.put("groupDelta", format("group %+.2f DPS", rung.dps - ladder.baseDps));
            if (ladder.mode == OptimizeMode.BALANCED) {
                node.put("delta2", format("%+.2f taken/s", rung.dtps - ladder.baseDtps));
            }
            node.put("gain", ladderGain(ladder, rung));
            // Same result as the worn item - ranked by its better stats.
            node.put("same", Math.abs(dps - base) <= 1e-9 && Math.abs(rung.dtps - ladder.baseDtps) <= 1e-9);
            if (rung.with != null || rung.offhand != null) {
                node.put("with", rung.with == null ? rung.offhand.name : rung.offhand == null ? rung.with.name
                    : rung.offhand.name + " and " + rung.with.name);
            }
            out.add(node);
        }
        return out;
    }

    /** "+0.12 DPS" (Max DPS and Balanced, whose damage taken rides as
     * delta2), "-0.30 taken/s" (Tanky). */
    static String ladderDelta(Ladder ladder, Rung rung) {
        switch (ladder.mode) {
            case TANKY:
                return format("%+.2f taken/s", rung.dtps - ladder.baseDtps);
            default:
                return format("%+.2f DPS", rung.dps - ladder.baseDps);
        }
    }

    /** One side's card: the shown set and everything rendered around it. */
    static Map card(StyleResult result, boolean bis, int rks, IntPredicate owned, Set<Integer> simmed) {
        DpsResult shown = shown(result, bis);
        if (shown == null) return null;
        Map card = map("dps", shown.dps, "maxHit", shown.maxHit, "accuracy", shown.accuracy);
        // The blowpipe's loaded dart rides the attackType STRING
        // ("ranged rapid - dragon dart") - split it into facts: a clean
        // style line + an internalAmmo entry for the column.
        String attackType = shown.attackType;
        if (attackType != null && attackType.contains(" - ")) {
            int dash = attackType.indexOf(" - ");
            String dt = attackType.substring(dash + 3);
            Integer di = BlowpipeDarts.baseIdForTierName(dt);
            if (di != null) {
                card.put("internalAmmo", map("id", di, "name", Names.cap(dt) + " (loaded)"));
                attackType = attackType.substring(0, dash);
            }
        }
        card.putAll(map("attackType", attackType, "spell", shown.getSpellName(),
            "purchaseCost", shown.purchaseCost, "antifireAssumed", shown.antifireAssumed,
            "counted", new ArrayList<>(shown.countedBonuses)));
        Map gear = new LinkedHashMap<>();
        DpsResult bs = result.overallBest;
        for (GearSlot slot : GearSlot.values()) {
            GearItem item = shown.loadout.get(slot);
            if (item != null) {
                Map node = item(item);
                if (owned != null) {
                    // The classic border language: gold = owned AND best-
                    // available (exact or stat-equivalent analog); green =
                    // gear the answer ASSUMES (simmed, or unowned on the
                    // Yours side); everything else stays quiet grey.
                    boolean owns = owned.test(item.id);
                    boolean isSimmed = !owns && simmed.contains(item.id);
                    GearItem bi = bis || bs == null ? null : bs.loadout.get(slot);
                    node.putAll(map("owned", owns, "assumed", isSimmed || (!bis && !owns),
                        "bisMatch", owns && bi != null && !result.beaten.contains(slot)
                            && (bi.id == item.id || statEquivalent(bi, item))));
                }
                IntFunction<String> lo = LOCATION.get();
                if (lo != null) {
                    // Only FETCH-TRIP storages carry a dot (the classic
                    // rule: at-hand gear stays unmarked).
                    String where = lo.apply(item.id);
                    if (where != null && !where.isEmpty()) node.put("source", where);
                }
                Ladder ladder = bis ? null : result.ladder.get(slot);
                if (ladder != null) node.put("ladder", ladderNode(ladder));
                gear.put(slot.name().toLowerCase(), node);
            }
        }
        card.putAll(map("gear", gear, "quiverAmmo", item(shown.loadout.quiverAmmo),
            "spec", spec(result, bis), "incoming", incoming(bis ? result.gameIncoming : result.incoming)));
        // The defensive mode's trade belongs to the Yours side only (BiS
        // stays the pure-DPS ceiling).
        ModeTrade trade = bis ? null : result.modeTrade;
        if (trade != null) {
            card.put("modeTrade", map("mode", trade.mode.name(), "dpsLossPct", trade.dpsLossPct,
                "dmgCutPct", trade.dmgCutPct, "reason", trade.reason));
        }
        // Carried GWD protection: which gods each bench item soothes, for
        // the Inventory row's "wear through the dungeon" tooltip.
        Map protection = new LinkedHashMap<>();
        for (GearItem item : bis ? result.gameProtection : result.protection) {
            protection.put(String.valueOf(item.id), join(", ", GwdProtection.godsOf(item)));
        }
        GearItem rw = shown.loadout.getWeapon();
        GearItem rs = shown.loadout.get(GearSlot.SHIELD);
        StatBlock bonuses = shown.loadout.bonuses;
        card.putAll(map("bench", items(bis ? result.gameBench : result.bench), "protection", protection,
            "runes", SpellRunes.costFor(shown.getSpellName(), rw == null ? null : rw.nameLower,
                rs == null ? null : rs.nameLower), "kitBacked", bis ? result.gameKitBacked : result.ownedKitBacked,
            "stats", map("offensive", statBlock(shown.loadout.offensive),
                "defensive", statBlock(shown.loadout.defensive),
                "strength", bonuses.strength, "rangedStrength", bonuses.rangedStrength,
                "magicDamage", bonuses.magicDamage, "prayer", bonuses.prayer)));
        if (rks >= 0) {
            Assessment risk = PvpRisk.assess( shown.loadout, bis ? result.gameSpecWeapon : result.specWeapon, rks);
            card.put("risk", map("riskGp", risk.riskGp, "kept", labels(risk.kept), "lost", labels(risk.lost)));
            // Per-cell fate (the classic risk dots): kept / dropped.
            for (Object sn : gear.values()) {
                if (!(sn instanceof Map)) continue;
                Map node = (Map) sn;
                int id = ((Number) node.get("id")).intValue();
                for (GearItem k : risk.kept) {
                    if (k.id == id) node.put("fate", "kept");
                }
                for (GearItem l : risk.lost) {
                    if (l.id == id) {
                        node.put("fate", "lost");
                        node.put("fateGp", risk.valueOf(l));
                    }
                }
            }
        }
        return card;
    }

    /** The items' labels, for the risk node's kept/lost lists. */
    static List<Object> labels(List<GearItem> items) {
        List<Object> out = new ArrayList<>();
        for (GearItem item : items) out.add(item.label());
        return out;
    }

    /** Analogs count as best-available (any god's d'hide coif). */
    static boolean statEquivalent(GearItem a, GearItem b) {
        return a.slot == b.slot && a.speed == b.speed && a.twoHanded == b.twoHanded
            && a.category.equals(b.category) && sameBlock(a.offensive, b.offensive)
            && sameBlock(a.defensive, b.defensive) && sameBlock(a.bonuses, b.bonuses);
    }

    static boolean sameBlock(StatBlock a, StatBlock b) { return Arrays.equals(values(a), values(b)); }

    /** Every stat of the block, in one array to compare. */
    static int[] values(StatBlock b) {
        return new int[]{b.stab, b.slash, b.crush, b.magic, b.ranged,
            b.strength, b.rangedStrength, b.magicDamage, b.prayer};
    }

    /** Facts for the header assume icons: the label "Piety + Divine
     * super combat" resolves to a prayer sprite id and a boost potion
     * item id (renderers draw, never map). */
    static Map assumeNode(String boostLabel) {
        if (boostLabel == null || boostLabel.isEmpty()) {
            // "None + none" must still be a node: the assume icons ARE the
            // pickers, and a null here erased both - picking None removed
            // the only control that could undo it (field report 2026-08-31,
            // Not on Hand). The sentinel sprites render from -1: the prayer
            // cell falls back to the prayer-book wings, the boost cell to
            // the muted dash, and both stay clickable.
            return map("text", "No prayer or boost", "prayerSprite", -1, "boostItem", -1, "boostSupplied", false);
        }
        int plus = boostLabel.indexOf(" + ");
        String pp = plus > 0 ? boostLabel.substring(0, plus) : boostLabel;
        String bp = plus > 0 ? boostLabel.substring(plus + 3) : null;
        // boostSupplied: the raid hands these out (CoX overloads, ToA salts) -
        // assumed, never packed (Andrew 2026-09-02: potions are supplies to bring).
        return map("text", boostLabel, "prayerSprite", prayerSprite(pp), "prayerImage", prayerImage(pp),
            "boostItem", bp == null ? -1 : boostItem(bp), "boostSupplied", bp != null
                && (bp.equals(BoostProfile.OVERLOAD.toString()) || bp.equals(BoostProfile.OVERLOAD_PLUS.toString())
                || bp.equals(BoostProfile.SMELLING_SALTS.toString())));
    }

    static Map statBlock(StatBlock block) {
        return map("stab", block.stab, "slash", block.slash, "crush", block.crush,
            "magic", block.magic, "ranged", block.ranged);
    }

    static Map spec(StyleResult result, boolean bis) {
        GearItem weapon = bis ? result.gameSpecWeapon : result.specWeapon;
        if (weapon == null) return null;
        Map wn = item(weapon);
        // The spec cell's swaps strip (ISS-01M5HCS725N7XVVBC7CMK6Y43A).
        if (!bis && result.specLadder != null && wn != null) {
            wn.put("ladder", ladderNode(result.specLadder));
        }
        return map("weapon", wn, "dpsAdded", bis ? result.gameSpecDpsAdded : result.specDpsAdded,
            "expectedDamage", bis ? result.gameSpecExpectedDamage : result.specExpectedDamage);
    }

    static Map incoming(Result incoming) {
        if (incoming == null) return null;
        List<Object> threats = new ArrayList<>();
        for (StyleThreat threat : incoming.threats) {
            threats.add(map("style", threat.style, "dps", threat.dps, "maxHit", threat.maxHit,
                "modeled", threat.modeled, "blocked", threat.blocked, "prayerFactor", threat.prayerFactor));
        }
        return map("dps", incoming.totalDps, "unprayedDps", incoming.unprayedDps,
            "protectSprite", incoming.protectPrayer == null ? -1 : prayerSprite(String.valueOf(incoming.protectPrayer)),
            "protectPrayer", incoming.protectPrayer, "fullyModeled", incoming.fullyModeled,
            "coverage", incoming.coverage, "unmodeled", new ArrayList<>(incoming.unmodeled),
            "overrideNote", incoming.overrideNote, "threats", threats);
    }

    static List<Object> items(List<GearItem> items) {
        List<Object> nodes = new ArrayList<>();
        if (items != null) {
            for (GearItem item : items) nodes.add(item(item));
        }
        return nodes;
    }

    static Map item(GearItem item) {
        if (item == null) return null;
        Map node = map("id", item.id, "name", item.label());
        if (Degradable.matches(item.nameLower)) node.put("degrades", true);
        node.put("price", item.getPriceOrZero());
        return node;
    }
}
