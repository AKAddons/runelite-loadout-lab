package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
		public final Map<Integer, Long> valueById;

		Assessment(long riskGp, List<GearItem> kept, List<GearItem> lost,
			List<Charge> untradeableCharges, Map<Integer, Long> valueById)
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

	/**
	 * The amulet of avarice keeps its wearer PERMANENTLY SKULLED: the
	 * default 3-item protection is gone (1 item with Protect Item). Maps
	 * the caller's kept-slot count (3 or 4) onto the skulled reality.
	 */
	private static int effectiveKeptSlots(Loadout loadout, int keptSlots)
	{
		if (keptSlots <= 0)
		{
			return keptSlots;
		}
		GearItem neck = loadout.get(com.loadoutlab.data.GearSlot.NECK);
		if (neck != null && "amulet of avarice".equals(neck.getNameLower()))
		{
			return keptSlots >= 4 ? 1 : 0;
		}
		return keptSlots;
	}

	public static Assessment assess(Loadout loadout, GearItem carriedSpecWeapon, int keptSlots)
	{
		// The protection pool: everything that would be LOST unprotected -
		// tradeables at GE value, convert-class untradeables (slayer helm,
		// crystal, treads...) at their component value. Break/mangle fees
		// apply regardless of protection and never occupy a slot.
		List<GearItem> pool = new ArrayList<>();
		List<Charge> charges = new ArrayList<>();
		Map<Integer, Long> valueById = new HashMap<>();
		for (GearItem item : loadout.getGear().values())
		{
			sort(item, pool, charges, valueById);
		}
		sort(carriedSpecWeapon, pool, charges, valueById);
		pool.sort(Comparator.comparingLong((GearItem g) ->
			valueById.getOrDefault(g.getId(), 0L)).reversed());
		charges.sort(Comparator.comparingLong((Charge c) -> c.costGp).reversed());
		int keep = Math.max(0, effectiveKeptSlots(loadout, keptSlots));
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

	/**
	 * The riskGp of assess() alone, without building the kept/lost lists,
	 * the value map, or sorting - the beam search calls this per candidate
	 * set, where the full assessment allocated its way to the top of the
	 * profile. Identity: riskGp = (pool total - top keptSlots pool values)
	 * + fees; ties in the value ranking cannot change either sum.
	 */
	public static long riskGp(Loadout loadout, GearItem carriedSpecWeapon, int keptSlots)
	{
		int keep = Math.max(0, effectiveKeptSlots(loadout, keptSlots));
		// The largest `keep` pool values seen so far, ascending (top[0] is
		// the smallest of them); a worn set is at most 11 items + the spec.
		long[] top = new long[Math.min(keep, 12)];
		int topCount = 0;
		long poolTotal = 0;
		long fees = 0;
		for (GearSlot slot : GearSlot.values())
		{
			GearItem item = loadout.get(slot);
			long value = poolValue(item);
			if (value < 0)
			{
				// Mirror sort(): the fee is gp cost PLUS rebuild friction,
				// so the beam's risk filter feels the errand too.
				fees += UntradeableDeathCosts.costFor(item)
					+ UntradeableDeathCosts.frictionFor(item);
				continue;
			}
			poolTotal += value;
			topCount = offerTop(top, topCount, value);
		}
		long specValue = poolValue(carriedSpecWeapon);
		if (specValue < 0)
		{
			fees += UntradeableDeathCosts.costFor(carriedSpecWeapon)
				+ UntradeableDeathCosts.frictionFor(carriedSpecWeapon);
		}
		else
		{
			poolTotal += specValue;
			topCount = offerTop(top, topCount, specValue);
		}
		long keptTotal = 0;
		for (int i = 0; i < topCount; i++)
		{
			keptTotal += top[i];
		}
		return poolTotal - keptTotal + fees;
	}

	/**
	 * True when this set would put a rebuild-burdened item (curated
	 * frictionGp: the salve line, imbued rings/masks) at risk - i.e. the
	 * item is NOT protected by a kept slot. Risk-constrained optimization
	 * rejects such sets outright (field request: never suggest something
	 * that needs a re-imbue as a risked item). Protected is fine: a kept
	 * slayer helmet is standard wilderness practice. Monotone-bad: adding
	 * items can only push a friction item OUT of the kept slots, never
	 * back in, so pruning partial states is safe.
	 */
	public static boolean risksRebuild(Loadout loadout, GearItem carriedSpecWeapon, int keptSlots)
	{
		return risksRebuild(loadout, carriedSpecWeapon, keptSlots, java.util.Collections.emptySet());
	}

	/** Pin-aware variant: a PINNED friction item is the player's explicit
	 * choice and never vetoes the set (its risk still counts).
	 *
	 * <p>Lean on purpose - the beam calls this per candidate set, and the
	 * assess()-based version built and sorted the full kept/lost profile
	 * per trial (the risk path's dominant cost whenever a salve was in the
	 * pool). Same semantics, asserted against assess() in PvpRiskTest:
	 * a non-poolable friction item is a Charge no matter what is protected;
	 * a poolable (convertible) one risks its rebuild only when it ranks
	 * OUTSIDE the kept slots under assess()'s stable value-descending
	 * order (count of strictly-greater values, plus equal values earlier
	 * in pool order). */
	public static boolean risksRebuild(Loadout loadout, GearItem carriedSpecWeapon, int keptSlots,
		java.util.Set<Integer> pinnedIds)
	{
		boolean anyFriction = UntradeableDeathCosts.frictionFor(carriedSpecWeapon) > 0;
		for (GearSlot slot : GearSlot.values())
		{
			anyFriction |= UntradeableDeathCosts.frictionFor(loadout.get(slot)) > 0;
		}
		if (!anyFriction)
		{
			return false;
		}
		// Pool values in assess()'s pool order: worn slots, then the spec.
		// A worn set is at most 11 items + the carried spec weapon.
		long[] values = new long[12];
		int count = 0;
		// Bit i set = pool item i carries unpinned rebuild friction.
		int frictionAt = 0;
		for (GearSlot slot : GearSlot.values())
		{
			GearItem item = loadout.get(slot);
			int fate = offerRebuild(item, values, count, pinnedIds);
			if (fate < 0)
			{
				return true; // unpinned charge-class friction: never protectable
			}
			frictionAt |= (fate & 1) << count;
			count += fate >> 1;
		}
		int fate = offerRebuild(carriedSpecWeapon, values, count, pinnedIds);
		if (fate < 0)
		{
			return true;
		}
		frictionAt |= (fate & 1) << count;
		count += fate >> 1;
		if (frictionAt == 0)
		{
			return false;
		}
		int keep = Math.max(0, effectiveKeptSlots(loadout, keptSlots));
		for (int i = 0; i < count; i++)
		{
			if ((frictionAt & (1 << i)) == 0)
			{
				continue;
			}
			int ahead = 0;
			for (int j = 0; j < count; j++)
			{
				if (values[j] > values[i] || (values[j] == values[i] && j < i))
				{
					ahead++;
				}
			}
			if (ahead >= keep)
			{
				return true; // ranks outside the kept slots - lost, rebuild due
			}
		}
		return false;
	}

	/**
	 * risksRebuild's per-item step. Returns -1 when the item is an UNPINNED
	 * charge-class friction item (instant veto); otherwise a two-bit code:
	 * bit1 = "joined the pool at values[count]", bit0 = "and carries
	 * unpinned friction" (0 = not poolable, contributes nothing).
	 */
	private static int offerRebuild(GearItem item, long[] values, int count,
		java.util.Set<Integer> pinnedIds)
	{
		if (item == null)
		{
			return 0;
		}
		long value = poolValue(item);
		boolean friction = UntradeableDeathCosts.frictionFor(item) > 0
			&& !pinnedIds.contains(item.getId());
		if (value < 0)
		{
			// Mirror sort(): non-poolable items become Charges (fees apply
			// regardless of protection) - friction there is always a veto.
			return friction ? -1 : 0;
		}
		values[count] = value;
		return friction ? 3 : 2;
	}

	/**
	 * This item's protection-pool value: tradeables at GE price,
	 * convert-class untradeables at their component value. -1 means the
	 * item is not poolable - absent, destroyed-on-death, fee-class, or a
	 * costless untradeable; for those, costFor() is the per-death fee
	 * (0 except the destroyed/fee classes). Shared classification for
	 * assess() and the lean riskGp().
	 */
	private static long poolValue(GearItem item)
	{
		if (item == null)
		{
			return -1; // costFor(null) == 0
		}
		if (UntradeableDeathCosts.isDestroyedOnDeath(item))
		{
			return -1; // always a charge, never poolable
		}
		if (item.isTradeable())
		{
			return item.getPriceOrZero();
		}
		long cost = UntradeableDeathCosts.costFor(item);
		if (cost <= 0)
		{
			return -1; // costs nothing either way
		}
		// Convertibles ride the kept-slot pool at component value PLUS the
		// rebuild friction (imbued rings: killer gets the base ring, the
		// victim still owes a re-imbue visit) - raising both their loss
		// price and their protection priority.
		return UntradeableDeathCosts.isConvertible(item)
			? cost + UntradeableDeathCosts.frictionFor(item) : -1;
	}

	/** Insert value into the ascending top-N array; returns the new count. */
	private static int offerTop(long[] top, int count, long value)
	{
		if (top.length == 0)
		{
			return 0;
		}
		if (count < top.length)
		{
			int i = count;
			while (i > 0 && top[i - 1] > value)
			{
				top[i] = top[i - 1];
				i--;
			}
			top[i] = value;
			return count + 1;
		}
		if (value <= top[0])
		{
			return count;
		}
		int i = 1;
		while (i < top.length && top[i] < value)
		{
			top[i - 1] = top[i];
			i++;
		}
		top[i - 1] = value;
		return count;
	}

	/** Losable items join the protection pool; the rest accrue fees.
	 * Classification lives in poolValue() - shared with the lean riskGp(). */
	private static void sort(GearItem item, List<GearItem> pool,
		List<Charge> charges, Map<Integer, Long> valueById)
	{
		if (item == null)
		{
			return;
		}
		long value = poolValue(item);
		if (value >= 0)
		{
			pool.add(item);
			valueById.put(item.getId(), value);
			return;
		}
		if (UntradeableDeathCosts.isDestroyedOnDeath(item))
		{
			// Crumbles to dust on ANY death - protection cannot save it,
			// so it never enters the pool; the replacement cost always applies.
			charges.add(new Charge(item, UntradeableDeathCosts.costFor(item)));
			return;
		}
		long cost = UntradeableDeathCosts.costFor(item);
		long friction = UntradeableDeathCosts.frictionFor(item);
		if (cost > 0 || friction > 0 || UntradeableDeathCosts.categoryFor(item) >= 2)
		{
			// Breakers charge their gp cost PLUS the curated rebuild
			// friction (the salve line: gp-free but a real errand chain) -
			// so the risk total and the risk cap feel the loss and a
			// "free reclaim" item can never ride into a low-risk set
			// unnoticed (field request).
			charges.add(new Charge(item, cost + friction));
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
