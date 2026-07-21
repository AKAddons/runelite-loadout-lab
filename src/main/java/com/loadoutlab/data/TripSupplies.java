package com.loadoutlab.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Trip supplies (food, fast food, prayer restores, surge, anti-venom): the
 * curated tier tables behind the persistent supply defaults. Tiers live in
 * trip_supplies.json (hub token cap), best first per category; "Detect best"
 * walks a category in order and picks the first option the player owns any
 * dose of. Anti-venom only applies against the wiki Venom page's inflictor
 * list (the only monsters able to inflict venom).
 */
public final class TripSupplies
{
	public static final String FOOD = "food";
	public static final String FAST_FOOD = "fastFood";
	public static final String PRAYER_RESTORE = "prayerRestore";
	public static final String SURGE = "surge";
	public static final String SPELLBOOK_CAPE = "spellbookCape";
	public static final String ANTIVENOM = "antivenom";

	/** One supply choice: ids best-first, ids[0] the display/cell id and the
	 * full list the bank-filter membership (every dose matches). */
	public static final class Option
	{
		public final String key;
		public final String name;
		public final int[] ids;
		/** False = only selectable explicitly (prayer regeneration's
		 * over-time mechanism should never win an auto-detect). */
		public final boolean detect;
		/** True = fight-relevant gear that is neither worn nor carried
		 * (spellbook capes): the bank layout's third strip, below the
		 * cross and the inventory block (field spec 2026-07-20). */
		public final boolean utility;

		Option(String key, String name, int[] ids, boolean detect, boolean utility)
		{
			this.key = key;
			this.name = name;
			this.ids = ids;
			this.detect = detect;
			this.utility = utility;
		}
	}

	private static final Map<String, List<Option>> CATEGORIES = new LinkedHashMap<>();
	private static final Set<String> VENOMOUS = new HashSet<>();

	static
	{
		JsonObject root = JsonResources.object("/com/loadoutlab/data/trip_supplies.json");
		if (root != null)
		{
			for (String category : new String[]{FOOD, FAST_FOOD, PRAYER_RESTORE, SURGE, SPELLBOOK_CAPE, ANTIVENOM})
			{
				List<Option> options = new ArrayList<>();
				JsonArray arr = root.getAsJsonArray(category);
				if (arr != null)
				{
					for (JsonElement e : arr)
					{
						JsonObject o = e.getAsJsonObject();
						JsonArray idArr = o.getAsJsonArray("ids");
						int[] ids = new int[idArr.size()];
						for (int i = 0; i < ids.length; i++)
						{
							ids[i] = idArr.get(i).getAsInt();
						}
						options.add(new Option(
							o.get("key").getAsString(),
							o.get("name").getAsString(),
							ids,
							!o.has("detect") || o.get("detect").getAsBoolean(),
							o.has("placement") && "utility".equals(o.get("placement").getAsString())));
					}
				}
				CATEGORIES.put(category, Collections.unmodifiableList(options));
			}
			JsonResources.strings(root, "venomousMonsters", VENOMOUS);
		}
	}

	private TripSupplies()
	{
	}

	/** The category's options, best first. */
	public static List<Option> options(String category)
	{
		return CATEGORIES.getOrDefault(category, Collections.emptyList());
	}

	/** The option a config-enum constant names, or null (NONE/DETECT_BEST
	 * and unknown keys resolve to null). */
	public static Option option(String category, String key)
	{
		for (Option o : options(category))
		{
			if (o.key.equals(key))
			{
				return o;
			}
		}
		return null;
	}

	/** Detect best: the first detectable option the player owns any dose
	 * of, or null when they own none. */
	public static Option detectBest(String category, IntPredicate owns)
	{
		for (Option o : options(category))
		{
			if (!o.detect)
			{
				continue;
			}
			for (int id : o.ids)
			{
				if (owns.test(id))
				{
					return o;
				}
			}
		}
		return null;
	}

	/** True when the monster can inflict venom (wiki Venom page inflictor
	 * list, matched by name so araxyte/snakeling variants count). */
	public static boolean inflictsVenom(MonsterStats monster)
	{
		if (monster == null)
		{
			return false;
		}
		String name = monster.getName().toLowerCase(Locale.ROOT);
		for (String token : VENOMOUS)
		{
			if (name.contains(token))
			{
				return true;
			}
		}
		return false;
	}
}
