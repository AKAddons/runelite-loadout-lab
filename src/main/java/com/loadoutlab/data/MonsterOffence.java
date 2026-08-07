package com.loadoutlab.data;

import java.util.Collections;
import java.util.List;

/**
 * A monster's offensive sheet - everything needed to compute the damage it
 * does to the PLAYER: attack levels, aggressive bonuses, attack speed, and
 * the styles it attacks with (wiki infobox strings such as "Crush",
 * "Ranged", "Magic", "Typeless", "Dragonfire").
 */
import lombok.Getter;

public final class MonsterOffence
{
	public static final MonsterOffence NONE = new MonsterOffence(
		1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 4, Collections.emptyList());

	@Getter
	private final int attackLevel;
	@Getter
	private final int strengthLevel;
	@Getter
	private final int rangedLevel;
	@Getter
	private final int magicLevel;
	@Getter
	private final int attackBonus;
	@Getter
	private final int strengthBonus;
	@Getter
	private final int rangedBonus;
	@Getter
	private final int rangedStrengthBonus;
	@Getter
	private final int magicBonus;
	@Getter
	private final int magicStrengthBonus;
	@Getter
	private final int speedTicks;
	@Getter
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












}
