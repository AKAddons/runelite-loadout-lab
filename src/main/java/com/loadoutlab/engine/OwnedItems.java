// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class OwnedItems
{
	public static final OwnedItems EMPTY = new OwnedItems(Collections.emptyMap(), false);

	private final Map<Integer, Integer> quantities;
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

	public boolean isBankScanned()
	{
		return bankScanned;
	}

	public int size()
	{
		return quantities.size();
	}
}
