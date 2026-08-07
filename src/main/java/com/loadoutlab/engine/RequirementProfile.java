// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import com.loadoutlab.data.GearRequirements;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Quest;
import net.runelite.api.Skill;

public final class RequirementProfile
{
	public static final RequirementProfile MAXED = new RequirementProfile(maxedLevels(), allQuests());

	private final Map<Skill, Integer> levels;
	private final Set<String> completedQuests;

	public RequirementProfile(Map<Skill, Integer> levels, Set<String> completedQuests)
	{
		EnumMap<Skill, Integer> copy = new EnumMap<>(Skill.class);
		if (levels != null)
		{
			copy.putAll(levels);
		}
		this.levels = Collections.unmodifiableMap(copy);
		this.completedQuests = Collections.unmodifiableSet(new HashSet<>(completedQuests == null ? Collections.emptySet() : completedQuests));
	}

	public Map<Skill, Integer> getLevels()
	{
		return Collections.unmodifiableMap(levels);
	}

	public Set<String> getCompletedQuests()
	{
		return Collections.unmodifiableSet(completedQuests);
	}

	public boolean canEquip(GearRequirements requirements)
	{
		if (requirements == null || requirements.isEmpty())
		{
			return true;
		}
		for (Map.Entry<String, Integer> entry : requirements.getSkills().entrySet())
		{
			Skill skill = skill(entry.getKey());
			if (skill != null && levels.getOrDefault(skill, 1) < entry.getValue())
			{
				return false;
			}
		}
		for (String quest : requirements.getQuests())
		{
			if (!completedQuests.contains(quest))
			{
				return false;
			}
		}
		return true;
	}

	int level(Skill skill)
	{
		return levels.getOrDefault(skill, 1);
	}

	private static Skill skill(String name)
	{
		if (name == null)
		{
			return null;
		}
		String normalized = name.toUpperCase(Locale.ROOT);
		if ("RUNECRAFTING".equals(normalized))
		{
			normalized = "RUNECRAFT";
		}
		try
		{
			return Skill.valueOf(normalized);
		}
		catch (IllegalArgumentException ex)
		{
			return null;
		}
	}

	private static Map<Skill, Integer> maxedLevels()
	{
		EnumMap<Skill, Integer> result = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			if (skill != Skill.OVERALL)
			{
				result.put(skill, 99);
			}
		}
		return result;
	}

	private static Set<String> allQuests()
	{
		Set<String> result = new HashSet<>();
		for (Quest quest : Quest.values())
		{
			result.add(quest.name());
		}
		return result;
	}
}
