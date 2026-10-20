package com.loadoutlab.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.List;

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
	private static final String RESOURCE = "monster_groups.json";

	/** One curated group, resolved to loaded rows. */
	@lombok.Getter
	public static final class MonsterGroup
	{
		private final String name;
		private final String note;
		private final List<MonsterStats> mobs;
		private final List<String> aliases;
		private final int inventory;
		final boolean comingSoon;
		private final boolean raid;

		MonsterGroup(String name, String note, List<MonsterStats> mobs, List<String> aliases,
			int inventory, boolean comingSoon, boolean raid)
		{
			this.comingSoon = comingSoon;
			this.name = name;
			this.note = note;
			this.mobs = Collections.unmodifiableList(mobs);
			this.aliases = Collections.unmodifiableList(aliases);
			this.inventory = inventory;
			this.raid = raid;
		}

		/** A raid selection: shared supplied boosts, big inventories, and
		 * never a slayer-task context (Andrew, 2026-08-31). */
		public boolean isRaid()
		{
			return raid;
		}

		/** "Fight Caves - 7 mobs" for list rows (the coming-soon note
		 * renders in the RESULT card, not here - field spec 2026-07-18). */
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
		try
		{
			for (JsonElement element : JsonResources.array(RESOURCE))
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
						// A synthetic per-phase variant (TD shields, KQ
						// forms, Nylocas forms, Warden P2): same sheet, new
						// id + label, one or SEVERAL immunity attributes.
						// The id ordinal is the style bitmask (melee 1,
						// ranged 2, magic 4) so every combo stays distinct.
						List<String> styles = new ArrayList<>();
						JsonElement immuneTo = member.get("immuneTo");
						if (immuneTo.isJsonArray())
						{
							for (JsonElement e : immuneTo.getAsJsonArray())
							{
								styles.add(Names.lower(e.getAsString()));
							}
						}
						else
						{
							styles.add(Names.lower(immuneTo.getAsString()));
						}
						int mask = 0;
						List<String> attributes = new ArrayList<>();
						if (member.has("prayerImmunity")
							&& member.get("prayerImmunity").getAsBoolean())
						{
							// The immunity is a protection PRAYER, not a
							// shield: Verac's set pierces it (KQ forms).
							attributes.add("prayer_immunity");
						}
						StringBuilder joined = new StringBuilder();
						for (String style : styles)
						{
							mask |= styleBit(style);
							attributes.add("immune_" + style);
							if (joined.length() > 0)
							{
								joined.append(" + ");
							}
							joined.append(Character.toUpperCase(style.charAt(0)))
								.append(style.substring(1));
						}
						String label = member.has("label")
							? member.get("label").getAsString()
							: joined + " immune";
						resolved = resolved.immuneVariant(
							MonsterStats.SYNTHETIC_ID_BASE + resolved.getId() * 10 + mask,
							label, attributes);
					}
					else if (member.has("without"))
					{
						// A phase that loses an attribute; id bit 8.
						List<String> removed = new ArrayList<>();
						for (JsonElement e : member.getAsJsonArray("without"))
						{
							removed.add(Names.lower(e.getAsString()));
						}
						resolved = resolved.withoutAttributes(
							MonsterStats.SYNTHETIC_ID_BASE + resolved.getId() * 10 + 8,
							member.get("label").getAsString(), removed);
					}
					else if (member.has("label"))
					{
						resolved = resolved.withVersion(member.get("label").getAsString());
					}
					if (member.has("nick"))
					{
						resolved = resolved.withDisplayName(member.get("nick").getAsString());
					}
					// Raid stipulations (Andrew 2026-09-10): a kit weight (the
					// fight that decides the raid) and a spec weapon family.
					List<String> extra = new ArrayList<>();
					if (member.has("weight"))
					{
						extra.add("weight:" + member.get("weight").getAsInt());
					}
					if (member.has("spec"))
					{
						extra.add("spec:" + Names.lower(member.get("spec").getAsString()));
					}
					if (!extra.isEmpty())
					{
						resolved = resolved.withAttributes(extra);
					}
					mobs.add(resolved);
				}
				List<String> aliases = new ArrayList<>();
				if (row.has("aliases"))
				{
					for (JsonElement a : row.getAsJsonArray("aliases"))
					{
						aliases.add(Names.lower(a.getAsString()));
					}
				}
				if (!mobs.isEmpty())
				{
					int inventory = row.has("inventory") ? row.get("inventory").getAsInt() : 0;
					boolean raid = row.has("raid") && row.get("raid").getAsBoolean();
					boolean comingSoon = row.has("comingSoon") && row.get("comingSoon").getAsBoolean();
					groups.add(new MonsterGroup(name, note, mobs, aliases, inventory, comingSoon, raid));
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

	private static int styleBit(String style)
	{
		switch (style)
		{
			case "melee": return 1;
			case "ranged": return 2;
			default: return 4; // magic
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
		String text = query == null ? "" : Names.lower(query.trim());
		List<MonsterGroup> hits = new ArrayList<>();
		if (text.length() < 2)
		{
			return hits;
		}
		for (MonsterGroup group : groups)
		{
			boolean hit = Names.lower(group.getName()).contains(text);
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
