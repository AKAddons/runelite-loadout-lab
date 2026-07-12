package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.loadoutlab.data.GearSlot;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.client.config.ConfigManager;

/**
 * Pinned items: slot -> the item the player ALWAYS brings (bracelet of
 * slaughter class - value the optimizer cannot price, so user preference
 * wins the slot and the search builds around it). One pin per slot.
 *
 * <p>Scope-free in v1 like dreams/exclusions (trip preferences follow
 * the player, and the RuneLite config profile already isolates setups).
 */
public class PinStore
{
	static final String CONFIG_GROUP = "loadoutlab";
	static final String KEY = "pinnedItems";

	private final ConfigManager configManager;
	private final Gson gson;
	private final Map<GearSlot, Integer> pins = new EnumMap<>(GearSlot.class);

	public PinStore(ConfigManager configManager, Gson gson)
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
		pins.clear();
		String json = configManager.getConfiguration(CONFIG_GROUP, KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			Map<String, Integer> stored = gson.fromJson(json,
				new TypeToken<Map<String, Integer>>(){}.getType());
			if (stored != null)
			{
				for (Map.Entry<String, Integer> entry : stored.entrySet())
				{
					try
					{
						pins.put(GearSlot.valueOf(entry.getKey()), entry.getValue());
					}
					catch (IllegalArgumentException ignored)
					{
						// Unknown slot name in config: drop that pin.
					}
				}
			}
		}
		catch (RuntimeException ex)
		{
			// Corrupt entry: start fresh rather than failing the plugin.
		}
	}

	/** Pin an item into its slot, replacing any previous pin there. */
	public synchronized void pin(GearSlot slot, int itemId)
	{
		pins.put(slot, itemId);
		save();
	}

	public synchronized void unpin(GearSlot slot)
	{
		pins.remove(slot);
		save();
	}

	public synchronized Integer pinnedFor(GearSlot slot)
	{
		return pins.get(slot);
	}

	public synchronized void clear()
	{
		pins.clear();
		save();
	}

	public synchronized Map<GearSlot, Integer> snapshot()
	{
		return pins.isEmpty() ? Collections.emptyMap()
			: Collections.unmodifiableMap(new EnumMap<>(pins));
	}

	private void save()
	{
		Map<String, Integer> out = new java.util.LinkedHashMap<>();
		for (Map.Entry<GearSlot, Integer> entry : pins.entrySet())
		{
			out.put(entry.getKey().name(), entry.getValue());
		}
		configManager.setConfiguration(CONFIG_GROUP, KEY, gson.toJson(out));
	}
}
