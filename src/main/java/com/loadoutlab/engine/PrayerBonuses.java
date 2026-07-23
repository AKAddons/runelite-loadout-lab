// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import lombok.Getter;
import java.util.List;
import java.util.ArrayList;

public final class PrayerBonuses
{
	public static final PrayerBonuses NONE = new PrayerBonuses(1.0, 1.0, 1.0, 1.0, 1.0, 0.0);

	@Getter
	private final double meleeAccuracy;
	@Getter
	private final double meleeStrength;
	@Getter
	private final double rangedAccuracy;
	@Getter
	private final double rangedStrength;
	@Getter
	private final double magicAccuracy;
	private String meleeName = "";
	private String rangedName = "";
	private String magicName = "";
	@Getter
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

	/** The selectable named tiers per style, best first - the assume-chip
	 * picker (field direction 2026-07-21: "I don't use Piety against every
	 * mob"). Factors mirror bestAvailable's cascade exactly. */
	public static String[] optionsFor(CombatStyle style)
	{
		Object[][] picks = picksFor(style);
		String[] names = new String[picks.length];
		for (int i = 0; i < picks.length; i++)
		{
			names[i] = (String) picks[i][0];
		}
		return names;
	}

	/** A specific pick's bonuses for ONE style: the fallback's factors with
	 * this style's replaced by the named tier (the other styles' values are
	 * irrelevant to a single-style request). Unknown names fall back. */
	/** name -> {accuracy, strength-or-damage} per style, best first -
	 * mirrors bestAvailable's cascade exactly. */
	private static final Object[][] MELEE_PICKS = {
		{"Piety", 1.20, 1.23}, {"Chivalry", 1.15, 1.18},
		{"Ultimate Strength + Incredible Reflexes", 1.15, 1.15},
		{"Superhuman Strength + Improved Reflexes", 1.10, 1.10},
		{"Burst of Strength + Clarity of Thought", 1.05, 1.05}};
	private static final Object[][] RANGED_PICKS = {
		{"Rigour", 1.20, 1.23}, {"Deadeye", 1.18, 1.18}, {"Eagle Eye", 1.15, 1.15},
		{"Hawk Eye", 1.10, 1.10}, {"Sharp Eye", 1.05, 1.05}};
	private static final Object[][] MAGIC_PICKS = {
		{"Augury", 1.25, 4.0}, {"Mystic Vigour", 1.18, 3.0}, {"Mystic Might", 1.15, 2.0},
		{"Mystic Lore", 1.10, 1.0}, {"Mystic Will", 1.05, 0.0}};

	private static Object[][] picksFor(CombatStyle style)
	{
		return style == CombatStyle.RANGED ? RANGED_PICKS
			: style == CombatStyle.MAGIC ? MAGIC_PICKS : MELEE_PICKS;
	}

	/** A specific pick's bonuses for ONE style: the fallback's factors with
	 * this style's replaced by the named tier (the other styles' values are
	 * irrelevant to a single-style request). Unknown names fall back. */
	public static PrayerBonuses forPick(CombatStyle style, String pick, PrayerBonuses fallback)
	{
		for (Object[] row : picksFor(style))
		{
			if (!row[0].equals(pick))
			{
				continue;
			}
			double a = (Double) row[1];
			double b = (Double) row[2];
			PrayerBonuses result;
			if (style == CombatStyle.RANGED)
			{
				result = new PrayerBonuses(fallback.meleeAccuracy, fallback.meleeStrength,
					a, b, fallback.magicAccuracy, fallback.magicDamagePercent);
			}
			else if (style == CombatStyle.MAGIC)
			{
				result = new PrayerBonuses(fallback.meleeAccuracy, fallback.meleeStrength,
					fallback.rangedAccuracy, fallback.rangedStrength, a, b);
			}
			else
			{
				result = new PrayerBonuses(a, b, fallback.rangedAccuracy,
					fallback.rangedStrength, fallback.magicAccuracy, fallback.magicDamagePercent);
			}
			result.meleeName = style == CombatStyle.MELEE ? pick : fallback.meleeName;
			result.rangedName = style == CombatStyle.RANGED ? pick : fallback.rangedName;
			result.magicName = style == CombatStyle.MAGIC ? pick : fallback.magicName;
			return result;
		}
		return fallback;
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
}
