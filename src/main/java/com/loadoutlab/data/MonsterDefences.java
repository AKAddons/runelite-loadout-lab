// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

public final class MonsterDefences
{
	public static final MonsterDefences ZERO = new MonsterDefences(0, 0, 0, 0, 0, 0, 0, 0, 0);

	private final int stab;
	private final int slash;
	private final int crush;
	private final int magic;
	private final int ranged;
	private final int flatArmour;
	private final int light;
	private final int standard;
	private final int heavy;

	public MonsterDefences(int stab, int slash, int crush, int magic, int ranged, int light, int standard, int heavy)
	{
		this(stab, slash, crush, magic, ranged, 0, light, standard, heavy);
	}

	public MonsterDefences(int stab, int slash, int crush, int magic, int ranged, int flatArmour, int light, int standard, int heavy)
	{
		this.stab = stab;
		this.slash = slash;
		this.crush = crush;
		this.magic = magic;
		this.ranged = ranged;
		this.flatArmour = flatArmour;
		this.light = light;
		this.standard = standard;
		this.heavy = heavy;
	}

	public int get(String type)
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
			case "light":
				return light;
			case "standard":
				return standard;
			case "heavy":
				return heavy;
			default:
				return 0;
		}
	}

	public int getFlatArmour()
	{
		return flatArmour;
	}
}
