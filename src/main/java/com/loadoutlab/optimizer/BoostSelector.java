package com.loadoutlab.optimizer;

import com.loadoutlab.data.JsonResources;
import com.google.gson.JsonObject;
import com.loadoutlab.engine.*;
import java.util.*;
import java.util.List;
import java.util.Map;

/**
 * The best stat boost assumed per style, given what the player actually owns.
 *
 * <p>Field report 2026-08-24 (a fresh ironman): the card claimed "Assumes:
 * Ranging potion" for an account with no ranging potion in the bank. The old
 * rule was "tradeable potions are ALWAYS assumed - cheap consumables, like
 * prayers you just bring them", which holds for a main with a GE but is simply
 * false for an ironman, and contradicts the panel's own "detect best in bank"
 * setting. EVERY boost now gates on ownership and the ladder falls through to
 * NONE.
 *
 * <p>Raid-scoped boosts (overloads, smelling salts) remain deliberately
 * un-assumed. Ids verified against net.runelite.api.ItemID 2026-08-25; the
 * divine arrays below match its DIVINE_* constants exactly.
 */
public final class BoostSelector {
	/** All dose variants - owning any counts (GE-verified ids 2026-07-21). */
	/** Base (non-divine) potions - these used to be assumed unconditionally. */
	/** Bastion and battlemage carry the SAME offensive boost as the ranging /
	 * magic potions plus a super-defence half, so owning one means you bring
	 * it - they rank above the plain family, divine or not. Their own divine
	 * variants share those numbers exactly, so both id sets map to the one
	 * profile rather than inventing a duplicate constant. */

	/** Every dose of each boost, by BoostProfile name (boost_items.json). */
	private static final Map<String, int[]> IDS = new HashMap<>();

	static {
		JsonObject root = JsonResources.objectOrThrow("boost_items.json");
		for (String key : root.keySet()) {
			if (!key.startsWith("_")) {
				List<Integer> ids = new ArrayList<>();
				JsonResources.ints(root, key, ids);
				IDS.put(key, ids.stream().mapToInt(Integer::intValue).toArray());
			}
		}
	}

	private static int[] ids(String profile) {
		return IDS.get(profile);
	}

	private BoostSelector() {
	}

	/** The best boost in the GAME per style - the BiS ceiling assumption.
	 * F2P mode: members consumables do not exist on a free world - the
	 * only boosts are the attack/strength potion pair (audit A3.5). */
	public static BoostProfile ceilingFor(CombatStyle style, boolean f2p) {
		if (f2p) {
			return style == CombatStyle.MELEE ? BoostProfile.F2P_COMBAT : BoostProfile.NONE;
		}
		switch (style) {
			// Divine variants hold the boost at ceiling for the whole fight
			// - same numbers, better assumption (field ask 2026-07-21).
			case MELEE: return BoostProfile.DIVINE_SUPER_COMBAT;
			case RANGED: return BoostProfile.DIVINE_RANGING;
			case MAGIC: return BoostProfile.SATURATED_HEART;
			default: return BoostProfile.NONE;
		}
	}

	/** Each style's ladder, best first (the divine and bastion/battlemage
	 * families outrank the plain potions; the hearts lead magic). */
	private static final Map<CombatStyle, String[]> LADDERS = Map.of(
		CombatStyle.MELEE, new String[]{"DIVINE_SUPER_COMBAT", "SUPER_COMBAT"},
		CombatStyle.RANGED, new String[]{"DIVINE_BASTION", "BASTION", "DIVINE_RANGING", "RANGING"},
		CombatStyle.MAGIC, new String[]{"SATURATED_HEART", "IMBUED_HEART", "DIVINE_BATTLEMAGE",
			"BATTLEMAGE", "DIVINE_MAGIC", "MAGIC"});

	/** The best boost you can actually assume per style, given what you own.
	 * Risk-capped wilderness searches never assume a heart (field spec
	 * 2026-07-18): both hearts are tradeable and worth far more than any
	 * sane risk cap, so the assumption falls back to the magic potion. */
	public static BoostProfile bestFor(CombatStyle style, OwnedItems owned, boolean f2p,
		boolean noHearts) {
		if (f2p) {
			return style == CombatStyle.MELEE ? BoostProfile.F2P_COMBAT : BoostProfile.NONE;
		}
		// Each ladder walks best -> worst and ends at NONE: an unowned boost is
		// never assumed, so a low-level account sees the numbers it can
		// actually reach today.
		for (String name : LADDERS.getOrDefault(style, new String[0])) {
			if (!(noHearts && name.endsWith("_HEART")) && ownsAny(owned, ids(name))) {
				return BoostProfile.valueOf(name);
			}
		}
		return BoostProfile.NONE;
	}

	/** The highest magic level this player can REACH: their live boosted
	 * level, or their base plus the best magic boost they actually own -
	 * never a potion they don't have, and never MAXED as a stand-in for
	 * unknown levels (the old Death Charge seed did both). */
	public static int reachableMagic(PlayerLevels real,
		PlayerLevels live, OwnedItems owned) {
		if (real == null) {
			return live == null ? 0 : live.getMagic();
		}
		int reachable = live == null ? real.getMagic() : live.getMagic();
		if (owned != null) {
			BoostProfile best = bestFor(CombatStyle.MAGIC,
				owned, false, false);
			reachable = Math.max(reachable, best.apply(real, live).getMagic());
		}
		return Math.max(reachable, real.getMagic());
	}

	private static boolean ownsAny(OwnedItems owned, int[] ids) {
		for (int id : ids) {
			if (owned.owns(id)) {
				return true;
			}
		}
		return false;
	}
}
