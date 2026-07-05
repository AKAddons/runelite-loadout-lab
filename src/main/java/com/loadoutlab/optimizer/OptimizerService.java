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
 * (collection fingerprint, monster id, style) - the fingerprint changes iff
 * ownership changes, so a cache hit is always current. LRU-bounded.
 *
 * <p>Threading: optimize() is CPU work - it runs on a single daemon worker,
 * never the EDT or client thread. Callers receive results via a callback
 * that is NOT thread-marshalled here; UI callers wrap in
 * SwingUtilities.invokeLater.
 */
public class OptimizerService
{
	private static final int CACHE_MAX = 64;

	private final LoadoutData data;
	private final LoadoutOptimizer optimizer = new LoadoutOptimizer();
	private final ExecutorService worker = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "loadout-lab-optimizer");
		t.setDaemon(true);
		return t;
	});

	/** LRU cache: key -> per-style best results. Access-ordered. */
	private final Map<String, Map<CombatStyle, List<DpsResult>>> cache =
		new LinkedHashMap<String, Map<CombatStyle, List<DpsResult>>>(32, 0.75f, true)
		{
			@Override
			protected boolean removeEldestEntry(Map.Entry<String, Map<CombatStyle, List<DpsResult>>> eldest)
			{
				return size() > CACHE_MAX;
			}
		};

	public OptimizerService(LoadoutData data)
	{
		this.data = data;
	}

	/**
	 * Compute the best OWNED set for each of melee/ranged/magic against a
	 * monster. Cached; the callback runs on the worker thread on a miss and
	 * synchronously on a hit.
	 */
	public void bestOwnedPerStyle(
		MonsterStats monster,
		PlayerLevels boostedLevels,
		RequirementProfile requirements,
		OwnedItems owned,
		int collectionFingerprint,
		Consumer<Map<CombatStyle, List<DpsResult>>> callback)
	{
		final String key = collectionFingerprint + "|" + monster.getId() + "|" + levelKey(boostedLevels);
		Map<CombatStyle, List<DpsResult>> cached;
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
			Map<CombatStyle, List<DpsResult>> results = new EnumMap<>(CombatStyle.class);
			for (CombatStyle style : new CombatStyle[]{CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC})
			{
				OptimizationRequest request = new OptimizationRequest(
					monster,
					style,
					boostedLevels,
					PrayerBonuses.bestAvailable(boostedLevels),
					null,                    // auto-pick the spell for magic
					0,                       // no budget: owned only
					CandidateMode.OWNED_ONLY,
					true,                    // untradeables count - we KNOW you own them
					false,                   // slayer-task toggle arrives in v0.2
					owned,
					requirements,
					3);
				results.put(style, optimizer.optimize(data, request));
			}
			synchronized (cache)
			{
				cache.put(key, results);
			}
			callback.accept(results);
		});
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
