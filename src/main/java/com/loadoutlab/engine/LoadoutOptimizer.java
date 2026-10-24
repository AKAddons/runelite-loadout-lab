// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import com.loadoutlab.data.*;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LoadoutOptimizer
{
	private static final int SLOT_LIMIT = 10;
	private static final int WEAPON_LIMIT = 24;
	private static final int BEAM_WIDTH = 96;
	// Risk-capped hunts reserve beam slots for the LOWEST-RISK partials:
	// score-only pruning kept only expensive high-dps lines, all of which
	// bust the cap once the late slots land, and the beam died returning
	// no set at all while a cheap compliant one existed (field bug
	// 2026-07-18: KBD magic went empty after excluding the anti-dragon
	// shield - the mystic/battlestaff line was pruned at slot two).
	private static final int RISK_RESERVE = 8;
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
	 * DPS-neutral set polish: fill empty slots AND upgrade filled ones to
	 * higher-utility stat ties - prefer prayer bonus, then total defensive
	 * bonuses. A candidate survives only if the recomputed DPS did not drop
	 * (negative style bonuses can cost accuracy). Candidates respect the
	 * request's owned/budget mode - so a best-owned set only draws from the
	 * bank, the game-best set from everything.
	 *
	 * <p>The filled-slot upgrade exists because the BEAM cannot promise the
	 * best of an exact DPS tie: its width cut breaks ties on cost, so a
	 * cheap-but-flimsy body (blood moon) could starve the Torva branch out
	 * of the search entirely (field report). Repairing the DISPLAYED set
	 * here is deterministic and cannot disturb the search.
	 */
	public DpsResult fillDpsNeutralSlots(LoadoutData data, OptimizationRequest request, DpsResult result)
	{
		if (result == null)
		{
			return null;
		}
		Pass pass = new Pass(data, request);
		// Dizana's quiver carries the arrows (field report 2026-08-07):
		// relocate them BEFORE the sweep so the freed ammo slot is filled
		// like any other dps-neutral slot. Shared with the roster shown-set
		// post-pass; the neutral guard inside verifies the move.
		DpsResult current = relocateQuiverAmmo(data, request, result);
		// Near-ties (Andrew 2026-10-20: the Mythical cape's 0.1% over the
		// fire cape): prayer and defence may cost up to 0.5% of the SET.
		double floor = result.getDps() * 0.995;
		for (GearSlot slot : GearSlot.values())
		{
			current = fillSlot(data, request, current, slot, pass, floor);
		}
		return current;
	}

	/** The per-request constants a display pass reads: the spell scratch,
	 * the risk guard, the dragonfire lock and the required-gear slots. */
	private static final class Pass
	{
		final SpellContext spells;
		final RiskGuard risk;
		final boolean dragonShield;
		final Map<GearSlot, Set<Integer>> required;

		Pass(LoadoutData data, OptimizationRequest request)
		{
			spells = new SpellContext(request, spellsFor(data, request));
			risk = RiskGuard.of(data, request);
			dragonShield = DragonfireRules.shieldRequired(request);
			required = RequiredGear.requiredIds(request.getMonster(), data, request.getWaivers());
		}
	}

	/**
	 * GWD protection, worn when it is free (ISS-01M5G9KPDQWBR014Z10F79B29T,
	 * Andrew 2026-10-21: "worn when DPS-free"): each route god the shown set
	 * does not yet cover may take a non-weapon slot with one of its items -
	 * cheapest first - when the swap keeps DPS, is legal, spends no budget
	 * and uncovers no other route god. Whatever stays uncovered is carried
	 * (carriedGodItems).
	 */
	public DpsResult wearGodItems(LoadoutData data, OptimizationRequest request, DpsResult result, List<String> gods)
	{
		if (result == null || gods.isEmpty())
		{
			return result;
		}
		Pass pass = new Pass(data, request);
		DpsResult current = result;
		for (String god : gods)
		{
			Set<String> before = GwdProtection.coveredGods(current.getLoadout());
			if (before.contains(god))
			{
				continue;
			}
			before.retainAll(gods);
			List<GearItem> options = keep(data.getGearItems(), item ->
				GwdProtection.godsOf(item).contains(god) && item.getSlot() != GearSlot.WEAPON
					&& legalProtection(data, request, item));
			options.sort(Comparator.comparingLong(GearItem::getPriceOrZero));
			for (GearItem item : options)
			{
				GearSlot slot = item.getSlot();
				GearItem weapon = current.getLoadout().getWeapon();
				GearItem worn = current.getLoadout().get(slot);
				if (request.pinnedFor(slot) != null
					|| (slot == GearSlot.SHIELD && weapon != null && weapon.isTwoHanded())
					|| (slot == GearSlot.AMMO && !RangedAmmo.compatible(item, weapon))
					|| (pass.dragonShield && slot == GearSlot.SHIELD && !DragonfireRules.isProtectiveShield(item))
					|| (pass.required.containsKey(slot) && worn != null && pass.required.get(slot).contains(worn.getId())
						&& !pass.required.get(slot).contains(item.getId()))
					|| (request.getCandidateMode() != CandidateMode.ALL_STANDARD
						&& budgetCost(request, item) > budgetCost(request, worn)))
				{
					continue;
				}
				Loadout trial = current.getLoadout().with(slot, item);
				Set<String> after = GwdProtection.coveredGods(trial);
				if (!after.containsAll(before) || !after.contains(god))
				{
					continue; // would uncover another route god
				}
				if (pass.risk != null && pass.risk.exceedsCap(trial, null))
				{
					continue;
				}
				DpsResult candidate = bestSpellResult(request, trial, pass.spells);
				if (candidate == null || candidate.getDps() < current.getDps() - 1e-9)
				{
					continue; // protection is worn only when it costs nothing
				}
				current = candidate.withPurchaseCost(current.getPurchaseCost());
				break;
			}
		}
		return current;
	}

	/**
	 * The GWD protection to CARRY (worn on the way in, swapped off for the
	 * fight): one item per route god the worn set leaves uncovered - owned
	 * (or allowed by the request's mode) first, an item covering several
	 * uncovered gods next, then the cheapest. With none allowed, the
	 * cheapest standard item is suggested so the trip is never unprotected.
	 */
	public List<GearItem> carriedGodItems(LoadoutData data, OptimizationRequest request, Loadout worn, List<String> gods)
	{
		Set<String> missing = new LinkedHashSet<>(gods);
		missing.removeAll(GwdProtection.coveredGods(worn));
		List<GearItem> carried = new ArrayList<>();
		while (!missing.isEmpty())
		{
			GearItem pick = null;
			boolean pickAllowed = false;
			int pickCover = 0;
			for (GearItem item : data.getGearItems())
			{
				Set<String> covers = new HashSet<>(GwdProtection.godsOf(item));
				covers.retainAll(missing);
				if (covers.isEmpty() || !eligible(data, request, item))
				{
					continue;
				}
				boolean allowed = allowedByMode(request, item);
				boolean better = pick == null
					|| (allowed && !pickAllowed)
					|| (allowed == pickAllowed && covers.size() > pickCover)
					|| (allowed == pickAllowed && covers.size() == pickCover
						&& item.getPriceOrZero() < pick.getPriceOrZero());
				if (better)
				{
					pick = item;
					pickAllowed = allowed;
					pickCover = covers.size();
				}
			}
			if (pick == null)
			{
				break;
			}
			carried.add(pick);
			missing.removeAll(GwdProtection.godsOf(pick));
		}
		return carried;
	}

	/**
	 * The slot ladder's raw material (ISS-01M5GE9EJSX6YWCKMSYEWKZ132): every
	 * eligible item put into ONE slot of the shown set, the rest fixed - a
	 * two-hander evicts the shield, a weapon the worn ammo does not fit
	 * brings its own best usable ammo, the dragonfire
	 * shield and required-gear locks hold. Ownership is left to the caller
	 * (any item may be a step up; only owned ones are steps down).
	 */
	public List<DpsResult> slotSwaps(LoadoutData data, OptimizationRequest request, DpsResult shown, GearSlot slot)
	{
		List<DpsResult> swaps = new ArrayList<>();
		if (shown == null)
		{
			return swaps;
		}
		Pass pass = new Pass(data, request);
		Loadout base = shown.getLoadout();
		GearItem worn = base.get(slot);
		GearItem weapon = base.getWeapon();
		for (GearItem item : data.getGearItems(slot))
		{
			if ((worn != null && item.getId() == worn.getId())
				|| !eligible(data, request, item)
				|| (slot == GearSlot.WEAPON && (!item.isWeaponFor(request.getStyle())
					|| (pass.dragonShield && item.isTwoHanded())))
				|| (slot == GearSlot.SHIELD && weapon != null && weapon.isTwoHanded())
				|| (slot == GearSlot.AMMO && !RangedAmmo.compatible(item, weapon))
				|| (pass.dragonShield && slot == GearSlot.SHIELD && !DragonfireRules.isProtectiveShield(item))
				|| (pass.required.containsKey(slot) && !pass.required.get(slot).contains(item.getId())))
			{
				continue;
			}
			Loadout swapped = base.with(slot, item);
			if (slot == GearSlot.WEAPON && item.isTwoHanded())
			{
				swapped = swapped.with(GearSlot.SHIELD, null);
			}
			// A weapon whose ammo the set does not carry (bowfa -> twisted
			// bow, twisted bow -> bowfa) comes with the best ammo the
			// request may use for it - otherwise it could never be a step
			// (Andrew 2026-10-21: "using a bowfa and it's not offering any
			// higher weapons").
			List<GearItem> ammoOptions = new ArrayList<>();
			if (slot == GearSlot.WEAPON && !RangedAmmo.compatible(base.get(GearSlot.AMMO), item))
			{
				if (RangedAmmo.compatible(null, item))
				{
					ammoOptions.add(null);
				}
				ammoOptions.addAll(candidates(data, request, GearSlot.AMMO, SLOT_LIMIT, item));
				if (ammoOptions.isEmpty())
				{
					continue;
				}
			}
			else
			{
				ammoOptions.add(base.get(GearSlot.AMMO));
			}
			// A one-hander competes as weapon + offhand against the
			// two-handers (Andrew 2026-10-22: "evaluate the weapon + shield as
			// one slot because it competes directly against the 2h options"):
			// it brings the best shield the request may use - re-picked per
			// weapon, the dragonfire and required-shield locks held.
			List<GearItem> shieldOptions = new ArrayList<>();
			boolean pairsShield = slot == GearSlot.WEAPON && !item.isTwoHanded();
			if (pairsShield)
			{
				for (GearItem shield : candidates(data, request, GearSlot.SHIELD, SLOT_LIMIT, null))
				{
					// The pool's null is "no shield" - the fallback below covers it.
					if (shield == null || (pass.dragonShield && !DragonfireRules.isProtectiveShield(shield))
						|| (pass.required.containsKey(GearSlot.SHIELD)
							&& !pass.required.get(GearSlot.SHIELD).contains(shield.getId())))
					{
						continue;
					}
					shieldOptions.add(shield);
				}
			}
			if (shieldOptions.isEmpty())
			{
				// Two-handers clear it above; other slots keep the worn one.
				shieldOptions.add(swapped.get(GearSlot.SHIELD));
			}
			DpsResult best = null;
			for (GearItem ammo : ammoOptions)
			{
				for (GearItem shield : shieldOptions)
				{
					// A fresh set per trial (the quiver's carried ammo rides
					// each copy).
					DpsResult result = bestSpellResult(request,
						swapped.with(GearSlot.AMMO, ammo).with(GearSlot.SHIELD, shield), pass.spells);
					if (result != null && (best == null || result.getDps() > best.getDps()))
					{
						best = result;
					}
				}
			}
			if (best != null)
			{
				swaps.add(best);
			}
		}
		return swaps;
	}

	private static boolean legalProtection(LoadoutData data, OptimizationRequest request, GearItem item)
	{
		return eligible(data, request, item) && allowedByMode(request, item);
	}

	/** The four checks every pool shares: standard gear, not an ornament/
	 * locked/degraded variant (the base item stands in for those), not
	 * excluded, and equippable at the request's levels and quests. */
	public static boolean eligible(LoadoutData data, OptimizationRequest request, GearItem item)
	{
		return item.isStandardGear() && !data.isVariant(item.getId())
			&& !request.isExcluded(item.getId())
			&& request.getRequirementProfile().canEquip(item.getRequirements());
	}

	/** The dps-neutral fill for ONE slot: swap in the highest-utility item
	 * that does not cost dps (or fill an empty slot), honouring budget,
	 * risk, dragonfire/required-protection locks. Extracted so the roster
	 * quiver relocation can fill just the freed ammo slot without the whole
	 * sweep (field report 2026-08-09: rosters kept the arrow in the ammo
	 * slot because the roster path never ran the sweep). */
	private DpsResult fillSlot(LoadoutData data, OptimizationRequest request,
		DpsResult current, GearSlot slot, Pass pass, double floor)
	{
		// A pinned slot is the player's explicit choice - never swapped.
		if (slot == GearSlot.WEAPON || request.pinnedFor(slot) != null)
		{
			return current;
		}
		GearItem weapon = current.getLoadout().getWeapon();
		if (slot == GearSlot.SHIELD && weapon != null && weapon.isTwoHanded())
		{
			return current;
		}
		GearItem worn = current.getLoadout().get(slot);
		// Only a strictly better utility can justify touching the slot.
		long wornUtility = worn == null ? 0 : utilityScore(worn);
		long wornDamage = damageBonus(request, worn);
		List<GearItem> options = new ArrayList<>();
		for (GearItem item : data.getGearItems(slot))
		{
			if (!eligible(data, request, item)
				|| utilityScore(item) <= wornUtility
				// Never TRADE DAMAGE for utility: a dps-neutral swap that
				// drops the style's damage bonus tank-ified a max-dps set
				// (proselyte legs displacing blood moon on a floored tie).
				// Torva-over-blood-moon still passes: it GAINS strength.
				|| damageBonus(request, item) < wornDamage
				// And never SPEND the budget on a dps-neutral upgrade swap
				// (game best has no budget - its ceiling swap is exempt).
				|| (worn != null && request.getCandidateMode() != CandidateMode.ALL_STANDARD
					&& budgetCost(request, item) > budgetCost(request, worn))
				|| !allowedByMode(request, item)
				|| (slot == GearSlot.AMMO && !RangedAmmo.compatible(item, weapon)
				&& !(current.getLoadout().getQuiverAmmo() != null
					&& RangedAmmo.passiveForQuiver(item)))
				// Dragonfire gear mode: never swap the protection away.
				|| (pass.dragonShield && slot == GearSlot.SHIELD
					&& !DragonfireRules.isProtectiveShield(item))
				// Same for a worn required-protection item (mirror
				// shield, earmuffs): only acceptable swaps may touch it.
				|| (pass.required.containsKey(slot)
					&& worn != null && pass.required.get(slot).contains(worn.getId())
					&& !pass.required.get(slot).contains(item.getId())))
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
			// Swap accounting: the replaced item's spend comes back.
			int cost = (int) Math.min(Integer.MAX_VALUE,
				(long) current.getPurchaseCost() - budgetCost(request, worn)
					+ budgetCost(request, item));
			if (!withinBudget(request, cost))
			{
				continue;
			}
			// Slot legality (field report 2026-08-15: the Eclipse Moon BiS
			// wore Scythe + Avernic defender) is settled by the guards above:
			// a shield never joins a two-handed weapon, and the weapon slot
			// is never filled. The quiver's carried arrows ride every trial
			// (with() carries them), or the recompute would lose their
			// strength and reject the swap.
			Loadout trial = current.getLoadout().with(slot, item);
			// Risk rejections must not consume tries: in risk mode the
			// top utility items are exactly the expensive gear the
			// budget rejects, and the free tier (god books, diary
			// boots) sits below them - it starved behind the cap.
			if (pass.risk != null && pass.risk.vetoes(trial, null))
			{
				continue;
			}
			if (++tried > 12)
			{
				break;
			}
			DpsResult candidate = bestSpellResult(request, trial, pass.spells);
			if (candidate == null || candidate.getDps() < Math.min(floor, current.getDps()) - 1e-9)
			{
				continue;
			}
			// A dps-NEUTRAL utility swap must not displace gear that is
			// stronger for this style (field report 2026-08-12: Virtus
			// robe top, +2 prayer, replaced Ancestral robe top on Vorkath
			// because the 1% magic damage gap vanished into max-hit
			// truncation). Utility is only free when nothing offensive is
			// given up; a strictly better dps still wins outright.
			boolean neutral = candidate.getDps() <= current.getDps() + 1e-9;
			if (neutral && worn != null
				&& worn.roughScore(request.getStyle()) > item.roughScore(request.getStyle()))
			{
				continue;
			}
			current = candidate.withPurchaseCost(cost);
			break;
		}
		return current;
	}

	/** Relocate a bow's arrows into a worn Dizana's quiver and fill the
	 * freed ammo slot with a blessing - the quiver-only slice of
	 * fillDpsNeutralSlots, for the roster shown sets (field report
	 * 2026-08-09: a roster BiS kept the arrow in the ammo slot instead of
	 * Rada's, because the roster path never ran the full sweep). Returns
	 * the input unchanged when no relocation applies, so it is safe on
	 * every shown set. */
	public DpsResult relocateQuiverAmmo(LoadoutData data, OptimizationRequest request, DpsResult result)
	{
		if (result == null || !QuiverAmmo.relocatable(result.getLoadout()))
		{
			return result;
		}
		Pass pass = new Pass(data, request);
		Loadout worn = result.getLoadout();
		Loadout relocated = worn.with(GearSlot.AMMO, null).withQuiverAmmo(worn.get(GearSlot.AMMO));
		DpsResult moved = bestSpellResult(request, relocated, pass.spells);
		if (moved == null || moved.getDps() < result.getDps() - 1e-9)
		{
			return result; // relocation must be dps-neutral, or leave it
		}
		moved = moved.withPurchaseCost(result.getPurchaseCost());
		return fillSlot(data, request, moved, GearSlot.AMMO, pass, moved.getDps());
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
			|| RequiredUtility.hasRecoil(result.getLoadout())
			// A pinned ring is the player's explicit pick - the forced
			// recoil swap must not override it.
			|| request.pinnedFor(GearSlot.RING) != null)
		{
			return result;
		}
		SpellContext spellContext = new SpellContext(request, spellsFor(data, request));
		DpsResult best = null;
		for (GearItem candidate : RequiredUtility.recoilCandidates(data, request))
		{
			// The quiver's carried arrows ride the swap (with() carries
			// them), or the recoil trial silently sheds their strength and
			// accuracy (found 2026-08-09: the roster golden halved at Zulrah
			// when the relocate-then-recoil chain dropped the quiver ammo).
			DpsResult swapped = bestSpellResult(request,
				result.getLoadout().with(candidate.getSlot(), candidate), spellContext);
			if (swapped != null && (best == null || swapped.getDps() > best.getDps()))
			{
				best = swapped.withPurchaseCost(result.getPurchaseCost() + budgetCost(request, candidate));
			}
		}
		return best == null ? result : best;
	}

	/** The beam's objective: pure dps, with a tiny attack-roll nudge. */
	/** The slots of a shown set where some game candidate beats the worn
	 * item with every other slot fixed: the gold border does not stand
	 * there (Andrew, field 2026-09-27: an occult on a Fire Bolt card,
	 * bordered because the game-best Shadow wears one). The weapon and
	 * protective requirements are never contested. */
	public Set<GearSlot> beatenSlots(LoadoutData data, OptimizationRequest own,
		OptimizationRequest game, DpsResult shown)
	{
		Set<GearSlot> beaten = EnumSet.noneOf(GearSlot.class);
		Pass pass = new Pass(data, own);
		Loadout worn = shown.getLoadout();
		DpsResult base = bestSpellResult(own, worn, pass.spells);
		for (GearSlot slot : worn.getGear().keySet())
		{
			if (base == null || slot == GearSlot.WEAPON || pass.required.containsKey(slot)
				|| slot == GearSlot.SHIELD && pass.dragonShield)
			{
				continue;
			}
			for (GearItem item : candidates(data, game, slot, SLOT_LIMIT, worn.getWeapon()))
			{
				DpsResult swapped = bestSpellResult(own, worn.with(slot, item), pass.spells);
				if (swapped != null && swapped.getDps() > base.getDps() + 1e-6)
				{
					beaten.add(slot);
					break;
				}
			}
		}
		return beaten;
	}

	/**
	 * The beam's objective. Pure dps (with a tiny attack-roll nudge) by
	 * default; with a defense weight the score becomes dps - weight *
	 * incoming dps, so the beam walks the offence/defence frontier the
	 * defensive modes sweep (restored from 491b6c0, ISS-01M5G5CZC9SK46499FACRSJ8ZT).
	 */
	private static double beamScore(OptimizationRequest request, DpsResult score, Loadout loadout,
		IncomingDpsCalculator.Prepared incoming)
	{
		double value = score.getDps() + score.getAttackRoll() * 1e-9;
		if (incoming != null)
		{
			value -= request.getDefenseWeight() * incoming.totalDps(loadout);
		}
		return value;
	}

	/** The style's damage-driving bonus (melee strength / ranged strength /
	 * magic damage) of an item's or a set's bonuses. */
	private static long damageBonus(CombatStyle style, StatBlock bonuses)
	{
		switch (style)
		{
			case RANGED:
				return bonuses.getRangedStrength();
			case MAGIC:
				return bonuses.getMagicDamage();
			case MELEE:
			default:
				return bonuses.getStrength();
		}
	}

	/** An item's damage bonus (0 for an empty slot). A DPS-neutral fill
	 * swap must never REDUCE it. */
	private static long damageBonus(OptimizationRequest request, GearItem item)
	{
		return item == null ? 0 : damageBonus(request.getStyle(), item.getBonuses());
	}

	/** Prayer first, then the sum of defensive bonuses - of an item, or of
	 * the whole set. */
	private static long utility(StatBlock bonuses, StatBlock defensive)
	{
		return bonuses.getPrayer() * 1000L + (defensive.getStab() + defensive.getSlash()
			+ defensive.getCrush() + defensive.getMagic() + defensive.getRanged());
	}

	private static long utilityScore(GearItem item)
	{
		return utility(item.getBonuses(), item.getDefensive());
	}

	/**
	 * The set's total damage bonus for the style - the FIRST DPS-tie
	 * breaker. On a floored DPS tie prefer the set that carries more raw
	 * damage bonus (the stat that almost mattered): blood moon legs (+str)
	 * over proselyte (a pure-prayer tank leg), and torva (+6 str) over blood
	 * moon (+4). Only when damage ties (helms with zero offence: slayer helm
	 * vs bare black mask) does setUtility decide on defence.
	 */
	private static long setDamageBonus(OptimizationRequest request, Loadout loadout)
	{
		return damageBonus(request.getStyle(), loadout.getBonuses());
	}

	/**
	 * utilityScore at the SET level - the SECONDARY DPS-tie breaker (after
	 * setDamageBonus). Field bug: the slayer helmet and the bare black mask
	 * tie EXACTLY on DPS (same on-task multiplier, zero offence each) AND on
	 * damage bonus (zero), so this decides on defence. Same DPS, same risk,
	 * same damage: prefer the set that protects better.
	 */
	private static long setUtility(Loadout loadout)
	{
		return utility(loadout.getBonuses(), loadout.getDefensive());
	}

	/**
	 * TERTIARY DPS-tie breaker, after setUtility: the set's damage bonuses
	 * across ALL styles. Field bug: the Avernic treads ornament variants
	 * tie exactly on the magic tab (same magic damage, same defence, zero
	 * prayer), so the (et) kit could displace the strictly-dominating
	 * (max) - data order decided. When everything the style cares about
	 * ties, prefer the set that is better everywhere else.
	 */
	private static long setCrossStyleDamage(Loadout loadout)
	{
		return loadout.getBonuses().getStrength()
			+ loadout.getBonuses().getRangedStrength()
			+ loadout.getBonuses().getMagicDamage();
	}

	public List<DpsResult> optimize(LoadoutData data, OptimizationRequest request)
	{
		if (request.getMonster() == null || request.getStyle() == null || request.getLevels() == null)
		{
			return List.of();
		}
		if (request.getStyle() == CombatStyle.ANY)
		{
			return optimizeAny(data, request);
		}
		List<DpsResult> results = optimize(data, request, preparePools(data, request));
		// The fold-in doubles the search, so it runs only where its
		// mechanism exists: the salve amulet (undead-only) is the one
		// bonus that DOMINATES the exclusive slayer-helm boost and
		// steers the beam into a helm branch it should not take. On
		// non-undead tasks the helm bonus is strictly the best head -
		// the beam cannot go backwards - and the shadow optimize was
		// pure cost (field report 2026-08-14: a task-only Sire roster
		// ground for minutes once on-task became always-on).
		return request.isOnSlayerTask() && request.getMonster() != null
			&& request.getMonster().hasAttribute("undead")
			? guaranteeTaskMonotone(data, request, results)
			: results;
	}

	/**
	 * Being on a slayer task can only ADD an option - the imbued slayer
	 * helm's accuracy bonus - so the on-task best set must never score below
	 * the off-task best (field-reported invariant). The beam is a heuristic
	 * and can break this: on an UNDEAD slayer target the salve amulet's
	 * bonus DOMINATES the exclusive slayer-helm bonus, so the helm is never
	 * actually worn in the optimum, yet its candidate boost steers the beam
	 * into a slayer-helm branch and prunes the raw-stat head the salve set
	 * wants. No beam ORDER fixes this in general (reordering just relocates
	 * the violation). The guarantee: the off-task optimum is always legal
	 * on task at the same dps, so fold the off-task search in and re-rank.
	 * Off-task sets never wear a bonus helm, so their dps is task-invariant
	 * (no recompute needed).
	 */
	private List<DpsResult> guaranteeTaskMonotone(LoadoutData data,
		OptimizationRequest request, List<DpsResult> onTask)
	{
		OptimizationRequest offRequest = request.withSlayerTask(false);
		List<DpsResult> offTask = optimize(data, offRequest, preparePools(data, offRequest));
		if (offTask.isEmpty())
		{
			return onTask;
		}
		List<DpsResult> merged = new ArrayList<>(onTask);
		Set<String> seen = new HashSet<>();
		for (DpsResult r : onTask)
		{
			seen.add(signature(r.getLoadout()));
		}
		for (DpsResult r : offTask)
		{
			if (seen.add(signature(r.getLoadout())))
			{
				merged.add(r);
			}
		}
		merged.sort(ranking(request, Map.of(), Map.of()));
		return limit(merged, request);
	}

	/** The result order: the (weighted) dps, the attack roll, less risk,
	 * then the set tie-breakers - damage bonus, utility, cross-style damage
	 * - and the purchase cost. The maps are per-result overrides (empty
	 * when the run has none). */
	private static Comparator<DpsResult> ranking(OptimizationRequest request,
		Map<DpsResult, Double> weighted, Map<DpsResult, Long> risk)
	{
		return Comparator.comparingDouble((DpsResult r) -> weighted.getOrDefault(r, r.getDps())).reversed()
			.thenComparing(Comparator.comparingLong(DpsResult::getAttackRoll).reversed())
			.thenComparingLong(r -> risk.getOrDefault(r, 0L))
			.thenComparingLong(r -> -setDamageBonus(request, r.getLoadout()))
			.thenComparingLong(r -> -setUtility(r.getLoadout()))
			.thenComparingLong(r -> -setCrossStyleDamage(r.getLoadout()))
			.thenComparingInt(DpsResult::getPurchaseCost);
	}

	/** The first resultLimit results. */
	private static List<DpsResult> limit(List<DpsResult> results, OptimizationRequest request)
	{
		return results.size() > request.getResultLimit()
			? new ArrayList<>(results.subList(0, request.getResultLimit())) : results;
	}

	/**
	 * The candidate pools one optimize run searches: legal spells, the
	 * weapon pool, the non-weapon slot pools, and (lazily, per weapon) the
	 * compatible-ammo pools. Confined to the optimizer worker.
	 */
	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	public static final class CandidatePools
	{
		private final List<SpellStats> spells;
		private final List<GearItem> weapons;
		private final Map<GearSlot, List<GearItem>> slotCandidates;
		private final Map<GearItem, List<GearItem>> ammoByWeapon = new IdentityHashMap<>();
	}

	/**
	 * Build the pools optimize(data, request) would build. Only useful with
	 * the pool-taking optimize overload, with an identical request.
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
			return List.of();
		}
		List<SpellStats> spells = pools.spells;
		List<GearItem> weapons = pools.weapons;
		SpellContext spellContext = new SpellContext(request, spells);

		List<DpsResult> results = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		boolean dragonShield = DragonfireRules.shieldRequired(request);
		// Honesty rule (audit A3.2): with no protective shield in the pool the
		// constraint cannot be satisfied - half-applying it banned every 2h
		// weapon AND still returned an empty shield, while the neutral fill
		// could not restore pure-damage items (Ultor, Avernic). All-or-nothing
		// instead: fall back to the unconstrained hunt and FLAG the results,
		// so the panel says "assumes a super antifire" rather than quietly
		// recommending a worse set that protects nothing.
		boolean antifireAssumed = false;
		if (dragonShield
			&& keep(pools.slotCandidates.get(GearSlot.SHIELD), DragonfireRules::isProtectiveShield).isEmpty())
		{
			dragonShield = false;
			antifireAssumed = true;
		}
		// Required slayer protection (grZ field request 2026-08-08: "some
		// tasks where u need to equip something"): a mandated slot - mirror
		// shield vs a basilisk's gaze, a lit bug lantern to harm harpies at
		// all - enforces exactly like the dragonfire shield: the slot's pool
		// collapses to the acceptable items and non-complying weapon lines
		// die. Same all-or-nothing honesty rule: with no acceptable item in
		// the pool (unowned on the owned side), fall back to the
		// unconstrained hunt and let the mechanics note explain the gap. A
		// pin on the slot outranks - the player's explicit choice.
		Map<GearSlot, Set<Integer>> required = new EnumMap<>(GearSlot.class);
		for (RequiredGear.Rule rule : RequiredGear.rulesFor(request.getMonster(), request.getWaivers()))
		{
			Set<Integer> acceptable = rule.ids(data);
			if (request.pinnedFor(rule.slot) == null
				&& !onlyIds(pools.slotCandidates.get(rule.slot), acceptable).isEmpty())
			{
				required.put(rule.slot, acceptable);
			}
		}
		// The monster-side incoming constants for a defense-weighted beam,
		// hoisted once per optimize (call-scoped, worker-thread confined).
		IncomingDpsCalculator.Prepared incoming = request.getDefenseWeight() > 0
			? new IncomingDpsCalculator.Prepared(request.getMonster(),
				BossIncomingOverrides.overridesFor(request.getMonster()),
				request.getLevels().getDefence(), request.getLevels().getMagic())
			: null;
		RiskGuard risk = RiskGuard.of(data, request);
		for (GearItem weapon : weapons)
		{
			// Dragonfire without a potion: the shield slot is spoken for,
			// so two-handed weapons cannot provide protection.
			if (dragonShield && weapon.isTwoHanded())
			{
				continue;
			}
			// Same for a required protective shield (basilisk gaze, the
			// harpies' lantern): a two-hander cannot comply.
			if (required.containsKey(GearSlot.SHIELD) && weapon.isTwoHanded())
			{
				continue;
			}
			// The ammo top-N must be cut AFTER weapon compatibility: bolts
			// and javelins out-score every arrow on raw ranged strength, so
			// a global cut starves arrow weapons of usable ammo entirely.
			List<GearItem> ammoCandidates = pools.ammoByWeapon.computeIfAbsent(weapon,
				w -> candidates(data, request, GearSlot.AMMO, SLOT_LIMIT, w));
			List<SearchState> states = new ArrayList<>();
			EnumMap<GearSlot, GearItem> baseGear = new EnumMap<>(GearSlot.class);
			baseGear.put(GearSlot.WEAPON, weapon);
			states.add(new SearchState(baseGear, budgetCost(request, weapon)));

			// Beam trials never reach the panel - skip the counted-bonus
			// bookkeeping until the final rescore below.
			calculator.setCollectCounted(false);
			for (GearSlot slot : NON_WEAPON_SLOTS)
			{
				List<SearchState> next = new ArrayList<>();
				List<GearItem> candidates = candidatesForSlotWithWeapon(
					slot == GearSlot.AMMO ? ammoCandidates : pools.slotCandidates.get(slot), weapon, slot);
				if (dragonShield && slot == GearSlot.SHIELD)
				{
					// Dragonfire gear mode: the shield MUST protect (no empty slot).
					candidates = keep(candidates, DragonfireRules::isProtectiveShield);
					if (candidates.isEmpty())
					{
						break; // no owned protection - this weapon line dies
					}
				}
				if (required.containsKey(slot))
				{
					candidates = onlyIds(candidates, required.get(slot));
					if (candidates.isEmpty())
					{
						break; // cannot comply on this line - it dies
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
						Loadout loadout = Loadout.adopting(gear);
						// Wilderness risk budget: the kept 3-4 are immune;
						// everything else may drop, and its TOTAL value must
						// stay within budget. Monotone (adding items never
						// lowers risk), so pruning partial states is safe.
						long riskGp = 0;
						long potentialRiskGp = 0;
						if (risk != null)
						{
							riskGp = PvpRisk.riskGp(loadout, null, risk.kept);
							potentialRiskGp = PvpRisk.riskGp(loadout, null, 0);
							if (riskGp > risk.capGp || risk.unprotected(loadout, null))
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
							beamScore(request, score, loadout, incoming), riskGp, potentialRiskGp));
					}
				}
				// On DPS ties prefer less risk (an untradeable that crumbles
				// on death must lose to a glory that rides a kept slot),
				// then lower purchase cost. Deliberately NO utility tie-break
				// HERE: reordering the beam cut changes which partials survive
				// pruning (tried - it broke "lifting the dragonfire constraint
				// never costs dps"). Utility decides at the RESULTS sort only.
				next.sort(Comparator.comparingDouble(SearchState::getScore).reversed()
					.thenComparingLong(SearchState::getRiskGp)
					.thenComparingInt(SearchState::getCost));
				states = cutBeam(next, request.isRiskConstrained());
				if (states.isEmpty())
				{
					break;
				}
			}

			// Rescore the survivors WITH counted bonuses - these are the
			// results the panel can show.
			calculator.setCollectCounted(true);
			for (SearchState state : states)
			{
				Loadout loadout = Loadout.adopting(state.gear);
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
		// A defense-weighted run ranks its results by the same weighted
		// score the beam used; a pure run keeps the plain dps order.
		Map<DpsResult, Double> weighted = new IdentityHashMap<>();
		if (incoming != null)
		{
			for (DpsResult result : results)
			{
				weighted.put(result, beamScore(request, result, result.getLoadout(), incoming));
			}
		}
		results.sort(ranking(request, weighted, riskByResult));
		if (antifireAssumed)
		{
			results.replaceAll(r -> r.withAntifireAssumed(true));
		}
		return limit(results, request);
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

	/** Spell contexts by request, for bestSpellFor's repeat callers. */
	private final Map<OptimizationRequest, SpellContext> spellContexts =
		Collections.synchronizedMap(new WeakHashMap<>());

	/** A set's DPS with the best autocast an auto-spell magic request may
	 * cast - the pick optimize() makes - on the caller's calculator. A group
	 * scored its shared sets with no spell, so a staff that needs an
	 * autocast lost to a powered staff (ISS-01M5K9KFJH7EZ0GMAKNGZ4K2JG,
	 * Andrew 2026-10-22: the trident beat his smoke staves at Barrows). */
	public DpsResult bestSpellFor(LoadoutData data, OptimizationRequest request, Loadout loadout,
		DpsCalculator calc)
	{
		if (loadout == null)
		{
			return null;
		}
		if (isPoweredStaff(loadout.getWeapon()))
		{
			return calc.calculate(request, loadout);
		}
		SpellContext context = spellContexts.computeIfAbsent(request, r -> new SpellContext(r, spellsFor(data, r)));
		boolean[] allowed;
		synchronized (context)
		{
			allowed = context.allowedFor(request, loadout);
		}
		DpsResult best = null;
		for (int i = 0; i < allowed.length; i++)
		{
			if (allowed[i])
			{
				best = DpsResult.better(best, calc.calculate(context.spellRequests[i], loadout));
			}
		}
		return best;
	}

	private DpsResult bestSpellResult(OptimizationRequest request, Loadout loadout, SpellContext context)
	{
		if (request.getStyle() != CombatStyle.MAGIC || !request.isAutoSpell())
		{
			// A pinned spell rides only weapons that can autocast it - the
			// same legality the auto path enforces. Without this a powered
			// staff priced its own built-in and outbid the legal staves
			// (field report 2026-08-09: Blood Barrage pin, Trident answer).
			if (request.getStyle() == CombatStyle.MAGIC && request.getSpell() != null
				&& !spellAllowed(request, loadout, request.getSpell()))
			{
				// A PINNED powered staff is the stronger intent: it casts
				// its built-in (Apathy, Discord 2026-09-06: the sceptre pin
				// vanished under a pinned spell).
				if (!isPoweredStaff(loadout.getWeapon()) || !request.isPinned(loadout.getWeapon().getId()))
				{
					return null;
				}
			}
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

	static List<SpellStats> spellsFor(LoadoutData data, OptimizationRequest request)
	{
		List<SpellStats> all = spellsForUnfiltered(data, request);
		// Yours-side honesty (field report 2026-08-27): never autocast a
		// spell whose blood/soul/death/wrath runes the bank does not hold.
		// Runs before the mob whitelists so a nibbler/Salarin lock cannot
		// resurrect a spell the player cannot pay for. The ceiling and the
		// upgrade-budget mode keep every spell - one prices the game, the
		// other already assumes purchases.
		if (request.getCandidateMode() == CandidateMode.OWNED_ONLY)
		{
			all = keep(all, spell -> SpellRunes
				.premiumRunesOwned(spell.getName(), request.getOwnedItems()));
		}
		// Nibblers: locked to the barrages (field decision 2026-08-06) -
		// Ice clears the trio, Blood heals off it; any other autocast is
		// the wrong advice for the mob whose role is being barraged.
		if (MonsterMechanics.isNibbler(request.getMonster()))
		{
			return keep(all, spell -> "Ice Barrage".equals(spell.getName())
				|| "Blood Barrage".equals(spell.getName()));
		}
		// Salarin: strike casts are the ONLY damage (the same whitelist
		// isImmune enforces), and the elemental prune would drop them as
		// dominated - short-circuit past it.
		if (MonsterMechanics.isSalarin(request.getMonster()))
		{
			return keep(all, MonsterMechanics::salarinDamagingSpell);
		}
		if (!request.getSpellbookLock().isEmpty())
		{
			all = keep(all, spell ->
				request.getSpellbookLock().equalsIgnoreCase(spell.getSpellbook()));
		}
		return pruneDominatedElementals(all, request);
	}

	/** The elements passing one predicate - the shape every pool filter
	 * shares. Runs per optimize or per weapon line, never in the beam. */
	private static <T> List<T> keep(List<T> all, java.util.function.Predicate<T> filter)
	{
		List<T> kept = new ArrayList<>(all.size());
		for (T element : all)
		{
			if (filter.test(element))
			{
				kept.add(element);
			}
		}
		return kept;
	}

	/**
	 * Plain elementals are interchangeable up to (effective max hit at this
	 * magic level, weakness match): the DPS chain reads nothing else from
	 * them, so for any loadout two same-class spells score identically and
	 * only the FIRST in list order can win (better() keeps the first on
	 * ties). Keeping one spell per class cuts the beam's per-trial spell
	 * loop from ~20 calculates to ~2 without changing any result. Spells
	 * with their own rules (Magic Dart, god/Iban casts, demonbane,
	 * ancients) are never pruned.
	 */
	private static List<SpellStats> pruneDominatedElementals(List<SpellStats> spells,
		OptimizationRequest request)
	{
		if (spells.isEmpty() || request.getMonster() == null)
		{
			return spells;
		}
		String weakness = request.getMonster().getWeaknessElement();
		int magicLevel = request.getLevels().getMagic();
		List<SpellStats> kept = new ArrayList<>(spells.size());
		// Dominance is per (weakness-match, twinflame-doubling) CELL, not
		// per class alone: the staff's 40% second hit applies to
		// Bolt/Blast/Wave but NOT Strike/Surge, so a lower-max Wave can
		// out-dps the Surge that "dominates" it whenever a twinflame is in
		// the pool. Field bug 2026-08-06: Wind Surge (24) pruned Wind Wave
		// (20) before evaluation, and a pinned twinflame staff was told to
		// autocast the worse spell. The prune runs request-level - it
		// cannot see the weapon - so both tier-groups survive to the
		// per-loadout evaluation, which prices the second hit correctly.
		// An amulet of air/water/earth/fire in the pool lifts one element
		// only: keep a cell per element then.
		boolean perElement = elementAmuletInPool(request);
		int[] best = new int[perElement ? 16 : 4];
		Arrays.fill(best, -1);
		for (SpellStats spell : spells)
		{
			if (!DpsCalculator.isPlainElemental(spell))
			{
				kept.add(spell);
				continue;
			}
			boolean match = spell.getElement().equals(weakness);
			int cell = (match ? 2 : 0) + (DpsCalculator.twinflameDoubles(spell) ? 1 : 0)
				+ (perElement ? 4 * ELEMENTS.indexOf(spell.getElement()) : 0);
			int effective = DpsCalculator.elementalSpellMax(spell, magicLevel);
			if (effective > best[cell])
			{
				kept.add(spell);
				best[cell] = effective;
			}
		}
		return kept;
	}

	private static final List<String> ELEMENTS = List.of("air", "water", "earth", "fire");
	/** Amulet of air, water, earth, fire (gameval ids). */
	private static final int[] ELEMENT_AMULETS = {34407, 34413, 34419, 34425};

	private static boolean elementAmuletInPool(OptimizationRequest request)
	{
		return request.getCandidateMode() != CandidateMode.OWNED_ONLY
			|| Arrays.stream(ELEMENT_AMULETS).anyMatch(request.getOwnedItems()::owns);
	}

	private static List<SpellStats> spellsForUnfiltered(LoadoutData data, OptimizationRequest request)
	{
		if (request.getStyle() != CombatStyle.MAGIC || !request.isAutoSpell())
		{
			return List.of();
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
		merged.sort(Comparator.comparingDouble(DpsResult::getDps).reversed()
			.thenComparingLong(r -> -setUtility(r.getLoadout()))
			.thenComparingLong(r -> -setCrossStyleDamage(r.getLoadout()))
			.thenComparingInt(DpsResult::getPurchaseCost));
		return limit(merged, request);
	}

	private List<GearItem> candidates(LoadoutData data, OptimizationRequest request, GearSlot slot, int limit, GearItem forWeapon)
	{
		// A pinned slot has exactly one candidate: the player's explicit
		// choice (bracelet of slaughter class - value the model cannot
		// price) bypasses exclusions, mode, scoring, and dedupe; the
		// optimizer's job becomes building the best set AROUND it. Pinned
		// ammo still respects weapon compatibility - an incompatible
		// weapon line dies honestly instead of computing nonsense.
		Integer pinnedId = request.pinnedFor(slot);
		GearItem pinned = pinnedId == null ? null : data.getGear(pinnedId);
		if (pinned != null)
		{
			return new ArrayList<>(slot == GearSlot.AMMO && forWeapon != null
				&& !RangedAmmo.compatible(pinned, forWeapon) ? List.of() : List.of(pinned));
		}
		// Dragonfire gear mode: protective shields must reach the pool even
		// though most have zero offensive stats (the score filter and the
		// stat-dedupe would silently drop the anti-dragon shield).
		boolean needProtectiveShield = slot == GearSlot.SHIELD && DragonfireRules.shieldRequired(request);
		// Required slayer protection rides the same rescue: a mirror shield
		// or earmuffs would die at the score cut, and the beam's enforcement
		// needs them present in the pool.
		Set<Integer> requiredKeep = request.pinnedFor(slot) == null
			? RequiredGear.requiredIds(request.getMonster(), data, request.getWaivers()).get(slot) : null;
		List<GearItem> protectives = new ArrayList<>();
		List<GearItem> rows = new ArrayList<>();
		for (GearItem item : data.getGearItems(slot))
		{
			if (slot == GearSlot.AMMO && forWeapon != null && !RangedAmmo.compatible(item, forWeapon))
			{
				continue;
			}
			// Ornament/locked/degraded variants: identical stats to the base
			// item, so suggesting them is noise - the base stands in, and
			// canonicalized ownership credits owned variants to it.
			if (!eligible(data, request, item))
			{
				continue;
			}
			// An uncharged/broken/locked WEAPON is never suggested for
			// ACQUISITION: when the charged version is untradeable (wilderness
			// weapons) the dead tradeable twin used to win the budget pool
			// outright, priced and scored as if it could attack (audit A3.4).
			// An OWNED uncharged twin stays: it stands in for the weapon you
			// own - charge it before the trip (the Tumeken-uncharged rule).
			// Armour keeps the dedupe tie-break instead of this filter: an
			// inactive crystal piece is still legitimately wearable.
			// The weapon bars must come BEFORE the top-N cut: vyre weapons and
			// halberds never win generic rough-score ranking, but vs tier-3
			// vampyres / flying monsters they are the only weapons that can
			// deal damage at all.
			if (slot == GearSlot.WEAPON && (!item.isWeaponFor(request.getStyle())
				|| badVersion(item) && !request.getOwnedItems().owns(item.getId()) && !request.isDream(item.getId())
				|| !MonsterMechanics.weaponUsable(request.getMonster(), request.getStyle(), item)
				|| !MonsterMechanics.weaponCanEverWork(request.getMonster(), request.getStyle(), item)))
			{
				continue;
			}
			// A charged tome scores like an empty shield (0% damage) and the
			// three share one stat key, so without this rescue the pool keeps
			// at most one and the spell-matched one is luck (rakaykhan1512,
			// Discord 2026-10-18: the tome of water lost to the ward).
			boolean protective = needProtectiveShield && DragonfireRules.isProtectiveShield(item)
				|| slot == GearSlot.SHIELD && request.getStyle() == CombatStyle.MAGIC
					&& item.getNameLower().startsWith("tome of ") && "charged".equals(item.getVersionLower());
			boolean requiredItem = requiredKeep != null && requiredKeep.contains(item.getId());
			// Defense-weighted runs must SEE pure-defence gear - ring of
			// suffering, guardian boots, tank shields score zero offence and
			// would never enter the pool otherwise.
			boolean defensiveCandidate = request.getDefenseWeight() > 0
				&& slot != GearSlot.WEAPON && utilityScore(item) > 0;
			if (slot != GearSlot.WEAPON && !protective && !requiredItem && !defensiveCandidate
				&& candidateScore(request, item) <= 0)
			{
				continue;
			}
			if (!allowedByMode(request, item))
			{
				continue;
			}
			if (protective || requiredItem)
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
			List<GearItem> cut = new ArrayList<>(rows.subList(0, limit));
			// Defense-weighted runs keep a second tier: the best defensive
			// items by utility beyond the offensive cut, so the beam can
			// genuinely trade damage for safety.
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

	/** Beam cut. Risk-capped hunts swap the tail of the score cut for the
	 * lowest-risk survivors: risk grows monotonically as slots fill, so a
	 * beam of pure score-leaders can march everyone past the cap and die
	 * even though a cheap compliant set exists. The reserve guarantees a
	 * low-risk line reaches the last slot. Ties keep score order (next is
	 * score-sorted and the sort is stable). */
	private static List<SearchState> cutBeam(List<SearchState> next, boolean riskConstrained)
	{
		if (next.size() <= BEAM_WIDTH)
		{
			return next;
		}
		if (!riskConstrained)
		{
			return new ArrayList<>(next.subList(0, BEAM_WIDTH));
		}
		// The reserve rides ON TOP of the score cut (96 + 8) rather than
		// displacing its tail: swapping out ranks 89-96 measurably cost
		// dps on lines whose winner grew from a mid-rank seed.
		List<SearchState> kept = new ArrayList<>(next.subList(0, BEAM_WIDTH));
		List<SearchState> rest = new ArrayList<>(next.subList(BEAM_WIDTH, next.size()));
		// Reserve by TOTAL droppable value, not current unprotected risk:
		// early partials all tie at riskGp 0 (everything worn so far rides
		// the kept slots), and a riskGp sort re-kept the score leaders -
		// the genuinely cheap line still starved (field follow-up
		// 2026-07-18: KBD magic offered ancient staff while the compliant
		// dragon hunter wand + cheap-robes line was beam-pruned).
		rest.sort(Comparator.comparingLong(SearchState::getPotentialRiskGp));
		kept.addAll(rest.subList(0, Math.min(RISK_RESERVE, rest.size())));
		return kept;
	}

	/** The candidates whose ids are acceptable for a required slot -
	 * nulls drop too, a mandated slot may not ride empty. */
	private static List<GearItem> onlyIds(List<GearItem> candidates, Set<Integer> ids)
	{
		return keep(candidates, item -> item != null && ids.contains(item.getId()));
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
		List<GearItem> result = keep(candidates, item -> RangedAmmo.compatible(item, weapon));
		if (result.isEmpty() && RangedAmmo.compatible(null, weapon))
		{
			return Collections.singletonList(null);
		}
		return result;
	}

	static double candidateScore(OptimizationRequest request, GearItem item)
	{
		double score = item.roughScore(request.getStyle());
		if (slayerTaskHeadCandidate(request, item))
		{
			score += 10_000.0;
		}
		String name = label(item);
		if (request.getStyle() == CombatStyle.MAGIC)
		{
			// Flat spell adds live in the DPS model, not the raw stats: the
			// gauntlets score zero here and the element amulets tie a crowd
			// of glories, so the pool cut dropped them (field report
			// 2026-09-08: simmed chaos gauntlets lost to the bracelet).
			SpellStats spell = request.getSpell();
			if (name.contains("chaos gauntlets") && (spell == null || "Bolt".equals(spell.getNameSecondWord())))
			{
				score += 2_000.0;
			}
			String element = spell == null ? "" : spell.getElement();
			if (name.equals("elemental amulet") && (spell == null || !element.isEmpty())
				|| name.startsWith("amulet of ") && (spell == null ? ELEMENTS.contains(name.substring(10)) : name.equals("amulet of " + element)))
			{
				score += 2_000.0;
			}
		}
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
			if (item.isDemonbane() && request.getMonster().hasAttribute("demon"))
			{
				score += 4_000.0;
			}
			// One-shot demonbane vs the Sire's vents lives entirely in the
			// DPS model - raw stats rank the Scorching bow under a blowpipe,
			// so without a boost the weapon cut prunes the actual answer
			// (pool lesson; field report 2026-08-05). Flag first: this runs
			// inside the pool sort's comparator.
			if (item.isDemonbane()
				&& MonsterMechanics.isRespiratorySystem(request.getMonster()))
			{
				score += 10_000.0;
			}
		}
		// Set pieces whose value lives in the DPS model, not raw stats
		// (shortlist_boosts.json): each block lifts a piece once.
		// Wilderness/revenant conditionals ride the same table: their +50%
		// passive and the incoming-nullify live in the DPS models, and so do
		// the monster-attribute rows (dragon hunter, keris, the leaf-bladed
		// battleaxe, golembane, Verac's at a prayer-immune phase - see the
		// table's notes), keyed "attr:<attribute>".
		boolean usable = !badVersion(item);
		List<String> keys = new ArrayList<>(List.of("all", request.getStyle().name(),
			usable && request.isInWilderness() ? "wilderness" : "",
			usable && isRevenantMonster(request) ? "revenant" : ""));
		if (request.getMonster() != null)
		{
			for (String attribute : request.getMonster().getAttributesLower())
			{
				keys.add("attr:" + attribute);
			}
		}
		for (String key : keys)
		{
			for (Map<String, Integer> block : SHORTLIST_BOOSTS.getOrDefault(key, List.of()))
			{
				for (Map.Entry<String, Integer> e : block.entrySet())
				{
					if (name.contains(e.getKey()))
					{
						score += e.getValue();
						break;
					}
				}
			}
		}
		return score;
	}

	/**
	 * Risk the pinned items alone carry - the wilderness cap constrains
	 * the REST of the set, never the player's explicit picks. Cheap when
	 * nothing is pinned (the overwhelmingly common case).
	 */
	public static long pinnedRiskFloor(LoadoutData data, OptimizationRequest request)
	{
		if (request.getPinnedItems().isEmpty())
		{
			return 0;
		}
		EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
		for (Map.Entry<GearSlot, Integer> entry : request.getPinnedItems().entrySet())
		{
			GearItem item = data.getGear(entry.getValue());
			if (item != null)
			{
				gear.put(entry.getKey(), item);
			}
		}
		return gear.isEmpty() ? 0
			: PvpRisk.riskGp(Loadout.adopting(gear), null, request.getMaxTradeables());
	}

	private static Set<Integer> pinnedIds(OptimizationRequest request)
	{
		return request.getPinnedItems().isEmpty()
			? Set.of()
			: new HashSet<>(request.getPinnedItems().values());
	}

	/**
	 * The request-level risk constants of a wilderness hunt, hoisted out of
	 * the trial loops (they were rebuilt - floor Loadout + a HashSet - per
	 * candidate): the kept slots, the cap (the budget raised by the pinned
	 * items' own risk floor, since the cap constrains the REST of the set),
	 * the pinned ids and the protect-only ids. Null off the Wilderness.
	 */
	public static final class RiskGuard
	{
		final int kept;
		final long capGp;
		final Set<Integer> pinned;
		final Set<Integer> protectOnly;

		private RiskGuard(LoadoutData data, OptimizationRequest request)
		{
			kept = request.getMaxTradeables();
			capGp = request.getRiskBudgetGp() + pinnedRiskFloor(data, request);
			pinned = pinnedIds(request);
			protectOnly = request.getProtectOnlyItems();
		}

		public static RiskGuard of(LoadoutData data, OptimizationRequest request)
		{
			return request.isRiskConstrained() ? new RiskGuard(data, request) : null;
		}

		/** The set (plus a carried spec weapon) would drop more than the cap. */
		public boolean exceedsCap(Loadout loadout, GearItem spec)
		{
			return PvpRisk.riskGp(loadout, spec, kept) > capGp;
		}

		/** A rebuild-burdened item (salve line, imbued gear) may never ride
		 * UNPROTECTED in a low-risk set, no matter the cap (field request) -
		 * unless the player PINNED it. */
		public boolean unprotected(Loadout loadout, GearItem spec)
		{
			return PvpRisk.risksUnprotected(loadout, spec, kept, pinned, protectOnly);
		}

		public boolean vetoes(Loadout loadout, GearItem spec)
		{
			return exceedsCap(loadout, spec) || unprotected(loadout, spec);
		}
	}

	private static boolean isRevenantMonster(OptimizationRequest request)
	{
		return request.getMonster() != null && request.getMonster().isRevenantMonster();
	}

	private static boolean slayerTaskHeadCandidate(OptimizationRequest request, GearItem item)
	{
		if (!request.isOnSlayerTask() || request.getMonster() == null || !request.getMonster().isSlayerMonster() || item == null || !item.isSlayerHead())
		{
			return false;
		}
		return request.getStyle() == CombatStyle.MELEE || item.isImbuedSlayerHead();
	}

	/** Style -> blocks of name -> shortlist boost (shortlist_boosts.json). */
	private static final Map<String, List<Map<String, Integer>>> SHORTLIST_BOOSTS = new HashMap<>();

	static
	{
		com.google.gson.JsonObject root = JsonResources.object("shortlist_boosts.json");
		for (String key : root.keySet())
		{
			if (!key.startsWith("_"))
			{
				List<Map<String, Integer>> blocks = new ArrayList<>();
				for (com.google.gson.JsonElement b : root.getAsJsonArray(key))
				{
					Map<String, Integer> block = new LinkedHashMap<>();
					b.getAsJsonObject().entrySet().forEach(e -> block.put(e.getKey(), e.getValue().getAsInt()));
					blocks.add(block);
				}
				SHORTLIST_BOOSTS.put(key, blocks);
			}
		}
	}

	/** Spell -> the weapons that cast it (spell_staves.json). */
	private static final Map<String, List<String>> SPELL_STAVES = new HashMap<>();

	static
	{
		com.google.gson.JsonObject root = JsonResources.object("spell_staves.json");
		for (String key : root.keySet())
		{
			if (!key.startsWith("_"))
			{
				List<String> names = new ArrayList<>();
				JsonResources.strings(root, key, names);
				SPELL_STAVES.put(key, names);
			}
		}
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
		List<String> staves = SPELL_STAVES.get("ancient".equalsIgnoreCase(spell.getSpellbook()) ? "ancient" : spellName);
		return staves == null || !weapon.contains("harmonised") && staves.stream().anyMatch(weapon::contains);
	}

	private static String label(GearItem item)
	{
		return item == null ? "" : item.labelLower();
	}

	private static boolean isPoweredStaff(GearItem weapon)
	{
		return DpsCalculator.isPoweredStaff(weapon);
	}

	private static boolean allowedByMode(OptimizationRequest request, GearItem item)
	{
		// THE GAUNTLET (field spec 2026-07-18): fights inside are locked
		// to the raid-crafted tiers - mainland gear never enters, and
		// ownership is irrelevant because everything is made in the run.
		String gauntlet = GauntletRules.family(request.getMonster());
		if (gauntlet != null)
		{
			return GauntletRules.allowed(gauntlet, item);
		}
		boolean owned = request.getOwnedItems().owns(item.getId())
			|| request.isDream(item.getId());
		CandidateMode mode = request.getCandidateMode();
		if (!item.isTradeable())
		{
			// ALL_STANDARD means "everything obtainable in the game" - untradeables
			// (fire cape, void, barrows gloves...) count without being owned.
			// Every other mode is ownership/budget-scoped, and untradeables can't
			// be bought, so there they require ownership.
			// Exception: quest rewards (mostly untradeable) can be earned, so the
			// upgrade budget - the "show me obtainable gear" switch - admits them.
			return request.isIncludeUntradeables()
				&& (mode == CandidateMode.ALL_STANDARD || owned
					|| (mode == CandidateMode.OWNED_OR_BUDGET && QuestRewardItems.isQuestReward(item)));
		}
		// A tradeable with no price is unaffordable in the budget modes.
		boolean affordable = item.getEstimatedPrice() != null && item.getPriceOrZero() <= request.getBudget();
		switch (mode)
		{
			case ALL_STANDARD:
				return item.getEstimatedPrice() != null || owned;
			case OWNED_ONLY:
				return owned;
			case OWNED_OR_BUDGET:
				// The upgrade budget is the "show me obtainable gear" switch:
				// quest rewards are obtainable too - for effort, not coins.
				return owned || affordable || QuestRewardItems.isQuestReward(item);
			case BUDGET:
			default:
				return affordable;
		}
	}

	private static int budgetCost(OptimizationRequest request, GearItem item)
	{
		// Dream items are pretend-owned: free against the upgrade budget
		// (the panel prices the dream separately). Quest rewards are free
		// too when the budget admitted them - they cost effort, not gp
		// (the panel labels them with their source quest instead).
		if (item == null || request.getOwnedItems().owns(item.getId())
			|| request.isDream(item.getId())
			|| request.isPinned(item.getId())
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
			// The element amulets are stat-identical and differ only in the
			// spell they lift: collapsed, the pool kept air and lost fire
			// (field 2026-09-27). Their name keeps them apart.
			String name = item.getNameLower();
			this.category = item.getCategory() + (name.equals("elemental amulet")
				|| name.startsWith("amulet of ") && ELEMENTS.contains(name.substring(10)) ? name : "");
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
				+ Arrays.hashCode(stats);
		}

		public boolean equals(Object other)
		{
			if (!(other instanceof StatKey))
			{
				return false;
			}
			StatKey that = (StatKey) other;
			return hash == that.hash && twoHanded == that.twoHanded
				&& category.equals(that.category) && Arrays.equals(stats, that.stats);
		}

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
		// A Rada's blessing beats the god blessing it stat-ties with: the
		// teleports are the free edge (Muuffin, Discord 2026-10-19 - the
		// tradeable rule below had been handing the slot to a holy blessing).
		boolean candidateRadas = candidate.getNameLower().startsWith("rada's blessing");
		if (candidateRadas != current.getNameLower().startsWith("rada's blessing"))
		{
			return candidateRadas;
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

	@AllArgsConstructor(access = AccessLevel.PRIVATE)
	private static final class SearchState
	{
		private final EnumMap<GearSlot, GearItem> gear;
		@Getter(AccessLevel.PRIVATE)
		private final int cost;
		@Getter(AccessLevel.PRIVATE)
		private final double score;
		@Getter(AccessLevel.PRIVATE)
		private final long riskGp;
		// Total droppable value with NOTHING protected. The RISK_RESERVE
		// sorts on this, not riskGp: early partials all tie at riskGp 0
		// (the first worn items ride the kept slots), so a riskGp reserve
		// just re-kept the score leaders and the cheap line still starved.
		@Getter(AccessLevel.PRIVATE)
		private final long potentialRiskGp;

		private SearchState(EnumMap<GearSlot, GearItem> gear, int cost)
		{
			this(gear, cost, 0.0, 0L, 0L);
		}
	}
}
