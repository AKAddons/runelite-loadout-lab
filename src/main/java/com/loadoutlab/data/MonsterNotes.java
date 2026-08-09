package com.loadoutlab.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Curated per-monster mechanics the stat data cannot express - finishing
 * items, immunities, environment hazards - shown as a note under the
 * selected monster so a mathematically-correct suggestion doesn't read
 * as a wrong one (e.g. the tentacle out-DPSes the granite hammer vs
 * Dusk by ~25%; the hammer's value is the auto-smash). Data-driven from
 * monster_notes.json (a token-free resource, Andrew's "all of this data
 * in json" call 2026-08-08); rows wiki-verified at encode time.
 */
public final class MonsterNotes
{
	private static final class Rule
	{
		final List<String> monsters;
		final String versionStartsWith;
		final String note;

		Rule(List<String> monsters, String versionStartsWith, String note)
		{
			this.monsters = monsters;
			this.versionStartsWith = versionStartsWith;
			this.note = note;
		}
	}

	private static final List<Rule> RULES = new ArrayList<>();

	static
	{
		// Fail LOUD (the name_rules pattern): silently-empty prose would
		// drop every warning with no visible error.
		JsonObject root = JsonResources.objectOrThrow(
			"/com/loadoutlab/data/monster_notes.json");
		for (JsonElement e : root.getAsJsonArray("rules"))
		{
			JsonObject row = e.getAsJsonObject();
			List<String> monsters = new ArrayList<>();
			JsonResources.strings(row, "monsters", monsters);
			RULES.add(new Rule(monsters,
				row.has("versionStartsWith")
					? row.get("versionStartsWith").getAsString() : null,
				row.get("note").getAsString()));
		}
		if (RULES.isEmpty())
		{
			throw new IllegalStateException("monster_notes.json loaded empty");
		}
	}

	private MonsterNotes()
	{
	}

	/** The mechanics notes for this monster (all matching rules,
	 * concatenated), or null. */
	public static String noteFor(MonsterStats monster)
	{
		if (monster == null)
		{
			return null;
		}
		String name = monster.getName().toLowerCase(Locale.ROOT);
		StringBuilder sb = new StringBuilder();
		for (Rule rule : RULES)
		{
			if (rule.monsters.contains(name)
				&& (rule.versionStartsWith == null
					|| monster.versionStartsWith(rule.versionStartsWith)))
			{
				if (sb.length() > 0)
				{
					sb.append(' ');
				}
				sb.append(rule.note);
			}
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	/** Every curated monster key (test seam - the binding pin). */
	public static List<String> monsterKeys()
	{
		List<String> keys = new ArrayList<>();
		for (Rule rule : RULES)
		{
			keys.addAll(rule.monsters);
		}
		return keys;
	}
}
