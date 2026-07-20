// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public final class StatBlock
{
	public static final StatBlock ZERO = new StatBlock(0, 0, 0, 0, 0, 0, 0, 0, 0);

	@Getter
	private final int stab;
	@Getter
	private final int slash;
	@Getter
	private final int crush;
	@Getter
	private final int magic;
	@Getter
	private final int ranged;
	@Getter
	private final int strength;
	@Getter
	private final int rangedStrength;
	@Getter
	private final int magicDamage;
	@Getter
	private final int prayer;

	public int getAttackBonus(String type)
	{
		switch (type)
		{
			case "stab":
				return stab;
			case "slash":
				return slash;
			case "crush":
				return crush;
			case "magic":
				return magic;
			case "ranged":
				return ranged;
			default:
				return 0;
		}
	}









}
