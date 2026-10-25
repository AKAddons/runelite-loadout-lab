// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import com.loadoutlab.data.*;
import java.util.*;
import net.runelite.api.*;

public final class RequirementProfile {
	public static final RequirementProfile MAXED = new RequirementProfile(maxedLevels(), allQuests());

	private final Map<Skill, Integer> levels;
	private final Set<String> completedQuests;

	/** Items worn whatever their requirements: the simmed max capes, whose
	 * 99-in-every-skill rule the seven-level stat sim cannot meet
	 * (ISS-01M5RYC2TB43AEGQ96TTNEKEJN). */
	private final Set<Integer> waived;

	public RequirementProfile(Map<Skill, Integer> levels, Set<String> completedQuests) { this(levels, completedQuests, Set.of()); }

	private RequirementProfile(Map<Skill, Integer> levels, Set<String> completedQuests, Set<Integer> waived) {
		this.waived = Set.copyOf(waived);
		var copy = new EnumMap<Skill, Integer>(Skill.class);
		if (levels != null) { copy.putAll(levels); }
		this.levels = Collections.unmodifiableMap(copy);
		this.completedQuests = Collections.unmodifiableSet(new HashSet<>(completedQuests == null ? Set.of() : completedQuests));
	}

	/** The same levels with every quest done - the game-best ceiling never
	 * trims on the quest log (Andrew 2026-09-09). */
	public RequirementProfile withAllQuests() { return new RequirementProfile(levels, allQuests(), waived); }

	public RequirementProfile waiving(Set<Integer> ids) { return new RequirementProfile(levels, completedQuests, ids); }

	public boolean canWear(GearItem item) { return waived.contains(item.getId()) || canEquip(item.getRequirements()); }

	/** The same quests with the seven combat levels replaced: the stat
	 * sim's gear gate (ISS-01M5QY13K6YHTYDW1CCYD30CSY) - 99 Attack admits the
	 * fang, 60 drops it; the other skills ride along unchanged. */
	public RequirementProfile withLevels(PlayerLevels simmed) {
		Map<Skill, Integer> next = new EnumMap<>(Skill.class);
		next.putAll(levels);
		next.put(Skill.ATTACK, simmed.getAttack());
		next.put(Skill.STRENGTH, simmed.getStrength());
		next.put(Skill.DEFENCE, simmed.getDefence());
		next.put(Skill.RANGED, simmed.getRanged());
		next.put(Skill.MAGIC, simmed.getMagic());
		next.put(Skill.PRAYER, simmed.getPrayer());
		next.put(Skill.HITPOINTS, simmed.getHitpoints());
		return new RequirementProfile(next, completedQuests, waived);
	}

	/** Both already unmodifiable (the constructor wraps its copies). */
	public Map<Skill, Integer> getLevels() { return levels; }

	public Set<String> getCompletedQuests() { return completedQuests; }

	public boolean canEquip(GearRequirements requirements) {
		if (requirements == null || requirements.isEmpty()) { return true; }
		for (var entry : requirements.getSkills().entrySet()) {
			Skill skill = skill(entry.getKey());
			if (skill != null && levels.getOrDefault(skill, 1) < entry.getValue()) { return false; }
		}
		for (String quest : requirements.getQuests()) {
			if (!completedQuests.contains(quest)) { return false; }
		}
		return true;
	}

	int level(Skill skill) { return levels.getOrDefault(skill, 1); }

	private static Skill skill(String name) {
		if (name == null) { return null; }
		String normalized = name.toUpperCase(Locale.ROOT);
		if ("RUNECRAFTING".equals(normalized)) { normalized = "RUNECRAFT"; }
		try {
			return Skill.valueOf(normalized);
		} catch (IllegalArgumentException ex) { return null; }
	}

	private static Map<Skill, Integer> maxedLevels() {
		var result = new EnumMap<Skill, Integer>(Skill.class);
		for (Skill skill : Skill.values()) {
			if (skill != Skill.OVERALL) { result.put(skill, 99); }
		}
		return result;
	}

	private static Set<String> allQuests() {
		Set<String> result = new HashSet<>();
		for (Quest quest : Quest.values()) { result.add(quest.name()); }
		return result;
	}
}
