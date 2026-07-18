// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public final class DataService
{
	private static final String GEAR_RESOURCE = "/com/loadoutlab/data/gear_prices.json.gz";
	private static final String REQUIREMENTS_RESOURCE = "/com/loadoutlab/data/equipment_requirements.json.gz";
	private static final String MONSTER_RESOURCE = "/com/loadoutlab/data/monsters.json.gz";
	private static final String SPELL_RESOURCE = "/com/loadoutlab/data/spells.json.gz";
	private static final String ALIAS_RESOURCE = "/com/loadoutlab/data/equipment_aliases.json.gz";

	public LoadoutData load()
	{
		List<GearItem> gear = loadGear();
		List<MonsterStats> monsters = normalizeQuestVersions(loadMonsters());
		List<SpellStats> spells = loadSpells();
		Map<Integer, GearItem> gearById = new HashMap<>();
		for (GearItem item : gear)
		{
			gearById.put(item.getId(), item);
		}
		return new LoadoutData(gear, monsters, spells, gearById, loadAliases());
	}

	/** Variant id -> base id (ornament/locked/degraded, identical stats). */
	private Map<Integer, Integer> loadAliases()
	{
		Map<Integer, Integer> aliases = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : readObject(ALIAS_RESOURCE).entrySet())
		{
			int variant = Integer.parseInt(entry.getKey());
			int base = entry.getValue().getAsInt();
			if (variant != base)
			{
				aliases.put(variant, base);
			}
		}
		return aliases;
	}

	private List<GearItem> loadGear()
	{
		JsonArray rows = readArray(GEAR_RESOURCE);
		Map<Integer, Map<String, Integer>> skillRequirements = loadSkillRequirements();
		List<GearItem> result = new ArrayList<>(rows.size());
		for (JsonElement element : rows)
		{
			JsonObject row = element.getAsJsonObject();
			GearSlot slot = GearSlot.fromJson(string(row, "slot"));
			if (slot == null)
			{
				continue;
			}
			if (isLeaguesReward(string(row, "name"), string(row, "examine")))
			{
				continue;
			}
			if (isMinigameOnly(string(row, "name")))
			{
				continue;
			}

			result.add(new GearItem(
				integer(row, "id", 0),
				string(row, "name"),
				string(row, "version"),
				slot,
				string(row, "category"),
				integer(row, "speed", 0),
				bool(row, "isTwoHanded", false),
				bool(row, "isStandardGear", true) || isWronglyFlaggedUsable(string(row, "name")),
				bool(row, "tradeable", false),
				bool(row, "members", true),
				nullableInteger(row, "estimatedPrice"),
				parseOffensive(row.getAsJsonObject("offensive")),
				parseDefensive(row.getAsJsonObject("defensive")),
				parseBonuses(row.getAsJsonObject("bonuses")),
				requirementsFor(
					integer(row, "id", 0),
					string(row, "name"),
					string(row, "version"),
					skillRequirements)));
		}
		result.sort(Comparator.comparing(GearItem::getName).thenComparingInt(GearItem::getId));
		return result;
	}

	/**
	 * Upstream's curated isStandardGear flag marks these permanent main-game
	 * upgrades as non-standard (audited 2026-07-05): the Avernic treads
	 * upgrade combinations carry real ranged/magic strength, and the
	 * Confliction gauntlets are a 7% magic damage glove. Force them usable.
	 */
	private static boolean isWronglyFlaggedUsable(String name)
	{
		String n = name == null ? "" : name.toLowerCase();
		return n.startsWith("avernic treads") || n.equals("confliction gauntlets");
	}

	/**
	 * Leagues-only rewards never enter the corpus - the optimizer suggests
	 * main-game gear only. The stat-relevant offenders are the Leagues V
	 * "Echo" set (wrongly flagged isStandardGear in the wiki data); the
	 * Trailblazer outfits, banners, and trophies are zero-stat cosmetics.
	 * The wiki examine text says "league" only on leagues rewards.
	 */
	private static boolean isLeaguesReward(String name, String examine)
	{
		String n = name == null ? "" : name.toLowerCase();
		// Only the actual Leagues V echo line - 'Echo boots' (Colosseum: echo crystal + guardian boots) is
		// a main-game recoil item and must stay in the corpus.
		return n.startsWith("echo venator") || n.startsWith("echo virtus")
			|| n.startsWith("echo ahrim") || n.startsWith("echo axe")
			|| n.startsWith("echo pickaxe") || n.startsWith("echo harpoon")
			|| n.contains("trailblazer")
			|| (examine != null && examine.toLowerCase().contains("league"));
	}

	/**
	 * Items that only function inside a minigame never enter the corpus.
	 * The Barbarian Assault arrows carry ranged strength 125 - stronger
	 * than dragon arrows - but can only be fired at penance inside BA
	 * (upstream best-dps flagged only two of the four non-standard).
	 */
	private static boolean isMinigameOnly(String name)
	{
		switch (name == null ? "" : name.toLowerCase())
		{
			case "bullet arrow":
			case "field arrow":
			case "blunt arrow":
			case "barbed arrow":
			case "castle wars bolts":
				return true;
			default:
				return false;
		}
	}

	private Map<Integer, Map<String, Integer>> loadSkillRequirements()
	{
		JsonArray rows = readArray(REQUIREMENTS_RESOURCE);
		Map<Integer, Map<String, Integer>> result = new HashMap<>();
		for (JsonElement element : rows)
		{
			JsonObject row = element.getAsJsonObject();
			JsonObject skills = object(row, "skills");
			Map<String, Integer> levels = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> entry : skills.entrySet())
			{
				if (!entry.getValue().isJsonNull())
				{
					levels.put(entry.getKey(), Math.max(1, entry.getValue().getAsInt()));
				}
			}
			if (!levels.isEmpty())
			{
				result.put(integer(row, "id", 0), levels);
			}
		}
		return result;
	}

	private static GearRequirements requirementsFor(int id, String name, String version, Map<Integer, Map<String, Integer>> skillRequirements)
	{
		Map<String, Integer> skills = skillRequirements.getOrDefault(id, java.util.Collections.emptyMap());
		Set<String> quests = new LinkedHashSet<>(QuestUnlocks.forItem(id, name, version));
		return skills.isEmpty() && quests.isEmpty() ? GearRequirements.NONE : new GearRequirements(skills, quests);
	}

	/** Version-label normalization (field spec 2026-07-18): the noise
	 * tokens go ("Awake"; "Post-quest" becomes the bare name - the
	 * everyday fight owns it), quest-only rows read "QUEST" loudly
	 * (either the literal Quest token, or - for a two-row pair like
	 * Vorkath's 'Dragon Slayer II' next to 'Post-quest' - the sibling
	 * that is not the post-quest fight). versionTier already sorts
	 * anything containing "quest" to the bottom of a bare-name search. */
	static List<MonsterStats> normalizeQuestVersions(List<MonsterStats> monsters)
	{
		Map<String, List<MonsterStats>> byName = new java.util.LinkedHashMap<>();
		for (MonsterStats monster : monsters)
		{
			byName.computeIfAbsent(monster.getNameLower(), k -> new ArrayList<>()).add(monster);
		}
		List<MonsterStats> out = new ArrayList<>(monsters.size());
		for (List<MonsterStats> group : byName.values())
		{
			boolean pairWithPostQuest = group.size() == 2 && group.stream()
				.anyMatch(m -> m.getVersion().toLowerCase(java.util.Locale.ROOT).contains("post-quest"));
			for (MonsterStats monster : group)
			{
				String version = monster.getVersion();
				if (version == null || version.isEmpty())
				{
					out.add(monster);
					continue;
				}
				List<String> kept = new ArrayList<>();
				boolean quest = false;
				for (String token : version.split(",\\s*"))
				{
					if (token.equalsIgnoreCase("awake") || token.equalsIgnoreCase("post-quest"))
					{
						continue;
					}
					if (token.equalsIgnoreCase("quest"))
					{
						quest = true;
						continue;
					}
					kept.add(token);
				}
				if (pairWithPostQuest && !quest
					&& !version.toLowerCase(java.util.Locale.ROOT).contains("post-quest"))
				{
					// The non-post-quest half of the pair IS the quest fight.
					quest = true;
					kept.clear();
				}
				String normalized = String.join(", ", kept);
				if (quest)
				{
					normalized = normalized.isEmpty() ? "QUEST" : normalized + ", QUEST";
				}
				out.add(normalized.equals(version) ? monster : monster.withVersion(normalized));
			}
		}
		return out;
	}

	private List<MonsterStats> loadMonsters()
	{
		JsonArray rows = readArray(MONSTER_RESOURCE);
		// The wiki data lists one row per SPAWN - Tormented Demon (1)..(4)
		// are four combat-identical rows. Collapse same-name rows whose
		// combat-relevant stats match, and drop the version label entirely
		// when a name has only one distinct stat block (Dusk keeps its
		// First/Second form; the four TDs become one unlabeled entry).
		Map<String, Set<String>> statKeysByName = new HashMap<>();
		// Collapsed groups display the HIGHEST combat level among their spawns.
		Map<String, Integer> maxLevelByGroup = new HashMap<>();
		for (JsonElement element : rows)
		{
			JsonObject row = element.getAsJsonObject();
			String nameKey = string(row, "name").toLowerCase(Locale.ROOT);
			statKeysByName
				.computeIfAbsent(nameKey, k -> new LinkedHashSet<>())
				.add(monsterStatKey(row));
			maxLevelByGroup.merge(nameKey + "|" + monsterStatKey(row),
				integer(row, "level", 0), Math::max);
		}

		Set<String> emitted = new HashSet<>();
		List<MonsterStats> result = new ArrayList<>(rows.size());
		for (JsonElement element : rows)
		{
			JsonObject row = element.getAsJsonObject();
			String nameKey = string(row, "name").toLowerCase(Locale.ROOT);
			String groupKey = nameKey + "|" + monsterStatKey(row);
			// Emit the HIGHEST-LEVEL spawn of each collapsed group, so the
			// offensive sheet (used for incoming dps) matches the combat
			// level the label displays.
			if (integer(row, "level", 0) != maxLevelByGroup.get(groupKey)
				|| !emitted.add(groupKey))
			{
				continue;
			}
			boolean distinctVersions = statKeysByName.get(nameKey).size() > 1;
			JsonObject skills = object(row, "skills");
			JsonObject offensive = object(row, "offensive");
			JsonObject defensive = object(row, "defensive");
			JsonObject weakness = object(row, "weakness");
			List<String> attributes = new ArrayList<>();
			JsonArray attrArray = array(row, "attributes");
			for (JsonElement attr : attrArray)
			{
				if (!attr.isJsonNull())
				{
					attributes.add(attr.getAsString());
				}
			}
			// Burn immunity rides the attribute list as a synthetic marker
			// (no MonsterStats signature churn): the eclipse/scorching burn
			// class is a NORMAL burn, blocked by Normal or Strong immunity
			// (official calc isImmuneToNormalBurns; Weak does not block it).
			JsonObject immunities = row.has("immunities") && row.get("immunities").isJsonObject()
				? row.getAsJsonObject("immunities") : null;
			String burn = immunities == null ? "" : string(immunities, "burn");
			if ("normal".equalsIgnoreCase(burn) || "strong".equalsIgnoreCase(burn))
			{
				attributes.add("burn_immune");
			}

			result.add(new MonsterStats(
				integer(row, "id", -1),
				string(row, "name"),
				distinctVersions ? string(row, "version") : "",
				maxLevelByGroup.get(nameKey + "|" + monsterStatKey(row)),
				integer(skills, "hp", 1),
				integer(row, "size", 1),
				integer(skills, "def", 1),
				integer(skills, "magic", 1),
				integer(offensive, "magic", 0),
				parseMonsterDefences(defensive),
				new MonsterOffence(
					integer(skills, "atk", 1),
					integer(skills, "str", 1),
					integer(skills, "ranged", 1),
					integer(skills, "magic", 1),
					integer(offensive, "atk", 0),
					integer(offensive, "str", 0),
					integer(offensive, "ranged", 0),
					integer(offensive, "ranged_str", 0),
					integer(offensive, "magic", 0),
					integer(offensive, "magic_str", 0),
					integer(row, "speed", 4),
					styleList(row)),
				attributes,
				bool(row, "is_slayer_monster", false) || knownSlayerMonster(string(row, "name")),
				string(weakness, "element"),
				integer(weakness, "severity", 0)));
		}
		result.sort(Comparator.comparing(MonsterStats::getName).thenComparing(MonsterStats::getVersion).thenComparingInt(MonsterStats::getId));
		return result;
	}

	private static List<String> styleList(JsonObject row)
	{
		List<String> styles = new ArrayList<>();
		for (JsonElement e : array(row, "style"))
		{
			if (!e.isJsonNull() && !e.getAsString().isEmpty())
			{
				styles.add(e.getAsString());
			}
		}
		return styles;
	}

	/** Everything the engine reads from a monster - rows agreeing on this are one entry. */
	private static String monsterStatKey(JsonObject row)
	{
		JsonObject skills = object(row, "skills");
		JsonObject offensive = object(row, "offensive");
		return integer(skills, "def", 1) + "|" + integer(skills, "magic", 1)
			+ "|" + integer(offensive, "magic", 0)
			+ "|" + object(row, "defensive").toString()
			+ "|" + array(row, "attributes").toString()
			+ "|" + bool(row, "is_slayer_monster", false)
			+ "|" + object(row, "weakness").toString()
			+ "|" + integer(row, "size", 1);
	}

	private List<SpellStats> loadSpells()
	{
		JsonArray rows = readArray(SPELL_RESOURCE);
		List<SpellStats> result = new ArrayList<>(rows.size());
		for (JsonElement element : rows)
		{
			JsonObject row = element.getAsJsonObject();
			int maxHit = integer(row, "max_hit", 0);
			String name = string(row, "name");
			if (maxHit <= 0 && !"Magic Dart".equals(name))
			{
				continue;
			}
			// Effect-only spells (leagues echoes, boss mechanics - e.g.
			// Flames of Cerberus) are flagged unselectable in the wiki data;
			// a player can never cast them, so they never enter the corpus.
			if (bool(row, "unselectable", false))
			{
				continue;
			}
			result.add(new SpellStats(
				name,
				"Magic Dart".equals(name) ? 1 : maxHit,
				spellLevel(name),
				string(row, "spellbook"),
				string(row, "element")));
		}
		result.sort(Comparator.comparingInt(SpellStats::getMaxHit).thenComparing(SpellStats::getName));
		return result;
	}

	private static int spellLevel(String name)
	{
		switch (name == null ? "" : name)
		{
			case "Wind Strike":
				return 1;
			case "Water Strike":
				return 5;
			case "Earth Strike":
				return 9;
			case "Fire Strike":
				return 13;
			case "Wind Bolt":
				return 17;
			case "Water Bolt":
				return 23;
			case "Earth Bolt":
				return 29;
			case "Fire Bolt":
				return 35;
			case "Crumble Undead":
				return 39;
			case "Wind Blast":
				return 41;
			case "Water Blast":
				return 47;
			case "Iban Blast":
				return 50;
			case "Magic Dart":
				return 50;
			case "Earth Blast":
				return 53;
			case "Fire Blast":
				return 59;
			case "Saradomin Strike":
			case "Claws of Guthix":
			case "Flames of Zamorak":
				return 60;
			case "Wind Wave":
				return 62;
			case "Water Wave":
				return 65;
			case "Earth Wave":
				return 70;
			case "Fire Wave":
				return 75;
			case "Wind Surge":
				return 81;
			case "Water Surge":
				return 85;
			case "Earth Surge":
				return 90;
			case "Fire Surge":
				return 95;
			case "Smoke Rush":
				return 50;
			case "Shadow Rush":
				return 52;
			case "Blood Rush":
				return 56;
			case "Ice Rush":
				return 58;
			case "Smoke Burst":
				return 62;
			case "Shadow Burst":
				return 64;
			case "Blood Burst":
				return 68;
			case "Ice Burst":
				return 70;
			case "Smoke Blitz":
				return 74;
			case "Shadow Blitz":
				return 76;
			case "Blood Blitz":
				return 80;
			case "Ice Blitz":
				return 82;
			case "Smoke Barrage":
				return 86;
			case "Shadow Barrage":
				return 88;
			case "Blood Barrage":
				return 92;
			case "Ice Barrage":
				return 94;
			case "Ghostly Grasp":
				return 35;
			case "Skeletal Grasp":
				return 56;
			case "Undead Grasp":
				return 79;
			case "Inferior Demonbane":
				return 44;
			case "Superior Demonbane":
				return 62;
			case "Dark Demonbane":
				return 82;
			default:
				return 1;
		}
	}

	private static JsonArray readArray(String resource)
	{
		try (InputStream stream = DataService.class.getResourceAsStream(resource))
		{
			if (stream == null)
			{
				throw new IllegalStateException("Missing resource " + resource);
			}
			try (InputStreamReader reader = new InputStreamReader(new GZIPInputStream(stream), StandardCharsets.UTF_8))
			{
				return new JsonParser().parse(reader).getAsJsonArray();
			}
		}
		catch (IOException ex)
		{
			throw new IllegalStateException("Could not load " + resource, ex);
		}
	}

	private static JsonObject readObject(String resource)
	{
		try (InputStream stream = DataService.class.getResourceAsStream(resource))
		{
			if (stream == null)
			{
				throw new IllegalStateException("Missing resource " + resource);
			}
			try (InputStreamReader reader = new InputStreamReader(new GZIPInputStream(stream), StandardCharsets.UTF_8))
			{
				return new JsonParser().parse(reader).getAsJsonObject();
			}
		}
		catch (IOException ex)
		{
			throw new IllegalStateException("Could not load " + resource, ex);
		}
	}

	private static StatBlock parseOffensive(JsonObject object)
	{
		if (object == null)
		{
			return StatBlock.ZERO;
		}
		return new StatBlock(
			integer(object, "stab", 0),
			integer(object, "slash", 0),
			integer(object, "crush", 0),
			integer(object, "magic", 0),
			integer(object, "ranged", 0),
			0,
			0,
			0,
			0);
	}

	private static StatBlock parseDefensive(JsonObject object)
	{
		if (object == null)
		{
			return StatBlock.ZERO;
		}
		return new StatBlock(
			integer(object, "stab", 0),
			integer(object, "slash", 0),
			integer(object, "crush", 0),
			integer(object, "magic", 0),
			integer(object, "ranged", 0),
			0,
			integer(object, "light", 0),
			integer(object, "standard", 0),
			integer(object, "heavy", 0));
	}

	private static MonsterDefences parseMonsterDefences(JsonObject object)
	{
		if (object == null)
		{
			return MonsterDefences.ZERO;
		}
		return new MonsterDefences(
			integer(object, "stab", 0),
			integer(object, "slash", 0),
			integer(object, "crush", 0),
			integer(object, "magic", 0),
			integer(object, "ranged", 0),
			integer(object, "flat_armour", 0),
			integer(object, "light", 0),
			integer(object, "standard", 0),
			integer(object, "heavy", 0));
	}

	private static StatBlock parseBonuses(JsonObject object)
	{
		if (object == null)
		{
			return StatBlock.ZERO;
		}
		return new StatBlock(
			0,
			0,
			0,
			0,
			0,
			integer(object, "str", 0),
			integer(object, "ranged_str", 0),
			integer(object, "magic_str", 0),
			integer(object, "prayer", 0));
	}

	private static JsonObject object(JsonObject parent, String key)
	{
		JsonElement element = parent.get(key);
		return element == null || element.isJsonNull() ? new JsonObject() : element.getAsJsonObject();
	}

	private static JsonArray array(JsonObject parent, String key)
	{
		JsonElement element = parent.get(key);
		return element == null || element.isJsonNull() ? new JsonArray() : element.getAsJsonArray();
	}

	private static String string(JsonObject object, String key)
	{
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? "" : element.getAsString();
	}

	private static Integer nullableInteger(JsonObject object, String key)
	{
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? null : element.getAsInt();
	}

	private static int integer(JsonObject object, String key, int fallback)
	{
		JsonElement element = object.get(key);
		if (element == null || element.isJsonNull())
		{
			return fallback;
		}
		try
		{
			return element.getAsInt();
		}
		catch (NumberFormatException ex)
		{
			return fallback;
		}
	}

	private static boolean bool(JsonObject object, String key, boolean fallback)
	{
		JsonElement element = object.get(key);
		return element == null || element.isJsonNull() ? fallback : element.getAsBoolean();
	}

	private static boolean knownSlayerMonster(String name)
	{
		String normalized = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
		return normalized.contains("aberrant spectre")
			|| normalized.contains("abyssal demon")
			|| normalized.contains("banshee")
			|| normalized.contains("basilisk")
			|| normalized.contains("bloodveld")
			|| normalized.contains("cave crawler")
			|| normalized.contains("cave horror")
			|| normalized.contains("crawling hand")
			|| normalized.contains("dust devil")
			|| normalized.contains("gargoyle")
			|| normalized.contains("kurask")
			|| normalized.contains("nechryael")
			|| normalized.contains("rockslug")
			|| normalized.contains("skeletal wyvern")
			|| normalized.contains("smoke devil")
			|| normalized.contains("turoth")
			|| normalized.contains("wyrm")
			|| normalized.contains("drake")
			|| normalized.contains("hydra")
			|| normalized.contains("tzkal-zuk")
			|| normalized.contains("tztok-jad")
			|| normalized.contains("jaltok-jad")
			|| normalized.contains("tzhaar")
			|| normalized.contains("tok-xil")
			|| normalized.contains("yt-mejkot")
			|| normalized.contains("yt-hurkot")
			|| normalized.contains("ket-zek")
			|| normalized.contains("tz-kih")
			|| normalized.contains("tz-kek")
			|| normalized.startsWith("jal-");
	}
}
