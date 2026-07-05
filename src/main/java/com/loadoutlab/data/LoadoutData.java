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

	LoadoutData(
		List<GearItem> gearItems,
		List<MonsterStats> monsters,
		List<SpellStats> spells,
		Map<Integer, GearItem> gearById)
	{
		this.gearItems = Collections.unmodifiableList(gearItems);
		this.monsters = Collections.unmodifiableList(monsters);
		this.spells = Collections.unmodifiableList(spells);
		this.gearById = Collections.unmodifiableMap(gearById);
	}

	public List<MonsterStats> searchMonsters(String query, int limit)
	{
		String text = query == null ? "" : query.trim().toLowerCase();
		if (text.isEmpty())
		{
			return Collections.emptyList();
		}

		java.util.ArrayList<MonsterStats> exact = new java.util.ArrayList<>();
		java.util.ArrayList<MonsterStats> prefix = new java.util.ArrayList<>();
		java.util.ArrayList<MonsterStats> contains = new java.util.ArrayList<>();
		for (MonsterStats monster : monsters)
		{
			String name = monster.getName().toLowerCase();
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
