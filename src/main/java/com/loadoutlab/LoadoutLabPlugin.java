package com.loadoutlab;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.loadoutlab.collection.CollectionLedger;
import com.loadoutlab.collection.ExclusionStore;
import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.RequirementProfile;
import com.loadoutlab.optimizer.OptimizerService;
import com.loadoutlab.ui.LoadoutLabPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

/**
 * Loadout Lab - best-in-slot from the gear YOU own.
 *
 * <p>Pick a monster; the plugin computes the strongest set you actually have,
 * per combat style, with exact DPS - from live knowledge of your bank,
 * inventory, and equipment, and a local DPS engine.
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
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Gson gson;

	@Inject
	private ItemManager itemManager;

	private CollectionLedger ledger;
	private ExclusionStore exclusions;
	private LoadoutData data;
	private OptimizerService optimizerService;
	private LoadoutLabPanel panel;
	private NavigationButton navButton;

	/**
	 * Requirement profile (levels + finished quests), snapshotted lazily on
	 * first compute and reused: Quest.getState runs a client script PER QUEST
	 * (~200 quests), so this must never run per-query or per-tick.
	 * Invalidated on login.
	 */
	private RequirementProfile requirementProfile;
	private PlayerLevels boostedLevels;

	/** Container-change coalescing - events mark, the per-tick drain scans. */
	private final EnumSet<CollectionLedger.Source> dirtySources =
		EnumSet.noneOf(CollectionLedger.Source.class);

	@Override
	protected void startUp()
	{
		ledger = new CollectionLedger(configManager, gson);
		exclusions = new ExclusionStore(configManager, gson);
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			ledger.loadScope(worldScope());
			dirtySources.addAll(EnumSet.allOf(CollectionLedger.Source.class));
		}

		// The dataset is ~3MB of gzipped JSON - parse off the startup path.
		Thread loader = new Thread(() ->
		{
			LoadoutData loaded = new DataService().load();
			SwingUtilities.invokeLater(() ->
			{
				data = loaded;
				optimizerService = new OptimizerService(loaded);
				panel = new LoadoutLabPanel(loaded, itemManager, this::computeForMonster, exclusions::toggle, exclusions::snapshot);
				panel.setF2pWorld(onF2pWorld());
				navButton = NavigationButton.builder()
					.tooltip("Loadout Lab")
					.icon(loadSidebarIcon())
					.priority(7)
					.panel(panel)
					.build();
				clientToolbar.addNavigation(navButton);
			});
		}, "loadout-lab-data-loader");
		loader.setDaemon(true);
		loader.start();

		log.info("Loadout Lab started");
	}

	@Override
	protected void shutDown()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		if (optimizerService != null)
		{
			optimizerService.shutdown();
			optimizerService = null;
		}
		panel = null;
		data = null;
		ledger = null;
		exclusions = null;
		requirementProfile = null;
		boostedLevels = null;
		dirtySources.clear();
		log.info("Loadout Lab stopped");
	}

	// ------------------------------------------------------------------
	// Owned-gear collection (see CollectionLedger)
	// ------------------------------------------------------------------

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			ledger.loadScope(worldScope());
			dirtySources.add(CollectionLedger.Source.EQUIPMENT);
			dirtySources.add(CollectionLedger.Source.INVENTORY);
			// New login = possibly a different account/levels: re-snapshot lazily.
			requirementProfile = null;
			boostedLevels = null;

			// Non-members world -> show the F2P filter, default on (world type
			// is client state, so read it here and hand the EDT a boolean).
			boolean f2p = onF2pWorld();
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.setF2pWorld(f2p);
				}
			});
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
			ItemContainer c = client.getItemContainer(containerFor(source));
			if (c == null)
			{
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

	// ------------------------------------------------------------------
	// Optimization flow: client thread (profile) -> worker (search) -> EDT (render)
	// ------------------------------------------------------------------

	private void computeForMonster(MonsterStats monster, boolean f2pOnly, boolean onSlayerTask, Runnable onDone)
	{
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				snapshotProfileIfNeeded();
			}
			RequirementProfile profile = requirementProfile != null
				? requirementProfile : RequirementProfile.MAXED;
			PlayerLevels levels = boostedLevels != null ? boostedLevels : PlayerLevels.MAXED;
			OwnedItems owned = new OwnedItems(ledger.owned(), ledger.bankKnown());
			int fingerprint = ledger.fingerprint();
			optimizerService.bestPerStyle(monster, levels, profile, owned, fingerprint, f2pOnly,
				onSlayerTask, exclusions.snapshot(),
				results -> SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.showResults(monster, results);
					}
					onDone.run();
				}));
		});
	}

	/** Client-thread only. Quest scan is the expensive part - done once per login. */
	private void snapshotProfileIfNeeded()
	{
		if (requirementProfile != null && boostedLevels != null)
		{
			return;
		}
		Map<Skill, Integer> real = new EnumMap<>(Skill.class);
		Map<Skill, Integer> boosted = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values())
		{
			real.put(skill, client.getRealSkillLevel(skill));
			boosted.put(skill, client.getBoostedSkillLevel(skill));
		}
		Set<String> quests = new HashSet<>();
		for (Quest quest : Quest.values())
		{
			if (quest.getState(client) == QuestState.FINISHED)
			{
				quests.add(quest.name());
			}
		}
		requirementProfile = new RequirementProfile(real, quests);
		boostedLevels = PlayerLevels.from(boosted);
	}

	// ------------------------------------------------------------------

	private String worldScope()
	{
		return client.getWorldType().contains(WorldType.SEASONAL) ? "seasonal" : "std";
	}

	/** True only when logged in to a non-members world - the F2P filter default. */
	private boolean onF2pWorld()
	{
		return client.getGameState() == GameState.LOGGED_IN
			&& !client.getWorldType().contains(WorldType.MEMBERS);
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

	/** Bundled sidebar icon (see scripts/generate_icons.py), drawn fallback if absent. */
	private static BufferedImage loadSidebarIcon()
	{
		try
		{
			return ImageUtil.loadImageResource(LoadoutLabPlugin.class, "icon.png");
		}
		catch (RuntimeException e)
		{
			log.warn("Bundled icon.png missing or unreadable; using drawn fallback", e);
			return drawIcon();
		}
	}

	/** Fallback sidebar icon drawn at runtime. */
	private static BufferedImage drawIcon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(new Color(45, 45, 55));
		g.fillRoundRect(0, 0, 16, 16, 4, 4);
		g.setColor(new Color(140, 200, 140));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 9));
		g.drawString("LL", 2, 12);
		g.dispose();
		return img;
	}

	/** The player's owned-items ledger (persistent across sessions). */
	public CollectionLedger getLedger()
	{
		return ledger;
	}

	@Provides
	LoadoutLabConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LoadoutLabConfig.class);
	}
}
