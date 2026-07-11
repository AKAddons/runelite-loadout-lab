package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigManager;

/**
 * "Stored elsewhere" items: gear the player really owns but keeps in
 * storage the ledger cannot see - STASH units, the POH costume room,
 * UIM cold/nest storage. Treated as genuinely owned: merged into the
 * owned view (at quantity 1 - the optimizer only checks membership),
 * so it reaches suggestions, bank borders, and the exported profile.
 *
 * <p>Unlike dreams (global what-ifs), this list is scoped like the
 * ledger it augments: world type + account hash, so two characters
 * never share a storage list. The scope is set via {@link #loadScope}
 * on login/identity change, mirroring CollectionLedger.
 */
public class ManualOwnedStore
{
	static final String CONFIG_GROUP = "loadoutlab";

	private final ConfigManager configManager;
	private final Gson gson;

	/** "std"/"seasonal" (+ ".accountHash") - set on login before use. */
	private String worldScope = "std";

	private final Set<Integer> items = new LinkedHashSet<>();

	public ManualOwnedStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
	}

	/** Point the store at a scope and load its persisted list. */
	public synchronized void loadScope(String scope)
	{
		this.worldScope = scope;
		items.clear();
		String json = configManager.getConfiguration(CONFIG_GROUP, key());
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			Set<Integer> stored = gson.fromJson(json, new TypeToken<Set<Integer>>(){}.getType());
			if (stored != null)
			{
				items.addAll(stored);
			}
		}
		catch (RuntimeException ex)
		{
			// Corrupt entry: start fresh rather than failing the plugin.
		}
	}

	/** Toggles the item; returns true when it is now marked stored. */
	public synchronized boolean toggle(int itemId)
	{
		boolean nowStored = !items.remove(itemId) && items.add(itemId);
		save();
		return nowStored;
	}

	public synchronized void clear()
	{
		items.clear();
		save();
	}

	public synchronized boolean isStored(int itemId)
	{
		return items.contains(itemId);
	}

	public synchronized Set<Integer> snapshot()
	{
		return Collections.unmodifiableSet(new LinkedHashSet<>(items));
	}

	/**
	 * Fold the stored items into an owned map as quantity-1 entries.
	 * Quantities are advisory throughout the engine (membership decides
	 * eligibility), so 1 is enough; real ledger counts pass through.
	 */
	public synchronized Map<Integer, Integer> mergeInto(Map<Integer, Integer> owned)
	{
		for (int id : items)
		{
			owned.merge(id, 1, Integer::sum);
		}
		return owned;
	}

	private void save()
	{
		configManager.setConfiguration(CONFIG_GROUP, key(), gson.toJson(items));
	}

	private String key()
	{
		return worldScope + ".manualOwned";
	}
}
