package com.loadoutlab.ui;

import com.loadoutlab.UsageLog;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterNotes;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SlayerLockedMonsters;
import com.loadoutlab.data.StatBlock;
import com.loadoutlab.data.WildernessMonsters;
import com.loadoutlab.engine.BlowpipeDarts;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.DragonfireRules;
import com.loadoutlab.engine.IncomingDpsCalculator;
import com.loadoutlab.engine.Loadout;
import com.loadoutlab.engine.MonsterMechanics;
import com.loadoutlab.engine.PvpRisk;
import com.loadoutlab.engine.QuestRewardItems;
import com.loadoutlab.engine.SpecialAttack;
import com.loadoutlab.optimizer.OptimizerService.ModeTrade;
import com.loadoutlab.optimizer.OptimizerService.StyleResult;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.ImageUtil;

/**
 * v0.1 panel: search a monster, see your best OWNED set per combat style -
 * rendered as item icons like an equipment view - with exact DPS and how it
 * compares to the best set in the game.
 *
 * <p>EDT discipline: search is debounced, optimization runs off-thread via
 * OptimizerService, item images fill in asynchronously via ItemManager;
 * this panel only renders. All content is width-constrained - the sidebar
 * must never scroll horizontally.
 */
public class LoadoutLabPanel extends PluginPanel
{
	/** (monster, f2pOnly, onDone) - the plugin wires this to the optimizer.
	 * maxTradeables: wilderness kept-slot cap (-1 = unconstrained);
	 * riskBudgetGp: the total gp the set may drop on a PvP death. */
	public interface ComputeHook
	{
		void compute(MonsterStats monster, boolean f2pOnly, boolean onSlayerTask,
			String spellbookLock, int maxTradeables, int riskBudgetGp,
			boolean antifirePotion, int upgradeBudgetGp,
			com.loadoutlab.optimizer.OptimizerService.OptimizeMode mode, Runnable onDone);
	}

	/** Toggle an item's excluded state; returns true when now excluded. */
	public interface ExclusionToggle
	{
		boolean toggle(int itemId);
	}

	/** The current excluded item ids. */
	public interface ExclusionView
	{
		Set<Integer> snapshot();
	}

	/** Toggle an item's "only bring if protected on death" flag (wilderness
	 * low-risk sets); returns true when the flag is now set. */
	public interface ProtectOnlyToggle
	{
		boolean toggle(int itemId);

		boolean isProtectOnly(int itemId);
	}

	/**
	 * Which detail lines and controls the panel shows - driven by the
	 * plugin's config. Immutable; the plugin builds one and hands it over
	 * with setDisplayOptions. Everything defaults on (the full panel).
	 */
	public static final class DisplayOptions
	{
		final boolean maxHit;
		final boolean accuracy;
		final boolean bonuses;
		final boolean assumes;
		final boolean damageTaken;
		final boolean riskLine;
		final boolean prayerBonus;
		final boolean attackStyle;
		final boolean gameBest;
		final boolean notes;
		final boolean spellControls;
		final boolean upgradeBudget;
		final boolean wildyRisk;
		final boolean showInBank;
		final boolean filterBank;
		final boolean classicLayout;

		public DisplayOptions(boolean maxHit, boolean accuracy, boolean bonuses, boolean assumes,
			boolean damageTaken, boolean riskLine, boolean prayerBonus, boolean attackStyle,
			boolean gameBest, boolean notes, boolean spellControls, boolean upgradeBudget,
			boolean wildyRisk, boolean showInBank, boolean filterBank, boolean classicLayout)
		{
			this.maxHit = maxHit;
			this.accuracy = accuracy;
			this.bonuses = bonuses;
			this.assumes = assumes;
			this.damageTaken = damageTaken;
			this.riskLine = riskLine;
			this.prayerBonus = prayerBonus;
			this.attackStyle = attackStyle;
			this.gameBest = gameBest;
			this.notes = notes;
			this.spellControls = spellControls;
			this.upgradeBudget = upgradeBudget;
			this.wildyRisk = wildyRisk;
			this.showInBank = showInBank;
			this.filterBank = filterBank;
			this.classicLayout = classicLayout;
		}

		static DisplayOptions all()
		{
			// classicLayout defaults OFF - the compact grid is the default look.
			return new DisplayOptions(true, true, true, true, true, true, true,
				true, true, true, true, true, true, true, true, false);
		}
	}

	/** Toggle an item's dream ("green") state; true when now dreamed. */
	public interface DreamToggle
	{
		boolean toggle(int itemId);
	}

	/** The current dream item ids. */
	public interface DreamView
	{
		Set<Integer> snapshot();
	}

	/** Toggle an item's "stored elsewhere" (manual owned) state. */
	public interface StoredToggle
	{
		boolean toggle(int itemId);
	}

	/** The current stored-elsewhere item ids. */
	public interface StoredView
	{
		Set<Integer> snapshot();
	}

	/** Where an owned item lives, for tooltips and the source legend. */
	public interface LocationHint
	{
		/** One tooltip clause ("" = at hand or unknown). */
		String hint(int itemId);

		/** Legend label of the primary origin ("" = unknown). */
		String primary(int itemId);
	}

	/** Per-monster user profile: pins ("always bring this HERE"), a free
	 * note, and extra items unioned into the bank Show/Filter sets. Pins
	 * and filter items are scoped: "ALL" or a CombatStyle name - a super
	 * combat for the melee card, a ranged potion for the ranged card. */
	public interface MobProfile
	{
		/** Effective pins for one style card (ALL overlaid by the style). */
		Map<com.loadoutlab.data.GearSlot, Integer> pins(int monsterId, CombatStyle style);

		/** Raw pins by scope, for the manage menu. */
		Map<String, Map<com.loadoutlab.data.GearSlot, Integer>> allPins(int monsterId);

		void pin(int monsterId, String scope, com.loadoutlab.data.GearSlot slot, int itemId);

		void unpin(int monsterId, String scope, com.loadoutlab.data.GearSlot slot);

		String note(int monsterId);

		void setNote(int monsterId, String note);

		/** Effective filter-item ids for one style card (ALL + the style). */
		Set<Integer> filterItems(int monsterId, CombatStyle style);

		/** Raw filter items by scope: scope -> id -> add-time name. */
		Map<String, Map<Integer, String>> allFilterItems(int monsterId);

		void addFilterItem(int monsterId, String scope, int itemId, String name);

		void removeFilterItem(int monsterId, String scope, int itemId);

		/** Pinned autocast spell name for the magic card ("" = auto). */
		String pinnedSpell(int monsterId);

		void setPinnedSpell(int monsterId, String spellName);

		/** Raw per-mob exclusions by scope, for the manage menu. */
		default Map<String, Set<Integer>> allMobExclusions(int monsterId)
		{
			return Map.of();
		}

		default void excludeForMob(int monsterId, String scope, int itemId)
		{
		}

		default void removeMobExclusion(int monsterId, String scope, int itemId)
		{
		}
	}

	/** Open the native chatbox item search; the pick (id, name) returns
	 * on the EDT. The strange dialog matcher is gone (field request). */
	public interface ItemSearch
	{
		void search(String prompt, java.util.function.BiConsumer<Integer, String> onPicked);
	}

	/** The every-style pin/filter scope key. */
	public static final String ALL_SETS = "ALL";

	/** Does the player actually own this item (black set)? */
	public interface OwnedCheck
	{
		boolean owns(int itemId);
	}

	/** "Show in bank": set the highlighted item ids (null clears). */
	public interface BankHighlighter
	{
		void highlight(Set<Integer> itemIds);
	}

	/** "Filter bank": show only these item ids in the bank (null clears). */
	public interface BankFilter
	{
		void filter(Set<Integer> itemIds);
	}

	private static final int SEARCH_DEBOUNCE_MS = 150;
	private static final int SEARCH_LIMIT = 25;
	private static final int ICON_SIZE = 32;
	/** Discord invite for the plugin's community; opened from the header. */
	private static final String DISCORD_URL = "https://discord.gg/6GuS6J8em3";
	/** Grid display order: weapon beside shield, body beside legs. */
	static final GearSlot[] GRID_ORDER = {
		GearSlot.HEAD, GearSlot.CAPE, GearSlot.NECK, GearSlot.AMMO,
		GearSlot.WEAPON, GearSlot.SHIELD, GearSlot.BODY, GearSlot.LEGS,
		GearSlot.HANDS, GearSlot.FEET, GearSlot.RING,
	};

	/** Text palette: muted grey for secondary info, green for good news,
	 * blue for "do this" instructions, green-border green for unowned gear. */
	private static final Color MUTED = new Color(160, 160, 160);
	private static final Color GOOD = new Color(140, 200, 140);
	private static final Color INFO = new Color(150, 170, 230);
	private static final Color UNOWNED = new Color(110, 190, 110);
	private static final Color BORDER_UNOWNED = new Color(100, 145, 100);

	/** Source-dot palette (bottom-right cell corner + legend), display
	 * order. Separate vocabulary from the BORDERS (gold/green/blue).
	 * At-hand sources (equipped/inventory/bank) are deliberately absent:
	 * no palette entry = no dot and no legend row - only gear needing a
	 * fetch trip gets marked, so the grid stays quiet for bank-only sets. */
	private static final Map<String, Color> SOURCE_COLORS = new java.util.LinkedHashMap<>();
	static
	{
		SOURCE_COLORS.put("looting bag", new Color(180, 130, 80));
		SOURCE_COLORS.put("POH costume room", new Color(190, 130, 230));
		SOURCE_COLORS.put("STASH", new Color(230, 120, 120));
		SOURCE_COLORS.put("cargo hold", new Color(100, 200, 190));
		SOURCE_COLORS.put("stored elsewhere", new Color(180, 210, 110));
		SOURCE_COLORS.put("DWMS", new Color(160, 160, 210));
	}

	/** Sources that appear in the CURRENT results - the legend shows
	 * exactly these, never the full palette. */
	private final Set<String> usedSources = new java.util.LinkedHashSet<>();

	/** Cell border language: gold = your item IS the game best, blue = the
	 * spec cell (matches the in-game spec orb), grey = owned/empty. */
	private static final Color BORDER_BIS = new Color(168, 148, 88);
	private static final Color BORDER_SPEC = new Color(120, 190, 240);
	private static final Color BORDER_PLAIN = new Color(70, 70, 70);
	private static final Color BORDER_EMPTY = new Color(50, 50, 50);

	private final LoadoutData data;
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final UsageLog usageLog = UsageLog.defaultLog();
	private final ComputeHook computeHook;
	private final ExclusionToggle exclusionToggle;
	private final ExclusionView exclusionView;
	private final ProtectOnlyToggle protectOnlyToggle;
	private final DreamToggle dreamToggle;
	private final DreamView dreamView;
	private final StoredToggle storedToggle;
	private final StoredView storedView;
	private final LocationHint locationHint;
	private final MobProfile mobProfile;
	private final ItemSearch itemSearch;
	private final OwnedCheck ownedCheck;
	/** Per-style card collapse: user override on top of the auto default
	 * (collapsed when a standard deviation under the best set's dps). */
	private final Map<CombatStyle, Boolean> cardCollapsed = new EnumMap<>(CombatStyle.class);
	private final Map<CombatStyle, Boolean> autoCollapsed = new EnumMap<>(CombatStyle.class);
	private final BankHighlighter bankHighlighter;
	private final BankFilter bankFilter;
	/** Which style's set is filtering the bank (null = none). */
	private CombatStyle bankFiltered;
	/** Which style's set is currently glowing in the bank (null = none). */
	private CombatStyle bankShown;
	/** D-4: which frontier point to recommend per style. */
	private final JComboBox<String> optimizeMode = new JComboBox<>(
		new String[]{"Optimize: Max DPS", "Optimize: Balanced", "Optimize: Tanky"});
	/** Free-form upgrade budget: "750k", "1m", "1.5b", plain gp, or "-"
	 * for max. Empty or unparseable = off. */
	private final JTextField upgradeBudget = new JTextField();
	private int lastBudgetGp;
	private final JLabel exclusionsLabel = new JLabel();
	private final JLabel storedLabel = new JLabel();
	private final JLabel pinnedLabel = new JLabel();
	/** The user's own note for the selected monster: a collapsible
	 * post-it, edited inline (saves on focus loss - no edit button). */
	private final JPanel notePanel = new JPanel();
	private final JLabel noteHeader = new JLabel();
	private final javax.swing.JTextArea noteArea = new javax.swing.JTextArea();
	private boolean noteCollapsed = true;
	/** Config-driven display gates (all on until the plugin sets them). */
	private DisplayOptions displayOptions = DisplayOptions.all();
	/** The upgrade-budget control row - gated by displayOptions.upgradeBudget. */
	private JPanel budgetRow;
	private static final Color POSTIT_BG = new Color(222, 212, 150);
	private static final Color POSTIT_FG = new Color(55, 50, 25);

	private final JTextField searchField = new JTextField();
	private final DefaultListModel<MonsterStats> monsterModel = new DefaultListModel<>();
	private final JList<MonsterStats> monsterList = new JList<>(monsterModel);
	private final JScrollPane monsterScroll;
	private final JPanel selectedRow = new JPanel(new BorderLayout(4, 0));
	private final JLabel selectedLabel = new JLabel();
	private final JLabel monsterNote = new JLabel();
	private final JCheckBox f2pOnly = new JCheckBox("Non-members gear only");
	private final JCheckBox slayerTask = new JCheckBox("On slayer task");
	private final JComboBox<String> spellbook =
		new JComboBox<>(new String[]{"Any spellbook", "Standard", "Ancient", "Arceuus"});
	// Sits in BorderLayout.CENTER (see the constructor), so it's stretched to
	// the fixed sidebar width and over-wide children can't inflate it - the
	// horizontal-clip guard the old Scrollable width-tracking used to provide
	// back when this lived in its own JScrollPane.
	private final JPanel resultsPanel = new JPanel();
	private final JLabel statusLabel = new JLabel(" ");
	/** Picks which loading animation each compute gets. */
	private static final java.util.Random MASCOT_MOOD = new java.util.Random();
	/** Last day of the striker's headline run (the 2026 final, inclusive). */
	private static final java.time.LocalDate WORLD_CUP_FINAL = java.time.LocalDate.of(2026, 7, 19);
	private final Timer searchDebounce;

	/** Guards against programmatic search-field changes re-opening the list. */
	private boolean suppressSearchEvents;

	private final JCheckBox lowRisk = new JCheckBox("Low-risk (wilderness)");
	private final JCheckBox protectItem = new JCheckBox("Protect Item (keep 4)");
	/** Wilderness risk-cap dropdown values in gp; 75k is the default. */
	private static final int[] RISK_STEPS = {0, 25_000, 75_000, 200_000, 1_000_000};
	private final JComboBox<String> riskBudget = new JComboBox<>(
		new String[]{"Risk cap: 0", "Risk cap: 25k", "Risk cap: 75k", "Risk cap: 200k", "Risk cap: 1M"});
	/** Dragonfire: gear protection by default; right-clicking the shield
	 * cell flips to an assumed super antifire (and back). */
	private boolean superAntifireAssumed;

	private MonsterStats selectedMonster;
	/** The style card currently being rendered (EDT-only render state) -
	 * grid cells read it for per-style pin menus and tooltips. */
	private CombatStyle renderingStyle;
	/** Per-style expanded game-best (BiS) sections - hidden by default,
	 * each card's header toggles only its own section. */
	private final Set<CombatStyle> gameBestExpanded = EnumSet.noneOf(CombatStyle.class);
	private Map<CombatStyle, StyleResult> lastResults;
	/** The monster lastResults belongs to - so a display-only re-render
	 * never paints a previous monster's sets under the current name while a
	 * new compute is still in flight. */
	private int lastResultsMonsterId = -1;

	public LoadoutLabPanel(LoadoutData data, ItemManager itemManager,
		SpriteManager spriteManager, ComputeHook computeHook,
		ExclusionToggle exclusionToggle, ExclusionView exclusionView,
		ProtectOnlyToggle protectOnlyToggle,
		DreamToggle dreamToggle, DreamView dreamView,
		StoredToggle storedToggle, StoredView storedView,
		LocationHint locationHint, MobProfile mobProfile, ItemSearch itemSearch,
		OwnedCheck ownedCheck,
		BankHighlighter bankHighlighter, BankFilter bankFilter)
	{
		this.bankHighlighter = bankHighlighter;
		this.bankFilter = bankFilter;
		this.protectOnlyToggle = protectOnlyToggle;
		this.data = data;
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
		this.computeHook = computeHook;
		this.exclusionToggle = exclusionToggle;
		this.exclusionView = exclusionView;
		this.dreamToggle = dreamToggle;
		this.dreamView = dreamView;
		this.storedToggle = storedToggle;
		this.storedView = storedView;
		this.locationHint = locationHint;
		this.mobProfile = mobProfile;
		this.itemSearch = itemSearch;
		this.ownedCheck = ownedCheck;

		setLayout(new BorderLayout(0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setOpaque(false);

		JLabel title = new JLabel("Loadout Lab");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));

		// Header row: title left, an "Options" menu right (Discord, and
		// future plugin-wide actions) - mirrors the Goal Planner header.
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.setAlignmentX(LEFT_ALIGNMENT);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		header.add(title, BorderLayout.WEST);
		JButton optionsButton = new JButton(new DotsIcon(13));
		optionsButton.setToolTipText("Options");
		optionsButton.setMargin(new Insets(2, 6, 2, 6));
		optionsButton.addActionListener(e ->
		{
			JPopupMenu menu = new JPopupMenu();
			// Entry point for the first stored-elsewhere item (before any
			// exists there is no label or right-click row to reach it from).
			JMenuItem addStored = new JMenuItem("Add a stored-elsewhere item...");
			addStored.addActionListener(ev -> showAddStoredDialog());
			menu.add(addStored);
			// Mob-specific actions live on the style cards and the
			// "This mob" line - the header menu stays plugin-wide.
			JMenuItem joinDiscord = new JMenuItem("Join our Discord");
			joinDiscord.addActionListener(ev -> LinkBrowser.browse(DISCORD_URL));
			menu.add(joinDiscord);
			menu.show(optionsButton, 0, optionsButton.getHeight());
		});
		header.add(optionsButton, BorderLayout.EAST);
		top.add(header);
		top.add(Box.createVerticalStrut(4));

		searchField.setAlignmentX(LEFT_ALIGNMENT);
		searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		top.add(searchField);
		top.add(Box.createVerticalStrut(4));

		// Selected-monster row: replaces the dropdown once a pick is made.
		selectedRow.setOpaque(false);
		selectedRow.setAlignmentX(LEFT_ALIGNMENT);
		// Height follows content: a long monster name wraps to a second line
		// rather than clipping (was capped at one 26px row).
		selectedRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
		selectedLabel.setForeground(GOOD);
		selectedLabel.setFont(selectedLabel.getFont().deriveFont(Font.BOLD, 14f));
		selectedLabel.setVerticalAlignment(SwingConstants.TOP);
		selectedRow.add(selectedLabel, BorderLayout.CENTER);
		JButton reloadButton = new JButton(new ReloadIcon(12));
		reloadButton.setMargin(new Insets(0, 6, 0, 6));
		reloadButton.setToolTipText("Re-run the search for this monster");
		reloadButton.addActionListener(e -> recompute());
		JButton clearSelection = new JButton("x");
		clearSelection.setMargin(new Insets(0, 6, 0, 6));
		clearSelection.setToolTipText("Choose a different monster");
		clearSelection.addActionListener(e -> clearSelection());
		JPanel selectedButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		selectedButtons.setOpaque(false);
		selectedButtons.add(reloadButton);
		selectedButtons.add(clearSelection);
		selectedRow.add(selectedButtons, BorderLayout.EAST);
		selectedRow.setVisible(false);
		top.add(selectedRow);

		// Curated mechanics note (finishing items, immunities) for the
		// selected monster - so a correct suggestion doesn't look wrong.
		monsterNote.setForeground(new Color(200, 170, 110));
		monsterNote.setFont(monsterNote.getFont().deriveFont(13f));
		monsterNote.setAlignmentX(LEFT_ALIGNMENT);
		monsterNote.setVisible(false);
		top.add(monsterNote);

		monsterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		monsterList.setVisibleRowCount(6);
		monsterList.setCellRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value,
				int index, boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				setText(((MonsterStats) value).label());
				return this;
			}
		});
		monsterScroll = new JScrollPane(monsterList);
		monsterScroll.setPreferredSize(new Dimension(0, 130));
		monsterScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
		monsterScroll.setAlignmentX(LEFT_ALIGNMENT);
		monsterScroll.setVisible(false);
		top.add(monsterScroll);

		initToggle(f2pOnly, "Only consider free-to-play gear");
		f2pOnly.setVisible(false); // only shown on non-members worlds
		top.add(f2pOnly);

		initToggle(slayerTask, "On task: slayer helmet bonuses apply");
		top.add(slayerTask);

		// Wilderness only: cap the set to the items death mechanics keep.
		initToggle(lowRisk, "Keep your 3 most valuable items (4 with Protect Item);"
			+ " everything else must total under the risk cap");
		lowRisk.setVisible(false);
		top.add(lowRisk);

		initToggle(protectItem, "Protect Item keeps a 4th item (not while skulled)");
		protectItem.setVisible(false);
		top.add(protectItem);

		// How much gp the set may drop on a wilderness death; 0 = nothing
		// droppable and no fees at all.
		riskBudget.setAlignmentX(LEFT_ALIGNMENT);
		riskBudget.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		riskBudget.setToolTipText("Total gp the set may drop on a wilderness death");
		riskBudget.setSelectedIndex(2);
		riskBudget.addActionListener(e -> recompute());
		riskBudget.setVisible(false);
		top.add(riskBudget);


		// Spellbook lock lives ON the magic card (vertical space) - the
		// combo keeps its state here and is re-parented per render.
		spellbook.setAlignmentX(LEFT_ALIGNMENT);
		spellbook.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		spellbook.setToolTipText("Limit spells to one spellbook (powered staves always considered)");
		spellbook.addActionListener(e -> recompute());

		// Buyable upgrades within a total gp budget join the consideration
		// pool (dream items are the manual version, via right-click).
		budgetRow = new JPanel(new BorderLayout(4, 0));
		budgetRow.setOpaque(false);
		budgetRow.setAlignmentX(LEFT_ALIGNMENT);
		budgetRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		JLabel budgetLabel = new JLabel("Upgrade budget:");
		budgetLabel.setForeground(new Color(200, 200, 200));
		budgetLabel.setFont(budgetLabel.getFont().deriveFont(13f));
		budgetRow.add(budgetLabel, BorderLayout.WEST);
		upgradeBudget.setToolTipText("Buyable-gear budget: 750k, 1m, 1.5b; - sets unlimited; empty = 0 (owned gear only, default)");
		upgradeBudget.addActionListener(e -> budgetEdited());
		upgradeBudget.addFocusListener(new java.awt.event.FocusAdapter()
		{
			@Override
			public void focusLost(java.awt.event.FocusEvent e)
			{
				budgetEdited();
			}
		});
		budgetRow.add(upgradeBudget, BorderLayout.CENTER);
		top.add(budgetRow);

		// D-4: pick the offense/defense frontier point (sweep is slower).
		optimizeMode.setAlignmentX(LEFT_ALIGNMENT);
		optimizeMode.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		optimizeMode.setToolTipText("Balanced/Tanky trade dps for less damage taken");
		optimizeMode.addActionListener(e -> recompute());
		top.add(optimizeMode);

		// Excluded items ("protected" from suggestions) - click to manage.
		exclusionsLabel.setForeground(new Color(200, 140, 140));
		exclusionsLabel.setFont(exclusionsLabel.getFont().deriveFont(13f));
		exclusionsLabel.setAlignmentX(LEFT_ALIGNMENT);
		exclusionsLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		exclusionsLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				showExclusionsMenu(e);
			}
		});
		top.add(exclusionsLabel);
		refreshExclusionsLabel();

		// Stored-elsewhere items (manual owned: STASH, POH, UIM storages).
		storedLabel.setForeground(GOOD);
		storedLabel.setFont(storedLabel.getFont().deriveFont(13f));
		storedLabel.setAlignmentX(LEFT_ALIGNMENT);
		storedLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		storedLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				showStoredMenu(e);
			}
		});
		top.add(storedLabel);
		refreshStoredLabel();

		// The mob's post-it note: collapsible, edited inline (saves when
		// focus leaves the text area).
		notePanel.setLayout(new BoxLayout(notePanel, BoxLayout.Y_AXIS));
		notePanel.setBackground(POSTIT_BG);
		notePanel.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
		notePanel.setAlignmentX(LEFT_ALIGNMENT);
		noteHeader.setForeground(POSTIT_FG);
		noteHeader.setFont(noteHeader.getFont().deriveFont(Font.BOLD, 12f));
		noteHeader.setAlignmentX(LEFT_ALIGNMENT);
		noteHeader.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		noteHeader.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				saveNoteIfChanged();
				noteCollapsed = !noteCollapsed;
				refreshNotePanel();
			}
		});
		noteArea.setLineWrap(true);
		noteArea.setWrapStyleWord(true);
		noteArea.setRows(3);
		noteArea.setBackground(POSTIT_BG);
		noteArea.setForeground(POSTIT_FG);
		noteArea.setCaretColor(POSTIT_FG);
		noteArea.setFont(noteArea.getFont().deriveFont(12f));
		noteArea.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
		noteArea.setAlignmentX(LEFT_ALIGNMENT);
		noteArea.addFocusListener(new java.awt.event.FocusAdapter()
		{
			@Override
			public void focusLost(java.awt.event.FocusEvent e)
			{
				saveNoteIfChanged();
				refreshNotePanel();
			}
		});
		notePanel.add(noteHeader);
		notePanel.add(noteArea);
		notePanel.setVisible(false);
		top.add(notePanel);

		// Pinned items ("always bring") - click to manage.
		pinnedLabel.setForeground(INFO);
		pinnedLabel.setFont(pinnedLabel.getFont().deriveFont(13f));
		pinnedLabel.setAlignmentX(LEFT_ALIGNMENT);
		pinnedLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		pinnedLabel.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				showPinnedMenu(e);
			}
		});
		top.add(pinnedLabel);
		refreshPinnedLabel();

		add(top, BorderLayout.NORTH);

		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
		resultsPanel.setOpaque(false);
		// No inner JScrollPane here: PluginPanel already wraps us in one (and
		// anchors this panel at BorderLayout.NORTH of its viewport, so we always
		// render at our preferred height). A nested scroll pane is a second Swing
		// validate root, which is why an expanded card's new height didn't reach
		// RuneLite's outer scroll in one pass - the panel wouldn't grow into the
		// empty space below, and a second collapse/expand was needed to force the
		// re-measure. Dropping straight into CENTER leaves one validate root, so
		// revalidate() propagates cleanly and the outer bar scrolls only once the
		// content actually exceeds the sidebar. CENTER stretches resultsPanel to
		// the fixed panel width, so over-wide children still can't inflate it
		// (the invariant the old ScrollableColumn width-tracking enforced).
		add(resultsPanel, BorderLayout.CENTER);

		statusLabel.setForeground(MUTED);
		add(statusLabel, BorderLayout.SOUTH);

		searchDebounce = new Timer(SEARCH_DEBOUNCE_MS, e -> runSearch());
		searchDebounce.setRepeats(false);
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e) { onSearchEdited(); }
			public void removeUpdate(DocumentEvent e) { onSearchEdited(); }
			public void changedUpdate(DocumentEvent e) { onSearchEdited(); }
		});
		monsterList.addListSelectionListener(e ->
		{
			if (!e.getValueIsAdjusting() && monsterList.getSelectedValue() != null)
			{
				select(monsterList.getSelectedValue());
			}
		});

		statusLabel.setText("Search a monster to begin.");
	}

	/** Shared checkbox chrome; every toggle recomputes on change. */
	private void initToggle(JCheckBox box, String tooltip)
	{
		box.setOpaque(false);
		box.setForeground(new Color(200, 200, 200));
		box.setAlignmentX(LEFT_ALIGNMENT);
		box.setToolTipText(tooltip);
		box.addActionListener(e -> recompute());
	}

	/** Small 11pt info line - the shape every card row shares. */
	private static JLabel line(String text, Color fg)
	{
		JLabel line = new JLabel(text);
		line.setForeground(fg);
		line.setFont(line.getFont().deriveFont(13f));
		line.setAlignmentX(LEFT_ALIGNMENT);
		return line;
	}

	/** Minimal HTML escape for text going into an html-rendered JLabel. */
	private static String escapeHtml(String text)
	{
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** "Max hit N - X% accuracy", either half omitted per the display gates;
	 * "" when both are off. */
	private String hitAccuracyText(int maxHit, double accuracy)
	{
		String hit = displayOptions.maxHit ? "Max hit " + maxHit : "";
		String acc = displayOptions.accuracy
			? String.format("%.0f%% accuracy", accuracy * 100) : "";
		if (hit.isEmpty())
		{
			return acc;
		}
		return acc.isEmpty() ? hit : hit + " - " + acc;
	}

	/**
	 * Set by the plugin on login. On a non-members world the filter appears
	 * and defaults on; on a members world it is hidden and forced off - the
	 * toggle only earns panel space where it can matter.
	 */
	public void setF2pWorld(boolean f2pWorld)
	{
		f2pOnly.setVisible(f2pWorld);
		if (f2pOnly.isSelected() != f2pWorld)
		{
			f2pOnly.setSelected(f2pWorld);
			recompute();
		}
		revalidate();
		repaint();
	}

	/** Config hook: which detail lines and controls to show. Re-renders the
	 * cards from the cached results (no recompute - display only) and updates
	 * the top-control visibility. Saved notes are never touched. */
	public void setDisplayOptions(DisplayOptions options)
	{
		this.displayOptions = options;
		// Flush any in-progress note edit before the post-it can vanish.
		if (!options.notes)
		{
			saveNoteIfChanged();
		}
		if (budgetRow != null)
		{
			budgetRow.setVisible(options.upgradeBudget);
		}
		boolean wild = selectedMonster != null && WildernessMonsters.isWilderness(selectedMonster);
		lowRisk.setVisible(wild && options.wildyRisk);
		protectItem.setVisible(wild && options.wildyRisk);
		riskBudget.setVisible(wild && options.wildyRisk);
		refreshNotePanel();
		// Rebuild the cards so per-line gates apply, reusing cached results -
		// but only when those results are for the CURRENT monster (a compute
		// may be in flight for a just-selected one; its render will follow).
		if (selectedMonster != null && lastResults != null
			&& lastResultsMonsterId == currentMonsterId())
		{
			showResults(selectedMonster, lastResults);
		}
		revalidate();
		repaint();
	}

	public boolean isF2pOnly()
	{
		return f2pOnly.isSelected();
	}

	private void onSearchEdited()
	{
		if (!suppressSearchEvents)
		{
			searchDebounce.restart();
		}
	}

	private void runSearch()
	{
		String query = searchField.getText().trim();
		monsterModel.clear();
		if (query.length() < 2)
		{
			monsterScroll.setVisible(false);
			revalidate();
			return;
		}
		for (MonsterStats m : data.searchMonsters(query, SEARCH_LIMIT))
		{
			monsterModel.addElement(m);
		}
		monsterScroll.setVisible(!monsterModel.isEmpty());
		statusLabel.setText(monsterModel.isEmpty() ? "No monsters match." : " ");
		revalidate();
		repaint();
	}

	/**
	 * External link-in (cross-plugin PluginMessage): select a monster by
	 * npc id or display name. Name matching reuses the search's
	 * punctuation-insensitive normalization. EDT only. Returns success.
	 */
	public boolean selectExternal(String monsterName, Integer npcId)
	{
		if (npcId != null)
		{
			for (MonsterStats m : data.getMonsters())
			{
				if (m.getId() == npcId)
				{
					select(m);
					return true;
				}
			}
		}
		if (monsterName != null && !monsterName.isEmpty())
		{
			List<MonsterStats> hits = data.searchMonsters(monsterName, 1);
			// Sender labels often carry qualifiers the corpus doesn't -
			// "Doom of Mokhaiotl (L3)", "Duke (Awake)" - retry without them.
			int paren = monsterName.indexOf(" (");
			if (hits.isEmpty() && paren > 0)
			{
				hits = data.searchMonsters(monsterName.substring(0, paren), 1);
			}
			if (!hits.isEmpty())
			{
				select(hits.get(0));
				return true;
			}
			// No match: surface the query in the search box so the caller's
			// click visibly did something instead of nothing.
			searchField.setText(monsterName);
		}
		return false;
	}

	/** A pick: collapse the dropdown, show the selection, clear the query. */
	private void select(MonsterStats monster)
	{
		suppressSearchEvents = true;
		try
		{
			searchField.setText("");
		}
		finally
		{
			suppressSearchEvents = false;
		}
		monsterModel.clear();
		monsterScroll.setVisible(false);
		selectedMonster = monster;
		boolean wilderness = WildernessMonsters.isWilderness(monster)
			&& displayOptions.wildyRisk;
		lowRisk.setVisible(wilderness);
		protectItem.setVisible(wilderness);
		riskBudget.setVisible(wilderness);
		superAntifireAssumed = false; // each monster starts on gear protection
		bankShown = null;
		bankHighlighter.highlight(null);
		bankFiltered = null;
		bankFilter.filter(null);
		// The slayer toggle has three states by monster: task-only bosses
		// (Hydra, Araxxor, Sire...) force it ON - you cannot fight them
		// off-task; unassignable monsters (raid bosses) force it OFF; and
		// everything else leaves it to the player.
		if (SlayerLockedMonsters.isTaskOnly(monster))
		{
			slayerTask.setSelected(true);
			slayerTask.setEnabled(false);
			slayerTask.setToolTipText("Task-only boss - always on");
		}
		else if (!monster.isSlayerMonster())
		{
			slayerTask.setSelected(false);
			slayerTask.setEnabled(false);
			slayerTask.setToolTipText("Not assignable as a slayer task");
		}
		else
		{
			slayerTask.setEnabled(true);
			slayerTask.setToolTipText("On task: slayer helmet bonuses apply");
		}
		usageLog.record(monster.label());
		// html so a long name/level wraps to a second line instead of
		// clipping to "..." in the fixed-width BorderLayout centre (the body
		// width leaves room for the reload/close buttons beside it).
		selectedLabel.setText("<html><body style='width:140px'>vs "
			+ escapeHtml(monster.label()) + "</body></html>");
		selectedLabel.setToolTipText(monster.label());
		selectedRow.setVisible(true);
		String note = MonsterNotes.noteFor(monster);
		monsterNote.setText(note == null ? "" : "<html>" + note + "</html>");
		monsterNote.setVisible(note != null);
		// A new mob: fresh collapse defaults, its own note state.
		cardCollapsed.clear();
		noteCollapsed = mobProfile.note(monster.getId()).isEmpty();
		refreshNotePanel();
		refreshPinnedLabel();
		revalidate();
		repaint();
		recompute();
	}

	/** The wilderness tradeable cap, or -1 when the mode is off/hidden. */
	private int riskCap()
	{
		if (!lowRisk.isVisible() || !lowRisk.isSelected())
		{
			return -1;
		}
		return protectItem.isSelected() ? 4 : 3;
	}

	/** The selected wilderness risk budget in gp. */
	private int selectedRiskBudget()
	{
		return RISK_STEPS[riskBudget.getSelectedIndex()];
	}

	/** Recompute only when the parsed budget actually changed. */
	private void budgetEdited()
	{
		int parsed = parsedBudgetGp();
		if (parsed != lastBudgetGp)
		{
			lastBudgetGp = parsed;
			recompute();
		}
	}

	/**
	 * "750k" / "1m" / "2.5b" / "1000000" -> gp; "-" -> max (clamped to
	 * 2b; the optimizer saturates cost sums); empty/junk -> 0 (off).
	 */
	private int parsedBudgetGp()
	{
		// Hidden control -> no budget (owned gear only), so a hidden field
		// cannot keep buying upgrades the user can no longer see or clear.
		if (!displayOptions.upgradeBudget)
		{
			return 0;
		}
		String raw = upgradeBudget.getText() == null ? "" : upgradeBudget.getText().trim().toLowerCase();
		if (raw.isEmpty())
		{
			return 0;
		}
		if (raw.equals("-") || raw.equals("max"))
		{
			return 2_000_000_000;
		}
		double multiplier = 1;
		if (raw.endsWith("k") || raw.endsWith("m") || raw.endsWith("b"))
		{
			multiplier = raw.endsWith("k") ? 1_000 : raw.endsWith("m") ? 1_000_000 : 1_000_000_000;
			raw = raw.substring(0, raw.length() - 1);
		}
		try
		{
			double value = Double.parseDouble(raw) * multiplier;
			return (int) Math.max(0, Math.min(value, 2_000_000_000));
		}
		catch (NumberFormatException ex)
		{
			return 0;
		}
	}

	private String spellbookLock()
	{
		// Hidden control -> no lock (its combo lives only on the magic card's
		// spell row, which displayOptions.spellControls can remove). Matches
		// riskCap() neutralizing when the wildy controls are hidden.
		if (!displayOptions.spellControls)
		{
			return "";
		}
		int index = spellbook.getSelectedIndex();
		return index <= 0 ? "" : ((String) spellbook.getSelectedItem()).toLowerCase();
	}

	private void refreshExclusionsLabel()
	{
		int count = exclusionView.snapshot().size();
		exclusionsLabel.setText(count == 0 ? "" : "Excluded items: " + count + " (click to manage)");
		exclusionsLabel.setVisible(count > 0);
	}

	private void showExclusionsMenu(MouseEvent e)
	{
		JPopupMenu menu = new JPopupMenu();
		for (Integer id : exclusionView.snapshot())
		{
			GearItem item = data.getGear(id);
			String label = item == null ? ("item " + id) : item.label();
			JMenuItem entry = new JMenuItem("Allow again: " + label);
			entry.addActionListener(a ->
			{
				exclusionToggle.toggle(id);
				refreshExclusionsLabel();
				recompute();
			});
			menu.add(entry);
		}
		menu.show(exclusionsLabel, e.getX(), e.getY());
	}

	private void refreshStoredLabel()
	{
		int count = storedView.snapshot().size();
		storedLabel.setText(count == 0 ? "" : "Stored elsewhere: " + count + " (click to manage)");
		storedLabel.setVisible(count > 0);
	}

	/** Legend for the source dots - EXACTLY the sources in the current
	 * results, in palette order; null when nothing has a known source. */
	private javax.swing.JComponent buildSourceLegend()
	{
		if (usedSources.isEmpty())
		{
			return null;
		}
		JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		legend.setOpaque(false);
		legend.setAlignmentX(LEFT_ALIGNMENT);
		JLabel title = new JLabel("Stored:");
		title.setForeground(MUTED);
		title.setFont(title.getFont().deriveFont(11f));
		legend.add(title);
		for (Map.Entry<String, Color> entry : SOURCE_COLORS.entrySet())
		{
			if (!usedSources.contains(entry.getKey()))
			{
				continue;
			}
			JLabel item = new JLabel(entry.getKey(), new SourceDotIcon(entry.getValue()),
				SwingConstants.LEADING);
			item.setForeground(MUTED);
			item.setFont(item.getFont().deriveFont(11f));
			item.setIconTextGap(4);
			legend.add(item);
		}
		return legend;
	}

	/** The small filled circle used by legend entries. */
	private static final class SourceDotIcon implements javax.swing.Icon
	{
		private final Color color;

		SourceDotIcon(Color color)
		{
			this.color = color;
		}

		@Override
		public void paintIcon(java.awt.Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(color);
			g2.fillOval(x, y + 1, 7, 7);
			g2.dispose();
		}

		@Override
		public int getIconWidth()
		{
			return 8;
		}

		@Override
		public int getIconHeight()
		{
			return 9;
		}
	}

	private int currentMonsterId()
	{
		return selectedMonster == null ? -1 : selectedMonster.getId();
	}

	/** Pin-scope label: "melee set" / "all sets" (menus append " only"). */
	private static String scopeLabel(String scope)
	{
		return ALL_SETS.equals(scope) ? "all sets"
			: scope.toLowerCase(java.util.Locale.ROOT) + " set";
	}

	/** Filter-scope label: the MOB'S NAME for all-sets ("bank filter -
	 * Venator"), the set name otherwise (field request). */
	private String filterScopeLabel(String scope)
	{
		if (ALL_SETS.equals(scope))
		{
			return selectedMonster == null ? "this mob" : selectedMonster.getName();
		}
		return scope.toLowerCase(java.util.Locale.ROOT) + " set";
	}

	private void refreshPinnedLabel()
	{
		if (selectedMonster == null)
		{
			pinnedLabel.setVisible(false);
			return;
		}
		int monsterId = currentMonsterId();
		int pins = 0;
		for (Map<com.loadoutlab.data.GearSlot, Integer> scoped
			: mobProfile.allPins(monsterId).values())
		{
			pins += scoped.size();
		}
		int filters = 0;
		for (Map<Integer, String> scoped : mobProfile.allFilterItems(monsterId).values())
		{
			filters += scoped.size();
		}
		int excluded = 0;
		for (Set<Integer> scoped : mobProfile.allMobExclusions(monsterId).values())
		{
			excluded += scoped.size();
		}
		if (pins == 0 && filters == 0 && excluded == 0)
		{
			pinnedLabel.setVisible(false);
			return;
		}
		StringBuilder text = new StringBuilder("This mob:");
		if (pins > 0)
		{
			text.append(" ").append(pins).append(pins == 1 ? " pin" : " pins");
		}
		if (filters > 0)
		{
			text.append(pins > 0 ? "," : "").append(" ")
				.append(filters).append(" filter item").append(filters == 1 ? "" : "s");
		}
		if (excluded > 0)
		{
			text.append(pins > 0 || filters > 0 ? "," : "").append(" ")
				.append(excluded).append(" excluded");
		}
		pinnedLabel.setText(text + " (click to manage)");
		pinnedLabel.setVisible(true);
	}

	private void saveNoteIfChanged()
	{
		if (selectedMonster == null)
		{
			return;
		}
		String current = mobProfile.note(currentMonsterId());
		String edited = noteArea.getText() == null ? "" : noteArea.getText().trim();
		if (!edited.equals(current))
		{
			mobProfile.setNote(currentMonsterId(), edited);
		}
	}

	/** The post-it: hidden without a monster; collapsed shows only the
	 * header line; expanded is the inline-editable note body. */
	private void refreshNotePanel()
	{
		if (selectedMonster == null || !displayOptions.notes)
		{
			notePanel.setVisible(false);
			return;
		}
		String note = mobProfile.note(currentMonsterId());
		if (!noteArea.getText().equals(note) && !noteArea.isFocusOwner())
		{
			noteArea.setText(note);
		}
		if (noteCollapsed && !note.isEmpty())
		{
			// Collapsed preview: the note's first line, truncated - the
			// full text rides the tooltip.
			String preview = note.replace('\n', ' ');
			if (preview.length() > 34)
			{
				preview = preview.substring(0, 34) + "...";
			}
			noteHeader.setText("> Note: " + preview);
			noteHeader.setToolTipText("<html>" + note.replace("\n", "<br>") + "</html>");
		}
		else
		{
			noteHeader.setText(noteCollapsed ? "+ Note (click to add)" : "v Note");
			noteHeader.setToolTipText(null);
		}
		noteArea.setVisible(!noteCollapsed);
		notePanel.setVisible(true);
		notePanel.revalidate();
		notePanel.repaint();
	}

	private void showPinnedMenu(MouseEvent e)
	{
		if (selectedMonster == null)
		{
			return;
		}
		int monsterId = currentMonsterId();
		JPopupMenu menu = new JPopupMenu();
		for (Map.Entry<String, Map<com.loadoutlab.data.GearSlot, Integer>> scoped
			: mobProfile.allPins(monsterId).entrySet())
		{
			String scope = scoped.getKey();
			for (Map.Entry<com.loadoutlab.data.GearSlot, Integer> entry
				: scoped.getValue().entrySet())
			{
				GearItem item = data.getGear(entry.getValue());
				String label = item == null ? ("item " + entry.getValue()) : item.label();
				JMenuItem row = new JMenuItem(
					"Unpin " + label + " (" + scopeLabel(scope) + ")");
				com.loadoutlab.data.GearSlot slot = entry.getKey();
				row.addActionListener(a ->
				{
					mobProfile.unpin(monsterId, scope, slot);
					refreshPinnedLabel();
					recompute();
				});
				menu.add(row);
			}
		}
		for (Map.Entry<String, Map<Integer, String>> scoped
			: mobProfile.allFilterItems(monsterId).entrySet())
		{
			String scope = scoped.getKey();
			for (Map.Entry<Integer, String> entry : scoped.getValue().entrySet())
			{
				JMenuItem row = new JMenuItem("Remove filter item " + entry.getValue()
					+ " (" + filterScopeLabel(scope) + ")");
				int itemId = entry.getKey();
				row.addActionListener(a ->
				{
					mobProfile.removeFilterItem(monsterId, scope, itemId);
					refreshPinnedLabel();
					reapplyBankViews();
				});
				menu.add(row);
			}
		}
		for (Map.Entry<String, Set<Integer>> scoped
			: mobProfile.allMobExclusions(monsterId).entrySet())
		{
			String scope = scoped.getKey();
			for (int itemId : scoped.getValue())
			{
				GearItem item = data.getGear(itemId);
				String label = item == null ? ("item " + itemId) : item.label();
				JMenuItem row = new JMenuItem("Allow " + label + " again ("
					+ scopeLabel(scope) + ")");
				row.addActionListener(a ->
				{
					mobProfile.removeMobExclusion(monsterId, scope, itemId);
					refreshPinnedLabel();
					recompute();
				});
				menu.add(row);
			}
		}
		menu.addSeparator();
		JMenuItem addPin = new JMenuItem("Pin an item - all sets (search)...");
		addPin.addActionListener(a -> searchAndPin(ALL_SETS));
		menu.add(addPin);
		JMenuItem addFilter = new JMenuItem("Bank filter - "
			+ filterScopeLabel(ALL_SETS) + " (search)...");
		addFilter.addActionListener(a -> searchAndAddFilter(ALL_SETS));
		menu.add(addFilter);
		menu.show(pinnedLabel, e.getX(), e.getY());
	}

	/**
	 * The magic card's spell controls: pin the autocast spell for this
	 * mob (the gear then optimizes around it - "I am casting Wind Bolt"),
	 * with the spellbook lock shown only while the spell is on Auto.
	 */
	private JPanel magicSpellRow()
	{
		JPanel wrap = new JPanel();
		wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
		wrap.setOpaque(false);
		wrap.setAlignmentX(LEFT_ALIGNMENT);
		String pinned = mobProfile.pinnedSpell(currentMonsterId());
		JComboBox<String> spellPin = new JComboBox<>();
		spellPin.addItem("Spell: Auto (best)");
		List<String> names = new ArrayList<>();
		for (com.loadoutlab.data.SpellStats spell : data.getSpells())
		{
			names.add(spell.getName());
		}
		Collections.sort(names);
		for (String name : names)
		{
			spellPin.addItem(name);
		}
		if (!pinned.isEmpty())
		{
			spellPin.setSelectedItem(pinned);
		}
		spellPin.setAlignmentX(LEFT_ALIGNMENT);
		spellPin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		spellPin.setToolTipText("Pin the autocast spell for this mob - the gear optimizes around it");
		spellPin.addActionListener(e ->
		{
			mobProfile.setPinnedSpell(currentMonsterId(),
				spellPin.getSelectedIndex() <= 0 ? "" : (String) spellPin.getSelectedItem());
			recompute();
		});
		wrap.add(spellPin);
		if (pinned.isEmpty())
		{
			wrap.add(spellbook);
		}
		return wrap;
	}

	/** The per-cell pin submenu: pin/unpin the shown item for this set or
	 * all sets, or chatbox-search ANOTHER item into the pin. */
	private javax.swing.JMenu pinSubmenu(GearItem item, com.loadoutlab.data.GearSlot slot,
		CombatStyle style)
	{
		int monsterId = currentMonsterId();
		javax.swing.JMenu pinMenu = new javax.swing.JMenu("Pin " + item.label());
		Map<String, Map<com.loadoutlab.data.GearSlot, Integer>> raw = mobProfile.allPins(monsterId);
		Integer styleScoped = raw.getOrDefault(style.name(), Collections.emptyMap()).get(slot);
		Integer allScoped = raw.getOrDefault(ALL_SETS, Collections.emptyMap()).get(slot);

		// Each item completes the parent "Pin <item>" - "...for the ranged
		// set only" / "...for all sets".
		if (styleScoped == null || styleScoped != item.getId())
		{
			JMenuItem thisSet = new JMenuItem("For the " + scopeLabel(style.name()) + " only");
			thisSet.addActionListener(a ->
			{
				mobProfile.pin(monsterId, style.name(), slot, item.getId());
				refreshPinnedLabel();
				recompute();
			});
			pinMenu.add(thisSet);
		}
		if (allScoped == null || allScoped != item.getId())
		{
			JMenuItem allSets = new JMenuItem("For all sets");
			// (all-sets pins keep their name - the mob is implicit)
			allSets.addActionListener(a ->
			{
				mobProfile.pin(monsterId, ALL_SETS, slot, item.getId());
				refreshPinnedLabel();
				recompute();
			});
			pinMenu.add(allSets);
		}
		if (styleScoped != null)
		{
			GearItem pinned = data.getGear(styleScoped);
			JMenuItem un = new JMenuItem("Unpin "
				+ (pinned == null ? "item" : pinned.label())
				+ " (" + style.name().toLowerCase(java.util.Locale.ROOT) + " set)");
			un.addActionListener(a ->
			{
				mobProfile.unpin(monsterId, style.name(), slot);
				refreshPinnedLabel();
				recompute();
			});
			pinMenu.add(un);
		}
		if (allScoped != null)
		{
			GearItem pinned = data.getGear(allScoped);
			JMenuItem un = new JMenuItem("Unpin "
				+ (pinned == null ? "item" : pinned.label()) + " (all sets)");
			un.addActionListener(a ->
			{
				mobProfile.unpin(monsterId, ALL_SETS, slot);
				refreshPinnedLabel();
				recompute();
			});
			pinMenu.add(un);
		}
		pinMenu.addSeparator();
		// Pin a DIFFERENT item into this slot (chatbox search), scoped.
		JMenuItem searchThis = new JMenuItem("Pin a different item (for the "
			+ scopeLabel(style.name()) + ")...");
		searchThis.addActionListener(a -> searchAndPin(style.name()));
		pinMenu.add(searchThis);
		JMenuItem searchAll = new JMenuItem("Pin a different item (for all sets)...");
		searchAll.addActionListener(a -> searchAndPin(ALL_SETS));
		pinMenu.add(searchAll);
		return pinMenu;
	}

	/** Chatbox item search -> pin into the picked item's own slot. */
	private void searchAndPin(String scope)
	{
		if (selectedMonster == null)
		{
			return;
		}
		int monsterId = currentMonsterId();
		itemSearch.search("Pin vs " + selectedMonster.getName()
			+ " (" + scopeLabel(scope) + ")", (itemId, name) ->
		{
			GearItem gear = data.getGear(itemId);
			if (gear == null)
			{
				JOptionPane.showMessageDialog(this,
					name + " is not equippable combat gear - use a bank-filter"
						+ " item for supplies.",
					"Pin an item", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			mobProfile.pin(monsterId, scope, gear.getSlot(), gear.getId());
			refreshPinnedLabel();
			recompute();
		});
	}

	/** Chatbox item search -> per-scope bank-filter supply. */
	private void searchAndAddFilter(String scope)
	{
		if (selectedMonster == null)
		{
			return;
		}
		int monsterId = currentMonsterId();
		itemSearch.search("Bank filter - " + filterScopeLabel(scope), (itemId, name) ->
		{
			// Filter items never touch the optimizer: NO recompute here or
			// anywhere in the filter paths - only the id-set views refresh.
			mobProfile.addFilterItem(monsterId, scope, itemId, name);
			refreshPinnedLabel();
			reapplyBankViews();
		});
	}

	/**
	 * Re-apply an active Show/Filter after the profile's filter items
	 * change: a pure id-set rebuild from the LAST results - never an
	 * optimizer recompute (filter items do not affect the math).
	 */
	private void reapplyBankViews()
	{
		if (lastResults == null || selectedMonster == null)
		{
			return;
		}
		if (bankFiltered != null)
		{
			StyleResult r = lastResults.get(bankFiltered);
			if (r != null && r.owned != null && !r.owned.isEmpty())
			{
				DpsResult best = r.owned.get(0);
				Set<Integer> ids = new java.util.HashSet<>(
					setItemIds(best, r.specWeapon, loadedDart(best)));
				ids.addAll(mobProfile.filterItems(currentMonsterId(), bankFiltered));
				bankFilter.filter(ids);
			}
		}
		if (bankShown != null)
		{
			StyleResult r = lastResults.get(bankShown);
			if (r != null && r.owned != null && !r.owned.isEmpty())
			{
				DpsResult best = r.owned.get(0);
				Set<Integer> ids = new java.util.HashSet<>();
				for (GearItem item : best.getLoadout().getGear().values())
				{
					if (item != null)
					{
						ids.add(item.getId());
					}
				}
				GearItem dart = loadedDart(best);
				if (dart != null)
				{
					ids.add(dart.getId());
				}
				if (r.specWeapon != null)
				{
					ids.add(r.specWeapon.getId());
				}
				ids.addAll(mobProfile.filterItems(currentMonsterId(), bankShown));
				bankHighlighter.highlight(ids);
			}
		}
	}

	/** Recompute the selected monster (the Connections toggle changes
	 * effective ownership; the plugin calls this on config change). */
	public void recomputeCurrent()
	{
		recompute();
	}

	private void showStoredMenu(MouseEvent e)
	{
		JPopupMenu menu = new JPopupMenu();
		for (Integer id : storedView.snapshot())
		{
			GearItem item = data.getGear(id);
			String label = item == null ? ("item " + id) : item.label();
			JMenuItem entry = new JMenuItem("No longer stored elsewhere: " + label);
			entry.addActionListener(a ->
			{
				storedToggle.toggle(id);
				refreshStoredLabel();
				recompute();
			});
			menu.add(entry);
		}
		menu.addSeparator();
		JMenuItem add = new JMenuItem("Add a stored-elsewhere item...");
		add.addActionListener(a -> showAddStoredDialog());
		menu.add(add);
		menu.show(storedLabel, e.getX(), e.getY());
	}

	/**
	 * Stored-elsewhere add: gear kept in storages the ledger cannot see
	 * never surfaces as a right-clickable suggestion, so the NATIVE
	 * chatbox item search is the way in (field request - the dialog
	 * matcher is gone here too).
	 */
	private void showAddStoredDialog()
	{
		itemSearch.search("Stored elsewhere (counts as owned)", (itemId, name) ->
		{
			GearItem gear = data.getGear(itemId);
			if (gear == null)
			{
				JOptionPane.showMessageDialog(this,
					name + " is not combat gear in the dataset - only equipment"
						+ " affects the loadout search.",
					"Stored elsewhere", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			if (!storedView.snapshot().contains(gear.getId()))
			{
				storedToggle.toggle(gear.getId());
			}
			refreshStoredLabel();
			recompute();
		});
	}

	/** Right-click menu on a suggested item: exclude it and recompute. A
	 * container weapon (blowpipe) also offers its loaded ammo. */
	private void attachExclusionMenu(JLabel cell, List<GearItem> items)
	{
		attachExclusionMenu(cell, items, Collections.emptyList(), null, null, Collections.emptySet());
	}

	private void attachExclusionMenu(JLabel cell, List<GearItem> items,
		List<JMenuItem> extras)
	{
		attachExclusionMenu(cell, items, extras, null, null, Collections.emptySet());
	}

	private void attachExclusionMenu(JLabel cell, List<GearItem> items,
		List<JMenuItem> extras, com.loadoutlab.data.GearSlot pinSlot, CombatStyle pinStyle)
	{
		attachExclusionMenu(cell, items, extras, pinSlot, pinStyle, Collections.emptySet());
	}

	/** lostIds: item ids currently shown with the death skull (dropped on
	 * death) - the "only bring if protected" flag is offered for those. */
	private void attachExclusionMenu(JLabel cell, List<GearItem> items,
		List<JMenuItem> extras, com.loadoutlab.data.GearSlot pinSlot, CombatStyle pinStyle,
		Set<Integer> lostIds)
	{
		cell.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				maybeShow(e);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				maybeShow(e);
			}

			private void maybeShow(MouseEvent e)
			{
				if (!e.isPopupTrigger())
				{
					return;
				}
				JPopupMenu menu = new JPopupMenu();
				for (JMenuItem extra : extras)
				{
					menu.add(extra);
				}
				for (GearItem item : items)
				{
					// Exclusion scopes (field request): everywhere, this mob,
					// or this mob + this set only.
					javax.swing.JMenu excludeMenu = new javax.swing.JMenu("Exclude " + item.label());
					JMenuItem everywhere = new JMenuItem("All mobs");
					everywhere.addActionListener(a ->
					{
						exclusionToggle.toggle(item.getId());
						refreshExclusionsLabel();
						recompute();
					});
					excludeMenu.add(everywhere);
					if (selectedMonster != null)
					{
						int monsterId = currentMonsterId();
						JMenuItem thisMob = new JMenuItem("Vs " + selectedMonster.getName() + " (all sets)");
						thisMob.addActionListener(a ->
						{
							mobProfile.excludeForMob(monsterId, ALL_SETS, item.getId());
							refreshPinnedLabel();
							recompute();
						});
						excludeMenu.add(thisMob);
						if (pinStyle != null)
						{
							JMenuItem thisSet = new JMenuItem("Vs " + selectedMonster.getName()
								+ " (" + scopeLabel(pinStyle.name()) + " only)");
							thisSet.addActionListener(a ->
							{
								mobProfile.excludeForMob(monsterId, pinStyle.name(), item.getId());
								refreshPinnedLabel();
								recompute();
							});
							excludeMenu.add(thisSet);
						}
					}
					menu.add(excludeMenu);
					// Wilderness low-risk: an item shown with the death skull
					// (dropped) can be flagged "only bring if protected" - the
					// optimizer then keeps it or omits it, never risks it.
					// Also offered while already flagged, to turn it back off.
					boolean flagged = protectOnlyToggle != null
						&& protectOnlyToggle.isProtectOnly(item.getId());
					if (protectOnlyToggle != null && (lostIds.contains(item.getId()) || flagged))
					{
						JMenuItem protect = new JMenuItem(flagged
							? "Allow " + item.label() + " unprotected again"
							: "Only bring " + item.label() + " if protected on death");
						protect.addActionListener(a ->
						{
							protectOnlyToggle.toggle(item.getId());
							recompute();
						});
						menu.add(protect);
					}
					// Unowned items can be dreamed into the owned pool
					// (and undreamed).
					boolean stored = storedView.snapshot().contains(item.getId());
					if (!ownedCheck.owns(item.getId()))
					{
						boolean dreamed = dreamView.snapshot().contains(item.getId());
						JMenuItem dream = new JMenuItem(dreamed
							? "Stop dreaming of " + item.label()
							: "Dream: consider " + item.label() + " as owned");
						dream.addActionListener(a ->
						{
							dreamToggle.toggle(item.getId());
							recompute();
						});
						menu.add(dream);
					}
					// Stored elsewhere: STASH, POH costume room, UIM cold or
					// nest storage - genuinely owned, just invisible to the
					// ledger. Once marked, owns() is true, so the un-mark
					// entry is what keeps the state reachable.
					if (stored || !ownedCheck.owns(item.getId()))
					{
						JMenuItem storeToggle = new JMenuItem(stored
							? "No longer stored elsewhere: " + item.label()
							: "Stored elsewhere: count " + item.label() + " as owned");
						storeToggle.addActionListener(a ->
						{
							storedToggle.toggle(item.getId());
							refreshStoredLabel();
							recompute();
						});
						menu.add(storeToggle);
					}
					// Pin: user preference wins the slot outright - for
					// THIS monster, scoped to this set or all sets.
					if (pinSlot != null && pinStyle != null && selectedMonster != null)
					{
						menu.add(pinSubmenu(item, pinSlot, pinStyle));
					}
				}
				menu.show(cell, e.getX(), e.getY());
			}
		});
	}

	/** The dart loaded in a blowpipe result, resolved for exclusion menus. */
	private GearItem loadedDart(DpsResult result)
	{
		String type = result.getAttackType();
		int idx = type.indexOf(" - ");
		if (idx < 0 || !type.startsWith("ranged"))
		{
			return null;
		}
		Integer dartId = BlowpipeDarts.baseIdForTierName(type.substring(idx + 3));
		return dartId == null ? null : data.getGear(dartId);
	}

	/** "No prayer helps" mark: a prohibition sign (circle + slash), painted so
	 * it inherits the incoming line's colour (glyphs tofu on macOS Tahoe). */
	private static final class NoPrayerIcon implements javax.swing.Icon
	{
		private final int size;

		NoPrayerIcon(int size)
		{
			this.size = size;
		}

		@Override
		public int getIconWidth()
		{
			return size;
		}

		@Override
		public int getIconHeight()
		{
			return size;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(c.getForeground());
			g2.setStroke(new BasicStroke(Math.max(1.3f, size / 9f),
				BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			double m = 1.5;
			double d = size - 2 * m - 1;
			g2.draw(new java.awt.geom.Ellipse2D.Double(x + m, y + m, d, d));
			double off = (d / 2.0) / Math.sqrt(2);
			double cx = x + size / 2.0;
			double cy = y + size / 2.0;
			g2.draw(new java.awt.geom.Line2D.Double(cx - off, cy + off, cx + off, cy - off));
			g2.dispose();
		}
	}

	/** Three-dots "more options" glyph, painted (Swing glyphs tofu on Tahoe). */
	private static final class DotsIcon implements javax.swing.Icon
	{
		private final int size;

		DotsIcon(int size)
		{
			this.size = size;
		}

		@Override
		public int getIconWidth()
		{
			return size;
		}

		@Override
		public int getIconHeight()
		{
			return size;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(c.getForeground());
			double r = Math.max(1.2, size / 9.0);
			double cy = y + size / 2.0;
			for (int i = 0; i < 3; i++)
			{
				double cx = x + size * (0.22 + 0.28 * i);
				g2.fill(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, 2 * r, 2 * r));
			}
			g2.dispose();
		}
	}

	/**
	 * Refresh glyph (circular arrow) painted as a ShapeIcon - the Unicode
	 * reload symbols tofu in Swing on macOS Tahoe, so we draw it. Inherits
	 * the host button's foreground colour.
	 */
	private static final class ReloadIcon implements javax.swing.Icon
	{
		private final int size;

		ReloadIcon(int size)
		{
			this.size = size;
		}

		@Override
		public int getIconWidth()
		{
			return size;
		}

		@Override
		public int getIconHeight()
		{
			return size;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			g2.translate(x, y);
			g2.setColor(c.getForeground());
			double cx = size / 2.0;
			double cy = size / 2.0;
			double r = size / 2.0 - 2.0;
			// 0 deg points up, sweeps clockwise; a gap is left for the head.
			double startDeg = 50;
			double sweepDeg = 265;
			java.awt.geom.Path2D.Double arc = new java.awt.geom.Path2D.Double();
			int steps = 48;
			for (int i = 0; i <= steps; i++)
			{
				double a = Math.toRadians(startDeg + sweepDeg * i / steps);
				double px = cx + r * Math.sin(a);
				double py = cy - r * Math.cos(a);
				if (i == 0)
				{
					arc.moveTo(px, py);
				}
				else
				{
					arc.lineTo(px, py);
				}
			}
			g2.setStroke(new BasicStroke(Math.max(1.4f, size / 9f),
				BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g2.draw(arc);
			// Arrowhead at the swept end, pointing along the clockwise tangent.
			double aEnd = Math.toRadians(startDeg + sweepDeg);
			double ex = cx + r * Math.sin(aEnd);
			double ey = cy - r * Math.cos(aEnd);
			double tx = Math.cos(aEnd);
			double ty = Math.sin(aEnd);
			double nx = -ty;
			double ny = tx;
			double h = size * 0.32;
			double w = size * 0.22;
			java.awt.geom.Path2D.Double head = new java.awt.geom.Path2D.Double();
			head.moveTo(ex + tx * h, ey + ty * h);
			head.lineTo(ex - tx * h * 0.2 + nx * w, ey - ty * h * 0.2 + ny * w);
			head.lineTo(ex - tx * h * 0.2 - nx * w, ey - ty * h * 0.2 - ny * w);
			head.closePath();
			g2.fill(head);
			g2.dispose();
		}
	}

	private void recompute()
	{
		if (selectedMonster == null)
		{
			return;
		}
		// Clear stale results immediately - showing the previous monster's
		// sets while the optimizer runs reads as an answer for this one.
		resultsPanel.removeAll();
		if (MascotArt.available())
		{
			// Mood roll, date-aware: through the World Cup final the striker
			// headlines (6/9), the 2-step keeps warm (2/9), the chef sneaks
			// in (1/9). After the final the striker hangs up his boots:
			// 2-step 2/3, chef 1/3.
			javax.swing.JComponent mascot;
			if (!java.time.LocalDate.now().isAfter(WORLD_CUP_FINAL))
			{
				int roll = MASCOT_MOOD.nextInt(9);
				mascot = roll < 6 ? new MascotStriker()
					: roll < 8 ? new MascotSpinner() : new MascotChef();
			}
			else
			{
				mascot = MASCOT_MOOD.nextInt(3) == 0
					? new MascotChef() : new MascotSpinner();
			}
			resultsPanel.add(mascot);
		}
		// html so long monster names wrap instead of clipping at the edge
		JLabel computing = new JLabel("<html>Optimizing vs " + selectedMonster.getName() + "...</html>");
		computing.setForeground(MUTED);
		computing.setAlignmentX(LEFT_ALIGNMENT);
		computing.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		resultsPanel.add(computing);
		resultsPanel.revalidate();
		revalidate();
		repaint();
		statusLabel.setText(" ");
		computeHook.compute(selectedMonster, f2pOnly.isSelected(), slayerTask.isSelected(),
			spellbookLock(), riskCap(), selectedRiskBudget(),
			superAntifireAssumed && DragonfireRules.breathesFire(selectedMonster),
			parsedBudgetGp(),
			com.loadoutlab.optimizer.OptimizerService.OptimizeMode.values()[optimizeMode.getSelectedIndex()],
			() -> statusLabel.setText(" "));
	}

	/** Account or profile switched: nothing on screen may survive. */
	public void resetForIdentityChange()
	{
		bankShown = null;
		bankFiltered = null;
		lastResults = null;
		clearSelection();
		refreshExclusionsLabel();
		refreshStoredLabel();
		refreshPinnedLabel();
		refreshNotePanel();
	}

	private void clearSelection()
	{
		selectedMonster = null;
		cardCollapsed.clear();
		selectedRow.setVisible(false);
		selectedLabel.setText("");
		monsterNote.setText("");
		monsterNote.setVisible(false);
		refreshNotePanel();
		refreshPinnedLabel();
		resultsPanel.removeAll();
		resultsPanel.revalidate();
		resultsPanel.repaint();
		statusLabel.setText("Search a monster to begin.");
		revalidate();
		searchField.requestFocusInWindow();
	}

	/** Render results (EDT). Called by the plugin once the optimizer returns. */
	public void showResults(MonsterStats monster, Map<CombatStyle, StyleResult> results)
	{
		if (selectedMonster == null || monster.getId() != selectedMonster.getId())
		{
			return; // stale result for a monster the user moved away from
		}
		lastResults = results;
		lastResultsMonsterId = monster.getId();
		resultsPanel.removeAll();
		usedSources.clear();
		// Default collapse: a set a full standard deviation under the best
		// dps (or with no usable set at all) starts folded to its header.
		autoCollapsed.clear();
		double bestDps = 0;
		double sum = 0;
		double sumSquares = 0;
		int usable = 0;
		for (CombatStyle style : CombatStyle.values())
		{
			StyleResult r = results.get(style);
			if (r != null && r.owned != null && !r.owned.isEmpty())
			{
				double d = r.owned.get(0).getDps();
				bestDps = Math.max(bestDps, d);
				sum += d;
				sumSquares += d * d;
				usable++;
			}
		}
		double stdev = usable > 1
			? Math.sqrt(Math.max(0, sumSquares / usable - (sum / usable) * (sum / usable)))
			: 0;
		for (CombatStyle style : CombatStyle.values())
		{
			StyleResult r = results.get(style);
			boolean hasSet = r != null && r.owned != null && !r.owned.isEmpty();
			autoCollapsed.put(style, !hasSet
				|| (usable > 1 && stdev > 0.01
					&& r.owned.get(0).getDps() < bestDps - stdev));
		}
		// Strongest style first: order the cards by your best set's dps.
		CombatStyle[] styleOrder = {CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC};
		Arrays.sort(styleOrder, Comparator.comparingDouble(style ->
		{
			StyleResult r = results.get(style);
			return r == null || r.owned.isEmpty() ? 0.0 : -r.owned.get(0).getDps();
		}));
		for (CombatStyle style : styleOrder)
		{
			resultsPanel.add(styleCard(style, results.get(style)));
			resultsPanel.add(Box.createVerticalStrut(6));
		}
		javax.swing.JComponent legend = buildSourceLegend();
		if (legend != null)
		{
			resultsPanel.add(legend);
		}
		statusLabel.setText("Best owned sets vs " + monster.getName());
		// Revalidate the PANEL, not just the results column: our new preferred
		// height has to reach RuneLite's outer scroll pane so the sidebar grows
		// to fit an expanded card (or shrinks back when one collapses).
		resultsPanel.revalidate();
		revalidate();
		repaint();
	}

	private JPanel styleCard(CombatStyle style, StyleResult result)
	{
		renderingStyle = style;
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		card.setAlignmentX(LEFT_ALIGNMENT);

		boolean hasSet = result != null && result.owned != null && !result.owned.isEmpty();
		boolean collapsed = cardCollapsed.containsKey(style)
			? cardCollapsed.get(style)
			: autoCollapsed.getOrDefault(style, false);

		// Header row: collapse toggle + summary dps left, the set's own
		// menu (per-set pins and bank-filter supplies) right.
		JLabel header = new JLabel((collapsed ? "> " : "v ") + style
			+ (hasSet
				? String.format(" - %.2f DPS", result.owned.get(0).getDps())
				: " - no set"));
		header.setForeground(Color.WHITE);
		header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
		header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		header.setToolTipText(collapsed ? "Click to expand this set" : "Click to collapse this set");
		header.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				cardCollapsed.put(style, !collapsed);
				if (selectedMonster != null && lastResults != null)
				{
					showResults(selectedMonster, lastResults);
				}
			}
		});
		JPanel headerRow = new JPanel(new BorderLayout());
		headerRow.setOpaque(false);
		headerRow.setAlignmentX(LEFT_ALIGNMENT);
		headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		headerRow.add(header, BorderLayout.WEST);
		JButton setMenu = new JButton(new DotsIcon(11));
		setMenu.setToolTipText("Pins and bank-filter items for this set");
		setMenu.setMargin(new Insets(1, 5, 1, 5));
		setMenu.addActionListener(e ->
		{
			JPopupMenu menu = new JPopupMenu();
			JMenuItem pinThis = new JMenuItem("Pin an item - "
				+ scopeLabel(style.name()) + " only (search)...");
			pinThis.addActionListener(ev -> searchAndPin(style.name()));
			menu.add(pinThis);
			JMenuItem pinAll = new JMenuItem("Pin an item - all sets (search)...");
			pinAll.addActionListener(ev -> searchAndPin(ALL_SETS));
			menu.add(pinAll);
			JMenuItem filterThis = new JMenuItem("Bank filter - "
				+ scopeLabel(style.name()) + " only (search)...");
			filterThis.addActionListener(ev -> searchAndAddFilter(style.name()));
			menu.add(filterThis);
			JMenuItem filterAll = new JMenuItem("Bank filter - "
				+ filterScopeLabel(ALL_SETS) + " (search)...");
			filterAll.addActionListener(ev -> searchAndAddFilter(ALL_SETS));
			menu.add(filterAll);
			menu.show(setMenu, 0, setMenu.getHeight());
		});
		// Assume icons ride the header (right, before the menu) - a whole
		// row of vertical space reclaimed per card.
		JPanel headerEast = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		headerEast.setOpaque(false);
		if (hasSet && displayOptions.assumes
			&& result.boostLabel != null && !result.boostLabel.isEmpty())
		{
			headerEast.add(assumesChips(result.boostLabel,
				"Assumed prayer + boost (you own these)"));
		}
		headerEast.add(setMenu);
		headerRow.add(headerEast, BorderLayout.EAST);
		card.add(headerRow);
		if (collapsed)
		{
			return card;
		}

		if (result == null || result.owned == null || result.owned.isEmpty())
		{
			boolean vyre = selectedMonster != null && selectedMonster.hasAttribute("vampyre3");
			boolean flying = style == CombatStyle.MELEE
				&& selectedMonster != null && selectedMonster.hasAttribute("flying");
			boolean immune = MonsterMechanics.styleImmune(selectedMonster, style);
			boolean leafy = selectedMonster != null && selectedMonster.hasAttribute("leafy");
			JLabel none = new JLabel(immune
				? "Immune to " + style.toString().toLowerCase()
				: vyre
				? "Immune - needs a vyre weapon"
				: leafy
				? "Needs leaf-bladed / broad / Magic Dart"
				: flying
					? "Flying - needs a halberd"
					: "No usable owned set found.");
			none.setForeground(MUTED);
			none.setAlignmentX(LEFT_ALIGNMENT);
			if (vyre)
			{
				none.setToolTipText("Only the Ivandis flail, blisterwood weapons,"
					+ " Sunspear, or Hallowed flail can damage this monster");
			}
			else if (flying)
			{
				none.setToolTipText("Melee cannot reach this monster"
					+ " except with halberds or salamanders");
			}
			card.add(none);
			return card;
		}

		DpsResult best = result.owned.get(0);

		if (style == CombatStyle.MAGIC && displayOptions.spellControls)
		{
			card.add(magicSpellRow());
		}

		// Max hit + accuracy for the shown set (each independently gated).
		// Its DPS lives in the card header - the old "Yours: X DPS" prefix
		// restated the same number one line apart (field report).
		String hitText = hitAccuracyText(best.getMaxHit(), best.getAccuracy());
		if (!hitText.isEmpty())
		{
			JLabel hitLine = new JLabel(hitText);
			hitLine.setForeground(GOOD);
			hitLine.setAlignmentX(LEFT_ALIGNMENT);
			card.add(hitLine);
		}
		// Assurance: name the conditional bonuses the math actually counted
		// WITH their exact numbers ("slayer helmet: +16.7% accuracy,
		// +16.7% damage"). Entries carry commas, so sources join on ";".
		// html with a fixed body width so the now-longer line WRAPS (and
		// reports a wrapped preferred HEIGHT - bare html labels still
		// measure single-line). As a plain label its preferred width
		// inflated every card past the sidebar edge.
		if (displayOptions.bonuses && !best.getCountedBonuses().isEmpty())
		{
			JLabel counting = line("<html><body style='width: 185px'>Counting: "
				+ String.join("; ", best.getCountedBonuses()) + "</body></html>", MUTED);
			counting.setFont(counting.getFont().deriveFont(11f));
			counting.setToolTipText("Conditional bonuses applied to this set's numbers");
			card.add(counting);
		}
		if (displayOptions.damageTaken)
		{
			addIncomingLine(card, result.incoming);
		}
		if (result.modeTrade != null)
		{
			card.add(modeTradeRow(result.modeTrade));
		}
		else if (optimizeMode.getSelectedIndex() > 0)
		{
			// Assurance: Balanced/Tanky ran and CHOSE the same set - not a
			// mix-up (common when a monster's incoming damage barely varies
			// with armour, or is not modeled).
			JLabel same = line("Same set as Max DPS - no safer trade found", MUTED);
			same.setFont(same.getFont().deriveFont(11f));
			same.setToolTipText("Balanced/Tanky swept the defence frontier and found no set"
				+ " worth trading dps for at this monster");
			card.add(same);
		}
		if (displayOptions.riskLine)
		{
			addRiskLine(card, best, result.specWeapon);
		}
		addUpgradeLine(card, best);
		if (displayOptions.prayerBonus)
		{
			addPrayerLine(card, best);
		}
		if (displayOptions.attackStyle)
		{
			addStyleLine(card, style, best);
			addSpellLine(card, style, best);
		}
		addDartLine(card, best);
		card.add(Box.createVerticalStrut(4));
		// The owned grid marks what you don't own (green) and what already
		// matches the game-best pick (gold).
		card.add(iconGrid(best, result.spec, result.specWeapon, result.specExpectedDamage,
			result.specDrainValue, best.getExpectedHit(), "Swap in for the special attack",
			true, result.overallBest == null ? null : result.overallBest.getLoadout()));
		if (displayOptions.showInBank || displayOptions.filterBank)
		{
			JPanel bankRow = iconRow(card);
			if (displayOptions.showInBank)
			{
				bankRow.add(bankButton(style, best, result.specWeapon));
			}
			if (displayOptions.filterBank)
			{
				bankRow.add(bankFilterButton(style, best, result.specWeapon));
			}
		}

		// The ceiling: the game-wide best set, so "off" numbers are inspectable.
		// The header always shows the summary; clicking it shows/hides the rest.
		if (displayOptions.gameBest && result.overallBest != null && result.overallBest.getDps() > 0)
		{
			card.add(Box.createVerticalStrut(6));
			boolean expanded = gameBestExpanded.contains(style);
			double pct = 100.0 * best.getDps() / result.overallBest.getDps();
			JLabel ceiling = line(String.format("%s Game best: %.2f DPS - you are at %.0f%%",
				expanded ? "v" : ">",
				result.overallBest.getDps(), Math.min(100.0, pct)), MUTED);
			ceiling.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			ceiling.setToolTipText(expanded ? "Click to hide the game-best set" : "Click to show the game-best set");
			ceiling.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if (!gameBestExpanded.remove(style))
					{
						gameBestExpanded.add(style);
					}
					if (selectedMonster != null && lastResults != null)
					{
						showResults(selectedMonster, lastResults);
					}
				}
			});
			card.add(ceiling);
			if (expanded)
			{
				if (displayOptions.assumes)
				{
					addAssumesRow(card, result.gameBoostLabel, "Best prayers + boost in the game");
				}
				// Max hit + accuracy for the ceiling set - the header only
				// carries its DPS, same as the owned card (each gated).
				String gameHitText = hitAccuracyText(result.overallBest.getMaxHit(),
					result.overallBest.getAccuracy());
				if (!gameHitText.isEmpty())
				{
					card.add(line(gameHitText, MUTED));
				}
				if (displayOptions.attackStyle)
				{
					addSpellLine(card, style, result.overallBest);
				}
				addDartLine(card, result.overallBest);
				card.add(Box.createVerticalStrut(4));
				card.add(iconGrid(result.overallBest, result.gameSpec, result.gameSpecWeapon, result.gameSpecExpectedDamage,
					result.gameSpecDrainValue, result.overallBest.getExpectedHit(),
					"Strongest special attack in the game vs this monster"));
			}
		}
		return card;
	}

	private static final ImageIcon PRAYER_ICON = loadPrayerIcon();
	private static final ImageIcon SWORD_ICON = loadSkillIcon("attack");
	private static final ImageIcon SHIELD_ICON = loadSkillIcon("defence");
	private static final javax.swing.Icon NO_PRAYER_ICON = new NoPrayerIcon(13);

	private static ImageIcon loadPrayerIcon()
	{
		return loadSkillIcon("prayer");
	}

	private static ImageIcon loadSkillIcon(String skill)
	{
		try
		{
			BufferedImage img = ImageUtil.loadImageResource(
				SkillIconManager.class, "/skill_icons_small/" + skill + ".png");
			return new ImageIcon(img.getScaledInstance(14, 14, Image.SCALE_SMOOTH));
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}

	/** Compact frontier trade: [sword] N%-  [shield] M%+ using the Attack and
	 * Defence skill icons (Swing emoji tofu on macOS Tahoe). Hover for the
	 * full sentence. */
	private JPanel modeTradeRow(ModeTrade t)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		row.setToolTipText(String.format(
			"This mode: %d%% less DPS for %d%% less damage taken", t.dpsLossPct, t.dmgCutPct));
		row.add(tradeChip(SWORD_ICON, t.dpsLossPct + "%-"));
		row.add(tradeChip(SHIELD_ICON, t.dmgCutPct + "%+"));
		return row;
	}

	private static JLabel tradeChip(ImageIcon icon, String text)
	{
		JLabel label = new JLabel(text);
		if (icon != null)
		{
			label.setIcon(icon);
			label.setIconTextGap(3);
		}
		label.setForeground(INFO);
		return label;
	}

	/** The set's total prayer bonus - just the prayer icon and the number. */
	private void addPrayerLine(JPanel card, DpsResult result)
	{
		int prayer = result.getLoadout().getBonuses().getPrayer();
		if (prayer == 0)
		{
			return;
		}
		JLabel line = line(PRAYER_ICON == null ? String.format("Prayer %+d", prayer)
			: String.format("%+d", prayer), MUTED);
		if (PRAYER_ICON != null)
		{
			line.setIcon(PRAYER_ICON);
			line.setIconTextGap(4);
		}
		line.setToolTipText("Gear prayer bonus - slower prayer drain");
		card.add(line);
	}

	/** The attack style the numbers use: "Style: Slash (aggressive)". */
	private void addStyleLine(JPanel card, CombatStyle style, DpsResult result)
	{
		if (style == CombatStyle.MAGIC)
		{
			return; // the spell / powered-staff line already covers magic
		}
		String type = result.getAttackType();
		String text;
		if (style == CombatStyle.RANGED)
		{
			text = type.contains("rapid") ? "Rapid" : "Accurate";
		}
		else
		{
			text = capitalize(type);
		}
		JLabel line = line("Style: " + text, INFO);
		line.setToolTipText("Use this attack style");
		card.add(line);
	}

	/** Blowpipes: name the loaded dart the numbers assume. */
	private void addDartLine(JPanel card, DpsResult result)
	{
		String type = result.getAttackType();
		int idx = type.indexOf(" - ");
		if (idx < 0 || !type.startsWith("ranged"))
		{
			return;
		}
		JLabel dart = line("Loaded with: " + type.substring(idx + 3), INFO);
		dart.setToolTipText("Dart included in the dps (right-click to exclude)");
		GearItem dartItem = loadedDart(result);
		if (dartItem != null)
		{
			attachExclusionMenu(dart, List.of(dartItem));
		}
		card.add(dart);
	}

	/** Everything the old spec line said, as the spec cell's tooltip. */
	private static String specTooltip(SpecialAttack spec, double expectedDamage,
		double drainValue, double replacedAutoExpected, String fallbackTooltip)
	{
		String headline = drainValue > 0.5
			? String.format("Spec: %s - %.0f dmg + drain ~%.0f (%d%% energy)",
				spec.getDisplayName(), expectedDamage, drainValue, spec.getEnergyCost())
			: String.format("Spec: %s - avg %.0f dmg (%d%% energy)",
				spec.getDisplayName(), expectedDamage, spec.getEnergyCost());
		String note = spec.getNote();
		// Spec throughput: weaving this spec on cooldown adds sustained dps
		// (energy regen 10% per 30s; the Lightbearer doubles it).
		String sustained = String.format("Weaving on cooldown: about +%.2f dps"
				+ " (+%.2f with a Lightbearer)",
			spec.sustainedDpsBonus(expectedDamage, replacedAutoExpected, false),
			spec.sustainedDpsBonus(expectedDamage, replacedAutoExpected, true));
		String drain = drainValue > 0.5
			? String.format("<br>Drain worth ~%.0f extra damage over the kill.", drainValue)
			: "";
		return "<html>" + headline
			+ "<br>" + (note.isEmpty() ? fallbackTooltip : note)
			+ "<br>" + sustained + drain + "</html>";
	}

	/**
	 * Magic only: name the spell and its spellbook explicitly - the weapon in
	 * the grid below is autocast-legal for that book (engine-gated).
	 */
	private void addSpellLine(JPanel card, CombatStyle style, DpsResult result)
	{
		if (style != CombatStyle.MAGIC)
		{
			return;
		}
		if (result.getSpellName() == null)
		{
			// A magic result without an autocast spell is a powered staff -
			// the weapon casts its own built-in spell.
			JLabel builtIn = line("Built-in spell (powered staff)", INFO);
			builtIn.setToolTipText("The staff casts its own spell");
			GearItem weapon = result.getLoadout().getWeapon();
			if (weapon != null)
			{
				attachItemIcon(builtIn, weapon.getId());
				builtIn.setIconTextGap(4);
			}
			card.add(builtIn);
			return;
		}
		String name = result.getSpellName();
		String book = data.getSpells().stream()
			.filter(s -> name.equals(s.getName()))
			.map(s -> s.getSpellbook())
			.findFirst().orElse("");
		JPanel row = iconRow(card);
		JLabel spell = line(name, INFO);
		spell.setToolTipText(book.isEmpty() ? "Autocast this spell"
			: "Autocast (" + capitalize(book) + " book)");
		int sprite = AssumeIcons.spellSprite(name);
		if (sprite >= 0)
		{
			attachSprite(spell, sprite);
			spell.setIconTextGap(4);
		}
		row.add(spell);
		if (name.contains("Demonbane"))
		{
			JLabel mod = new JLabel();
			mod.setToolTipText("Assumes Mark of Darkness");
			attachSprite(mod, AssumeIcons.MARK_OF_DARKNESS);
			row.add(mod);
		}
	}

	/**
	 * Wilderness: what a PvP death costs in gp for this set. Worn
	 * tradeables plus the carried spec weapon compete for the kept-on-
	 * death slots by value; everything past them is the risk.
	 */
	private void addRiskLine(JPanel card, DpsResult best, GearItem specWeapon)
	{
		if (!WildernessMonsters.isWilderness(selectedMonster))
		{
			return;
		}
		int keep = protectItem.isSelected() ? 4 : 3;
		PvpRisk.Assessment risk =
			PvpRisk.assess(best.getLoadout(), specWeapon, keep);
		JLabel line = line(String.format("Risk: %s gp (%d kept on death)",
			PvpRisk.formatGp(risk.riskGp), keep),
			risk.riskGp <= selectedRiskBudget()
				? GOOD : new Color(220, 140, 120));
		StringBuilder tip = new StringBuilder("<html>Kept on death:");
		if (risk.kept.isEmpty())
		{
			tip.append(" (none - all untradeable)");
		}
		for (GearItem item : risk.kept)
		{
			tip.append("<br>+ ").append(item.label())
				.append(" (").append(PvpRisk.formatGp(risk.valueOf(item))).append(")");
		}
		if (!risk.lost.isEmpty())
		{
			tip.append("<br>Lost:");
			for (GearItem item : risk.lost)
			{
				tip.append("<br>- ").append(item.label())
					.append(" (").append(PvpRisk.formatGp(risk.valueOf(item))).append(")");
			}
		}
		if (!risk.untradeableCharges.isEmpty())
		{
			tip.append("<br>Untradeable fees on death:");
			for (PvpRisk.Charge charge : risk.untradeableCharges)
			{
				tip.append("<br>- ").append(charge.item.label())
					.append(" (").append(PvpRisk.formatGp(charge.costGp)).append(")");
			}
		}
		tip.append("<br>Skulled: keep 0-1.");
		tip.append("</html>");
		line.setToolTipText(tip.toString());
		card.add(line);
	}

	/**
	 * What buying the unowned pieces in this set would cost. Quest rewards
	 * are excluded from the gp sum - they cost effort, not coins - and are
	 * listed by source quest in the tooltip instead; a set whose only
	 * unowned pieces are quest rewards shows a compact quest-only line.
	 */
	private void addUpgradeLine(JPanel card, DpsResult best)
	{
		long cost = 0;
		boolean questRewards = false;
		StringBuilder tip = new StringBuilder("<html>Not owned yet:");
		for (GearItem item : best.getLoadout().getGear().values())
		{
			if (item == null || ownedCheck.owns(item.getId()))
			{
				continue;
			}
			String quest = QuestRewardItems.questFor(item);
			if (quest != null)
			{
				questRewards = true;
				tip.append("<br>").append(item.label())
					.append(" (quest: ").append(quest).append(")");
			}
			else
			{
				cost += item.getPriceOrZero();
				tip.append("<br>").append(item.label()).append(" (")
					.append(PvpRisk.formatGp(item.getPriceOrZero())).append(")");
			}
		}
		if (cost <= 0 && !questRewards)
		{
			return;
		}
		JLabel line = line(cost > 0
			? String.format("Upgrade cost: ~%s gp", PvpRisk.formatGp(cost))
			: "Upgrade: quest rewards", UNOWNED);
		line.setToolTipText(tip.append("</html>").toString());
		card.add(line);
	}

	/** What the boss does back to you in this set, protection prayer up. */
	private void addIncomingLine(JPanel card, IncomingDpsCalculator.Result incoming)
	{
		if (incoming == null || incoming.totalDps <= 0)
		{
			return;
		}
		boolean prayable = incoming.protectPrayer != null;
		// The protect icon IS the pray call; the text is the cost - prayed,
		// and what skipping the prayer would cost you. Unblockable bosses
		// (dodge-based / typeless) still show the intake, with a no-prayer mark.
		JLabel line = line(prayable
			? String.format("~%.2f DPS to you (~%.2f unprayed)",
				incoming.totalDps, incoming.unprayedDps)
			: String.format("~%.2f DPS to you (unavoidable)", incoming.totalDps),
			new Color(210, 140, 130));
		if (prayable)
		{
			int sprite = AssumeIcons.prayerSprite(incoming.protectPrayer);
			if (sprite >= 0)
			{
				attachSprite(line, sprite);
				line.setIconTextGap(4);
			}
		}
		else
		{
			line.setIcon(NO_PRAYER_ICON);
			line.setIconTextGap(4);
		}
		StringBuilder tip = new StringBuilder("<html>")
			.append(prayable ? "Run " + incoming.protectPrayer + "."
				: "No prayer reduces this damage.");
		for (IncomingDpsCalculator.StyleThreat threat : incoming.threats)
		{
			tip.append("<br>").append(threat.style).append(": ");
			if (!threat.modeled)
			{
				tip.append("not modeled yet");
			}
			else if (threat.blocked)
			{
				tip.append(threat.prayerFactor > 0
					? String.format("%.0f%% pierces prayer (%.2f dps, max %d)",
						threat.prayerFactor * 100, threat.dps, threat.maxHit)
					: String.format("blocked (%.2f dps, max %d)", threat.dps, threat.maxHit));
			}
			else
			{
				tip.append(String.format("%.2f dps, max %d", threat.dps, threat.maxHit));
			}
		}
		tip.append(incoming.overrideNote != null && !incoming.overrideNote.isEmpty()
			? "<br>Curated: " + incoming.overrideNote
			: "<br>Assumes a uniform rotation.");
		if (!incoming.fullyModeled)
		{
			tip.append("<br>Unmodeled attacks not counted - treat as a floor.");
		}
		line.setToolTipText(tip.append("</html>").toString());
		card.add(line);
	}

	/** The wilderness death fates a grid cell can be badged with. */
	private enum Fate
	{
		KEPT, DROPPED, FEE
	}

	/**
	 * A grid cell that can carry a small top-left glyph: the wilderness
	 * death-fate marker (halo = protected, skull = lost to the killer,
	 * coin stack = survives but bills a replacement fee). Glyphs get a
	 * dark backing so they read on bright item sprites; borders keep
	 * their own language (gold/blue/green).
	 */
	private static final class RiskDotLabel extends JLabel
	{
		private static final Color BACKING = new Color(30, 30, 30);

		private Fate fate;
		/** Item-source dot (bottom-right; fates own the top-left). */
		private Color sourceDot;

		void setFate(Fate fate)
		{
			this.fate = fate;
		}

		void setSourceDot(Color color)
		{
			this.sourceDot = color;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			if (fate == null && sourceDot == null)
			{
				return;
			}
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			if (sourceDot != null)
			{
				g2.setColor(BACKING);
				g2.fillOval(getWidth() - 12, getHeight() - 12, 10, 10);
				g2.setColor(sourceDot);
				g2.fillOval(getWidth() - 10, getHeight() - 10, 6, 6);
			}
			if (fate != null)
			{
				switch (fate)
				{
					case KEPT:
						paintHalo(g2);
						break;
					case DROPPED:
						paintSkull(g2);
						break;
					default:
						paintCoins(g2);
						break;
				}
			}
			g2.dispose();
		}

		/** Angel halo: a tilted golden-white ring over a dark backing. */
		private static void paintHalo(Graphics2D g2)
		{
			g2.rotate(Math.toRadians(-15), 7.5, 4.5);
			g2.setColor(BACKING);
			g2.setStroke(new BasicStroke(4f));
			g2.drawOval(2, 2, 11, 5);
			g2.setColor(new Color(255, 236, 150));
			g2.setStroke(new BasicStroke(2f));
			g2.drawOval(2, 2, 11, 5);
		}

		/** The PK skull: cranium, jaw, eye sockets, tooth gaps. */
		private static void paintSkull(Graphics2D g2)
		{
			g2.setColor(BACKING);
			g2.fillOval(1, 1, 13, 12);
			g2.setColor(new Color(235, 235, 225));
			g2.fillOval(3, 2, 9, 8);
			g2.fillRect(5, 9, 5, 3);
			g2.setColor(BACKING);
			g2.fillOval(5, 5, 2, 2);
			g2.fillOval(8, 5, 2, 2);
			g2.drawLine(6, 10, 6, 11);
			g2.drawLine(8, 10, 8, 11);
		}

		/** The classic gp pile: stacked gold coins with darker rims. */
		private static void paintCoins(Graphics2D g2)
		{
			g2.setColor(BACKING);
			g2.fillOval(1, 1, 13, 12);
			paintCoin(g2, 3, 8);
			paintCoin(g2, 2, 5);
			paintCoin(g2, 3, 2);
		}

		private static void paintCoin(Graphics2D g2, int x, int y)
		{
			g2.setColor(new Color(140, 100, 25));
			g2.fillOval(x, y, 9, 5);
			g2.setColor(new Color(255, 200, 60));
			g2.fillOval(x + 1, y + 1, 7, 3);
			g2.setColor(new Color(255, 214, 90));
			g2.fillOval(x + 2, y + 1, 4, 2);
		}
	}

	/** Super antifire potion(4) - the icon for the assumed-potion chip. */
	private static final int SUPER_ANTIFIRE_ID = 21978;

	/** The dragonfire protection flip, shown on the shield cell. */
	private List<JMenuItem> dragonfireMenuEntries()
	{
		if (!DragonfireRules.breathesFire(selectedMonster))
		{
			return Collections.emptyList();
		}
		JMenuItem flip = new JMenuItem(superAntifireAssumed
			? "Require a dragonfire shield (drop the super antifire)"
			: "Assume a super antifire (drop the shield)");
		flip.addActionListener(a ->
		{
			superAntifireAssumed = !superAntifireAssumed;
			recompute();
		});
		return List.of(flip);
	}

	/** The active set's item ids: gear + loaded dart + spec weapon. */
	private static Set<Integer> setItemIds(DpsResult best, GearItem specWeapon, GearItem dart)
	{
		Set<Integer> ids = new java.util.HashSet<>();
		for (GearItem item : best.getLoadout().getGear().values())
		{
			if (item != null)
			{
				ids.add(item.getId());
			}
		}
		if (dart != null)
		{
			ids.add(dart.getId());
		}
		if (specWeapon != null)
		{
			ids.add(specWeapon.getId());
		}
		return ids;
	}

	/** "Filter bank": a virtual bank tag showing only this set's items. */
	private JButton bankFilterButton(CombatStyle style, DpsResult best, GearItem specWeapon)
	{
		boolean filtering = bankFiltered == style;
		JButton button = new JButton(filtering ? "Unfilter bank" : "Filter bank");
		button.setFont(button.getFont().deriveFont(13f));
		button.setMargin(new Insets(1, 6, 1, 6));
		button.setToolTipText("Show only this set's items in the bank (needs Bank Tags enabled)");
		button.addActionListener(e ->
		{
			if (bankFiltered == style)
			{
				bankFiltered = null;
				bankFilter.filter(null);
			}
			else
			{
				bankFiltered = style;
				Set<Integer> filterIds =
					new java.util.HashSet<>(setItemIds(best, specWeapon, loadedDart(best)));
				// The mob profile's supplies (food, antidotes...) join the
				// filtered bank view - they are part of THIS trip; ALL-sets
				// items plus this style's own (ranged pot on the ranged set).
				filterIds.addAll(mobProfile.filterItems(currentMonsterId(), style));
				bankFilter.filter(filterIds);
			}
			if (selectedMonster != null && lastResults != null)
			{
				showResults(selectedMonster, lastResults);
			}
		});
		return button;
	}

	/** "Show in bank": outline this set's items while the bank is open. */
	private JButton bankButton(CombatStyle style, DpsResult best, GearItem specWeapon)
	{
		boolean showing = bankShown == style;
		JButton button = new JButton(showing ? "Stop showing in bank" : "Show in bank");
		button.setAlignmentX(LEFT_ALIGNMENT);
		button.setFont(button.getFont().deriveFont(13f));
		button.setMargin(new Insets(1, 6, 1, 6));
		button.setToolTipText("Outline this set's items in the bank");
		button.addActionListener(e ->
		{
			if (bankShown == style)
			{
				bankShown = null;
				bankHighlighter.highlight(null);
			}
			else
			{
				Set<Integer> ids = new java.util.HashSet<>();
				for (GearItem item : best.getLoadout().getGear().values())
				{
					if (item != null)
					{
						ids.add(item.getId());
					}
				}
				GearItem dart = loadedDart(best);
				if (dart != null)
				{
					ids.add(dart.getId());
				}
				if (specWeapon != null)
				{
					ids.add(specWeapon.getId());
				}
				ids.addAll(mobProfile.filterItems(currentMonsterId(), style));
				bankShown = style;
				bankHighlighter.highlight(ids);
			}
			if (selectedMonster != null && lastResults != null)
			{
				showResults(selectedMonster, lastResults); // refresh button labels
			}
		});
		return button;
	}

	/** A left-aligned, height-capped flow row added to the card. */
	private JPanel iconRow(JPanel card)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		card.add(row);
		return row;
	}

	/**
	 * The assumed prayer + boost as icons - prayer-book sprite plus the
	 * potion/heart item icon; names live in the tooltips. Unmapped parts
	 * (e.g. "Current boosted levels") stay as text.
	 */
	private void addAssumesRow(JPanel card, String label, String tooltip)
	{
		if (label == null || label.isEmpty())
		{
			return;
		}
		JPanel row = iconRow(card);
		JLabel prefix = line("Assumes:", MUTED);
		prefix.setToolTipText(tooltip);
		row.add(prefix);
		row.add(assumesChips(label, tooltip));
	}

	/** Just the prayer/boost icon chips - the card HEADER hosts these
	 * inline with the style title to reclaim a whole row of vertical
	 * space (field request); tooltips carry the words. */
	private JPanel assumesChips(String label, String tooltip)
	{
		JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
		chips.setOpaque(false);
		chips.setToolTipText(tooltip);
		if (superAntifireAssumed && DragonfireRules.breathesFire(selectedMonster))
		{
			JLabel potion = new JLabel();
			potion.setToolTipText("Super antifire (right-click the shield cell to flip back)");
			attachItemIcon(potion, SUPER_ANTIFIRE_ID);
			chips.add(potion);
		}
		for (String part : label.split(" \\+ "))
		{
			JLabel chip = new JLabel();
			chip.setToolTipText(part);
			int sprite = AssumeIcons.prayerSprite(part);
			int item = AssumeIcons.boostItem(part);
			if (sprite >= 0)
			{
				attachSprite(chip, sprite);
			}
			else if (item >= 0)
			{
				attachItemIcon(chip, item);
			}
			else
			{
				chip.setText(part);
				chip.setForeground(MUTED);
				chip.setFont(chip.getFont().deriveFont(13f));
			}
			chips.add(chip);
		}
		return chips;
	}

	/** Game-cache sprite, scaled to line height, set async. */
	private void attachSprite(JLabel label, int spriteId)
	{
		spriteManager.getSpriteAsync(spriteId, 0, img ->
			SwingUtilities.invokeLater(() -> label.setIcon(new ImageIcon(
				img.getScaledInstance(-1, 16, Image.SCALE_SMOOTH)))));
	}

	/** Item icon scaled to line height (the native 36x32 dwarfs a text row). */
	private void attachItemIcon(JLabel label, int itemId)
	{
		AsyncBufferedImage img = itemManager.getImage(itemId);
		Runnable set = () -> label.setIcon(new ImageIcon(
			img.getScaledInstance(-1, 18, Image.SCALE_SMOOTH)));
		img.onLoaded(() -> SwingUtilities.invokeLater(set));
		set.run();
	}

	/**
	 * The set as an equipment grid. Two layouts share one cell builder: the
	 * compact 3x4 (11 slots + the amber spec cell as the 12th) for vertical
	 * density, or - when "Classic gear layout" is on - the in-game worn-
	 * equipment tab (5 rows of 3, empty corners) with the spec weapon in the
	 * empty slot left of the legs. Fixed rows x cols means the preferred
	 * height is always right (the old wrapping grid clipped its second row).
	 */
	private JPanel iconGrid(DpsResult result, SpecialAttack spec, GearItem specWeapon, double specExpected,
		double specDrainValue, double replacedAutoExpected, String specFallbackTooltip)
	{
		return iconGrid(result, spec, specWeapon, specExpected, specDrainValue,
			replacedAutoExpected, specFallbackTooltip, false, null);
	}

	private JPanel iconGrid(DpsResult result, SpecialAttack spec, GearItem specWeapon, double specExpected,
		double specDrainValue, double replacedAutoExpected, String specFallbackTooltip, boolean markUnowned,
		Loadout gameBest)
	{
		int cell = ICON_SIZE + 4;
		// Wilderness: badge every cell with its death fate.
		PvpRisk.Assessment fates = null;
		if (markUnowned && WildernessMonsters.isWilderness(selectedMonster))
		{
			fates = PvpRisk.assess(result.getLoadout(), specWeapon,
				protectItem.isSelected() ? 4 : 3);
		}
		// One store lookup per grid, not one per cell.
		Map<GearSlot, Integer> pinnedSlots = renderingStyle == null
			? Collections.emptyMap() : mobProfile.pins(currentMonsterId(), renderingStyle);
		RiskDotLabel specCell = buildSpecCell(cell, spec, specWeapon, specExpected,
			specDrainValue, replacedAutoExpected, specFallbackTooltip, fates);
		JPanel grid = displayOptions.classicLayout
			? classicGrid(cell, result, fates, pinnedSlots, markUnowned, gameBest, specCell)
			: compactGrid(cell, result, fates, pinnedSlots, markUnowned, gameBest, specCell);
		return centerRow(grid);
	}

	/**
	 * Wrap a grid in a full-width row that centres it horizontally. The card
	 * is a Y_AXIS BoxLayout of LEFT-aligned rows; centring the grid's own
	 * alignmentX instead would drag it against the other rows' alignment
	 * point (Swing's mixed-alignment gotcha), so we give it its own row.
	 */
	private JPanel centerRow(JPanel grid)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.add(grid);
		// Fill the card's width (so FlowLayout has room to centre in) but keep
		// the row's own height at the grid's height - don't let BoxLayout
		// stretch it vertically.
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	/** Compact 3x4: the 11 slots in GRID_ORDER, then the spec cell. */
	private JPanel compactGrid(int cell, DpsResult result, PvpRisk.Assessment fates,
		Map<GearSlot, Integer> pinnedSlots, boolean markUnowned, Loadout gameBest, RiskDotLabel specCell)
	{
		JPanel icons = new JPanel(new GridLayout(3, 4, 2, 2));
		icons.setOpaque(false);
		icons.setAlignmentX(LEFT_ALIGNMENT);
		for (GearSlot slotType : GRID_ORDER)
		{
			icons.add(buildSlotCell(slotType, result, cell, fates, pinnedSlots, markUnowned, gameBest));
		}
		icons.add(specCell);
		// Stretch past the minimum so there is no dead right margin, but cap
		// the width - unbounded stretch made cells balloon on wide layouts.
		int height = 3 * cell + 4;
		icons.setPreferredSize(new Dimension(4 * cell + 6, height));
		icons.setMaximumSize(new Dimension(4 * (cell + 8) + 6, height));
		return icons;
	}

	/**
	 * The in-game worn-equipment tab as a flat 5x3 order (read left-to-right,
	 * top-to-bottom): null = a blank corner, CLASSIC_SPEC_INDEX = the spec
	 * cell (the empty slot left of the legs). Every GearSlot in GRID_ORDER
	 * appears exactly once - guarded by ClassicLayoutTest.
	 */
	static final GearSlot[] CLASSIC_ORDER = {
		null,            GearSlot.HEAD, null,
		GearSlot.CAPE,   GearSlot.NECK, GearSlot.AMMO,
		GearSlot.WEAPON, GearSlot.BODY, GearSlot.SHIELD,
		null,            GearSlot.LEGS, null,   // index 9 (null) holds the spec cell
		GearSlot.HANDS,  GearSlot.FEET, GearSlot.RING,
	};
	static final int CLASSIC_SPEC_INDEX = 9;

	/** The in-game worn-equipment tab: 5 rows of 3, empty corners blank, spec
	 * in the empty slot left of the legs. */
	private JPanel classicGrid(int cell, DpsResult result, PvpRisk.Assessment fates,
		Map<GearSlot, Integer> pinnedSlots, boolean markUnowned, Loadout gameBest, RiskDotLabel specCell)
	{
		JPanel icons = new JPanel(new GridLayout(5, 3, 2, 2));
		icons.setOpaque(false);
		icons.setAlignmentX(LEFT_ALIGNMENT);
		for (int i = 0; i < CLASSIC_ORDER.length; i++)
		{
			if (i == CLASSIC_SPEC_INDEX)
			{
				icons.add(specCell);
				continue;
			}
			GearSlot slotType = CLASSIC_ORDER[i];
			icons.add(slotType == null ? blankCell(cell)
				: buildSlotCell(slotType, result, cell, fates, pinnedSlots, markUnowned, gameBest));
		}
		int height = 5 * cell + 8;
		icons.setPreferredSize(new Dimension(3 * cell + 4, height));
		icons.setMaximumSize(new Dimension(3 * (cell + 8) + 4, height));
		return icons;
	}

	/** A transparent placeholder holding a grid corner open (classic layout). */
	private static javax.swing.JComponent blankCell(int cell)
	{
		JLabel blank = new JLabel();
		blank.setPreferredSize(new Dimension(cell, cell));
		return blank;
	}

	/** One equipment-slot cell: the item icon with its border language, death
	 * fate, source dot, tooltip and right-click menu - or an empty box. */
	private RiskDotLabel buildSlotCell(GearSlot slotType, DpsResult result, int cell,
		PvpRisk.Assessment fates, Map<GearSlot, Integer> pinnedSlots, boolean markUnowned, Loadout gameBest)
	{
		GearItem item = result.getLoadout().get(slotType);
		RiskDotLabel slot = new RiskDotLabel();
		slot.setPreferredSize(new Dimension(cell, cell));
		slot.setHorizontalAlignment(SwingConstants.CENTER);
		List<JMenuItem> extras = slotType == GearSlot.SHIELD
			? dragonfireMenuEntries() : Collections.emptyList();
		if (item != null)
		{
			// Border language: green = you don't own it (dream/budget
			// upgrade); gold = your item IS the game's best available
			// for this slot; blue = the spec cell (matches the in-game
			// special attack bar).
			boolean unowned = markUnowned && !ownedCheck.owns(item.getId());
			GearItem bisItem = gameBest == null ? null : gameBest.get(slotType);
			// Analogs count: a stat-identical item (any god's d'hide
			// coif) is just as best-available as the exact pick.
			boolean bis = !unowned && bisItem != null
				&& (bisItem.getId() == item.getId() || statEquivalent(bisItem, item));
			Color border = unowned ? BORDER_UNOWNED
				: bis ? BORDER_BIS : BORDER_PLAIN;
			slot.setBorder(BorderFactory.createLineBorder(border));
			// Quest rewards are earned, not bought: name the quest
			// instead of quoting a gp price.
			String quest = QuestRewardItems.questFor(item);
			String obtain = quest != null ? "quest: " + quest
				: PvpRisk.formatGp(item.getPriceOrZero());
			String fate = "";
			if (fates != null)
			{
				if (containsId(fates.kept, item))
				{
					slot.setFate(Fate.KEPT);
					fate = " - protected on death";
				}
				else if (containsId(fates.lost, item))
				{
					slot.setFate(Fate.DROPPED);
					fate = " - lost on death ("
						+ PvpRisk.formatGp(fates.valueOf(item))
						+ imbueRefundNote(item) + ")";
				}
				else
				{
					long fee = feeFor(fates, item);
					long friction = com.loadoutlab.engine.UntradeableDeathCosts.frictionFor(item);
					if (fee > 0 && fee <= friction)
					{
						// Gp-free but a real errand chain (salve line):
						// the charge is all rebuild friction.
						slot.setFate(Fate.FEE);
						fate = " - breaks on death (rebuild errand ~" + PvpRisk.formatGp(friction) + ")";
					}
					else if (fee > 0)
					{
						slot.setFate(Fate.FEE);
						fate = " - replaceable for " + PvpRisk.formatGp(fee) + " on death";
					}
					else if (hasDeathCharge(fates, item))
					{
						slot.setFate(Fate.FEE);
						fate = " - breaks on death (free reclaim)";
					}
				}
			}
			// Location clause only when a fetch trip is needed - "in
			// bank" would be noise on 95% of cells.
			String where = unowned ? "" : locationHint.hint(item.getId());
			Integer pinnedHere = pinnedSlots.get(slotType);
			String pinNote = pinnedHere != null && pinnedHere == item.getId()
				? " - pinned" : "";
			// Source dot + legend entry: only for locations we know.
			if (!unowned)
			{
				String source = locationHint.primary(item.getId());
				Color sourceColor = SOURCE_COLORS.get(source);
				if (sourceColor != null)
				{
					slot.setSourceDot(sourceColor);
					usedSources.add(source);
				}
			}
			slot.setToolTipText(slotName(slotType) + ": " + item.label()
				+ pinNote
				+ (unowned ? " - NOT OWNED (" + obtain + ")" : "")
				+ (where.isEmpty() ? "" : " - " + where)
				+ (bis ? " - best available" : "")
				+ fate
				+ " (right-click to exclude)");
			AsyncBufferedImage img = itemManager.getImage(item.getId());
			img.addTo(slot);
			List<GearItem> menuItems = new ArrayList<>();
			menuItems.add(item);
			GearItem dart = slotType == GearSlot.WEAPON ? loadedDart(result) : null;
			if (dart != null)
			{
				menuItems.add(dart);
			}
			// Offer the protect-only flag on this cell only when the item
			// is dropped-on-death in the current wilderness set.
			Set<Integer> lostHere = fates != null && containsId(fates.lost, item)
				? Collections.singleton(item.getId()) : Collections.emptySet();
			attachExclusionMenu(slot, menuItems, extras, slotType, renderingStyle, lostHere);
		}
		else
		{
			slot.setBorder(BorderFactory.createLineBorder(BORDER_EMPTY));
			slot.setToolTipText(slotName(slotType) + ": empty");
			if (!extras.isEmpty())
			{
				attachExclusionMenu(slot, Collections.emptyList(), extras);
			}
		}
		return slot;
	}

	/** The special-attack weapon to swap in, amber-bordered - or an empty box. */
	private RiskDotLabel buildSpecCell(int cell, SpecialAttack spec, GearItem specWeapon, double specExpected,
		double specDrainValue, double replacedAutoExpected, String specFallbackTooltip, PvpRisk.Assessment fates)
	{
		RiskDotLabel specCell = new RiskDotLabel();
		specCell.setPreferredSize(new Dimension(cell, cell));
		specCell.setHorizontalAlignment(SwingConstants.CENTER);
		if (spec != null && specWeapon != null && specExpected > 0)
		{
			// Light sky blue, sampled from the in-game spec orb's gradient.
			specCell.setBorder(BorderFactory.createLineBorder(BORDER_SPEC));
			String specFate = "";
			if (fates != null && specWeapon != null)
			{
				if (containsId(fates.kept, specWeapon))
				{
					specCell.setFate(Fate.KEPT);
					specFate = "<br>Protected on death.";
				}
				else if (containsId(fates.lost, specWeapon))
				{
					specCell.setFate(Fate.DROPPED);
					specFate = "<br>Lost on death ("
						+ PvpRisk.formatGp(fates.valueOf(specWeapon)) + ").";
				}
				else if (feeFor(fates, specWeapon) == 0 && hasDeathCharge(fates, specWeapon))
				{
					specCell.setFate(Fate.FEE);
					specFate = "<br>Breaks on death (free reclaim).";
				}
				else if (feeFor(fates, specWeapon) > 0)
				{
					specCell.setFate(Fate.FEE);
					specFate = "<br>Replaceable for "
						+ PvpRisk.formatGp(feeFor(fates, specWeapon)) + " on death.";
				}
			}
			String specTip = specTooltip(spec, specExpected,
				specDrainValue, replacedAutoExpected, specFallbackTooltip);
			specCell.setToolTipText(specFate.isEmpty() ? specTip
				: specTip.replace("</html>", specFate + "</html>"));
			itemManager.getImage(specWeapon.getId()).addTo(specCell);
			attachExclusionMenu(specCell, List.of(specWeapon));
		}
		else
		{
			specCell.setBorder(BorderFactory.createLineBorder(BORDER_EMPTY));
			specCell.setToolTipText("Spec: none");
		}
		return specCell;
	}

	/** Same combat stats in every block - interchangeable for dps. */
	private static boolean statEquivalent(GearItem a, GearItem b)
	{
		return a.getSlot() == b.getSlot()
			&& a.getSpeed() == b.getSpeed()
			&& a.isTwoHanded() == b.isTwoHanded()
			&& a.getCategory().equals(b.getCategory())
			&& sameBlock(a.getOffensive(), b.getOffensive())
			&& sameBlock(a.getDefensive(), b.getDefensive())
			&& sameBlock(a.getBonuses(), b.getBonuses());
	}

	private static boolean sameBlock(StatBlock a, StatBlock b)
	{
		return a.getStab() == b.getStab() && a.getSlash() == b.getSlash()
			&& a.getCrush() == b.getCrush() && a.getMagic() == b.getMagic()
			&& a.getRanged() == b.getRanged() && a.getStrength() == b.getStrength()
			&& a.getRangedStrength() == b.getRangedStrength()
			&& a.getMagicDamage() == b.getMagicDamage()
			&& a.getPrayer() == b.getPrayer();
	}

	private static boolean containsId(List<GearItem> items, GearItem item)
	{
		for (GearItem candidate : items)
		{
			if (candidate.getId() == item.getId())
			{
				return true;
			}
		}
		return false;
	}

	private static long feeFor(PvpRisk.Assessment fates, GearItem item)
	{
		for (PvpRisk.Charge charge : fates.untradeableCharges)
		{
			if (charge.item.getId() == item.getId())
			{
				return charge.costGp;
			}
		}
		return 0;
	}

	/**
	 * Imbued convert-class items (rings (i), ...) drop UNIMBUED to the
	 * killer and the imbue points are fully refunded (April 2024 change) -
	 * without saying so, the bare gp figure looks like it forgot the imbue
	 * (field report: warrior ring (i) at 60k read as a bad suggestion).
	 */
	private static String imbueRefundNote(GearItem item)
	{
		if (com.loadoutlab.engine.UntradeableDeathCosts.categoryFor(item) == 4
			&& (item.getNameLower().endsWith("(i)") || item.getNameLower().endsWith("(ei)")))
		{
			return com.loadoutlab.engine.UntradeableDeathCosts.frictionFor(item) > 0
				? "; incl. rebuild errand - imbue points refunded"
				: "; drops unimbued, imbue points refunded";
		}
		return "";
	}

	/** True when the item breaks on death even at zero reclaim cost. */
	private static boolean hasDeathCharge(PvpRisk.Assessment fates, GearItem item)
	{
		for (PvpRisk.Charge charge : fates.untradeableCharges)
		{
			if (charge.item.getId() == item.getId())
			{
				return true;
			}
		}
		return false;
	}

	private static String slotName(GearSlot slot)
	{
		String name = slot.name().toLowerCase();
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	private static String capitalize(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
