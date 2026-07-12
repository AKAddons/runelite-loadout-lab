// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.SpellStats;
import com.loadoutlab.data.StatBlock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
		SpellContext spellContext = new SpellContext(request, spellsFor(data, request));
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
			for (GearItem item : data.getGearItems(slot))
			{
				if (!item.isStandardGear() || data.isVariant(item.getId())
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
			// Collapse stat-clone families first (explorer's ring tiers, god
			// book analogs) so the owned version represents its clones and
			// the try cap is spent on genuinely different items.
			options = dedupe(options, request);
			options.sort(Comparator.comparingLong(LoadoutOptimizer::utilityScore).reversed());
			int tried = 0;
			for (GearItem item : options)
			{
				EnumMap<GearSlot, GearItem> gear = new EnumMap<>(current.getLoadout().getGear());
				gear.put(slot, item);
				Loadout trial = new Loadout(gear);
				// Risk rejections must not consume tries: in risk mode the
				// top utility items are exactly the expensive gear the
				// budget rejects, and the free tier (god books, diary
				// boots) sits below them - it starved behind the cap.
				if (request.isRiskConstrained()
					&& (PvpRisk.riskGp(trial, null, request.getMaxTradeables())
							> request.getRiskBudgetGp()
						|| PvpRisk.risksRebuild(trial, null, request.getMaxTradeables())))
				{
					continue;
				}
				if (++tried > 12)
				{
					break;
				}
				DpsResult candidate = bestSpellResult(request, trial, spellContext);
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

	/**
	 * Mandatory utility (Zulrah recoil): when the fight requires an item
	 * class the dps ranking would never pick, swap in the least-costly
	 * satisfying option - ring slot vs feet slot compared on the full
	 * set's dps. Runs after the main search; when nothing satisfying is
	 * accessible the set is returned unchanged (the monster note warns).
	 */
	public DpsResult ensureRequiredUtility(LoadoutData data, OptimizationRequest request, DpsResult result)
	{
		if (result == null || !RequiredUtility.requiresRecoil(request.getMonster())
			|| RequiredUtility.hasRecoil(result.getLoadout()))
		{
			return result;
		}
		SpellContext spellContext = new SpellContext(request, spellsFor(data, request));
		DpsResult best = null;
		for (GearItem candidate : RequiredUtility.recoilCandidates(data, request))
		{
			EnumMap<GearSlot, GearItem> gear = new EnumMap<>(result.getLoadout().getGear());
			gear.put(candidate.getSlot(), candidate);
			DpsResult swapped = bestSpellResult(request, new Loadout(gear), spellContext);
			if (swapped != null && (best == null || swapped.getDps() > best.getDps()))
			{
				best = swapped.withPurchaseCost(result.getPurchaseCost() + budgetCost(request, candidate));
			}
		}
		return best == null ? result : best;
	}

	/**
	 * D-4: the beam's objective. Pure dps by default; with a defense
	 * weight the score becomes dps - weight * incoming dps, so the beam
	 * walks the offense/defense frontier instead of its endpoint.
	 */
	private static double weightedScore(OptimizationRequest request, DpsResult score, Loadout loadout,
		IncomingDpsCalculator.Prepared incoming)
	{
		double value = score.getDps() + score.getAttackRoll() * 1e-9;
		if (request.getDefenseWeight() > 0)
		{
			value -= request.getDefenseWeight() * incoming.totalDps(loadout);
		}
		return value;
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
			return Collections.emptyList();
		}
		if (request.getStyle() == CombatStyle.ANY)
		{
			return optimizeAny(data, request);
		}
		return optimize(data, request, preparePools(data, request));
	}

	/**
	 * The candidate pools one optimize run searches: legal spells, the
	 * weapon pool, the non-weapon slot pools, and (lazily, per weapon) the
	 * compatible-ammo pools. Pools depend on the request's filters but NOT
	 * on the defense weight's magnitude - candidates() only branches on
	 * weight > 0 - so the D-4 sweep builds them once and reuses them
	 * across its weighted runs. Confined to the optimizer worker.
	 */
	public static final class CandidatePools
	{
		private final List<SpellStats> spells;
		private final List<GearItem> weapons;
		private final Map<GearSlot, List<GearItem>> slotCandidates;
		private final Map<GearItem, List<GearItem>> ammoByWeapon = new IdentityHashMap<>();

		private CandidatePools(List<SpellStats> spells, List<GearItem> weapons,
			Map<GearSlot, List<GearItem>> slotCandidates)
		{
			this.spells = spells;
			this.weapons = weapons;
			this.slotCandidates = slotCandidates;
		}
	}

	/**
	 * Build the pools optimize(data, request) would build. Only useful with
	 * the pool-taking optimize overload; the request passed there must
	 * differ from this one at most in defenseWeight, and both must have
	 * defenseWeight on the same side of zero.
	 */
	public CandidatePools preparePools(LoadoutData data, OptimizationRequest request)
	{
		Map<GearSlot, List<GearItem>> slotCandidates = new EnumMap<>(GearSlot.class);
		for (GearSlot slot : NON_WEAPON_SLOTS)
		{
			if (slot != GearSlot.AMMO)
			{
				// Ammo is weapon-scoped (see the top-N comment below) and
				// pooled lazily per weapon.
				slotCandidates.put(slot, candidates(data, request, slot, SLOT_LIMIT, null));
			}
		}
		return new CandidatePools(
			spellsFor(data, request),
			candidates(data, request, GearSlot.WEAPON, WEAPON_LIMIT, null),
			slotCandidates);
	}

	/** optimize() over prebuilt pools - see preparePools for the contract. */
	public List<DpsResult> optimize(LoadoutData data, OptimizationRequest request, CandidatePools pools)
	{
		if (request.getMonster() == null || request.getStyle() == null || request.getLevels() == null)
		{
			return Collections.emptyList();
		}
		List<SpellStats> spells = pools.spells;
		List<GearItem> weapons = pools.weapons;
		SpellContext spellContext = new SpellContext(request, spells);

		List<DpsResult> results = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		boolean dragonShield = DragonfireRules.shieldRequired(request);
		// The monster-side incoming constants for the D-4 beam objective,
		// hoisted once per optimize (call-scoped, worker-thread confined).
		IncomingDpsCalculator.Prepared incoming = request.getDefenseWeight() > 0
			? IncomingDpsCalculator.prepare(request.getMonster(),
				request.getLevels().getDefence(), request.getLevels().getMagic())
			: null;
		for (GearItem weapon : weapons)
		{
			// Dragonfire without a potion: the shield slot is spoken for,
			// so two-handed weapons cannot provide protection.
			if (dragonShield && weapon.isTwoHanded())
			{
				continue;
			}
			// The ammo top-N must be cut AFTER weapon compatibility: bolts
			// and javelins out-score every arrow on raw ranged strength, so
			// a global cut starves arrow weapons of usable ammo entirely.
			// Cached per weapon so the D-4 sweep does not rebuild it.
			List<GearItem> ammoCandidates = pools.ammoByWeapon.computeIfAbsent(weapon,
				w -> candidates(data, request, GearSlot.AMMO, SLOT_LIMIT, w));
			List<SearchState> states = new ArrayList<>();
			EnumMap<GearSlot, GearItem> baseGear = new EnumMap<>(GearSlot.class);
			baseGear.put(GearSlot.WEAPON, weapon);
			states.add(new SearchState(baseGear, budgetCost(request, weapon)));

			for (GearSlot slot : NON_WEAPON_SLOTS)
			{
				List<SearchState> next = new ArrayList<>();
				List<GearItem> candidates = candidatesForSlotWithWeapon(
					slot == GearSlot.AMMO ? ammoCandidates : pools.slotCandidates.get(slot), weapon, slot);
				if (dragonShield && slot == GearSlot.SHIELD)
				{
					candidates = protectiveShieldsOnly(candidates);
					if (candidates.isEmpty())
					{
						break; // no owned protection - this weapon line dies
					}
				}
				for (SearchState state : states)
				{
					for (GearItem item : candidates)
					{
						// Saturating: a "max" budget plus a full endgame set
						// can push an int sum past overflow.
						int cost = (int) Math.min((long) state.cost + budgetCost(request, item), Integer.MAX_VALUE);
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
							riskGp = PvpRisk.riskGp(loadout, null, request.getMaxTradeables());
							// A rebuild-burdened item (salve line, imbued
							// gear) may never ride UNPROTECTED in a low-risk
							// set, no matter the cap (field request).
							if (riskGp > request.getRiskBudgetGp()
								|| PvpRisk.risksRebuild(loadout, null, request.getMaxTradeables()))
							{
								continue;
							}
						}
						DpsResult score = bestSpellResult(request, loadout, spellContext);
						if (score == null)
						{
							continue;
						}
						// Tiny attack-roll nudge: vs guaranteed-hit monsters
						// (tormented demons) accuracy gear ties on DPS, and a
						// pure cost tie-break picked snakeskin boots over
						// pegasians. Never outweighs a real DPS difference.
						next.add(new SearchState(gear, cost,
							weightedScore(request, score, loadout, incoming), riskGp));
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
				DpsResult scored = bestSpellResult(request, loadout, spellContext);
				if (scored != null)
				{
					results.add(scored.withPurchaseCost(state.cost));
				}
			}
		}

		Map<DpsResult, Long> riskByResult = new IdentityHashMap<>();
		if (request.isRiskConstrained())
		{
			for (DpsResult result : results)
			{
				riskByResult.put(result,
					PvpRisk.riskGp(result.getLoadout(), null, request.getMaxTradeables()));
			}
		}
		Map<DpsResult, Double> weighted = new IdentityHashMap<>();
		if (request.getDefenseWeight() > 0)
		{
			for (DpsResult result : results)
			{
				weighted.put(result, weightedScore(request, result, result.getLoadout(), incoming));
			}
		}
		results.sort(Comparator.comparingDouble(
				(DpsResult r) -> weighted.getOrDefault(r, r.getDps())).reversed()
			.thenComparing(Comparator.comparingLong(DpsResult::getAttackRoll).reversed())
			.thenComparingLong(r -> riskByResult.getOrDefault(r, 0L))
			.thenComparingInt(DpsResult::getPurchaseCost));
		return results.size() > request.getResultLimit() ? new ArrayList<>(results.subList(0, request.getResultLimit())) : results;
	}

	/**
	 * Per-optimize spell scratch: the withSpell request per spell (they do
	 * not vary per candidate set) and, per weapon, which spells are legal -
	 * spell legality depends only on the weapon and the monster, and the
	 * beam re-asks it for thousands of armour combinations around the same
	 * weapon. Created and confined per optimize/fill call (the worker
	 * thread), never stored on the optimizer.
	 */
	private static final class SpellContext
	{
		private final List<SpellStats> spells;
		private final OptimizationRequest[] spellRequests;
		private final Map<GearItem, boolean[]> allowedByWeapon = new IdentityHashMap<>();

		private SpellContext(OptimizationRequest request, List<SpellStats> spells)
		{
			this.spells = spells;
			boolean magic = request.getStyle() == CombatStyle.MAGIC && request.isAutoSpell();
			this.spellRequests = new OptimizationRequest[magic ? spells.size() : 0];
			for (int i = 0; i < spellRequests.length; i++)
			{
				spellRequests[i] = request.withSpell(spells.get(i));
			}
		}

		private boolean[] allowedFor(OptimizationRequest request, Loadout loadout)
		{
			GearItem weapon = loadout.getWeapon();
			boolean[] allowed = allowedByWeapon.get(weapon);
			if (allowed == null)
			{
				allowed = new boolean[spells.size()];
				for (int i = 0; i < allowed.length; i++)
				{
					allowed[i] = spellAllowed(request, loadout, spells.get(i));
				}
				allowedByWeapon.put(weapon, allowed);
			}
			return allowed;
		}
	}

	private DpsResult bestSpellResult(OptimizationRequest request, Loadout loadout, SpellContext context)
	{
		if (request.getStyle() != CombatStyle.MAGIC || !request.isAutoSpell())
		{
			return calculator.calculate(request, loadout);
		}
		DpsResult best = null;
		boolean poweredStaff = isPoweredStaff(loadout.getWeapon());
		if (poweredStaff)
		{
			best = DpsResult.better(best, calculator.calculate(request, loadout));
		}
		if (!poweredStaff)
		{
			boolean[] allowed = context.allowedFor(request, loadout);
			for (int i = 0; i < allowed.length; i++)
			{
				if (allowed[i])
				{
					best = DpsResult.better(best, calculator.calculate(context.spellRequests[i], loadout));
				}
			}
		}
		// No legal spell for this staff/book: no result - a spell-less
		// 'cast' has max hit 0 and produced garbage 0.04-dps rows.
		return best;
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
			return Collections.emptyList();
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
		// Dragonfire gear mode: protective shields must reach the pool even
		// though most have zero offensive stats (the score filter and the
		// stat-dedupe would silently drop the anti-dragon shield).
		boolean needProtectiveShield = slot == GearSlot.SHIELD && DragonfireRules.shieldRequired(request);
		List<GearItem> protectives = new ArrayList<>();
		List<GearItem> rows = new ArrayList<>();
		for (GearItem item : data.getGearItems(slot))
		{
			if (slot == GearSlot.AMMO && forWeapon != null && !RangedAmmo.compatible(item, forWeapon))
			{
				continue;
			}
			if (request.isExcluded(item.getId()))
			{
				continue;
			}
			if (!item.isStandardGear())
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
			boolean protective = needProtectiveShield && DragonfireRules.isProtectiveShield(item);
			// Defense-weighted runs (Tanky/Balanced) must SEE pure-defense
			// gear - ring of suffering, guardian boots, tank shields score
			// zero offense and would never enter the pool otherwise.
			boolean defensiveCandidate = request.getDefenseWeight() > 0
				&& slot != GearSlot.WEAPON && utilityScore(item) > 0;
			if (slot != GearSlot.WEAPON && !protective && !defensiveCandidate
				&& candidateScore(request, item) <= 0)
			{
				continue;
			}
			if (!allowedByMode(request, item))
			{
				continue;
			}
			if (protective)
			{
				protectives.add(item);
				continue;
			}
			rows.add(item);
		}

		rows.sort(Comparator.comparingDouble((GearItem item) -> candidateScore(request, item)).reversed().thenComparingInt(GearItem::getPriceOrZero));
		rows = dedupe(rows, request);
		if (rows.size() > limit)
		{
			// Defense-weighted runs keep a second tier: the best defensive
			// items by utility, unioned with the offensive top-N, so the
			// beam can genuinely trade damage for safety.
			List<GearItem> cut = new ArrayList<>(rows.subList(0, limit));
			if (request.getDefenseWeight() > 0 && slot != GearSlot.WEAPON)
			{
				List<GearItem> defensive = new ArrayList<>(rows.subList(limit, rows.size()));
				defensive.sort(Comparator.comparingLong(LoadoutOptimizer::utilityScore).reversed());
				for (GearItem item : defensive.subList(0, Math.min(8, defensive.size())))
				{
					if (utilityScore(item) > 0)
					{
						cut.add(item);
					}
				}
			}
			rows = cut;
		}
		rows.addAll(protectives);
		if (slot != GearSlot.WEAPON)
		{
			rows.add(0, null);
		}
		return rows;
	}

	/** Dragonfire gear mode: the shield MUST protect (no empty slot). */
	private static List<GearItem> protectiveShieldsOnly(List<GearItem> candidates)
	{
		List<GearItem> result = new ArrayList<>();
		for (GearItem item : candidates)
		{
			if (DragonfireRules.isProtectiveShield(item))
			{
				result.add(item);
			}
		}
		return result;
	}

	private static List<GearItem> candidatesForSlotWithWeapon(List<GearItem> candidates, GearItem weapon, GearSlot slot)
	{
		if (slot == GearSlot.SHIELD && weapon != null && weapon.isTwoHanded())
		{
			return Collections.singletonList(null);
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
			return Collections.singletonList(null);
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
				// Tiered: the salve family stat-ties at zero raw stats, so
				// dedupe keeps the first of equals - score the stronger
				// enchant/imbue higher so the survivor is the variant the
				// DPS chains actually reward ((ei) > (i)/(e) > base). Field-
				// found: the BASE salve was shadowing the (ei) in game-best
				// pools, so ranged/magic "salve" suggestions did nothing.
				score += name.contains("(ei)") ? 5_300.0
					: name.contains("(i)") || name.contains("salve amulet (e)") ? 5_150.0
					: 5_000.0;
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
			// Wilderness/revenant conditionals: their raw stats undersell
			// them (the +50% passive and the incoming-nullify live in the
			// DPS models), so without a boost the pool cut or the zero-score
			// prune removes them before they are ever evaluated.
			if (com.loadoutlab.data.WildernessMonsters.isWilderness(request.getMonster())
				&& !badVersion(item) && isWildernessWeapon(name))
			{
				score += 6_000.0;
			}
			if (isRevenantMonster(request) && name.contains("amulet of avarice"))
			{
				score += 5_000.0;
			}
			if (isRevenantMonster(request) && name.contains("bracelet of ethereum")
				&& !badVersion(item))
			{
				score += 4_000.0;
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

	/** Test seam: RevenantPoolTest pins the conditional pool boosts. */
	static double candidateScoreForTest(OptimizationRequest request, GearItem item)
	{
		return candidateScore(request, item);
	}

	private static boolean isRevenantMonster(OptimizationRequest request)
	{
		return request.getMonster() != null && request.getMonster().getName()
			.toLowerCase(java.util.Locale.ROOT).startsWith("revenant");
	}

	/** The six wilderness weapons whose charged +50% passive the DPS
	 * model grants (DpsCalculator.revWeaponBuff). */
	private static boolean isWildernessWeapon(String name)
	{
		return name.contains("craw's bow") || name.contains("webweaver bow")
			|| name.contains("ursine chainmace") || name.contains("viggora's chainmace")
			|| name.contains("thammaron's sceptre") || name.contains("accursed sceptre");
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
		return item == null ? "" : item.labelLower();
	}

	private static boolean isPoweredStaff(GearItem weapon)
	{
		return DpsCalculator.isPoweredStaff(weapon);
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
		boolean owned = request.getOwnedItems().owns(item.getId())
			|| request.isDream(item.getId());
		switch (request.getCandidateMode())
		{
			case ALL_STANDARD:
				return item.getEstimatedPrice() != null || owned;
			case OWNED_ONLY:
				return owned;
			case OWNED_OR_BUDGET:
				// The upgrade budget is the "show me obtainable gear" switch:
				// quest rewards are obtainable too - for effort, not coins.
				return owned || affordable(request, item)
					|| QuestRewardItems.isQuestReward(item);
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
		// Exception: quest rewards (mostly untradeable) can be earned, so the
		// upgrade budget - the "show me obtainable gear" switch - admits them.
		return item != null && request.isIncludeUntradeables() && !item.isTradeable()
			&& (request.getCandidateMode() == CandidateMode.ALL_STANDARD
				|| request.getOwnedItems().owns(item.getId())
				|| request.isDream(item.getId())
				|| (request.getCandidateMode() == CandidateMode.OWNED_OR_BUDGET
					&& QuestRewardItems.isQuestReward(item)));
	}

	private static int budgetCost(OptimizationRequest request, GearItem item)
	{
		// Dream items are pretend-owned: free against the upgrade budget
		// (the panel prices the dream separately). Quest rewards are free
		// too when the budget admitted them - they cost effort, not gp
		// (the panel labels them with their source quest instead).
		if (item == null || request.getOwnedItems().owns(item.getId())
			|| request.isDream(item.getId())
			|| (request.getCandidateMode() == CandidateMode.OWNED_OR_BUDGET
				&& QuestRewardItems.isQuestReward(item)))
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
		Map<StatKey, GearItem> best = new LinkedHashMap<>();
		for (GearItem item : rows)
		{
			StatKey key = new StatKey(item);
			GearItem current = best.get(key);
			if (current == null || betterEquivalent(request, item, current))
			{
				best.put(key, item);
			}
		}
		return new ArrayList<>(best.values());
	}

	/**
	 * The stat-identity of a gear item for dedupe(): two items with equal
	 * keys are interchangeable in a slot. Slot and style are deliberately
	 * absent - every dedupe() call operates on rows of ONE slot under ONE
	 * request style, so they cannot distinguish anything within a call.
	 * Prayer and defence belong in the key: without them all zero-offense
	 * blessings collapsed and the tradeable preference ate Rada's
	 * blessing 4 (+2 prayer) in favor of a +1 tradeable blessing.
	 */
	private static final class StatKey
	{
		private final String category;
		private final boolean twoHanded;
		private final int[] stats;
		private final int hash;

		private StatKey(GearItem item)
		{
			this.category = item.getCategory();
			this.twoHanded = item.isTwoHanded();
			this.stats = new int[]{
				item.getSpeed(),
				item.getOffensive().getStab(),
				item.getOffensive().getSlash(),
				item.getOffensive().getCrush(),
				item.getOffensive().getMagic(),
				item.getOffensive().getRanged(),
				item.getBonuses().getStrength(),
				item.getBonuses().getRangedStrength(),
				item.getBonuses().getMagicDamage(),
				item.getBonuses().getPrayer(),
				item.getDefensive().getStab(),
				item.getDefensive().getSlash(),
				item.getDefensive().getCrush(),
				item.getDefensive().getMagic(),
				item.getDefensive().getRanged(),
			};
			this.hash = (category.hashCode() * 31 + (twoHanded ? 1 : 0)) * 31
				+ java.util.Arrays.hashCode(stats);
		}

		@Override
		public boolean equals(Object other)
		{
			if (!(other instanceof StatKey))
			{
				return false;
			}
			StatKey that = (StatKey) other;
			return hash == that.hash && twoHanded == that.twoHanded
				&& category.equals(that.category) && java.util.Arrays.equals(stats, that.stats);
		}

		@Override
		public int hashCode()
		{
			return hash;
		}
	}

	private static boolean betterEquivalent(OptimizationRequest request, GearItem candidate, GearItem current)
	{
		boolean candidateOwned = request.getOwnedItems().owns(candidate.getId());
		boolean currentOwned = request.getOwnedItems().owns(current.getId());
		if (candidateOwned != currentOwned)
		{
			return candidateOwned;
		}
		// Between versions of the SAME item, version state breaks the tie
		// BEFORE tradeability: the tradeable Uncharged twin of a wilderness
		// weapon was shadowing the Charged one, and only Charged fires the
		// +50% passive (field report: craw's bow missing at revenants).
		// Cross-item ties (uncharged glory vs amulet of the damned) must
		// NOT use this - there tradeability/death behavior decides.
		if (candidate.getNameLower().equals(current.getNameLower()))
		{
			boolean candidateBad = badVersion(candidate);
			if (candidateBad != badVersion(current))
			{
				return !candidateBad;
			}
			// Poison tiers stat-tie but are NOT equal in game: the venom
			// is free damage the model does not price. Without this the
			// tie fell to budgetCost and the CHEAPEST (unpoisoned) tier
			// won every dagger/knife family collapse.
			if (candidate.poisonTier() != current.poisonTier())
			{
				return candidate.poisonTier() > current.poisonTier();
			}
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
		String version = item.getVersionLower();
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
