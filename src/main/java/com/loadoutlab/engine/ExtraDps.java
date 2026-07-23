package com.loadoutlab.engine;

/**
 * External DPS the worn set doesn't produce: the thrall's flat tier dps,
 * folded into the SHOWN numbers only (matching the official calculator's
 * thrall toggle) - never a ranking input. Wiki-verified 2026-07-21:
 * thralls attack every 4 ticks (2.4s), always deal a successful 0..max
 * roll ignoring armour - max 1/2/3 by spell tier (Magic 38/57/76) =
 * 0.208/0.416/0.625 dps, requiring the Arceuus book and the book of the
 * dead wielded or carried. (Vengeance modeling deferred - roadmap.)
 */
public final class ExtraDps
{
	public static final int BOOK_OF_THE_DEAD = 25818;

	private ExtraDps()
	{
	}

	/** The best thrall tier's flat dps at this Magic level (0 below 38). */
	public static double thrallDps(int magicLevel)
	{
		if (magicLevel >= 76)
		{
			return 0.625;
		}
		if (magicLevel >= 57)
		{
			return 0.416;
		}
		if (magicLevel >= 38)
		{
			return 0.208;
		}
		return 0;
	}

	/** The tier name the Magic level reaches, or null below 38. */
	public static String thrallTier(int magicLevel)
	{
		if (magicLevel >= 76)
		{
			return "Greater";
		}
		if (magicLevel >= 57)
		{
			return "Superior";
		}
		if (magicLevel >= 38)
		{
			return "Lesser";
		}
		return null;
	}

}
