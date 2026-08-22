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
		MonsterStats copy = new MonsterStats(id, name, newVersion, combatLevel, hitpoints,
			size, defence, magic, offensiveMagic, defensive, offence, attributes,
			slayerMonster, weaknessElement, weaknessSeverity);
		copy.wikiVersion = getWikiVersion();
		return copy;
	}

	/** The RAW wiki version string ("Post-quest, Awake") - what the
	 * official data and calculator key on. Display renames
	 * (normalizeQuestVersions) preserve it; null = never renamed.
	 * Field 2026-08-21: exporting the DISPLAY version made the calc's
	 * import miss and silently fall back to the FIRST same-id row -
	 * the post-quest Duke opened as Awakened. */
	private String wikiVersion;

	public String getWikiVersion()
	{
		return wikiVersion == null ? version : wikiVersion;
	}

	/** What the label SHOWS when the community name beats the in-game one
	 * (the Inferno's Jal- vocabulary: "Bat" for Jal-MejRah). Display only:
	 * getName() stays the real name, so every name-keyed rule - notes, TD
	 * damage reduction, boss overrides - keeps applying, and profile
	 * pins/exclusions follow the real row. */
	private String displayName;

	public MonsterStats withDisplayName(String nick)
	{
		MonsterStats copy = withVersion(version);
		copy.displayName = nick;
		copy.toaInvocationLevel = toaInvocationLevel;
		return copy;
	}

	/** ToA raid level carried ON the monster so every path - pool, kit
	 * re-shows, spec sims - prices the same invocation; 0 everywhere
	 * else. The engine scales defence rolls by (250+level)/250 (the
	 * official engine's rule) for invocation-scaled rows. */
	private int toaInvocationLevel;

	public int getToaInvocationLevel()
	{
		return toaInvocationLevel;
	}

	/** A copy priced at this raid level (SET, not compounded - re-applying
	 * a different level replaces the old one); returns this when the
	 * level already matches. */
	public MonsterStats withToaInvocation(int level)
	{
		int clamped = Math.max(0, level);
		if (clamped == toaInvocationLevel)
		{
			return this;
		}
		MonsterStats copy = withVersion(version);
		copy.displayName = displayName;
		copy.toaInvocationLevel = clamped;
		return copy;
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
		MonsterStats copy = new MonsterStats(syntheticId, name, versionLabel, combatLevel, hitpoints,
			size, defence, magic, offensiveMagic, defensive, offence, extended,
			slayerMonster, weaknessElement, weaknessSeverity);
		copy.wikiVersion = getWikiVersion();
		return copy;
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

	/** A copy at a different Defence level - defence-drain spec modeling.
	 * Carries the invocation level: a drained ToA row keeps its raid
	 * scaling or the drain would be valued at invocation 0. */
	public MonsterStats withDefence(int newDefence)
	{
		MonsterStats copy = new MonsterStats(id, name, version, combatLevel, hitpoints, size,
			Math.max(0, newDefence), magic, offensiveMagic, defensive, offence,
			attributes, slayerMonster, weaknessElement, weaknessSeverity);
		copy.toaInvocationLevel = toaInvocationLevel;
		return copy;
	}

	public boolean hasAttribute(String attribute)
	{
		return attributesLower.contains(attribute.toLowerCase(Locale.ROOT));
	}

	/** True when this variant's version label starts with the prefix - the
	 * phase gate MonsterNotes and RecommendedBring share ("Phase 1..."). */
	public boolean versionStartsWith(String prefix)
	{
		return version != null && version.startsWith(prefix);
	}

	public String label()
	{
		// Level-derived version labels ("Level 137") are redundant with -
		// and after group-collapsing can contradict - the lvl suffix.
		boolean levelVersion = version.regionMatches(true, 0, "level", 0, 5);
		String suffix = version.isEmpty() || levelVersion ? "" : " (" + version + ")";
		String level = combatLevel > 0 ? " - lvl " + combatLevel : "";
		return (displayName != null ? displayName : name) + suffix + level;
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
