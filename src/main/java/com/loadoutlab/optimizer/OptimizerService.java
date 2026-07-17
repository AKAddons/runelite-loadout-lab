package com.loadoutlab.optimizer;

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
	/** Per-STYLE entries (a monster query stores three). */
	private static final int CACHE_MAX = 192;

	/** D-4: which point of the offense/defense frontier to recommend. */
	public enum OptimizeMode
	{
		MAX_DPS,
		BALANCED,
		TANKY
	}

	/** Frontier sweep weights, as multiples of maxDps/incoming. */
	/** Frontier sweep weights (x maxDps/incoming); the 10.0 extreme lets
	 * Tanky reach the genuine minimum-intake end of the frontier. */
	private static final double[] SWEEP_ALPHAS = {0.3, 0.7, 1.5, 3.0, 10.0};

	/**
	 * Balanced's objective slightly favors dps out over dps in:
	 * score = dpsOut^(1+BIAS) / dpsIn. At BIAS 0 it is the plain ratio;
	 * 0.2 means a 10% dps gain outweighs a ~12% intake increase.
	 */
	static final double BALANCED_DPS_BIAS = 0.2;

	/** The frontier trade a non-max mode made: dps given up vs damage cut,
	 * both as whole percents relative to the max-dps set. */
	public static final class ModeTrade
	{
		public final int dpsLossPct;
		public final int dmgCutPct;

		ModeTrade(int dpsLossPct, int dmgCutPct)
		{
			this.dpsLossPct = dpsLossPct;
			this.dmgCutPct = dmgCutPct;
		}
	}

	/** Per-style outcome: your best owned sets, the game-wide best set, and
	 * the strongest special-attack weapon for each - owned and game-wide. */
	public static final class StyleResult
	{
		public final List<DpsResult> owned;
		public final DpsResult overallBest;
		public final SpecialAttack spec;
		public final GearItem specWeapon;
		public final double specExpectedDamage;
		public final double specDrainValue;
		public final SpecialAttack gameSpec;
		public final GearItem gameSpecWeapon;
		public final double gameSpecExpectedDamage;
		public final double gameSpecDrainValue;
		/** The prayers/boost YOUR numbers assume ("Deadeye + Ranging potion"). */
		public final String boostLabel;
		/** The ceiling assumption for game best ("Rigour + Ranging potion"). */
		public final String gameBoostLabel;
		/** What the boss does back to you in the shown owned set (nullable). */
		public final IncomingDpsCalculator.Result incoming;
		/** Same, in the game-best set - the BiS side's DTPS (nullable). */
		public final IncomingDpsCalculator.Result gameIncoming;
		/** The frontier trade the chosen mode made: dps given up vs damage
		 * cut, as whole percents (null on max dps / when no better trade). */
		public final ModeTrade modeTrade;

		StyleResult(List<DpsResult> owned, DpsResult overallBest,
			SpecPick spec, SpecPick gameSpec, String boostLabel, String gameBoostLabel,
			IncomingDpsCalculator.Result incoming, IncomingDpsCalculator.Result gameIncoming,
			ModeTrade modeTrade)
		{
			this.boostLabel = boostLabel;
			this.gameBoostLabel = gameBoostLabel;
			this.incoming = incoming;
			this.gameIncoming = gameIncoming;
			this.modeTrade = modeTrade;
			this.owned = owned;
			this.overallBest = overallBest;
			this.spec = spec == null ? null : spec.spec;
			this.specWeapon = spec == null ? null : spec.weapon;
			this.specExpectedDamage = spec == null ? 0 : spec.expectedDamage;
			this.specDrainValue = spec == null ? 0 : spec.drainValue;
			this.gameSpec = gameSpec == null ? null : gameSpec.spec;
			this.gameSpecWeapon = gameSpec == null ? null : gameSpec.weapon;
			this.gameSpecExpectedDamage = gameSpec == null ? 0 : gameSpec.expectedDamage;
			this.gameSpecDrainValue = gameSpec == null ? 0 : gameSpec.drainValue;
		}
	}

	private final LoadoutData data;
	private final LoadoutOptimizer optimizer = new LoadoutOptimizer();
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
	/** Back-compat overload (headless/tests): no pinned items. */
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
		Set<Integer> excludedItems,
		int maxTradeables,
		int riskBudgetGp,
		boolean antifirePotion,
		Set<Integer> dreamItems,
		int upgradeBudgetGp,
		OptimizeMode mode,
		Consumer<Map<CombatStyle, StyleResult>> callback)
	{
		bestPerStyle(monster, realLevels, boostedLevels, prayerUnlocks, requirements,
			owned, collectionFingerprint, f2pOnly, onSlayerTask, spellbookLock,
			excludedItems, maxTradeables, riskBudgetGp, antifirePotion, dreamItems,
			upgradeBudgetGp, mode,
			Collections.<CombatStyle, Map<com.loadoutlab.data.GearSlot, Integer>>emptyMap(),
			null, callback);
	}

	/** Back-compat overload (headless/tests): one exclusion set, all styles. */
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
		Set<Integer> excludedItems,
		int maxTradeables,
		int riskBudgetGp,
		boolean antifirePotion,
		Set<Integer> dreamItems,
		int upgradeBudgetGp,
		OptimizeMode mode,
		Map<CombatStyle, Map<com.loadoutlab.data.GearSlot, Integer>> pinnedByStyle,
		com.loadoutlab.data.SpellStats pinnedSpell,
		Consumer<Map<CombatStyle, StyleResult>> callback)
	{
		Set<Integer> uniform = excludedItems == null
			? Collections.emptySet() : excludedItems;
		Map<CombatStyle, Set<Integer>> byStyle = new EnumMap<>(CombatStyle.class);
		for (CombatStyle style : CombatStyle.concreteValues())
		{
			byStyle.put(style, uniform);
		}
		bestPerStyle(monster, realLevels, boostedLevels, prayerUnlocks, requirements,
			owned, collectionFingerprint, f2pOnly, onSlayerTask, spellbookLock,
			byStyle, maxTradeables, riskBudgetGp, antifirePotion,
			// Headless/tests default: in the Wilderness only when the
			// monster exists nowhere else (matches the request default).
			com.loadoutlab.data.WildernessMonsters.isExclusive(monster),
			dreamItems, upgradeBudgetGp, mode, pinnedByStyle, pinnedSpell,
			Collections.<Integer>emptySet(), callback);
	}

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
		boolean inWilderness,
		Set<Integer> dreamItems,
		int upgradeBudgetGp,
		OptimizeMode mode,
		Map<CombatStyle, Map<com.loadoutlab.data.GearSlot, Integer>> pinnedByStyle,
		com.loadoutlab.data.SpellStats pinnedSpell,
		Set<Integer> protectOnlyItems,
		Consumer<Map<CombatStyle, StyleResult>> callback)
	{
		final Map<CombatStyle, Map<com.loadoutlab.data.GearSlot, Integer>> pins =
			pinnedByStyle == null ? Collections.emptyMap() : pinnedByStyle;
		final Map<CombatStyle, Set<Integer>> excluded = excludedByStyle == null
			? Collections.emptyMap() : excludedByStyle;
		final Set<Integer> dreams = dreamItems == null
			? Collections.emptySet() : dreamItems;
		final Set<Integer> protectOnly = protectOnlyItems == null
			? Collections.emptySet() : protectOnlyItems;
		final String lock = spellbookLock == null ? "" : spellbookLock;
		final PlayerLevels real = realLevels == null ? boostedLevels : realLevels;
		// A free world has no members prayers, whatever the account has
		// unlocked - F2P mode caps the book at Mystic Might (audit A3.5).
		final PrayerUnlocks unlocks = f2pOnly ? PrayerUnlocks.F2P
			: prayerUnlocks == null ? PrayerUnlocks.ALL : prayerUnlocks;
		// The budget only matters when risk-constrained; pin it otherwise so
		// flipping the dropdown with the cap off cannot split the cache.
		final int riskBudget = maxTradeables >= 0
			? riskBudgetGp : OptimizationRequest.DEFAULT_RISK_BUDGET_GP;
		final String baseKey = collectionFingerprint + "|" + monster.getId() + "|" + f2pOnly
			+ "|" + onSlayerTask + "|" + lock + "|" + unlocks.key()
			+ "|" + maxTradeables + "|" + riskBudget + "|" + antifirePotion
			+ "|" + inWilderness
			+ "|" + dreams.hashCode() + "|" + upgradeBudgetGp
			+ "|" + (mode == null ? OptimizeMode.MAX_DPS : mode).name()
			+ "|" + protectOnly.hashCode()
			+ "|" + levelKey(real) + "|" + levelKey(boostedLevels);
		Map<CombatStyle, StyleResult> allCached = new EnumMap<>(CombatStyle.class);
		synchronized (cache)
		{
			for (CombatStyle style : new CombatStyle[]{CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC})
			{
				StyleResult hit = cache.get(styleKey(baseKey, style, pins, excluded, pinnedSpell));
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
		final OptimizeMode chosenMode = mode == null ? OptimizeMode.MAX_DPS : mode;
		final long ticket = requestSeq.incrementAndGet();
		worker.execute(() ->
		{
			if (requestSeq.get() != ticket)
			{
				abandonedForTest++;
				return; // superseded while queued
			}
			LoadoutData dataset = f2pOnly ? f2pView() : data;
			// Owned ornament/locked variants count as their base item - the
			// suggestion always shows the base version.
			OwnedItems effectiveOwned = new OwnedItems(
				dataset.canonicalizeOwned(owned.getQuantities()), owned.isBankScanned());
			Map<CombatStyle, StyleResult> results = new EnumMap<>(CombatStyle.class);
			for (CombatStyle style : new CombatStyle[]{CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC})
			{
				if (requestSeq.get() != ticket)
				{
					abandonedForTest++;
					return; // superseded mid-flight - abandon between styles
				}
				// Styles cache independently: a pin or per-set exclusion on
				// one style leaves the other two answers standing.
				String styleKey = styleKey(baseKey, style, pins, excluded, pinnedSpell);
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
				// bring), never below what is already live.
				BoostProfile boost = BoostSelector.bestFor(style, effectiveOwned, f2pOnly);
				PlayerLevels styleLevels = real.boosted(boost, boostedLevels).max(boostedLevels);
				String prayerName = PrayerBonuses.bestAvailable(styleLevels, unlocks).nameFor(style);
				String boostLabel = joinAssumes(prayerName,
					boost == BoostProfile.NONE ? null : boost.toString());
				// The ceiling assumes the best prayers/boost in the GAME,
				// not just what this player has unlocked or owns.
				BoostProfile gameBoost = BoostSelector.ceilingFor(style, f2pOnly);
				PlayerLevels gameLevels = real.boosted(gameBoost, boostedLevels).max(boostedLevels);
				String gamePrayerName = PrayerBonuses.bestAvailable(gameLevels,
					f2pOnly ? PrayerUnlocks.F2P : PrayerUnlocks.ALL).nameFor(style);
				String gameBoostLabel = joinAssumes(gamePrayerName, gameBoost.toString());
				// Dreams are pretend-owned; a positive upgrade budget also
				// admits anything buyable within it (total spend, tracked
				// by the beam).
				OptimizationRequest ownedRequest = request(
					monster, style, styleLevels, unlocks, requirements,
					upgradeBudgetGp > 0 ? CandidateMode.OWNED_OR_BUDGET : CandidateMode.OWNED_ONLY,
					effectiveOwned, 3, onSlayerTask, Math.max(0, upgradeBudgetGp))
					.withExcludedItems(excluded.getOrDefault(style, Collections.emptySet()))
					.withSpellbookLock(lock)
					.withMaxTradeables(maxTradeables).withRiskBudgetGp(riskBudget)
					.withAntifirePotion(antifirePotion)
					.withInWilderness(inWilderness)
					.withDreamItems(dreams)
					.withProtectOnlyItems(protectOnly)
					// Pins shape YOUR set only; game best stays the pure
					// ceiling so the cost of the preference is visible.
					.withPinnedItems(pins.getOrDefault(style, Collections.emptyMap()));
				if (style == CombatStyle.MAGIC && pinnedSpell != null)
				{
					// The mob's pinned autocast spell: forced, the search
					// optimizes the gear around it.
					ownedRequest = ownedRequest.withSpell(pinnedSpell);
				}
				List<DpsResult> ownedBest = optimizer.optimize(dataset, ownedRequest);
				if (!ownedBest.isEmpty())
				{
					// The displayed set: top up DPS-neutral empty slots with
					// prayer/defensive gear (verified not to change the DPS).
					ownedBest.set(0, optimizer.fillDpsNeutralSlots(dataset, ownedRequest, ownedBest.get(0)));
					ownedBest.set(0, optimizer.ensureRequiredUtility(dataset, ownedRequest, ownedBest.get(0)));
				}
				// D-4 frontier: when the mode wants safety, sweep defense
				// weights, walk the (dps out, dps in) frontier, and swap the
				// displayed set for the mode's pick. Every downstream number
				// (spec, incoming, risk) then describes the chosen set.
				ModeTrade modeTrade = null;
				if (chosenMode != OptimizeMode.MAX_DPS && !ownedBest.isEmpty())
				{
					modeTrade = applyMode(dataset, ownedRequest, ownedBest, chosenMode,
						monster, real, ticket);
				}
				// The ceiling: every obtainable item, no quest/level gating -
				// but computed at the player's own levels, so the comparison
				// percentage isolates the GEAR gap.
				// ALL_STANDARD ignores ownership for eligibility, but the
				// candidate dedupe prefers OWNED analogs on stat ties - so
				// the game-best card shows YOUR god d'hide coif, not an
				// arbitrary god's, and the BiS border matches by id.
				OptimizationRequest gameRequest = request(
					monster, style, gameLevels, PrayerUnlocks.ALL,
					RequirementProfile.MAXED,
					CandidateMode.ALL_STANDARD, effectiveOwned, 1, onSlayerTask, 0)
					.withExcludedItems(excluded.getOrDefault(style, Collections.emptySet()))
					.withSpellbookLock(lock)
					.withMaxTradeables(maxTradeables).withRiskBudgetGp(riskBudget)
					.withAntifirePotion(antifirePotion)
					.withInWilderness(inWilderness)
					.withProtectOnlyItems(protectOnly);
				List<DpsResult> gameBest = optimizer.optimize(dataset, gameRequest);
				if (!gameBest.isEmpty())
				{
					gameBest.set(0, optimizer.fillDpsNeutralSlots(dataset, gameRequest, gameBest.get(0)));
					gameBest.set(0, optimizer.ensureRequiredUtility(dataset, gameRequest, gameBest.get(0)));
				}
				SpecPick spec = bestSpec(dataset, ownedRequest, ownedBest, style, monster, styleLevels, effectiveOwned);
				SpecPick gameSpec = bestSpec(dataset, gameRequest, gameBest, style, monster, gameLevels, null);
				// The defensive story of the shown set: what the boss does
				// back to you, at your REAL levels (protection prayer up).
				IncomingDpsCalculator.Result incoming = ownedBest.isEmpty()
					? null
					: IncomingDpsCalculator.calculate(
						monster, ownedBest.get(0).getLoadout(), real.getDefence(), real.getMagic());
				IncomingDpsCalculator.Result gameIncoming = gameBest.isEmpty()
					? null
					: IncomingDpsCalculator.calculate(
						monster, gameBest.get(0).getLoadout(), real.getDefence(), real.getMagic());
				StyleResult styleResult = new StyleResult(
					ownedBest, gameBest.isEmpty() ? null : gameBest.get(0), spec, gameSpec,
					boostLabel, gameBoostLabel, incoming, gameIncoming, modeTrade);
				// Store per style as computed - even a superseded job donates
				// the styles it finished.
				synchronized (cache)
				{
					cache.put(styleKey, styleResult);
				}
				results.put(style, styleResult);
			}
			if (requestSeq.get() != ticket)
			{
				abandonedForTest++;
				return; // finished stale - never deliver over the newer answer
			}
			callback.accept(results);
		});
	}

	/** The per-style cache key: the query key minus per-style state, plus
	 * this style and the pins/exclusions/pinned-spell that shape ITS answer. */
	private static String styleKey(String baseKey, CombatStyle style,
		Map<CombatStyle, Map<com.loadoutlab.data.GearSlot, Integer>> pins,
		Map<CombatStyle, Set<Integer>> excluded,
		com.loadoutlab.data.SpellStats pinnedSpell)
	{
		return baseKey + "|" + style.name()
			+ "|" + pins.getOrDefault(style, Collections.emptyMap()).hashCode()
			+ "|" + excluded.getOrDefault(style, Collections.emptySet()).hashCode()
			+ "|" + (style == CombatStyle.MAGIC && pinnedSpell != null ? pinnedSpell.getName() : "");
	}

	/**
	 * Sweep defense weights to trace the offense/defense frontier, pick
	 * the mode's point, swap it into ownedBest[0], and return the note
	 * quantifying the trade. BALANCED = the knee (farthest from the
	 * line between the max-dps and tankiest points); TANKY = best
	 * out/in ratio holding at least half the max dps.
	 */
	private ModeTrade applyMode(LoadoutData dataset, OptimizationRequest ownedRequest,
		List<DpsResult> ownedBest, OptimizeMode mode,
		MonsterStats monster, PlayerLevels real, long ticket)
	{
		DpsResult maxDps = ownedBest.get(0);
		double d0 = maxDps.getDps();
		double i0 = incomingOf(monster, maxDps, real);
		if (d0 <= 0 || i0 <= 0.05)
		{
			return null; // nothing meaningful to trade against
		}
		List<DpsResult> frontier = new ArrayList<>();
		frontier.add(maxDps);
		// Candidate pools do not depend on the weight's magnitude (only on
		// weight > 0), so the sweep builds them once and reuses them.
		LoadoutOptimizer.CandidatePools pools = null;
		for (double alpha : SWEEP_ALPHAS)
		{
			if (requestSeq.get() != ticket)
			{
				return null; // superseded mid-sweep
			}
			OptimizationRequest weighted = ownedRequest.withDefenseWeight(alpha * d0 / i0);
			if (pools == null)
			{
				pools = optimizer.preparePools(dataset, weighted);
			}
			List<DpsResult> out = optimizer.optimize(dataset, weighted, pools);
			if (out.isEmpty())
			{
				continue;
			}
			DpsResult candidate = optimizer.ensureRequiredUtility(dataset, weighted,
				optimizer.fillDpsNeutralSlots(dataset, weighted, out.get(0)));
			frontier.add(candidate);
		}
		// Three pure objectives: MAX_DPS maximizes output (the input set);
		// TANKY minimizes intake, full stop; BALANCED maximizes the out/in
		// ratio over the whole frontier INCLUDING both endpoints - so its
		// ratio is >= the max-dps ratio and >= the tanky ratio by
		// construction. Ties always prefer more dps.
		DpsResult picked = maxDps;
		if (mode == OptimizeMode.TANKY)
		{
			double bestIn = i0;
			for (DpsResult candidate : frontier)
			{
				double in = incomingOf(monster, candidate, real);
				if (in < bestIn - 1e-9
					|| (in < bestIn + 1e-9 && candidate.getDps() > picked.getDps() + 1e-9))
				{
					bestIn = in;
					picked = candidate;
				}
			}
		}
		else
		{
			double bestScore = balancedScore(d0, i0);
			for (DpsResult candidate : frontier)
			{
				double score = balancedScore(candidate.getDps(),
					incomingOf(monster, candidate, real));
				if (score > bestScore + 1e-9
					|| (score > bestScore - 1e-9 && candidate.getDps() > picked.getDps() + 1e-9))
				{
					bestScore = score;
					picked = candidate;
				}
			}
		}
		if (picked == maxDps)
		{
			return null;
		}
		double d = picked.getDps();
		double in = incomingOf(monster, picked, real);
		ownedBest.set(0, picked);
		return new ModeTrade(
			(int) Math.round((1 - d / d0) * 100),
			(int) Math.round((1 - in / i0) * 100));
	}

	/** The dps-favored ratio Balanced maximizes. */
	static double balancedScore(double dpsOut, double dpsIn)
	{
		return Math.pow(Math.max(dpsOut, 0), 1 + BALANCED_DPS_BIAS)
			/ Math.max(dpsIn, 1e-9);
	}

	private static double incomingOf(MonsterStats monster,
		DpsResult result, PlayerLevels real)
	{
		return IncomingDpsCalculator.calculate(
			monster, result.getLoadout(), real.getDefence(), real.getMagic()).totalDps;
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
	static final class SpecPick
	{
		final SpecialAttack spec;
		final GearItem weapon;
		final double expectedDamage;
		final double drainValue;

		SpecPick(SpecialAttack spec, GearItem weapon, double expectedDamage, double drainValue)
		{
			this.spec = spec;
			this.weapon = weapon;
			this.expectedDamage = expectedDamage;
			this.drainValue = drainValue;
		}
	}

	/**
	 * The strongest special-attack weapon for this style, evaluated by
	 * swapping it into the base set (shield dropped for two-handers, ammo
	 * re-picked for compatibility) and applying the spec's verified roll
	 * modifiers. With an ownership ledger, only owned weapons/ammo count
	 * (requirement #7/#8); with null, everything standard counts - the
	 * game-best spec.
	 */
	SpecPick bestSpec(
		LoadoutData dataset,
		OptimizationRequest request,
		List<DpsResult> baseResults,
		CombatStyle style,
		MonsterStats monster,
		PlayerLevels levels,
		OwnedItems owned)
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
				: new java.util.HashSet<>(request.getPinnedItems().values());
		}
		// Spec weapons are weapons by definition (SpecialAttack.match rejects
		// every other slot), so only the weapon partition needs scanning.
		for (GearItem item : dataset.getGearItems(GearSlot.WEAPON))
		{
			if (!item.isStandardGear() || dataset.isVariant(item.getId())
				|| request.isExcluded(item.getId())
				|| (owned != null && !owned.owns(item.getId())))
			{
				continue;
			}
			SpecialAttack spec = SpecialAttack.match(item, style);
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
			Loadout loadout = specLoadout(dataset, baseResults.get(0).getLoadout(), item, owned, request);
			if (loadout == null)
			{
				continue;
			}
			DpsResult base = calculator.calculate(request, loadout);
			if (base == null || base.getMaxHit() <= 0)
			{
				continue;
			}
			double expected = spec.expectedDamage(base, monster, levels);
			double drainValue = drainValue(calculator, spec, base, expected, request, baseResults.get(0), monster);
			double total = expected + drainValue;
			double bestTotal = best == null
				? Double.NEGATIVE_INFINITY : best.expectedDamage + best.drainValue;
			// Ties (identical stats across poison tiers) prefer the higher
			// tier - the venom is free spec damage the model does not price.
			if (best == null || total > bestTotal + 1e-9
				|| (total > bestTotal - 1e-9 && item.poisonTier() > best.weapon.poisonTier()))
			{
				best = new SpecPick(spec, item, expected, drainValue);
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
	private double drainValue(
		DpsCalculator calculator,
		SpecialAttack spec,
		DpsResult specBase,
		double specExpectedDamage,
		OptimizationRequest request,
		DpsResult mainResult,
		MonsterStats monster)
	{
		if (!spec.drainsDefence() || request.getStyle() != CombatStyle.MELEE)
		{
			return 0;
		}
		double mainDps = mainResult.getDps();
		if (mainDps <= 0.01)
		{
			return 0;
		}
		int drainedDefence = spec.drainedDefence(monster.getDefence(), specExpectedDamage);
		if (drainedDefence >= monster.getDefence())
		{
			return 0;
		}
		DpsResult drained = calculator.calculate(
			request.withMonster(monster.withDefence(drainedDefence)), mainResult.getLoadout());
		if (drained == null || drained.getDps() <= mainDps)
		{
			return 0;
		}
		double fightSeconds = Math.min(600, monster.getHitpoints() / mainDps);
		return spec.landChance(specBase) * (drained.getDps() - mainDps) * fightSeconds;
	}

	/** The base set with the spec weapon swapped in, or null if unusable.
	 * owned == null -> any standard ammo may be picked (game-best spec). */
	private Loadout specLoadout(LoadoutData dataset, Loadout baseSet, GearItem weapon, OwnedItems owned, OptimizationRequest request)
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
			}
			else
			{
				gear.remove(GearSlot.AMMO);
			}
		}
		return new Loadout(gear);
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
		synchronized (cache)
		{
			cache.clear();
		}
	}
}
