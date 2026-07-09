package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import java.util.Locale;

/**
 * Blowpipes fire a LOADED dart whose ranged strength stacks on the
 * weapon's own stats - flat gear data cannot express it, which made bare
 * thrown dragon darts (35 str) outrank a dragon-dart blowpipe (20 + 35).
 *
 * <p>Game best assumes dragon darts; ownership-scoped queries use the
 * best dart tier the player owns (any poison variant counts). A blowpipe
 * with no owned darts gets no bonus and naturally falls out of the
 * suggestions - it cannot fire.
 */
public final class BlowpipeDarts
{
	/** Dart tiers, strongest first: {ranged strength, ids...} (all poison variants). */
	private static final int[][] TIERS = {
		{35, 11230, 11231, 11233, 11234},   // dragon
		{28, 25849, 25851, 25855, 25857},   // amethyst
		{26, 811, 817, 5634, 5641},         // rune
		{17, 810, 816, 5633, 5640},         // adamant
		{9, 809, 815, 5632, 5639},          // mithril
		{6, 3093, 3094, 5631, 5638},        // black
		{3, 808, 814, 5630, 5637},          // steel
		{2, 807, 813, 5629, 5636},          // iron
		{1, 806, 812, 5628, 5635},          // bronze
	};
	private static final String[] TIER_NAMES = {
		"dragon darts", "amethyst darts", "rune darts", "adamant darts",
		"mithril darts", "black darts", "steel darts", "iron darts", "bronze darts",
	};

	private BlowpipeDarts()
	{
	}

	/** Extra ranged strength from the loaded dart, 0 for non-blowpipes. */
	public static int strength(OptimizationRequest request, GearItem weapon)
	{
		int tier = tierFor(request, weapon);
		return tier < 0 ? 0 : TIERS[tier][0];
	}

	/** The assumed/owned dart tier name for display, or null. */
	public static String tierName(OptimizationRequest request, GearItem weapon)
	{
		int tier = tierFor(request, weapon);
		return tier < 0 ? null : TIER_NAMES[tier];
	}

	/** The representative (unpoisoned) dart id for a display tier name -
	 * lets the panel offer "exclude the loaded darts" on the blowpipe. */
	public static Integer baseIdForTierName(String tierName)
	{
		for (int tier = 0; tier < TIER_NAMES.length; tier++)
		{
			if (TIER_NAMES[tier].equals(tierName))
			{
				return TIERS[tier][1];
			}
		}
		return null;
	}

	/** Excluding any variant of a dart tier protects the whole tier. */
	private static boolean tierExcluded(OptimizationRequest request, int tier)
	{
		for (int i = 1; i < TIERS[tier].length; i++)
		{
			if (request.isExcluded(TIERS[tier][i]))
			{
				return true;
			}
		}
		return false;
	}

	private static int tierFor(OptimizationRequest request, GearItem weapon)
	{
		if (weapon == null || !weapon.getNameLower().contains("blowpipe"))
		{
			return -1;
		}
		boolean ownershipScoped = request.getCandidateMode() == CandidateMode.OWNED_ONLY
			|| request.getCandidateMode() == CandidateMode.OWNED_OR_BUDGET;
		if (!ownershipScoped)
		{
			// Game best: the best dart tier that is not excluded.
			for (int tier = 0; tier < TIERS.length; tier++)
			{
				if (!tierExcluded(request, tier))
				{
					return tier;
				}
			}
			return -1;
		}
		for (int tier = 0; tier < TIERS.length; tier++)
		{
			if (tierExcluded(request, tier))
			{
				continue;
			}
			for (int i = 1; i < TIERS[tier].length; i++)
			{
				if (request.getOwnedItems().owns(TIERS[tier][i]))
				{
					return tier;
				}
			}
		}
		return -1; // owns no usable darts: the blowpipe cannot fire
	}
}
