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
		description = "What every NEW result assumes - the per-card chips and"
			+ " pickers still override per mob",
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
		name = "Counted bonuses / sets",
		description = "Show the 'Counting:' line naming the conditional bonuses applied.",
		section = display,
		position = 3
	)
	default boolean displayBonuses()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayDamageTaken",
		name = "Damage taken per second",
		description = "Show the incoming-damage line (prayed and unprayed).",
		section = display,
		position = 4
	)
	default boolean displayDamageTaken()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayDefensivePrayer",
		name = "Defensive prayer",
		description = "Show the pray-against call icon on the damage-taken line.",
		section = display,
		position = 5
	)
	default boolean displayDefensivePrayer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayRiskOnDeath",
		name = "Risk on death",
		description = "Show the wilderness risk-on-death line.",
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
		description = "Show the attack-style line (and the magic card's cast spell).",
		section = display,
		position = 8
	)
	default boolean displayAttackStyle()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayAssumes",
		name = "Assumed prayer / boost",
		description = "Show the assumed prayer and boost icons in the card header.",
		section = display,
		position = 9
	)
	default boolean displayAssumes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displaySpellbookChip",
		name = "Assumed spellbook chip",
		description = "Show the assumed-spellbook chip when thralls or Death Charge"
			+ " are on, red when you are not on that book.",
		section = display,
		position = 10
	)
	default boolean displaySpellbookChip()
	{
		return true;
	}

	@ConfigItem(
		keyName = "enableNotes",
		name = "Per-monster notes",
		description = "Show the '+ Note' control; saved notes survive a re-enable.",
		section = display,
		position = 11
	)
	default boolean enableNotes()
	{
		return true;
	}

	@ConfigItem(
		keyName = "loadingAnimation",
		name = "Loading animation",
		description = "Show the animated mascot while computing; off = a plain line.",
		section = display,
		position = 12
	)
	default boolean loadingAnimation()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayFootnote",
		name = "Footnote disclaimer",
		description = "Show the cross-check footnote with Copy report and Discord.",
		section = display,
		position = 13
	)
	default boolean displayFootnote()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayAddMob",
		name = "'+ Add mob' row",
		description = "Show the row that grows a result into a multi-mob roster.",
		section = display,
		position = 14
	)
	default boolean displayAddMob()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayGameBest",
		name = "Game best (BiS)",
		description = "Show the game-best ceiling view beside yours.",
		section = display,
		position = 15
	)
	default boolean displayGameBest()
	{
		return true;
	}

	@ConfigItem(
		keyName = "displayInventory",
		name = "Inventory row",
		description = "Show the Inventory row - swaps, spec weapon, consumables.",
		section = display,
		position = 16
	)
	default boolean displayInventory()
	{
		return true;
	}

	// --- Controls --------------------------------------------------------

	@ConfigItem(
		keyName = "defaultOnTask",
		name = "On slayer task",
		description = "Seed new results with On task enabled.",
		section = defaults,
		position = 1
	)
	default boolean defaultOnTask()
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

	enum DpsFold
	{
		IN_THE_NUMBERS,
		AS_A_FOOTNOTE,
		NOT_SHOWN;
	}

	@ConfigItem(
		keyName = "specDpsOutput",
		name = "Spec dps",
		description = "Where the carried spec weapon's added dps appears:"
			+ " folded into the shown numbers, as a footnote under the card,"
			+ " or not shown.",
		section = display,
		position = 17
	)
	default DpsFold specDpsOutput()
	{
		return DpsFold.IN_THE_NUMBERS;
	}

	@ConfigItem(
		keyName = "thrallDpsOutput",
		name = "Thrall dps",
		description = "Where the assumed thrall's dps appears: folded into"
			+ " the shown numbers, as a footnote under the card, or not shown.",
		section = display,
		position = 18
	)
	default DpsFold thrallDpsOutput()
	{
		return DpsFold.IN_THE_NUMBERS;
	}

	@ConfigItem(
		keyName = "showUpgradeBudget",
		name = "Upgrade budget",
		description = "Show the upgrade-budget field.",
		section = controls,
		position = 1
	)
	default boolean showUpgradeBudget()
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
		description = "Seed new results' wilderness risk cap (empty = unconstrained).",
		section = defaults,
		position = 15
	)
	default String defaultRiskCap()
	{
		return "75k";
	}

	@ConfigItem(
		keyName = "showSpellControls",
		name = "Spell selection",
		description = "Show the spellbook lock and per-mob autocast spell pin.",
		section = controls,
		position = 3
	)
	default boolean showSpellControls()
	{
		return true;
	}

	enum AssumeDefault
	{
		DETECT,
		NONE
	}

	@ConfigItem(
		keyName = "defaultAutocast",
		name = "Autocast",
		description = "Detect picks the best castable spell; None = powered staves only.",
		section = defaults,
		position = 11
	)
	default AssumeDefault defaultAutocast()
	{
		return AssumeDefault.DETECT;
	}

	enum MeleePrayerDefault
	{
		DETECT_BEST(null),
		NONE(null),
		PIETY("Piety"),
		CHIVALRY("Chivalry"),
		ULT_INCREDIBLE("Ultimate Strength + Incredible Reflexes"),
		SUPER_IMPROVED("Superhuman Strength + Improved Reflexes"),
		BURST_CLARITY("Burst of Strength + Clarity of Thought");

		/** The exact PrayerBonuses tier name the seed writes (null = none). */
		final String pick;

		MeleePrayerDefault(String pick)
		{
			this.pick = pick;
		}
	}

	@ConfigItem(
		keyName = "defaultMeleePrayer",
		name = "Melee prayer",
		description = "Seed the melee card's prayer assumption.",
		section = defaults,
		position = 3
	)
	default MeleePrayerDefault defaultMeleePrayer()
	{
		return MeleePrayerDefault.DETECT_BEST;
	}

	enum RangedPrayerDefault
	{
		DETECT_BEST,
		NONE,
		RIGOUR,
		DEADEYE,
		EAGLE_EYE,
		HAWK_EYE,
		SHARP_EYE;
	}

	@ConfigItem(
		keyName = "defaultRangedPrayer",
		name = "Ranged prayer",
		description = "Seed the ranged card's prayer assumption.",
		section = defaults,
		position = 4
	)
	default RangedPrayerDefault defaultRangedPrayer()
	{
		return RangedPrayerDefault.DETECT_BEST;
	}

	enum MagicPrayerDefault
	{
		DETECT_BEST,
		NONE,
		AUGURY,
		MYSTIC_VIGOUR,
		MYSTIC_MIGHT,
		MYSTIC_LORE,
		MYSTIC_WILL;
	}

	@ConfigItem(
		keyName = "defaultMagicPrayer",
		name = "Magic prayer",
		description = "Seed the magic card's prayer assumption.",
		section = defaults,
		position = 5
	)
	default MagicPrayerDefault defaultMagicPrayer()
	{
		return MagicPrayerDefault.DETECT_BEST;
	}

	enum MeleeBoostDefault
	{
		DETECT_BEST,
		NONE,
		SUPER_COMBAT,
		DIV_SUPER_COMBAT,
		ATK_STR_POTIONS,
		OVERLOAD,
		OVERLOAD_PLUS,
		SMELLING_SALTS;
	}

	@ConfigItem(
		keyName = "defaultMeleeBoost",
		name = "Melee boost",
		description = "Seed the melee card's potion/boost assumption.",
		section = defaults,
		position = 6
	)
	default MeleeBoostDefault defaultMeleeBoost()
	{
		return MeleeBoostDefault.DETECT_BEST;
	}

	enum RangedBoostDefault
	{
		DETECT_BEST,
		NONE,
		RANGING,
		DIVINE_RANGING,
		SUPER_RANGING,
		OVERLOAD,
		OVERLOAD_PLUS,
		SMELLING_SALTS;
	}

	@ConfigItem(
		keyName = "defaultRangedBoost",
		name = "Ranged boost",
		description = "Seed the ranged card's potion/boost assumption.",
		section = defaults,
		position = 7
	)
	default RangedBoostDefault defaultRangedBoost()
	{
		return RangedBoostDefault.DETECT_BEST;
	}

	enum MagicBoostDefault
	{
		DETECT_BEST,
		NONE,
		SATURATED_HEART,
		IMBUED_HEART,
		MAGIC,
		DIVINE_MAGIC,
		SUPER_MAGIC,
		OVERLOAD,
		OVERLOAD_PLUS,
		SMELLING_SALTS;
	}

	@ConfigItem(
		keyName = "defaultMagicBoost",
		name = "Magic boost",
		description = "Seed the magic card's potion/boost assumption.",
		section = defaults,
		position = 8
	)
	default MagicBoostDefault defaultMagicBoost()
	{
		return MagicBoostDefault.DETECT_BEST;
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
		keyName = "spellbookSwapVengeance",
		name = "Arceuus via Spellbook Swap",
		description = "Reach Arceuus casts from Lunar via Spellbook Swap (96 Magic);"
			+ " the kit adds the swap and Vengeance runes.",
		section = defaults,
		position = 12
	)
	default boolean spellbookSwapVengeance()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showExcludeControls",
		name = "Exclude controls",
		description = "Show the red exclude chips (global and per-mob).",
		section = controls,
		position = 4
	)
	default boolean showExcludeControls()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSimControls",
		name = "Sim controls",
		description = "Show the green sim chips (global and per-mob).",
		section = controls,
		position = 5
	)
	default boolean showSimControls()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showFilterControls",
		name = "Filter controls",
		description = "Show the grey filter chips (global and per-mob).",
		section = controls,
		position = 6
	)
	default boolean showFilterControls()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPinControls",
		name = "Pin controls",
		description = "Show the 'Pins: N' chip (right-click pinning stays).",
		section = controls,
		position = 7
	)
	default boolean showPinControls()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showInBankButton",
		name = "'Show in bank' button",
		description = "Show the button that outlines the set in your open bank.",
		section = controls,
		position = 8
	)
	default boolean showInBankButton()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showFilterBankButton",
		name = "'Filter bank' button",
		description = "Show the button that filters your bank to the set's items.",
		section = controls,
		position = 9
	)
	default boolean showFilterBankButton()
	{
		return true;
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

	enum AntifireDefault
	{
		DETECT,
		NONE,
		REGULAR,
		SUPER
	}

	@ConfigItem(
		keyName = "defaultAntifire",
		name = "Antifire",
		description = "Dragonfire seed: Detect = best owned potion, None = gear only.",
		section = defaults,
		position = 13
	)
	default AntifireDefault defaultAntifire()
	{
		return AntifireDefault.DETECT;
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
}
