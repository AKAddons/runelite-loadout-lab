package com.loadoutlab.engine;

import com.google.gson.JsonObject;
import com.loadoutlab.data.*;
import java.util.*;
import static com.loadoutlab.data.JsonResources.*;
import static com.loadoutlab.engine.CombatStyle.*;

/**
 * Vampyres at parity with the official calculator's engine
 * (ISS-01M5RM2A7FFR9XWKATPX877TDD; osrs-dps-calc 89c3e25b). Tier 1 takes
 * every weapon, silver ones at +10%; tier 2 only vampyrebane weapons, silver
 * weapons (each hit capped at 10, PR #671) or anything with Efaritay's aid
 * (each hit halved); tier 3 only a vampyrebane weapon in melee or the
 * blisterwood stake - never a spell (wiki: "no spells are capable of dealing
 * damage to vampyres"). The names and numbers ride vampyre_weapons.json,
 * whose comment carries every place this follows the wiki over the calc.
 */
public final class VampyreRules {
	private static final Map<String, List<String>> NAMES = new HashMap<>();
	private static final Map<String, Integer> N = new HashMap<>();

	static {
		JsonObject data = objectOrThrow("vampyre_weapons.json");
		for (String key : List.of("silver", "silverRanged", "bane2", "bane3")) {
			strings(data, key, NAMES.computeIfAbsent(key, k -> new ArrayList<>()));
		}
		stringIntMap(data, "numbers", N);
	}

	static final int EFARITAY = N.get("efaritay");

	private VampyreRules() {
	}

	/** 1-3 for a vampyre's tier, 0 for anything else. */
	static int tier(MonsterStats m) {
		return m == null ? 0 : m.getVampyreTier();
	}

	private static String name(GearItem item) { return item == null ? "" : item.getNameLower(); }

	private static boolean in(String list, GearItem item) { return NAMES.get(list).contains(name(item)); }

	/** Vampyrebane at a tier: the stake in ranged; the listed weapons in
	 * melee, and vs tier 2 in any style (the calc's wearingVampyrebane). */
	static boolean bane(int tier, CombatStyle style, GearItem weapon) {
		return style == RANGED ? in("silverRanged", weapon)
			: (tier == 2 || style == MELEE) && in(tier == 2 ? "bane2" : "bane3", weapon);
	}

	/** Silver: the listed weapons in melee; silver ammo or the stake in ranged. */
	static boolean silver(CombatStyle style, Loadout loadout) {
		return style == RANGED ? in("silverRanged", loadout.get(GearSlot.AMMO)) || bane(0, style, loadout.getWeapon())
			: style == MELEE && in("silver", loadout.getWeapon());
	}

	private static boolean ring(Loadout loadout) { return name(loadout.get(GearSlot.RING)).equals("efaritay's aid"); }

	/** The weapon-level tier-3 bar the pool filter shares: a tier-3
	 * weapon, and never a spell (ISS-01M5RGMT70PG86MWHPF28DBJ3V). */
	public static boolean canDamage(MonsterStats monster, CombatStyle style, GearItem weapon) {
		return tier(monster) != 3 || bane(3, style, weapon);
	}

	/** The calc's isImmune: tier 3 without vampyrebane; tier 2 without
	 * vampyrebane, Efaritay's aid or a silver weapon. */
	static boolean immune(MonsterStats monster, CombatStyle style, Loadout loadout) {
		int tier = tier(monster);
		return tier == 3 ? !bane(3, style, loadout.getWeapon())
			: tier == 2 && !bane(2, style, loadout.getWeapon()) && !ring(loadout) && !silver(style, loadout);
	}

	/** The melee roll vs any vampyre, after the post rules (the calc's
	 * order): the weapon's own bonus, then a silver weapon with the ring. */
	static long accuracy(MonsterStats monster, Loadout loadout, long roll) {
		if (tier(monster) == 0) return roll;
		roll = roll * N.getOrDefault("acc " + name(loadout.getWeapon()), 100) / 100;
		return silver(MELEE, loadout) && ring(loadout) ? roll * N.get("efaritayAccuracy") / 100 : roll;
	}

	/** A result vs a vampyre with each hit through the calc's map - the
	 * weapon's scale (the ring's first), the raised accurate 0, then tier
	 * 2's halving or cap. Exact for one uniform hit: the expectation scales
	 * by the mapped sum over the plain one, where an accurate 0 already
	 * counts as 1. */
	static DpsResult apply(MonsterStats monster, CombatStyle style, Loadout loadout, DpsResult result) {
		int tier = tier(monster), top = result.getMaxHit();
		if (tier == 0) return result;
		GearItem weapon = loadout.getWeapon();
		boolean ring = ring(loadout), silver = silver(style, loadout);
		int scale = N.getOrDefault("dmg " + name(weapon), silver && tier == 1 ? N.get("silverTier1") : 0);
		boolean half = tier == 2 && ring && !bane(2, style, weapon), cap = tier == 2 && !half && silver;
		if (tier == 0 || top <= 0 || scale == 0 && !half && !cap) return result;
		int ringScale = ring ? N.get("efaritayDamage") : 100, v = 0;
		double plain = 0, mapped = 0;
		for (int d = 0; d <= top; d++) {
			v = Math.max(1, scale == 0 ? d : d * ringScale / 100 * scale / 100);
			mapped += v = half ? v / 2 : cap ? Math.min(v, N.get("tier2Cap")) : v;
			plain += Math.max(1, d);
		}
		double r = mapped / plain;
		return result.withHitModel(result.getDps() * r, result.getAccuracy(), result.getExpectedHit() * r, v);
	}
}
