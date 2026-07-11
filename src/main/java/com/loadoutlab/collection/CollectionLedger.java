package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;

/**
 * The persistent "what I own" ledger - Loadout Lab's first differentiator.
 *
 * <p>Container snapshots (equipment, inventory, bank, looting bag), each
 * replaced wholesale when its container is seen, merged on demand into one
 * itemId → quantity view. Persisted through RuneLite's ConfigManager so the
 * ledger survives sessions: your bank from LAST visit still counts today,
 * no re-scan required (unlike per-session scanners).
 *
 * <p>Scoping: RuneLite config profiles already isolate the backing
 * .properties per profile; on top of that, keys carry a world-type prefix so
 * a leagues/seasonal login never pollutes the standard-world ledger.
 *
 * <p>Write discipline (learned on goal-planner): saves are per-snapshot and
 * only when that snapshot actually changed - callers coalesce container
 * events per tick before calling {@link #update}.
 *
 * <p>Item ids are stored RAW. Variant canonicalization (degraded/charged
 * forms mapping to the engine's canonical gear ids via equipment aliases)
 * happens at query time once the data pipeline vendors the alias table.
 */
public class CollectionLedger
{
	static final String CONFIG_GROUP = "loadoutlab";
	private static final Type MAP_TYPE = new TypeToken<Map<Integer, Integer>>() {}.getType();

	/** Ledger sources, each persisted under its own key. */
	public enum Source
	{
		EQUIPMENT("equipment"),
		INVENTORY("inventory"),
		BANK("bank"),
		/** Seen when the bag is opened or checked; vital for UIM accounts. */
		LOOTING_BAG("lootingBag");

		private final String key;

		Source(String key)
		{
			this.key = key;
		}
	}

	private final ConfigManager configManager;
	private final Gson gson;

	/** "std" or "seasonal" - set on login before any update/load. */
	private String worldScope = "std";

	private final Map<Source, Map<Integer, Integer>> snapshots = new HashMap<>();

	/** Cached fingerprint of the merged view; recomputed lazily. */
	private transient Integer fingerprint;

	public CollectionLedger(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
		for (Source s : Source.values())
		{
			snapshots.put(s, new HashMap<>());
		}
	}

	/**
	 * Point the ledger at a world scope ("std" or "seasonal") and load that
	 * scope's persisted snapshots, replacing in-memory state.
	 */
	public void loadScope(String scope)
	{
		this.worldScope = scope;
		for (Source s : Source.values())
		{
			Map<Integer, Integer> loaded = null;
			String json = configManager.getConfiguration(CONFIG_GROUP, key(s));
			if (json == null || json.isEmpty())
			{
				// One-time adoption of pre-account-scoping data ("std.*"):
				// a single-account install keeps its scanned bank when the
				// scope key gains the account hash.
				int dot = scope.indexOf('.');
				String legacy = (dot > 0 ? scope.substring(0, dot) : scope) + ".collection." + s.key;
				json = configManager.getConfiguration(CONFIG_GROUP, legacy);
			}
			if (json != null && !json.isEmpty())
			{
				try
				{
					loaded = gson.fromJson(json, MAP_TYPE);
				}
				catch (RuntimeException e)
				{
					// Corrupt entry: start that snapshot fresh rather than failing.
					loaded = null;
				}
			}
			snapshots.put(s, loaded != null ? loaded : new HashMap<>());
		}
		fingerprint = null;
	}

	/**
	 * Replace one source's snapshot. Persists only when the content actually
	 * changed; returns true in that case (callers use it to invalidate caches).
	 */
	public boolean update(Source source, Map<Integer, Integer> items)
	{
		Map<Integer, Integer> next = new HashMap<>(items);
		if (next.equals(snapshots.get(source)))
		{
			return false;
		}
		snapshots.put(source, next);
		configManager.setConfiguration(CONFIG_GROUP, key(source), gson.toJson(next, MAP_TYPE));
		fingerprint = null;
		return true;
	}

	/** Merged itemId → total quantity across all sources. */
	public Map<Integer, Integer> owned()
	{
		Map<Integer, Integer> merged = new HashMap<>();
		for (Map<Integer, Integer> snap : snapshots.values())
		{
			for (Map.Entry<Integer, Integer> e : snap.entrySet())
			{
				merged.merge(e.getKey(), e.getValue(), Integer::sum);
			}
		}
		return merged;
	}

	/** True once a bank snapshot exists (this session or any previous one). */
	public boolean bankKnown()
	{
		return !snapshots.get(Source.BANK).isEmpty();
	}

	/**
	 * Stable fingerprint of the merged view - the optimizer cache key
	 * component. Changes iff ownership actually changed.
	 */
	public int fingerprint()
	{
		if (fingerprint == null)
		{
			fingerprint = owned().hashCode();
		}
		return fingerprint;
	}

	private String key(Source s)
	{
		return worldScope + ".collection." + s.key;
	}
}
