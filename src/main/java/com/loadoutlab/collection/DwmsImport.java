package com.loadoutlab.collection;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.config.ConfigManager;

/**
 * Best-effort import of gear tracked by the "Dude, Where's My Stuff?"
 * plugin (config group "dudewheresmystuff"): storages it watches that the
 * ledger cannot see - STASH units, the POH costume room, death storage,
 * boat cargo holds - count as owned without manual marking.
 *
 * <p>Reads DWMS's persisted values through the public ConfigManager API
 * (no reflection; works even while DWMS is disabled, since the data
 * outlives the plugin). The value format is DWMS-internal and unversioned
 * ("lastUpdated;id x qty,..."), so parsing is strictly defensive: any
 * field or token that does not match is dropped, never guessed at. After
 * an upstream format change the worst case is items silently not counting
 * - the manual stored-elsewhere list remains the ground truth, and a
 * PluginMessage contract upstream is the planned durable path.
 */
public class DwmsImport
{
	static final String DWMS_GROUP = "dudewheresmystuff";

	/** Key prefixes that can hold wearable gear. Points/supply stores
	 * (minigames., coins.) persist positional quantity lists that are
	 * meaningless without DWMS's internal item ordering - skipped. */
	private static final String[] GEAR_PREFIXES =
		{"carryable.", "death.", "poh.", "sailing.", "stash.", "world."};

	private final ConfigManager configManager;

	private volatile Map<Integer, Integer> items = Collections.emptyMap();

	public DwmsImport(ConfigManager configManager)
	{
		this.configManager = configManager;
	}

	/** Re-read the current RSProfile's DWMS data (cheap; called per compute). */
	public void reload()
	{
		Map<Integer, Integer> next = new HashMap<>();
		String profileKey = configManager.getRSProfileKey();
		if (profileKey != null)
		{
			for (String prefix : GEAR_PREFIXES)
			{
				List<String> keys = configManager.getRSProfileConfigurationKeys(
					DWMS_GROUP, profileKey, prefix);
				if (keys == null)
				{
					continue;
				}
				for (String key : keys)
				{
					parseValue(configManager.getConfiguration(DWMS_GROUP, profileKey, key), next);
				}
			}
		}
		items = next.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(next);
	}

	/** Item id -> quantity last seen by DWMS, merged across its storages. */
	public Map<Integer, Integer> snapshot()
	{
		return items;
	}

	/** Distinct imported item count - the panel's provenance line. */
	public int count()
	{
		return items.size();
	}

	/** Fold the imported items into an owned map (quantities sum). */
	public Map<Integer, Integer> mergeInto(Map<Integer, Integer> owned)
	{
		for (Map.Entry<Integer, Integer> e : items.entrySet())
		{
			owned.merge(e.getKey(), e.getValue(), Integer::sum);
		}
		return owned;
	}

	/**
	 * One DWMS storage value: ';'-separated fields where a leading
	 * all-digit field is the lastUpdated timestamp (absent on automatic
	 * storages) and the item list is comma-joined "&lt;id&gt;x&lt;qty&gt;" tokens;
	 * later fields (deathbank flags etc.) are storage-specific extras.
	 * Static-item storages persist bare quantity lists - their tokens
	 * have no 'x' and fall out here by construction.
	 */
	static void parseValue(String value, Map<Integer, Integer> into)
	{
		if (value == null || value.isEmpty())
		{
			return;
		}
		String[] fields = value.split(";", -1);
		String itemField = fields.length > 1 && fields[0].matches("\\d+") ? fields[1] : fields[0];
		for (String token : itemField.split(","))
		{
			int x = token.indexOf('x');
			if (x <= 0)
			{
				continue;
			}
			try
			{
				int id = Integer.parseInt(token.substring(0, x));
				int quantity = Integer.parseInt(token.substring(x + 1));
				if (id > 0 && quantity > 0)
				{
					into.merge(id, quantity, Integer::sum);
				}
			}
			catch (NumberFormatException ignored)
			{
				// Unversioned upstream format: drop, never guess.
			}
		}
	}
}
