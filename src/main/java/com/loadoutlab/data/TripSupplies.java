package com.loadoutlab.data;

import com.google.gson.*;
import java.util.*;
import java.util.List;
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
	public static final String SHIP_REPAIR_KIT = "shipRepairKit";
	public static final String ANTIPOISON = "antipoison";
	/** The dwarf multicannon as a five-cell kit (miko 2026-10-17): no dps, no placement rules, never auto-detected. */
	public static final String CANNON = "cannon";
	public static final String[] CATEGORY_KEYS = {FOOD, FAST_FOOD, PRAYER_RESTORE, SURGE, SPELLBOOK_CAPE, ANTIVENOM, ANTIPOISON, SHIP_REPAIR_KIT, CANNON};

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
		/** True = usable only in the wilderness (blighted supplies): detect
		 * prefers it on a wilderness trip and never picks it elsewhere. */
		public final boolean wildyOnly;
		/** Per-item names for a kit (one card cell each); empty for doses. */
		public final String[] names;
		/** A kit cell's ids - the display id first, then its variants (the
		 * ornamented cannon parts, granite cannonballs); null = ids[i] alone. */
		public final int[][] cells;

		Option(String key, String name, int[] ids, boolean detect, boolean utility,
			boolean wildyOnly, String[] names, int[][] cells)
		{
			this.names = names;
			this.cells = cells;
			this.key = key;
			this.name = name;
			this.ids = ids;
			this.detect = detect;
			this.utility = utility;
			this.wildyOnly = wildyOnly;
		}
	}

	private static final Map<String, List<Option>> CATEGORIES = new LinkedHashMap<>();
	private static final Map<String, String> LABELS = new LinkedHashMap<>();
	private static final Set<String> VENOMOUS = new HashSet<>();
	private static final Set<String> POISONOUS = new HashSet<>();
	private static final Set<String> NO_CANNON = new HashSet<>();

	static
	{
		JsonObject root = JsonResources.object("trip_supplies.json");
		if (root != null)
		{
			for (String category : CATEGORY_KEYS)
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
							o.has("placement") && "utility".equals(o.get("placement").getAsString()),
							o.has("wildyOnly") && o.get("wildyOnly").getAsBoolean(),
							kitNames(o), cells(o)));
					}
				}
				CATEGORIES.put(category, Collections.unmodifiableList(options));
			}
			JsonObject labels = root.getAsJsonObject("labels");
			for (String key : labels == null ? Set.<String>of() : labels.keySet())
			{
				LABELS.put(key, labels.get(key).getAsString());
			}
			JsonResources.strings(root, "venomousMonsters", VENOMOUS);
			JsonResources.strings(root, "poisonMonsters", POISONOUS);
			JsonResources.strings(root, "noCannonMonsters", NO_CANNON);
		}
	}

	private static int[][] cells(JsonObject o)
	{
		JsonArray rows = o.getAsJsonArray("cells");
		if (rows == null)
		{
			return null;
		}
		int[][] out = new int[rows.size()][];
		for (int i = 0; i < out.length; i++)
		{
			JsonArray ids = rows.get(i).getAsJsonArray();
			out[i] = new int[ids.size()];
			for (int j = 0; j < out[i].length; j++)
			{
				out[i][j] = ids.get(j).getAsInt();
			}
		}
		return out;
	}

	private static String[] kitNames(JsonObject o)
	{
		List<String> names = new ArrayList<>();
		JsonResources.strings(o, "names", names);
		return names.toArray(new String[0]);
	}

	private TripSupplies()
	{
	}

	/** The grey chip's submenu label, or null for a category the menu never
	 * shows (ship repair kits ride the sea card). */
	public static String label(String category)
	{
		return LABELS.get(category);
	}

	/** The category's options, best first. */
	public static List<Option> options(String category)
	{
		return CATEGORIES.getOrDefault(category, List.of());
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

	/** Detect best for a trip: in the wilderness a banked blighted variant
	 * wins outright (cheap to lose, wilderness-only); on land no
	 * wilderness-only option is ever picked (Andrew 2026-09-01). */
	public static Option detectBest(String category, IntPredicate owns, boolean inWilderness)
	{
		Option pick = null;
		for (Option o : options(category))
		{
			if (!o.detect || (o.wildyOnly && !inWilderness))
			{
				continue;
			}
			for (int id : o.ids)
			{
				if (owns.test(id))
				{
					if (o.wildyOnly)
					{
						return o;
					}
					if (pick == null)
					{
						pick = o;
					}
					break;
				}
			}
		}
		return pick;
	}

	/** Detect best on land: the first detectable option the player owns
	 * any dose of, or null when they own none. */
	public static Option detectBest(String category, IntPredicate owns)
	{
		return detectBest(category, owns, false);
	}

	/** True when the monster can inflict venom (wiki Venom page inflictor
	 * list, matched by name so araxyte/snakeling variants count). */
	public static boolean inflictsVenom(MonsterStats monster)
	{
		return named(monster, VENOMOUS);
	}

	/** Poisons but does not envenom (the Kings' spinolyps, KQ...) - the
	 * antipoison category; venomous mobs keep anti-venom. */
	public static boolean inflictsPoison(MonsterStats monster)
	{
		return named(monster, POISONOUS) && !named(monster, VENOMOUS);
	}

	/** No cannon here: a prohibited arena or cannon-immune (exact names). */
	public static boolean noCannon(MonsterStats monster)
	{
		return monster != null && NO_CANNON.contains(Names.lower(monster.getName()));
	}

	private static boolean named(MonsterStats monster, Set<String> tokens)
	{
		if (monster == null)
		{
			return false;
		}
		String name = Names.lower(monster.getName());
		return tokens.stream().anyMatch(name::contains);
	}
}
