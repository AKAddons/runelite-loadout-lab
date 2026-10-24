package com.loadoutlab.data;

import com.google.gson.*;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

import static com.loadoutlab.data.JsonResources.*;

/**
 * Trip supplies (food, fast food, prayer restores, surge, anti-venom): the
 * curated tier tables behind the persistent supply defaults. Tiers live in
 * trip_supplies.json (hub token cap), best first per category; "Detect best"
 * walks a category in order and picks the first option the player owns any
 * dose of. Anti-venom only applies against the wiki Venom page's inflictor
 * list (the only monsters able to inflict venom).
 */
public final class TripSupplies {
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
	/** Getting there (ISS-01M5JJ0Z4M988DR0M0ZGJHD139, Andrew 2026-10-22): the teleport capes,
	 * best first, the Crafting cape an explicit choice. */
	public static final String TELEPORT_CAPE = "teleportCape";
	public static final String[] CATEGORY_KEYS = {FOOD, FAST_FOOD, PRAYER_RESTORE, SURGE, SPELLBOOK_CAPE, TELEPORT_CAPE, ANTIVENOM, ANTIPOISON, SHIP_REPAIR_KIT, CANNON};

	/** One supply choice: ids best-first, ids[0] the display/cell id and the
	 * full list the bank-filter membership (every dose matches). */
	@lombok.AllArgsConstructor
	public static final class Option {
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
	}

	private static final Map<String, List<Option>> CATEGORIES = new LinkedHashMap<>();
	private static final Map<String, String> LABELS = new LinkedHashMap<>();
	private static final Set<String> VENOMOUS = new HashSet<>();
	private static final Set<String> POISONOUS = new HashSet<>();
	private static final Set<String> NO_CANNON = new HashSet<>();

	static {
		JsonObject root = object("trip_supplies.json");
		if (root != null) {
			for (String category : CATEGORY_KEYS) {
				List<Option> options = new ArrayList<>();
				JsonArray arr = root.getAsJsonArray(category);
				if (arr != null) {
					for (JsonElement e : arr) {
						JsonObject o = e.getAsJsonObject();
						List<String> names = new ArrayList<>();
						strings(o, "names", names);
						options.add(new Option(
							string(o, "key"),
							string(o, "name"),
							ints(o.getAsJsonArray("ids")),
							bool(o, "detect", true),
							"utility".equals(string(o, "placement")),
							bool(o, "wildyOnly", false),
							names.toArray(new String[0]), cells(o)));
					}
				}
				CATEGORIES.put(category, Collections.unmodifiableList(options));
			}
			stringMap(root, "labels", LABELS);
			strings(root, "venomousMonsters", VENOMOUS);
			strings(root, "poisonMonsters", POISONOUS);
			strings(root, "noCannonMonsters", NO_CANNON);
		}
	}

	private static int[][] cells(JsonObject o) {
		JsonArray rows = o.getAsJsonArray("cells");
		if (rows == null) {
			return null;
		}
		int[][] out = new int[rows.size()][];
		for (int i = 0; i < out.length; i++) {
			out[i] = ints(rows.get(i).getAsJsonArray());
		}
		return out;
	}

	private TripSupplies() {
	}

	/** The grey chip's submenu label, or null for a category the menu never
	 * shows (ship repair kits ride the sea card). */
	public static String label(String category) {
		return LABELS.get(category);
	}

	/** The category's options, best first. */
	public static List<Option> options(String category) {
		return CATEGORIES.getOrDefault(category, List.of());
	}

	/** The option a config-enum constant names, or null (NONE/DETECT_BEST
	 * and unknown keys resolve to null). */
	public static Option option(String category, String key) {
		return options(category).stream().filter(o -> o.key.equals(key)).findFirst().orElse(null);
	}

	/** Detect best for a trip: in the wilderness a banked blighted variant
	 * wins outright (cheap to lose, wilderness-only); on land no
	 * wilderness-only option is ever picked (Andrew 2026-09-01). */
	public static Option detectBest(String category, IntPredicate owns, boolean inWilderness) {
		Option pick = null;
		for (Option o : options(category)) {
			if (!o.detect || (o.wildyOnly && !inWilderness)) {
				continue;
			}
			for (int id : o.ids) {
				if (owns.test(id)) {
					if (o.wildyOnly) {
						return o;
					}
					if (pick == null) {
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
	public static Option detectBest(String category, IntPredicate owns) {
		return detectBest(category, owns, false);
	}

	/** True when the monster can inflict venom (wiki Venom page inflictor
	 * list, matched by name so araxyte/snakeling variants count). */
	public static boolean inflictsVenom(MonsterStats monster) {
		return named(monster, VENOMOUS);
	}

	/** Poisons but does not envenom (the Kings' spinolyps, KQ...) - the
	 * antipoison category; venomous mobs keep anti-venom. */
	public static boolean inflictsPoison(MonsterStats monster) {
		return named(monster, POISONOUS) && !named(monster, VENOMOUS);
	}

	/** No cannon here: a prohibited arena or cannon-immune (exact names). */
	public static boolean noCannon(MonsterStats monster) {
		return monster != null && NO_CANNON.contains(Names.lower(monster.getName()));
	}

	private static boolean named(MonsterStats monster, Set<String> tokens) {
		if (monster == null) {
			return false;
		}
		String name = Names.lower(monster.getName());
		return tokens.stream().anyMatch(name::contains);
	}
}
