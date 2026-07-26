package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * D-7 mode enumeration, v1 (test-tree): a MODE pins the slots whose
 * items only pay off through a multiplier or set bonus that lives in
 * DpsCalculator, not in the fold's (accuracy, strength) dimensions -
 * so domination pruning cannot starve them. Modes are DISCOVERED from
 * the beam's own candidate pools by name, never invented, so mode
 * eligibility always equals pool eligibility.
 *
 * <p>Two categories (both evidence-backed by the mode-necessity map):
 * item multipliers (salve/avarice necks - dragonbane and wilderness
 * WEAPONS are already covered by the per-weapon loop) and set
 * completions (blood moon, inquisitor, void-knight, crystal armour).
 * The base mode (no pins) always runs too.
 */
final class ParetoModes
{
	/** Slot pins for one mode; empty = the unpinned base mode. */
	static final class Mode
	{
		final String name;
		final Map<GearSlot, GearItem> pins;

		Mode(String name, Map<GearSlot, GearItem> pins)
		{
			this.name = name;
			this.pins = pins;
		}
	}

	private static final String[][] SET_FAMILIES = {
		{"blood moon", "blood moon helm", "blood moon chestplate", "blood moon tassets"},
		{"inquisitor", "inquisitor's great helm", "inquisitor's hauberk", "inquisitor's plateskirt"},
		{"void melee", "void melee helm", "void knight top", "void knight robe"},
		{"elite void melee", "void melee helm", "elite void top", "elite void robe"},
		{"crystal armour", "crystal helm", "crystal body", "crystal legs"},
	};

	private static final String[] CONDITIONAL_NECKS = {
		"salve amulet(ei)", "salve amulet(i)", "salve amulet (e)", "salve amulet",
		"amulet of avarice",
	};

	/** Every mode the pools can support for this request. */
	static List<Mode> enumerate(LoadoutOptimizer.CandidatePools pools)
	{
		List<Mode> modes = new ArrayList<>();
		modes.add(new Mode("base", new EnumMap<>(GearSlot.class)));

		List<GearItem> necks = pools.slotCandidates.getOrDefault(GearSlot.NECK, List.of());
		for (String name : CONDITIONAL_NECKS)
		{
			for (GearItem neck : necks)
			{
				if (neck != null && neck.getNameLower().equals(name))
				{
					Map<GearSlot, GearItem> pins = new EnumMap<>(GearSlot.class);
					pins.put(GearSlot.NECK, neck);
					modes.add(new Mode(name, pins));
					break;
				}
			}
		}

		for (String[] family : SET_FAMILIES)
		{
			Map<GearSlot, GearItem> pins = new EnumMap<>(GearSlot.class);
			GearSlot[] slots = {GearSlot.HEAD, GearSlot.BODY, GearSlot.LEGS};
			for (int i = 0; i < 3; i++)
			{
				for (GearItem item : pools.slotCandidates.getOrDefault(slots[i], List.of()))
				{
					if (item != null && item.getNameLower().startsWith(family[i + 1]))
					{
						pins.put(slots[i], item);
						break;
					}
				}
			}
			if (pins.size() == 3)
			{
				modes.add(new Mode(family[0], pins));
			}
		}
		return modes;
	}

	/**
	 * The moded frontier search for one weapon/style: pinned slots skip the
	 * fold and land straight in the gear; the dragonfire shield rule
	 * filters shield candidates exactly as the beam does.
	 */
	static double best(OptimizationRequest request, LoadoutOptimizer.CandidatePools pools,
		DpsCalculator calculator, GearItem weapon, String attackType, Mode mode)
	{
		boolean dragonShield = DragonfireRules.shieldRequired(request);
		ParetoFrontier frontier = new ParetoFrontier();
		for (Map.Entry<GearSlot, List<GearItem>> slot : pools.slotCandidates.entrySet())
		{
			if (slot.getKey() == GearSlot.WEAPON || mode.pins.containsKey(slot.getKey())
				|| (slot.getKey() == GearSlot.SHIELD && weapon.isTwoHanded()))
			{
				continue;
			}
			List<GearItem> candidates = new ArrayList<>();
			for (GearItem item : slot.getValue())
			{
				if (item == null)
				{
					continue;
				}
				if (dragonShield && slot.getKey() == GearSlot.SHIELD
					&& !DragonfireRules.isProtectiveShield(item))
				{
					continue;
				}
				candidates.add(item);
			}
			frontier.fold(slot.getKey(), candidates,
				item -> item.getOffensive().getAttackBonus(attackType),
				item -> item.getBonuses().getStrength());
		}

		double best = 0;
		for (ParetoFrontier.State state : frontier.states())
		{
			Map<GearSlot, GearItem> gear = new EnumMap<>(state.picks);
			gear.putAll(mode.pins);
			gear.put(GearSlot.WEAPON, weapon);
			DpsResult result = calculator.calculate(request, new Loadout(gear));
			if (result != null && result.getDps() > best)
			{
				best = result.getDps();
			}
		}
		return best;
	}

	/** Best over every (weapon x melee style x mode). */
	static double bestOverModes(OptimizationRequest request,
		LoadoutOptimizer.CandidatePools pools, DpsCalculator calculator)
	{
		List<Mode> modes = enumerate(pools);
		double best = 0;
		for (GearItem weapon : pools.weapons)
		{
			for (WeaponStyles.MeleeStyle style : WeaponStyles.melee(weapon))
			{
				for (Mode mode : modes)
				{
					best = Math.max(best,
						best(request, pools, calculator, weapon, style.attackType, mode));
				}
			}
		}
		return best;
	}

	private ParetoModes()
	{
	}

	// Keep the map's report labels stable.
	static final Map<String, String> NOTES = new LinkedHashMap<>();
}
