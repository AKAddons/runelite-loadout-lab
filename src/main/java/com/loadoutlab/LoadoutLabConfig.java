package com.loadoutlab;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("loadoutlab")
public interface LoadoutLabConfig extends Config
{
	// Config items land with their features (owned-gear tracking scope,
	// optimizer preferences, food/potion options later).

	@ConfigItem(
		keyName = "enableNotes",
		name = "Per-monster notes",
		description = "Show the collapsible note ('+ Note') on each monster's"
			+ " panel for your own reminders. Turning this off hides the note"
			+ " control; saved notes are kept and reappear when re-enabled.",
		position = 0
	)
	default boolean enableNotes()
	{
		return true;
	}

	@ConfigSection(
		name = "Connections",
		description = "Other plugins Loadout Lab reads data from when they are installed",
		position = 1
	)
	String connections = "connections";

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
