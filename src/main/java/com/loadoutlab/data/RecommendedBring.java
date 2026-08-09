package com.loadoutlab.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Curated per-monster "bring this" recommendations, chipped into the
 * inventory row. The mechanics NOTE tells the player why; these chips
 * SHOW the items (field report 2026-08-06: the Inferno's barrage
 * stipulation rendered as prose but nothing recommended the runes or
 * the spellbook). Rune chips name their spell AND spellbook in the
 * tooltip - counts are deliberately absent, like every supply chip.
 * Data-driven from recommended_bring.json (a token-free resource,
 * Andrew's "all of this data in json" call 2026-08-08): finishing
 * items, casting runes, and poison/venom protection all live there as
 * rows - extending coverage is a data edit, never code.
 */
public final class RecommendedBring
{
	private static final class Rule
	{
		final List<String> monsters;
		final String versionStartsWith;
		final String spellbook;
		final Map<Integer, String> chips;

		Rule(List<String> monsters, String versionStartsWith, String spellbook,
			Map<Integer, String> chips)
		{
			this.monsters = monsters;
			this.versionStartsWith = versionStartsWith;
			this.spellbook = spellbook;
			this.chips = chips;
		}
	}

	private static final List<Rule> RULES = new ArrayList<>();
	private static final Set<Integer> RUNES = new HashSet<>();
	private static final Map<Integer, Set<Integer>> REDUNDANT = new LinkedHashMap<>();

	static
	{
		// Fail LOUD (the name_rules pattern).
		JsonObject root = JsonResources.objectOrThrow(
			"/com/loadoutlab/data/recommended_bring.json");
		for (JsonElement e : root.getAsJsonArray("rules"))
		{
			JsonObject row = e.getAsJsonObject();
			List<String> monsters = new ArrayList<>();
			JsonResources.strings(row, "monsters", monsters);
			Map<Integer, String> chips = new LinkedHashMap<>();
			for (JsonElement c : row.getAsJsonArray("chips"))
			{
				JsonObject chip = c.getAsJsonObject();
				int id = chip.get("id").getAsInt();
				chips.put(id, chip.get("tooltip").getAsString());
				if (chip.has("rune") && chip.get("rune").getAsBoolean())
				{
					RUNES.add(id);
				}
				if (chip.has("redundantWith"))
				{
					Set<Integer> redundant = REDUNDANT.computeIfAbsent(id,
						k -> new HashSet<>());
					for (JsonElement r : chip.getAsJsonArray("redundantWith"))
					{
						redundant.add(r.getAsInt());
					}
				}
			}
			RULES.add(new Rule(monsters,
				row.has("versionStartsWith")
					? row.get("versionStartsWith").getAsString() : null,
				row.has("spellbook") ? row.get("spellbook").getAsString() : null,
				chips));
		}
		if (RULES.isEmpty())
		{
			throw new IllegalStateException("recommended_bring.json loaded empty");
		}
	}

	private RecommendedBring()
	{
	}

	private static boolean matches(Rule rule, MonsterStats monster, String name)
	{
		return rule.monsters.contains(name)
			&& (rule.versionStartsWith == null
				|| monster.versionStartsWith(rule.versionStartsWith));
	}

	/** True when the chip is a casting rune - the caller adds the rune
	 * pouch alongside. */
	public static boolean isRune(int itemId)
	{
		return RUNES.contains(itemId);
	}

	/** True when the shown answer already makes this chip pointless -
	 * a rock hammer is dead weight IF AND ONLY IF the granite hammer is
	 * in the set (it auto-smashes; field refinement 2026-08-09). */
	public static boolean redundant(int chipId, Set<Integer> shownItemIds)
	{
		Set<Integer> redundant = REDUNDANT.get(chipId);
		if (redundant == null)
		{
			return false;
		}
		for (int id : redundant)
		{
			if (shownItemIds.contains(id))
			{
				return true;
			}
		}
		return false;
	}

	/** True when this monster's recommendation puts the trip on a specific
	 * NON-ARCEUUS spellbook - Ancients for the barrage stipulations,
	 * standard for Vorkath's Crumble Undead - which has no path to Arceuus
	 * summons, so the thrall/Death Charge folds must stand down (field
	 * reports 2026-08-06 and 2026-08-08). Derived from the rule's
	 * spellbook field, so data and switch can never drift. */
	public static boolean stipulatesSpellbook(MonsterStats monster)
	{
		if (monster == null)
		{
			return false;
		}
		String name = monster.getName().toLowerCase(Locale.ROOT);
		for (Rule rule : RULES)
		{
			if (rule.spellbook != null && matches(rule, monster, name))
			{
				return true;
			}
		}
		return false;
	}

	/** The curated chips for this monster (all matching rules merged):
	 * item id -> tooltip. Empty for monsters with no recommendation. */
	public static Map<Integer, String> chipsFor(MonsterStats monster)
	{
		LinkedHashMap<Integer, String> chips = new LinkedHashMap<>();
		if (monster == null)
		{
			return chips;
		}
		String name = monster.getName().toLowerCase(Locale.ROOT);
		for (Rule rule : RULES)
		{
			if (matches(rule, monster, name))
			{
				chips.putAll(rule.chips);
			}
		}
		return chips;
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
