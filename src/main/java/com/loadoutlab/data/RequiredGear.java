package com.loadoutlab.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Mandatory protective slayer gear per monster (grZ field request
 * 2026-08-08: "some tasks where u need to equip something. would be
 * cool if it was autopinned"): a mirror shield vs a basilisk's gaze, a
 * lit bug lantern to harm harpies at all. The optimizer enforces the
 * slot exactly like the dragonfire shield - the pool collapses to the
 * acceptable items and non-complying weapon lines die - whenever the
 * request can actually field one; a user pin on the slot outranks.
 * Data-driven from required_gear.json (a resource - token-free), every
 * row wiki-verified before encoding.
 */
public final class RequiredGear
{
	/** One monster family's requirement: the slot, the acceptable item
	 * specs (corpus names, optionally name#Version), and the why. */
	public static final class Rule
	{
		public final GearSlot slot;
		public final String note;
		private final List<String> itemSpecs;
		/** Resolved once per corpus - the corpus never changes post-load. */
		private volatile Set<Integer> resolved;

		Rule(GearSlot slot, String note, List<String> itemSpecs)
		{
			this.slot = slot;
			this.note = note;
			this.itemSpecs = itemSpecs;
		}

		/** The acceptable item ids, resolved against the loaded corpus
		 * (standard-gear rows only; variants fold via the candidate
		 * pool's own canonicalization). */
		public Set<Integer> ids(LoadoutData data)
		{
			Set<Integer> out = resolved;
			if (out != null)
			{
				return out;
			}
			out = new LinkedHashSet<>();
			for (String spec : itemSpecs)
			{
				String name = spec;
				String version = null;
				int hash = spec.indexOf('#');
				if (hash >= 0)
				{
					name = spec.substring(0, hash);
					version = spec.substring(hash + 1);
				}
				for (GearItem item : data.getGearItems())
				{
					if (item.getNameLower().equals(name) && item.isStandardGear()
						&& (version == null || version.equals(item.getVersion())))
					{
						out.add(item.getId());
					}
				}
			}
			resolved = out;
			return out;
		}
	}

	private static final Map<String, Rule> BY_NAME = new HashMap<>();

	static
	{
		// Fail LOUD (the name_rules pattern): a silently empty table would
		// drop every requirement with no visible error.
		JsonObject root = JsonResources.objectOrThrow(
			"/com/loadoutlab/data/required_gear.json");
		for (JsonElement e : root.getAsJsonArray("rules"))
		{
			JsonObject row = e.getAsJsonObject();
			List<String> items = new ArrayList<>();
			JsonResources.strings(row, "items", items);
			Rule rule = new Rule(
				GearSlot.valueOf(row.get("slot").getAsString()),
				row.get("note").getAsString(),
				Collections.unmodifiableList(items));
			for (JsonElement m : row.getAsJsonArray("monsters"))
			{
				BY_NAME.put(m.getAsString(), rule);
			}
		}
		if (BY_NAME.isEmpty())
		{
			throw new IllegalStateException("required_gear.json loaded empty");
		}
	}

	private RequiredGear()
	{
	}

	/** The requirement for this monster, or null. Name-keyed like the
	 * notes, so RequiredGearTest pins every key against loaded rows. */
	public static Rule ruleFor(MonsterStats monster)
	{
		return monster == null ? null
			: BY_NAME.get(monster.getName().toLowerCase(Locale.ROOT));
	}

	/** The requirement's explanation for the stat panel, or null. */
	public static String noteFor(MonsterStats monster)
	{
		Rule rule = ruleFor(monster);
		return rule == null ? null : rule.note;
	}

	/** Every curated monster key (test seam). */
	public static Set<String> monsterKeys()
	{
		return Collections.unmodifiableSet(BY_NAME.keySet());
	}
}
