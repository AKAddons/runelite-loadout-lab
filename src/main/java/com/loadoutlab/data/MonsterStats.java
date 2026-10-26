// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

import java.util.*;

import lombok.Getter;

@Getter
public final class MonsterStats {
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
	private final Set<String> attributesLower;
	private final boolean slayerMonster;
	private final String weaknessElement;
	private final int weaknessSeverity;
	/** Lowercased monster name, cached (per-trial engine gates). */
	private final String nameLower;
	private final boolean wilderness;
	private final boolean revenant;
	/** 0, or the vampyre tier (attribute vampyre1-3): read per trial set. */
	private final int vampyreTier;

	public MonsterStats(
		int id,
		String name,
		String version,
		int combatLevel,
		int hitpoints,
		int defence,
		int magic,
		MonsterDefences defensive,
		List<String> attributes) {
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
		int weaknessSeverity) {
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
		this.attributes = attributes == null ? List.of() : Collections.unmodifiableList(attributes);
		this.slayerMonster = slayerMonster;
		this.weaknessElement = weaknessElement == null ? "" : Names.lower(weaknessElement);
		this.weaknessSeverity = Math.max(0, weaknessSeverity);
		// hasAttribute runs per candidate set in the optimizer's inner loop;
		// lowercase once instead of per query.
		var lower = new HashSet<String>();
		for (String value : this.attributes) {
			if (value != null) lower.add(Names.lower(value));
		}
		this.attributesLower = lower;
		this.vampyreTier = lower.contains("vampyre3") ? 3 : lower.contains("vampyre2") ? 2 : lower.contains("vampyre1") ? 1 : 0;
		// The revenant/wilderness gates lowercase the name per DPS trial -
		// that allocation was ~20% of optimizer samples. Once, here.
		this.nameLower = Names.lower(this.name);
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
	public MonsterStats withVersion(String newVersion) { return copy(id, newVersion, attributes, defence); }

	private MonsterStats copy(int newId, String newVersion, List<String> newAttributes, int newDefence) {
		var copy = new MonsterStats(newId, name, newVersion, combatLevel, hitpoints,
			size, newDefence, magic, offensiveMagic, defensive, offence, newAttributes,
			slayerMonster, weaknessElement, weaknessSeverity);
		copy.wikiVersion = getWikiVersion();
		copy.displayName = displayName;
		copy.toaInvocationLevel = toaInvocationLevel;
		return copy;
	}

	/** The RAW wiki version string ("Post-quest, Awake") - what the
	 * official data and calculator key on. Display renames
	 * (normalizeQuestVersions) preserve it; null = never renamed.
	 * Field 2026-08-21: exporting the DISPLAY version made the calc's
	 * import miss and silently fall back to the FIRST same-id row -
	 * the post-quest Duke opened as Awakened. */
	private String wikiVersion;

	public String getWikiVersion() { return wikiVersion == null ? version : wikiVersion; }

	/** What the label SHOWS when the community name beats the in-game one
	 * (the Inferno's Jal- vocabulary: "Bat" for Jal-MejRah). Display only:
	 * getName() stays the real name, so every name-keyed rule - notes, TD
	 * damage reduction, boss overrides - keeps applying, and profile
	 * pins/exclusions follow the real row. */
	private String displayName;

	/** The value after a "prefix:" attribute (group stipulations), or null. */
	public String attributeValue(String prefix) {
		return attributes.stream().filter(a -> a.startsWith(prefix))
			.map(a -> a.substring(prefix.length())).findFirst().orElse(null);
	}

	/** A copy carrying extra attributes (the group's stipulations). */
	public MonsterStats withAttributes(List<String> extra) {
		List<String> extended = new ArrayList<>(attributes);
		extended.addAll(extra);
		return copy(id, version, extended, defence);
	}

	public MonsterStats withDisplayName(String nick) {
		MonsterStats copy = withVersion(version);
		copy.displayName = nick;
		return copy;
	}

	/** ToA raid level carried ON the monster so every path - pool, kit
	 * re-shows, spec sims - prices the same invocation; 0 everywhere
	 * else. The engine scales defence rolls by (250+level)/250 (the
	 * official engine's rule) for invocation-scaled rows. */
	private int toaInvocationLevel;

	public int getToaInvocationLevel() { return toaInvocationLevel; }

	/** A copy priced at this raid level (SET, not compounded - re-applying
	 * a different level replaces the old one); returns this when the
	 * level already matches. */
	public MonsterStats withToaInvocation(int level) {
		int clamped = Math.max(0, level);
		if (clamped == toaInvocationLevel) return this;
		MonsterStats copy = withVersion(version);
		copy.toaInvocationLevel = clamped;
		return copy;
	}

	public MonsterStats immuneVariant(int syntheticId, String versionLabel, String immuneAttribute) {
		return immuneVariant(syntheticId, versionLabel,
			Collections.singletonList(immuneAttribute));
	}

	/** Multi-immunity variant: a phase can lock out SEVERAL styles at
	 * once (Kalphite Queen's first form prays off magic AND ranged; a
	 * Nylocas form takes only its own style). */
	public MonsterStats immuneVariant(int syntheticId, String versionLabel,
		List<String> immuneAttributes) {
		List<String> extended = new ArrayList<>(attributes);
		extended.addAll(immuneAttributes);
		return copy(syntheticId, versionLabel, extended, defence);
	}

	/** A phase variant with attributes taken away. */
	public MonsterStats withoutAttributes(int syntheticId, String versionLabel, List<String> removed) {
		List<String> kept = new ArrayList<>(attributes);
		kept.removeIf(a -> removed.contains(Names.lower(a)));
		return copy(syntheticId, versionLabel, kept, defence);
	}

	/** Lowercased monster name, cached (per-trial engine gates). */

	/** Fought in the Wilderness (see WildernessMonsters) - cached, the
	 * wilderness-weapon gate asks several times per DPS trial. */
	public boolean isWildernessMonster() { return wilderness; }

	/** Name starts with "revenant" - the avarice/ethereum gates. */
	public boolean isRevenantMonster() { return revenant; }

	/** A copy at a different Defence level - defence-drain spec modeling.
	 * Carries the invocation level: a drained ToA row keeps its raid
	 * scaling or the drain would be valued at invocation 0. */
	public MonsterStats withDefence(int newDefence) { return copy(id, version, attributes, Math.max(0, newDefence)); }

	public boolean hasAttribute(String attribute) { return attributesLower.contains(Names.key(attribute)); }

	/** True when this variant's version label starts with the prefix - the
	 * phase gate the mechanics rules share ("Phase 1..."). */
	public boolean versionStartsWith(String prefix) { return version != null && version.startsWith(prefix); }

	public String label() {
		// Level-derived version labels ("Level 137") are redundant with -
		// and after group-collapsing can contradict - the lvl suffix.
		boolean levelVersion = version.regionMatches(true, 0, "level", 0, 5);
		String suffix = version.isEmpty() || levelVersion ? "" : " (" + version + ")";
		String level = combatLevel > 0 ? " - lvl " + combatLevel : "";
		return (displayName != null ? displayName : name) + suffix + level;
	}

	public String searchText() { return normalizeQuery(name + " " + version + " " + id); }

	/** Search matching ignores punctuation: "kril" finds K'ril Tsutsaroth,
	 * "kreearra" finds Kree'arra. */
	public static String normalizeQuery(String text) {
		var sb = new StringBuilder(text.length());
		for (char c : Names.lower(text).toCharArray()) {
			if (Character.isLetterOrDigit(c) || c == ' ') sb.append(c);
		}
		return sb.toString();
	}


	/** Synthetic phase-variant ids live above this base (M-3 groups):
	 * base + realId * 10 + styleOrdinal. */
	public static final int SYNTHETIC_ID_BASE = 9_000_000;

	/** The id user-profile data (pins, exclusions, notes) attaches to -
	 * a synthetic phase variant maps back to its real monster, so a
	 * profile set on the plain mob follows it into groups. */
	public int profileId() { return profileIdOf(id); }

	/** The same mapping for a bare id (the profile store normalises every
	 * read and write through it, so a pin made on a phase card is the
	 * real monster's pin - Apathy, Discord 2026-10-09, Tormented Demons). */
	public static int profileIdOf(int id) { return id >= SYNTHETIC_ID_BASE ? (id - SYNTHETIC_ID_BASE) / 10 : id; }














}
