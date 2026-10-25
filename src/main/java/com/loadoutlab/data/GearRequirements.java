// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

import java.util.*;

import lombok.Getter;

public final class GearRequirements {
	public static final GearRequirements NONE = new GearRequirements(Map.of(), Set.of());

	private final Map<String, Integer> skills;
	@Getter
	private final Set<String> quests;

	public GearRequirements(Map<String, Integer> skills, Set<String> quests) {
		this.skills = Collections.unmodifiableMap(new LinkedHashMap<>(skills == null ? Map.of() : skills));
		this.quests = Collections.unmodifiableSet(new LinkedHashSet<>(quests == null ? Set.of() : quests));
	}

	public Map<String, Integer> getSkills() { return skills; }


	public boolean isEmpty() { return skills.isEmpty() && quests.isEmpty(); }
}
