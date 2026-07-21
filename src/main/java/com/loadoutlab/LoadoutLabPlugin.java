package com.loadoutlab;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.loadoutlab.collection.CollectionLedger;
import com.loadoutlab.command.CommandHistory;
import com.loadoutlab.command.Commands;
import com.loadoutlab.collection.DreamStore;
import com.loadoutlab.collection.DwmsImport;
import com.loadoutlab.collection.DwmsLink;
import com.loadoutlab.collection.ExclusionStore;
import com.loadoutlab.collection.ManualOwnedStore;
import com.loadoutlab.collection.StoragesApi;
import com.loadoutlab.data.DataService;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.OwnedItems;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.PlayerLevels;
import com.loadoutlab.engine.PrayerUnlocks;
import com.loadoutlab.engine.RequirementProfile;
import com.loadoutlab.optimizer.OptimizerService;
import com.loadoutlab.profile.PlayerProfile;
import com.loadoutlab.ui.LoadoutLabPanel;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
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
import net.runelite.api.ScriptID;
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.ClientUI;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import net.runelite.client.plugins.banktags.tabs.TabManager;
import net.runelite.client.plugins.banktags.tabs.TagTab;
import net.runelite.client.game.chatbox.ChatboxItemSearch;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.events.ConfigChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.AccountHashChanged;
import net.runelite.api.NPC;
import net.runelite.api.MenuEntry;
import net.runelite.api.MenuAction;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.ArrayList;

/**
 * Loadout Lab - best-in-slot from the gear YOU own.
 *
 * <p>Pick a monster; the plugin computes the strongest set you actually have,
 * per combat style, with exact DPS - from live knowledge of your bank,
 * inventory, and equipment, and a local DPS engine.
 */
@Slf4j
@net.runelite.client.plugins.PluginDependency(net.runelite.client.plugins.banktags.BankTagsPlugin.class)
@PluginDescriptor(
	name = "Loadout Lab",
	description = "Best-in-slot sets from the gear you own, per enemy and combat style, with exact DPS",
	tags = {"gear", "bis", "dps", "loadout", "equipment"}
)
public class LoadoutLabPlugin extends Plugin implements LoadoutLabPanel.ComputeHook
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
	private LoadoutLabConfig config;

	@Inject
	private Gson gson;

	@Inject
	@javax.inject.Named("developerMode")
	private boolean developerMode;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private BankTagsService bankTagsService;

	@Inject
	private TagManager tagManager;

	@Inject
	private LayoutManager layoutManager;

	@Inject
	private TabManager tabManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private okhttp3.OkHttpClient okHttpClient;

	private com.loadoutlab.ui.MonsterIcons monsterIcons;

	@Inject
	private ChatboxItemSearch chatboxItemSearch;

	@Inject
	private ClientUI clientUI;

	@Inject
	private EventBus eventBus;

	@Inject
	private PluginManager pluginManager;

	private CollectionLedger ledger;
	private ExclusionStore exclusions;
	/** Session-only undo/redo over deliberate store mutations. EDT-owned;
	 * cleared on profile change (entries captured against another profile's
	 * stores must never replay into this one). */
	private final CommandHistory commandHistory = new CommandHistory();
	private com.loadoutlab.collection.ProtectOnlyStore protectOnly;
	/** "Show in bank": the expanded id set the overlay outlines; null = off. */
	private volatile Set<Integer> bankHighlight;
	/** "Filter bank": a VIRTUAL bank tag (never persisted to the player's
	 * tag config) containing the active set's expanded ids; null = off. */
	private volatile Set<Integer> bankFilter;
	private static final String BANK_TAG = "loadout-lab";
	/** DWMS's exact plugin name - detection and icon lookup key off it. */
	private static final String DWMS_PLUGIN_NAME = "Dude, Where's My Stuff?";
	private com.loadoutlab.ui.BankHighlightOverlay bankOverlay;
	private DreamStore dreams;
	private ManualOwnedStore manualOwned;
	private com.loadoutlab.collection.MonsterProfileStore mobProfiles;
	private com.loadoutlab.collection.AlwaysFilterStore alwaysFilter;
	private com.loadoutlab.collection.SupplyDefaultsStore supplyDefaults;
	private DwmsImport dwmsImport;
	private DwmsLink dwmsLink;
	private LoadoutData data;
	/** Vendored STASH-unit table; loaded off-thread, read on game ticks. */
	private volatile com.loadoutlab.data.StashUnits stashUnits;
	/** One chart read per opening, mirroring the container-scan coalescing. */
	private boolean stashChartSeen;
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
	private PlayerLevels realLevels;
	private PlayerLevels boostedLevels;
	private PrayerUnlocks prayerUnlocks;

	/** Container-change coalescing - events mark, the per-tick drain scans. */
	private final EnumSet<CollectionLedger.Source> dirtySources =
		EnumSet.noneOf(CollectionLedger.Source.class);

	/** Event-time snapshots for storage containers that cannot be
	 * re-fetched by id at drain time (see onItemContainerChanged). */
	private final Map<CollectionLedger.Source, Map<Integer, Integer>> pendingScans =
		new EnumMap<>(CollectionLedger.Source.class);

	/** Ticks left to re-scan the looting bag after its Check screen opens
	 * (the contents can land a tick after the widget). */
	private int lootingBagScanTicks;

	/**
	 * Cross-plugin link-in: other plugins (e.g. Goal Planner) post
	 * PluginMessage(namespace "loadoutlab", name "search") with data
	 * {"monster": String display name, "npcId": Integer optional,
	 * "source": String} to open the panel on that monster. The message
	 * arrives on the POSTER's thread - everything marshals to the EDT.
	 */
	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		// DWMS storages-response (see DwmsLink): arrives on DWMS's client
		// thread; the parse is pure and the snapshot swap volatile, so only
		// the provenance-label refresh marshals (to the EDT).
		if (DwmsLink.DWMS_NAMESPACE.equals(event.getNamespace())
			&& DwmsLink.RESPONSE_NAME.equals(event.getName()))
		{
			DwmsLink link = dwmsLink;
			if (link != null)
			{
				// The live snapshot feeds the next compute via the ownership
				// fingerprint; nothing on screen shows it directly anymore
				// (the connection lives in the config panel now).
				link.accept(event.getData());
			}
			return;
		}
		// Our half of the bidirectional storages contract (see StoragesApi):
		// another plugin asks for the owned-gear ledger; the reply is built
		// and posted on the client thread (ids canonicalize there).
		if (StoragesApi.NAMESPACE.equals(event.getNamespace())
			&& StoragesApi.REQUEST_NAME.equals(event.getName()))
		{
			String requester = StoragesApi.requester(event.getData());
			if (requester != null)
			{
				clientThread.invokeLater(() -> respondWithStorages(requester));
			}
			return;
		}
		if (!"loadoutlab".equals(event.getNamespace()) || !"search".equals(event.getName()))
		{
			return;
		}
		Object monster = event.getData().get("monster");
		Object npcId = event.getData().get("npcId");
		String name = monster instanceof String ? (String) monster : null;
		Integer id = npcId instanceof Number ? ((Number) npcId).intValue() : null;
		SwingUtilities.invokeLater(() ->
		{
			if (panel == null || navButton == null)
			{
				return; // dataset still loading - drop rather than queue
			}
			if (panel.selectExternal(name, id))
			{
				clientToolbar.openPanel(navButton);
			}
		});
	}

	/**
	 * In-world link-in: right-clicking an NPC the dataset knows adds a
	 * "Search in Loadout Lab" entry. Client thread; the click marshals to
	 * the EDT and reuses the same select-and-open path as onPluginMessage.
	 */
	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (data == null || panel == null || navButton == null || !config.npcRightClickEntry())
		{
			return;
		}
		for (MenuEntry entry : event.getMenuEntries())
		{
			NPC npc = entry.getNpc();
			if (npc == null || npc.getName() == null)
			{
				continue;
			}
			final String name = npc.getName();
			final int id = npc.getId();
			if (!knownMonster(name, id))
			{
				return; // an NPC menu, but not one we can compute for
			}
			client.createMenuEntry(1)
				.setOption("Search in Loadout Lab")
				.setTarget(entry.getTarget())
				.setType(MenuAction.RUNELITE)
				.onClick(e -> SwingUtilities.invokeLater(() ->
				{
					if (panel.selectExternal(name, id))
					{
						clientToolbar.openPanel(navButton);
					}
				}));
			return; // one entry, even when several rows reference the NPC
		}
	}

	/** Cheap client-thread gate: exact id or normalized-name match. */
	private boolean knownMonster(String name, int id)
	{
		String normalized = name.toLowerCase().replaceAll("[^a-z0-9]", "");
		for (com.loadoutlab.data.MonsterStats m : data.getMonsters())
		{
			if (m.getId() == id
				|| m.getName().toLowerCase().replaceAll("[^a-z0-9]", "").equals(normalized))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	protected void startUp()
	{
		ledger = new CollectionLedger(configManager, gson);
		exclusions = new ExclusionStore(configManager, gson);
		protectOnly = new com.loadoutlab.collection.ProtectOnlyStore(configManager, gson);
		dreams = new DreamStore(configManager, gson);
		manualOwned = new ManualOwnedStore(configManager, gson);
		mobProfiles = new com.loadoutlab.collection.MonsterProfileStore(configManager, gson);
		alwaysFilter = new com.loadoutlab.collection.AlwaysFilterStore(configManager, gson);
		supplyDefaults = new com.loadoutlab.collection.SupplyDefaultsStore(configManager, gson);
		dwmsImport = new DwmsImport(configManager);
		dwmsLink = new DwmsLink();
		bankOverlay = new com.loadoutlab.ui.BankHighlightOverlay(() -> bankHighlight);
		overlayManager.add(bankOverlay);
		// Bank-tag hygiene: drop the layout Bank Tag Layouts auto-enabled on
		// our tag before the real-tab fix existed, any core layout a crash
		// left behind, and our tab name if a user tab-edit ever persisted
		// the in-memory tab into the tagtabs CSV (we never save it ourselves).
		configManager.unsetConfiguration(BTL_GROUP, BTL_LAYOUT_KEY);
		layoutManager.removeLayout(BANK_TAG);
		String tagtabs = configManager.getConfiguration("banktags", "tagtabs");
		if (tagtabs != null && tagtabs.contains(BANK_TAG))
		{
			List<String> names = new ArrayList<>(Text.fromCSV(tagtabs));
			if (names.remove(BANK_TAG))
			{
				configManager.setConfiguration("banktags", "tagtabs", Text.toCSV(names));
				configManager.unsetConfiguration("banktags", "icon_" + BANK_TAG);
			}
		}
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			ledger.loadScope(worldScope());
			manualOwned.loadScope(worldScope());
			dwmsImport.reload();
			requestDwmsStorages();
			dirtySources.addAll(EnumSet.allOf(CollectionLedger.Source.class));
		}

		// The dataset is ~3MB of gzipped JSON - parse off the startup path.
		Thread loader = new Thread(() ->
		{
			try
			{
				stashUnits = com.loadoutlab.data.StashUnits.load();
			}
			catch (RuntimeException ex)
			{
				log.warn("STASH unit table unavailable; chart scans disabled", ex);
			}
			LoadoutData loaded = new DataService().load();
			SwingUtilities.invokeLater(() ->
			{
				data = loaded;
				optimizerService = new OptimizerService(loaded);
			// The plugin IS the compute hook (see compute/computeRoster
			// below) - no delegating anonymous class needed.
			panel = new LoadoutLabPanel(loaded, itemManager, spriteManager, this,
					id ->
					{
						exec(Commands.toggleExclusion(exclusions, id, itemLabel(id)));
						return exclusions.isExcluded(id);
					},
					exclusions::snapshot,
					protectOnlyView(),
					id ->
					{
						exec(Commands.toggleDream(dreams, id, itemLabel(id)));
						return dreams.isDreamed(id);
					},
					dreams::snapshot,
					id ->
					{
						exec(Commands.toggleStored(manualOwned, id, itemLabel(id)));
						return manualOwned.isStored(id);
					},
					manualOwned::snapshot,
					locationHintView(),
					mobProfileView(), itemSearchView(),
					this::ownsCanonical,
					this::setBankHighlight,
					this::setBankFilter);
				panel.setHistoryControl(historyControl());
				panel.setF2pWorld(onF2pWorld());
				panel.setDisplayOptions(buildDisplayOptions());
				panel.setSupplyDefaults(buildSupplyDefaults());
				panel.setGlobalFilters(globalFiltersView());
				panel.setDeveloperMode(developerMode);
				monsterIcons = new com.loadoutlab.ui.MonsterIcons(okHttpClient);
				panel.setMonsterIcons(monsterIcons);
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
		if (monsterIcons != null)
		{
			monsterIcons.shutdown();
			monsterIcons = null;
		}
		if (bankOverlay != null)
		{
			overlayManager.remove(bankOverlay);
			bankOverlay = null;
		}
		bankHighlight = null;
		bankFilter = null;
		layoutManager.removeLayout(BANK_TAG);
		removeBankTagTab();
		tagManager.unregisterTag(BANK_TAG);
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
		protectOnly = null;
		manualOwned = null;
		mobProfiles = null;
		dwmsImport = null;
		dwmsLink = null;
		stashUnits = null;
		stashChartSeen = false;
		requirementProfile = null;
		realLevels = null;
		boostedLevels = null;
		prayerUnlocks = null;
		dirtySources.clear();
		pendingScans.clear();
		log.info("Loadout Lab stopped");
	}

	// ------------------------------------------------------------------
	// Owned-gear collection (see CollectionLedger)
	// ------------------------------------------------------------------

	/** A different character logged in: nothing from the previous one may
	 * survive - ledger scope, caches, snapshot, panel results, bank tools. */
	@Subscribe
	public void onAccountHashChanged(AccountHashChanged event)
	{
		resetForIdentityChange();
	}

	/** The Connections toggle changed: effective ownership changed with it,
	 * so refresh the shown answer (the ownership fingerprint keys the
	 * optimizer cache - neither direction can serve a stale set). */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"loadoutlab".equals(event.getGroup()))
		{
			return;
		}
		// The stores share this config group (ledger, exclusions, dreams,
		// profiles all write "loadoutlab" keys) and fire ConfigChanged on
		// every drain - a bank deposit is an event storm. React ONLY to the
		// wrench-panel keys, never a store write, or each ledger flush would
		// rebuild the whole results panel on the EDT.
		String key = event.getKey();
		if ("useDwmsData".equals(key))
		{
			// Ownership changed -> recompute (not just re-render).
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.recomputeCurrent();
				}
			});
			return;
		}
		if (!PANEL_CONFIG_KEYS.contains(key))
		{
			return; // a store write, not a display toggle
		}
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.setDisplayOptions(buildDisplayOptions());
				panel.setSupplyDefaults(buildSupplyDefaults());
			}
		});
	}

	/** The wrench-panel display/control keys (see LoadoutLabConfig) - the
	 * only keys onConfigChanged reacts to besides useDwmsData. */
	private static final Set<String> PANEL_CONFIG_KEYS = Set.of(
		"displayMaxHit", "displayAccuracy", "displayBonuses", "displayAssumes",
		"displayDamageTaken", "displayRiskOnDeath", "displayPrayerBonus",
		"displayAttackStyle", "displayGameBest", "enableNotes", "showSpellControls",
		"showUpgradeBudget", "showWildyRisk", "showInBankButton", "showFilterBankButton",
		"loadingAnimation", "displaySpellbookChip", "defaultUpgradeBudget",
		"defaultRiskCap");

	/** The panel's grey-trio hook: the global always-filter list plus
	 * wrench-panel supply defaults editable straight from the chip menu
	 * (the config write loops back through PANEL_CONFIG_KEYS, so the
	 * panel refreshes exactly like a wrench edit). */
	private LoadoutLabPanel.GlobalFilters globalFiltersView()
	{
		return new LoadoutLabPanel.GlobalFilters()
		{
			@Override
			public Map<Integer, String> alwaysFiltered()
			{
				return alwaysFilter == null ? Map.of() : alwaysFilter.all();
			}

			@Override
			public void addAlwaysFiltered(int itemId, String name)
			{
				alwaysFilter.add(itemId, name);
			}

			@Override
			public void removeAlwaysFiltered(int itemId)
			{
				alwaysFilter.remove(itemId);
			}

			@Override
			public void setSupplyDefault(String category, String enumName)
			{
				supplyDefaults.setChoice(category, enumName);
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.setSupplyDefaults(buildSupplyDefaults());
					}
				});
			}
		};
	}

	/** The persistent trip-supply defaults (choice keys by TripSupplies
	 * category) - store-backed, every category DETECT_BEST until the grey
	 * chip's menu changes it. The panel resolves them per result. */
	private Map<String, String> buildSupplyDefaults()
	{
		Map<String, String> defaults = new LinkedHashMap<>();
		for (String category : new String[]{
			com.loadoutlab.data.TripSupplies.FOOD,
			com.loadoutlab.data.TripSupplies.FAST_FOOD,
			com.loadoutlab.data.TripSupplies.PRAYER_RESTORE,
			com.loadoutlab.data.TripSupplies.SURGE,
			com.loadoutlab.data.TripSupplies.SPELLBOOK_CAPE,
			com.loadoutlab.data.TripSupplies.ANTIVENOM,
			"arceuusAccess"})
		{
			defaults.put(category, supplyDefaults == null
				? com.loadoutlab.collection.SupplyDefaultsStore.DETECT_BEST
				: supplyDefaults.choice(category));
		}
		return defaults;
	}

	private LoadoutLabPanel.DisplayOptions buildDisplayOptions()
	{
		return new LoadoutLabPanel.DisplayOptions(
			config.displayMaxHit(),
			config.displayAccuracy(),
			config.displayBonuses(),
			config.displayAssumes(),
			config.displayDamageTaken(),
			config.displayRiskOnDeath(),
			config.displayPrayerBonus(),
			config.displayAttackStyle(),
			config.displayGameBest(),
			config.enableNotes(),
			config.showSpellControls(),
			config.showUpgradeBudget(),
			config.showWildyRisk(),
			config.showInBankButton(),
			config.showFilterBankButton(),
			config.loadingAnimation(),
			config.displaySpellbookChip(),
			config.defaultUpgradeBudget(),
			config.defaultRiskCap(),
			config.defaultOnTask(),
			config.defaultAntifire() == LoadoutLabConfig.AntifireDefault.DETECT ? -1
				: config.defaultAntifire().ordinal() - 1);
	}

	/** The RuneLite config profile changed: config-backed stores re-read. */
	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		if (exclusions != null)
		{
			exclusions.reload();
		}
		if (protectOnly != null)
		{
			protectOnly.reload();
		}
		if (dreams != null)
		{
			dreams.reload();
		}
		if (mobProfiles != null)
		{
			mobProfiles.reload();
		}
		if (alwaysFilter != null)
		{
			alwaysFilter.reload();
		}
		if (supplyDefaults != null)
		{
			supplyDefaults.reload();
		}
		// Undo entries captured against the previous profile's stores must
		// never replay into this one. The stack is EDT-owned - hop over.
		SwingUtilities.invokeLater(() ->
		{
			commandHistory.clear();
			if (panel != null)
			{
				panel.refreshHistoryButtons();
			}
		});
		resetForIdentityChange();
	}

	private void resetForIdentityChange()
	{
		if (ledger != null)
		{
			ledger.loadScope(worldScope());
		}
		if (manualOwned != null)
		{
			manualOwned.loadScope(worldScope());
		}
		if (dwmsImport != null)
		{
			dwmsImport.reload();
		}
		if (dwmsLink != null)
		{
			// The live snapshot belongs to the PREVIOUS identity; drop it and
			// re-ask. DWMS re-answers for whoever is logged in now.
			dwmsLink.reset();
			requestDwmsStorages();
		}
		requirementProfile = null;
		realLevels = null;
		boostedLevels = null;
		prayerUnlocks = null;
		canonicalOwnedCache = null;
		bankHighlight = null;
		bankFilter = null;
		if (optimizerService != null)
		{
			optimizerService.clearCache();
		}
		dirtySources.addAll(EnumSet.allOf(CollectionLedger.Source.class));
		pendingScans.clear();
		stashChartSeen = false;
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.resetForIdentityChange();
			}
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			ledger.loadScope(worldScope());
			manualOwned.loadScope(worldScope());
			dwmsImport.reload();
			requestDwmsStorages();
			dirtySources.add(CollectionLedger.Source.EQUIPMENT);
			dirtySources.add(CollectionLedger.Source.INVENTORY);
			// New login = possibly a different account/levels: re-snapshot lazily.
			requirementProfile = null;
			realLevels = null;
			boostedLevels = null;
			prayerUnlocks = null;

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
		// Storage containers (POH costume storage confirmed in the field)
		// arrive with the 0x8000 flag set on the id - the same masking
		// DWMS applies. Field report: the treasure chest fired 0x8000|637
		// and the unmasked comparison missed it.
		int id = event.getContainerId();
		if (id >= 0x8000)
		{
			id -= 0x8000;
		}
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
		else
		{
			// EQUIPMENT/INVENTORY/BANK are consumed by the branches above,
			// so this only ever resolves storage-style containers.
			CollectionLedger.Source source = CollectionLedger.Source.forContainer(id);
			if (source != null)
			{
				// These containers may not be re-fetchable later under the
				// unmasked id, so capture the contents off the event now
				// (rare one-shot opens, not bank-style event storms); the
				// per-tick drain still does the coalesced ledger write.
				ItemContainer container = event.getItemContainer();
				if (container != null)
				{
					pendingScans.put(source, itemsOf(container));
				}
				dirtySources.add(source);
			}
		}
	}

	/**
	 * Checking the looting bag does NOT fire ItemContainerChanged
	 * (field-tested; DWMS polls this container per tick for the same
	 * reason). The Check screen opening is the capture moment - scan the
	 * container for a few ticks so contents that land a tick late are
	 * still seen. An empty-over-empty scan is a no-op write.
	 */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.WILDERNESS_LOOTINGBAG)
		{
			lootingBagScanTicks = 3;
		}
	}

	// The container id per source (looting bag, POH costume storage, the
	// five sailing cargo holds) now lives on CollectionLedger.Source, so
	// both directions of the mapping come from one table there.

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		scanStashChart();
		if (lootingBagScanTicks > 0)
		{
			lootingBagScanTicks--;
			dirtySources.add(CollectionLedger.Source.LOOTING_BAG);
		}
		if (dirtySources.isEmpty())
		{
			return;
		}
		for (CollectionLedger.Source source : EnumSet.copyOf(dirtySources))
		{
			Map<Integer, Integer> pending = pendingScans.remove(source);
			if (pending != null)
			{
				ledger.update(source, pending);
				dirtySources.remove(source);
				continue;
			}
			ItemContainer c = client.getItemContainer(source.containerId());
			if (c == null)
			{
				dirtySources.remove(source);
				continue;
			}
			ledger.update(source, itemsOf(c));
			dirtySources.remove(source);
		}
	}

	private static Map<Integer, Integer> itemsOf(ItemContainer container)
	{
		Map<Integer, Integer> items = new HashMap<>();
		for (Item item : container.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				items.merge(item.getId(), item.getQuantity(), Integer::sum);
			}
		}
		return items;
	}

	/** Tier panels on the STASH chart, beginner through master. */
	private static final int[] STASH_TIER_CHILDREN = {4, 6, 8, 10, 12, 14};

	/**
	 * The STASH chart (widget group 493, the noticeboard by Watson's house)
	 * shows every unit's built/filled state - one read covers all 100+
	 * units, no visits needed. Filled units count their default items as
	 * owned; the whole STASH source is replaced per read, so emptied units
	 * drop out. Client thread, once per chart opening.
	 */
	private void scanStashChart()
	{
		if (client.getWidget(493, 2) == null)
		{
			stashChartSeen = false;
			return;
		}
		com.loadoutlab.data.StashUnits units = stashUnits;
		if (stashChartSeen || units == null)
		{
			return;
		}
		stashChartSeen = true;
		Map<Integer, Integer> items = new HashMap<>();
		for (int childId : STASH_TIER_CHILDREN)
		{
			Widget tier = client.getWidget(493, childId);
			if (tier == null || tier.getChildren() == null)
			{
				continue;
			}
			List<com.loadoutlab.data.StashUnits.Cell> cells = new ArrayList<>();
			for (Widget child : tier.getChildren())
			{
				if (child != null)
				{
					cells.add(new com.loadoutlab.data.StashUnits.Cell(child.getType(), child.getText()));
				}
			}
			for (String name : com.loadoutlab.data.StashUnits.filledNames(cells, childId == 14))
			{
				int[] ids = units.itemsFor(name);
				if (ids == null)
				{
					log.debug("unknown STASH chart unit: {}", name);
					continue;
				}
				for (int id : ids)
				{
					items.merge(id, 1, Integer::sum);
				}
			}
		}
		ledger.update(CollectionLedger.Source.STASH, items);
	}

	// ------------------------------------------------------------------
	// Optimization flow: client thread (profile) -> worker (search) -> EDT (render)
	// ------------------------------------------------------------------

	/**
	 * Ownership for the panel's borders/menus, VARIANT-AWARE: owning a
	 * degraded or ornamented version (Blood moon tassets (Used), whip
	 * (or)) counts as owning the base item the optimizer suggests - the
	 * same canonicalization the optimizer itself uses. Cached per ledger
	 * fingerprint; the canonicalization walks the whole bank otherwise.
	 */
	private volatile Set<Integer> canonicalOwnedCache;
	private volatile int canonicalOwnedFingerprint;

	private boolean ownsCanonical(int itemId)
	{
		if (data == null)
		{
			return ledger != null && ownedItems().containsKey(itemId);
		}
		int fingerprint = ownedFingerprint();
		Set<Integer> cache = canonicalOwnedCache;
		if (cache == null || canonicalOwnedFingerprint != fingerprint)
		{
			cache = data.canonicalizeOwned(ownedItems()).keySet();
			canonicalOwnedCache = cache;
			canonicalOwnedFingerprint = fingerprint;
		}
		return cache.contains(itemId);
	}

	/**
	 * The ledger view plus manual "stored elsewhere" and DWMS storages.
	 * Once DWMS has answered a PluginMessage request this session the live
	 * snapshot wins outright (guaranteed-correct parse, every storage);
	 * until then the best-effort config read fills in.
	 */
	private Map<Integer, Integer> ownedItems()
	{
		Map<Integer, Integer> owned = manualOwned.mergeInto(ledger.owned());
		if (!config.useDwmsData())
		{
			// The Connections toggle is off: DWMS-tracked storages do not
			// count as owned (the stores stay loaded for a live re-enable).
			return owned;
		}
		return dwmsLink.isLive() ? dwmsLink.mergeInto(owned) : dwmsImport.mergeInto(owned);
	}

	/**
	 * Ownership fingerprint covering the ledger, the manual list, AND the
	 * effective DWMS source - the optimizer/panel cache key, so any of them
	 * changing is a real ownership change everywhere a bank deposit would be.
	 */
	private int ownedFingerprint()
	{
		int fingerprint = 31 * ledger.fingerprint() + manualOwned.snapshot().hashCode();
		int dwms = !config.useDwmsData() ? 0
			: dwmsLink.isLive()
				? dwmsLink.snapshot().hashCode() : dwmsImport.snapshot().hashCode();
		return 31 * fingerprint + dwms;
	}

	/**
	 * Origin label -> items, in display order - the provenance the flat
	 * owned map erases. Feeds the tooltip location hints and the profile
	 * export's per-source breakdown. DWMS labels come from the config-read
	 * families even when the live link supplies ownership (same underlying
	 * data, and the live snapshot is flat); an owned item with no known
	 * origin simply gets no hint.
	 */
	private Map<String, Map<Integer, Integer>> ownedBySources()
	{
		LinkedHashMap<String, Map<Integer, Integer>> origins = new LinkedHashMap<>();
		origins.put("equipped", ledger.snapshot(CollectionLedger.Source.EQUIPMENT));
		origins.put("inventory", ledger.snapshot(CollectionLedger.Source.INVENTORY));
		origins.put("bank", ledger.snapshot(CollectionLedger.Source.BANK));
		origins.put("looting bag", ledger.snapshot(CollectionLedger.Source.LOOTING_BAG));
		origins.put("POH costume room", ledger.snapshot(CollectionLedger.Source.POH_COSTUMES));
		origins.put("STASH", ledger.snapshot(CollectionLedger.Source.STASH));
		Map<Integer, Integer> cargo = new HashMap<>();
		for (CollectionLedger.Source hold : List.of(
			CollectionLedger.Source.CARGO_HOLD_1, CollectionLedger.Source.CARGO_HOLD_2,
			CollectionLedger.Source.CARGO_HOLD_3, CollectionLedger.Source.CARGO_HOLD_4,
			CollectionLedger.Source.CARGO_HOLD_5))
		{
			for (Map.Entry<Integer, Integer> e : ledger.snapshot(hold).entrySet())
			{
				cargo.merge(e.getKey(), e.getValue(), Integer::sum);
			}
		}
		origins.put("cargo hold", cargo);
		Map<Integer, Integer> manual = new HashMap<>();
		for (int id : manualOwned.snapshot())
		{
			manual.put(id, 1);
		}
		origins.put("stored elsewhere", manual);
		if (config.useDwmsData())
		{
			for (Map.Entry<String, Map<Integer, Integer>> family : dwmsImport.families().entrySet())
			{
				origins.put(dwmsFamilyLabel(family.getKey()), family.getValue());
			}
		}
		origins.values().removeIf(Map::isEmpty);
		return origins;
	}

	private static String dwmsFamilyLabel(String family)
	{
		switch (family)
		{
			case "poh": return "POH costume room" + com.loadoutlab.collection.ItemLocations.VIA_DWMS;
			case "stash": return "STASH" + com.loadoutlab.collection.ItemLocations.VIA_DWMS;
			case "death": return "death storage" + com.loadoutlab.collection.ItemLocations.VIA_DWMS;
			case "sailing": return "cargo hold" + com.loadoutlab.collection.ItemLocations.VIA_DWMS;
			case "carryable": return "carried container" + com.loadoutlab.collection.ItemLocations.VIA_DWMS;
			default: return "world storage" + com.loadoutlab.collection.ItemLocations.VIA_DWMS;
		}
	}

	/** The mob's effective pins per style (ALL overlaid by each style) -
	 * what the optimizer request for each card carries. */
	private Map<com.loadoutlab.engine.CombatStyle, Map<com.loadoutlab.data.GearSlot, Integer>> pinnedByStyle(int monsterId)
	{
		EnumMap<com.loadoutlab.engine.CombatStyle, Map<com.loadoutlab.data.GearSlot, Integer>> byStyle =
			new EnumMap<>(com.loadoutlab.engine.CombatStyle.class);
		for (com.loadoutlab.engine.CombatStyle style : com.loadoutlab.engine.CombatStyle.values())
		{
			Map<com.loadoutlab.data.GearSlot, Integer> pins =
				mobProfiles.pinsFor(monsterId, style.name());
			if (!pins.isEmpty())
			{
				byStyle.put(style, pins);
			}
		}
		return byStyle;
	}

	/** Run a user mutation through the undo stack and sync the buttons. */
	private boolean exec(com.loadoutlab.command.Command cmd)
	{
		boolean ok = commandHistory.execute(cmd);
		if (panel != null)
		{
			panel.refreshHistoryButtons();
		}
		return ok;
	}

	/** Item label for command descriptions, from the gear corpus (EDT-safe -
	 * never ItemManager, which is client-thread-only). */
	private String itemLabel(int itemId)
	{
		com.loadoutlab.data.GearItem item = data == null ? null : data.getGear(itemId);
		return item == null ? ("item " + itemId) : item.label();
	}

	/** Panel hook: the header undo/redo buttons over the command stack. */
	private LoadoutLabPanel.HistoryControl historyControl()
	{
		return new LoadoutLabPanel.HistoryControl()
		{
			@Override
			public boolean undo()
			{
				return commandHistory.undo();
			}

			@Override
			public boolean redo()
			{
				return commandHistory.redo();
			}

			@Override
			public boolean canUndo()
			{
				return commandHistory.canUndo();
			}

			@Override
			public boolean canRedo()
			{
				return commandHistory.canRedo();
			}

			@Override
			public String undoLabel()
			{
				return commandHistory.peekUndoDescription();
			}

			@Override
			public String redoLabel()
			{
				return commandHistory.peekRedoDescription();
			}

			@Override
			public boolean execute(com.loadoutlab.command.Command command)
			{
				return exec(command);
			}
		};
	}

	/** Panel hook: the "only bring if protected on death" flag store. */
	private LoadoutLabPanel.ProtectOnlyToggle protectOnlyView()
	{
		return new LoadoutLabPanel.ProtectOnlyToggle()
		{
			@Override
			public boolean toggle(int itemId)
			{
				if (protectOnly == null)
				{
					return false;
				}
				exec(Commands.toggleProtectOnly(protectOnly, itemId, itemLabel(itemId)));
				return protectOnly.isProtectOnly(itemId);
			}

			@Override
			public boolean isProtectOnly(int itemId)
			{
				return protectOnly != null && protectOnly.isProtectOnly(itemId);
			}
		};
	}

	/** Effective exclusions per style: the global list unioned with this
	 * mob's ALL + style scopes. Styles with no mob exclusions share the
	 * global set instance, so their cache keys stay stable. */
	private Map<com.loadoutlab.engine.CombatStyle, Set<Integer>> excludedByStyle(int monsterId)
	{
		Set<Integer> global = exclusions.snapshot();
		EnumMap<com.loadoutlab.engine.CombatStyle, Set<Integer>> byStyle =
			new EnumMap<>(com.loadoutlab.engine.CombatStyle.class);
		for (com.loadoutlab.engine.CombatStyle style : com.loadoutlab.engine.CombatStyle.concreteValues())
		{
			Set<Integer> mob = mobProfiles.exclusionsFor(monsterId, style.name());
			if (mob.isEmpty())
			{
				byStyle.put(style, global);
				continue;
			}
			Set<Integer> merged = new HashSet<>(global);
			merged.addAll(mob);
			byStyle.put(style, merged);
		}
		return byStyle;
	}

	/** Roster exclusions travel PER MOB (field decision 2026-07-17,
	 * refining the same-day union: "never scythe vs Ket-Zek" means the
	 * Fight Caves KIT may still carry the scythe, but Ket-Zek's own
	 * answer never wears it). Global exclusions ride the style map; each
	 * mob's own exclusions ride this per-profileId map, so synthetic
	 * phase variants (TD shields) inherit their real monster's profile. */
	private Map<Integer, Map<com.loadoutlab.engine.CombatStyle, Set<Integer>>> perMobExclusions(
		List<MonsterStats> mobs)
	{
		Map<Integer, Map<com.loadoutlab.engine.CombatStyle, Set<Integer>>> byMob =
			new HashMap<>();
		for (MonsterStats mob : mobs)
		{
			EnumMap<com.loadoutlab.engine.CombatStyle, Set<Integer>> byStyle = null;
			for (com.loadoutlab.engine.CombatStyle style : com.loadoutlab.engine.CombatStyle.concreteValues())
			{
				Set<Integer> per = mobProfiles.exclusionsFor(mob.profileId(), style.name());
				if (per.isEmpty())
				{
					continue;
				}
				if (byStyle == null)
				{
					byStyle = new EnumMap<>(com.loadoutlab.engine.CombatStyle.class);
				}
				byStyle.put(style, per);
			}
			if (byStyle != null)
			{
				byMob.put(mob.profileId(), byStyle);
			}
		}
		return byMob;
	}

	/** The lensed mob's local sims join the global simmed set for a
	 * single-mob compute (field spec 2026-07-18). */
	private Set<Integer> dreamsWithMobSims(MonsterStats monster)
	{
		Set<Integer> global = dreams.snapshot();
		Set<Integer> local = mobProfiles == null
			? Collections.emptySet() : mobProfiles.simsFor(monster.profileId());
		if (local.isEmpty())
		{
			return global;
		}
		Set<Integer> merged = new HashSet<>(global);
		merged.addAll(local);
		return merged;
	}

	/** Per-mob sims for a roster, keyed by profileId. */
	private Map<Integer, Set<Integer>> perMobSims(List<MonsterStats> mobs)
	{
		Map<Integer, Set<Integer>> byMob = new HashMap<>();
		if (mobProfiles == null)
		{
			return byMob;
		}
		for (MonsterStats mob : mobs)
		{
			Set<Integer> sims = mobProfiles.simsFor(mob.profileId());
			if (!sims.isEmpty())
			{
				byMob.put(mob.profileId(), sims);
			}
		}
		return byMob;
	}

	/** The global exclusion set for every style - the roster path's base
	 * map; per-mob exclusions layer on top inside the optimizer. */
	private Map<com.loadoutlab.engine.CombatStyle, Set<Integer>> globalExcludedByStyle()
	{
		Set<Integer> global = exclusions.snapshot();
		EnumMap<com.loadoutlab.engine.CombatStyle, Set<Integer>> byStyle =
			new EnumMap<>(com.loadoutlab.engine.CombatStyle.class);
		for (com.loadoutlab.engine.CombatStyle style : com.loadoutlab.engine.CombatStyle.concreteValues())
		{
			byStyle.put(style, global);
		}
		return byStyle;
	}

	/** Panel hook: the per-monster user profile, backed by the store. */
	private LoadoutLabPanel.MobProfile mobProfileView()
	{
		return new LoadoutLabPanel.MobProfile()
		{
			@Override
			public Map<com.loadoutlab.data.GearSlot, Integer> pins(int monsterId, com.loadoutlab.engine.CombatStyle style)
			{
				return mobProfiles == null ? Map.of()
					: mobProfiles.pinsFor(monsterId, style.name());
			}

			@Override
			public Map<String, Map<com.loadoutlab.data.GearSlot, Integer>> allPins(int monsterId)
			{
				return mobProfiles == null ? Map.of() : mobProfiles.allPins(monsterId);
			}

			@Override
			public void pin(int monsterId, String scope, com.loadoutlab.data.GearSlot slot, int itemId)
			{
				if (mobProfiles != null)
				{
					exec(Commands.pin(mobProfiles, monsterId, scope, slot, itemId, itemLabel(itemId)));
				}
			}

			@Override
			public void unpin(int monsterId, String scope, com.loadoutlab.data.GearSlot slot)
			{
				if (mobProfiles != null)
				{
					Integer current = mobProfiles.allPins(monsterId)
						.getOrDefault(scope, Map.of()).get(slot);
					String label = current == null
						? slot.name().toLowerCase() : itemLabel(current);
					exec(Commands.unpin(mobProfiles, monsterId, scope, slot, label));
				}
			}

			@Override
			public String note(int monsterId)
			{
				return mobProfiles == null ? "" : mobProfiles.noteFor(monsterId);
			}

			@Override
			public void setNote(int monsterId, String note)
			{
				if (mobProfiles != null)
				{
					exec(Commands.setNote(mobProfiles, monsterId, note));
				}
			}

			@Override
			public Set<Integer> filterItems(int monsterId, com.loadoutlab.engine.CombatStyle style)
			{
				return mobProfiles == null ? Set.of()
					: mobProfiles.filterItemsFor(monsterId, style.name());
			}

			@Override
			public Map<String, Map<Integer, String>> allFilterItems(int monsterId)
			{
				return mobProfiles == null ? Map.of() : mobProfiles.allFilterItems(monsterId);
			}

			@Override
			public void addFilterItem(int monsterId, String scope, int itemId, String name)
			{
				if (mobProfiles != null)
				{
					exec(Commands.addFilterItem(mobProfiles, monsterId, scope, itemId, name));
				}
			}

			@Override
			public void removeFilterItem(int monsterId, String scope, int itemId)
			{
				if (mobProfiles != null)
				{
					exec(Commands.removeFilterItem(mobProfiles, monsterId, scope, itemId));
				}
			}

			@Override
			public String pinnedSpell(int monsterId)
			{
				return mobProfiles == null ? "" : mobProfiles.pinnedSpellFor(monsterId);
			}

			@Override
			public void setPinnedSpell(int monsterId, String spellName)
			{
				if (mobProfiles != null)
				{
					exec(Commands.setPinnedSpell(mobProfiles, monsterId, spellName));
				}
			}

			@Override
			public Map<String, Set<Integer>> allMobExclusions(int monsterId)
			{
				return mobProfiles == null ? Map.of() : mobProfiles.allExclusions(monsterId);
			}

			@Override
			public void excludeForMob(int monsterId, String scope, int itemId)
			{
				if (mobProfiles != null)
				{
					exec(Commands.excludeForMob(mobProfiles, monsterId, scope, itemId, itemLabel(itemId)));
				}
			}

			@Override
			public void removeMobExclusion(int monsterId, String scope, int itemId)
			{
				if (mobProfiles != null)
				{
					exec(Commands.removeMobExclusion(mobProfiles, monsterId, scope, itemId, itemLabel(itemId)));
				}
			}

			@Override
			public Map<Integer, String> allMobSims(int monsterId)
			{
				return mobProfiles == null ? Map.of() : mobProfiles.allSims(monsterId);
			}

			@Override
			public void simForMob(int monsterId, int itemId, String name)
			{
				if (mobProfiles != null)
				{
					exec(Commands.simForMob(mobProfiles, monsterId, itemId, name));
				}
			}

			@Override
			public Map<String, String> supplyOverrides(int monsterId)
			{
				return mobProfiles.supplies(monsterId);
			}

			@Override
			public void setSupplyOverride(int monsterId, String category, String choice)
			{
				mobProfiles.setSupply(monsterId, category, choice);
			}

			@Override
			public void removeMobSim(int monsterId, int itemId)
			{
				if (mobProfiles != null)
				{
					exec(Commands.removeMobSim(mobProfiles, monsterId, itemId, itemLabel(itemId)));
				}
			}

			@Override
			public void excludeForMobs(java.util.List<Integer> monsterIds, String scope, int itemId)
			{
				if (mobProfiles == null)
				{
					return;
				}
				// One undo entry for the whole group (field request 2026-07-18).
				commandHistory.beginCompound("Exclude for the whole group: " + itemLabel(itemId));
				for (int id : monsterIds)
				{
					exec(Commands.excludeForMob(mobProfiles, id, scope, itemId, itemLabel(itemId)));
				}
				commandHistory.endCompound();
				if (panel != null)
				{
					panel.refreshHistoryButtons();
				}
			}

			@Override
			public void simForMobs(java.util.List<Integer> monsterIds, int itemId, String name)
			{
				if (mobProfiles == null)
				{
					return;
				}
				commandHistory.beginCompound("Sim for the whole group: " + name);
				for (int id : monsterIds)
				{
					exec(Commands.simForMob(mobProfiles, id, itemId, name));
				}
				commandHistory.endCompound();
				if (panel != null)
				{
					panel.refreshHistoryButtons();
				}
			}
		};
	}

	/** The mob's pinned autocast spell resolved to the dataset, or null. */
	private com.loadoutlab.data.SpellStats resolvedPinnedSpell(int monsterId)
	{
		String name = mobProfiles == null ? "" : mobProfiles.pinnedSpellFor(monsterId);
		if (name.isEmpty() || data == null)
		{
			return null;
		}
		for (com.loadoutlab.data.SpellStats spell : data.getSpells())
		{
			if (spell.getName().equalsIgnoreCase(name))
			{
				return spell;
			}
		}
		return null;
	}

	/**
	 * Panel hook: the NATIVE chatbox item search (field request - replaces
	 * the dialog matcher). Runs on the client thread; the pick resolves
	 * its display name there (the only thread where that is legal) and
	 * returns to the EDT.
	 */
	private LoadoutLabPanel.ItemSearch itemSearchView()
	{
		return (prompt, onPicked) ->
		{
			clientThread.invokeLater(() ->
				chatboxItemSearch
					.tooltipText(prompt)
					.onItemSelected(itemId ->
					{
						String name = itemManager.getItemComposition(itemId).getName();
						SwingUtilities.invokeLater(() -> onPicked.accept(itemId, name));
					})
					.build());
			// The click came from the Swing sidebar - hand the OS focus to
			// the game so typing searches immediately (field request).
			clientUI.requestFocus();
		};
	}

	/** Panel hook: tooltip clause + legend label for an item's location.
	 * Render/hover frequency - the origins map is memoized on the ownership
	 * fingerprint (a render pass asked ~2x per gear cell, and every ask
	 * rebuilt all the per-source snapshots). */
	private LoadoutLabPanel.LocationHint locationHintView()
	{
		return new LoadoutLabPanel.LocationHint()
		{
			private com.loadoutlab.collection.ItemLocations cached;
			private int cachedFingerprint;

			@Override
			public String hint(int itemId)
			{
				com.loadoutlab.collection.ItemLocations locations = locations();
				return locations == null ? "" : locations.fetchHint(itemId);
			}

			@Override
			public String primary(int itemId)
			{
				com.loadoutlab.collection.ItemLocations locations = locations();
				return locations == null ? "" : locations.primary(itemId);
			}

			/** EDT-confined (panel render/hover), like the panel itself. */
			private com.loadoutlab.collection.ItemLocations locations()
			{
				if (ledger == null || manualOwned == null || dwmsImport == null)
				{
					return null;
				}
				int fingerprint = ownedFingerprint();
				if (cached == null || cachedFingerprint != fingerprint)
				{
					cached = new com.loadoutlab.collection.ItemLocations(ownedBySources(),
						data == null ? null : data::equivalentIds);
					cachedFingerprint = fingerprint;
				}
				return cached;
			}
		};
	}

	/**
	 * Fire-and-forget: ask DWMS for its tracked storages (see DwmsLink).
	 * Nothing is posted when the plugin is absent or disabled, and a
	 * missing reply (a DWMS predating the contract) just leaves the
	 * config-read fallback in charge.
	 */
	private void requestDwmsStorages()
	{
		if (!config.useDwmsData() || !dwmsPresent())
		{
			return;
		}
		eventBus.post(new PluginMessage(
			DwmsLink.DWMS_NAMESPACE, DwmsLink.REQUEST_NAME, DwmsLink.request()));
	}

	/**
	 * Client thread: answer a storages-request with the ledger's per-source
	 * snapshots plus the manual stored-elsewhere list (quantity 1 each, the
	 * same way the optimizer counts them). Fire-and-forget like the DWMS
	 * side: no reply while the stores are down (plugin shutting down).
	 */
	private void respondWithStorages(String target)
	{
		CollectionLedger currentLedger = ledger;
		ManualOwnedStore manual = manualOwned;
		if (currentLedger == null || manual == null)
		{
			return;
		}
		// Canonicalization needs the item cache; before the login screen
		// exists, ship raw ids rather than crash (DWMS guards the same way).
		java.util.function.IntUnaryOperator canonicalize =
			client.getGameState().getState() >= GameState.LOGIN_SCREEN.getState()
				? itemManager::canonicalize
				: java.util.function.IntUnaryOperator.identity();
		List<Map<String, Object>> storages = new ArrayList<>();
		for (CollectionLedger.Source source : CollectionLedger.Source.values())
		{
			Map<String, Object> entry = StoragesApi.storage(
				"collection", source.key(), -1L, currentLedger.snapshot(source), canonicalize);
			if (entry != null)
			{
				storages.add(entry);
			}
		}
		Map<Integer, Integer> manualItems = new HashMap<>();
		for (int itemId : manual.snapshot())
		{
			manualItems.put(itemId, 1);
		}
		Map<String, Object> manualEntry = StoragesApi.storage(
			"manual", "manualOwned", -1L, manualItems, canonicalize);
		if (manualEntry != null)
		{
			storages.add(manualEntry);
		}
		eventBus.post(new PluginMessage(StoragesApi.NAMESPACE, StoragesApi.RESPONSE_NAME,
			StoragesApi.response(target, storages)));
	}

	/** Check ALL same-named plugins - a hub copy and a sideloaded dev copy
	 * can coexist and the first match may be the disabled loser. */
	private boolean dwmsPresent()
	{
		for (Plugin p : pluginManager.getPlugins())
		{
			if (DWMS_PLUGIN_NAME.equals(p.getName()) && pluginManager.isPluginEnabled(p))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * The real account as a replayable fixture: written next to the usage
	 * log on every query, so any in-game result can be reproduced headless
	 * (./gradlew query) or attached to a bug report. Local only.
	 */
	private void exportProfile(PlayerProfile profile)
	{
		Thread writer = new Thread(() ->
		{
			try
			{
				Path file = new File(RuneLite.RUNELITE_DIR,
					"loadout-lab/profile.json").toPath();
				Files.createDirectories(file.getParent());
				Files.writeString(file, profile.toJson());
			}
			catch (Exception ex)
			{
				log.warn("could not export the player profile", ex);
			}
		}, "loadout-lab-profile-export");
		writer.setDaemon(true);
		writer.start();
	}

	/** Panel hook: set (or clear, with null) the bank-highlighted item ids. */
	private void setBankHighlight(Set<Integer> itemIds)
	{
		if (itemIds == null || itemIds.isEmpty() || data == null)
		{
			bankHighlight = null;
			return;
		}
		Set<Integer> expanded = new HashSet<>();
		for (int id : itemIds)
		{
			expanded.addAll(data.equivalentIds(id));
		}
		bankHighlight = expanded;
	}

	/** Panel hook: filter the open bank to these ids via a virtual tag, and -
	 * when {@code layout} is non-null - arrange the set in the bank in the
	 * equipment + inventory shape (a bank-tag layout position array). */
	private void setBankFilter(Set<Integer> itemIds, int[] layout)
	{
		if (itemIds == null || itemIds.isEmpty() || data == null)
		{
			bankFilter = null;
			clientThread.invokeLater(() ->
			{
				if (BANK_TAG.equals(bankTagsService.getActiveTag()))
				{
					bankTagsService.closeBankTag();
				}
				layoutManager.removeLayout(BANK_TAG);
				removeBankTagTab();
				tagManager.unregisterTag(BANK_TAG);
			});
			return;
		}
		Set<Integer> expanded = new HashSet<>();
		for (int id : itemIds)
		{
			expanded.addAll(data.equivalentIds(id));
		}
		bankFilter = expanded;
		clientThread.invokeLater(() ->
		{
			tagManager.registerTag(BANK_TAG, itemId ->
			{
				Set<Integer> ids = bankFilter;
				return ids != null && ids.contains(itemId);
			});
			if (layout != null)
			{
				layoutManager.saveLayout(new Layout(BANK_TAG, layout));
				ensureBankTagTab();
				bankTagsService.openBankTag(BANK_TAG, 0);
			}
			else
			{
				layoutManager.removeLayout(BANK_TAG);
				removeBankTagTab();
				bankTagsService.openBankTag(BANK_TAG,
					BankTagsService.OPTION_NO_LAYOUT);
			}
		});
	}

	/** The hub "Bank Tag Layouts" plugin's config coordinates - kept only to
	 * clean up the layout its auto-enable wrote for our tag before the
	 * real-tab fix below existed. */
	private static final String BTL_GROUP = "banktaglayouts";
	private static final String BTL_LAYOUT_KEY = "layout_" + BANK_TAG;
	/** The filter tab's icon in the bank's tag strip (rune scimitar). */
	private static final int BANK_TAB_ICON = 1333;

	private boolean bankTagLayoutsActive()
	{
		for (Plugin p : pluginManager.getPlugins())
		{
			if ("Bank Tag Layouts".equals(p.getName()) && pluginManager.isPluginEnabled(p))
			{
				return true;
			}
		}
		return false;
	}

	/** With Bank Tag Layouts running, our tag must exist as a REAL tag tab
	 * while the filter is on: BTL only defers to the core bank-tags layout
	 * for a tab it can find (isVanillaLayoutEnabled = a TagTab AND a core
	 * layout), and with "layout enabled by default" on it auto-enables its
	 * own flat layout over any purely virtual tag every bank rebuild
	 * (field-found 2026-07-20: the cross rendered flat under it; a config
	 * mirror lost the write war). Inventory Setups escapes via a hardcoded
	 * _invsetup_ prefix exemption; a real tab is the road for everyone else.
	 *
	 * IN-MEMORY ONLY - deliberately no TabManager.save(): save() rewrites
	 * the user's whole tagtabs config from the in-memory list, which would
	 * WIPE their real tabs if the list isn't loaded yet. BTL's find() and
	 * the tab strip both read the live list, so persistence buys nothing;
	 * the bank-build hook below re-adds it whenever core reloads the list. */
	private void ensureBankTagTab()
	{
		if (!bankTagLayoutsActive())
		{
			log.debug("bank tab: Bank Tag Layouts not active, skipping");
			return;
		}
		if (tabManager.find(BANK_TAG) != null)
		{
			log.debug("bank tab: already present");
			return;
		}
		TagTab tab = new TagTab();
		tab.setTag(BANK_TAG);
		tab.setIconItemId(BANK_TAB_ICON);
		tabManager.add(tab);
		log.debug("bank tab: added (find now = {})", tabManager.find(BANK_TAG) != null);
	}

	private void removeBankTagTab()
	{
		if (tabManager.find(BANK_TAG) != null)
		{
			tabManager.remove(BANK_TAG);
		}
	}

	/** Re-assert the real tab on every bank build while the filter is on.
	 * Two hooks because BTL applies its layout at PostFired(BANKMAIN_BUILD,
	 * priority -1): the PostFired hook at -0.5 slots BETWEEN core's handlers
	 * (0) and BTL's apply (-1) - the decisive ordering on the first build
	 * after a bank open, where core's BANKMAIN_INIT reload has just wiped
	 * the in-memory list. The FINISHBUILDING hook covers rebuild variants.
	 * (Priority-after-core precedent: Inventory Setups.) */
	@Subscribe(priority = -1)
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING
			&& bankFilter != null
			&& BANK_TAG.equals(bankTagsService.getActiveTag()))
		{
			log.debug("bank tab: FINISHBUILDING prefired, ensuring tab");
			ensureBankTagTab();
		}
	}

	@Subscribe(priority = -0.5f)
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_BUILD
			&& bankFilter != null
			&& BANK_TAG.equals(bankTagsService.getActiveTag()))
		{
			log.debug("bank tab: BANKMAIN_BUILD postfired, ensuring tab");
			ensureBankTagTab();
		}
	}

	/** Roster mirror of compute: same client-thread staging, then
	 * ONE shared set per style across the mobs (bestPerStyleAcross). The
	 * FIRST mob anchors per-mob state (exclusions, pins, pinned spell) in
	 * v1 - roster-wide per-mob preferences come later. */
	/** Consumable GE prices, resolved ON the client thread (compositions
	 * assert off-thread) and handed to the panel for EDT rendering. */
	private void refreshConsumablePrices()
	{
		if (panel == null)
		{
			return;
		}
		Map<Integer, Long> prices = new HashMap<>();
		for (int id : LoadoutLabPanel.CONSUMABLE_PRICE_IDS)
		{
			prices.put(id, (long) itemManager.getItemPrice(id));
		}
		panel.setConsumablePrices(prices);
	}

	@Override
	public void computeRoster(List<MonsterStats> mobs, boolean f2pOnly, boolean onSlayerTask, boolean inWilderness, String spellbookLock, int maxTradeables, int riskBudgetGp, boolean antifirePotion, int deathCharge, Map<CombatStyle, String> boostPicks, Map<CombatStyle, String> prayerPicks, int upgradeBudgetGp, OptimizerService.OptimizeMode mode, int maxSwaps, boolean raidBoost, Runnable onDone)
	{
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				snapshotProfileIfNeeded();
			}
			refreshConsumablePrices();
			if (config.useDwmsData())
			{
				dwmsImport.reload();
				requestDwmsStorages();
			}
			RequirementProfile profile = requirementProfile != null
				? requirementProfile : RequirementProfile.MAXED;
			PlayerLevels live = boostedLevels != null ? boostedLevels : PlayerLevels.MAXED;
			PlayerLevels real = realLevels != null ? realLevels : PlayerLevels.MAXED;
			if (panel != null)
			{
				// BOOSTED magic (field call 2026-07-21): castability follows
				// the boosted stat, not the base level.
				panel.setMagicLevel(live.getMagic());
				panel.setDeathChargeUpgraded(client.getVarbitValue(
					net.runelite.api.gameval.VarbitID.DEATH_CHARGE_SCROLL_USED) > 0);
				panel.setCurrentSpellbook(client.getVarbitValue(
					net.runelite.api.gameval.VarbitID.SPELLBOOK));
			}
			Map<Integer, Integer> mergedOwned = ownedItems();
			OwnedItems owned = new OwnedItems(mergedOwned, ledger.bankKnown());
			int fingerprint = owned.presenceFingerprint();
			PrayerUnlocks unlocks = prayerUnlocks != null
				? prayerUnlocks : PrayerUnlocks.ALL;
			MonsterStats anchor = mobs.get(0);
			optimizerService.bestPerStyleAcross(mobs, real, live, unlocks, profile, owned, fingerprint, f2pOnly,
				onSlayerTask, spellbookLock, globalExcludedByStyle(), maxTradeables, riskBudgetGp, antifirePotion, deathCharge, boostPicks, prayerPicks,
				inWilderness, dreams.snapshot(), upgradeBudgetGp, mode, maxSwaps, perMobExclusions(mobs),
				perMobSims(mobs), raidBoost, pinnedByStyle(anchor.getId()), resolvedPinnedSpell(anchor.getId()),
				protectOnly.snapshot(),
				roster -> SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.showRosterResults(roster.mobs, roster.perMob, roster.curve);
					}
					onDone.run();
				}));
		});
	}

	@Override
	public void compute(MonsterStats monster, boolean f2pOnly, boolean onSlayerTask, boolean inWilderness, String spellbookLock, int maxTradeables, int riskBudgetGp, boolean antifirePotion, int deathCharge, Map<CombatStyle, String> boostPicks, Map<CombatStyle, String> prayerPicks, int upgradeBudgetGp, OptimizerService.OptimizeMode mode, int maxSwaps, boolean raidBoost, Runnable onDone)
	{
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				snapshotProfileIfNeeded();
			}
			refreshConsumablePrices();
			// DWMS saves on its own cadence (ConfigSync/shutdown); a per-query
			// re-read keeps imported storages as fresh as they can be. The
			// PluginMessage re-request refreshes the live snapshot the same
			// way (its reply lands after this compute - one-query lag, and
			// DWMS-tracked storages change rarely).
			if (config.useDwmsData())
			{
				dwmsImport.reload();
				requestDwmsStorages();
			}
			RequirementProfile profile = requirementProfile != null
				? requirementProfile : RequirementProfile.MAXED;
			PlayerLevels live = boostedLevels != null ? boostedLevels : PlayerLevels.MAXED;
			PlayerLevels real = realLevels != null ? realLevels : PlayerLevels.MAXED;
			if (panel != null)
			{
				// BOOSTED magic (field call 2026-07-21): castability follows
				// the boosted stat, not the base level.
				panel.setMagicLevel(live.getMagic());
				panel.setDeathChargeUpgraded(client.getVarbitValue(
					net.runelite.api.gameval.VarbitID.DEATH_CHARGE_SCROLL_USED) > 0);
				panel.setCurrentSpellbook(client.getVarbitValue(
					net.runelite.api.gameval.VarbitID.SPELLBOOK));
			}
			// One merge, shared by the optimizer request and the export - this
			// runs on the client thread, where every merge is a frame tax.
			Map<Integer, Integer> mergedOwned = ownedItems();
			OwnedItems owned = new OwnedItems(mergedOwned, ledger.bankKnown());
			// The optimizer cache keys on ownership PRESENCE, not quantities
			// - firing an arrow must not re-pay a 20s Balanced compute.
			int fingerprint = owned.presenceFingerprint();
			PrayerUnlocks unlocks = prayerUnlocks != null
				? prayerUnlocks : PrayerUnlocks.ALL;
			exportProfile(new PlayerProfile(
				real, live, unlocks, profile, mergedOwned, ledger.bankKnown(),
				ownedBySources()));
			optimizerService.bestPerStyle(monster, real, live, unlocks, profile, owned, fingerprint, f2pOnly,
				onSlayerTask, spellbookLock, excludedByStyle(monster.getId()), maxTradeables, riskBudgetGp, antifirePotion, deathCharge, boostPicks, prayerPicks,
				inWilderness, dreamsWithMobSims(monster), upgradeBudgetGp, mode, maxSwaps, raidBoost,
				pinnedByStyle(monster.getId()), resolvedPinnedSpell(monster.getId()),
				protectOnly.snapshot(),
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
		if (requirementProfile != null && boostedLevels != null && realLevels != null)
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
		// Canary: real Hitpoints can never be below 10. A sub-10 read means
		// the client stats were not populated yet (login race) - skip the
		// snapshot so the next compute retries, instead of poisoning every
		// number this session (field report: all magic at 0.06 dps).
		if (real.getOrDefault(Skill.HITPOINTS, 0) < 10)
		{
			log.debug("skill snapshot looked uninitialized, retrying on next compute");
			return;
		}
		// Stronger canary: stats stream in Skill-ordinal order at login, so a
		// fast first search can catch attack/str/def/hp populated while
		// ranged/prayer/magic still read 1 - and the HP check above passes.
		// The client's own total level is authoritative: a partial snapshot
		// always sums BELOW it (field report: ranged/magic/prayer poisoned
		// at 1 -> blisterwood-stake ranged picks and Wind Strike autocasts).
		int summed = 0;
		for (int level : real.values())
		{
			summed += level;
		}
		if (summed < client.getTotalLevel())
		{
			log.debug("skill snapshot partial ({} < total {}), retrying on next compute",
				summed, client.getTotalLevel());
			return;
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
		realLevels = PlayerLevels.from(real);
		boostedLevels = PlayerLevels.from(boosted);
		// Unlock-gated prayers: King's Ransom for Piety/Chivalry; scroll
		// unlock varbits for Rigour/Augury (CoX) and Deadeye/Mystic Vigour.
		prayerUnlocks = new PrayerUnlocks(
			quests.contains(Quest.KINGS_RANSOM.name()),
			client.getVarbitValue(5451) == 1,
			client.getVarbitValue(5452) == 1,
			client.getVarbitValue(16097) == 1,
			client.getVarbitValue(16098) == 1);
	}

	// ------------------------------------------------------------------

	/**
	 * Persistence scope: world type PLUS the account hash - two accounts
	 * on standard worlds must never share a scanned bank (field report:
	 * switching characters showed the previous character's gear).
	 */
	private String worldScope()
	{
		String world = client.getWorldType().contains(WorldType.SEASONAL) ? "seasonal" : "std";
		long account = client.getAccountHash();
		return account == -1 ? world : world + "." + account;
	}

	/** True only when logged in to a non-members world - the F2P filter default. */
	private boolean onF2pWorld()
	{
		return client.getGameState() == GameState.LOGGED_IN
			&& !client.getWorldType().contains(WorldType.MEMBERS);
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

	@Provides
	LoadoutLabConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(LoadoutLabConfig.class);
	}
}
