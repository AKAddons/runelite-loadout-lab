package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.MonsterStats;
import java.util.Set;

/**
 * Dragonfire: monsters whose wiki style list contains "Dragonfire"
 * (regular/metal/brutal/lava dragons, KBD, Vorkath, Elvarg - baby
 * dragons correctly lack it) need anti-dragon protection. By default
 * the set must provide it via a shield (which also rules out two-
 * handed weapons); the antifire-potion toggle assumes a super
 * antifire instead and lifts the constraint.
 */
public final class DragonfireRules
{
	/** Antifire potion dose ids per tier - the Detect scan and the
	 * consumable chips share them (relocated from the classic panel). */
	public static final int[] SUPER_ANTIFIRE_IDS = {
		21978, 21981, 21984, 21987, 22209, 22212, 22215, 22218};
	public static final int[] REGULAR_ANTIFIRE_IDS = {
		2452, 2454, 2456, 2458, 11951, 11953, 11955, 11957};

	private static final Set<String> PROTECTIVE_SHIELDS = Set.of(
		"anti-dragon shield",
		"dragonfire shield",
		"dragonfire ward",
		"ancient wyvern shield");

	private DragonfireRules()
	{
	}

	public static boolean breathesFire(MonsterStats monster)
	{
		if (monster == null)
		{
			return false;
		}
		for (String style : monster.getOffence().getStyles())
		{
			if ("dragonfire".equalsIgnoreCase(style))
			{
				return true;
			}
		}
		return false;
	}

	public static boolean isProtectiveShield(GearItem item)
	{
		return item != null
			&& PROTECTIVE_SHIELDS.contains(item.getNameLower());
	}

	/** Must this request's sets carry a dragonfire shield? */
	public static boolean shieldRequired(OptimizationRequest request)
	{
		return breathesFire(request.getMonster()) && !request.isAntifirePotion();
	}
}
