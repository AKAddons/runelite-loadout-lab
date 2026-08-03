package com.loadoutlab.engine;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.JsonResources;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Melee combat styles available per weapon category - game facts from the
 * wiki's weapon-type pages (cross-checked against the official calculator,
 * 2026-07-05). The upstream best-dps engine assumed every melee weapon has
 * every attack type at every stance, which invented styles weapons don't
 * have: a whip has no aggressive stance, and a Blunt weapon cannot slash -
 * that one let a barronite mace "slash" a Grey golem (slash defence 1,
 * everything else 300) for a 3x accuracy error against the official calc.
 *
 * <p>Stances: accurate = +3 attack, aggressive = +3 strength,
 * controlled = +1 both. Defensive is omitted - it adds no offence, so it
 * can never be the best-DPS pick.
 *
 * <p>The table itself lives in weapon_styles.json (hub token cap: data goes
 * in resources, not source). It is GENERATED from the old hardcoded table by
 * scripts/extract_weapon_styles.py, which asserts 24 categories, 56 style
 * entries and the 9-entry fallback row - regenerate with that script, never
 * by hand.
 */
public final class WeaponStyles
{
	/** One selectable style: the attack type plus its stance bonuses. */
	public static final class MeleeStyle
	{
		public final String attackType;
		public final int attackStance;
		public final int strengthStance;

		MeleeStyle(String attackType, int attackStance, int strengthStance)
		{
			this.attackType = attackType;
			this.attackStance = attackStance;
			this.strengthStance = strengthStance;
		}
	}

	private static final String RESOURCE = "/com/loadoutlab/data/weapon_styles.json";
	/** Resource key of the unknown-category fallback row - not a real weapon
	 * category, so it can never collide with one. */
	private static final String FALLBACK = "*";

	private static final Map<String, List<MeleeStyle>> BY_CATEGORY = load();

	/** Every attack type x stance a full search would try - the fallback. */
	private static final List<MeleeStyle> ALL =
		BY_CATEGORY.getOrDefault(FALLBACK, Collections.emptyList());

	private static Map<String, List<MeleeStyle>> load()
	{
		Map<String, List<MeleeStyle>> table = new HashMap<>();
		JsonObject root = JsonResources.object(RESOURCE);
		if (root == null)
		{
			return table;
		}
		for (Map.Entry<String, JsonElement> entry : root.entrySet())
		{
			List<MeleeStyle> styles = new ArrayList<>();
			for (JsonElement element : entry.getValue().getAsJsonArray())
			{
				JsonObject style = element.getAsJsonObject();
				styles.add(new MeleeStyle(style.get("attackType").getAsString(),
					style.get("attackStance").getAsInt(),
					style.get("strengthStance").getAsInt()));
			}
			table.put(entry.getKey(), Collections.unmodifiableList(styles));
		}
		return table;
	}

	private WeaponStyles()
	{
	}

	/** The melee styles this weapon actually offers (unknown category: all). */
	public static List<MeleeStyle> melee(GearItem weapon)
	{
		// Null weapon = bare fists, which is a real category row. Looking it up
		// through the same map (rather than returning it directly) is what
		// keeps a missing resource from returning null here and NPE-ing
		// DpsCalculator: an absent row falls back to ALL, and ALL itself
		// defaults to an empty list.
		List<MeleeStyle> styles = BY_CATEGORY.get(
			weapon == null ? "Unarmed" : weapon.getCategory());
		return styles != null ? styles : ALL;
	}
}
