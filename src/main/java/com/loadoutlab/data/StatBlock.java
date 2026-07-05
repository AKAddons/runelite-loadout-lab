// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

public final class StatBlock
{
	public static final StatBlock ZERO = new StatBlock(0, 0, 0, 0, 0, 0, 0, 0, 0);

	private final int stab;
	private final int slash;
	private final int crush;
	private final int magic;
	private final int ranged;
	private final int strength;
	private final int rangedStrength;
	private final int magicDamage;
	private final int prayer;

	public StatBlock(
		int stab,
		int slash,
		int crush,
		int magic,
		int ranged,
		int strength,
		int rangedStrength,
		int magicDamage,
		int prayer)
	{
		this.stab = stab;
		this.slash = slash;
		this.crush = crush;
		this.magic = magic;
		this.ranged = ranged;
		this.strength = strength;
		this.rangedStrength = rangedStrength;
		this.magicDamage = magicDamage;
		this.prayer = prayer;
	}

	public StatBlock plus(StatBlock other)
	{
		return new StatBlock(
			stab + other.stab,
			slash + other.slash,
			crush + other.crush,
			magic + other.magic,
			ranged + other.ranged,
			strength + other.strength,
			rangedStrength + other.rangedStrength,
			magicDamage + other.magicDamage,
			prayer + other.prayer);
	}

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

	public int getStab()
	{
		return stab;
	}

	public int getSlash()
	{
		return slash;
	}

	public int getCrush()
	{
		return crush;
	}

	public int getMagic()
	{
		return magic;
	}

	public int getRanged()
	{
		return ranged;
	}

	public int getStrength()
	{
		return strength;
	}

	public int getRangedStrength()
	{
		return rangedStrength;
	}

	public int getMagicDamage()
	{
		return magicDamage;
	}

	public int getPrayer()
	{
		return prayer;
	}
}
