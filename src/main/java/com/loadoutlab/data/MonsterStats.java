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

	/**
	 * A synthetic per-phase variant (M-3 groups): the same stat sheet under
	 * a new id + version label, with an immunity attribute the engine
	 * honors ("immune_melee"...). Tormented demons' shield rotation is the
	 * flagship: one variant per shielded style, so a roster shows the best
	 * set for each phase. The NAME is preserved - name-keyed rules (the
	 * TD damage reduction, boss overrides) keep applying.
	 */
	public MonsterStats immuneVariant(int syntheticId, String versionLabel, String immuneAttribute)
	{
		return immuneVariant(syntheticId, versionLabel,
			java.util.Collections.singletonList(immuneAttribute));
	}

	/** Multi-immunity variant: a phase can lock out SEVERAL styles at
	 * once (Kalphite Queen's first form prays off magic AND ranged; a
	 * Nylocas form takes only its own style). */
	public MonsterStats immuneVariant(int syntheticId, String versionLabel,
		java.util.List<String> immuneAttributes)
	{
		java.util.List<String> extended = new java.util.ArrayList<>(attributes);
		extended.addAll(immuneAttributes);
		return new MonsterStats(syntheticId, name, versionLabel, combatLevel, hitpoints,
			size, defence, magic, offensiveMagic, defensive, offence, extended,
			slayerMonster, weaknessElement, weaknessSeverity);
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

	/** Synthetic phase-variant ids live above this base (M-3 groups):
	 * base + realId * 10 + styleOrdinal. */
	public static final int SYNTHETIC_ID_BASE = 9_000_000;

	/** The id user-profile data (pins, exclusions, notes) attaches to -
	 * a synthetic phase variant maps back to its real monster, so a
	 * profile set on the plain mob follows it into groups. */
	public int profileId()
	{
		return id >= SYNTHETIC_ID_BASE ? (id - SYNTHETIC_ID_BASE) / 10 : id;
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
