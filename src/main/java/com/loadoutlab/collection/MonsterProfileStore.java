package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.loadoutlab.data.GearSlot;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.client.config.ConfigManager;

/**
 * Per-monster user profiles: the preferences the optimizer cannot infer,
 * remembered per mob. Each profile carries PINS (slot -> item the player
 * always brings THERE), a free-text NOTE, and extra ITEMS unioned into
 * the Show-in-bank / Filter-bank sets (trip supplies no suggestion would
 * contain). Pins and filter items are SCOPED: "ALL" applies to every
 * style card, or a specific style ("MELEE"/"RANGED"/"MAGIC") applies to
 * that card only - a super combat for melee, a ranged potion for ranged.
 * The effective view overlays ALL with the style scope (style wins a
 * pin-slot collision).
 *
 * <p>Scope-free config in v1 like dreams/exclusions: trip preferences
 * follow the player. Empty profiles are pruned on save.
 */
public class MonsterProfileStore
{
	static final String CONFIG_GROUP = "loadoutlab";
	static final String KEY = "monsterProfiles";
	/** The every-style scope key. Style scopes use CombatStyle names. */
	public static final String ALL = "ALL";

	/** Serialized form: scope -> slot-name -> id for pins; scope -> id ->
	 * display name for filter items (names captured at add time - id
	 * resolution later needs the client thread). */
	private static final class Stored
	{
		Map<String, Map<String, Integer>> pins;
		String note;
		Map<String, Map<Integer, String>> filterItems;
		/** Pinned autocast spell name for the magic card ("" / null = auto). */
		String spell;
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

	/** Effective pins for one style card: ALL overlaid by the style scope. */
	public synchronized Map<GearSlot, Integer> pinsFor(int monsterId, String style)
	{
		Stored profile = profiles.get(monsterId);
		if (profile == null || profile.pins == null)
		{
			return Collections.emptyMap();
		}
		EnumMap<GearSlot, Integer> pins = new EnumMap<>(GearSlot.class);
		copyPins(profile.pins.get(ALL), pins);
		copyPins(profile.pins.get(style), pins);
		return pins.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(pins);
	}

	private static void copyPins(Map<String, Integer> from, EnumMap<GearSlot, Integer> into)
	{
		if (from == null)
		{
			return;
		}
		for (Map.Entry<String, Integer> entry : from.entrySet())
		{
			try
			{
				into.put(GearSlot.valueOf(entry.getKey()), entry.getValue());
			}
			catch (IllegalArgumentException ignored)
			{
				// Unknown slot name in config: drop that pin.
			}
		}
	}

	/** Raw pins by scope, for the manage menu: scope -> slot -> id. */
	public synchronized Map<String, Map<GearSlot, Integer>> allPins(int monsterId)
	{
		Stored profile = profiles.get(monsterId);
		if (profile == null || profile.pins == null || profile.pins.isEmpty())
		{
			return Collections.emptyMap();
		}
		Map<String, Map<GearSlot, Integer>> out = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, Integer>> scope : profile.pins.entrySet())
		{
			EnumMap<GearSlot, Integer> pins = new EnumMap<>(GearSlot.class);
			copyPins(scope.getValue(), pins);
			if (!pins.isEmpty())
			{
				out.put(scope.getKey(), Collections.unmodifiableMap(pins));
			}
		}
		return Collections.unmodifiableMap(out);
	}

	public synchronized void pin(int monsterId, String scope, GearSlot slot, int itemId)
	{
		Stored profile = profiles.computeIfAbsent(monsterId, id -> new Stored());
		if (profile.pins == null)
		{
			profile.pins = new LinkedHashMap<>();
		}
		profile.pins.computeIfAbsent(scope, s -> new LinkedHashMap<>())
			.put(slot.name(), itemId);
		save();
	}

	public synchronized void unpin(int monsterId, String scope, GearSlot slot)
	{
		Stored profile = profiles.get(monsterId);
		if (profile != null && profile.pins != null && profile.pins.get(scope) != null)
		{
			profile.pins.get(scope).remove(slot.name());
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

	/** The pinned autocast spell for this monster ("" = auto-pick). */
	public synchronized String pinnedSpellFor(int monsterId)
	{
		Stored profile = profiles.get(monsterId);
		return profile == null || profile.spell == null ? "" : profile.spell;
	}

	public synchronized void setPinnedSpell(int monsterId, String spellName)
	{
		Stored profile = profiles.computeIfAbsent(monsterId, id -> new Stored());
		profile.spell = spellName == null || spellName.trim().isEmpty()
			? null : spellName.trim();
		save();
	}

	/** Effective filter-item ids for one style card: ALL plus the style. */
	public synchronized Set<Integer> filterItemsFor(int monsterId, String style)
	{
		Stored profile = profiles.get(monsterId);
		if (profile == null || profile.filterItems == null)
		{
			return Collections.emptySet();
		}
		Set<Integer> ids = new LinkedHashSet<>();
		Map<Integer, String> all = profile.filterItems.get(ALL);
		if (all != null)
		{
			ids.addAll(all.keySet());
		}
		Map<Integer, String> styled = profile.filterItems.get(style);
		if (styled != null)
		{
			ids.addAll(styled.keySet());
		}
		return ids.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(ids);
	}

	/** Raw filter items by scope: scope -> id -> display name. */
	public synchronized Map<String, Map<Integer, String>> allFilterItems(int monsterId)
	{
		Stored profile = profiles.get(monsterId);
		if (profile == null || profile.filterItems == null || profile.filterItems.isEmpty())
		{
			return Collections.emptyMap();
		}
		Map<String, Map<Integer, String>> out = new LinkedHashMap<>();
		for (Map.Entry<String, Map<Integer, String>> scope : profile.filterItems.entrySet())
		{
			if (scope.getValue() != null && !scope.getValue().isEmpty())
			{
				out.put(scope.getKey(),
					Collections.unmodifiableMap(new LinkedHashMap<>(scope.getValue())));
			}
		}
		return Collections.unmodifiableMap(out);
	}

	public synchronized void addFilterItem(int monsterId, String scope, int itemId, String name)
	{
		Stored profile = profiles.computeIfAbsent(monsterId, id -> new Stored());
		if (profile.filterItems == null)
		{
			profile.filterItems = new LinkedHashMap<>();
		}
		profile.filterItems.computeIfAbsent(scope, s -> new LinkedHashMap<>())
			.put(itemId, name == null ? ("item " + itemId) : name);
		save();
	}

	public synchronized void removeFilterItem(int monsterId, String scope, int itemId)
	{
		Stored profile = profiles.get(monsterId);
		if (profile != null && profile.filterItems != null
			&& profile.filterItems.get(scope) != null)
		{
			profile.filterItems.get(scope).remove(itemId);
			save();
		}
	}

	private void save()
	{
		Map<Integer, Stored> out = new LinkedHashMap<>();
		for (Map.Entry<Integer, Stored> entry : profiles.entrySet())
		{
			Stored profile = entry.getValue();
			prune(profile.pins);
			prune(profile.filterItems);
			boolean empty = (profile.pins == null || profile.pins.isEmpty())
				&& (profile.note == null || profile.note.isEmpty())
				&& (profile.spell == null || profile.spell.isEmpty())
				&& (profile.filterItems == null || profile.filterItems.isEmpty());
			if (!empty)
			{
				out.put(entry.getKey(), profile);
			}
		}
		profiles.keySet().retainAll(out.keySet());
		configManager.setConfiguration(CONFIG_GROUP, KEY, gson.toJson(out));
	}

	private static void prune(Map<String, ? extends Map<?, ?>> scoped)
	{
		if (scoped != null)
		{
			scoped.values().removeIf(m -> m == null || m.isEmpty());
		}
	}
}
