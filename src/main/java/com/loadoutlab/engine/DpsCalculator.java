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
			|| !RatBoneRules.canUse(request.getMonster(), loadout.getWeapon()))
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
		if (result != null && factor < 1.0)
		{
			result = new DpsResult(result.getLoadout(), result.getDps() * factor,
				result.getAccuracy(), result.getExpectedHit() * factor,
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
		int maxHit = RollMath.maxHitFromEffective(effectiveDamage, effectiveRangedStrength(loadout));
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
		return new DpsResult(loadout, expected / (speed * RollMath.SECONDS_PER_TICK), accuracy, expected, maxHit, speed, rapid ? "ranged rapid" : "ranged accurate", attackRoll, defenceRoll);
	}

	private DpsResult calculateMagic(OptimizationRequest request, Loadout loadout)
	{
		OptimizationRequest effectiveRequest = isPoweredStaff(loadout) && request.getSpell() != null ? request.withSpell(null) : request;
		PlayerLevels levels = effectiveRequest.getLevels();
		PrayerBonuses prayers = effectiveRequest.getPrayers();
		int effectiveAccuracy = (int) Math.floor(levels.getMagic() * prayers.getMagicAccuracy()) + 9;
		if (isWearingMagicVoid(loadout))
		{
			effectiveAccuracy = (int) Math.floor(effectiveAccuracy * 1.45);
		}

		long attackRoll = RollMath.attackRoll(effectiveAccuracy, loadout.getOffensive().getMagic());
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
			return spell.getMaxHit();
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
		if (weaponName.contains("bone staff"))
		{
			return Math.max(1, magicLevel / 3 - 5) + 10;
		}
		return Math.max(1, magicLevel / 3 + 1);
	}

	private long npcDefenceRoll(MonsterStats monster, String attackType, GearItem weapon)
	{
		int level = "magic".equals(attackType) ? monster.getMagic() : monster.getDefence();
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
		if (isUndead(request) && wearing(loadout, "salve amulet (e)"))
		{
			return multiply(roll, 6, 5);
		}
		if (isUndead(request) && wearing(loadout, "salve amulet"))
		{
			return multiply(roll, 7, 6);
		}
		if (isSlayerTaskEligible(request) && isSlayerHelm(loadout))
		{
			return multiply(roll, 7, 6);
		}
		if (isTzhaarWeapon(loadout) && isWearingObsidian(loadout))
		{
			roll += multiply(baseRoll, 1, 10);
		}
		if (isDemon(request) && (wearing(loadout, "arclight") || wearing(loadout, "emberlight")))
		{
			roll = multiply(roll, 17, 10);
		}
		if (isDemon(request) && (wearing(loadout, "silverlight") || wearing(loadout, "darklight")))
		{
			roll = multiply(roll, 8, 5);
		}
		if (isKalphite(request) && wearing(loadout, "keris partisan of breaching"))
		{
			roll = multiply(roll, 133, 100);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter lance"))
		{
			return multiply(roll, 6, 5);
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
			return multiply(maxHit, 6, 5);
		}
		if (isUndead(request) && wearing(loadout, "salve amulet"))
		{
			return multiply(maxHit, 7, 6);
		}
		if (isSlayerTaskEligible(request) && isSlayerHelm(loadout))
		{
			return multiply(maxHit, 7, 6);
		}
		if (isDemon(request) && (wearing(loadout, "arclight") || wearing(loadout, "emberlight")))
		{
			maxHit = multiply(maxHit, 17, 10);
		}
		if (isDemon(request) && (wearing(loadout, "silverlight") || wearing(loadout, "darklight")))
		{
			maxHit = multiply(maxHit, 8, 5);
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
			return multiply(maxHit, 6, 5);
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
			return multiply(roll, 6, 5);
		}
		if (isUndead(request) && wearing(loadout, "salve amulet(i)"))
		{
			return multiply(roll, 7, 6);
		}
		if (isSlayerTaskEligible(request) && isImbuedSlayerHelm(loadout))
		{
			return multiply(roll, 23, 20);
		}
		if (isCrystalBow(loadout))
		{
			roll = multiply(roll, 20 + crystalArmourPieces(loadout), 20);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter crossbow"))
		{
			return multiply(roll, 13, 10);
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
			return multiply(maxHit, 6, 5);
		}
		if (isUndead(request) && wearing(loadout, "salve amulet(i)"))
		{
			return multiply(maxHit, 7, 6);
		}
		if (isSlayerTaskEligible(request) && isImbuedSlayerHelm(loadout))
		{
			return multiply(maxHit, 23, 20);
		}
		if (isCrystalBow(loadout))
		{
			maxHit = multiply(maxHit, 40 + crystalArmourPieces(loadout), 40);
		}
		if (isDragon(request) && wearing(loadout, "dragon hunter crossbow"))
		{
			return multiply(maxHit, 5, 4);
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
			return multiply(roll, 7, 4);
		}
		if (request.getSpell() != null && request.getSpell().getElement().equals(request.getMonster().getWeaknessElement()))
		{
			roll = multiply(roll, 100 + request.getMonster().getWeaknessSeverity(), 100);
		}
		if (isDemon(request) && request.getSpell() != null && request.getSpell().getName().contains("Demonbane"))
		{
			roll = multiply(roll, 6, 5);
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
			maxHit = multiply(maxHit, 7, 4);
		}
		if (request.getSpell() != null && request.getSpell().getElement().equals(request.getMonster().getWeaknessElement()))
		{
			maxHit += multiply(baseMaxHit, request.getMonster().getWeaknessSeverity(), 100);
		}
		if (isDemon(request) && request.getSpell() != null && request.getSpell().getName().contains("Demonbane"))
		{
			maxHit = multiply(maxHit, 6, 5);
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
