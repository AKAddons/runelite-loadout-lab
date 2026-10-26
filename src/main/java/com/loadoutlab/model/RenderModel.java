package com.loadoutlab.model;

import com.loadoutlab.data.*;
import com.loadoutlab.engine.*;
import com.loadoutlab.optimizer.OptimizerService;
import java.util.*;
import java.util.function.*;
import com.loadoutlab.engine.IncomingDpsCalculator.*;
import com.loadoutlab.optimizer.OptimizerService.*;
import com.loadoutlab.engine.PvpRisk.Assessment;
import static com.loadoutlab.data.AssumeIcons.*;

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
public final class RenderModel {
    /** Contract version stamped on every page; additive evolution only. */
    public static final int VERSION = 1;

    private RenderModel() {
    }

    /** A node from key/value pairs, in argument order (the JSON stays
     * canonical: every builder below lists its keys in the contract's order). */
    static Map<String, Object> map(Object... kv) { return JsonResources.map(kv); }

    /** The side a card shows: the game-wide best, or the first owned set
     * (null for none, or for no result). */
    static DpsResult shown(StyleResult result, boolean bis) {
        return result == null ? null : bis ? result.overallBest
            : result.owned == null || result.owned.isEmpty() ? null : result.owned.get(0);
    }

    public static Map<String, Object> page(List<Map<String, Object>> entries) {
        return map("v", VERSION, "entries", entries);
    }

    /** One result entry: its mobs and, per mob, the per-style cards.
     * Single-mob pages are the one-mob case of the same shape. */
    public static Map<String, Object> entry(List<MonsterStats> mobs, List<Map<CombatStyle, StyleResult>> perMob) {
        return entry(mobs, perMob, -1, null, Set.of(), null);
    }

    /** Per-build provenance lookup: an item's storage NAME when a fetch
     * trip is needed (the classic source dots), "" when at hand.
     * riskKeptSlots >= 0 adds a wilderness risk node per card; ownership +
     * simmed ids flag every gear cell for the classic border language
     * (gold owned-BiS / green assumed / grey). */
    private static final ThreadLocal<IntFunction<String>> LOCATION = new ThreadLocal<>();

    public static Map<String, Object> entry(List<MonsterStats> mobs,
        List<Map<CombatStyle, StyleResult>> perMob, int rks, IntPredicate owned, Set<Integer> simmed,
        IntFunction<String> lo) {
        LOCATION.set(lo);
        try {
            return buildEntry(mobs, perMob, rks, owned, simmed);
        } finally {
            LOCATION.remove();
        }
    }

    private static Map<String, Object> buildEntry(List<MonsterStats> mobs,
        List<Map<CombatStyle, StyleResult>> perMob, int rks, IntPredicate owned, Set<Integer> simmed) {
        List<Object> mn = new ArrayList<>();
        for (int i = 0; i < mobs.size(); i++) {
            Map<String, Object> node = mob(mobs.get(i));
            MOB.set(i);
            try {
                node.put("styles", styles(perMob.get(i), rks, owned, simmed));
                Map<String, Object> multi = multi(perMob.get(i), rks, owned, simmed);
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
    private static final ThreadLocal<Integer> MOB = new ThreadLocal<>();

    static Map<String, Object> mob(MonsterStats mob) {
        return map("id", mob.getId(), "profileId", mob.profileId(), "name", mob.getName(),
            "version", mob.getVersion(), "label", mob.label(), "hp", mob.getHitpoints(),
            "level", mob.getCombatLevel(), "invocationScaled", MonsterMechanics.isToaInvocationScaled(mob),
            "breathesFire", DragonfireRules.breathesFire(mob), "wilderness", WildernessMonsters.isWilderness(mob),
            "naval", NavalCombat.isNaval(mob.getName()), "raid", RaidBoosts.raidKey(mob),
            "taskOnly", SlayerLockedMonsters.isTaskOnly(mob), "fightBook", MonsterSpellbooks.bookFor(mob),
            "fightBookReason", MonsterSpellbooks.reasonFor(mob), "slayerMonster", mob.isSlayerMonster(),
            "multiAble", MultiMobs.isMultiAble(mob), "multiLocations", MultiMobs.locations(mob),
            "wildernessExclusive", WildernessMonsters.isExclusive(mob), "gwd", GwdProtection.isGwd(mob));
    }

    private static Map<String, Object> styles(Map<CombatStyle, StyleResult> results,
        int rks, IntPredicate owned, Set<Integer> simmed) {
        Map<String, Object> styles = new LinkedHashMap<>();
        for (CombatStyle style : CombatStyle.concreteValues()) {
            StyleResult result = results == null ? null : results.get(style);
            if (result == null) continue;
            styles.put(style.name().toLowerCase(), styleNode(style, result, rks, owned, simmed));
        }
        return styles;
    }

    /** The Multi card rows as style nodes keyed by tool, unscaled (the
     * engine's decoration applies the stack); null without the switch. */
    private static Map<String, Object> multi(Map<CombatStyle, StyleResult> results,
        int rks, IntPredicate owned, Set<Integer> simmed) {
        Map<String, StyleResult> tools = null;
        for (StyleResult r : results == null ? List.<StyleResult>of() : results.values()) {
            if (r != null && r.multiTools != null) tools = r.multiTools;
        }
        if (tools == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        for (MultiTool tool : MultiTool.values()) {
            StyleResult r = tools.get(tool.key);
            if (r != null) {
                Map<String, Object> node = styleNode(tool.style, r, rks, owned, simmed);
                node.put("multiKind", tool.kind);
                // The two ranged tools wear their own item icon on the strip
                // (Andrew 2026-10-19); the barrage keeps the magic sprite.
                node.put("styleItem", tool == MultiTool.CHINS ? MultiTool.CHIN_IDS[0] : tool == MultiTool.VENATOR ? MultiTool.VENATOR_BOW
                    : tool == MultiTool.HALLOWFELL ? MultiTool.HALLOWFELL_SWORD : 0);
                out.put(tool.key, node);
            }
        }
        return out;
    }

    private static Map<String, Object> styleNode(CombatStyle style, StyleResult result,
        int rks, IntPredicate owned, Set<Integer> simmed) {
        DpsResult os = shown(result, false);
        // Tab facts after the sides: the sprite and each side's SHOWN dps
        // (set + spec) so a renderer can label the strip without re-deriving.
        return map("yours", card(result, false, rks, owned, simmed), "bis", card(result, true, rks, owned, simmed),
            "boostLabel", result.boostLabel, "bisBoostLabel", result.gameBoostLabel,
            "assume", assumeNode(result.boostLabel), "bisAssume", assumeNode(result.gameBoostLabel), "styleSprite", styleSprite(style),
            "tabDps", os == null ? 0 : os.getDps() + result.specDpsAdded,
            "bisTabDps", result.overallBest == null ? 0 : result.overallBest.getDps() + result.gameSpecDpsAdded);
    }

    /** A slot ladder for the card (ISS-01M5GE9EJSX6YWCKMSYEWKZ132): each rung
     * with its change in the mode's own terms and a signed gain (positive =
     * better under the mode) that sizes the strip's bars; base = the worn
     * item's own value in the same terms. */
    public static Map<String, Object> ladderNode(Ladder ladder) { return ladderNode(ladder, MOB.get()); }

    /** The ladder for mob `mob` of a roster (null = a solo card): a group
     * ladder shows that mob's own base and changes and names the group
     * figure beside them (Andrew 2026-10-24: "3.97 but in the card it is
     * showing 5.54 which is a confusing mismatch"). */
    public static Map<String, Object> ladderNode(Ladder ladder, Integer mob) {
        boolean own = ladder.basePerMob != null && mob != null && mob < ladder.basePerMob.length;
        double base = own ? ladder.basePerMob[mob] : ladder.baseDps;
        Map<String, Object> node = map("base", ladder.mode == OptimizeMode.TANKY
            ? String.format("%.2f taken/s", ladder.baseDtps) : String.format("%.2f DPS", base));
        if (own) node.put("groupBase", String.format("group %.2f DPS", ladder.baseDps));
        // Balanced weighs both, so it shows both (Andrew 2026-10-22: "show
        // in the ladder view both the dps gain and the dtps change").
        if (ladder.mode == OptimizeMode.BALANCED) node.put("base2", String.format("%.2f taken/s", ladder.baseDtps));
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
                all.add(map("id", rung.item.getId(), "name", rung.item.getName(), "owned", rung.owned,
                    "dps", dps, "delta", String.format("%+.2f DPS", dps - base)));
            }
            node.put("all", all);
        }
        if (ladder.powered != null || ladder.autocast != null) {
            // The magic weapon slot's two strips (ISS-01M5PKSQBAP823XNC13QBW8830):
            // powered staves and autocast staves, each a ladder of its own.
            Map<String, Object> kinds = new LinkedHashMap<>();
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

    private static List<Object> rungs(Ladder ladder, List<Rung> rungs, Integer mob) {
        List<Object> out = new ArrayList<>();
        for (Rung rung : rungs) {
            // A group step reads this mob's own change; the group's ranks it.
            boolean own = mob != null && rung.perMob != null && mob < rung.perMob.length;
            double dps = own ? rung.perMob[mob] : rung.dps;
            double base = own ? ladder.basePerMob[mob] : ladder.baseDps;
            Map<String, Object> node = map("id", rung.item.getId(), "name", rung.item.getName(),
                "owned", rung.owned, "price", rung.item.getPriceOrZero(),
                "delta", own ? String.format("%+.2f DPS", dps - base) : ladderDelta(ladder, rung));
            if (own) node.put("groupDelta", String.format("group %+.2f DPS", rung.dps - ladder.baseDps));
            if (ladder.mode == OptimizeMode.BALANCED) {
                node.put("delta2", String.format("%+.2f taken/s", rung.dtps - ladder.baseDtps));
            }
            node.put("gain", ladderGain(ladder, rung));
            // Same result as the worn item - ranked by its better stats.
            node.put("same", Math.abs(dps - base) <= 1e-9 && Math.abs(rung.dtps - ladder.baseDtps) <= 1e-9);
            if (rung.with != null || rung.offhand != null) {
                node.put("with", rung.with == null ? rung.offhand.getName() : rung.offhand == null ? rung.with.getName()
                    : rung.offhand.getName() + " and " + rung.with.getName());
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
                return String.format("%+.2f taken/s", rung.dtps - ladder.baseDtps);
            default:
                return String.format("%+.2f DPS", rung.dps - ladder.baseDps);
        }
    }

    /** One side's card: the shown set and everything rendered around it. */
    static Map<String, Object> card(StyleResult result, boolean bis, int rks, IntPredicate owned, Set<Integer> simmed) {
        DpsResult shown = shown(result, bis);
        if (shown == null) return null;
        Map<String, Object> card = map("dps", shown.getDps(), "maxHit", shown.getMaxHit(), "accuracy", shown.getAccuracy());
        // The blowpipe's loaded dart rides the attackType STRING
        // ("ranged rapid - dragon dart") - split it into facts: a clean
        // style line + an internalAmmo entry for the column.
        String attackType = shown.getAttackType();
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
            "purchaseCost", shown.getPurchaseCost(), "antifireAssumed", shown.isAntifireAssumed(),
            "counted", new ArrayList<>(shown.getCountedBonuses())));
        Map<String, Object> gear = new LinkedHashMap<>();
        DpsResult bs = result.overallBest;
        for (GearSlot slot : GearSlot.values()) {
            GearItem item = shown.getLoadout().get(slot);
            if (item != null) {
                Map<String, Object> node = item(item);
                if (owned != null) {
                    // The classic border language: gold = owned AND best-
                    // available (exact or stat-equivalent analog); green =
                    // gear the answer ASSUMES (simmed, or unowned on the
                    // Yours side); everything else stays quiet grey.
                    boolean owns = owned.test(item.getId());
                    boolean isSimmed = !owns && simmed.contains(item.getId());
                    GearItem bi = bis || bs == null ? null : bs.getLoadout().get(slot);
                    node.putAll(map("owned", owns, "assumed", isSimmed || (!bis && !owns),
                        "bisMatch", owns && bi != null && !result.beaten.contains(slot)
                            && (bi.getId() == item.getId() || statEquivalent(bi, item))));
                }
                IntFunction<String> lo = LOCATION.get();
                if (lo != null) {
                    // Only FETCH-TRIP storages carry a dot (the classic
                    // rule: at-hand gear stays unmarked).
                    String where = lo.apply(item.getId());
                    if (where != null && !where.isEmpty()) node.put("source", where);
                }
                Ladder ladder = bis ? null : result.ladder.get(slot);
                if (ladder != null) node.put("ladder", ladderNode(ladder));
                gear.put(slot.name().toLowerCase(), node);
            }
        }
        card.putAll(map("gear", gear, "quiverAmmo", item(shown.getLoadout().getQuiverAmmo()),
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
        Map<String, Object> protection = new LinkedHashMap<>();
        for (GearItem item : bis ? result.gameProtection : result.protection) {
            protection.put(String.valueOf(item.getId()), String.join(", ", GwdProtection.godsOf(item)));
        }
        GearItem rw = shown.getLoadout().getWeapon();
        GearItem rs = shown.getLoadout().get(GearSlot.SHIELD);
        StatBlock bonuses = shown.getLoadout().getBonuses();
        card.putAll(map("bench", items(bis ? result.gameBench : result.bench), "protection", protection,
            "runes", SpellRunes.costFor(shown.getSpellName(), rw == null ? null : rw.getNameLower(),
                rs == null ? null : rs.getNameLower()), "kitBacked", bis ? result.gameKitBacked : result.ownedKitBacked,
            "stats", map("offensive", statBlock(shown.getLoadout().getOffensive()),
                "defensive", statBlock(shown.getLoadout().getDefensive()),
                "strength", bonuses.getStrength(), "rangedStrength", bonuses.getRangedStrength(),
                "magicDamage", bonuses.getMagicDamage(), "prayer", bonuses.getPrayer())));
        if (rks >= 0) {
            Assessment risk = PvpRisk.assess( shown.getLoadout(), bis ? result.gameSpecWeapon : result.specWeapon, rks);
            card.put("risk", map("riskGp", risk.riskGp, "kept", labels(risk.kept), "lost", labels(risk.lost)));
            // Per-cell fate (the classic risk dots): kept / dropped.
            for (Object sn : gear.values()) {
                if (!(sn instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> node = (Map<String, Object>) sn;
                int id = ((Number) node.get("id")).intValue();
                for (GearItem k : risk.kept) {
                    if (k.getId() == id) node.put("fate", "kept");
                }
                for (GearItem l : risk.lost) {
                    if (l.getId() == id) {
                        node.put("fate", "lost");
                        node.put("fateGp", risk.valueOf(l));
                    }
                }
            }
        }
        return card;
    }

    /** The items' labels, for the risk node's kept/lost lists. */
    private static List<Object> labels(List<GearItem> items) {
        List<Object> out = new ArrayList<>();
        for (GearItem item : items) out.add(item.label());
        return out;
    }

    /** Analogs count as best-available (any god's d'hide coif). */
    private static boolean statEquivalent(GearItem a, GearItem b) {
        return a.getSlot() == b.getSlot() && a.getSpeed() == b.getSpeed() && a.isTwoHanded() == b.isTwoHanded()
            && a.getCategory().equals(b.getCategory()) && sameBlock(a.getOffensive(), b.getOffensive())
            && sameBlock(a.getDefensive(), b.getDefensive()) && sameBlock(a.getBonuses(), b.getBonuses());
    }

    private static boolean sameBlock(StatBlock a, StatBlock b) { return Arrays.equals(values(a), values(b)); }

    /** Every stat of the block, in one array to compare. */
    private static int[] values(StatBlock b) {
        return new int[]{b.getStab(), b.getSlash(), b.getCrush(), b.getMagic(), b.getRanged(),
            b.getStrength(), b.getRangedStrength(), b.getMagicDamage(), b.getPrayer()};
    }

    /** Facts for the header assume icons: the label "Piety + Divine
     * super combat" resolves to a prayer sprite id and a boost potion
     * item id (renderers draw, never map). */
    static Map<String, Object> assumeNode(String boostLabel) {
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

    private static Map<String, Object> statBlock(StatBlock block) {
        return map("stab", block.getStab(), "slash", block.getSlash(), "crush", block.getCrush(),
            "magic", block.getMagic(), "ranged", block.getRanged());
    }

    private static Map<String, Object> spec(StyleResult result, boolean bis) {
        GearItem weapon = bis ? result.gameSpecWeapon : result.specWeapon;
        if (weapon == null) return null;
        Map<String, Object> wn = item(weapon);
        // The spec cell's swaps strip (ISS-01M5HCS725N7XVVBC7CMK6Y43A).
        if (!bis && result.specLadder != null && wn != null) {
            wn.put("ladder", ladderNode(result.specLadder));
        }
        return map("weapon", wn, "dpsAdded", bis ? result.gameSpecDpsAdded : result.specDpsAdded,
            "expectedDamage", bis ? result.gameSpecExpectedDamage : result.specExpectedDamage);
    }

    private static Map<String, Object> incoming(Result incoming) {
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

    private static List<Object> items(List<GearItem> items) {
        List<Object> nodes = new ArrayList<>();
        if (items != null) {
            for (GearItem item : items) nodes.add(item(item));
        }
        return nodes;
    }

    private static Map<String, Object> item(GearItem item) {
        if (item == null) return null;
        Map<String, Object> node = map("id", item.getId(), "name", item.label());
        if (Degradable.matches(item.getNameLower())) node.put("degrades", true);
        node.put("price", item.getPriceOrZero());
        return node;
    }
}
