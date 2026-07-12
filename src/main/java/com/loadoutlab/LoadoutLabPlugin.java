package com.loadoutlab;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.loadoutlab.collection.CollectionLedger;
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
import net.runelite.api.Skill;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
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

	@Inject
	private net.runelite.client.ui.overlay.OverlayManager overlayManager;

	@Inject
	private net.runelite.client.plugins.banktags.BankTagsService bankTagsService;

	@Inject
	private net.runelite.client.plugins.banktags.TagManager tagManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private PluginManager pluginManager;

	private CollectionLedger ledger;
	private ExclusionStore exclusions;
	/** "Show in bank": the expanded id set the overlay outlines; null = off. */
	private volatile java.util.Set<Integer> bankHighlight;
	/** "Filter bank": a VIRTUAL bank tag (never persisted to the player's
	 * tag config) containing the active set's expanded ids; null = off. */
	private volatile java.util.Set<Integer> bankFilter;
	private static final String BANK_TAG = "loadout-lab";
	private com.loadoutlab.ui.BankHighlightOverlay bankOverlay;
	private DreamStore dreams;
	private ManualOwnedStore manualOwned;
	private com.loadoutlab.collection.PinStore pins;
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
			if (link != null && link.accept(event.getData()))
			{
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.dwmsUpdated();
					}
				});
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
	public void onMenuOpened(net.runelite.api.events.MenuOpened event)
	{
		if (data == null || panel == null || navButton == null)
		{
			return;
		}
		for (net.runelite.api.MenuEntry entry : event.getMenuEntries())
		{
			net.runelite.api.NPC npc = entry.getNpc();
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
				.setType(net.runelite.api.MenuAction.RUNELITE)
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
		dreams = new DreamStore(configManager, gson);
		manualOwned = new ManualOwnedStore(configManager, gson);
		pins = new com.loadoutlab.collection.PinStore(configManager, gson);
		dwmsImport = new DwmsImport(configManager);
		dwmsLink = new DwmsLink();
		bankOverlay = new com.loadoutlab.ui.BankHighlightOverlay(() -> bankHighlight);
		overlayManager.add(bankOverlay);
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
				panel = new LoadoutLabPanel(loaded, itemManager, spriteManager, this::computeForMonster,
					exclusions::toggle, exclusions::snapshot,
					dreams::toggle, dreams::snapshot,
					manualOwned::toggle, manualOwned::snapshot,
					dwmsView(),
					locationHintView(),
					pins::pin, pins::unpin, pins::snapshot,
					this::ownsCanonical,
					this::setBankHighlight,
					this::setBankFilter);
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
		if (bankOverlay != null)
		{
			overlayManager.remove(bankOverlay);
			bankOverlay = null;
		}
		bankHighlight = null;
		bankFilter = null;
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
		manualOwned = null;
		pins = null;
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
	public void onAccountHashChanged(net.runelite.api.events.AccountHashChanged event)
	{
		resetForIdentityChange();
	}

	/** The RuneLite config profile changed: config-backed stores re-read. */
	@Subscribe
	public void onProfileChanged(net.runelite.client.events.ProfileChanged event)
	{
		if (exclusions != null)
		{
			exclusions.reload();
		}
		if (dreams != null)
		{
			dreams.reload();
		}
		if (pins != null)
		{
			pins.reload();
		}
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
			CollectionLedger.Source source = storageSourceFor(id);
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
	public void onWidgetLoaded(net.runelite.api.events.WidgetLoaded event)
	{
		if (event.getGroupId() == net.runelite.api.gameval.InterfaceID.WILDERNESS_LOOTINGBAG)
		{
			lootingBagScanTicks = 3;
		}
	}

	/** Storage containers scanned from the event rather than re-fetched. */
	private static CollectionLedger.Source storageSourceFor(int containerId)
	{
		switch (containerId)
		{
			case net.runelite.api.gameval.InventoryID.LOOTING_BAG:
				// Fires when the bag is opened or checked - the only times
				// the client learns its contents. Vital for UIM.
				return CollectionLedger.Source.LOOTING_BAG;
			case net.runelite.api.gameval.InventoryID.POH_COSTUMES:
				// One shared container for every costume-room storage; fires
				// when a case/wardrobe/chest interface is opened in the POH.
				return CollectionLedger.Source.POH_COSTUMES;
			default:
				return cargoSourceFor(containerId);
		}
	}

	/** Sailing cargo holds: one container per boat slot. */
	private static CollectionLedger.Source cargoSourceFor(int containerId)
	{
		switch (containerId)
		{
			case net.runelite.api.gameval.InventoryID.SAILING_BOAT_1_CARGOHOLD:
				return CollectionLedger.Source.CARGO_HOLD_1;
			case net.runelite.api.gameval.InventoryID.SAILING_BOAT_2_CARGOHOLD:
				return CollectionLedger.Source.CARGO_HOLD_2;
			case net.runelite.api.gameval.InventoryID.SAILING_BOAT_3_CARGOHOLD:
				return CollectionLedger.Source.CARGO_HOLD_3;
			case net.runelite.api.gameval.InventoryID.SAILING_BOAT_4_CARGOHOLD:
				return CollectionLedger.Source.CARGO_HOLD_4;
			case net.runelite.api.gameval.InventoryID.SAILING_BOAT_5_CARGOHOLD:
				return CollectionLedger.Source.CARGO_HOLD_5;
			default:
				return null;
		}
	}

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
			ItemContainer c = client.getItemContainer(containerFor(source));
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
			net.runelite.api.widgets.Widget tier = client.getWidget(493, childId);
			if (tier == null || tier.getChildren() == null)
			{
				continue;
			}
			java.util.List<com.loadoutlab.data.StashUnits.Cell> cells = new java.util.ArrayList<>();
			for (net.runelite.api.widgets.Widget child : tier.getChildren())
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
		int dwms = dwmsLink.isLive()
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
		java.util.LinkedHashMap<String, Map<Integer, Integer>> origins = new java.util.LinkedHashMap<>();
		origins.put("equipped", ledger.snapshot(CollectionLedger.Source.EQUIPMENT));
		origins.put("inventory", ledger.snapshot(CollectionLedger.Source.INVENTORY));
		origins.put("bank", ledger.snapshot(CollectionLedger.Source.BANK));
		origins.put("looting bag", ledger.snapshot(CollectionLedger.Source.LOOTING_BAG));
		origins.put("POH costume room", ledger.snapshot(CollectionLedger.Source.POH_COSTUMES));
		origins.put("STASH", ledger.snapshot(CollectionLedger.Source.STASH));
		Map<Integer, Integer> cargo = new HashMap<>();
		for (CollectionLedger.Source hold : java.util.List.of(
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
		for (Map.Entry<String, Map<Integer, Integer>> family : dwmsImport.families().entrySet())
		{
			origins.put(dwmsFamilyLabel(family.getKey()), family.getValue());
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

	/** Panel hook: tooltip clause + legend label for an item's location.
	 * Render/hover frequency; each lookup rebuilds the small origins map. */
	private LoadoutLabPanel.LocationHint locationHintView()
	{
		return new LoadoutLabPanel.LocationHint()
		{
			@Override
			public String hint(int itemId)
			{
				return locations() == null ? "" : locations().fetchHint(itemId);
			}

			@Override
			public String primary(int itemId)
			{
				return locations() == null ? "" : locations().primary(itemId);
			}

			private com.loadoutlab.collection.ItemLocations locations()
			{
				if (ledger == null || manualOwned == null || dwmsImport == null)
				{
					return null;
				}
				return new com.loadoutlab.collection.ItemLocations(ownedBySources(),
					data == null ? null : data::equivalentIds);
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
		if (!dwmsPresent())
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
		java.util.List<Map<String, Object>> storages = new java.util.ArrayList<>();
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
			if ("Dude, Where's My Stuff?".equals(p.getName()) && pluginManager.isPluginEnabled(p))
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

	/** Panel view of the effective DWMS source: live once DWMS replied,
	 * the config read before then. Null-safe across shutdown. */
	private LoadoutLabPanel.DwmsView dwmsView()
	{
		return new LoadoutLabPanel.DwmsView()
		{
			@Override
			public int count()
			{
				DwmsLink link = dwmsLink;
				DwmsImport imported = dwmsImport;
				if (link != null && link.isLive())
				{
					return link.count();
				}
				return imported != null ? imported.count() : 0;
			}

			@Override
			public boolean live()
			{
				DwmsLink link = dwmsLink;
				return link != null && link.isLive();
			}
		};
	}

	/** Panel hook: set (or clear, with null) the bank-highlighted item ids. */
	private void setBankHighlight(java.util.Set<Integer> itemIds)
	{
		if (itemIds == null || itemIds.isEmpty() || data == null)
		{
			bankHighlight = null;
			return;
		}
		java.util.Set<Integer> expanded = new java.util.HashSet<>();
		for (int id : itemIds)
		{
			expanded.addAll(data.equivalentIds(id));
		}
		bankHighlight = expanded;
	}

	/** Panel hook: filter the open bank to these ids via a virtual tag. */
	private void setBankFilter(java.util.Set<Integer> itemIds)
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
				tagManager.unregisterTag(BANK_TAG);
			});
			return;
		}
		java.util.Set<Integer> expanded = new java.util.HashSet<>();
		for (int id : itemIds)
		{
			expanded.addAll(data.equivalentIds(id));
		}
		bankFilter = expanded;
		clientThread.invokeLater(() ->
		{
			tagManager.registerTag(BANK_TAG, itemId ->
			{
				java.util.Set<Integer> ids = bankFilter;
				return ids != null && ids.contains(itemId);
			});
			bankTagsService.openBankTag(BANK_TAG,
				net.runelite.client.plugins.banktags.BankTagsService.OPTION_NO_LAYOUT);
		});
	}

	private void computeForMonster(MonsterStats monster, boolean f2pOnly, boolean onSlayerTask, String spellbookLock, int maxTradeables, int riskBudgetGp, boolean antifirePotion, int upgradeBudgetGp, OptimizerService.OptimizeMode mode, Runnable onDone)
	{
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				snapshotProfileIfNeeded();
			}
			// DWMS saves on its own cadence (ConfigSync/shutdown); a per-query
			// re-read keeps imported storages as fresh as they can be. The
			// PluginMessage re-request refreshes the live snapshot the same
			// way (its reply lands after this compute - one-query lag, and
			// DWMS-tracked storages change rarely).
			dwmsImport.reload();
			requestDwmsStorages();
			RequirementProfile profile = requirementProfile != null
				? requirementProfile : RequirementProfile.MAXED;
			PlayerLevels live = boostedLevels != null ? boostedLevels : PlayerLevels.MAXED;
			PlayerLevels real = realLevels != null ? realLevels : PlayerLevels.MAXED;
			OwnedItems owned = new OwnedItems(ownedItems(), ledger.bankKnown());
			int fingerprint = ownedFingerprint();
			PrayerUnlocks unlocks = prayerUnlocks != null
				? prayerUnlocks : PrayerUnlocks.ALL;
			exportProfile(new PlayerProfile(
				real, live, unlocks, profile, ownedItems(), ledger.bankKnown(),
				ownedBySources()));
			optimizerService.bestPerStyle(monster, real, live, unlocks, profile, owned, fingerprint, f2pOnly,
				onSlayerTask, spellbookLock, exclusions.snapshot(), maxTradeables, riskBudgetGp, antifirePotion,
				dreams.snapshot(), upgradeBudgetGp, mode, pins.snapshot(),
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

	private static int containerFor(CollectionLedger.Source source)
	{
		switch (source)
		{
			case EQUIPMENT: return InventoryID.EQUIPMENT.getId();
			case INVENTORY: return InventoryID.INVENTORY.getId();
			case BANK: return InventoryID.BANK.getId();
			// The classic InventoryID enum lacks the newer containers; the
			// gameval ids are the authoritative modern constants.
			case LOOTING_BAG: return net.runelite.api.gameval.InventoryID.LOOTING_BAG;
			case POH_COSTUMES: return net.runelite.api.gameval.InventoryID.POH_COSTUMES;
			case CARGO_HOLD_1: return net.runelite.api.gameval.InventoryID.SAILING_BOAT_1_CARGOHOLD;
			case CARGO_HOLD_2: return net.runelite.api.gameval.InventoryID.SAILING_BOAT_2_CARGOHOLD;
			case CARGO_HOLD_3: return net.runelite.api.gameval.InventoryID.SAILING_BOAT_3_CARGOHOLD;
			case CARGO_HOLD_4: return net.runelite.api.gameval.InventoryID.SAILING_BOAT_4_CARGOHOLD;
			case CARGO_HOLD_5: return net.runelite.api.gameval.InventoryID.SAILING_BOAT_5_CARGOHOLD;
			// STASH is chart-driven, never container-scanned; -1 makes the
			// per-tick drain's null-container check clear a stray dirty flag.
			default: return -1;
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
