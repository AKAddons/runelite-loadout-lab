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

/**
 * Runs BiS searches off the game threads and caches results.
 *
 * <p>Caching (founding requirement): results are keyed PER STYLE by
 * (collection fingerprint, monster id, levels, f2p, ..., style, that style's
 * pins) - the fingerprint changes iff ownership changes, so a cache hit is
 * always current, and per-style state (pins) never invalidates the other
 * styles' answers. LRU-bounded.
 *
 * <p>Each query answers TWO questions per style: the best set you OWN, and
 * the best set that exists in the game - so the panel can show how close
 * your gear is to the ceiling.
 *
 * <p>Threading: optimize() is CPU work - it runs on a single daemon worker,
 * never the EDT or client thread. Callbacks are NOT thread-marshalled here;
 * UI callers wrap in SwingUtilities.invokeLater.
 */
public class OptimizerService {
	private static final org.slf4j.Logger log =
		org.slf4j.LoggerFactory.getLogger(OptimizerService.class);

	/** Per-STYLE entries (a monster query stores three). */
	private static final int CACHE_MAX = 192;

	/** Which point of the offence/defence frontier to show - the dev-mode
	 * prototype of ISS-01M5G5CZC9SK46499FACRSJ8ZT. Andrew 2026-10-21:
	 * "balance = maximizing the DTPS/DPS ratio in an optimized way, tanky =
	 * minimum DTPS" - Balanced takes the least damage per damage dealt. */
	public enum OptimizeMode {
		MAX_DPS,
		BALANCED,
		TANKY
	}

	/** Frontier sweep weights, as multiples of maxDps/incoming; the 10.0
	 * extreme reaches the genuine minimum-intake end (491b6c0). */
	private static final double[] SWEEP_ALPHAS = {0.3, 0.7, 1.5, 3.0, 10.0};

	/** What a defensive mode did to the shown set: the DPS it gave up and
	 * the damage taken it cut, whole percents vs the max-DPS set - or, when
	 * it kept that set, why (reason non-null). */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	public static final class ModeTrade {
		public final OptimizeMode mode;
		public final int dpsLossPct;
		public final int dmgCutPct;
		public final String reason;
	}

	/** One step on a slot ladder: the item, the set's DPS and damage taken
	 * with it swapped in, and whether you own it. */
	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	public static final class Rung {
		public final GearItem item;
		public final double dps;
		public final double dtps;
		public final boolean owned;
		/** The ammo a weapon step brings when it changes the worn ammo
		 * (ISS-01M5H2AH8ERQG471AZY3S67FKW); null otherwise. */
		public final GearItem with;
		/** The shield a one-handed weapon step brings when it changes the
		 * worn one (weapon + offhand vs a two-hander, ISS-01M5JFG00G1BS82X9X2QNZZC7S). */
		public GearItem offhand;
		/** A group step's dps for EACH member (index-aligned with the roster):
		 * the card shows its own mob's number, the group figure ranks
		 * (ISS-01M5PE4TZXTGJAQA1YXNRKPEDW). Null on a solo ladder. */
		public double[] perMob;
		/** The swapped set behind a solo step: what the card adopts when an
		 * owned step outranks the worn item (ISS-01M5PKSQBAP823XNC13QBW8830). */
		DpsResult set;
	}

	/** A slot's ladder (ISS-01M5GE9EJSX6YWCKMSYEWKZ132): the steps just above
	 * the worn item (any item, closest first) and just below (owned only,
	 * closest first), ranked by the mode the set was picked for. Shown in
	 * game as the slot's "swaps". */
	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	public static final class Ladder {
		/** Steps each way by default... */
		public static final int RUNGS = 2;
		/** ...and always this many beside the worn item when the steps exist. */
		public static final int SPOTS = 2 * RUNGS;
		/** ...and this many further steps each way kept for the strip's
		 * "more" pages (ISS-01M5QD7PNMCMNRZD92RPRMHWRP, Andrew 2026-10-24: "an
		 * option to 'show more' on the ladder to see more alternatives"). */
		public static final int MORE = 10;
		/** The steps past up/down, closest first - the pages after the strip. */
		public List<Rung> upMore = List.of();
		public List<Rung> downMore = List.of();
		/** Scored across a group search's members, HP-weighted, on the
		 * group's shared kit (ISS-01M5JAGFEJ5QHXE37B37XM8HJJ). */
		public boolean group;
		/** The worn item's dps per member on a group ladder (null solo). */
		public double[] basePerMob;
		/** The weapon slot only: every step the strip could show, best first,
		 * for the "All weapons" list (ISS-01M5PE4TZXTGJAQA1YXNRKPEDW). */
		public List<Rung> all;
		/** The magic weapon slot only: the same steps split by kind - powered
		 * staves and autocast staves, each around the worn weapon (Andrew
		 * 2026-10-24: "for magic just show two ladders: a powered and a non
		 * powered one so we don't need to do the math of combining them").
		 * Null elsewhere, and for a kind with no step. */
		public Ladder powered;
		public Ladder autocast;

		/** How many steps up and down to show (ISS-01M5H13V0TVF58RHPHT8WQ1GFP,
		 * Andrew 2026-10-21: "when the user has bis show the 4 preceding"):
		 * two each way, a short side's spare spots going to the other. */
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

	/** Per-style outcome: your best owned sets, the game-wide best set, and
	 * the strongest special-attack weapon for each - owned and game-wide. */
	public static final class StyleResult {
		/** The defensive mode's trade on the Yours side; null on Max DPS. */
		public ModeTrade modeTrade;
		/** GWD protection CARRIED on each side (worn on the way in, swapped
		 * off for the fight) - also in the bench; empty away from GWD. */
		public List<GearItem> protection = List.of();
		public List<GearItem> gameProtection = List.of();
		public Map<GearSlot, Ladder> ladder = Map.of();
		/** The carried spec weapon's swaps, ranked by DPS added; null when
		 * no spec is carried (ISS-01M5HCS725N7XVVBC7CMK6Y43A). */
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
		/** The prayers/boost YOUR numbers assume ("Deadeye + Ranging potion"). */
		public final String boostLabel;
		/** The ceiling assumption for game best ("Rigour + Ranging potion"). */
		public final String gameBoostLabel;
		/** What the boss does back to you in the shown owned set (nullable). */
		public final Result incoming;
		/** Same, in the game-best set - the BiS side's DTPS (nullable). */
		public final Result gameIncoming;
		/** The INVENTORY vs this mob on the Yours side: the trip plan's
		 * items not currently worn (spec weapon first). */
		public final List<GearItem> bench;
		/** Same, for the BiS side's kit. */
		public final List<GearItem> gameBench;
		/** True when the Yours half is something the CARRIED KIT can
		 * actually assemble mid-trip (or no kit ran, so the shared set IS
		 * the trip). False marks a tab-only fallback - "what this style
		 * could do if you geared for it" - which must never win the mob
		 * row (field bug 2026-08-06: the vents row showed a whole ranged
		 * set on a melee kit with Inventory 3). */
		public final boolean ownedKitBacked;
		/** Same contract for the BiS half. */
		public final boolean gameKitBacked;
		/** Slots of the Yours set where some item in the game beats the
		 * worn one with the rest of the set fixed - no gold border there
		 * (Andrew, field 2026-09-27: an occult on a Fire Bolt card). */
		public Set<GearSlot> beaten = Set.of();
		/** The Multi card rows (tool key -> per-target result) when the
		 * switch is on and the mob is fought in multi; null otherwise. */
		public Map<String, StyleResult> multiTools;

		StyleResult(List<DpsResult> owned, DpsResult overallBest,
			SpecPick spec, SpecPick gameSpec, String boostLabel, String gameBoostLabel,
			Result incoming, Result gameIncoming,
			List<GearItem> bench, List<GearItem> gameBench,
			boolean ownedKitBacked, boolean gameKitBacked) {
			this.ownedKitBacked = ownedKitBacked;
			this.gameKitBacked = gameKitBacked;
			this.bench = bench == null ? List.of()
				: Collections.unmodifiableList(bench);
			this.gameBench = gameBench == null ? List.of()
				: Collections.unmodifiableList(gameBench);
			this.boostLabel = boostLabel;
			this.gameBoostLabel = gameBoostLabel;
			this.incoming = incoming;
			this.gameIncoming = gameIncoming;
			this.owned = owned;
			this.overallBest = overallBest;
			this.spec = spec == null ? null : spec.spec;
			this.specWeapon = spec == null ? null : spec.weapon;
			this.specExpectedDamage = spec == null ? 0 : spec.expectedDamage;
			this.specDpsAdded = spec == null ? 0 : spec.dpsAdded;
			this.gameSpec = gameSpec == null ? null : gameSpec.spec;
			this.gameSpecWeapon = gameSpec == null ? null : gameSpec.weapon;
			this.gameSpecExpectedDamage = gameSpec == null ? 0 : gameSpec.expectedDamage;
			this.gameSpecDpsAdded = gameSpec == null ? 0 : gameSpec.dpsAdded;
		}
	}

	private final LoadoutData data;
	private final LoadoutOptimizer optimizer = new LoadoutOptimizer();

	/** Stamp the Yours set's beaten slots on a fresh result. */
	private StyleResult verified(StyleResult result, LoadoutData dataset,
		OptimizationRequest own, OptimizationRequest game) {
		if (own != null && game != null && !emptyBest(result.owned)) {
			result.beaten = optimizer.beatenSlots(dataset, own, game, result.owned.get(0));
		}
		return result;
	}

	/** Roster fan-out pool (field fix 2026-07-17: a 15-mob raid ran ~90
	 * independent optimize() calls serially - THE wall, not the kit
	 * search). Small and near-minimum priority so the client thread never
	 * starves; each task builds its own LoadoutOptimizer because the
	 * optimizer's internal DpsCalculator is stateful. Fixed size rather
	 * than sized to the host: the Plugin Hub bans java.lang.Runtime, so
	 * availableProcessors() is unavailable - 4 min-priority daemon threads
	 * clear the serial wall without oversubscribing a modest machine.
	 * The 4 is also what the #14014 review thread was told, so it stays 4
	 * until a widening is deliberately decided and re-verified - a wider
	 * pool rode along uninvited once (2026-08-05, reverted same day). */
	private static final int ROSTER_POOL_SIZE = 4;
	private final ExecutorService rosterPool =
		Executors.newFixedThreadPool(
			ROSTER_POOL_SIZE, r -> {
				var t = new Thread(r, "loadout-lab-roster");
				t.setDaemon(true);
				t.setPriority(Thread.MIN_PRIORITY + 1);
				return t;
			});
	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		var t = new Thread(r, "loadout-lab-optimizer");
		t.setDaemon(true);
		// A full search pegs a core for seconds; at default priority that
		// competes with the client thread for CPU (field report: the game
		// felt choppy while recomputing). Background-priority compute only.
		t.setPriority(Thread.MIN_PRIORITY + 1);
		return t;
	});

	/** Lazily-built free-to-play view of the dataset (gear filtered by the members flag). */
	private volatile LoadoutData f2pData;

	/**
	 * Supersession: every bestPerStyle call takes a fresh ticket; an
	 * in-flight computation checks it at each checkpoint and abandons
	 * itself the moment a newer request exists (toggling slayer/f2p/risk
	 * mid-load must not make the new answer wait behind the stale one,
	 * nor let stale results flash in). Panel-driven: only the newest
	 * request ever matters.
	 */
	private final AtomicLong requestSeq = new AtomicLong();
	/** Abandoned-computation count - test observability only. */
	volatile int abandonedForTest;

	/**
	 * Results cached PER STYLE, not per query: pins are per-style state, so
	 * pinning a melee item must not throw away the ranged and magic answers
	 * (a pin toggle was recomputing all three - the reported-choppy path).
	 * The key is the query key minus pins, plus the style and ITS pins.
	 */
	private final Map<String, StyleResult> cache =
		new LinkedHashMap<String, StyleResult>(32, 0.75f, true) {
			protected boolean removeEldestEntry(Map.Entry<String, StyleResult> eldest) { return size() > CACHE_MAX; }
		};

	public OptimizerService(LoadoutData data) { this.data = data; }

	/**
	 * Compute, per melee/ranged/magic: the best OWNED set and the game-wide
	 * best set against a monster. Cached; the callback runs on the worker
	 * thread on a miss and synchronously on a hit.
	 */
	public void bestPerStyle(
		MonsterStats monster,
		PlayerLevels realLevels,
		PlayerLevels boostedLevels,
		PrayerUnlocks prayerUnlocks,
		RequirementProfile requirements,
		OwnedItems owned,
		int collectionFingerprint,
		ComputeParams p,
		Map<CombatStyle, Set<Integer>> excludedByStyle,
		Set<Integer> dreamItems,
		Map<CombatStyle, Map<GearSlot, Integer>> pinnedByStyle,
		SpellStats pinnedSpell,
		int pinnedSpec,
		Set<Integer> protectOnlyItems,
		Consumer<Map<CombatStyle, StyleResult>> callback) {
		int id = monster.profileId();
		bestPerStyle(monster, buildContext(realLevels, boostedLevels, prayerUnlocks,
			requirements, owned, collectionFingerprint, p, excludedByStyle, dreamItems,
			Collections.singletonMap(id, pinnedByStyle), Collections.singletonMap(id, pinnedSpell),
			Collections.singletonMap(id, pinnedSpec), protectOnlyItems), callback);
	}

	/** The single-mob compute on a built context: a synchronous answer when
	 * every style is cached, else one worker job. A one-mob roster takes
	 * this road too, its member's own exclusions and sims on the context. */
	private void bestPerStyle(MonsterStats monster, ComputeContext ctx,
		Consumer<Map<CombatStyle, StyleResult>> callback) {
		ctx.mode = OptimizeMode.valueOf(ctx.p.optimizeMode);
		ctx.gwdAncient = ctx.p.gwdAncient;
		final String baseKey = baseKeyFor(monster, ctx);
		Map<CombatStyle, StyleResult> allCached = new EnumMap<>(CombatStyle.class);
		synchronized (cache) {
			for (CombatStyle style : concreteValues()) {
				StyleResult hit = cache.get(styleKey(baseKey, style, ctx, monster));
				if (hit == null) {
					allCached = null;
					break;
				}
				allCached.put(style, hit);
			}
		}
		if (allCached != null) {
			callback.accept(allCached);
			return;
		}
		submit(ticket -> computeAllStyles(monster, ctx, ticket), () -> new EnumMap<>(CombatStyle.class),
			"compute failed for {}", monster.getName(), callback);
	}

	/** One compute on the worker under a fresh ticket: abandoned when
	 * superseded while queued, mid-flight (a null answer) or after it. A
	 * throw must still answer (the fallback), or the card sits on Computing
	 * for good (TonyDAnnunzio 2026-10-18). */
	private <T> void submit(LongFunction<T> compute, Supplier<T> fallback, String failure, String name,
		Consumer<T> callback) {
		final long ticket = requestSeq.incrementAndGet();
		worker.execute(() -> {
			if (requestSeq.get() != ticket) {
				abandonedForTest++;
				return; // superseded while queued
			}
			T results;
			try {
				results = compute.apply(ticket);
			} catch (RuntimeException ex) {
				log.warn(failure, name, ex);
				results = fallback.get();
			}
			if (results == null || requestSeq.get() != ticket) {
				abandonedForTest++;
				return; // superseded during or after the compute
			}
			callback.accept(results);
		});
	}

	/** Every query-wide value the per-style compute needs, gathered once so
	 * the roster path can run computeAllStyles for each mob. */
	private static final class ComputeContext {
		/** The page's compute settings as handed in; the fields below are
		 * the ones the context derives or normalises from them. */
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
		/** The bench size: carried items beyond the worn set; the spec
		 * weapon occupies a slot when it differs from the worn weapon. */
		int maxSwaps = 1;
		/** PER-MOB pins (profileId -> style -> slot -> item), pinned spell
		 * and pinned spec, keyed like excludedByMob: a mixed roster reads
		 * each member's own (ISS-01M4Y2VJ8DWBBKFR1HP9NR45NG); the single-mob
		 * path wraps its three arguments into one-entry maps. */
		Map<Integer, Map<CombatStyle, Map<GearSlot, Integer>>> pinsByMob;
		Map<Integer, SpellStats> spellByMob;
		/** A pinned SPEC weapon id (0 = auto-pick): forces the spec slot on
		 * the card whose style the weapon serves, leaving other styles to
		 * pick freely. The pin machinery's carried-slot twin; a roster
		 * seats its FIRST member's as the shared spec. */
		Map<Integer, Integer> specByMob;
		Map<CombatStyle, Set<Integer>> excluded;
		/** PER-MOB exclusions for rosters, keyed by MonsterStats.profileId()
		 * (field decision 2026-07-17): each mob's request carries its own
		 * exclusions on top of the global set - the group may still use the
		 * item, the excluding mob's answer never does. */
		Map<Integer, Map<CombatStyle, Set<Integer>>> excludedByMob = Map.of();
		/** PER-MOB sims (profileId -> ids): counted as owned for that mob
		 * only - the local twin of the global dream items. */
		Map<Integer, Set<Integer>> dreamsByMob = Map.of();
		/** The defensive mode (single-mob path only; rosters stay Max DPS). */
		OptimizeMode mode = MAX_DPS;
		/** The GWD Ancient chip: Zaros joins the route (the Nex bank). */
		boolean gwdAncient;
		/** This compute's request ticket - the mode sweep bails when superseded. */
		long ticket;
	}

	/** The spec-weapon restriction (0 = none): a user PIN forces the spec
	 * slot to that weapon on EVERY style card, not just its own - specs
	 * compete cross-style (a ranged Tonalztics is a real spec swap on a
	 * melee setup; a DWH on a magic set), so a pin that only bound its own
	 * style read as "the pin doesn't take" when viewing another card
	 * (field report 2026-08-09). A card the weapon cannot serve shows the
	 * pinned spec at its honest ~0 (keepZero), never a different auto pick. */
	private static Set<Integer> restrictSpec(ComputeContext ctx, CombatStyle style, MonsterStats mob) {
		int id = ctx.specByMob.getOrDefault(mob.profileId(), 0);
		if (id == 0) { return null; }
		GearItem pinned = ctx.dataset.getGear(id);
		return pinned != null && SpecialAttack.match(pinned) != null
			? Collections.singleton(id) : null;
	}

	/** The style's pins for ONE mob (its own profile's). A weapon pin binds
	 * only the styles its weapon fights with (ISS-01M5TH7V74GYQKRN231XRCM6BE,
	 * Andrew 2026-10-25: a trident pinned on the Enraged Warden left "only
	 * ... a magic set" though the bowfa was "a more applicaable range set"). */
	private static Map<GearSlot, Integer> pinsFor(ComputeContext ctx, CombatStyle style, MonsterStats mob) {
		Map<CombatStyle, Map<GearSlot, Integer>> byStyle = ctx.pinsByMob.get(mob.profileId());
		Map<GearSlot, Integer> pins = byStyle == null ? Map.of() : byStyle.getOrDefault(style, Map.of());
		Integer weapon = pins.get(GearSlot.WEAPON);
		GearItem item = weapon == null ? null : ctx.dataset.getGear(weapon);
		if (item == null || item.isWeaponFor(style)) { return pins; }
		Map<GearSlot, Integer> out = new EnumMap<>(pins);
		out.remove(GearSlot.WEAPON);
		return out;
	}

	private static SpellStats spellFor(ComputeContext ctx, MonsterStats mob) {
		return ctx.spellByMob.get(mob.profileId());
	}

	/** The per-style pin state that shapes a cached answer: the mob's pins
	 * and, on magic, its pinned spell. */
	private static String pinKey(ComputeContext ctx, CombatStyle style, MonsterStats mob) {
		SpellStats spell = spellFor(ctx, mob);
		return pinsFor(ctx, style, mob).hashCode()
			+ "|" + (style == MAGIC && spell != null ? spell.getName() : "");
	}

	/** Per-(mob, style, side) optimize results for the roster path, LRU.
	 * Keyed WITHOUT mode/inventory (they do not shape an optimize), so a
	 * chip tweak recomputes only the kit - not the ~2x3xN optimizes. */
	private final Map<String, List<DpsResult>> optimizeCache =
		new LinkedHashMap<String, List<DpsResult>>(256, 0.75f, true) {
			protected boolean removeEldestEntry(Map.Entry<String, List<DpsResult>> eldest) { return size() > 1024; }
		};

	private static String optimizeKey(MonsterStats mob, CombatStyle style, boolean game,
		ComputeContext ctx) {
		// getId() ALONE collides: a boss's versions share one id (Maggot
		// King's Far/Nearby/Roaring, field report 2026-08-20 - Nearby's
		// cached melee answer was served for the melee-IMMUNE Far row).
		// The version string is the missing discriminator.
		return mob.getId() + "|" + mob.getVersion() + "|" + mob.getToaInvocationLevel()
			+ "|" + style.name() + "|" + (game ? "g" : "o")
			+ "|" + ctxKey(ctx)
			+ "|" + dreamsFor(ctx, mob).hashCode()
			+ "|" + excludedFor(ctx, style, mob).hashCode()
			+ "|" + pinKey(ctx, style, mob);
	}

	/** The query-wide ctx fields every cache key shares (keys only need
	 * equality, so the two key builders reuse one middle). */
	private static String ctxKey(ComputeContext ctx) {
		return ctx.collectionFingerprint + "|" + ctx.p.f2pOnly + "|" + ctx.p.onSlayerTask
			+ "|" + ctx.lock + "|" + ctx.unlocks.key() + "|" + ctx.p.maxTradeables
			+ "|" + ctx.riskBudget + "|" + ctx.p.antifirePotion + "|" + ctx.p.sunfireRunes + "|" + ctx.p.multiView + "|" + ctx.p.multiStack + "|" + ctx.p.deathCharge
			+ "|" + ctx.p.specWeapon + "|" + ctx.p.inWilderness + "|" + ctx.boostPicks + "|" + ctx.prayerPicks
			+ "|" + ctx.p.upgradeBudgetGp + "|" + ctx.protectOnly.hashCode()
			// Levels participate in the key so a boost/level-up invalidates.
			+ "|" + ctx.real + "|" + ctx.boostedLevels + "|" + ctx.p.kourendElite;
	}

	/** One roster optimize (a pool task): LRU-cached; a hit hands out a
	 * fresh list copy so downstream mutation never poisons the cache. */
	private List<DpsResult> optimizeCached(MonsterStats mob, CombatStyle style, boolean game,
		ComputeContext ctx, OptimizationRequest req, long ticket) {
		String key = optimizeKey(mob, style, game, ctx);
		synchronized (optimizeCache) {
			List<DpsResult> cached = optimizeCache.get(key);
			if (cached != null) { return new ArrayList<>(cached); }
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

	/** The displayed set's finish, shared by every optimize site: top up
	 * DPS-neutral empty slots and required utility (verified DPS-neutral). */
	private static void polish(LoadoutOptimizer opt, LoadoutData dataset,
		OptimizationRequest req, List<DpsResult> best) {
		if (!best.isEmpty()) {
			best.set(0, opt.fillDpsNeutralSlots(dataset, req, best.get(0)));
			best.set(0, opt.ensureRequiredUtility(dataset, req, best.get(0)));
		}
	}

	/** The single-mob post-passes on a roster shown set (field reports
	 * 2026-08-09): the Dizana's quiver relocation and the mandatory-recoil
	 * swap (a Zulrah roster recommended sets with no recoil gear). Runs on
	 * the phase-1 sets and again on whatever the kit merge shows. */
	private DpsResult postPass(ComputeContext ctx, OptimizationRequest req, DpsResult shown) {
		return optimizer.ensureRequiredUtility(ctx.dataset, req,
			optimizer.relocateQuiverAmmo(ctx.dataset, req, shown));
	}

	/** The dream items for ONE mob: global sims plus that mob's own. */
	private static Set<Integer> dreamsFor(ComputeContext ctx, MonsterStats mob) {
		Set<Integer> local = ctx.dreamsByMob.get(mob.profileId());
		if (local == null || local.isEmpty()) { return ctx.dreams; }
		Set<Integer> merged = new HashSet<>(ctx.dreams);
		merged.addAll(withPairedAmmo(local));
		return merged;
	}

	/** The style's exclusions for ONE mob: global plus that mob's own. */
	private static Set<Integer> excludedFor(ComputeContext ctx, CombatStyle style, MonsterStats mob) {
		Set<Integer> global = ctx.excluded.getOrDefault(style, Set.of());
		Map<CombatStyle, Set<Integer>> byMob = ctx.excludedByMob.get(mob.profileId());
		Set<Integer> per = byMob == null ? null : byMob.get(style);
		if (per == null || per.isEmpty()) { return global; }
		Set<Integer> merged = new HashSet<>(global);
		merged.addAll(per);
		return merged;
	}

	/** One multi tool through the per-style pipeline: BARRAGE pins Ice
	 * Barrage (Ice Burst under 94), CHINS the best owned chinchompa (the
	 * ceiling black), VENATOR the charged bow. Yours only when the player
	 * owns the tool and, for the spell, its runes. Null when the account
	 * cannot cast any burst. */
	private StyleResult multiToolResult(MonsterStats monster, ComputeContext ctx, MultiTool tool) {
		var pick = new ToolPick();
		boolean ownedSide;
		if (tool == BARRAGE) {
			int magic = ctx.boostedLevels.getMagic();
			String name = magic >= 94 ? "Ice Barrage" : "Ice Burst";
			pick.spell = ctx.dataset.getSpells().stream()
				.filter(sp -> sp.getName().equals(name)).findFirst().orElse(null);
			if (pick.spell == null || magic < pick.spell.getMagicLevel()) { return null; }
			ownedSide = SpellRunes.premiumRunesOwned(name, ctx.effectiveOwned);
		} else {
			// The owned scan canonicalises a charged bow onto its uncharged id,
			// so either id proves the bow; the pin always wears the charged one.
			int[] ids = tool == VENATOR ? new int[]{VENATOR_BOW, VENATOR_BOW_UNCHARGED} : CHIN_IDS;
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

	/** One style's answer: both sides, spec, incoming, bench. The multi
	 * tools run through it with their pins; ownedSide false skips Yours
	 * (a tool the player does not own). */
	private StyleResult styleResultFor(MonsterStats monster, ComputeContext ctx, CombatStyle style,
		ToolPick tool, boolean ownedSide) {
			BoostProfile supplied = suppliedBoost(monster);
			StylePlan plan = stylePlan(ctx, style, supplied);
			PlayerLevels styleLevels = plan.levels;
			String boostLabel = plan.label;
			PlayerLevels gameLevels = plan.gameLevels;
			String gameBoostLabel = plan.gameLabel;
			// Dreams are pretend-owned; a positive upgrade budget also
			// admits anything buyable within it (total spend, tracked
			// by the beam).
			OptimizationRequest ownedRequest = ownedRequestFor(ctx, monster, style, styleLevels, tool);
			List<DpsResult> ownedBest = ownedSide ? optimizer.optimize(ctx.dataset, ownedRequest) : new ArrayList<>();
			// The displayed set: top up DPS-neutral empty slots with
			// prayer/defensive gear (verified not to change the DPS).
			polish(optimizer, ctx.dataset, ownedRequest, ownedBest);
			// A defensive mode swaps the shown set for its frontier pick
			// BEFORE spec, incoming and bench, so every downstream number
			// describes the set the card shows. Style cards only - a
			// Multi tool's pinned weapon is its own question.
			ModeTrade modeTrade = tool != null || ctx.mode == MAX_DPS || ownedBest.isEmpty()
				? null : applyMode(ctx, ownedRequest, ownedBest, monster, ctx.ticket);
			// The swaps strips, and the set made to wear its own owned upgrades
			// first (ISS-01M5PKSQBAP823XNC13QBW8830, Andrew 2026-10-24: "if it
			// is SIM IT AS THE MAIN SET"): the search's beam can miss a set one
			// swap away, the strip prices every swap, so an owned step above
			// the worn item is adopted BEFORE spec, incoming and bench read the
			// set. The strips are extras: one that fails is logged and dropped,
			// never the card (2026-10-22: a strip's NPE blanked every style it
			// touched).
			Map<GearSlot, Ladder> ladders = null;
			if (tool == null && ownedSide && !ownedBest.isEmpty()) {
				try {
					ladders = adoptOwnedSteps(ctx, ownedRequest, ownedBest, monster);
				} catch (RuntimeException ex) {
					log.warn("swaps strip failed for {} {}", monster.getName(), style, ex);
				}
			}
			// The ceiling: every obtainable item, no quest/level gating -
			// but computed at the player's own levels, so the comparison
			// percentage isolates the GEAR gap.
			// ALL_STANDARD ignores ownership for eligibility, but the
			// candidate dedupe prefers OWNED analogs on stat ties - so
			// the game-best card shows YOUR god d'hide coif, not an
			// arbitrary god's, and the BiS border matches by id.
			OptimizationRequest gameRequest = gameRequestFor(ctx, monster, style, gameLevels, tool);
			List<DpsResult> gameBest = optimizer.optimize(ctx.dataset, gameRequest);
			polish(optimizer, ctx.dataset, gameRequest, gameBest);
			// GWD protection (ISS-01M5G9KPDQWBR014Z10F79B29T): the route's
			// gods worn when free, the rest carried - and carried protection
			// takes its Inventory slots before the spec weapon does.
			List<String> gods = tool != null ? List.of() : GwdProtection.godsFor(monster, ctx.gwdAncient);
			List<GearItem> protection = protect(ctx, ownedRequest, ownedBest, gods, monster,
				ctx.mode != MAX_DPS);
			List<GearItem> gameProtection = protect(ctx, gameRequest, gameBest, gods, monster, false);
			int specSlots = ctx.maxSwaps - protection.size();
			int gameSpecSlots = ctx.maxSwaps - gameProtection.size();
			// Bench 0 = strictly one worn set: no spec swap is carried.
			Set<Integer> specPin = restrictSpec(ctx, style, monster);
			SpecPick spec = ownedSide && specSlots >= 1 && ctx.p.specWeapon
				? seat(arbitrateLightbearer(ctx.dataset, ownedRequest, ownedBest, ctx.effectiveOwned, specPin), specSlots)
				: null;
			SpecPick gameSpec = gameSpecSlots >= 1 && ctx.p.specWeapon
				? seat(arbitrateLightbearer(ctx.dataset, gameRequest, gameBest, null, specPin), gameSpecSlots)
				: null;
			// The defensive story of the shown set: what the boss does
			// back to you, at your REAL levels (protection prayer up).
			Result incoming = incomingFor(monster,
				ownedBest.isEmpty() ? null : ownedBest.get(0).getLoadout(), ctx);
			Result gameIncoming = incomingFor(monster,
				gameBest.isEmpty() ? null : gameBest.get(0).getLoadout(), ctx);
			List<GearItem> bench = specBenchOf(spec, ownedBest);
			bench.addAll(protection);
			// The BiS side's inventory mirrors it (field fix 2026-07-18:
			// a single mob's BiS view showed no carried spec).
			List<GearItem> gameBench = specBenchOf(gameSpec, gameBest);
			gameBench.addAll(gameProtection);
			StyleResult result = verified(new StyleResult(
				ownedBest, gameBest.isEmpty() ? null : gameBest.get(0), spec, gameSpec,
				boostLabel, gameBoostLabel, incoming, gameIncoming, bench, gameBench, true, true),
				ctx.dataset, ownedRequest, gameRequest);
			result.modeTrade = modeTrade;
			result.protection = protection;
			result.gameProtection = gameProtection;
			if (ladders != null) {
				result.ladder = ladders;
				if (spec != null) {
					try {
						result.specLadder = specLadderFor(ctx, ownedRequest, ownedBest.get(0), spec.weapon);
					} catch (RuntimeException ex) {
						log.warn("spec strip failed for {} {}", monster.getName(), style, ex);
					}
				}
			}
			return result;
			// Store per style as computed - even a superseded job donates
			// the styles it finished.
	}

	/** The query-wide cache key for one monster (per-style state layered on
	 * top by styleKey). Centralised so the fast path and computeAllStyles
	 * can never drift into split/colliding cache buckets. */
	private static String baseKeyFor(MonsterStats monster, ComputeContext ctx) {
		// The invocation level scales the monster's defence but not its id
		// (found 2026-08-09: optimizeKey carried it, this key did not, so
		// flipping the Invocation chip served the previous level's cache).
		// The VERSION is the same lesson (found 2026-08-20): a boss's
		// versions share one id, so Far/Nearby/Roaring collided.
		return monster.getId() + "|" + monster.getVersion() + "|" + monster.getToaInvocationLevel()
			+ "|" + ctxKey(ctx)
			+ "|" + dreamsFor(ctx, monster).hashCode() + "|" + ctx.maxSwaps
			+ "|" + ctx.specByMob.getOrDefault(monster.profileId(), 0)
			+ "|" + ctx.mode.name() + "|" + ctx.gwdAncient;
	}

	private Map<CombatStyle, StyleResult> computeAllStyles(
		MonsterStats monster, ComputeContext ctx, long ticket) {
		ctx.ticket = ticket;
		String baseKey = baseKeyFor(monster, ctx);
			Map<CombatStyle, StyleResult> results = new EnumMap<>(CombatStyle.class);
			for (CombatStyle style : concreteValues()) {
				if (requestSeq.get() != ticket) {
					abandonedForTest++;
					return null; // superseded mid-flight - abandon between styles
				}
				// Styles cache independently: a pin or per-set exclusion on
				// one style leaves the other two answers standing.
				String styleKey = styleKey(baseKey, style, ctx, monster);
				StyleResult cachedStyle;
				synchronized (cache) { cachedStyle = cache.get(styleKey); }
				if (cachedStyle != null) {
					results.put(style, cachedStyle);
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
				String key = styleKey(baseKey, tool.style, ctx, monster) + "|" + tool.key;
				StyleResult row;
				synchronized (cache) { row = cache.get(key); }
				if (row == null) {
					row = multiToolResult(monster, ctx, tool);
					if (row == null) { continue; }
					synchronized (cache) { cache.put(key, row); }
				}
				tools.put(tool.key, row);
			}
			results.values().iterator().next().multiTools = tools;
		}
		return results;
	}

	/**
	 * Sweep defense weights over the max-DPS set to trace the offence/
	 * defence frontier, pick the mode's point, swap it into ownedBest[0],
	 * and say what it cost. Only where the damage-taken model covers the
	 * monster (full or curated) - a partial number would steer the pick by
	 * the attacks it leaves out.
	 */
	private ModeTrade applyMode(ComputeContext ctx, OptimizationRequest ownedRequest,
		List<DpsResult> ownedBest, MonsterStats monster, long ticket) {
		OptimizeMode mode = ctx.mode;
		DpsResult maxDps = ownedBest.get(0);
		Result base = incomingFor(monster, maxDps.getLoadout(), ctx);
		if (IncomingDpsCalculator.NONE.equals(base.coverage)) { return new ModeTrade(mode, 0, 0, "no damage data"); }
		if (IncomingDpsCalculator.PARTIAL.equals(base.coverage)) {
			return new ModeTrade(mode, 0, 0, "partial damage data");
		}
		double d0 = maxDps.getDps();
		double i0 = base.totalDps;
		if (d0 <= 0 || i0 <= 0.05) { return new ModeTrade(mode, 0, 0, "takes ~0 damage"); }
		List<DpsResult> frontier = new ArrayList<>();
		frontier.add(maxDps);
		// Candidate pools depend on weight > 0, not its size: build once.
		CandidatePools pools = null;
		for (double alpha : SWEEP_ALPHAS) {
			if (requestSeq.get() != ticket) {
				return null; // superseded mid-sweep
			}
			OptimizationRequest weighted = ownedRequest.withDefenseWeight(alpha * d0 / i0);
			if (pools == null) { pools = optimizer.preparePools(ctx.dataset, weighted); }
			List<DpsResult> out = optimizer.optimize(ctx.dataset, weighted, pools);
			if (!out.isEmpty()) {
				polish(optimizer, ctx.dataset, weighted, out);
				frontier.add(out.get(0));
			}
		}
		// Scored as rungs: the mode's own ranking (compareRungs - Tanky the
		// least DTPS, Balanced the least DTPS/DPS, ties to the higher DPS).
		DpsResult picked = maxDps;
		var pickedRung = new Rung(null, d0, i0, false, null);
		for (DpsResult candidate : frontier) {
			var rung = new Rung(null, candidate.getDps(),
				incomingFor(monster, candidate.getLoadout(), ctx).totalDps, false, null);
			if (compareRungs(mode, rung, pickedRung) > 0) {
				picked = candidate;
				pickedRung = rung;
			}
		}
		if (picked == maxDps) { return new ModeTrade(mode, 0, 0, "no safer set"); }
		ownedBest.set(0, picked);
		return new ModeTrade(mode,
			(int) round((1 - picked.getDps() / d0) * 100),
			(int) round((1 - pickedRung.dtps / i0) * 100), null);
	}

	/** Every worn slot's ladder for the shown Yours set: each swap scored by
	 * the active mode (Andrew 2026-10-21: "same objective as the active
	 * mode"), two steps up from any item, two down from owned ones. */
	private Map<GearSlot, Ladder> ladderFor(ComputeContext ctx, OptimizationRequest request,
		DpsResult shown, MonsterStats monster) {
		Map<GearSlot, Ladder> ladders = new EnumMap<>(GearSlot.class);
		boolean scoresDamage = ctx.mode != MAX_DPS;
		double baseDtps = incomingFor(monster, shown.getLoadout(), ctx).totalDps;
		Set<Integer> dreams = dreamsFor(ctx, monster);
		for (GearSlot slot : GearSlot.values()) {
			if (shown.getLoadout().get(slot) == null) { continue; }
			var base = new Rung(shown.getLoadout().get(slot), shown.getDps(), baseDtps, true, null);
			List<Rung> candidates = new ArrayList<>();
			boolean weapon = slot == WEAPON;
			for (DpsResult swap : optimizer.slotSwaps(ctx.dataset, request, shown, slot)) {
				GearItem item = swap.getLoadout().get(slot);
				double dtps = scoresDamage ? incomingFor(monster, swap.getLoadout(), ctx).totalDps : baseDtps;
				var rung = new Rung(item, swap.getDps(), dtps,
					ctx.effectiveOwned.owns(item.getId()) || dreams.contains(item.getId()),
					weapon ? brought(swap.getLoadout(), shown.getLoadout(), AMMO) : null);
				rung.offhand = weapon ? brought(swap.getLoadout(), shown.getLoadout(), SHIELD) : null;
				rung.set = swap;
				candidates.add(rung);
			}
			if (weapon) { candidates = foldStaffFamilies(candidates, base.item); }
			Ladder ladder = rankSteps(ctx.mode, request.getStyle(), base, candidates, shown.getDps(), baseDtps);
			if (ladder != null) {
				if (weapon) {
					ladder.all = allRanked(ctx.mode, request.getStyle(), candidates);
					splitByKind(ladder, ctx.mode, request.getStyle(), base, candidates, shown.getDps(), baseDtps);
				}
				ladders.put(slot, ladder);
			}
		}
		return ladders;
	}

	/** The solo card's ladders, the set first made to wear its own owned
	 * upgrades (ISS-01M5PKSQBAP823XNC13QBW8830, Andrew 2026-10-24: "it's still
	 * doing the thing for whisperer where it things a mystic smoke battlestaff
	 * is an upgrade. so if it is SIM IT AS THE MAIN SET"): while a step the
	 * strip rates above the worn item under the mode is yours - owned or
	 * simmed - the shown set becomes that step's set and the ladders are
	 * rebuilt from it, a few rounds at most. */
	private Map<GearSlot, Ladder> adoptOwnedSteps(ComputeContext ctx, OptimizationRequest request,
		List<DpsResult> ownedBest, MonsterStats monster) {
		Map<GearSlot, Ladder> ladders = ladderFor(ctx, request, ownedBest.get(0), monster);
		for (int round = 0; round < 3; round++) {
			Rung best = null;
			for (var entry : ladders.entrySet()) {
				Ladder ladder = entry.getValue();
				GearItem pinned = ownedBest.get(0).getLoadout().get(entry.getKey());
				if (pinned != null && request.isPinned(pinned.getId())) {
					continue; // the player's own word for the slot: the strip may rate it, never replace it
				}
				var worn = new Rung(null, ladder.baseDps, ladder.baseDtps, true, null);
				for (Rung rung : ladder.up) {
					if (rung.owned && rung.set != null && compareRungs(ctx.mode, rung, worn) > 0
						&& (best == null || compareRungs(ctx.mode, rung, best) > 0)) {
						best = rung;
					}
				}
			}
			if (best == null) { return ladders; }
			ownedBest.set(0, best.set);
			ladders = ladderFor(ctx, request, best.set, monster);
		}
		return ladders;
	}

	/** The magic weapon ladder's two kinds (ISS-01M5PKSQBAP823XNC13QBW8830):
	 * powered staves and autocast staves, each ranked around the worn weapon
	 * by the same rule, sharing the "All weapons" list. Other styles, and the
	 * other slots, have one ladder. */
	private static void splitByKind(Ladder ladder, OptimizeMode mode, CombatStyle style, Rung worn,
		List<Rung> candidates, double baseDps, double baseDtps) {
		if (style != MAGIC) { return; }
		List<Rung> powered = new ArrayList<>();
		List<Rung> autocast = new ArrayList<>();
		for (Rung rung : candidates) { (rung.item.isPoweredStaff() ? powered : autocast).add(rung); }
		ladder.powered = kindOf(ladder, rankSteps(mode, style, worn, powered, baseDps, baseDtps));
		ladder.autocast = kindOf(ladder, rankSteps(mode, style, worn, autocast, baseDps, baseDtps));
	}

	private static Ladder kindOf(Ladder parent, Ladder kind) {
		if (kind != null) {
			kind.group = parent.group;
			kind.basePerMob = parent.basePerMob;
			kind.all = parent.all;
		}
		return kind;
	}

	private static final Pattern BATTLESTAFF = Pattern.compile("(air|water|earth|fire|lava|mud|mist|steam|dust|smoke) battlestaff");
	private static final Pattern MYSTIC = Pattern.compile("mystic (air|water|earth|fire|lava|mud|mist|steam|dust|smoke) staff");

	/** The twin that stands for a base weapon: the mystic staff for an
	 * <element> battlestaff, the (e) for a trident (ISS-01M5PTHS4MAVKFB8C1QXF0KTS1,
	 * Andrew 2026-10-24: "combine trident of the seas/swamp and their (e)
	 * version - those have the same damage just hold a different number of
	 * charges"); null for every other weapon. */
	static String twinOf(String name) {
		Matcher staff = BATTLESTAFF.matcher(name);
		if (staff.matches()) { return "mystic " + staff.group(1) + " staff"; }
		return name.equals("trident of the seas") || name.equals("trident of the swamp") ? name + " (e)" : null;
	}

	/** The base a twin stands for; null when the name is no twin. */
	static String baseOf(String name) {
		Matcher mystic = MYSTIC.matcher(name);
		if (mystic.matches()) { return mystic.group(1) + " battlestaff"; }
		String base = name.endsWith(" (e)") ? name.substring(0, name.length() - 4) : "";
		return twinOf(base) != null ? base : null;
	}

	/** A weapon and its twin are one weapon to the strip (Andrew 2026-10-24:
	 * "just consider that as the same weapon and if the user has the mystic
	 * version use that and if they don't sim the other but don't waste both
	 * slots with the results in the ladder"): the worn one stands for the
	 * pair, else the OWNED twin, else the base - a twin's sim rung never sits
	 * beside its base. */
	static List<Rung> foldStaffFamilies(List<Rung> rungs, GearItem worn) {
		Map<String, Rung> byName = new HashMap<>();
		for (Rung rung : rungs) { byName.put(rung.item.getNameLower(), rung); }
		String wornName = worn == null ? "" : worn.getNameLower();
		List<Rung> kept = new ArrayList<>(rungs.size());
		for (Rung rung : rungs) {
			String name = rung.item.getNameLower();
			String twin = twinOf(name);
			String base = baseOf(name);
			Rung twinRung = twin == null ? null : byName.get(twin);
			boolean drop = twin != null ? wornName.equals(twin) || twinRung != null && twinRung.owned
				: base != null && !rung.owned && (wornName.equals(base) || byName.containsKey(base));
			if (!drop) { kept.add(rung); }
		}
		return kept;
	}

	/** The weight a style's shared kit gives each member
	 * (ISS-01M5PKSQBAP823XNC13QBW8830, Andrew 2026-10-24: "it is missing
	 * trident of the seas in this calculation which is even better because it
	 * allows the whole set to use arceuss"): its kit weight where the style
	 * is how the member is fought - the side's best set's style, or one within
	 * 5% of it - and a thousandth of it where it is not, so the Warden's Core
	 * you melee has next to no say in the magic kit, yet a member fought
	 * another way still gets a valid set in this style (its pinned weapon is
	 * still worth carrying, a kit nobody fights with still answers). */
	static final double STYLE_BAND = 0.95;
	static final double OTHER_STYLE_SHARE = 0.001;

	static double[] styleUseWeights(List<MonsterStats> mobs, CombatStyle style,
		Map<CombatStyle, List<List<DpsResult>>> bestsByStyle) {
		int n = mobs.size();
		double[] weights = new double[n];
		for (int j = 0; j < n; j++) {
			double own = dpsOf(bestsByStyle.get(style), j);
			double top = 0;
			for (List<List<DpsResult>> bests : bestsByStyle.values()) { top = max(top, dpsOf(bests, j)); }
			boolean fought = own > 0 && own >= STYLE_BAND * top;
			weights[j] = kitWeight(mobs.get(j)) * (fought ? 1 : OTHER_STYLE_SHARE);
		}
		return weights;
	}

	private static double dpsOf(List<List<DpsResult>> bests, int j) {
		return bests == null || j >= bests.size() || emptyBest(bests.get(j)) ? 0 : bests.get(j).get(0).getDps();
	}

	/** Every side's shared-spec options together, best trip value first
	 * (ISS-01M5PVHNZDC61B019S782QN0PJ, Andrew 2026-10-24, Royal Titans: "it's
	 * recommending dark bow spec on royal titans i feel like a chally spec
	 * would be much better"): the trip carries ONE spec weapon, so a primary
	 * weighs the meleed titans' chally against the out-of-reach pair's dark
	 * bow, not just its own side's list. */
	private static List<SpecPick[]> tripSpecOptions(Map<CombatStyle, SharedSide> sides, List<MonsterStats> mobs) {
		double[] weights = hpWeights(mobs);
		List<SpecPick[]> all = new ArrayList<>();
		for (SharedSide side : sides.values()) { all.addAll(side.specOptions); }
		all.sort(Comparator.comparingDouble((SpecPick[] option) -> tripValue(option, weights)).reversed());
		return all;
	}

	/** Every member's spec weight: the trip's spec currency - a spec-only
	 * member (the Warden's Core) counts in full here and nowhere else. */
	private static double[] hpWeights(List<MonsterStats> mobs) {
		double[] weights = new double[mobs.size()];
		for (int j = 0; j < weights.length; j++) { weights[j] = specWeight(mobs.get(j)); }
		return weights;
	}

	private static final int ALL_STEPS = 24;

	/** Every step the strip could show, best first, owned ahead on ties
	 * (ISS-01M5PE4TZXTGJAQA1YXNRKPEDW, Andrew 2026-10-24: "it should evaluate
	 * all options and show the result of all based on dps"). */
	private static List<Rung> allRanked(OptimizeMode mode, CombatStyle style, List<Rung> candidates) {
		List<Rung> all = new ArrayList<>(candidates);
		Comparator<Rung> order = (a, b) -> compareRungs(mode, a, b);
		all.sort(order.reversed().thenComparing(Comparator.comparing((Rung r) -> !r.owned)));
		return all.size() > ALL_STEPS ? new ArrayList<>(all.subList(0, ALL_STEPS)) : all;
	}

	/** The item a swapped set brings in a slot the worn set does not already
	 * fill with it (a weapon step's ammo or offhand); null otherwise. */
	private static GearItem brought(Loadout swapped, Loadout worn, GearSlot slot) {
		GearItem item = swapped.get(slot);
		GearItem had = worn.get(slot);
		return item != null && (had == null || item.getId() != had.getId()) ? item : null;
	}

	/** The steps around the worn item: up from any item, down from your own,
	 * closest first, five columns by the fill rule. Equal under the mode,
	 * the item's own stats decide (ISS-01M5H9KGF4FSAM5DR1XSD9F9RX, Andrew
	 * 2026-10-22: Torva tied Bandos on one max hit, and a whole band of
	 * boots folded into Boots of the Eye). Null when there is no step. */
	private static Ladder rankSteps(OptimizeMode mode, CombatStyle style, Rung base, List<Rung> candidates,
		double baseDps, double baseDtps) {
		Comparator<Rung> order = (a, b) -> compareRungs(mode, a, b);
		// No style (the spec ladder): the mode alone ranks the steps.
		Comparator<Rung> stats = style == null ? (a, b) -> 0 : (a, b) -> compareStats(style, a, b);
		Comparator<Rung> full = order.thenComparing(stats);
		List<Rung> above = new ArrayList<>();
		List<Rung> below = new ArrayList<>();
		for (Rung rung : candidates) {
			int vsWorn = full.compare(rung, base);
			if (vsWorn > 0) {
				above.add(rung);
			} else if (vsWorn < 0 && rung.owned) { below.add(rung); }
		}
		// Closest first. Up keeps one step per result - its best-stats
		// item; down is your own gear, so equal results stand side by
		// side and only stat-identical twins fold (owned, then cheaper).
		Comparator<Rung> tieBreak = Comparator.comparing((Rung r) -> !r.owned)
			.thenComparingInt(r -> r.item.getPriceOrZero());
		above.sort(order.thenComparing(stats.reversed()).thenComparing(tieBreak));
		below.sort(full.reversed().thenComparing(tieBreak));
		List<Rung> up = closest(above, order);
		List<Rung> down = closest(below, full);
		int[] spots = Ladder.spots(up.size(), down.size());
		if (up.isEmpty() && down.isEmpty()) { return null; }
		var ladder = new Ladder(mode, baseDps, baseDtps, up.subList(0, spots[0]), down.subList(0, spots[1]));
		ladder.upMore = up.subList(spots[0], up.size());
		ladder.downMore = down.subList(spots[1], down.size());
		return ladder;
	}

	/** A group search's swaps (ISS-01M5JAGFEJ5QHXE37B37XM8HJJ, Andrew 2026-10-22: "ladder
	 * view is disabled for group searches but i think it should be
	 * included" - scored by "The whole group"): each slot of what each member
	 * WEARS for the style - the kit-backed set where the kit answers
	 * (ISS-01M5QJMTBRNAWX5WT7NRJD8JWC, Andrew 2026-10-24: "melee dps is
	 * calcing to 4.44 with nox halberd but ladder is showing 6.06") - swapped
	 * for every member, the DPS averaged by the weights that picked the kit
	 * (side.weights). The strip's worn item is the one most members wear in
	 * the slot. One strip for the group. */
	private Map<GearSlot, Ladder> groupLadderFor(ComputeContext ctx, SharedSide side, List<MonsterStats> mobs,
		List<Map<CombatStyle, StyleResult>> perMob, CombatStyle style) {
		Map<GearSlot, Ladder> ladders = new EnumMap<>(GearSlot.class);
		if (side == null || side.shared == null) { return ladders; }
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
				baseDps += base[j].getDps() * weight[j];
				basePerMob[j] = base[j].getDps();
			}
		}
		if (total <= 0) { return ladders; }
		baseDps /= total;
		for (GearSlot slot : GearSlot.values()) {
			GearItem worn = mostWorn(base, slot);
			if (worn == null) { continue; }
			Map<Integer, double[]> sums = new LinkedHashMap<>();
			Map<Integer, double[]> perMobDps = new HashMap<>();
			Map<Integer, GearItem> items = new HashMap<>();
			for (int j = 0; j < n; j++) {
				if (base[j] == null) { continue; }
				for (DpsResult swap : optimizer.slotSwaps(ctx.dataset, side.reqs.get(j), base[j], slot)) {
					GearItem item = swap.getLoadout().get(slot);
					if (item.getId() == worn.getId()) {
						continue; // the strip's worn item: a member wearing another reads its own card
					}
					items.putIfAbsent(item.getId(), item);
					sums.computeIfAbsent(item.getId(), k -> new double[1])[0] += swap.getDps() * weight[j];
					// Each member's own number for the step: its card shows
					// that, the group figure ranks (ISS-01M5PE4TZXTGJAQA1YXNRKPEDW).
					perMobDps.computeIfAbsent(item.getId(), k -> new double[n])[j] = swap.getDps();
				}
			}
			List<Rung> candidates = new ArrayList<>();
			for (var entry : sums.entrySet()) {
				int id = entry.getKey();
				var rung = new Rung(items.get(id), entry.getValue()[0] / total, 0,
					ctx.effectiveOwned.owns(id) || dreams.contains(id), null);
				rung.perMob = perMobDps.get(id);
				candidates.add(rung);
			}
			var wornRung = new Rung(worn, baseDps, 0, true, null);
			wornRung.perMob = basePerMob;
			if (slot == WEAPON) { candidates = foldStaffFamilies(candidates, worn); }
			Ladder ladder = rankSteps(MAX_DPS, style, wornRung, candidates, baseDps, 0);
			if (ladder != null) {
				ladder.group = true;
				ladder.basePerMob = basePerMob;
				if (slot == WEAPON) {
					ladder.all = allRanked(MAX_DPS, style, candidates);
					splitByKind(ladder, MAX_DPS, style, wornRung, candidates, baseDps, 0);
				}
				ladders.put(slot, ladder);
			}
		}
		return ladders;
	}

	/** The item most members wear in a slot; null when none does. */
	private static GearItem mostWorn(DpsResult[] base, GearSlot slot) {
		Map<Integer, int[]> counts = new HashMap<>();
		GearItem best = null;
		int most = 0;
		for (DpsResult result : base) {
			GearItem item = result == null ? null : result.getLoadout().get(slot);
			if (item != null && ++counts.computeIfAbsent(item.getId(), k -> new int[1])[0] > most) {
				most = counts.get(item.getId())[0];
				best = item;
			}
		}
		return best;
	}

	/** The carried spec's swaps (ISS-01M5HCS725N7XVVBC7CMK6Y43A, Andrew
	 * 2026-10-22: "not seeing the ladder view for the spec weapon slot"):
	 * every spec weapon scored alone by the card's own pick - the DPS it
	 * adds over just attacking - any item above the carried one, your own
	 * below, five columns by the slots' fill rule. */
	private Ladder specLadderFor(ComputeContext ctx, OptimizationRequest request, DpsResult main,
		GearItem carried) {
		SpecPick worn = bestSpec(ctx.dataset, request, main, null, Set.of(carried.getId()), true);
		if (worn == null) { return null; }
		Set<Integer> dreams = dreamsFor(ctx, request.getMonster());
		List<Rung> candidates = new ArrayList<>();
		for (GearItem item : ctx.dataset.getGearItems(WEAPON)) {
			if (item.getId() == carried.getId() || SpecialAttack.match(item) == null
				|| !LoadoutOptimizer.eligible(ctx.dataset, request, item)) {
				continue;
			}
			SpecPick pick = bestSpec(ctx.dataset, request, main, null, Set.of(item.getId()), true);
			if (pick != null) {
				candidates.add(new Rung(item, pick.dpsAdded, 0,
					ctx.effectiveOwned.owns(item.getId()) || dreams.contains(item.getId()), null));
			}
		}
		return rankSteps(MAX_DPS, null, new Rung(carried, worn.dpsAdded, 0, true, null),
			candidates, worn.dpsAdded, 0);
	}

	/** The first SPOTS + MORE distinct steps of an already-sorted list
	 * (distinct under {@code order}: a step equal to the previous one folds
	 * into it). */
	private static List<Rung> closest(List<Rung> sorted, Comparator<Rung> order) {
		List<Rung> kept = new ArrayList<>();
		for (Rung rung : sorted) {
			if (kept.size() == Ladder.SPOTS + Ladder.MORE) { break; }
			if (kept.isEmpty() || order.compare(rung, kept.get(kept.size() - 1)) != 0) { kept.add(rung); }
		}
		return kept;
	}

	/** Positive when a's item has the better stats for the style: its rough
	 * offensive score, then its total defence. */
	static int compareStats(CombatStyle style, Rung a, Rung b) {
		double sa = a.item.roughScore(style);
		double sb = b.item.roughScore(style);
		if (abs(sa - sb) > 1e-9) { return sa > sb ? 1 : -1; }
		return Integer.compare(defenceOf(a.item), defenceOf(b.item));
	}

	private static int defenceOf(GearItem item) {
		com.loadoutlab.data.StatBlock d = item.getDefensive();
		return d.getStab() + d.getSlash() + d.getCrush() + d.getMagic() + d.getRanged();
	}

	/** Positive when a is better under the mode: Max DPS by DPS; Tanky by
	 * least damage taken; Balanced by least DTPS/DPS - DPS breaks ties. */
	static int compareRungs(OptimizeMode mode, Rung a, Rung b) {
		if (mode == TANKY && abs(a.dtps - b.dtps) > 1e-9) { return a.dtps < b.dtps ? 1 : -1; }
		if (mode == BALANCED) {
			double ra = a.dps <= 0 ? Double.POSITIVE_INFINITY : a.dtps / a.dps;
			double rb = b.dps <= 0 ? Double.POSITIVE_INFINITY : b.dtps / b.dps;
			if (abs(ra - rb) > 1e-12 && !(Double.isInfinite(ra) && Double.isInfinite(rb))) { return ra < rb ? 1 : -1; }
		}
		return abs(a.dps - b.dps) <= 1e-9 ? 0 : a.dps > b.dps ? 1 : -1;
	}

	/** One side's GWD protection: wear the free ones into best[0], return
	 * what must be carried (empty when the monster is not on a GWD route).
	 * Under a defensive mode "free" also means no more damage taken - a
	 * DPS-neutral god item must not undo the set Tanky/Balanced picked. */
	private List<GearItem> protect(ComputeContext ctx, OptimizationRequest request,
		List<DpsResult> best, List<String> gods, MonsterStats monster, boolean defensive) {
		if (gods.isEmpty() || best.isEmpty()) { return new ArrayList<>(); }
		DpsResult worn = optimizer.wearGodItems(ctx.dataset, request, best.get(0), gods);
		if (!defensive || incomingFor(monster, worn.getLoadout(), ctx).totalDps
			<= incomingFor(monster, best.get(0).getLoadout(), ctx).totalDps + 1e-9) {
			best.set(0, worn);
		}
		return new ArrayList<>(optimizer.carriedGodItems(ctx.dataset, request, best.get(0).getLoadout(), gods));
	}

	/** What the boss does back to you in this worn set, at REAL levels
	 * (protection prayer up) - null loadout answers null. Twin owned/game
	 * call sites in both compute paths share it. */
	private static Result incomingFor(MonsterStats mob,
		Loadout worn, ComputeContext ctx) {
		return worn == null ? null
			: IncomingDpsCalculator.calculate(mob, worn, ctx.real.getDefence(), ctx.real.getMagic());
	}

	/** One side's bench for the single-mob path: the spec weapon when the
	 * shown set does not already wear it. */
	private static List<GearItem> specBenchOf(SpecPick spec, List<DpsResult> best) {
		List<GearItem> bench = new ArrayList<>();
		if (spec != null && spec.weapon != null && !best.isEmpty()
			&& (best.get(0).getLoadout().getWeapon() == null
				|| best.get(0).getLoadout().getWeapon().getId() != spec.weapon.getId())) {
			bench.add(spec.weapon);
			if (spec.offhand != null) { bench.add(spec.offhand); }
		}
		return bench;
	}

	/** Gather the query-wide state one compute needs. Shared by the single-
	 * mob (bestPerStyle) and roster (bestPerStyleAcross) entry points so the
	 * two can never disagree on levels, cache keys, or candidate eligibility. */
	private ComputeContext buildContext(
		PlayerLevels realLevels, PlayerLevels boostedLevels, PrayerUnlocks prayerUnlocks,
		RequirementProfile requirements, OwnedItems owned, int collectionFingerprint,
		ComputeParams p, Map<CombatStyle, Set<Integer>> excludedByStyle, Set<Integer> dreamItems,
		Map<Integer, Map<CombatStyle, Map<GearSlot, Integer>>> pinsByMob,
		Map<Integer, SpellStats> spellByMob, Map<Integer, Integer> specByMob,
		Set<Integer> protectOnlyItems) {
		var ctx = new ComputeContext();
		ctx.p = p;
		ctx.maxSwaps = max(0, p.maxSwaps);
		ctx.pinsByMob = pinsByMob == null ? Map.of() : pinsByMob;
		ctx.spellByMob = spellByMob == null ? Map.of() : spellByMob;
		ctx.specByMob = specByMob == null ? Map.of() : specByMob;
		ctx.excluded = excludedByStyle == null ? Map.of() : excludedByStyle;
		ctx.dreams = dreamItems == null ? Set.of() : withPairedAmmo(dreamItems);
		ctx.protectOnly = protectOnlyItems == null ? Set.of() : protectOnlyItems;
		ctx.lock = p.spellbookLock == null ? "" : p.spellbookLock;
		ctx.real = realLevels == null ? boostedLevels : realLevels;
		ctx.boostedLevels = boostedLevels;
		// A free world has no members prayers - F2P caps the book (audit A3.5).
		ctx.unlocks = p.f2pOnly ? F2P
			: prayerUnlocks == null ? ALL : prayerUnlocks;
		// The budget only matters when risk-constrained; pin it otherwise so
		// flipping the dropdown with the cap off cannot split the cache.
		ctx.riskBudget = p.maxTradeables >= 0 ? p.riskBudgetGp : OptimizationRequest.DEFAULT_RISK_BUDGET_GP;
		ctx.dataset = p.f2pOnly ? f2pView() : data;
		// Owned ornament/locked variants count as their base item.
		ctx.effectiveOwned = new OwnedItems(
			ctx.dataset.canonicalizeOwned(owned.getQuantities()), owned.isBankScanned());
		ctx.requirements = requirements;
		ctx.boostPicks = p.boostPicks == null ? Map.of() : p.boostPicks;
		ctx.prayerPicks = p.prayerPicks == null ? Map.of() : p.prayerPicks;
		ctx.collectionFingerprint = collectionFingerprint;
		return ctx;
	}

	/** The roster answer: one shared set per style, chosen for the best
	 * AVERAGE dps across the mobs (field decision 2026-07-17), with a per-mob
	 * display bundle for that set (index-aligned with the mob list). */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	public static final class RosterResult {
		public final List<MonsterStats> mobs;
		public final List<Map<CombatStyle, StyleResult>> perMob;
		/** The one book the whole group computed under ("" = none pinned)
		 * and why - the plates and the stand-down read it
		 * (ISS-01M5PB4R0MP149NXWNFSEEHVP8). */
		public final String groupBook;
		public final String groupBookReason;
	}

	/** The group's one spellbook: the lock already set (the player's chip,
	 * Cure Me, a fight book) stands; else the book the magic-using
	 * autocasters demand - the heaviest HP-weighted magic when they
	 * disagree; else Arceuus while thralls or Death Charge are on, so the
	 * summons stay up and magic casts from powered staves (Andrew
	 * 2026-10-24: "the spellbook has to be one total choice for everything"). */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	static final class GroupBook {
		final String book;
		final String reason;
		/** The first pass's picks disagree with the book: run again under it. */
		final boolean recompute;
	}

	private GroupBook groupBook(List<MonsterStats> mobs, List<Map<CombatStyle, StyleResult>> perMob,
		ComputeContext ctx) {
		if (!ctx.lock.isEmpty()) { return new GroupBook(ctx.lock, "", false); }
		// Per demanded book: the mob carrying the most of its magic, and its spell.
		Map<String, String> who = new LinkedHashMap<>();
		Map<String, String> what = new LinkedHashMap<>();
		Map<String, Double> heaviest = new HashMap<>();
		List<Integer> demanding = new ArrayList<>();
		boolean autocast = false;
		// A PINNED spell is the player's own word: its book is never argued
		// down by the summons.
		boolean pinnedDemand = false;
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
			pinnedDemand |= spellFor(ctx, mobs.get(j)) != null;
			double weight = kitWeight(mobs.get(j)) * magic.getDps();
			if (weight > heaviest.getOrDefault(book, -1.0)) {
				heaviest.put(book, weight);
				MonsterStats mob = mobs.get(j);
				who.put(book, mob.getName() + (mob.getVersion().isEmpty() ? "" : " (" + mob.getVersion() + ")"));
				what.put(book, spell);
			}
		}
		if (who.isEmpty()) {
			if (!ctx.p.thralls && ctx.p.deathCharge <= 0) { return new GroupBook("", "", false); }
			return new GroupBook("arceuus", "Arceuus for the group - thralls and Death Charge stay up,"
				+ " magic sets cast from powered staves", autocast);
		}
		// Raids run Arceuus whenever they can (ISS-01M5TMC6SK50VPV8713B33SPPM,
		// Andrew 2026-10-25: "all raids should try to use arceuss+thralls until
		// its not possible"): thralls on every card outweigh one card's
		// autocast; only a spell you pinned moves the book.
		if (ctx.p.raid && (ctx.p.thralls || ctx.p.deathCharge > 0) && !pinnedDemand) {
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
			double thrall = ctx.p.thralls ? ExtraDps.thrallDps(ctx.real.getMagic()) : 0;
			double arceuus = 0;
			for (int j : demanding) { arceuus += kitWeight(mobs.get(j)) * (arceuusMagicDps(ctx, mobs, j) + thrall); }
			String favour = pinnedDemand ? null
				: arceuusIfNotBeaten(arceuus, bestTotal, summons, who.get(best), what.get(best));
			if (favour != null) { return new GroupBook("arceuus", favour, autocast); }
			edge = String.format(" - Arceuus casts with %s on the magic cards would lose (%.0f vs %.0f weighted dps)",
				summons, arceuus, bestTotal);
		}
		boolean recompute = false;
		for (Map<CombatStyle, StyleResult> results : perMob) {
			DpsResult magic = ownedMagic(results);
			if (magic != null && magic.getSpellName() != null
				&& !best.equals(bookOf(ctx.dataset, magic.getSpellName()))) {
				recompute = true;
			}
		}
		var others = new StringBuilder();
		for (String book : who.keySet()) {
			if (!book.equals(best)) {
				others.append(others.length() == 0 ? " (it carries more of the group's magic than the " : " or ")
					.append(book);
			}
		}
		if (others.length() > 0) { others.append(" book would)"); }
		return new GroupBook(best, who.get(best) + " wants " + what.get(best) + " - the group runs the " + best
			+ " book" + others + edge + "; thralls and Death Charge stand down", recompute);
	}

	/** The Arceuus side of the group's book, or null when the autocast book
	 * is strictly ahead: each magic card's Arceuus cast plus its thralls,
	 * against its autocast on the other book, HP-weighted over the magic
	 * cards. Ties and near-ties stay Arceuus (Andrew 2026-10-24: "favor a
	 * powered staff but if a non-powered staff has a supreme result it
	 * should be the winner"). */
	static String arceuusIfNotBeaten(double arceuusTotal, double autocastTotal, String summons, String who, String what) {
		if (arceuusTotal + 1e-9 < autocastTotal) { return null; }
		return String.format("Arceuus for the group: Arceuus casts with %s on the magic cards outweigh %s's %s"
			+ " (%.0f vs %.0f weighted dps)", summons, who, what, arceuusTotal, autocastTotal);
	}

	/** The mob's best magic set under the Arceuus lock - a powered staff or
	 * an Arceuus autocast (the Grasp line); 0 when it owns nothing that
	 * casts there. A pinned spell is dropped: the question is what the
	 * player would cast on Arceuus. */
	private double arceuusMagicDps(ComputeContext ctx, List<MonsterStats> mobs, int j) {
		StylePlan plan = stylePlan(ctx, MAGIC, suppliedAcross(mobs));
		OptimizationRequest request = ownedRequestFor(ctx, mobs.get(j), MAGIC, plan.levels, null)
			.withSpell(null).withSpellbookLock("arceuus");
		List<DpsResult> out = optimizer.optimize(ctx.dataset, request, optimizer.preparePools(ctx.dataset, request));
		return out.isEmpty() ? 0 : out.get(0).getDps();
	}

	/** The mob's shown owned magic set, or null. */
	private static DpsResult ownedMagic(Map<CombatStyle, StyleResult> results) {
		StyleResult magic = results.get(MAGIC);
		return magic == null || magic.owned.isEmpty() ? null : magic.owned.get(0);
	}

	/** Magic is how this mob gets fought: nothing else owned beats it. */
	private static boolean usesMagic(Map<CombatStyle, StyleResult> results, DpsResult magic) {
		for (var entry : results.entrySet()) {
			if (entry.getKey() != MAGIC && !entry.getValue().owned.isEmpty()
				&& entry.getValue().owned.get(0).getDps() > magic.getDps()) {
				return false;
			}
		}
		return true;
	}

	private static String bookOf(LoadoutData dataset, String spell) {
		for (SpellStats s : dataset.getSpells()) {
			if (s.getName().equals(spell)) { return Names.lower(s.getSpellbook()); }
		}
		return "";
	}

	/** This mob's first-pass magic set re-cast under `book`: its own dps on
	 * its own book, 0 when its staff can cast nothing from the other one. */
	private double magicDpsUnder(ComputeContext ctx, List<MonsterStats> mobs, int j,
		Map<CombatStyle, StyleResult> results, String book) {
		DpsResult magic = ownedMagic(results);
		if (book.equals(bookOf(ctx.dataset, magic.getSpellName()))) { return magic.getDps(); }
		StylePlan plan = stylePlan(ctx, MAGIC, suppliedAcross(mobs));
		OptimizationRequest request = ownedRequestFor(ctx, mobs.get(j), MAGIC, plan.levels, null)
			.withSpellbookLock(book);
		DpsResult cast = optimizer.bestSpellFor(ctx.dataset, request, magic.getLoadout(), new DpsCalculator());
		return cast == null ? 0 : cast.getDps();
	}

	/** The raid's own boost when EVERY mob is inside the same raid (CoX
	 * overload+, ToA salts), else null. */
	private static BoostProfile suppliedAcross(List<MonsterStats> mobs) {
		BoostProfile supplied = suppliedBoost(mobs.get(0));
		for (MonsterStats mob : mobs) {
			if (suppliedBoost(mob) != supplied) { return null; }
		}
		return supplied;
	}

	/**
	 * Greedy bench selection: candidates are the per-mob free-best items
	 * that differ from the shared base; each round carries the item with
	 * the best HP-weighted marginal gain (every mob wearing its best
	 * combination of base + carried) until the bench is full or nothing
	 * gains. Small scale by construction: candidates <= mobs x slots.
	 */
	/** True when a per-mob optimize answer has nothing usable in front. */
	private static boolean emptyBest(List<DpsResult> best) {
		return best == null || best.isEmpty() || best.get(0) == null;
	}

	/** The same-style swap candidate pool: every per-mob free-best item
	 * that differs from the base in its slot. */
	private static Collection<GearItem> swapCandidates(Loadout base,
		List<List<DpsResult>> freeBests) {
		LinkedHashMap<Integer, GearItem> candidates = new LinkedHashMap<>();
		for (List<DpsResult> best : freeBests) {
			if (emptyBest(best)) { continue; }
			for (GearItem item : best.get(0).getLoadout().items()) {
				GearItem baseItem = base.get(item.getSlot());
				if (baseItem == null || baseItem.getId() != item.getId()) {
					candidates.putIfAbsent(item.getId(), item);
				}
			}
		}
		return candidates.values();
	}

	private List<GearItem> chooseSwaps(DpsCalculator calc, Loadout base,
		List<OptimizationRequest> reqs, double[] weights,
		List<List<DpsResult>> freeBests, int benchForSwaps) {
		List<GearItem> carried = new ArrayList<>();
		if (benchForSwaps <= 0) { return carried; }
		Collection<GearItem> candidates = swapCandidates(base, freeBests);
		double current = hpWeightedTotal(calc, base, carried, reqs, weights);
		while (carried.size() < benchForSwaps) {
			GearItem bestPick = null;
			double bestTotal = current;
			for (GearItem candidate : candidates) {
				if (carried.contains(candidate)) { continue; }
				carried.add(candidate);
				double total = hpWeightedTotal(calc, base, carried, reqs, weights);
				carried.remove(carried.size() - 1);
				if (total > bestTotal + 1e-9) {
					bestTotal = total;
					bestPick = candidate;
				}
			}
			if (bestPick == null) {
				break; // nothing left that helps
			}
			carried.add(bestPick);
			current = bestTotal;
		}
		return carried;
	}

	private double hpWeightedTotal(DpsCalculator calc, Loadout base,
		List<GearItem> carried, List<OptimizationRequest> reqs, double[] weights) {
		double sum = 0;
		for (int j = 0; j < reqs.size(); j++) {
			DpsResult worn = calcRespecting(calc, reqs.get(j), bestWorn(calc, reqs.get(j), base, carried));
			sum += (worn == null ? 0 : worn.getDps()) * weights[j];
		}
		return sum;
	}

	/** calculate(), refusing a loadout that wears an item EXCLUDED for
	 * this mob's request (field decision 2026-07-17: exclusions are
	 * per-mob - the kit may carry the scythe for the roster, but the
	 * excluding mob's shown answer never wears it). */
	/** The true upgrade cost of a set: the summed price of pieces the
	 * player does NOT own (field bug 2026-08-14: kit-backed cards showed
	 * the WHOLE set's value - calculate() defaults purchaseCost to
	 * loadout.getCost(), and only the beam path was overwriting it). */
	private static int unownedValue(Loadout loadout, OwnedItems owned) {
		if (loadout == null) { return 0; }
		long total = 0;
		for (GearItem item : loadout.items()) {
			if (owned == null || !owned.owns(item.getId())) { total += item.getPriceOrZero(); }
		}
		return (int) min(Integer.MAX_VALUE, total);
	}

	private DpsResult calcRespecting(DpsCalculator calc,
		OptimizationRequest req, Loadout loadout) {
		if (loadout != null) {
			Set<Integer> excluded = req.getExcludedItems();
			// A per-mob SIM is pretend-owned for THAT mob only (field bug
			// 2026-07-18: a rapier simmed vs one group member leaked into
			// every mob's shared answer) - on the owned side, a loadout may
			// only wear what this request's bank or dreams grant.
			boolean ownedOnly = req.getCandidateMode() == CandidateMode.OWNED_ONLY;
			for (GearItem item : loadout.items()) {
				if (excluded != null && excluded.contains(item.getId())) { return null; }
				if (ownedOnly && !req.getOwnedItems().owns(item.getId())
					&& !req.isDream(item.getId())) {
					return null;
				}
			}
			// A member's PIN binds its own shown set the same way (ISS-
			// 01M4Y2VJ8DWBBKFR1HP9NR45NG): the shared set and the swap
			// passes are dps-greedy, so without this a second member's
			// pin would never be worn against it.
			for (var pin : req.getPinnedItems().entrySet()) {
				GearItem worn = loadout.get(pin.getKey());
				if (worn == null || worn.getId() != pin.getValue()) { return null; }
			}
		}
		// An auto-spell magic request scores its best autocast, the pick the
		// single-mob optimizer makes (ISS-01M5K9KFJH7EZ0GMAKNGZ4K2JG).
		// Autocasting weapons only (staves, wands): a melee weapon's hand-cast
		// spell must not make magic look viable and reshape the kit.
		if (loadout != null && req.getStyle() == MAGIC && req.isAutoSpell()
			&& loadout.getWeapon() != null && (loadout.getWeapon().getCategoryLower().contains("staff")
				|| loadout.getWeapon().getCategoryLower().contains("wand"))) {
			DpsResult cast = optimizer.bestSpellFor(data, req, loadout, calc);
			// No legal autocast (a melee weapon on a magic card): keep the
			// plain score the roster has always read - a null here reads as
			// "cannot fight this way" and reshapes the whole kit.
			if (cast != null) { return cast; }
		}
		return calc.calculate(req, loadout);
	}

	/** The trip inventory while fighting ONE mob: the roster PLAN (the
	 * union of what the mobs actually WEAR in their best answers, plus
	 * the spec weapon) minus what is worn right now. Gear no mob wears
	 * is not brought at all (field fix 2026-07-17: base-compromise and
	 * candidate leftovers read as junk at Inventory 20), and a worn item
	 * never duplicates into the view. */
	private static List<GearItem> inventoryFor(Collection<GearItem> plan,
		GearItem specCarried, Loadout worn) {
		Set<Integer> wornIds = worn == null ? Set.of() : worn.ids();
		List<GearItem> inventory = new ArrayList<>();
		Set<Integer> packed = new HashSet<>();
		if (specCarried != null && !wornIds.contains(specCarried.getId())
			&& packed.add(specCarried.getId())) {
			inventory.add(specCarried);
		}
		for (GearItem item : plan) {
			if (!wornIds.contains(item.getId()) && packed.add(item.getId())) { inventory.add(item); }
		}
		return inventory;
	}

	/** A cross-style carry: another style's weapon (+ its ammo when the
	 * weapon needs slot ammo), worn INTO the primary base set and rolled
	 * under its OWN style's request - "bring a blowpipe in your melee
	 * gear". Cost = bench slots consumed; a weapon the bench already
	 * carries as the spec weapon costs nothing extra. */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	private static final class SwapBundle {
		final CombatStyle style;
		final List<GearItem> items;
		final int cost;
	}

	/** The chosen kit: same-style single swaps + cross-style bundles. */
	private static final class KitAnswer {
		final List<GearItem> singles = new ArrayList<>();
		final List<SwapBundle> bundles = new ArrayList<>();
		double total;
	}

	/** The mob's own free best, when the kit can actually assemble it -
	 * every worn item present in the base or carried. This is what makes
	 * a BIG bench an EASY problem (field insight 2026-07-17): at the
	 * limit every mob simply wears its own best owned set, exactly. */
	private static DpsResult coverableBest(List<DpsResult> freeBest,
		Loadout base, List<GearItem> carried) {
		if (emptyBest(freeBest)) { return null; }
		Set<Integer> have = base.ids();
		for (GearItem item : carried) { have.add(item.getId()); }
		for (GearItem item : freeBest.get(0).getLoadout().items()) {
			if (!have.contains(item.getId())) { return null; }
		}
		return freeBest.get(0);
	}

	/** Wear a bundle into the base set; null when it cannot be worn
	 * honestly (shield under a 2h, projectile weapon with no ammo). */
	private static Loadout applyBundle(Loadout base, SwapBundle bundle) {
		Loadout out = base;
		for (GearItem item : bundle.items) {
			Loadout next = withSwap(out, item);
			if (next == null) { return null; }
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

	/** Cross-style bundle candidates for a primary base: the other styles'
	 * shared and per-mob free-best weapons, each offered bare and (when the
	 * source set wears ammo) with its ammo. */
	private static List<SwapBundle> bundleCandidates(CombatStyle primary, Loadout base,
		GearItem specCarried, Map<CombatStyle, SharedSide> sides) {
		List<SwapBundle> out = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (var entry : sides.entrySet()) {
			CombatStyle style = entry.getKey();
			if (style == primary) { continue; }
			List<Loadout> sources = new ArrayList<>();
			sources.add(entry.getValue().shared);
			for (List<DpsResult> best : entry.getValue().bests) {
				if (!emptyBest(best)) { sources.add(best.get(0).getLoadout()); }
			}
			for (Loadout source : sources) {
				GearItem weapon = source.getWeapon();
				if (weapon == null
					|| (base.getWeapon() != null && base.getWeapon().getId() == weapon.getId())) {
					continue;
				}
				GearItem ammo = brought(source, base, AMMO);
				int specFree = specCarried != null && specCarried.getId() == weapon.getId() ? 1 : 0;
				if (seen.add(weapon.getId() * 2L)) {
					out.add(new SwapBundle(style,
						Collections.singletonList(weapon), max(0, 1 - specFree)));
				}
				if (ammo != null && seen.add(weapon.getId() * 2L + 1)) {
					List<GearItem> items = new ArrayList<>();
					items.add(weapon);
					items.add(ammo);
					out.add(new SwapBundle(style, items, max(0, 2 - specFree)));
				}
			}
		}
		return out;
	}

	/** One mob's best answer given the kit: the primary base + singles,
	 * challenged by every carried bundle rolled under its own style, and
	 * by any style's FREE BEST the kit can fully assemble - the best of
	 * every style's shownFor (only its dps is read). */
	private DpsResult kitBest(DpsCalculator calc, Loadout base, CombatStyle primary,
		List<GearItem> singles, List<SwapBundle> bundles, List<GearItem> carried,
		Map<CombatStyle, SharedSide> sides, int mobIndex) {
		DpsResult best = null;
		for (CombatStyle s : sides.keySet()) {
			best = better(best,
				shownFor(calc, base, primary, singles, bundles, carried, sides, mobIndex, s));
		}
		return best;
	}

	/** One (mob, style) answer on a kit: the primary style wears the base
	 * + singles, a bundled style wears its weapon into the base armour, and
	 * any style whose free best the kit can fully assemble shows it exactly
	 * (the best of the three, the earlier on ties); null when the style has
	 * no answer on this kit. */
	private DpsResult shownFor(DpsCalculator calc, Loadout base, CombatStyle primary,
		List<GearItem> singles, List<SwapBundle> bundles, List<GearItem> carried,
		Map<CombatStyle, SharedSide> sides, int mobIndex, CombatStyle s) {
		SharedSide side = sides.get(s);
		OptimizationRequest req = side.reqs.get(mobIndex);
		DpsResult result = s == primary
			? calcRespecting(calc, req, bestWorn(calc, req, base, singles)) : null;
		for (SwapBundle bundle : bundles) {
			Loadout seeded = bundle.style == s ? applyBundle(base, bundle) : null;
			if (seeded != null) {
				result = better(result,
					calcRespecting(calc, req, bestWorn(calc, req, seeded, singles)));
			}
		}
		return better(result, coverableBest(side.bests.get(mobIndex), base, carried));
	}

	/** One mob's kit dps, MEMOIZED (field insight 2026-07-17: hone the
	 * greedy toward dynamic programming): the answer depends only on the
	 * carried bundles and the carried singles RELEVANT to this mob (items
	 * worn by some free best of its own), so the key is exactly that -
	 * testing a candidate against the roster re-evaluates only the mobs
	 * it could matter to, and repeat evaluations are table lookups. */
	private double evalMobDps(DpsCalculator calc, Loadout base, CombatStyle primary,
		List<GearItem> singles, List<SwapBundle> bundles, String bundlesKey,
		Map<CombatStyle, SharedSide> sides,
		Set<Integer> relevant, int mobIndex, Map<String, Double> memo) {
		List<GearItem> relevantSingles = new ArrayList<>();
		List<Integer> ids = new ArrayList<>();
		for (GearItem item : singles) {
			if (relevant.contains(item.getId())) {
				relevantSingles.add(item);
				ids.add(item.getId());
			}
		}
		Collections.sort(ids);
		String key = mobIndex + "|" + bundlesKey + "|" + ids;
		Double hit = memo.get(key);
		if (hit != null) { return hit; }
		DpsResult shown = kitBest(calc, base, primary, relevantSingles, bundles,
			carriedOf(relevantSingles, bundles), sides, mobIndex);
		double dps = shown == null ? 0 : shown.getDps();
		memo.put(key, dps);
		return dps;
	}

	private static String bundlesKeyOf(List<SwapBundle> bundles) {
		List<String> parts = new ArrayList<>();
		for (SwapBundle bundle : bundles) {
			var part = new StringBuilder().append(bundle.style.ordinal()).append(':');
			for (GearItem item : bundle.items) { part.append(item.getId()).append(','); }
			parts.add(part.toString());
		}
		Collections.sort(parts);
		return String.join(";", parts);
	}

	/** The cross-style single-swap candidate pool for a kit search: the
	 * primary style's diff items PLUS the other carried styles' non-weapon
	 * pieces, deduped by id - the diff between the per-mob BiS sets IS the
	 * candidate list, so a generous budget can assemble the other style's
	 * FULL set, not just its weapon. The other styles' weapons are excluded
	 * because a weapon swap is a bundle, not a single. */
	private static List<GearItem> crossStylePool(Loadout base, CombatStyle primary,
		Map<CombatStyle, SharedSide> sides) {
		List<GearItem> pool = new ArrayList<>(swapCandidates(base, sides.get(primary).bests));
		for (CombatStyle s : sides.keySet()) {
			if (s == primary) { continue; }
			for (GearItem item : swapCandidates(base, sides.get(s).bests)) {
				if (item.getSlot() != WEAPON
					&& pool.stream().noneMatch(i -> i.getId() == item.getId())) {
					pool.add(item);
				}
			}
		}
		return pool;
	}

	/** Kit selection. A BIGGER bench is an EASIER problem (field insight
	 * 2026-07-17): the ceiling is each mob wearing its own winning free
	 * best, so first test that ideal - the union of every mob's winning
	 * diff vs the base; when it fits the budget, carry exactly that with
	 * NO search. Only a LIMITED budget ranks the diff items: greedy over
	 * singles (cost 1) and bundles (their slot cost) by HP-weighted
	 * marginal gain - "the most valuable swap item, then the 2nd" -
	 * with cost-0 bundles (the spec weapon doubling as the other style's
	 * weapon) fitting even a full bench. */
	private KitAnswer chooseKit(DpsCalculator calc, Loadout base, CombatStyle primary,
		Collection<GearItem> singleCandidates, List<SwapBundle> bundleCandidates,
		Map<CombatStyle, SharedSide> sides,
		List<MonsterStats> mobs, int slots, Map<String, Double> memo) {
		List<GearItem> allNeeded = new ArrayList<>();
		double idealTotal = 0;
		for (int j = 0; j < mobs.size(); j++) {
			CombatStyle answer = answerFor(sides, j);
			if (answer == null) { continue; }
			DpsResult winner = sides.get(answer).bests.get(j).get(0);
			idealTotal += winner.getDps() * kitWeight(mobs.get(j));
			for (GearItem item : winner.getLoadout().items()) {
				GearItem baseItem = base.get(item.getSlot());
				if ((baseItem == null || baseItem.getId() != item.getId())
					&& allNeeded.stream().noneMatch(i -> i.getId() == item.getId())) {
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
		Map<Integer, Set<Integer>> mobsByItem = new HashMap<>();
		List<Set<Integer>> relevantByMob = new ArrayList<>();
		for (int j = 0; j < n; j++) {
			Set<Integer> relevant = new HashSet<>();
			for (SharedSide styleSide : sides.values()) {
				List<DpsResult> best = styleSide.bests.get(j);
				if (emptyBest(best)) { continue; }
				for (GearItem item : best.get(0).getLoadout().items()) {
					relevant.add(item.getId());
					mobsByItem.computeIfAbsent(item.getId(), k -> new HashSet<>()).add(j);
				}
			}
			relevantByMob.add(relevant);
		}
		double[] hp = new double[n];
		for (int j = 0; j < n; j++) { hp[j] = kitWeight(mobs.get(j)); }
		var kit = new KitAnswer();
		int used = 0;
		double[] mobDps = new double[n];
		double current = 0;
		String baseBundlesKey = bundlesKeyOf(kit.bundles);
		for (int j = 0; j < n; j++) {
			mobDps[j] = evalMobDps(calc, base, primary, kit.singles, kit.bundles,
				baseBundlesKey, sides, relevantByMob.get(j), j, memo);
			current += mobDps[j] * hp[j];
		}
		while (true) {
			GearItem bestSingle = null;
			SwapBundle bestBundle = null;
			double bestTotal = current;
			int bestCost = 0;
			String bundlesKey = bundlesKeyOf(kit.bundles);
			for (GearItem candidate : singleCandidates) {
				if (used + 1 > slots || kit.singles.contains(candidate)) { continue; }
				Set<Integer> affected = mobsByItem.get(candidate.getId());
				if (affected == null || affected.isEmpty()) {
					continue; // in nobody's free best - it can help no one
				}
				kit.singles.add(candidate);
				double total = current;
				for (int j : affected) {
					total += (evalMobDps(calc, base, primary, kit.singles, kit.bundles,
						bundlesKey, sides, relevantByMob.get(j), j, memo)
						- mobDps[j]) * hp[j];
				}
				kit.singles.remove(kit.singles.size() - 1);
				if (total > bestTotal + 1e-9) {
					bestTotal = total;
					bestSingle = candidate;
					bestBundle = null;
					bestCost = 1;
				}
			}
			for (SwapBundle candidate : bundleCandidates) {
				if (used + candidate.cost > slots || kit.bundles.contains(candidate)) { continue; }
				// A new bundle opens a style path for every mob.
				kit.bundles.add(candidate);
				String withKey = bundlesKeyOf(kit.bundles);
				double total = 0;
				for (int j = 0; j < n; j++) {
					total += evalMobDps(calc, base, primary, kit.singles, kit.bundles,
						withKey, sides, relevantByMob.get(j), j, memo) * hp[j];
				}
				kit.bundles.remove(kit.bundles.size() - 1);
				if (total > bestTotal + 1e-9) {
					bestTotal = total;
					bestBundle = candidate;
					bestSingle = null;
					bestCost = candidate.cost;
				}
			}
			if (bestSingle == null && bestBundle == null) { break; }
			if (bestSingle != null) {
				kit.singles.add(bestSingle);
			} else {
				kit.bundles.add(bestBundle);
			}
			used += bestCost;
			current = bestTotal;
			String appliedKey = bundlesKeyOf(kit.bundles);
			for (int j = 0; j < n; j++) {
				mobDps[j] = evalMobDps(calc, base, primary, kit.singles, kit.bundles,
					appliedKey, sides, relevantByMob.get(j), j, memo);
			}
		}
		kit.total = current;
		return kit;
	}

	/**
	 * The best worn combination for ONE mob: start from the base and
	 * greedily apply the single carried swap with the biggest gain until
	 * none helps (approximates set-bonus interactions without a subset
	 * explosion). A 2h swap clears the shield; a shield swap is skipped
	 * under a 2h weapon.
	 */
	private Loadout bestWorn(DpsCalculator calc, OptimizationRequest req,
		Loadout base, List<GearItem> carried) {
		Loadout worn = base;
		DpsResult wornResult = calcRespecting(calc, req, worn);
		double wornDps = wornResult == null ? 0 : wornResult.getDps();
		boolean improved = true;
		Set<Integer> applied = new HashSet<>();
		while (improved) {
			improved = false;
			Loadout bestNext = null;
			double bestDps = wornDps;
			Integer bestId = null;
			for (GearItem swap : carried) {
				if (applied.contains(swap.getId())) { continue; }
				Loadout trial = withSwap(worn, swap);
				if (trial == null) { continue; }
				DpsResult r = calcRespecting(calc, req, trial);
				double dps = r == null ? 0 : r.getDps();
				if (dps > bestDps + 1e-9) {
					bestDps = dps;
					bestNext = trial;
					bestId = swap.getId();
				}
			}
			if (bestNext != null) {
				worn = bestNext;
				wornDps = bestDps;
				applied.add(bestId);
				improved = true;
			}
		}
		return worn;
	}

	/** A copy with one slot changed (null empties it) and NO quiver ammo -
	 * the kit passes price every re-show from the slot contents alone. */
	private static Loadout withSlot(Loadout base, GearSlot slot, GearItem item) {
		return new Loadout(base.with(slot, item).getGear());
	}

	private static Loadout withSwap(Loadout base, GearItem swap) {
		GearSlot slot = swap.getSlot();
		GearItem weapon = base.getWeapon();
		if (slot == SHIELD
			&& weapon != null && weapon.isTwoHanded()) {
			return null; // no shield under a 2h
		}
		Loadout out = withSlot(base, slot, swap);
		return slot == WEAPON && swap.isTwoHanded() ? withSlot(out, SHIELD, null) : out;
	}

	/** Pick the set that maximises the HP-WEIGHTED average dps across the
	 * roster (field decision 2026-07-17, superseding the plain mean): a
	 * 500-hp mob's needs outweigh a 50-hp mob's ten to one, because that is
	 * where the trip's time goes. A mob the set cannot damage contributes
	 * zero to every candidate regardless of its hp - so an unhittable big
	 * mob drops out of the decision naturally ("unless you can't hit it").
	 * Candidate pool = each mob's own best set; a compromise that is
	 * nobody's #1 is not considered (heuristic). */
	private Loadout chooseSharedLoadout(DpsCalculator calc,
		List<List<DpsResult>> bests, List<OptimizationRequest> reqs, double[] weights) {
		List<Loadout> candidates = new ArrayList<>();
		for (List<DpsResult> b : bests) {
			if (!emptyBest(b)) { candidates.add(b.get(0).getLoadout()); }
		}
		Loadout best = null;
		double bestScore = -1;
		for (Loadout loadout : candidates) {
			double sum = 0;
			for (int j = 0; j < reqs.size(); j++) {
				DpsResult r = calcRespecting(calc, reqs.get(j), loadout);
				sum += (r == null ? 0 : r.getDps()) * weights[j];
			}
			if (sum > bestScore + 1e-9) {
				bestScore = sum;
				best = loadout;
			}
		}
		return best;
	}

	/** Per style: optimise each mob independently, choose ONE shared owned
	 * set and ONE shared game set by average dps, then rebuild every mob's
	 * StyleResult around the shared sets (that mob's own dps/incoming/spec).
	 * Returns null if the request was superseded mid-compute. */
	private List<Map<CombatStyle, StyleResult>> computeStyleAcross(
		List<MonsterStats> mobs, ComputeContext ctx, long ticket) {
		int n = mobs.size();
		List<Map<CombatStyle, StyleResult>> perMob = new ArrayList<>();
		for (int j = 0; j < n; j++) { perMob.add(new EnumMap<>(CombatStyle.class)); }
		var calc = new DpsCalculator();
		// Per-style state the cross-style kit passes reuse after the loop -
		// one pass for the Yours side, one for the BiS side.
		Map<CombatStyle, SharedSide> ownedSides = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, SharedSide> gameSides = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, StyleWork> works = new EnumMap<>(CombatStyle.class);
		// PASS A (field fix 2026-07-17: the load-time wall was the 2x3xN
		// independent optimize() calls, not the kit search): build every
		// style's plan and requests up front, then ONE flat fan-out over
		// the pool - no per-style straggler waves, owned and game as
		// separate tasks. Each task owns its optimizer (its DpsCalculator
		// is stateful); distinct array slots + invokeAll's join publish.
		List<Callable<Void>> optimizeTasks = new ArrayList<>();
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
				final int index = j;
				final MonsterStats mob = mobs.get(index);
				optimizeTasks.add(() -> {
					w.ownedArr[index] = optimizeCached(mob, style, false, ctx,
						w.ownedReqs.get(index), ticket);
					return null;
				});
				optimizeTasks.add(() -> {
					w.gameArr[index] = optimizeCached(mob, style, true, ctx,
						w.gameReqs.get(index), ticket);
					return null;
				});
			}
		}
		try {
			// Check every future: a task that THROWS otherwise vanishes
			// silently, its array slot stays null, and the whole style
			// renders as "-" with no trace (field bug 2026-08-05: the
			// Sire roster's ranged and magic buttons dashed while every
			// member answered fine alone).
			for (Future<Void> f : rosterPool.invokeAll(optimizeTasks)) {
				try {
					f.get();
				} catch (ExecutionException e) { log.warn("roster optimize task failed", e.getCause()); }
			}
		} catch (InterruptedException e) {
			// Cancellation of a superseded search is cooperative, via requestSeq
			// (checked immediately below) - not thread interruption. This runs on
			// our own daemon optimizer worker, interrupted only when the executor
			// is shut down on plugin stop, so just abandon the computation.
			return null;
		}
		if (requestSeq.get() != ticket) {
			abandonedForTest++;
			return null;
		}
		// Each side's free bests, every style together: a style's shared kit
		// answers to the members fought with it (styleUseWeights).
		Map<CombatStyle, List<List<DpsResult>>> ownedByStyle = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, List<List<DpsResult>>> gameByStyle = new EnumMap<>(CombatStyle.class);
		for (CombatStyle style : concreteValues()) {
			StyleWork w = works.get(style);
			List<List<DpsResult>> ownedBests = new ArrayList<>();
			List<List<DpsResult>> gameBests = new ArrayList<>();
			for (int j = 0; j < n; j++) {
				ownedBests.add(w.ownedArr[j] == null ? new ArrayList<>() : w.ownedArr[j]);
				gameBests.add(w.gameArr[j] == null ? new ArrayList<>() : w.gameArr[j]);
			}
			ownedByStyle.put(style, ownedBests);
			gameByStyle.put(style, gameBests);
		}
		for (CombatStyle style : concreteValues()) {
			StyleWork w = works.get(style);
			List<OptimizationRequest> ownedReqs = w.ownedReqs;
			List<OptimizationRequest> gameReqs = w.gameReqs;
			List<List<DpsResult>> ownedBests = ownedByStyle.get(style);
			List<List<DpsResult>> gameBests = gameByStyle.get(style);
			SharedSide ownedSide = sharedSideFor(calc, ctx, mobs, ownedReqs, ownedBests, ctx.effectiveOwned,
				styleUseWeights(mobs, style, ownedByStyle));
			SharedSide gameSide = sharedSideFor(calc, ctx, mobs, gameReqs, gameBests, null,
				styleUseWeights(mobs, style, gameByStyle));
			Loadout sharedOwned = ownedSide.shared;
			Loadout sharedGame = gameSide.shared;
			SpecPick[] specs = ownedSide.specOptions.isEmpty() ? new SpecPick[n] : ownedSide.specOptions.get(0);
			SpecPick[] gameSpecs = gameSide.specOptions.isEmpty() ? new SpecPick[n] : gameSide.specOptions.get(0);
			// The INVENTORY budget counts every carried item INCLUDING the
			// spec weapon (field decision 2026-07-17, reversing same-day:
			// Inventory 0 = strictly one worn set, NO spec recommended).
			// Swaps are chosen greedily by HP-weighted marginal gain; each
			// mob then WEARS its best combination of base + carried swaps.
			// The bench reserves slots for the PRESUMPTIVE (top-ranked)
			// spec; the kit pass re-prices each option it actually seats.
			GearItem[] carriedSlots = carriedSpecOf(specs, sharedOwned);
			GearItem[] gameSlots = carriedSpecOf(gameSpecs, sharedGame);
			int benchForSwaps = max(0, ctx.maxSwaps - slotsOf(carriedSlots));
			List<GearItem> swaps = sharedOwned == null ? List.of()
				: chooseSwaps(calc, sharedOwned, ownedReqs, ownedSide.weights, ownedBests, benchForSwaps);
			w.specs = specs;
			w.gameSpecs = gameSpecs;
			if (sharedOwned != null) { ownedSides.put(style, ownedSide); }
			if (sharedGame != null) { gameSides.put(style, gameSide); }
			if (sharedOwned != null && !swaps.isEmpty()) {
				// Re-show each mob wearing its best base+swaps combination;
				// when the carried kit can fully assemble the mob's own
				// free best, show THAT exactly (big bench = easy problem).
				for (int j = 0; j < n; j++) {
					Loadout worn = bestWorn(calc, ownedReqs.get(j), sharedOwned, swaps);
					ownedSide.shown[j] = better(calcRespecting(calc, ownedReqs.get(j), worn),
						coverableBest(ownedBests.get(j), sharedOwned, swaps));
				}
			}
			// The trip PLAN for this style: the union of what the mobs
			// actually wear across the roster - the inventory view shows
			// plan-minus-worn, so displaced pieces no mob uses drop out.
			var plan = new LinkedHashMap<Integer, GearItem>();
			for (DpsResult shown : ownedSide.shown) {
				if (shown != null) { pack(plan, shown.getLoadout().items()); }
			}
			pack(plan, Arrays.asList(carriedSlots[1], carriedSlots[2]));
			for (int j = 0; j < n; j++) {
				MonsterStats mob = mobs.get(j);
				// The single-mob post-passes the roster shown sets never got
				// (field reports 2026-08-09): the Dizana's quiver relocation,
				// and the mandatory-recoil swap (a Zulrah roster recommended
				// sets with no recoil gear).
				DpsResult ownedShown = postPass(ctx, ownedReqs.get(j), ownedSide.shown[j]);
				DpsResult gameShown = postPass(ctx, gameReqs.get(j), gameSide.shown[j]);
				// calculate() returns null vs an immune mob (the TD shield
				// phases) - an honest empty list, never a null element the
				// card would trip over.
				List<DpsResult> ownedList = new ArrayList<>();
				if (ownedShown != null) { ownedList.add(ownedShown); }
				SpecPick spec = specs[j];
				SpecPick gameSpec = gameSpecs[j];
				Result incoming = incomingFor(mob, sharedOwned, ctx);
				Result gameIncoming = incomingFor(mob, sharedGame, ctx);
				Loadout worn = ownedShown == null ? sharedOwned : ownedShown.getLoadout();
				List<GearItem> bench = sharedOwned == null ? List.of()
					: inventoryFor(plan.values(), carriedSlots[0], worn);
				List<GearItem> gameBench = gameSlots[0] == null
					? List.of()
					: gameSlots[2] == null ? Collections.singletonList(gameSlots[0])
					: Arrays.asList(gameSlots[0], gameSlots[2]);
				StyleResult sr = verified(new StyleResult(ownedList, gameShown, spec, gameSpec,
					w.plan.label, w.plan.gameLabel, incoming, gameIncoming, bench, gameBench, true, true),
					ctx.dataset, ownedReqs.get(j), gameReqs.get(j));
				perMob.get(j).put(style, sr);
			}
		}
		// THE CROSS-STYLE KIT (field decision 2026-07-17): bench slots may
		// carry ANOTHER style's weapon, so one carried kit can answer a
		// style-immune phase (TD shields) with a genuine style switch. The
		// pass runs for BOTH sides - the Yours kit over your bank and the
		// BiS kit over the whole game, under the same inventory budget.
		KitView ownedView = ctx.maxSwaps >= 1 && ownedSides.size() >= 2
			? kitPass(calc, mobs, ctx, ticket, ownedSides)
			: null;
		KitView gameView = ctx.maxSwaps >= 1 && gameSides.size() >= 2
			? kitPass(calc, mobs, ctx, ticket, gameSides)
			: null;
		if (requestSeq.get() != ticket) {
			abandonedForTest++;
			return null;
		}
		if (ownedView != null || gameView != null) {
			// Merge the kit views over the phase-1 tabs: each side that has
			// a kit replaces its half of every StyleResult; a style the kit
			// cannot reach shows nothing on that side (no set you cannot
			// assemble mid-trip). The other side's fields carry over.
			for (int j = 0; j < n; j++) {
				for (CombatStyle s : CombatStyle.values()) {
					StyleResult old = perMob.get(j).get(s);
					if (old == null) { continue; }
					List<DpsResult> ownedList = old.owned;
					StyleWork w = works.get(s);
					SpecPick spec = w.specs[j];
					String label = old.boostLabel;
					Result incoming = old.incoming;
					List<GearItem> bench = old.bench;
					boolean ownedKitAnswers = ownedView != null && ownedView.styles.contains(s)
						&& ownedView.shownByMob.get(j).get(s) != null;
					if (ownedKitAnswers) {
						DpsResult result = ownedView.shownByMob.get(j).get(s);
						result = result.withPurchaseCost(
							unownedValue(result.getLoadout(), ctx.effectiveOwned));
						ownedList = new ArrayList<>();
						ownedList.add(result);
						spec = ownedView.specs != null ? ownedView.specs[j] : null;
						if (spec == null) { spec = pickFor(ownedSides.get(s), j, ownedView.specCarried); }
						label = w.plan.label;
						Loadout worn = result.getLoadout();
						incoming = incomingFor(mobs.get(j), worn, ctx);
						bench = inventoryFor(ownedView.plan.values(), ownedView.specCarried, worn);
					} else if (ownedView != null) {
						// The kit has no answer for this style here - keep the
						// PRE-KIT shared per-style answer instead of blanking
						// (field bug 2026-08-05: the Sire roster dashed ranged
						// and magic while every mob answered them alone). The
						// carried spec is kit-specific, so only that is
						// dropped.
						spec = null;
					}
					DpsResult gameBest = old.overallBest;
					SpecPick gameSpec = w.gameSpecs[j];
					String gameLabel = old.gameBoostLabel;
					Result gameIncoming = old.gameIncoming;
					List<GearItem> gameBench = old.gameBench;
					boolean gameKitAnswers = gameView != null && gameView.styles.contains(s)
						&& gameView.shownByMob.get(j).get(s) != null;
					if (gameKitAnswers) {
						gameBest = gameView.shownByMob.get(j).get(s);
						gameBest = gameBest.withPurchaseCost(
							unownedValue(gameBest.getLoadout(), ctx.effectiveOwned));
						gameSpec = gameView.specs != null ? gameView.specs[j] : null;
						if (gameSpec == null) { gameSpec = pickFor(gameSides.get(s), j, gameView.specCarried); }
						gameLabel = w.plan.gameLabel;
						Loadout worn = gameBest.getLoadout();
						gameIncoming = incomingFor(mobs.get(j), worn, ctx);
						gameBench = inventoryFor(gameView.plan.values(), gameView.specCarried, worn);
					} else if (gameView != null) {
						// Same fallback on the BiS side: the ceiling per style
						// always shows (it is aspirational by definition); only
						// the kit-specific spec drops.
						gameSpec = null;
					}
					// The single-mob post-passes on whichever shown set won
					// (base-shared or kit-backed): quiver relocation and the
					// mandatory-recoil swap - the kit's shownByMob sets bypass
					// the earlier per-mob pass (field reports 2026-08-09).
					OptimizationRequest own = ownedSides.get(s) == null
						? null : ownedSides.get(s).reqs.get(j);
					OptimizationRequest game = gameSides.get(s) == null
						? null : gameSides.get(s).reqs.get(j);
					if (ownedList != null && !ownedList.isEmpty() && own != null) {
						ownedList = new ArrayList<>(ownedList);
						ownedList.set(0, postPass(ctx, own, ownedList.get(0)));
					}
					if (gameBest != null && game != null) { gameBest = postPass(ctx, game, gameBest); }
					// A side is kit-backed when it has no kit at all (nothing
					// to contradict) or the kit just answered this style.
					boolean ownedKitBacked = ownedView == null || ownedKitAnswers;
					boolean gameKitBacked = gameView == null || gameKitAnswers;
					perMob.get(j).put(s, verified(new StyleResult(ownedList, gameBest, spec, gameSpec,
						label, gameLabel, incoming, gameIncoming, bench, gameBench,
						ownedKitBacked, gameKitBacked), ctx.dataset, own, game));
				}
			}
		}
		// The group's swaps: one strip per style, on every member's card,
		// built from what the cards wear after the kit merge.
		for (var entry : ownedSides.entrySet()) {
			Map<GearSlot, Ladder> group = groupLadderFor(ctx, entry.getValue(), mobs, perMob, entry.getKey());
			for (int j = 0; j < n; j++) {
				StyleResult sr = perMob.get(j).get(entry.getKey());
				if (sr != null) { sr.ladder = group; }
			}
		}
		return perMob;
	}

	/** One style's roster state: pass A's plan and member requests and the
	 * fanned-out optimize answers (one array slot per task), then the
	 * shared-spec picks the kit merge reads back. */
	private static final class StyleWork {
		StylePlan plan;
		final List<OptimizationRequest> ownedReqs = new ArrayList<>();
		final List<OptimizationRequest> gameReqs = new ArrayList<>();
		final List<DpsResult>[] ownedArr;
		final List<DpsResult>[] gameArr;
		SpecPick[] specs;
		SpecPick[] gameSpecs;

		@SuppressWarnings("unchecked")
		StyleWork(int n) {
			ownedArr = new List[n];
			gameArr = new List[n];
		}
	}

	/** One side's shared answer for a style (Yours or BiS): the member
	 * requests and free bests, the shared set, each mob's result on it (null
	 * where calculate() has nothing - the TD shield phases) and the ranked
	 * shared-spec options - the kit pass's per-style input. */
	@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
	private static final class SharedSide {
		final List<OptimizationRequest> reqs;
		final List<List<DpsResult>> bests;
		/** Each member's say in this style's kit (styleUseWeights). */
		final double[] weights;
		Loadout shared;
		DpsResult[] shown;
		List<SpecPick[]> specOptions = List.of();
	}

	/** The side's shared set re-shown per mob, with its shared spec and the
	 * Lightbearer arbitration on top. owned == null is the BiS side. */
	private SharedSide sharedSideFor(DpsCalculator calc, ComputeContext ctx, List<MonsterStats> mobs,
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

	/** Each mob's result on the shared set (all null without one). */
	private DpsResult[] shownAcross(DpsCalculator calc, List<OptimizationRequest> reqs, Loadout shared) {
		DpsResult[] shown = new DpsResult[reqs.size()];
		for (int j = 0; shared != null && j < shown.length; j++) {
			shown[j] = calcRespecting(calc, reqs.get(j), shared);
		}
		return shown;
	}

	/** ROSTER LIGHTBEARER ARBITRATION (field report 2026-08-08: a Nex
	 * group recommended Ring of shadows over the Lightbearer): the
	 * shared ring falls out of raw-stat set construction, which the
	 * stat-less Lightbearer can never win - but doubled regen scales
	 * the whole trip's spec value. Price the trip both ways (HP-
	 * weighted shared-set dps + top shared-spec value) and adopt the
	 * variant on a strict win - the single-mob arbitration's argmax,
	 * at the shared-set altitude. */
	private void adoptLightbearer(DpsCalculator calc, ComputeContext ctx, List<MonsterStats> mobs,
		List<OptimizationRequest> reqs, OwnedItems owned, SharedSide side) {
		Loadout variant = lightbearerVariant(ctx.dataset, side.shared, reqs, owned);
		if (variant == null) { return; }
		DpsResult[] shown = shownAcross(calc, reqs, variant);
		double baseTotal = topSpecScore(side.specOptions, side.weights);
		double lbTotal = 0;
		for (int j = 0; j < shown.length; j++) {
			double weight = side.weights[j];
			if (shown[j] != null) { lbTotal += shown[j].getDps() * weight; }
			if (side.shown[j] != null) { baseTotal += side.shown[j].getDps() * weight; }
		}
		List<SpecPick[]> options = chooseSharedSpec(ctx, mobs, reqs, shown, owned, side.weights);
		lbTotal += topSpecScore(options, side.weights);
		if (lbTotal > baseTotal + 1e-9) {
			side.shared = variant;
			side.shown = shown;
			side.specOptions = options;
		}
	}

	/** Every non-null item into the plan, once each. */
	private static void pack(Map<Integer, GearItem> plan, Collection<GearItem> items) {
		for (GearItem item : items) {
			if (item != null) { plan.putIfAbsent(item.getId(), item); }
		}
	}

	/** The style whose free best answers mob j with the most dps (the
	 * earlier on ties), or null when no style has an answer. */
	private static CombatStyle answerFor(Map<CombatStyle, SharedSide> sides, int j) {
		CombatStyle answer = null;
		DpsResult top = null;
		for (var entry : sides.entrySet()) {
			List<DpsResult> best = entry.getValue().bests.get(j);
			if (!emptyBest(best) && (top == null || best.get(0).getDps() > top.getDps())) {
				top = best.get(0);
				answer = entry.getKey();
			}
		}
		return answer;
	}

	/** One primary base's kit contest: the kit, the kept spec option with
	 * its carried [weapon, ammo, offhand], and the score the primaries are
	 * ranked on. */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	private static final class Primary {
		final CombatStyle style;
		final KitAnswer kit;
		final SpecPick[] specs;
		final GearItem[] carried;
		final double score;
	}

	/** The carried spec weapon's pick for mob j on this side's OWN sets, from
	 * the side's ranked options: the kit's kept option was priced on the
	 * primary style's sets, so a card another style answers reads its own
	 * (ISS-01M5PKSQBAP823XNC13QBW8830: with the magic primary's dagger option
	 * priced for the Core alone, Ba-Ba's melee card lost the Wardens'
	 * dagger). Null when no option carries it or it adds nothing here. */
	private static SpecPick pickFor(SharedSide side, int j, GearItem carried) {
		if (side == null || carried == null) { return null; }
		for (SpecPick[] option : side.specOptions) {
			for (SpecPick pick : option) {
				if (pick != null && pick.weapon.getId() == carried.getId()) { return option[j]; }
			}
		}
		return null;
	}

	/** One side's kit view, ready to merge over the phase-1 tabs. */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	private static final class KitView {
		final Set<CombatStyle> styles;
		final GearItem specCarried;
		/** The kept spec picks, or null when the swaps outbid the spec. */
		final SpecPick[] specs;
		final List<Map<CombatStyle, DpsResult>> shownByMob;
		final LinkedHashMap<Integer, GearItem> plan;
	}

	/**
	 * The cross-style kit pass for ONE side (Yours or BiS): unify neutral
	 * ammo, try each style as the primary base, let the SPEC COMPETE for
	 * its slot against the swaps (field fix 2026-07-17: a spec weapon must
	 * not lock the budget when a cross-style weapon would answer an immune
	 * phase), pick the winner by HP-weighted total plus the spec's damage
	 * value, and compute every (mob, style) answer plus the trip plan.
	 * Returns null only when superseded mid-compute.
	 */
	private KitView kitPass(DpsCalculator calc, List<MonsterStats> mobs,
		ComputeContext ctx, long ticket, Map<CombatStyle, SharedSide> sides) {
		int n = mobs.size();
		// SLOT UNIFICATION (field insight 2026-07-18: a tribrid answer
		// should not pay one carried slot per near-identical variant):
		// per style and slot, the roster's most-worn item absorbs any
		// per-mob variant whose dps cost is under 0.5%. The per-mob bests
		// converge onto shared pieces, the diffs shrink, and the
		// significant breakpoints arrive with far fewer items - the
		// mage/tribrid answers assemble early instead of fragmenting.
		for (SharedSide unifySide : sides.values()) {
			List<OptimizationRequest> unifyReqs = unifySide.reqs;
			for (GearSlot slot : GearSlot.values()) {
				Map<Integer, Integer> counts = new HashMap<>();
				Map<Integer, GearItem> itemsById = new HashMap<>();
				for (List<DpsResult> best : unifySide.bests) {
					if (emptyBest(best)) { continue; }
					GearItem item = best.get(0).getLoadout().get(slot);
					if (item != null) {
						counts.merge(item.getId(), 1, Integer::sum);
						itemsById.put(item.getId(), item);
					}
				}
				if (counts.size() < 2) { continue; }
				int canonicalId = Collections.max(counts.entrySet(), Map.Entry.comparingByValue()).getKey();
				GearItem canonical = itemsById.get(canonicalId);
				for (int j = 0; j < n; j++) {
					List<DpsResult> best = unifySide.bests.get(j);
					if (emptyBest(best)) { continue; }
					GearItem worn = best.get(0).getLoadout().get(slot);
					if (worn == null || worn.getId() == canonicalId) { continue; }
					// Slot legality (field report 2026-08-15 x2, the Eclipse
					// Moon's scythe+defender: THIS was the birthplace) - a
					// 2h canonical evicts the shield, and a shield canonical
					// cannot join a 2h wearer.
					Loadout unifiedLoadout = best.get(0).getLoadout();
					GearItem unifiedWeapon = unifiedLoadout.getWeapon();
					if (slot == SHIELD && unifiedWeapon != null
						&& unifiedWeapon.isTwoHanded()) {
						continue;
					}
					unifiedLoadout = withSlot(unifiedLoadout, slot, canonical);
					if (slot == WEAPON && canonical.isTwoHanded()) {
						unifiedLoadout = withSlot(unifiedLoadout, SHIELD, null);
					}
					DpsResult unified = calcRespecting(calc, unifyReqs.get(j),
						unifiedLoadout);
					if (unified != null && unified.getDps() >= best.get(0).getDps() * 0.995) { best.set(0, unified); }
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
		GearItem[] neededBy = new GearItem[n];
		for (int j = 0; j < n; j++) {
			CombatStyle answer = answerFor(sides, j);
			DpsResult top = answer == null ? null : sides.get(answer).bests.get(j).get(0);
			GearItem ammo = top == null ? null : top.getLoadout().get(AMMO);
			if (ammo == null) { continue; }
			DpsResult without = calcRespecting(calc, sides.get(answer).reqs.get(j),
				withSlot(top.getLoadout(), AMMO, null));
			if (without == null || without.getDps() < top.getDps() - 1e-9) {
				neededBy[j] = ammo;
				rosterAmmo = rosterAmmo == null ? ammo : rosterAmmo;
			}
		}
		for (int j = 0; j < n; j++) {
			GearItem needed = neededBy[j] != null ? neededBy[j] : rosterAmmo;
			if (needed == null) { continue; }
			for (CombatStyle s : sides.keySet()) {
				List<DpsResult> best = sides.get(s).bests.get(j);
				if (emptyBest(best)) { continue; }
				DpsResult r = best.get(0);
				GearItem ammo = r.getLoadout().get(AMMO);
				if (ammo != null && ammo.getId() == needed.getId()) { continue; }
				DpsResult unified = calcRespecting(calc, sides.get(s).reqs.get(j),
					withSlot(r.getLoadout(), AMMO, needed));
				if (unified != null && unified.getDps() >= r.getDps() - 1e-9) { best.set(0, unified); }
			}
		}
		if (rosterAmmo != null) {
			for (SharedSide ammoSide : sides.values()) {
				Loadout b = ammoSide.shared;
				GearItem ammo = b.get(AMMO);
				if (ammo != null && ammo.getId() == rosterAmmo.getId()) { continue; }
				Loadout unifiedBase = withSlot(b, AMMO, rosterAmmo);
				boolean neutral = true;
				List<OptimizationRequest> reqs = ammoSide.reqs;
				for (int j = 0; j < n && neutral; j++) {
					DpsResult before = calcRespecting(calc, reqs.get(j), b);
					DpsResult after = calcRespecting(calc, reqs.get(j), unifiedBase);
					neutral = (after == null ? 0 : after.getDps())
						>= (before == null ? 0 : before.getDps()) - 1e-9;
				}
				if (neutral) { ammoSide.shared = unifiedBase; }
			}
		}
		// The primaries are independent once the ammo pass has settled the
		// shared state - fan them out (each task owns its calculator; the
		// kit search is the second half of the raid load-time wall).
		List<CombatStyle> primaries = new ArrayList<>(sides.keySet());
		List<Callable<Primary>> primaryTasks = new ArrayList<>();
		List<SpecPick[]> tripOptions = tripSpecOptions(sides, mobs);
		for (CombatStyle primary : primaries) {
			primaryTasks.add(() -> {
				if (requestSeq.get() != ticket) { return null; }
				var localCalc = new DpsCalculator();
				Loadout base = sides.get(primary).shared;
				List<GearItem> singlePool =
					crossStylePool(base, primary, sides);
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
				KitAnswer kitFree = chooseKit(localCalc, base, primary, singlePool,
					bundleCandidates(primary, base, null, sides),
					sides, mobs, ctx.maxSwaps, memo);
				KitAnswer kit = kitFree;
				SpecPick[] keptSpecs = null;
				GearItem[] kept = new GearItem[3];
				double score = kitFree.total;
				// A pinned spec is seated unconditionally - the player's
				// explicit choice overrides the seat economics, so it always
				// shows (even adding ~0.00, field report 2026-08-09).
				boolean specPinned = restrictSpec(ctx, primary, mobs.get(0)) != null;
				for (SpecPick[] option : specOptions) {
					GearItem[] slots = carriedSpecOf(option, base);
					int specSlots = slotsOf(slots);
					KitAnswer kitSpec = specSlots == 0 ? kitFree
						: chooseKit(localCalc, base, primary, singlePool,
							bundleCandidates(primary, base, slots[0], sides),
							sides, mobs,
							max(0, ctx.maxSwaps - specSlots), memo);
					double specValue = 0;
					List<GearItem> carriedSpec = carriedOf(kitSpec);
					for (int j = 0; j < n; j++) {
						if (option[j] == null) { continue; }
						DpsResult shown = kitBest(localCalc, base, primary, kitSpec.singles,
							kitSpec.bundles, carriedSpec, sides, j);
						if (shown != null && shown.getDps() > 0) {
							specValue += option[j].expectedDamage * shown.getDps();
						}
					}
					if (specPinned || kitSpec.total + specValue >= kitFree.total) {
						kit = kitSpec;
						keptSpecs = option;
						kept = slots;
						// The PRIMARY contest scores what the card SHOWS -
						// kit total plus the kept spec's honest dpsAdded
						// value - not the damage-blind seat currency (field
						// report 2026-08-09: the Muspah roster's magic
						// primary outbid a spec-kept ranged primary by 0.3%
						// of kit total while ignoring an elder maul worth
						// 20x that in drain value, and the BiS card lost
						// its spec entirely).
						score = kitSpec.total + tripValue(option, hpWeights(mobs));
						break;
					}
				}
				return new Primary(primary, kit, keptSpecs, kept, score);
			});
		}
		Primary winner = null;
		try {
			for (Future<Primary> future : rosterPool.invokeAll(primaryTasks)) {
				Primary outcome;
				try {
					outcome = future.get();
				} catch (ExecutionException e) {
					continue; // one primary failed - the others still answer
				}
				if (outcome != null && (winner == null || outcome.score > winner.score + 1e-9)) {
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
		if (requestSeq.get() != ticket) {
			abandonedForTest++;
			return null;
		}
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
			DpsResult mobBest = null;
			for (CombatStyle s : sides.keySet()) {
				DpsResult result = shownFor(calc, base, winner.style, winner.kit.singles, winner.kit.bundles,
					carried, sides, j, s);
				if (result != null) {
					shown.put(s, result);
					mobBest = better(mobBest, result);
				}
			}
			shownByMob.add(shown);
			if (mobBest != null) { pack(plan, mobBest.getLoadout().items()); }
		}
		pack(plan, Arrays.asList(winner.carried[1], winner.carried[2]));
		return new KitView(EnumSet.copyOf(sides.keySet()),
			winner.carried[0], winner.specs, shownByMob, plan);
	}

	private static List<GearItem> carriedOf(KitAnswer kit) { return carriedOf(kit.singles, kit.bundles); }

	/** Everything a kit carries, once each: the singles, then the bundles' pieces. */
	private static List<GearItem> carriedOf(List<GearItem> singles, List<SwapBundle> bundles) {
		List<GearItem> carried = new ArrayList<>(singles);
		for (SwapBundle bundle : bundles) {
			for (GearItem item : bundle.items) {
				if (carried.stream().noneMatch(i -> i.getId() == item.getId())) { carried.add(item); }
			}
		}
		return carried;
	}

	/**
	 * Optimise ONE shared set per style across a roster of mobs. Single-mob
	 * rosters take the exact bestPerStyle path (wrapped as a 1-mob answer);
	 * multi-mob rosters choose the shared set by average dps and deliver a
	 * per-mob display bundle. Parameters mirror bestPerStyle (result-level
	 * params shared across the roster; exclusions, sims, pins, the pinned
	 * spell and the pinned spec are per member, keyed by profileId - the
	 * spec seats the FIRST member's).
	 */
	public void bestPerStyleAcross(
		List<MonsterStats> mobs,
		PlayerLevels realLevels, PlayerLevels boostedLevels, PrayerUnlocks prayerUnlocks,
		RequirementProfile requirements, OwnedItems owned, int collectionFingerprint,
		ComputeParams p, Map<CombatStyle, Set<Integer>> excludedByStyle, Set<Integer> dreamItems,
		Map<Integer, Map<CombatStyle, Set<Integer>>> excludedByMob,
		Map<Integer, Set<Integer>> dreamsByMob,
		Map<Integer, Map<CombatStyle, Map<GearSlot, Integer>>> pinsByMob,
		Map<Integer, SpellStats> spellByMob, Map<Integer, Integer> specByMob,
		Set<Integer> protectOnlyItems,
		Consumer<RosterResult> callback) {
		if (mobs == null || mobs.isEmpty()) { return; }
		final ComputeContext ctx = buildContext(realLevels, boostedLevels, prayerUnlocks,
			requirements, owned, collectionFingerprint, p, excludedByStyle, dreamItems,
			pinsByMob, spellByMob, specByMob, protectOnlyItems);
		ctx.excludedByMob = excludedByMob == null ? Map.of() : excludedByMob;
		ctx.dreamsByMob = dreamsByMob == null ? Map.of() : dreamsByMob;
		if (mobs.size() == 1) {
			// The single-mob fast path, the member's own exclusions and sims
			// layered per mob by the context - one request, same semantics.
			bestPerStyle(mobs.get(0), ctx,
				map -> callback.accept(new RosterResult(mobs, Collections.singletonList(map), "", "")));
			return;
		}
		final List<MonsterStats> roster = new ArrayList<>(mobs);
		submit(ticket -> {
			List<Map<CombatStyle, StyleResult>> perMob = computeStyleAcross(roster, ctx, ticket);
			GroupBook book = groupBook(roster, perMob, ctx);
			if (book.recompute) {
				// One book for the whole raid: every magic set recomputed
				// under it, Death Charge off when that book is not Arceuus.
				ComputeParams locked = p.copy();
				locked.spellbookLock = book.book;
				if (!"arceuus".equals(book.book)) { locked.deathCharge = 0; }
				ComputeContext again = buildContext(realLevels, boostedLevels, prayerUnlocks, requirements, owned,
					collectionFingerprint, locked, excludedByStyle, dreamItems, pinsByMob, spellByMob, specByMob,
					protectOnlyItems);
				again.excludedByMob = ctx.excludedByMob;
				again.dreamsByMob = ctx.dreamsByMob;
				perMob = computeStyleAcross(roster, again, ticket);
			}
			return new RosterResult(roster, perMob, book.book, book.reason);
		}, () -> new RosterResult(roster,
				new ArrayList<>(Collections.nCopies(roster.size(), Map.<CombatStyle, StyleResult>of())), "", ""),
			"roster compute failed for {}", roster.get(0).getName(), callback);
	}

	/** The per-style cache key: the query key minus per-style state, plus
	 * this style and the pins/exclusions/pinned-spell that shape ITS answer. */
	private static String styleKey(String baseKey, CombatStyle style,
		ComputeContext ctx, MonsterStats mob) {
		return baseKey + "|" + style.name()
			+ "|" + excludedFor(ctx, style, mob).hashCode()
			+ "|" + pinKey(ctx, style, mob);
	}

	private static String joinAssumes(String prayer, String boost) {
		if (prayer == null || prayer.isEmpty()) { return boost; }
		return boost == null ? prayer : prayer + " + " + boost;
	}

	/** Package-private: SpecPoisonTest pins the spec tie-break. */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	static final class SpecPick {
		final SpecialAttack spec;
		final GearItem weapon;
		final double expectedDamage;
		/** The DPS this spec ADDS to the kill over just attacking - the
		 * win-over-replacement value the card shows and the ranking key. */
		final double dpsAdded;
		/** Non-null when the spec needs its own ammo carried (a dark bow
		 * next to a chargebow base needs arrows - an extra slot). */
		final GearItem ammo;
		/** The offhand brought for the switch - a one-handed spec weapon
		 * off a two-handed main (N, Discord 2026-09-26). An extra slot. */
		final GearItem offhand;
		/** This pick WITH its best offhand and the numbers that follow;
		 * null when none applies. The bare pick is what ranks. */
		final SpecPick upgraded;
	}

	/** The pick the inventory can seat: the offhand rides only when the
	 * weapon, its ammo and the offhand all have a slot. */
	private static SpecPick seat(SpecPick pick, int slots) {
		return pick != null && pick.upgraded != null
			&& slots >= (pick.ammo == null ? 2 : 3) ? pick.upgraded : pick;
	}

	/**
	 * The strongest special-attack weapon for this style, evaluated by
	 * swapping it into the base set (shield dropped for two-handers, ammo
	 * re-picked for compatibility) and applying the spec's verified roll
	 * modifiers. With an ownership ledger, only owned weapons/ammo count
	 * (requirement #7/#8); with null, everything standard counts - the
	 * game-best spec.
	 */
	/** One shared spec weapon for the whole roster: candidates are each
	 * mob's free pick, RANKED by HP-weighted value added (a mob a candidate
	 * cannot roll against contributes zero). The kit seat check walks this
	 * ranking and keeps the strongest option that pays for its carried slot
	 * - the argmax winner alone can be vetoed at the seat (a drain-heavy
	 * pick whose value the damage-priced seat cannot see), and dropping the
	 * spec entirely loses to carrying the runner-up (regression 2026-08-08:
	 * the DWH outbid the dagger trip-wide, failed its seat, and the roster
	 * carried no spec at all). Each entry is one candidate's per-mob
	 * SpecPicks (null where it cannot spec); empty list = no candidate has
	 * positive trip value. */
	private List<SpecPick[]> chooseSharedSpec(ComputeContext ctx, List<MonsterStats> mobs,
		List<OptimizationRequest> reqs, DpsResult[] shown, OwnedItems owned, double[] weights) {
		int n = mobs.size();
		var candidates = new LinkedHashSet<Integer>();
		// A pinned spec weapon (serving this style) is the ONLY candidate:
		// the player's explicit choice, priced across the trip like any
		// other but never contested by the free picks.
		Set<Integer> specPin = restrictSpec(ctx, reqs.get(0).getStyle(), mobs.get(0));
		if (specPin != null) {
			candidates.addAll(specPin);
		} else {
			for (int j = 0; j < n; j++) {
				SpecPick free = bestSpec(ctx.dataset, reqs.get(j), shown[j], owned, null, false);
				if (free != null && free.weapon != null) { candidates.add(free.weapon.getId()); }
			}
		}
		if (candidates.isEmpty()) { return List.of(); }
		List<double[]> scores = new ArrayList<>();
		List<SpecPick[]> perCandidate = new ArrayList<>();
		for (Integer id : candidates) {
			SpecPick[] bare = new SpecPick[n];
			for (int j = 0; j < n; j++) {
				bare[j] = bestSpec(ctx.dataset, reqs.get(j), shown[j], owned,
					Collections.singleton(id), specPin != null);
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
				if ((pass == 0 || differs) && (score > 1e-9 || specPin != null)) {
					scores.add(new double[]{score, perCandidate.size()});
					perCandidate.add(perMob);
				}
			}
		}
		scores.sort((a, b) -> Double.compare(b[0], a[0]));
		List<SpecPick[]> ranked = new ArrayList<>(scores.size());
		for (double[] row : scores) { ranked.add(perCandidate.get((int) row[1])); }
		return ranked;
	}

	/** The shared set with a Lightbearer in the ring slot, or null when the
	 * roster arbitration cannot apply: no shared set, already a Lightbearer,
	 * a ring pinned or a risk budget on any mob (the shared risk package is
	 * not re-priced here - conservative skip), or the ring unavailable
	 * (unowned and unsimmed, excluded, requirements). owned == null is the
	 * game-best side: everything standard is available. */
	private static Loadout lightbearerVariant(LoadoutData dataset, Loadout shared,
		List<OptimizationRequest> reqs, OwnedItems owned) {
		if (shared == null) { return null; }
		for (OptimizationRequest req : reqs) {
			if (req.pinnedFor(RING) != null || req.isRiskConstrained()) { return null; }
		}
		return lightbearerSwap(dataset, shared, reqs, owned);
	}

	/** The shared Lightbearer machinery: find the ring, prove it available
	 * under every request (excluded/requirements/owned-or-dreamed), and wear
	 * it into the set - null when the swap cannot apply. Callers layer their
	 * own pin/risk policy on top (the roster skips risk conservatively; the
	 * single-mob arbitration prices it). */
	private static Loadout lightbearerSwap(LoadoutData dataset, Loadout shared,
		List<OptimizationRequest> reqs, OwnedItems owned) {
		GearItem worn = shared.get(RING);
		if (worn != null && worn.getNameLower().contains("lightbearer")) { return null; }
		GearItem lightbearer = null;
		for (GearItem item : dataset.getGearItems(RING)) {
			if (item.getNameLower().equals("lightbearer") && item.isStandardGear()) {
				lightbearer = item;
				break;
			}
		}
		if (lightbearer == null) { return null; }
		boolean dreamed = false;
		for (OptimizationRequest req : reqs) {
			if (req.isExcluded(lightbearer.getId())
				|| !req.getRequirementProfile().canEquip(lightbearer.getRequirements())) {
				return null;
			}
			dreamed |= req.isDream(lightbearer.getId());
		}
		if (owned != null && !owned.owns(lightbearer.getId()) && !dreamed) { return null; }
		return shared.with(RING, lightbearer);
	}

	/** Weighted trip value of the top-ranked shared-spec option. */
	private static double topSpecScore(List<SpecPick[]> options, double[] weights) {
		return options.isEmpty() ? 0 : tripValue(options.get(0), weights);
	}

	/** Weighted trip value of one shared-spec option (null = no spec there):
	 * a style side weighs by styleUseWeights, the cross-style kit by hpWeights. */
	private static double tripValue(SpecPick[] picks, double[] weights) {
		double score = 0;
		for (int j = 0; j < weights.length; j++) {
			if (picks[j] != null) { score += picks[j].dpsAdded * weights[j]; }
		}
		return score;
	}

	/** owned == null is the game-best side: everything standard counts. */
	private static boolean available(OwnedItems owned, OptimizationRequest request, int id) {
		return owned == null || owned.owns(id) || request.isDream(id);
	}

	/** The spec's expected damage per use at the Multi card's stack. */
	private static double specDamage(SpecialAttack spec, DpsResult base, OptimizationRequest request) {
		return spec.expectedDamage(base, request.getMonster(), request.getLevels())
			* spec.multiFactor(request.getMultiStack());
	}

	/** The carried slots a shared-spec option costs: [weapon, ammo,
	 * offhand], all null when the base already wears the spec weapon (it rides free). A
	 * dark bow next to a chargebow base needs its arrows carried too - the
	 * spec spends TWO slots when the base quiver cannot feed it (field
	 * spec 2026-07-18). */
	private static GearItem[] carriedSpecOf(SpecPick[] specs, Loadout shared) {
		for (SpecPick pick : specs) {
			if (pick != null && pick.weapon != null && shared != null
				&& (shared.getWeapon() == null
					|| shared.getWeapon().getId() != pick.weapon.getId())) {
				return new GearItem[]{pick.weapon, pick.ammo, pick.offhand};
			}
		}
		return new GearItem[3];
	}

	/** The bench slots those carried pieces spend. */
	private static int slotsOf(GearItem... slots) {
		int spent = 0;
		for (GearItem slot : slots) {
			if (slot != null) { spent++; }
		}
		return spent;
	}

	/** restrictTo non-null: only these weapon ids are scanned - the roster
	 * path uses it to evaluate ONE shared spec weapon per mob. */
	/** The Lightbearer never survives the ring slot's raw-stat cut (its
	 * stat line is empty), but its doubled spec regen can beat the best
	 * dps ring ON THE TOTAL - set dps + spec dps-added - at fights with
	 * real spec value (roadmap's "compare the two totals"; field report
	 * 2026-08-08: "it can be really impactful in fights with big spec
	 * utility"). Deterministic post-arbitration: both candidates fully
	 * priced, the argmax wins, the beam is never touched. On a win the
	 * results list is updated in place so the card shows the ring that
	 * earned its slot. Rosters run the same argmax at the shared-set
	 * altitude (lightbearerVariant, field report 2026-08-08 #2). */
	SpecPick arbitrateLightbearer(
		LoadoutData dataset,
		OptimizationRequest request,
		List<DpsResult> results,
		OwnedItems owned,
		Set<Integer> restrictTo) {
		SpecPick spec = bestSpec(dataset, request, results.isEmpty() ? null : results.get(0),
			owned, restrictTo, restrictTo != null);
		if (spec == null) {
			return null; // no spec value in play - dps ring stands
		}
		DpsResult base = results.get(0);
		if (request.pinnedFor(RING) != null) { return spec; }
		Loadout variantLoadout = lightbearerSwap(dataset, base.getLoadout(),
			Collections.singletonList(request), owned);
		if (variantLoadout == null) { return spec; }
		RiskGuard risk = RiskGuard.of(dataset, request);
		if (risk != null && risk.vetoes(variantLoadout, null)) { return spec; }
		DpsResult variant = new DpsCalculator().calculate(request, variantLoadout);
		if (variant == null) { return spec; }
		SpecPick variantSpec = bestSpec(dataset, request, variant, owned, restrictTo, restrictTo != null);
		if (variantSpec == null) { return spec; }
		double baseTotal = base.getDps() + spec.dpsAdded;
		double variantTotal = variant.getDps() + variantSpec.dpsAdded;
		if (variantTotal > baseTotal + 1e-9) {
			results.set(0, variant);
			return variantSpec;
		}
		return spec;
	}

	/** main: the worn set's result the spec swaps into (null = nothing to
	 * spec from). keepZero: keep a restricted (user-PINNED) weapon even
	 * when it adds ~0 dps, so the pin always displays with an honest
	 * number instead of silently vanishing (field report 2026-08-09: a
	 * pinned Tonalztics showed nothing on a set it did not help). */
	SpecPick bestSpec(
		LoadoutData dataset,
		OptimizationRequest request,
		DpsResult main,
		OwnedItems owned,
		Set<Integer> restrictTo,
		boolean keepZero) {
		if (main == null) { return null; }
		MonsterStats monster = request.getMonster();
		var calculator = new DpsCalculator();
		SpecPick best = null;
		// Request-level risk constants, hoisted out of the weapon scan (they
		// were rebuilt - floor Loadout + a HashSet - per candidate weapon).
		RiskGuard risk = RiskGuard.of(dataset, request);
		// A Lightbearer in the set doubles special-energy regen (10%/15s vs
		// 10%/30s), so more specs fit the kill - detected by ring name, no id.
		GearItem ring = main.getLoadout().get(RING);
		boolean lightbearer = ring != null && ring.getNameLower().contains("lightbearer");
		// Spec weapons are weapons by definition (SpecialAttack.match rejects
		// every other slot), so only the weapon partition needs scanning.
		// A group's spec stipulation (the Wardens' dragon dagger: nothing
		// breaks into the next phase faster - Andrew 2026-09-10) narrows the
		// pool to that family whenever one is at hand; otherwise the free pick.
		String family = specStipulation(monster);
		boolean stipulated = family != null && dataset.getGearItems(WEAPON).stream()
			.anyMatch(i -> i.getNameLower().contains(family) && available(owned, request, i.getId()));
		for (GearItem item : dataset.getGearItems(WEAPON)) {
			if (restrictTo != null && !restrictTo.contains(item.getId())
				|| stipulated && !item.getNameLower().contains(family)) {
				continue;
			}
			if (!LoadoutOptimizer.eligible(dataset, request, item)
				// Sims are pretend-owned EVERYWHERE, including here: the
				// worn-set machinery honoured dreams but the spec pool
				// checked raw ownership only, so a simmed spec weapon
				// could never compete (field report 2026-08-06: simmed
				// Burning claws vs Barrows still recommended the owned
				// abyssal dagger).
				|| !available(owned, request, item.getId())) {
				continue;
			}
			// ANY style's spec competes (field request: the spec swap is its
			// own weapon switch - magic set + chally is a real play).
			SpecialAttack spec = SpecialAttack.match(item);
			if (spec == null) { continue; }

			// In a wilderness low-risk set the carried spec weapon competes
			// for kept slots like everything else - the whole package (worn
			// set + this weapon) must stay within the total risk budget.
			if (risk != null && risk.vetoes(main.getLoadout(), item)) { continue; }
			GearItem[] ammoOut = new GearItem[1];
			Loadout loadout = specLoadout(dataset, main.getLoadout(), item, owned, request, ammoOut);
			if (loadout == null) { continue; }
			// A spec fires under ITS OWN style, so a style the monster is
			// immune to bars the spec outright. At sea that is the melee
			// ban (field-verified, Andrew 2026-08-31: "ranged spec weapons
			// work but not mele"); on land it bars e.g. a chally spec at a
			// melee-immune mob the cross-style rule used to let through.
			if (isImmune(
				monster, spec.getStyle(), loadout, null)) {
				continue;
			}
			// A cross-style spec rolls under ITS OWN style's math - the
			// assume-best-prayer model carries all three books' factors,
			// matching the real play of flicking (e.g.) Piety for the spec.
			OptimizationRequest baseRequest = spec.getStyle() == request.getStyle()
				? request : request.withStyle(spec.getStyle());
			DpsResult base = calculator.calculate(baseRequest, loadout);
			if (base == null || base.getMaxHit() <= 0) { continue; }
			double expected = specDamage(spec, base, request);
			// Rank by the DPS the spec ADDS over just attacking - marginal
			// (every spec fired gives up a main-hand hit), regen-aware over the
			// fight, and drain-inclusive for every style (field direction
			// 2026-07-19). A spec that adds nothing loses to carrying no slot.
			double added = specDpsAdded(calculator, spec, base, expected,
				request, main, lightbearer);
			if (added <= 1e-4 && !keepZero) { continue; }
			double bestAdded = best == null ? Double.NEGATIVE_INFINITY : best.dpsAdded;
			// Ties (identical stats across poison tiers) prefer the higher
			// tier - the venom is free spec damage the model does not price.
			if (best == null || added > bestAdded + 1e-9
				|| (added > bestAdded - 1e-9 && item.poisonTier() > best.weapon.poisonTier())) {
				SpecPick up = null;
				if (loadout.get(SHIELD) == null && !item.isTwoHanded()
					&& !request.isRiskConstrained()) {
					for (GearItem shield : dataset.getGearItems(SHIELD)) {
						StatBlock b = shield.getBonuses();
						if (max(max(b.getStrength(), b.getRangedStrength()), b.getMagicDamage()) <= 0
							|| !LoadoutOptimizer.eligible(dataset, request, shield)
							|| !available(owned, request, shield.getId())) {
							continue;
						}
						DpsResult with = calculator.calculate(baseRequest,
							withSlot(loadout, SHIELD, shield));
						double damage = with == null ? 0 : specDamage(spec, with, request);
						if (damage > (up == null ? expected : up.expectedDamage) + 1e-9) {
							up = new SpecPick(spec, item, damage, specDpsAdded(calculator, spec, with,
								damage, request, main, lightbearer),
								ammoOut[0], shield, null);
						}
					}
				}
				best = new SpecPick(spec, item, expected, added, ammoOut[0], null, up);
			}
		}
		return best;
	}

	/**
	 * Defence-drain specs (DWH/BGS/elder maul) are worth more than their
	 * hit: a landed drain raises the main set's DPS for the REST of the
	 * kill. Valued as land-chance x dps-gain-at-drained-defence x expected
	 * remaining fight (monster hp / current dps) - which is exactly why
	 * drains shine on high-HP, high-defence targets and are pointless on
	 * throwaway mobs.
	 */
	/**
	 * The DPS this spec ADDS to the kill over just attacking with the main
	 * set - its win-over-replacement value, the ranking key and the number
	 * the card shows (field direction 2026-07-19). Three ingredients:
	 *
	 * <ul>
	 * <li><b>Marginal.</b> Every spec fired gives up a main-hand attack, so
	 *     the per-use win is {@code specDamage - replacedAuto}, never the raw
	 *     spec damage.</li>
	 * <li><b>Regen-aware over the fight.</b> The opening 100% bar
	 *     ({@code 100/cost} uses) amortises across the kill length, on top of
	 *     the sustained regen-fed rate (10%/30s, doubled by a Lightbearer) -
	 *     a longer kill fits more specs.</li>
	 * <li><b>Drain, for every style.</b> A landed defence-drain lowers the
	 *     Defence LEVEL, which ranged and magic roll against too, not only
	 *     melee (v0.3.1 valued the drain for melee main-hands only). The
	 *     player fishes for the land with the specs the energy budget
	 *     allows - P(landed) over the attempts, charged the EXPECTED specs
	 *     spent (fishing stops on the land) - so a Lightbearer's doubled
	 *     regen buys real drain probability where the budget limits
	 *     fishing (field report 2026-08-08).</li>
	 * </ul>
	 *
	 * A weapon is used the better of the two ways (damage or drain), so the
	 * value is their max, not their sum - a small undercount for a spec that
	 * genuinely does both at once (BGS), accepted for v1.
	 */
	/** The spec energy available per kill at SUSTAINED pace (field
	 * direction 2026-08-08: "sustained is more realistic for 99% of
	 * outings" - a 50-200 mob grind is regen-bound, so the opening bar
	 * amortises to nothing and specs per kill go fractional): regen
	 * (10%/30s, doubled by a Lightbearer) over the kill, plus - with
	 * Death Charge assumed - one 15% refund per cast window, scaled down
	 * when kills come faster than the window (the refund needs a killing
	 * blow in it). Yama's rite of vile transference upgrades the spell to
	 * refund from TWO kills per cast, an effective 30s window (level 2).
	 * The old burst frame (every kill opens on a full 100% bar) is
	 * roadmapped as a POH-pool mode. Package-private: the deterministic
	 * unit under DeathChargeTest. */
	static double specEnergyOverKill(double ttkSeconds, boolean lightbearer,
		int deathChargeLevel, boolean freshBar) {
		double regenPerSec = lightbearer ? 10.0 / 15.0 : 10.0 / 30.0;
		double window = deathChargeLevel >= 2 ? 30.0 : 60.0;
		// A one-off fight opens on a full bar (fight-length design, 2026-10-18).
		return (freshBar ? 100.0 : 0.0) + regenPerSec * ttkSeconds
			+ (deathChargeLevel > 0
				? 15.0 * ttkSeconds / max(window, ttkSeconds) : 0.0);
	}

	double specDpsAdded(
		DpsCalculator calculator,
		SpecialAttack spec,
		DpsResult specBase,
		double expected,
		OptimizationRequest request,
		DpsResult mainResult,
		boolean lightbearer) {
		MonsterStats monster = request.getMonster();
		double mainDps = mainResult.getDps();
		if (mainDps <= 0.01) { return 0; }
		double ttkSeconds = min(600, max(0.6, monster.getHitpoints() / mainDps));
		double replacedAuto = mainResult.getExpectedHit() + mainDps * spec.extraTicks(specBase) * 0.6;
		double specCycle = max(0.6, specBase.getAttackSpeed() * 0.6);

		// How many specs fire per kill at SUSTAINED pace: the regen-bound
		// budget, fractional across kills (0.5 = a spec every other kill),
		// but never more than the fight has time for - so a mob that dies
		// in one hit gets no spec value, and a long fight fits more. The
		// burst frame's floor() was an artifact of pricing one kill from a
		// full bar; sustained throughput is a rate, not a count.
		double energyOverKill = specEnergyOverKill(ttkSeconds, lightbearer,
			request.getDeathCharge(), startsWithFullBar(monster));
		double usesByEnergy = energyOverKill / max(1, spec.getEnergyCost());
		double usesByTime = floor(ttkSeconds / specCycle);
		double uses = max(0, min(usesByEnergy, usesByTime));

		// DAMAGE use: each spec fired wins its damage over the main-hand hit it
		// replaces, amortised across the kill.
		double marginal = max(0, expected - replacedAuto);
		double damageDps = uses * marginal / ttkSeconds;

		// DRAIN use: a landed drain lifts the whole set's dps for the rest of
		// the fight - for EVERY style, since the drop is to the Defence LEVEL
		// (v0.3.1 valued this for melee main-hands only). The player FISHES
		// for the land with the specs the energy budget allows - P(landed)
		// = 1 - (1-p)^attempts, each attempt charging its replaced auto
		// (v1 priced exactly one attempt, which made drain value blind to
		// spec regen - the whole reason a Lightbearer rides DWH fights;
		// field report 2026-08-08). Attempts stop once a land is likely:
		// ~1/p tries, never more than the budget.
		double drainDps = 0;
		if (spec.drainsDefence() && usesByTime >= 1) {
			int drained = spec.drainedDefence(monster, expected);
			// Per-boss defence floors (competitor audit 2026-08-08, from
			// the official calc): drain stops at the floor - a DWH at Nex
			// buys 10 levels, at Verzik/Vardorvis nothing at all. Without
			// the clamp the fishing model overvalues drain specs there.
			drained = max(drained, DefenceFloors.floorFor(monster));
			if (drained < monster.getDefence()) {
				DpsResult after = calculator.calculate(
					request.withMonster(monster.withDefence(drained)), mainResult.getLoadout());
				if (after != null && after.getDps() > mainDps) {
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
					drainDps = landed * (after.getDps() - mainDps)
						- spent * replacedAuto / ttkSeconds;
				}
			}
		}
		return max(damageDps, drainDps);
	}

	/** The base set with the spec weapon swapped in, or null if unusable.
	 * owned == null -> any standard ammo may be picked (game-best spec). */
	private Loadout specLoadout(LoadoutData dataset, Loadout baseSet, GearItem weapon, OwnedItems owned, OptimizationRequest request,
		GearItem[] ammoOut) {
		Loadout out = withSwap(baseSet, weapon);
		if (!compatible(out.get(AMMO), weapon)) {
			GearItem replacement = null;
			for (GearItem ammo : dataset.getGearItems(AMMO)) {
				if (ammo.isStandardGear() && !dataset.isVariant(ammo.getId())
					&& !request.isExcluded(ammo.getId())
					&& (owned == null || owned.owns(ammo.getId()))
					&& compatible(ammo, weapon)
					&& (replacement == null
						|| ammo.getBonuses().getRangedStrength() > replacement.getBonuses().getRangedStrength())) {
					replacement = ammo;
				}
			}
			if (replacement == null && !compatible(null, weapon)) {
				return null; // needs ammo that is not available
			}
			if (replacement != null) { ammoOut[0] = replacement; }
			out = withSlot(out, AMMO, replacement);
		}
		return out;
	}

	/** The per-style assumption bundle: your levels/boost/prayer label and
	 * the game-ceiling versions. Shared by the single-mob and roster paths
	 * (they MUST agree - levels and labels depend on style + owned + real,
	 * never the mob). */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	private static final class StylePlan {
		final PlayerLevels levels;
		final String label;
		final PlayerLevels gameLevels;
		final String gameLabel;
	}

	/** Decorate a request with the user's prayer pick: NONE = prayerless,
	 * a named tier = that tier's factors for this style (detect otherwise). */
	private static OptimizationRequest withPrayerPick(OptimizationRequest r,
		ComputeContext ctx, CombatStyle style, PlayerLevels levels, PrayerUnlocks unlocks) {
		String pick = ctx.prayerPicks.get(style);
		if (pick == null) { return r; }
		return r.withPrayers("NONE".equals(pick) ? NONE
			: forPick(style, pick, bestAvailable(levels, unlocks)));
	}

	/** The user's boost pick for this style (assume-chip picker), or null
	 * for detect; "NONE" maps to the unboosted profile. */
	private static BoostProfile boostPickFor(ComputeContext ctx, CombatStyle style) {
		String pick = ctx.boostPicks.get(style);
		if (pick == null) { return null; }
		try {
			return BoostProfile.valueOf(pick);
		} catch (IllegalArgumentException bad) { return null; }
	}

	/** The assumed-prayer label part: the user's pick (null part when
	 * prayerless), or detect's best-available tier. */
	private static String prayerLabelFor(ComputeContext ctx, CombatStyle style,
		PlayerLevels levels, PrayerUnlocks unlocks) {
		String pick = ctx.prayerPicks.get(style);
		if (pick == null) { return bestAvailable(levels, unlocks).nameFor(style); }
		return "NONE".equals(pick) ? null : pick;
	}

	private static StylePlan stylePlan(ComputeContext ctx, CombatStyle style, BoostProfile supplied) {
		BoostProfile userBoost = boostPickFor(ctx, style);
		BoostProfile boost = userBoost != null ? userBoost
			: supplied != null ? supplied
			// A risk-capped trip (kept slots >= 0) dies to a player: the
			// Wilderness or a PvP world - the page only constrains there.
			: BoostSelector.bestFor(style, ctx.effectiveOwned, ctx.p.f2pOnly,
				ctx.p.maxTradeables >= 0);
		PlayerLevels levels = ctx.real.boosted(boost, ctx.boostedLevels).max(ctx.boostedLevels);
		String label = joinAssumes(prayerLabelFor(ctx, style, levels, ctx.unlocks),
			boost == BoostProfile.NONE ? null : boost.toString());
		// The ceiling assumes the best prayers/boost in the GAME, not just
		// what this player has unlocked or owns.
		BoostProfile gameBoost = userBoost != null ? userBoost
			: supplied != null ? supplied
			: BoostSelector.ceilingFor(style, ctx.p.f2pOnly);
		PlayerLevels gameLevels = ctx.real.boosted(gameBoost, ctx.boostedLevels).max(ctx.boostedLevels);
		String gameLabel = joinAssumes(prayerLabelFor(ctx, style, gameLevels,
			ctx.p.f2pOnly ? F2P : ALL),
			gameBoost == BoostProfile.NONE ? null : gameBoost.toString());
		return new StylePlan(levels, label, gameLevels, gameLabel);
	}

	/** A multi tool's pins and spell, in place of the mob's own. */
	private static final class ToolPick {
		Map<GearSlot, Integer> pins = Map.of();
		Map<GearSlot, Integer> gamePins = Map.of();
		SpellStats spell;
	}

	/** The best-OWNED request for one mob at one style - shared verbatim by
	 * the single-mob and roster paths (they MUST agree). Pins shape YOUR
	 * set only; a pinned autocast spell forces the magic search around it;
	 * a multi tool (nullable) brings its own pins and spell. */
	private OptimizationRequest ownedRequestFor(ComputeContext ctx, MonsterStats mob,
		CombatStyle style, PlayerLevels styleLevels, ToolPick tool) {
		OptimizationRequest r = request(ctx, mob, style, styleLevels, ctx.unlocks, ctx.requirements,
			ctx.p.upgradeBudgetGp > 0 ? CandidateMode.OWNED_OR_BUDGET : CandidateMode.OWNED_ONLY,
			3, max(0, ctx.p.upgradeBudgetGp))
			.withDreamItems(withForcedRequired(ctx, mob, style, dreamsFor(ctx, mob)))
			.withPinnedItems(tool != null ? tool.pins : pinsFor(ctx, style, mob))
			.withWaivers(ctx.p.kourendElite ? Set.of(RequiredGear.KOUREND_ELITE) : Set.of());
		r = withPrayerPick(r, ctx, style, styleLevels, ctx.unlocks);
		if (tool != null) { r = r.withMultiStack(ctx.p.multiStack); }
		SpellStats spell = tool != null && tool.spell != null ? tool.spell : spellFor(ctx, mob);
		if (style == MAGIC && spell != null) { r = r.withSpell(spell); }
		return r;
	}

	/** Required slayer gear you own none of (ISS-01M5JJ0Z4M988DR0M0ZGJHD139,
	 * Andrew 2026-10-22: "we should pin/require those by default and show
	 * them as necessary even when the player doesn't own those items"): the
	 * first acceptable item you can equip rides as if owned for this
	 * request, so the card wears it - marked not owned - instead of the
	 * requirement quietly lapsing. Soft rules and a pinned slot never force. */
	private Set<Integer> withForcedRequired(ComputeContext ctx, MonsterStats mob, CombatStyle style,
		Set<Integer> dreams) {
		Map<GearSlot, Integer> pins = pinsFor(ctx, style, mob);
		Set<Integer> out = null;
		for (RequiredGear.Rule rule : RequiredGear.rulesFor(mob,
			ctx.p.kourendElite ? Set.of(RequiredGear.KOUREND_ELITE) : Set.of())) {
			if (rule.soft || pins.containsKey(rule.slot)) { continue; }
			Set<Integer> ids = rule.ids(ctx.dataset);
			if (ids.stream().anyMatch(id -> ctx.effectiveOwned.owns(id) || dreams.contains(id))) { continue; }
			for (int id : ids) {
				GearItem item = ctx.dataset.getGear(id);
				if (item != null && ctx.requirements.canEquip(item.getRequirements())) {
					if (out == null) { out = new HashSet<>(dreams); }
					out.add(id);
					break;
				}
			}
		}
		return out == null ? dreams : out;
	}

	/** The game-ceiling request: every obtainable item you can actually
	 * equip, no pins - so Yours and BiS differ by OWNERSHIP alone, which is
	 * the gap the comparison is meant to isolate.
	 *
	 * <p>This gated on RequirementProfile.MAXED until 2026-08-27. The intent
	 * was a pure gear gap, but it introduced a second difference between the
	 * two sides and, on a low-level account, named gear the player cannot
	 * wear: Obor's BiS came back a rune scimitar at 10 Attack, a DPS that can
	 * never occur, since reaching 40 Attack changes the number. */
	private OptimizationRequest gameRequestFor(ComputeContext ctx, MonsterStats mob,
		CombatStyle style, PlayerLevels gameLevels, ToolPick tool) {
		// The max cape options (ISS-01M5T53FBMHVMJTDPS5ZW4WS50, Andrew
		// 2026-10-25: "any maxxed player or anyone simming as max should always
		// see max cape options instead of the alternative" ... "the yours view
		// could be based on what you have, the BIS should show the max cape"):
		// a simmed max cape waives every combo, owned half or not; a maxed or
		// simmed ceiling drops the base capes the combos tie with.
		RequirementProfile req = ctx.requirements.withAllQuests();
		if (ctx.p.simMaxCape) { req = req.waiving(MaxCapeSims.ids(data, id -> true)); }
		OptimizationRequest r = request(ctx, mob, style, gameLevels, ALL,
			req, CandidateMode.ALL_STANDARD, 1, 0)
			// The ceiling drinks a super antifire and casts with sunfire runes:
			// its shield slot is for dps (Andrew 2026-10-19: best aligns with
			// the highest-dps option).
			.withAntifirePotion(true).withSunfireRunes(true)
			// The true ceiling ignores your exclusions - they shape Yours and
			// the swaps strip (Andrew 2026-10-22: "BiS ignores exclusions").
			.withExcludedItems(MaxCapeSims.wearable(data, req) ? MaxCapeSims.bases(data) : Set.of())
			// The ceiling is a finished account: no Karuulm boots needed.
			.withWaivers(Set.of(RequiredGear.KOUREND_ELITE));
		if (tool != null) {
			r = r.withPinnedItems(tool.gamePins).withMultiStack(ctx.p.multiStack);
			if (tool.spell != null) { r = r.withSpell(tool.spell); }
		}
		return withPrayerPick(r, ctx, style, gameLevels,
			ctx.p.f2pOnly ? F2P : ALL);
	}

	/** The request both the owned and the game-best sides start from: the
	 * mob, style and levels, the best prayers at those levels and unlocks
	 * (the spell auto-picked, untradeables counted, budgetGp = the total
	 * upgrade spend OWNED_OR_BUDGET allows) and the query-wide settings. */
	private OptimizationRequest request(ComputeContext ctx, MonsterStats mob, CombatStyle style,
		PlayerLevels levels, PrayerUnlocks unlocks, RequirementProfile requirements,
		CandidateMode mode, int limit, int budgetGp) {
		return new OptimizationRequest(mob, style, levels, bestAvailable(levels, unlocks),
			null, budgetGp, mode, true, ctx.p.onSlayerTask, ctx.effectiveOwned, requirements, limit)
			.withExcludedItems(excludedFor(ctx, style, mob))
			.withSpellbookLock(ctx.lock)
			.withMaxTradeables(ctx.p.maxTradeables).withRiskBudgetGp(ctx.riskBudget)
			.withAntifirePotion(ctx.p.antifirePotion)
			.withSunfireRunes(ctx.p.sunfireRunes)
			.withDeathCharge(ctx.p.deathCharge)
			.withInWilderness(ctx.p.inWilderness)
			.withProtectOnlyItems(ctx.protectOnly);
	}

	private LoadoutData f2pView() {
		LoadoutData view = f2pData;
		if (view == null) {
			view = data.freeToPlayView();
			f2pData = view;
		}
		return view;
	}

	/** Drop all cached results - account or profile switched. */
	public void clearCache() {
		synchronized (cache) { cache.clear(); }
	}

	public void shutdown() {
		worker.shutdownNow();
		rosterPool.shutdownNow();
		clearCache();
		synchronized (optimizeCache) { optimizeCache.clear(); }
	}
}
