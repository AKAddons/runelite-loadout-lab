package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.loadoutlab.data.GearSlot;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigManager;

/**
 * Per-monster user profiles: the preferences the optimizer cannot infer,
 * remembered per mob. Each profile carries PINS (slot -> item the player
 * always brings THERE - bracelet of slaughter class), a free-text NOTE
 * ("bring antidote++, pray melee after the spec"), and extra ITEM IDS
 * unioned into the Show-in-bank / Filter-bank sets (supplies the trip
 * needs that no loadout suggestion would ever contain).
 *
 * <p>Scope-free in v1 like dreams/exclusions: trip preferences follow
 * the player, and the RuneLite config profile isolates setups. Empty
 * profiles are pruned on save so the config never accumulates husks.
 */
public class MonsterProfileStore
{
	static final String CONFIG_GROUP = "loadoutlab";
	static final String KEY = "monsterProfiles";

	/** Serialized form: slot names and plain maps survive gson cleanly.
	 * Filter items carry their NAME (captured at add time from the item
	 * search) - resolving ids to names later needs the client thread. */
	private static final class Stored
	{
		Map<String, Integer> pins;
		String note;
		Map<Integer, String> filterItems;
	}

	private final ConfigManager configManager;
	private final Gson gson;
	private final Map<Integer, Stored> profiles = new HashMap<>();

	public MonsterProfileStore(ConfigManager configManager, Gson gson)
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
		profiles.clear();
		String json = configManager.getConfiguration(CONFIG_GROUP, KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}
		try
		{
			Map<Integer, Stored> stored = gson.fromJson(json,
				new TypeToken<Map<Integer, Stored>>(){}.getType());
			if (stored != null)
			{
				profiles.putAll(stored);
			}
		}
		catch (RuntimeException ex)
		{
			// Corrupt entry: start fresh rather than failing the plugin.
		}
	}

	public synchronized Map<GearSlot, Integer> pinsFor(int monsterId)
	{
		Stored profile = profiles.get(monsterId);
		if (profile == null || profile.pins == null || profile.pins.isEmpty())
		{
			return Collections.emptyMap();
		}
		EnumMap<GearSlot, Integer> pins = new EnumMap<>(GearSlot.class);
		for (Map.Entry<String, Integer> entry : profile.pins.entrySet())
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
		return Collections.unmodifiableMap(pins);
	}

	public synchronized void pin(int monsterId, GearSlot slot, int itemId)
	{
		Stored profile = profiles.computeIfAbsent(monsterId, id -> new Stored());
		if (profile.pins == null)
		{
			profile.pins = new LinkedHashMap<>();
		}
		profile.pins.put(slot.name(), itemId);
		save();
	}

	public synchronized void unpin(int monsterId, GearSlot slot)
	{
		Stored profile = profiles.get(monsterId);
		if (profile != null && profile.pins != null)
		{
			profile.pins.remove(slot.name());
			save();
		}
	}

	/** The user's note for this monster ("" when none). */
	public synchronized String noteFor(int monsterId)
	{
		Stored profile = profiles.get(monsterId);
		return profile == null || profile.note == null ? "" : profile.note;
	}

	public synchronized void setNote(int monsterId, String note)
	{
		Stored profile = profiles.computeIfAbsent(monsterId, id -> new Stored());
		profile.note = note == null || note.trim().isEmpty() ? null : note.trim();
		save();
	}

	/** Extra item ids unioned into Show-in-bank / Filter-bank sets. */
	public synchronized Set<Integer> filterItemsFor(int monsterId)
	{
		Stored profile = profiles.get(monsterId);
		return profile == null || profile.filterItems == null || profile.filterItems.isEmpty()
			? Collections.emptySet()
			: Collections.unmodifiableSet(new LinkedHashSet<>(profile.filterItems.keySet()));
	}

	/** Display names for the filter items, id -> name (add-time names). */
	public synchronized Map<Integer, String> filterItemNamesFor(int monsterId)
	{
		Stored profile = profiles.get(monsterId);
		return profile == null || profile.filterItems == null || profile.filterItems.isEmpty()
			? Collections.emptyMap()
			: Collections.unmodifiableMap(new LinkedHashMap<>(profile.filterItems));
	}

	public synchronized void addFilterItem(int monsterId, int itemId, String name)
	{
		Stored profile = profiles.computeIfAbsent(monsterId, id -> new Stored());
		if (profile.filterItems == null)
		{
			profile.filterItems = new LinkedHashMap<>();
		}
		profile.filterItems.put(itemId, name == null ? ("item " + itemId) : name);
		save();
	}

	public synchronized void removeFilterItem(int monsterId, int itemId)
	{
		Stored profile = profiles.get(monsterId);
		if (profile != null && profile.filterItems != null)
		{
			profile.filterItems.remove(itemId);
			save();
		}
	}

	private void save()
	{
		Map<Integer, Stored> out = new LinkedHashMap<>();
		for (Map.Entry<Integer, Stored> entry : profiles.entrySet())
		{
			Stored profile = entry.getValue();
			boolean empty = (profile.pins == null || profile.pins.isEmpty())
				&& (profile.note == null || profile.note.isEmpty())
				&& (profile.filterItems == null || profile.filterItems.isEmpty());
			if (!empty)
			{
				out.put(entry.getKey(), profile);
			}
		}
		profiles.keySet().retainAll(out.keySet());
		configManager.setConfiguration(CONFIG_GROUP, KEY, gson.toJson(out));
	}
}
