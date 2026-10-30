package com.loadoutlab.engine;

import lombok.*;
import com.loadoutlab.data.*;
import java.util.*;
import com.loadoutlab.engine.BossIncomingOverrides.*;
import com.loadoutlab.data.MaxHits.Family;
import static com.loadoutlab.data.MaxHits.Family.*;

/**
 * The other direction: how hard the monster hits YOU in a given set.
 * Standard NPC combat math (effective level +9, roll = eff * (bonus+64),
 * max hit = (effStr * (bonus+64) + 320) / 640) against the loadout's
 * defensive bonuses and the player's real Defence/Magic levels.
 *
 * v1 model and its stated assumptions:
 * - The player runs the protection prayer against the nastiest modeled
 *   style, which blocks that style completely (true for standard NPCs;
 *   partial-block bosses need per-boss overrides later).
 * - Multi-style monsters rotate uniformly across their listed styles, so
 *   each style owns an equal share of the attack cadence and the blocked
 *   style's share is dead time. Real rotations (Zulrah phases, Vorkath
 *   specials) are a curated-override layer we build up over time.
 * - The style's prayer raises Defence and Magic beside the protection one
 *   (Piety +25%: PrayerBonuses.defending); no defensive stance or boost.
 * - Styles the sheet cannot express (Typeless, Dragonfire, Curse...) are
 *   surfaced as unmodeled rather than silently dropped.
 *
 * D-2: when BossIncomingOverrides has a curated entry for the monster, its
 * attack list replaces the uniform model - scripted max hits, real rotation
 * shares, prayer-pierce flags, and typeless chip damage. Accuracy is still
 * rolled from the stat sheet's offensive stats vs the loadout; an override
 * maxHit only replaces the damage term (typeless attacks always hit).
 */
public class IncomingDpsCalculator {
    /** One attack style's threat: its dps if it attacked every cycle. */
    @AllArgsConstructor
    public static class StyleThreat {
        public final String style;
        public final double dps;
        public final int maxHit;
        public final boolean modeled;
        public final boolean blocked;
        /** This attack's slice of the rotation (1/n in the uniform model). */
        public final double share;
        /** When blocked: the fraction that still gets THROUGH the prayer
         * (0 = fully blocked, 0.5 = half pierces). 1 when not blocked. */
        public final double prayerFactor;
    }

    public static class Result {
        /** Expected incoming dps with the protection prayer up. */
        public final double totalDps;
        /** Expected incoming dps with NO protection prayer running. */
        public final double unprayedDps;
        /** What the boss heals per second off this set (0 where nothing heals). */
        public final double healDps;
        /** The prayer to run, e.g. "Protect from Missiles", or null. */
        public final String protectPrayer;
        public final List<StyleThreat> threats;
        /** False when any listed style is beyond the stat-sheet model. */
        public final boolean fullyModeled;
        /** The curated override's source note, or null in the v1 model. */
        public final String overrideNote;
        /** How much of the monster the number covers: curated, full,
         * partial (some attacks not counted) or none (unknown). */
        public final String coverage;
        /** The attacks the number leaves out, for the card's tooltip. */
        public final List<String> unmodeled;

        Result(double totalDps, double unprayedDps, String protectPrayer,
            List<StyleThreat> threats, String overrideNote, String coverage, List<String> unmodeled) {
            this(totalDps, unprayedDps, 0, protectPrayer, threats, overrideNote, coverage, unmodeled);
        }

        Result(double totalDps, double unprayedDps, double healDps, String protectPrayer,
            List<StyleThreat> threats, String overrideNote, String coverage, List<String> unmodeled) {
            this.totalDps = totalDps;
            this.unprayedDps = unprayedDps;
            this.healDps = healDps;
            this.protectPrayer = protectPrayer;
            this.threats = threats;
            fullyModeled = unmodeled.isEmpty() && !NONE.equals(coverage);
            this.overrideNote = overrideNote;
            this.coverage = coverage;
            this.unmodeled = unmodeled;
        }
    }

    public static final String CURATED = "curated";
    public static final String FULL = "full";
    public static final String PARTIAL = "partial";
    public static final String NONE = "none";


    // Style kinds for the precomputed accuracy path (Prepared).
    static final int KIND_STAB = 0;
    static final int KIND_SLASH = 1;
    static final int KIND_CRUSH = 2;
    static final int KIND_MELEE_GENERIC = 3;
    static final int KIND_RANGED = 4;
    static final int KIND_MAGIC = 5;
    static final int KIND_TYPELESS = 6;
    static final int KIND_UNMODELED = -1;
    /** "None" / "N/A": the monster does not attack this way at all. */
    static final int KIND_NONE = -2;

    /**
     * Everything about the incoming model that does NOT depend on the worn
     * set, hoisted once per optimize: override lookup, style parsing, npc
     * max hits and attack rolls, rotation shares, prayer names. The
     * defense-weighted beam then scores each candidate loadout with
     * totalDps() - the same number calculate() reports (asserted in
     * IncomingDpsCalculatorTest), without the per-call threat/Result
     * allocations; calculate() itself is result() on the same machinery.
     */
    public static class Prepared {
        final int defenceLevel;
        /** Player magic defence level term: floor(70% Magic) + floor(30% Defence) + 8. */
        final int magicEffective;
        /** Per style/attack: KIND_*, npc attack roll, (v1) max hit, and
         * the speed its rate uses (the sheet's for every sheet style). */
        final int[] kinds;
        final int[] attackRolls;
        final int[] maxHits;
        final int[] speeds;
        /** Priced: it has a rate the card can show. Live: counted in the
         * totals. A sheet style is both when modeled at a known speed; an
         * override attack is priced at a known speed and live when the
         * standard play does not dodge it. */
        final boolean[] priced;
        final boolean[] live;
        final boolean[] prayable;
        /** The card's name per style/attack, and the prayer answering it. */
        final String[] labels;
        final String[] prayerOf;
        // v1 sheet model (override == null): modeled styles share the cadence.
        final double share;
        // Curated override model: per-attack constants.
        final boolean overridden;
        final String note;
        final double[] shares;
        final double[] prayerFactors;
        final boolean[] avoidable;
        /** Hits per attack; prayed on cue by the standard play; and how
         * much of such an attack still lands (ISS-01M5PBSZBBBAS9Y46X04MVMHZB). */
        final int[] hits;
        final boolean[] flicked;
        final double[] leaks;
        /** Lands only if the previous attack landed (a stack); and the
         * boss's mean heal per landed hit, 0 where it heals nothing
         * (ISS-01M6415DTE84ZZ45C4YD3MZRBJ). Null in the sheet model. */
        final boolean[] chain;
        final double[] healPerLand;
        /** Per attack: index into the distinct prayer list (first-occurrence
         * order), and that list. */
        final int[] prayerIndex;
        final String[] prayerNames;
        /** Revenants deal NO damage through a charged ethereum bracelet -
         * the beam's defense scoring must see that, or Tanky/Balanced can
         * never choose the bracelet (field report: not recommended). */
        final boolean revenant;
        /** The curated dragonfire attack's index (-1 when none), its layers,
         * and the antifire assumed (0 none, 1 antifire, 2 super). */
        final int dragonfire;
        final Map<String, int[]> layers;
        final int antifireTier;

        /** The beam's constants: the request's mob at the levels its prayer defends with. */
        static Prepared of(OptimizationRequest r) {
            int[] dm = PrayerBonuses.defending(r.style, r.prayers.nameFor(r.style), r.levels.defence, r.levels.magic);
            return new Prepared(r.monster, BossIncomingOverrides.overridesFor(r.monster), dm[0], dm[1], r.antifireTier);
        }

        Prepared(MonsterStats monster, BossOverride override, int defenceLevel, int ml, int antifireTier) {
            revenant = monster.isRevenantMonster();
            this.antifireTier = antifireTier;
            int fire = -1;
            Map<String, int[]> fl = null;
            this.defenceLevel = defenceLevel;
            magicEffective = magicEffectiveLevel(defenceLevel, ml);
            MonsterOffence off = monster.offence;
            overridden = override != null;
            int st = off.speedTicks;
            List<String> styles = off.styles;
            List<Attack> attacks = overridden ? override.attacks : null;
            int n = overridden ? attacks.size() : styles.size();
            kinds = new int[n];
            attackRolls = new int[n];
            maxHits = new int[n];
            speeds = new int[n];
            live = new boolean[n];
            labels = new String[n];
            prayerOf = new String[n];
            if (!overridden) {
                priced = live;
                prayable = new boolean[n];
                // Only modeled attacks share the cadence: an unmodeled style is
                // named on the card, never a 0-damage slice that dilutes the rest.
                int modeled = modeledCount(styles, off);
                share = modeled == 0 ? 0 : 1.0 / modeled;
                boolean single = singleFamily(styles);
                for (int i = 0; i < n; i++) {
                    int kind = kindOf(styles.get(i));
                    kinds[i] = kind;
                    // No listed speed (0 / -1) is no rate at all - never one tick.
                    live[i] = kind >= 0 && st > 0;
                    prayable[i] = prayable(styles.get(i));
                    attackRolls[i] = attackRollFor(kind, off);
                    maxHits[i] = maxHitFor(kind, off, single);
                    speeds[i] = st;
                    labels[i] = shown(styles.get(i));
                    prayerOf[i] = protectPrayerFor(styles.get(i));
                }
                note = null;
                shares = null;
                prayerFactors = null;
                avoidable = null;
                hits = null;
                flicked = null;
                leaks = null;
                prayerIndex = null;
                prayerNames = null;
                chain = null;
                healPerLand = null;
            } else {
                shares = new double[n];
                prayerFactors = new double[n];
                prayerIndex = new int[n];
                priced = new boolean[n];
                avoidable = new boolean[n];
                hits = new int[n];
                flicked = new boolean[n];
                leaks = new double[n];
                chain = new boolean[n];
                healPerLand = new double[n];
                prayable = null;
                share = 0;
                note = override.note;
                // Distinct prayers in first-occurrence order.
                List<String> prayers = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    Attack attack = attacks.get(i);
                    String style = attack.style;
                    // Curated dragonfire always lands (as the typeless rows); Protect
                    // from Magic is its prayer, priced per set by the layers.
                    boolean layered = attack.layers != null;
                    if (layered) {
                        fire = i;
                        fl = attack.layers;
                    }
                    int kind = "typeless".equals(style) || layered ? KIND_TYPELESS : kindOf(style);
                    kinds[i] = kind;
                    attackRolls[i] = kind == KIND_TYPELESS ? 0 : attackRollFor(kind, off);
                    maxHits[i] = attack.maxHit;
                    // A chained hit rides its stack's share and speed.
                    chain[i] = attack.chain && i > 0;
                    shares[i] = chain[i] ? shares[i - 1] : attack.share;
                    prayerFactors[i] = attack.prayerFactor;
                    speeds[i] = chain[i] ? speeds[i - 1] : attack.speedTicks > 0 ? attack.speedTicks : st;
                    healPerLand[i] = attack.heal == null ? 0 : meanHeal(attack.heal, attack.maxHit);
                    // Avoidable attacks are dodged by the standard play, and an
                    // unknown speed is no rate at all - the totals count neither.
                    avoidable[i] = attack.avoidable;
                    priced[i] = speeds[i] > 0;
                    live[i] = !avoidable[i] && priced[i];
                    hits[i] = attack.hits;
                    flicked[i] = attack.flicked;
                    leaks[i] = attack.leak;
                    labels[i] = attack.name.isEmpty() ? displayStyle(style) : attack.name;
                    String prayer = layered ? "Protect from Magic" : protectPrayerFor(style);
                    prayerOf[i] = prayer;
                    int index = prayers.indexOf(prayer);
                    if (index < 0) {
                        index = prayers.size();
                        prayers.add(prayer);
                    }
                    prayerIndex[i] = index;
                }
                prayerNames = prayers.toArray(new String[0]);
            }
            dragonfire = fire;
            layers = fl;
        }

        /** The dragonfire's {max without, max with Protect from Magic} for this
         * set (a protective shield worn) and the antifire assumed, or null. */
        int[] dragonfireRow(Loadout loadout) {
            if (dragonfire < 0) return null;
            boolean shield = DragonfireRules.isProtectiveShield(loadout.get(GearSlot.SHIELD));
            String potion = antifireTier == 2 ? "super" : antifireTier == 1 ? "antifire" : "";
            return layers.get(shield ? (potion.isEmpty() ? "shield" : "shield+" + potion) : potion.isEmpty() ? "none" : potion);
        }

        int maxHit(int i, int[] row) { return i == dragonfire && row != null ? row[0] : maxHits[i]; }

        /** Attack i's prayer factor; the dragonfire's is Protect from Magic's
         * share of its damage for this set. */
        double factor(int i, int[] row) {
            if (i != dragonfire || row == null) return prayerFactors[i];
            return row[0] == 0 ? 1 : (double) row[1] / row[0];
        }

        /** Each `which` attack's dps if it attacked every cycle - the ONE
         * rate path, for the beam's totals and the card's rows alike.
         * Typeless attacks have no defence roll: they always hit; a chained
         * hit lands only when the hit before it did. */
        void fillDps(Loadout loadout, double[] dps, boolean[] which, int[] row) {
            double[] land = new double[kinds.length];
            fillLand(loadout, land);
            for (int i = 0; i < kinds.length; i++) {
                if (which[i]) dps[i] = land[i] * (maxHit(i, row) / 2.0) * (hits == null ? 1 : hits[i]) / (speeds[i] * 0.6);
            }
        }

        /** Each attack's chance to land against the set. */
        void fillLand(Loadout loadout, double[] land) {
            StatBlock def = loadout.defensive;
            for (int i = 0; i < kinds.length; i++) {
                double accuracy = kinds[i] == KIND_TYPELESS ? 1.0
                    : accuracyOfKind(kinds[i], attackRolls[i], def, defenceLevel, magicEffective);
                land[i] = chain != null && chain[i] ? accuracy * land[i - 1] : accuracy;
            }
        }

        /** What the boss heals per second off this set: each healing attack's
         * land chance x its mean heal per landed hit, at its rate, through
         * the picked prayer (a blocked hit heals nothing). */
        public double healRate(Loadout loadout) {
            if (healPerLand == null || revenant && wearsChargedEthereum(loadout)) return 0;
            double[] land = new double[kinds.length];
            fillLand(loadout, land);
            double[] dps = new double[kinds.length];
            int[] row = dragonfireRow(loadout);
            fillDps(loadout, dps, live, row);
            int prayer = bestPrayer(dps, row);
            double heal = 0;
            for (int i = 0; i < kinds.length; i++) {
                if (live[i] && healPerLand[i] > 0) heal += land[i] * healPerLand[i] * hits[i] * shares[i] / (speeds[i] * 0.6) * through(i, prayer, row);
            }
            return heal;
        }

        /** v1 uniform model: the nastiest prayable modeled style, or -1 -
         * the one the protection prayer blocks. */
        int bestBlockable(double[] dps) {
            int best = -1;
            double bd = -1;
            for (int i = 0; i < kinds.length; i++) {
                if (live[i] && prayable[i] && dps[i] > bd) {
                    bd = dps[i];
                    best = i;
                }
            }
            return best;
        }

        /**
         * Override model: the prayer that SAVES the most (its index in the
         * distinct list, -1 when none saves). An attack's saving is dps x
         * share x (1 - prayerFactor) - partial pierces (Corp magic, Callisto
         * melee) save less than their raw size suggests; prayable=false
         * attacks always land, and typeless chip damage is encoded in the
         * curated maxHit. Ties go to the prayer whose first positive saving
         * came first in the curated attack order (deterministic).
         */
        int bestPrayer(double[] dps, int[] row) {
            double[] saved = new double[prayerNames.length];
            int[] order = new int[saved.length];
            int count = 0;
            for (int i = 0; i < kinds.length; i++) {
                // An avoidable attack is dodged by the standard play - no saving;
                // a flicked one is prayed on cue whatever is camped - no vote.
                double saving = live[i] && !flicked[i] ? dps[i] * shares[i] * (1 - factor(i, row)) : 0;
                if (saving > 0) {
                    int p = prayerIndex[i];
                    if (saved[p] == 0) order[count++] = p;
                    saved[p] += saving;
                }
            }
            int best = -1;
            double bs = 0;
            for (int k = 0; k < count; k++) {
                if (saved[order[k]] > bs) {
                    bs = saved[order[k]];
                    best = order[k];
                }
            }
            return best;
        }

        /** The fraction of attack i that gets through the picked prayer. A
         * flicked attack is prayed on cue whatever is camped: its leak lands
         * in full, the rest at the pierce factor. */
        double through(int i, int prayer, int[] row) {
            double f = factor(i, row);
            if (flicked[i]) return leaks[i] + (1 - leaks[i]) * f;
            return prayer >= 0 && prayerIndex[i] == prayer && f < 1 ? f : 1;
        }

        /** "Protect from Missiles / Protect from Magic on cue": the prayers
         * the flicked attacks call for, or null when nothing is flicked. */
        String onCue() {
            List<String> prayers = new ArrayList<>();
            for (int i = 0; i < kinds.length; i++) {
                if (live[i] && flicked[i] && !prayers.contains(prayerOf[i])) prayers.add(prayerOf[i]);
            }
            return prayers.isEmpty() ? null : String.join(" / ", prayers) + " on cue";
        }

        /**
         * The prayed incoming dps of calculate() for this loadout - same
         * math, same order, none of the display allocations.
         */
        public double totalDps(Loadout loadout) {
            if (revenant && wearsChargedEthereum(loadout)) return 0;
            double[] dps = new double[kinds.length];
            int[] row = dragonfireRow(loadout);
            fillDps(loadout, dps, live, row);
            double total = 0;
            if (!overridden) {
                int blocked = bestBlockable(dps);
                for (int i = 0; i < kinds.length; i++) {
                    if (live[i] && i != blocked) total += dps[i] * share;
                }
                return total;
            }
            int prayer = bestPrayer(dps, row);
            for (int i = 0; i < kinds.length; i++) {
                if (live[i]) total += dps[i] * shares[i] * through(i, prayer, row);
            }
            return total;
        }

        /** The display answer for this loadout: every threat as the card
         * lists it, the prayer, both totals, the coverage and the names the
         * number leaves out. The same rates totalDps() scores. */
        Result result(Loadout loadout) {
            if (!overridden && kinds.length == 0) return new Result(0, 0, null, List.of(), null, NONE, List.of());
            double[] dps = new double[kinds.length];
            int[] row = dragonfireRow(loadout);
            fillDps(loadout, dps, priced, row);
            List<StyleThreat> threats = new ArrayList<>();
            List<String> unmodeled = new ArrayList<>();
            String prayer = null;
            double total = 0;
            double u = 0;
            if (!overridden) {
                // Uniform rotation: each modeled style owns 1/m of the cadence;
                // the nastiest prayable one is prayed away.
                int blocked = bestBlockable(dps);
                prayer = blocked < 0 ? null : prayerOf[blocked];
                int modeled = 0;
                for (int i = 0; i < kinds.length; i++) {
                    if (kinds[i] == KIND_NONE) continue;
                    if (!live[i]) {
                        unmodeled.add(labels[i]);
                        threats.add(new StyleThreat(labels[i], 0, 0, false, false, 0, 1));
                        continue;
                    }
                    modeled++;
                    boolean ib = i == blocked;
                    threats.add(new StyleThreat(labels[i], dps[i], maxHits[i], true, ib, share, ib ? 0 : 1));
                    u += dps[i] * share;
                    if (!ib) total += dps[i] * share;
                }
                // A monster whose styles are all "None" does not attack: modeled, 0.
                String coverage = !unmodeled.isEmpty() ? (modeled == 0 ? NONE : PARTIAL) : FULL;
                return new Result(total, u, prayer, threats, null, coverage, unmodeled);
            }
            // D-2 path: the curated attack list replaces the uniform model;
            // the protection prayer blocks the class that saves the most.
            int best = bestPrayer(dps, row);
            prayer = best < 0 ? onCue() : prayerNames[best];
            for (int i = 0; i < kinds.length; i++) {
                if (avoidable[i]) {
                    // In the threat list for the tooltip (what it WOULD hit),
                    // out of both totals - the standard play dodges it, so an
                    // unknown cadence costs it nothing either.
                    threats.add(new StyleThreat(labels[i] + " (avoidable - assumed dodged)",
                        dps[i], maxHit(i, row), priced[i], false, shares[i], 1));
                    continue;
                }
                if (!priced[i]) {
                    unmodeled.add(labels[i]);
                    threats.add(new StyleThreat(labels[i], 0, maxHit(i, row), false, false, 0, 1));
                    continue;
                }
                double through = through(i, best, row);
                u += dps[i] * shares[i];
                total += dps[i] * shares[i] * through;
                String label = !flicked[i] ? labels[i] : labels[i] + " (prayed on cue"
                    + (leaks[i] > 0 ? String.format(", ~%.0f%% lands", leaks[i] * 100) : "") + ")";
                threats.add(new StyleThreat(label, dps[i], maxHit(i, row), true, through < 1, shares[i], through));
            }
            return new Result(total, u, healRate(loadout), prayer, threats, note, unmodeled.isEmpty() ? CURATED : PARTIAL, unmodeled);
        }
    }

    /** The mean of clamp(factor x d, min, max) over a uniform 0..max roll. */
    static double meanHeal(double[] heal, int maxHit) {
        double sum = 0;
        for (int d = 0; d <= maxHit; d++) sum += Math.min(heal[2], Math.max(heal[1], heal[0] * d));
        return sum / (maxHit + 1);
    }

    /** Corpus style text, cleaned: lower case, no DEL noise, single spaces. */
    static String clean(String raw) {
        return Names.lower(raw.replace('\u007f', ' ')).trim().replaceAll("\\s+", " ");
    }

    /** The style as the card shows it - the corpus spelling, minus noise. */
    static String shown(String raw) { return raw.replace('\u007f', ' ').trim(); }

    /** "Typeless X" and "X (100% prayer penetration)" roll X's accuracy,
     * but no protection prayer stops them (wiki: Typeless damage). */
    static boolean prayable(String raw) {
        String style = clean(raw);
        return !style.startsWith("typeless") && !style.contains("prayer penetration");
    }

    /**
     * The KIND_* for a sheet/override style string (never "typeless").
     * Corpus spellings are normalised first (ISS-01M5FZ3KYCGEMMX1A45D3J9NQ9):
     * "Typeless slash" rolls slash, "Melee (Slash)" names its type,
     * "Magic (special)" is magic, "None"/"N/A" do not attack.
     */
    static int kindOf(String rs) {
        String style = clean(rs).replace("(100% prayer penetration)", "").trim();
        if (style.isEmpty() || style.equals("none") || style.equals("n/a")) return KIND_NONE;
        if (style.startsWith("typeless ")) style = style.substring("typeless ".length());
        style = style.startsWith("melee (") ? style.substring("melee (".length()).replace(")", "").replace("?", "").trim()
            : style.replaceAll("\\s*\\(.*\\)$", "");
        if (style.equals("single and multi-target ranged")) style = "ranged";
        switch (style) {
            case "stab": return KIND_STAB;
            case "slash": return KIND_SLASH;
            case "crush": return KIND_CRUSH;
            case "melee": return KIND_MELEE_GENERIC;
            case "ranged":
            case "range":
            // The inverse hybrids roll vs RANGED defence (wiki: Ranged
            // magic "less likely to hit players wearing armour with high
            // Ranged defence bonuses"; Ranged melee likewise) - the
            // delivery only decides the prayer, mapped separately.
            case "ranged magic":
            case "ranged melee": return KIND_RANGED;
            case "magic":
            case "magical ranged":
            case "magical melee":
            // The NMZ Moss Guardians' corpus spelling of magical melee.
            case "magic melee": return KIND_MAGIC;
            default: return KIND_UNMODELED;
        }
    }

    static int attackRollFor(int kind, MonsterOffence off) {
        Family family = familyOf(kind);
        return family == MELEE ? npcRoll(off.attackLevel, off.attackBonus)
            : family == RANGED ? npcRoll(off.rangedLevel, off.rangedBonus)
            : family == MAGIC ? npcRoll(off.magicLevel, off.magicBonus) : 0;
    }

    /** The listed-max family of each modeled KIND_* (indexed by kind). */
    static final Family[] FAMILY = {MELEE, MELEE, MELEE, MELEE, RANGED, MAGIC};

    /** The listed-max family of a modeled KIND_*, or null. */
    static Family familyOf(int kind) { return kind >= 0 && kind < FAMILY.length ? FAMILY[kind] : null; }

    /** True when every attacking style is one family - only then can an
     * unlabelled listed max ("50") be trusted for it; a dragon's plain 50
     * might be its dragonfire. */
    static boolean singleFamily(List<String> styles) {
        Family only = null;
        boolean seen = false;
        for (String style : styles) {
            int kind = kindOf(style);
            if (kind == KIND_NONE) continue;
            Family family = familyOf(kind);
            if (family == null || seen && family != only) return false;
            only = family;
            seen = true;
        }
        return seen;
    }

    /** Sheet styles the model can price: a known kind at a known speed. */
    static int modeledCount(List<String> styles, MonsterOffence off) {
        int modeled = 0;
        for (String style : styles) {
            if (kindOf(style) >= 0 && off.speedTicks > 0) modeled++;
        }
        return modeled;
    }

    /** The wiki's listed max for this kind's family (or the unlabelled one
     * when the monster attacks in one family only), else the formula -
     * the formula misses magic and str-0 sheets badly (Vardorvis 1 vs 37). */
    static int maxHitFor(int kind, MonsterOffence off, boolean singleFamily) {
        Family family = familyOf(kind);
        if (family != null) {
            MaxHits listed = off.listedMaxHits;
            int max = listed.of(family);
            if (max < 0 && singleFamily) max = listed.of(GENERAL);
            if (max > 0) return max;
        }
        return family == MELEE ? npcMaxHit(off.strengthLevel, off.strengthBonus)
            : family == RANGED ? npcMaxHit(off.rangedLevel, off.rangedStrengthBonus)
            : family == MAGIC ? npcMaxHit(off.magicLevel, off.magicStrengthBonus) : 0;
    }

    /** Player magic defence level: floor(70% Magic) + floor(30% Defence) + 8
     * (osrs-dps-calc NPCVsPlayerCalc; the +8 sits OUTSIDE the 30%, where the
     * old (Defence+9) term read magic damage taken 4-7% high). Stance 0. */
    static int magicEffectiveLevel(int defenceLevel, int ml) {
        return (int) (ml * 0.7) + (int) (defenceLevel * 0.3) + 8;
    }

    /** The defensive bonus a KIND_* attack rolls against. */
    static int defenceBonusOf(int kind, StatBlock def) {
        switch (kind) {
            case KIND_STAB: return def.stab;
            case KIND_SLASH: return def.slash;
            case KIND_CRUSH: return def.crush;
            case KIND_MELEE_GENERIC:
                // Generic "Melee": assume the boss hits your weakest side.
                return Math.min(def.stab, Math.min(def.slash, def.crush));
            case KIND_RANGED: return def.ranged;
            default: return def.magic;
        }
    }

    /**
     * The monster's chance to hit with this KIND_* vs the loadout - the ONE
     * accuracy path, shared by the display calculate() and the beam's
     * Prepared. The level-term guard must stay an explicit STAB..RANGED range
     * check: KIND_UNMODELED is -1, so a "kind < KIND_RANGED" test would hand
     * it the melee (Defence+8) term instead of the magic term the old
     * string-keyed else-branch gave it.
     */
    static double accuracyOfKind(int kind, int ar, StatBlock def, int defenceLevel, int magicEffective) {
        // Defence + 8 + stance (wiki DPS/Melee); the optimizer assumes an
        // offensive stance, which adds no defence.
        long level = kind >= KIND_STAB && kind <= KIND_RANGED ? defenceLevel + 8 : magicEffective;
        return accuracy(ar, level * (defenceBonusOf(kind, def) + 64));
    }

    /** antifireTier: the page's antifireMode - 0 no potion, 1 antifire, 2 super
     * antifire - for curated dragonfire layers (Vorkath, ISS-01M5WVMVD4MZMWC7GRQDV3AFSY). */
    public static Result calculate(MonsterStats monster, Loadout loadout, int defenceLevel, int ml, int antifireTier) {
        // Bracelet of ethereum (charged): revenants deal NO damage while it
        // is worn - an absolute rule that outranks every other model.
        if (monster.isRevenantMonster() && wearsChargedEthereum(loadout)) return new Result(0, 0, null, List.of(), "Bracelet of ethereum blocks all revenant damage", FULL, List.of());
        // The wiki cannot price it yet: say why instead of a bare "?".
        String unpriced = BossIncomingOverrides.unpricedReason(monster);
        if (unpriced != null) return new Result(0, 0, null, List.of(), unpriced, NONE, List.of());
        return new Prepared(monster, BossIncomingOverrides.overridesFor(monster), defenceLevel, ml, antifireTier) .result(loadout);
    }

    static boolean wearsChargedEthereum(Loadout loadout) {
        GearItem hands = loadout.get(GearSlot.HANDS);
        return hands != null && "bracelet of ethereum".equals(hands.nameLower) && "charged".equals(hands.versionLower);
    }

    static String displayStyle(String style) {
        return style.isEmpty() ? style : Character.toUpperCase(style.charAt(0)) + style.substring(1);
    }

    /** The prayer follows the attack's DELIVERY, never its accuracy roll
     * (wiki: Magical melee / Magical ranged / Ranged magic / Ranged melee;
     * field report 2026-08-08, grZ: a Pyrefiend's magical melee was told
     * Protect from Magic - Protect from MELEE blocks it):
     *   magical/magic melee -> Melee (rolls vs magic def, swings melee)
     *   magical ranged      -> Missiles (rolls vs magic def, projectile)
     *   ranged magic        -> Magic (rolls vs ranged def, a cast -
     *                          Kree'arra, Spinolyps, Torchers)
     *   ranged melee        -> Melee (rolls vs ranged def, swings melee) */
    static String protectPrayerFor(String rs) {
        String style = clean(rs);
        if (style.contains("melee")) return "Protect from Melee";
        if (style.startsWith("magical ranged")) return "Protect from Missiles";
        if (style.equals("ranged magic") || style.startsWith("magic")) return "Protect from Magic";
        if (style.startsWith("ranged") || style.equals("range")) return "Protect from Missiles";
        return "Protect from Melee";
    }

    static int npcRoll(int level, int bonus) { return (level + 9) * (bonus + 64); }

    static int npcMaxHit(int level, int bonus) {
        return (int) (((long) (level + 9) * (bonus + 64) + 320) / 640);
    }

    static double accuracy(long ar, long dr) {
        if (ar > dr) return 1.0 - (dr + 2.0) / (2.0 * (ar + 1.0));
        return ar / (2.0 * (dr + 1.0));
    }
}
