package com.loadoutlab.data;

import com.google.gson.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Curated monster groups (M-3): a group is the ROSTER of distinct mob
 * types featured in the content - Fight Caves, Inferno, Zulrah's forms,
 * the Dagannoth Kings... - searchable beside the monsters and expanding
 * into one multi-mob result (one shared set per style, HP-weighted).
 *
 * Members resolve against LOADED corpus rows by exact name (+ version
 * when the name is ambiguous) - never raw wiki names, because the
 * stat-key collapse merges versions (Zuk Normal/Enraged, Tz-Kek's two
 * levels). A member that fails to resolve fails loudly in tests, not
 * silently at runtime: the group simply omits it and carries on.
 */
public final class MonsterGroups {
	private static final String RESOURCE = "monster_groups.json";

	/** One curated group, resolved to loaded rows (the loader hands in
	 * unmodifiable mob and alias lists). */
	@lombok.Getter
	@lombok.AllArgsConstructor
	public static final class MonsterGroup {
		private final String name;
		private final String note;
		private final List<MonsterStats> mobs;
		private final List<String> aliases;
		private final int inventory;
		final boolean comingSoon;
		/** A raid selection: shared supplied boosts, big inventories, and
		 * never a slayer-task context (Andrew, 2026-08-31). */
		private final boolean raid;

		/** "Fight Caves - 7 mobs" for list rows (the coming-soon note
		 * renders in the RESULT card, not here - field spec 2026-07-18). */
		public String label() { return name + " - " + mobs.size() + " mobs"; }
	}

	private MonsterGroups() {
	}

	/** Load and resolve every curated group against the dataset. */
	public static List<MonsterGroup> load(LoadoutData data) {
		List<MonsterGroup> groups = new ArrayList<>();
		try {
			for (JsonElement element : JsonResources.array(RESOURCE)) {
				JsonObject row = element.getAsJsonObject();
				String name = row.get("name").getAsString();
				String note = JsonResources.string(row, "note");
				List<MonsterStats> mobs = new ArrayList<>();
				for (JsonElement m : row.getAsJsonArray("members")) {
					JsonObject member = m.getAsJsonObject();
					MonsterStats resolved = resolve(data,
						member.get("name").getAsString(),
						member.has("version") ? member.get("version").getAsString() : null);
					if (resolved == null) continue;
					if (member.has("immuneTo")) {
						// A synthetic per-phase variant (TD shields, KQ
						// forms, Nylocas forms, Warden P2): same sheet, new
						// id + label, one or SEVERAL immunity attributes.
						// The id ordinal is the style bitmask (melee 1,
						// ranged 2, magic 4) so every combo stays distinct.
						List<String> styles = new ArrayList<>();
						JsonElement immuneTo = member.get("immuneTo");
						if (immuneTo.isJsonArray()) {
							for (JsonElement e : immuneTo.getAsJsonArray()) {
								styles.add(Names.lower(e.getAsString()));
							}
						} else {
							styles.add(Names.lower(immuneTo.getAsString()));
						}
						int mask = 0;
						List<String> attributes = new ArrayList<>();
						if (member.has("prayerImmunity")
							&& member.get("prayerImmunity").getAsBoolean()) {
							// The immunity is a protection PRAYER, not a
							// shield: Verac's set pierces it (KQ forms).
							attributes.add("prayer_immunity");
						}
						var joined = new StringBuilder();
						for (String style : styles) {
							mask |= styleBit(style);
							attributes.add("immune_" + style);
							if (joined.length() > 0) joined.append(" + ");
							joined.append(Names.cap(style));
						}
						String label = member.has("label")
							? member.get("label").getAsString()
							: joined + " immune";
						resolved = resolved.immuneVariant(
							MonsterStats.SYNTHETIC_ID_BASE + resolved.getId() * 10 + mask,
							label, attributes);
					} else if (member.has("without")) {
						// A phase that loses an attribute; id bit 8.
						List<String> removed = new ArrayList<>();
						for (JsonElement e : member.getAsJsonArray("without")) {
							removed.add(Names.lower(e.getAsString()));
						}
						resolved = resolved.withoutAttributes(
							MonsterStats.SYNTHETIC_ID_BASE + resolved.getId() * 10 + 8,
							member.get("label").getAsString(), removed);
					} else if (member.has("label")) {
						resolved = resolved.withVersion(member.get("label").getAsString());
					}
					if (member.has("nick")) resolved = resolved.withDisplayName(member.get("nick").getAsString());
					// Raid stipulations (Andrew 2026-09-10): a kit weight (the
					// fight that decides the raid) and a spec weapon family.
					List<String> extra = new ArrayList<>();
					if (member.has("weight")) extra.add("weight:" + member.get("weight").getAsInt());
					if (member.has("spec")) extra.add("spec:" + Names.lower(member.get("spec").getAsString()));
					// A spec-only member (the Warden's Core, ISS-01M5QEFMBSZXAHV84ZXF1YZHWY):
					// its main weapon has no say in the kit; its spec hits still count.
					if (member.has("specOnly") && member.get("specOnly").getAsBoolean()) extra.add("spec_only");
					if (!extra.isEmpty()) resolved = resolved.withAttributes(extra);
					mobs.add(resolved);
				}
				List<String> aliases = new ArrayList<>();
				if (row.has("aliases")) {
					for (JsonElement a : row.getAsJsonArray("aliases")) aliases.add(Names.lower(a.getAsString()));
				}
				if (!mobs.isEmpty()) {
					groups.add(new MonsterGroup(name, note, Collections.unmodifiableList(mobs),
						Collections.unmodifiableList(aliases), JsonResources.integer(row, "inventory", 0),
						JsonResources.bool(row, "comingSoon", false), JsonResources.bool(row, "raid", false)));
				}
			}
		} catch (Exception e) {
			// A malformed curation must never take the panel down.
			return groups;
		}
		return groups;
	}

	private static int styleBit(String style) {
		switch (style) {
			case "melee": return 1;
			case "ranged": return 2;
			default: return 4; // magic
		}
	}

	/** Exact-name match; version disambiguates when given, otherwise the
	 * name must be unique (ambiguity = curation bug, caught by tests). */
	static MonsterStats resolve(LoadoutData data, String name, String version) {
		// Two hits = ambiguous: the curation must name a version.
		List<MonsterStats> matches = data.getMonsters().stream().filter(m -> m.getName().equalsIgnoreCase(name)
			&& (version == null || version.equalsIgnoreCase(m.getVersion()))).limit(2).collect(Collectors.toList());
		return matches.size() == 1 ? matches.get(0) : null;
	}

	/** Name-contains search over the groups, hits in curated order. */
	public static List<MonsterGroup> search(List<MonsterGroup> groups, String query, int limit) {
		String text = query == null ? "" : Names.lower(query.trim());
		if (text.length() < 2) return new ArrayList<>();
		// Player vocabulary ("jad", "zuk", "dks") reaches the group through its aliases.
		return groups.stream().filter(group -> Names.lower(group.getName()).contains(text)
			|| group.aliases.stream().anyMatch(alias -> alias.contains(text) || text.contains(alias)))
			.limit(limit).collect(Collectors.toList());
	}
}
