package com.loadoutlab.engine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.JsonResources;
import java.util.*;
import java.util.List;
import java.util.Map;

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
	private static final int[][] TIERS;
	private static final String[] TIER_NAMES;

	static
	{
		JsonObject root = JsonResources.objectOrThrow("blowpipe_darts.json");
		List<String> names = new ArrayList<>();
		JsonResources.strings(root, "names", names);
		TIER_NAMES = names.toArray(new String[0]);
		JsonArray rows = root.getAsJsonArray("rows");
		TIERS = new int[rows.size()][];
		for (int t = 0; t < rows.size(); t++)
		{
			JsonArray row = rows.get(t).getAsJsonArray();
			TIERS[t] = new int[row.size()];
			for (int i = 0; i < row.size(); i++)
			{
				TIERS[t][i] = row.get(i).getAsInt();
			}
		}
	}

	private BlowpipeDarts()
	{
	}

	/** Every tier for the pickers: [{id (unpoisoned base), name}],
	 * strongest first - excluding the base id protects the whole tier. */
	public static List<Map<String, Object>> tiers()
	{
		List<Map<String, Object>> out = new ArrayList<>();
		for (int tier = 0; tier < TIERS.length; tier++)
		{
			Map<String, Object> node = new LinkedHashMap<>();
			node.put("id", TIERS[tier][1]);
			node.put("name", TIER_NAMES[tier]);
			out.add(node);
		}
		return out;
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
