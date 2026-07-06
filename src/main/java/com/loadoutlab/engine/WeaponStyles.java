package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
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

	private static MeleeStyle accurate(String type)
	{
		return new MeleeStyle(type, 3, 0);
	}

	private static MeleeStyle aggressive(String type)
	{
		return new MeleeStyle(type, 0, 3);
	}

	private static MeleeStyle controlled(String type)
	{
		return new MeleeStyle(type, 1, 1);
	}

	private static final Map<String, List<MeleeStyle>> BY_CATEGORY = new HashMap<>();

	static
	{
		BY_CATEGORY.put("2h Sword", List.of(accurate("slash"), aggressive("slash"), aggressive("crush")));
		BY_CATEGORY.put("Axe", List.of(accurate("slash"), aggressive("slash"), aggressive("crush")));
		BY_CATEGORY.put("Banner", List.of(accurate("stab"), aggressive("slash"), controlled("crush")));
		BY_CATEGORY.put("Bladed Staff", List.of(accurate("stab"), aggressive("slash")));
		BY_CATEGORY.put("Bludgeon", List.of(aggressive("crush")));
		BY_CATEGORY.put("Blunt", List.of(accurate("crush"), aggressive("crush")));
		BY_CATEGORY.put("Bulwark", List.of(accurate("crush")));
		BY_CATEGORY.put("Claw", List.of(accurate("slash"), aggressive("slash"), controlled("stab")));
		BY_CATEGORY.put("Flail", List.of(accurate("slash"), aggressive("slash")));
		BY_CATEGORY.put("Gun", List.of(aggressive("crush")));
		BY_CATEGORY.put("Multi-Melee", List.of(accurate("stab"), aggressive("slash"), aggressive("crush")));
		BY_CATEGORY.put("Partisan", List.of(accurate("stab"), aggressive("stab"), aggressive("crush")));
		BY_CATEGORY.put("Pickaxe", List.of(accurate("stab"), aggressive("stab"), aggressive("crush")));
		BY_CATEGORY.put("Polearm", List.of(controlled("stab"), aggressive("slash")));
		BY_CATEGORY.put("Polestaff", List.of(accurate("crush"), aggressive("crush")));
		BY_CATEGORY.put("Salamander", List.of(aggressive("slash")));
		BY_CATEGORY.put("Scythe", List.of(accurate("slash"), aggressive("slash"), aggressive("crush")));
		BY_CATEGORY.put("Slash Sword", List.of(accurate("slash"), aggressive("slash"), controlled("stab")));
		BY_CATEGORY.put("Spear", List.of(controlled("stab"), controlled("slash"), controlled("crush")));
		BY_CATEGORY.put("Spiked", List.of(accurate("crush"), aggressive("crush"), controlled("stab")));
		BY_CATEGORY.put("Stab Sword", List.of(accurate("stab"), aggressive("stab"), aggressive("slash")));
		BY_CATEGORY.put("Staff", List.of(accurate("crush"), aggressive("crush")));
		BY_CATEGORY.put("Unarmed", List.of(accurate("crush"), aggressive("crush")));
		BY_CATEGORY.put("Whip", List.of(accurate("slash"), controlled("slash")));
	}

	/** Every attack type x stance a full search would try - the fallback. */
	private static final List<MeleeStyle> ALL = List.of(
		accurate("stab"), aggressive("stab"), controlled("stab"),
		accurate("slash"), aggressive("slash"), controlled("slash"),
		accurate("crush"), aggressive("crush"), controlled("crush"));

	private WeaponStyles()
	{
	}

	/** The melee styles this weapon actually offers (unknown category: all). */
	public static List<MeleeStyle> melee(GearItem weapon)
	{
		if (weapon == null)
		{
			return BY_CATEGORY.get("Unarmed");
		}
		List<MeleeStyle> styles = BY_CATEGORY.get(weapon.getCategory());
		return styles != null ? styles : ALL;
	}
}
