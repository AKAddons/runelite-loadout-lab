// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;

import lombok.Getter;

public final class MonsterStats
{
	@Getter
	private final int id;
	@Getter
	private final String name;
	@Getter
	private final String version;
	@Getter
	private final int combatLevel;
	@Getter
	private final int hitpoints;
	@Getter
	private final int size;
	@Getter
	private final int defence;
	@Getter
	private final int magic;
	@Getter
	private final int offensiveMagic;
	@Getter
	private final MonsterDefences defensive;
	@Getter
	private final MonsterOffence offence;
	@Getter
	private final List<String> attributes;
	private final Set<String> attributesLower;
	@Getter
	private final boolean slayerMonster;
	@Getter
	private final String weaknessElement;
	@Getter
	private final int weaknessSeverity;
	@Getter
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
		HashSet<String> lower = new HashSet<>();
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
	/** Same sheet under a different version label (load-time
	 * normalization of quest/post-quest noise). */
	public MonsterStats withVersion(String newVersion)
	{
		return new MonsterStats(id, name, newVersion, combatLevel, hitpoints,
			size, defence, magic, offensiveMagic, defensive, offence, attributes,
			slayerMonster, weaknessElement, weaknessSeverity);
	}

	public MonsterStats immuneVariant(int syntheticId, String versionLabel, String immuneAttribute)
	{
		return immuneVariant(syntheticId, versionLabel,
			Collections.singletonList(immuneAttribute));
	}

	/** Multi-immunity variant: a phase can lock out SEVERAL styles at
	 * once (Kalphite Queen's first form prays off magic AND ranged; a
	 * Nylocas form takes only its own style). */
	public MonsterStats immuneVariant(int syntheticId, String versionLabel,
		List<String> immuneAttributes)
	{
		List<String> extended = new ArrayList<>(attributes);
		extended.addAll(immuneAttributes);
		return new MonsterStats(syntheticId, name, versionLabel, combatLevel, hitpoints,
			size, defence, magic, offensiveMagic, defensive, offence, extended,
			slayerMonster, weaknessElement, weaknessSeverity);
	}

	/** Lowercased monster name, cached (per-trial engine gates). */

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














}
