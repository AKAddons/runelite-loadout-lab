package com.loadoutlab;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.loadoutlab.collection.CollectionLedger;
import com.loadoutlab.collection.DreamStore;
import com.loadoutlab.collection.ExclusionStore;
import com.loadoutlab.collection.ManualOwnedStore;
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
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
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
	private PlayerLevels realLevels;
	private PlayerLevels boostedLevels;
	private PrayerUnlocks prayerUnlocks;

	/** Container-change coalescing - events mark, the per-tick drain scans. */
	private final EnumSet<CollectionLedger.Source> dirtySources =
		EnumSet.noneOf(CollectionLedger.Source.class);

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
		bankOverlay = new com.loadoutlab.ui.BankHighlightOverlay(() -> bankHighlight);
		overlayManager.add(bankOverlay);
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			ledger.loadScope(worldScope());
			manualOwned.loadScope(worldScope());
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
				panel = new LoadoutLabPanel(loaded, itemManager, spriteManager, this::computeForMonster,
					exclusions::toggle, exclusions::snapshot,
					dreams::toggle, dreams::snapshot,
					manualOwned::toggle, manualOwned::snapshot,
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
		requirementProfile = null;
		realLevels = null;
		boostedLevels = null;
		prayerUnlocks = null;
		dirtySources.clear();
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
		else if (id == net.runelite.api.gameval.InventoryID.LOOTING_BAG)
		{
			// Fires when the bag is opened or checked - the only times the
			// client learns its contents. Vital owned-gear storage for UIM.
			dirtySources.add(CollectionLedger.Source.LOOTING_BAG);
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

	/** The ledger view plus the manual "stored elsewhere" items. */
	private Map<Integer, Integer> ownedItems()
	{
		return manualOwned.mergeInto(ledger.owned());
	}

	/**
	 * Ownership fingerprint covering BOTH the ledger and the manual list -
	 * the optimizer/panel cache key, so a stored-elsewhere toggle is a real
	 * ownership change everywhere a bank deposit would be.
	 */
	private int ownedFingerprint()
	{
		return 31 * ledger.fingerprint() + manualOwned.snapshot().hashCode();
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
			RequirementProfile profile = requirementProfile != null
				? requirementProfile : RequirementProfile.MAXED;
			PlayerLevels live = boostedLevels != null ? boostedLevels : PlayerLevels.MAXED;
			PlayerLevels real = realLevels != null ? realLevels : PlayerLevels.MAXED;
			OwnedItems owned = new OwnedItems(ownedItems(), ledger.bankKnown());
			int fingerprint = ownedFingerprint();
			PrayerUnlocks unlocks = prayerUnlocks != null
				? prayerUnlocks : PrayerUnlocks.ALL;
			exportProfile(new PlayerProfile(
				real, live, unlocks, profile, ownedItems(), ledger.bankKnown()));
			optimizerService.bestPerStyle(monster, real, live, unlocks, profile, owned, fingerprint, f2pOnly,
				onSlayerTask, spellbookLock, exclusions.snapshot(), maxTradeables, riskBudgetGp, antifirePotion,
				dreams.snapshot(), upgradeBudgetGp, mode,
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
			// The classic InventoryID enum has no looting bag entry; the
			// gameval id is the authoritative modern constant.
			case LOOTING_BAG: return net.runelite.api.gameval.InventoryID.LOOTING_BAG;
			default: return InventoryID.BANK.getId();
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
