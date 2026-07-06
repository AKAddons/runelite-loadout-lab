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

	public DpsResult calculate(OptimizationRequest request, Loadout loadout)
	{
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
		return result;
	}

	private DpsResult calculateMelee(OptimizationRequest request, Loadout loadout)
	{
		DpsResult best = null;
		// Only the styles this weapon actually has: a whip cannot go
		// aggressive, a mace cannot slash (see WeaponStyles).
		for (WeaponStyles.MeleeStyle style : WeaponStyles.melee(loadout.getWeapon()))
		{
			best = best(best, meleeVariant(request, loadout, style.attackType,
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
		return new DpsResult(loadout, expected / (speed * RollMath.SECONDS_PER_TICK), accuracy, expected, maxHit, speed, attackType, attackRoll, defenceRoll);
	}

	private DpsResult calculateRanged(OptimizationRequest request, Loadout loadout)
	{
		PlayerLevels levels = request.getLevels();
		PrayerBonuses prayers = request.getPrayers();

		DpsResult rapid = rangedVariant(request, loadout, levels, prayers, 0, true);
		DpsResult accurate = rangedVariant(request, loadout, levels, prayers, 3, false);
		return best(rapid, accurate);
	}

	private DpsResult rangedVariant(OptimizationRequest request, Loadout loadout, PlayerLevels levels, PrayerBonuses prayers, int stanceBonus, boolean rapid)
	{
		int effectiveAccuracy = RollMath.effectiveLevel(levels.getRanged(), prayers.getRangedAccuracy(), stanceBonus);
		int effectiveDamage = RollMath.effectiveLevel(levels.getRanged(), prayers.getRangedStrength(), stanceBonus);

		if (isWearingRangedVoid(loadout))
		{
			effectiveAccuracy = (int) Math.floor(effectiveAccuracy * 1.10);
			effectiveDamage = (int) Math.floor(effectiveDamage * (isWearingEliteVoid(loadout) ? 1.125 : 1.10));
		}

		long attackRoll = RollMath.attackRoll(effectiveAccuracy, loadout.getOffensive().getRanged());
		int maxHit = RollMath.maxHitFromEffective(effectiveDamage,
			effectiveRangedStrength(loadout) + BlowpipeDarts.strength(request, loadout.getWeapon()));
		attackRoll = applyRangedAccuracyBonuses(request, loadout, attackRoll);
		maxHit = applyRangedDamageBonuses(request, loadout, maxHit);
		maxHit += RatBoneRules.flatMaxHitBonus(request.getMonster(), loadout.getWeapon());
		maxHit = applyFlatArmour(request, maxHit);

		long defenceRoll = npcDefenceRoll(request.getMonster(), "ranged", loadout.getWeapon());
		double accuracy = RollMath.normalAccuracy(attackRoll, defenceRoll);
		double expected = RollMath.normalExpectedHit(accuracy, maxHit);
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
		// Two-step prayer floors (Augury then Mystic Vigour's 1.18 accuracy),
		// +2 accurate stance, +9 - matches the official calc's effective level.
		int effectiveAccuracy = (int) Math.floor(Math.floor(levels.getMagic() * prayers.getMagicAccuracy())
			* prayers.getMagicAccuracySecondary()) + 2 + 9;
		if (isWearingMagicVoid(loadout))
		{
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
	private static int elementalSpellMax(SpellStats spell, int magicLevel)
	{
		if (spell.getElement().isEmpty())
		{
			return spell.getMaxHit();
		}
		String[] parts = spell.getName().split(" ");
		if (parts.length != 2 || !isElementWord(parts[0]))
		{
			return spell.getMaxHit();
		}
		int tier;
		switch (parts[1])
		{
			case "Strike": tier = magicLevel >= 13 ? 3 : magicLevel >= 9 ? 2 : magicLevel >= 5 ? 1 : 0; break;
			case "Bolt": tier = magicLevel >= 35 ? 3 : magicLevel >= 29 ? 2 : magicLevel >= 23 ? 1 : 0; break;
			case "Blast": tier = magicLevel >= 59 ? 3 : magicLevel >= 53 ? 2 : magicLevel >= 47 ? 1 : 0; break;
			case "Wave": tier = magicLevel >= 75 ? 3 : magicLevel >= 70 ? 2 : magicLevel >= 65 ? 1 : 0; break;
			case "Surge": tier = magicLevel >= 95 ? 3 : magicLevel >= 90 ? 2 : magicLevel >= 85 ? 1 : 0; break;
			default: return spell.getMaxHit();
		}
		// Class max hits: wind/water/earth/fire per tier.
		switch (parts[1])
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
		String category = weapon == null ? "" : weapon.getCategory().toLowerCase(Locale.ROOT);
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
			roll = multiply(roll, 11, 10);
		}
		// Golembane (Aug 2025: granite hammer 30%, barronite mace 15%) -
		// applied before the salve/slayer early returns because it stacks
		// multiplicatively with them (helm first, per the wiki).
		if (isGolem(request) && wearing(loadout, "granite hammer"))
		{
			roll = multiply(roll, 13, 10);
		}
		if (isGolem(request) && wearing(loadout, "barronite mace"))
		{
			roll = multiply(roll, 23, 20);
		}
		// Salve and the slayer helm are exclusive with EACH OTHER only -
		// weapon bonuses below (arclight, dhl, golembane...) stack on top.
		if (isUndead(request) && wearing(loadout, "salve amulet (e)"))
		{
			roll = multiply(roll, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet"))
		{
			roll = multiply(roll, 7, 6);
		}
		else if (isSlayerTaskEligible(request) && isSlayerHelm(loadout))
		{
			roll = multiply(roll, 7, 6);
		}
		if (isTzhaarWeapon(loadout) && isWearingObsidian(loadout))
		{
			roll += multiply(baseRoll, 1, 10);
		}
		if (isDemon(request) && (wearing(loadout, "arclight") || wearing(loadout, "emberlight")))
		{
			roll = multiply(roll, 17, 10);
		}
		if (isDemon(request) && (wearing(loadout, "bone claws") || wearing(loadout, "burning claws")))
		{
			roll = multiply(roll, 21, 20);
		}
		if (isKalphite(request) && wearing(loadout, "keris partisan of breaching"))
		{
			roll = multiply(roll, 133, 100);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter lance"))
		{
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
			maxHit = multiply(maxHit, 11, 10);
		}
		if (isGolem(request) && wearing(loadout, "granite hammer"))
		{
			maxHit = multiply(maxHit, 13, 10);
		}
		if (isGolem(request) && wearing(loadout, "barronite mace"))
		{
			maxHit = multiply(maxHit, 23, 20);
		}
		if (isUndead(request) && wearing(loadout, "salve amulet (e)"))
		{
			maxHit = multiply(maxHit, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet"))
		{
			maxHit = multiply(maxHit, 7, 6);
		}
		else if (isSlayerTaskEligible(request) && isSlayerHelm(loadout))
		{
			maxHit = multiply(maxHit, 7, 6);
		}
		if (isDemon(request) && (wearing(loadout, "arclight") || wearing(loadout, "emberlight")))
		{
			maxHit = multiply(maxHit, 17, 10);
		}
		if (isDemon(request) && (wearing(loadout, "silverlight") || wearing(loadout, "darklight")))
		{
			// Damage only - the wiki and official calc give these no accuracy bonus.
			maxHit = multiply(maxHit, 8, 5);
		}
		if (isDemon(request) && (wearing(loadout, "bone claws") || wearing(loadout, "burning claws")))
		{
			maxHit = multiply(maxHit, 21, 20);
		}
		if (isTzhaarWeapon(loadout) && isWearingObsidian(loadout))
		{
			maxHit += multiply(baseMaxHit, 1, 10);
		}
		if (isTzhaarWeapon(loadout) && wearing(loadout, "berserker necklace"))
		{
			maxHit = multiply(maxHit, 6, 5);
		}
		if (isKalphite(request) && wearing(loadout, "keris"))
		{
			maxHit = multiply(maxHit, wearing(loadout, "keris partisan of amascut") ? 115 : 133, 100);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter lance"))
		{
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
		if (isUndead(request) && wearing(loadout, "salve amulet(ei)"))
		{
			roll = multiply(roll, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet(i)"))
		{
			roll = multiply(roll, 7, 6);
		}
		else if (isSlayerTaskEligible(request) && isImbuedSlayerHelm(loadout))
		{
			roll = multiply(roll, 23, 20);
		}
		if (isCrystalBow(loadout))
		{
			roll = multiply(roll, 20 + crystalArmourPieces(loadout), 20);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter crossbow"))
		{
			roll = multiply(roll, 13, 10);
		}
		if (isDemon(request) && wearing(loadout, "scorching bow"))
		{
			roll = multiply(roll, 13, 10);
		}
		if (wearing(loadout, "twisted bow"))
		{
			int cap = request.getMonster().hasAttribute("xerician") ? 350 : 250;
			return tbowScaling(roll, Math.min(cap, Math.max(request.getMonster().getMagic(), request.getMonster().getOffensiveMagic())), true);
		}
		return roll;
	}

	private int applyRangedDamageBonuses(OptimizationRequest request, Loadout loadout, int maxHit)
	{
		if (isUndead(request) && wearing(loadout, "salve amulet(ei)"))
		{
			maxHit = multiply(maxHit, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet(i)"))
		{
			maxHit = multiply(maxHit, 7, 6);
		}
		else if (isSlayerTaskEligible(request) && isImbuedSlayerHelm(loadout))
		{
			maxHit = multiply(maxHit, 23, 20);
		}
		if (isCrystalBow(loadout))
		{
			maxHit = multiply(maxHit, 40 + crystalArmourPieces(loadout), 40);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter crossbow"))
		{
			maxHit = multiply(maxHit, 5, 4);
		}
		if (isDemon(request) && wearing(loadout, "scorching bow"))
		{
			maxHit = multiply(maxHit, 13, 10);
		}
		if (wearing(loadout, "twisted bow"))
		{
			int cap = request.getMonster().hasAttribute("xerician") ? 350 : 250;
			return (int) tbowScaling(maxHit, Math.min(cap, Math.max(request.getMonster().getMagic(), request.getMonster().getOffensiveMagic())), false);
		}
		return maxHit;
	}

	private long applyMagicAccuracyBonuses(OptimizationRequest request, Loadout loadout, long roll)
	{
		if (isUndead(request) && wearing(loadout, "salve amulet(ei)"))
		{
			roll = multiply(roll, 6, 5);
		}
		else if (isUndead(request) && wearing(loadout, "salve amulet(i)"))
		{
			roll = multiply(roll, 23, 20);
		}
		else if (isSlayerTaskEligible(request) && isImbuedSlayerHelm(loadout))
		{
			roll = multiply(roll, 23, 20);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter wand"))
		{
			roll = multiply(roll, 7, 4);
		}
		if (request.getSpell() != null && request.getSpell().getElement().equals(request.getMonster().getWeaknessElement()))
		{
			roll = multiply(roll, 100 + request.getMonster().getWeaknessSeverity(), 100);
		}
		if (isDemon(request) && request.getSpell() != null && request.getSpell().getName().contains("Demonbane"))
		{
			// Demonbane assumes Mark of Darkness (standard practice - it is
			// a cheap self-buff and the purging staff quintuples its
			// duration): 40% accuracy, 80% with the purging staff.
			roll = wearing(loadout, "purging staff") ? multiply(roll, 9, 5) : multiply(roll, 7, 5);
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
		if (isSlayerTaskEligible(request) && isImbuedSlayerHelm(loadout))
		{
			maxHit = multiply(maxHit, 23, 20);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter wand"))
		{
			// Accuracy is 7/4 but damage is 7/5 (official-verified) -
			// upstream applied 7/4 to both (ENGINE-GAPS bug #1).
			maxHit = multiply(maxHit, 7, 5);
		}
		if (request.getSpell() != null && request.getSpell().getElement().equals(request.getMonster().getWeaknessElement()))
		{
			maxHit += multiply(baseMaxHit, request.getMonster().getWeaknessSeverity(), 100);
		}
		if (isDemon(request) && request.getSpell() != null && request.getSpell().getName().contains("Demonbane"))
		{
			// Mark of Darkness damage: +25%, +50% with the purging staff.
			maxHit = wearing(loadout, "purging staff")
				? multiply(maxHit, 3, 2) : multiply(maxHit, 5, 4);
		}
		return maxHit;
	}

	private static DpsResult best(DpsResult left, DpsResult right)
	{
		if (left == null)
		{
			return right;
		}
		if (right == null)
		{
			return left;
		}
		return right.getDps() > left.getDps() ? right : left;
	}

	private static boolean wearing(Loadout loadout, String marker)
	{
		String needle = marker.toLowerCase(Locale.ROOT);
		for (Map.Entry<GearSlot, GearItem> entry : loadout.getGear().entrySet())
		{
			GearItem item = entry.getValue();
			if (item != null && item.getName().toLowerCase(Locale.ROOT).contains(needle))
			{
				return true;
			}
		}
		return false;
	}

	private static String name(GearItem item)
	{
		return item == null ? "" : item.getName().toLowerCase(Locale.ROOT);
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

	private static boolean isSlayerHelm(Loadout loadout)
	{
		for (GearItem item : loadout.getGear().values())
		{
			if (item != null && item.isSlayerHead())
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isImbuedSlayerHelm(Loadout loadout)
	{
		for (GearItem item : loadout.getGear().values())
		{
			if (item != null && item.isImbuedSlayerHead())
			{
				return true;
			}
		}
		return false;
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

	private static boolean isSlayerTaskEligible(OptimizationRequest request)
	{
		return request.isOnSlayerTask() && request.getMonster().isSlayerMonster();
	}

	private static boolean isPoweredStaff(Loadout loadout)
	{
		GearItem weapon = loadout.getWeapon();
		String weaponName = name(weapon);
		String category = weapon == null ? "" : weapon.getCategory().toLowerCase(Locale.ROOT);
		return category.contains("powered staff")
			|| weaponName.contains("trident")
			|| weaponName.contains("thammaron")
			|| weaponName.contains("accursed sceptre")
			|| weaponName.contains("sanguinesti")
			|| weaponName.contains("tumeken")
			|| weaponName.contains("warped sceptre")
			|| weaponName.contains("eye of ayak")
			|| weaponName.contains("bone staff");
	}

	private static boolean isFang(Loadout loadout)
	{
		return wearing(loadout, "osmumten's fang");
	}

	private static boolean isScythe(Loadout loadout)
	{
		return wearing(loadout, "scythe of vitur");
	}

	private static boolean isCrystalBow(Loadout loadout)
	{
		return wearing(loadout, "crystal bow") || wearing(loadout, "bow of faerdhinen");
	}

	private static int crystalArmourPieces(Loadout loadout)
	{
		int pieces = 0;
		if (wearing(loadout, "crystal helm"))
		{
			pieces += 1;
		}
		if (wearing(loadout, "crystal legs"))
		{
			pieces += 2;
		}
		if (wearing(loadout, "crystal body"))
		{
			pieces += 3;
		}
		return pieces;
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

	private static long tbowScaling(long current, int magic, boolean accuracyMode)
	{
		int factor = accuracyMode ? 10 : 14;
		int base = accuracyMode ? 140 : 250;
		int t2 = Math.floorDiv(3 * magic - factor, 100);
		int inner = Math.floorDiv(3 * magic, 10) - (10 * factor);
		int t3 = Math.floorDiv(inner * inner, 100);
		int bonus = base + t2 - t3;
		return Math.floorDiv(current * bonus, 100);
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
