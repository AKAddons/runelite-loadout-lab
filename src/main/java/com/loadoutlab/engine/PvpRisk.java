package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * What a PvP death actually costs in a given set. Mechanics
 * (wiki: Items Kept on Death, verified 2026-07-07): unskulled you keep
 * your keptSlots (3, 4 with Protect Item) most valuable items by
 * GE/alch value; every other TRADEABLE is lost to the killer;
 * untradeables are kept outside the slot ranking (combat ones return
 * broken for a coin repair - not priced here). The carried special
 * attack weapon rides in the inventory, so it competes for kept slots
 * exactly like worn gear and can displace a cheaper item into the
 * lost pile.
 */
public final class PvpRisk
{
	public static final class Assessment
	{
		public final long riskGp;
		/** Most valuable tradeables, kept on death - best first. */
		public final List<GearItem> kept;
		/** Tradeables beyond the kept slots - lost to the killer. */
		public final List<GearItem> lost;

		Assessment(long riskGp, List<GearItem> kept, List<GearItem> lost)
		{
			this.riskGp = riskGp;
			this.kept = Collections.unmodifiableList(kept);
			this.lost = Collections.unmodifiableList(lost);
		}
	}

	private PvpRisk()
	{
	}

	public static Assessment assess(Loadout loadout, GearItem carriedSpecWeapon, int keptSlots)
	{
		List<GearItem> tradeables = new ArrayList<>();
		for (GearItem item : loadout.getGear().values())
		{
			if (item != null && item.isTradeable())
			{
				tradeables.add(item);
			}
		}
		if (carriedSpecWeapon != null && carriedSpecWeapon.isTradeable())
		{
			tradeables.add(carriedSpecWeapon);
		}
		tradeables.sort(Comparator.comparingInt(GearItem::getPriceOrZero).reversed());
		int keep = Math.max(0, keptSlots);
		List<GearItem> kept = new ArrayList<>(tradeables.subList(0, Math.min(keep, tradeables.size())));
		List<GearItem> lost = new ArrayList<>(tradeables.subList(Math.min(keep, tradeables.size()), tradeables.size()));
		long risk = 0;
		for (GearItem item : lost)
		{
			risk += item.getPriceOrZero();
		}
		return new Assessment(risk, kept, lost);
	}

	/** Compact gp formatting: 1.2B / 45.3M / 820k / 950. */
	public static String formatGp(long gp)
	{
		if (gp >= 1_000_000_000L)
		{
			return String.format("%.2fB", gp / 1_000_000_000.0);
		}
		if (gp >= 1_000_000L)
		{
			return String.format("%.1fM", gp / 1_000_000.0);
		}
		if (gp >= 1_000L)
		{
			return String.format("%dk", gp / 1_000L);
		}
		return String.valueOf(gp);
	}
}
