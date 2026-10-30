package com.loadoutlab.optimizer;

import com.loadoutlab.data.*;
import com.loadoutlab.engine.*;
import lombok.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.*;
import java.util.regex.*;
import com.loadoutlab.engine.IncomingDpsCalculator.Result;
import com.loadoutlab.engine.LoadoutOptimizer.*;
import static com.loadoutlab.data.GearSlot.*;
import static com.loadoutlab.engine.CombatStyle.*;
import static com.loadoutlab.engine.DpsResult.*;
import static com.loadoutlab.engine.MonsterMechanics.*;
import static com.loadoutlab.engine.MultiTool.*;
import static com.loadoutlab.engine.PrayerBonuses.*;
import static com.loadoutlab.engine.PrayerUnlocks.*;
import static com.loadoutlab.engine.RaidBoosts.*;
import static com.loadoutlab.engine.RangedAmmo.*;
import static com.loadoutlab.optimizer.OptimizerService.OptimizeMode.*;
import static java.lang.Math.*;
import java.util.stream.Collectors;
import static java.util.Collections.*;
import static com.loadoutlab.data.RequiredGear.*;
import static com.loadoutlab.engine.LoadoutOptimizer.*;
import static com.loadoutlab.engine.MaxCapeSims.*;

// Runs BiS searches off the game threads and caches results.
// <p>Caching (founding requirement): results are keyed PER STYLE by
// (collection fingerprint, monster id, levels, f2p, ..., style, that style's
// pins) - the fingerprint changes iff ownership changes, so a cache hit is
// always current, and per-style state (pins) never invalidates the other
// styles' answers. LRU-bounded.
// <p>Each query answers TWO questions per style: the best set you OWN, and
// the best set that exists in the game - so the panel can show how close
// your gear is to the ceiling.
// <p>Threading: optimize() is CPU work - it runs on a single daemon worker,
// never the EDT or client thread. Callbacks are NOT thread-marshalled here;
// UI callers wrap in SwingUtilities.invokeLater.
public class OptimizerService {
    static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OptimizerService.class);

    // Per-STYLE entries (a monster query stores three).
    static final int CACHE_MAX = 192;

    // Which point of the offence/defence frontier to show - the dev-mode
    // prototype of ISS-01M5G5CZC9SK46499FACRSJ8ZT. Andrew 2026-10-21:
    // "balance = maximizing the DTPS/DPS ratio in an optimized way, tanky =
    // minimum DTPS" - Balanced takes the least damage per damage dealt.
    public enum OptimizeMode {
        MAX_DPS, BALANCED, TANKY
    }

    // Frontier sweep weights, as multiples of maxDps/incoming; the 10.0
    // extreme reaches the genuine minimum-intake end (491b6c0).
    static final double[] SWEEP_ALPHAS = {0.3, 0.7, 1.5, 3.0, 10.0};

    // What a defensive mode did to the shown set: the DPS it gave up and
    // the damage taken it cut, whole percents vs the max-DPS set - or, when
    // it kept that set, why (reason non-null).
    @AllArgsConstructor
    public static class ModeTrade {
        public final OptimizeMode mode;
        public final int dpsLossPct;
        public final int dmgCutPct;
        public final String reason;
    }

    // One step on a slot ladder: the item, the set's DPS and damage taken
    // with it swapped in, and whether you own it.
    @RequiredArgsConstructor
    public static class Rung {
        public final GearItem item;
        public final double dps;
        public final double dtps;
        public final boolean owned;
        // The ammo a weapon step brings when it changes the worn ammo
        // (ISS-01M5H2AH8ERQG471AZY3S67FKW); null otherwise.
        public final GearItem with;
        // The shield a one-handed weapon step brings when it changes the
        // worn one (weapon + offhand vs a two-hander, ISS-01M5JFG00G1BS82X9X2QNZZC7S).
        public GearItem offhand;
        // A group step's dps for EACH member (index-aligned with the roster):
        // the card shows its own mob's number, the group figure ranks
        // (ISS-01M5PE4TZXTGJAQA1YXNRKPEDW). Null on a solo ladder.
        public double[] perMob;
        // The swapped set behind a solo step: what the card adopts when an
        // owned step outranks the worn item (ISS-01M5PKSQBAP823XNC13QBW8830).
        DpsResult set;
    }

    // A slot's ladder (ISS-01M5GE9EJSX6YWCKMSYEWKZ132): the steps just above
    // the worn item (any item, closest first) and just below (owned only,
    // closest first), ranked by the mode the set was picked for. Shown in
    // game as the slot's "swaps".
    @RequiredArgsConstructor
    public static class Ladder {
        // Steps each way by default...
        public static final int RUNGS = 2;
        // ...and always this many beside the worn item when the steps exist.
        public static final int SPOTS = 2 * RUNGS;
        // ...and this many further steps each way kept for the strip's
        // "more" pages (ISS-01M5QD7PNMCMNRZD92RPRMHWRP, Andrew 2026-10-24: "an
        // option to 'show more' on the ladder to see more alternatives").
        public static final int MORE = 10;
        // Priced with the spec (the ring slot): the hovers say so.
        public boolean spec;
        // The steps past up/down, closest first - the pages after the strip.
        public List<Rung> upMore = List.of();
        public List<Rung> downMore = List.of();
        // Scored across a group search's members, HP-weighted, on the
        // group's shared kit (ISS-01M5JAGFEJ5QHXE37B37XM8HJJ).
        public boolean group;
        // The worn item's dps per member on a group ladder (null solo).
        public double[] basePerMob;
        // The weapon slot only: every step the strip could show, best first,
        // for the "All weapons" list (ISS-01M5PE4TZXTGJAQA1YXNRKPEDW).
        public List<Rung> all;
        // The magic weapon slot only: the same steps split by kind - powered
        // staves and autocast staves, each around the worn weapon (Andrew
        // 2026-10-24: "for magic just show two ladders: a powered and a non
        // powered one so we don't need to do the math of combining them").
        // Null elsewhere, and for a kind with no step.
        public Ladder powered;
        public Ladder autocast;

        // How many steps up and down to show (ISS-01M5H13V0TVF58RHPHT8WQ1GFP,
        // Andrew 2026-10-21: "when the user has bis show the 4 preceding"):
        // two each way, a short side's spare spots going to the other.
        static int[] spots(int above, int below) {
            int up = min(RUNGS, above);
            int down = min(RUNGS, below);
            up += min(SPOTS - up - down, above - up);
            down += min(SPOTS - up - down, below - down);
            return new int[]{up, down};
        }

        public final OptimizeMode mode;
        public final double baseDps;
        public final double baseDtps;
        public final List<Rung> up;
        public final List<Rung> down;
    }

    // Per-style outcome: your best owned sets, the game-wide best set, and
    // the strongest special-attack weapon for each - owned and game-wide.
    public static class StyleResult {
        // The defensive mode's trade on the Yours side; null on Max DPS.
        public ModeTrade modeTrade;
        // GWD protection CARRIED on each side (worn on the way in, swapped
        // off for the fight) - also in the bench; empty away from GWD.
        public List<GearItem> protection = List.of();
        public List<GearItem> gameProtection = List.of();
        public Map<GearSlot, Ladder> ladder = Map.of();
        // The carried spec weapon's swaps, ranked by DPS added; null when
        // no spec is carried (ISS-01M5HCS725N7XVVBC7CMK6Y43A).
        public Ladder specLadder;
        public final List<DpsResult> owned;
        public final DpsResult overallBest;
        public final SpecialAttack spec;
        public final GearItem specWeapon;
        public final double specExpectedDamage;
        public final double specDpsAdded;
        public final SpecialAttack gameSpec;
        public final GearItem gameSpecWeapon;
        public final double gameSpecExpectedDamage;
        public final double gameSpecDpsAdded;
        // The prayers/boost YOUR numbers assume ("Deadeye + Ranging potion").
        public final String boostLabel;
        // The ceiling assumption for game best ("Rigour + Ranging potion").
        public final String gameBoostLabel;
        // What the boss does back to you in the shown owned set (nullable);
        // a group's mode re-wear resets it (modeCard).
        public Result incoming;
        // Same, in the game-best set - the BiS side's DTPS (nullable).
        public final Result gameIncoming;
        // The INVENTORY vs this mob on the Yours side: the trip plan's
        // items not currently worn (spec weapon first).
        public List<GearItem> bench;
        // Same, for the BiS side's kit.
        public final List<GearItem> gameBench;
        // True when the Yours half is something the CARRIED KIT can
        // actually assemble mid-trip (or no kit ran, so the shared set IS
        // the trip). False marks a tab-only fallback - "what this style
        // could do if you geared for it" - which must never win the mob
        // row (field bug 2026-08-06: the vents row showed a whole ranged
        // set on a melee kit with Inventory 3).
        public final boolean ownedKitBacked;
        // Same contract for the BiS half.
        public final boolean gameKitBacked;
        // Slots of the Yours set where some item in the game beats the
        // worn one with the rest of the set fixed - no gold border there
        // (Andrew, field 2026-09-27: an occult on a Fire Bolt card).
        public Set<GearSlot> beaten = Set.of();
        // The shown sets' damage per attack (HitDist) for their kill time.
        public double[] hitDist, gameHitDist;
        // Why a carried swap picked by kind rides (item id -> the rule's note).
        public Map<Integer, String> swapNotes = Map.of(), gameSwapNotes = Map.of();
        // The Multi card rows (tool key -> per-target result) when the
        // switch is on and the mob is fought in multi; null otherwise.
        public Map<String, StyleResult> multiTools;

        StyleResult(List<DpsResult> owned, DpsResult overallBest,
            SpecPick spec, SpecPick gameSpec, String boostLabel, String gameBoostLabel,
            Result incoming, Result gameIncoming, List<GearItem> bench, List<GearItem> gameBench,
            boolean ownedKitBacked, boolean gameKitBacked) {
            this.ownedKitBacked = ownedKitBacked;
            this.gameKitBacked = gameKitBacked;
            this.bench = bench == null ? List.of() : bench;
            this.gameBench = gameBench == null ? List.of() : gameBench;
            this.boostLabel = boostLabel;
            this.gameBoostLabel = gameBoostLabel;
            this.incoming = incoming;
            this.gameIncoming = gameIncoming;
            this.owned = owned;
            this.overallBest = overallBest;
            this.spec = spec == null ? null : spec.spec;
            specWeapon = spec == null ? null : spec.weapon;
            specExpectedDamage = spec == null ? 0 : spec.expectedDamage;
            specDpsAdded = spec == null ? 0 : spec.dpsAdded;
            this.gameSpec = gameSpec == null ? null : gameSpec.spec;
            gameSpecWeapon = gameSpec == null ? null : gameSpec.weapon;
            gameSpecExpectedDamage = gameSpec == null ? 0 : gameSpec.expectedDamage;
            gameSpecDpsAdded = gameSpec == null ? 0 : gameSpec.dpsAdded;
        }
    }

    static final double EPS = 1e-9;
    final LoadoutData data;
    final LoadoutOptimizer optimizer = new LoadoutOptimizer();

    // Stamp the Yours set's beaten slots on a fresh result.
    StyleResult verified(StyleResult result, LoadoutData dataset, OptimizationRequest own, OptimizationRequest game) {
        if (own != null && game != null && !emptyBest(result.owned)) result.beaten = optimizer.beatenSlots(dataset, own, game, result.owned.get(0));
        if (own != null && !emptyBest(result.owned)) result.hitDist = hitDist(dataset, own, result.owned.get(0));
        if (game != null && result.overallBest != null) result.gameHitDist = hitDist(dataset, game, result.overallBest);
        result.swapNotes = avasNotes(result.bench);
        result.gameSwapNotes = avasNotes(result.gameBench);
        return result;
    }

    // The shown set's damage per attack at the card's spell
    // (ISS-01M655THWG41VPTM1ZTRA5B3YE); null unless it reproduces the card's
    // expected hit.
    static double[] hitDist(LoadoutData data, OptimizationRequest request, DpsResult shown) {
        String spell = shown.getSpellName();
        if (spell != null) {
            for (SpellStats s : data.spells) if (s.name.equals(spell)) request = request.withSpell(s);
        }
        var calc = new DpsCalculator();
        double[] dist = calc.hitDist(request, shown.loadout);
        return dist != null && Math.abs(HitDist.mean(dist) - shown.expectedHit) <= 1e-6 * Math.max(1, shown.expectedHit) ? dist : null;
    }

    // Roster fan-out pool (field fix 2026-07-17: a 15-mob raid ran ~90
    // independent optimize() calls serially - THE wall, not the kit
    // search). Small and near-minimum priority so the client thread never
    // starves; each task builds its own LoadoutOptimizer because the
    // optimizer's internal DpsCalculator is stateful. Fixed size rather
    // than sized to the host: the Plugin Hub bans java.lang.Runtime, so
    // availableProcessors() is unavailable - 4 min-priority daemon threads
    // clear the serial wall without oversubscribing a modest machine.
    // The 4 is also what the #14014 review thread was told, so it stays 4
    // until a widening is deliberately decided and re-verified - a wider
    // pool rode along uninvited once (2026-08-05, reverted same day).
    static final int ROSTER_POOL_SIZE = 4;
    final ExecutorService rosterPool = Executors.newFixedThreadPool( ROSTER_POOL_SIZE, r -> {
                var t = new Thread(r, "loadout-lab-roster");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY + 1);
                return t;
            });
    final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        var t = new Thread(r, "loadout-lab-optimizer");
        t.setDaemon(true);
        // A full search pegs a core for seconds; at default priority that
        // competes with the client thread for CPU (field report: the game
        // felt choppy while recomputing). Background-priority compute only.
        t.setPriority(Thread.MIN_PRIORITY + 1);
        return t;
    });

    // Lazily-built free-to-play view of the dataset (gear filtered by the members flag).
    volatile LoadoutData f2pData;

    // Supersession: every bestPerStyle call takes a fresh ticket; an
    // in-flight computation checks it at each checkpoint and abandons
    // itself the moment a newer request exists (toggling slayer/f2p/risk
    // mid-load must not make the new answer wait behind the stale one,
    // nor let stale results flash in). Panel-driven: only the newest
    // request ever matters.
    final AtomicLong requestSeq = new AtomicLong();

    // Results cached PER STYLE, not per query: pins are per-style state, so
    // pinning a melee item must not throw away the ranged and magic answers
    // (a pin toggle was recomputing all three - the reported-choppy path).
    // The key is the query key minus pins, plus the style and ITS pins.
    final Map<String, StyleResult> cache = new LinkedHashMap<>(32, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, StyleResult> eldest) { return size() > CACHE_MAX; }
        };

    public OptimizerService(LoadoutData data) { this.data = data; }

    // Compute, per melee/ranged/magic: the best OWNED set and the game-wide
    // best set against a monster. Cached; the callback runs on the worker
    // thread on a miss and synchronously on a hit.
    public void bestPerStyle( MonsterStats monster, PlayerLevels rl, PlayerLevels boostedLevels,
        PrayerUnlocks pu, RequirementProfile requirements, OwnedItems owned, int collectionFingerprint,
        ComputeParams p, Map<CombatStyle, Set<Integer>> ebs, Set<Integer> di,
        Map<CombatStyle, Map<GearSlot, Integer>> pinnedByStyle, SpellStats pinnedSpell, int pinnedSpec,
        Set<Integer> poi, Consumer<Map<CombatStyle, StyleResult>> callback) {
        int id = monster.profileId();
        bestPerStyle(monster, buildContext(rl, boostedLevels, pu,
            requirements, owned, collectionFingerprint, p, ebs, di,
            singletonMap(id, pinnedByStyle), singletonMap(id, pinnedSpell),
            singletonMap(id, pinnedSpec), poi), callback);
    }

    // The single-mob compute on a built context: a synchronous answer when
    // every style is cached, else one worker job. A one-mob roster takes
    // this road too, its member's own exclusions and sims on the context.
    void bestPerStyle(MonsterStats monster, ComputeContext ctx, Consumer<Map<CombatStyle, StyleResult>> callback) {
        ctx.gwdAncient = ctx.p.gwdAncient;
        String bk = baseKeyFor(monster, ctx);
        Map<CombatStyle, StyleResult> ac = new EnumMap<>(CombatStyle.class);
        synchronized (cache) {
            for (CombatStyle style : concreteValues()) {
                StyleResult hit = cache.get(styleKey(bk, style, ctx, monster));
                if (hit == null) {
                    ac = null;
                    break;
                }
                ac.put(style, hit);
            }
        }
        if (ac != null) {
            callback.accept(ac);
            return;
        }
        submit(ticket -> computeAllStyles(monster, ctx, ticket), () -> new EnumMap<>(CombatStyle.class),
            "compute failed for {}", monster.name, callback);
    }

    // One compute on the worker under a fresh ticket: abandoned when
    // superseded while queued, mid-flight (a null answer) or after it. A
    // throw must still answer (the fallback), or the card sits on Computing
    // for good (TonyDAnnunzio 2026-10-18).
    <T> void submit(LongFunction<T> compute, Supplier<T> fallback, String failure, String name, Consumer<T> callback) {
        long ticket = requestSeq.incrementAndGet();
        worker.execute(() -> {
            if (requestSeq.get() != ticket) return; // superseded while queued
            T results;
            try {
                results = compute.apply(ticket);
            } catch (RuntimeException ex) {
                log.warn(failure, name, ex);
                results = fallback.get();
            }
            if (results == null || requestSeq.get() != ticket) return; // superseded during or after the compute
            callback.accept(results);
        });
    }

    // Every query-wide value the per-style compute needs, gathered once so
    // the roster path can run computeAllStyles for each mob.
    static class ComputeContext {
        // The page's compute settings as handed in; the fields below are
        // the ones the context derives or normalises from them.
        ComputeParams p;
        LoadoutData dataset;
        OwnedItems effectiveOwned;
        PlayerLevels real;
        PlayerLevels boostedLevels;
        PrayerUnlocks unlocks;
        RequirementProfile requirements;
        Map<CombatStyle, String> boostPicks = Map.of();
        Map<CombatStyle, String> prayerPicks = Map.of();
        String lock;
        int riskBudget;
        int collectionFingerprint;
        Set<Integer> dreams;
        Set<Integer> protectOnly;
        // The bench size: carried items beyond the worn set; the spec
        // weapon occupies a slot when it differs from the worn weapon.
        int maxSwaps = 1;
        // PER-MOB pins (profileId -> style -> slot -> item), pinned spell
        // and pinned spec, keyed like excludedByMob: a mixed roster reads
        // each member's own (ISS-01M4Y2VJ8DWBBKFR1HP9NR45NG); the single-mob
        // path wraps its three arguments into one-entry maps.
        Map<Integer, Map<CombatStyle, Map<GearSlot, Integer>>> pinsByMob;
        Map<Integer, SpellStats> spellByMob;
        // A pinned SPEC weapon id (0 = auto-pick): forces the spec slot on
        // the card whose style the weapon serves, leaving other styles to
        // pick freely. The pin machinery's carried-slot twin; a roster
        // seats its FIRST member's as the shared spec.
        Map<Integer, Integer> specByMob;
        Map<CombatStyle, Set<Integer>> excluded;
        // PER-MOB exclusions for rosters, keyed by MonsterStats.profileId()
        // (field decision 2026-07-17): each mob's request carries its own
        // exclusions on top of the global set - the group may still use the
        // item, the excluding mob's answer never does.
        Map<Integer, Map<CombatStyle, Set<Integer>>> excludedByMob = Map.of();
        // PER-MOB sims (profileId -> ids): counted as owned for that mob
        // only - the local twin of the global dream items.
        Map<Integer, Set<Integer>> dreamsByMob = Map.of();
        // The defensive mode (single-mob path only; rosters stay Max DPS).
        OptimizeMode mode = MAX_DPS;
        // The GWD Ancient chip: Zaros joins the route (the Nex bank).
        boolean gwdAncient;
        // This compute's request ticket - the mode sweep bails when superseded.
        long ticket;
    }

    // The spec-weapon restriction (0 = none): a user PIN forces the spec
    // slot to that weapon on EVERY style card, not just its own - specs
    // compete cross-style (a ranged Tonalztics is a real spec swap on a
    // melee setup; a DWH on a magic set), so a pin that only bound its own
    // style read as "the pin doesn't take" when viewing another card
    // (field report 2026-08-09). A card the weapon cannot serve shows the
    // pinned spec at its honest ~0 (keepZero), never a different auto pick.
    static Set<Integer> restrictSpec(ComputeContext ctx, CombatStyle style, MonsterStats mob) {
        int id = ctx.specByMob.getOrDefault(mob.profileId(), 0);
        if (id == 0) return null;
        GearItem pinned = ctx.dataset.getGear(id);
        return pinned != null && SpecialAttack.match(pinned) != null ? singleton(id) : null;
    }

    // The style's pins for ONE mob (its own profile's). A weapon pin binds
    // only the styles its weapon fights with (ISS-01M5TH7V74GYQKRN231XRCM6BE,
    // Andrew 2026-10-25: a trident pinned on the Enraged Warden left "only
    // ... a magic set" though the bowfa was "a more applicaable range set").
    static Map<GearSlot, Integer> pinsFor(ComputeContext ctx, CombatStyle style, MonsterStats mob) {
        Map<CombatStyle, Map<GearSlot, Integer>> byStyle = ctx.pinsByMob.get(mob.profileId());
        Map<GearSlot, Integer> pins = byStyle == null ? Map.of() : byStyle.getOrDefault(style, Map.of());
        Integer weapon = pins.get(GearSlot.WEAPON);
        GearItem item = weapon == null ? null : ctx.dataset.getGear(weapon);
        if (item == null || item.isWeaponFor(style)) return pins;
        Map<GearSlot, Integer> out = new EnumMap<>(pins);
        out.remove(GearSlot.WEAPON);
        return out;
    }

    static SpellStats spellFor(ComputeContext ctx, MonsterStats mob) {
        return ctx.spellByMob.get(mob.profileId());
    }

    // The per-style pin state that shapes a cached answer: the mob's pins
    // and, on magic, its pinned spell.
    static String pinKey(ComputeContext ctx, CombatStyle style, MonsterStats mob) {
        SpellStats spell = spellFor(ctx, mob);
        return pinsFor(ctx, style, mob).hashCode() + "|" + (style == MAGIC && spell != null ? spell.name : "");
    }

    // Per-(mob, style, side) optimize results for the roster path, LRU.
    // Keyed WITHOUT mode/inventory (they do not shape an optimize), so a
    // chip tweak recomputes only the kit - not the ~2x3xN optimizes.
    final Map<String, List<DpsResult>> optimizeCache = new LinkedHashMap<String, List<DpsResult>>(256, 0.75f, true) {
            protected boolean removeEldestEntry(Map.Entry<String, List<DpsResult>> eldest) { return size() > 1024; }
        };

    static String optimizeKey(MonsterStats mob, CombatStyle style, boolean game, ComputeContext ctx) {
        // getId() ALONE collides: a boss's versions share one id (Maggot
        // King's Far/Nearby/Roaring, field report 2026-08-20 - Nearby's
        // cached melee answer was served for the melee-IMMUNE Far row).
        // The version string is the missing discriminator.
        return mob.id + "|" + mob.version + "|" + mob.getToaInvocationLevel()
            + "|" + style.name() + "|" + (game ? "g" : "o") + "|" + ctxKey(ctx) + "|" + dreamsFor(ctx, mob).hashCode()
            + "|" + excludedFor(ctx, style, mob).hashCode() + "|" + pinKey(ctx, style, mob);
    }

    // The query-wide ctx fields every cache key shares (keys only need
    // equality, so the two key builders reuse one middle).
    static String ctxKey(ComputeContext ctx) {
        return ctx.collectionFingerprint + "|" + Objects.hashCode(ctx.effectiveOwned.atHand) + "|" + ctx.p.f2pOnly + "|" + ctx.p.onSlayerTask
            + "|" + ctx.lock + "|" + ctx.unlocks.key() + "|" + ctx.p.maxTradeables
            + "|" + ctx.riskBudget + "|" + ctx.p.antifirePotion + "|" + ctx.p.antifireTier + "|" + ctx.p.setRules + "|" + ctx.p.sunfireRunes + "|" + ctx.p.multiView + "|" + ctx.p.multiStack + "|" + ctx.p.deathCharge
            + "|" + ctx.p.specWeapon + "|" + ctx.p.inWilderness + "|" + ctx.boostPicks + "|" + ctx.prayerPicks
            + "|" + ctx.p.upgradeBudgetGp + "|" + ctx.protectOnly.hashCode()
            // Levels participate in the key so a boost/level-up invalidates.
            + "|" + ctx.real + "|" + ctx.boostedLevels + "|" + ctx.p.kourendElite;
    }

    // One roster optimize (a pool task): LRU-cached; a hit hands out a
    // fresh list copy so downstream mutation never poisons the cache.
    List<DpsResult> optimizeCached(MonsterStats mob, CombatStyle style, boolean game,
        ComputeContext ctx, OptimizationRequest req, long ticket) {
        String key = optimizeKey(mob, style, game, ctx);
        synchronized (optimizeCache) {
            List<DpsResult> cached = optimizeCache.get(key);
            if (cached != null) return new ArrayList<>(cached);
        }
        if (requestSeq.get() != ticket) {
            return null; // superseded - stop burning cores
        }
        var local = new LoadoutOptimizer();
        List<DpsResult> best = local.optimize(ctx.dataset, req);
        polish(local, ctx.dataset, req, best);
        synchronized (optimizeCache) { optimizeCache.put(key, new ArrayList<>(best)); }
        return best;
    }

    // One side's polished optimize for a card: a style card's through the
    // optimize cache (mode and bench are not in its key, so a flip of either
    // reuses it), a multi tool's direct - as is a miss the ticket abandoned.
    List<DpsResult> sideBest(boolean game, ComputeContext ctx, OptimizationRequest req, ToolPick tool) {
        List<DpsResult> best = tool == null ? optimizeCached(req.monster, req.style, game, ctx, req, ctx.ticket) : null;
        if (best == null) {
            best = optimizer.optimize(ctx.dataset, req);
            polish(optimizer, ctx.dataset, req, best);
        }
        return best;
    }

    // The displayed set's finish, shared by every optimize site: top up
    // DPS-neutral empty slots and required utility (verified DPS-neutral).
    static void polish(LoadoutOptimizer opt, LoadoutData dataset, OptimizationRequest req, List<DpsResult> best) {
        if (!best.isEmpty()) {
            best.set(0, opt.fillDpsNeutralSlots(dataset, req, best.get(0)));
            best.set(0, opt.ensureRequiredUtility(dataset, req, best.get(0)));
        }
    }

    // The single-mob post-passes on a roster shown set (field reports
    // 2026-08-09): the Dizana's quiver relocation and the mandatory-recoil
    // swap (a Zulrah roster recommended sets with no recoil gear). Runs on
    // the phase-1 sets and again on whatever the kit merge shows.
    DpsResult postPass(ComputeContext ctx, OptimizationRequest req, DpsResult shown) {
        return optimizer.ensureRequiredUtility(ctx.dataset, req, optimizer.relocateQuiverAmmo(ctx.dataset, req, shown));
    }

    // The dream items for ONE mob: global sims plus that mob's own.
    static Set<Integer> dreamsFor(ComputeContext ctx, MonsterStats mob) {
        Set<Integer> local = ctx.dreamsByMob.get(mob.profileId());
        if (local == null || local.isEmpty()) return ctx.dreams;
        Set<Integer> merged = new HashSet<>(ctx.dreams);
        merged.addAll(withPairedAmmo(local));
        return merged;
    }

    // The style's exclusions for ONE mob: global plus that mob's own.
    static Set<Integer> excludedFor(ComputeContext ctx, CombatStyle style, MonsterStats mob) {
        Set<Integer> global = ctx.excluded.getOrDefault(style, Set.of());
        Map<CombatStyle, Set<Integer>> bm = ctx.excludedByMob.get(mob.profileId());
        Set<Integer> per = bm == null ? null : bm.get(style);
        if (per == null || per.isEmpty()) return global;
        Set<Integer> merged = new HashSet<>(global);
        merged.addAll(per);
        return merged;
    }

    // One multi tool through the per-style pipeline: BARRAGE pins Ice
    // Barrage (Ice Burst under 94), CHINS the best owned chinchompa (the
    // ceiling black), VENATOR the charged bow. Yours only when the player
    // owns the tool and, for the spell, its runes. Null when the account
    // cannot cast any burst.
    StyleResult multiToolResult(MonsterStats monster, ComputeContext ctx, MultiTool tool) {
        var pick = new ToolPick();
        boolean ownedSide;
        if (tool == BARRAGE) {
            int magic = ctx.boostedLevels.magic;
            String name = magic >= 94 ? "Ice Barrage" : "Ice Burst";
            pick.spell = ctx.dataset.spells.stream() .filter(sp -> sp.name.equals(name)).findFirst().orElse(null);
            if (pick.spell == null || magic < pick.spell.magicLevel) return null;
            ownedSide = SpellRunes.premiumRunesOwned(name, ctx.effectiveOwned);
        } else {
            // The owned scan canonicalises a charged bow onto its uncharged id,
            // so either id proves the bow; the pin always wears the charged one.
            int[] ids = tool == VENATOR ? new int[]{VENATOR_BOW, VENATOR_BOW_UNCHARGED}
                : tool == HALLOWFELL ? new int[]{HALLOWFELL_SWORD} : CHIN_IDS;
            int weapon = ids[0];
            ownedSide = false;
            for (int id : ids) {
                if (ctx.effectiveOwned.owns(id) || dreamsFor(ctx, monster).contains(id)) {
                    weapon = tool == VENATOR ? VENATOR_BOW : id;
                    ownedSide = true;
                    break;
                }
            }
            pick.pins = Map.of(WEAPON, weapon);
            pick.gamePins = Map.of(WEAPON, ids[0]);
        }
        return styleResultFor(monster, ctx, tool.style, pick, ownedSide);
    }

    // One style's answer: both sides, spec, incoming, bench. The multi
    // tools run through it with their pins; ownedSide false skips Yours
    // (a tool the player does not own).
    StyleResult styleResultFor(MonsterStats monster, ComputeContext ctx, CombatStyle style, ToolPick tool, boolean ownedSide) {
            BoostProfile supplied = suppliedBoost(monster);
            StylePlan plan = stylePlan(ctx, style, supplied);
            PlayerLevels sl = plan.levels;
            String boostLabel = plan.label;
            PlayerLevels gameLevels = plan.gameLevels;
            String gameBoostLabel = plan.gameLabel;
            // Dreams are pretend-owned; a positive upgrade budget also
            // admits anything buyable within it (total spend, tracked
            // by the beam).
            OptimizationRequest ownedRequest = ownedRequestFor(ctx, monster, style, sl, tool);
            // The displayed set: top up DPS-neutral empty slots with
            // prayer/defensive gear (verified not to change the DPS).
            List<DpsResult> ob = ownedSide ? sideBest(false, ctx, ownedRequest, tool) : new ArrayList<>();
            // A defensive mode swaps the shown set for its frontier pick
            // BEFORE spec, incoming and bench, so every downstream number
            // describes the set the card shows. Style cards only - a
            // Multi tool's pinned weapon is its own question.
            ModeTrade modeTrade = tool != null || ctx.mode == MAX_DPS || ob.isEmpty()
                ? null : applyMode(ctx, ownedRequest, ob, monster, ctx.ticket, optimizer);
            // The swaps strips, and the set made to wear its own owned upgrades
            // first (ISS-01M5PKSQBAP823XNC13QBW8830, Andrew 2026-10-24: "if it
            // is SIM IT AS THE MAIN SET"): the search's beam can miss a set one
            // swap away, the strip prices every swap, so an owned step above
            // the worn item is adopted BEFORE spec, incoming and bench read the
            // set. The strips are extras: one that fails is logged and dropped,
            // never the card (2026-10-22: a strip's NPE blanked every style it
            // touched).
            Map<GearSlot, Ladder> ladders = null;
            if (tool == null && ownedSide && !ob.isEmpty()) {
                try {
                    ladders = adoptOwnedSteps(ctx, ownedRequest, ob, monster);
                } catch (RuntimeException ex) {
                    log.warn("swaps strip failed for {} {}", monster.name, style, ex);
                }
            }
            if (ownedSide && !ob.isEmpty()) preferAtHand(ctx, ownedRequest, ob, monster);
            // The ceiling: every obtainable item, no quest/level gating -
            // but computed at the player's own levels, so the comparison
            // percentage isolates the GEAR gap.
            // ALL_STANDARD ignores ownership for eligibility, but the
            // candidate dedupe prefers OWNED analogs on stat ties - so
            // the game-best card shows YOUR god d'hide coif, not an
            // arbitrary god's, and the BiS border matches by id.
            OptimizationRequest gr = gameRequestFor(ctx, monster, style, gameLevels, tool);
            List<DpsResult> gb = sideBest(true, ctx, gr, tool);
            // GWD protection (ISS-01M5G9KPDQWBR014Z10F79B29T): the route's
            // gods worn when free, the rest carried - and carried protection
            // takes its Inventory slots before the spec weapon does.
            List<String> gods = tool != null ? List.of() : GwdProtection.godsFor(monster, ctx.gwdAncient);
            List<GearItem> protection = protect(ctx, ownedRequest, ob, gods, monster, ctx.mode != MAX_DPS);
            List<GearItem> gameProtection = protect(ctx, gr, gb, gods, monster, false);
            // The fight's swaps (ISS-01M5VCXYGF51P5NJCDNC1QJZD1): Cerberus' spectral spirit shield for the
            // summoned souls - carried before the spec weapon takes a slot.
            Map<Integer, String> swapNotes = new HashMap<>(), gameSwapNotes = new HashMap<>();
            List<GearItem> swaps = tool != null ? new ArrayList<>() : fightSwaps(ctx, ownedRequest, monster, ob, true, swapNotes);
            List<GearItem> gs2 = tool != null ? new ArrayList<>() : fightSwaps(ctx, gr, monster, gb, false, gameSwapNotes);
            int ss = ctx.maxSwaps - protection.size() - swaps.size();
            int gss = ctx.maxSwaps - gameProtection.size() - gs2.size();
            // Bench 0 = strictly one worn set: no spec swap is carried.
            Set<Integer> specPin = restrictSpec(ctx, style, monster);
            SpecPick spec = ownedSide && ss >= 1 && ctx.p.specWeapon
                ? seat(arbitrateLightbearer(ctx.dataset, ownedRequest, ob, ctx.effectiveOwned, specPin), ss) : null;
            SpecPick gameSpec = gss >= 1 && ctx.p.specWeapon
                ? seat(arbitrateLightbearer(ctx.dataset, gr, gb, null, specPin), gss) : null;
            // The defensive story of the shown set: what the boss does back
            // to you, at your REAL levels under the style's prayer (protection up).
            Result incoming = incomingFor(monster, ob.isEmpty() ? null : ob.get(0).loadout, ctx, style);
            Result gameIncoming = incomingFor(monster, gb.isEmpty() ? null : gb.get(0).loadout, ctx, style);
            List<GearItem> bench = specBenchOf(spec, ob);
            bench.addAll(protection);
            bench.addAll(swaps);
            // The BiS side's inventory mirrors it (field fix 2026-07-18:
            // a single mob's BiS view showed no carried spec).
            List<GearItem> gameBench = specBenchOf(gameSpec, gb);
            gameBench.addAll(gameProtection);
            gameBench.addAll(gs2);
            StyleResult result = verified(new StyleResult( ob, gb.isEmpty() ? null : gb.get(0), spec, gameSpec,
                boostLabel, gameBoostLabel, incoming, gameIncoming, bench, gameBench, true, true), ctx.dataset, ownedRequest, gr);
            result.modeTrade = modeTrade;
            result.protection = protection;
            result.gameProtection = gameProtection;
            result.swapNotes.putAll(swapNotes);
            result.gameSwapNotes.putAll(gameSwapNotes);
            if (ladders != null) {
                if (spec != null && !ob.isEmpty()) {
                    try {
                        ringLadderWithSpec(ctx, ownedRequest, ob.get(0), spec, monster, specPin, ladders);
                    } catch (RuntimeException ex) {
                        log.warn("ring strip failed for {} {}", monster.name, style, ex);
                    }
                }
                result.ladder = ladders;
                if (spec != null) {
                    try {
                        result.specLadder = specLadderFor(ctx, ownedRequest, ob.get(0), spec.weapon);
                    } catch (RuntimeException ex) {
                        log.warn("spec strip failed for {} {}", monster.name, style, ex);
                    }
                }
            }
            return result;
            // Store per style as computed - even a superseded job donates
            // the styles it finished.
    }

    // The query-wide cache key for one monster (per-style state layered on
    // top by styleKey). Centralised so the fast path and computeAllStyles
    // can never drift into split/colliding cache buckets.
    static String baseKeyFor(MonsterStats monster, ComputeContext ctx) {
        // The invocation level scales the monster's defence but not its id
        // (found 2026-08-09: optimizeKey carried it, this key did not, so
        // flipping the Invocation chip served the previous level's cache).
        // The VERSION is the same lesson (found 2026-08-20): a boss's
        // versions share one id, so Far/Nearby/Roaring collided.
        return monster.id + "|" + monster.version + "|" + monster.getToaInvocationLevel() + "|" + ctxKey(ctx)
            + "|" + dreamsFor(ctx, monster).hashCode() + "|" + ctx.maxSwaps
            + "|" + ctx.specByMob.getOrDefault(monster.profileId(), 0) + "|" + ctx.mode.name() + "|" + ctx.gwdAncient;
    }

    Map<CombatStyle, StyleResult> computeAllStyles( MonsterStats monster, ComputeContext ctx, long ticket) {
        ctx.ticket = ticket;
        String bk = baseKeyFor(monster, ctx);
        // The styles' owned and BiS optimizes side by side on the pool, a
        // roster's pass A for one mob; the cards below read them back from
        // the optimize cache (a style already on the card cache is a hit).
        if (passA(List.of(monster), ctx, ticket) == null) return null;
            Map<CombatStyle, StyleResult> results = new EnumMap<>(CombatStyle.class);
            for (CombatStyle style : concreteValues()) {
                if (requestSeq.get() != ticket) return null; // superseded mid-flight - abandon between styles
                // Styles cache independently: a pin or per-set exclusion on
                // one style leaves the other two answers standing.
                String styleKey = styleKey(bk, style, ctx, monster);
                StyleResult cs;
                synchronized (cache) { cs = cache.get(styleKey); }
                if (cs != null) {
                    results.put(style, cs);
                    continue;
                }
                // DETECT assumes the best boost the player OWNS (drink what
                // you bring), never below what is already live - unless the
                // RAID supplies its own (CoX overload+, ToA salts). A picked
                // tier beats both (Andrew 2026-09-02: the picker owns it).
                StyleResult styleResult = styleResultFor(monster, ctx, style, null, true);
                synchronized (cache) { cache.put(styleKey, styleResult); }
                results.put(style, styleResult);
            }
        if (ctx.p.multiView && MultiMobs.isMultiAble(monster) && !results.isEmpty()) {
            // The Multi card (Andrew 2026-10-19): the three multi tools, each a
            // per-target result with the tool pinned, cached beside the styles.
            Map<String, StyleResult> tools = new LinkedHashMap<>();
            for (MultiTool tool : MultiTool.values()) {
                String key = styleKey(bk, tool.style, ctx, monster) + "|" + tool.key;
                StyleResult row;
                synchronized (cache) { row = cache.get(key); }
                if (row == null) {
                    row = multiToolResult(monster, ctx, tool);
                    if (row == null) continue;
                    synchronized (cache) { cache.put(key, row); }
                }
                tools.put(tool.key, row);
            }
            results.values().iterator().next().multiTools = tools;
        }
        return results;
    }

    // Sweep defense weights over the max-DPS set to trace the offence/
    // defence frontier, pick the mode's point, swap it into ownedBest[0],
    // and say what it cost. Only where the damage-taken model covers the
    // monster (full or curated) - a partial number would steer the pick by
    // the attacks it leaves out. A group's sweep task passes its own
    // optimizer (the shared one's calculator is stateful).
    ModeTrade applyMode(ComputeContext ctx, OptimizationRequest ownedRequest,
        List<DpsResult> ob, MonsterStats monster, long ticket, LoadoutOptimizer opt) {
        OptimizeMode mode = ctx.mode;
        DpsResult md2 = ob.get(0);
        Result base = incomingFor(monster, md2.loadout, ctx, ownedRequest.style);
        if (IncomingDpsCalculator.NONE.equals(base.coverage)) return new ModeTrade(mode, 0, 0, "no damage data");
        if (IncomingDpsCalculator.PARTIAL.equals(base.coverage)) return new ModeTrade(mode, 0, 0, "partial damage data");
        double d0 = md2.dps;
        double i0 = base.totalDps;
        if (d0 <= 0 || i0 <= 0.05) return new ModeTrade(mode, 0, 0, "takes ~0 damage");
        List<DpsResult> frontier = new ArrayList<>();
        frontier.add(md2);
        // Candidate pools depend on weight > 0, not its size: build once.
        CandidatePools pools = null;
        for (double alpha : SWEEP_ALPHAS) {
            if (requestSeq.get() != ticket) {
                return null; // superseded mid-sweep
            }
            OptimizationRequest weighted = ownedRequest.withDefenseWeight(alpha * d0 / i0);
            if (pools == null) pools = opt.preparePools(ctx.dataset, weighted);
            List<DpsResult> out = opt.optimize(ctx.dataset, weighted, pools);
            if (!out.isEmpty()) {
                polish(opt, ctx.dataset, weighted, out);
                frontier.add(out.get(0));
            }
        }
        // Scored as rungs: the mode's own ranking (compareRungs - Tanky the
        // least DTPS, Balanced the least DTPS/DPS, ties to the higher DPS).
        DpsResult picked = md2;
        var pr = new Rung(null, d0, i0, false, null);
        for (DpsResult candidate : frontier) {
            var rung = rungOf(ctx, monster, ownedRequest.style, candidate);
            if (compareRungs(mode, rung, pr) > 0) {
                picked = candidate;
                pr = rung;
            }
        }
        if (picked == md2) return new ModeTrade(mode, 0, 0, NO_SAFER);
        ob.set(0, picked);
        return new ModeTrade(mode, (int) round((1 - picked.dps / d0) * 100), (int) round((1 - pr.dtps / i0) * 100), null);
    }

    // applyMode's verdict when the sweep finds nothing safer. The data
    // verdicts before it also stop a group member's re-wear; this one does not.
    static final String NO_SAFER = "no safer set";

    // A set scored for the mode's ranking: its DPS and its damage taken.
    static Rung rungOf(ComputeContext ctx, MonsterStats mob, CombatStyle style, DpsResult r) {
        return new Rung(null, r.net, incomingFor(mob, r.loadout, ctx, style).totalDps, false, null);
    }

    // Every worn slot's ladder for the shown Yours set: each swap scored by
    // the active mode (Andrew 2026-10-21: "same objective as the active
    // mode"), two steps up from any item, two down from owned ones.
    Map<GearSlot, Ladder> ladderFor(ComputeContext ctx, OptimizationRequest request, DpsResult shown, MonsterStats monster) {
        Map<GearSlot, Ladder> ladders = new EnumMap<>(GearSlot.class);
        boolean scoresDamage = ctx.mode != MAX_DPS;
        double baseDtps = incomingFor(monster, shown.loadout, ctx, request.style).totalDps;
        Set<Integer> dreams = dreamsFor(ctx, monster);
        for (GearSlot slot : GearSlot.values()) {
            if (shown.loadout.get(slot) == null) continue;
            var base = new Rung(shown.loadout.get(slot), shown.net, baseDtps, true, null);
            List<Rung> candidates = new ArrayList<>();
            boolean weapon = slot == WEAPON;
            for (DpsResult swap : optimizer.slotSwaps(ctx.dataset, request, shown, slot)) {
                GearItem item = swap.loadout.get(slot);
                double dtps = scoresDamage ? incomingFor(monster, swap.loadout, ctx, request.style).totalDps : baseDtps;
                var rung = new Rung(item, swap.net, dtps, ctx.effectiveOwned.owns(item.id) || dreams.contains(item.id),
                    weapon ? brought(swap.loadout, shown.loadout, AMMO) : null);
                rung.offhand = weapon ? brought(swap.loadout, shown.loadout, SHIELD) : null;
                rung.set = swap;
                candidates.add(rung);
            }
            if (weapon) candidates = foldStaffFamilies(candidates, base.item);
            Ladder ladder = rankSteps(ctx.mode, request.style, base, candidates, shown.net, baseDtps);
            if (ladder != null) {
                if (weapon) {
                    ladder.all = allRanked(ctx.mode, request.style, candidates);
                    splitByKind(ladder, ctx.mode, request.style, base, candidates, shown.net, baseDtps);
                }
                ladders.put(slot, ladder);
            }
        }
        return ladders;
    }

    // The ring slot's ladder priced WITH the spec (ISS-01M65RVX7GAEXPRYHWW529ZQ19,
    // Andrew 2026-10-30: "when i sim lightbearer i'm not seeing how it compares
    // with my ring swaps"): each step's dps is its set plus what the spec adds
    // with that ring on, less what it adds now, so the stat-less Lightbearer
    // (doubled regen) stands against the dps rings and a worn Lightbearer's
    // rivals show what they would cost. Each step hovers "with the spec".
    void ringLadderWithSpec(ComputeContext ctx, OptimizationRequest request, DpsResult shown, SpecPick spec,
        MonsterStats monster, Set<Integer> rt, Map<GearSlot, Ladder> ladders) {
        if (request.pinnedFor(RING) != null || shown.loadout.get(RING) == null) return;
        boolean scoresDamage = ctx.mode != MAX_DPS;
        double baseDtps = incomingFor(monster, shown.loadout, ctx, request.style).totalDps;
        Set<Integer> dreams = dreamsFor(ctx, monster);
        var base = new Rung(shown.loadout.get(RING), shown.net, baseDtps, true, null);
        List<DpsResult> steps = new ArrayList<>(optimizer.slotSwaps(ctx.dataset, request, shown, RING));
        Loadout lb = lightbearerSwap(ctx.dataset, shown.loadout, singletonList(request), ctx.effectiveOwned);
        DpsResult lbSet = lb == null ? null : new DpsCalculator().calculate(request, lb);
        if (lbSet != null) steps.add(lbSet);
        List<Rung> candidates = new ArrayList<>();
        for (DpsResult swap : steps) {
            SpecPick sp = bestSpec(ctx.dataset, request, swap, ctx.effectiveOwned, rt, rt != null);
            GearItem item = swap.loadout.get(RING);
            double dtps = scoresDamage ? incomingFor(monster, swap.loadout, ctx, request.style).totalDps : baseDtps;
            var rung = new Rung(item, swap.net + (sp == null ? 0 : sp.dpsAdded) - spec.dpsAdded, dtps,
                ctx.effectiveOwned.owns(item.id) || dreams.contains(item.id), null);
            rung.set = swap;
            candidates.add(rung);
        }
        Ladder ladder = rankSteps(ctx.mode, request.style, base, candidates, shown.net, baseDtps);
        if (ladder != null) {
            ladder.spec = true;
            ladders.put(RING, ladder);
        }
    }

    // The solo card's ladders, the set first made to wear its own owned
    // upgrades (ISS-01M5PKSQBAP823XNC13QBW8830, Andrew 2026-10-24: "it's still
    // doing the thing for whisperer where it things a mystic smoke battlestaff
    // is an upgrade. so if it is SIM IT AS THE MAIN SET"): while a step the
    // strip rates above the worn item under the mode is yours - owned or
    // simmed - the shown set becomes that step's set and the ladders are
    // rebuilt from it, a few rounds at most.
    Map<GearSlot, Ladder> adoptOwnedSteps(ComputeContext ctx, OptimizationRequest request,
        List<DpsResult> ob, MonsterStats monster) {
        Map<GearSlot, Ladder> ladders = ladderFor(ctx, request, ob.get(0), monster);
        // An adopted step obeys the Wilderness risk cap (field report
        // 2026-10-26: a 75k cap showed a 14.3m set). A task's set rules are
        // kept already: slotSwaps offers no step that breaks them.
        RiskGuard risk = RiskGuard.of(ctx.dataset, request);
        for (int round = 0; round < 3; round++) {
            Rung best = null;
            for (var entry : ladders.entrySet()) {
                Ladder ladder = entry.getValue();
                GearItem pinned = ob.get(0).loadout.get(entry.getKey());
                if (pinned != null && request.isPinned(pinned.id)) {
                    continue; // the player's own word for the slot: the strip may rate it, never replace it
                }
                var worn = new Rung(null, ladder.baseDps, ladder.baseDtps, true, null);
                for (Rung rung : ladder.up) {
                    if (rung.owned && rung.set != null && compareRungs(ctx.mode, rung, worn) > 0
                        && (risk == null || !risk.vetoes(rung.set.loadout, null))
                        && (best == null || compareRungs(ctx.mode, rung, best) > 0)) {
                        best = rung;
                    }
                }
            }
            if (best == null) return ladders;
            ob.set(0, best.set);
            ladders = ladderFor(ctx, request, best.set, monster);
        }
        return ladders;
    }

    // A worn item kept only in storage gives way to a same-slot, same-stats
    // twin at hand (ISS-01M5ZS534AA325AZ0DW97B5G33, Andrew 2026-10-27: "guthix
    // d'hide boots are in my costume room and sara d'hide boots are in my
    // inventory. since they ahve the same stats and the god item attribution
    // is not relevant") - never where the god is the point (a GWD route), and
    // only when the swap passes the request's own rules at no dps cost.
    void preferAtHand(ComputeContext ctx, OptimizationRequest request, List<DpsResult> ob, MonsterStats monster) {
        if (ctx.effectiveOwned.atHand == null || !GwdProtection.godsFor(monster, ctx.gwdAncient).isEmpty()) return;
        DpsResult shown = ob.get(0);
        Loadout loadout = shown.loadout;
        RiskGuard risk = RiskGuard.of(ctx.dataset, request);
        for (GearSlot slot : GearSlot.values()) {
            GearItem worn = loadout.get(slot);
            if (worn == null || !ctx.effectiveOwned.owns(worn.id) || ctx.effectiveOwned.atHand(worn.id)
                || request.isPinned(worn.id)) continue;
            for (GearItem twin : ctx.dataset.getGearItems(slot)) {
                if (twin.id == worn.id || !ctx.effectiveOwned.atHand(twin.id) || !sameStats(twin, worn)
                    || !eligible(ctx.dataset, request, twin)) continue;
                Loadout trial = loadout.with(slot, twin);
                if (risk != null && risk.vetoes(trial, null)) continue;
                if (request.setRules != null && !request.setRules.holds(trial)) continue;
                loadout = trial;
                break;
            }
        }
        if (loadout == shown.loadout) return;
        DpsResult swapped = new DpsCalculator().calculate(request, loadout);
        if (swapped != null && swapped.net >= shown.net - EPS) ob.set(0, swapped.withPurchaseCost(shown.purchaseCost));
    }

    static boolean sameStats(GearItem a, GearItem b) {
        return a.twoHanded == b.twoHanded && a.speed == b.speed && sameBlock(a.offensive, b.offensive)
            && sameBlock(a.defensive, b.defensive) && sameBlock(a.bonuses, b.bonuses);
    }

    static boolean sameBlock(StatBlock a, StatBlock b) {
        return a.stab == b.stab && a.slash == b.slash && a.crush == b.crush
            && a.magic == b.magic && a.ranged == b.ranged && a.strength == b.strength
            && a.rangedStrength == b.rangedStrength && a.magicDamage == b.magicDamage
            && a.prayer == b.prayer;
    }

    // The magic weapon ladder's two kinds (ISS-01M5PKSQBAP823XNC13QBW8830):
    // powered staves and autocast staves, each ranked around the worn weapon
    // by the same rule, sharing the "All weapons" list. Other styles, and the
    // other slots, have one ladder.
    static void splitByKind(Ladder ladder, OptimizeMode mode, CombatStyle style, Rung worn,
        List<Rung> candidates, double baseDps, double baseDtps) {
        if (style != MAGIC) return;
        List<Rung> powered = new ArrayList<>();
        List<Rung> autocast = new ArrayList<>();
        for (Rung rung : candidates) (rung.item.poweredStaff ? powered : autocast).add(rung);
        ladder.powered = kindOf(ladder, rankSteps(mode, style, worn, powered, baseDps, baseDtps));
        ladder.autocast = kindOf(ladder, rankSteps(mode, style, worn, autocast, baseDps, baseDtps));
    }

    static Ladder kindOf(Ladder parent, Ladder kind) {
        if (kind != null) {
            kind.group = parent.group;
            kind.basePerMob = parent.basePerMob;
            kind.all = parent.all;
        }
        return kind;
    }

    static final Pattern BATTLESTAFF = Pattern.compile("(air|water|earth|fire|lava|mud|mist|steam|dust|smoke) battlestaff");
    static final Pattern MYSTIC = Pattern.compile("mystic (air|water|earth|fire|lava|mud|mist|steam|dust|smoke) staff");

    // The twin that stands for a base weapon: the mystic staff for an
    // <element> battlestaff, the (e) for a trident (ISS-01M5PTHS4MAVKFB8C1QXF0KTS1,
    // Andrew 2026-10-24: "combine trident of the seas/swamp and their (e)
    // version - those have the same damage just hold a different number of
    // charges"); null for every other weapon.
    static String twinOf(String name) {
        Matcher staff = BATTLESTAFF.matcher(name);
        if (staff.matches()) return "mystic " + staff.group(1) + " staff";
        return name.equals("trident of the seas") || name.equals("trident of the swamp") ? name + " (e)" : null;
    }

    // The base a twin stands for; null when the name is no twin.
    static String baseOf(String name) {
        Matcher m = MYSTIC.matcher(name);
        if (m.matches()) return m.group(1) + " battlestaff";
        String base = name.endsWith(" (e)") ? name.substring(0, name.length() - 4) : "";
        return twinOf(base) != null ? base : null;
    }

    // A weapon and its twin are one weapon to the strip (Andrew 2026-10-24:
    // "just consider that as the same weapon and if the user has the mystic
    // version use that and if they don't sim the other but don't waste both
    // slots with the results in the ladder"): the worn one stands for the
    // pair, else the OWNED twin, else the base - a twin's sim rung never sits
    // beside its base.
    static List<Rung> foldStaffFamilies(List<Rung> rungs, GearItem worn) {
        Map<String, Rung> byName = new HashMap<>();
        for (Rung rung : rungs) byName.put(rung.item.nameLower, rung);
        String wn = worn == null ? "" : worn.nameLower;
        List<Rung> kept = new ArrayList<>(rungs.size());
        for (Rung rung : rungs) {
            String name = rung.item.nameLower;
            String twin = twinOf(name);
            String base = baseOf(name);
            Rung tr = twin == null ? null : byName.get(twin);
            boolean drop = twin != null ? wn.equals(twin) || tr != null && tr.owned
                : base != null && !rung.owned && (wn.equals(base) || byName.containsKey(base));
            if (!drop) kept.add(rung);
        }
        return kept;
    }

    // The weight a style's shared kit gives each member
    // (ISS-01M5PKSQBAP823XNC13QBW8830, Andrew 2026-10-24: "it is missing
    // trident of the seas in this calculation which is even better because it
    // allows the whole set to use arceuss"): its kit weight where the style
    // is how the member is fought - the side's best set's style, or one within
    // 5% of it - and a thousandth of it where it is not, so the Warden's Core
    // you melee has next to no say in the magic kit, yet a member fought
    // another way still gets a valid set in this style (its pinned weapon is
    // still worth carrying, a kit nobody fights with still answers).
    static final double STYLE_BAND = 0.95;
    static final double OTHER_STYLE_SHARE = 0.001;

    static double[] styleUseWeights(List<MonsterStats> mobs, CombatStyle style, Map<CombatStyle, List<List<DpsResult>>> bbs) {
        int n = mobs.size();
        double[] weights = new double[n];
        for (int j = 0; j < n; j++) {
            double own = dpsOf(bbs.get(style), j);
            double top = 0;
            for (List<List<DpsResult>> bests : bbs.values()) top = max(top, dpsOf(bests, j));
            boolean fought = own > 0 && own >= STYLE_BAND * top;
            weights[j] = kitWeight(mobs.get(j)) * (fought ? 1 : OTHER_STYLE_SHARE);
        }
        return weights;
    }

    static double dpsOf(List<List<DpsResult>> bests, int j) {
        return bests == null || j >= bests.size() || emptyBest(bests.get(j)) ? 0 : bests.get(j).get(0).net;
    }

    // Every side's shared-spec options together, best trip value first
    // (ISS-01M5PVHNZDC61B019S782QN0PJ, Andrew 2026-10-24, Royal Titans: "it's
    // recommending dark bow spec on royal titans i feel like a chally spec
    // would be much better"): the trip carries ONE spec weapon, so a primary
    // weighs the meleed titans' chally against the out-of-reach pair's dark
    // bow, not just its own side's list.
    static List<SpecPick[]> tripSpecOptions(Map<CombatStyle, SharedSide> sides, List<MonsterStats> mobs) {
        double[] weights = hpWeights(mobs);
        List<SpecPick[]> all = new ArrayList<>();
        for (SharedSide side : sides.values()) all.addAll(side.specOptions);
        all.sort(Comparator.comparingDouble((SpecPick[] option) -> tripValue(option, weights)).reversed());
        return all;
    }

    // A kill slower than this counts as this long in a trip's time: a member
    // the set cannot damage neither vetoes the set nor drops out.
    static final double KILL_CAP = 600;

    // A member's say in a group's objective (ISS-01M6415DTE84ZZ45C4YD3MZRBJ,
    // Andrew 2026-10-29: the kit ranks by total trip time): minus its weighted
    // kill time, hp / net dps capped at KILL_CAP, so the higher sum still
    // wins. The weight already carries its hp (kitWeight, specWeight), so
    // the term is weight / net: the trip's seconds times the group weight.
    static double worth(double net, double weight, MonsterStats mob) {
        double cap = KILL_CAP / max(1, mob.hitpoints);
        return -weight * (net <= EPS ? cap : min(1 / net, cap));
    }

    // Every member's spec weight: the trip's spec currency - a spec-only
    // member (the Warden's Core) counts in full here and nowhere else.
    static double[] hpWeights(List<MonsterStats> mobs) {
        double[] weights = new double[mobs.size()];
        for (int j = 0; j < weights.length; j++) weights[j] = specWeight(mobs.get(j));
        return weights;
    }

    static final int ALL_STEPS = 24;

    // Every step the strip could show, best first, owned ahead on ties
    // (ISS-01M5PE4TZXTGJAQA1YXNRKPEDW, Andrew 2026-10-24: "it should evaluate
    // all options and show the result of all based on dps").
    static List<Rung> allRanked(OptimizeMode mode, CombatStyle style, List<Rung> candidates) {
        List<Rung> all = new ArrayList<>(candidates);
        Comparator<Rung> order = (a, b) -> compareRungs(mode, a, b);
        all.sort(order.reversed().thenComparing(Comparator.comparing((Rung r) -> !r.owned)));
        return all.size() > ALL_STEPS ? new ArrayList<>(all.subList(0, ALL_STEPS)) : all;
    }

    // The item a swapped set brings in a slot the worn set does not already
    // fill with it (a weapon step's ammo or offhand); null otherwise.
    static GearItem brought(Loadout swapped, Loadout worn, GearSlot slot) {
        GearItem item = swapped.get(slot);
        GearItem had = worn.get(slot);
        return item != null && (had == null || item.id != had.id) ? item : null;
    }

    // The steps around the worn item: up from any item, down from your own,
    // closest first, five columns by the fill rule. Equal under the mode,
    // the item's own stats decide (ISS-01M5H9KGF4FSAM5DR1XSD9F9RX, Andrew
    // 2026-10-22: Torva tied Bandos on one max hit, and a whole band of
    // boots folded into Boots of the Eye). Null when there is no step.
    static Ladder rankSteps(OptimizeMode mode, CombatStyle style, Rung base, List<Rung> candidates,
        double baseDps, double baseDtps) {
        Comparator<Rung> order = (a, b) -> compareRungs(mode, a, b);
        // No style (the spec ladder): the mode alone ranks the steps.
        Comparator<Rung> stats = style == null ? (a, b) -> 0 : (a, b) -> compareStats(style, a, b);
        Comparator<Rung> full = order.thenComparing(stats);
        List<Rung> above = new ArrayList<>();
        List<Rung> below = new ArrayList<>();
        for (Rung rung : candidates) {
            int vw = full.compare(rung, base);
            if (vw > 0) {
                above.add(rung);
            } else if (vw < 0 && rung.owned) below.add(rung);
        }
        // Closest first. Up keeps one step per result - its best-stats
        // item; down is your own gear, so equal results stand side by
        // side and only stat-identical twins fold (owned, then cheaper).
        Comparator<Rung> tb = Comparator.comparing((Rung r) -> !r.owned) .thenComparingInt(r -> r.item.getPriceOrZero());
        above.sort(order.thenComparing(stats.reversed()).thenComparing(tb));
        below.sort(full.reversed().thenComparing(tb));
        List<Rung> up = closest(above, order);
        List<Rung> down = closest(below, full);
        int[] spots = Ladder.spots(up.size(), down.size());
        if (up.isEmpty() && down.isEmpty()) return null;
        var ladder = new Ladder(mode, baseDps, baseDtps, up.subList(0, spots[0]), down.subList(0, spots[1]));
        ladder.upMore = up.subList(spots[0], up.size());
        ladder.downMore = down.subList(spots[1], down.size());
        return ladder;
    }

    // A group search's swaps (ISS-01M5JAGFEJ5QHXE37B37XM8HJJ, Andrew 2026-10-22: "ladder
    // view is disabled for group searches but i think it should be
    // included" - scored by "The whole group"): each slot of what each member
    // WEARS for the style - the kit-backed set where the kit answers
    // (ISS-01M5QJMTBRNAWX5WT7NRJD8JWC, Andrew 2026-10-24: "melee dps is
    // calcing to 4.44 with nox halberd but ladder is showing 6.06") - swapped
    // for every member, the DPS averaged by the weights that picked the kit
    // (side.weights). The strip's worn item is the one most members wear in
    // the slot. One strip for the group.
    Map<GearSlot, Ladder> groupLadderFor(ComputeContext ctx, SharedSide side, List<MonsterStats> mobs,
        List<Map<CombatStyle, StyleResult>> perMob, CombatStyle style) {
        Map<GearSlot, Ladder> ladders = new EnumMap<>(GearSlot.class);
        if (side == null || side.shared == null) return ladders;
        int n = side.reqs.size();
        double[] weight = new double[n];
        DpsResult[] base = new DpsResult[n];
        double total = 0;
        double baseDps = 0;
        Set<Integer> dreams = new HashSet<>();
        double[] basePerMob = new double[n];
        for (int j = 0; j < n; j++) {
            StyleResult shown = perMob.get(j).get(style);
            base[j] = shown == null || shown.owned.isEmpty() ? null : shown.owned.get(0);
            weight[j] = side.weights[j];
            dreams.addAll(dreamsFor(ctx, mobs.get(j)));
            if (base[j] != null) {
                total += weight[j];
                baseDps += base[j].net * weight[j];
                basePerMob[j] = base[j].net;
            }
        }
        if (total <= 0) return ladders;
        baseDps /= total;
        for (GearSlot slot : GearSlot.values()) {
            GearItem worn = mostWorn(base, slot);
            if (worn == null) continue;
            Map<Integer, double[]> sums = new LinkedHashMap<>();
            Map<Integer, double[]> pmd = new HashMap<>();
            Map<Integer, GearItem> items = new HashMap<>();
            List<Set<Integer>> takeable = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                if (base[j] == null) continue;
                Set<Integer> offered = new HashSet<>();
                for (DpsResult swap : optimizer.slotSwaps(ctx.dataset, side.reqs.get(j), base[j], slot)) {
                    GearItem item = swap.loadout.get(slot);
                    offered.add(item.id);
                    if (item.id == worn.id) {
                        continue; // the strip's worn item: a member wearing another reads its own card
                    }
                    items.putIfAbsent(item.id, item);
                    sums.computeIfAbsent(item.id, k -> new double[1])[0] += swap.net * weight[j];
                    // Each member's own number for the step: its card shows
                    // that, the group figure ranks (ISS-01M5PE4TZXTGJAQA1YXNRKPEDW).
                    pmd.computeIfAbsent(item.id, k -> new double[n])[j] = swap.net;
                }
                // slotSwaps skips a member's own item: wearing it is no refusal.
                GearItem own = base[j].loadout.get(slot);
                if (own != null) offered.add(own.id);
                if (side.reqs.get(j).setRules != null) takeable.add(offered);
            }
            // A group step is one swap for everyone: under a task's rules, one
            // a member cannot take is no step, not a 0 averaged in on its card.
            for (Set<Integer> offered : takeable) sums.keySet().retainAll(offered);
            List<Rung> candidates = new ArrayList<>();
            for (var entry : sums.entrySet()) {
                int id = entry.getKey();
                var rung = new Rung(items.get(id), entry.getValue()[0] / total, 0,
                    ctx.effectiveOwned.owns(id) || dreams.contains(id), null);
                rung.perMob = pmd.get(id);
                candidates.add(rung);
            }
            var wr2 = new Rung(worn, baseDps, 0, true, null);
            wr2.perMob = basePerMob;
            if (slot == WEAPON) candidates = foldStaffFamilies(candidates, worn);
            Ladder ladder = rankSteps(MAX_DPS, style, wr2, candidates, baseDps, 0);
            if (ladder != null) {
                ladder.group = true;
                ladder.basePerMob = basePerMob;
                if (slot == WEAPON) {
                    ladder.all = allRanked(MAX_DPS, style, candidates);
                    splitByKind(ladder, MAX_DPS, style, wr2, candidates, baseDps, 0);
                }
                ladders.put(slot, ladder);
            }
        }
        return ladders;
    }

    // The item most members wear in a slot; null when none does.
    static GearItem mostWorn(DpsResult[] base, GearSlot slot) {
        Map<Integer, int[]> counts = new HashMap<>();
        GearItem best = null;
        int most = 0;
        for (DpsResult result : base) {
            GearItem item = result == null ? null : result.loadout.get(slot);
            if (item != null && ++counts.computeIfAbsent(item.id, k -> new int[1])[0] > most) {
                most = counts.get(item.id)[0];
                best = item;
            }
        }
        return best;
    }

    // The carried spec's swaps (ISS-01M5HCS725N7XVVBC7CMK6Y43A, Andrew
    // 2026-10-22: "not seeing the ladder view for the spec weapon slot"):
    // every spec weapon scored alone by the card's own pick - the DPS it
    // adds over just attacking - any item above the carried one, your own
    // below, five columns by the slots' fill rule.
    Ladder specLadderFor(ComputeContext ctx, OptimizationRequest request, DpsResult main, GearItem carried) {
        SpecPick worn = bestSpec(ctx.dataset, request, main, null, Set.of(carried.id), true);
        if (worn == null) return null;
        Set<Integer> dreams = dreamsFor(ctx, request.monster);
        List<Rung> candidates = new ArrayList<>();
        for (GearItem item : ctx.dataset.getGearItems(WEAPON)) {
            if (item.id == carried.id || SpecialAttack.match(item) == null
                || !eligible(ctx.dataset, request, item)) {
                continue;
            }
            SpecPick pick = bestSpec(ctx.dataset, request, main, null, Set.of(item.id), true);
            if (pick != null) {
                candidates.add(new Rung(item, pick.dpsAdded, 0,
                    ctx.effectiveOwned.owns(item.id) || dreams.contains(item.id), null));
            }
        }
        return rankSteps(MAX_DPS, null, new Rung(carried, worn.dpsAdded, 0, true, null), candidates, worn.dpsAdded, 0);
    }

    // The first SPOTS + MORE distinct steps of an already-sorted list
    // (distinct under {@code order}: a step equal to the previous one folds
    // into it).
    static List<Rung> closest(List<Rung> sorted, Comparator<Rung> order) {
        List<Rung> kept = new ArrayList<>();
        for (Rung rung : sorted) {
            if (kept.size() == Ladder.SPOTS + Ladder.MORE) break;
            if (kept.isEmpty() || order.compare(rung, kept.get(kept.size() - 1)) != 0) kept.add(rung);
        }
        return kept;
    }

    // Positive when a's item has the better stats for the style: its rough
    // offensive score, then its total defence.
    static int compareStats(CombatStyle style, Rung a, Rung b) {
        double sa = a.item.roughScore(style);
        double sb = b.item.roughScore(style);
        if (abs(sa - sb) > EPS) return sa > sb ? 1 : -1;
        return Integer.compare(defenceOf(a.item), defenceOf(b.item));
    }

    static int defenceOf(GearItem item) {
        StatBlock d = item.defensive;
        return d.stab + d.slash + d.crush + d.magic + d.ranged;
    }

    // Positive when a is better under the mode: Max DPS by DPS; Tanky by
    // least damage taken; Balanced by least DTPS/DPS - DPS breaks ties.
    static int compareRungs(OptimizeMode mode, Rung a, Rung b) {
        if (mode == TANKY && abs(a.dtps - b.dtps) > EPS) return a.dtps < b.dtps ? 1 : -1;
        if (mode == BALANCED) {
            double ra = a.dps <= 0 ? Double.POSITIVE_INFINITY : a.dtps / a.dps;
            double rb = b.dps <= 0 ? Double.POSITIVE_INFINITY : b.dtps / b.dps;
            if (abs(ra - rb) > 1e-12 && !(Double.isInfinite(ra) && Double.isInfinite(rb))) return ra < rb ? 1 : -1;
        }
        return abs(a.dps - b.dps) <= EPS ? 0 : a.dps > b.dps ? 1 : -1;
    }

    // One side's GWD protection: wear the free ones into best[0], return
    // what must be carried (empty when the monster is not on a GWD route).
    // Under a defensive mode "free" also means no more damage taken - a
    // DPS-neutral god item must not undo the set Tanky/Balanced picked.
    List<GearItem> protect(ComputeContext ctx, OptimizationRequest request,
        List<DpsResult> best, List<String> gods, MonsterStats monster, boolean defensive) {
        if (gods.isEmpty() || best.isEmpty()) return new ArrayList<>();
        DpsResult worn = optimizer.wearGodItems(ctx.dataset, request, best.get(0), gods);
        if (!defensive || incomingFor(monster, worn.loadout, ctx, request.style).totalDps
            <= incomingFor(monster, best.get(0).loadout, ctx, request.style).totalDps + EPS) {
            best.set(0, worn);
        }
        return new ArrayList<>(optimizer.carriedGodItems(ctx.dataset, request, best.get(0).loadout, gods));
    }

    // What the boss does back to you in this worn set, at REAL levels under
    // the style's prayer (protection up) - null loadout answers null. Twin
    // owned/game call sites in both compute paths share it.
    static Result incomingFor(MonsterStats mob, Loadout worn, ComputeContext ctx, CombatStyle style) {
        int[] dm = defending(style, prayerLabelFor(ctx, style, ctx.real, ctx.unlocks), ctx.real.defence, ctx.real.magic);
        return worn == null ? null : IncomingDpsCalculator.calculate(mob, worn, dm[0], dm[1], ctx.p.antifireTier);
    }

    // The fight's swap gear beside the set: on Yours the first one you own or sim,
    // on the ceiling the first; never one the set already wears.
    static List<GearItem> fightSwaps(ComputeContext ctx, OptimizationRequest request, MonsterStats mob, List<DpsResult> best,
        boolean owned, Map<Integer, String> notes) {
        List<GearItem> out = new ArrayList<>();
        if (best.isEmpty()) return out;
        for (int id : swapIds(mob)) {
            GearItem item = ctx.dataset.getGear(id);
            GearItem worn = item == null ? null : best.get(0).loadout.get(item.slot);
            if (item != null && out.isEmpty() && (!owned || ctx.effectiveOwned.owns(id) || dreamsFor(ctx, mob).contains(id)) && (worn == null || worn.id != id)) out.add(item);
        }
        for (RequiredGear.Carried rule : RequiredGear.swaps(mob)) {
            if (!"fastRanged".equals(rule.category)) continue;
            GearItem w = fastRanged(ctx, request, mob, owned);
            GearItem worn = best.get(0).loadout.getWeapon();
            if (w != null && (worn == null || worn.id != w.id) && out.stream().noneMatch(g -> g.id == w.id)) {
                out.add(w);
                notes.put(w.id, rule.note);
            }
        }
        return out;
    }

    // "retrieves the switch's arrows or bolts" on a carried Ava's device.
    static Map<Integer, String> avasNotes(List<GearItem> bench) {
        Map<Integer, String> notes = new HashMap<>();
        for (GearItem g : bench) {
            if (RangedAmmo.avasDevice(g)) notes.put(g.id, "retrieves the ranged switch's arrows or bolts");
        }
        return notes;
    }

    // The best 2-tick-on-rapid ranged weapon (speed 3: darts, knives, the
    // blowpipe) for a fight's adds (ISS-01M65N6WS5N2V9TQC0MJ03NKY8, Elling:
    // Hespori's sprouts) - owned (or simmed) on the Yours side, any in the
    // game for BiS, ranked by its dps against the mob alone.
    static GearItem fastRanged(ComputeContext ctx, OptimizationRequest request, MonsterStats mob, boolean owned) {
        OptimizationRequest rr = request.withStyle(RANGED);
        var calc = new DpsCalculator();
        GearItem best = null;
        double bd = 0;
        for (GearItem w : ctx.dataset.getGearItems(GearSlot.WEAPON)) {
            if (!w.standardGear || w.speed != 3 || !w.isWeaponFor(RANGED) || !rr.getRequirementProfile().canWear(w)) continue;
            if (owned && !ctx.effectiveOwned.owns(w.id) && !dreamsFor(ctx, mob).contains(w.id)) continue;
            DpsResult r = calc.calculate(rr, new Loadout(Map.of(GearSlot.WEAPON, w)));
            if (r != null && r.dps > bd) {
                bd = r.dps;
                best = w;
            }
        }
        return best;
    }

    // One side's bench for the single-mob path: the spec weapon when the
    // shown set does not already wear it.
    static List<GearItem> specBenchOf(SpecPick spec, List<DpsResult> best) {
        List<GearItem> bench = new ArrayList<>();
        if (spec != null && spec.weapon != null && !best.isEmpty() && (best.get(0).loadout.getWeapon() == null
                || best.get(0).loadout.getWeapon().id != spec.weapon.id)) {
            bench.add(spec.weapon);
            if (spec.offhand != null) bench.add(spec.offhand);
        }
        return bench;
    }

    // Gather the query-wide state one compute needs. Shared by the single-
    // mob (bestPerStyle) and roster (bestPerStyleAcross) entry points so the
    // two can never disagree on levels, cache keys, or candidate eligibility.
    ComputeContext buildContext( PlayerLevels rl, PlayerLevels boostedLevels, PrayerUnlocks pu,
        RequirementProfile requirements, OwnedItems owned, int collectionFingerprint,
        ComputeParams p, Map<CombatStyle, Set<Integer>> ebs, Set<Integer> di,
        Map<Integer, Map<CombatStyle, Map<GearSlot, Integer>>> pinsByMob,
        Map<Integer, SpellStats> spellByMob, Map<Integer, Integer> specByMob, Set<Integer> poi) {
        var ctx = new ComputeContext();
        ctx.p = p;
        // Here, not on the single-mob road only: a group runs the modes too
        // (ISS-01M64GNB852CBYVVWE29A9HJK4), its raid book recompute as well.
        ctx.mode = OptimizeMode.valueOf(p.optimizeMode);
        ctx.maxSwaps = max(0, p.maxSwaps);
        ctx.pinsByMob = pinsByMob == null ? Map.of() : pinsByMob;
        ctx.spellByMob = spellByMob == null ? Map.of() : spellByMob;
        ctx.specByMob = specByMob == null ? Map.of() : specByMob;
        ctx.excluded = ebs == null ? Map.of() : ebs;
        ctx.dreams = di == null ? Set.of() : withPairedAmmo(di);
        ctx.protectOnly = poi == null ? Set.of() : poi;
        ctx.lock = p.spellbookLock == null ? "" : p.spellbookLock;
        ctx.real = rl == null ? boostedLevels : rl;
        ctx.boostedLevels = boostedLevels;
        // A free world has no members prayers - F2P caps the book (audit A3.5).
        ctx.unlocks = p.f2pOnly ? F2P : pu == null ? ALL : pu;
        // The budget only matters when risk-constrained; pin it otherwise so
        // flipping the dropdown with the cap off cannot split the cache.
        ctx.riskBudget = p.maxTradeables >= 0 ? p.riskBudgetGp : OptimizationRequest.DEFAULT_RISK_BUDGET_GP;
        ctx.dataset = p.f2pOnly ? f2pView() : data;
        // Owned ornament/locked variants count as their base item.
        ctx.effectiveOwned = new OwnedItems( ctx.dataset.canonicalizeOwned(owned.quantities), owned.bankScanned,
            owned.atHand == null ? null : ctx.dataset.canonicalizeOwned(owned.atHand.stream()
                .collect(Collectors.toMap(id -> id, id -> 1, Integer::sum))).keySet());
        ctx.requirements = requirements;
        ctx.boostPicks = p.boostPicks == null ? Map.of() : p.boostPicks;
        ctx.prayerPicks = p.prayerPicks == null ? Map.of() : p.prayerPicks;
        ctx.collectionFingerprint = collectionFingerprint;
        return ctx;
    }

    // The roster answer: one shared set per style, chosen for the best
    // AVERAGE dps across the mobs (field decision 2026-07-17), with a per-mob
    // display bundle for that set (index-aligned with the mob list).
    @AllArgsConstructor
    public static class RosterResult {
        public final List<MonsterStats> mobs;
        public final List<Map<CombatStyle, StyleResult>> perMob;
        // The one book the whole group computed under ("" = none pinned)
        // and why - the plates and the stand-down read it
        // (ISS-01M5PB4R0MP149NXWNFSEEHVP8).
        public final String groupBook;
        public final String groupBookReason;
    }

    // The group's one spellbook: the lock already set (the player's chip,
    // Cure Me, a fight book) stands; else the book the magic-using
    // autocasters demand - the heaviest HP-weighted magic when they
    // disagree; else Arceuus while thralls or Death Charge are on, so the
    // summons stay up and magic casts from powered staves (Andrew
    // 2026-10-24: "the spellbook has to be one total choice for everything").
    @AllArgsConstructor
    static class GroupBook {
        final String book;
        final String reason;
        // The first pass's picks disagree with the book: run again under it.
        final boolean recompute;
    }

    GroupBook groupBook(List<MonsterStats> mobs, List<Map<CombatStyle, StyleResult>> perMob, ComputeContext ctx) {
        if (!ctx.lock.isEmpty()) return new GroupBook(ctx.lock, "", false);
        // Per demanded book: the mob carrying the most of its magic, and its spell.
        Map<String, String> who = new LinkedHashMap<>();
        Map<String, String> what = new LinkedHashMap<>();
        Map<String, Double> heaviest = new HashMap<>();
        List<Integer> demanding = new ArrayList<>();
        boolean autocast = false;
        // A PINNED spell is the player's own word: its book is never argued
        // down by the summons.
        boolean pd = false;
        for (int j = 0; j < mobs.size(); j++) {
            DpsResult magic = ownedMagic(perMob.get(j));
            String spell = magic == null ? null : magic.getSpellName();
            if (spell == null) {
                continue; // no magic set, or a powered staff: casts on any book
            }
            autocast = true;
            String book = bookOf(ctx.dataset, spell);
            if (book.isEmpty() || !usesMagic(perMob.get(j), magic)) {
                continue; // this mob is fought another way: no say in the book
            }
            demanding.add(j);
            pd |= spellFor(ctx, mobs.get(j)) != null;
            double weight = kitWeight(mobs.get(j)) * magic.net;
            if (weight > heaviest.getOrDefault(book, -1.0)) {
                heaviest.put(book, weight);
                MonsterStats mob = mobs.get(j);
                who.put(book, mob.name + (mob.version.isEmpty() ? "" : " (" + mob.version + ")"));
                what.put(book, spell);
            }
        }
        if (who.isEmpty()) {
            if (!ctx.p.thralls && ctx.p.deathCharge <= 0) return new GroupBook("", "", false);
            return new GroupBook("arceuus", "Arceuus for the group - thralls and Death Charge stay up,"
                + " magic sets cast from powered staves", autocast);
        }
        // Raids run Arceuus whenever they can (ISS-01M5TMC6SK50VPV8713B33SPPM,
        // Andrew 2026-10-25: "all raids should try to use arceuss+thralls until
        // its not possible"): thralls on every card outweigh one card's
        // autocast; only a spell you pinned moves the book.
        if (ctx.p.raid && (ctx.p.thralls || ctx.p.deathCharge > 0) && !pd) {
            return new GroupBook("arceuus", "Arceuus for the raid - thralls and Death Charge stay up on every card,"
                + " magic sets cast from Arceuus", autocast);
        }
        String best = null;
        double bestTotal = -1;
        for (String book : who.keySet()) {
            double total = 0;
            for (int j : demanding) {
                total += kitWeight(mobs.get(j)) * magicDpsUnder(ctx, mobs, j, perMob.get(j), book);
            }
            if (total > bestTotal) {
                bestTotal = total;
                best = book;
            }
        }
        String edge = "";
        if (ctx.p.thralls || ctx.p.deathCharge > 0) {
            // Arceuus is favoured, not forced (Andrew 2026-10-24: "favor a
            // powered staff but if a non-powered staff has a supreme result it
            // should be the winner"): powered staves on the magic users plus
            // thralls on EVERY member, against the best autocast book.
            String summons = ctx.p.thralls ? "thralls" : "Death Charge";
            // Weighed where the magic choice is made (Andrew: "if the player
            // needs to run a standard spellbook to use smoke battlestaff on
            // wardens it should correctly pin the standard spellbook"): each
            // magic user's Arceuus cast - a powered staff or a Grasp - plus the
            // thralls on THAT card, never the other ten cards' thralls paying
            // for a Grasp at half the dps (ISS-01M5PHE6WD81W8542RW71EXSHC).
            double thrall = ctx.p.thralls ? ExtraDps.thrallDps(ctx.real.magic) : 0;
            double a2 = 0;
            for (int j : demanding) a2 += kitWeight(mobs.get(j)) * (arceuusMagicDps(ctx, mobs, j) + thrall);
            String favour = pd ? null : arceuusIfNotBeaten(a2, bestTotal, summons, who.get(best), what.get(best));
            if (favour != null) return new GroupBook("arceuus", favour, autocast);
            edge = String.format(" - Arceuus casts with %s on the magic cards would lose (%.0f vs %.0f weighted dps)",
                summons, a2, bestTotal);
        }
        boolean recompute = false;
        for (Map<CombatStyle, StyleResult> results : perMob) {
            DpsResult magic = ownedMagic(results);
            if (magic != null && magic.getSpellName() != null && !best.equals(bookOf(ctx.dataset, magic.getSpellName()))) recompute = true;
        }
        var others = new StringBuilder();
        for (String book : who.keySet()) {
            if (!book.equals(best)) others.append(others.length() == 0 ? " (it carries more of the group's magic than the " : " or ") .append(book);
        }
        if (others.length() > 0) others.append(" book would)");
        return new GroupBook(best, who.get(best) + " wants " + what.get(best) + " - the group runs the " + best
            + " book" + others + edge + "; thralls and Death Charge stand down", recompute);
    }

    // The Arceuus side of the group's book, or null when the autocast book
    // is strictly ahead: each magic card's Arceuus cast plus its thralls,
    // against its autocast on the other book, HP-weighted over the magic
    // cards. Ties and near-ties stay Arceuus (Andrew 2026-10-24: "favor a
    // powered staff but if a non-powered staff has a supreme result it
    // should be the winner").
    static String arceuusIfNotBeaten(double at2, double at3, String summons, String who, String what) {
        if (at2 + EPS < at3) return null;
        return String.format("Arceuus for the group: Arceuus casts with %s on the magic cards outweigh %s's %s"
            + " (%.0f vs %.0f weighted dps)", summons, who, what, at2, at3);
    }

    // The mob's best magic set under the Arceuus lock - a powered staff or
    // an Arceuus autocast (the Grasp line); 0 when it owns nothing that
    // casts there. A pinned spell is dropped: the question is what the
    // player would cast on Arceuus.
    double arceuusMagicDps(ComputeContext ctx, List<MonsterStats> mobs, int j) {
        StylePlan plan = stylePlan(ctx, MAGIC, suppliedAcross(mobs));
        OptimizationRequest request = ownedRequestFor(ctx, mobs.get(j), MAGIC, plan.levels, null)
            .withSpell(null).withSpellbookLock("arceuus");
        List<DpsResult> out = optimizer.optimize(ctx.dataset, request, optimizer.preparePools(ctx.dataset, request));
        return out.isEmpty() ? 0 : out.get(0).net;
    }

    // The mob's shown owned magic set, or null.
    static DpsResult ownedMagic(Map<CombatStyle, StyleResult> results) {
        StyleResult magic = results.get(MAGIC);
        return magic == null || magic.owned.isEmpty() ? null : magic.owned.get(0);
    }

    // Magic is how this mob gets fought: nothing else owned beats it.
    static boolean usesMagic(Map<CombatStyle, StyleResult> results, DpsResult magic) {
        for (var entry : results.entrySet()) {
            if (entry.getKey() != MAGIC && !entry.getValue().owned.isEmpty() && entry.getValue().owned.get(0).net > magic.net) return false;
        }
        return true;
    }

    static String bookOf(LoadoutData dataset, String spell) {
        for (SpellStats s : dataset.spells) {
            if (s.name.equals(spell)) return Names.lower(s.spellbook);
        }
        return "";
    }

    // This mob's first-pass magic set re-cast under `book`: its own dps on
    // its own book, 0 when its staff can cast nothing from the other one.
    double magicDpsUnder(ComputeContext ctx, List<MonsterStats> mobs, int j, Map<CombatStyle, StyleResult> results, String book) {
        DpsResult magic = ownedMagic(results);
        if (book.equals(bookOf(ctx.dataset, magic.getSpellName()))) return magic.net;
        StylePlan plan = stylePlan(ctx, MAGIC, suppliedAcross(mobs));
        OptimizationRequest request = ownedRequestFor(ctx, mobs.get(j), MAGIC, plan.levels, null) .withSpellbookLock(book);
        DpsResult cast = optimizer.bestSpellFor(ctx.dataset, request, magic.loadout, new DpsCalculator());
        return cast == null ? 0 : cast.net;
    }

    // The raid's own boost when EVERY mob is inside the same raid (CoX
    // overload+, ToA salts), else null.
    static BoostProfile suppliedAcross(List<MonsterStats> mobs) {
        BoostProfile supplied = suppliedBoost(mobs.get(0));
        for (MonsterStats mob : mobs) {
            if (suppliedBoost(mob) != supplied) return null;
        }
        return supplied;
    }

    // Greedy bench selection: candidates are the per-mob free-best items
    // that differ from the shared base; each round carries the item with
    // the best HP-weighted marginal gain (every mob wearing its best
    // combination of base + carried) until the bench is full or nothing
    // gains. Small scale by construction: candidates <= mobs x slots.
    // True when a per-mob optimize answer has nothing usable in front.
    static boolean emptyBest(List<DpsResult> best) {
        return best == null || best.isEmpty() || best.get(0) == null;
    }

    // The same-style swap candidate pool: every per-mob free-best item
    // that differs from the base in its slot.
    static Collection<GearItem> swapCandidates(Loadout base, List<List<DpsResult>> fb2) {
        LinkedHashMap<Integer, GearItem> candidates = new LinkedHashMap<>();
        for (List<DpsResult> best : fb2) {
            if (emptyBest(best)) continue;
            for (GearItem item : best.get(0).loadout.items()) {
                GearItem bi = base.get(item.slot);
                if (bi == null || bi.id != item.id) candidates.putIfAbsent(item.id, item);
            }
        }
        return candidates.values();
    }

    List<GearItem> chooseSwaps(DpsCalculator calc, Loadout base,
        List<OptimizationRequest> reqs, double[] weights, List<List<DpsResult>> fb2, int bfs) {
        List<GearItem> carried = new ArrayList<>();
        if (bfs <= 0) return carried;
        Collection<GearItem> candidates = swapCandidates(base, fb2);
        double current = hpWeightedTotal(calc, base, carried, reqs, weights);
        while (carried.size() < bfs) {
            GearItem bp = null;
            double bestTotal = current;
            for (GearItem candidate : candidates) {
                if (carried.contains(candidate)) continue;
                carried.add(candidate);
                double total = hpWeightedTotal(calc, base, carried, reqs, weights);
                carried.remove(carried.size() - 1);
                if (total > bestTotal + EPS) {
                    bestTotal = total;
                    bp = candidate;
                }
            }
            if (bp == null) {
                break; // nothing left that helps
            }
            carried.add(bp);
            current = bestTotal;
        }
        return carried;
    }

    double hpWeightedTotal(DpsCalculator calc, Loadout base,
        List<GearItem> carried, List<OptimizationRequest> reqs, double[] weights) {
        double sum = 0;
        for (int j = 0; j < reqs.size(); j++) {
            DpsResult worn = calcRespecting(calc, reqs.get(j), bestWorn(calc, reqs.get(j), base, carried));
            sum += worth(worn == null ? 0 : worn.net, weights[j], reqs.get(j).monster);
        }
        return sum;
    }

    // calculate(), refusing a loadout that wears an item EXCLUDED for
    // this mob's request (field decision 2026-07-17: exclusions are
    // per-mob - the kit may carry the scythe for the roster, but the
    // excluding mob's shown answer never wears it).
    // The true upgrade cost of a set: the summed price of pieces the
    // player does NOT own (field bug 2026-08-14: kit-backed cards showed
    // the WHOLE set's value - calculate() defaults purchaseCost to
    // loadout.cost, and only the beam path was overwriting it).
    static int unownedValue(Loadout loadout, OwnedItems owned) {
        if (loadout == null) return 0;
        long total = 0;
        for (GearItem item : loadout.items()) {
            if (owned == null || !owned.owns(item.id)) total += item.getPriceOrZero();
        }
        return (int) min(Integer.MAX_VALUE, total);
    }

    DpsResult calcRespecting(DpsCalculator calc, OptimizationRequest req, Loadout loadout) {
        if (loadout != null) {
            Set<Integer> excluded = req.excludedItems;
            // A per-mob SIM is pretend-owned for THAT mob only (field bug
            // 2026-07-18: a rapier simmed vs one group member leaked into
            // every mob's shared answer) - on the owned side, a loadout may
            // only wear what this request's bank or dreams grant.
            boolean ownedOnly = req.candidateMode == CandidateMode.OWNED_ONLY;
            for (GearItem item : loadout.items()) {
                if (excluded != null && excluded.contains(item.id)) return null;
                if (ownedOnly && !req.ownedItems.owns(item.id) && !req.isDream(item.id)) return null;
            }
            // A member's PIN binds its own shown set the same way (ISS-
            // 01M4Y2VJ8DWBBKFR1HP9NR45NG): the shared set and the swap
            // passes are dps-greedy, so without this a second member's
            // pin would never be worn against it.
            for (var pin : req.pinnedItems.entrySet()) {
                GearItem worn = loadout.get(pin.getKey());
                if (worn == null || worn.id != pin.getValue()) return null;
            }
            // So do the set rules and the risk cap: a kit-built set (one style's
            // weapon in another's armour) is a set the search never vetted (field
            // report 2026-10-27: Maggot King melee wore 4 prayer under prayer >= 10).
            if (req.setRules != null && !req.setRules.holds(loadout)) return null;
            RiskGuard risk = RiskGuard.of(data, req);
            if (risk != null && risk.vetoes(loadout, null)) return null;
        }
        // An auto-spell magic request scores its best autocast, the pick the
        // single-mob optimizer makes (ISS-01M5K9KFJH7EZ0GMAKNGZ4K2JG).
        // Autocasting weapons only (staves, wands): a melee weapon's hand-cast
        // spell must not make magic look viable and reshape the kit.
        if (loadout != null && req.style == MAGIC && req.isAutoSpell()
            && loadout.getWeapon() != null && (loadout.getWeapon().categoryLower.contains("staff")
                || loadout.getWeapon().categoryLower.contains("wand"))) {
            DpsResult cast = optimizer.bestSpellFor(data, req, loadout, calc);
            // No legal autocast (a melee weapon on a magic card): keep the
            // plain score the roster has always read - a null here reads as
            // "cannot fight this way" and reshapes the whole kit.
            if (cast != null) return cast;
        }
        return calc.calculate(req, loadout);
    }

    // The trip inventory while fighting ONE mob: the roster PLAN (the
    // union of what the mobs actually WEAR in their best answers, plus
    // the spec weapon) minus what is worn right now. Gear no mob wears
    // is not brought at all (field fix 2026-07-17: base-compromise and
    // candidate leftovers read as junk at Inventory 20), and a worn item
    // never duplicates into the view.
    static List<GearItem> inventoryFor(Collection<GearItem> plan, GearItem specCarried, Loadout worn) {
        Set<Integer> wi = worn == null ? Set.of() : worn.ids();
        List<GearItem> inventory = new ArrayList<>();
        Set<Integer> packed = new HashSet<>();
        if (specCarried != null && !wi.contains(specCarried.id) && packed.add(specCarried.id)) inventory.add(specCarried);
        for (GearItem item : plan) {
            if (!wi.contains(item.id) && packed.add(item.id)) inventory.add(item);
        }
        return inventory;
    }

    // A cross-style carry: another style's weapon (+ its ammo when the
    // weapon needs slot ammo), worn INTO the primary base set and rolled
    // under its OWN style's request - "bring a blowpipe in your melee
    // gear". Cost = bench slots consumed; a weapon the bench already
    // carries as the spec weapon costs nothing extra.
    @AllArgsConstructor
    static class SwapBundle {
        final CombatStyle style;
        final List<GearItem> items;
        final int cost;
    }

    // The chosen kit: same-style single swaps + cross-style bundles.
    static class KitAnswer {
        final List<GearItem> singles = new ArrayList<>();
        final List<SwapBundle> bundles = new ArrayList<>();
        double total;
    }

    // The mob's own free best, when the kit can actually assemble it -
    // every worn item present in the base or carried. This is what makes
    // a BIG bench an EASY problem (field insight 2026-07-17): at the
    // limit every mob simply wears its own best owned set, exactly.
    static DpsResult coverableBest(List<DpsResult> fb, Loadout base, List<GearItem> carried) {
        if (emptyBest(fb)) return null;
        Set<Integer> have = base.ids();
        for (GearItem item : carried) have.add(item.id);
        for (GearItem item : fb.get(0).loadout.items()) {
            if (!have.contains(item.id)) return null;
        }
        return fb.get(0);
    }

    // Wear a bundle into the base set; null when it cannot be worn
    // honestly (shield under a 2h, projectile weapon with no ammo).
    static Loadout applyBundle(Loadout base, SwapBundle bundle) {
        Loadout out = base;
        for (GearItem item : bundle.items) {
            Loadout next = withSwap(out, item);
            if (next == null) return null;
            out = next;
        }
        GearItem weapon = out.getWeapon();
        GearItem ammo = out.get(AMMO);
        if (weapon != null && ammo != null && !compatible(ammo, weapon)) {
            // The base set's quiver does not fit the swapped weapon -
            // strip it rather than let its bonuses feed a false roll.
            out = out.with(AMMO, null);
        }
        if (!compatible(out.get(AMMO), out.getWeapon())) {
            return null; // a projectile weapon carried without its ammo
        }
        return out;
    }

    // Cross-style bundle candidates for a primary base: the other styles'
    // shared and per-mob free-best weapons, each offered bare and (when the
    // source set wears ammo) with its ammo.
    static List<SwapBundle> bundleCandidates(CombatStyle primary, Loadout base,
        GearItem specCarried, Map<CombatStyle, SharedSide> sides) {
        List<SwapBundle> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (var entry : sides.entrySet()) {
            CombatStyle style = entry.getKey();
            if (style == primary) continue;
            List<Loadout> sources = new ArrayList<>();
            sources.add(entry.getValue().shared);
            for (List<DpsResult> best : entry.getValue().bests) {
                if (!emptyBest(best)) sources.add(best.get(0).loadout);
            }
            for (Loadout source : sources) {
                GearItem weapon = source.getWeapon();
                if (weapon == null || (base.getWeapon() != null && base.getWeapon().id == weapon.id)) continue;
                GearItem ammo = brought(source, base, AMMO);
                int sf = specCarried != null && specCarried.id == weapon.id ? 1 : 0;
                if (seen.add(weapon.id * 2L)) out.add(new SwapBundle(style, singletonList(weapon), max(0, 1 - sf)));
                if (ammo != null && seen.add(weapon.id * 2L + 1)) {
                    List<GearItem> items = new ArrayList<>();
                    items.add(weapon);
                    items.add(ammo);
                    // The switch's Ava's device rides with arrows or bolts, so
                    // the kit never prices it as one more dps swap (Andrew
                    // 2026-10-30: "the assembler isn't suggested until like 6
                    // swap items are added").
                    GearItem cape = source.get(CAPE);
                    if (RangedAmmo.retrievable(ammo) && RangedAmmo.avasDevice(cape) && !RangedAmmo.avasDevice(base.get(CAPE))) items.add(cape);
                    out.add(new SwapBundle(style, items, max(0, items.size() - sf)));
                }
            }
        }
        return out;
    }

    // One mob's best answer given the kit: the primary base + singles,
    // challenged by every carried bundle rolled under its own style, and
    // by any style's FREE BEST the kit can fully assemble - the best of
    // every style's shownFor (only its dps is read).
    DpsResult kitBest(DpsCalculator calc, Loadout base, CombatStyle primary,
        List<GearItem> singles, List<SwapBundle> bundles, List<GearItem> carried, Map<CombatStyle, SharedSide> sides, int mi) {
        DpsResult best = null;
        for (CombatStyle s : sides.keySet()) {
            best = better(best, shownFor(calc, base, primary, singles, bundles, carried, sides, mi, s));
        }
        return best;
    }

    // One (mob, style) answer on a kit: the primary style wears the base
    // + singles, a bundled style wears its weapon into the base armour, and
    // any style whose free best the kit can fully assemble shows it exactly
    // (the best of the three, the earlier on ties); null when the style has
    // no answer on this kit.
    DpsResult shownFor(DpsCalculator calc, Loadout base, CombatStyle primary,
        List<GearItem> singles, List<SwapBundle> bundles, List<GearItem> carried,
        Map<CombatStyle, SharedSide> sides, int mi, CombatStyle s) {
        SharedSide side = sides.get(s);
        OptimizationRequest req = side.reqs.get(mi);
        DpsResult result = s == primary ? calcRespecting(calc, req, bestWorn(calc, req, base, singles)) : null;
        for (SwapBundle bundle : bundles) {
            Loadout seeded = bundle.style == s ? applyBundle(base, bundle) : null;
            if (seeded != null) result = better(result, calcRespecting(calc, req, bestWorn(calc, req, seeded, singles)));
        }
        return better(result, coverableBest(side.bests.get(mi), base, carried));
    }

    // One mob's kit dps, MEMOIZED (field insight 2026-07-17: hone the
    // greedy toward dynamic programming): the answer depends only on the
    // carried bundles and the carried singles RELEVANT to this mob (items
    // worn by some free best of its own), so the key is exactly that -
    // testing a candidate against the roster re-evaluates only the mobs
    // it could matter to, and repeat evaluations are table lookups.
    double evalMobDps(DpsCalculator calc, Loadout base, CombatStyle primary,
        List<GearItem> singles, List<SwapBundle> bundles, String bundlesKey, Map<CombatStyle, SharedSide> sides,
        Set<Integer> relevant, int mi, Map<String, Double> memo) {
        List<GearItem> rs = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        for (GearItem item : singles) {
            if (relevant.contains(item.id)) {
                rs.add(item);
                ids.add(item.id);
            }
        }
        sort(ids);
        String key = mi + "|" + bundlesKey + "|" + ids;
        Double hit = memo.get(key);
        if (hit != null) return hit;
        DpsResult shown = kitBest(calc, base, primary, rs, bundles, carriedOf(rs, bundles), sides, mi);
        double dps = shown == null ? 0 : shown.net;
        memo.put(key, dps);
        return dps;
    }

    static String bundlesKeyOf(List<SwapBundle> bundles) {
        List<String> parts = new ArrayList<>();
        for (SwapBundle bundle : bundles) {
            var part = new StringBuilder().append(bundle.style.ordinal()).append(':');
            for (GearItem item : bundle.items) part.append(item.id).append(',');
            parts.add(part.toString());
        }
        sort(parts);
        return String.join(";", parts);
    }

    // The cross-style single-swap candidate pool for a kit search: the
    // primary style's diff items PLUS the other carried styles' non-weapon
    // pieces, deduped by id - the diff between the per-mob BiS sets IS the
    // candidate list, so a generous budget can assemble the other style's
    // FULL set, not just its weapon. The other styles' weapons are excluded
    // because a weapon swap is a bundle, not a single.
    static List<GearItem> crossStylePool(Loadout base, CombatStyle primary, Map<CombatStyle, SharedSide> sides) {
        List<GearItem> pool = new ArrayList<>(swapCandidates(base, sides.get(primary).bests));
        for (CombatStyle s : sides.keySet()) {
            if (s == primary) continue;
            for (GearItem item : swapCandidates(base, sides.get(s).bests)) {
                if (item.slot != WEAPON && pool.stream().noneMatch(i -> i.id == item.id)) pool.add(item);
            }
        }
        return pool;
    }

    // Kit selection. A BIGGER bench is an EASIER problem (field insight
    // 2026-07-17): the ceiling is each mob wearing its own winning free
    // best, so first test that ideal - the union of every mob's winning
    // diff vs the base; when it fits the budget, carry exactly that with
    // NO search. Only a LIMITED budget ranks the diff items: greedy over
    // singles (cost 1) and bundles (their slot cost) by HP-weighted
    // marginal gain - "the most valuable swap item, then the 2nd" -
    // with cost-0 bundles (the spec weapon doubling as the other style's
    // weapon) fitting even a full bench.
    KitAnswer chooseKit(DpsCalculator calc, Loadout base, CombatStyle primary,
        Collection<GearItem> singleCandidates, List<SwapBundle> bundleCandidates, Map<CombatStyle, SharedSide> sides,
        List<MonsterStats> mobs, int slots, Map<String, Double> memo) {
        List<GearItem> allNeeded = new ArrayList<>();
        double idealTotal = 0;
        for (int j = 0; j < mobs.size(); j++) {
            CombatStyle answer = answerFor(sides, j);
            if (answer == null) continue;
            DpsResult winner = sides.get(answer).bests.get(j).get(0);
            idealTotal += worth(winner.net, kitWeight(mobs.get(j)), mobs.get(j));
            for (GearItem item : winner.loadout.items()) {
                GearItem bi = base.get(item.slot);
                if ((bi == null || bi.id != item.id)
                    && allNeeded.stream().noneMatch(i -> i.id == item.id)) {
                    allNeeded.add(item);
                }
            }
        }
        if (!allNeeded.isEmpty() && allNeeded.size() <= slots) {
            // The ideal fits: every mob wears its own best, exactly (the
            // coverage challenge in kitBest reproduces each winner).
            var kit = new KitAnswer();
            kit.singles.addAll(allNeeded);
            kit.total = idealTotal;
            return kit;
        }
        // DP-flavored greedy (field insight 2026-07-17): an inverted
        // relevance index (item -> the mobs whose free bests wear it) plus
        // the evalMobDps memo. Testing a single only re-evaluates its own
        // mobs; every other row's number carries forward unchanged, and
        // repeat tests across rounds are table lookups.
        int n = mobs.size();
        Map<Integer, Set<Integer>> mbi = new HashMap<>();
        List<Set<Integer>> rbm = new ArrayList<>();
        for (int j = 0; j < n; j++) {
            Set<Integer> relevant = new HashSet<>();
            for (SharedSide styleSide : sides.values()) {
                List<DpsResult> best = styleSide.bests.get(j);
                if (emptyBest(best)) continue;
                for (GearItem item : best.get(0).loadout.items()) {
                    relevant.add(item.id);
                    mbi.computeIfAbsent(item.id, k -> new HashSet<>()).add(j);
                }
            }
            rbm.add(relevant);
        }
        double[] hp = new double[n];
        for (int j = 0; j < n; j++) hp[j] = kitWeight(mobs.get(j));
        var kit = new KitAnswer();
        int used = 0;
        double[] mobDps = new double[n];
        double current = 0;
        String baseBundlesKey = bundlesKeyOf(kit.bundles);
        for (int j = 0; j < n; j++) {
            mobDps[j] = evalMobDps(calc, base, primary, kit.singles, kit.bundles, baseBundlesKey, sides, rbm.get(j), j, memo);
            current += worth(mobDps[j], hp[j], mobs.get(j));
        }
        while (true) {
            GearItem bestSingle = null;
            SwapBundle bb = null;
            double bestTotal = current;
            int bc = 0;
            String bundlesKey = bundlesKeyOf(kit.bundles);
            for (GearItem candidate : singleCandidates) {
                if (used + 1 > slots || kit.singles.contains(candidate)) continue;
                Set<Integer> affected = mbi.get(candidate.id);
                if (affected == null || affected.isEmpty()) {
                    continue; // in nobody's free best - it can help no one
                }
                kit.singles.add(candidate);
                double total = current;
                for (int j : affected) {
                    total += worth(evalMobDps(calc, base, primary, kit.singles, kit.bundles,
                        bundlesKey, sides, rbm.get(j), j, memo), hp[j], mobs.get(j)) - worth(mobDps[j], hp[j], mobs.get(j));
                }
                kit.singles.remove(kit.singles.size() - 1);
                if (total > bestTotal + EPS) {
                    bestTotal = total;
                    bestSingle = candidate;
                    bb = null;
                    bc = 1;
                }
            }
            for (SwapBundle candidate : bundleCandidates) {
                if (used + candidate.cost > slots || kit.bundles.contains(candidate)) continue;
                // A new bundle opens a style path for every mob.
                kit.bundles.add(candidate);
                String withKey = bundlesKeyOf(kit.bundles);
                double total = 0;
                for (int j = 0; j < n; j++) {
                    total += worth(evalMobDps(calc, base, primary, kit.singles, kit.bundles,
                        withKey, sides, rbm.get(j), j, memo), hp[j], mobs.get(j));
                }
                kit.bundles.remove(kit.bundles.size() - 1);
                if (total > bestTotal + EPS) {
                    bestTotal = total;
                    bb = candidate;
                    bestSingle = null;
                    bc = candidate.cost;
                }
            }
            if (bestSingle == null && bb == null) break;
            if (bestSingle != null) {
                kit.singles.add(bestSingle);
            } else kit.bundles.add(bb);
            used += bc;
            current = bestTotal;
            String appliedKey = bundlesKeyOf(kit.bundles);
            for (int j = 0; j < n; j++) {
                mobDps[j] = evalMobDps(calc, base, primary, kit.singles, kit.bundles, appliedKey, sides, rbm.get(j), j, memo);
            }
        }
        kit.total = current;
        return kit;
    }

    // The best worn combination for ONE mob: start from the base and
    // greedily apply the single carried swap with the biggest gain until
    // none helps (approximates set-bonus interactions without a subset
    // explosion). A 2h swap clears the shield; a shield swap is skipped
    // under a 2h weapon.
    Loadout bestWorn(DpsCalculator calc, OptimizationRequest req, Loadout base, List<GearItem> carried) {
        Loadout worn = base;
        DpsResult wr = calcRespecting(calc, req, worn);
        double wd = wr == null ? 0 : wr.net;
        boolean improved = true;
        Set<Integer> applied = new HashSet<>();
        while (improved) {
            improved = false;
            Loadout bn = null;
            double bd = wd;
            Integer bestId = null;
            for (GearItem swap : carried) {
                if (applied.contains(swap.id)) continue;
                Loadout trial = withSwap(worn, swap);
                if (trial == null) continue;
                DpsResult r = calcRespecting(calc, req, trial);
                double dps = r == null ? 0 : r.net;
                if (dps > bd + EPS) {
                    bd = dps;
                    bn = trial;
                    bestId = swap.id;
                }
            }
            if (bn != null) {
                worn = bn;
                wd = bd;
                applied.add(bestId);
                improved = true;
            }
        }
        return worn;
    }

    // A copy with one slot changed (null empties it) and NO quiver ammo -
    // the kit passes price every re-show from the slot contents alone.
    static Loadout withSlot(Loadout base, GearSlot slot, GearItem item) {
        return new Loadout(base.with(slot, item).getGear());
    }

    static Loadout withSwap(Loadout base, GearItem swap) {
        GearSlot slot = swap.slot;
        GearItem weapon = base.getWeapon();
        if (slot == SHIELD && weapon != null && weapon.twoHanded) {
            return null; // no shield under a 2h
        }
        Loadout out = withSlot(base, slot, swap);
        return slot == WEAPON && swap.twoHanded ? withSlot(out, SHIELD, null) : out;
    }

    // Pick the set that maximises the HP-WEIGHTED average dps across the
    // roster (field decision 2026-07-17, superseding the plain mean): a
    // 500-hp mob's needs outweigh a 50-hp mob's ten to one, because that is
    // where the trip's time goes. A mob the set cannot damage contributes
    // zero to every candidate regardless of its hp - so an unhittable big
    // mob drops out of the decision naturally ("unless you can't hit it").
    // Candidate pool = each mob's own best set; a compromise that is
    // nobody's #1 is not considered (heuristic).
    Loadout chooseSharedLoadout(DpsCalculator calc, List<List<DpsResult>> bests, List<OptimizationRequest> reqs, double[] weights) {
        List<Loadout> candidates = new ArrayList<>();
        for (List<DpsResult> b : bests) {
            if (!emptyBest(b)) candidates.add(b.get(0).loadout);
        }
        Loadout best = null;
        double bs = Double.NEGATIVE_INFINITY;
        for (Loadout loadout : candidates) {
            double sum = 0;
            for (int j = 0; j < reqs.size(); j++) {
                DpsResult r = calcRespecting(calc, reqs.get(j), loadout);
                sum += worth(r == null ? 0 : r.net, weights[j], reqs.get(j).monster);
            }
            if (sum > bs + EPS) {
                bs = sum;
                best = loadout;
            }
        }
        return best;
    }

    // Every style's plan, member requests and optimizes (pass A), the
    // optimizes cached; null when superseded or stopped. A single mob
    // runs it too, so its cards read their six optimizes back.
    Map<CombatStyle, StyleWork> passA(List<MonsterStats> mobs, ComputeContext ctx, long ticket) {
        int n = mobs.size();
        Map<CombatStyle, StyleWork> works = new EnumMap<>(CombatStyle.class);
        // PASS A (field fix 2026-07-17: the load-time wall was the 2x3xN
        // independent optimize() calls, not the kit search): build every
        // style's plan and requests up front, then ONE flat fan-out over
        // the pool - no per-style straggler waves, owned and game as
        // separate tasks. Each task owns its optimizer (its DpsCalculator
        // is stateful); distinct array slots + invokeAll's join publish.
        List<Callable<Object>> ot = new ArrayList<>();
        for (CombatStyle style : concreteValues()) {
            // Mob-independent plan - MUST mirror computeAllStyles (levels,
            // boost and labels depend on style + owned + real, not the mob).
            // The roster assumes the raid's own boost when EVERY mob is
            // inside the same raid (CoX overload+, ToA salts).
            BoostProfile supplied = suppliedBoost(mobs.get(0));
            for (MonsterStats mob : mobs) {
                if (suppliedBoost(mob) != supplied) {
                    supplied = null;
                    break;
                }
            }
            var w = new StyleWork(n);
            works.put(style, w);
            w.plan = stylePlan(ctx, style, supplied);
            for (MonsterStats mob : mobs) {
                w.ownedReqs.add(ownedRequestFor(ctx, mob, style, w.plan.levels, null));
                w.gameReqs.add(gameRequestFor(ctx, mob, style, w.plan.gameLevels, null));
            }
            for (int j = 0; j < n; j++) {
                int index = j;
                MonsterStats mob = mobs.get(index);
                ot.add(() -> w.ownedArr[index] = optimizeCached(mob, style, false, ctx, w.ownedReqs.get(index), ticket));
                ot.add(() -> w.gameArr[index] = optimizeCached(mob, style, true, ctx, w.gameReqs.get(index), ticket));
            }
        }
        return fanOut(ot, ticket) ? works : null;
    }

    // One flat fan-out over the roster pool; false when superseded or stopped.
    boolean fanOut(List<Callable<Object>> tasks, long ticket) {
        try {
            // Check every future: a task that THROWS otherwise vanishes
            // silently, its array slot stays null, and the whole style
            // renders as "-" with no trace (field bug 2026-08-05: the
            // Sire roster's ranged and magic buttons dashed while every
            // member answered fine alone).
            for (Future<Object> f : rosterPool.invokeAll(tasks)) {
                try {
                    f.get();
                } catch (ExecutionException e) { log.warn("roster task failed", e.getCause()); }
            }
        } catch (InterruptedException e) {
            // Cancellation of a superseded search is cooperative, via requestSeq
            // (checked immediately below) - not thread interruption. This runs on
            // our own daemon optimizer worker, interrupted only when the executor
            // is shut down on plugin stop, so just abandon the computation.
            return false;
        }
        return requestSeq.get() == ticket;
    }

    // Each member's sweep under a defensive mode, every style at once on the
    // pool (ISS-01M64GNB852CBYVVWE29A9HJK4, Andrew 2026-10-29: "Mode picks
    // feed the kit"): applyMode's pick replaces the member's best before the
    // kit is built, and its verdict waits for the card's trade.
    boolean sweep(List<MonsterStats> mobs, ComputeContext ctx, long ticket, Map<CombatStyle, StyleWork> works) {
        List<Callable<Object>> st = new ArrayList<>();
        for (StyleWork w : works.values()) {
            for (int j = 0; j < mobs.size(); j++) {
                int index = j;
                List<DpsResult> ob = w.ownedArr[j];
                if (ob == null || ob.isEmpty()) continue;
                st.add(() -> {
                    w.trades[index] = applyMode(ctx, w.ownedReqs.get(index), ob, mobs.get(index), ticket, new LoadoutOptimizer());
                    return w.picks[index] = ob.get(0);
                });
            }
        }
        return fanOut(st, ticket);
    }

    // A group's cards. Under Balanced or Tanky they are built twice
    // (ISS-01M64GNB852CBYVVWE29A9HJK4): Max DPS first, each mode card's trade
    // reads against it, as a single search's reads its own max-DPS set. Pass
    // A's optimizes are cache hits the second time.
    List<Map<CombatStyle, StyleResult>> computeStyleAcross(List<MonsterStats> mobs, ComputeContext ctx, long ticket) {
        List<Map<CombatStyle, StyleResult>> max = ctx.mode == MAX_DPS ? null : styleAcross(mobs, ctx, ticket, null);
        return ctx.mode != MAX_DPS && max == null ? null : styleAcross(mobs, ctx, ticket, max);
    }

    // Per style: optimise each mob independently, choose ONE shared owned
    // set and ONE shared game set by average dps, then rebuild every mob's
    // StyleResult around the shared sets (that mob's own dps/incoming/spec).
    // With max (the group's Max DPS cards) the mode's sweep feeds the kit
    // and each member re-wears by the mode; without it under a mode this IS
    // that Max DPS answer, and skips the swap strips nobody sees. Returns
    // null if the request was superseded mid-compute.
    List<Map<CombatStyle, StyleResult>> styleAcross(List<MonsterStats> mobs, ComputeContext ctx, long ticket,
        List<Map<CombatStyle, StyleResult>> max) {
        int n = mobs.size();
        List<Map<CombatStyle, StyleResult>> perMob = new ArrayList<>();
        for (int j = 0; j < n; j++) perMob.add(new EnumMap<>(CombatStyle.class));
        var calc = new DpsCalculator();
        // Per-style state the cross-style kit passes reuse after the loop -
        // one pass for the Yours side, one for the BiS side.
        Map<CombatStyle, SharedSide> ownedSides = new EnumMap<>(CombatStyle.class);
        Map<CombatStyle, SharedSide> gameSides = new EnumMap<>(CombatStyle.class);
        Map<CombatStyle, StyleWork> works = passA(mobs, ctx, ticket);
        if (works == null || max != null && !sweep(mobs, ctx, ticket, works)) return null;
        // Each side's free bests, every style together: a style's shared kit
        // answers to the members fought with it (styleUseWeights).
        Map<CombatStyle, List<List<DpsResult>>> obs = new EnumMap<>(CombatStyle.class);
        Map<CombatStyle, List<List<DpsResult>>> gbs = new EnumMap<>(CombatStyle.class);
        for (CombatStyle style : concreteValues()) {
            StyleWork w = works.get(style);
            List<List<DpsResult>> ob2 = new ArrayList<>();
            List<List<DpsResult>> gb2 = new ArrayList<>();
            for (int j = 0; j < n; j++) {
                ob2.add(w.ownedArr[j] == null ? new ArrayList<>() : w.ownedArr[j]);
                gb2.add(w.gameArr[j] == null ? new ArrayList<>() : w.gameArr[j]);
            }
            obs.put(style, ob2);
            gbs.put(style, gb2);
        }
        for (CombatStyle style : concreteValues()) {
            StyleWork w = works.get(style);
            List<OptimizationRequest> ownedReqs = w.ownedReqs;
            List<OptimizationRequest> gameReqs = w.gameReqs;
            List<List<DpsResult>> ob2 = obs.get(style);
            List<List<DpsResult>> gb2 = gbs.get(style);
            SharedSide ownedSide = sharedSideFor(calc, ctx, mobs, ownedReqs, ob2, ctx.effectiveOwned,
                styleUseWeights(mobs, style, obs));
            SharedSide gs = sharedSideFor(calc, ctx, mobs, gameReqs, gb2, null, styleUseWeights(mobs, style, gbs));
            Loadout sharedOwned = ownedSide.shared;
            Loadout sg = gs.shared;
            SpecPick[] specs = ownedSide.specOptions.isEmpty() ? new SpecPick[n] : ownedSide.specOptions.get(0);
            SpecPick[] gameSpecs = gs.specOptions.isEmpty() ? new SpecPick[n] : gs.specOptions.get(0);
            // The INVENTORY budget counts every carried item INCLUDING the
            // spec weapon (field decision 2026-07-17, reversing same-day:
            // Inventory 0 = strictly one worn set, NO spec recommended).
            // Swaps are chosen greedily by HP-weighted marginal gain; each
            // mob then WEARS its best combination of base + carried swaps.
            // The bench reserves slots for the PRESUMPTIVE (top-ranked)
            // spec; the kit pass re-prices each option it actually seats.
            GearItem[] carriedSlots = carriedSpecOf(specs, sharedOwned);
            GearItem[] gameSlots = carriedSpecOf(gameSpecs, sg);
            int bfs = max(0, ctx.maxSwaps - slotsOf(carriedSlots));
            List<GearItem> swaps = sharedOwned == null ? List.of()
                : chooseSwaps(calc, sharedOwned, ownedReqs, ownedSide.weights, ob2, bfs);
            w.specs = specs;
            w.gameSpecs = gameSpecs;
            if (sharedOwned != null) ownedSides.put(style, ownedSide);
            if (sg != null) gameSides.put(style, gs);
            if (sharedOwned != null && !swaps.isEmpty()) {
                // Re-show each mob wearing its best base+swaps combination;
                // when the carried kit can fully assemble the mob's own
                // free best, show THAT exactly (big bench = easy problem).
                for (int j = 0; j < n; j++) {
                    Loadout worn = bestWorn(calc, ownedReqs.get(j), sharedOwned, swaps);
                    ownedSide.shown[j] = better(calcRespecting(calc, ownedReqs.get(j), worn),
                        coverableBest(ob2.get(j), sharedOwned, swaps));
                }
            }
            // The trip PLAN for this style: the union of what the mobs
            // actually wear across the roster - the inventory view shows
            // plan-minus-worn, so displaced pieces no mob uses drop out.
            var plan = new LinkedHashMap<Integer, GearItem>();
            for (DpsResult shown : ownedSide.shown) {
                if (shown != null) pack(plan, shown.loadout.items());
            }
            pack(plan, Arrays.asList(carriedSlots[1], carriedSlots[2]));
            for (int j = 0; j < n; j++) {
                MonsterStats mob = mobs.get(j);
                // The single-mob post-passes the roster shown sets never got
                // (field reports 2026-08-09): the Dizana's quiver relocation,
                // and the mandatory-recoil swap (a Zulrah roster recommended
                // sets with no recoil gear).
                DpsResult os = postPass(ctx, ownedReqs.get(j), ownedSide.shown[j]);
                DpsResult gameShown = postPass(ctx, gameReqs.get(j), gs.shown[j]);
                // calculate() returns null vs an immune mob (the TD shield
                // phases) - an honest empty list, never a null element the
                // card would trip over.
                List<DpsResult> ol = new ArrayList<>();
                if (os != null) ol.add(os);
                SpecPick spec = specs[j];
                SpecPick gameSpec = gameSpecs[j];
                Result incoming = incomingFor(mob, sharedOwned, ctx, style);
                Result gameIncoming = incomingFor(mob, sg, ctx, style);
                Loadout worn = os == null ? sharedOwned : os.loadout;
                List<GearItem> bench = sharedOwned == null ? List.of() : inventoryFor(plan.values(), carriedSlots[0], worn);
                List<GearItem> gameBench = gameSlots[0] == null ? List.of() : gameSlots[2] == null ? singletonList(gameSlots[0])
                    : Arrays.asList(gameSlots[0], gameSlots[2]);
                StyleResult sr = verified(new StyleResult(ol, gameShown, spec, gameSpec,
                    w.plan.label, w.plan.gameLabel, incoming, gameIncoming, bench, gameBench, true, true),
                    ctx.dataset, ownedReqs.get(j), gameReqs.get(j));
                perMob.get(j).put(style, sr);
            }
        }
        // THE CROSS-STYLE KIT (field decision 2026-07-17): bench slots may
        // carry ANOTHER style's weapon, so one carried kit can answer a
        // style-immune phase (TD shields) with a genuine style switch. Yours
        // only: BiS is each style's free game-wide best, never a swap kit
        // (ISS-01M60YKX8YAMV6QQX3XCYGJDBF, Andrew 2026-10-28: "Free BiS per style").
        KitView ov = ctx.maxSwaps >= 1 && ownedSides.size() >= 2 ? kitPass(calc, mobs, ctx, ticket, ownedSides, max != null) : null;
        if (requestSeq.get() != ticket) return null;
        if (ov != null) {
            // Merge the kit views over the phase-1 tabs: each side that has
            // a kit replaces its half of every StyleResult; a style the kit
            // cannot reach shows nothing on that side (no set you cannot
            // assemble mid-trip). The other side's fields carry over.
            for (int j = 0; j < n; j++) {
                for (CombatStyle s : CombatStyle.values()) {
                    StyleResult old = perMob.get(j).get(s);
                    if (old == null) continue;
                    StyleWork w = works.get(s);
                    // A side with a kit but no answer for this style keeps the
                    // PRE-KIT shared per-style answer instead of blanking (field
                    // bug 2026-08-05: the Sire roster dashed ranged and magic
                    // while every mob answered them alone); only the kit-specific
                    // carried spec drops. BiS keeps its pre-kit answer whole.
                    Half oh = kitHalf(ov, ownedSides, s, j, mobs.get(j), ctx);
                    boolean oka = oh != null;
                    List<DpsResult> ol = oka ? new ArrayList<>(List.of(oh.r)) : old.owned;
                    // The single-mob post-passes on whichever shown set won
                    // (base-shared or kit-backed): quiver relocation and the
                    // mandatory-recoil swap - the kit's shownByMob sets bypass
                    // the earlier per-mob pass (field reports 2026-08-09).
                    OptimizationRequest own = ownedSides.get(s) == null ? null : ownedSides.get(s).reqs.get(j);
                    OptimizationRequest game = gameSides.get(s) == null ? null : gameSides.get(s).reqs.get(j);
                    if (ol != null && !ol.isEmpty() && own != null) {
                        ol = new ArrayList<>(ol);
                        ol.set(0, postPass(ctx, own, ol.get(0)));
                    }
                    // Yours is kit-backed when the kit just answered this style.
                    perMob.get(j).put(s, verified(new StyleResult(ol, old.overallBest, oka ? oh.spec : null,
                        w.gameSpecs[j], oka ? w.plan.label : old.boostLabel, old.gameBoostLabel, oka ? oh.incoming : old.incoming,
                        old.gameIncoming, oka ? oh.bench : old.bench, old.gameBench, oka, true), ctx.dataset, own, game));
                }
            }
        }
        if (max == null && ctx.mode != MAX_DPS) return perMob;
        for (int j = 0; max != null && j < n; j++) {
            for (CombatStyle s : concreteValues()) {
                StyleResult sr = perMob.get(j).get(s);
                StyleResult b = max.get(j).get(s);
                if (sr != null && !sr.owned.isEmpty() && b != null && !b.owned.isEmpty()) modeCard(calc, ctx, mobs.get(j), s, works.get(s), j, sr, b);
            }
        }
        // The group's swaps: one strip per style, on every member's card,
        // built from what the cards wear after the kit merge.
        for (var entry : ownedSides.entrySet()) {
            Map<GearSlot, Ladder> group = groupLadderFor(ctx, entry.getValue(), mobs, perMob, entry.getKey());
            for (int j = 0; j < n; j++) {
                StyleResult sr = perMob.get(j).get(entry.getKey());
                if (sr != null) sr.ladder = group;
            }
        }
        return perMob;
    }

    // A group card under the defensive mode (ISS-01M64GNB852CBYVVWE29A9HJK4):
    // the member wears its mode-best combination of what the trip carries for
    // it (unless its sweep had no damage to rank by), then its trade reads
    // against its card in the group's Max DPS answer b - kept Max DPS, with
    // the sweep's reason, when the set is the same.
    void modeCard(DpsCalculator calc, ComputeContext ctx, MonsterStats mob, CombatStyle s, StyleWork w, int j,
        StyleResult sr, StyleResult b) {
        OptimizationRequest req = w.ownedReqs.get(j);
        ModeTrade sweep = w.trades[j];
        DpsResult shown = sr.owned.get(0);
        if (sweep != null && (sweep.reason == null || NO_SAFER.equals(sweep.reason))) {
            DpsResult worn = modeWear(calc, ctx, req, mob, shown, sr.bench, w.picks[j]);
            if (worn != shown) {
                worn = postPass(ctx, req, worn);
                sr.bench = rebench(sr.bench, shown.loadout, worn.loadout);
                sr.owned.set(0, worn);
                sr.incoming = incomingFor(mob, worn.loadout, ctx, s);
                verified(sr, ctx.dataset, req, w.gameReqs.get(j));
                shown = worn;
            }
        }
        DpsResult base = b.owned.get(0);
        double bd = b.incoming == null ? 0 : b.incoming.totalDps;
        sr.modeTrade = shown.loadout.ids().equals(base.loadout.ids())
            ? new ModeTrade(ctx.mode, 0, 0, sweep != null && sweep.reason != null ? sweep.reason : "no safer set carried")
            : new ModeTrade(ctx.mode, (int) round((1 - shown.dps / base.dps) * 100),
                bd <= 0 || sr.incoming == null ? 0 : (int) round((1 - sr.incoming.totalDps / bd) * 100), null);
    }

    // The member's mode-best combination of the shown set and what it
    // carries: bestWorn's greedy wear ranked by the mode (compareRungs), or
    // its own sweep pick when the shown set and the carried items cover it
    // and it ranks higher. Shown itself when nothing ranks higher.
    DpsResult modeWear(DpsCalculator calc, ComputeContext ctx, OptimizationRequest req, MonsterStats mob,
        DpsResult shown, List<GearItem> carried, DpsResult pick) {
        DpsResult worn = shown;
        Rung wr = rungOf(ctx, mob, req.style, worn);
        Set<Integer> applied = new HashSet<>();
        while (true) {
            DpsResult next = null;
            GearItem step = null;
            Rung nr = wr;
            for (GearItem swap : carried) {
                Loadout trial = applied.contains(swap.id) ? null : withSwap(worn.loadout, swap);
                DpsResult r = trial == null ? null : calcRespecting(calc, req, trial);
                Rung rr = r == null ? null : rungOf(ctx, mob, req.style, r);
                if (rr != null && compareRungs(ctx.mode, rr, nr) > 0) {
                    next = r;
                    step = swap;
                    nr = rr;
                }
            }
            if (next == null) break;
            worn = next;
            wr = nr;
            applied.add(step.id);
        }
        DpsResult cover = pick == null ? null : coverableBest(singletonList(pick), shown.loadout, carried);
        return cover != null && compareRungs(ctx.mode, rungOf(ctx, mob, req.style, cover), wr) > 0 ? cover : worn;
    }

    // The bench after a re-wear: what was worn or carried, minus the new
    // set (a carried spec weapon stays first).
    static List<GearItem> rebench(List<GearItem> bench, Loadout was, Loadout now) {
        var all = new LinkedHashMap<Integer, GearItem>();
        pack(all, bench);
        pack(all, was.items());
        return inventoryFor(all.values(), null, now);
    }

    // One style's roster state: pass A's plan and member requests and the
    // fanned-out optimize answers (one array slot per task), then the
    // shared-spec picks the kit merge reads back, and under a defensive mode
    // each member's sweep verdict and pick.
    static class StyleWork {
        StylePlan plan;
        final List<OptimizationRequest> ownedReqs = new ArrayList<>();
        final List<OptimizationRequest> gameReqs = new ArrayList<>();
        final List<DpsResult>[] ownedArr;
        final List<DpsResult>[] gameArr;
        SpecPick[] specs;
        SpecPick[] gameSpecs;
        final ModeTrade[] trades;
        final DpsResult[] picks;

        StyleWork(int n) {
            ownedArr = new List[n];
            gameArr = new List[n];
            trades = new ModeTrade[n];
            picks = new DpsResult[n];
        }
    }

    // One side's shared answer for a style (Yours or BiS): the member
    // requests and free bests, the shared set, each mob's result on it (null
    // where calculate() has nothing - the TD shield phases) and the ranked
    // shared-spec options - the kit pass's per-style input.
    @RequiredArgsConstructor
    static class SharedSide {
        final List<OptimizationRequest> reqs;
        final List<List<DpsResult>> bests;
        // Each member's say in this style's kit (styleUseWeights).
        final double[] weights;
        Loadout shared;
        DpsResult[] shown;
        List<SpecPick[]> specOptions = List.of();
    }

    // The side's shared set re-shown per mob, with its shared spec and the
    // Lightbearer arbitration on top. owned == null is the BiS side.
    SharedSide sharedSideFor(DpsCalculator calc, ComputeContext ctx, List<MonsterStats> mobs,
        List<OptimizationRequest> reqs, List<List<DpsResult>> bests, OwnedItems owned, double[] weights) {
        var side = new SharedSide(reqs, bests, weights);
        side.shared = chooseSharedLoadout(calc, bests, reqs, weights);
        side.shown = shownAcross(calc, reqs, side.shared);
        if (ctx.maxSwaps >= 1 && ctx.p.specWeapon) {
            // The spec weapon is PART of the carried set (field decision
            // 2026-07-17): with zero swaps a roster brings ONE spec weapon,
            // so the pick is shared - weighted like the worn set - and
            // only its per-mob numbers flip. Swapping per mob is M-4
            // swap-budget territory.
            side.specOptions = chooseSharedSpec(ctx, mobs, reqs, side.shown, owned, weights);
            adoptLightbearer(calc, ctx, mobs, reqs, owned, side);
        }
        return side;
    }

    // Each mob's result on the shared set (all null without one).
    DpsResult[] shownAcross(DpsCalculator calc, List<OptimizationRequest> reqs, Loadout shared) {
        DpsResult[] shown = new DpsResult[reqs.size()];
        for (int j = 0; shared != null && j < shown.length; j++) {
            shown[j] = calcRespecting(calc, reqs.get(j), shared);
        }
        return shown;
    }

    // ROSTER LIGHTBEARER ARBITRATION (field report 2026-08-08: a Nex
    // group recommended Ring of shadows over the Lightbearer): the
    // shared ring falls out of raw-stat set construction, which the
    // stat-less Lightbearer can never win - but doubled regen scales
    // the whole trip's spec value. Price the trip both ways (HP-
    // weighted shared-set dps + top shared-spec value) and adopt the
    // variant on a strict win - the single-mob arbitration's argmax,
    // at the shared-set altitude.
    void adoptLightbearer(DpsCalculator calc, ComputeContext ctx, List<MonsterStats> mobs,
        List<OptimizationRequest> reqs, OwnedItems owned, SharedSide side) {
        Loadout variant = lightbearerVariant(ctx.dataset, side.shared, reqs, owned);
        if (variant == null) return;
        DpsResult[] shown = shownAcross(calc, reqs, variant);
        double bt = topSpecScore(side.specOptions, side.weights, side.shown, mobs);
        double lt = 0;
        for (int j = 0; j < shown.length; j++) {
            double weight = side.weights[j];
            if (shown[j] != null) lt += worth(shown[j].net, weight, mobs.get(j));
            if (side.shown[j] != null) bt += worth(side.shown[j].net, weight, mobs.get(j));
        }
        List<SpecPick[]> options = chooseSharedSpec(ctx, mobs, reqs, shown, owned, side.weights);
        lt += topSpecScore(options, side.weights, shown, mobs);
        if (lt > bt + EPS) {
            side.shared = variant;
            side.shown = shown;
            side.specOptions = options;
        }
    }

    // Every non-null item into the plan, once each.
    static void pack(Map<Integer, GearItem> plan, Collection<GearItem> items) {
        for (GearItem item : items) {
            if (item != null) plan.putIfAbsent(item.id, item);
        }
    }

    // The style whose free best answers mob j with the most dps (the
    // earlier on ties), or null when no style has an answer.
    static CombatStyle answerFor(Map<CombatStyle, SharedSide> sides, int j) {
        CombatStyle answer = null;
        DpsResult top = null;
        for (var entry : sides.entrySet()) {
            List<DpsResult> best = entry.getValue().bests.get(j);
            if (!emptyBest(best) && (top == null || best.get(0).net > top.net)) {
                top = best.get(0);
                answer = entry.getKey();
            }
        }
        return answer;
    }

    // One primary base's kit contest: the kit, the kept spec option with
    // its carried [weapon, ammo, offhand], and the score the primaries are
    // ranked on.
    @AllArgsConstructor
    static class Primary {
        final CombatStyle style;
        final KitAnswer kit;
        final SpecPick[] specs;
        final GearItem[] carried;
        final double score;
    }

    // The carried spec weapon's pick for mob j on this side's OWN sets, from
    // the side's ranked options: the kit's kept option was priced on the
    // primary style's sets, so a card another style answers reads its own
    // (ISS-01M5PKSQBAP823XNC13QBW8830: with the magic primary's dagger option
    // priced for the Core alone, Ba-Ba's melee card lost the Wardens'
    // dagger). Null when no option carries it or it adds nothing here.
    // One side's kit answer for a style on one mob (the merge above); null when the kit has none.
    @AllArgsConstructor
    static class Half {
        final DpsResult r;
        final SpecPick spec;
        final Result incoming;
        final List<GearItem> bench;
    }

    Half kitHalf(KitView v, Map<CombatStyle, SharedSide> sides, CombatStyle s, int j, MonsterStats mob, ComputeContext ctx) {
        DpsResult r = v != null && v.styles.contains(s) ? v.shownByMob.get(j).get(s) : null;
        if (r == null) return null;
        r = r.withPurchaseCost(unownedValue(r.loadout, ctx.effectiveOwned));
        SpecPick sp = v.specs != null ? v.specs[j] : null;
        return new Half(r, sp != null ? sp : pickFor(sides.get(s), j, v.specCarried), incomingFor(mob, r.loadout, ctx, s),
            inventoryFor(v.plan.values(), v.specCarried, r.loadout));
    }

    static SpecPick pickFor(SharedSide side, int j, GearItem carried) {
        if (side == null || carried == null) return null;
        for (SpecPick[] option : side.specOptions) {
            for (SpecPick pick : option) {
                if (pick != null && pick.weapon.id == carried.id) return option[j];
            }
        }
        return null;
    }

    // One side's kit view, ready to merge over the phase-1 tabs.
    @AllArgsConstructor
    static class KitView {
        final Set<CombatStyle> styles;
        final GearItem specCarried;
        // The kept spec picks, or null when the swaps outbid the spec.
        final SpecPick[] specs;
        final List<Map<CombatStyle, DpsResult>> shownByMob;
        final LinkedHashMap<Integer, GearItem> plan;
    }

    // The cross-style kit pass for ONE side (Yours or BiS): unify neutral
    // ammo, try each style as the primary base, let the SPEC COMPETE for
    // its slot against the swaps (field fix 2026-07-17: a spec weapon must
    // not lock the budget when a cross-style weapon would answer an immune
    // phase), pick the winner by HP-weighted total plus the spec's damage
    // value, and compute every (mob, style) answer plus the trip plan.
    // Returns null only when superseded mid-compute.
    KitView kitPass(DpsCalculator calc, List<MonsterStats> mobs,
        ComputeContext ctx, long ticket, Map<CombatStyle, SharedSide> sides, boolean defensive) {
        int n = mobs.size();
        // SLOT UNIFICATION (field insight 2026-07-18: a tribrid answer
        // should not pay one carried slot per near-identical variant):
        // per style and slot, the roster's most-worn item absorbs any
        // per-mob variant whose dps cost is under 0.5%. The per-mob bests
        // converge onto shared pieces, the diffs shrink, and the
        // significant breakpoints arrive with far fewer items - the
        // mage/tribrid answers assemble early instead of fragmenting.
        // Under a defensive mode a variant must not raise the member's
        // damage taken either (protect()'s rule), or the sweep's pick
        // would unify away before the kit sees it (ISS-01M64GNB852CBYVVWE29A9HJK4).
        for (var e : sides.entrySet()) {
            SharedSide us = e.getValue();
            List<OptimizationRequest> unifyReqs = us.reqs;
            for (GearSlot slot : GearSlot.values()) {
                Map<Integer, Integer> counts = new HashMap<>();
                Map<Integer, GearItem> ibi = new HashMap<>();
                for (List<DpsResult> best : us.bests) {
                    if (emptyBest(best)) continue;
                    GearItem item = best.get(0).loadout.get(slot);
                    if (item != null) {
                        counts.merge(item.id, 1, Integer::sum);
                        ibi.put(item.id, item);
                    }
                }
                if (counts.size() < 2) continue;
                int ci = max(counts.entrySet(), Map.Entry.comparingByValue()).getKey();
                GearItem canonical = ibi.get(ci);
                for (int j = 0; j < n; j++) {
                    List<DpsResult> best = us.bests.get(j);
                    if (emptyBest(best)) continue;
                    GearItem worn = best.get(0).loadout.get(slot);
                    if (worn == null || worn.id == ci) continue;
                    // Slot legality (field report 2026-08-15 x2, the Eclipse
                    // Moon's scythe+defender: THIS was the birthplace) - a
                    // 2h canonical evicts the shield, and a shield canonical
                    // cannot join a 2h wearer.
                    Loadout ul2 = best.get(0).loadout;
                    GearItem uw = ul2.getWeapon();
                    if (slot == SHIELD && uw != null && uw.twoHanded) continue;
                    ul2 = withSlot(ul2, slot, canonical);
                    if (slot == WEAPON && canonical.twoHanded) ul2 = withSlot(ul2, SHIELD, null);
                    DpsResult unified = calcRespecting(calc, unifyReqs.get(j), ul2);
                    if (unified != null && unified.net >= best.get(0).net * 0.995 && (!defensive
                        || incomingFor(mobs.get(j), ul2, ctx, e.getKey()).totalDps
                            <= incomingFor(mobs.get(j), best.get(0).loadout, ctx, e.getKey()).totalDps + EPS)) best.set(0, unified);
                }
            }
        }
        // A quiver never costs a swap slot (field fix 2026-07-17): the
        // ammo slot does nothing for melee, so every ammo-NEUTRAL answer
        // wears the ammo the mob's caring answer rolls with (the ranged
        // bolts ride in the melee set), and the shared bases follow suit.
        // Only the style that ANSWERS a mob (its best dps) sets the ammo
        // (Kendyy 2026-10-20: the magic card wore bolts over a blessing
        // at Barrows, where ranged answered nobody).
        GearItem rosterAmmo = null;
        GearItem[] nb = new GearItem[n];
        for (int j = 0; j < n; j++) {
            CombatStyle answer = answerFor(sides, j);
            DpsResult top = answer == null ? null : sides.get(answer).bests.get(j).get(0);
            GearItem ammo = top == null ? null : top.loadout.get(AMMO);
            if (ammo == null) continue;
            DpsResult without = calcRespecting(calc, sides.get(answer).reqs.get(j), withSlot(top.loadout, AMMO, null));
            if (without == null || without.net < top.net - EPS) {
                nb[j] = ammo;
                rosterAmmo = rosterAmmo == null ? ammo : rosterAmmo;
            }
        }
        for (int j = 0; j < n; j++) {
            GearItem needed = nb[j] != null ? nb[j] : rosterAmmo;
            if (needed == null) continue;
            for (CombatStyle s : sides.keySet()) {
                List<DpsResult> best = sides.get(s).bests.get(j);
                if (emptyBest(best)) continue;
                DpsResult r = best.get(0);
                GearItem ammo = r.loadout.get(AMMO);
                if (ammo != null && ammo.id == needed.id) continue;
                DpsResult unified = calcRespecting(calc, sides.get(s).reqs.get(j), withSlot(r.loadout, AMMO, needed));
                if (unified != null && unified.net >= r.net - EPS) best.set(0, unified);
            }
        }
        if (rosterAmmo != null) {
            for (SharedSide ammoSide : sides.values()) {
                Loadout b = ammoSide.shared;
                GearItem ammo = b.get(AMMO);
                if (ammo != null && ammo.id == rosterAmmo.id) continue;
                Loadout ub = withSlot(b, AMMO, rosterAmmo);
                boolean neutral = true;
                List<OptimizationRequest> reqs = ammoSide.reqs;
                for (int j = 0; j < n && neutral; j++) {
                    DpsResult before = calcRespecting(calc, reqs.get(j), b);
                    DpsResult after = calcRespecting(calc, reqs.get(j), ub);
                    neutral = (after == null ? 0 : after.net) >= (before == null ? 0 : before.net) - EPS;
                }
                if (neutral) ammoSide.shared = ub;
            }
        }
        // The primaries are independent once the ammo pass has settled the
        // shared state - fan them out (each task owns its calculator; the
        // kit search is the second half of the raid load-time wall).
        List<CombatStyle> primaries = new ArrayList<>(sides.keySet());
        List<Callable<Primary>> pt = new ArrayList<>();
        List<SpecPick[]> tripOptions = tripSpecOptions(sides, mobs);
        for (CombatStyle primary : primaries) {
            pt.add(() -> {
                if (requestSeq.get() != ticket) return null;
                var lc = new DpsCalculator();
                Loadout base = sides.get(primary).shared;
                List<GearItem> singlePool = crossStylePool(base, primary, sides);
                List<SpecPick[]> specOptions = tripOptions;
                // One memo per primary base, shared across the spec-compete
                // evaluations - keys carry the bundle set, so options with
                // different spec weapons never collide.
                Map<String, Double> memo = new HashMap<>();
                // THE SPEC COMPETES FOR ITS SLOT: build the kit both ways -
                // spec carried (one slot fewer for swaps) and spec dropped -
                // and let the spec's damage value argue for its seat. The
                // value is the expected spec damage once per kill, in the
                // same HP-weighted currency as the kit total. The options
                // arrive ranked by trip value; the FIRST that pays for its
                // seat is kept - a vetoed argmax winner falls through to
                // the runner-up rather than costing the roster its spec
                // entirely (regression 2026-08-08).
                KitAnswer kitFree = chooseKit(lc, base, primary, singlePool,
                    bundleCandidates(primary, base, null, sides), sides, mobs, ctx.maxSwaps, memo);
                KitAnswer kit = kitFree;
                SpecPick[] ks = null;
                GearItem[] kept = new GearItem[3];
                double score = kitFree.total;
                // A pinned spec is seated unconditionally - the player's
                // explicit choice overrides the seat economics, so it always
                // shows (even adding ~0.00, field report 2026-08-09).
                boolean specPinned = restrictSpec(ctx, primary, mobs.get(0)) != null;
                for (SpecPick[] option : specOptions) {
                    GearItem[] slots = carriedSpecOf(option, base);
                    int ss = slotsOf(slots);
                    KitAnswer kitSpec = ss == 0 ? kitFree : chooseKit(lc, base, primary, singlePool,
                            bundleCandidates(primary, base, slots[0], sides), sides, mobs, max(0, ctx.maxSwaps - ss), memo);
                    // The seat: the kill is shorter by the spec's damage (its
                    // expected damage once per kill, as a share of the hp); the
                    // score: by its honest dpsAdded. Both in worth's currency, a
                    // spec-only member (the Warden's Core) at its spec weight.
                    double specTotal = kitSpec.total;
                    double tv = 0;
                    List<GearItem> carriedSpec = carriedOf(kitSpec);
                    for (int j = 0; j < n; j++) {
                        if (option[j] == null) continue;
                        DpsResult shown = kitBest(lc, base, primary, kitSpec.singles, kitSpec.bundles, carriedSpec, sides, j);
                        if (shown != null && shown.net > 0) {
                            MonsterStats mob = mobs.get(j);
                            double w = specWeight(mob);
                            double was = worth(shown.net, w, mob);
                            specTotal += worth(shown.net * (1 + option[j].expectedDamage / max(1, mob.hitpoints)), w, mob) - was;
                            tv += worth(shown.net + option[j].dpsAdded, w, mob) - was;
                        }
                    }
                    if (specPinned || specTotal >= kitFree.total) {
                        kit = kitSpec;
                        ks = option;
                        kept = slots;
                        // The PRIMARY contest scores what the card SHOWS -
                        // kit total plus the kept spec's honest dpsAdded
                        // value - not the damage-blind seat currency (field
                        // report 2026-08-09: the Muspah roster's magic
                        // primary outbid a spec-kept ranged primary by 0.3%
                        // of kit total while ignoring an elder maul worth
                        // 20x that in drain value, and the BiS card lost
                        // its spec entirely).
                        score = kitSpec.total + tv;
                        break;
                    }
                }
                return new Primary(primary, kit, ks, kept, score);
            });
        }
        Primary winner = null;
        try {
            for (Future<Primary> future : rosterPool.invokeAll(pt)) {
                Primary outcome;
                try {
                    outcome = future.get();
                } catch (ExecutionException e) {
                    continue; // one primary failed - the others still answer
                }
                if (outcome != null && (winner == null || outcome.score > winner.score + EPS)) {
                    winner = outcome; // null = superseded mid-task
                }
            }
        } catch (InterruptedException e) {
            // Cancellation of a superseded search is cooperative, via requestSeq
            // (checked immediately below) - not thread interruption. This runs on
            // our own daemon optimizer worker, interrupted only when the executor
            // is shut down on plugin stop, so just abandon the computation.
            return null;
        }
        if (requestSeq.get() != ticket) return null;
        if (winner == null) {
            return null; // unreachable with >= 2 shared styles; be safe
        }
        Loadout base = sides.get(winner.style).shared;
        List<GearItem> carried = carriedOf(winner.kit);
        // Every (mob, style) answer, and the trip PLAN: the union of each
        // mob's best worn set. The primary wears the base, a bundled style
        // wears its weapon into the base armor, and ANY style whose free
        // best the kit can fully assemble shows it exactly.
        List<Map<CombatStyle, DpsResult>> shownByMob = new ArrayList<>();
        var plan = new LinkedHashMap<Integer, GearItem>();
        for (int j = 0; j < n; j++) {
            Map<CombatStyle, DpsResult> shown = new EnumMap<>(CombatStyle.class);
            DpsResult mb = null;
            for (CombatStyle s : sides.keySet()) {
                DpsResult result = shownFor(calc, base, winner.style, winner.kit.singles, winner.kit.bundles, carried, sides, j, s);
                if (result != null) {
                    shown.put(s, result);
                    mb = better(mb, result);
                }
            }
            shownByMob.add(shown);
            if (mb != null) pack(plan, mb.loadout.items());
        }
        pack(plan, Arrays.asList(winner.carried[1], winner.carried[2]));
        return new KitView(EnumSet.copyOf(sides.keySet()), winner.carried[0], winner.specs, shownByMob, plan);
    }

    static List<GearItem> carriedOf(KitAnswer kit) { return carriedOf(kit.singles, kit.bundles); }

    // Everything a kit carries, once each: the singles, then the bundles' pieces.
    static List<GearItem> carriedOf(List<GearItem> singles, List<SwapBundle> bundles) {
        List<GearItem> carried = new ArrayList<>(singles);
        for (SwapBundle bundle : bundles) {
            for (GearItem item : bundle.items) {
                if (carried.stream().noneMatch(i -> i.id == item.id)) carried.add(item);
            }
        }
        return carried;
    }

    // Optimise ONE shared set per style across a roster of mobs. Single-mob
    // rosters take the exact bestPerStyle path (wrapped as a 1-mob answer);
    // multi-mob rosters choose the shared set by average dps and deliver a
    // per-mob display bundle. Parameters mirror bestPerStyle (result-level
    // params shared across the roster; exclusions, sims, pins, the pinned
    // spell and the pinned spec are per member, keyed by profileId - the
    // spec seats the FIRST member's).
    public void bestPerStyleAcross( List<MonsterStats> mobs,
        PlayerLevels rl, PlayerLevels boostedLevels, PrayerUnlocks pu,
        RequirementProfile requirements, OwnedItems owned, int collectionFingerprint,
        ComputeParams p, Map<CombatStyle, Set<Integer>> ebs, Set<Integer> di,
        Map<Integer, Map<CombatStyle, Set<Integer>>> excludedByMob, Map<Integer, Set<Integer>> dreamsByMob,
        Map<Integer, Map<CombatStyle, Map<GearSlot, Integer>>> pinsByMob,
        Map<Integer, SpellStats> spellByMob, Map<Integer, Integer> specByMob, Set<Integer> poi,
        Consumer<RosterResult> callback) {
        if (mobs == null || mobs.isEmpty()) return;
        ComputeContext ctx = buildContext(rl, boostedLevels, pu,
            requirements, owned, collectionFingerprint, p, ebs, di, pinsByMob, spellByMob, specByMob, poi);
        ctx.excludedByMob = excludedByMob == null ? Map.of() : excludedByMob;
        ctx.dreamsByMob = dreamsByMob == null ? Map.of() : dreamsByMob;
        if (mobs.size() == 1) {
            // The single-mob fast path, the member's own exclusions and sims
            // layered per mob by the context - one request, same semantics.
            bestPerStyle(mobs.get(0), ctx, map -> callback.accept(new RosterResult(mobs, singletonList(map), "", "")));
            return;
        }
        List<MonsterStats> roster = new ArrayList<>(mobs);
        submit(ticket -> {
            List<Map<CombatStyle, StyleResult>> perMob = computeStyleAcross(roster, ctx, ticket);
            GroupBook book = groupBook(roster, perMob, ctx);
            if (book.recompute) {
                // One book for the whole raid: every magic set recomputed
                // under it, Death Charge off when that book is not Arceuus.
                ComputeParams locked = p.copy();
                locked.spellbookLock = book.book;
                if (!"arceuus".equals(book.book)) locked.deathCharge = 0;
                ComputeContext again = buildContext(rl, boostedLevels, pu, requirements, owned,
                    collectionFingerprint, locked, ebs, di, pinsByMob, spellByMob, specByMob, poi);
                again.excludedByMob = ctx.excludedByMob;
                again.dreamsByMob = ctx.dreamsByMob;
                perMob = computeStyleAcross(roster, again, ticket);
            }
            return new RosterResult(roster, perMob, book.book, book.reason);
        }, () -> new RosterResult(roster, new ArrayList<>(nCopies(roster.size(), Map.<CombatStyle, StyleResult>of())), "", ""),
            "roster compute failed for {}", roster.get(0).name, callback);
    }

    // The per-style cache key: the query key minus per-style state, plus
    // this style and the pins/exclusions/pinned-spell that shape ITS answer.
    static String styleKey(String bk, CombatStyle style, ComputeContext ctx, MonsterStats mob) {
        return bk + "|" + style.name() + "|" + excludedFor(ctx, style, mob).hashCode() + "|" + pinKey(ctx, style, mob);
    }

    static String joinAssumes(String prayer, String boost) {
        if (prayer == null || prayer.isEmpty()) return boost;
        return boost == null ? prayer : prayer + " + " + boost;
    }

    // Package-private: SpecPoisonTest pins the spec tie-break.
    @AllArgsConstructor
    static class SpecPick {
        final SpecialAttack spec;
        final GearItem weapon;
        final double expectedDamage;
        // The DPS this spec ADDS to the kill over just attacking - the
        // win-over-replacement value the card shows and the ranking key.
        final double dpsAdded;
        // Non-null when the spec needs its own ammo carried (a dark bow
        // next to a chargebow base needs arrows - an extra slot).
        final GearItem ammo;
        // The offhand brought for the switch - a one-handed spec weapon
        // off a two-handed main (N, Discord 2026-09-26). An extra slot.
        final GearItem offhand;
        // This pick WITH its best offhand and the numbers that follow;
        // null when none applies. The bare pick is what ranks.
        final SpecPick upgraded;
    }

    // The pick the inventory can seat: the offhand rides only when the
    // weapon, its ammo and the offhand all have a slot.
    static SpecPick seat(SpecPick pick, int slots) {
        return pick != null && pick.upgraded != null && slots >= (pick.ammo == null ? 2 : 3) ? pick.upgraded : pick;
    }

    // The strongest special-attack weapon for this style, evaluated by
    // swapping it into the base set (shield dropped for two-handers, ammo
    // re-picked for compatibility) and applying the spec's verified roll
    // modifiers. With an ownership ledger, only owned weapons/ammo count
    // (requirement #7/#8); with null, everything standard counts - the
    // game-best spec.
    // One shared spec weapon for the whole roster: candidates are each
    // mob's free pick, RANKED by HP-weighted value added (a mob a candidate
    // cannot roll against contributes zero). The kit seat check walks this
    // ranking and keeps the strongest option that pays for its carried slot
    // - the argmax winner alone can be vetoed at the seat (a drain-heavy
    // pick whose value the damage-priced seat cannot see), and dropping the
    // spec entirely loses to carrying the runner-up (regression 2026-08-08:
    // the DWH outbid the dagger trip-wide, failed its seat, and the roster
    // carried no spec at all). Each entry is one candidate's per-mob
    // SpecPicks (null where it cannot spec); empty list = no candidate has
    // positive trip value.
    List<SpecPick[]> chooseSharedSpec(ComputeContext ctx, List<MonsterStats> mobs,
        List<OptimizationRequest> reqs, DpsResult[] shown, OwnedItems owned, double[] weights) {
        int n = mobs.size();
        var candidates = new LinkedHashSet<Integer>();
        // A pinned spec weapon (serving this style) is the ONLY candidate:
        // the player's explicit choice, priced across the trip like any
        // other but never contested by the free picks.
        Set<Integer> specPin = restrictSpec(ctx, reqs.get(0).style, mobs.get(0));
        if (specPin != null) {
            candidates.addAll(specPin);
        } else {
            for (int j = 0; j < n; j++) {
                SpecPick free = bestSpec(ctx.dataset, reqs.get(j), shown[j], owned, null, false);
                if (free != null && free.weapon != null) candidates.add(free.weapon.id);
            }
        }
        if (candidates.isEmpty()) return List.of();
        List<double[]> scores = new ArrayList<>();
        List<SpecPick[]> pc = new ArrayList<>();
        for (Integer id : candidates) {
            SpecPick[] bare = new SpecPick[n];
            for (int j = 0; j < n; j++) {
                bare[j] = bestSpec(ctx.dataset, reqs.get(j), shown[j], owned, singleton(id), specPin != null);
            }
            // The switch WITH its offhand is its own option, ranked beside
            // the bare weapon: the seat check keeps whichever pays for its
            // slots. A pinned weapon is the player's explicit choice - it
            // stays an option even at zero trip value, so the card can show
            // it (with an honest ~0.00) instead of silently dropping it.
            for (int pass = 0; pass < 2; pass++) {
                SpecPick[] perMob = new SpecPick[n];
                boolean differs = false;
                for (int j = 0; j < n; j++) {
                    perMob[j] = pass == 0 ? bare[j] : seat(bare[j], ctx.maxSwaps);
                    differs |= perMob[j] != bare[j];
                }
                double score = tripValue(perMob, weights);
                if ((pass == 0 || differs) && (score > EPS || specPin != null)) {
                    scores.add(new double[]{score, pc.size()});
                    pc.add(perMob);
                }
            }
        }
        scores.sort((a, b) -> Double.compare(b[0], a[0]));
        List<SpecPick[]> ranked = new ArrayList<>(scores.size());
        for (double[] row : scores) ranked.add(pc.get((int) row[1]));
        return ranked;
    }

    // The shared set with a Lightbearer in the ring slot, or null when the
    // roster arbitration cannot apply: no shared set, already a Lightbearer,
    // a ring pinned or a risk budget on any mob (the shared risk package is
    // not re-priced here - conservative skip), or the ring unavailable
    // (unowned and unsimmed, excluded, requirements). owned == null is the
    // game-best side: everything standard is available.
    static Loadout lightbearerVariant(LoadoutData dataset, Loadout shared, List<OptimizationRequest> reqs, OwnedItems owned) {
        if (shared == null) return null;
        for (OptimizationRequest req : reqs) {
            if (req.pinnedFor(RING) != null || req.isRiskConstrained()) return null;
        }
        return lightbearerSwap(dataset, shared, reqs, owned);
    }

    // The shared Lightbearer machinery: find the ring, prove it available
    // under every request (excluded/requirements/owned-or-dreamed), and wear
    // it into the set - null when the swap cannot apply, or when the set would
    // break a request's rules: every caller adds this ring after the search
    // that checked them (ISS-01M674YPVT5GFVSAT2PAK8RXS1, field report 2026-10-28:
    // "gives gear that is greater than the maximum 2m"). Callers layer their
    // own pin/risk policy on top (the roster skips risk conservatively; the
    // single-mob arbitration prices it).
    static Loadout lightbearerSwap(LoadoutData dataset, Loadout shared, List<OptimizationRequest> reqs, OwnedItems owned) {
        GearItem worn = shared.get(RING);
        if (worn != null && worn.nameLower.contains("lightbearer")) return null;
        GearItem l2 = null;
        for (GearItem item : dataset.getGearItems(RING)) {
            if (item.nameLower.equals("lightbearer") && item.standardGear) {
                l2 = item;
                break;
            }
        }
        if (l2 == null) return null;
        boolean dreamed = false;
        for (OptimizationRequest req : reqs) {
            if (req.isExcluded(l2.id) || !req.requirementProfile.canEquip(l2.requirements)) return null;
            dreamed |= req.isDream(l2.id);
        }
        if (owned != null && !owned.owns(l2.id) && !dreamed) return null;
        Loadout swapped = shared.with(RING, l2);
        for (OptimizationRequest req : reqs) if (req.setRules != null && !req.setRules.holds(swapped)) return null;
        return swapped;
    }

    // The trip time the top-ranked shared-spec option saves on these shown sets.
    static double topSpecScore(List<SpecPick[]> options, double[] weights, DpsResult[] shown, List<MonsterStats> mobs) {
        return options.isEmpty() ? 0 : tripValue(options.get(0), weights, shown, mobs);
    }

    // The trip time a spec option saves (worth's currency): each member's
    // kill with the spec's dpsAdded on top of its shown set's net dps.
    static double tripValue(SpecPick[] picks, double[] weights, DpsResult[] shown, List<MonsterStats> mobs) {
        double score = 0;
        for (int j = 0; j < weights.length; j++) {
            if (picks[j] != null && shown[j] != null) score += worth(shown[j].net + picks[j].dpsAdded, weights[j], mobs.get(j)) - worth(shown[j].net, weights[j], mobs.get(j));
        }
        return score;
    }

    // Weighted trip value of one shared-spec option (null = no spec there):
    // a style side weighs by styleUseWeights, the cross-style kit by hpWeights.
    static double tripValue(SpecPick[] picks, double[] weights) {
        double score = 0;
        for (int j = 0; j < weights.length; j++) {
            if (picks[j] != null) score += picks[j].dpsAdded * weights[j];
        }
        return score;
    }

    // owned == null is the game-best side: everything standard counts.
    static boolean available(OwnedItems owned, OptimizationRequest request, int id) {
        return owned == null || owned.owns(id) || request.isDream(id);
    }

    // The spec's expected damage per use at the Multi card's stack.
    static double specDamage(SpecialAttack spec, DpsResult base, OptimizationRequest request) {
        return spec.expectedDamage(base, request.monster, request.getLevels())
            * spec.multiFactor(request.multiStack);
    }

    // The carried slots a shared-spec option costs: [weapon, ammo,
    // offhand], all null when the base already wears the spec weapon (it rides free). A
    // dark bow next to a chargebow base needs its arrows carried too - the
    // spec spends TWO slots when the base quiver cannot feed it (field
    // spec 2026-07-18).
    static GearItem[] carriedSpecOf(SpecPick[] specs, Loadout shared) {
        for (SpecPick pick : specs) {
            if (pick != null && pick.weapon != null && shared != null && (shared.getWeapon() == null
                    || shared.getWeapon().id != pick.weapon.id)) {
                return new GearItem[]{pick.weapon, pick.ammo, pick.offhand};
            }
        }
        return new GearItem[3];
    }

    // The bench slots those carried pieces spend.
    static int slotsOf(GearItem... slots) {
        int spent = 0;
        for (GearItem slot : slots) {
            if (slot != null) spent++;
        }
        return spent;
    }

    // restrictTo non-null: only these weapon ids are scanned - the roster
    // path uses it to evaluate ONE shared spec weapon per mob.
    // The Lightbearer never survives the ring slot's raw-stat cut (its
    // stat line is empty), but its doubled spec regen can beat the best
    // dps ring ON THE TOTAL - set dps + spec dps-added - at fights with
    // real spec value (roadmap's "compare the two totals"; field report
    // 2026-08-08: "it can be really impactful in fights with big spec
    // utility"). Deterministic post-arbitration: both candidates fully
    // priced, the argmax wins, the beam is never touched. On a win the
    // results list is updated in place so the card shows the ring that
    // earned its slot. Rosters run the same argmax at the shared-set
    // altitude (lightbearerVariant, field report 2026-08-08 #2).
    SpecPick arbitrateLightbearer( LoadoutData dataset, OptimizationRequest request, List<DpsResult> results,
        OwnedItems owned, Set<Integer> rt) {
        SpecPick spec = bestSpec(dataset, request, results.isEmpty() ? null : results.get(0), owned, rt, rt != null);
        if (spec == null) {
            return null; // no spec value in play - dps ring stands
        }
        DpsResult base = results.get(0);
        if (request.pinnedFor(RING) != null) return spec;
        Loadout vl = lightbearerSwap(dataset, base.loadout, singletonList(request), owned);
        if (vl == null) return spec;
        RiskGuard risk = RiskGuard.of(dataset, request);
        if (risk != null && risk.vetoes(vl, null)) return spec;
        DpsResult variant = new DpsCalculator().calculate(request, vl);
        if (variant == null) return spec;
        SpecPick variantSpec = bestSpec(dataset, request, variant, owned, rt, rt != null);
        if (variantSpec == null) return spec;
        double bt = base.net + spec.dpsAdded;
        double variantTotal = variant.net + variantSpec.dpsAdded;
        if (variantTotal > bt + EPS) {
            results.set(0, variant);
            return variantSpec;
        }
        return spec;
    }

    // main: the worn set's result the spec swaps into (null = nothing to
    // spec from). keepZero: keep a restricted (user-PINNED) weapon even
    // when it adds ~0 dps, so the pin always displays with an honest
    // number instead of silently vanishing (field report 2026-08-09: a
    // pinned Tonalztics showed nothing on a set it did not help).
    SpecPick bestSpec( LoadoutData dataset, OptimizationRequest request, DpsResult main, OwnedItems owned,
        Set<Integer> rt, boolean keepZero) {
        if (main == null) return null;
        MonsterStats monster = request.monster;
        var calculator = new DpsCalculator();
        SpecPick best = null;
        // Request-level risk constants, hoisted out of the weapon scan (they
        // were rebuilt - floor Loadout + a HashSet - per candidate weapon).
        RiskGuard risk = RiskGuard.of(dataset, request);
        // A Lightbearer in the set doubles special-energy regen (10%/15s vs
        // 10%/30s), so more specs fit the kill - detected by ring name, no id.
        GearItem ring = main.loadout.get(RING);
        boolean l2 = ring != null && ring.nameLower.contains("lightbearer");
        // Spec weapons are weapons by definition (SpecialAttack.match rejects
        // every other slot), so only the weapon partition needs scanning.
        // A group's spec stipulation (the Wardens' dragon dagger: nothing
        // breaks into the next phase faster - Andrew 2026-09-10) narrows the
        // pool to that family whenever one is at hand; otherwise the free pick.
        String family = specStipulation(monster);
        boolean stipulated = family != null && dataset.getGearItems(WEAPON).stream()
            .anyMatch(i -> i.nameLower.contains(family) && available(owned, request, i.id));
        for (GearItem item : dataset.getGearItems(WEAPON)) {
            if (rt != null && !rt.contains(item.id) || stipulated && !item.nameLower.contains(family)) continue;
            if (!eligible(dataset, request, item)
                // Sims are pretend-owned EVERYWHERE, including here: the
                // worn-set machinery honoured dreams but the spec pool
                // checked raw ownership only, so a simmed spec weapon
                // could never compete (field report 2026-08-06: simmed
                // Burning claws vs Barrows still recommended the owned
                // abyssal dagger).
                || !available(owned, request, item.id)) {
                continue;
            }
            // ANY style's spec competes (field request: the spec swap is its
            // own weapon switch - magic set + chally is a real play).
            SpecialAttack spec = SpecialAttack.match(item);
            if (spec == null) continue;

            // In a wilderness low-risk set the carried spec weapon competes
            // for kept slots like everything else - the whole package (worn
            // set + this weapon) must stay within the total risk budget.
            if (risk != null && risk.vetoes(main.loadout, item)) continue;
            GearItem[] ao = new GearItem[1];
            Loadout loadout = specLoadout(dataset, main.loadout, item, owned, request, ao);
            if (loadout == null) continue;
            // A spec fires under ITS OWN style, so a style the monster is
            // immune to bars the spec outright. At sea that is the melee
            // ban (field-verified, Andrew 2026-08-31: "ranged spec weapons
            // work but not mele"); on land it bars e.g. a chally spec at a
            // melee-immune mob the cross-style rule used to let through.
            if (isImmune( monster, spec.style, loadout, null)) continue;
            // A cross-style spec rolls under ITS OWN style's math - the
            // assume-best-prayer model carries all three books' factors,
            // matching the real play of flicking (e.g.) Piety for the spec.
            OptimizationRequest br = spec.style == request.style ? request : request.withStyle(spec.style);
            DpsResult base = calculator.calculate(br, loadout);
            if (base == null || base.maxHit <= 0) continue;
            double expected = specDamage(spec, base, request);
            // Rank by the DPS the spec ADDS over just attacking - marginal
            // (every spec fired gives up a main-hand hit), regen-aware over the
            // fight, and drain-inclusive for every style (field direction
            // 2026-07-19). A spec that adds nothing loses to carrying no slot.
            double added = specDpsAdded(calculator, spec, base, expected, request, main, l2);
            if (added <= 1e-4 && !keepZero) continue;
            double ba = best == null ? Double.NEGATIVE_INFINITY : best.dpsAdded;
            // Ties (identical stats across poison tiers) prefer the higher
            // tier - the venom is free spec damage the model does not price.
            if (best == null || added > ba + EPS || (added > ba - EPS && item.poisonTier() > best.weapon.poisonTier())) {
                SpecPick up = null;
                if (loadout.get(SHIELD) == null && !item.twoHanded && !request.isRiskConstrained()) {
                    for (GearItem shield : dataset.getGearItems(SHIELD)) {
                        StatBlock b = shield.bonuses;
                        if (max(max(b.strength, b.rangedStrength), b.magicDamage) <= 0
                            || !eligible(dataset, request, shield) || !available(owned, request, shield.id)) {
                            continue;
                        }
                        DpsResult with = calculator.calculate(br, withSlot(loadout, SHIELD, shield));
                        double damage = with == null ? 0 : specDamage(spec, with, request);
                        if (damage > (up == null ? expected : up.expectedDamage) + EPS) {
                            up = new SpecPick(spec, item, damage, specDpsAdded(calculator, spec, with,
                                damage, request, main, l2), ao[0], shield, null);
                        }
                    }
                }
                best = new SpecPick(spec, item, expected, added, ao[0], null, up);
            }
        }
        return best;
    }

    // Defence-drain specs (DWH/BGS/elder maul) are worth more than their
    // hit: a landed drain raises the main set's DPS for the REST of the
    // kill. Valued as land-chance x dps-gain-at-drained-defence x expected
    // remaining fight (monster hp / current dps) - which is exactly why
    // drains shine on high-HP, high-defence targets and are pointless on
    // throwaway mobs.
    // The DPS this spec ADDS to the kill over just attacking with the main
    // set - its win-over-replacement value, the ranking key and the number
    // the card shows (field direction 2026-07-19). Three ingredients:
    // <ul>
    // <li><b>Marginal.</b> Every spec fired gives up a main-hand attack, so
    //     the per-use win is {@code specDamage - replacedAuto}, never the raw
    //     spec damage.</li>
    // <li><b>Regen-aware over the fight.</b> The opening 100% bar
    //     ({@code 100/cost} uses) amortises across the kill length, on top of
    //     the sustained regen-fed rate (10%/30s, doubled by a Lightbearer) -
    //     a longer kill fits more specs.</li>
    // <li><b>Drain, for every style.</b> A landed defence-drain lowers the
    //     Defence LEVEL, which ranged and magic roll against too, not only
    //     melee (v0.3.1 valued the drain for melee main-hands only). The
    //     player fishes for the land with the specs the energy budget
    //     allows - P(landed) over the attempts, charged the EXPECTED specs
    //     spent (fishing stops on the land) - so a Lightbearer's doubled
    //     regen buys real drain probability where the budget limits
    //     fishing (field report 2026-08-08).</li>
    // </ul>
    // A weapon is used the better of the two ways (damage or drain), so the
    // value is their max, not their sum - a small undercount for a spec that
    // genuinely does both at once (BGS), accepted for v1.
    // The spec energy available per kill at SUSTAINED pace (field
    // direction 2026-08-08: "sustained is more realistic for 99% of
    // outings" - a 50-200 mob grind is regen-bound, so the opening bar
    // amortises to nothing and specs per kill go fractional): regen
    // (10%/30s, doubled by a Lightbearer) over the kill, plus - with
    // Death Charge assumed - one 15% refund per cast window, scaled down
    // when kills come faster than the window (the refund needs a killing
    // blow in it). Yama's rite of vile transference upgrades the spell to
    // refund from TWO kills per cast, an effective 30s window (level 2).
    // The old burst frame (every kill opens on a full 100% bar) is
    // roadmapped as a POH-pool mode. Package-private: the deterministic
    // unit under DeathChargeTest.
    static double specEnergyOverKill(double ts, boolean l2, int dcl, boolean freshBar) {
        double regenPerSec = l2 ? 10.0 / 15.0 : 10.0 / 30.0;
        double window = dcl >= 2 ? 30.0 : 60.0;
        // A one-off fight opens on a full bar (fight-length design, 2026-10-18).
        return (freshBar ? 100.0 : 0.0) + regenPerSec * ts + (dcl > 0
                ? 15.0 * ts / max(window, ts) : 0.0);
    }

    double specDpsAdded( DpsCalculator calculator, SpecialAttack spec, DpsResult specBase, double expected,
        OptimizationRequest request, DpsResult mr, boolean l2) {
        MonsterStats monster = request.monster;
        double md = mr.net;
        if (md <= 0.01) return 0;
        double ts = min(600, max(0.6, monster.hitpoints / md));
        double replacedAuto = mr.expectedHit + md * spec.extraTicks(specBase) * 0.6;
        double specCycle = max(0.6, specBase.attackSpeed * 0.6);

        // How many specs fire per kill at SUSTAINED pace: the regen-bound
        // budget, fractional across kills (0.5 = a spec every other kill),
        // but never more than the fight has time for - so a mob that dies
        // in one hit gets no spec value, and a long fight fits more. The
        // burst frame's floor() was an artifact of pricing one kill from a
        // full bar; sustained throughput is a rate, not a count.
        double energyOverKill = specEnergyOverKill(ts, l2, request.deathCharge, startsWithFullBar(monster));
        double usesByEnergy = energyOverKill / max(1, spec.energyCost);
        double ubt = floor(ts / specCycle);
        double uses = max(0, min(usesByEnergy, ubt));

        // DAMAGE use: each spec fired wins its damage over the main-hand hit it
        // replaces, amortised across the kill.
        double marginal = max(0, expected - replacedAuto);
        double damageDps = uses * marginal / ts;

        // DRAIN use: a landed drain lifts the whole set's dps for the rest of
        // the fight - for EVERY style, since the drop is to the Defence LEVEL
        // (v0.3.1 valued this for melee main-hands only). The player FISHES
        // for the land with the specs the energy budget allows - P(landed)
        // = 1 - (1-p)^attempts, each attempt charging its replaced auto
        // (v1 priced exactly one attempt, which made drain value blind to
        // spec regen - the whole reason a Lightbearer rides DWH fights;
        // field report 2026-08-08). Attempts stop once a land is likely:
        // ~1/p tries, never more than the budget.
        double dd = 0;
        if (spec.drainsDefence() && ubt >= 1) {
            int drained = spec.drainedDefence(monster, expected);
            // Per-boss defence floors (competitor audit 2026-08-08, from
            // the official calc): drain stops at the floor - a DWH at Nex
            // buys 10 levels, at Verzik/Vardorvis nothing at all. Without
            // the clamp the fishing model overvalues drain specs there.
            drained = max(drained, DefenceFloors.floorFor(monster));
            if (drained < monster.defence) {
                DpsResult after = calculator.calculate( request.withMonster(monster.withDefence(drained)), mr.loadout);
                if (after != null && after.net > md) {
                    double p = max(0.01, spec.landChance(specBase));
                    // Fractional attempts are the sustained regime: 0.5
                    // attempts per kill = fishing lands on half the kills
                    // it is tried across.
                    double attempts = min(uses, ceil(1.0 / p));
                    double landed = 1 - pow(1 - p, attempts);
                    // Fishing stops on the land, so the cost is the EXPECTED
                    // specs spent - (1-(1-p)^n)/p, the truncated geometric mean
                    // - not the full attempt cap (which overcharges exactly
                    // when the land comes early).
                    double spent = landed / p;
                    dd = landed * (after.net - md) - spent * replacedAuto / ts;
                }
            }
        }
        return max(damageDps, dd);
    }

    // The base set with the spec weapon swapped in, or null if unusable.
    // owned == null -> any standard ammo may be picked (game-best spec).
    Loadout specLoadout(LoadoutData dataset, Loadout baseSet, GearItem weapon, OwnedItems owned, OptimizationRequest request,
        GearItem[] ao) {
        Loadout out = withSwap(baseSet, weapon);
        if (!compatible(out.get(AMMO), weapon)) {
            GearItem replacement = null;
            for (GearItem ammo : dataset.getGearItems(AMMO)) {
                if (ammo.standardGear && !dataset.isVariant(ammo.id) && !request.isExcluded(ammo.id)
                    && (owned == null || owned.owns(ammo.id)) && compatible(ammo, weapon) && (replacement == null
                        || ammo.bonuses.rangedStrength > replacement.bonuses.rangedStrength)) {
                    replacement = ammo;
                }
            }
            if (replacement == null && !compatible(null, weapon)) {
                return null; // needs ammo that is not available
            }
            if (replacement != null) ao[0] = replacement;
            out = withSlot(out, AMMO, replacement);
        }
        return out;
    }

    // The per-style assumption bundle: your levels/boost/prayer label and
    // the game-ceiling versions. Shared by the single-mob and roster paths
    // (they MUST agree - levels and labels depend on style + owned + real,
    // never the mob).
    @AllArgsConstructor
    static class StylePlan {
        final PlayerLevels levels;
        final String label;
        final PlayerLevels gameLevels;
        final String gameLabel;
    }

    // Decorate a request with the user's prayer pick: NONE = prayerless,
    // a named tier = that tier's factors for this style (detect otherwise).
    static OptimizationRequest withPrayerPick(OptimizationRequest r,
        ComputeContext ctx, CombatStyle style, PlayerLevels levels, PrayerUnlocks unlocks) {
        String pick = ctx.prayerPicks.get(style);
        if (pick == null) return r;
        return r.withPrayers("NONE".equals(pick) ? NONE : forPick(style, pick, bestAvailable(levels, unlocks)));
    }

    // The user's boost pick for this style (assume-chip picker), or null
    // for detect; "NONE" maps to the unboosted profile.
    static BoostProfile boostPickFor(ComputeContext ctx, CombatStyle style) {
        String pick = ctx.boostPicks.get(style);
        if (pick == null) return null;
        try {
            return BoostProfile.valueOf(pick);
        } catch (IllegalArgumentException bad) { return null; }
    }

    // The assumed-prayer label part: the user's pick (null part when
    // prayerless), or detect's best-available tier.
    static String prayerLabelFor(ComputeContext ctx, CombatStyle style, PlayerLevels levels, PrayerUnlocks unlocks) {
        String pick = ctx.prayerPicks.get(style);
        if (pick == null) return bestAvailable(levels, unlocks).nameFor(style);
        return "NONE".equals(pick) ? null : pick;
    }

    static StylePlan stylePlan(ComputeContext ctx, CombatStyle style, BoostProfile supplied) {
        BoostProfile userBoost = boostPickFor(ctx, style);
        BoostProfile boost = userBoost != null ? userBoost : supplied != null ? supplied
            // A risk-capped trip (kept slots >= 0) dies to a player: the
            // Wilderness or a PvP world - the page only constrains there.
            : BoostSelector.bestFor(style, ctx.effectiveOwned, ctx.p.f2pOnly, ctx.p.maxTradeables >= 0);
        PlayerLevels levels = ctx.real.boosted(boost, ctx.boostedLevels).max(ctx.boostedLevels);
        String label = joinAssumes(prayerLabelFor(ctx, style, levels, ctx.unlocks), boost == BoostProfile.NONE ? null : boost.toString());
        // The ceiling assumes the best prayers/boost in the GAME, not just
        // what this player has unlocked or owns.
        BoostProfile gameBoost = userBoost != null ? userBoost : supplied != null ? supplied
            : BoostSelector.ceilingFor(style, ctx.p.f2pOnly);
        PlayerLevels gameLevels = ctx.real.boosted(gameBoost, ctx.boostedLevels).max(ctx.boostedLevels);
        String gameLabel = joinAssumes(prayerLabelFor(ctx, style, gameLevels, ctx.p.f2pOnly ? F2P : ALL),
            gameBoost == BoostProfile.NONE ? null : gameBoost.toString());
        return new StylePlan(levels, label, gameLevels, gameLabel);
    }

    // A multi tool's pins and spell, in place of the mob's own.
    static class ToolPick {
        Map<GearSlot, Integer> pins = Map.of();
        Map<GearSlot, Integer> gamePins = Map.of();
        SpellStats spell;
    }

    // The best-OWNED request for one mob at one style - shared verbatim by
    // the single-mob and roster paths (they MUST agree). Pins shape YOUR
    // set only; a pinned autocast spell forces the magic search around it;
    // a multi tool (nullable) brings its own pins and spell.
    OptimizationRequest ownedRequestFor(ComputeContext ctx, MonsterStats mob,
        CombatStyle style, PlayerLevels sl, ToolPick tool) {
        OptimizationRequest r = request(ctx, mob, style, sl, ctx.unlocks, ctx.requirements,
            ctx.p.upgradeBudgetGp > 0 ? CandidateMode.OWNED_OR_BUDGET : CandidateMode.OWNED_ONLY,
            3, max(0, ctx.p.upgradeBudgetGp)) .withDreamItems(withForcedRequired(ctx, mob, style, dreamsFor(ctx, mob)))
            .withPinnedItems(tool != null ? tool.pins : pinsFor(ctx, style, mob))
            .withWaivers(ctx.p.kourendElite ? Set.of(KOUREND_ELITE) : Set.of());
        r = withPrayerPick(r, ctx, style, sl, ctx.unlocks);
        if (tool != null) r = r.withMultiStack(ctx.p.multiStack);
        SpellStats spell = tool != null && tool.spell != null ? tool.spell : spellFor(ctx, mob);
        if (style == MAGIC && spell != null) r = r.withSpell(spell);
        return r;
    }

    // Required slayer gear you own none of (ISS-01M5JJ0Z4M988DR0M0ZGJHD139,
    // Andrew 2026-10-22: "we should pin/require those by default and show
    // them as necessary even when the player doesn't own those items"): the
    // first acceptable item you can equip rides as if owned for this
    // request, so the card wears it - marked not owned - instead of the
    // requirement quietly lapsing. Soft rules and a pinned slot never force.
    Set<Integer> withForcedRequired(ComputeContext ctx, MonsterStats mob, CombatStyle style, Set<Integer> dreams) {
        Map<GearSlot, Integer> pins = pinsFor(ctx, style, mob);
        Set<Integer> out = null;
        for (RequiredGear.Rule rule : rulesFor(mob, ctx.p.kourendElite ? Set.of(KOUREND_ELITE) : Set.of())) {
            if (rule.soft || pins.containsKey(rule.slot)) continue;
            Set<Integer> ids = rule.ids(ctx.dataset);
            if (ids.stream().anyMatch(id -> ctx.effectiveOwned.owns(id) || dreams.contains(id))) continue;
            for (int id : ids) {
                GearItem item = ctx.dataset.getGear(id);
                if (item != null && ctx.requirements.canEquip(item.requirements)) {
                    if (out == null) out = new HashSet<>(dreams);
                    out.add(id);
                    break;
                }
            }
        }
        return out == null ? dreams : out;
    }

    // The game-ceiling request: every obtainable item you can actually
    // equip, no pins - so Yours and BiS differ by OWNERSHIP alone, which is
    // the gap the comparison is meant to isolate.
    // <p>This gated on RequirementProfile.MAXED until 2026-08-27. The intent
    // was a pure gear gap, but it introduced a second difference between the
    // two sides and, on a low-level account, named gear the player cannot
    // wear: Obor's BiS came back a rune scimitar at 10 Attack, a DPS that can
    // never occur, since reaching 40 Attack changes the number.
    OptimizationRequest gameRequestFor(ComputeContext ctx, MonsterStats mob,
        CombatStyle style, PlayerLevels gameLevels, ToolPick tool) {
        // The max cape options (ISS-01M5T53FBMHVMJTDPS5ZW4WS50, Andrew
        // 2026-10-25: "any maxxed player or anyone simming as max should always
        // see max cape options instead of the alternative" ... "the yours view
        // could be based on what you have, the BIS should show the max cape"):
        // a simmed max cape waives every combo, owned half or not; a maxed or
        // simmed ceiling drops the base capes the combos tie with.
        RequirementProfile req = ctx.requirements.withAllQuests();
        if (ctx.p.simMaxCape) req = req.waiving(ids(data, id -> true));
        OptimizationRequest r = request(ctx, mob, style, gameLevels, ALL, req, CandidateMode.ALL_STANDARD, 1, 0)
            // The ceiling drinks a super antifire and casts with sunfire runes:
            // its shield slot is for dps (Andrew 2026-10-19: best aligns with
            // the highest-dps option).
            .withAntifirePotion(true).withSunfireRunes(true)
            // The true ceiling ignores your exclusions - they shape Yours and
            // the swaps strip (Andrew 2026-10-22: "BiS ignores exclusions").
            .withExcludedItems(wearable(data, req) ? bases(data) : Set.of())
            // The ceiling is a finished account: no Karuulm boots needed.
            .withWaivers(Set.of(KOUREND_ELITE));
        if (tool != null) {
            r = r.withPinnedItems(tool.gamePins).withMultiStack(ctx.p.multiStack);
            if (tool.spell != null) r = r.withSpell(tool.spell);
        }
        return withPrayerPick(r, ctx, style, gameLevels, ctx.p.f2pOnly ? F2P : ALL);
    }

    // The request both the owned and the game-best sides start from: the
    // mob, style and levels, the best prayers at those levels and unlocks
    // (the spell auto-picked, untradeables counted, budgetGp = the total
    // upgrade spend OWNED_OR_BUDGET allows) and the query-wide settings.
    OptimizationRequest request(ComputeContext ctx, MonsterStats mob, CombatStyle style,
        PlayerLevels levels, PrayerUnlocks unlocks, RequirementProfile requirements, CandidateMode mode, int limit, int budgetGp) {
        return new OptimizationRequest(mob, style, levels, bestAvailable(levels, unlocks),
            null, budgetGp, mode, true, ctx.p.onSlayerTask, ctx.effectiveOwned, requirements, limit)
            .withExcludedItems(excludedFor(ctx, style, mob)) .withSpellbookLock(ctx.lock)
            .withMaxTradeables(ctx.p.maxTradeables).withRiskBudgetGp(ctx.riskBudget)
            .withAntifirePotion(ctx.p.antifirePotion).withAntifireTier(ctx.p.antifireTier)
            .withSunfireRunes(ctx.p.sunfireRunes) .withDeathCharge(ctx.p.deathCharge)
            .withInWilderness(ctx.p.inWilderness) .withProtectOnlyItems(ctx.protectOnly).withSetRules(ctx.p.setRules);
    }

    LoadoutData f2pView() {
        LoadoutData view = f2pData;
        if (view == null) {
            view = data.freeToPlayView();
            f2pData = view;
        }
        return view;
    }

    // Drop all cached results - account or profile switched. The optimize
    // cache goes too: its key carries no quest or diary state.
    public void clearCache() {
        synchronized (cache) { cache.clear(); }
        synchronized (optimizeCache) { optimizeCache.clear(); }
    }

    public void shutdown() {
        worker.shutdownNow();
        rosterPool.shutdownNow();
        clearCache();
    }
}
