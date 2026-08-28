package com.loadoutlab.optimizer;

import com.loadoutlab.data.DefenceFloors;
import com.loadoutlab.data.SpellStats;
import com.loadoutlab.engine.RaidBoosts;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import com.loadoutlab.engine.BoostProfile;
import com.loadoutlab.engine.CandidateMode;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.DpsCalculator;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.IncomingDpsCalculator;
import com.loadoutlab.engine.Loadout;
import com.loadoutlab.engine.LoadoutOptimizer;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerBonuses;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.PvpRisk;
import com.loadoutlab.engine.RangedAmmo;
import com.loadoutlab.engine.RequirementProfile;
import com.loadoutlab.engine.SpecialAttack;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.EnumSet;
import java.util.Collection;

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
public class OptimizerService
{
	private static final org.slf4j.Logger log =
		org.slf4j.LoggerFactory.getLogger(OptimizerService.class);

	/** Per-STYLE entries (a monster query stores three). */
	private static final int CACHE_MAX = 192;

	/** Roster optimize-cache traffic, cumulative - the per-compute debug
	 * line logs the deltas so a field log shows warm vs cold plainly. */
	private final java.util.concurrent.atomic.AtomicLong optimizeHits =
		new java.util.concurrent.atomic.AtomicLong();
	private final java.util.concurrent.atomic.AtomicLong optimizeMisses =
		new java.util.concurrent.atomic.AtomicLong();

	/** Per-style outcome: your best owned sets, the game-wide best set, and
	 * the strongest special-attack weapon for each - owned and game-wide. */
	public static final class StyleResult
	{
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
		public final IncomingDpsCalculator.Result incoming;
		/** Same, in the game-best set - the BiS side's DTPS (nullable). */
		public final IncomingDpsCalculator.Result gameIncoming;
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

		StyleResult(List<DpsResult> owned, DpsResult overallBest,
			SpecPick spec, SpecPick gameSpec, String boostLabel, String gameBoostLabel,
			IncomingDpsCalculator.Result incoming, IncomingDpsCalculator.Result gameIncoming,
			List<GearItem> bench, List<GearItem> gameBench)
		{
			this(owned, overallBest, spec, gameSpec, boostLabel, gameBoostLabel,
				incoming, gameIncoming, bench, gameBench, true, true);
		}

		StyleResult(List<DpsResult> owned, DpsResult overallBest,
			SpecPick spec, SpecPick gameSpec, String boostLabel, String gameBoostLabel,
			IncomingDpsCalculator.Result incoming, IncomingDpsCalculator.Result gameIncoming,
			List<GearItem> bench, List<GearItem> gameBench,
			boolean ownedKitBacked, boolean gameKitBacked)
		{
			this.ownedKitBacked = ownedKitBacked;
			this.gameKitBacked = gameKitBacked;
			this.bench = bench == null ? Collections.emptyList()
				: Collections.unmodifiableList(bench);
			this.gameBench = gameBench == null ? Collections.emptyList()
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
	private final java.util.concurrent.ExecutorService rosterPool =
		Executors.newFixedThreadPool(
			ROSTER_POOL_SIZE, r ->
			{
				Thread t = new Thread(r, "loadout-lab-roster");
				t.setDaemon(true);
				t.setPriority(Thread.MIN_PRIORITY + 1);
				return t;
			});
	private final ExecutorService worker = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "loadout-lab-optimizer");
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
		new LinkedHashMap<String, StyleResult>(32, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, StyleResult> eldest)
			{
				return size() > CACHE_MAX;
			}
		};

	public OptimizerService(LoadoutData data)
	{
		this.data = data;
	}

	/**
	 * Compute, per melee/ranged/magic: the best OWNED set and the game-wide
	 * best set against a monster. Cached; the callback runs on the worker
	 * thread on a miss and synchronously on a hit.
	 */
	/** raidBoostAssumed=false falls back to the bank's own potions even
	 * inside a raid. */
	public void bestPerStyle(
		MonsterStats monster,
		PlayerLevels realLevels,
		PlayerLevels boostedLevels,
		PrayerUnlocks prayerUnlocks,
		RequirementProfile requirements,
		OwnedItems owned,
		int collectionFingerprint,
		boolean f2pOnly,
		boolean onSlayerTask,
		String spellbookLock,
		Map<CombatStyle, Set<Integer>> excludedByStyle,
		int maxTradeables,
		int riskBudgetGp,
		boolean antifirePotion,
		int deathCharge,
		boolean specWeapon,
		Map<CombatStyle, String> boostPicks,
		Map<CombatStyle, String> prayerPicks,
		boolean inWilderness,
		Set<Integer> dreamItems,
		int upgradeBudgetGp,
		int maxSwaps,
		boolean raidBoostAssumed,
		Map<CombatStyle, Map<GearSlot, Integer>> pinnedByStyle,
		SpellStats pinnedSpell,
		int pinnedSpec,
		Set<Integer> protectOnlyItems,
		Consumer<Map<CombatStyle, StyleResult>> callback)
	{
		final ComputeContext ctx = buildContext(realLevels, boostedLevels, prayerUnlocks,
			requirements, owned, collectionFingerprint, f2pOnly, onSlayerTask, spellbookLock,
			excludedByStyle, maxTradeables, riskBudgetGp, antifirePotion, deathCharge, specWeapon, boostPicks, prayerPicks, inWilderness,
			dreamItems, upgradeBudgetGp, maxSwaps, pinnedByStyle, pinnedSpell, pinnedSpec, protectOnlyItems);
		ctx.raidBoostAssumed = raidBoostAssumed;
		final String baseKey = baseKeyFor(monster, ctx);
		Map<CombatStyle, StyleResult> allCached = new EnumMap<>(CombatStyle.class);
		synchronized (cache)
		{
			for (CombatStyle style : CombatStyle.concreteValues())
			{
				StyleResult hit = cache.get(styleKey(baseKey, style, ctx.pins, ctx.excluded, ctx.pinnedSpell));
				if (hit == null)
				{
					allCached = null;
					break;
				}
				allCached.put(style, hit);
			}
		}
		if (allCached != null)
		{
			callback.accept(allCached);
			return;
		}
		final long ticket = requestSeq.incrementAndGet();
		worker.execute(() ->
		{
			if (requestSeq.get() != ticket)
			{
				abandonedForTest++;
				return; // superseded while queued
			}
			Map<CombatStyle, StyleResult> results = computeAllStyles(monster, ctx, ticket);
			if (results == null || requestSeq.get() != ticket)
			{
				abandonedForTest++;
				return; // superseded during or after the compute
			}
			callback.accept(results);
		});
	}

	/** Every query-wide value the per-style compute needs, gathered once so
	 * the roster path can run computeAllStyles for each mob. */
	private static final class ComputeContext
	{
		LoadoutData dataset;
		OwnedItems effectiveOwned;
		PlayerLevels real;
		PlayerLevels boostedLevels;
		PrayerUnlocks unlocks;
		RequirementProfile requirements;
		boolean f2pOnly;
		boolean onSlayerTask;
		boolean antifirePotion;
		int deathCharge;
		boolean specWeapon = true;
		Map<CombatStyle, String> boostPicks = Collections.emptyMap();
		Map<CombatStyle, String> prayerPicks = Collections.emptyMap();
		boolean inWilderness;
		String lock;
		int maxTradeables;
		int riskBudget;
		int upgradeBudgetGp;
		int collectionFingerprint;
		Set<Integer> dreams;
		Set<Integer> protectOnly;
		/** The bench size: carried items beyond the worn set; the spec
		 * weapon occupies a slot when it differs from the worn weapon. */
		int maxSwaps = 1;
		Map<CombatStyle, Map<GearSlot, Integer>> pins;
		Map<CombatStyle, Set<Integer>> excluded;
		/** Assume the raid-supplied boost (CoX overload+, ToA salts) when
		 * fighting inside - OFF falls back to the bank's own potions. */
		boolean raidBoostAssumed = true;
		/** PER-MOB exclusions for rosters, keyed by MonsterStats.profileId()
		 * (field decision 2026-07-17): each mob's request carries its own
		 * exclusions on top of the global set - the group may still use the
		 * item, the excluding mob's answer never does. */
		Map<Integer, Map<CombatStyle, Set<Integer>>> excludedByMob = Collections.emptyMap();
		/** PER-MOB sims (profileId -> ids): counted as owned for that mob
		 * only - the local twin of the global dream items. */
		Map<Integer, Set<Integer>> dreamsByMob = Collections.emptyMap();
		SpellStats pinnedSpell;
		/** A pinned SPEC weapon id (0 = auto-pick): forces the spec slot on
		 * the card whose style the weapon serves, leaving other styles to
		 * pick freely. The pin machinery's carried-slot twin. */
		int pinnedSpec;
	}

	/** The spec-weapon restriction (0 = none): a user PIN forces the spec
	 * slot to that weapon on EVERY style card, not just its own - specs
	 * compete cross-style (a ranged Tonalztics is a real spec swap on a
	 * melee setup; a DWH on a magic set), so a pin that only bound its own
	 * style read as "the pin doesn't take" when viewing another card
	 * (field report 2026-08-09). A card the weapon cannot serve shows the
	 * pinned spec at its honest ~0 (keepZero), never a different auto pick. */
	private static Set<Integer> restrictSpec(ComputeContext ctx, CombatStyle style)
	{
		if (ctx.pinnedSpec == 0)
		{
			return null;
		}
		GearItem pinned = ctx.dataset.getGear(ctx.pinnedSpec);
		return pinned != null && SpecialAttack.match(pinned) != null
			? Collections.singleton(ctx.pinnedSpec) : null;
	}

	/** Per-(mob, style, side) optimize results for the roster path, LRU.
	 * Keyed WITHOUT mode/inventory (they do not shape an optimize), so a
	 * chip tweak recomputes only the kit - not the ~2x3xN optimizes. */
	private final Map<String, List<DpsResult>> optimizeCache =
		new LinkedHashMap<String, List<DpsResult>>(256, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, List<DpsResult>> eldest)
			{
				return size() > 1024;
			}
		};

	private static String optimizeKey(MonsterStats mob, CombatStyle style, boolean game,
		ComputeContext ctx)
	{
		// getId() ALONE collides: a boss's versions share one id (Maggot
		// King's Far/Nearby/Roaring, field report 2026-08-20 - Nearby's
		// cached melee answer was served for the melee-IMMUNE Far row).
		// The version string is the missing discriminator.
		return mob.getId() + "|" + mob.getVersion() + "|" + mob.getToaInvocationLevel()
			+ "|" + style.name() + "|" + (game ? "g" : "o")
			+ "|" + ctxKey(ctx)
			+ "|" + dreamsFor(ctx, mob).hashCode()
			+ "|" + excludedFor(ctx, style, mob).hashCode()
			+ "|" + ctx.pins.getOrDefault(style, Collections.emptyMap()).hashCode()
			+ "|" + (style == CombatStyle.MAGIC && ctx.pinnedSpell != null
				? ctx.pinnedSpell.getName() : "");
	}

	/** The query-wide ctx fields every cache key shares (keys only need
	 * equality, so the two key builders reuse one middle). */
	private static String ctxKey(ComputeContext ctx)
	{
		return ctx.collectionFingerprint + "|" + ctx.f2pOnly + "|" + ctx.onSlayerTask
			+ "|" + ctx.lock + "|" + ctx.unlocks.key() + "|" + ctx.maxTradeables
			+ "|" + ctx.riskBudget + "|" + ctx.antifirePotion + "|" + ctx.deathCharge
			+ "|" + ctx.specWeapon + "|" + ctx.inWilderness + "|" + ctx.boostPicks + "|" + ctx.prayerPicks
			+ "|" + ctx.upgradeBudgetGp + "|" + ctx.protectOnly.hashCode() + "|" + ctx.raidBoostAssumed
			+ "|" + levelKey(ctx.real) + "|" + levelKey(ctx.boostedLevels);
	}

	/** One roster optimize (a pool task): LRU-cached; a hit hands out a
	 * fresh list copy so downstream mutation never poisons the cache. */
	private List<DpsResult> optimizeCached(MonsterStats mob, CombatStyle style, boolean game,
		ComputeContext ctx, OptimizationRequest req, long ticket)
	{
		String key = optimizeKey(mob, style, game, ctx);
		synchronized (optimizeCache)
		{
			List<DpsResult> cached = optimizeCache.get(key);
			if (cached != null)
			{
				optimizeHits.incrementAndGet();
				return new ArrayList<>(cached);
			}
		}
		optimizeMisses.incrementAndGet();
		if (requestSeq.get() != ticket)
		{
			return null; // superseded - stop burning cores
		}
		LoadoutOptimizer local = new LoadoutOptimizer();
		List<DpsResult> best = local.optimize(ctx.dataset, req);
		polish(local, ctx.dataset, req, best);
		synchronized (optimizeCache)
		{
			optimizeCache.put(key, new ArrayList<>(best));
		}
		return best;
	}

	/** The displayed set's finish, shared by every optimize site: top up
	 * DPS-neutral empty slots and required utility (verified DPS-neutral). */
	private static void polish(LoadoutOptimizer opt, LoadoutData dataset,
		OptimizationRequest req, List<DpsResult> best)
	{
		if (!best.isEmpty())
		{
			best.set(0, opt.fillDpsNeutralSlots(dataset, req, best.get(0)));
			best.set(0, opt.ensureRequiredUtility(dataset, req, best.get(0)));
		}
	}

	/** The dream items for ONE mob: global sims plus that mob's own. */
	private static Set<Integer> dreamsFor(ComputeContext ctx, MonsterStats mob)
	{
		Set<Integer> local = ctx.dreamsByMob.get(mob.profileId());
		if (local == null || local.isEmpty())
		{
			return ctx.dreams;
		}
		Set<Integer> merged = new HashSet<>(ctx.dreams);
		merged.addAll(local);
		return merged;
	}

	/** The style's exclusions for ONE mob: global plus that mob's own. */
	private static Set<Integer> excludedFor(ComputeContext ctx, CombatStyle style, MonsterStats mob)
	{
		Set<Integer> global = ctx.excluded.getOrDefault(style, Collections.emptySet());
		Map<CombatStyle, Set<Integer>> byMob = ctx.excludedByMob.get(mob.profileId());
		Set<Integer> per = byMob == null ? null : byMob.get(style);
		if (per == null || per.isEmpty())
		{
			return global;
		}
		Set<Integer> merged = new HashSet<>(global);
		merged.addAll(per);
		return merged;
	}

	/** The query-wide cache key for one monster (per-style state layered on
	 * top by styleKey). Centralised so the fast path and computeAllStyles
	 * can never drift into split/colliding cache buckets. */
	private static String baseKeyFor(MonsterStats monster, ComputeContext ctx)
	{
		// The invocation level scales the monster's defence but not its id
		// (found 2026-08-09: optimizeKey carried it, this key did not, so
		// flipping the Invocation chip served the previous level's cache).
		// The VERSION is the same lesson (found 2026-08-20): a boss's
		// versions share one id, so Far/Nearby/Roaring collided.
		return monster.getId() + "|" + monster.getVersion() + "|" + monster.getToaInvocationLevel()
			+ "|" + ctxKey(ctx)
			+ "|" + ctx.dreams.hashCode() + "|" + ctx.maxSwaps + "|" + ctx.pinnedSpec;
	}

	private Map<CombatStyle, StyleResult> computeAllStyles(
		MonsterStats monster, ComputeContext ctx, long ticket)
	{
		String baseKey = baseKeyFor(monster, ctx);
			Map<CombatStyle, StyleResult> results = new EnumMap<>(CombatStyle.class);
			for (CombatStyle style : CombatStyle.concreteValues())
			{
				if (requestSeq.get() != ticket)
				{
					abandonedForTest++;
					return null; // superseded mid-flight - abandon between styles
				}
				// Styles cache independently: a pin or per-set exclusion on
				// one style leaves the other two answers standing.
				String styleKey = styleKey(baseKey, style, ctx.pins, ctx.excluded, ctx.pinnedSpell);
				StyleResult cachedStyle;
				synchronized (cache)
				{
					cachedStyle = cache.get(styleKey);
				}
				if (cachedStyle != null)
				{
					results.put(style, cachedStyle);
					continue;
				}
				// Assume the best boost the player OWNS (drink what you
				// bring), never below what is already live - unless the
				// RAID supplies its own (CoX overload+, ToA salts).
				BoostProfile supplied = ctx.raidBoostAssumed
					? RaidBoosts.suppliedBoost(monster) : null;
				StylePlan plan = stylePlan(ctx, style, supplied);
				PlayerLevels styleLevels = plan.levels;
				String boostLabel = plan.label;
				PlayerLevels gameLevels = plan.gameLevels;
				String gameBoostLabel = plan.gameLabel;
				// Dreams are pretend-owned; a positive upgrade budget also
				// admits anything buyable within it (total spend, tracked
				// by the beam).
				OptimizationRequest ownedRequest = ownedRequestFor(ctx, monster, style, styleLevels);
				List<DpsResult> ownedBest = optimizer.optimize(ctx.dataset, ownedRequest);
				// The displayed set: top up DPS-neutral empty slots with
				// prayer/defensive gear (verified not to change the DPS).
				polish(optimizer, ctx.dataset, ownedRequest, ownedBest);
				// The ceiling: every obtainable item, no quest/level gating -
				// but computed at the player's own levels, so the comparison
				// percentage isolates the GEAR gap.
				// ALL_STANDARD ignores ownership for eligibility, but the
				// candidate dedupe prefers OWNED analogs on stat ties - so
				// the game-best card shows YOUR god d'hide coif, not an
				// arbitrary god's, and the BiS border matches by id.
				OptimizationRequest gameRequest = gameRequestFor(ctx, monster, style, gameLevels);
				List<DpsResult> gameBest = optimizer.optimize(ctx.dataset, gameRequest);
				polish(optimizer, ctx.dataset, gameRequest, gameBest);
				// Bench 0 = strictly one worn set: no spec swap is carried.
				Set<Integer> specPin = restrictSpec(ctx, style);
				SpecPick spec = ctx.maxSwaps >= 1 && ctx.specWeapon
					? arbitrateLightbearer(ctx.dataset, ownedRequest, ownedBest, style, monster, styleLevels, ctx.effectiveOwned, specPin)
					: null;
				SpecPick gameSpec = ctx.maxSwaps >= 1 && ctx.specWeapon
					? arbitrateLightbearer(ctx.dataset, gameRequest, gameBest, style, monster, gameLevels, null, specPin)
					: null;
				// The defensive story of the shown set: what the boss does
				// back to you, at your REAL levels (protection prayer up).
				IncomingDpsCalculator.Result incoming = incomingFor(monster,
					ownedBest.isEmpty() ? null : ownedBest.get(0).getLoadout(), ctx);
				IncomingDpsCalculator.Result gameIncoming = incomingFor(monster,
					gameBest.isEmpty() ? null : gameBest.get(0).getLoadout(), ctx);
				List<GearItem> bench = specBenchOf(spec, ownedBest);
				// The BiS side's inventory mirrors it (field fix 2026-07-18:
				// a single mob's BiS view showed no carried spec).
				List<GearItem> gameBench = specBenchOf(gameSpec, gameBest);
				StyleResult styleResult = new StyleResult(
					ownedBest, gameBest.isEmpty() ? null : gameBest.get(0), spec, gameSpec,
					boostLabel, gameBoostLabel, incoming, gameIncoming, bench, gameBench);
				// Store per style as computed - even a superseded job donates
				// the styles it finished.
				synchronized (cache)
				{
					cache.put(styleKey, styleResult);
				}
				results.put(style, styleResult);
			}
		return results;
	}

	/** What the boss does back to you in this worn set, at REAL levels
	 * (protection prayer up) - null loadout answers null. Twin owned/game
	 * call sites in both compute paths share it. */
	private static IncomingDpsCalculator.Result incomingFor(MonsterStats mob,
		Loadout worn, ComputeContext ctx)
	{
		return worn == null ? null
			: IncomingDpsCalculator.calculate(mob, worn, ctx.real.getDefence(), ctx.real.getMagic());
	}

	/** One side's bench for the single-mob path: the spec weapon when the
	 * shown set does not already wear it. */
	private static List<GearItem> specBenchOf(SpecPick spec, List<DpsResult> best)
	{
		List<GearItem> bench = new ArrayList<>();
		if (spec != null && spec.weapon != null && !best.isEmpty()
			&& (best.get(0).getLoadout().getWeapon() == null
				|| best.get(0).getLoadout().getWeapon().getId() != spec.weapon.getId()))
		{
			bench.add(spec.weapon);
		}
		return bench;
	}

	/** Gather the query-wide state one compute needs. Shared by the single-
	 * mob (bestPerStyle) and roster (bestPerStyleAcross) entry points so the
	 * two can never disagree on levels, cache keys, or candidate eligibility. */
	private ComputeContext buildContext(
		PlayerLevels realLevels, PlayerLevels boostedLevels, PrayerUnlocks prayerUnlocks,
		RequirementProfile requirements, OwnedItems owned, int collectionFingerprint,
		boolean f2pOnly, boolean onSlayerTask, String spellbookLock,
		Map<CombatStyle, Set<Integer>> excludedByStyle, int maxTradeables, int riskBudgetGp,
		boolean antifirePotion, int deathCharge, boolean specWeapon, Map<CombatStyle, String> boostPicks, Map<CombatStyle, String> prayerPicks, boolean inWilderness, Set<Integer> dreamItems, int upgradeBudgetGp,
		int maxSwaps,
		Map<CombatStyle, Map<GearSlot, Integer>> pinnedByStyle,
		SpellStats pinnedSpell, int pinnedSpec, Set<Integer> protectOnlyItems)
	{
		ComputeContext ctx = new ComputeContext();
		ctx.maxSwaps = Math.max(0, maxSwaps);
		ctx.pins = pinnedByStyle == null ? Collections.emptyMap() : pinnedByStyle;
		ctx.excluded = excludedByStyle == null ? Collections.emptyMap() : excludedByStyle;
		ctx.dreams = dreamItems == null ? Collections.emptySet() : dreamItems;
		ctx.protectOnly = protectOnlyItems == null ? Collections.emptySet() : protectOnlyItems;
		ctx.lock = spellbookLock == null ? "" : spellbookLock;
		ctx.real = realLevels == null ? boostedLevels : realLevels;
		ctx.boostedLevels = boostedLevels;
		// A free world has no members prayers - F2P caps the book (audit A3.5).
		ctx.unlocks = f2pOnly ? PrayerUnlocks.F2P
			: prayerUnlocks == null ? PrayerUnlocks.ALL : prayerUnlocks;
		// The budget only matters when risk-constrained; pin it otherwise so
		// flipping the dropdown with the cap off cannot split the cache.
		ctx.riskBudget = maxTradeables >= 0 ? riskBudgetGp : OptimizationRequest.DEFAULT_RISK_BUDGET_GP;
		ctx.dataset = f2pOnly ? f2pView() : data;
		// Owned ornament/locked variants count as their base item.
		ctx.effectiveOwned = new OwnedItems(
			ctx.dataset.canonicalizeOwned(owned.getQuantities()), owned.isBankScanned());
		ctx.requirements = requirements;
		ctx.f2pOnly = f2pOnly;
		ctx.onSlayerTask = onSlayerTask;
		ctx.antifirePotion = antifirePotion;
		ctx.deathCharge = deathCharge;
		ctx.specWeapon = specWeapon;
		ctx.boostPicks = boostPicks == null ? Collections.emptyMap() : boostPicks;
		ctx.prayerPicks = prayerPicks == null ? Collections.emptyMap() : prayerPicks;
		ctx.inWilderness = inWilderness;
		ctx.maxTradeables = maxTradeables;
		ctx.upgradeBudgetGp = upgradeBudgetGp;
		ctx.collectionFingerprint = collectionFingerprint;
		ctx.pinnedSpell = pinnedSpell;
		ctx.pinnedSpec = pinnedSpec;
		return ctx;
	}

	/** The inventory BREAKPOINT CURVE (field spec 2026-07-18): one greedy
	 * run to exhaustion for the winning kit configuration, recording after
	 * every pick how many slots are spent, the HP-weighted total, and how
	 * many mobs have a nonzero answer. The UI reads the minimum-viability
	 * point, the major/minor breakpoints, and the final breakpoint (where
	 * more slots stop paying) straight off the points. */
	public static final class KitCurve
	{
		/** Each point: {slots spent, hpWeighted total, viable mob count}.
		 * The first point is the empty kit; points are pick-ordered. */
		public final List<double[]> points;
		public final int mobCount;
		/** True when the first slot carries the spec weapon. */
		public final boolean specFirst;

		KitCurve(List<double[]> points, int mobCount, boolean specFirst)
		{
			this.points = Collections.unmodifiableList(points);
			this.mobCount = mobCount;
			this.specFirst = specFirst;
		}
	}

	/** The roster answer: one shared set per style, chosen for the best
	 * AVERAGE dps across the mobs (field decision 2026-07-17), with a per-mob
	 * display bundle for that set (index-aligned with the mob list). */
	public static final class RosterResult
	{
		public final List<MonsterStats> mobs;
		public final List<Map<CombatStyle, StyleResult>> perMob;
		/** Nullable: the owned side's inventory breakpoint curve. */
		public final KitCurve curve;

		RosterResult(List<MonsterStats> mobs, List<Map<CombatStyle, StyleResult>> perMob)
		{
			this(mobs, perMob, null);
		}

		RosterResult(List<MonsterStats> mobs, List<Map<CombatStyle, StyleResult>> perMob,
			KitCurve curve)
		{
			this.mobs = mobs;
			this.perMob = perMob;
			this.curve = curve;
		}
	}

	/**
	 * Greedy bench selection: candidates are the per-mob free-best items
	 * that differ from the shared base; each round carries the item with
	 * the best HP-weighted marginal gain (every mob wearing its best
	 * combination of base + carried) until the bench is full or nothing
	 * gains. Small scale by construction: candidates <= mobs x slots.
	 */
	/** True when a per-mob optimize answer has nothing usable in front. */
	private static boolean emptyBest(List<DpsResult> best)
	{
		return best == null || best.isEmpty() || best.get(0) == null;
	}

	/** The same-style swap candidate pool: every per-mob free-best item
	 * that differs from the base in its slot. */
	private static Collection<GearItem> swapCandidates(Loadout base,
		List<List<DpsResult>> freeBests)
	{
		LinkedHashMap<Integer, GearItem> candidates = new LinkedHashMap<>();
		for (List<DpsResult> best : freeBests)
		{
			if (emptyBest(best))
			{
				continue;
			}
			for (GearItem item : best.get(0).getLoadout().getGear().values())
			{
				if (item == null)
				{
					continue;
				}
				GearItem baseItem = base.get(item.getSlot());
				if (baseItem == null || baseItem.getId() != item.getId())
				{
					candidates.putIfAbsent(item.getId(), item);
				}
			}
		}
		return candidates.values();
	}

	private List<GearItem> chooseSwaps(DpsCalculator calc, Loadout base,
		List<OptimizationRequest> reqs, List<MonsterStats> mobs,
		List<List<DpsResult>> freeBests, int benchForSwaps)
	{
		List<GearItem> carried = new ArrayList<>();
		if (benchForSwaps <= 0)
		{
			return carried;
		}
		Collection<GearItem> candidates = swapCandidates(base, freeBests);
		double current = hpWeightedTotal(calc, base, carried, reqs, mobs);
		while (carried.size() < benchForSwaps)
		{
			GearItem bestPick = null;
			double bestTotal = current;
			for (GearItem candidate : candidates)
			{
				if (carried.contains(candidate))
				{
					continue;
				}
				carried.add(candidate);
				double total = hpWeightedTotal(calc, base, carried, reqs, mobs);
				carried.remove(carried.size() - 1);
				if (total > bestTotal + 1e-9)
				{
					bestTotal = total;
					bestPick = candidate;
				}
			}
			if (bestPick == null)
			{
				break; // nothing left that helps
			}
			carried.add(bestPick);
			current = bestTotal;
		}
		return carried;
	}

	private double hpWeightedTotal(DpsCalculator calc, Loadout base,
		List<GearItem> carried, List<OptimizationRequest> reqs, List<MonsterStats> mobs)
	{
		double sum = 0;
		for (int j = 0; j < reqs.size(); j++)
		{
			DpsResult worn = calcRespecting(calc, reqs.get(j), bestWorn(calc, reqs.get(j), base, carried));
			sum += (worn == null ? 0 : worn.getDps()) * Math.max(1, mobs.get(j).getHitpoints());
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
	private static int unownedValue(Loadout loadout, OwnedItems owned)
	{
		if (loadout == null)
		{
			return 0;
		}
		long total = 0;
		for (GearItem item : loadout.getGear().values())
		{
			if (item != null && (owned == null || !owned.owns(item.getId())))
			{
				total += item.getPriceOrZero();
			}
		}
		return (int) Math.min(Integer.MAX_VALUE, total);
	}

	private static DpsResult calcRespecting(DpsCalculator calc,
		OptimizationRequest req, Loadout loadout)
	{
		if (loadout != null)
		{
			Set<Integer> excluded = req.getExcludedItems();
			// A per-mob SIM is pretend-owned for THAT mob only (field bug
			// 2026-07-18: a rapier simmed vs one group member leaked into
			// every mob's shared answer) - on the owned side, a loadout may
			// only wear what this request's bank or dreams grant.
			boolean ownedOnly = req.getCandidateMode() == CandidateMode.OWNED_ONLY;
			for (GearItem item : loadout.getGear().values())
			{
				if (item == null)
				{
					continue;
				}
				if (excluded != null && excluded.contains(item.getId()))
				{
					return null;
				}
				if (ownedOnly && !req.getOwnedItems().owns(item.getId())
					&& !req.isDream(item.getId()))
				{
					return null;
				}
			}
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
		GearItem specCarried, Loadout worn)
	{
		Set<Integer> wornIds = new HashSet<>();
		if (worn != null)
		{
			for (GearItem item : worn.getGear().values())
			{
				if (item != null)
				{
					wornIds.add(item.getId());
				}
			}
		}
		List<GearItem> inventory = new ArrayList<>();
		Set<Integer> packed = new HashSet<>();
		if (specCarried != null && !wornIds.contains(specCarried.getId())
			&& packed.add(specCarried.getId()))
		{
			inventory.add(specCarried);
		}
		for (GearItem item : plan)
		{
			if (!wornIds.contains(item.getId()) && packed.add(item.getId()))
			{
				inventory.add(item);
			}
		}
		return inventory;
	}

	/** A cross-style carry: another style's weapon (+ its ammo when the
	 * weapon needs slot ammo), worn INTO the primary base set and rolled
	 * under its OWN style's request - "bring a blowpipe in your melee
	 * gear". Cost = bench slots consumed; a weapon the bench already
	 * carries as the spec weapon costs nothing extra. */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	private static final class SwapBundle
	{
		final CombatStyle style;
		final List<GearItem> items;
		final int cost;
	}

	/** The chosen kit: same-style single swaps + cross-style bundles. */
	private static final class KitAnswer
	{
		final List<GearItem> singles = new ArrayList<>();
		final List<SwapBundle> bundles = new ArrayList<>();
		double total;
	}

	/** The mob's own free best, when the kit can actually assemble it -
	 * every worn item present in the base or carried. This is what makes
	 * a BIG bench an EASY problem (field insight 2026-07-17): at the
	 * limit every mob simply wears its own best owned set, exactly. */
	private static DpsResult coverableBest(List<DpsResult> freeBest,
		Loadout base, List<GearItem> carried)
	{
		if (emptyBest(freeBest))
		{
			return null;
		}
		Set<Integer> have = new HashSet<>();
		for (GearItem item : base.getGear().values())
		{
			if (item != null)
			{
				have.add(item.getId());
			}
		}
		for (GearItem item : carried)
		{
			have.add(item.getId());
		}
		for (GearItem item : freeBest.get(0).getLoadout().getGear().values())
		{
			if (item != null && !have.contains(item.getId()))
			{
				return null;
			}
		}
		return freeBest.get(0);
	}

	/** Wear a bundle into the base set; null when it cannot be worn
	 * honestly (shield under a 2h, projectile weapon with no ammo). */
	private static Loadout applyBundle(Loadout base, SwapBundle bundle)
	{
		Loadout out = base;
		for (GearItem item : bundle.items)
		{
			Loadout next = withSwap(out, item);
			if (next == null)
			{
				return null;
			}
			out = next;
		}
		GearItem weapon = out.getWeapon();
		GearItem ammo = out.get(GearSlot.AMMO);
		if (weapon != null && ammo != null && !RangedAmmo.compatible(ammo, weapon))
		{
			// The base set's quiver does not fit the swapped weapon -
			// strip it rather than let its bonuses feed a false roll.
			EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
			gear.putAll(out.getGear());
			gear.remove(GearSlot.AMMO);
			out = new Loadout(gear);
		}
		if (!RangedAmmo.compatible(out.get(GearSlot.AMMO), out.getWeapon()))
		{
			return null; // a projectile weapon carried without its ammo
		}
		return out;
	}

	/** Cross-style bundle candidates for a primary base: the other styles'
	 * shared and per-mob free-best weapons, each offered bare and (when the
	 * source set wears ammo) with its ammo. */
	private static List<SwapBundle> bundleCandidates(CombatStyle primary, Loadout base,
		GearItem specCarried, Map<CombatStyle, Loadout> sharedByStyle,
		Map<CombatStyle, List<List<DpsResult>>> bestsByStyle)
	{
		List<SwapBundle> out = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (Map.Entry<CombatStyle, Loadout> entry : sharedByStyle.entrySet())
		{
			CombatStyle style = entry.getKey();
			if (style == primary)
			{
				continue;
			}
			List<Loadout> sources = new ArrayList<>();
			sources.add(entry.getValue());
			for (List<DpsResult> best : bestsByStyle.get(style))
			{
				if (!emptyBest(best))
				{
					sources.add(best.get(0).getLoadout());
				}
			}
			for (Loadout source : sources)
			{
				GearItem weapon = source.getWeapon();
				if (weapon == null
					|| (base.getWeapon() != null && base.getWeapon().getId() == weapon.getId()))
				{
					continue;
				}
				GearItem ammo = source.get(GearSlot.AMMO);
				GearItem baseAmmo = base.get(GearSlot.AMMO);
				boolean ownAmmo = ammo != null
					&& (baseAmmo == null || baseAmmo.getId() != ammo.getId());
				int specFree = specCarried != null && specCarried.getId() == weapon.getId() ? 1 : 0;
				if (seen.add(weapon.getId() * 2L))
				{
					out.add(new SwapBundle(style,
						Collections.singletonList(weapon), Math.max(0, 1 - specFree)));
				}
				if (ownAmmo && seen.add(weapon.getId() * 2L + 1))
				{
					List<GearItem> items = new ArrayList<>();
					items.add(weapon);
					items.add(ammo);
					out.add(new SwapBundle(style, items, Math.max(0, 2 - specFree)));
				}
			}
		}
		return out;
	}

	/** One mob's best answer given the kit: the primary base + singles,
	 * challenged by every carried bundle rolled under its own style, and
	 * by any style's FREE BEST the kit can fully assemble. */
	private DpsResult kitBest(DpsCalculator calc, Loadout base, CombatStyle primary,
		List<GearItem> singles, List<SwapBundle> bundles, List<GearItem> carried,
		Map<CombatStyle, List<OptimizationRequest>> reqsByStyle,
		Map<CombatStyle, List<List<DpsResult>>> bestsByStyle, int mobIndex)
	{
		OptimizationRequest primaryReq = reqsByStyle.get(primary).get(mobIndex);
		DpsResult best = calcRespecting(calc, primaryReq, bestWorn(calc, primaryReq, base, singles));
		for (SwapBundle bundle : bundles)
		{
			List<OptimizationRequest> reqs = reqsByStyle.get(bundle.style);
			Loadout seeded = reqs == null ? null : applyBundle(base, bundle);
			if (seeded == null)
			{
				continue;
			}
			OptimizationRequest req = reqs.get(mobIndex);
			DpsResult challenger = calcRespecting(calc, req, bestWorn(calc, req, seeded, singles));
			if (challenger != null && (best == null || challenger.getDps() > best.getDps()))
			{
				best = challenger;
			}
		}
		for (List<List<DpsResult>> bests : bestsByStyle.values())
		{
			DpsResult covered = coverableBest(bests.get(mobIndex), base, carried);
			if (covered != null && (best == null || covered.getDps() > best.getDps()))
			{
				best = covered;
			}
		}
		return best;
	}

	/** One mob's kit dps, MEMOIZED (field insight 2026-07-17: hone the
	 * greedy toward dynamic programming): the answer depends only on the
	 * carried bundles and the carried singles RELEVANT to this mob (items
	 * worn by some free best of its own), so the key is exactly that -
	 * testing a candidate against the roster re-evaluates only the mobs
	 * it could matter to, and repeat evaluations are table lookups. */
	private double evalMobDps(DpsCalculator calc, Loadout base, CombatStyle primary,
		List<GearItem> singles, List<SwapBundle> bundles, String bundlesKey,
		Map<CombatStyle, List<OptimizationRequest>> reqsByStyle,
		Map<CombatStyle, List<List<DpsResult>>> bestsByStyle,
		Set<Integer> relevant, int mobIndex, Map<String, Double> memo)
	{
		List<GearItem> relevantSingles = new ArrayList<>();
		List<Integer> ids = new ArrayList<>();
		for (GearItem item : singles)
		{
			if (relevant.contains(item.getId()))
			{
				relevantSingles.add(item);
				ids.add(item.getId());
			}
		}
		Collections.sort(ids);
		String key = mobIndex + "|" + bundlesKey + "|" + ids;
		Double hit = memo.get(key);
		if (hit != null)
		{
			return hit;
		}
		List<GearItem> carried = new ArrayList<>(relevantSingles);
		for (SwapBundle bundle : bundles)
		{
			for (GearItem item : bundle.items)
			{
				if (carried.stream().noneMatch(i -> i.getId() == item.getId()))
				{
					carried.add(item);
				}
			}
		}
		DpsResult shown = kitBest(calc, base, primary, relevantSingles, bundles,
			carried, reqsByStyle, bestsByStyle, mobIndex);
		double dps = shown == null ? 0 : shown.getDps();
		memo.put(key, dps);
		return dps;
	}

	private static String bundlesKeyOf(List<SwapBundle> bundles)
	{
		List<String> parts = new ArrayList<>();
		for (SwapBundle bundle : bundles)
		{
			StringBuilder part = new StringBuilder().append(bundle.style.ordinal()).append(':');
			for (GearItem item : bundle.items)
			{
				part.append(item.getId()).append(',');
			}
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
	 * because a weapon swap is a bundle, not a single. Shared by the kit
	 * pass and the breakpoint-curve pass, which need the identical pool. */
	private static List<GearItem> crossStylePool(Loadout base, CombatStyle primary,
		Map<CombatStyle, Loadout> sharedByStyle,
		Map<CombatStyle, List<List<DpsResult>>> bestsByStyle)
	{
		List<GearItem> pool = new ArrayList<>(swapCandidates(base, bestsByStyle.get(primary)));
		for (CombatStyle s : sharedByStyle.keySet())
		{
			if (s == primary)
			{
				continue;
			}
			for (GearItem item : swapCandidates(base, bestsByStyle.get(s)))
			{
				if (item.getSlot() != GearSlot.WEAPON
					&& pool.stream().noneMatch(i -> i.getId() == item.getId()))
				{
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
		Map<CombatStyle, List<OptimizationRequest>> reqsByStyle,
		Map<CombatStyle, List<List<DpsResult>>> bestsByStyle,
		List<MonsterStats> mobs, int slots, Map<String, Double> memo,
		List<double[]> curveOut)
	{
		List<GearItem> allNeeded = new ArrayList<>();
		double idealTotal = 0;
		for (int j = 0; j < mobs.size(); j++)
		{
			List<DpsResult> winner = null;
			for (List<List<DpsResult>> bests : bestsByStyle.values())
			{
				List<DpsResult> best = bests.get(j);
				if (emptyBest(best))
				{
					continue;
				}
				if (winner == null || best.get(0).getDps() > winner.get(0).getDps())
				{
					winner = best;
				}
			}
			if (winner == null)
			{
				continue;
			}
			idealTotal += winner.get(0).getDps() * Math.max(1, mobs.get(j).getHitpoints());
			for (GearItem item : winner.get(0).getLoadout().getGear().values())
			{
				GearItem baseItem = item == null ? null : base.get(item.getSlot());
				if (item != null
					&& (baseItem == null || baseItem.getId() != item.getId())
					&& allNeeded.stream().noneMatch(i -> i.getId() == item.getId()))
				{
					allNeeded.add(item);
				}
			}
		}
		// A curve run wants the full pick-by-pick sequence - the greedy
		// converges to the same totals (coverage challenge), so the ideal
		// shortcut only serves the non-curve path.
		if (curveOut == null && !allNeeded.isEmpty() && allNeeded.size() <= slots)
		{
			// The ideal fits: every mob wears its own best, exactly (the
			// coverage challenge in kitBest reproduces each winner).
			KitAnswer kit = new KitAnswer();
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
		for (int j = 0; j < n; j++)
		{
			Set<Integer> relevant = new HashSet<>();
			for (List<List<DpsResult>> bests : bestsByStyle.values())
			{
				List<DpsResult> best = bests.get(j);
				if (emptyBest(best))
				{
					continue;
				}
				for (GearItem item : best.get(0).getLoadout().getGear().values())
				{
					if (item != null)
					{
						relevant.add(item.getId());
						mobsByItem.computeIfAbsent(item.getId(),
							k -> new HashSet<>()).add(j);
					}
				}
			}
			relevantByMob.add(relevant);
		}
		double[] hp = new double[n];
		for (int j = 0; j < n; j++)
		{
			hp[j] = Math.max(1, mobs.get(j).getHitpoints());
		}
		KitAnswer kit = new KitAnswer();
		int used = 0;
		double[] mobDps = new double[n];
		double current = 0;
		String baseBundlesKey = bundlesKeyOf(kit.bundles);
		for (int j = 0; j < n; j++)
		{
			mobDps[j] = evalMobDps(calc, base, primary, kit.singles, kit.bundles,
				baseBundlesKey, reqsByStyle, bestsByStyle, relevantByMob.get(j), j, memo);
			current += mobDps[j] * hp[j];
		}
		if (curveOut != null)
		{
			curveOut.add(new double[]{used, current, viableCount(mobDps)});
		}
		while (true)
		{
			GearItem bestSingle = null;
			SwapBundle bestBundle = null;
			double bestTotal = current;
			int bestCost = 0;
			String bundlesKey = bundlesKeyOf(kit.bundles);
			for (GearItem candidate : singleCandidates)
			{
				if (used + 1 > slots || kit.singles.contains(candidate))
				{
					continue;
				}
				Set<Integer> affected = mobsByItem.get(candidate.getId());
				if (affected == null || affected.isEmpty())
				{
					continue; // in nobody's free best - it can help no one
				}
				kit.singles.add(candidate);
				double total = current;
				for (int j : affected)
				{
					total += (evalMobDps(calc, base, primary, kit.singles, kit.bundles,
						bundlesKey, reqsByStyle, bestsByStyle, relevantByMob.get(j), j, memo)
						- mobDps[j]) * hp[j];
				}
				kit.singles.remove(kit.singles.size() - 1);
				if (total > bestTotal + 1e-9)
				{
					bestTotal = total;
					bestSingle = candidate;
					bestBundle = null;
					bestCost = 1;
				}
			}
			for (SwapBundle candidate : bundleCandidates)
			{
				if (used + candidate.cost > slots || kit.bundles.contains(candidate))
				{
					continue;
				}
				// A new bundle opens a style path for every mob.
				kit.bundles.add(candidate);
				String withKey = bundlesKeyOf(kit.bundles);
				double total = 0;
				for (int j = 0; j < n; j++)
				{
					total += evalMobDps(calc, base, primary, kit.singles, kit.bundles,
						withKey, reqsByStyle, bestsByStyle, relevantByMob.get(j), j, memo) * hp[j];
				}
				kit.bundles.remove(kit.bundles.size() - 1);
				if (total > bestTotal + 1e-9)
				{
					bestTotal = total;
					bestBundle = candidate;
					bestSingle = null;
					bestCost = candidate.cost;
				}
			}
			if (bestSingle == null && bestBundle == null)
			{
				break;
			}
			if (bestSingle != null)
			{
				kit.singles.add(bestSingle);
			}
			else
			{
				kit.bundles.add(bestBundle);
			}
			used += bestCost;
			current = bestTotal;
			String appliedKey = bundlesKeyOf(kit.bundles);
			for (int j = 0; j < n; j++)
			{
				mobDps[j] = evalMobDps(calc, base, primary, kit.singles, kit.bundles,
					appliedKey, reqsByStyle, bestsByStyle, relevantByMob.get(j), j, memo);
			}
			if (curveOut != null)
			{
				curveOut.add(new double[]{used, current, viableCount(mobDps)});
			}
		}
		kit.total = current;
		return kit;
	}

	private static int viableCount(double[] mobDps)
	{
		int viable = 0;
		for (double dps : mobDps)
		{
			if (dps > 1e-9)
			{
				viable++;
			}
		}
		return viable;
	}

	/**
	 * The best worn combination for ONE mob: start from the base and
	 * greedily apply the single carried swap with the biggest gain until
	 * none helps (approximates set-bonus interactions without a subset
	 * explosion). A 2h swap clears the shield; a shield swap is skipped
	 * under a 2h weapon.
	 */
	private Loadout bestWorn(DpsCalculator calc, OptimizationRequest req,
		Loadout base, List<GearItem> carried)
	{
		Loadout worn = base;
		DpsResult wornResult = calcRespecting(calc, req, worn);
		double wornDps = wornResult == null ? 0 : wornResult.getDps();
		boolean improved = true;
		Set<Integer> applied = new HashSet<>();
		while (improved)
		{
			improved = false;
			Loadout bestNext = null;
			double bestDps = wornDps;
			Integer bestId = null;
			for (GearItem swap : carried)
			{
				if (applied.contains(swap.getId()))
				{
					continue;
				}
				Loadout trial = withSwap(worn, swap);
				if (trial == null)
				{
					continue;
				}
				DpsResult r = calcRespecting(calc, req, trial);
				double dps = r == null ? 0 : r.getDps();
				if (dps > bestDps + 1e-9)
				{
					bestDps = dps;
					bestNext = trial;
					bestId = swap.getId();
				}
			}
			if (bestNext != null)
			{
				worn = bestNext;
				wornDps = bestDps;
				applied.add(bestId);
				improved = true;
			}
		}
		return worn;
	}

	private static Loadout withSlot(Loadout base, GearSlot slot, GearItem item)
	{
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.putAll(base.getGear());
		if (item == null)
		{
			gear.remove(slot);
		}
		else
		{
			gear.put(slot, item);
		}
		return new Loadout(gear);
	}

	private static Loadout withSwap(Loadout base, GearItem swap)
	{
		GearSlot slot = swap.getSlot();
		GearItem weapon = base.getWeapon();
		if (slot == GearSlot.SHIELD
			&& weapon != null && weapon.isTwoHanded())
		{
			return null; // no shield under a 2h
		}
		EnumMap<GearSlot, GearItem> gear =
			new EnumMap<>(GearSlot.class);
		gear.putAll(base.getGear());
		gear.put(slot, swap);
		if (slot == GearSlot.WEAPON && swap.isTwoHanded())
		{
			gear.remove(GearSlot.SHIELD);
		}
		return new Loadout(gear);
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
		List<List<DpsResult>> bests, List<OptimizationRequest> reqs)
	{
		List<Loadout> candidates = new ArrayList<>();
		for (List<DpsResult> b : bests)
		{
			if (!emptyBest(b))
			{
				candidates.add(b.get(0).getLoadout());
			}
		}
		Loadout best = null;
		double bestScore = -1;
		for (Loadout loadout : candidates)
		{
			double sum = 0;
			for (OptimizationRequest req : reqs)
			{
				DpsResult r = calcRespecting(calc, req, loadout);
				double hp = Math.max(1, req.getMonster().getHitpoints());
				sum += (r == null ? 0 : r.getDps()) * hp;
			}
			if (sum > bestScore + 1e-9)
			{
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
		List<MonsterStats> mobs, ComputeContext ctx, long ticket, KitCurve[] curveOut)
	{
		int n = mobs.size();
		long tStart = System.nanoTime();
		long hitsBefore = optimizeHits.get();
		long missesBefore = optimizeMisses.get();
		List<Map<CombatStyle, StyleResult>> perMob = new ArrayList<>();
		for (int j = 0; j < n; j++)
		{
			perMob.add(new EnumMap<>(CombatStyle.class));
		}
		DpsCalculator calc = new DpsCalculator();
		// Per-style state the cross-style kit passes reuse after the loop -
		// one pass for the Yours side, one for the BiS side.
		Map<CombatStyle, List<OptimizationRequest>> reqsByStyle = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, Loadout> sharedByStyle = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, List<List<DpsResult>>> bestsByStyle = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, List<OptimizationRequest>> gameReqsByStyle = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, Loadout> sharedGameByStyle = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, List<List<DpsResult>>> gameBestsByStyle = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, SpecPick[]> specsByStyle = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, SpecPick[]> gameSpecsByStyle = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, List<SpecPick[]>> specOptionsByStyle = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, List<SpecPick[]>> gameSpecOptionsByStyle = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, String> boostLabelByStyle = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, String> gameBoostLabelByStyle = new EnumMap<>(CombatStyle.class);
		// PASS A (field fix 2026-07-17: the load-time wall was the 2x3xN
		// independent optimize() calls, not the kit search): build every
		// style's plan and requests up front, then ONE flat fan-out over
		// the pool - no per-style straggler waves, owned and game as
		// separate tasks. Each task owns its optimizer (its DpsCalculator
		// is stateful); distinct array slots + invokeAll's join publish.
		Map<CombatStyle, PlayerLevels> styleLevelsBy = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, PlayerLevels> gameLevelsBy = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, String> planBoostLabelBy = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, String> planGameBoostLabelBy = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, List<OptimizationRequest>> ownedReqsBy = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, List<OptimizationRequest>> gameReqsBy = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, List<DpsResult>[]> ownedArrBy = new EnumMap<>(CombatStyle.class);
		Map<CombatStyle, List<DpsResult>[]> gameArrBy = new EnumMap<>(CombatStyle.class);
		List<java.util.concurrent.Callable<Void>> optimizeTasks = new ArrayList<>();
		for (CombatStyle style : CombatStyle.concreteValues())
		{
			// Mob-independent plan - MUST mirror computeAllStyles (levels,
			// boost and labels depend on style + owned + real, not the mob).
			// The roster assumes the raid's own boost when EVERY mob is
			// inside the same raid (CoX overload+, ToA salts).
			BoostProfile supplied = ctx.raidBoostAssumed
				? RaidBoosts.suppliedBoost(mobs.get(0)) : null;
			for (MonsterStats mob : mobs)
			{
				if (RaidBoosts.suppliedBoost(mob) != supplied)
				{
					supplied = null;
					break;
				}
			}
			StylePlan plan = stylePlan(ctx, style, supplied);
			PlayerLevels styleLevels = plan.levels;
			PlayerLevels gameLevels = plan.gameLevels;
			styleLevelsBy.put(style, styleLevels);
			gameLevelsBy.put(style, gameLevels);
			planBoostLabelBy.put(style, plan.label);
			planGameBoostLabelBy.put(style, plan.gameLabel);

			List<OptimizationRequest> ownedReqs = new ArrayList<>();
			List<OptimizationRequest> gameReqs = new ArrayList<>();
			for (MonsterStats mob : mobs)
			{
				ownedReqs.add(ownedRequestFor(ctx, mob, style, styleLevels));
				gameReqs.add(gameRequestFor(ctx, mob, style, gameLevels));
			}
			ownedReqsBy.put(style, ownedReqs);
			gameReqsBy.put(style, gameReqs);
			@SuppressWarnings("unchecked")
			List<DpsResult>[] ownedArr = new List[n];
			@SuppressWarnings("unchecked")
			List<DpsResult>[] gameArr = new List[n];
			ownedArrBy.put(style, ownedArr);
			gameArrBy.put(style, gameArr);
			for (int j = 0; j < n; j++)
			{
				final int index = j;
				final MonsterStats mob = mobs.get(index);
				optimizeTasks.add(() ->
				{
					ownedArr[index] = optimizeCached(mob, style, false, ctx,
						ownedReqs.get(index), ticket);
					return null;
				});
				optimizeTasks.add(() ->
				{
					gameArr[index] = optimizeCached(mob, style, true, ctx,
						gameReqs.get(index), ticket);
					return null;
				});
			}
		}
		long tFanStart = System.nanoTime();
		try
		{
			// Check every future: a task that THROWS otherwise vanishes
			// silently, its array slot stays null, and the whole style
			// renders as "-" with no trace (field bug 2026-08-05: the
			// Sire roster's ranged and magic buttons dashed while every
			// member answered fine alone).
			for (java.util.concurrent.Future<Void> f : rosterPool.invokeAll(optimizeTasks))
			{
				try
				{
					f.get();
				}
				catch (java.util.concurrent.ExecutionException e)
				{
					log.warn("roster optimize task failed", e.getCause());
				}
			}
		}
		catch (InterruptedException e)
		{
			// Cancellation of a superseded search is cooperative, via requestSeq
			// (checked immediately below) - not thread interruption. This runs on
			// our own daemon optimizer worker, interrupted only when the executor
			// is shut down on plugin stop, so just abandon the computation.
			return null;
		}
		if (requestSeq.get() != ticket)
		{
			abandonedForTest++;
			return null;
		}
		long tFanMs = (System.nanoTime() - tFanStart) / 1_000_000;
		for (CombatStyle style : CombatStyle.concreteValues())
		{
			PlayerLevels styleLevels = styleLevelsBy.get(style);
			PlayerLevels gameLevels = gameLevelsBy.get(style);
			String boostLabel = planBoostLabelBy.get(style);
			String gameBoostLabel = planGameBoostLabelBy.get(style);
			List<OptimizationRequest> ownedReqs = ownedReqsBy.get(style);
			List<OptimizationRequest> gameReqs = gameReqsBy.get(style);
			List<DpsResult>[] ownedArr = ownedArrBy.get(style);
			List<DpsResult>[] gameArr = gameArrBy.get(style);
			List<List<DpsResult>> ownedBests = new ArrayList<>();
			List<List<DpsResult>> gameBests = new ArrayList<>();
			for (int j = 0; j < n; j++)
			{
				ownedBests.add(ownedArr[j] == null ? new ArrayList<>() : ownedArr[j]);
				gameBests.add(gameArr[j] == null ? new ArrayList<>() : gameArr[j]);
			}
			Loadout sharedOwned = chooseSharedLoadout(calc, ownedBests, ownedReqs);
			Loadout sharedGame = chooseSharedLoadout(calc, gameBests, gameReqs);
			// The spec weapon is PART of the carried set (field decision
			// 2026-07-17): with zero swaps a roster brings ONE spec weapon,
			// so the pick is shared - HP-weighted like the worn set - and
			// only its per-mob numbers flip. Swapping per mob is M-4
			// swap-budget territory.
			List<List<DpsResult>> shownOwned = new ArrayList<>();
			List<DpsResult> shownGame = new ArrayList<>();
			for (int j = 0; j < n; j++)
			{
				List<DpsResult> ownedList = new ArrayList<>();
				if (sharedOwned != null)
				{
					// calculate() returns null vs an immune mob (the TD
					// shield phases) - an honest empty list, never a null
					// element the card would trip over.
					DpsResult shown = calcRespecting(calc, ownedReqs.get(j), sharedOwned);
					if (shown != null)
					{
						ownedList.add(shown);
					}
				}
				shownOwned.add(ownedList);
				shownGame.add(sharedGame == null ? null
					: calcRespecting(calc, gameReqs.get(j), sharedGame));
			}
			List<SpecPick[]> specOptions = ctx.maxSwaps >= 1 && ctx.specWeapon
				? chooseSharedSpec(ctx, style, mobs, ownedReqs,
					shownOwned, styleLevels, ctx.effectiveOwned, false, shownGame)
				: Collections.emptyList();
			List<SpecPick[]> gameSpecOptions = ctx.maxSwaps >= 1 && ctx.specWeapon
				? chooseSharedSpec(ctx, style, mobs, gameReqs,
					shownOwned, gameLevels, null, true, shownGame)
				: Collections.emptyList();
			// ROSTER LIGHTBEARER ARBITRATION (field report 2026-08-08: a Nex
			// group recommended Ring of shadows over the Lightbearer): the
			// shared ring falls out of raw-stat set construction, which the
			// stat-less Lightbearer can never win - but doubled regen scales
			// the whole trip's spec value. Price the trip both ways (HP-
			// weighted shared-set dps + top shared-spec value) and adopt the
			// variant on a strict win - the single-mob arbitration's argmax,
			// at the shared-set altitude.
			if (ctx.maxSwaps >= 1 && ctx.specWeapon)
			{
				Loadout lbOwned = lightbearerVariant(ctx.dataset, sharedOwned,
					ownedReqs, ctx.effectiveOwned);
				if (lbOwned != null)
				{
					List<List<DpsResult>> lbShown = new ArrayList<>();
					double baseTotal = topSpecScore(specOptions, mobs);
					double lbTotal = 0;
					for (int j = 0; j < n; j++)
					{
						List<DpsResult> list = new ArrayList<>();
						DpsResult shown = calcRespecting(calc, ownedReqs.get(j), lbOwned);
						if (shown != null)
						{
							list.add(shown);
							lbTotal += shown.getDps() * Math.max(1, mobs.get(j).getHitpoints());
						}
						lbShown.add(list);
						if (!shownOwned.get(j).isEmpty())
						{
							baseTotal += shownOwned.get(j).get(0).getDps()
								* Math.max(1, mobs.get(j).getHitpoints());
						}
					}
					List<SpecPick[]> lbOptions = chooseSharedSpec(ctx, style, mobs,
						ownedReqs, lbShown, styleLevels, ctx.effectiveOwned, false, shownGame);
					lbTotal += topSpecScore(lbOptions, mobs);
					if (lbTotal > baseTotal + 1e-9)
					{
						sharedOwned = lbOwned;
						shownOwned = lbShown;
						specOptions = lbOptions;
					}
				}
				Loadout lbGame = lightbearerVariant(ctx.dataset, sharedGame, gameReqs, null);
				if (lbGame != null)
				{
					List<DpsResult> lbGameShown = new ArrayList<>();
					double baseTotal = topSpecScore(gameSpecOptions, mobs);
					double lbTotal = 0;
					for (int j = 0; j < n; j++)
					{
						DpsResult shown = calcRespecting(calc, gameReqs.get(j), lbGame);
						lbGameShown.add(shown);
						if (shown != null)
						{
							lbTotal += shown.getDps() * Math.max(1, mobs.get(j).getHitpoints());
						}
						if (shownGame.get(j) != null)
						{
							baseTotal += shownGame.get(j).getDps()
								* Math.max(1, mobs.get(j).getHitpoints());
						}
					}
					List<SpecPick[]> lbOptions = chooseSharedSpec(ctx, style, mobs,
						gameReqs, shownOwned, gameLevels, null, true, lbGameShown);
					lbTotal += topSpecScore(lbOptions, mobs);
					if (lbTotal > baseTotal + 1e-9)
					{
						sharedGame = lbGame;
						shownGame = lbGameShown;
						gameSpecOptions = lbOptions;
					}
				}
			}
			SpecPick[] specs = specOptions.isEmpty() ? new SpecPick[n] : specOptions.get(0);
			SpecPick[] gameSpecs = gameSpecOptions.isEmpty() ? new SpecPick[n] : gameSpecOptions.get(0);
			// The INVENTORY budget counts every carried item INCLUDING the
			// spec weapon (field decision 2026-07-17, reversing same-day:
			// Inventory 0 = strictly one worn set, NO spec recommended).
			// Swaps are chosen greedily by HP-weighted marginal gain; each
			// mob then WEARS its best combination of base + carried swaps.
			// The bench reserves slots for the PRESUMPTIVE (top-ranked)
			// spec; the kit pass re-prices each option it actually seats.
			GearItem[] carriedSlots = carriedSpecOf(specs, sharedOwned);
			GearItem specCarried = carriedSlots[0];
			GearItem specAmmoCarried = carriedSlots[1];
			GearItem gameSpecCarried = carriedSpecOf(gameSpecs, sharedGame)[0];
			int benchForSwaps = Math.max(0, ctx.maxSwaps
				- (specCarried != null ? 1 : 0) - (specAmmoCarried != null ? 1 : 0));
			List<GearItem> swaps = sharedOwned == null ? Collections.emptyList()
				: chooseSwaps(calc, sharedOwned, ownedReqs, mobs, ownedBests, benchForSwaps);
			specsByStyle.put(style, specs);
			gameSpecsByStyle.put(style, gameSpecs);
			specOptionsByStyle.put(style, specOptions);
			gameSpecOptionsByStyle.put(style, gameSpecOptions);
			boostLabelByStyle.put(style, boostLabel);
			gameBoostLabelByStyle.put(style, gameBoostLabel);
			if (sharedOwned != null)
			{
				reqsByStyle.put(style, ownedReqs);
				sharedByStyle.put(style, sharedOwned);
				bestsByStyle.put(style, ownedBests);
			}
			if (sharedGame != null)
			{
				gameReqsByStyle.put(style, gameReqs);
				sharedGameByStyle.put(style, sharedGame);
				gameBestsByStyle.put(style, gameBests);
			}
			if (sharedOwned != null && !swaps.isEmpty())
			{
				// Re-show each mob wearing its best base+swaps combination;
				// when the carried kit can fully assemble the mob's own
				// free best, show THAT exactly (big bench = easy problem).
				for (int j = 0; j < n; j++)
				{
					Loadout worn = bestWorn(calc, ownedReqs.get(j), sharedOwned, swaps);
					DpsResult shown = calcRespecting(calc, ownedReqs.get(j), worn);
					DpsResult covered = coverableBest(ownedBests.get(j), sharedOwned, swaps);
					if (covered != null && (shown == null || covered.getDps() > shown.getDps()))
					{
						shown = covered;
					}
					List<DpsResult> ownedList = new ArrayList<>();
					if (shown != null)
					{
						ownedList.add(shown);
					}
					shownOwned.set(j, ownedList);
				}
			}
			// The trip PLAN for this style: the union of what the mobs
			// actually wear across the roster - the inventory view shows
			// plan-minus-worn, so displaced pieces no mob uses drop out.
			LinkedHashMap<Integer, GearItem> plan = new LinkedHashMap<>();
			for (List<DpsResult> ownedList : shownOwned)
			{
				if (ownedList.isEmpty())
				{
					continue;
				}
				for (GearItem item : ownedList.get(0).getLoadout().getGear().values())
				{
					if (item != null)
					{
						plan.putIfAbsent(item.getId(), item);
					}
				}
			}
			for (int j = 0; j < n; j++)
			{
				MonsterStats mob = mobs.get(j);
				List<DpsResult> ownedList = shownOwned.get(j);
				DpsResult gameShown = shownGame.get(j);
				// The single-mob post-passes the roster shown sets never got
				// (field reports 2026-08-09): the Dizana's quiver relocation,
				// and the mandatory-recoil swap (a Zulrah roster recommended
				// sets with no recoil gear).
				if (!ownedList.isEmpty())
				{
					ownedList = new ArrayList<>(ownedList);
					ownedList.set(0, optimizer.ensureRequiredUtility(ctx.dataset, ownedReqs.get(j),
						optimizer.relocateQuiverAmmo(ctx.dataset, ownedReqs.get(j), ownedList.get(0))));
				}
				gameShown = optimizer.ensureRequiredUtility(ctx.dataset, gameReqs.get(j),
					optimizer.relocateQuiverAmmo(ctx.dataset, gameReqs.get(j), gameShown));
				SpecPick spec = specs[j];
				SpecPick gameSpec = gameSpecs[j];
				IncomingDpsCalculator.Result incoming = incomingFor(mob, sharedOwned, ctx);
				IncomingDpsCalculator.Result gameIncoming = incomingFor(mob, sharedGame, ctx);
				Loadout worn = ownedList.isEmpty() ? sharedOwned
					: ownedList.get(0).getLoadout();
				if (specAmmoCarried != null)
				{
					plan.putIfAbsent(specAmmoCarried.getId(), specAmmoCarried);
				}
				List<GearItem> bench = sharedOwned == null ? Collections.emptyList()
					: inventoryFor(plan.values(), specCarried, worn);
				List<GearItem> gameBench = gameSpecCarried == null
					? Collections.emptyList()
					: Collections.singletonList(gameSpecCarried);
				StyleResult sr = new StyleResult(ownedList, gameShown, spec, gameSpec,
					boostLabel, gameBoostLabel, incoming, gameIncoming, bench, gameBench);
				perMob.get(j).put(style, sr);
			}
		}
		// THE CROSS-STYLE KIT (field decision 2026-07-17): bench slots may
		// carry ANOTHER style's weapon, so one carried kit can answer a
		// style-immune phase (TD shields) with a genuine style switch. The
		// pass runs for BOTH sides - the Yours kit over your bank and the
		// BiS kit over the whole game, under the same inventory budget.
		long tKitStart = System.nanoTime();
		KitView ownedView = ctx.maxSwaps >= 1 && sharedByStyle.size() >= 2
			? kitPass(calc, mobs, ctx, ticket, reqsByStyle, sharedByStyle,
				bestsByStyle, specOptionsByStyle, "owned", true)
			: null;
		long tOwnedKitMs = (System.nanoTime() - tKitStart) / 1_000_000;
		long tGameKitStart = System.nanoTime();
		KitView gameView = ctx.maxSwaps >= 1 && sharedGameByStyle.size() >= 2
			? kitPass(calc, mobs, ctx, ticket, gameReqsByStyle, sharedGameByStyle,
				gameBestsByStyle, gameSpecOptionsByStyle, "BiS", false)
			: null;
		long tGameKitMs = (System.nanoTime() - tGameKitStart) / 1_000_000;
		if (requestSeq.get() != ticket)
		{
			abandonedForTest++;
			return null;
		}
		if (curveOut != null)
		{
			curveOut[0] = ownedView == null ? null : ownedView.curve;
		}
		if (ownedView != null || gameView != null)
		{
			// Merge the kit views over the phase-1 tabs: each side that has
			// a kit replaces its half of every StyleResult; a style the kit
			// cannot reach shows nothing on that side (no set you cannot
			// assemble mid-trip). The other side's fields carry over.
			for (int j = 0; j < n; j++)
			{
				for (CombatStyle s : CombatStyle.values())
				{
					StyleResult old = perMob.get(j).get(s);
					if (old == null)
					{
						continue;
					}
					List<DpsResult> ownedList = old.owned;
					SpecPick spec = specsByStyle.get(s) == null ? null : specsByStyle.get(s)[j];
					String label = old.boostLabel;
					IncomingDpsCalculator.Result incoming = old.incoming;
					List<GearItem> bench = old.bench;
					boolean ownedKitAnswers = ownedView != null && ownedView.styles.contains(s)
						&& ownedView.shownByMob.get(j).get(s) != null;
					if (ownedKitAnswers)
					{
						DpsResult result = ownedView.shownByMob.get(j).get(s);
						result = result.withPurchaseCost(
							unownedValue(result.getLoadout(), ctx.effectiveOwned));
						ownedList = new ArrayList<>();
						ownedList.add(result);
						spec = ownedView.specs != null ? ownedView.specs[j] : null;
						label = boostLabelByStyle.get(s);
						Loadout worn = result.getLoadout();
						incoming = incomingFor(mobs.get(j), worn, ctx);
						bench = inventoryFor(ownedView.plan.values(), ownedView.specCarried, worn);
					}
					else if (ownedView != null)
					{
						// The kit has no answer for this style here - keep the
						// PRE-KIT shared per-style answer instead of blanking
						// (field bug 2026-08-05: the Sire roster dashed ranged
						// and magic while every mob answered them alone). The
						// carried spec is kit-specific, so only that is
						// dropped.
						spec = null;
					}
					DpsResult gameBest = old.overallBest;
					SpecPick gameSpec = gameSpecsByStyle.get(s) == null ? null : gameSpecsByStyle.get(s)[j];
					String gameLabel = old.gameBoostLabel;
					IncomingDpsCalculator.Result gameIncoming = old.gameIncoming;
					List<GearItem> gameBench = old.gameBench;
					boolean gameKitAnswers = gameView != null && gameView.styles.contains(s)
						&& gameView.shownByMob.get(j).get(s) != null;
					if (gameKitAnswers)
					{
						gameBest = gameView.shownByMob.get(j).get(s);
						gameBest = gameBest.withPurchaseCost(
							unownedValue(gameBest.getLoadout(), ctx.effectiveOwned));
						gameSpec = gameView.specs != null ? gameView.specs[j] : null;
						gameLabel = gameBoostLabelByStyle.get(s);
						Loadout worn = gameBest.getLoadout();
						gameIncoming = incomingFor(mobs.get(j), worn, ctx);
						gameBench = inventoryFor(gameView.plan.values(), gameView.specCarried, worn);
					}
					else if (gameView != null)
					{
						// Same fallback on the BiS side: the ceiling per style
						// always shows (it is aspirational by definition); only
						// the kit-specific spec drops.
						gameSpec = null;
					}
					// The single-mob post-passes on whichever shown set won
					// (base-shared or kit-backed): quiver relocation and the
					// mandatory-recoil swap - the kit's shownByMob sets bypass
					// the earlier per-mob pass (field reports 2026-08-09).
					if (ownedList != null && !ownedList.isEmpty() && reqsByStyle.get(s) != null)
					{
						ownedList = new ArrayList<>(ownedList);
						ownedList.set(0, optimizer.ensureRequiredUtility(
							ctx.dataset, reqsByStyle.get(s).get(j),
							optimizer.relocateQuiverAmmo(
								ctx.dataset, reqsByStyle.get(s).get(j), ownedList.get(0))));
					}
					if (gameBest != null && gameReqsByStyle.get(s) != null)
					{
						gameBest = optimizer.ensureRequiredUtility(
							ctx.dataset, gameReqsByStyle.get(s).get(j),
							optimizer.relocateQuiverAmmo(
								ctx.dataset, gameReqsByStyle.get(s).get(j), gameBest));
					}
					// A side is kit-backed when it has no kit at all (nothing
					// to contradict) or the kit just answered this style.
					boolean ownedKitBacked = ownedView == null || ownedKitAnswers;
					boolean gameKitBacked = gameView == null || gameKitAnswers;
					perMob.get(j).put(s, new StyleResult(ownedList, gameBest, spec, gameSpec,
						label, gameLabel, incoming, gameIncoming, bench, gameBench,
						ownedKitBacked, gameKitBacked));
				}
			}
		}
		log.debug("roster compute: {} mobs @ inventory {} - fanout {}ms"
			+ " ({} cache hits, {} misses), owned kit {}ms, BiS kit {}ms, total {}ms",
			n, ctx.maxSwaps, tFanMs,
			optimizeHits.get() - hitsBefore, optimizeMisses.get() - missesBefore,
			tOwnedKitMs, tGameKitMs, (System.nanoTime() - tStart) / 1_000_000);
		return perMob;
	}

	/** One side's kit view, ready to merge over the phase-1 tabs. */
	private static final class KitView
	{
		final Set<CombatStyle> styles;
		final Loadout base;
		final GearItem specCarried;
		/** The kept spec picks, or null when the swaps outbid the spec. */
		final SpecPick[] specs;
		final List<Map<CombatStyle, DpsResult>> shownByMob;
		final LinkedHashMap<Integer, GearItem> plan;
		/** Nullable: the breakpoint curve (owned side only). */
		final KitCurve curve;

		KitView(Set<CombatStyle> styles, Loadout base, GearItem specCarried,
			SpecPick[] specs, List<Map<CombatStyle, DpsResult>> shownByMob,
			LinkedHashMap<Integer, GearItem> plan, KitCurve curve)
		{
			this.styles = styles;
			this.base = base;
			this.specCarried = specCarried;
			this.specs = specs;
			this.shownByMob = shownByMob;
			this.plan = plan;
			this.curve = curve;
		}
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
		ComputeContext ctx, long ticket,
		Map<CombatStyle, List<OptimizationRequest>> reqsByStyle,
		Map<CombatStyle, Loadout> sharedByStyle,
		Map<CombatStyle, List<List<DpsResult>>> bestsByStyle,
		Map<CombatStyle, List<SpecPick[]>> specOptionsByStyle,
		String side, boolean wantCurve)
	{
		int n = mobs.size();
		// SLOT UNIFICATION (field insight 2026-07-18: a tribrid answer
		// should not pay one carried slot per near-identical variant):
		// per style and slot, the roster's most-worn item absorbs any
		// per-mob variant whose dps cost is under 0.5%. The per-mob bests
		// converge onto shared pieces, the diffs shrink, and the
		// significant breakpoints arrive with far fewer items - the
		// mage/tribrid answers assemble early instead of fragmenting.
		for (Map.Entry<CombatStyle, List<List<DpsResult>>> unifyEntry : bestsByStyle.entrySet())
		{
			List<OptimizationRequest> unifyReqs = reqsByStyle.get(unifyEntry.getKey());
			for (GearSlot slot : GearSlot.values())
			{
				Map<Integer, Integer> counts = new HashMap<>();
				Map<Integer, GearItem> itemsById = new HashMap<>();
				for (List<DpsResult> best : unifyEntry.getValue())
				{
					if (emptyBest(best))
					{
						continue;
					}
					GearItem item = best.get(0).getLoadout().get(slot);
					if (item != null)
					{
						counts.merge(item.getId(), 1, Integer::sum);
						itemsById.put(item.getId(), item);
					}
				}
				if (counts.size() < 2)
				{
					continue;
				}
				int canonicalId = -1;
				int canonicalCount = -1;
				for (Map.Entry<Integer, Integer> count : counts.entrySet())
				{
					if (count.getValue() > canonicalCount)
					{
						canonicalCount = count.getValue();
						canonicalId = count.getKey();
					}
				}
				GearItem canonical = itemsById.get(canonicalId);
				for (int j = 0; j < n; j++)
				{
					List<DpsResult> best = unifyEntry.getValue().get(j);
					if (emptyBest(best))
					{
						continue;
					}
					GearItem worn = best.get(0).getLoadout().get(slot);
					if (worn == null || worn.getId() == canonicalId)
					{
						continue;
					}
					// Slot legality (field report 2026-08-15 x2, the Eclipse
					// Moon's scythe+defender: THIS was the birthplace) - a
					// 2h canonical evicts the shield, and a shield canonical
					// cannot join a 2h wearer.
					Loadout unifiedLoadout = best.get(0).getLoadout();
					GearItem unifiedWeapon = unifiedLoadout.getWeapon();
					if (slot == GearSlot.SHIELD && unifiedWeapon != null
						&& unifiedWeapon.isTwoHanded())
					{
						continue;
					}
					unifiedLoadout = withSlot(unifiedLoadout, slot, canonical);
					if (slot == GearSlot.WEAPON && canonical.isTwoHanded())
					{
						unifiedLoadout = withSlot(unifiedLoadout, GearSlot.SHIELD, null);
					}
					DpsResult unified = calcRespecting(calc, unifyReqs.get(j),
						unifiedLoadout);
					if (unified != null && unified.getDps() >= best.get(0).getDps() * 0.995)
					{
						best.set(0, unified);
					}
				}
			}
		}
		// A quiver never costs a swap slot (field fix 2026-07-17): the
		// ammo slot does nothing for melee, so every ammo-NEUTRAL answer
		// wears the ammo the mob's caring answer rolls with (the ranged
		// bolts ride in the melee set), and the shared bases follow suit.
		GearItem rosterAmmo = null;
		for (int j = 0; j < n; j++)
		{
			GearItem needed = null;
			for (CombatStyle s : sharedByStyle.keySet())
			{
				List<DpsResult> best = bestsByStyle.get(s).get(j);
				if (emptyBest(best))
				{
					continue;
				}
				DpsResult r = best.get(0);
				GearItem ammo = r.getLoadout().get(GearSlot.AMMO);
				if (ammo == null)
				{
					continue;
				}
				DpsResult without = calcRespecting(calc, reqsByStyle.get(s).get(j),
					withSlot(r.getLoadout(), GearSlot.AMMO, null));
				if (without == null || without.getDps() < r.getDps() - 1e-9)
				{
					needed = ammo;
					break;
				}
			}
			if (needed == null)
			{
				continue;
			}
			if (rosterAmmo == null)
			{
				rosterAmmo = needed;
			}
			for (CombatStyle s : sharedByStyle.keySet())
			{
				List<DpsResult> best = bestsByStyle.get(s).get(j);
				if (emptyBest(best))
				{
					continue;
				}
				DpsResult r = best.get(0);
				GearItem ammo = r.getLoadout().get(GearSlot.AMMO);
				if (ammo != null && ammo.getId() == needed.getId())
				{
					continue;
				}
				DpsResult unified = calcRespecting(calc, reqsByStyle.get(s).get(j),
					withSlot(r.getLoadout(), GearSlot.AMMO, needed));
				if (unified != null && unified.getDps() >= r.getDps() - 1e-9)
				{
					best.set(0, unified);
				}
			}
		}
		if (rosterAmmo != null)
		{
			for (Map.Entry<CombatStyle, Loadout> entry : sharedByStyle.entrySet())
			{
				Loadout b = entry.getValue();
				GearItem ammo = b.get(GearSlot.AMMO);
				if (ammo != null && ammo.getId() == rosterAmmo.getId())
				{
					continue;
				}
				Loadout unifiedBase = withSlot(b, GearSlot.AMMO, rosterAmmo);
				boolean neutral = true;
				List<OptimizationRequest> reqs = reqsByStyle.get(entry.getKey());
				for (int j = 0; j < n && neutral; j++)
				{
					DpsResult before = calcRespecting(calc, reqs.get(j), b);
					DpsResult after = calcRespecting(calc, reqs.get(j), unifiedBase);
					neutral = (after == null ? 0 : after.getDps())
						>= (before == null ? 0 : before.getDps()) - 1e-9;
				}
				if (neutral)
				{
					entry.setValue(unifiedBase);
				}
			}
		}
		// The primaries are independent once the ammo pass has settled the
		// shared state - fan them out (each task owns its calculator; the
		// kit search is the second half of the raid load-time wall).
		List<CombatStyle> primaries = new ArrayList<>(sharedByStyle.keySet());
		List<java.util.concurrent.Callable<Object[]>> primaryTasks = new ArrayList<>();
		for (CombatStyle primary : primaries)
		{
			primaryTasks.add(() ->
			{
				if (requestSeq.get() != ticket)
				{
					return null;
				}
				DpsCalculator localCalc = new DpsCalculator();
				Loadout base = sharedByStyle.get(primary);
				List<GearItem> singlePool =
					crossStylePool(base, primary, sharedByStyle, bestsByStyle);
				List<SpecPick[]> specOptions = specOptionsByStyle.getOrDefault(
					primary, Collections.emptyList());
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
					bundleCandidates(primary, base, null, sharedByStyle, bestsByStyle),
					reqsByStyle, bestsByStyle, mobs, ctx.maxSwaps, memo, null);
				KitAnswer kit = kitFree;
				SpecPick[] keptSpecs = null;
				GearItem keptCarried = null;
				GearItem keptAmmo = null;
				double score = kitFree.total;
				// A pinned spec is seated unconditionally - the player's
				// explicit choice overrides the seat economics, so it always
				// shows (even adding ~0.00, field report 2026-08-09).
				boolean specPinned = restrictSpec(ctx, primary) != null;
				for (SpecPick[] option : specOptions)
				{
					GearItem[] slots = carriedSpecOf(option, base);
					int specSlots = (slots[0] != null ? 1 : 0) + (slots[1] != null ? 1 : 0);
					KitAnswer kitSpec = specSlots == 0 ? kitFree
						: chooseKit(localCalc, base, primary, singlePool,
							bundleCandidates(primary, base, slots[0], sharedByStyle, bestsByStyle),
							reqsByStyle, bestsByStyle, mobs,
							Math.max(0, ctx.maxSwaps - specSlots), memo, null);
					double specValue = 0;
					List<GearItem> carriedSpec = carriedOf(kitSpec);
					for (int j = 0; j < n; j++)
					{
						if (option[j] == null)
						{
							continue;
						}
						DpsResult shown = kitBest(localCalc, base, primary, kitSpec.singles,
							kitSpec.bundles, carriedSpec, reqsByStyle, bestsByStyle, j);
						if (shown != null && shown.getDps() > 0)
						{
							specValue += option[j].expectedDamage * shown.getDps();
						}
					}
					if (specPinned || kitSpec.total + specValue >= kitFree.total)
					{
						kit = kitSpec;
						keptSpecs = option;
						keptCarried = slots[0];
						keptAmmo = slots[1];
						// The PRIMARY contest scores what the card SHOWS -
						// kit total plus the kept spec's honest dpsAdded
						// value - not the damage-blind seat currency (field
						// report 2026-08-09: the Muspah roster's magic
						// primary outbid a spec-kept ranged primary by 0.3%
						// of kit total while ignoring an elder maul worth
						// 20x that in drain value, and the BiS card lost
						// its spec entirely).
						double shownSpec = 0;
						for (int j = 0; j < n; j++)
						{
							if (option[j] != null)
							{
								shownSpec += option[j].dpsAdded
									* Math.max(1, mobs.get(j).getHitpoints());
							}
						}
						score = kitSpec.total + shownSpec;
						break;
					}
				}
				return new Object[]{primary, kit, keptSpecs, score, keptCarried, keptAmmo};
			});
		}
		CombatStyle bestPrimary = null;
		KitAnswer bestKit = null;
		SpecPick[] bestSpecs = null;
		GearItem bestSpecCarried = null;
		GearItem bestSpecAmmo = null;
		double bestScore = -1;
		try
		{
			for (java.util.concurrent.Future<Object[]> future : rosterPool.invokeAll(primaryTasks))
			{
				Object[] outcome;
				try
				{
					outcome = future.get();
				}
				catch (java.util.concurrent.ExecutionException e)
				{
					continue; // one primary failed - the others still answer
				}
				if (outcome == null)
				{
					continue; // superseded mid-task
				}
				double score = (Double) outcome[3];
				if (bestKit == null || score > bestScore + 1e-9)
				{
					bestPrimary = (CombatStyle) outcome[0];
					bestKit = (KitAnswer) outcome[1];
					bestSpecs = (SpecPick[]) outcome[2];
					bestSpecCarried = (GearItem) outcome[4];
					bestSpecAmmo = (GearItem) outcome[5];
					bestScore = score;
				}
			}
		}
		catch (InterruptedException e)
		{
			// Cancellation of a superseded search is cooperative, via requestSeq
			// (checked immediately below) - not thread interruption. This runs on
			// our own daemon optimizer worker, interrupted only when the executor
			// is shut down on plugin stop, so just abandon the computation.
			return null;
		}
		if (requestSeq.get() != ticket)
		{
			abandonedForTest++;
			return null;
		}
		if (bestKit == null)
		{
			return null; // unreachable with >= 2 shared styles; be safe
		}
		Loadout base = sharedByStyle.get(bestPrimary);
		GearItem specCarried = bestSpecCarried;
		GearItem specAmmo = bestSpecAmmo;
		SpecPick[] specs = bestSpecs;
		List<GearItem> carried = carriedOf(bestKit);
		// THE BREAKPOINT CURVE (field spec 2026-07-18): one more greedy to
		// exhaustion for the winning configuration - with warm machinery
		// this is cheap - recording (slots, total, viable mobs) at every
		// pick. The UI reads viability/major/final breakpoints off it.
		KitCurve curve = null;
		if (wantCurve)
		{
			List<GearItem> curvePool =
				crossStylePool(base, bestPrimary, sharedByStyle, bestsByStyle);
			List<double[]> raw = new ArrayList<>();
			int curveSpecSlots = (specCarried != null ? 1 : 0) + (specAmmo != null ? 1 : 0);
			chooseKit(calc, base, bestPrimary, curvePool,
				bundleCandidates(bestPrimary, base, specCarried, sharedByStyle, bestsByStyle),
				reqsByStyle, bestsByStyle, mobs,
				Math.max(0, 20 - curveSpecSlots),
				new HashMap<>(), raw);
			if (curveSpecSlots > 0 && !raw.isEmpty())
			{
				// The spec (and its ammo) spend the first slots: shift every
				// point past them and keep a cost-0 baseline copy in front.
				for (double[] point : raw)
				{
					point[0] += curveSpecSlots;
				}
				raw.add(0, new double[]{0, raw.get(0)[1], raw.get(0)[2]});
			}
			curve = new KitCurve(raw, n, specCarried != null);
			if (log.isDebugEnabled() && !raw.isEmpty())
			{
				StringBuilder line = new StringBuilder();
				double finalTotal = raw.get(raw.size() - 1)[1];
				for (int i = 0; i < raw.size(); i++)
				{
					double gain = i == 0 ? 0 : raw.get(i)[1] - raw.get(i - 1)[1];
					line.append((int) raw.get(i)[0]).append(':')
						.append(String.format("%.0f", raw.get(i)[1]))
						.append(i == 0 ? "" : String.format("(+%.1f%%)",
							finalTotal > 0 ? gain * 100.0 / finalTotal : 0))
						.append('v').append((int) raw.get(i)[2]).append(' ');
				}
				log.debug("curve[{}]: {}", side, line);
			}
		}
		// Every (mob, style) answer, and the trip PLAN: the union of each
		// mob's best worn set. The primary wears the base, a bundled style
		// wears its weapon into the base armor, and ANY style whose free
		// best the kit can fully assemble shows it exactly.
		List<Map<CombatStyle, DpsResult>> shownByMob = new ArrayList<>();
		LinkedHashMap<Integer, GearItem> plan = new LinkedHashMap<>();
		for (int j = 0; j < n; j++)
		{
			Map<CombatStyle, DpsResult> shown = new EnumMap<>(CombatStyle.class);
			DpsResult mobBest = null;
			for (CombatStyle s : sharedByStyle.keySet())
			{
				DpsResult result = null;
				OptimizationRequest req = reqsByStyle.get(s).get(j);
				if (s == bestPrimary)
				{
					result = calcRespecting(calc, req,
						bestWorn(calc, req, base, bestKit.singles));
				}
				for (SwapBundle bundle : bestKit.bundles)
				{
					if (bundle.style != s)
					{
						continue;
					}
					Loadout seeded = applyBundle(base, bundle);
					if (seeded == null)
					{
						continue;
					}
					DpsResult r = calcRespecting(calc, req,
						bestWorn(calc, req, seeded, bestKit.singles));
					if (r != null && (result == null || r.getDps() > result.getDps()))
					{
						result = r;
					}
				}
				DpsResult covered = coverableBest(bestsByStyle.get(s).get(j), base, carried);
				if (covered != null && (result == null || covered.getDps() > result.getDps()))
				{
					result = covered;
				}
				if (result != null)
				{
					shown.put(s, result);
					if (mobBest == null || result.getDps() > mobBest.getDps())
					{
						mobBest = result;
					}
				}
			}
			shownByMob.add(shown);
			if (mobBest != null)
			{
				for (GearItem item : mobBest.getLoadout().getGear().values())
				{
					if (item != null)
					{
						plan.putIfAbsent(item.getId(), item);
					}
				}
			}
		}
		if (specAmmo != null)
		{
			plan.putIfAbsent(specAmmo.getId(), specAmmo);
		}
		if (log.isDebugEnabled())
		{
			StringBuilder carriedNames = new StringBuilder();
			for (GearItem item : carried)
			{
				carriedNames.append(item.getNameLower()).append(", ");
			}
			log.debug("kit[{}]: primary={} specKept={} spec={} carried=[{}] score={}",
				side, bestPrimary, specs != null,
				specCarried == null ? "-" : specCarried.getNameLower(),
				carriedNames, String.format("%.0f", bestScore));
		}
		return new KitView(EnumSet.copyOf(sharedByStyle.keySet()),
			base, specCarried, specs, shownByMob, plan, curve);
	}

	private static List<GearItem> carriedOf(KitAnswer kit)
	{
		List<GearItem> carried = new ArrayList<>(kit.singles);
		for (SwapBundle bundle : kit.bundles)
		{
			for (GearItem item : bundle.items)
			{
				if (carried.stream().noneMatch(i -> i.getId() == item.getId()))
				{
					carried.add(item);
				}
			}
		}
		return carried;
	}

	/**
	 * Optimise ONE shared set per style across a roster of mobs. Single-mob
	 * rosters take the exact bestPerStyle path (wrapped as a 1-mob answer);
	 * multi-mob rosters choose the shared set by average dps and deliver a
	 * per-mob display bundle. Parameters mirror bestPerStyle (result-level
	 * params shared across the roster; exclusions/pins apply uniformly).
	 */
	/** raidBoostAssumed=false falls back to the bank's own potions even
	 * inside a raid (field spec 2026-07-18: overloads/salts are a
	 * toggle, not a promise). */
	public void bestPerStyleAcross(
		List<MonsterStats> mobs,
		PlayerLevels realLevels, PlayerLevels boostedLevels, PrayerUnlocks prayerUnlocks,
		RequirementProfile requirements, OwnedItems owned, int collectionFingerprint,
		boolean f2pOnly, boolean onSlayerTask, String spellbookLock,
		Map<CombatStyle, Set<Integer>> excludedByStyle, int maxTradeables, int riskBudgetGp,
		boolean antifirePotion, int deathCharge, boolean specWeapon, Map<CombatStyle, String> boostPicks, Map<CombatStyle, String> prayerPicks, boolean inWilderness, Set<Integer> dreamItems, int upgradeBudgetGp,
		int maxSwaps,
		Map<Integer, Map<CombatStyle, Set<Integer>>> excludedByMob,
		Map<Integer, Set<Integer>> dreamsByMob, boolean raidBoostAssumed,
		Map<CombatStyle, Map<GearSlot, Integer>> pinnedByStyle,
		SpellStats pinnedSpell, int pinnedSpec, Set<Integer> protectOnlyItems,
		Consumer<RosterResult> callback)
	{
		if (mobs == null || mobs.isEmpty())
		{
			return;
		}
		if (mobs.size() == 1)
		{
			// The single-mob fast path merges the mob's own exclusions into
			// the style map - one request, same semantics. (EnumMap's copy
			// constructor rejects an empty plain map - build then putAll.)
			Map<CombatStyle, Set<Integer>> merged = new EnumMap<>(CombatStyle.class);
			if (excludedByStyle != null)
			{
				merged.putAll(excludedByStyle);
			}
			Map<CombatStyle, Set<Integer>> perMobExcl = excludedByMob == null ? null
				: excludedByMob.get(mobs.get(0).profileId());
			if (perMobExcl != null)
			{
				for (Map.Entry<CombatStyle, Set<Integer>> e : perMobExcl.entrySet())
				{
					Set<Integer> union = new HashSet<>(
						merged.getOrDefault(e.getKey(), Collections.emptySet()));
					union.addAll(e.getValue());
					merged.put(e.getKey(), union);
				}
			}
			Set<Integer> mergedDreams = dreamItems == null
				? new HashSet<>() : new HashSet<>(dreamItems);
			if (dreamsByMob != null)
			{
				Set<Integer> localSims = dreamsByMob.get(mobs.get(0).profileId());
				if (localSims != null)
				{
					mergedDreams.addAll(localSims);
				}
			}
			bestPerStyle(mobs.get(0), realLevels, boostedLevels, prayerUnlocks, requirements,
				owned, collectionFingerprint, f2pOnly, onSlayerTask, spellbookLock, merged,
				maxTradeables, riskBudgetGp, antifirePotion, deathCharge, specWeapon, boostPicks, prayerPicks, inWilderness, mergedDreams, upgradeBudgetGp,
				maxSwaps, raidBoostAssumed, pinnedByStyle, pinnedSpell, pinnedSpec, protectOnlyItems,
				map -> callback.accept(new RosterResult(mobs, Collections.singletonList(map))));
			return;
		}
		final ComputeContext ctx = buildContext(realLevels, boostedLevels, prayerUnlocks,
			requirements, owned, collectionFingerprint, f2pOnly, onSlayerTask, spellbookLock,
			excludedByStyle, maxTradeables, riskBudgetGp, antifirePotion, deathCharge, specWeapon, boostPicks, prayerPicks, inWilderness,
			dreamItems, upgradeBudgetGp, maxSwaps, pinnedByStyle, pinnedSpell, pinnedSpec, protectOnlyItems);
		ctx.raidBoostAssumed = raidBoostAssumed;
		ctx.excludedByMob = excludedByMob == null ? Collections.emptyMap() : excludedByMob;
		ctx.dreamsByMob = dreamsByMob == null ? Collections.emptyMap() : dreamsByMob;
		final List<MonsterStats> roster = new ArrayList<>(mobs);
		final long ticket = requestSeq.incrementAndGet();
		worker.execute(() ->
		{
			if (requestSeq.get() != ticket)
			{
				abandonedForTest++;
				return;
			}
			KitCurve[] curveOut = new KitCurve[1];
			List<Map<CombatStyle, StyleResult>> perMob = computeStyleAcross(roster, ctx, ticket, curveOut);
			if (perMob == null || requestSeq.get() != ticket)
			{
				abandonedForTest++;
				return;
			}
			callback.accept(new RosterResult(roster, perMob, curveOut[0]));
		});
	}

	/** The per-style cache key: the query key minus per-style state, plus
	 * this style and the pins/exclusions/pinned-spell that shape ITS answer. */
	private static String styleKey(String baseKey, CombatStyle style,
		Map<CombatStyle, Map<GearSlot, Integer>> pins,
		Map<CombatStyle, Set<Integer>> excluded,
		SpellStats pinnedSpell)
	{
		return baseKey + "|" + style.name()
			+ "|" + pins.getOrDefault(style, Collections.emptyMap()).hashCode()
			+ "|" + excluded.getOrDefault(style, Collections.emptySet()).hashCode()
			+ "|" + (style == CombatStyle.MAGIC && pinnedSpell != null ? pinnedSpell.getName() : "");
	}

	private static String joinAssumes(String prayer, String boost)
	{
		if (prayer != null && !prayer.isEmpty() && boost != null)
		{
			return prayer + " + " + boost;
		}
		if (prayer != null && !prayer.isEmpty())
		{
			return prayer;
		}
		return boost;
	}

	/** Package-private: SpecPoisonTest pins the spec tie-break. */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	static final class SpecPick
	{
		final SpecialAttack spec;
		final GearItem weapon;
		final double expectedDamage;
		/** The DPS this spec ADDS to the kill over just attacking - the
		 * win-over-replacement value the card shows and the ranking key. */
		final double dpsAdded;
		/** Non-null when the spec needs its own ammo carried (a dark bow
		 * next to a chargebow base needs arrows - an extra slot). */
		final GearItem ammo;
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
	private List<SpecPick[]> chooseSharedSpec(ComputeContext ctx, CombatStyle style,
		List<MonsterStats> mobs, List<OptimizationRequest> reqs,
		List<List<DpsResult>> shownOwned, PlayerLevels levels, OwnedItems owned,
		boolean game, List<DpsResult> shownGame)
	{
		int n = mobs.size();
		LinkedHashSet<Integer> candidates = new LinkedHashSet<>();
		// A pinned spec weapon (serving this style) is the ONLY candidate:
		// the player's explicit choice, priced across the trip like any
		// other but never contested by the free picks.
		Set<Integer> specPin = restrictSpec(ctx, style);
		if (specPin != null)
		{
			candidates.addAll(specPin);
		}
		else
		{
			for (int j = 0; j < n; j++)
			{
				List<DpsResult> base = baseFor(game, shownOwned.get(j), shownGame.get(j));
				if (base == null)
				{
					continue;
				}
				SpecPick free = bestSpec(ctx.dataset, reqs.get(j), base, style,
					mobs.get(j), levels, owned, null);
				if (free != null && free.weapon != null)
				{
					candidates.add(free.weapon.getId());
				}
			}
		}
		if (candidates.isEmpty())
		{
			return Collections.emptyList();
		}
		List<double[]> scores = new ArrayList<>();
		List<SpecPick[]> perCandidate = new ArrayList<>();
		for (Integer id : candidates)
		{
			SpecPick[] perMob = new SpecPick[n];
			double score = 0;
			for (int j = 0; j < n; j++)
			{
				List<DpsResult> base = baseFor(game, shownOwned.get(j), shownGame.get(j));
				if (base == null)
				{
					continue;
				}
				perMob[j] = bestSpec(ctx.dataset, reqs.get(j), base, style,
					mobs.get(j), levels, owned, Collections.singleton(id), specPin != null);
				if (perMob[j] != null)
				{
					score += perMob[j].dpsAdded * Math.max(1, mobs.get(j).getHitpoints());
				}
			}
			// A pinned weapon is the player's explicit choice - it stays an
			// option even at zero trip value, so the card can show it (with
			// an honest ~0.00) instead of silently dropping it.
			if (score > 1e-9 || specPin != null)
			{
				scores.add(new double[]{score, perCandidate.size()});
				perCandidate.add(perMob);
			}
		}
		scores.sort((a, b) -> Double.compare(b[0], a[0]));
		List<SpecPick[]> ranked = new ArrayList<>(scores.size());
		for (double[] row : scores)
		{
			ranked.add(perCandidate.get((int) row[1]));
		}
		return ranked;
	}

	private static List<DpsResult> baseFor(boolean game, List<DpsResult> owned, DpsResult gameShown)
	{
		if (game)
		{
			return gameShown == null ? null
				: new ArrayList<>(Collections.singletonList(gameShown));
		}
		return emptyBest(owned) ? null : new ArrayList<>(owned);
	}

	/** The shared set with a Lightbearer in the ring slot, or null when the
	 * roster arbitration cannot apply: no shared set, already a Lightbearer,
	 * a ring pinned or a risk budget on any mob (the shared risk package is
	 * not re-priced here - conservative skip), or the ring unavailable
	 * (unowned and unsimmed, excluded, requirements). owned == null is the
	 * game-best side: everything standard is available. */
	private static Loadout lightbearerVariant(LoadoutData dataset, Loadout shared,
		List<OptimizationRequest> reqs, OwnedItems owned)
	{
		if (shared == null)
		{
			return null;
		}
		for (OptimizationRequest req : reqs)
		{
			if (req.pinnedFor(GearSlot.RING) != null || req.isRiskConstrained())
			{
				return null;
			}
		}
		return lightbearerSwap(dataset, shared, reqs, owned);
	}

	/** The shared Lightbearer machinery: find the ring, prove it available
	 * under every request (excluded/requirements/owned-or-dreamed), and wear
	 * it into the set - null when the swap cannot apply. Callers layer their
	 * own pin/risk policy on top (the roster skips risk conservatively; the
	 * single-mob arbitration prices it). */
	private static Loadout lightbearerSwap(LoadoutData dataset, Loadout shared,
		List<OptimizationRequest> reqs, OwnedItems owned)
	{
		GearItem worn = shared.get(GearSlot.RING);
		if (worn != null && worn.getNameLower().contains("lightbearer"))
		{
			return null;
		}
		GearItem lightbearer = null;
		for (GearItem item : dataset.getGearItems(GearSlot.RING))
		{
			if (item.getNameLower().equals("lightbearer") && item.isStandardGear())
			{
				lightbearer = item;
				break;
			}
		}
		if (lightbearer == null)
		{
			return null;
		}
		boolean dreamed = false;
		for (OptimizationRequest req : reqs)
		{
			if (req.isExcluded(lightbearer.getId())
				|| !req.getRequirementProfile().canEquip(lightbearer.getRequirements()))
			{
				return null;
			}
			dreamed |= req.isDream(lightbearer.getId());
		}
		if (owned != null && !owned.owns(lightbearer.getId()) && !dreamed)
		{
			return null;
		}
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(shared.getGear());
		gear.put(GearSlot.RING, lightbearer);
		Loadout variant = Loadout.adopting(gear);
		return shared.getQuiverAmmo() != null
			? variant.withQuiverAmmo(shared.getQuiverAmmo()) : variant;
	}

	/** HP-weighted trip value of the top-ranked shared-spec option. */
	private static double topSpecScore(List<SpecPick[]> options, List<MonsterStats> mobs)
	{
		if (options.isEmpty())
		{
			return 0;
		}
		double score = 0;
		SpecPick[] top = options.get(0);
		for (int j = 0; j < mobs.size(); j++)
		{
			if (top[j] != null)
			{
				score += top[j].dpsAdded * Math.max(1, mobs.get(j).getHitpoints());
			}
		}
		return score;
	}

	/** The carried slots a shared-spec option costs: [weapon, ammo], both
	 * null when the base already wears the spec weapon (it rides free). A
	 * dark bow next to a chargebow base needs its arrows carried too - the
	 * spec spends TWO slots when the base quiver cannot feed it (field
	 * spec 2026-07-18). */
	private static GearItem[] carriedSpecOf(SpecPick[] specs, Loadout shared)
	{
		for (SpecPick pick : specs)
		{
			if (pick != null && pick.weapon != null && shared != null
				&& (shared.getWeapon() == null
					|| shared.getWeapon().getId() != pick.weapon.getId()))
			{
				return new GearItem[]{pick.weapon, pick.ammo};
			}
		}
		return new GearItem[]{null, null};
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
		CombatStyle style,
		MonsterStats monster,
		PlayerLevels levels,
		OwnedItems owned,
		Set<Integer> restrictTo)
	{
		SpecPick spec = bestSpec(dataset, request, results, style, monster, levels, owned, restrictTo, restrictTo != null);
		if (spec == null || results.isEmpty())
		{
			return spec; // no spec value in play - dps ring stands
		}
		DpsResult base = results.get(0);
		if (request.pinnedFor(GearSlot.RING) != null)
		{
			return spec;
		}
		Loadout variantLoadout = lightbearerSwap(dataset, base.getLoadout(),
			Collections.singletonList(request), owned);
		if (variantLoadout == null)
		{
			return spec;
		}
		if (request.isRiskConstrained()
			&& (PvpRisk.riskGp(variantLoadout, null, request.getMaxTradeables())
					> request.getRiskBudgetGp() + LoadoutOptimizer.pinnedRiskFloor(dataset, request)
				|| PvpRisk.risksUnprotected(variantLoadout, null, request.getMaxTradeables(),
					request.getPinnedItems().isEmpty() ? Collections.emptySet()
						: new HashSet<>(request.getPinnedItems().values()),
					request.getProtectOnlyItems())))
		{
			return spec;
		}
		DpsResult variant = new DpsCalculator().calculate(request, variantLoadout);
		if (variant == null)
		{
			return spec;
		}
		List<DpsResult> variantResults = new ArrayList<>(results);
		variantResults.set(0, variant);
		SpecPick variantSpec = bestSpec(dataset, request, variantResults, style, monster, levels, owned, restrictTo, restrictTo != null);
		if (variantSpec == null)
		{
			return spec;
		}
		double baseTotal = base.getDps() + spec.dpsAdded;
		double variantTotal = variant.getDps() + variantSpec.dpsAdded;
		if (variantTotal > baseTotal + 1e-9)
		{
			results.set(0, variant);
			return variantSpec;
		}
		return spec;
	}

	SpecPick bestSpec(
		LoadoutData dataset,
		OptimizationRequest request,
		List<DpsResult> baseResults,
		CombatStyle style,
		MonsterStats monster,
		PlayerLevels levels,
		OwnedItems owned,
		Set<Integer> restrictTo)
	{
		return bestSpec(dataset, request, baseResults, style, monster, levels, owned, restrictTo, false);
	}

	/** keepZero: keep a restricted (user-PINNED) weapon even when it adds
	 * ~0 dps, so the pin always displays with an honest number instead of
	 * silently vanishing (field report 2026-08-09: a pinned Tonalztics
	 * showed nothing on a set it did not help). */
	SpecPick bestSpec(
		LoadoutData dataset,
		OptimizationRequest request,
		List<DpsResult> baseResults,
		CombatStyle style,
		MonsterStats monster,
		PlayerLevels levels,
		OwnedItems owned,
		Set<Integer> restrictTo,
		boolean keepZero)
	{
		if (baseResults == null || baseResults.isEmpty())
		{
			return null;
		}
		DpsCalculator calculator = new DpsCalculator();
		SpecPick best = null;
		// Request-level risk constants, hoisted out of the weapon scan (they
		// were rebuilt - floor Loadout + a HashSet - per candidate weapon).
		long riskCapGp = 0;
		Set<Integer> pinnedIds = Collections.emptySet();
		if (request.isRiskConstrained())
		{
			riskCapGp = request.getRiskBudgetGp()
				+ LoadoutOptimizer.pinnedRiskFloor(dataset, request);
			pinnedIds = request.getPinnedItems().isEmpty()
				? Collections.emptySet()
				: new HashSet<>(request.getPinnedItems().values());
		}
		// A Lightbearer in the set doubles special-energy regen (10%/15s vs
		// 10%/30s), so more specs fit the kill - detected by ring name, no id.
		GearItem ring = baseResults.get(0).getLoadout().get(GearSlot.RING);
		boolean lightbearer = ring != null && ring.getNameLower().contains("lightbearer");
		// Spec weapons are weapons by definition (SpecialAttack.match rejects
		// every other slot), so only the weapon partition needs scanning.
		for (GearItem item : dataset.getGearItems(GearSlot.WEAPON))
		{
			if (restrictTo != null && !restrictTo.contains(item.getId()))
			{
				continue;
			}
			if (!item.isStandardGear() || dataset.isVariant(item.getId())
				|| request.isExcluded(item.getId())
				// Sims are pretend-owned EVERYWHERE, including here: the
				// worn-set machinery honoured dreams but the spec pool
				// checked raw ownership only, so a simmed spec weapon
				// could never compete (field report 2026-08-06: simmed
				// Burning claws vs Barrows still recommended the owned
				// abyssal dagger).
				|| (owned != null && !owned.owns(item.getId())
					&& !request.isDream(item.getId())))
			{
				continue;
			}
			// ANY style's spec competes (field request: the spec swap is its
			// own weapon switch - magic set + chally is a real play).
			SpecialAttack spec = SpecialAttack.match(item);
			if (spec == null || !request.getRequirementProfile().canEquip(item.getRequirements()))
			{
				continue;
			}
			// In a wilderness low-risk set the carried spec weapon competes
			// for kept slots like everything else - the whole package (worn
			// set + this weapon) must stay within the total risk budget.
			if (request.isRiskConstrained()
				&& (PvpRisk.riskGp(baseResults.get(0).getLoadout(), item,
						request.getMaxTradeables()) > riskCapGp
					|| PvpRisk.risksUnprotected(baseResults.get(0).getLoadout(), item,
						request.getMaxTradeables(), pinnedIds, request.getProtectOnlyItems())))
			{
				continue;
			}
			GearItem[] ammoOut = new GearItem[1];
			Loadout loadout = specLoadout(dataset, baseResults.get(0).getLoadout(), item, owned, request, ammoOut);
			if (loadout == null)
			{
				continue;
			}
			// A cross-style spec rolls under ITS OWN style's math - the
			// assume-best-prayer model carries all three books' factors,
			// matching the real play of flicking (e.g.) Piety for the spec.
			OptimizationRequest baseRequest = spec.getStyle() == style
				? request : request.withStyle(spec.getStyle());
			DpsResult base = calculator.calculate(baseRequest, loadout);
			if (base == null || base.getMaxHit() <= 0)
			{
				continue;
			}
			double expected = spec.expectedDamage(base, monster, levels);
			// Rank by the DPS the spec ADDS over just attacking - marginal
			// (every spec fired gives up a main-hand hit), regen-aware over the
			// fight, and drain-inclusive for every style (field direction
			// 2026-07-19). A spec that adds nothing loses to carrying no slot.
			double added = specDpsAdded(calculator, spec, base, expected,
				request, baseResults.get(0), monster, lightbearer);
			if (added <= 1e-4 && !keepZero)
			{
				continue;
			}
			double bestAdded = best == null ? Double.NEGATIVE_INFINITY : best.dpsAdded;
			// Ties (identical stats across poison tiers) prefer the higher
			// tier - the venom is free spec damage the model does not price.
			if (best == null || added > bestAdded + 1e-9
				|| (added > bestAdded - 1e-9 && item.poisonTier() > best.weapon.poisonTier()))
			{
				best = new SpecPick(spec, item, expected, added, ammoOut[0]);
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
		int deathChargeLevel)
	{
		double regenPerSec = lightbearer ? 10.0 / 15.0 : 10.0 / 30.0;
		double window = deathChargeLevel >= 2 ? 30.0 : 60.0;
		return regenPerSec * ttkSeconds
			+ (deathChargeLevel > 0
				? 15.0 * ttkSeconds / Math.max(window, ttkSeconds) : 0.0);
	}

	double specDpsAdded(
		DpsCalculator calculator,
		SpecialAttack spec,
		DpsResult specBase,
		double expected,
		OptimizationRequest request,
		DpsResult mainResult,
		MonsterStats monster,
		boolean lightbearer)
	{
		double mainDps = mainResult.getDps();
		if (mainDps <= 0.01)
		{
			return 0;
		}
		double ttkSeconds = Math.min(600, Math.max(0.6, monster.getHitpoints() / mainDps));
		double replacedAuto = mainResult.getExpectedHit();
		double specCycle = Math.max(0.6, specBase.getAttackSpeed() * 0.6);

		// How many specs fire per kill at SUSTAINED pace: the regen-bound
		// budget, fractional across kills (0.5 = a spec every other kill),
		// but never more than the fight has time for - so a mob that dies
		// in one hit gets no spec value, and a long fight fits more. The
		// burst frame's floor() was an artifact of pricing one kill from a
		// full bar; sustained throughput is a rate, not a count.
		double energyOverKill = specEnergyOverKill(ttkSeconds, lightbearer,
			request.getDeathCharge());
		double usesByEnergy = energyOverKill / Math.max(1, spec.getEnergyCost());
		double usesByTime = Math.floor(ttkSeconds / specCycle);
		double uses = Math.max(0, Math.min(usesByEnergy, usesByTime));

		// DAMAGE use: each spec fired wins its damage over the main-hand hit it
		// replaces, amortised across the kill.
		double marginal = Math.max(0, expected - replacedAuto);
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
		if (spec.drainsDefence() && usesByTime >= 1)
		{
			int drained = spec.drainedDefence(monster, expected);
			// Per-boss defence floors (competitor audit 2026-08-08, from
			// the official calc): drain stops at the floor - a DWH at Nex
			// buys 10 levels, at Verzik/Vardorvis nothing at all. Without
			// the clamp the fishing model overvalues drain specs there.
			drained = Math.max(drained, DefenceFloors.floorFor(monster));
			if (drained < monster.getDefence())
			{
				DpsResult after = calculator.calculate(
					request.withMonster(monster.withDefence(drained)), mainResult.getLoadout());
				if (after != null && after.getDps() > mainDps)
				{
					double p = Math.max(0.01, spec.landChance(specBase));
					// Fractional attempts are the sustained regime: 0.5
					// attempts per kill = fishing lands on half the kills
					// it is tried across.
					double attempts = Math.min(uses, Math.ceil(1.0 / p));
					double landed = 1 - Math.pow(1 - p, attempts);
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
		return Math.max(damageDps, drainDps);
	}

	/** The base set with the spec weapon swapped in, or null if unusable.
	 * owned == null -> any standard ammo may be picked (game-best spec). */
	private Loadout specLoadout(LoadoutData dataset, Loadout baseSet, GearItem weapon, OwnedItems owned, OptimizationRequest request,
		GearItem[] ammoOut)
	{
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		gear.putAll(baseSet.getGear());
		gear.put(GearSlot.WEAPON, weapon);
		if (weapon.isTwoHanded())
		{
			gear.remove(GearSlot.SHIELD);
		}
		if (!RangedAmmo.compatible(gear.get(GearSlot.AMMO), weapon))
		{
			GearItem replacement = null;
			for (GearItem ammo : dataset.getGearItems(GearSlot.AMMO))
			{
				if (ammo.isStandardGear() && !dataset.isVariant(ammo.getId())
					&& !request.isExcluded(ammo.getId())
					&& (owned == null || owned.owns(ammo.getId()))
					&& RangedAmmo.compatible(ammo, weapon)
					&& (replacement == null
						|| ammo.getBonuses().getRangedStrength() > replacement.getBonuses().getRangedStrength()))
				{
					replacement = ammo;
				}
			}
			if (replacement == null && !RangedAmmo.compatible(null, weapon))
			{
				return null; // needs ammo that is not available
			}
			if (replacement != null)
			{
				gear.put(GearSlot.AMMO, replacement);
				if (ammoOut != null)
				{
					ammoOut[0] = replacement;
				}
			}
			else
			{
				gear.remove(GearSlot.AMMO);
			}
		}
		return new Loadout(gear);
	}

	/** The per-style assumption bundle: your levels/boost/prayer label and
	 * the game-ceiling versions. Shared by the single-mob and roster paths
	 * (they MUST agree - levels and labels depend on style + owned + real,
	 * never the mob). */
	@AllArgsConstructor(access = AccessLevel.PACKAGE)
	private static final class StylePlan
	{
		final PlayerLevels levels;
		final String label;
		final PlayerLevels gameLevels;
		final String gameLabel;
	}

	/** Decorate a request with the user's prayer pick: NONE = prayerless,
	 * a named tier = that tier's factors for this style (detect otherwise). */
	private static OptimizationRequest withPrayerPick(OptimizationRequest r,
		ComputeContext ctx, CombatStyle style, PlayerLevels levels, PrayerUnlocks unlocks)
	{
		String pick = ctx.prayerPicks.get(style);
		if (pick == null)
		{
			return r;
		}
		return r.withPrayers("NONE".equals(pick) ? PrayerBonuses.NONE
			: PrayerBonuses.forPick(style, pick, PrayerBonuses.bestAvailable(levels, unlocks)));
	}

	/** The user's boost pick for this style (assume-chip picker), or null
	 * for detect; "NONE" maps to the unboosted profile. */
	private static BoostProfile boostPickFor(ComputeContext ctx, CombatStyle style)
	{
		String pick = ctx.boostPicks.get(style);
		if (pick == null)
		{
			return null;
		}
		try
		{
			return BoostProfile.valueOf(pick);
		}
		catch (IllegalArgumentException bad)
		{
			return null;
		}
	}

	/** The assumed-prayer label part: the user's pick (null part when
	 * prayerless), or detect's best-available tier. */
	private static String prayerLabelFor(ComputeContext ctx, CombatStyle style,
		PlayerLevels levels, PrayerUnlocks unlocks)
	{
		String pick = ctx.prayerPicks.get(style);
		if (pick == null)
		{
			return PrayerBonuses.bestAvailable(levels, unlocks).nameFor(style);
		}
		return "NONE".equals(pick) ? null : pick;
	}

	private static StylePlan stylePlan(ComputeContext ctx, CombatStyle style, BoostProfile supplied)
	{
		BoostProfile userBoost = boostPickFor(ctx, style);
		BoostProfile boost = userBoost != null ? userBoost
			: supplied != null ? supplied
			: BoostSelector.bestFor(style, ctx.effectiveOwned, ctx.f2pOnly,
				ctx.inWilderness && ctx.maxTradeables >= 0);
		PlayerLevels levels = ctx.real.boosted(boost, ctx.boostedLevels).max(ctx.boostedLevels);
		String label = joinAssumes(prayerLabelFor(ctx, style, levels, ctx.unlocks),
			boost == BoostProfile.NONE ? null : boost.toString());
		// The ceiling assumes the best prayers/boost in the GAME, not just
		// what this player has unlocked or owns.
		BoostProfile gameBoost = userBoost != null ? userBoost
			: supplied != null ? supplied
			: BoostSelector.ceilingFor(style, ctx.f2pOnly);
		PlayerLevels gameLevels = ctx.real.boosted(gameBoost, ctx.boostedLevels).max(ctx.boostedLevels);
		String gameLabel = joinAssumes(prayerLabelFor(ctx, style, gameLevels,
			ctx.f2pOnly ? PrayerUnlocks.F2P : PrayerUnlocks.ALL),
			gameBoost == BoostProfile.NONE ? null : gameBoost.toString());
		return new StylePlan(levels, label, gameLevels, gameLabel);
	}

	/** The best-OWNED request for one mob at one style - shared verbatim by
	 * the single-mob and roster paths (they MUST agree). Pins shape YOUR
	 * set only; a pinned autocast spell forces the magic search around it. */
	private OptimizationRequest ownedRequestFor(ComputeContext ctx, MonsterStats mob,
		CombatStyle style, PlayerLevels styleLevels)
	{
		OptimizationRequest r = request(
			mob, style, styleLevels, ctx.unlocks, ctx.requirements,
			ctx.upgradeBudgetGp > 0 ? CandidateMode.OWNED_OR_BUDGET : CandidateMode.OWNED_ONLY,
			ctx.effectiveOwned, 3, ctx.onSlayerTask, Math.max(0, ctx.upgradeBudgetGp))
			.withExcludedItems(excludedFor(ctx, style, mob))
			.withSpellbookLock(ctx.lock)
			.withMaxTradeables(ctx.maxTradeables).withRiskBudgetGp(ctx.riskBudget)
			.withAntifirePotion(ctx.antifirePotion)
			.withDeathCharge(ctx.deathCharge)
			.withInWilderness(ctx.inWilderness)
			.withDreamItems(dreamsFor(ctx, mob))
			.withProtectOnlyItems(ctx.protectOnly)
			.withPinnedItems(ctx.pins.getOrDefault(style, Collections.emptyMap()));
		r = withPrayerPick(r, ctx, style, styleLevels, ctx.unlocks);
		if (style == CombatStyle.MAGIC && ctx.pinnedSpell != null)
		{
			r = r.withSpell(ctx.pinnedSpell);
		}
		return r;
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
		CombatStyle style, PlayerLevels gameLevels)
	{
		OptimizationRequest r = request(
			mob, style, gameLevels, PrayerUnlocks.ALL, ctx.requirements,
			CandidateMode.ALL_STANDARD, ctx.effectiveOwned, 1, ctx.onSlayerTask, 0)
			.withExcludedItems(excludedFor(ctx, style, mob))
			.withSpellbookLock(ctx.lock)
			.withMaxTradeables(ctx.maxTradeables).withRiskBudgetGp(ctx.riskBudget)
			.withAntifirePotion(ctx.antifirePotion)
			.withDeathCharge(ctx.deathCharge)
			.withInWilderness(ctx.inWilderness)
			.withProtectOnlyItems(ctx.protectOnly);
		return withPrayerPick(r, ctx, style, gameLevels,
			ctx.f2pOnly ? PrayerUnlocks.F2P : PrayerUnlocks.ALL);
	}

	private OptimizationRequest request(
		MonsterStats monster,
		CombatStyle style,
		PlayerLevels levels,
		PrayerUnlocks unlocks,
		RequirementProfile requirements,
		CandidateMode mode,
		OwnedItems owned,
		int limit,
		boolean onSlayerTask,
		int budgetGp)
	{
		return new OptimizationRequest(
			monster,
			style,
			levels,
			PrayerBonuses.bestAvailable(levels, unlocks),
			null,          // auto-pick the spell for magic
			budgetGp,      // OWNED_OR_BUDGET: total upgrade spend allowed
			mode,
			true,          // untradeables count
			onSlayerTask,
			owned,
			requirements,
			limit);
	}

	private LoadoutData f2pView()
	{
		LoadoutData view = f2pData;
		if (view == null)
		{
			view = data.freeToPlayView();
			f2pData = view;
		}
		return view;
	}

	private static String levelKey(PlayerLevels l)
	{
		// Levels participate in the key so a boost/level-up invalidates.
		return l.getAttack() + "." + l.getStrength() + "." + l.getDefence() + "."
			+ l.getRanged() + "." + l.getMagic() + "." + l.getPrayer() + "." + l.getHitpoints();
	}

	/** Drop all cached results - account or profile switched. */
	public void clearCache()
	{
		synchronized (cache)
		{
			cache.clear();
		}
	}

	public void shutdown()
	{
		worker.shutdownNow();
		rosterPool.shutdownNow();
		synchronized (cache)
		{
			cache.clear();
		}
		synchronized (optimizeCache)
		{
			optimizeCache.clear();
		}
	}
}
