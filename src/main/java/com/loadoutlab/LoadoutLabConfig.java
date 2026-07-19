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
		description = "Which parameter chips and buttons each result card offers",
		position = 1
	)
	String controls = "controls";

	@ConfigSection(
		name = "Connections",
		description = "Other plugins Loadout Lab reads data from when they are installed",
		position = 2
	)
	String connections = "connections";

	// --- Display ---------------------------------------------------------

	@ConfigItem(
		keyName = "displayMaxHit",
		name = "Max hit",
		description = "Show the max hit on each set card.",
		section = display,
		position = 1
	)
	default boolean displayMaxHit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayAccuracy",
		name = "Accuracy",
		description = "Show the hit chance on each set card.",
		section = display,
		position = 2
	)
	default boolean displayAccuracy()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayBonuses",
		name = "Counted bonuses",
		description = "Show the 'Counting:' line naming the conditional bonuses"
			+ " (salve, slayer helm, crystal set...) applied to the numbers.",
		section = display,
		position = 3
	)
	default boolean displayBonuses()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayAssumes",
		name = "Assumed prayer / boost",
		description = "Show the assumed prayer and potion/boost the numbers use"
			+ " (e.g. 'Piety + Super combat'), in the card header and the"
			+ " game-best section. The cast spell is under 'Attack style / spell'.",
		section = display,
		position = 4
	)
	default boolean displayAssumes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayDamageTaken",
		name = "Damage taken per second",
		description = "Show the incoming-damage line (what the monster does back"
			+ " to you, prayed and unprayed).",
		section = display,
		position = 5
	)
	default boolean displayDamageTaken()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayRiskOnDeath",
		name = "Risk on death",
		description = "Show the wilderness 'Risk: X gp (N kept on death)' line on"
			+ " each set (wilderness monsters only).",
		section = display,
		position = 6
	)
	default boolean displayRiskOnDeath()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayPrayerBonus",
		name = "Prayer bonus",
		description = "Show the set's total prayer bonus line.",
		section = display,
		position = 7
	)
	default boolean displayPrayerBonus()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayAttackStyle",
		name = "Attack style / spell",
		description = "Show the attack-style line (and, on the magic card, the"
			+ " cast spell).",
		section = display,
		position = 8
	)
	default boolean displayAttackStyle()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayGameBest",
		name = "Game best (BiS)",
		description = "Show the collapsible game-best ceiling under each set - the"
			+ " strongest set in the game and how close yours is.",
		section = display,
		position = 9
	)
	default boolean displayGameBest()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableNotes",
		name = "Per-monster notes",
		description = "Show the collapsible note ('+ Note') on each monster's"
			+ " panel for your own reminders. Turning this off hides the note"
			+ " control; saved notes are kept and reappear when re-enabled.",
		section = display,
		position = 10
	)
	default boolean enableNotes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "loadingAnimation",
		name = "Loading animation",
		description = "Show the animated mascot while the optimizer computes a"
			+ " set. Some moods are seasonal. Turn off for a plain loading line.",
		section = display,
		position = 12
	)
	default boolean loadingAnimation()
	{
		return true;
	}

	// --- Controls --------------------------------------------------------

	@ConfigItem(
		keyName = "showSpellControls",
		name = "Spell selection",
		description = "Show the spellbook lock and per-monster autocast spell"
			+ " pin on the magic card.",
		section = controls,
		position = 1
	)
	default boolean showSpellControls()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showUpgradeBudget",
		name = "Upgrade budget",
		description = "Show the upgrade-budget field (suggest buyable gear within"
			+ " a gp budget).",
		section = controls,
		position = 2
	)
	default boolean showUpgradeBudget()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showWildyRisk",
		name = "Wilderness risk options",
		description = "Show the low-risk, Protect Item, and risk-cap controls on"
			+ " wilderness monsters.",
		section = controls,
		position = 3
	)
	default boolean showWildyRisk()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showInBankButton",
		name = "'Show in bank' button",
		description = "Show the button that outlines the set's items in your"
			+ " open bank (uses the Bank Tags plugin).",
		section = controls,
		position = 4
	)
	default boolean showInBankButton()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showFilterBankButton",
		name = "'Filter bank' button",
		description = "Show the button that filters your open bank to only the"
			+ " set's items (uses the Bank Tags plugin).",
		section = controls,
		position = 5
	)
	default boolean showFilterBankButton()
	{
		return true;
	}

	@ConfigItem(
		keyName = "npcRightClickEntry",
		name = "NPC right-click entry",
		description = "Add a 'Search in Loadout Lab' option when you right-click"
			+ " a monster the plugin knows. Turn off to keep NPC menus clean;"
			+ " you can still search from the panel.",
		section = controls,
		position = 6
	)
	default boolean npcRightClickEntry()
	{
		return true;
	}

	@ConfigItem(
		keyName = "defaultUpgradeBudget",
		name = "Default upgrade budget",
		description = "Seed every new result's upgrade budget (750k, 1m, 1.5b;"
			+ " - = unlimited; empty = owned gear only)",
		section = controls,
		position = 90
	)
	default String defaultUpgradeBudget()
	{
		return "";
	}

	@ConfigItem(
		keyName = "defaultRiskCap",
		name = "Default wilderness risk cap",
		description = "Seed every new result's wilderness risk cap in gp"
			+ " (25k, 1m...; empty = unconstrained). Defaults to 75k so a"
			+ " wilderness search starts low-risk out of the box.",
		section = controls,
		position = 91
	)
	default String defaultRiskCap()
	{
		return "75k";
	}

	@ConfigItem(
		keyName = "defaultOnTask",
		name = "Default searches to On task",
		description = "Seed every new result with the On task toggle enabled"
			+ " (slayer-only monsters always start on-task regardless)",
		section = controls,
		position = 92
	)
	default boolean defaultOnTask()
	{
		return false;
	}

	enum AntifireDefault
	{
		DETECT,
		NONE,
		REGULAR,
		SUPER
	}

	@ConfigItem(
		keyName = "defaultAntifire",
		name = "Default antifire",
		description = "Seed dragonfire searches with this antifire mode:"
			+ " Detect picks the best potion your collection has, None"
			+ " computes gear-only protection",
		section = controls,
		position = 93
	)
	default AntifireDefault defaultAntifire()
	{
		return AntifireDefault.DETECT;
	}

	// --- Connections -----------------------------------------------------

	@ConfigItem(
		keyName = "useDwmsData",
		name = "Use Dude, Where's My Stuff",
		description = "Count gear tracked by the Dude, Where's My Stuff plugin as owned"
			+ " - STASH units, POH storage, death storage and more. Uses its live"
			+ " data while it runs, its saved data otherwise.",
		section = connections,
		position = 1
	)
	default boolean useDwmsData()
	{
		return true;
	}
}
