package com.loadoutlab.collection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Where each owned item lives - the per-source view the flattened owned
 * map erases. Built from the ledger's source snapshots plus the manual
 * stored-elsewhere list and the DWMS import families; lookups are
 * variant-aware, so a STASH'd ornamented variant answers for the base id
 * the optimizer suggests.
 */
public final class ItemLocations
{
	/** Sources the player can grab from without a fetch trip. */
	private static final Set<String> AT_HAND = Set.of("equipped", "inventory", "bank");

	/** Suffix convention for origins the DWMS import contributed. */
	public static final String VIA_DWMS = " (via DWMS)";

	/** Origin label -> itemId -> quantity, in display order. */
	private final Map<String, Map<Integer, Integer>> origins;

	/** id -> all ids interchangeable with it for ownership (incl. itself);
	 * null means exact-id matching only (dataset still loading). */
	private final Function<Integer, Set<Integer>> equivalents;

	public ItemLocations(Map<String, Map<Integer, Integer>> origins,
		Function<Integer, Set<Integer>> equivalents)
	{
		this.origins = origins;
		this.equivalents = equivalents;
	}

	/** Origin labels holding this item or an interchangeable variant. */
	public List<String> where(int itemId)
	{
		Set<Integer> ids = equivalents == null ? Set.of(itemId) : equivalents.apply(itemId);
		List<String> found = new ArrayList<>();
		for (Map.Entry<String, Map<Integer, Integer>> origin : origins.entrySet())
		{
			for (int id : ids)
			{
				if (origin.getValue().containsKey(id))
				{
					found.add(origin.getKey());
					break;
				}
			}
		}
		return found;
	}

	/**
	 * One tooltip clause: "" when the item is at hand (equipped, inventory,
	 * bank) or not owned at all; otherwise "stored in X" naming the
	 * storage(s) to fetch from. DWMS-attributed origins are dropped when a
	 * natively tracked storage already names the location.
	 */
	public String fetchHint(int itemId)
	{
		List<String> where = where(itemId);
		if (where.isEmpty())
		{
			return "";
		}
		for (String label : where)
		{
			if (AT_HAND.contains(label))
			{
				return "";
			}
		}
		List<String> named = new ArrayList<>();
		for (String label : where)
		{
			if (!label.endsWith(VIA_DWMS))
			{
				named.add(label);
			}
		}
		if (named.isEmpty())
		{
			named = where;
		}
		return "stored in " + String.join(" + ", named);
	}
}
