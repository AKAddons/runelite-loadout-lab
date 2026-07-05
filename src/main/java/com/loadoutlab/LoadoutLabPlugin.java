package com.loadoutlab;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.loadoutlab.collection.CollectionLedger;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * Loadout Lab - best-in-slot from the gear YOU own.
 *
 * <p>Pick a monster; the plugin computes the strongest set you actually have,
 * per combat style, with exact DPS - from live knowledge of your bank,
 * inventory, and equipment, and a local DPS engine.
 *
 * <p>Architecture (see docs/ROADMAP.md):
 * <ul>
 *   <li>{@code engine/}     - pure DPS math + optimizer (derived from best-dps, BSD-2)</li>
 *   <li>{@code data/}       - monster stats + gear knowledge (gzipped resources)</li>
 *   <li>{@code collection/} - the persistent "what I own" ledger, per profile + world type</li>
 *   <li>{@code optimizer/}  - BiS search caching keyed on the ledger fingerprint</li>
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
	@Inject
	private Client client;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	private CollectionLedger ledger;

	/**
	 * Container-change coalescing (an event storm lesson: a bank deposit fires
	 * one ItemContainerChanged PER SLOT). Events only mark which sources are
	 * dirty; the per-tick drain does one scan per dirty source, max.
	 * Client-thread only.
	 */
	private final EnumSet<CollectionLedger.Source> dirtySources =
		EnumSet.noneOf(CollectionLedger.Source.class);

	@Override
	protected void startUp()
	{
		ledger = new CollectionLedger(configManager, gson);
		// Scope + load happen on login (world type isn't known before);
		// if we start mid-session, adopt the current state immediately.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			ledger.loadScope(worldScope());
			dirtySources.addAll(EnumSet.allOf(CollectionLedger.Source.class));
		}
		log.info("Loadout Lab started");
	}

	@Override
	protected void shutDown()
	{
		dirtySources.clear();
		ledger = null;
		log.info("Loadout Lab stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			// (Re)point the ledger at this world's scope - a seasonal login
			// must never write into the standard ledger.
			ledger.loadScope(worldScope());
			dirtySources.add(CollectionLedger.Source.EQUIPMENT);
			dirtySources.add(CollectionLedger.Source.INVENTORY);
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.EQUIPMENT.getId())
		{
			dirtySources.add(CollectionLedger.Source.EQUIPMENT);
		}
		else if (id == InventoryID.INVENTORY.getId())
		{
			dirtySources.add(CollectionLedger.Source.INVENTORY);
		}
		else if (id == InventoryID.BANK.getId())
		{
			dirtySources.add(CollectionLedger.Source.BANK);
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (dirtySources.isEmpty() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		for (CollectionLedger.Source source : EnumSet.copyOf(dirtySources))
		{
			InventoryID container = containerFor(source);
			ItemContainer c = client.getItemContainer(container);
			if (c == null)
			{
				// Container not available (e.g. bank closed before the drain) -
				// keep the last-known snapshot; that persistence IS the feature.
				dirtySources.remove(source);
				continue;
			}
			Map<Integer, Integer> items = new HashMap<>();
			for (Item item : c.getItems())
			{
				if (item.getId() > 0 && item.getQuantity() > 0)
				{
					items.merge(item.getId(), item.getQuantity(), Integer::sum);
				}
			}
			ledger.update(source, items);
			dirtySources.remove(source);
		}
	}

	/** The player's owned-items ledger (persistent across sessions). */
	public CollectionLedger getLedger()
	{
		return ledger;
	}

	private String worldScope()
	{
		return client.getWorldType().contains(WorldType.SEASONAL) ? "seasonal" : "std";
	}

	private static InventoryID containerFor(CollectionLedger.Source source)
	{
		switch (source)
		{
			case EQUIPMENT: return InventoryID.EQUIPMENT;
			case INVENTORY: return InventoryID.INVENTORY;
			default: return InventoryID.BANK;
		}
	}

	@Provides
	LoadoutLabConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LoadoutLabConfig.class);
	}
}
