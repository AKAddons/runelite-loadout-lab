package com.loadoutlab.data;

import java.util.Collections;
import java.util.List;

/**
 * A monster's offensive sheet - everything needed to compute the damage it
 * does to the PLAYER: attack levels, aggressive bonuses, attack speed, and
 * the styles it attacks with (wiki infobox strings such as "Crush",
 * "Ranged", "Magic", "Typeless", "Dragonfire").
 */
public final class MonsterOffence
{
	public static final MonsterOffence NONE = new MonsterOffence(
		1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 4, Collections.emptyList());

	private final int attackLevel;
	private final int strengthLevel;
	private final int rangedLevel;
	private final int magicLevel;
	private final int attackBonus;
	private final int strengthBonus;
	private final int rangedBonus;
	private final int rangedStrengthBonus;
	private final int magicBonus;
	private final int magicStrengthBonus;
	private final int speedTicks;
	private final List<String> styles;

	public MonsterOffence(
		int attackLevel,
		int strengthLevel,
		int rangedLevel,
		int magicLevel,
		int attackBonus,
		int strengthBonus,
		int rangedBonus,
		int rangedStrengthBonus,
		int magicBonus,
		int magicStrengthBonus,
		int speedTicks,
		List<String> styles)
	{
		this.attackLevel = attackLevel;
		this.strengthLevel = strengthLevel;
		this.rangedLevel = rangedLevel;
		this.magicLevel = magicLevel;
		this.attackBonus = attackBonus;
		this.strengthBonus = strengthBonus;
		this.rangedBonus = rangedBonus;
		this.rangedStrengthBonus = rangedStrengthBonus;
		this.magicBonus = magicBonus;
		this.magicStrengthBonus = magicStrengthBonus;
		this.speedTicks = Math.max(1, speedTicks);
		this.styles = styles == null ? Collections.emptyList()
			: Collections.unmodifiableList(styles);
	}

	public int getAttackLevel()
	{
		return attackLevel;
	}

	public int getStrengthLevel()
	{
		return strengthLevel;
	}

	public int getRangedLevel()
	{
		return rangedLevel;
	}

	public int getMagicLevel()
	{
		return magicLevel;
	}

	public int getAttackBonus()
	{
		return attackBonus;
	}

	public int getStrengthBonus()
	{
		return strengthBonus;
	}

	public int getRangedBonus()
	{
		return rangedBonus;
	}

	public int getRangedStrengthBonus()
	{
		return rangedStrengthBonus;
	}

	public int getMagicBonus()
	{
		return magicBonus;
	}

	public int getMagicStrengthBonus()
	{
		return magicStrengthBonus;
	}

	public int getSpeedTicks()
	{
		return speedTicks;
	}

	public List<String> getStyles()
	{
		return styles;
	}
}
