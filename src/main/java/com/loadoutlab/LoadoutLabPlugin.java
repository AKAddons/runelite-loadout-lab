package com.loadoutlab;

import net.runelite.client.plugins.banktags.tabs.LayoutManager;
import com.loadoutlab.collection.*;
import com.loadoutlab.data.*;
import com.google.gson.Gson;
import com.google.inject.Provides;
import com.loadoutlab.engine.*;
import com.loadoutlab.optimizer.OptimizerService;
import com.loadoutlab.profile.PlayerProfile;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Map;
import java.util.Set;
import java.util.List;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.*;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.*;
import net.runelite.client.ui.*;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.tabs.*;
import net.runelite.client.game.chatbox.ChatboxItemSearch;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import com.loadoutlab.model.CommandEngine;
import com.loadoutlab.model.CompanionLink;
import com.loadoutlab.render.*;
import com.loadoutlab.ui.*;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;

/**
 * Loadout Lab - best-in-slot from the gear YOU own.
 *
 * <p>Pick a monster; the plugin computes the strongest set you actually have,
 * per combat style, with exact DPS - from live knowledge of your bank,
 * inventory, and equipment, and a local DPS engine.
 */
@Slf4j
@PluginDependency(net.runelite.client.plugins.banktags.BankTagsPlugin.class)
@PluginDescriptor(
	name = "Loadout Lab",
	description = "Best-in-slot sets from the gear you own, per enemy and combat style, with exact DPS",
	tags = {"gear", "bis", "dps", "loadout", "equipment"}
)
public class LoadoutLabPlugin extends Plugin {
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
	private okhttp3.OkHttpClient okHttpClient;

	private MonsterIcons monsterIcons;

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
	private net.runelite.client.game.SkillIconManager skillIconManager;

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
	private ProtectOnlyStore protectOnly;
	/** "Show in bank": the expanded id set the overlay outlines; null = off. */
	private volatile Set<Integer> bankHighlight;
	/** "Filter bank": a VIRTUAL bank tag (never persisted to the player's
	 * tag config) containing the active set's expanded ids; null = off. */
	private volatile Set<Integer> bankFilter;
	private static final String BANK_TAG = "loadout-lab";
	/** DWMS's exact plugin name - detection and icon lookup key off it. */
	private static final String DWMS_PLUGIN_NAME = "Dude, Where's My Stuff?";
	private BankHighlightOverlay bankOverlay;
	private DreamStore dreams;
	private MonsterProfileStore mobProfiles;
	private AlwaysFilterStore alwaysFilter;
	private SupplyDefaultsStore supplyDefaults;
	private DwmsLink dwmsLink;
	private volatile CompanionLink companionLink;
	private volatile CommandEngine commandEngine;
	private volatile RenderSurface internalSurface;
	private CompanionHost companionHost;
	private LoadoutData data;
	/** Vendored STASH-unit table; loaded off-thread, read on game ticks. */
	private volatile StashUnits stashUnits;
	/** One chart read per opening, mirroring the container-scan coalescing. */
	private boolean stashChartSeen;
	private OptimizerService optimizerService;
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
	/** Last published world type, so the F2P filter is forced only on a flip
	 * and a manual untick survives a loading zone. */
	private Boolean lastF2pWorld;
	private Integer lastPvpWorld;
	private PrayerUnlocks prayerUnlocks;
	/** Account the snapshot above was taken for (-1 = none/hashless). */
	private long snapshotAccount = -1;

	/** Container-change coalescing - events mark, the per-tick drain scans. */
	/** Trailing debounce for ledger-driven recomputes (game ticks). */
	private int ledgerRecomputeTicks;

	private final EnumSet<CollectionLedger.Source> dirtySources =
		EnumSet.noneOf(CollectionLedger.Source.class);

	/** The rune pouch's six slots: a type varbit (RUNEPOUCH_RUNE enum key)
	 * and a quantity varbit each, as RuneLite's own pouch overlay reads them. */
	private static final int[] POUCH_TYPES = {VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2,
		VarbitID.RUNE_POUCH_TYPE_3, VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6};
	private static final int[] POUCH_QUANTITIES = {VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2,
		VarbitID.RUNE_POUCH_QUANTITY_3, VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6};

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
	public void onPluginMessage(PluginMessage event) {
		// DWMS storages-response (see DwmsLink): arrives on DWMS's client
		// thread; the parse is pure and the snapshot swap volatile, so only
		// the provenance-label refresh marshals (to the EDT).
		if (DwmsLink.DWMS_NAMESPACE.equals(event.getNamespace())
			&& DwmsLink.RESPONSE_NAME.equals(event.getName())) {
			DwmsLink link = dwmsLink;
			if (link != null) {
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
			&& StoragesApi.REQUEST_NAME.equals(event.getName())) {
			String requester = StoragesApi.requester(event.getData());
			if (requester != null) {
				clientThread.invokeLater(() -> respondWithStorages(requester));
			}
			return;
		}
		if (!"loadoutlab".equals(event.getNamespace()) || !"search".equals(event.getName())) {
			return;
		}
		Object monster = event.getData().get("monster");
		String name = monster instanceof String ? (String) monster : null;
		SwingUtilities.invokeLater(() -> {
			CommandEngine engine = commandEngine;
			if (engine == null || navButton == null || name == null) {
				return; // dataset still loading - drop rather than queue
			}
			if (engine.execute("select", Map.of("query", name))) {
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
	public void onMenuOpened(MenuOpened event) {
		if (data == null || commandEngine == null || navButton == null || !config.npcRightClickEntry()) {
			return;
		}
		for (MenuEntry entry : event.getMenuEntries()) {
			NPC npc = entry.getNpc();
			if (npc == null || npc.getName() == null) {
				continue;
			}
			final String name = npc.getName();
			final int id = npc.getId();
			if (!attackable(npc)) {
				// Corpus membership is not enough (Discord field report
				// 2026-07-23: a Rabbit got the entry): only NPCs whose own
				// menu offers Attack get one - the entry rides fights, not
				// scenery.
				return;
			}
			if (!knownMonster(name, id)) {
				return; // an NPC menu, but not one we can compute for
			}
			client.createMenuEntry(1)
				.setOption("Search in Loadout Lab")
				.setTarget(entry.getTarget())
				.setType(MenuAction.RUNELITE)
				.onClick(e -> SwingUtilities.invokeLater(() -> {
					CommandEngine engine = commandEngine;
					if (engine != null && engine.execute("select", Map.of("query", name))) {
						clientToolbar.openPanel(navButton);
					}
				}));
			return; // one entry, even when several rows reference the NPC
		}
	}

	/** The NPC's own right-click menu offers Attack - composition actions,
	 * client thread. */
	private static boolean attackable(NPC npc) {
		return npc.getComposition() != null && npc.getComposition().getActions() != null
			&& Arrays.stream(npc.getComposition().getActions()).anyMatch("Attack"::equalsIgnoreCase);
	}

	/** Cheap client-thread gate: exact id or normalized-name match. */
	private boolean knownMonster(String name, int id) {
		String normalized = name.toLowerCase().replaceAll("[^a-z0-9]", "");
		return data.getMonsters().stream().anyMatch(m -> m.getId() == id
			|| m.getName().toLowerCase().replaceAll("[^a-z0-9]", "").equals(normalized));
	}

	protected void startUp() {
		ledger = new CollectionLedger(configManager, gson);
		exclusions = new ExclusionStore(configManager, gson);
		protectOnly = new ProtectOnlyStore(configManager, gson);
		dreams = new DreamStore(configManager, gson);
		mobProfiles = new MonsterProfileStore(configManager, gson);
		alwaysFilter = new AlwaysFilterStore(configManager, gson);
		supplyDefaults = new SupplyDefaultsStore(configManager, gson);
		stores = new ScopedStore[]{ledger, exclusions, dreams, protectOnly, alwaysFilter, mobProfiles, supplyDefaults};
		dwmsLink = new DwmsLink();
		companionLink = new CompanionLink();
		bankOverlay = new BankHighlightOverlay(() -> bankHighlight);
		overlayManager.add(bankOverlay);
		// Bank-tag hygiene: drop the layout Bank Tag Layouts auto-enabled on
		// our tag before the real-tab fix existed, any core layout a crash
		// left behind, and our tab name if a user tab-edit ever persisted
		// the in-memory tab into the tagtabs CSV (we never save it ourselves).
		configManager.unsetConfiguration(BTL_GROUP, BTL_LAYOUT_KEY);
		layoutManager.removeLayout(BANK_TAG);
		forgetRememberedTab();
		String tagtabs = configManager.getConfiguration("banktags", "tagtabs");
		if (tagtabs != null && tagtabs.contains(BANK_TAG)) {
			List<String> names = new ArrayList<>(Text.fromCSV(tagtabs));
			if (names.remove(BANK_TAG)) {
				configManager.setConfiguration("banktags", "tagtabs", Text.toCSV(names));
				configManager.unsetConfiguration("banktags", "icon_" + BANK_TAG);
			}
		}
		if (client.getGameState() == GameState.LOGGED_IN) {
			loadAllScopes();
			requestDwmsStorages();
			dirtySources.addAll(EnumSet.allOf(CollectionLedger.Source.class));
		}

		// The dataset is ~3MB of gzipped JSON - parse off the startup path.
		Thread loader = new Thread(() -> {
			try {
				stashUnits = StashUnits.load();
			} catch (RuntimeException ex) {
				log.warn("STASH unit table unavailable; chart scans disabled", ex);
			}
			LoadoutData loaded = new DataService().load();
			Set<Integer> degradable = Degradable.ids(loaded);
			SwingUtilities.invokeLater(() -> {
				data = loaded;
				mobProfiles.setDegradableIds(degradable);
				optimizerService = new OptimizerService(loaded);
				commandEngine = new CommandEngine(
					loaded, new com.loadoutlab.model.PageState(), this::compute, companionLink);
				commandEngine.setCounts(() -> {
					List<Map<String, Object>> excludedItems = rows(exclusions.snapshot());
					List<Map<String, Object>> simmedItems = rows(dreams.snapshot());
					List<Map<String, Object>> filteredItems = new ArrayList<>();
					alwaysFilter.all().forEach((id, name) -> filteredItems.add(row(id, name, null)));
					return JsonResources.map("excluded", excludedItems.size(), "simmed", simmedItems.size(),
						"simmedNames", simmedItems.stream().map(r -> r.get("name")).collect(Collectors.toList()),
						"excludedItems", excludedItems, "simmedItems", simmedItems, "filteredItems", filteredItems,
						"taskBracelet", config.slayerTaskBracelet().name());
				});
				commandEngine.setRosterCompute(this::computeRoster);
				commandEngine.setSupplyDefaults(this::buildSupplyDefaults);
				commandEngine.setOwnedCheck(this::ownsOrSimmed);
				// The slot ladder's "how to get it" opens Not Enough Runes.
				commandEngine.setNerLink(this::nerPresent, this::openNer);
				// Source dots: the storage an item must be fetched from
				// (at-hand gear returns "" and stays unmarked).
				commandEngine.setItemLocation(this::primaryLocationOf);
				// Classic seedAssumptionDefaults: spec from config
				// (default ON), thralls when the tier is reachable and
				// the Book of the Dead is owned, Death Charge at Magic 80.
				commandEngine.setAssumptionSeeder(() -> {
					// Both Arceuus assumptions read the level the player can
					// actually REACH: live boost, or base + best OWNED potion
					// (never MAXED as a stand-in for unknown levels - that
					// fallback seeded Death Charge ON for a fresh login, and
					// on a F2P account: field report 2026-08-27).
					OwnedItems bag = new OwnedItems(ownedItems(),
						ledger != null && ledger.bankKnown());
					int magic = com.loadoutlab.optimizer.BoostSelector
						.reachableMagic(realLevels, boostedLevels, bag);
					boolean members = !Boolean.TRUE.equals(lastF2pWorld);
					Map<String, Object> seeds = new LinkedHashMap<>();
					seeds.put("specWeapon", config.defaultSpecWeapon());
					seeds.put("optimizeMode", config.defaultOptimizeMode().name());
					// DETECT keeps the derived answer; NONE means off, as
					// the settings promise. Both were hardcoded and
					// ignored the config outright (audit 2026-08-22).
					boolean detectThralls =
						config.defaultThralls() == LoadoutLabConfig.AssumeDefault.DETECT;
					seeds.put("thralls", detectThralls && members
						&& ExtraDps.thrallDps(magic) > 0
						&& ownsCanonical(ExtraDps.BOOK_OF_THE_DEAD));
					// Death Charge: Arceuus, Magic 90, A Kingdom Divided, and
					// a blood + death + soul rune per cast - seed it only for
					// a player who can actually cast it (wiki-verified).
					boolean detectCharge =
						config.defaultDeathCharge() == LoadoutLabConfig.AssumeDefault.DETECT;
					RequirementProfile reqs = requirementProfile;
					boolean canCharge = members && magic >= 90
						&& reqs != null && reqs.getCompletedQuests()
							.contains(Quest.A_KINGDOM_DIVIDED.name())
						&& SpellRunes.premiumRunesOwned("Death Charge", bag);
					seeds.put("deathCharge", detectCharge && canCharge ? 1 : 0);
					seeds.put("upgradeBudgetGp",
						Gp.parse(config.defaultUpgradeBudget()));
					// The configured wilderness cap - inert since the
					// merge-back (field report 2026-08-22: the chip showed
					// 'Risk 75k' while nothing constrained the answer,
					// because this default was never seeded and the value
					// itself collided with the engine's no-cap sentinel).
					int cap = Gp.parse(config.defaultRiskCap());
					seeds.put("riskBudgetGp", cap > 0 ? cap : null);
					// The per-character ship config (cannons/crew/ammo)
					// stays consistent across all ship combat - seeded from
					// the scoped store on every new selection.
					if (supplyDefaults != null) {
						for (String key : CommandEngine.SHIP_PERSISTED) {
							String saved = supplyDefaults.choice("ship." + key);
							if (saved != null
								&& !SupplyDefaultsStore.DETECT_BEST.equals(saved)) {
								if ("cannonCount".equals(key)) {
									try {
										seeds.put(key, Integer.parseInt(saved));
									} catch (NumberFormatException ignored) {
									}
								} else {
									seeds.put(key, saved);
								}
							}
						}
					}
					return seeds;
				});
				// The chips exist before the first search (idle page).
				commandEngine.publishIdle();
				clientThread.invokeLater(() -> commandEngine.setLiveSpellbook(spellbookName(
					client.getVarbitValue(VarbitID.SPELLBOOK))));
				commandEngine.setCoreVersion(PluginVersion.VERSION);
				// The network features (individually reviewable): the
				// wiki-calc shortlink is strictly CLICK-initiated; the
				// monster thumbnails are config-gated (Connections).
				WikiCalcLink wikiCalc = new WikiCalcLink(okHttpClient, gson);
				commandEngine.setWikiCalcOpener((mob, shown, dartId, assumes, onTask, wildy) ->
					wikiCalc.open(mob, shown, dartId, assumes,
						realLevels != null ? realLevels : PlayerLevels.MAXED,
						boostedLevels != null ? boostedLevels : PlayerLevels.MAXED,
						onTask, wildy));
				// Detect (classic resolveDefaultAntifire): only for a
				// fire-breathing selection, best owned tier wins.
				commandEngine.setAntifireResolver(mobs -> mobs.stream().noneMatch(DragonfireRules::breathesFire) ? 0
					: Arrays.stream(DragonfireRules.SUPER_ANTIFIRE_IDS).anyMatch(this::ownsOrSimmed) ? 2
					: Arrays.stream(DragonfireRules.REGULAR_ANTIFIRE_IDS).anyMatch(this::ownsOrSimmed) ? 1 : 0);
				// The model-driven renderer lives in core - single plugin,
				// single surface (the 2026-08 merge-back).
				CommandSink sink = (n, a) -> {
					CommandEngine engine = commandEngine;
					if (engine != null) {
						engine.execute(n, a);
					}
				};
				ItemPicker picker = (prompt, onPicked) ->
					itemSearchView().search(prompt, onPicked);
				ResultCards cards =
					new ResultCards(itemManager, spriteManager, sink, picker);
				monsterIcons = new MonsterIcons(okHttpClient);
				cards.setMonsterIcons(monsterIcons, () -> config.fetchMonsterIcons());

				internalSurface = new RenderSurface(cards,
					() -> companionLink == null ? null : companionLink.lastPage(), sink, picker,
					(query, onResults) -> {
						CommandEngine engine = commandEngine;
						onResults.accept(engine == null
							? List.of() : engine.searchOptions(query));
					});
				internalSurface.setSpriteManager(spriteManager);
				cards.setDegradableChip(() -> config.showDegradableChip());
				cards.setBreakdown(() -> config.showTripBreakdown(),
					shown -> configManager.setConfiguration("loadoutlab", "showTripBreakdown", shown));
				cards.setSailingIcon(() ->
					skillIconManager.getSkillImage(Skill.SAILING, true));
				// Two display settings that described behaviour they did
				// not control until 2026-08-22.
				internalSurface.setDisplayGates(
					() -> config.loadingAnimation(), () -> config.showWildyRisk(),
					() -> config.showPvpWorldChip());
				companionLink.setPageListener(() -> {
					RenderSurface surface = internalSurface;
					if (surface != null) {
						surface.onModelChanged();
					}
					refreshHostedView();
				});
				companionLink.setStatusListener(computing -> {
					RenderSurface surface = internalSurface;
					if (surface != null) {
						surface.setComputing(computing);
					}
				});
				commandEngine.setStoreOps(new CommandEngine.StoreOps() {
					public boolean toggleExclusion(int itemId) {
						exclusions.toggle(itemId);
						return true;
					}

					public boolean toggleSim(int itemId) {
						dreams.toggle(itemId);
						return true;
					}

					public void setSupplyDefault(String category, String choice) {
						supplyDefaults.setChoice(category, choice);
					}

					@Override
					public void setTaskBracelet(String choice) {
						configManager.setConfiguration("loadoutlab", "slayerTaskBracelet", choice);
					}

					@Override
					public void toggleAlwaysFilter(int itemId) {
						if (alwaysFilter.all().containsKey(itemId)) {
							alwaysFilter.remove(itemId);
						} else {
							alwaysFilter.add(itemId, itemLabel(itemId));
						}
					}

					public void showInBank(Set<Integer> itemIds) {
						setBankHighlight(itemIds);
					}

					public void filterBank(Set<Integer> itemIds, int[] layout) {
						setBankFilter(itemIds, layout);
					}

					public void setPinnedSpec(int monsterId, int itemId) {
						mobProfiles.setPinnedSpec(monsterId, itemId);
					}

					public void excludeForMob(int monsterId, String scope, int itemId) {
						mobProfiles.exclude(monsterId, scope, itemId);
					}

					public void simForMob(int monsterId, int itemId) {
						mobProfiles.addSim(monsterId, itemId, itemLabel(itemId));
					}

					public List<Map<String, Object>> mobExclusions(int monsterId) {
						List<Map<String, Object>> out = new ArrayList<>();
						mobProfiles.allExclusions(monsterId).forEach((scope, ids) ->
							ids.forEach(id -> out.add(row(id, itemLabel(id), scope))));
						return out;
					}

					public List<Map<String, Object>> mobSims(int monsterId) {
						List<Map<String, Object>> out = new ArrayList<>();
						mobProfiles.allSims(monsterId).forEach((id, name) -> out.add(row(id, name, null)));
						return out;
					}

					public List<Map<String, Object>> mobFilters(int monsterId) {
						List<Map<String, Object>> out = new ArrayList<>();
						mobProfiles.allFilterItems(monsterId).forEach((scope, items) ->
							items.forEach((id, name) -> out.add(row(id, name, scope))));
						return out;
					}

					public void addMobFilter(int monsterId, int itemId) {
						mobProfiles.addFilterItem(monsterId,
							MonsterProfileStore.ALL,
							itemId, itemLabel(itemId));
					}

					public CommandEngine.MobProfiles mobs() {
						return mobProfiles;
					}

					public boolean skipDegradable(int monsterId) {
						return mobProfiles.skipDegradable(monsterId, config.skipDegradableDefault());
					}

				});
			// The one-surface host: the model-driven renderer IS the
			// panel. (No cross-plugin registration exists any more - the
			// companion split was retired in the 2026-08 merge-back.)
			companionHost = new CompanionHost();
			// The bundled sidebar icon (see scripts/generate_icons.py) ships
			// inside the jar, so a missing icon is a broken build and may fail
			// startUp loudly - the runtime-drawn "LL" fallback it once had
			// was unreachable in a built plugin (slim, 2026-10-23).
			navButton = NavigationButton.builder()
					.tooltip("Loadout Lab")
					.icon(ImageUtil.loadImageResource(LoadoutLabPlugin.class, "icon.png"))
					.priority(7)
					.panel(companionHost)
					.build();
				clientToolbar.addNavigation(navButton);
				// A Companion that registered while we were loading mounts now.
				refreshHostedView();
			});
		}, "loadout-lab-data-loader");
		loader.setDaemon(true);
		loader.start();

		log.info("Loadout Lab started");
	}

	protected void shutDown() {
		if (monsterIcons != null) {
			monsterIcons.shutdown();
			monsterIcons = null;
		}
		if (bankOverlay != null) {
			overlayManager.remove(bankOverlay);
			bankOverlay = null;
		}
		bankHighlight = null;
		bankFilter = null;
		dropBankTag();
		if (navButton != null) {
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		if (optimizerService != null) {
			optimizerService.shutdown();
			optimizerService = null;
		}
		data = null;
		ledger = null;
		exclusions = null;
		protectOnly = null;
		mobProfiles = null;
		stores = new ScopedStore[0];
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
	public void onAccountHashChanged(AccountHashChanged event) {
		resetForIdentityChange();
	}

	/** The Connections toggle changed: effective ownership changed with it,
	 * so refresh the shown answer (the ownership fingerprint keys the
	 * optimizer cache - neither direction can serve a stale set). */
	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!"loadoutlab".equals(event.getGroup())) {
			return;
		}
		// The stores share this config group (ledger, exclusions, dreams,
		// profiles all write "loadoutlab" keys) and fire ConfigChanged on
		// every drain - a bank deposit is an event storm. React ONLY to the
		// wrench-panel keys, never a store write, or each ledger flush would
		// rebuild the whole results panel on the EDT.
		String key = event.getKey();
		CommandEngine engine = commandEngine;
		if (engine == null) {
			return;
		}
		if ("useDwmsData".equals(key) || "slayerTaskBracelet".equals(key)) {
			// Ownership or the task bracelet changed -> recompute (not just re-render).
			engine.execute("recompute", Map.of());
		} else if (RENDER_KEYS.contains(key)) {
			// A display gate the renderer reads at paint - redraw the
			// held answer so the setting takes effect at once, without
			// paying for a compute.
			engine.execute("republish", Map.of());
		} else if ("resetCustomizations".equals(key) && config.resetCustomizations()) {
			// Lostmind (Discord 2026-09-17): customisations live in the
			// RuneLite profile, so a reinstall cannot clear them. Andrew
			// (2026-09-18): a just-installed state - settings back to their
			// defaults and the page back to empty as well. Bank memory is
			// not a customisation and stays.
			for (ScopedStore store : stores) {
				store.clear();
			}
			LoadoutLabConfig.unsetAll(configManager, config);
			engine.clearForIdentityChange();
		}
	}

	/** Settings the RENDERER honours, so flipping one must redraw. The
	 * old PANEL_CONFIG_KEYS listed 42 keys for a panel that no longer
	 * exists and nothing ever read it (audit 2026-08-22). */
	private static final Set<String> RENDER_KEYS =
		Set.of("loadingAnimation", "showWildyRisk", "fetchMonsterIcons", "showTripBreakdown", "showDegradableChip",
			"showPvpWorldChip");

	/** Client-thread staging push: the castability state the panel's
	 * chips read - BOOSTED magic (field call 2026-07-21: castability
	 * follows the boosted stat), the Yama rite unlock, the live book. */
	/** Live cast state for the engine: the Magic level drives thrall
	 * tiers. (The classic panel's spellbook/D-charge display state died
	 * with it; detect-grade spellbook awareness returns via the model
	 * when the arceuus-clash rendering ports.) */
	private void pushPanelCastState(PlayerLevels live) {
		CommandEngine engine = commandEngine;
		if (engine != null) {
			engine.setMagicLevel(live.getMagic());
			engine.setRangedLevel(live.getRanged());
			// Sailing is not a PlayerLevels combat stat - read it straight
			// off the client for the crew cannon's (stale-formula) base.
			engine.setSailingLevel(client.getBoostedSkillLevel(
				Skill.SAILING));
		}
	}

	/** The persistent trip-supply defaults (choice keys by TripSupplies
	 * category) - store-backed, every category DETECT_BEST until the grey
	 * chip's menu changes it. The panel resolves them per result. */
	private Map<String, String> buildSupplyDefaults() {
		Map<String, String> defaults = new LinkedHashMap<>();
		List<String> categories = new ArrayList<>(List.of(TripSupplies.CATEGORY_KEYS));
		categories.add("arceuusAccess");
		for (String category : categories) {
			defaults.put(category, supplyDefaults == null
				? SupplyDefaultsStore.DETECT_BEST
				: supplyDefaults.choice(category));
		}
		return defaults;
	}

	/** The RuneLite config profile changed: config-backed stores re-read. */
	@Subscribe
	public void onProfileChanged(ProfileChanged event) {
		for (ScopedStore store : stores) {
			store.reload();
		}
		resetForIdentityChange();
	}

	private void resetForIdentityChange() {
		loadAllScopes();
		if (dwmsLink != null) {
			// The live snapshot belongs to the PREVIOUS identity; drop it and
			// re-ask. DWMS re-answers for whoever is logged in now.
			dwmsLink.reset();
			requestDwmsStorages();
		}
		if (!PlayerProfile.snapshotSurvives(snapshotAccount, client.getAccountHash())) {
			// A different character: their levels must not price this one's
			// bank. Plain logout KEEPS the snapshot - a logged-out browse
			// prices the last character, not a maxed stranger (field report
			// 2026-09-01: Piety suggested at 45 prayer).
			requirementProfile = null;
			realLevels = null;
			boostedLevels = null;
			prayerUnlocks = null;
		}
		canonicalOwnedCache = null;
		bankHighlight = null;
		bankFilter = null;
		if (optimizerService != null) {
			optimizerService.clearCache();
		}
		dirtySources.addAll(EnumSet.allOf(CollectionLedger.Source.class));
		pendingScans.clear();
		stashChartSeen = false;
		SwingUtilities.invokeLater(() -> {
			CommandEngine engine = commandEngine;
			if (engine != null) {
				engine.clearForIdentityChange();
			}
		});
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event) {
		if (event.getGameState() == GameState.LOGGED_IN) {
			loadAllScopes();
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
			// LOGGED_IN re-fires on every region change, so only FORCE the
			// filter when the world type actually flipped - otherwise walking
			// through a loading zone would undo a manual untick.
			boolean f2p = onF2pWorld();
			boolean flipped = lastF2pWorld == null || lastF2pWorld != f2p;
			lastF2pWorld = f2p;
			// PvP / High Risk worlds seed the PvP mode the same way: forced
			// only on a flip, so a manual pick survives region loads.
			EnumSet<WorldType> types = client.getWorldType();
			int pvp = PvpRisk.worldMode(types.contains(WorldType.PVP),
				types.contains(WorldType.HIGH_RISK));
			boolean pvpFlipped = lastPvpWorld == null || lastPvpWorld != pvp;
			lastPvpWorld = pvp;
			// Karuulm's boots rule lifts with the elite Kourend & Kebos diary.
			boolean kourendElite = client.getVarbitValue(VarbitID.KOUREND_DIARY_ELITE_COMPLETE) == 1;
			SwingUtilities.invokeLater(() -> {
				CommandEngine engine = commandEngine;
				if (engine != null) {
					engine.set("f2pWorld", f2p);
					if (flipped) {
						engine.set("f2pOnly", f2p);
					}
					engine.set("pvpWorldHere", pvp > 0);
					engine.set("kourendElite", kourendElite);
					if (pvpFlipped) {
						engine.set("pvpWorld", pvp);
					}
				}
			});
		}
	}

	/** Re-fetched by id at drain time; every other container (POH costume
	 * storage, the looting bag, the cargo holds) may not be re-fetchable
	 * later under the unmasked id, so its contents are captured off the
	 * event (rare one-shot opens, not bank-style event storms). */
	private static final EnumSet<CollectionLedger.Source> LIVE_SOURCES = EnumSet.of(
		CollectionLedger.Source.EQUIPMENT, CollectionLedger.Source.INVENTORY, CollectionLedger.Source.BANK);

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event) {
		// Storage containers (POH costume storage confirmed in the field)
		// arrive with the 0x8000 flag set on the id - the same masking
		// DWMS applies. Field report: the treasure chest fired 0x8000|637
		// and the unmasked comparison missed it.
		int id = event.getContainerId();
		if (id >= 0x8000) {
			id -= 0x8000;
		}
		CollectionLedger.Source source = CollectionLedger.Source.forContainer(id);
		if (source == null) {
			return;
		}
		if (!LIVE_SOURCES.contains(source)) {
			// The per-tick drain still does the coalesced ledger write.
			ItemContainer container = event.getItemContainer();
			if (container != null) {
				pendingScans.put(source, itemsOf(container));
			}
		}
		dirtySources.add(source);
	}

	/**
	 * Checking the looting bag does NOT fire ItemContainerChanged
	 * (field-tested; DWMS polls this container per tick for the same
	 * reason). The Check screen opening is the capture moment - scan the
	 * container for a few ticks so contents that land a tick late are
	 * still seen. An empty-over-empty scan is a no-op write.
	 */
	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event) {
		if (event.getGroupId() == InterfaceID.WILDERNESS_LOOTINGBAG) {
			lootingBagScanTicks = 3;
		}
	}

	// The container id per source (looting bag, POH costume storage, the
	// five sailing cargo holds) now lives on CollectionLedger.Source, so
	// both directions of the mapping come from one table there.

	@Subscribe
	public void onGameTick(GameTick event) {
		if (client.getGameState() != GameState.LOGGED_IN) {
			return;
		}
		scanStashChart();
		// The pouch is read every tick (twelve varbits) rather than filtered out
		// of the per-tick varbit spray - but it goes DIRTY only on a change:
		// a dirty source every tick starved the quiet-tick branch below, so the
		// ledger-settled refresh never fired and a card computed before the
		// bank scan kept its empty Detect-best supplies (Andrew, 2026-10-15,
		// live in 0.5.4).
		Map<Integer, Integer> pouch = scanRunePouch();
		if (!pouch.equals(ledger.snapshot(CollectionLedger.Source.RUNE_POUCH))) {
			pendingScans.put(CollectionLedger.Source.RUNE_POUCH, pouch);
			dirtySources.add(CollectionLedger.Source.RUNE_POUCH);
		}
		if (lootingBagScanTicks > 0) {
			lootingBagScanTicks--;
			dirtySources.add(CollectionLedger.Source.LOOTING_BAG);
		}
		if (dirtySources.isEmpty()) {
			// Quiet tick: the debounce countdown runs HERE (the early
			// return would starve it otherwise).
			tickLedgerDebounce();
			return;
		}
		boolean ledgerChanged = false;
		for (CollectionLedger.Source source : EnumSet.copyOf(dirtySources)) {
			Map<Integer, Integer> pending = pendingScans.remove(source);
			if (pending != null) {
				ledgerChanged |= ledger.update(source, pending);
				dirtySources.remove(source);
				continue;
			}
			ItemContainer c = client.getItemContainer(source.containerId());
			if (c == null) {
				dirtySources.remove(source);
				continue;
			}
			ledgerChanged |= ledger.update(source, itemsOf(c));
			dirtySources.remove(source);
		}
		if (ledgerChanged) {
			// A card created before this container was known may sit on a
			// stale DETECTED default (field report 2026-08-08: Vorkath
			// froze on "gear only" with super antifires in the bank).
			// DEBOUNCED (field report 2026-08-15: Moons of Peril hands out
			// consumables every room, so presence changed per transition
			// and the full search re-ran each time) - one recompute, five
			// quiet ticks after the churn stops.
			ledgerRecomputeTicks = 5;
		} else {
			tickLedgerDebounce();
		}
	}

	private void tickLedgerDebounce() {
		if (ledgerRecomputeTicks > 0 && --ledgerRecomputeTicks == 0) {
			SwingUtilities.invokeLater(() -> {
				CommandEngine engine = commandEngine;
				if (engine != null) {
					engine.execute("refresh", Map.of());
				}
			});
		}
	}

	/** The pouch's runes by item id (Mumblerit, Discord 2026-10-10: runes
	 * moved into the pouch vanished and Fire Wave fell off the magic set). */
	private Map<Integer, Integer> scanRunePouch() {
		int[] types = new int[POUCH_TYPES.length];
		int[] quantities = new int[POUCH_TYPES.length];
		for (int i = 0; i < POUCH_TYPES.length; i++) {
			types[i] = client.getVarbitValue(POUCH_TYPES[i]);
			quantities[i] = client.getVarbitValue(POUCH_QUANTITIES[i]);
		}
		EnumComposition runes = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		return RunePouchScan.items(types, quantities, type -> runes == null ? 0 : runes.getIntValue(type));
	}

	private static Map<Integer, Integer> itemsOf(ItemContainer container) {
		Map<Integer, Integer> items = new HashMap<>();
		for (Item item : container.getItems()) {
			if (item.getId() > 0 && item.getQuantity() > 0) {
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
	private void scanStashChart() {
		if (client.getWidget(493, 2) == null) {
			stashChartSeen = false;
			return;
		}
		StashUnits units = stashUnits;
		if (stashChartSeen || units == null) {
			return;
		}
		stashChartSeen = true;
		Map<Integer, Integer> items = new HashMap<>();
		for (int childId : STASH_TIER_CHILDREN) {
			Widget tier = client.getWidget(493, childId);
			if (tier == null || tier.getChildren() == null) {
				continue;
			}
			List<StashUnits.Cell> cells = Arrays.stream(tier.getChildren()).filter(Objects::nonNull)
				.map(child -> new StashUnits.Cell(child.getType(), child.getText())).collect(Collectors.toList());
			for (String name : StashUnits.filledNames(cells, childId == 14)) {
				int[] ids = units.itemsFor(name);
				if (ids == null) {
					continue;
				}
				for (int id : ids) {
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

	/** The detections' owned check: the canonical owned scan plus the sims
	 * (Andrew, client test 2026-10-19: a simmed sunfire rune must count). */
	private boolean ownsOrSimmed(int itemId) {
		return ownsCanonical(itemId) || dreams != null && dreams.isDreamed(itemId);
	}

	private boolean ownsCanonical(int itemId) {
		if (data == null) {
			return ledger != null && ownedItems().containsKey(itemId);
		}
		int fingerprint = ownedFingerprint();
		Set<Integer> cache = canonicalOwnedCache;
		if (cache == null || canonicalOwnedFingerprint != fingerprint) {
			cache = data.canonicalizeOwned(ownedItems()).keySet();
			canonicalOwnedCache = cache;
			canonicalOwnedFingerprint = fingerprint;
		}
		return cache.contains(itemId);
	}

	/**
	 * The ledger view plus the potion storage and DWMS storages. Once DWMS has answered a PluginMessage request this session the live
	 * snapshot wins outright (guaranteed-correct parse, every storage);
	 * until then the best-effort config read fills in.
	 */
	/** The bank's potion storage, read from its varbits at each compute
	 * and on change (Andrew 2026-10-19: the super antifires lived there). */
	private volatile Map<Integer, Integer> potionStorage = Map.of();

	private Map<Integer, Integer> ownedItems() {
		Map<Integer, Integer> owned = ledger.owned();
		potionStorage.forEach((id, n) -> owned.merge(id, n, Integer::sum));
		if (!config.useDwmsData()) {
			// The Connections toggle is off: DWMS-tracked storages do not
			// count as owned (the stores stay loaded for a live re-enable).
			return owned;
		}
		return dwmsLink.mergeInto(owned);
	}

	/**
	 * Ownership fingerprint covering the ledger, the potion storage AND the
	 * effective DWMS source - the optimizer/panel cache key, so any of them
	 * changing is a real ownership change everywhere a bank deposit would be.
	 */
	private int ownedFingerprint() {
		int fingerprint = 31 * ledger.fingerprint() + potionStorage.hashCode();
		int dwms = config.useDwmsData() ? dwmsLink.snapshot().hashCode() : 0;
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
	private Map<String, Map<Integer, Integer>> ownedBySources() {
		LinkedHashMap<String, Map<Integer, Integer>> origins = new LinkedHashMap<>();
		origins.put("equipped", ledger.snapshot(CollectionLedger.Source.EQUIPMENT));
		origins.put("inventory", ledger.snapshot(CollectionLedger.Source.INVENTORY));
		origins.put("bank", ledger.snapshot(CollectionLedger.Source.BANK));
		origins.put("looting bag", ledger.snapshot(CollectionLedger.Source.LOOTING_BAG));
		origins.put("POH costume room", ledger.snapshot(CollectionLedger.Source.POH_COSTUMES));
		origins.put("STASH", ledger.snapshot(CollectionLedger.Source.STASH));
		Map<Integer, Integer> cargo = new HashMap<>();
		for (CollectionLedger.Source hold : CollectionLedger.Source.values()) {
			if (hold.name().startsWith("CARGO_HOLD")) {
				ledger.snapshot(hold).forEach((id, n) -> cargo.merge(id, n, Integer::sum));
			}
		}
		origins.put("cargo hold", cargo);
		origins.put("potion storage", potionStorage);
		if (config.useDwmsData()) {
			for (Map.Entry<String, Map<Integer, Integer>> family : dwmsLink.families().entrySet()) {
				origins.put(dwmsFamilyLabel(family.getKey()), family.getValue());
			}
		}
		origins.values().removeIf(Map::isEmpty);
		return origins;
	}

	/** DWMS response categories -> the origin labels the hints show. */
	private static final Map<String, String> DWMS_FAMILIES = Map.of("poh", "POH costume room", "stash", "STASH",
		"death", "death storage", "sailing", "cargo hold", "carryable", "carried container");

	private static String dwmsFamilyLabel(String family) {
		return DWMS_FAMILIES.getOrDefault(family, "world storage") + ItemLocations.VIA_DWMS;
	}

	/** The mob's effective pins per style (ALL overlaid by each style) -
	 * what the optimizer request for each card carries. A pin whose item
	 * has left the corpus (a data refresh) is no pin: the optimizer
	 * ignores it, and a roster member's shown set is refused over a pin
	 * it cannot wear (calcRespecting), so it must not travel. */
	private Map<CombatStyle, Map<GearSlot, Integer>> pinnedByStyle(MonsterStats mob, int monsterId, boolean onTask) {
		EnumMap<CombatStyle, Map<GearSlot, Integer>> byStyle =
			new EnumMap<>(CombatStyle.class);
		for (CombatStyle style : CombatStyle.values()) {
			Map<GearSlot, Integer> pins = new EnumMap<>(GearSlot.class);
			mobProfiles.pinsFor(monsterId, style.name()).forEach((slot, id) -> {
				if (data == null || data.getGear(id) != null) {
					pins.put(slot, id);
				}
			});
			if (!pins.isEmpty()) {
				byStyle.put(style, pins);
			}
		}
		return TaskBracelet.overlay(byStyle, mob, onTask, config.slayerTaskBracelet());
	}

	/** Item label for command descriptions, from the gear corpus (EDT-safe -
	 * never ItemManager, which is client-thread-only). */
	/** One trio-menu row: {id, name[, scope]}. */
	private static Map<String, Object> row(int id, String name, String scope) {
		return scope == null ? Map.of("id", id, "name", name) : Map.of("id", id, "name", name, "scope", scope);
	}

	/** {id, name} rows for a global store's ids, corpus-labelled. */
	private List<Map<String, Object>> rows(Set<Integer> ids) {
		return ids.stream().map(id -> row(id, itemLabel(id), null)).collect(Collectors.toList());
	}

	private String itemLabel(int itemId) {
		GearItem item = data == null ? null : data.getGear(itemId);
		return item == null ? ("item " + itemId) : item.label();
	}

	/** Effective exclusions per style: the global list unioned with this
	 * mob's ALL + style scopes. Styles with no mob exclusions share the
	 * global set instance, so their cache keys stay stable. A monster id
	 * no profile can hold (-1) therefore yields the plain global map -
	 * the roster path's base, with per-mob exclusions layered on top
	 * inside the optimizer. */
	private Map<CombatStyle, Set<Integer>> excludedByStyle(int monsterId) {
		Set<Integer> global = exclusions.snapshot();
		EnumMap<CombatStyle, Set<Integer>> byStyle =
			new EnumMap<>(CombatStyle.class);
		for (CombatStyle style : CombatStyle.concreteValues()) {
			Set<Integer> mob = mobProfiles.exclusionsFor(monsterId, style.name());
			if (mob.isEmpty()) {
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
	private Map<Integer, Map<CombatStyle, Set<Integer>>> perMobExclusions(
		List<MonsterStats> mobs) {
		Map<Integer, Map<CombatStyle, Set<Integer>>> byMob =
			new HashMap<>();
		for (MonsterStats mob : mobs) {
			EnumMap<CombatStyle, Set<Integer>> byStyle = new EnumMap<>(CombatStyle.class);
			for (CombatStyle style : CombatStyle.concreteValues()) {
				Set<Integer> per = mobProfiles.exclusionsFor(mob.profileId(), style.name());
				if (!per.isEmpty()) {
					byStyle.put(style, per);
				}
			}
			if (!byStyle.isEmpty()) {
				byMob.put(mob.profileId(), byStyle);
			}
		}
		return byMob;
	}

	/** The lensed mob's local sims join the global simmed set for a
	 * single-mob compute (field spec 2026-07-18). */
	private Set<Integer> dreamsWithMobSims(MonsterStats monster) {
		Set<Integer> global = dreams.snapshot();
		Set<Integer> local = mobProfiles == null
			? Set.of() : mobProfiles.simsFor(monster.profileId());
		if (local.isEmpty()) {
			return global;
		}
		Set<Integer> merged = new HashSet<>(global);
		merged.addAll(local);
		return merged;
	}

	/** Per-mob sims for a roster, keyed by profileId. */
	private Map<Integer, Set<Integer>> perMobSims(List<MonsterStats> mobs) {
		Map<Integer, Set<Integer>> byMob = new HashMap<>();
		if (mobProfiles == null) {
			return byMob;
		}
		for (MonsterStats mob : mobs) {
			Set<Integer> sims = mobProfiles.simsFor(mob.profileId());
			if (!sims.isEmpty()) {
				byMob.put(mob.profileId(), sims);
			}
		}
		return byMob;
	}

	/** The mob's pinned autocast spell resolved to the dataset, or null. */
	private SpellStats resolvedPinnedSpell(int monsterId) {
		String name = mobProfiles == null ? "" : mobProfiles.pinnedSpell(monsterId);
		if (name.isEmpty() || data == null) {
			return null;
		}
		return data.getSpells().stream().filter(spell -> spell.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
	}

	/**
	 * Panel hook: the NATIVE chatbox item search (field request - replaces
	 * the dialog matcher). Runs on the client thread; the pick resolves
	 * its display name there (the only thread where that is legal) and
	 * returns to the EDT.
	 */
	private ItemPicker itemSearchView() {
		return (prompt, onPicked) -> {
			clientThread.invokeLater(() ->
				chatboxItemSearch
					.tooltipText(prompt)
					.onItemSelected(itemId -> {
						String name = itemManager.getItemComposition(itemId).getName();
						SwingUtilities.invokeLater(() -> onPicked.accept(itemId, name));
					})
					.build());
			// The click came from the Swing sidebar - hand the OS focus to
			// the game so typing searches immediately (field request).
			clientUI.requestFocus();
		};
	}

	/**
	 * Fire-and-forget: ask DWMS for its tracked storages (see DwmsLink).
	 * Nothing is posted when the plugin is absent or disabled; a missing
	 * reply (a DWMS predating 2.11.5) just means its storages do not
	 * count until it updates.
	 */
	private void requestDwmsStorages() {
		if (!config.useDwmsData() || !pluginEnabled(DWMS_PLUGIN_NAME)) {
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
	private void respondWithStorages(String target) {
		CollectionLedger currentLedger = ledger;
		if (currentLedger == null) {
			return;
		}
		// Canonicalization needs the item cache; before the login screen
		// exists, ship raw ids rather than crash (DWMS guards the same way).
		IntUnaryOperator canonicalize =
			client.getGameState().getState() >= GameState.LOGIN_SCREEN.getState()
				? itemManager::canonicalize
				: IntUnaryOperator.identity();
		List<Map<String, Object>> storages = Arrays.stream(CollectionLedger.Source.values())
			.map(source -> StoragesApi.storage("collection", source.key(), -1L, currentLedger.snapshot(source), canonicalize))
			.filter(Objects::nonNull).collect(Collectors.toList());
		eventBus.post(new PluginMessage(StoragesApi.NAMESPACE, StoragesApi.RESPONSE_NAME,
			StoragesApi.response(target, storages)));
	}

	/** Not Enough Runes enabled? (A method reference for the engine's NER link.) */
	private boolean nerPresent() {
		return pluginEnabled("Not Enough Runes");
	}

	/** The named plugin is on. Check ALL same-named plugins - a hub copy
	 * and a sideloaded dev copy can coexist and the first match may be
	 * the disabled loser. Shared by the NER, DWMS and Bank Tag Layouts
	 * detections. */
	private boolean pluginEnabled(String name) {
		for (Plugin p : pluginManager.getPlugins()) {
			if (name.equals(p.getName()) && pluginManager.isPluginEnabled(p)) {
				return true;
			}
		}
		return false;
	}

	/** Open Not Enough Runes on an item - its own PluginMessage contract
	 * (Hannah-GBS/notenoughrunes: "displayItemById" {itemId: Integer}). */
	private void openNer(int itemId) {
		Map<String, Object> data = new HashMap<>();
		data.put("itemId", itemId);
		eventBus.post(new PluginMessage("notenoughrunes", "displayItemById", data));
	}

	/** A plugin turned on or off: the ladder's NER link follows. */
	@Subscribe
	public void onPluginChanged(net.runelite.client.events.PluginChanged event) {
		CommandEngine engine = commandEngine;
		if (engine != null) {
			SwingUtilities.invokeLater(() -> engine.setNerLink(this::nerPresent, this::openNer));
		}
	}

	/**
	 * The real account as a replayable fixture: written next to the usage
	 * log on every query, so any in-game result can be reproduced headless
	 * (./gradlew query) or attached to a bug report. Local only.
	 */
	private void exportProfile(PlayerProfile profile) {
		Thread writer = new Thread(() -> {
			try {
				Path file = new File(RuneLite.RUNELITE_DIR,
					"loadout-lab/profile.json").toPath();
				Files.createDirectories(file.getParent());
				Files.writeString(file, profile.toJson());
			} catch (Exception ex) {
				log.warn("could not export the player profile", ex);
			}
		}, "loadout-lab-profile-export");
		writer.setDaemon(true);
		writer.start();
	}

	/** Panel hook: set (or clear, with null) the bank-highlighted item ids. */
	private void setBankHighlight(Set<Integer> itemIds) {
		bankHighlight = itemIds == null || itemIds.isEmpty() || data == null ? null : equivalents(itemIds);
	}

	/** The ids plus every variant that canonicalizes with them. */
	private Set<Integer> equivalents(Set<Integer> itemIds) {
		Set<Integer> expanded = new HashSet<>();
		itemIds.forEach(id -> expanded.addAll(data.equivalentIds(id)));
		return expanded;
	}

	/** Panel hook: filter the open bank to these ids via a virtual tag, and -
	 * when {@code layout} is non-null - arrange the set in the bank in the
	 * equipment + inventory shape (a bank-tag layout position array). */
	private void setBankFilter(Set<Integer> itemIds, int[] layout) {
		if (itemIds == null || itemIds.isEmpty() || data == null) {
			bankFilter = null;
			clientThread.invokeLater(() -> {
				if (BANK_TAG.equals(bankTagsService.getActiveTag())) {
					bankTagsService.closeBankTag();
				}
				dropBankTag();
				forgetRememberedTab();
			});
			return;
		}
		bankFilter = equivalents(itemIds);
		clientThread.invokeLater(() -> {
			tagManager.registerTag(BANK_TAG, itemId -> {
				Set<Integer> ids = bankFilter;
				return ids != null && ids.contains(itemId);
			});
			if (layout != null) {
				layoutManager.saveLayout(new Layout(BANK_TAG, layout));
				ensureBankTagTab();
				bankTagsService.openBankTag(BANK_TAG, 0);
			} else {
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
	private void ensureBankTagTab() {
		if (!pluginEnabled("Bank Tag Layouts") || tabManager.find(BANK_TAG) != null) {
			return;
		}
		TagTab tab = new TagTab();
		tab.setTag(BANK_TAG);
		tab.setIconItemId(BANK_TAB_ICON);
		tabManager.add(tab);
	}

	private void removeBankTagTab() {
		if (tabManager.find(BANK_TAG) != null) {
			tabManager.remove(BANK_TAG);
		}
	}

	/** The virtual tag's layout, tab and registration all go (shutdown
	 * and the filter-clear share it). */
	private void dropBankTag() {
		layoutManager.removeLayout(BANK_TAG);
		removeBankTagTab();
		tagManager.unregisterTag(BANK_TAG);
	}

	/** Core bank-tags "Remember tab" reopens the last active tag at the
	 * next bank open - across sessions our virtual tag then leads to
	 * NOTHING (no filter registered yet; field bug 2026-07-21). Forgotten
	 * whenever a session starts and whenever the filter clears. */
	private void forgetRememberedTab() {
		if (BANK_TAG.equals(configManager.getConfiguration("banktags", "tab"))) {
			configManager.setConfiguration("banktags", "tab", "");
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
	public void onScriptPreFired(ScriptPreFired event) {
		if (event.getScriptId() != ScriptID.BANKMAIN_FINISHBUILDING
			|| !BANK_TAG.equals(bankTagsService.getActiveTag())) {
			return;
		}
		if (bankFilter == null) {
			// A remembered-tab ghost: our tag is open with no filter
			// registered - it shows an empty bank (field bug 2026-07-21).
			bankTagsService.closeBankTag();
			return;
		}
		ensureBankTagTab();
	}

	@Subscribe(priority = -0.5f)
	public void onScriptPostFired(ScriptPostFired event) {
		if (event.getScriptId() == ScriptID.BANKMAIN_BUILD
			&& bankFilter != null
			&& BANK_TAG.equals(bankTagsService.getActiveTag())) {
			ensureBankTagTab();
		}
	}

	/** Roster mirror of compute: same client-thread staging, then
	 * ONE shared set per style across the mobs (bestPerStyleAcross). Each
	 * member's own profile travels keyed by profileId: exclusions, sims,
	 * pins, the pinned spell and the pinned spec (the first member's spec
	 * is the roster's shared spec). */

	/** Client-thread staging shared by both computes: the profile snapshot,
	 * the DWMS re-read, levels, the one owned-items merge and its presence
	 * fingerprint (the optimizer cache keys on presence, not quantities). */
	private final class Staging {
		final RequirementProfile profile;
		final PlayerLevels live;
		final PlayerLevels real;
		final Map<Integer, Integer> mergedOwned;
		final OwnedItems owned;
		final int fingerprint;
		final PrayerUnlocks unlocks;

		Staging() {
			if (client.getGameState() == GameState.LOGGED_IN) {
				snapshotProfileIfNeeded();
			}
			if (config.useDwmsData()) {
				requestDwmsStorages();
			}
			profile = requirementProfile != null ? requirementProfile : RequirementProfile.MAXED;
			live = boostedLevels != null ? boostedLevels : PlayerLevels.MAXED;
			real = realLevels != null ? realLevels : PlayerLevels.MAXED;
			pushPanelCastState(live);
			potionStorage = PotionStorage.owned(client::getVarbitValue);
			mergedOwned = ownedItems();
			owned = new OwnedItems(mergedOwned, ledger.bankKnown());
			fingerprint = owned.presenceFingerprint();
			unlocks = prayerUnlocks != null ? prayerUnlocks : PrayerUnlocks.ALL;
		}
	}

	public void computeRoster(List<MonsterStats> mobs, ComputeParams p, Runnable onDone) {
		clientThread.invokeLater(() -> {
			Staging s = new Staging();
			Map<Integer, Map<CombatStyle, Map<GearSlot, Integer>>> pinsByMob = new HashMap<>();
			Map<Integer, SpellStats> spellByMob = new HashMap<>();
			Map<Integer, Integer> specByMob = new HashMap<>();
			for (MonsterStats mob : mobs) {
				int id = mob.profileId();
				pinsByMob.put(id, pinnedByStyle(mob, id, p.onSlayerTask));
				spellByMob.put(id, resolvedPinnedSpell(id));
				specByMob.put(id, mobProfiles.pinnedSpec(id));
			}
			optimizerService.bestPerStyleAcross(mobs, s.real, s.live, s.unlocks, s.profile, s.owned, s.fingerprint, p,
				excludedByStyle(-1), dreams.snapshot(), perMobExclusions(mobs),
				perMobSims(mobs), pinsByMob, spellByMob, specByMob,
				protectOnly.snapshot(),
				roster -> SwingUtilities.invokeLater(() -> {
					// EDT-only page assembly: it reads the ledger, profile
					// and config stores the client thread writes (deadlock
					// report 2026-08-12 - the callback thread had been
					// building pages under their locks).
					CommandEngine engine = commandEngine;
					if (engine != null) {
						engine.onRosterResults(roster.mobs, roster.perMob, roster.groupBook, roster.groupBookReason);
						refreshHostedView();
					}
					onDone.run();
				}));
		});
	}

	public void compute(MonsterStats monster, ComputeParams p, Runnable onDone) {
		clientThread.invokeLater(() -> {
			// DWMS saves on its own cadence (ConfigSync/shutdown); the staging
			// re-read keeps imported storages as fresh as they can be (its
			// reply lands after this compute - one-query lag).
			Staging s = new Staging();
			exportProfile(new PlayerProfile(
				s.real, s.live, s.unlocks, s.profile, s.mergedOwned, ledger.bankKnown(),
				ownedBySources()));
			optimizerService.bestPerStyle(monster, s.real, s.live, s.unlocks, s.profile, s.owned, s.fingerprint, p,
				excludedByStyle(monster.getId()), dreamsWithMobSims(monster),
				pinnedByStyle(monster, monster.getId(), p.onSlayerTask), resolvedPinnedSpell(monster.getId()),
				mobProfiles.pinnedSpec(monster.getId()),
				protectOnly.snapshot(),
				results -> SwingUtilities.invokeLater(() -> {
					CommandEngine engine = commandEngine;
					if (engine != null) {
						engine.onResults(monster, results);
						refreshHostedView();
					}
					onDone.run();
				}));
		});
	}

	/** The one-surface mount: Core's single nav slot hosts the
	 * model-driven surface. */
	private void refreshHostedView() {
		SwingUtilities.invokeLater(() -> {
			if (navButton == null || companionHost == null || internalSurface == null) {
				return; // still loading
			}
			try {
				companionHost.mount(internalSurface.asFunction().apply(
					companionLink == null ? null : companionLink.lastPage()));
			} catch (RuntimeException ex) {
				log.warn("renderer failed; keeping the previous content", ex);
			}
		});
	}

	/** VarbitID.SPELLBOOK values -> the contract's book names. */
	private static String spellbookName(int varbit) {
		switch (varbit) {
			case 1: return "ancient";
			case 2: return "lunar";
			case 3: return "arceuus";
			default: return "standard";
		}
	}

	/** The book plate reacts LIVE to a spellbook swap (classic field
	 * bug 2026-07-21: the chip stayed red after swapping). */
	@Subscribe
	public void onVarbitChanged(VarbitChanged event) {
		if (PotionStorage.tracks(event.getVarbitId())) {
			potionStorage = PotionStorage.owned(client::getVarbitValue);
			ledgerRecomputeTicks = 5;
			return;
		}
		if (event.getVarbitId() != VarbitID.SPELLBOOK) {
			return;
		}
		CommandEngine engine = commandEngine;
		if (engine != null) {
			engine.setLiveSpellbook(spellbookName(event.getValue()));
		}
	}

	/** The classic provenance memo (rebuilt only when ownership moves):
	 * an item's primary storage NAME when a fetch trip is needed, "" at
	 * hand - the source dots key off it. */
	private volatile ItemLocations cachedLocations;
	private volatile int cachedLocationsFingerprint;

	private String primaryLocationOf(int itemId) {
		if (ledger == null || dwmsLink == null) {
			return "";
		}
		int fingerprint = ownedFingerprint();
		ItemLocations found = cachedLocations;
		if (found == null || cachedLocationsFingerprint != fingerprint) {
			found = new ItemLocations(ownedBySources(), data == null ? null : data::equivalentIds);
			cachedLocations = found;
			cachedLocationsFingerprint = fingerprint;
		}
		String where = found.primary(itemId);
		return where == null ? "" : where;
	}

	/** Client-thread only. Quest scan is the expensive part - done once per login. */
	private void snapshotProfileIfNeeded() {
		if (requirementProfile != null && boostedLevels != null && realLevels != null) {
			return;
		}
		Map<Skill, Integer> real = new EnumMap<>(Skill.class);
		Map<Skill, Integer> boosted = new EnumMap<>(Skill.class);
		for (Skill skill : Skill.values()) {
			real.put(skill, client.getRealSkillLevel(skill));
			boosted.put(skill, client.getBoostedSkillLevel(skill));
		}
		// Canary: real Hitpoints can never be below 10. A sub-10 read means
		// the client stats were not populated yet (login race) - skip the
		// snapshot so the next compute retries, instead of poisoning every
		// number this session (field report: all magic at 0.06 dps).
		if (real.getOrDefault(Skill.HITPOINTS, 0) < 10) {
			return;
		}
		// Stronger canary: stats stream in Skill-ordinal order at login, so a
		// fast first search can catch attack/str/def/hp populated while
		// ranged/prayer/magic still read 1 - and the HP check above passes.
		// The client's own total level is authoritative: a partial snapshot
		// always sums BELOW it (field report: ranged/magic/prayer poisoned
		// at 1 -> blisterwood-stake ranged picks and Wind Strike autocasts).
		if (real.values().stream().mapToInt(Integer::intValue).sum() < client.getTotalLevel()) {
			return;
		}
		Set<String> quests = Arrays.stream(Quest.values()).filter(quest -> quest.getState(client) == QuestState.FINISHED)
			.map(Enum::name).collect(Collectors.toSet());
		requirementProfile = new RequirementProfile(real, quests);
		realLevels = PlayerLevels.from(real);
		boostedLevels = PlayerLevels.from(boosted);
		// Unlock-gated prayers: King's Ransom for Piety/Chivalry; scroll
		// unlock varbits for Rigour/Augury (CoX), Deadeye/Mystic Vigour
		// and Zeal (16578 = PRAYER_ZEAL_UNLOCKED).
		prayerUnlocks = new PrayerUnlocks(
			quests.contains(Quest.KINGS_RANSOM.name()),
			client.getVarbitValue(5451) == 1,
			client.getVarbitValue(5452) == 1,
			client.getVarbitValue(16097) == 1,
			client.getVarbitValue(16098) == 1,
			client.getVarbitValue(16578) == 1);
		snapshotAccount = client.getAccountHash();
	}

	// ------------------------------------------------------------------

	/**
	 * Persistence scope: world type PLUS the account hash - two accounts
	 * on standard worlds must never share a scanned bank (field report:
	 * switching characters showed the previous character's gear).
	 */
	/** Point every per-character store at the current account. ONE place, so a
	 *  store added later cannot be wired into two of the three call sites and
	 *  silently stay global on the third - which is how the exclude/sim lists
	 *  ended up shared across characters (field report 2026-08-25). */
	private void loadAllScopes() {
		String scope = worldScope();
		if (scope == null) {
			// LOGGED_IN fires before getAccountHash() is populated. Reloading
			// now would swap every store onto a hash-less bucket and drop
			// whatever the player just added (field report 2026-08-26); the
			// stores keep the last known character until the hash arrives.
			return;
		}
		for (ScopedStore store : stores) {
			store.loadScope(scope);
		}
	}

	/** Every per-character store, ONE list (loadAllScopes, the profile
	 * reload and the reset all walk it); empty until startUp builds them. */
	private ScopedStore[] stores = {};

	private String worldScope() {
		long account = client.getAccountHash();
		if (account == -1) {
			return null;
		}
		String world = client.getWorldType().contains(WorldType.SEASONAL) ? "seasonal" : "std";
		return world + "." + account;
	}

	/** True only when logged in to a non-members world - the F2P filter default. */
	private boolean onF2pWorld() {
		return client.getGameState() == GameState.LOGGED_IN
			&& !client.getWorldType().contains(WorldType.MEMBERS);
	}

	@Provides
	LoadoutLabConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(LoadoutLabConfig.class);
	}
}
