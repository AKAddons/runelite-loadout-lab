package com.loadoutlab.engine;

/**
 * Boat-cannon math (wiki: Boat combat / Cannons, verified 2026-08-31). Pure
 * formulas; the data (per-tier strengths, accuracies, tick rate) lives in
 * NavalCombat and callers pass numbers in.
 *
 * <p>Player-fired: max hit rides the player's EFFECTIVE ranged level - the
 * caller applies prayer/void through the engine's existing effective-level
 * machinery, exactly as land ranged does - and the worn ranged strength
 * bonus counts EXCLUDING the weapon, shield and ammo slots (wiki: those
 * three are ignored at the cannon).
 *
 * <p>Crew-fired: the last documented formula scales a base max hit by the
 * crew's Privateering level, (priv + 13) / 20. The wiki flags this formula
 * out-of-date with the real one unknown, so it is isolated HERE and marked
 * by NavalCombat.crewFormulaStale() - when the corrected formula lands,
 * this one method changes and every caller stays put.
 */
public final class ShipCannon
{
	/** All cannons fire on the same cycle regardless of tier. */
	public static final double ATTACK_SECONDS = 4.2;

	private ShipCannon()
	{
	}

	/**
	 * Max hit for a player-fired cannon:
	 * floor((level * (cannonStr + ballStr + wornStr + 64) + 320) / 640),
	 * plus one when a perildance bitter keg is aboard.
	 */
	public static int playerMaxHit(int effectiveRanged, int cannonStrength,
		int ballStrength, int wornRangedStrength, boolean perildance)
	{
		long strength = cannonStrength + ballStrength + wornRangedStrength + 64L;
		int hit = (int) ((effectiveRanged * strength + 320L) / 640L);
		return hit + (perildance ? 1 : 0);
	}

	/** The stale-but-documented crew scaling: floor(base * (priv + 13) / 20). */
	public static int crewMaxHit(int baseMaxHit, int privateering)
	{
		return (int) (baseMaxHit * (privateering + 13L) / 20L);
	}

	/** The cannon's equipment accuracy: its heavy-ranged accuracy plus the
	 * ball's, plus worn ranged accuracy excluding weapon/shield/ammo. Rolls
	 * against the monster's HEAVY defence through the standard machinery. */
	public static int equipmentAccuracy(int cannonHeavyAccuracy, int ballAccuracy,
		int wornRangedAccuracy)
	{
		return cannonHeavyAccuracy + ballAccuracy + wornRangedAccuracy;
	}

	/**
	 * Hit chance vs the monster's HEAVY ranged defence, through the same
	 * roll math the land engine uses. Effective level is visible ranged
	 * + 8 (no stance bonus at a cannon); prayer accuracy multipliers are
	 * the caller's concern, matching how the wiki's calculator treats
	 * prayer as an input to the level term.
	 */
	public static double hitChance(int effectiveRanged, int equipmentAccuracy,
		int monsterDefence, int monsterHeavyBonus)
	{
		long attack = RollMath.attackRoll(effectiveRanged + 8, equipmentAccuracy);
		long defence = RollMath.defenceRoll(monsterDefence + 9, monsterHeavyBonus);
		return RollMath.normalAccuracy(attack, defence);
	}

	/** DPS of one cannon: average landed damage over the 7-tick cycle.
	 * hitChance in [0,1]; a landed hit averages (maxHit / 2) with the
	 * uniform damage roll the game uses. */
	public static double dps(int maxHit, double hitChance)
	{
		return hitChance * (maxHit / 2.0) / ATTACK_SECONDS;
	}
}
