// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class LoadoutData
{
	private final List<GearItem> gearItems;
	private final List<MonsterStats> monsters;
	private final List<SpellStats> spells;
	private final Map<Integer, GearItem> gearById;
	private final Map<Integer, Integer> variantToBase;

	LoadoutData(
		List<GearItem> gearItems,
		List<MonsterStats> monsters,
		List<SpellStats> spells,
		Map<Integer, GearItem> gearById,
		Map<Integer, Integer> variantToBase)
	{
		this.gearItems = Collections.unmodifiableList(gearItems);
		this.monsters = Collections.unmodifiableList(monsters);
		this.spells = Collections.unmodifiableList(spells);
		this.gearById = Collections.unmodifiableMap(gearById);
		this.variantToBase = Collections.unmodifiableMap(variantToBase);
	}

	/**
	 * A view of this dataset containing only free-to-play gear (monsters and
	 * spells unchanged) - drives the non-members filter.
	 */
	public LoadoutData freeToPlayView()
	{
		java.util.List<GearItem> free = new java.util.ArrayList<>();
		java.util.Map<Integer, GearItem> byId = new java.util.HashMap<>();
		for (GearItem g : gearItems)
		{
			if (!g.isMembers())
			{
				free.add(g);
				byId.put(g.getId(), g);
			}
		}
		return new LoadoutData(free, monsters, spells, byId, variantToBase);
	}

	/**
	 * True for ornament/locked/degraded variants of a base item with the
	 * same stats - never suggested; the base version stands in for them.
	 */
	public boolean isVariant(int itemId)
	{
		return variantToBase.containsKey(itemId);
	}

	/**
	 * Owned quantities with every variant also credited to its base item,
	 * so an owned "Abyssal whip (or)" satisfies an "Abyssal whip" pick.
	 */
	public Map<Integer, Integer> canonicalizeOwned(Map<Integer, Integer> owned)
	{
		java.util.Map<Integer, Integer> result = new java.util.HashMap<>(owned);
		for (Map.Entry<Integer, Integer> entry : owned.entrySet())
		{
			Integer base = variantToBase.get(entry.getKey());
			if (base != null)
			{
				result.merge(base, entry.getValue(), Integer::sum);
			}
		}
		return result;
	}

	/**
	 * Community nicknames and activity names -> the monster to show.
	 * Raids and wave activities map to their final boss - a defensible
	 * anchor for "what do I wear there". Keys and values are in
	 * normalizeQuery form.
	 */
	private static final Map<String, String> SEARCH_ALIASES = Map.ofEntries(
		Map.entry("thermy", "thermonuclear smoke devil"),
		Map.entry("grotesque guardians", "dusk"),
		Map.entry("fight caves", "tztokjad"),
		Map.entry("the fight caves", "tztokjad"),
		Map.entry("inferno", "tzkalzuk"),
		Map.entry("the inferno", "tzkalzuk"),
		Map.entry("gauntlet", "crystalline hunllef"),
		Map.entry("the gauntlet", "crystalline hunllef"),
		Map.entry("corrupted gauntlet", "corrupted hunllef"),
		Map.entry("cox", "great olm"),
		Map.entry("chambers of xeric", "great olm"),
		Map.entry("toa", "tumekens warden"),
		Map.entry("tombs of amascut", "tumekens warden"),
		Map.entry("tob", "verzik vitur"),
		Map.entry("theatre of blood", "verzik vitur"),
		Map.entry("colosseum", "sol heredit"),
		Map.entry("fortis colosseum", "sol heredit"),
		Map.entry("fortis colosseum waves", "sol heredit"),
		Map.entry("duke", "duke sucellus"),
		Map.entry("kbd", "king black dragon"),
		Map.entry("kq", "kalphite queen"));

	public List<MonsterStats> searchMonsters(String query, int limit)
	{
		String text = query == null ? "" : MonsterStats.normalizeQuery(query.trim());
		text = SEARCH_ALIASES.getOrDefault(text, text);
		if (text.isEmpty())
		{
			return Collections.emptyList();
		}

		java.util.ArrayList<MonsterStats> exact = new java.util.ArrayList<>();
		java.util.ArrayList<MonsterStats> prefix = new java.util.ArrayList<>();
		java.util.ArrayList<MonsterStats> contains = new java.util.ArrayList<>();
		for (MonsterStats monster : monsters)
		{
			String name = MonsterStats.normalizeQuery(monster.getName());
			if (name.equals(text) || String.valueOf(monster.getId()).equals(text))
			{
				exact.add(monster);
			}
			else if (name.startsWith(text))
			{
				prefix.add(monster);
			}
			else if (monster.searchText().contains(text))
			{
				contains.add(monster);
			}
		}

		java.util.ArrayList<MonsterStats> result = new java.util.ArrayList<>(limit);
		addLimited(result, exact, limit);
		addLimited(result, prefix, limit);
		addLimited(result, contains, limit);
		return result;
	}

	private static void addLimited(List<MonsterStats> target, List<MonsterStats> source, int limit)
	{
		for (MonsterStats monster : source)
		{
			if (target.size() >= limit)
			{
				return;
			}
			target.add(monster);
		}
	}

	/**
	 * All ids interchangeable with this one for OWNERSHIP purposes: the
	 * base plus every variant that canonicalizes to it - so a bank
	 * highlight for "Abyssal whip" also lights the (or) version.
	 */
	public java.util.Set<Integer> equivalentIds(int itemId)
	{
		Integer base = variantToBase.getOrDefault(itemId, itemId);
		java.util.Set<Integer> ids = new java.util.HashSet<>();
		ids.add(itemId);
		ids.add(base);
		for (Map.Entry<Integer, Integer> entry : variantToBase.entrySet())
		{
			if (entry.getValue().equals(base))
			{
				ids.add(entry.getKey());
			}
		}
		return ids;
	}

	public GearItem getGear(int id)
	{
		return gearById.get(id);
	}

	public List<GearItem> getGearItems()
	{
		return gearItems;
	}

	public List<MonsterStats> getMonsters()
	{
		return monsters;
	}

	public List<SpellStats> getSpells()
	{
		return spells;
	}
}
