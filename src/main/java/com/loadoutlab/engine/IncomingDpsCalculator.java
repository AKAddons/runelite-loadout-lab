package com.loadoutlab.engine;

import com.loadoutlab.data.MonsterOffence;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.StatBlock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The other direction: how hard the monster hits YOU in a given set.
 * Standard NPC combat math (effective level +9, roll = eff * (bonus+64),
 * max hit = (effStr * (bonus+64) + 320) / 640) against the loadout's
 * defensive bonuses and the player's real Defence/Magic levels.
 *
 * v1 model and its stated assumptions:
 * - The player runs the protection prayer against the nastiest modeled
 *   style, which blocks that style completely (true for standard NPCs;
 *   partial-block bosses need per-boss overrides later).
 * - Multi-style monsters rotate uniformly across their listed styles, so
 *   each style owns an equal share of the attack cadence and the blocked
 *   style's share is dead time. Real rotations (Zulrah phases, Vorkath
 *   specials) are a curated-override layer we build up over time.
 * - No defensive stance, boost, or defence-raising prayer on the player -
 *   protection is assumed to occupy the prayer slot.
 * - Styles the sheet cannot express (Typeless, Dragonfire, Curse...) are
 *   surfaced as unmodeled rather than silently dropped.
 */
public final class IncomingDpsCalculator
{
	/** One attack style's threat: its dps if it attacked every cycle. */
	public static final class StyleThreat
	{
		public final String style;
		public final double dps;
		public final int maxHit;
		public final boolean modeled;
		public final boolean blocked;

		StyleThreat(String style, double dps, int maxHit, boolean modeled, boolean blocked)
		{
			this.style = style;
			this.dps = dps;
			this.maxHit = maxHit;
			this.modeled = modeled;
			this.blocked = blocked;
		}
	}

	public static final class Result
	{
		/** Expected incoming dps with the protection prayer up. */
		public final double totalDps;
		/** Expected incoming dps with NO protection prayer running. */
		public final double unprayedDps;
		/** The prayer to run, e.g. "Protect from Missiles", or null. */
		public final String protectPrayer;
		public final List<StyleThreat> threats;
		/** False when any listed style is beyond the stat-sheet model. */
		public final boolean fullyModeled;

		Result(double totalDps, double unprayedDps, String protectPrayer,
			List<StyleThreat> threats, boolean fullyModeled)
		{
			this.totalDps = totalDps;
			this.unprayedDps = unprayedDps;
			this.protectPrayer = protectPrayer;
			this.threats = Collections.unmodifiableList(threats);
			this.fullyModeled = fullyModeled;
		}
	}

	private IncomingDpsCalculator()
	{
	}

	public static Result calculate(MonsterStats monster, Loadout loadout,
		int defenceLevel, int magicLevel)
	{
		MonsterOffence off = monster.getOffence();
		List<String> styles = off.getStyles();
		if (styles.isEmpty())
		{
			return new Result(0, 0, null, Collections.emptyList(), false);
		}

		StatBlock def = loadout.getDefensive();
		List<StyleThreat> threats = new ArrayList<>();
		boolean fullyModeled = true;
		int bestBlockable = -1;
		double bestBlockableDps = -1;
		for (String style : styles)
		{
			StyleThreat threat = threatFor(style, off, def, defenceLevel, magicLevel);
			if (!threat.modeled)
			{
				fullyModeled = false;
			}
			else if (threat.dps > bestBlockableDps)
			{
				bestBlockableDps = threat.dps;
				bestBlockable = threats.size();
			}
			threats.add(threat);
		}

		String prayer = null;
		if (bestBlockable >= 0)
		{
			StyleThreat blocked = threats.get(bestBlockable);
			threats.set(bestBlockable, new StyleThreat(
				blocked.style, blocked.dps, blocked.maxHit, true, true));
			prayer = protectPrayerFor(blocked.style);
		}

		// Uniform rotation: each listed style owns 1/n of the cadence.
		double total = 0;
		double unprayed = 0;
		for (StyleThreat threat : threats)
		{
			if (!threat.modeled)
			{
				continue;
			}
			unprayed += threat.dps / styles.size();
			if (!threat.blocked)
			{
				total += threat.dps / styles.size();
			}
		}
		return new Result(total, unprayed, prayer, threats, fullyModeled);
	}

	private static StyleThreat threatFor(String rawStyle, MonsterOffence off,
		StatBlock def, int defenceLevel, int magicLevel)
	{
		String style = rawStyle.toLowerCase(Locale.ROOT);
		int attackRoll;
		int maxHit;
		long defenceRoll;
		if (style.equals("stab") || style.equals("slash") || style.equals("crush") || style.equals("melee"))
		{
			attackRoll = npcRoll(off.getAttackLevel(), off.getAttackBonus());
			maxHit = npcMaxHit(off.getStrengthLevel(), off.getStrengthBonus());
			defenceRoll = (long) (defenceLevel + 9) * (meleeDefBonus(style, def) + 64);
		}
		else if (style.equals("ranged") || style.equals("range"))
		{
			attackRoll = npcRoll(off.getRangedLevel(), off.getRangedBonus());
			maxHit = npcMaxHit(off.getRangedLevel(), off.getRangedStrengthBonus());
			defenceRoll = (long) (defenceLevel + 9) * (def.getRanged() + 64);
		}
		else if (style.equals("magic") || style.equals("magical ranged") || style.equals("magical melee"))
		{
			attackRoll = npcRoll(off.getMagicLevel(), off.getMagicBonus());
			maxHit = npcMaxHit(off.getMagicLevel(), off.getMagicStrengthBonus());
			// Player magic defence: 70% Magic + 30% Defence for the level term.
			int effective = (int) (magicLevel * 0.7) + (int) ((defenceLevel + 9) * 0.3);
			defenceRoll = (long) effective * (def.getMagic() + 64);
		}
		else
		{
			return new StyleThreat(rawStyle, 0, 0, false, false);
		}

		double accuracy = accuracy(attackRoll, defenceRoll);
		double dps = accuracy * (maxHit / 2.0) / (off.getSpeedTicks() * 0.6);
		return new StyleThreat(rawStyle, dps, maxHit, true, false);
	}

	private static int meleeDefBonus(String style, StatBlock def)
	{
		switch (style)
		{
			case "stab": return def.getStab();
			case "slash": return def.getSlash();
			case "crush": return def.getCrush();
			default:
				// Generic "Melee": assume the boss hits your weakest side.
				return Math.min(def.getStab(), Math.min(def.getSlash(), def.getCrush()));
		}
	}

	private static String protectPrayerFor(String rawStyle)
	{
		String style = rawStyle.toLowerCase(Locale.ROOT);
		if (style.equals("ranged") || style.equals("range"))
		{
			return "Protect from Missiles";
		}
		if (style.startsWith("magic"))
		{
			return "Protect from Magic";
		}
		return "Protect from Melee";
	}

	private static int npcRoll(int level, int bonus)
	{
		return (level + 9) * (bonus + 64);
	}

	private static int npcMaxHit(int level, int bonus)
	{
		return (int) (((long) (level + 9) * (bonus + 64) + 320) / 640);
	}

	private static double accuracy(long attackRoll, long defenceRoll)
	{
		if (attackRoll > defenceRoll)
		{
			return 1.0 - (defenceRoll + 2.0) / (2.0 * (attackRoll + 1.0));
		}
		return attackRoll / (2.0 * (defenceRoll + 1.0));
	}
}
