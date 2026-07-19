// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import java.util.List;
import java.util.ArrayList;

public final class PrayerBonuses
{
	public static final PrayerBonuses NONE = new PrayerBonuses(1.0, 1.0, 1.0, 1.0, 1.0, 0.0);

	private final double meleeAccuracy;
	private final double meleeStrength;
	private final double rangedAccuracy;
	private final double rangedStrength;
	private final double magicAccuracy;
	private String meleeName = "";
	private String rangedName = "";
	private String magicName = "";
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
		this.magicDamagePercent = magicDamagePercent;
	}

	public static PrayerBonuses bestAvailable(PlayerLevels levels)
	{
		return bestAvailable(levels, PrayerUnlocks.ALL);
	}

	public static PrayerBonuses bestAvailable(PlayerLevels levels, PrayerUnlocks unlocks)
	{
		String meleeName;
		double meleeAcc = 1.0;
		double meleeStr = 1.0;
		if (levels.getPrayer() >= 70 && unlocks.piety())
		{
			meleeAcc = 1.20;
			meleeStr = 1.23;
			meleeName = "Piety";
		}
		else if (levels.getPrayer() >= 60 && unlocks.chivalry())
		{
			meleeAcc = 1.15;
			meleeStr = 1.18;
			meleeName = "Chivalry";
		}
		else
		{
			// Every applied tier is NAMED - the assumes chip must never fold
			// in a multiplier it does not admit to (audit A2.12).
			List<String> parts = new ArrayList<>();
			if (levels.getPrayer() >= 34)
			{
				meleeAcc = 1.15;
				parts.add("Incredible Reflexes");
			}
			else if (levels.getPrayer() >= 16)
			{
				meleeAcc = 1.10;
				parts.add("Improved Reflexes");
			}
			else if (levels.getPrayer() >= 7)
			{
				meleeAcc = 1.05;
				parts.add("Clarity of Thought");
			}
			if (levels.getPrayer() >= 31)
			{
				meleeStr = 1.15;
				parts.add("Ultimate Strength");
			}
			else if (levels.getPrayer() >= 13)
			{
				meleeStr = 1.10;
				parts.add("Superhuman Strength");
			}
			else if (levels.getPrayer() >= 4)
			{
				meleeStr = 1.05;
				parts.add("Burst of Strength");
			}
			meleeName = String.join(" + ", parts);
		}

		boolean rigour = levels.getPrayer() >= 74 && unlocks.rigour();
		boolean deadeye = levels.getPrayer() >= 62 && unlocks.deadeye();
		double rangedAccuracy = rigour ? 1.20 : deadeye ? 1.18 : levels.getPrayer() >= 44 ? 1.15 : levels.getPrayer() >= 26 ? 1.10 : levels.getPrayer() >= 8 ? 1.05 : 1.0;
		double rangedStrength = rigour ? 1.23 : deadeye ? 1.18 : levels.getPrayer() >= 44 ? 1.15 : levels.getPrayer() >= 26 ? 1.10 : levels.getPrayer() >= 8 ? 1.05 : 1.0;
		boolean augury = levels.getPrayer() >= 77 && unlocks.augury();
		boolean vigour = levels.getPrayer() >= 77 && unlocks.mysticVigour();
		double magic = augury ? 1.25 : vigour ? 1.18 : levels.getPrayer() >= 45 ? 1.15 : levels.getPrayer() >= 27 ? 1.10 : levels.getPrayer() >= 9 ? 1.05 : 1.0;
		// All magic prayers share one prayer group in game - Augury and
		// Mystic Vigour cannot be active together (the wiki calc engine
		// stacks whatever it is fed, which is how the old 7% slipped past
		// the harness). Augury strictly dominates when both are unlocked.
		double magicDamage = augury ? 4.0 : vigour ? 3.0
			: levels.getPrayer() >= 45 ? 2.0 : levels.getPrayer() >= 27 ? 1.0 : 0.0;
		PrayerBonuses result = new PrayerBonuses(meleeAcc, meleeStr, rangedAccuracy, rangedStrength, magic, magicDamage);
		result.meleeName = meleeName;
		result.rangedName = rigour ? "Rigour" : deadeye ? "Deadeye"
			: levels.getPrayer() >= 44 ? "Eagle Eye"
			: levels.getPrayer() >= 26 ? "Hawk Eye"
			: levels.getPrayer() >= 8 ? "Sharp Eye" : "";
		result.magicName = augury ? "Augury" : vigour ? "Mystic Vigour"
			: levels.getPrayer() >= 45 ? "Mystic Might"
			: levels.getPrayer() >= 27 ? "Mystic Lore"
			: levels.getPrayer() >= 9 ? "Mystic Will" : "";
		return result;
	}

	/** The prayer tier the numbers assume for a style ("Piety", "Rigour"). */
	public String nameFor(CombatStyle style)
	{
		switch (style)
		{
			case RANGED: return rangedName;
			case MAGIC: return magicName;
			default: return meleeName;
		}
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

	public double getMagicAccuracy()
	{
		return magicAccuracy;
	}

	public double getMagicDamagePercent()
	{
		return magicDamagePercent;
	}
}
