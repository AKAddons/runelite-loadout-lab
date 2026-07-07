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
		/** Total per-death cost: lost items + untradeable fees. */
		public final long riskGp;
		/** Most valuable losable items, kept on death - best first.
		 * Includes convert-class untradeables (slayer helm, crystal):
		 * protecting them prevents the component drop. */
		public final List<GearItem> kept;
		/** Losable items beyond the kept slots - lost to the killer. */
		public final List<GearItem> lost;
		/** Worn untradeables that cost coins on death regardless of
		 * protection (break/mangle fees), biggest first. */
		public final List<Charge> untradeableCharges;
		/** Display value per item id - a convert-class untradeable's
		 * value is its component, not its (absent) GE price. */
		public final java.util.Map<Integer, Long> valueById;

		Assessment(long riskGp, List<GearItem> kept, List<GearItem> lost,
			List<Charge> untradeableCharges, java.util.Map<Integer, Long> valueById)
		{
			this.riskGp = riskGp;
			this.kept = Collections.unmodifiableList(kept);
			this.lost = Collections.unmodifiableList(lost);
			this.untradeableCharges = Collections.unmodifiableList(untradeableCharges);
			this.valueById = Collections.unmodifiableMap(valueById);
		}

		public long valueOf(GearItem item)
		{
			Long value = valueById.get(item.getId());
			return value == null ? item.getPriceOrZero() : value;
		}
	}

	private PvpRisk()
	{
	}

	public static Assessment assess(Loadout loadout, GearItem carriedSpecWeapon, int keptSlots)
	{
		// The protection pool: everything that would be LOST unprotected -
		// tradeables at GE value, convert-class untradeables (slayer helm,
		// crystal, treads...) at their component value. Break/mangle fees
		// apply regardless of protection and never occupy a slot.
		List<GearItem> pool = new ArrayList<>();
		List<Charge> charges = new ArrayList<>();
		java.util.Map<Integer, Long> valueById = new java.util.HashMap<>();
		for (GearItem item : loadout.getGear().values())
		{
			sort(item, pool, charges, valueById);
		}
		sort(carriedSpecWeapon, pool, charges, valueById);
		pool.sort(Comparator.comparingLong((GearItem g) ->
			valueById.getOrDefault(g.getId(), 0L)).reversed());
		charges.sort(Comparator.comparingLong((Charge c) -> c.costGp).reversed());
		int keep = Math.max(0, keptSlots);
		List<GearItem> kept = new ArrayList<>(pool.subList(0, Math.min(keep, pool.size())));
		List<GearItem> lost = new ArrayList<>(pool.subList(Math.min(keep, pool.size()), pool.size()));
		long risk = 0;
		for (GearItem item : lost)
		{
			risk += valueById.getOrDefault(item.getId(), 0L);
		}
		for (Charge charge : charges)
		{
			risk += charge.costGp;
		}
		return new Assessment(risk, kept, lost, charges, valueById);
	}

	/** Losable items join the protection pool; the rest accrue fees. */
	private static void sort(GearItem item, List<GearItem> pool,
		List<Charge> charges, java.util.Map<Integer, Long> valueById)
	{
		if (item == null)
		{
			return;
		}
		if (item.isTradeable())
		{
			pool.add(item);
			valueById.put(item.getId(), (long) item.getPriceOrZero());
			return;
		}
		long cost = UntradeableDeathCosts.costFor(item);
		if (cost <= 0)
		{
			return;
		}
		if (UntradeableDeathCosts.isConvertible(item))
		{
			pool.add(item);
			valueById.put(item.getId(), cost);
			return;
		}
		charges.add(new Charge(item, cost));
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
