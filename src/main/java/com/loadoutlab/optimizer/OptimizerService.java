package com.loadoutlab.optimizer;

import com.loadoutlab.engine.CandidateMode;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.OptimizationRequest;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerBonuses;
import com.loadoutlab.engine.LoadoutOptimizer;
import com.loadoutlab.engine.RequirementProfile;
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

	/** Per-style outcome: your best owned sets + the game-wide best set. */
	public static final class StyleResult
	{
		public final List<DpsResult> owned;
		public final DpsResult overallBest;

		StyleResult(List<DpsResult> owned, DpsResult overallBest)
		{
			this.owned = owned;
			this.overallBest = overallBest;
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
			Map<CombatStyle, StyleResult> results = new EnumMap<>(CombatStyle.class);
			for (CombatStyle style : new CombatStyle[]{CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC})
			{
				List<DpsResult> ownedBest = optimizer.optimize(dataset, request(
					monster, style, boostedLevels, requirements,
					CandidateMode.OWNED_ONLY, owned, 3));
				List<DpsResult> gameBest = optimizer.optimize(dataset, request(
					monster, style, boostedLevels, requirements,
					CandidateMode.ALL_STANDARD, OwnedItems.EMPTY, 1));
				results.put(style, new StyleResult(
					ownedBest, gameBest.isEmpty() ? null : gameBest.get(0)));
			}
			synchronized (cache)
			{
				cache.put(key, results);
			}
			callback.accept(results);
		});
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
