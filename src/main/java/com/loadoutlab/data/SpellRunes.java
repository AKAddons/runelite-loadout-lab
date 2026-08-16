package com.loadoutlab.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runes per cast for every corpus spell (wiki-fetched, see the
 * resource's note). Elemental staves cover their element: the worn
 * weapon and shield names decide which requirements drop - the combo
 * staves (smoke/mist/dust/mud/lava/steam) cover two, the Kodai wand
 * covers water, the tomes cover their book's element.
 */
public final class SpellRunes
{
	private static final Map<String, Integer> RUNE_ITEMS = new LinkedHashMap<>();
	private static final Map<String, Map<String, Integer>> BY_SPELL = new LinkedHashMap<>();

	static
	{
		JsonObject root = JsonResources.object("/com/loadoutlab/data/spell_runes.json");
		if (root != null)
		{
			JsonResources.stringIntMap(root, "runeItems", RUNE_ITEMS);
			if (root.has("bySpell"))
			{
				for (Map.Entry<String, JsonElement> spell
					: root.getAsJsonObject("bySpell").entrySet())
				{
					Map<String, Integer> runes = new LinkedHashMap<>();
					for (Map.Entry<String, JsonElement> rune
						: spell.getValue().getAsJsonObject().entrySet())
					{
						runes.put(rune.getKey(), rune.getValue().getAsInt());
					}
					BY_SPELL.put(spell.getKey(), runes);
				}
			}
		}
	}

	private SpellRunes()
	{
	}

	/** The elements a worn item covers, by name (empty = none). */
	private static List<String> covers(String nameLower)
	{
		List<String> out = new ArrayList<>(2);
		if (nameLower == null)
		{
			return out;
		}
		if (nameLower.contains("smoke")) { out.add("air"); out.add("fire"); }
		else if (nameLower.contains("mist")) { out.add("air"); out.add("water"); }
		else if (nameLower.contains("dust")) { out.add("air"); out.add("earth"); }
		else if (nameLower.contains("mud")) { out.add("water"); out.add("earth"); }
		else if (nameLower.contains("lava")) { out.add("earth"); out.add("fire"); }
		else if (nameLower.contains("steam")) { out.add("water"); out.add("fire"); }
		else if (nameLower.contains("kodai")) { out.add("water"); }
		else
		{
			for (String element : new String[]{"air", "water", "earth", "fire"})
			{
				// "staff of air", "air battlestaff", "mystic fire staff",
				// "tome of fire" - the element word is the signal, but only
				// on staves and tomes (an "earth warrior" style name never
				// reaches here: gear names only).
				if (nameLower.contains(element)
					&& (nameLower.contains("staff") || nameLower.contains("tome")))
				{
					out.add(element);
				}
			}
		}
		return out;
	}

	/** [{id, name, qty}] for one cast of the spell, minus what the worn
	 * weapon/shield provide; null when the spell is unknown. */
	public static List<Map<String, Object>> costFor(String spellName,
		String weaponNameLower, String shieldNameLower)
	{
		Map<String, Integer> runes = spellName == null ? null : BY_SPELL.get(spellName);
		if (runes == null)
		{
			return null;
		}
		List<String> covered = covers(weaponNameLower);
		covered.addAll(covers(shieldNameLower));
		List<Map<String, Object>> out = new ArrayList<>();
		for (Map.Entry<String, Integer> rune : runes.entrySet())
		{
			if (covered.contains(rune.getKey()))
			{
				continue;
			}
			Integer itemId = RUNE_ITEMS.get(rune.getKey());
			if (itemId == null)
			{
				continue;
			}
			Map<String, Object> node = new LinkedHashMap<>();
			node.put("id", itemId);
			node.put("name", capitalize(rune.getKey()) + " rune");
			node.put("qty", rune.getValue());
			out.add(node);
		}
		return out;
	}

	private static String capitalize(String word)
	{
		return Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase(Locale.ROOT);
	}
}
