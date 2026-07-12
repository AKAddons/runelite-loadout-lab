// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class MonsterStats
{
	private final int id;
	private final String name;
	private final String version;
	private final int combatLevel;
	private final int hitpoints;
	private final int size;
	private final int defence;
	private final int magic;
	private final int offensiveMagic;
	private final MonsterDefences defensive;
	private final MonsterOffence offence;
	private final List<String> attributes;
	private final java.util.Set<String> attributesLower;
	private final boolean slayerMonster;
	private final String weaknessElement;
	private final int weaknessSeverity;
	private final String nameLower;
	private final boolean wilderness;
	private final boolean revenant;

	public MonsterStats(
		int id,
		String name,
		String version,
		int combatLevel,
		int hitpoints,
		int defence,
		int magic,
		MonsterDefences defensive,
		List<String> attributes)
	{
		this(id, name, version, combatLevel, hitpoints, 1, defence, magic, 0, defensive,
			MonsterOffence.NONE, attributes, false, "", 0);
	}

	public MonsterStats(
		int id,
		String name,
		String version,
		int combatLevel,
		int hitpoints,
		int size,
		int defence,
		int magic,
		int offensiveMagic,
		MonsterDefences defensive,
		MonsterOffence offence,
		List<String> attributes,
		boolean slayerMonster,
		String weaknessElement,
		int weaknessSeverity)
	{
		this.id = id;
		this.name = name == null ? "" : name;
		this.version = version == null ? "" : version;
		this.combatLevel = combatLevel;
		this.hitpoints = hitpoints;
		this.size = Math.max(1, size);
		this.defence = defence;
		this.magic = magic;
		this.offensiveMagic = offensiveMagic;
		this.defensive = defensive == null ? MonsterDefences.ZERO : defensive;
		this.offence = offence == null ? MonsterOffence.NONE : offence;
		this.attributes = attributes == null ? Collections.emptyList() : Collections.unmodifiableList(attributes);
		this.slayerMonster = slayerMonster;
		this.weaknessElement = weaknessElement == null ? "" : weaknessElement.toLowerCase(Locale.ROOT);
		this.weaknessSeverity = Math.max(0, weaknessSeverity);
		// hasAttribute runs per candidate set in the optimizer's inner loop;
		// lowercase once instead of per query.
		java.util.HashSet<String> lower = new java.util.HashSet<>();
		for (String value : this.attributes)
		{
			if (value != null)
			{
				lower.add(value.toLowerCase(Locale.ROOT));
			}
		}
		this.attributesLower = lower;
		// The revenant/wilderness gates lowercase the name per DPS trial -
		// that allocation was ~20% of optimizer samples. Once, here.
		this.nameLower = this.name.toLowerCase(Locale.ROOT);
		this.wilderness = WildernessMonsters.containsName(this.nameLower);
		this.revenant = this.nameLower.startsWith("revenant");
	}

	/** Lowercased monster name, cached (per-trial engine gates). */
	public String getNameLower()
	{
		return nameLower;
	}

	/** Fought in the Wilderness (see WildernessMonsters) - cached, the
	 * wilderness-weapon gate asks several times per DPS trial. */
	public boolean isWildernessMonster()
	{
		return wilderness;
	}

	/** Name starts with "revenant" - the avarice/ethereum gates. */
	public boolean isRevenantMonster()
	{
		return revenant;
	}

	/** A copy at a different Defence level - defence-drain spec modeling. */
	public MonsterStats withDefence(int newDefence)
	{
		return new MonsterStats(id, name, version, combatLevel, hitpoints, size,
			Math.max(0, newDefence), magic, offensiveMagic, defensive, offence,
			attributes, slayerMonster, weaknessElement, weaknessSeverity);
	}

	public boolean hasAttribute(String attribute)
	{
		return attributesLower.contains(attribute.toLowerCase(Locale.ROOT));
	}

	public String label()
	{
		// Level-derived version labels ("Level 137") are redundant with -
		// and after group-collapsing can contradict - the lvl suffix.
		boolean levelVersion = version.regionMatches(true, 0, "level", 0, 5);
		String suffix = version.isEmpty() || levelVersion ? "" : " (" + version + ")";
		String level = combatLevel > 0 ? " - lvl " + combatLevel : "";
		return name + suffix + level;
	}

	public String searchText()
	{
		return normalizeQuery(name + " " + version + " " + id);
	}

	/** Search matching ignores punctuation: "kril" finds K'ril Tsutsaroth,
	 * "kreearra" finds Kree'arra. */
	public static String normalizeQuery(String text)
	{
		StringBuilder sb = new StringBuilder(text.length());
		for (char c : text.toLowerCase(Locale.ROOT).toCharArray())
		{
			if (Character.isLetterOrDigit(c) || c == ' ')
			{
				sb.append(c);
			}
		}
		return sb.toString();
	}

	public int getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	public String getVersion()
	{
		return version;
	}

	public int getCombatLevel()
	{
		return combatLevel;
	}

	public int getHitpoints()
	{
		return hitpoints;
	}

	public int getSize()
	{
		return size;
	}

	public int getDefence()
	{
		return defence;
	}

	public int getMagic()
	{
		return magic;
	}

	public int getOffensiveMagic()
	{
		return offensiveMagic;
	}

	public MonsterDefences getDefensive()
	{
		return defensive;
	}

	public MonsterOffence getOffence()
	{
		return offence;
	}

	public List<String> getAttributes()
	{
		return attributes;
	}

	public boolean isSlayerMonster()
	{
		return slayerMonster;
	}

	public String getWeaknessElement()
	{
		return weaknessElement;
	}

	public int getWeaknessSeverity()
	{
		return weaknessSeverity;
	}
}
