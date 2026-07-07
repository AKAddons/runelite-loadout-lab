// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import java.util.Set;
import net.runelite.api.Prayer;

public final class PrayerBonuses
{
	public static final PrayerBonuses NONE = new PrayerBonuses(1.0, 1.0, 1.0, 1.0, 1.0, 0.0);

	private final double meleeAccuracy;
	private final double meleeStrength;
	private final double rangedAccuracy;
	private final double rangedStrength;
	private final double magicAccuracy;
	/** Mystic Vigour's separate x1.18 magic accuracy factor - the official
	 * calc applies it with its own floor AFTER Augury's 1.25. */
	private final double magicAccuracySecondary;
	private final double magicDamagePercent;

	public PrayerBonuses(double meleeAccuracy, double meleeStrength, double rangedAccuracy, double rangedStrength, double magicAccuracy)
	{
		this(meleeAccuracy, meleeStrength, rangedAccuracy, rangedStrength, magicAccuracy, 0.0);
	}

	public PrayerBonuses(double meleeAccuracy, double meleeStrength, double rangedAccuracy, double rangedStrength, double magicAccuracy, double magicDamagePercent)
	{
		this.meleeAccuracy = meleeAccuracy;
		this.meleeStrength = meleeStrength;
		this.rangedAccuracy = rangedAccuracy;
		this.rangedStrength = rangedStrength;
		this.magicAccuracy = magicAccuracy;
		this.magicAccuracySecondary = levelFor(magicDamagePercent);
		this.magicDamagePercent = magicDamagePercent;
	}

	public static PrayerBonuses bestAvailable(PlayerLevels levels)
	{
		return bestAvailable(levels, PrayerUnlocks.ALL);
	}

	public static PrayerBonuses bestAvailable(PlayerLevels levels, PrayerUnlocks unlocks)
	{
		double meleeAcc = 1.0;
		double meleeStr = 1.0;
		if (levels.getPrayer() >= 70 && unlocks.piety())
		{
			meleeAcc = 1.20;
			meleeStr = 1.23;
		}
		else if (levels.getPrayer() >= 60 && unlocks.chivalry())
		{
			meleeAcc = 1.15;
			meleeStr = 1.18;
		}
		else
		{
			if (levels.getPrayer() >= 31)
			{
				meleeAcc = 1.15;
			}
			else if (levels.getPrayer() >= 13)
			{
				meleeAcc = 1.10;
			}
			else if (levels.getPrayer() >= 7)
			{
				meleeAcc = 1.05;
			}
			if (levels.getPrayer() >= 31)
			{
				meleeStr = 1.15;
			}
			else if (levels.getPrayer() >= 10)
			{
				meleeStr = 1.10;
			}
			else if (levels.getPrayer() >= 4)
			{
				meleeStr = 1.05;
			}
		}

		boolean rigour = levels.getPrayer() >= 74 && unlocks.rigour();
		boolean deadeye = levels.getPrayer() >= 62 && unlocks.deadeye();
		double rangedAccuracy = rigour ? 1.20 : deadeye ? 1.18 : levels.getPrayer() >= 44 ? 1.15 : levels.getPrayer() >= 26 ? 1.10 : levels.getPrayer() >= 8 ? 1.05 : 1.0;
		double rangedStrength = rigour ? 1.23 : deadeye ? 1.18 : levels.getPrayer() >= 44 ? 1.15 : levels.getPrayer() >= 26 ? 1.10 : levels.getPrayer() >= 8 ? 1.05 : 1.0;
		boolean augury = levels.getPrayer() >= 77 && unlocks.augury();
		boolean vigour = levels.getPrayer() >= 77 && unlocks.mysticVigour();
		double magic = augury ? 1.25 : vigour ? 1.18 : levels.getPrayer() >= 45 ? 1.15 : levels.getPrayer() >= 27 ? 1.10 : levels.getPrayer() >= 9 ? 1.05 : 1.0;
		// Augury (4%) and Mystic Vigour (3%) stack - verified vs the official calc.
		double magicDamage = (augury ? 4.0 : 0.0) + (vigour ? 3.0 : 0.0)
			+ (!augury && !vigour ? (levels.getPrayer() >= 45 ? 2.0 : levels.getPrayer() >= 27 ? 1.0 : 0.0) : 0.0);
		return new PrayerBonuses(meleeAcc, meleeStr, rangedAccuracy, rangedStrength, magic, magicDamage);
	}

	public static PrayerBonuses fromActive(Set<Prayer> active)
	{
		if (active == null || active.isEmpty())
		{
			return NONE;
		}
		double meleeAccuracy = 1.0;
		double meleeStrength = 1.0;
		double rangedAccuracy = 1.0;
		double rangedStrength = 1.0;
		double magicAccuracy = 1.0;
		double magicDamage = 0.0;
		for (Prayer prayer : active)
		{
			switch (prayer)
			{
				case BURST_OF_STRENGTH:
					meleeStrength = Math.max(meleeStrength, 1.05);
					break;
				case CLARITY_OF_THOUGHT:
					meleeAccuracy = Math.max(meleeAccuracy, 1.05);
					break;
				case SUPERHUMAN_STRENGTH:
					meleeStrength = Math.max(meleeStrength, 1.10);
					break;
				case IMPROVED_REFLEXES:
					meleeAccuracy = Math.max(meleeAccuracy, 1.10);
					break;
				case ULTIMATE_STRENGTH:
					meleeStrength = Math.max(meleeStrength, 1.15);
					break;
				case INCREDIBLE_REFLEXES:
					meleeAccuracy = Math.max(meleeAccuracy, 1.15);
					break;
				case CHIVALRY:
					meleeAccuracy = Math.max(meleeAccuracy, 1.15);
					meleeStrength = Math.max(meleeStrength, 1.18);
					break;
				case PIETY:
					meleeAccuracy = Math.max(meleeAccuracy, 1.20);
					meleeStrength = Math.max(meleeStrength, 1.23);
					break;
				case SHARP_EYE:
					rangedAccuracy = Math.max(rangedAccuracy, 1.05);
					rangedStrength = Math.max(rangedStrength, 1.05);
					break;
				case HAWK_EYE:
					rangedAccuracy = Math.max(rangedAccuracy, 1.10);
					rangedStrength = Math.max(rangedStrength, 1.10);
					break;
				case EAGLE_EYE:
					rangedAccuracy = Math.max(rangedAccuracy, 1.15);
					rangedStrength = Math.max(rangedStrength, 1.15);
					break;
				case DEADEYE:
					rangedAccuracy = Math.max(rangedAccuracy, 1.18);
					rangedStrength = Math.max(rangedStrength, 1.18);
					break;
				case RIGOUR:
					rangedAccuracy = Math.max(rangedAccuracy, 1.20);
					rangedStrength = Math.max(rangedStrength, 1.23);
					break;
				case MYSTIC_WILL:
					magicAccuracy = Math.max(magicAccuracy, 1.05);
					break;
				case MYSTIC_LORE:
					magicAccuracy = Math.max(magicAccuracy, 1.10);
					magicDamage = Math.max(magicDamage, 1.0);
					break;
				case MYSTIC_MIGHT:
					magicAccuracy = Math.max(magicAccuracy, 1.15);
					magicDamage = Math.max(magicDamage, 2.0);
					break;
				case MYSTIC_VIGOUR:
					magicAccuracy = Math.max(magicAccuracy, 1.18);
					magicDamage = Math.max(magicDamage, 3.0);
					break;
				case AUGURY:
					magicAccuracy = Math.max(magicAccuracy, 1.25);
					magicDamage = Math.max(magicDamage, 4.0);
					break;
				default:
					break;
			}
		}
		return new PrayerBonuses(meleeAccuracy, meleeStrength, rangedAccuracy, rangedStrength, magicAccuracy, magicDamage);
	}

	public double getMeleeAccuracy()
	{
		return meleeAccuracy;
	}

	public double getMeleeStrength()
	{
		return meleeStrength;
	}

	public double getRangedAccuracy()
	{
		return rangedAccuracy;
	}

	public double getRangedStrength()
	{
		return rangedStrength;
	}

	/** 1.18 when Mystic Vigour is up (77+ prayer), else 1.0. */
	private static double levelFor(double magicDamagePercent)
	{
		return magicDamagePercent >= 7.0 ? 1.18 : 1.0;
	}

	public double getMagicAccuracySecondary()
	{
		return magicAccuracySecondary;
	}

	public double getMagicAccuracy()
	{
		return magicAccuracy;
	}

	public double getMagicDamagePercent()
	{
		return magicDamagePercent;
	}
}
