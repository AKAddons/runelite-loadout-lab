package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Phase-1 core of the D-7 DP optimizer (test-tree resident until the
 * beam swap): a Pareto frontier over summed (accuracy bonus, damage
 * bonus) as slots fold in. DPS is monotone in both sums for a fixed
 * weapon/attack-type/mode, so the optimum ALWAYS lies on this frontier -
 * evaluating full DPS only at the end is exact, where the beam's top-N
 * cuts are heuristic.
 *
 * <p>States are kept sorted by accuracy ascending; domination is then a
 * single sweep (strictly increasing damage). Payload per state = the
 * chosen item per slot, for reconstructing the Loadout.
 */
final class ParetoFrontier
{
	/** One non-dominated bonus-sum state and the picks that reached it. */
	static final class State
	{
		final int accuracy;
		final int damage;
		final Map<GearSlot, GearItem> picks;

		State(int accuracy, int damage, Map<GearSlot, GearItem> picks)
		{
			this.accuracy = accuracy;
			this.damage = damage;
			this.picks = picks;
		}
	}

	private List<State> states = new ArrayList<>();

	ParetoFrontier()
	{
		states.add(new State(0, 0, new EnumMap<>(GearSlot.class)));
	}

	/**
	 * Fold one slot's candidates in: every (state x candidate) combination,
	 * then prune to the non-dominated set. A null candidate = leave the
	 * slot empty (always offered, so empty-slot fills stay possible).
	 */
	void fold(GearSlot slot, List<GearItem> candidates,
		java.util.function.ToIntFunction<GearItem> accuracyOf,
		java.util.function.ToIntFunction<GearItem> damageOf)
	{
		List<State> next = new ArrayList<>(states.size() * (candidates.size() + 1));
		next.addAll(states); // the empty-slot choice keeps each state as-is
		for (State s : states)
		{
			for (GearItem item : candidates)
			{
				if (item == null)
				{
					continue;
				}
				Map<GearSlot, GearItem> picks = new EnumMap<>(s.picks);
				picks.put(slot, item);
				next.add(new State(
					s.accuracy + accuracyOf.applyAsInt(item),
					s.damage + damageOf.applyAsInt(item),
					picks));
			}
		}
		states = prune(next);
	}

	/** Non-dominated subset: sort by accuracy desc then damage desc; keep
	 * states whose damage strictly exceeds every higher-accuracy state's. */
	private static List<State> prune(List<State> all)
	{
		all.sort((a, b) -> a.accuracy != b.accuracy
			? Integer.compare(b.accuracy, a.accuracy)
			: Integer.compare(b.damage, a.damage));
		List<State> kept = new ArrayList<>();
		int bestDamage = Integer.MIN_VALUE;
		for (State s : all)
		{
			if (s.damage > bestDamage)
			{
				kept.add(s);
				bestDamage = s.damage;
			}
		}
		return kept;
	}

	List<State> states()
	{
		return states;
	}
}
