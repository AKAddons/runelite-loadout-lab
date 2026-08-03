package com.loadoutlab.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.runelite.api.Skill;
import java.util.LinkedHashMap;
import java.util.Collections;

/**
 * A player as data: levels (real and live-boosted), prayer unlocks,
 * equip requirements (skills + quests), and owned items. Everything a
 * query needs - so the optimizer can be driven by a MOCK environment
 * (fixtures, the headless runner, replayed bug reports) without a
 * running client. The plugin exports the real account to
 * .runelite/loadout-lab/profile.json on every query.
 */
public final class PlayerProfile
{
	public final PlayerLevels realLevels;
	public final PlayerLevels boostedLevels;
	public final PrayerUnlocks prayerUnlocks;
	public final RequirementProfile requirements;
	public final Map<Integer, Integer> owned;
	public final boolean bankScanned;
	/** Optional provenance: origin label ("bank", "STASH", ...) -> items.
	 * The flat owned map stays the source of truth for the optimizer. */
	public final Map<String, Map<Integer, Integer>> ownedBySource;

	public PlayerProfile(PlayerLevels realLevels, PlayerLevels boostedLevels,
		PrayerUnlocks prayerUnlocks, RequirementProfile requirements,
		Map<Integer, Integer> owned, boolean bankScanned)
	{
		this(realLevels, boostedLevels, prayerUnlocks, requirements, owned, bankScanned, null);
	}

	public PlayerProfile(PlayerLevels realLevels, PlayerLevels boostedLevels,
		PrayerUnlocks prayerUnlocks, RequirementProfile requirements,
		Map<Integer, Integer> owned, boolean bankScanned,
		Map<String, Map<Integer, Integer>> ownedBySource)
	{
		this.realLevels = realLevels;
		this.boostedLevels = boostedLevels == null ? realLevels : boostedLevels;
		this.prayerUnlocks = prayerUnlocks == null ? PrayerUnlocks.ALL : prayerUnlocks;
		this.requirements = requirements == null ? RequirementProfile.MAXED : requirements;
		this.owned = owned == null ? Map.of() : Map.copyOf(owned);
		this.bankScanned = bankScanned;
		// Deep-copied on the constructing thread: the export writer runs
		// off-thread while the client keeps mutating the live snapshots.
		LinkedHashMap<String, Map<Integer, Integer>> copy = new LinkedHashMap<>();
		if (ownedBySource != null)
		{
			for (Map.Entry<String, Map<Integer, Integer>> e : ownedBySource.entrySet())
			{
				copy.put(e.getKey(), Map.copyOf(e.getValue()));
			}
		}
		this.ownedBySource = Collections.unmodifiableMap(copy);
	}

	public OwnedItems ownedItems()
	{
		return new OwnedItems(owned, bankScanned);
	}

	public String toJson()
	{
		JsonObject root = new JsonObject();
		root.add("realLevels", levels(realLevels));
		root.add("boostedLevels", levels(boostedLevels));
		JsonObject prayers = new JsonObject();
		prayers.addProperty("kingsRansom", prayerUnlocks.piety());
		prayers.addProperty("rigour", prayerUnlocks.rigour());
		prayers.addProperty("augury", prayerUnlocks.augury());
		prayers.addProperty("deadeye", prayerUnlocks.deadeye());
		prayers.addProperty("mysticVigour", prayerUnlocks.mysticVigour());
		root.add("prayerUnlocks", prayers);
		JsonObject reqSkills = new JsonObject();
		for (Map.Entry<Skill, Integer> entry : requirements.getLevels().entrySet())
		{
			reqSkills.addProperty(entry.getKey().name(), entry.getValue());
		}
		JsonObject reqs = new JsonObject();
		reqs.add("skills", reqSkills);
		com.google.gson.JsonArray quests = new com.google.gson.JsonArray();
		for (String quest : requirements.getCompletedQuests())
		{
			quests.add(quest);
		}
		reqs.add("quests", quests);
		root.add("requirements", reqs);
		JsonObject ownedObj = new JsonObject();
		for (Map.Entry<Integer, Integer> entry : owned.entrySet())
		{
			ownedObj.addProperty(String.valueOf(entry.getKey()), entry.getValue());
		}
		root.add("owned", ownedObj);
		if (!ownedBySource.isEmpty())
		{
			JsonObject bySource = new JsonObject();
			for (Map.Entry<String, Map<Integer, Integer>> origin : ownedBySource.entrySet())
			{
				JsonObject originItems = new JsonObject();
				for (Map.Entry<Integer, Integer> entry : origin.getValue().entrySet())
				{
					originItems.addProperty(String.valueOf(entry.getKey()), entry.getValue());
				}
				bySource.add(origin.getKey(), originItems);
			}
			root.add("ownedBySource", bySource);
		}
		root.addProperty("bankScanned", bankScanned);
		return root.toString();
	}

	public static PlayerProfile fromJson(String json)
	{
		JsonObject root = new JsonParser().parse(json).getAsJsonObject();
		JsonObject prayers = root.getAsJsonObject("prayerUnlocks");
		PrayerUnlocks unlocks = new PrayerUnlocks(
			prayers.get("kingsRansom").getAsBoolean(),
			prayers.get("rigour").getAsBoolean(),
			prayers.get("augury").getAsBoolean(),
			prayers.get("deadeye").getAsBoolean(),
			prayers.get("mysticVigour").getAsBoolean());
		JsonObject reqs = root.getAsJsonObject("requirements");
		Map<Skill, Integer> reqSkills = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : reqs.getAsJsonObject("skills").entrySet())
		{
			reqSkills.put(Skill.valueOf(entry.getKey()), entry.getValue().getAsInt());
		}
		Set<String> quests = new HashSet<>();
		for (JsonElement quest : reqs.getAsJsonArray("quests"))
		{
			quests.add(quest.getAsString());
		}
		Map<Integer, Integer> owned = new HashMap<>();
		for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("owned").entrySet())
		{
			owned.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsInt());
		}
		// Optional (older exports lack it) - absence means no provenance.
		Map<String, Map<Integer, Integer>> bySource = null;
		if (root.has("ownedBySource"))
		{
			bySource = new LinkedHashMap<>();
			for (Map.Entry<String, JsonElement> origin : root.getAsJsonObject("ownedBySource").entrySet())
			{
				Map<Integer, Integer> items = new HashMap<>();
				for (Map.Entry<String, JsonElement> entry : origin.getValue().getAsJsonObject().entrySet())
				{
					items.put(Integer.parseInt(entry.getKey()), entry.getValue().getAsInt());
				}
				bySource.put(origin.getKey(), items);
			}
		}
		return new PlayerProfile(
			parseLevels(root.getAsJsonObject("realLevels")),
			parseLevels(root.getAsJsonObject("boostedLevels")),
			unlocks,
			new RequirementProfile(reqSkills, quests),
			owned,
			root.get("bankScanned").getAsBoolean(),
			bySource);
	}

	private static JsonObject levels(PlayerLevels levels)
	{
		JsonObject object = new JsonObject();
		object.addProperty("attack", levels.getAttack());
		object.addProperty("strength", levels.getStrength());
		object.addProperty("defence", levels.getDefence());
		object.addProperty("ranged", levels.getRanged());
		object.addProperty("magic", levels.getMagic());
		object.addProperty("prayer", levels.getPrayer());
		object.addProperty("hitpoints", levels.getHitpoints());
		return object;
	}

	private static PlayerLevels parseLevels(JsonObject object)
	{
		return new PlayerLevels(
			object.get("attack").getAsInt(),
			object.get("strength").getAsInt(),
			object.get("defence").getAsInt(),
			object.get("ranged").getAsInt(),
			object.get("magic").getAsInt(),
			object.get("prayer").getAsInt(),
			object.get("hitpoints").getAsInt());
	}
}
