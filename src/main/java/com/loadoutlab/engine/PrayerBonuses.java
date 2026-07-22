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
		switch (style)
		{
			case RANGED: return new String[]{"Rigour", "Deadeye", "Eagle Eye"};
			case MAGIC: return new String[]{"Augury", "Mystic Vigour", "Mystic Might"};
			default: return new String[]{"Piety", "Chivalry",
				"Ultimate Strength + Incredible Reflexes",
				"Superhuman Strength + Improved Reflexes",
				"Burst of Strength + Clarity of Thought"};
		}
	}

	/** A specific pick's bonuses for ONE style: the fallback's factors with
	 * this style's replaced by the named tier (the other styles' values are
	 * irrelevant to a single-style request). Unknown names fall back. */
	public static PrayerBonuses forPick(CombatStyle style, String pick, PrayerBonuses fallback)
	{
		double mAcc = fallback.meleeAccuracy, mStr = fallback.meleeStrength;
		double rAcc = fallback.rangedAccuracy, rStr = fallback.rangedStrength;
		double gAcc = fallback.magicAccuracy, gDmg = fallback.magicDamagePercent;
		String name = pick;
		switch (style)
		{
			case RANGED:
				if ("Rigour".equals(pick)) { rAcc = 1.20; rStr = 1.23; }
				else if ("Deadeye".equals(pick)) { rAcc = 1.18; rStr = 1.18; }
				else if ("Eagle Eye".equals(pick)) { rAcc = 1.15; rStr = 1.15; }
				else { return fallback; }
				break;
			case MAGIC:
				if ("Augury".equals(pick)) { gAcc = 1.25; gDmg = 4.0; }
				else if ("Mystic Vigour".equals(pick)) { gAcc = 1.18; gDmg = 3.0; }
				else if ("Mystic Might".equals(pick)) { gAcc = 1.15; gDmg = 2.0; }
				else { return fallback; }
				break;
			default:
				if ("Piety".equals(pick)) { mAcc = 1.20; mStr = 1.23; }
				else if ("Chivalry".equals(pick)) { mAcc = 1.15; mStr = 1.18; }
				else if ("Ultimate Strength + Incredible Reflexes".equals(pick)) { mAcc = 1.15; mStr = 1.15; }
				else if ("Superhuman Strength + Improved Reflexes".equals(pick)) { mAcc = 1.10; mStr = 1.10; }
				else if ("Burst of Strength + Clarity of Thought".equals(pick)) { mAcc = 1.05; mStr = 1.05; }
				else { return fallback; }
				break;
		}
		PrayerBonuses result = new PrayerBonuses(mAcc, mStr, rAcc, rStr, gAcc, gDmg);
		result.meleeName = style == CombatStyle.MELEE ? name : fallback.meleeName;
		result.rangedName = style == CombatStyle.RANGED ? name : fallback.rangedName;
		result.magicName = style == CombatStyle.MAGIC ? name : fallback.magicName;
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
}
