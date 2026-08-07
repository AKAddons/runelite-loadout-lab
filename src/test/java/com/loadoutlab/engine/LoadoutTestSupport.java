package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;

/**
 * Loadout queries only the test suite asks for. Kept out of main source so
 * they do not count against the Plugin Hub bot's 200k token budget; the
 * logic is the production version verbatim, over the public
 * {@link Loadout#getGear()} view.
 */
public final class LoadoutTestSupport
{
	private LoadoutTestSupport()
	{
	}

	/** Items in this set you would risk in PvP (tradeables). */
	public static int tradeableCount(Loadout loadout)
	{
		int count = 0;
		for (GearItem item : loadout.getGear().values())
		{
			if (item != null && item.isTradeable())
			{
				count++;
			}
		}
		return count;
	}
}
