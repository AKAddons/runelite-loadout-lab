package com.loadoutlab;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Loadout Lab - best-in-slot from the gear YOU own.
 *
 * <p>Pick a monster; the plugin computes the strongest set you actually have,
 * per combat style, with exact DPS - from live knowledge of your bank,
 * inventory, and equipment, and a local port of the community DPS math.
 *
 * <p>Architecture (see docs/ROADMAP.md):
 * <ul>
 *   <li>{@code engine/}     - pure DPS math (accuracy, max hit, speed, gear nuances)</li>
 *   <li>{@code data/}       - monster stats + gear knowledge (resource files; hub token cap excludes resources)</li>
 *   <li>{@code collection/} - what the player owns (bank/inventory/equipment, persisted per profile)</li>
 *   <li>{@code optimizer/}  - BiS search over owned gear, cached by (collection, monster, style)</li>
 *   <li>{@code ui/}         - the panel</li>
 * </ul>
 */
@Slf4j
@PluginDescriptor(
	name = "Loadout Lab",
	description = "Best-in-slot sets from the gear you own, per enemy and combat style, with exact DPS",
	tags = {"gear", "bis", "dps", "loadout", "equipment"}
)
public class LoadoutLabPlugin extends Plugin
{
	@Override
	protected void startUp()
	{
		log.info("Loadout Lab started");
	}

	@Override
	protected void shutDown()
	{
		log.info("Loadout Lab stopped");
	}

	@Provides
	LoadoutLabConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LoadoutLabConfig.class);
	}
}
