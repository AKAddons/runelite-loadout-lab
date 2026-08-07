package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.config.ConfigManager;
import java.util.Collections;

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
		EQUIPMENT("equipment", net.runelite.api.InventoryID.EQUIPMENT.getId()),
		INVENTORY("inventory", net.runelite.api.InventoryID.INVENTORY.getId()),
		BANK("bank", net.runelite.api.InventoryID.BANK.getId()),
		/** Seen when the bag is opened or checked; vital for UIM accounts.
		 * The classic InventoryID enum lacks the newer containers; the
		 * gameval ids are the authoritative modern constants. */
		LOOTING_BAG("lootingBag", InventoryID.LOOTING_BAG),
		/** POH costume storage: one shared container covering the armour
		 * case, wardrobes, treasure chest, and cape rack, seen when a
		 * costume storage interface is opened. */
		POH_COSTUMES("pohCostumes", InventoryID.POH_COSTUMES),
		/** Not container-backed: filled units' default items, read from
		 * the STASH chart (see LoadoutLabPlugin.scanStashChart). The -1
		 * container sentinel makes the per-tick drain's null-container
		 * check clear a stray dirty flag, and keeps STASH out of the
		 * {@link #forContainer} reverse lookup. */
		STASH("stash", -1),
		/** Sailing boat cargo holds, one container per boat slot. */
		CARGO_HOLD_1("cargoHold1", InventoryID.SAILING_BOAT_1_CARGOHOLD),
		CARGO_HOLD_2("cargoHold2", InventoryID.SAILING_BOAT_2_CARGOHOLD),
		CARGO_HOLD_3("cargoHold3", InventoryID.SAILING_BOAT_3_CARGOHOLD),
		CARGO_HOLD_4("cargoHold4", InventoryID.SAILING_BOAT_4_CARGOHOLD),
		CARGO_HOLD_5("cargoHold5", InventoryID.SAILING_BOAT_5_CARGOHOLD);

		private final String key;
		private final int containerId;

		Source(String key, int containerId)
		{
			this.key = key;
			this.containerId = containerId;
		}

		/** The item-container id this source is scanned from, or -1 when
		 * the source is not container-backed (STASH). */
		public int containerId()
		{
			return containerId;
		}

		/**
		 * Reverse lookup: the source scanned from {@code containerId}, or
		 * null when no source uses it. Callers pass an UNMASKED container
		 * id (the 0x8000 modified-container bit already stripped).
		 */
		public static Source forContainer(int containerId)
		{
			// Guard the STASH sentinel: without it, -1 would resolve to
			// STASH instead of "no such container".
			if (containerId < 0)
			{
				return null;
			}
			for (Source s : values())
			{
				if (s.containerId == containerId)
				{
					return s;
				}
			}
			return null;
		}

		/** The persisted config-key segment; also the storage name in the
		 * storages-response PluginMessage (see StoragesApi). */
		public String key()
		{
			return key;
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

	/** One source's current snapshot, read-only (itemId → quantity). */
	public Map<Integer, Integer> snapshot(Source source)
	{
		return Collections.unmodifiableMap(snapshots.get(source));
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
