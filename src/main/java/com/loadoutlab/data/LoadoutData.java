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

	public List<MonsterStats> searchMonsters(String query, int limit)
	{
		String text = query == null ? "" : MonsterStats.normalizeQuery(query.trim());
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
