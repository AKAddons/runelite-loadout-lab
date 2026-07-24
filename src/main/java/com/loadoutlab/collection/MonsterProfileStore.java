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
public class MonsterProfileStore extends ConfigJsonStore<Integer, MonsterProfileStore.Stored>
{
	static final String KEY = "monsterProfiles";
	/** The every-style scope key. Style scopes use CombatStyle names. */
	public static final String ALL = "ALL";

	/** Serialized form: scope -> slot-name -> id for pins; scope -> id ->
	 * display name for filter items (names captured at add time - id
	 * resolution later needs the client thread). */
	static final class Stored
	{
		Map<String, Map<String, Integer>> pins;
		String note;
		Map<String, Map<Integer, String>> filterItems;
		/** Per-mob simulated items (id -> add-time name) - counted as
		 * owned for THIS mob only. */
		Map<Integer, String> sims;
		/** Pinned autocast spell name for the magic card ("" / null = auto). */
		String spell;
		/** Per-mob exclusions: scope -> item ids the suggestions here must
		 * never contain (the global exclusion list handles "everywhere"). */
		Map<String, Set<Integer>> exclusions;
		/** Per-mob trip-supply overrides: TripSupplies category ->
		 * mode/option key ("DETECT_BEST", "NONE", "SANFEW_SERUM"...).
		 * Absent category = the wrench-panel default applies. */
		Map<String, String> supplies;
	}

	private final Map<Integer, Stored> profiles = map;

	public MonsterProfileStore(ConfigManager configManager, Gson gson)
	{
		super(configManager, gson, KEY, new TypeToken<Map<Integer, Stored>>(){}.getType());
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

	/** Effective per-mob exclusions for one style card: ALL plus the style
	 * scope. The caller unions in the global exclusion list. */
	public synchronized Set<Integer> exclusionsFor(int monsterId, String style)
	{
		Stored profile = profiles.get(monsterId);
		if (profile == null || profile.exclusions == null)
		{
			return Collections.emptySet();
		}
		LinkedHashSet<Integer> merged = new LinkedHashSet<>();
		Set<Integer> all = profile.exclusions.get(ALL);
		if (all != null)
		{
			merged.addAll(all);
		}
		Set<Integer> scoped = profile.exclusions.get(style);
		if (scoped != null)
		{
			merged.addAll(scoped);
		}
		return merged.isEmpty() ? Collections.emptySet() : Collections.unmodifiableSet(merged);
	}

	/** Raw per-mob exclusions by scope, for the manage menu. */
	public synchronized Map<String, Set<Integer>> allExclusions(int monsterId)
	{
		Stored profile = profiles.get(monsterId);
		if (profile == null || profile.exclusions == null || profile.exclusions.isEmpty())
		{
			return Collections.emptyMap();
		}
		Map<String, Set<Integer>> out = new LinkedHashMap<>();
		for (Map.Entry<String, Set<Integer>> scope : profile.exclusions.entrySet())
		{
			if (scope.getValue() != null && !scope.getValue().isEmpty())
			{
				out.put(scope.getKey(),
					Collections.unmodifiableSet(new LinkedHashSet<>(scope.getValue())));
			}
		}
		return Collections.unmodifiableMap(out);
	}

	/** Per-mob sims: counted as owned for THIS mob only. */
	public synchronized Set<Integer> simsFor(int monsterId)
	{
		Stored profile = profiles.get(monsterId);
		if (profile == null || profile.sims == null || profile.sims.isEmpty())
		{
			return Collections.emptySet();
		}
		return Collections.unmodifiableSet(new LinkedHashSet<>(profile.sims.keySet()));
	}

	/** Raw per-mob sims (id -> add-time name), for the manage menu. */
	public synchronized Map<Integer, String> allSims(int monsterId)
	{
		Stored profile = profiles.get(monsterId);
		if (profile == null || profile.sims == null || profile.sims.isEmpty())
		{
			return Collections.emptyMap();
		}
		return Collections.unmodifiableMap(new LinkedHashMap<>(profile.sims));
	}

	public synchronized void addSim(int monsterId, int itemId, String name)
	{
		Stored profile = profiles.computeIfAbsent(monsterId, id -> new Stored());
		if (profile.sims == null)
		{
			profile.sims = new LinkedHashMap<>();
		}
		profile.sims.put(itemId, name == null ? ("item " + itemId) : name);
		save();
	}

	public synchronized void removeSim(int monsterId, int itemId)
	{
		Stored profile = profiles.get(monsterId);
		if (profile != null && profile.sims != null)
		{
			profile.sims.remove(itemId);
			save();
		}
	}

	/** Per-mob trip-supply overrides (category -> mode/option key);
	 * empty when the wrench-panel defaults apply untouched. */
	public synchronized Map<String, String> supplies(int monsterId)
	{
		Stored profile = profiles.get(monsterId);
		if (profile == null || profile.supplies == null || profile.supplies.isEmpty())
		{
			return Collections.emptyMap();
		}
		return Collections.unmodifiableMap(new LinkedHashMap<>(profile.supplies));
	}

	/** Set one category's override; null/empty choice returns the category
	 * to the wrench-panel default. */
	public synchronized void setSupply(int monsterId, String category, String choice)
	{
		if (choice == null || choice.isEmpty())
		{
			Stored existing = profiles.get(monsterId);
			if (existing != null && existing.supplies != null)
			{
				existing.supplies.remove(category);
				save();
			}
			return;
		}
		Stored profile = profiles.computeIfAbsent(monsterId, id -> new Stored());
		if (profile.supplies == null)
		{
			profile.supplies = new LinkedHashMap<>();
		}
		profile.supplies.put(category, choice);
		save();
	}

	public synchronized void exclude(int monsterId, String scope, int itemId)
	{
		Stored profile = profiles.computeIfAbsent(monsterId, id -> new Stored());
		if (profile.exclusions == null)
		{
			profile.exclusions = new LinkedHashMap<>();
		}
		profile.exclusions.computeIfAbsent(scope, s -> new LinkedHashSet<>()).add(itemId);
		save();
	}

	public synchronized void removeExclusion(int monsterId, String scope, int itemId)
	{
		Stored profile = profiles.get(monsterId);
		if (profile != null && profile.exclusions != null
			&& profile.exclusions.get(scope) != null)
		{
			profile.exclusions.get(scope).remove(itemId);
			save();
		}
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

	@Override
	void save()
	{
		Map<Integer, Stored> out = new LinkedHashMap<>();
		for (Map.Entry<Integer, Stored> entry : profiles.entrySet())
		{
			Stored profile = entry.getValue();
			prune(profile.pins);
			prune(profile.filterItems);
			if (profile.exclusions != null)
			{
				profile.exclusions.values().removeIf(s -> s == null || s.isEmpty());
			}
			// EVERY field participates (field bug 2026-07-18: sims was
			// missing here, so a sims-only profile was judged empty and the
			// retainAll below erased the sim in the same call that added it
			// - "Sim here" appeared to do nothing).
			boolean empty = (profile.pins == null || profile.pins.isEmpty())
				&& (profile.note == null || profile.note.isEmpty())
				&& (profile.spell == null || profile.spell.isEmpty())
				&& (profile.filterItems == null || profile.filterItems.isEmpty())
				&& (profile.exclusions == null || profile.exclusions.isEmpty())
				&& (profile.sims == null || profile.sims.isEmpty())
				&& (profile.supplies == null || profile.supplies.isEmpty());
			if (!empty)
			{
				out.put(entry.getKey(), profile);
			}
		}
		profiles.keySet().retainAll(out.keySet());
		saveJson(gson.toJson(out));
	}

	private static void prune(Map<String, ? extends Map<?, ?>> scoped)
	{
		if (scoped != null)
		{
			scoped.values().removeIf(m -> m == null || m.isEmpty());
		}
	}
}
