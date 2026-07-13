package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import net.runelite.client.config.ConfigManager;

/**
 * Items the player will bring into the Wilderness ONLY when they would be
 * protected on death - flagged from the right-click menu on an item shown
 * with the death skull ("lost on death"). A low-risk set that would leave
 * a flagged item in the lost pile is vetoed by the optimizer, exactly like
 * the salve-line rebuild friction, but chosen per item by the player.
 *
 * <p>Global and scope-free (the item's importance is intrinsic, not
 * mob-specific), mirroring ExclusionStore. Only consulted while a query is
 * risk-constrained (low-risk wilderness).
 */
public class ProtectOnlyStore
{
	static final String CONFIG_GROUP = "loadoutlab";
	static final String KEY = "protectOnlyItems";

	private final ConfigManager configManager;
	private final Gson gson;
	private final Set<Integer> protectOnly = new LinkedHashSet<>();

	public ProtectOnlyStore(ConfigManager configManager, Gson gson)
	{
		this.configManager = configManager;
		this.gson = gson;
		load();
	}

	/** Re-read from config - the active RuneLite profile may have changed. */
	public synchronized void reload()
	{
		load();
	}

	private void load()
	{
		protectOnly.clear();
		String json = configManager.getConfiguration(CONFIG_GROUP, KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			Set<Integer> stored = gson.fromJson(json, new TypeToken<Set<Integer>>(){}.getType());
			if (stored != null)
			{
				protectOnly.addAll(stored);
			}
		}
		catch (RuntimeException ex)
		{
			// Corrupt entry: start fresh rather than failing the plugin.
		}
	}

	/** Toggles the item; returns true when it is now protect-only. */
	public synchronized boolean toggle(int itemId)
	{
		boolean nowProtectOnly = !protectOnly.remove(itemId) && protectOnly.add(itemId);
		configManager.setConfiguration(CONFIG_GROUP, KEY, gson.toJson(protectOnly));
		return nowProtectOnly;
	}

	public synchronized boolean isProtectOnly(int itemId)
	{
		return protectOnly.contains(itemId);
	}

	public synchronized Set<Integer> snapshot()
	{
		return Collections.unmodifiableSet(new LinkedHashSet<>(protectOnly));
	}
}
