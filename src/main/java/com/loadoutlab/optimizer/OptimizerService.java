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

		StyleResult(List<DpsResult> owned, DpsResult overallBest,
			SpecPick spec, SpecPick gameSpec)
		{
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
		PlayerLevels boostedLevels,
		RequirementProfile requirements,
		OwnedItems owned,
		int collectionFingerprint,
		boolean f2pOnly,
		Consumer<Map<CombatStyle, StyleResult>> callback)
	{
		final String key = collectionFingerprint + "|" + monster.getId() + "|" + f2pOnly
			+ "|" + levelKey(boostedLevels);
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
		worker.execute(() ->
		{
			LoadoutData dataset = f2pOnly ? f2pView() : data;
			// Owned ornament/locked variants count as their base item - the
			// suggestion always shows the base version.
			OwnedItems effectiveOwned = new OwnedItems(
				dataset.canonicalizeOwned(owned.getQuantities()), owned.isBankScanned());
			Map<CombatStyle, StyleResult> results = new EnumMap<>(CombatStyle.class);
			for (CombatStyle style : new CombatStyle[]{CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC})
			{
				OptimizationRequest ownedRequest = request(
					monster, style, boostedLevels, requirements,
					CandidateMode.OWNED_ONLY, effectiveOwned, 3);
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
					monster, style, boostedLevels, RequirementProfile.MAXED,
					CandidateMode.ALL_STANDARD, OwnedItems.EMPTY, 1);
				List<DpsResult> gameBest = optimizer.optimize(dataset, gameRequest);
				if (!gameBest.isEmpty())
				{
					gameBest.set(0, optimizer.fillDpsNeutralSlots(dataset, gameRequest, gameBest.get(0)));
				}
				SpecPick spec = bestSpec(dataset, ownedRequest, ownedBest, style, monster, boostedLevels, effectiveOwned);
				SpecPick gameSpec = bestSpec(dataset, gameRequest, gameBest, style, monster, boostedLevels, null);
				results.put(style, new StyleResult(
					ownedBest, gameBest.isEmpty() ? null : gameBest.get(0), spec, gameSpec));
			}
			synchronized (cache)
			{
				cache.put(key, results);
			}
			callback.accept(results);
		});
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
				|| (owned != null && !owned.owns(item.getId())))
			{
				continue;
			}
			SpecialAttack spec = SpecialAttack.match(item, style);
			if (spec == null || !request.getRequirementProfile().canEquip(item.getRequirements()))
			{
				continue;
			}
			Loadout loadout = specLoadout(dataset, baseResults.get(0).getLoadout(), item, owned);
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
	private Loadout specLoadout(LoadoutData dataset, Loadout baseSet, GearItem weapon, OwnedItems owned)
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
		RequirementProfile requirements,
		CandidateMode mode,
		OwnedItems owned,
		int limit)
	{
		return new OptimizationRequest(
			monster,
			style,
			levels,
			PrayerBonuses.bestAvailable(levels),
			null,          // auto-pick the spell for magic
			0,             // budget unused by these modes
			mode,
			true,          // untradeables count
			false,         // slayer-task toggle arrives in v0.2
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
