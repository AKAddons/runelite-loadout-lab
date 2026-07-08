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

	public PlayerProfile(PlayerLevels realLevels, PlayerLevels boostedLevels,
		PrayerUnlocks prayerUnlocks, RequirementProfile requirements,
		Map<Integer, Integer> owned, boolean bankScanned)
	{
		this.realLevels = realLevels;
		this.boostedLevels = boostedLevels == null ? realLevels : boostedLevels;
		this.prayerUnlocks = prayerUnlocks == null ? PrayerUnlocks.ALL : prayerUnlocks;
		this.requirements = requirements == null ? RequirementProfile.MAXED : requirements;
		this.owned = owned == null ? Map.of() : Map.copyOf(owned);
		this.bankScanned = bankScanned;
	}

	public OwnedItems ownedItems()
	{
		return new OwnedItems(owned, bankScanned);
	}

	/** A maxed account that owns nothing - game-best queries only. */
	public static PlayerProfile maxed()
	{
		return new PlayerProfile(PlayerLevels.MAXED, PlayerLevels.MAXED,
			PrayerUnlocks.ALL, RequirementProfile.MAXED, Map.of(), true);
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
		return new PlayerProfile(
			parseLevels(root.getAsJsonObject("realLevels")),
			parseLevels(root.getAsJsonObject("boostedLevels")),
			unlocks,
			new RequirementProfile(reqSkills, quests),
			owned,
			root.get("bankScanned").getAsBoolean());
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
