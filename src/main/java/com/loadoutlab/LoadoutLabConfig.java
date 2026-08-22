package com.loadoutlab;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("loadoutlab")
public interface LoadoutLabConfig extends Config
{
	@ConfigSection(
		name = "Display",
		description = "Which detail lines each set card shows",
		position = 0
	)
	String display = "display";

	@ConfigSection(
		name = "Controls",
		description = "Which parameter chips and buttons each card offers",
		position = 1
	)
	String controls = "controls";

	@ConfigSection(
		name = "Defaults",
		description = "What every new result assumes - chips and pickers"
			+ " still override per mob",
		position = 2
	)
	String defaults = "defaults";

	@ConfigSection(
		name = "Connections",
		description = "Other plugins Loadout Lab reads data from when they are installed",
		position = 3
	)
	String connections = "connections";

	// --- Display ---------------------------------------------------------

	@ConfigItem(
		keyName = "loadingAnimation",
		name = "Loading animation",
		description = "Show the animation while computing; off = a plain line.",
		section = display,
		position = 12
	)
	default boolean loadingAnimation()
	{
		return true;
	}

	// --- Controls --------------------------------------------------------

	@ConfigItem(
		keyName = "preferComboRunes",
		name = "Combo runes",
		description = "Show combination runes instead of separate elemental"
			+ " stacks where they cover the cost.",
		section = defaults,
		position = 3
	)
	default boolean preferComboRunes()
	{
		return false;
	}

	@ConfigItem(
		keyName = "defaultSpecWeapon",
		name = "Spec weapon",
		description = "Seed new results with the Spec chip on.",
		section = defaults,
		position = 2
	)
	default boolean defaultSpecWeapon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "defaultUpgradeBudget",
		name = "Upgrade budget",
		description = "Seed new results' budget (750k, 1m; - = unlimited; empty = owned only)",
		section = defaults,
		position = 14
	)
	default String defaultUpgradeBudget()
	{
		return "";
	}

	@ConfigItem(
		keyName = "showWildyRisk",
		name = "Wilderness risk options",
		description = "Show the low-risk, Protect Item and risk-cap controls.",
		section = controls,
		position = 2
	)
	default boolean showWildyRisk()
	{
		return true;
	}

	@ConfigItem(
		keyName = "defaultRiskCap",
		name = "Wilderness risk cap",
		description = "Seed new results' wilderness risk cap (empty = uncapped).",
		section = defaults,
		position = 15
	)
	default String defaultRiskCap()
	{
		return "75k";
	}

	enum AssumeDefault
	{
		DETECT,
		NONE
	}

	@ConfigItem(
		keyName = "defaultThralls",
		name = "Thralls",
		description = "Detect folds a thrall in where it benefits; None starts off.",
		section = defaults,
		position = 9
	)
	default AssumeDefault defaultThralls()
	{
		return AssumeDefault.DETECT;
	}

	@ConfigItem(
		keyName = "defaultDeathCharge",
		name = "Death Charge",
		description = "Detect assumes Death Charge where it benefits; None starts off.",
		section = defaults,
		position = 10
	)
	default AssumeDefault defaultDeathCharge()
	{
		return AssumeDefault.DETECT;
	}

	@ConfigItem(
		keyName = "npcRightClickEntry",
		name = "NPC right-click entry",
		description = "Add 'Search in Loadout Lab' to known monsters' right-click menus.",
		section = controls,
		position = 10
	)
	default boolean npcRightClickEntry()
	{
		return true;
	}

	// --- Connections -----------------------------------------------------

	@ConfigItem(
		keyName = "useDwmsData",
		name = "Use Dude, Where's My Stuff",
		description = "Count gear tracked by Dude, Where's My Stuff (2.11.5+,"
			+ " running) as owned - STASH, POH, death storage and more.",
		section = connections,
		position = 1
	)
	default boolean useDwmsData()
	{
		return true;
	}

	@ConfigItem(
		keyName = "fetchMonsterIcons",
		name = "Monster pictures (wiki)",
		description = "Fetch each mob row's picture from the OSRS wiki - one"
			+ " request per monster, cached. Off = no wiki requests, plain"
			+ " text rows.",
		section = connections,
		position = 2
	)
	default boolean fetchMonsterIcons()
	{
		return true;
	}

}
