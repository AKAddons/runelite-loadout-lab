package com.loadoutlab.engine;

/**
 * The three multi-combat tools the Multi card ranks (Andrew 2026-10-19,
 * design DES-01M5B2AGKDBE6G3J3WR06TBSW2): the ancient burst/barrage
 * spells and chinchompas hit every target in the 3x3 around the primary,
 * up to nine; the venator bow bounces up to twice, each bounce at
 * two-thirds of the max hit on its own accuracy roll (wiki). The engine
 * prices one target; the card multiplies by the stack.
 */
public enum MultiTool
{
	BARRAGE("barrage", CombatStyle.MAGIC, "area"),
	CHINS("chins", CombatStyle.RANGED, "area"),
	VENATOR("venator", CombatStyle.RANGED, "chain");

	/** Black, red, grey: the pin takes the best owned, the ceiling black. */
	public static final int[] CHIN_IDS = {11959, 10034, 10033};
	public static final int VENATOR_BOW = 27610;
	public static final int VENATOR_BOW_UNCHARGED = 27612;
	public static final int AREA_CAP = 9;

	public final String key;
	public final CombatStyle style;
	public final String kind;

	MultiTool(String key, CombatStyle style, String kind)
	{
		this.key = key;
		this.style = style;
		this.kind = kind;
	}

	/** The multiplier on a per-target number at a stack of targets. */
	public static double factor(String kind, int stack, int maxHit)
	{
		if ("chain".equals(kind))
		{
			return stack >= 2 ? RollMath.venatorFactor(maxHit) : 1;
		}
		return Math.max(1, Math.min(stack, AREA_CAP));
	}
}
