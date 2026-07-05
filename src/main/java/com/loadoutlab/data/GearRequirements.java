// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class GearRequirements
{
	public static final GearRequirements NONE = new GearRequirements(Collections.emptyMap(), Collections.emptySet());

	private final Map<String, Integer> skills;
	private final Set<String> quests;

	public GearRequirements(Map<String, Integer> skills, Set<String> quests)
	{
		this.skills = Collections.unmodifiableMap(new LinkedHashMap<>(skills == null ? Collections.emptyMap() : skills));
		this.quests = Collections.unmodifiableSet(new LinkedHashSet<>(quests == null ? Collections.emptySet() : quests));
	}

	public Map<String, Integer> getSkills()
	{
		return skills;
	}

	public Set<String> getQuests()
	{
		return quests;
	}

	public boolean isEmpty()
	{
		return skills.isEmpty() && quests.isEmpty();
	}
}
