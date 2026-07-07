package com.loadoutlab.optimizer;

import com.loadoutlab.engine.CandidateMode;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.DpsCalculator;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.Loadout;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerBonuses;
import com.loadoutlab.engine.LoadoutOptimizer;
import com.loadoutlab.engine.RangedAmmo;
import com.loadoutlab.engine.RequirementProfile;
import com.loadoutlab.engine.SpecialAttack;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Runs BiS searches off the game threads and caches results.
 *
 * <p>Caching (founding requirement): results are keyed by
 * (collection fingerprint, monster id, levels, f2p) - the fingerprint changes
 * iff ownership changes, so a cache hit is always current. LRU-bounded.
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
	private static final int CACHE_MAX = 64;

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
		public final com.loadoutlab.engine.IncomingDpsCalculator.Result incoming;

		StyleResult(List<DpsResult> owned, DpsResult overallBest,
			SpecPick spec, SpecPick gameSpec, String boostLabel, String gameBoostLabel,
			com.loadoutlab.engine.IncomingDpsCalculator.Result incoming)
		{
			this.boostLabel = boostLabel;
			this.gameBoostLabel = gameBoostLabel;
			this.incoming = incoming;
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
	private final java.util.concurrent.atomic.AtomicLong requestSeq =
		new java.util.concurrent.atomic.AtomicLong();
	/** Abandoned-computation count - test observability only. */
	volatile int abandonedForTest;

	private final Map<String, Map<CombatStyle, StyleResult>> cache =
		new LinkedHashMap<String, Map<CombatStyle, StyleResult>>(32, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Map<CombatStyle, StyleResult>> eldest)
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
	public void bestPerStyle(
		MonsterStats monster,
		PlayerLevels realLevels,
		PlayerLevels boostedLevels,
		com.loadoutlab.engine.PrayerUnlocks prayerUnlocks,
		RequirementProfile requirements,
		OwnedItems owned,
		int collectionFingerprint,
		boolean f2pOnly,
		boolean onSlayerTask,
		String spellbookLock,
		java.util.Set<Integer> excludedItems,
		int maxTradeables,
		Consumer<Map<CombatStyle, StyleResult>> callback)
	{
		final java.util.Set<Integer> excluded = excludedItems == null
			? java.util.Collections.emptySet() : excludedItems;
		final String lock = spellbookLock == null ? "" : spellbookLock;
		final PlayerLevels real = realLevels == null ? boostedLevels : realLevels;
		final com.loadoutlab.engine.PrayerUnlocks unlocks = prayerUnlocks == null
			? com.loadoutlab.engine.PrayerUnlocks.ALL : prayerUnlocks;
		final String key = collectionFingerprint + "|" + monster.getId() + "|" + f2pOnly
			+ "|" + onSlayerTask + "|" + lock + "|" + excluded.hashCode() + "|" + unlocks.key()
			+ "|" + maxTradeables + "|" + levelKey(real) + "|" + levelKey(boostedLevels);
		Map<CombatStyle, StyleResult> cached;
		synchronized (cache)
		{
			cached = cache.get(key);
		}
		if (cached != null)
		{
			callback.accept(cached);
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
				// Assume the best boost the player OWNS (drink what you
				// bring), never below what is already live.
				com.loadoutlab.engine.BoostProfile boost = BoostSelector.bestFor(style, effectiveOwned);
				PlayerLevels styleLevels = real.boosted(boost, boostedLevels).max(boostedLevels);
				String prayerName = PrayerBonuses.bestAvailable(styleLevels, unlocks).nameFor(style);
				String boostLabel = joinAssumes(prayerName,
					boost == com.loadoutlab.engine.BoostProfile.NONE ? null : boost.toString());
				// The ceiling assumes the best prayers/boost in the GAME,
				// not just what this player has unlocked or owns.
				com.loadoutlab.engine.BoostProfile gameBoost = BoostSelector.ceilingFor(style);
				PlayerLevels gameLevels = real.boosted(gameBoost, boostedLevels).max(boostedLevels);
				String gamePrayerName = PrayerBonuses.bestAvailable(gameLevels,
					com.loadoutlab.engine.PrayerUnlocks.ALL).nameFor(style);
				String gameBoostLabel = joinAssumes(gamePrayerName, gameBoost.toString());
				OptimizationRequest ownedRequest = request(
					monster, style, styleLevels, unlocks, requirements,
					CandidateMode.OWNED_ONLY, effectiveOwned, 3, onSlayerTask)
					.withExcludedItems(excluded).withSpellbookLock(lock)
					.withMaxTradeables(maxTradeables);
				List<DpsResult> ownedBest = optimizer.optimize(dataset, ownedRequest);
				if (!ownedBest.isEmpty())
				{
					// The displayed set: top up DPS-neutral empty slots with
					// prayer/defensive gear (verified not to change the DPS).
					ownedBest.set(0, optimizer.fillDpsNeutralSlots(dataset, ownedRequest, ownedBest.get(0)));
				}
				// The ceiling: every obtainable item, no quest/level gating -
				// but computed at the player's own levels, so the comparison
				// percentage isolates the GEAR gap.
				OptimizationRequest gameRequest = request(
					monster, style, gameLevels, com.loadoutlab.engine.PrayerUnlocks.ALL,
					RequirementProfile.MAXED,
					CandidateMode.ALL_STANDARD, OwnedItems.EMPTY, 1, onSlayerTask)
					.withExcludedItems(excluded).withSpellbookLock(lock)
					.withMaxTradeables(maxTradeables);
				List<DpsResult> gameBest = optimizer.optimize(dataset, gameRequest);
				if (!gameBest.isEmpty())
				{
					gameBest.set(0, optimizer.fillDpsNeutralSlots(dataset, gameRequest, gameBest.get(0)));
				}
				SpecPick spec = bestSpec(dataset, ownedRequest, ownedBest, style, monster, styleLevels, effectiveOwned);
				SpecPick gameSpec = bestSpec(dataset, gameRequest, gameBest, style, monster, gameLevels, null);
				// The defensive story of the shown set: what the boss does
				// back to you, at your REAL levels (protection prayer up).
				com.loadoutlab.engine.IncomingDpsCalculator.Result incoming = ownedBest.isEmpty()
					? null
					: com.loadoutlab.engine.IncomingDpsCalculator.calculate(
						monster, ownedBest.get(0).getLoadout(), real.getDefence(), real.getMagic());
				results.put(style, new StyleResult(
					ownedBest, gameBest.isEmpty() ? null : gameBest.get(0), spec, gameSpec,
					boostLabel, gameBoostLabel, incoming));
			}
			if (requestSeq.get() != ticket)
			{
				abandonedForTest++;
				return; // finished stale - never deliver over the newer answer
			}
			synchronized (cache)
			{
				cache.put(key, results);
			}
			callback.accept(results);
		});
	}

	private static int riskCapCount(OptimizationRequest request, com.loadoutlab.engine.Loadout loadout)
	{
		int count = 0;
		for (com.loadoutlab.data.GearItem item : loadout.getGear().values())
		{
			if (request.countsAgainstRiskCap(item))
			{
				count++;
			}
		}
		return count;
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

	private static final class SpecPick
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
	private SpecPick bestSpec(
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
		for (GearItem item : dataset.getGearItems())
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
			// In a wilderness low-risk set the carried spec weapon is a risk
			// item too - but throwaway-cheap ones (the classic dragon
			// dagger) pass freely; only valuable specs need cap headroom.
			if (request.countsAgainstRiskCap(item)
				&& riskCapCount(request, baseResults.get(0).getLoadout()) + 1 > request.getMaxTradeables())
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
			double drainValue = drainValue(spec, base, expected, request, baseResults.get(0), monster);
			if (best == null || expected + drainValue > best.expectedDamage + best.drainValue)
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
		DpsResult drained = new DpsCalculator().calculate(
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
			for (GearItem ammo : dataset.getGearItems())
			{
				if (ammo.getSlot() == GearSlot.AMMO
					&& ammo.isStandardGear() && !dataset.isVariant(ammo.getId())
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
		com.loadoutlab.engine.PrayerUnlocks unlocks,
		RequirementProfile requirements,
		CandidateMode mode,
		OwnedItems owned,
		int limit,
		boolean onSlayerTask)
	{
		return new OptimizationRequest(
			monster,
			style,
			levels,
			PrayerBonuses.bestAvailable(levels, unlocks),
			null,          // auto-pick the spell for magic
			0,             // budget unused by these modes
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

	public void shutdown()
	{
		worker.shutdownNow();
		synchronized (cache)
		{
			cache.clear();
		}
	}
}
