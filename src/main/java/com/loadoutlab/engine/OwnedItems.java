// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import lombok.Getter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;

public final class OwnedItems
{
	public static final OwnedItems EMPTY = new OwnedItems(Collections.emptyMap(), false);

	@Getter
	private final Map<Integer, Integer> quantities;
	@Getter
	private final boolean bankScanned;

	public OwnedItems(Map<Integer, Integer> quantities, boolean bankScanned)
	{
		this.quantities = Collections.unmodifiableMap(new HashMap<>(quantities));
		this.bankScanned = bankScanned;
	}

	public boolean owns(int itemId)
	{
		return quantities.getOrDefault(itemId, 0) > 0;
	}

	public int size()
	{
		return quantities.size();
	}

	/**
	 * Fingerprint of what owns() answers - the ids with quantity > 0,
	 * order-independent, quantity-BLIND. The optimizer's results depend
	 * only on presence, so its cache keys on this: a quantity-sensitive
	 * hash invalidated every cached answer each time the player shot an
	 * arrow or picked up loot (field-observed: a 22s Balanced compute
	 * re-paid on every revisit while grinding).
	 */
	public int presenceFingerprint()
	{
		int[] ids = new int[quantities.size()];
		int count = 0;
		for (Map.Entry<Integer, Integer> entry : quantities.entrySet())
		{
			if (entry.getValue() != null && entry.getValue() > 0)
			{
				ids[count++] = entry.getKey();
			}
		}
		Arrays.sort(ids, 0, count);
		int hash = count;
		for (int i = 0; i < count; i++)
		{
			hash = 31 * hash + ids[i];
		}
		return hash;
	}
}
