package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * What a PvP death actually costs in a given set. Mechanics
 * (wiki: Items Kept on Death + Trouver parchment, June 2026 rework,
 * verified 2026-07-07): unskulled you keep your keptSlots (3, 4 with
 * Protect Item) most valuable items by GE/alch value; every other
 * TRADEABLE is lost to the killer. UNTRADEABLES are kept outside the
 * slot ranking but combat ones cost coins per death - a repair fee
 * (category 2), the flat 500k mangle fee once trouver-locked
 * (category 3), or the value of the tradeable component the killer
 * receives - priced by UntradeableDeathCosts and summed into riskGp.
 * The carried special attack weapon rides in the inventory, so it
 * competes for kept slots exactly like worn gear and can displace a
 * cheaper item into the lost pile.
 */
public final class PvpRisk
{
	/** One worn untradeable and its per-death cost (repair/mangle fee). */
	public static final class Charge
	{
		public final GearItem item;
		public final long costGp;

		Charge(GearItem item, long costGp)
		{
			this.item = item;
			this.costGp = costGp;
		}
	}

	public static final class Assessment
	{
		/** Total per-death cost: lost tradeables + untradeable fees. */
		public final long riskGp;
		/** Most valuable tradeables, kept on death - best first. */
		public final List<GearItem> kept;
		/** Tradeables beyond the kept slots - lost to the killer. */
		public final List<GearItem> lost;
		/** Worn untradeables that cost coins on death, biggest fee first. */
		public final List<Charge> untradeableCharges;

		Assessment(long riskGp, List<GearItem> kept, List<GearItem> lost, List<Charge> untradeableCharges)
		{
			this.riskGp = riskGp;
			this.kept = Collections.unmodifiableList(kept);
			this.lost = Collections.unmodifiableList(lost);
			this.untradeableCharges = Collections.unmodifiableList(untradeableCharges);
		}
	}

	private PvpRisk()
	{
	}

	public static Assessment assess(Loadout loadout, GearItem carriedSpecWeapon, int keptSlots)
	{
		List<GearItem> tradeables = new ArrayList<>();
		List<Charge> charges = new ArrayList<>();
		for (GearItem item : loadout.getGear().values())
		{
			sort(item, tradeables, charges);
		}
		sort(carriedSpecWeapon, tradeables, charges);
		tradeables.sort(Comparator.comparingInt(GearItem::getPriceOrZero).reversed());
		charges.sort(Comparator.comparingLong((Charge c) -> c.costGp).reversed());
		int keep = Math.max(0, keptSlots);
		List<GearItem> kept = new ArrayList<>(tradeables.subList(0, Math.min(keep, tradeables.size())));
		List<GearItem> lost = new ArrayList<>(tradeables.subList(Math.min(keep, tradeables.size()), tradeables.size()));
		long risk = 0;
		for (GearItem item : lost)
		{
			risk += item.getPriceOrZero();
		}
		for (Charge charge : charges)
		{
			risk += charge.costGp;
		}
		return new Assessment(risk, kept, lost, charges);
	}

	/** Tradeables compete for kept slots; untradeables accrue death fees. */
	private static void sort(GearItem item, List<GearItem> tradeables, List<Charge> charges)
	{
		if (item == null)
		{
			return;
		}
		if (item.isTradeable())
		{
			tradeables.add(item);
			return;
		}
		long cost = UntradeableDeathCosts.costFor(item);
		if (cost > 0)
		{
			charges.add(new Charge(item, cost));
		}
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
