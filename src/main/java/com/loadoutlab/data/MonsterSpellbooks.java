package com.loadoutlab.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Fight-level spellbook requirements (wiki-verified, curated): some
 * fights want a specific book for their MECHANICS regardless of the
 * gear set - the Abyssal Sire's Shadow Barrage stun is the founding
 * case (field report 2026-08-15). Matched by lowercase name-contains
 * so phase and form suffixes ride along.
 */
public final class MonsterSpellbooks
{
	/** name-fragment -> [book, reason] */
	private static final Map<String, String[]> BY_NAME = new LinkedHashMap<>();

	static
	{
		JsonObject root = JsonResources.object(
			"/com/loadoutlab/data/monster_spellbooks.json");
		if (root != null && root.has("byNameContains"))
		{
			for (Map.Entry<String, JsonElement> e
				: root.getAsJsonObject("byNameContains").entrySet())
			{
				JsonObject node = e.getValue().getAsJsonObject();
				BY_NAME.put(e.getKey().toLowerCase(Locale.ROOT), new String[]{
					node.get("book").getAsString(), node.get("reason").getAsString()});
			}
		}
	}

	private MonsterSpellbooks()
	{
	}

	/** The fight's required book ("ancient"...) or null. */
	public static String bookFor(MonsterStats monster)
	{
		String[] entry = lookup(monster);
		return entry == null ? null : entry[0];
	}

	/** Why the fight wants it, or null. */
	public static String reasonFor(MonsterStats monster)
	{
		String[] entry = lookup(monster);
		return entry == null ? null : entry[1];
	}

	private static String[] lookup(MonsterStats monster)
	{
		if (monster == null)
		{
			return null;
		}
		String name = monster.getName().toLowerCase(Locale.ROOT);
		for (Map.Entry<String, String[]> e : BY_NAME.entrySet())
		{
			if (name.contains(e.getKey()))
			{
				return e.getValue();
			}
		}
		return null;
	}
}
