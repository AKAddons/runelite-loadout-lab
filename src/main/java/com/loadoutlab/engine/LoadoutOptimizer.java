// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.SpellStats;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LoadoutOptimizer
{
	private static final int SLOT_LIMIT = 10;
	private static final int WEAPON_LIMIT = 24;
	private static final int BEAM_WIDTH = 64;
	private static final GearSlot[] NON_WEAPON_SLOTS = {
		GearSlot.HEAD,
		GearSlot.CAPE,
		GearSlot.NECK,
		GearSlot.AMMO,
		GearSlot.BODY,
		GearSlot.SHIELD,
		GearSlot.LEGS,
		GearSlot.HANDS,
		GearSlot.FEET,
		GearSlot.RING
	};

	private final DpsCalculator calculator = new DpsCalculator();

	public List<DpsResult> optimize(LoadoutData data, OptimizationRequest request)
	{
		if (request.getMonster() == null || request.getStyle() == null || request.getLevels() == null)
		{
			return java.util.Collections.emptyList();
		}
		if (request.getStyle() == CombatStyle.ANY)
		{
			return optimizeAny(data, request);
		}

		List<SpellStats> spells = spellsFor(data, request);
		List<GearItem> weapons = candidates(data, request, GearSlot.WEAPON, WEAPON_LIMIT);
		Map<GearSlot, List<GearItem>> slotCandidates = new EnumMap<>(GearSlot.class);
		for (GearSlot slot : NON_WEAPON_SLOTS)
		{
			slotCandidates.put(slot, candidates(data, request, slot, SLOT_LIMIT));
		}

		List<DpsResult> results = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (GearItem weapon : weapons)
		{
			List<SearchState> states = new ArrayList<>();
			EnumMap<GearSlot, GearItem> baseGear = new EnumMap<>(GearSlot.class);
			baseGear.put(GearSlot.WEAPON, weapon);
			states.add(new SearchState(baseGear, budgetCost(request, weapon)));

			for (GearSlot slot : NON_WEAPON_SLOTS)
			{
				List<SearchState> next = new ArrayList<>();
				List<GearItem> candidates = candidatesForSlotWithWeapon(slotCandidates.get(slot), weapon, slot);
				for (SearchState state : states)
				{
					for (GearItem item : candidates)
					{
						int cost = state.cost + budgetCost(request, item);
						if (!withinBudget(request, cost))
						{
							continue;
						}
						EnumMap<GearSlot, GearItem> gear = new EnumMap<>(state.gear);
						if (item != null)
						{
							gear.put(slot, item);
						}
						Loadout loadout = new Loadout(gear);
						DpsResult score = bestSpellResult(request, loadout, spells);
						next.add(new SearchState(gear, cost, score.getDps()));
					}
				}
				next.sort(Comparator.comparingDouble(SearchState::getScore).reversed().thenComparingInt(SearchState::getCost));
				states = next.size() > BEAM_WIDTH ? new ArrayList<>(next.subList(0, BEAM_WIDTH)) : next;
				if (states.isEmpty())
				{
					break;
				}
			}

			for (SearchState state : states)
			{
				Loadout loadout = new Loadout(state.gear);
				String signature = signature(loadout);
				if (!seen.add(signature))
				{
					continue;
				}
				results.add(bestSpellResult(request, loadout, spells).withPurchaseCost(state.cost));
			}
		}

		results.sort(Comparator.comparingDouble(DpsResult::getDps).reversed().thenComparingInt(DpsResult::getPurchaseCost));
		return results.size() > request.getResultLimit() ? new ArrayList<>(results.subList(0, request.getResultLimit())) : results;
	}

	private DpsResult bestSpellResult(OptimizationRequest request, Loadout loadout, List<SpellStats> spells)
	{
		if (request.getStyle() != CombatStyle.MAGIC || !request.isAutoSpell())
		{
			return calculator.calculate(request, loadout);
		}
		DpsResult best = null;
		boolean poweredStaff = isPoweredStaff(loadout.getWeapon());
		if (poweredStaff)
		{
			best = best(best, calculator.calculate(request, loadout));
		}
		if (!poweredStaff)
		{
			for (SpellStats spell : spells)
			{
				if (spellAllowed(request, loadout, spell))
				{
					best = best(best, calculator.calculate(request.withSpell(spell), loadout));
				}
			}
		}
		return best == null ? calculator.calculate(request, loadout) : best;
	}

	private static DpsResult best(DpsResult first, DpsResult second)
	{
		if (first == null || second.getDps() > first.getDps())
		{
			return second;
		}
		return first;
	}

	private static List<SpellStats> spellsFor(LoadoutData data, OptimizationRequest request)
	{
		if (request.getStyle() != CombatStyle.MAGIC || !request.isAutoSpell())
		{
			return java.util.Collections.emptyList();
		}
		List<SpellStats> spells = new ArrayList<>();
		for (SpellStats spell : data.getSpells())
		{
			if (request.getLevels().getMagic() >= spell.getMagicLevel())
			{
				spells.add(spell);
			}
		}
		spells.sort(Comparator.comparingInt(SpellStats::getMaxHit).reversed());
		return spells.isEmpty() ? data.getSpells() : spells;
	}

	private List<DpsResult> optimizeAny(LoadoutData data, OptimizationRequest request)
	{
		List<DpsResult> merged = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (CombatStyle style : CombatStyle.concreteValues())
		{
			OptimizationRequest styled = request.withStyle(style);
			for (DpsResult result : optimize(data, styled))
			{
				String signature = result.getAttackType() + ":" + signature(result.getLoadout());
				if (seen.add(signature))
				{
					merged.add(result);
				}
			}
		}
		merged.sort(Comparator.comparingDouble(DpsResult::getDps).reversed().thenComparingInt(DpsResult::getPurchaseCost));
		return merged.size() > request.getResultLimit() ? new ArrayList<>(merged.subList(0, request.getResultLimit())) : merged;
	}

	private List<GearItem> candidates(LoadoutData data, OptimizationRequest request, GearSlot slot, int limit)
	{
		List<GearItem> rows = new ArrayList<>();
		for (GearItem item : data.getGearItems())
		{
			if (item.getSlot() != slot || !item.isStandardGear())
			{
				continue;
			}
			if (!request.getRequirementProfile().canEquip(item.getRequirements()))
			{
				continue;
			}
			if (slot == GearSlot.WEAPON && !item.isWeaponFor(request.getStyle()))
			{
				continue;
			}
			if (slot != GearSlot.WEAPON && candidateScore(request, item) <= 0)
			{
				continue;
			}
			if (!allowedByMode(request, item))
			{
				continue;
			}
			rows.add(item);
		}

		rows.sort(Comparator.comparingDouble((GearItem item) -> candidateScore(request, item)).reversed().thenComparingInt(GearItem::getPriceOrZero));
		rows = dedupe(rows, request);
		if (rows.size() > limit)
		{
			rows = new ArrayList<>(rows.subList(0, limit));
		}
		if (slot != GearSlot.WEAPON)
		{
			rows.add(0, null);
		}
		return rows;
	}

	private static List<GearItem> candidatesForSlotWithWeapon(List<GearItem> candidates, GearItem weapon, GearSlot slot)
	{
		if (slot == GearSlot.SHIELD && weapon != null && weapon.isTwoHanded())
		{
			return java.util.Collections.singletonList(null);
		}
		if (slot != GearSlot.AMMO)
		{
			return candidates;
		}
		List<GearItem> result = new ArrayList<>();
		for (GearItem item : candidates)
		{
			if (RangedAmmo.compatible(item, weapon))
			{
				result.add(item);
			}
		}
		if (result.isEmpty() && RangedAmmo.compatible(null, weapon))
		{
			return java.util.Collections.singletonList(null);
		}
		return result;
	}

	private static double candidateScore(OptimizationRequest request, GearItem item)
	{
		double score = item.roughScore(request.getStyle());
		if (slayerTaskHeadCandidate(request, item))
		{
			score += 10_000.0;
		}
		String name = label(item);
		if (request.getMonster() != null)
		{
			if (request.getMonster().hasAttribute("undead") && name.contains("salve amulet"))
			{
				score += 5_000.0;
			}
			if (request.getMonster().hasAttribute("dragon") && name.contains("dragon hunter"))
			{
				score += 4_500.0;
			}
			if (request.getMonster().hasAttribute("demon") && (name.contains("arclight") || name.contains("emberlight") || name.contains("darklight") || name.contains("silverlight") || name.contains("scorching bow")))
			{
				score += 4_000.0;
			}
			if (request.getMonster().hasAttribute("kalphite") && name.contains("keris"))
			{
				score += 3_000.0;
			}
		}
		if (request.getStyle() == CombatStyle.RANGED && (name.contains("crystal helm") || name.contains("crystal body") || name.contains("crystal legs") || name.contains("bow of faerdhinen") || name.contains("crystal bow")))
		{
			score += 2_500.0;
		}
		if (request.getStyle() == CombatStyle.MELEE && (name.contains("obsidian helmet") || name.contains("obsidian platebody") || name.contains("obsidian platelegs") || name.contains("berserker necklace") || isTzhaarWeapon(name)))
		{
			score += 2_000.0;
		}
		if (request.getStyle() == CombatStyle.MELEE && (name.contains("inquisitor's great helm") || name.contains("inquisitor's hauberk") || name.contains("inquisitor's plateskirt") || name.contains("inquisitor's mace")))
		{
			score += 1_750.0;
		}
		return score;
	}

	private static boolean slayerTaskHeadCandidate(OptimizationRequest request, GearItem item)
	{
		if (!request.isOnSlayerTask() || request.getMonster() == null || !request.getMonster().isSlayerMonster() || item == null || !item.isSlayerHead())
		{
			return false;
		}
		return request.getStyle() == CombatStyle.MELEE || item.isImbuedSlayerHead();
	}

	private static boolean spellAllowed(OptimizationRequest request, Loadout loadout, SpellStats spell)
	{
		String spellName = spell.getName();
		String weapon = label(loadout.getWeapon());
		if (isPoweredStaff(loadout.getWeapon()))
		{
			return false;
		}
		if (spellName.contains("Demonbane") && (request.getMonster() == null || !request.getMonster().hasAttribute("demon")))
		{
			return false;
		}
		if ("Crumble Undead".equals(spellName) && (request.getMonster() == null || !request.getMonster().hasAttribute("undead")))
		{
			return false;
		}
		if ("Iban Blast".equals(spellName))
		{
			return weapon.contains("iban's staff");
		}
		if ("Saradomin Strike".equals(spellName))
		{
			return weapon.contains("saradomin staff") || weapon.contains("staff of light");
		}
		if ("Claws of Guthix".equals(spellName))
		{
			return weapon.contains("guthix staff") || weapon.contains("void knight mace") || weapon.contains("staff of balance");
		}
		if ("Flames of Zamorak".equals(spellName))
		{
			return weapon.contains("zamorak staff")
				|| weapon.contains("staff of the dead")
				|| weapon.contains("toxic staff of the dead")
				|| weapon.contains("thammaron")
				|| weapon.contains("accursed sceptre");
		}
		if ("Magic Dart".equals(spellName))
		{
			return weapon.contains("slayer's staff")
				|| weapon.contains("staff of the dead")
				|| weapon.contains("toxic staff of the dead")
				|| weapon.contains("staff of light")
				|| weapon.contains("staff of balance");
		}
		return true;
	}

	private static String label(GearItem item)
	{
		return item == null ? "" : item.label().toLowerCase(Locale.ROOT);
	}

	private static boolean isPoweredStaff(GearItem weapon)
	{
		String category = weapon == null ? "" : weapon.getCategory().toLowerCase(Locale.ROOT);
		String name = label(weapon);
		return category.contains("powered staff")
			|| name.contains("trident")
			|| name.contains("thammaron")
			|| name.contains("accursed sceptre")
			|| name.contains("sanguinesti")
			|| name.contains("tumeken")
			|| name.contains("warped sceptre")
			|| name.contains("bone staff");
	}

	private static boolean isTzhaarWeapon(String name)
	{
		return name.contains("tzhaar-ket-em")
			|| name.contains("tzhaar-ket-om")
			|| name.contains("toktz-xil-ak")
			|| name.contains("toktz-xil-ek")
			|| name.contains("toktz-mej-tal");
	}

	private static boolean allowedByMode(OptimizationRequest request, GearItem item)
	{
		if (!item.isTradeable())
		{
			return canUseUntradeable(request, item);
		}
		boolean owned = request.getOwnedItems().owns(item.getId());
		switch (request.getCandidateMode())
		{
			case ALL_STANDARD:
				return item.getEstimatedPrice() != null || owned;
			case OWNED_ONLY:
				return owned;
			case OWNED_OR_BUDGET:
				return owned || affordable(request, item);
			case BUDGET:
			default:
				return affordable(request, item);
		}
	}

	private static boolean affordable(OptimizationRequest request, GearItem item)
	{
		if (!item.isTradeable() || item.getEstimatedPrice() == null)
		{
			return false;
		}
		return item.getPriceOrZero() <= request.getBudget();
	}

	private static boolean canUseUntradeable(OptimizationRequest request, GearItem item)
	{
		return item != null && request.isIncludeUntradeables() && !item.isTradeable() && request.getOwnedItems().owns(item.getId());
	}

	private static int budgetCost(OptimizationRequest request, GearItem item)
	{
		if (item == null || request.getOwnedItems().owns(item.getId()))
		{
			return 0;
		}
		return item.getPriceOrZero();
	}

	private static boolean withinBudget(OptimizationRequest request, int cost)
	{
		return request.getCandidateMode() == CandidateMode.ALL_STANDARD || cost <= request.getBudget();
	}

	private static List<GearItem> dedupe(List<GearItem> rows, OptimizationRequest request)
	{
		Map<String, GearItem> best = new java.util.LinkedHashMap<>();
		for (GearItem item : rows)
		{
			String key = item.getSlot() + ":" + item.getCategory() + ":" + item.getSpeed() + ":" + item.isTwoHanded()
				+ ":" + item.getOffensive().getAttackBonus("stab")
				+ ":" + item.getOffensive().getAttackBonus("slash")
				+ ":" + item.getOffensive().getAttackBonus("crush")
				+ ":" + item.getOffensive().getAttackBonus("magic")
				+ ":" + item.getOffensive().getAttackBonus("ranged")
				+ ":" + item.getBonuses().getStrength()
				+ ":" + item.getBonuses().getRangedStrength()
				+ ":" + item.getBonuses().getMagicDamage()
				+ ":" + request.getStyle();
			GearItem current = best.get(key);
			if (current == null || betterEquivalent(request, item, current))
			{
				best.put(key, item);
			}
		}
		return new ArrayList<>(best.values());
	}

	private static boolean betterEquivalent(OptimizationRequest request, GearItem candidate, GearItem current)
	{
		boolean candidateOwned = request.getOwnedItems().owns(candidate.getId());
		boolean currentOwned = request.getOwnedItems().owns(current.getId());
		if (candidateOwned != currentOwned)
		{
			return candidateOwned;
		}
		return budgetCost(request, candidate) < budgetCost(request, current);
	}

	private static String signature(Loadout loadout)
	{
		StringBuilder builder = new StringBuilder();
		for (GearSlot slot : GearSlot.values())
		{
			GearItem item = loadout.get(slot);
			builder.append(slot.name()).append('=');
			if (item != null)
			{
				builder.append(item.getId());
			}
			builder.append(';');
		}
		return builder.toString();
	}

	private static final class SearchState
	{
		private final EnumMap<GearSlot, GearItem> gear;
		private final int cost;
		private final double score;

		private SearchState(EnumMap<GearSlot, GearItem> gear, int cost)
		{
			this(gear, cost, 0.0);
		}

		private SearchState(EnumMap<GearSlot, GearItem> gear, int cost, double score)
		{
			this.gear = gear;
			this.cost = cost;
			this.score = score;
		}

		private int getCost()
		{
			return cost;
		}

		private double getScore()
		{
			return score;
		}
	}
}
