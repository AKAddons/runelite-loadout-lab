// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.SpellStats;
import com.loadoutlab.data.StatBlock;
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
	private static final int BEAM_WIDTH = 96;
	/**
	 * Beam evaluation order: high-impact slots first so pruning is informed,
	 * and BODY immediately before LEGS so paired set bonuses (crystal armour
	 * with the bofa) compound before the cut - with them apart, a lone
	 * crystal body ranked below raw-stat bodies and was pruned before the
	 * legs could complete the set (the slayer-helm-on regression).
	 */
	private static final GearSlot[] NON_WEAPON_SLOTS = {
		GearSlot.AMMO,
		GearSlot.BODY,
		GearSlot.LEGS,
		GearSlot.HEAD,
		GearSlot.NECK,
		GearSlot.HANDS,
		GearSlot.SHIELD,
		GearSlot.CAPE,
		GearSlot.FEET,
		GearSlot.RING
	};

	private final DpsCalculator calculator = new DpsCalculator();

	/**
	 * Fill DPS-neutral empty slots: when no item can add damage, prefer
	 * prayer bonus, then total defensive bonuses. A candidate survives only
	 * if the recomputed DPS did not drop (negative style bonuses can cost
	 * accuracy). Candidates respect the request's owned/budget mode - so a
	 * best-owned set only fills from the bank, the game-best set from
	 * everything.
	 */
	public DpsResult fillDpsNeutralSlots(LoadoutData data, OptimizationRequest request, DpsResult result)
	{
		if (result == null)
		{
			return null;
		}
		List<SpellStats> spells = spellsFor(data, request);
		DpsResult current = result;
		for (GearSlot slot : GearSlot.values())
		{
			if (slot == GearSlot.WEAPON || current.getLoadout().get(slot) != null)
			{
				continue;
			}
			GearItem weapon = current.getLoadout().getWeapon();
			if (slot == GearSlot.SHIELD && weapon != null && weapon.isTwoHanded())
			{
				continue;
			}
			List<GearItem> options = new ArrayList<>();
			for (GearItem item : data.getGearItems())
			{
				if (item.getSlot() != slot || !item.isStandardGear() || data.isVariant(item.getId())
					|| request.isExcluded(item.getId())
					|| utilityScore(item) <= 0
					|| !request.getRequirementProfile().canEquip(item.getRequirements())
					|| !allowedByMode(request, item)
					|| (slot == GearSlot.AMMO && !RangedAmmo.compatible(item, weapon)))
				{
					continue;
				}
				options.add(item);
			}
			options.sort(Comparator.comparingLong(LoadoutOptimizer::utilityScore).reversed());
			int tried = 0;
			for (GearItem item : options)
			{
				if (++tried > 12)
				{
					break;
				}
				EnumMap<GearSlot, GearItem> gear = new EnumMap<>(current.getLoadout().getGear());
				gear.put(slot, item);
				Loadout trial = new Loadout(gear);
				if (request.isRiskConstrained()
					&& PvpRisk.assess(trial, null, request.getMaxTradeables()).riskGp
						> OptimizationRequest.RISK_BUDGET_GP)
				{
					continue;
				}
				DpsResult candidate = bestSpellResult(request, trial, spells);
				if (candidate != null && candidate.getDps() >= current.getDps() - 1e-9)
				{
					current = candidate.withPurchaseCost(
						current.getPurchaseCost() + budgetCost(request, item));
					break;
				}
			}
		}
		return current;
	}

	/** Prayer first, then the sum of defensive bonuses. */
	private static long utilityScore(GearItem item)
	{
		StatBlock defensive = item.getDefensive();
		long defenceSum = defensive.getStab() + defensive.getSlash() + defensive.getCrush()
			+ defensive.getMagic() + defensive.getRanged();
		return item.getBonuses().getPrayer() * 1000L + defenceSum;
	}

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
		List<GearItem> weapons = candidates(data, request, GearSlot.WEAPON, WEAPON_LIMIT, null);
		Map<GearSlot, List<GearItem>> slotCandidates = new EnumMap<>(GearSlot.class);
		for (GearSlot slot : NON_WEAPON_SLOTS)
		{
			slotCandidates.put(slot, candidates(data, request, slot, SLOT_LIMIT, null));
		}

		List<DpsResult> results = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (GearItem weapon : weapons)
		{
			// The ammo top-N must be cut AFTER weapon compatibility: bolts
			// and javelins out-score every arrow on raw ranged strength, so
			// a global cut starves arrow weapons of usable ammo entirely.
			slotCandidates.put(GearSlot.AMMO, candidates(data, request, GearSlot.AMMO, SLOT_LIMIT, weapon));
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
						// Wilderness risk budget: the kept 3-4 are immune;
						// everything else may drop, and its TOTAL value must
						// stay within budget. Monotone (adding items never
						// lowers risk), so pruning partial states is safe.
						long riskGp = 0;
						if (request.isRiskConstrained())
						{
							riskGp = PvpRisk.assess(loadout, null, request.getMaxTradeables()).riskGp;
							if (riskGp > OptimizationRequest.RISK_BUDGET_GP)
							{
								continue;
							}
						}
						DpsResult score = bestSpellResult(request, loadout, spells);
						if (score == null)
						{
							continue;
						}
						// Tiny attack-roll nudge: vs guaranteed-hit monsters
						// (tormented demons) accuracy gear ties on DPS, and a
						// pure cost tie-break picked snakeskin boots over
						// pegasians. Never outweighs a real DPS difference.
						next.add(new SearchState(gear, cost, score.getDps() + score.getAttackRoll() * 1e-9, riskGp));
					}
				}
				// On DPS ties prefer less risk (an untradeable that crumbles
				// on death must lose to a glory that rides a kept slot),
				// then lower purchase cost.
				next.sort(Comparator.comparingDouble(SearchState::getScore).reversed()
					.thenComparingLong(SearchState::getRiskGp)
					.thenComparingInt(SearchState::getCost));
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
				DpsResult scored = bestSpellResult(request, loadout, spells);
				if (scored != null)
				{
					results.add(scored.withPurchaseCost(state.cost));
				}
			}
		}

		Map<DpsResult, Long> riskByResult = new java.util.IdentityHashMap<>();
		if (request.isRiskConstrained())
		{
			for (DpsResult result : results)
			{
				riskByResult.put(result,
					PvpRisk.assess(result.getLoadout(), null, request.getMaxTradeables()).riskGp);
			}
		}
		results.sort(Comparator.comparingDouble(DpsResult::getDps).reversed()
			.thenComparing(Comparator.comparingLong(DpsResult::getAttackRoll).reversed())
			.thenComparingLong(r -> riskByResult.getOrDefault(r, 0L))
			.thenComparingInt(DpsResult::getPurchaseCost));
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
		// No legal spell for this staff/book: no result - a spell-less
		// 'cast' has max hit 0 and produced garbage 0.04-dps rows.
		return best;
	}

	private static DpsResult best(DpsResult first, DpsResult second)
	{
		if (second == null)
		{
			return first;
		}
		if (first == null || second.getDps() > first.getDps())
		{
			return second;
		}
		return first;
	}

	private static List<SpellStats> spellsFor(LoadoutData data, OptimizationRequest request)
	{
		List<SpellStats> all = spellsForUnfiltered(data, request);
		if (request.getSpellbookLock().isEmpty())
		{
			return all;
		}
		List<SpellStats> locked = new ArrayList<>();
		for (SpellStats spell : all)
		{
			if (request.getSpellbookLock().equalsIgnoreCase(spell.getSpellbook()))
			{
				locked.add(spell);
			}
		}
		return locked;
	}

	private static List<SpellStats> spellsForUnfiltered(LoadoutData data, OptimizationRequest request)
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

	private List<GearItem> candidates(LoadoutData data, OptimizationRequest request, GearSlot slot, int limit, GearItem forWeapon)
	{
		List<GearItem> rows = new ArrayList<>();
		for (GearItem item : data.getGearItems())
		{
			if (slot == GearSlot.AMMO && forWeapon != null && !RangedAmmo.compatible(item, forWeapon))
			{
				continue;
			}
			if (request.isExcluded(item.getId()))
			{
				continue;
			}
			if (item.getSlot() != slot || !item.isStandardGear())
			{
				continue;
			}
			// Ornament/locked/degraded variants: identical stats to the base
			// item, so suggesting them is noise - the base stands in, and
			// canonicalized ownership credits owned variants to it.
			if (data.isVariant(item.getId()))
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
			// Must come BEFORE the top-N cut: vyre weapons and halberds never
			// win generic rough-score ranking, but vs tier-3 vampyres / flying
			// monsters they are the only weapons that can deal damage at all.
			if (slot == GearSlot.WEAPON && !VampyreRules.canDamage(request.getMonster(), item))
			{
				continue;
			}
			if (slot == GearSlot.WEAPON && !FlyingRules.canReach(request.getMonster(), request.getStyle(), item))
			{
				continue;
			}
			if (slot == GearSlot.WEAPON && !RatBoneRules.canUse(request.getMonster(), item))
			{
				continue;
			}
			if (slot == GearSlot.WEAPON && !MonsterMechanics.weaponCanEverWork(request.getMonster(), request.getStyle(), item))
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
		// Ancient Magicks can only be AUTOCAST from specific staves - the
		// upstream engine let any staff barrage, recommending illegal
		// weapon+spell pairs. (Harmonised nightmare staff notably autocasts
		// the standard book only.)
		if ("ancient".equalsIgnoreCase(spell.getSpellbook()))
		{
			return weapon.contains("ancient staff")
				|| weapon.contains("ancient sceptre")
				|| weapon.contains("kodai wand")
				|| weapon.contains("master wand")
				|| weapon.contains("blue moon spear")
				|| weapon.contains("accursed sceptre")
				|| weapon.contains("purging staff")
				|| (weapon.contains("nightmare staff") && !weapon.contains("harmonised"));
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
		// ALL_STANDARD means "everything obtainable in the game" - untradeables
		// (fire cape, void, barrows gloves...) count without being owned.
		// Every other mode is ownership/budget-scoped, and untradeables can't
		// be bought, so there they require ownership.
		return item != null && request.isIncludeUntradeables() && !item.isTradeable()
			&& (request.getCandidateMode() == CandidateMode.ALL_STANDARD
				|| request.getOwnedItems().owns(item.getId()));
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
		// Stat ties prefer the tradeable base item: untradeable stat-clones
		// (fire arrows, locked variants) read as 'cost 0' and would shadow
		// the item players actually recognize.
		if (candidate.isTradeable() != current.isTradeable())
		{
			return candidate.isTradeable();
		}
		// Destroyed-on-ANY-death items (amulet of the damned) lose stat
		// ties everywhere, not just the wilderness - their only edge,
		// enhancing barrows set effects, is not modeled yet (ENGINE-GAPS);
		// without a glory owned they still surface via the owned check.
		boolean candidateDestroyed = UntradeableDeathCosts.isDestroyedOnDeath(candidate);
		if (candidateDestroyed != UntradeableDeathCosts.isDestroyedOnDeath(current))
		{
			return !candidateDestroyed;
		}
		// Wilderness risk mode: on stat ties prefer the item with the
		// smaller unavoidable death fee.
		if (request.isRiskConstrained())
		{
			long candidateFee = alwaysDeathFee(candidate);
			long currentFee = alwaysDeathFee(current);
			if (candidateFee != currentFee)
			{
				return candidateFee < currentFee;
			}
		}
		// And prefer a normal-looking version over Broken/Locked/Uncharged
		// states (a 'Broken' Dizana's quiver was winning its stat tie).
		boolean candidateBad = badVersion(candidate);
		if (candidateBad != badVersion(current))
		{
			return !candidateBad;
		}
		return budgetCost(request, candidate) < budgetCost(request, current);
	}

	/** The per-death fee this item pays no matter what is protected. */
	private static long alwaysDeathFee(GearItem item)
	{
		if (item.isTradeable() && !UntradeableDeathCosts.isDestroyedOnDeath(item))
		{
			return 0; // priced by the kept-slot ranking instead
		}
		if (UntradeableDeathCosts.isConvertible(item))
		{
			return 0; // protectable - may ride a kept slot
		}
		return UntradeableDeathCosts.costFor(item);
	}

	private static boolean badVersion(GearItem item)
	{
		String version = item.getVersion().toLowerCase(Locale.ROOT);
		return version.contains("broken") || version.contains("locked")
			|| version.contains("uncharged") || version.contains("inactive");
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
		private final long riskGp;

		private SearchState(EnumMap<GearSlot, GearItem> gear, int cost)
		{
			this(gear, cost, 0.0, 0L);
		}

		private SearchState(EnumMap<GearSlot, GearItem> gear, int cost, double score, long riskGp)
		{
			this.gear = gear;
			this.cost = cost;
			this.score = score;
			this.riskGp = riskGp;
		}

		private int getCost()
		{
			return cost;
		}

		private double getScore()
		{
			return score;
		}

		private long getRiskGp()
		{
			return riskGp;
		}
	}
}
