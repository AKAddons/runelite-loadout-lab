package com.loadoutlab.engine;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.MonsterStats;
import java.util.Locale;
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
			&& PROTECTIVE_SHIELDS.contains(item.getName().toLowerCase(Locale.ROOT));
	}

	/** Must this request's sets carry a dragonfire shield? */
	public static boolean shieldRequired(OptimizationRequest request)
	{
		return breathesFire(request.getMonster()) && !request.isAntifirePotion();
	}
}
