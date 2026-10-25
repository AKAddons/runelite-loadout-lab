// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

import com.google.gson.JsonObject;
import java.util.*;

import lombok.Getter;
import com.loadoutlab.engine.CombatStyle;

@Getter
public final class GearItem {
	/** The name vocabulary behind poweredStaff, the demonbane class and
	 * isWeaponFor lives in gear_words.json (hub token cap); the lists are
	 * walked once per item at load, or once per pool build. */
	private static final List<String> POWERED = new ArrayList<>();
	private static final List<String> MELEE_DEMONBANE = new ArrayList<>();
	private static final List<String> RANGED_WEAPON = new ArrayList<>();
	private static final List<String> MAGIC_WEAPON = new ArrayList<>();
	private static final List<String> NOT_MELEE = new ArrayList<>();

	static {
		JsonObject words = JsonResources.objectOrThrow("gear_words.json");
		JsonResources.strings(words, "poweredStaff", POWERED);
		JsonResources.strings(words, "meleeDemonbane", MELEE_DEMONBANE);
		JsonResources.strings(words, "rangedWeapon", RANGED_WEAPON);
		JsonResources.strings(words, "magicWeapon", MAGIC_WEAPON);
		JsonResources.strings(words, "notMelee", NOT_MELEE);
	}

	private final int id;
	private final String name;
	private final String version;
	private final GearSlot slot;
	private final String category;
	private final int speed;
	private final boolean twoHanded;
	private final boolean standardGear;
	private final boolean tradeable;
	private final boolean members;
	private final Integer estimatedPrice;
	private final StatBlock offensive;
	private final StatBlock defensive;
	private final StatBlock bonuses;
	private final GearRequirements requirements;
	// Lowercase views, precomputed once: the engine matches items by name
	// fragment on every candidate evaluation, and per-call toLowerCase was
	// the single hottest path in the whole optimize (64% of CPU samples).
	private final String nameLower;
	private final String versionLower;
	private final String categoryLower;
	private final String labelLower;
	private final String nameVersionLower;
	/** Powered staff (built-in spell, no autocast) - see DpsCalculator.isPoweredStaff. */
	private final boolean poweredStaff;
	private final boolean meleeDemonbane;
	private final boolean demonbane;

	public GearItem(
		int id,
		String name,
		String version,
		GearSlot slot,
		String category,
		int speed,
		boolean twoHanded,
		boolean standardGear,
		boolean tradeable,
		boolean members,
		Integer estimatedPrice,
		StatBlock offensive,
		StatBlock defensive,
		StatBlock bonuses,
		GearRequirements requirements) {
		this.id = id;
		this.name = name == null ? "" : name;
		this.version = version == null ? "" : version;
		this.slot = slot;
		this.category = category == null ? "" : category;
		this.speed = speed;
		this.twoHanded = twoHanded;
		this.standardGear = standardGear;
		this.tradeable = tradeable;
		this.members = members;
		this.estimatedPrice = estimatedPrice;
		this.offensive = offensive == null ? StatBlock.ZERO : offensive;
		this.defensive = defensive == null ? StatBlock.ZERO : defensive;
		this.bonuses = bonuses == null ? StatBlock.ZERO : bonuses;
		this.requirements = requirements == null ? GearRequirements.NONE : requirements;
		this.nameLower = Names.lower(this.name);
		this.versionLower = Names.lower(this.version);
		this.categoryLower = Names.lower(this.category);
		this.labelLower = Names.lower(label());
		this.nameVersionLower = (this.name + " " + this.version).toLowerCase(Locale.ROOT);
		// Precomputed once: the optimizer asks this on every trial, and the
		// name-fragment chain was the single hottest call in the profile.
		// DpsCalculator.isPoweredStaff stays the API - it delegates here.
		this.poweredStaff = categoryLower.contains("powered staff")
			|| POWERED.stream().anyMatch(nameLower::contains);
		// The demonbane weapon class (wiki), one vocabulary for everyone:
		// MonsterMechanics (the Sire's vents), TormentedDemonRules, and the
		// optimizer's demon pool bump all ask this in hot loops - same
		// precompute rationale as poweredStaff. Holy water is not gear.
		this.meleeDemonbane = MELEE_DEMONBANE.stream().anyMatch(nameLower::contains);
		this.demonbane = meleeDemonbane
			|| nameLower.startsWith("scorching bow")
			|| nameLower.startsWith("purging staff");
	}

	/** This item as a tradeable at a price: a charged weapon priced as the
	 * uncharged twin you buy and charge (ISS-01M5QQV2TGQ5X730T0JJ75S1RD). */
	public GearItem priced(int price) {
		return new GearItem(id, name, version, slot, category, speed, twoHanded, standardGear, true, members, price,
			offensive, defensive, bonuses, requirements);
	}

	public boolean isWeaponFor(CombatStyle style) {
		if (slot != GearSlot.WEAPON) { return false; }
		switch (style) {
			case RANGED:
				return RANGED_WEAPON.stream().anyMatch(categoryLower::contains)
					|| offensive.getRanged() > 0;
			case MAGIC:
				return MAGIC_WEAPON.stream().anyMatch(categoryLower::contains)
					|| offensive.getMagic() > 0;
			case MELEE:
			default:
				return NOT_MELEE.stream().noneMatch(categoryLower::contains);
		}
	}

	public double roughScore(CombatStyle style) {
		switch (style) {
			case MELEE:
				return Math.max(offensive.getStab(), Math.max(offensive.getSlash(), offensive.getCrush()))
					+ bonuses.getStrength() * 2.5
					+ Math.max(0, bonuses.getPrayer()) * 0.25;
			case RANGED:
				return offensive.getRanged()
					+ bonuses.getRangedStrength() * 2.5
					+ Math.max(0, bonuses.getPrayer()) * 0.25;
			case MAGIC:
			default:
				return offensive.getMagic()
					+ bonuses.getMagicDamage() * 3.0
					+ Math.max(0, bonuses.getPrayer()) * 0.25;
		}
	}

	public boolean isSlayerHead() {
		if (slot != GearSlot.HEAD) { return false; }
		return nameVersionLower.contains("black mask") || nameVersionLower.contains("slayer helmet");
	}

	public boolean isImbuedSlayerHead() { return isSlayerHead() && nameVersionLower.contains("(i)"); }

	public String label() { return version.isEmpty() ? name : name + " (" + version + ")"; }

	/** label() lowercased (Locale.ROOT), precomputed. */
	public String labelLower() { return labelLower; }

	/**
	 * Poison tier of this version: Poison++ 3, Poison+ 2, Poison 1,
	 * everything else 0. Poison is free damage the DPS model does not
	 * price, so suggestion tie-breaks prefer the higher tier (field
	 * request: always recommend dragon dagger p++ over plain).
	 */
	public int poisonTier() {
		if (versionLower.contains("unpoison")) { return 0; }
		if (versionLower.contains("poison++")) { return 3; }
		if (versionLower.contains("poison+")) { return 2; }
		return versionLower.contains("poison") ? 1 : 0;
	}

	public int getPriceOrZero() { return estimatedPrice == null ? 0 : Math.max(0, estimatedPrice); }
}
