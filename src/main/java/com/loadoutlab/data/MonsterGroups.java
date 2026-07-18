package com.loadoutlab.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
public final class MonsterGroups
{
	private static final String RESOURCE = "/com/loadoutlab/data/monster_groups.json";

	/** One curated group, resolved to loaded rows. */
	public static final class MonsterGroup
	{
		private final String name;
		private final String note;
		private final List<MonsterStats> mobs;
		private final List<String> aliases;

		MonsterGroup(String name, String note, List<MonsterStats> mobs, List<String> aliases)
		{
			this.name = name;
			this.note = note;
			this.mobs = Collections.unmodifiableList(mobs);
			this.aliases = Collections.unmodifiableList(aliases);
		}

		public String getName()
		{
			return name;
		}

		public String getNote()
		{
			return note;
		}

		public List<MonsterStats> getMobs()
		{
			return mobs;
		}

		/** "Fight Caves - 7 mobs" for list rows. */
		public String label()
		{
			return name + " - " + mobs.size() + " mobs";
		}
	}

	private MonsterGroups()
	{
	}

	/** Load and resolve every curated group against the dataset. */
	public static List<MonsterGroup> load(LoadoutData data)
	{
		List<MonsterGroup> groups = new ArrayList<>();
		try (InputStream in = MonsterGroups.class.getResourceAsStream(RESOURCE))
		{
			if (in == null)
			{
				return groups;
			}
			JsonArray rows = new JsonParser().parse(
				new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
			for (JsonElement element : rows)
			{
				JsonObject row = element.getAsJsonObject();
				String name = row.get("name").getAsString();
				String note = row.has("note") ? row.get("note").getAsString() : "";
				List<MonsterStats> mobs = new ArrayList<>();
				for (JsonElement m : row.getAsJsonArray("members"))
				{
					JsonObject member = m.getAsJsonObject();
					MonsterStats resolved = resolve(data,
						member.get("name").getAsString(),
						member.has("version") ? member.get("version").getAsString() : null);
					if (resolved == null)
					{
						continue;
					}
					if (member.has("immuneTo"))
					{
						// A synthetic per-phase variant (TD shield rotation):
						// same sheet, new id + label, immunity attribute.
						String style = member.get("immuneTo").getAsString().toLowerCase(Locale.ROOT);
						String label = Character.toUpperCase(style.charAt(0)) + style.substring(1)
							+ " immune";
						resolved = resolved.immuneVariant(
							MonsterStats.SYNTHETIC_ID_BASE + resolved.getId() * 10 + styleOrdinal(style),
							label, "immune_" + style);
					}
					mobs.add(resolved);
				}
				List<String> aliases = new ArrayList<>();
				if (row.has("aliases"))
				{
					for (JsonElement a : row.getAsJsonArray("aliases"))
					{
						aliases.add(a.getAsString().toLowerCase(Locale.ROOT));
					}
				}
				if (!mobs.isEmpty())
				{
					groups.add(new MonsterGroup(name, note, mobs, aliases));
				}
			}
		}
		catch (Exception e)
		{
			// A malformed curation must never take the panel down.
			return groups;
		}
		return groups;
	}

	private static int styleOrdinal(String style)
	{
		switch (style)
		{
			case "melee": return 0;
			case "ranged": return 1;
			default: return 2; // magic
		}
	}

	/** Exact-name match; version disambiguates when given, otherwise the
	 * name must be unique (ambiguity = curation bug, caught by tests). */
	static MonsterStats resolve(LoadoutData data, String name, String version)
	{
		MonsterStats match = null;
		for (MonsterStats monster : data.getMonsters())
		{
			if (!monster.getName().equalsIgnoreCase(name))
			{
				continue;
			}
			if (version != null && !version.equalsIgnoreCase(monster.getVersion()))
			{
				continue;
			}
			if (match != null)
			{
				return null; // ambiguous - the curation must name a version
			}
			match = monster;
		}
		return match;
	}

	/** Name-contains search over the groups, hits in curated order. */
	public static List<MonsterGroup> search(List<MonsterGroup> groups, String query, int limit)
	{
		String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		List<MonsterGroup> hits = new ArrayList<>();
		if (text.length() < 2)
		{
			return hits;
		}
		for (MonsterGroup group : groups)
		{
			boolean hit = group.getName().toLowerCase(Locale.ROOT).contains(text);
			if (!hit)
			{
				// Player vocabulary ("jad", "zuk", "dks") reaches the group.
				for (String alias : group.aliases)
				{
					if (alias.contains(text) || text.contains(alias))
					{
						hit = true;
						break;
					}
				}
			}
			if (hit)
			{
				hits.add(group);
				if (hits.size() >= limit)
				{
					break;
				}
			}
		}
		return hits;
	}
}
