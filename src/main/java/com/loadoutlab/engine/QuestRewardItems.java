package com.loadoutlab.engine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.loadoutlab.data.GearItem;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Gear earned by QUESTING rather than bought: quest rewards, reward-chest
 * purchases and quest-unlocked creations (barrows gloves, Iban's staff,
 * the vampyre flails, Ava's devices, god books...). With the upgrade
 * budget active ("show me obtainable gear") these join the candidate
 * pool at 0 gp - they cost effort, not coins - and the panel labels them
 * with their source quest instead of a price.
 *
 * Entries in quest_rewards.json are keyed by lowercase item name
 * (GearItem.getName(), no version suffix). Every entry is wiki-verified
 * as a quest reward or quest-unlocked acquisition; tradeable boss drops
 * merely wield-locked behind a quest (dragon scimitar, anguish) and
 * minigame/slayer unlocks (void, defenders, slayer helm) do NOT belong
 * here - the curation test enforces that every key resolves to a real
 * corpus item.
 */
public final class QuestRewardItems
{
	private static final String RESOURCE = "/com/loadoutlab/data/quest_rewards.json";

	private static final Map<String, String> QUEST_BY_NAME = new HashMap<>();

	static
	{
		try (InputStream stream = QuestRewardItems.class.getResourceAsStream(RESOURCE);
			InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
		{
			JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
			for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet())
			{
				JsonObject row = entry.getValue().getAsJsonObject();
				QUEST_BY_NAME.put(entry.getKey().toLowerCase(Locale.ROOT),
					row.get("quest").getAsString());
			}
		}
		catch (Exception ex)
		{
			throw new IllegalStateException("Could not load " + RESOURCE, ex);
		}
	}

	private QuestRewardItems()
	{
	}

	/** True when this item is earned through a quest rather than bought. */
	public static boolean isQuestReward(GearItem item)
	{
		return questFor(item) != null;
	}

	/** The source quest's name, or null when the item is not a quest reward. */
	public static String questFor(GearItem item)
	{
		if (item == null)
		{
			return null;
		}
		return QUEST_BY_NAME.get(item.getName().toLowerCase(Locale.ROOT));
	}

	/** Every curated item name (lowercase) - for corpus validation tests. */
	public static Set<String> itemNames()
	{
		return java.util.Collections.unmodifiableSet(QUEST_BY_NAME.keySet());
	}
}
