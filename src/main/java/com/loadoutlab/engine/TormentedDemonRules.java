package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.MonsterStats;

/**
 * Tormented Demon mechanics, matching the official calculator's default
 * phase view (verified against weirdgloop's engine 2026-07-05):
 *
 * <ul>
 * <li>Player accuracy is effectively 100% (their shield phases leave them
 *     defenceless to properly-aimed attacks; only the active 'Shielded'
 *     phase resists, which the official calc's default also ignores).</li>
 * <li>They take 20% reduced damage unless hit by a demonbane weapon/spell
 *     or an abyssal weapon (whip, tentacle, dagger, bludgeon).</li>
 * </ul>
 *
 * Not modeled: the Unshielded-phase bonus for crush/heavy/spell attacks.
 */
public final class TormentedDemonRules
{
	private static final String[] ABYSSAL = {
		"abyssal whip", "abyssal tentacle", "abyssal dagger", "abyssal bludgeon",
	};

	private TormentedDemonRules()
	{
	}

	public static boolean applies(MonsterStats monster)
	{
		return monster != null && "Tormented Demon".equalsIgnoreCase(monster.getName());
	}

	/** The flat 20% reduction, bypassed by demonbane and abyssal weapons. */
	public static double damageFactor(MonsterStats monster, CombatStyle style, GearItem weapon, String spellName)
	{
		if (!applies(monster))
		{
			return 1.0;
		}
		return bypassesReduction(style, weapon, spellName) ? 1.0 : 0.8;
	}

	private static boolean bypassesReduction(CombatStyle style, GearItem weapon, String spellName)
	{
		if (style == CombatStyle.MAGIC)
		{
			return spellName != null && spellName.contains("Demonbane");
		}
		// The demonbane weapon vocabulary lives on GearItem (one list with
		// MonsterMechanics and the optimizer's demon bump); a melee name
		// can never be the weapon of a RANGED attack, so one flag serves
		// both remaining styles.
		if (weapon == null)
		{
			return false;
		}
		if (weapon.isDemonbane())
		{
			return true;
		}
		for (String abyssal : ABYSSAL)
		{
			if (weapon.getNameLower().startsWith(abyssal))
			{
				return true;
			}
		}
		return false;
	}
}
