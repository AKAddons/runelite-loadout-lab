// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellStats;
import java.util.Locale;
import java.util.Map;

public final class DpsCalculator
{
	private static final String[] MELEE_TYPES = {"stab", "slash", "crush"};

	/** Conditional bonuses counted during the CURRENT calculate() call -
	 * source -> exact parts ("+16.7% accuracy"), both insertion-ordered,
	 * attached to the result for user assurance (single-threaded use,
	 * matching how the optimizer drives this class). Melee tries several
	 * stance variants per calculate; the part set dedupes the re-adds. */
	private final java.util.LinkedHashMap<String, java.util.LinkedHashSet<String>> counted =
		new java.util.LinkedHashMap<>();
	/** The beam turns collection off: only results that can reach the panel
	 * (final rescore, fill, spec) need the bonus list, and the map churn +
	 * per-result copy is pure waste across beam trials. */
	private boolean collectCounted = true;

	/** Toggle counted-bonus collection (optimizer beam: off). */
	void setCollectCounted(boolean collect)
	{
		collectCounted = collect;
	}

	/** Record one exact bonus ("+16.7% accuracy") under its source. */
	private void counted(String source, String part)
	{
		if (collectCounted)
		{
			counted.computeIfAbsent(source, s -> new java.util.LinkedHashSet<>()).add(part);
		}
	}

	/** The assurance lines: "source: +X% accuracy, +Y% damage" per source. */
	private java.util.List<String> countedLines()
	{
		java.util.List<String> lines = new java.util.ArrayList<>(counted.size());
		for (java.util.Map.Entry<String, java.util.LinkedHashSet<String>> entry : counted.entrySet())
		{
			lines.add(entry.getKey() + ": " + String.join(", ", entry.getValue()));
		}
		return lines;
	}

	public DpsResult calculate(OptimizationRequest request, Loadout loadout)
	{
		counted.clear();
		if (!VampyreRules.canDamage(request.getMonster(), loadout.getWeapon())
			|| !FlyingRules.canReach(request.getMonster(), request.getStyle(), loadout.getWeapon())
			|| !RatBoneRules.canUse(request.getMonster(), loadout.getWeapon())
			|| MonsterMechanics.isImmune(request.getMonster(), request.getStyle(), loadout, request.getSpell()))
		{
			return null;
		}
		DpsResult result;
		switch (request.getStyle())
		{
			case RANGED:
				result = calculateRanged(request, loadout);
				break;
			case MAGIC:
				result = calculateMagic(request, loadout);
				break;
			case MELEE:
			default:
				result = calculateMelee(request, loadout);
		}
		double factor = VampyreRules.damageFactor(request.getMonster(), loadout.getWeapon());
		if (result != null)
		{
			factor *= TormentedDemonRules.damageFactor(
				request.getMonster(), request.getStyle(), loadout.getWeapon(), result.getSpellName());
			factor *= MonsterMechanics.damageFactor(request.getMonster(), request.getStyle(),
				loadout, result.getAttackType(), request.getSpell());
		}
		// Zulrah rerolls any hit above 50 down to 45-50 (Jagex-confirmed);
		// scale the expectation accordingly and cap the displayed max.
		if (result != null && "Zulrah".equalsIgnoreCase(request.getMonster().getName())
			&& result.getMaxHit() > 50)
		{
			int max = result.getMaxHit();
			double cappedMean = 0;
			for (int d = 0; d <= max; d++)
			{
				cappedMean += d <= 50 ? d : 47.5;
			}
			cappedMean /= (max + 1);
			double capFactor = cappedMean / (max / 2.0);
			result = new DpsResult(result.getLoadout(), result.getDps() * capFactor,
				result.getAccuracy(), result.getExpectedHit() * capFactor,
				50, result.getAttackSpeed(), result.getAttackType(),
				result.getAttackRoll(), result.getDefenceRoll(),
				result.getPurchaseCost(), result.getSpellName());
		}
		// Tormented demons: guaranteed hits in the official default phase -
		// scale the expectation up to accuracy 1 (all hit models here are
		// linear in accuracy).
		double accuracyOverride = result != null
			&& TormentedDemonRules.applies(request.getMonster())
			&& result.getAccuracy() > 0 ? 1.0 / result.getAccuracy() : 1.0;
		if (result != null && (factor < 1.0 || accuracyOverride != 1.0))
		{
			result = new DpsResult(result.getLoadout(), result.getDps() * factor * accuracyOverride,
				Math.min(1.0, result.getAccuracy() * accuracyOverride),
				result.getExpectedHit() * factor * accuracyOverride,
				(int) (result.getMaxHit() * factor), result.getAttackSpeed(),
				result.getAttackType(), result.getAttackRoll(), result.getDefenceRoll(),
				result.getPurchaseCost(), result.getSpellName());
		}
		return result == null || counted.isEmpty()
			? result : result.withCountedBonuses(countedLines());
	}

	private DpsResult calculateMelee(OptimizationRequest request, Loadout loadout)
	{
		DpsResult best = null;
		// Only the styles this weapon actually has: a whip cannot go
		// aggressive, a mace cannot slash (see WeaponStyles).
		for (WeaponStyles.MeleeStyle style : WeaponStyles.melee(loadout.getWeapon()))
		{
			best = DpsResult.better(best, meleeVariant(request, loadout, style.attackType,
				style.attackStance, style.strengthStance));
		}
		return best;
	}

	private DpsResult meleeVariant(OptimizationRequest request, Loadout loadout, String attackType, int attackStance, int strengthStance)
	{
		PlayerLevels levels = request.getLevels();
		PrayerBonuses prayers = request.getPrayers();
		int effectiveAttack = RollMath.effectiveLevel(levels.getAttack(), prayers.getMeleeAccuracy(), attackStance);
		int effectiveStrength = RollMath.effectiveLevel(levels.getStrength(), prayers.getMeleeStrength(), strengthStance);

		long attackRoll = RollMath.attackRoll(effectiveAttack, loadout.getOffensive().getAttackBonus(attackType));
		long baseAttackRoll = attackRoll;
		int maxHit = RollMath.maxHitFromEffective(effectiveStrength, loadout.getBonuses().getStrength());
		int baseMaxHit = maxHit;
		attackRoll = applyMeleeAccuracyBonuses(request, loadout, attackRoll, baseAttackRoll, attackType);
		maxHit = applyMeleeDamageBonuses(request, loadout, maxHit, baseMaxHit, attackType);
		maxHit += RatBoneRules.flatMaxHitBonus(request.getMonster(), loadout.getWeapon());

		long defenceRoll = npcDefenceRoll(request.getMonster(), attackType, loadout.getWeapon());
		double accuracy = isFang(loadout) && "stab".equals(attackType)
			? RollMath.fangAccuracy(attackRoll, defenceRoll)
			: RollMath.normalAccuracy(attackRoll, defenceRoll);
		int minHit = 0;
		if (isFang(loadout) && "stab".equals(attackType))
		{
			minHit = (int) Math.floor(maxHit * 3.0 / 20.0);
			maxHit -= minHit;
		}
		minHit = applyFlatArmour(request, minHit);
		maxHit = applyFlatArmour(request, maxHit);
		double expected = RollMath.expectedHit(accuracy, minHit, maxHit);
		if (isDualMacuahuitl(loadout))
		{
			// Two chained hits (official calc model): the first rolls half
			// the max; the second (the remainder) only rolls when the
			// first lands, with its own accuracy roll.
			int firstMax = maxHit / 2;
			int secondMax = maxHit - firstMax;
			expected = RollMath.normalExpectedHit(accuracy, firstMax)
				+ accuracy * RollMath.normalExpectedHit(accuracy, secondMax);
		}
		if (isScythe(loadout))
		{
			if (request.getMonster().getSize() >= 2)
			{
				expected += RollMath.normalExpectedHit(accuracy, applyFlatArmour(request, maxHit / 2));
			}
			if (request.getMonster().getSize() >= 3)
			{
				expected += RollMath.normalExpectedHit(accuracy, applyFlatArmour(request, maxHit / 4));
			}
		}
		int speed = attackSpeed(loadout, CombatStyle.MELEE);
		double effectiveSpeed = speed;
		if (isWearingBloodMoonSet(loadout))
		{
			// Bloodrager (wiki): each successful macuahuitl hit has a 1/3
			// chance to make the NEXT attack come one tick earlier; with
			// the chained hits that is acc/3 + acc^2 * 2/9 per attack
			// (matches the official calc's expected attack speed).
			double proc = accuracy / 3.0 + accuracy * accuracy * 2.0 / 9.0;
			counted("bloodrager set", String.format("%.2f ticks faster on average", proc));
			effectiveSpeed = speed - proc;
		}
		String stance = attackStance == 3 ? "accurate" : strengthStance == 3 ? "aggressive" : "controlled";
		return new DpsResult(loadout, expected / (effectiveSpeed * RollMath.SECONDS_PER_TICK), accuracy, expected, maxHit, speed, attackType + " (" + stance + ")", attackRoll, defenceRoll);
	}

	private DpsResult calculateRanged(OptimizationRequest request, Loadout loadout)
	{
		PlayerLevels levels = request.getLevels();
		PrayerBonuses prayers = request.getPrayers();

		DpsResult rapid = rangedVariant(request, loadout, levels, prayers, 0, true);
		DpsResult accurate = rangedVariant(request, loadout, levels, prayers, 3, false);
		return DpsResult.better(rapid, accurate);
	}

	private DpsResult rangedVariant(OptimizationRequest request, Loadout loadout, PlayerLevels levels, PrayerBonuses prayers, int stanceBonus, boolean rapid)
	{
		int effectiveAccuracy = RollMath.effectiveLevel(levels.getRanged(), prayers.getRangedAccuracy(), stanceBonus);
		int effectiveDamage = RollMath.effectiveLevel(levels.getRanged(), prayers.getRangedStrength(), stanceBonus);

		if (isWearingRangedVoid(loadout))
		{
			boolean elite = isWearingEliteVoid(loadout);
			counted("void set", "+10% accuracy");
			counted("void set", elite ? "+12.5% damage" : "+10% damage");
			effectiveAccuracy = (int) Math.floor(effectiveAccuracy * 1.10);
			effectiveDamage = (int) Math.floor(effectiveDamage * (elite ? 1.125 : 1.10));
		}

		long attackRoll = RollMath.attackRoll(effectiveAccuracy, loadout.getOffensive().getRanged());
		boolean atlatl = isEclipseAtlatl(loadout);
		if (atlatl)
		{
			// Official calc: the atlatl swaps the DAMAGE side to melee -
			// Strength level (ranged prayer factors and void still apply)
			// and the worn MELEE strength bonuses - while accuracy stays
			// pure ranged.
			effectiveDamage = RollMath.effectiveLevel(levels.getStrength(),
				prayers.getRangedStrength(), stanceBonus);
			if (isWearingRangedVoid(loadout))
			{
				effectiveDamage = (int) Math.floor(effectiveDamage
					* (isWearingEliteVoid(loadout) ? 1.125 : 1.10));
			}
		}
		int maxHit = RollMath.maxHitFromEffective(effectiveDamage,
			atlatl ? loadout.getBonuses().getStrength()
				: effectiveRangedStrength(loadout) + BlowpipeDarts.strength(request, loadout.getWeapon()));
		attackRoll = applyRangedAccuracyBonuses(request, loadout, attackRoll);
		maxHit = applyRangedDamageBonuses(request, loadout, maxHit);
		maxHit += RatBoneRules.flatMaxHitBonus(request.getMonster(), loadout.getWeapon());
		maxHit = applyFlatArmour(request, maxHit);

		long defenceRoll = npcDefenceRoll(request.getMonster(), "ranged", loadout.getWeapon());
		double accuracy = RollMath.normalAccuracy(attackRoll, defenceRoll);
		double expected = RollMath.normalExpectedHit(accuracy, maxHit);
		if (isWearingEclipseMoonSet(loadout)
			&& !request.getMonster().hasAttribute("burn_immune"))
		{
			// Eclipse (wiki): each successful hit has a 20% chance to apply
			// a 10-damage burn - "roughly 2 burn damage per successful hit"
			// sustained (their own average; the 5-stack cap and target
			// death truncate a little in practice).
			counted("eclipse set", "~2 burn damage per landed hit");
			expected += accuracy * 2.0;
		}
		int speed = attackSpeed(loadout, CombatStyle.RANGED);
		if (rapid)
		{
			speed = Math.max(2, speed - 1);
		}
		String attackType = rapid ? "ranged rapid" : "ranged accurate";
		String dartName = BlowpipeDarts.tierName(request, loadout.getWeapon());
		if (dartName != null)
		{
			attackType += " - " + dartName;
		}
		return new DpsResult(loadout, expected / (speed * RollMath.SECONDS_PER_TICK), accuracy, expected, maxHit, speed, attackType, attackRoll, defenceRoll);
	}

	private DpsResult calculateMagic(OptimizationRequest request, Loadout loadout)
	{
		OptimizationRequest effectiveRequest = isPoweredStaff(loadout) && request.getSpell() != null ? request.withSpell(null) : request;
		PlayerLevels levels = effectiveRequest.getLevels();
		PrayerBonuses prayers = effectiveRequest.getPrayers();
		// Prayer floor, +2 accurate stance, +9 - matches the official
		// calc's effective level.
		int effectiveAccuracy = (int) Math.floor(levels.getMagic() * prayers.getMagicAccuracy()) + 2 + 9;
		if (isWearingMagicVoid(loadout))
		{
			counted("void set", "+45% accuracy");
			effectiveAccuracy = (int) Math.floor(effectiveAccuracy * 1.45);
		}

		int magicAttackBonus = loadout.getOffensive().getMagic();
		if (wearing(loadout, "tumeken"))
		{
			// The shadow triples the magic attack bonus of ALL equipment
			// (it triples magic damage too - handled in the damage path).
			magicAttackBonus *= 3;
		}
		long attackRoll = RollMath.attackRoll(effectiveAccuracy, magicAttackBonus);
		int maxHit = magicMaxHit(effectiveRequest, loadout);
		attackRoll = applyMagicAccuracyBonuses(effectiveRequest, loadout, attackRoll);
		maxHit = applyMagicDamageBonuses(effectiveRequest, loadout, maxHit);
		maxHit = applyFlatArmour(effectiveRequest, maxHit);

		long defenceRoll = npcDefenceRoll(effectiveRequest.getMonster(), "magic", loadout.getWeapon());
		double accuracy = RollMath.normalAccuracy(attackRoll, defenceRoll);
		double expected = RollMath.normalExpectedHit(accuracy, maxHit);
		int speed = attackSpeed(loadout, CombatStyle.MAGIC);
		String spellName = effectiveRequest.getSpell() == null ? "" : effectiveRequest.getSpell().getName();
		double frostweaver = frostweaverBonus(effectiveRequest, loadout);
		if (frostweaver > 0)
		{
			counted("frostweaver set", "chance of a free spear hit per cast");
			expected += frostweaver;
		}
		String poweredName = spellName.isEmpty() && isPoweredStaff(loadout) && loadout.getWeapon() != null ? loadout.getWeapon().getName() : "";
		String attackType = spellName.isEmpty() ? poweredName.isEmpty() ? "magic" : "magic: " + poweredName : "magic: " + spellName;
		return new DpsResult(loadout, expected / (speed * RollMath.SECONDS_PER_TICK), accuracy, expected, maxHit, speed, attackType, attackRoll, defenceRoll, loadout.getCost(), spellName);
	}

	private int magicMaxHit(OptimizationRequest request, Loadout loadout)
	{
		GearItem weapon = loadout.getWeapon();
		String weaponName = name(weapon);
		int magicLevel = request.getLevels().getMagic();
		SpellStats spell = request.getSpell();
		boolean poweredStaff = isPoweredStaff(loadout);
		if (spell != null && !poweredStaff)
		{
			if ("Magic Dart".equals(spell.getName()))
			{
				int base = magicLevel / 10 + 10;
				return wearing(loadout, "slayer's staff (e)") ? magicLevel / 6 + 13 : base;
			}
			return elementalSpellMax(spell, magicLevel);
		}
		if (!poweredStaff)
		{
			return 0;
		}
		if (weaponName.contains("trident of the seas"))
		{
			return Math.max(1, magicLevel / 3 - 5);
		}
		if (weaponName.contains("thammaron"))
		{
			return Math.max(1, magicLevel / 3 - 8);
		}
		if (weaponName.contains("accursed sceptre"))
		{
			return Math.max(1, magicLevel / 3 - 6);
		}
		if (weaponName.contains("trident of the swamp"))
		{
			return Math.max(1, magicLevel / 3 - 2);
		}
		if (weaponName.contains("sanguinesti"))
		{
			return Math.max(1, magicLevel / 3 - 1);
		}
		if (weaponName.contains("tumeken"))
		{
			return Math.max(1, magicLevel / 3 + 1);
		}
		if (weaponName.contains("warped sceptre"))
		{
			return Math.max(1, (8 * magicLevel + 96) / 37);
		}
		if (weaponName.contains("eye of ayak"))
		{
			return Math.max(1, magicLevel / 3 - 6);
		}
		if (weaponName.contains("bone staff"))
		{
			return Math.max(1, magicLevel / 3 - 5) + 10;
		}
		return Math.max(1, magicLevel / 3 + 1);
	}

	/**
	 * June 2025 magic rebalance: elemental spells share a class-wide max
	 * hit scaled by the caster's Magic level - Water Surge cast at 95+
	 * hits like Fire Surge. The chosen element then only matters for
	 * elemental-weakness matching, which is what makes weakness-exploiting
	 * spell picks viable. Verified against the official calculator.
	 */
	/** Package-private: the optimizer prunes dominated elementals with the
	 * same effective-max computation the DPS chain uses. */
	static int elementalSpellMax(SpellStats spell, int magicLevel)
	{
		if (spell.getElement().isEmpty())
		{
			return spell.getMaxHit();
		}
		// The two-word parse is precomputed on SpellStats - split() here
		// allocated a String[] per spell per trial.
		String tierWord = spell.getNameSecondWord();
		if (tierWord == null || !isElementWord(spell.getNameFirstWord()))
		{
			return spell.getMaxHit();
		}
		int tier;
		switch (tierWord)
		{
			case "Strike": tier = magicLevel >= 13 ? 3 : magicLevel >= 9 ? 2 : magicLevel >= 5 ? 1 : 0; break;
			case "Bolt": tier = magicLevel >= 35 ? 3 : magicLevel >= 29 ? 2 : magicLevel >= 23 ? 1 : 0; break;
			case "Blast": tier = magicLevel >= 59 ? 3 : magicLevel >= 53 ? 2 : magicLevel >= 47 ? 1 : 0; break;
			case "Wave": tier = magicLevel >= 75 ? 3 : magicLevel >= 70 ? 2 : magicLevel >= 65 ? 1 : 0; break;
			case "Surge": tier = magicLevel >= 95 ? 3 : magicLevel >= 90 ? 2 : magicLevel >= 85 ? 1 : 0; break;
			default: return spell.getMaxHit();
		}
		// Class max hits: wind/water/earth/fire per tier.
		switch (tierWord)
		{
			case "Strike": return 2 + 2 * tier;
			case "Bolt": return 9 + tier;
			case "Blast": return 13 + tier;
			case "Wave": return 17 + tier;
			default: return 21 + tier;
		}
	}

	private static boolean isElementWord(String word)
	{
		return "Wind".equals(word) || "Water".equals(word)
			|| "Earth".equals(word) || "Fire".equals(word);
	}

	/**
	 * Plain standard-book elemental combat spell ("Wind Strike".."Fire
	 * Surge"): legal on every non-powered staff (spellAllowed has no rule
	 * for them), and the DPS chain reads nothing from it beyond the
	 * effective max hit and the weakness element - which makes same-class
	 * spells interchangeable, and dominated ones prunable per optimize.
	 */
	static boolean isPlainElemental(SpellStats spell)
	{
		return !spell.getElement().isEmpty()
			&& spell.getNameSecondWord() != null
			&& isElementWord(spell.getNameFirstWord());
	}

	private long npcDefenceRoll(MonsterStats monster, String attackType, GearItem weapon)
	{
		// Most NPCs defend magic with their Magic level; a curated list
		// (vendored from the official calc) uses Defence level instead.
		int level = "magic".equals(attackType)
			&& !MonsterMechanics.magicDefenceUsesDefenceLevel(monster)
			? monster.getMagic() : monster.getDefence();
		int bonus;
		if ("ranged".equals(attackType))
		{
			bonus = monster.getDefensive().get(rangedDefenceType(weapon));
		}
		else
		{
			bonus = monster.getDefensive().get(attackType);
		}
		return RollMath.defenceRoll(level + 9, bonus);
	}

	private static String rangedDefenceType(GearItem weapon)
	{
		String category = weapon == null ? "" : weapon.getCategoryLower();
		if (category.contains("thrown"))
		{
			return "light";
		}
		if (category.contains("crossbow") || category.contains("chinchompa"))
		{
			return "heavy";
		}
		return "standard";
	}

	private static int effectiveRangedStrength(Loadout loadout)
	{
		int rangedStrength = loadout.getBonuses().getRangedStrength();
		GearItem ammo = loadout.get(GearSlot.AMMO);
		if (ammo != null && ammo.getBonuses().getRangedStrength() > 0 && !RangedAmmo.strengthApplies(ammo, loadout.getWeapon()))
		{
			rangedStrength -= ammo.getBonuses().getRangedStrength();
		}
		return rangedStrength;
	}

	private static int attackSpeed(Loadout loadout, CombatStyle style)
	{
		GearItem weapon = loadout.getWeapon();
		if (weapon == null || weapon.getSpeed() <= 0)
		{
			return style == CombatStyle.MAGIC ? 5 : 4;
		}
		if (style == CombatStyle.MAGIC && !isPoweredStaff(loadout))
		{
			// Casting a spell is 5 ticks regardless of the staff's melee
			// speed (upstream billed autocasts at the wand's 4 ticks - a
			// 25% dps overstatement). Harmonised nightmare staff: 4 ticks
			// on standard spells.
			return name(weapon).contains("harmonised") ? 4 : 5;
		}
		return Math.max(1, weapon.getSpeed());
	}

	private long applyMeleeAccuracyBonuses(OptimizationRequest request, Loadout loadout, long roll, long baseRoll, String attackType)
	{
		if (isWearingMeleeVoid(loadout))
		{
			counted("void set", "+10% accuracy");
			roll = multiply(roll, 11, 10);
		}
		// Golembane (Aug 2025: granite hammer 30%, barronite mace 15%) -
		// applied before the salve/slayer early returns because it stacks
		// multiplicatively with them (helm first, per the wiki).
		if (isGolem(request) && wearing(loadout, "granite hammer"))
		{
			counted("golembane weapon", "+30% accuracy");
			roll = multiply(roll, 13, 10);
		}
		if (isGolem(request) && wearing(loadout, "barronite mace"))
		{
			counted("golembane weapon", "+15% accuracy");
			roll = multiply(roll, 23, 20);
		}
		// Avarice, salve, and the slayer helm are exclusive with EACH OTHER
		// only - weapon bonuses below (arclight, dhl...) stack on top.
		// Chain order mirrors the wiki calc (avarice first at revenants).
		if (isRevenant(request) && wearing(loadout, "amulet of avarice"))
		{
			counted("amulet of avarice", "+20% accuracy");
			roll = multiply(roll, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet(ei)"))
		{
			// Named WITHOUT a space, unlike "salve amulet (e)" - a contains
			// match on the (e) string misses it and demotes it a tier.
			counted("salve amulet(ei)", "+20% accuracy");
			roll = multiply(roll, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet (e)"))
		{
			counted("salve amulet (e)", "+20% accuracy");
			roll = multiply(roll, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet"))
		{
			counted("salve amulet", "+16.7% accuracy");
			roll = multiply(roll, 7, 6);
		}
		else if (isSlayerTaskEligible(request) && slayerHead(loadout) != null)
		{
			counted(slayerHead(loadout).getNameLower(), "+16.7% accuracy");
			roll = multiply(roll, 7, 6);
		}
		if (isTzhaarWeapon(loadout) && isWearingObsidian(loadout))
		{
			counted("obsidian set", "+10% base accuracy");
			roll += multiply(baseRoll, 1, 10);
		}
		if (revWeaponBuff(request, loadout, "ursine chainmace", "viggora's chainmace"))
		{
			counted("wilderness weapon", "+50% accuracy");
			roll = multiply(roll, 3, 2);
		}
		if (isDemon(request) && (wearing(loadout, "arclight") || wearing(loadout, "emberlight")))
		{
			counted("demonbane weapon", "+70% accuracy");
			roll = multiply(roll, 17, 10);
		}
		if (isDemon(request) && (wearing(loadout, "bone claws") || wearing(loadout, "burning claws")))
		{
			counted("demonbane weapon", "+5% accuracy");
			roll = multiply(roll, 21, 20);
		}
		if (isKalphite(request) && wearing(loadout, "keris partisan of breaching"))
		{
			counted("keris vs kalphites", "+33% accuracy");
			roll = multiply(roll, 133, 100);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter lance"))
		{
			counted("dragon hunter lance", "+20% accuracy");
			roll = multiply(roll, 6, 5);
		}
		if ("crush".equals(attackType))
		{
			roll = applyInquisitorBonus(loadout, roll);
		}
		return roll;
	}

	private int applyMeleeDamageBonuses(OptimizationRequest request, Loadout loadout, int maxHit, int baseMaxHit, String attackType)
	{
		if (isWearingMeleeVoid(loadout))
		{
			counted("void set", "+10% damage");
			maxHit = multiply(maxHit, 11, 10);
		}
		if (isGolem(request) && wearing(loadout, "granite hammer"))
		{
			counted("golembane weapon", "+30% damage");
			maxHit = multiply(maxHit, 13, 10);
		}
		if (isGolem(request) && wearing(loadout, "barronite mace"))
		{
			counted("golembane weapon", "+15% damage");
			maxHit = multiply(maxHit, 23, 20);
		}
		if (isLeafy(request) && wearing(loadout, "leaf-bladed battleaxe"))
		{
			// Damage only (accuracy untouched); stacks with the slayer helm.
			counted("leaf-bladed battleaxe", "+17.5% damage vs leafy");
			maxHit = multiply(maxHit, 47, 40);
		}
		if (isRevenant(request) && wearing(loadout, "amulet of avarice"))
		{
			counted("amulet of avarice", "+20% damage");
			maxHit = multiply(maxHit, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet(ei)"))
		{
			// Spaceless name - see the accuracy chain note.
			counted("salve amulet(ei)", "+20% damage");
			maxHit = multiply(maxHit, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet (e)"))
		{
			counted("salve amulet (e)", "+20% damage");
			maxHit = multiply(maxHit, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet"))
		{
			counted("salve amulet", "+16.7% damage");
			maxHit = multiply(maxHit, 7, 6);
		}
		else if (isSlayerTaskEligible(request) && slayerHead(loadout) != null)
		{
			counted(slayerHead(loadout).getNameLower(), "+16.7% damage");
			maxHit = multiply(maxHit, 7, 6);
		}
		if (isDemon(request) && (wearing(loadout, "arclight") || wearing(loadout, "emberlight")))
		{
			counted("demonbane weapon", "+70% damage");
			maxHit = multiply(maxHit, 17, 10);
		}
		if (isDemon(request) && (wearing(loadout, "silverlight") || wearing(loadout, "darklight")))
		{
			// Damage only - the wiki and official calc give these no accuracy bonus.
			counted("demonbane weapon", "+60% damage");
			maxHit = multiply(maxHit, 8, 5);
		}
		if (isDemon(request) && (wearing(loadout, "bone claws") || wearing(loadout, "burning claws")))
		{
			counted("demonbane weapon", "+5% damage");
			maxHit = multiply(maxHit, 21, 20);
		}
		if (isTzhaarWeapon(loadout) && isWearingObsidian(loadout))
		{
			counted("obsidian set", "+10% base damage");
			maxHit += multiply(baseMaxHit, 1, 10);
		}
		if (revWeaponBuff(request, loadout, "ursine chainmace", "viggora's chainmace"))
		{
			counted("wilderness weapon", "+50% damage");
			maxHit = multiply(maxHit, 3, 2);
		}
		if (isTzhaarWeapon(loadout) && wearing(loadout, "berserker necklace"))
		{
			counted("berserker necklace", "+20% damage");
			maxHit = multiply(maxHit, 6, 5);
		}
		if (isKalphite(request) && wearing(loadout, "keris"))
		{
			boolean amascut = wearing(loadout, "keris partisan of amascut");
			counted("keris vs kalphites", amascut ? "+15% damage" : "+33% damage");
			maxHit = multiply(maxHit, amascut ? 115 : 133, 100);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter lance"))
		{
			counted("dragon hunter lance", "+20% damage");
			maxHit = multiply(maxHit, 6, 5);
		}
		if ("crush".equals(attackType))
		{
			maxHit = (int) applyInquisitorBonus(loadout, maxHit);
		}
		return maxHit;
	}

	private long applyRangedAccuracyBonuses(OptimizationRequest request, Loadout loadout, long roll)
	{
		// Crystal armour scales the base roll BEFORE salve/slayer - the wiki
		// calc devs verified the flooring order in-game (bofa + slayer helm
		// + crystal body/legs maxes 36, not the 37 the reverse order gives).
		if (isCrystalBow(loadout))
		{
			int pieces = crystalArmourPieces(loadout);
			if (pieces > 0)
			{
				// x(20+n)/20: each helm/legs/body weight is +5% accuracy.
				counted("crystal armour set", "+" + pieces * 5 + "% accuracy");
			}
			roll = multiply(roll, 20 + pieces, 20);
		}
		if (isRevenant(request) && wearing(loadout, "amulet of avarice"))
		{
			counted("amulet of avarice", "+20% accuracy");
			roll = multiply(roll, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet(ei)"))
		{
			counted("salve amulet(ei)", "+20% accuracy");
			roll = multiply(roll, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet(i)"))
		{
			counted("salve amulet(i)", "+16.7% accuracy");
			roll = multiply(roll, 7, 6);
		}
		else if (isSlayerTaskEligible(request) && imbuedSlayerHead(loadout) != null)
		{
			counted(imbuedSlayerHead(loadout).getNameLower(), "+15% accuracy");
			roll = multiply(roll, 23, 20);
		}
		if (revWeaponBuff(request, loadout, "craw's bow", "webweaver bow"))
		{
			counted("wilderness weapon", "+50% accuracy");
			roll = multiply(roll, 3, 2);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter crossbow"))
		{
			counted("dragon hunter crossbow", "+30% accuracy");
			roll = multiply(roll, 13, 10);
		}
		if (isDemon(request) && wearing(loadout, "scorching bow"))
		{
			counted("scorching bow", "+30% accuracy");
			roll = multiply(roll, 13, 10);
		}
		if (wearing(loadout, "twisted bow"))
		{
			int cap = request.getMonster().hasAttribute("xerician") ? 350 : 250;
			int magic = Math.min(cap, Math.max(request.getMonster().getMagic(), request.getMonster().getOffensiveMagic()));
			int bonus = tbowBonus(magic, true);
			counted("twisted bow scaling", (bonus >= 100 ? "+" : "") + (bonus - 100) + "% accuracy");
			return Math.floorDiv(roll * bonus, 100);
		}
		return roll;
	}

	private int applyRangedDamageBonuses(OptimizationRequest request, Loadout loadout, int maxHit)
	{
		// Crystal scaling floors before salve/slayer - see accuracy note above.
		if (isCrystalBow(loadout))
		{
			int pieces = crystalArmourPieces(loadout);
			if (pieces > 0)
			{
				// x(40+n)/40: each piece weight is +2.5% damage (in tenths
				// of a percent so 7.5 prints exactly, 15 prints without .0).
				int tenths = pieces * 25;
				counted("crystal armour set", "+" + (tenths / 10)
					+ (tenths % 10 == 0 ? "" : "." + tenths % 10) + "% damage");
			}
			maxHit = multiply(maxHit, 40 + pieces, 40);
		}
		if (isRevenant(request) && wearing(loadout, "amulet of avarice"))
		{
			counted("amulet of avarice", "+20% damage");
			maxHit = multiply(maxHit, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet(ei)"))
		{
			counted("salve amulet(ei)", "+20% damage");
			maxHit = multiply(maxHit, 6, 5);
		}
		else if (isUndead(request) && isEclipseAtlatl(loadout) && wearing(loadout, "salve amulet (e)"))
		{
			// The atlatl's melee-side damage accepts the UNIMBUED melee
			// salve variants (official calc scalesWithStr branches).
			counted("salve amulet (e)", "+20% damage");
			maxHit = multiply(maxHit, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet(i)"))
		{
			counted("salve amulet(i)", "+16.7% damage");
			maxHit = multiply(maxHit, 7, 6);
		}
		else if (isUndead(request) && isEclipseAtlatl(loadout) && wearing(loadout, "salve amulet"))
		{
			counted("salve amulet", "+16.7% damage");
			maxHit = multiply(maxHit, 7, 6);
		}
		else if (isSlayerTaskEligible(request) && isEclipseAtlatl(loadout)
			&& wearing(loadout, "black mask") && imbuedSlayerHead(loadout) == null)
		{
			counted("black mask", "+16.7% damage");
			maxHit = multiply(maxHit, 7, 6);
		}
		else if (isSlayerTaskEligible(request) && imbuedSlayerHead(loadout) != null)
		{
			counted(imbuedSlayerHead(loadout).getNameLower(), "+15% damage");
			maxHit = multiply(maxHit, 23, 20);
		}
		if (revWeaponBuff(request, loadout, "craw's bow", "webweaver bow"))
		{
			counted("wilderness weapon", "+50% damage");
			maxHit = multiply(maxHit, 3, 2);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter crossbow"))
		{
			counted("dragon hunter crossbow", "+25% damage");
			maxHit = multiply(maxHit, 5, 4);
		}
		if (isDemon(request) && wearing(loadout, "scorching bow"))
		{
			counted("scorching bow", "+30% damage");
			maxHit = multiply(maxHit, 13, 10);
		}
		if (wearing(loadout, "twisted bow"))
		{
			int cap = request.getMonster().hasAttribute("xerician") ? 350 : 250;
			int magic = Math.min(cap, Math.max(request.getMonster().getMagic(), request.getMonster().getOffensiveMagic()));
			int bonus = tbowBonus(magic, false);
			counted("twisted bow scaling", (bonus >= 100 ? "+" : "") + (bonus - 100) + "% damage");
			return (int) Math.floorDiv((long) maxHit * bonus, 100);
		}
		return maxHit;
	}

	private long applyMagicAccuracyBonuses(OptimizationRequest request, Loadout loadout, long roll)
	{
		if (isRevenant(request) && wearing(loadout, "amulet of avarice"))
		{
			counted("amulet of avarice", "+20% accuracy");
			roll = multiply(roll, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet(ei)"))
		{
			counted("salve amulet(ei)", "+20% accuracy");
			roll = multiply(roll, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet(i)"))
		{
			counted("salve amulet(i)", "+15% accuracy");
			roll = multiply(roll, 23, 20);
		}
		else if (isSlayerTaskEligible(request) && imbuedSlayerHead(loadout) != null)
		{
			counted(imbuedSlayerHead(loadout).getNameLower(), "+15% accuracy");
			roll = multiply(roll, 23, 20);
		}
		if (revWeaponBuff(request, loadout, "thammaron's sceptre", "thammaron's sceptre (a)",
			"accursed sceptre", "accursed sceptre (a)"))
		{
			counted("wilderness weapon", "+50% accuracy");
			roll = multiply(roll, 3, 2);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter wand"))
		{
			counted("dragon hunter wand", "+75% accuracy");
			roll = multiply(roll, 7, 4);
		}
		if (request.getSpell() != null && request.getSpell().getElement().equals(request.getMonster().getWeaknessElement()))
		{
			int severity = request.getMonster().getWeaknessSeverity();
			counted("elemental weakness", "+" + severity + "% accuracy");
			roll = multiply(roll, 100 + severity, 100);
		}
		if (isDemon(request) && request.getSpell() != null && request.getSpell().getName().contains("Demonbane"))
		{
			// Demonbane assumes Mark of Darkness (standard practice - it is
			// a cheap self-buff and the purging staff quintuples its
			// duration): 40% accuracy, 80% with the purging staff.
			boolean purging = wearing(loadout, "purging staff");
			counted("demonbane spell (Mark of Darkness)",
				purging ? "+80% accuracy" : "+40% accuracy");
			roll = purging ? multiply(roll, 9, 5) : multiply(roll, 7, 5);
		}
		return roll;
	}

	private int applyMagicDamageBonuses(OptimizationRequest request, Loadout loadout, int maxHit)
	{
		int baseMaxHit = maxHit;
		int magicDamage = loadout.getBonuses().getMagicDamage();
		if (wearing(loadout, "tumeken"))
		{
			magicDamage = Math.min(1000, magicDamage * 3);
		}
		maxHit = (int) Math.floor(maxHit * (1.0 + (magicDamage + request.getPrayers().getMagicDamagePercent() * 10.0) / 1000.0));
		if (isRevenant(request) && wearing(loadout, "amulet of avarice"))
		{
			counted("amulet of avarice", "+20% damage");
			maxHit = multiply(maxHit, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet(ei)"))
		{
			// The magic damage path lacked a salve branch entirely (the
			// accuracy path had one) - wiki: (ei) +20%, (i) +15% magic damage.
			counted("salve amulet(ei)", "+20% damage");
			maxHit = multiply(maxHit, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet(i)"))
		{
			counted("salve amulet(i)", "+15% damage");
			maxHit = multiply(maxHit, 23, 20);
		}
		else if (isSlayerTaskEligible(request) && imbuedSlayerHead(loadout) != null)
		{
			counted(imbuedSlayerHead(loadout).getNameLower(), "+15% damage");
			maxHit = multiply(maxHit, 23, 20);
		}
		if (revWeaponBuff(request, loadout, "thammaron's sceptre", "thammaron's sceptre (a)",
			"accursed sceptre", "accursed sceptre (a)"))
		{
			counted("wilderness weapon", "+50% damage");
			maxHit = multiply(maxHit, 3, 2);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter wand"))
		{
			// Accuracy is 7/4 but damage is 7/5 (official-verified) -
			// upstream applied 7/4 to both (ENGINE-GAPS bug #1).
			counted("dragon hunter wand", "+40% damage");
			maxHit = multiply(maxHit, 7, 5);
		}
		if (request.getSpell() != null && request.getSpell().getElement().equals(request.getMonster().getWeaknessElement()))
		{
			int severity = request.getMonster().getWeaknessSeverity();
			counted("elemental weakness", "+" + severity + "% base damage");
			maxHit += multiply(baseMaxHit, severity, 100);
		}
		if (isDemon(request) && request.getSpell() != null && request.getSpell().getName().contains("Demonbane"))
		{
			// Mark of Darkness damage: +25%, +50% with the purging staff.
			boolean purging = wearing(loadout, "purging staff");
			counted("demonbane spell (Mark of Darkness)",
				purging ? "+50% damage" : "+25% damage");
			maxHit = purging ? multiply(maxHit, 3, 2) : multiply(maxHit, 5, 4);
		}
		return maxHit;
	}

	private static boolean wearing(Loadout loadout, String marker)
	{
		return loadout.namesLower().contains(marker.toLowerCase(Locale.ROOT));
	}

	/** Revenants: the avarice and wilderness-weapon conditionals key off
	 * them (wiki calc: monster name starts with "Revenant").
	 * Package-private: RevenantConditionalsTest exercises the gate. */
	static boolean isRevenant(OptimizationRequest request)
	{
		return request.getMonster().isRevenantMonster();
	}

	/**
	 * Wilderness weapon passive: +50% accuracy AND damage against monsters
	 * in the Wilderness, CHARGED version only (wiki calc BaseCalc
	 * .isRevWeaponBuffApplicable, applied after the avarice/salve/slayer
	 * chain). Keyed on the REQUEST's in-Wilderness flag - name membership
	 * alone buffed Catacombs hellhounds and Taverley dungeon staples
	 * (audit A3.1); wilderness-exclusive monsters default the flag on.
	 */
	static boolean revWeaponBuff(OptimizationRequest request, Loadout loadout, String... weapons)
	{
		if (!request.isInWilderness())
		{
			return false;
		}
		GearItem weapon = loadout.get(GearSlot.WEAPON);
		if (weapon == null || !"charged".equals(weapon.getVersionLower()))
		{
			return false;
		}
		for (String name : weapons)
		{
			if (weapon.getNameLower().equals(name))
			{
				return true;
			}
		}
		return false;
	}

	private static String name(GearItem item)
	{
		return item == null ? "" : item.getNameLower();
	}

	private static boolean isWearingMeleeVoid(Loadout loadout)
	{
		return wearing(loadout, "void melee helm") && isWearingVoidRobes(loadout);
	}

	private static boolean isWearingRangedVoid(Loadout loadout)
	{
		return wearing(loadout, "void ranger helm") && isWearingVoidRobes(loadout);
	}

	private static boolean isWearingMagicVoid(Loadout loadout)
	{
		return wearing(loadout, "void mage helm") && isWearingVoidRobes(loadout);
	}

	private static boolean isWearingVoidRobes(Loadout loadout)
	{
		return (wearing(loadout, "void knight top") || wearing(loadout, "elite void top"))
			&& (wearing(loadout, "void knight robe") || wearing(loadout, "elite void robe"))
			&& wearing(loadout, "void knight gloves");
	}

	private static boolean isWearingEliteVoid(Loadout loadout)
	{
		return wearing(loadout, "elite void top") && wearing(loadout, "elite void robe") && wearing(loadout, "void knight gloves");
	}

	/** The worn slayer-head item (helm, mask, imbued variants), or null -
	 * the counted-bonus line names the ACTUAL item (a bare black mask must
	 * not be reported as "slayer helmet"). */
	private static GearItem slayerHead(Loadout loadout)
	{
		for (GearItem item : loadout.getGear().values())
		{
			if (item != null && item.isSlayerHead())
			{
				return item;
			}
		}
		return null;
	}

	/** The worn IMBUED slayer head, or null (ranged/magic need the imbue). */
	private static GearItem imbuedSlayerHead(Loadout loadout)
	{
		for (GearItem item : loadout.getGear().values())
		{
			if (item != null && item.isImbuedSlayerHead())
			{
				return item;
			}
		}
		return null;
	}

	private static boolean isUndead(OptimizationRequest request)
	{
		return request.getMonster().hasAttribute("undead");
	}

	private static boolean isDragon(OptimizationRequest request)
	{
		return request.getMonster().hasAttribute("dragon");
	}

	private static boolean isDemon(OptimizationRequest request)
	{
		return request.getMonster().hasAttribute("demon");
	}

	private static boolean isKalphite(OptimizationRequest request)
	{
		return request.getMonster().hasAttribute("kalphite");
	}

	private static boolean isGolem(OptimizationRequest request)
	{
		return request.getMonster().hasAttribute("golem");
	}

	private static boolean isLeafy(OptimizationRequest request)
	{
		return request.getMonster().hasAttribute("leafy");
	}

	private static boolean isSlayerTaskEligible(OptimizationRequest request)
	{
		return request.isOnSlayerTask() && request.getMonster().isSlayerMonster();
	}

	private static boolean isPoweredStaff(Loadout loadout)
	{
		return isPoweredStaff(loadout.getWeapon());
	}

	/** THE powered-staff check - the optimizer must agree with the
	 * calculator or a staff gets evaluated down the wrong path (the
	 * optimizer once had its own copy missing eye of ayak). The rule
	 * itself is precomputed per item (GearItem ctor) - this ran the
	 * name-fragment chain per trial and topped the profile. */
	static boolean isPoweredStaff(GearItem weapon)
	{
		return weapon != null && weapon.isPoweredStaff();
	}

	private static boolean isFang(Loadout loadout)
	{
		return wearing(loadout, "osmumten's fang");
	}

	private static boolean isScythe(Loadout loadout)
	{
		return wearing(loadout, "scythe of vitur");
	}

	private static boolean isDualMacuahuitl(Loadout loadout)
	{
		return wearing(loadout, "dual macuahuitl");
	}

	/** Bloodrager: all three blood moon pieces + the dual macuahuitl. The
	 * wiki confirms the effect activates even on broken pieces, and every
	 * New/Used/Broken variant shares the clean item name. */
	private static boolean isWearingBloodMoonSet(Loadout loadout)
	{
		return isDualMacuahuitl(loadout)
			&& wearing(loadout, "blood moon helm")
			&& wearing(loadout, "blood moon chestplate")
			&& wearing(loadout, "blood moon tassets");
	}

	private static boolean isEclipseAtlatl(Loadout loadout)
	{
		return wearing(loadout, "eclipse atlatl");
	}

	/** Eclipse: all three eclipse moon pieces + the atlatl (broken counts,
	 * same as the other moon sets). */
	private static boolean isWearingEclipseMoonSet(Loadout loadout)
	{
		return isEclipseAtlatl(loadout)
			&& wearing(loadout, "eclipse moon helm")
			&& wearing(loadout, "eclipse moon chestplate")
			&& wearing(loadout, "eclipse moon tassets");
	}

	/** Frostweaver: all three blue moon pieces + the spear. */
	private static boolean isWearingBlueMoonSet(Loadout loadout)
	{
		return wearing(loadout, "blue moon spear")
			&& wearing(loadout, "blue moon helm")
			&& wearing(loadout, "blue moon chestplate")
			&& wearing(loadout, "blue moon tassets");
	}

	/**
	 * Frostweaver (wiki): after casting a spell that binds, freezes or
	 * roots, the spear has a chance to land an instant melee attack - 20%
	 * for Standard-book binds and Ancient ice spells, 50% for Arceuus
	 * grasps, "even if it does not bind a target". Expected bonus damage
	 * per cast = chance x the spear's plain melee expected hit (stab, no
	 * melee prayer - you are on a magic prayer while casting).
	 */
	private double frostweaverBonus(OptimizationRequest request, Loadout loadout)
	{
		com.loadoutlab.data.SpellStats spell = request.getSpell();
		if (spell == null || !isWearingBlueMoonSet(loadout))
		{
			return 0;
		}
		String spellName = spell.getName();
		String book = spell.getSpellbook() == null ? "" : spell.getSpellbook().toLowerCase(java.util.Locale.ROOT);
		double chance;
		if ("arceuus".equals(book) && "Grasp".equalsIgnoreCase(spell.getNameSecondWord()))
		{
			chance = 0.5;
		}
		else if ("ancient".equals(book) && "Ice".equalsIgnoreCase(spell.getNameFirstWord()))
		{
			chance = 0.2;
		}
		else if ("standard".equals(book) && ("Bind".equalsIgnoreCase(spellName)
			|| "Snare".equalsIgnoreCase(spellName) || "Entangle".equalsIgnoreCase(spellName)))
		{
			chance = 0.2;
		}
		else
		{
			return 0;
		}
		PlayerLevels levels = request.getLevels();
		int effAttack = RollMath.effectiveLevel(levels.getAttack(), 1.0, 0);
		long meleeRoll = RollMath.attackRoll(effAttack, loadout.getOffensive().getAttackBonus("stab"));
		int effStrength = RollMath.effectiveLevel(levels.getStrength(), 1.0, 0);
		int meleeMax = RollMath.maxHitFromEffective(effStrength, loadout.getBonuses().getStrength());
		long defenceRoll = npcDefenceRoll(request.getMonster(), "stab", loadout.getWeapon());
		double meleeAccuracy = RollMath.normalAccuracy(meleeRoll, defenceRoll);
		return chance * RollMath.normalExpectedHit(meleeAccuracy, meleeMax);
	}

	private static boolean isCrystalBow(Loadout loadout)
	{
		return wearingActive(loadout, "crystal bow") || wearingActive(loadout, "bow of faerdhinen");
	}

	/**
	 * Crystal armour boost weights for the crystal bow / bow of faerdhinen:
	 * accuracy x(20+n)/20, damage x(40+n)/40 (helm 1, legs 2, body 3; full
	 * set +30% accuracy, +15% damage). Inactive pieces share the item name
	 * and differ only by version, so the check must be version-aware.
	 */
	private static int crystalArmourPieces(Loadout loadout)
	{
		int pieces = 0;
		if (wearingActive(loadout, "crystal helm"))
		{
			pieces += 1;
		}
		if (wearingActive(loadout, "crystal legs"))
		{
			pieces += 2;
		}
		if (wearingActive(loadout, "crystal body"))
		{
			pieces += 3;
		}
		return pieces;
	}

	/** wearing(), but "Inactive" versions (uncharged crystal) never match. */
	private static boolean wearingActive(Loadout loadout, String marker)
	{
		return loadout.activeNamesLower().contains(marker.toLowerCase(Locale.ROOT));
	}

	private static boolean isTzhaarWeapon(Loadout loadout)
	{
		return wearing(loadout, "tzhaar-ket-em")
			|| wearing(loadout, "tzhaar-ket-om")
			|| wearing(loadout, "toktz-xil-ak")
			|| wearing(loadout, "toktz-xil-ek")
			|| wearing(loadout, "toktz-mej-tal");
	}

	private static boolean isWearingObsidian(Loadout loadout)
	{
		return wearing(loadout, "obsidian helmet")
			&& wearing(loadout, "obsidian platebody")
			&& wearing(loadout, "obsidian platelegs");
	}

	private static int applyFlatArmour(OptimizationRequest request, int hit)
	{
		return Math.max(0, hit - request.getMonster().getDefensive().getFlatArmour());
	}

	private static long applyInquisitorBonus(Loadout loadout, long value)
	{
		int pieces = 0;
		if (wearing(loadout, "inquisitor's great helm"))
		{
			pieces++;
		}
		if (wearing(loadout, "inquisitor's hauberk"))
		{
			pieces++;
		}
		if (wearing(loadout, "inquisitor's plateskirt"))
		{
			pieces++;
		}
		if (pieces <= 0)
		{
			return value;
		}
		int numerator = wearing(loadout, "inquisitor's mace") ? 200 + pieces * 5 : 200 + (pieces == 3 ? 5 : pieces);
		return multiply(value, numerator, 200);
	}

	/** The twisted bow's percent multiplier (roll or hit becomes bonus% of
	 * itself) at this monster magic level - exposed as the number so the
	 * counted-bonus assurance can print exactly what applied. */
	private static int tbowBonus(int magic, boolean accuracyMode)
	{
		int factor = accuracyMode ? 10 : 14;
		int base = accuracyMode ? 140 : 250;
		int t2 = Math.floorDiv(3 * magic - factor, 100);
		int inner = Math.floorDiv(3 * magic, 10) - (10 * factor);
		int t3 = Math.floorDiv(inner * inner, 100);
		return base + t2 - t3;
	}

	private static int multiply(int value, int numerator, int denominator)
	{
		return (int) Math.floor((double) value * numerator / denominator);
	}

	private static long multiply(long value, int numerator, int denominator)
	{
		return (long) Math.floor((double) value * numerator / denominator);
	}
}
