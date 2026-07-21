package com.loadoutlab.ui;

import com.loadoutlab.UsageLog;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterNotes;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SlayerLockedMonsters;
import com.loadoutlab.data.StatBlock;
import com.loadoutlab.data.TripSupplies;
import com.loadoutlab.engine.ExtraDps;
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
import com.loadoutlab.optimizer.OptimizerService;
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
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
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
import javax.swing.JCheckBoxMenuItem;
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
import net.runelite.api.gameval.SpriteID;
import javax.swing.KeyStroke;
import javax.swing.JTextArea;
import javax.swing.JSlider;
import javax.swing.JMenu;
import javax.swing.JDialog;
import javax.swing.JComponent;
import javax.swing.Icon;
import java.util.Random;
import java.util.Objects;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.awt.Window;
import java.awt.Point;
import java.awt.Dialog;
import java.awt.Container;

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
			boolean inWilderness, String spellbookLock, int maxTradeables, int riskBudgetGp,
			boolean antifirePotion, int deathCharge,
			Map<CombatStyle, String> boostPicks, Map<CombatStyle, String> prayerPicks,
			int upgradeBudgetGp,
			com.loadoutlab.optimizer.OptimizerService.OptimizeMode mode, int maxSwaps,
			boolean raidBoost, Runnable onDone);

		/** Roster compute: ONE shared set per style across the mobs, with
		 * per-mob numbers delivered via showRosterResults. Default no-op
		 * keeps the interface functional for test lambdas - the plugin's
		 * production hook overrides it. */
		default void computeRoster(List<MonsterStats> mobs, boolean f2pOnly, boolean onSlayerTask,
			boolean inWilderness, String spellbookLock, int maxTradeables, int riskBudgetGp,
			boolean antifirePotion, int deathCharge,
			Map<CombatStyle, String> boostPicks, Map<CombatStyle, String> prayerPicks,
			int upgradeBudgetGp,
			com.loadoutlab.optimizer.OptimizerService.OptimizeMode mode, int maxSwaps,
			boolean raidBoost, Runnable onDone)
		{
			onDone.run();
		}
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
		final boolean loadingAnimation;
		/** The cross-tab assumed-spellbook chip - optional: it annoys
		 * players who never juggle spellbooks (field call 2026-07-21). */
		final boolean spellbookChip;

		/** Seeds for every NEW result's parameter zone (settings panel). */
		public final String defaultUpgradeBudget;
		public final String defaultRiskCap;
		public final boolean defaultOnTask;
		/** -1 = detect from the collection, else the antifire mode. */
		public final int defaultAntifireMode;

		/**
		 * The one canonical constructor. Callers that used to rely on the
		 * 18-/19-arg telescoping forms now pass the trailing defaults
		 * explicitly (defaultOnTask=false, defaultAntifireMode=-1).
		 */
		public DisplayOptions(boolean maxHit, boolean accuracy, boolean bonuses, boolean assumes,
			boolean damageTaken, boolean riskLine, boolean prayerBonus, boolean attackStyle,
			boolean gameBest, boolean notes, boolean spellControls, boolean upgradeBudget,
			boolean wildyRisk, boolean showInBank, boolean filterBank,
			boolean loadingAnimation, boolean spellbookChip,
			String defaultUpgradeBudget, String defaultRiskCap,
			boolean defaultOnTask, int defaultAntifireMode)
		{
			this.defaultAntifireMode = defaultAntifireMode;
			this.defaultOnTask = defaultOnTask;
			this.defaultUpgradeBudget = defaultUpgradeBudget == null ? "" : defaultUpgradeBudget;
			this.defaultRiskCap = defaultRiskCap == null ? "" : defaultRiskCap;
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
			this.loadingAnimation = loadingAnimation;
			this.spellbookChip = spellbookChip;
		}

		static DisplayOptions all()
		{
			return new DisplayOptions(true, true, true, true, true, true, true,
				true, true, true, true, true, true, true, true, true, true, "", "", false, -1);
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
	/** Back/forward over the plugin's CommandHistory. The panel renders the
	 * buttons, relays clicks, and feeds its own steps (monster selections)
	 * into the shared stack; the stack lives plugin-side. */
	public interface HistoryControl
	{
		boolean undo();

		boolean redo();

		boolean canUndo();

		boolean canRedo();

		/** Next back target's description, or null when the stack is empty. */
		String undoLabel();

		String redoLabel();

		/** Run a panel-originated step (a monster selection) through the
		 * same stack the store mutations use - ONE unified back/forward. */
		boolean execute(com.loadoutlab.command.Command command);
	}

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

		/** Per-mob sims (id -> add-time name): counted as owned for THIS
		 * mob only - the local twin of the global simmed items. */
		default Map<Integer, String> allMobSims(int monsterId)
		{
			return Map.of();
		}

		default void simForMob(int monsterId, int itemId, String name)
		{
		}

		default void removeMobSim(int monsterId, int itemId)
		{
		}

		/** Per-mob trip-supply overrides (TripSupplies category ->
		 * mode/option key); empty = the wrench-panel defaults apply. */
		default Map<String, String> supplyOverrides(int monsterId)
		{
			return Map.of();
		}

		/** Null/empty choice returns the category to the global default. */
		default void setSupplyOverride(int monsterId, String category, String choice)
		{
		}

		/** Whole-group twins (field request 2026-07-18): the same write on
		 * every group member, undoable as ONE action. */
		default void excludeForMobs(java.util.List<Integer> monsterIds, String scope, int itemId)
		{
			for (int id : monsterIds)
			{
				excludeForMob(id, scope, itemId);
			}
		}

		default void simForMobs(java.util.List<Integer> monsterIds, int itemId, String name)
		{
			for (int id : monsterIds)
			{
				simForMob(id, itemId, name);
			}
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

	/** The grey member of the exclude/sim/filter trio at the GLOBAL level:
	 * the always-filter item list plus panel-side edits of the wrench
	 * supply defaults (the config write loops back as a normal refresh). */
	public interface GlobalFilters
	{
		Map<Integer, String> alwaysFiltered();

		void addAlwaysFiltered(int itemId, String name);

		void removeAlwaysFiltered(int itemId);

		void setSupplyDefault(String category, String enumName);
	}

	private GlobalFilters globalFilters;

	public void setGlobalFilters(GlobalFilters filters)
	{
		globalFilters = filters;
		refreshCountChips();
	}

	/** The player's real Magic level (plugin-pushed at compute staging) -
	 * gates the thrall tier and the Vengeance chip. Volatile: written on
	 * the client thread, read on the EDT. */
	private volatile int magicLevel = 99;

	public void setMagicLevel(int level)
	{
		magicLevel = level;
	}

	/** Yama's rite of vile transference read (VarbitID
	 * DEATH_CHARGE_SCROLL_USED): Death Charge refunds from TWO kills per
	 * cast. Plugin-pushed at compute staging, like the Magic level. */
	private volatile boolean deathChargeUpgraded;

	public void setDeathChargeUpgraded(boolean upgraded)
	{
		deathChargeUpgraded = upgraded;
	}

	/** The player's LIVE spellbook (VarbitID.SPELLBOOK: 0 standard,
	 * 1 ancient, 2 lunar, 3 arceuus; -1 unknown) - the cross-tab Arceuus
	 * chip warns when the build's assumed book is not the current one. */
	private volatile int currentSpellbook = -1;
	private static final int SPELLBOOK_ARCEUUS = 3;
	private static final int SPELLBOOK_LUNAR = 2;

	public void setCurrentSpellbook(int spellbook)
	{
		currentSpellbook = spellbook;
	}

	/** Thralls default ON where they benefit (field call 2026-07-21):
	 * requirements met (tier reachable + book of the dead owned) and a
	 * fight long enough to matter - 150+ hp, boss/slayer-boss tier, not
	 * trash that dies before the thrall lands twice. */
	private boolean defaultThralls(ResultEntry entry)
	{
		if (ExtraDps.thrallDps(magicLevel) <= 0
			|| !ownedCheck.owns(ExtraDps.BOOK_OF_THE_DEAD))
		{
			return false;
		}
		int maxHp = 0;
		for (MonsterStats m : entry.mobs)
		{
			maxHp = Math.max(maxHp, m.getHitpoints());
		}
		return maxHp >= 150;
	}

	/** Death Charge defaults ON with the same benefit gate as thralls
	 * (Magic 80 + a 150+ hp fight); no book required - it is a spell. */
	private boolean defaultDeathCharge(ResultEntry entry)
	{
		if (magicLevel < 80)
		{
			return false;
		}
		int maxHp = 0;
		for (MonsterStats m : entry.mobs)
		{
			maxHp = Math.max(maxHp, m.getHitpoints());
		}
		return maxHp >= 150;
	}

	/** Defensive copy for the compute hook (the entry maps mutate on the
	 * EDT while the optimizer reads on its worker). */
	private static Map<CombatStyle, String> copyPicks(Map<CombatStyle, String> picks)
	{
		return picks.isEmpty() ? Collections.emptyMap() : new EnumMap<>(picks);
	}

	/** The assume-chip pickers (field direction 2026-07-21): the prayer and
	 * boost icons on each style card open a menu - Detect best (grey
	 * default) / None / the style's named tiers (boosts add the universal
	 * overloads and salts). Engine inputs: picking recomputes. */
	private void showPrayerPickMenu(ResultEntry entry, CombatStyle style)
	{
		JPopupMenu menu = new JPopupMenu();
		String current = entry.prayerPicks.get(style);
		pickItem(menu, "Detect best", current == null,
			() -> { entry.prayerPicks.remove(style); recompute(); }, entry);
		pickItem(menu, "None (prayerless)", "NONE".equals(current),
			() -> { entry.prayerPicks.put(style, "NONE"); recompute(); }, entry);
		for (String option : com.loadoutlab.engine.PrayerBonuses.optionsFor(style))
		{
			pickItem(menu, option, option.equals(current),
				() -> { entry.prayerPicks.put(style, option); recompute(); }, entry);
		}
		Point at = getMousePosition();
		menu.show(this, at != null ? at.x : 20, at != null ? at.y : 20);
	}

	private void showBoostPickMenu(ResultEntry entry, CombatStyle style)
	{
		JPopupMenu menu = new JPopupMenu();
		String current = entry.boostPicks.get(style);
		pickItem(menu, "Detect best in bank", current == null,
			() -> { entry.boostPicks.remove(style); recompute(); }, entry);
		pickItem(menu, "None (unboosted)", "NONE".equals(current),
			() -> { entry.boostPicks.put(style, "NONE"); recompute(); }, entry);
		for (com.loadoutlab.engine.BoostProfile option : styleBoosts(style))
		{
			pickItem(menu, option.toString(), option.name().equals(current),
				() -> { entry.boostPicks.put(style, option.name()); recompute(); }, entry);
		}
		Point at = getMousePosition();
		menu.show(this, at != null ? at.x : 20, at != null ? at.y : 20);
	}

	/** The style's boost family plus the universal raid boosts. */
	private static java.util.List<com.loadoutlab.engine.BoostProfile> styleBoosts(CombatStyle style)
	{
		java.util.List<com.loadoutlab.engine.BoostProfile> options = new ArrayList<>();
		for (com.loadoutlab.engine.BoostProfile b : com.loadoutlab.engine.BoostProfile.values())
		{
			if (b == com.loadoutlab.engine.BoostProfile.NONE
				|| b == com.loadoutlab.engine.BoostProfile.LIVE_CURRENT)
			{
				continue;
			}
			boolean universal = b.boosts('a') && b.boosts('r') && b.boosts('m');
			boolean forStyle = style == CombatStyle.MELEE ? b.boosts('a')
				: style == CombatStyle.RANGED ? b.boosts('r') : b.boosts('m');
			if (universal || forStyle)
			{
				options.add(b);
			}
		}
		return options;
	}

	private void pickItem(JPopupMenu menu, String label, boolean selected,
		Runnable action, ResultEntry entry)
	{
		JCheckBoxMenuItem item = new JCheckBoxMenuItem(label, selected);
		item.addActionListener(a -> asActive(entry, action));
		menu.add(item);
	}

	/** The display-only dps addition for the shown numbers: the thrall's
	 * flat tier dps (the official calculator's thrall toggle behaves the
	 * same) - never a ranking input. */
	private double extraShownDps(ResultEntry entry)
	{
		return entry.thralls ? ExtraDps.thrallDps(magicLevel) : 0;
	}

	/** True when the shown MAGIC answer autocasts a Demonbane spell - the
	 * case where Mark of Darkness pays (field ask 2026-07-21). */
	private boolean demonbaneCast(ResultEntry entry)
	{
		if (entry.results == null)
		{
			return false;
		}
		StyleResult magic = entry.results.get(CombatStyle.MAGIC);
		if (magic == null || magic.owned == null || magic.owned.isEmpty())
		{
			return false;
		}
		String spell = magic.owned.get(0).getSpellName();
		return spell != null && spell.contains("Demonbane");
	}

	/** The Arceuus casting dependencies the current assumptions imply
	 * (field ask 2026-07-21: "if we're recommending thralls we need to be
	 * recommending... the runes + rune pouch"): the resurrect runes + book
	 * of the dead when thralls are on (greater tier only - the one the
	 * 76+ auto-on gate reaches), Death Charge's runes when it is assumed,
	 * Mark of Darkness's when the magic card casts Demonbane, and the best
	 * owned rune pouch to carry any of them. Filter/layout ids - the cells
	 * stay compact (book + pouch only, via consumableIds). */
	/** Just the RUNES the assumptions imply - routed to the bank layout's
	 * utility strip beside the spellbook cape (field spec 2026-07-21: runes
	 * are staging gear, loaded into the pouch before the trip). */
	private List<Integer> arcaneRuneIds(ResultEntry entry)
	{
		List<Integer> ids = new ArrayList<>();
		if (entry.thralls && magicLevel >= 76)
		{
			for (int id : TripSupplies.spellKit("thrallGreaterRunes"))
			{
				ids.add(id);
			}
		}
		if (entry.deathCharge)
		{
			for (int id : TripSupplies.spellKit("deathChargeRunes"))
			{
				ids.add(id);
			}
		}
		if (demonbaneCast(entry))
		{
			for (int id : TripSupplies.spellKit("markOfDarknessRunes"))
			{
				ids.add(id);
			}
		}
		if ((entry.thralls || entry.deathCharge) && arceuusViaSwap())
		{
			for (int id : TripSupplies.spellKit("spellbookSwapRunes"))
			{
				ids.add(id);
			}
			for (int id : TripSupplies.spellKit("vengeanceRunes"))
			{
				ids.add(id);
			}
		}
		return ids;
	}

	private List<Integer> arcaneKitFilterIds(ResultEntry entry)
	{
		List<Integer> ids = new ArrayList<>(arcaneRuneIds(entry));
		if (entry.thralls)
		{
			for (int id : TripSupplies.spellKit("bookOfTheDead"))
			{
				ids.add(id);
			}
		}
		if (!ids.isEmpty())
		{
			int pouch = ownedRunePouch();
			if (pouch != -1)
			{
				ids.add(pouch);
			}
		}
		return ids;
	}

	/** Arceuus access mode (field nuance 2026-07-21): true = the player
	 * camps LUNAR and reaches Arceuus casts via Spellbook Swap (96 Magic,
	 * Dream Mentor) - the kit then adds the swap runes AND Vengeance's
	 * (being on Lunar makes veng available). Set from the grey chip menu. */
	private boolean arceuusViaSwap()
	{
		return "SPELLBOOK_SWAP".equals(supplyDefaults.get("arceuusAccess"))
			&& magicLevel >= 96;
	}

	/** The best owned rune pouch (divine first, trouver-locked variants
	 * counted), or -1. */
	private int ownedRunePouch()
	{
		for (int id : TripSupplies.spellKit("runePouch"))
		{
			if (ownedCheck.owns(id))
			{
				return id;
			}
		}
		return -1;
	}

	/** The persistent trip-supply defaults (config enum names keyed by
	 * TripSupplies category); resolved per result in activeSupplies. */
	private Map<String, String> supplyDefaults = Collections.emptyMap();

	/** Plugin hook: swap in the wrench-panel supply defaults and re-render
	 * so the cells, filter and layout pick them up. */
	public void setSupplyDefaults(Map<String, String> defaults)
	{
		supplyDefaults = defaults == null ? Collections.emptyMap() : defaults;
		renderPage();
	}

	/** The trip supplies this result brings: each category's config default
	 * resolved against the collection (Detect = best owned tier), anti-venom
	 * only when a roster mob can inflict venom. Options carry ids best
	 * first: ids[0] is the display cell, the full list joins the filter. */
	private List<TripSupplies.Option> activeSupplies(ResultEntry entry)
	{
		if (entry == null || supplyDefaults.isEmpty())
		{
			return Collections.emptyList();
		}
		List<TripSupplies.Option> picks = new ArrayList<>();
		// Per-mob overrides (the exclude/sim pattern) overlay the wrench-
		// panel defaults, keyed by the lensed mob's profile.
		Map<String, String> overrides = mobProfile.supplyOverrides(entry.mob().profileId());
		for (Map.Entry<String, String> e : supplyDefaults.entrySet())
		{
			String category = e.getKey();
			String mode = overrides.getOrDefault(category, e.getValue());
			if ("NONE".equals(mode))
			{
				continue;
			}
			if (TripSupplies.ANTIVENOM.equals(category))
			{
				boolean venomous = false;
				for (MonsterStats m : entry.mobs)
				{
					if (TripSupplies.inflictsVenom(m))
					{
						venomous = true;
						break;
					}
				}
				if (!venomous)
				{
					continue;
				}
			}
			TripSupplies.Option pick =
				"DETECT_BEST".equals(mode) || "DETECT".equals(mode)
					? TripSupplies.detectBest(category, id -> ownedCheck.owns(id))
					: TripSupplies.option(category, mode);
			if (pick != null)
			{
				picks.add(pick);
			}
		}
		return picks;
	}

	/** "Show in bank": set the highlighted item ids (null clears). */
	public interface BankHighlighter
	{
		void highlight(Set<Integer> itemIds);
	}

	/** "Filter bank": show only these item ids in the bank (null clears), laid
	 * out in the set's shape when {@code layout} is non-null - a bank-tag
	 * position array (index = bank slot, value = item id, -1 = empty). */
	public interface BankFilter
	{
		void filter(Set<Integer> itemIds, int[] layout);
	}

	private static final int SEARCH_DEBOUNCE_MS = 150;
	private static final int SEARCH_LIMIT = 25;
	private static final int ICON_SIZE = 32;
	/** Discord invite for the plugin's community; opened from the header. */
	private static final String DISCORD_URL = "https://discord.gg/6GuS6J8em3";
	/** Stamped into the copy-report text so a Discord report names its build.
	 * Keep in sync with the version in runelite-plugin.properties on release. */
	private static final String PLUGIN_VERSION = "0.3.3";
	/** Resting label of the copy-report chip; the flash reverts to exactly
	 * this, so a rapid double-click can't strand it on "Copied!". */
	private static final String COPY_REPORT_LABEL = "Copy report";
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
	private static final Map<String, Color> SOURCE_COLORS = new LinkedHashMap<>();
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
	private final Set<String> usedSources = new LinkedHashSet<>();

	/** Cell border language: gold = your item IS the game best, blue = the
	 * spec cell (matches the in-game spec orb), grey = owned/empty. */
	private static final Color BORDER_BIS = new Color(168, 148, 88);
	private static final Color BORDER_SPEC = new Color(120, 190, 240);
	private static final Color BORDER_PLAIN = new Color(70, 70, 70);
	/** Slot-cell interior - a step lighter than the card so dark item
	 * sprites (black boots, dark capes) keep their silhouette. */
	private static final Color CELL_BG = new Color(50, 50, 50);
	/** The toggle/selected border across the chip language - the mascots'
	 * limb green (field spec: match the legs). */
	private static final Color ACCENT = MascotArt.LIMB;
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
	private final BankHighlighter bankHighlighter;
	private final BankFilter bankFilter;
	/** Which style's set is filtering the bank (null = none). */
	private boolean bankFiltering;
	private Set<Integer> lastFilterIds;
	/** Which style's set is currently glowing in the bank (null = none). */
	private boolean bankShowing;
	private Set<Integer> lastHighlightIds;
	/** D-4: which frontier point to recommend per style. */
	private final JComboBox<String> optimizeMode = new JComboBox<>(
		new String[]{"Optimize: Max DPS", "Optimize: Balanced", "Optimize: Tanky"});
	/** Free-form upgrade budget: "750k", "1m", "1.5b", plain gp, or "-"
	 * for max. Empty or unparseable = off. */
	private final JTextField upgradeBudget = new JTextField();
	private int lastBudgetGp;
	private int lastRiskGp;
	private String lastRiskText = "";
	private final JTextField riskCapField = new JTextField();
	private final JLabel storedLabel = new JLabel();
	/** The user's own note for the selected monster: a collapsible
	 * post-it, edited inline (saves on focus loss - no edit button). */
	private final JPanel notePanel = new JPanel();
	private final JLabel excludeCountChip = new JLabel();
	private final JLabel dreamCountChip = new JLabel();
	private final JLabel filterCountChip = new JLabel();
	private final JLabel noteHeader = new JLabel();
	private final JTextArea noteArea = new JTextArea();
	/** Config-driven display gates (all on until the plugin sets them). */
	private DisplayOptions displayOptions = DisplayOptions.all();
	/** The upgrade-budget control row - gated by displayOptions.upgradeBudget. */
	private JPanel budgetRow;
	private static final Color POSTIT_BG = new Color(78, 72, 50);
	private static final Color POSTIT_FG = new Color(215, 205, 160);

	private final JTextField searchField = new JTextField();
	/** Search hits: MonsterStats rows and MonsterGroups.MonsterGroup rows
	 * (M-3) share the list - the renderer and selection handler branch. */
	private final DefaultListModel<Object> monsterModel = new DefaultListModel<>();
	private final JList<Object> monsterList = new JList<>(monsterModel);
	private final List<com.loadoutlab.data.MonsterGroups.MonsterGroup> groups;
	private final JScrollPane monsterScroll;
	private final JCheckBox f2pOnly = new JCheckBox("Non-members gear only");
	private final JCheckBox slayerTask = new JCheckBox("On slayer task");
	/** Shared-name wilderness monsters (Catacombs hellhounds...): the user
	 * says where the fight happens. Wilderness-exclusive monsters skip the
	 * checkbox - fighting them IS the Wilderness. */
	private final JCheckBox inWilderness = new JCheckBox("In the Wilderness");

	/** Header back/forward buttons; state refreshed after every step. */
	private JButton undoButton;
	private JButton redoButton;
	private HistoryControl historyControl;
	/** True while back/forward replays a step - the control listeners must
	 * apply effects (recompute) without recording a second step. */
	private boolean replayingHistory;
	/** True while painting the controls FROM the active entry (an active-
	 * result switch): effects must not recompute and recorders must not
	 * record - the controls are being told, not telling. */
	private boolean syncingControls;
	/** True once any monster has been selected this session - a pick after
	 * a deliberate CLEAR records (back returns to the cleared state), while
	 * the session's true first pick stays unrecorded. */
	private boolean hadSelection;
	/** The budget field's last committed text, for the back step. */
	private String lastBudgetText = "";
	private final JComboBox<String> spellbook =
		new JComboBox<>(new String[]{"Any spellbook", "Standard", "Ancient", "Arceuus"});
	// Sits in BorderLayout.CENTER (see the constructor), so it's stretched to
	// the fixed sidebar width and over-wide children can't inflate it - the
	// horizontal-clip guard the old Scrollable width-tracking used to provide
	// back when this lived in its own JScrollPane.
	private final JPanel resultsPanel = new JPanel();
	private final JLabel statusLabel = new JLabel(" ");
	/** The weighted-random source the roster draws each compute's mood from. */
	private static final Random MASCOT_MOOD = new Random();
	/** RuneLite developer mode: unlocks the resting animation gallery. */
	private boolean developerMode;
	private final Timer searchDebounce;

	/** Guards against programmatic search-field changes re-opening the list. */
	private boolean suppressSearchEvents;

	private final JCheckBox protectItem = new JCheckBox("Protect Item (keep 4)");
	/** Wilderness risk-cap dropdown values in gp; 75k is the default. */

	private MonsterStats selectedMonster;
	/** The style card currently being rendered (EDT-only render state) -
	 * grid cells read it for per-style pin menus and tooltips. */
	private CombatStyle renderingStyle;
	/** The rendering card's incoming-damage result (EDT-only render state) -
	 * the classic grid's info corner reads it. Null on game-best grids. */
	private IncomingDpsCalculator.Result renderingIncoming;
	/** The rendering card's assume chips (prayer + boost icons), stashed by
	 * styleCard for the stat panel (EDT-only render state). */
	private JComponent renderingChips;
	/** Whether the card being rendered is the BiS side of the toggle. */
	private boolean renderingBis;
	/** The lensed mob's curated mechanics note for the stat panel's (i). */
	private String renderingMechanicsNote;
	/** Protect Item assumed for this card (wilderness, field spec). */
	private boolean renderingProtectItem;
	/** Wilderness risk staging for the stat panel's skull line: the spec
	 * weapon competing for kept slots, and how many are kept. Null spec
	 * flag = no risk line (not wilderness / option off / BiS view). */
	private boolean renderingRiskLine;
	private List<Integer> renderingRiskConsumables = Collections.emptyList();

	/** Every id consumableIds() can emit - the plugin prefetches their GE
	 * prices ON THE CLIENT THREAD per compute (ItemManager.getItemPrice
	 * resolves compositions and asserts off-thread; field crash
	 * 2026-07-18) and hands the panel this cache for EDT rendering. */
	public static final int[] CONSUMABLE_PRICE_IDS = {
		20996, 20992, 12695, 2428, 113, 11722, 2444, 11726, 3040,
		27641, 20724, 2452, 21978};
	private volatile Map<Integer, Long> consumablePrices =
		Collections.emptyMap();

	public void setConsumablePrices(Map<Integer, Long> prices)
	{
		this.consumablePrices = prices == null
			? Collections.emptyMap() : prices;
	}
	/** The upgrade-cost line renders in the stat panel (Yours view). */
	private boolean renderingUpgradeLine;
	private GearItem renderingRiskSpecWeapon;
	private int renderingRiskKeep;
	/**
	 * One RESULT on the page: a query's monster plus its computed style
	 * results and every piece of view state that belongs to THIS result
	 * rather than the panel (multi-mob canvas M-1). The page renders each
	 * entry as its own card; today the page holds at most one.
	 */
	static final class ResultEntry
	{
		/** The roster this result answers: a shared set optimized ACROSS
		 * these mobs, with the mob list acting as an informational lens
		 * (Step C). Step A keeps it at exactly one mob, so a single-mob
		 * result is pixel-identical to before. */
		final List<MonsterStats> mobs = new ArrayList<>();
		/** Which mob's numbers show beneath the shared set (the lens). */
		int lensIndex;
		Map<CombatStyle, StyleResult> results;
		/** Roster answers, index-aligned with mobs; results above is the
		 * LENSED element. Null while computing or for legacy single-mob. */
		List<Map<CombatStyle, StyleResult>> perMobResults;
		/** Viewing the BiS answer instead of yours (the Yours|BiS toggle). */
		boolean viewingBis;
		boolean noteCollapsed = true;
		/** Dragonfire mode: 0 = gear (a shield is required in the set),
		 * 1 = regular antifire assumed (shield STILL required - only the
		 * combo fully blocks; the honesty rule refuses half-protection),
		 * 2 = super antifire assumed (shield slot freed). */
		int antifireMode;
		/** Whole-result fold: collapsed to the header's one-line summary. */
		boolean folded;
		/** The style tab in view; null = strongest owned set (the default). */
		CombatStyle selectedTab;

		// ---- the per-result PARAMETER ZONE (card anatomy spec) ----------
		// Owned here so every result carries its own query settings; the
		// controls write through to the ACTIVE entry (M-2c-2 step 1) until
		// the per-card chip row lands (step 2).
		boolean onSlayerTask;
		boolean inWilderness;
		/** Fold thrall dps into the shown numbers (display-only). */
		boolean thralls;
		/** Death Charge (Arceuus): 15% spec energy per killing blow - an
		 * ENGINE input to the spec model, so toggling recomputes. */
		boolean deathCharge;
		/** Assume-chip picker overrides (field direction 2026-07-21): per
		 * style, a BoostProfile enum name / a prayer tier name, or "NONE";
		 * absent = detect best (grey). Engine inputs - changing recomputes. */
		final Map<CombatStyle, String> boostPicks = new EnumMap<>(CombatStyle.class);
		final Map<CombatStyle, String> prayerPicks = new EnumMap<>(CombatStyle.class);
		int optimizeMode;
		boolean protectItem;
		/** Free-form wilderness risk cap ("25k"); empty = unconstrained.
		 * Non-empty IS the low-risk mode - the old toggle + step combo
		 * merged into one value (field spec 2026-07-17). */
		String riskCap = "";
		String upgradeBudget = "";
		int spellbookIndex;
		/** Assume your best offensive prayer (default) - off computes
		 * prayerless, the number you do while camping a protection prayer. */
		boolean assumeBestPrayer = true;
		/** Assume potion boosts (default) - off computes unboosted. */
		boolean assumeBoosts = true;
		/** The INVENTORY budget: carried items beyond the worn set, the
		 * spec weapon included. Defaults to 1 for singles/pairs, 3 for
		 * rosters of 3+ mobs, and a group's own preset (raids: 8) when it
		 * has one (field decisions 2026-07-17). */
		int maxSwaps = 1;
		/** What this entry's default currently is - the chip highlights
		 * only a value that differs from it. */
		int inventoryDefault = 1;
		/** True once the user picked an Inventory value - the roster
		 * default never overrides an explicit choice. */
		boolean inventoryTouched;
		/** Assume the raid-supplied boost (CoX overload+, ToA salts);
		 * off = the bank's own potions as a backup. */
		boolean raidBoost = true;
		/** Nullable: the owned kit's inventory breakpoint curve. */
		OptimizerService.KitCurve kitCurve;

		/** Re-seed the roster default (a group preset raises the floor):
		 * never shrinks, never overrides an explicit choice. */
		void seedInventoryDefault(int preset)
		{
			inventoryDefault = Math.max(inventoryDefault,
				Math.max(preset, mobs.size() >= 3 ? 3 : 1));
			if (!inventoryTouched && maxSwaps < inventoryDefault)
			{
				maxSwaps = inventoryDefault;
			}
		}

		ResultEntry(MonsterStats monster)
		{
			this.mobs.add(monster);
		}

		/** The lensed mob - whose numbers the card shows, and (until the
		 * roster optimizer lands at Step B) the only mob. Clamped so a
		 * shrinking roster never dangles the lens. */
		MonsterStats mob()
		{
			return mobs.get(Math.max(0, Math.min(lensIndex, mobs.size() - 1)));
		}

		/** Add a mob to this result's roster; false when already present. */
		boolean addMob(MonsterStats candidate)
		{
			for (MonsterStats existing : mobs)
			{
				if (existing.getId() == candidate.getId())
				{
					return false;
				}
			}
			mobs.add(candidate);
			return true;
		}

		/** True when any mob in the roster carries this id (result routing). */
		boolean hasMob(int monsterId)
		{
			for (MonsterStats m : mobs)
			{
				if (m.getId() == monsterId)
				{
					return true;
				}
			}
			return false;
		}
	}

	/** The page: an ordered list of results. M-1 keeps it at 0..1 entries -
	 * a single-mob page renders pixel-identical to the old single view. */
	private final List<ResultEntry> page = new ArrayList<>();
	/** The entry the panel-global affordances (toggles, notes, bank tools)
	 * act on. With a one-entry page this is always page.get(0). */
	private ResultEntry active;
	/** Async wiki thumbnails for the mob rows (null in tests - rows keep
	 * their text-only look). */
	private MonsterIcons monsterIcons;

	public void setMonsterIcons(MonsterIcons monsterIcons)
	{
		this.monsterIcons = monsterIcons;
	}

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
		this.groups = com.loadoutlab.data.MonsterGroups.load(data);
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
		// Undo/redo arrows ride the header row next to the options dots
		// (header-inline per the vertical-space rule; no row of their own).
		undoButton = new JButton(new UndoArrowIcon(12, true));
		undoButton.setMargin(new Insets(2, 5, 2, 5));
		undoButton.addActionListener(e ->
		{
			if (historyControl != null && historyControl.undo())
			{
				refreshAfterHistory();
			}
		});
		redoButton = new JButton(new UndoArrowIcon(12, false));
		redoButton.setMargin(new Insets(2, 5, 2, 5));
		redoButton.addActionListener(e ->
		{
			if (historyControl != null && historyControl.redo())
			{
				refreshAfterHistory();
			}
		});
		JButton optionsButton = new JButton(new DotsIcon(13));
		optionsButton.setToolTipText("Options");
		optionsButton.setMargin(new Insets(2, 6, 2, 6));
		optionsButton.addActionListener(e ->
		{
			JPopupMenu menu = new JPopupMenu();
			// Entry point for the first stored-elsewhere item (before any
			// exists there is no label or right-click row to reach it from).
			// Stored-elsewhere and simmed items moved to their own entry
			// points (the stored label's menu + the green +N chip); the
			// mob-specific actions live on the style cards and the
			// "This mob" line - the header menu stays plugin-wide.
			menuItem(menu, "Join our Discord", ev -> LinkBrowser.browse(DISCORD_URL));
			// Developer-mode only: reopen the live gallery of every mood.
			if (developerMode)
			{
				menuItem(menu, "Preview loading animations", ev -> showGallery());
			}
			menu.show(optionsButton, 0, optionsButton.getHeight());
		});
		JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		headerButtons.setOpaque(false);
		headerButtons.add(undoButton);
		headerButtons.add(redoButton);
		headerButtons.add(optionsButton);
		header.add(headerButtons, BorderLayout.EAST);
		refreshHistoryButtons();
		top.add(header);
		top.add(Box.createVerticalStrut(4));

		searchField.setAlignmentX(LEFT_ALIGNMENT);
		searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		// Exclusions (red -N) and dreams (green +N) live ABOVE the search
		// bar (field spec 2026-07-17) - compact rounded chips; clicking
		// manages the list. Counts refresh via refreshCountChips().
		JPanel countRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
		countRow.setOpaque(false);
		countRow.setAlignmentX(LEFT_ALIGNMENT);
		countRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		excludeCountChip.setOpaque(true);
		excludeCountChip.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		excludeCountChip.setFont(excludeCountChip.getFont().deriveFont(Font.BOLD, 12f));
		excludeCountChip.setToolTipText("Excluded items - click to manage");
		excludeCountChip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		onClick(excludeCountChip, e -> showExclusionsMenu(e));
		dreamCountChip.setOpaque(true);
		dreamCountChip.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		dreamCountChip.setFont(dreamCountChip.getFont().deriveFont(Font.BOLD, 12f));
		dreamCountChip.setToolTipText("Simmed items (considered as owned) - click to manage");
		dreamCountChip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		onClick(dreamCountChip, e -> showDreamsMenu(e));
		filterCountChip.setOpaque(true);
		filterCountChip.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		filterCountChip.setFont(filterCountChip.getFont().deriveFont(Font.BOLD, 12f));
		filterCountChip.setToolTipText("Bank filters: supply defaults +"
			+ " always-filtered items - click to manage");
		filterCountChip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		onClick(filterCountChip, e -> showGlobalFiltersMenu(e));
		countRow.add(excludeCountChip);
		countRow.add(dreamCountChip);
		countRow.add(filterCountChip);
		top.add(countRow);
		top.add(Box.createVerticalStrut(4));
		top.add(searchField);
		top.add(Box.createVerticalStrut(4));

		// Curated mechanics note (finishing items, immunities) for the
		// selected monster - so a correct suggestion doesn't look wrong.

		monsterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		monsterList.setVisibleRowCount(6);
		monsterList.setCellRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value,
				int index, boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if (value instanceof com.loadoutlab.data.MonsterGroups.MonsterGroup)
				{
					// Group hits wear the accent - they expand into a
					// whole roster, not a single mob.
					setText(((com.loadoutlab.data.MonsterGroups.MonsterGroup) value).label());
					setForeground(isSelected ? Color.WHITE : ACCENT);
				}
				else
				{
					setText(((MonsterStats) value).label());
				}
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

		// Shown only for monsters that ALSO live outside the Wilderness:
		// checked = wilderness weapons get their +50% and the risk options
		// appear. Wilderness-exclusive monsters are always "in".
		initToggle(inWilderness, "Fighting this monster inside the Wilderness:"
			+ " wilderness weapons get their +50% and the risk options apply");
		inWilderness.setVisible(false);


		initToggle(protectItem, "Protect Item keeps a 4th item (not while skulled)");
		protectItem.setVisible(false);


		// Spellbook lock lives ON the magic card (vertical space) - the
		// combo keeps its state here and is re-parented per render.
		spellbook.setAlignmentX(LEFT_ALIGNMENT);
		spellbook.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		spellbook.setToolTipText("Limit spells to one spellbook (powered staves always considered)");
		spellbook.addActionListener(e -> { if (!syncingControls) recompute(); });
		recordCombo(spellbook, "Spellbook");

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

		// D-4: pick the offense/defense frontier point (sweep is slower).
		optimizeMode.setAlignmentX(LEFT_ALIGNMENT);
		optimizeMode.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		optimizeMode.setToolTipText("Balanced/Tanky trade dps for less damage taken");
		optimizeMode.addActionListener(e -> { if (!syncingControls) recompute(); });
		recordCombo(optimizeMode, "Optimize");

		refreshExclusionsLabel();

		// Stored-elsewhere items (manual owned: STASH, POH, UIM storages).
		storedLabel.setForeground(GOOD);
		storedLabel.setFont(storedLabel.getFont().deriveFont(13f));
		storedLabel.setAlignmentX(LEFT_ALIGNMENT);
		storedLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		onClick(storedLabel, e -> showStoredMenu(e));
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
		onClick(noteHeader, e ->
		{
			saveNoteIfChanged();
			setNoteCollapsed(!noteCollapsed());
			refreshNotePanel();
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
		// The note rides the ACTIVE result card's tail (under the bank
		// buttons, field spec 2026-07-17) - re-parented per render, like
		// the spellbook combo before it.

		// Pinned items ("always bring") - click to manage.
		// The old "This mob: N pins..." label retired (field spec) - pins
		// and filter items surface as count chips in the parameter zone.

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
		// The empty-state prompt reads as a caption: centered, italic.
		statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
		statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC));
		add(statusLabel, BorderLayout.SOUTH);

		searchDebounce = new Timer(SEARCH_DEBOUNCE_MS, e -> runSearch());
		searchDebounce.setRepeats(false);
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e) { onSearchEdited(); }
			public void removeUpdate(DocumentEvent e) { onSearchEdited(); }
			public void changedUpdate(DocumentEvent e) { onSearchEdited(); }
		});
		// The search-hit right-click ("Add to view" - a SECOND result on
		// the page) left this release: the + Add mob row grows a roster on
		// the card, and a plain click opens a fresh result. The addToView
		// machinery stays for the group expansion to come (M-3).
		monsterList.addListSelectionListener(e ->
		{
			if (!e.getValueIsAdjusting() && monsterList.getSelectedValue() != null)
			{
				Object hit = monsterList.getSelectedValue();
				if (hit instanceof com.loadoutlab.data.MonsterGroups.MonsterGroup)
				{
					selectGroup((com.loadoutlab.data.MonsterGroups.MonsterGroup) hit);
					return;
				}
				select((MonsterStats) hit);
			}
		});

		statusLabel.setText("Search a monster to begin.");
	}

	/** Shared checkbox chrome; every toggle recomputes on change. */
	private boolean noteCollapsed()
	{
		return active == null || active.noteCollapsed;
	}

	private void setNoteCollapsed(boolean collapsed)
	{
		if (active != null)
		{
			active.noteCollapsed = collapsed;
		}
	}

	private boolean superAntifireAssumed()
	{
		return active != null && active.antifireMode == 2;
	}

	private Map<CombatStyle, StyleResult> lastResults()
	{
		return active == null ? null : active.results;
	}

	/** Is the CURRENT query a Wilderness fight - exclusive monsters always,
	 * shared-name wilderness monsters only when the user checked the box. */
	private boolean effectiveWilderness()
	{
		return active != null && effectiveWilderness(active);
	}

	/** Per-entry resolution: each entry carries its own in-Wilderness
	 * statement, resolved against its own monster (card anatomy spec). */
	private boolean effectiveWilderness(ResultEntry entry)
	{
		if (!WildernessMonsters.isWilderness(entry.mob()))
		{
			return false;
		}
		return WildernessMonsters.isExclusive(entry.mob()) || entry.inWilderness;
	}

	/** Sync the wilderness checkbox + risk rows to the selected monster. */
	private void initToggle(JCheckBox box, String tooltip)
	{
		box.setOpaque(false);
		box.setForeground(new Color(200, 200, 200));
		box.setAlignmentX(LEFT_ALIGNMENT);
		box.setToolTipText(tooltip);
		box.addActionListener(e -> recompute());
		// Every deliberate flip is a back/forward step. Replays drive the
		// box via doClick() so the effect listeners above still fire; this
		// recorder skips itself during a replay.
		box.addActionListener(e ->
		{
			if (replayingHistory || syncingControls)
			{
				return;
			}
			boolean turnedOn = box.isSelected();
			recordStep(box.getText() + (turnedOn ? " on" : " off"),
				() -> setToggleTo(box, turnedOn),
				() -> setToggleTo(box, !turnedOn));
		});
	}

	/** Drive a checkbox to a state as a replay: doClick fires the effect
	 * listeners (recompute, wilderness rows) exactly like a user click. */
	private void setToggleTo(JCheckBox box, boolean selected)
	{
		if (box.isSelected() != selected)
		{
			replayingHistory = true;
			try
			{
				box.doClick();
			}
			finally
			{
				replayingHistory = false;
			}
		}
	}

	/** Record a reversible panel step into the shared back/forward stack.
	 * CommandHistory.execute() runs apply() immediately; the setters are
	 * idempotent, so applying the state the control already shows is a
	 * no-op and nothing recomputes twice. */
	private void recordStep(String description, Runnable toNew, Runnable toOld)
	{
		if (historyControl == null)
		{
			return;
		}
		// A step is (action + the mob it was taken on): replaying it first
		// restores that monster, so "Optimize: Tanky" never flips a setting
		// against a cleared or different selection (field report 2026-07-16).
		MonsterStats at = selectedMonster;
		String label = at == null ? description : description + " - " + at.getName();
		historyControl.execute(new com.loadoutlab.command.Command()
		{
			@Override
			public boolean apply()
			{
				restoreContext(at);
				toNew.run();
				return true;
			}

			@Override
			public boolean revert()
			{
				restoreContext(at);
				toOld.run();
				return true;
			}

			@Override
			public String getDescription()
			{
				return label;
			}
		});
	}

	/** Re-select the monster a step was taken on before replaying it. An
	 * entry still on the page is switched to IN PLACE (its parameters and
	 * results survive); only a monster no longer on the page re-selects. */
	private void restoreContext(MonsterStats at)
	{
		if (at == null || selectedMonster == at)
		{
			return;
		}
		ResultEntry existing = entryFor(at.getId());
		if (existing != null)
		{
			setActive(existing);
			renderPage();
		}
		else
		{
			applySelection(at);
		}
	}

	/** Combo steps: track the previous index so the step can go back to it;
	 * replays and programmatic sets only refresh the tracker. */
	private void recordCombo(JComboBox<String> combo, String prefix)
	{
		int[] last = {combo.getSelectedIndex()};
		combo.addActionListener(e ->
		{
			int now = combo.getSelectedIndex();
			if (replayingHistory || syncingControls || historyControl == null || now == last[0] || now < 0)
			{
				last[0] = now;
				return;
			}
			int old = last[0];
			last[0] = now;
			recordStep(prefix + ": " + combo.getItemAt(now),
				() -> setComboTo(combo, now),
				() -> setComboTo(combo, old));
		});
	}

	private void setComboTo(JComboBox<String> combo, int index)
	{
		if (combo.getSelectedIndex() != index)
		{
			replayingHistory = true;
			try
			{
				combo.setSelectedIndex(index);
			}
			finally
			{
				replayingHistory = false;
			}
		}
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
		refreshNotePanel();
		// Rebuild the cards so per-line gates apply, reusing cached results -
		// but only when those results are for the CURRENT monster (a compute
		// may be in flight for a just-selected one; its render will follow).
		if (selectedMonster != null && lastResults() != null)
		{
			showResults(selectedMonster, lastResults());
		}
		revalidate();
		repaint();
	}

	/** Plugin hook: RuneLite developer mode. When on, the resting panel shows
	 * the live animation gallery and the "..." menu can reopen it. */
	public void setDeveloperMode(boolean dev)
	{
		this.developerMode = dev;
		// At rest (no monster picked yet) drop straight into the gallery.
		if (dev && selectedMonster == null)
		{
			showGallery();
		}
	}

	/** Fill the results area with a live gallery of every roster mood. */
	private void showGallery()
	{
		if (!MascotArt.available())
		{
			return;
		}
		resultsPanel.removeAll();
		resultsPanel.add(new MascotGallery());
		resultsPanel.revalidate();
		revalidate();
		repaint();
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
		for (com.loadoutlab.data.MonsterGroups.MonsterGroup group
			: com.loadoutlab.data.MonsterGroups.search(groups, query, 3))
		{
			monsterModel.addElement(group);
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

	/** A pick: record it as a back/forward step, then apply. Selecting a
	 * monster joins the same history as edits - the arrows are BACK and
	 * FORWARD through everything you did, so searching Vorkath after
	 * Zulrah means back lands on Zulrah (field request 2026-07-16). The
	 * first pick of a session is not recorded: back never lands on a
	 * blank panel. */
	private void select(MonsterStats monster)
	{
		MonsterStats previous = selectedMonster;
		if (historyControl == null || (previous == null && !hadSelection) || previous == monster)
		{
			applySelection(monster);
			return;
		}
		final List<ResultEntry> pageBefore = new ArrayList<>(page);
		final ResultEntry activeBefore = active;
		historyControl.execute(new com.loadoutlab.command.Command()
		{
			private List<ResultEntry> pageAfter;
			private ResultEntry activeAfter;

			@Override
			public boolean apply()
			{
				if (pageAfter == null)
				{
					applySelection(monster);
					pageAfter = new ArrayList<>(page);
					activeAfter = active;
				}
				else
				{
					// Redo reinstates the SAME entries - their parameter
					// zones and computed results survive the round trip.
					restorePage(pageAfter, activeAfter);
				}
				return true;
			}

			@Override
			public boolean revert()
			{
				// Back reinstates the replaced entries intact; a pick made
				// from a deliberately cleared panel goes back to cleared.
				if (activeBefore != null)
				{
					restorePage(pageBefore, activeBefore);
				}
				else
				{
					clearSelectionInternal();
				}
				return true;
			}

			@Override
			public String getDescription()
			{
				return "vs " + monster.label();
			}
		});
	}

	/** Reinstate a captured page (entry OBJECTS - parameters and results
	 * intact); entries whose results were never computed recompute. */
	private void restorePage(List<ResultEntry> entries, ResultEntry newActive)
	{
		page.clear();
		page.addAll(entries);
		hadSelection = true;
		setActive(newActive);
		renderPage();
		for (ResultEntry entry : page)
		{
			if (entry.results == null)
			{
				computeEntry(entry);
			}
		}
	}

	/** A group pick (M-3): the roster expands into ONE multi-mob result -
	 * recorded as a back/forward step exactly like a monster pick. */
	private void selectGroup(com.loadoutlab.data.MonsterGroups.MonsterGroup group)
	{
		if (historyControl == null || (selectedMonster == null && !hadSelection))
		{
			applyGroupSelection(group);
			return;
		}
		final List<ResultEntry> pageBefore = new ArrayList<>(page);
		final ResultEntry activeBefore = active;
		historyControl.execute(new com.loadoutlab.command.Command()
		{
			private List<ResultEntry> pageAfter;
			private ResultEntry activeAfter;

			@Override
			public boolean apply()
			{
				if (pageAfter == null)
				{
					applyGroupSelection(group);
					pageAfter = new ArrayList<>(page);
					activeAfter = active;
				}
				else
				{
					// Redo reinstates the SAME entries - results intact.
					restorePage(pageAfter, activeAfter);
				}
				return true;
			}

			@Override
			public boolean revert()
			{
				restorePage(pageBefore, activeBefore);
				return true;
			}

			@Override
			public String getDescription()
			{
				return "vs " + group.getName();
			}
		});
	}

	/** The group expansion: a fresh page holding one roster entry. */
	private void applyGroupSelection(com.loadoutlab.data.MonsterGroups.MonsterGroup group)
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
		hadSelection = true;
		page.clear();
		MonsterStats first = group.getMobs().get(0);
		selectedMonster = first;
		active = new ResultEntry(first);
		for (int i = 1; i < group.getMobs().size(); i++)
		{
			active.addMob(group.getMobs().get(i));
		}
		active.thralls = defaultThralls(active);
		active.deathCharge = defaultDeathCharge(active);
		active.seedInventoryDefault(group.getInventory());
		// Parameter seeding mirrors a single pick, anchored on the first
		// mob (the roster compute anchors exclusions/pins there too).
		active.onSlayerTask = SlayerLockedMonsters.isTaskOnly(first) || displayOptions.defaultOnTask;
		active.antifireMode = resolveDefaultAntifire(active);
		active.upgradeBudget = displayOptions.upgradeBudget
			? displayOptions.defaultUpgradeBudget : "";
		active.riskCap = displayOptions.wildyRisk ? displayOptions.defaultRiskCap : "";
		page.add(active);
		syncControlsFromActive();
		bankShowing = false;
		bankFiltering = false;
		lastHighlightIds = null;
		lastFilterIds = null;
		bankHighlighter.highlight(null);
		bankFilter.filter(null, null);
		applyActiveMonsterUi(first);
		usageLog.record(group.getName());
		setNoteCollapsed(mobProfile.note(first.getId()).isEmpty());
		refreshNotePanel();
		revalidate();
		repaint();
		renderPage();
		statusLabel.setText(" ");
		computeEntry(active);
	}

	/** The selection itself: collapse the dropdown, show it, recompute. */
	private void applySelection(MonsterStats monster)
	{
		applySelection(monster, true);
	}

	/** The selection itself. replacePage: the classic pick (page becomes
	 * this monster alone); otherwise the monster JOINS the page as a new
	 * result and becomes active (multi-add, M-2). */
	private void applySelection(MonsterStats monster, boolean replacePage)
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
		hadSelection = true;
		if (replacePage)
		{
			page.clear();
		}
		else
		{
			// Only a single-mob duplicate leaves; a ROSTER containing this
			// mob survives (searching a member must not destroy the group).
			page.removeIf(e -> e.mobs.size() == 1 && e.hasMob(monster.getId()));
		}
		active = new ResultEntry(monster);
		// Seed the parameter zone from the monster's own gating: task-only
		// bosses fight on-task; everything else starts at the defaults
		// (out of the wilderness - a per-fight statement, not a preference).
		active.onSlayerTask = SlayerLockedMonsters.isTaskOnly(monster) || displayOptions.defaultOnTask;
		active.thralls = defaultThralls(active);
		active.deathCharge = defaultDeathCharge(active);
		active.antifireMode = resolveDefaultAntifire(active);
		active.upgradeBudget = displayOptions.upgradeBudget
			? displayOptions.defaultUpgradeBudget : "";
		active.riskCap = displayOptions.wildyRisk ? displayOptions.defaultRiskCap : "";
		page.add(active);
		syncControlsFromActive();
		bankShowing = false;
		bankFiltering = false;
		lastHighlightIds = null;
		lastFilterIds = null;
		bankHighlighter.highlight(null);
		bankFilter.filter(null, null);
		applyActiveMonsterUi(monster);
		usageLog.record(monster.label());
		// A new mob: its own note state.
		setNoteCollapsed(mobProfile.note(monster.getId()).isEmpty());
		refreshNotePanel();
		revalidate();
		repaint();
		if (replacePage)
		{
			recompute();
		}
		else
		{
			// Existing results stay; only the newcomer computes.
			renderPage();
			statusLabel.setText(" ");
			computeEntry(active);
		}
	}

	/** The user changed a control: copy the control values into the ACTIVE
	 * entry's parameter zone. Other entries keep their own settings - a
	 * recompute re-runs them with unchanged params (cache hits). */
	private void syncActiveFromControls()
	{
		if (active == null)
		{
			return;
		}
		active.onSlayerTask = slayerTask.isSelected();
		active.inWilderness = inWilderness.isSelected();
		active.optimizeMode = Math.max(0, optimizeMode.getSelectedIndex());
		active.protectItem = protectItem.isSelected();
		active.riskCap = riskCapField.getText() == null ? "" : riskCapField.getText();
		active.upgradeBudget = upgradeBudget.getText() == null ? "" : upgradeBudget.getText();
		active.spellbookIndex = Math.max(0, spellbook.getSelectedIndex());
	}

	/** The active result changed: paint the controls from ITS parameter
	 * zone, without recomputing or recording (syncingControls guard). */
	private void syncControlsFromActive()
	{
		if (active == null)
		{
			return;
		}
		syncingControls = true;
		try
		{
			slayerTask.setSelected(active.onSlayerTask);
			inWilderness.setSelected(active.inWilderness);
			optimizeMode.setSelectedIndex(active.optimizeMode);
			protectItem.setSelected(active.protectItem);
			riskCapField.setText(active.riskCap);
			lastRiskGp = parsedBudgetGp(active.riskCap);
			lastRiskText = active.riskCap;
			upgradeBudget.setText(active.upgradeBudget);
			lastBudgetGp = parsedBudgetGp(active.upgradeBudget);
			lastBudgetText = active.upgradeBudget;
			spellbook.setSelectedIndex(active.spellbookIndex);
		}
		finally
		{
			syncingControls = false;
		}
	}

	/** The affordances keyed to the ACTIVE monster: slayer-toggle gating,
	 * the selected row, and the curated monster note. Shared by selection
	 * and by active-entry changes when a result closes. */
	private void applyActiveMonsterUi(MonsterStats monster)
	{
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
		// The name row and the note moved onto the result card itself
		// (mob list + per-card mechanics note) - nothing to paint here.
	}

	/** Remove one mob from a roster (the row's x): the entry recomputes
	 * as the smaller shared set. A back step restores roster + results. */
	void removeMobFromEntry(ResultEntry entry, int index)
	{
		if (entry.mobs.size() <= 1 || index < 0 || index >= entry.mobs.size())
		{
			return;
		}
		final MonsterStats removed = entry.mobs.get(index);
		final List<MonsterStats> mobsBefore = new ArrayList<>(entry.mobs);
		final Map<CombatStyle, StyleResult> resultsBefore = entry.results;
		final List<Map<CombatStyle, StyleResult>> perMobBefore = entry.perMobResults;
		final int lensBefore = entry.lensIndex;
		final Runnable applyRemove = () ->
		{
			entry.mobs.remove(index);
			entry.lensIndex = Math.max(0, Math.min(entry.lensIndex, entry.mobs.size() - 1));
			entry.results = null;
			entry.perMobResults = null;
			if (entry == active)
			{
				selectedMonster = entry.mob();
				applyActiveMonsterUi(entry.mob());
			}
			renderPage();
			statusLabel.setText(" ");
			computeEntry(entry);
		};
		if (historyControl == null)
		{
			applyRemove.run();
			return;
		}
		historyControl.execute(new com.loadoutlab.command.Command()
		{
			@Override
			public boolean apply()
			{
				applyRemove.run();
				return true;
			}

			@Override
			public boolean revert()
			{
				entry.mobs.clear();
				entry.mobs.addAll(mobsBefore);
				entry.lensIndex = lensBefore;
				entry.results = resultsBefore;
				entry.perMobResults = perMobBefore;
				if (entry == active)
				{
					selectedMonster = entry.mob();
					applyActiveMonsterUi(entry.mob());
				}
				renderPage();
				return true;
			}

			@Override
			public String getDescription()
			{
				return "Remove " + removed.getName() + " from result";
			}
		});
	}

	/** Join a mob to a result's roster (the + row's picker): the entry
	 * recomputes as ONE shared set across the list. A back step restores
	 * the previous roster and its results intact. */
	void addMobToEntry(ResultEntry entry, MonsterStats monster)
	{
		final ResultEntry target = entry;
		if (target == null)
		{
			addToView(monster);
			return;
		}
		if (target.hasMob(monster.getId()))
		{
			for (int i = 0; i < target.mobs.size(); i++)
			{
				if (target.mobs.get(i).getId() == monster.getId())
				{
					lensTo(target, i);
					return;
				}
			}
		}
		final List<ResultEntry> pageBefore = new ArrayList<>(page);
		final ResultEntry activeBefore = active;
		final List<MonsterStats> mobsBefore = new ArrayList<>(target.mobs);
		final Map<CombatStyle, StyleResult> resultsBefore = target.results;
		final List<Map<CombatStyle, StyleResult>> perMobBefore = target.perMobResults;
		final int lensBefore = target.lensIndex;
		final Runnable applyAdd = () ->
		{
			// A single-mob duplicate leaves the page (the roster absorbs it).
			page.removeIf(e -> e != target && e.mobs.size() == 1 && e.hasMob(monster.getId()));
			target.addMob(monster);
			target.seedInventoryDefault(0);
			target.lensIndex = target.mobs.size() - 1;
			target.results = null;
			target.perMobResults = null;
			setActive(target);
			selectedMonster = target.mob();
			applyActiveMonsterUi(target.mob());
			renderPage();
			statusLabel.setText(" ");
			computeEntry(target);
		};
		if (historyControl == null)
		{
			applyAdd.run();
			return;
		}
		historyControl.execute(new com.loadoutlab.command.Command()
		{
			@Override
			public boolean apply()
			{
				applyAdd.run();
				return true;
			}

			@Override
			public boolean revert()
			{
				target.mobs.clear();
				target.mobs.addAll(mobsBefore);
				target.lensIndex = lensBefore;
				target.results = resultsBefore;
				target.perMobResults = perMobBefore;
				restorePage(pageBefore, activeBefore);
				return true;
			}

			@Override
			public String getDescription()
			{
				return "Add " + monster.getName() + " to result";
			}
		});
	}

	/** Add a monster to the page as its own result (search-hit right-click).
	 * A back/forward step: back removes it and restores the previous active. */
	void addToView(MonsterStats monster)
	{
		MonsterStats previousActive = selectedMonster;
		if (historyControl == null || previousActive == null)
		{
			applySelection(monster, previousActive != null);
			return;
		}
		if (previousActive.getId() == monster.getId())
		{
			return; // already the active result
		}
		historyControl.execute(new com.loadoutlab.command.Command()
		{
			@Override
			public boolean apply()
			{
				applySelection(monster, false);
				return true;
			}

			@Override
			public boolean revert()
			{
				removeFromPage(monster.getId(), previousActive);
				return true;
			}

			@Override
			public String getDescription()
			{
				return "+ vs " + monster.label();
			}
		});
	}

	/** Close a result (the X or a step revert): drop the entry; if it was
	 * active, the given (or last remaining) entry's monster takes over. */
	private void removeFromPage(int monsterId, MonsterStats nextActive)
	{
		// Single-mob entries only: a roster containing this mob is its own
		// result and must survive a namesake's removal (addToView's revert
		// path; the X uses the entry-exact overload below).
		page.removeIf(e -> e.mobs.size() == 1 && e.hasMob(monsterId));
		removeFallout(nextActive);
	}

	/** Remove the EXACT entry (the X path) - a roster's close must remove
	 * the roster itself, never a namesake single-mob result. */
	private void removeFromPage(ResultEntry entry, MonsterStats nextActive)
	{
		page.remove(entry);
		removeFallout(nextActive);
	}

	private void removeFallout(MonsterStats nextActive)
	{
		if (page.isEmpty())
		{
			clearSelectionInternal();
			return;
		}
		ResultEntry next = nextActive != null ? entryFor(nextActive.getId()) : null;
		if (next == null)
		{
			next = page.get(page.size() - 1);
		}
		setActive(next);
		renderPage();
	}

	/** Switch which existing entry the controls describe - no recompute
	 * (its results are still valid), no new entry. */
	private void setActive(ResultEntry entry)
	{
		active = entry;
		selectedMonster = entry.mob();
		applyActiveMonsterUi(entry.mob());
		syncControlsFromActive();
		refreshNotePanel();
	}

	/** The header X: a recorded step - back re-adds the result (recomputed;
	 * results are not retained through a close). */
	private void closeResult(ResultEntry entry)
	{
		MonsterStats closing = entry.mob();
		MonsterStats fallback = null;
		for (ResultEntry e : page)
		{
			if (e != entry)
			{
				fallback = e.mob(); // the last other entry wins below anyway
			}
		}
		MonsterStats nextActive = selectedMonster != null
			&& selectedMonster.getId() == closing.getId() ? fallback : selectedMonster;
		if (historyControl == null)
		{
			removeFromPage(entry, nextActive);
			return;
		}
		final int index = page.indexOf(entry);
		historyControl.execute(new com.loadoutlab.command.Command()
		{
			@Override
			public boolean apply()
			{
				removeFromPage(entry, nextActive);
				return true;
			}

			@Override
			public boolean revert()
			{
				// Re-insert the SAME entry where it was - parameters and
				// results intact, no recompute needed.
				page.add(Math.min(Math.max(0, index), page.size()), entry);
				hadSelection = true;
				setActive(entry);
				renderPage();
				if (entry.results == null)
				{
					computeEntry(entry);
				}
				return true;
			}

			@Override
			public String getDescription()
			{
				return "Close - " + closing.getName();
			}
		});
	}

	/** The wilderness tradeable cap, or -1 when the mode is off/hidden. */
	private int riskCap(ResultEntry entry)
	{
		if (!effectiveWilderness(entry) || !displayOptions.wildyRisk
			|| entry.riskCap == null || entry.riskCap.trim().isEmpty())
		{
			return -1;
		}
		return entry.protectItem ? 4 : 3;
	}

	/** Recompute only when the parsed budget actually changed. */
	private void budgetEdited()
	{
		int parsed = parsedBudgetGp();
		if (parsed == lastBudgetGp)
		{
			return;
		}
		String oldText = lastBudgetText;
		String newText = upgradeBudget.getText();
		lastBudgetGp = parsed;
		lastBudgetText = newText;
		if (!replayingHistory)
		{
			recordStep(newText == null || newText.isEmpty()
					? "Upgrade budget cleared" : "Upgrade budget " + newText,
				() -> setBudgetTo(newText), () -> setBudgetTo(oldText));
		}
		recompute();
	}

	private void riskCapEdited()
	{
		int parsed = parsedBudgetGp(riskCapField.getText());
		String newText = riskCapField.getText() == null ? "" : riskCapField.getText();
		if (parsed == lastRiskGp && newText.equals(lastRiskText))
		{
			return;
		}
		String oldText = lastRiskText;
		lastRiskGp = parsed;
		lastRiskText = newText;
		if (!replayingHistory)
		{
			recordStep(newText.isEmpty() ? "Risk cap cleared" : "Risk cap " + newText,
				() -> setRiskCapTo(newText), () -> setRiskCapTo(oldText));
		}
		recompute();
	}

	private void setRiskCapTo(String text)
	{
		if (riskCapField.getText().equals(text))
		{
			return;
		}
		replayingHistory = true;
		try
		{
			riskCapField.setText(text);
			riskCapEdited();
		}
		finally
		{
			replayingHistory = false;
		}
	}

	private void setBudgetTo(String text)
	{
		if (upgradeBudget.getText().equals(text))
		{
			return;
		}
		replayingHistory = true;
		try
		{
			upgradeBudget.setText(text);
			budgetEdited();
		}
		finally
		{
			replayingHistory = false;
		}
	}

	/**
	 * "750k" / "1m" / "2.5b" / "1000000" -> gp; "-" -> max (clamped to
	 * 2b; the optimizer saturates cost sums); empty/junk -> 0 (off).
	 */
	private int parsedBudgetGp()
	{
		return parsedBudgetGp(upgradeBudget.getText());
	}

	private int parsedBudgetGp(String text)
	{
		// Hidden control -> no budget (owned gear only), so a hidden field
		// cannot keep buying upgrades the user can no longer see or clear.
		if (!displayOptions.upgradeBudget)
		{
			return 0;
		}
		String raw = text == null ? "" : text.trim().toLowerCase();
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

	private String spellbookLock(ResultEntry entry)
	{
		// Hidden control -> no lock (its combo lives only on the magic card's
		// spell row, which displayOptions.spellControls can remove). Matches
		// riskCap() neutralizing when the wildy controls are hidden.
		if (!displayOptions.spellControls)
		{
			return "";
		}
		return entry.spellbookIndex <= 0 ? ""
			: spellbook.getItemAt(entry.spellbookIndex).toLowerCase();
	}

	/** Test seams (package-private): controls and state for history tests. */
	JComboBox<String> optimizeModeForTest()
	{
		return optimizeMode;
	}

	void clearSelectionForTest()
	{
		clearSelection();
	}

	int pageSizeForTest()
	{
		return page.size();
	}

	void closeActiveResultForTest()
	{
		if (active != null)
		{
			closeResult(active);
		}
	}

	MonsterStats selectedMonsterForTest()
	{
		return selectedMonster;
	}

	/** Wire the plugin's undo/redo stack in. Called once after construction. */
	public void setHistoryControl(HistoryControl control)
	{
		this.historyControl = control;
		refreshHistoryButtons();
	}

	/** Sync the header buttons' enabled state and peek tooltips. */
	public void refreshHistoryButtons()
	{
		if (undoButton == null)
		{
			return;
		}
		boolean canUndo = historyControl != null && historyControl.canUndo();
		boolean canRedo = historyControl != null && historyControl.canRedo();
		undoButton.setEnabled(canUndo);
		redoButton.setEnabled(canRedo);
		undoButton.setToolTipText(canUndo ? "Back: " + historyControl.undoLabel() : "Nothing to go back to");
		redoButton.setToolTipText(canRedo ? "Forward: " + historyControl.redoLabel() : "Nothing to go forward to");
	}

	/** After an undo/redo any store may have changed - refresh every
	 * affordance that renders store state, then recompute. Each refresher
	 * is null-guarded for the no-monster-selected case. */
	private void refreshAfterHistory()
	{
		refreshExclusionsLabel();
		refreshStoredLabel();
		refreshNotePanel();
		refreshHistoryButtons();
		recompute();
	}

	private void refreshExclusionsLabel()
	{
		refreshCountChips();
	}

	/** The -N / +N chips above the search bar: red exclusions, green
	 * dreams; zero counts render muted so the entry points stay visible. */
	private void refreshCountChips()
	{
		int excluded = exclusionView.snapshot().size();
		excludeCountChip.setText("-" + excluded);
		excludeCountChip.setForeground(excluded > 0
			? new Color(220, 120, 120) : new Color(140, 110, 110));
		excludeCountChip.setBorder(new RoundedBorder(excluded > 0
			? new Color(170, 90, 90) : ColorScheme.MEDIUM_GRAY_COLOR, 2, 22));
		int dreams = dreamView.snapshot().size();
		dreamCountChip.setText("+" + dreams);
		dreamCountChip.setForeground(dreams > 0
			? new Color(130, 200, 130) : new Color(110, 140, 110));
		dreamCountChip.setBorder(new RoundedBorder(dreams > 0
			? new Color(95, 160, 95) : ColorScheme.MEDIUM_GRAY_COLOR, 2, 22));
		// The grey member of the trio (field spec 2026-07-20): the always-
		// filter count; the menu also edits the supply defaults.
		int filtered = globalFilters == null ? 0 : globalFilters.alwaysFiltered().size();
		filterCountChip.setText("~" + filtered);
		filterCountChip.setForeground(filtered > 0
			? new Color(190, 190, 190) : new Color(130, 130, 130));
		filterCountChip.setBorder(new RoundedBorder(filtered > 0
			? new Color(150, 150, 150) : ColorScheme.MEDIUM_GRAY_COLOR, 2, 22));
	}

	/** The dream chip's menu: each dream un-dreamable, plus the add entry. */
	private void showDreamsMenu(MouseEvent e)
	{
		JPopupMenu menu = new JPopupMenu();
		List<GearItem> dreamGear = new ArrayList<>();
		for (int id : dreamView.snapshot())
		{
			GearItem gear = data.getGear(id);
			if (gear != null)
			{
				dreamGear.add(gear);
			}
		}
		dreamGear.sort(Comparator.comparing(GearItem::label));
		for (GearItem gear : dreamGear)
		{
			menuItem(menu, "Stop simming " + gear.label(), ev ->
			{
				dreamToggle.toggle(gear.getId());
				refreshCountChips();
				recompute();
			});
		}
		if (!dreamGear.isEmpty())
		{
			menu.addSeparator();
		}
		menuItem(menu, "Sim an item (consider as owned)...", ev -> showAddDreamDialog());
		menu.show((Component) e.getSource(), 0, ((Component) e.getSource()).getHeight());
	}

	private void showExclusionsMenu(MouseEvent e)
	{
		JPopupMenu menu = new JPopupMenu();
		for (Integer id : exclusionView.snapshot())
		{
			GearItem item = data.getGear(id);
			String label = item == null ? ("item " + id) : item.label();
			menuItem(menu, "Allow again: " + label, a ->
			{
				exclusionToggle.toggle(id);
				refreshExclusionsLabel();
				recompute();
			});
		}
		if (menu.getComponentCount() > 0)
		{
			menu.addSeparator();
		}
		menuItem(menu, "Exclude an item (search)...", a -> showAddExclusionDialog());
		// Anchor to the CLICKED component - the old label left the layout
		// when the -N chip replaced it (field bug: dead click; show() on a
		// non-displayable component throws).
		Component source = (Component) e.getSource();
		menu.show(source, 0, source.getHeight());
	}

	private void refreshStoredLabel()
	{
		int count = storedView.snapshot().size();
		storedLabel.setText(count == 0 ? "" : "Stored elsewhere: " + count + " (click to manage)");
		storedLabel.setVisible(count > 0);
	}

	/** Legend for the source dots - EXACTLY the sources in the current
	 * results, in palette order; null when nothing has a known source. */
	private JComponent buildSourceLegend()
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
	private static final class SourceDotIcon implements Icon
	{
		private final Color color;

		SourceDotIcon(Color color)
		{
			this.color = color;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
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
			: scope.toLowerCase(Locale.ROOT) + " set";
	}

	/** Filter-scope label: the MOB'S NAME for all-sets ("bank filter -
	 * Venator"), the set name otherwise (field request). */
	private String filterScopeLabel(String scope)
	{
		if (ALL_SETS.equals(scope))
		{
			return selectedMonster == null ? "this mob" : selectedMonster.getName();
		}
		return scope.toLowerCase(Locale.ROOT) + " set";
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
		if (noteCollapsed() && !note.isEmpty())
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
			noteHeader.setText(noteCollapsed() ? "+ Note (click to add)" : "v Note");
			noteHeader.setToolTipText(null);
		}
		noteArea.setVisible(!noteCollapsed());
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
				// Hoisted above the item so the whole construct is one
				// menuItem(...) call; the lambda still captures it.
				com.loadoutlab.data.GearSlot slot = entry.getKey();
				menuItem(menu,
					"Unpin " + label + " (" + scopeLabel(scope) + ")", a ->
				{
					mobProfile.unpin(monsterId, scope, slot);
								recompute();
				});
			}
		}
		for (Map.Entry<String, Map<Integer, String>> scoped
			: mobProfile.allFilterItems(monsterId).entrySet())
		{
			String scope = scoped.getKey();
			for (Map.Entry<Integer, String> entry : scoped.getValue().entrySet())
			{
				// Hoisted above the item (see above) so the construct
				// collapses to a single menuItem(...) call.
				int itemId = entry.getKey();
				menuItem(menu, "Remove filter item " + entry.getValue()
					+ " (" + filterScopeLabel(scope) + ")", a ->
				{
					mobProfile.removeFilterItem(monsterId, scope, itemId);
								reapplyBankViews();
				});
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
				menuItem(menu, "Allow " + label + " again ("
					+ scopeLabel(scope) + ")", a ->
				{
					mobProfile.removeMobExclusion(monsterId, scope, itemId);
								recompute();
				});
			}
		}
		menu.addSeparator();
		menuItem(menu, "Pin an item - all sets (search)...", a -> searchAndPin(ALL_SETS));
		menuItem(menu, "Bank filter - "
			+ filterScopeLabel(ALL_SETS) + " (search)...", a -> searchAndAddFilter(ALL_SETS));
		Component pinSource = (Component) e.getSource();
		menu.show(pinSource, 0, pinSource.getHeight());
	}

	/**
	 * The magic card's spell controls: pin the autocast spell for this
	 * mob (the gear then optimizes around it - "I am casting Wind Bolt"),
	 * with the spellbook lock shown only while the spell is on Auto.
	 */
	private JPanel magicSpellRow(DpsResult best)
	{
		JPanel wrap = new JPanel();
		wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
		wrap.setOpaque(false);
		wrap.setAlignmentX(LEFT_ALIGNMENT);
		String pinned = mobProfile.pinnedSpell(currentMonsterId());
		JComboBox<String> spellPin = new JComboBox<>();
		// The control IS the display: unpinned, its first entry names the
		// spell auto actually resolved to (field spec - the separate spell
		// line is gone).
		String resolved = best == null ? null : best.getSpellName();
		spellPin.addItem(resolved != null && pinned.isEmpty()
			? "Auto: " + resolved
			: resolved == null && pinned.isEmpty() && best != null
				? "Auto: staff built-in"
				: "Spell: Auto (best)");
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
		return wrap;
	}

	/**
	 * Build a menu row, wire its action and append it to the popup, all in
	 * one call. The item is added AT CALL TIME, so call order is menu order
	 * exactly as the hand-written construct/listen/add triples were.
	 */
	private static JMenuItem menuItem(JPopupMenu menu, String text, ActionListener onPick)
	{
		JMenuItem item = new JMenuItem(text);
		item.addActionListener(onPick);
		menu.add(item);
		return item;
	}

	/** JMenu twin of {@link #menuItem(JPopupMenu, String, ActionListener)}. */
	private static JMenuItem menuItem(JMenu menu, String text, ActionListener onPick)
	{
		JMenuItem item = new JMenuItem(text);
		item.addActionListener(onPick);
		menu.add(item);
		return item;
	}

	/** The per-cell pin submenu: pin/unpin the shown item for this set or
	 * all sets, or chatbox-search ANOTHER item into the pin. */
	private JMenu pinSubmenu(GearItem item, com.loadoutlab.data.GearSlot slot,
		CombatStyle style)
	{
		int monsterId = currentMonsterId();
		JMenu pinMenu = new JMenu("Pin " + item.label());
		Map<String, Map<com.loadoutlab.data.GearSlot, Integer>> raw = mobProfile.allPins(monsterId);
		Integer styleScoped = raw.getOrDefault(style.name(), Collections.emptyMap()).get(slot);
		Integer allScoped = raw.getOrDefault(ALL_SETS, Collections.emptyMap()).get(slot);

		// Each item completes the parent "Pin <item>" - "...for the ranged
		// set only" / "...for all sets".
		if (styleScoped == null || styleScoped != item.getId())
		{
			menuItem(pinMenu, "For the " + scopeLabel(style.name()) + " only", a ->
			{
				mobProfile.pin(monsterId, style.name(), slot, item.getId());
						recompute();
			});
		}
		if (allScoped == null || allScoped != item.getId())
		{
			// (all-sets pins keep their name - the mob is implicit)
			menuItem(pinMenu, "For all sets", a ->
			{
				mobProfile.pin(monsterId, ALL_SETS, slot, item.getId());
						recompute();
			});
		}
		if (styleScoped != null)
		{
			GearItem pinned = data.getGear(styleScoped);
			menuItem(pinMenu, "Unpin "
				+ (pinned == null ? "item" : pinned.label())
				+ " (" + style.name().toLowerCase(Locale.ROOT) + " set)", a ->
			{
				mobProfile.unpin(monsterId, style.name(), slot);
						recompute();
			});
		}
		if (allScoped != null)
		{
			GearItem pinned = data.getGear(allScoped);
			menuItem(pinMenu, "Unpin "
				+ (pinned == null ? "item" : pinned.label()) + " (all sets)", a ->
			{
				mobProfile.unpin(monsterId, ALL_SETS, slot);
						recompute();
			});
		}
		pinMenu.addSeparator();
		// Pin a DIFFERENT item into this slot (chatbox search), scoped.
		menuItem(pinMenu, "Pin a different item (for the "
			+ scopeLabel(style.name()) + ")...", a -> searchAndPin(style.name()));
		menuItem(pinMenu, "Pin a different item (for all sets)...", a -> searchAndPin(ALL_SETS));
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
				reapplyBankViews();
		});
	}

	/**
	 * Re-apply an active Show/Filter after the profile's filter items
	 * change: clear the change guards and re-render - the styleCard apply
	 * hook rebuilds the id sets from the viewed style (never an optimizer
	 * recompute; filter items do not affect the math).
	 */
	private void reapplyBankViews()
	{
		lastHighlightIds = null;
		lastFilterIds = null;
		renderPage();
	}

	/**
	 * The bank views FOLLOW the viewed style (field spec 2026-07-17):
	 * toggled once, Show/Filter stay on across melee/ranged/magic and
	 * Yours|BiS flips, retargeting to the newly viewed set. Null best
	 * clears the displays but keeps the toggles. Guarded by id-set
	 * equality so renders do not spam the bank APIs.
	 */
	private void applyBankViews(CombatStyle style, DpsResult best, GearItem specWeapon)
	{
		Set<Integer> highlightIds = null;
		Set<Integer> filterIds = null;
		int[] filterLayout = null;
		if (best != null)
		{
			// The persistent trip supplies: every dose id joins the filter
			// and highlight so a 2-dose potion in the bank still matches;
			// the LAYOUT places only the display id (ids[0]) - the bank-tag
			// variant matcher draws whatever dose the bank holds there, and
			// placing ghost doses would render faded placeholders.
			List<TripSupplies.Option> supplies = activeSupplies(active);
			List<Integer> arcaneKit = active == null
				? Collections.emptyList() : arcaneKitFilterIds(active);
			if (bankShowing)
			{
				highlightIds = new HashSet<>();
				for (GearItem item : best.getLoadout().getGear().values())
				{
					if (item != null)
					{
						highlightIds.add(item.getId());
					}
				}
				GearItem dart = loadedDart(best);
				if (dart != null)
				{
					highlightIds.add(dart.getId());
				}
				if (specWeapon != null)
				{
					highlightIds.add(specWeapon.getId());
				}
				// The inventory items are part of this trip's kit too.
				highlightIds.addAll(inventoryIds(style));
				highlightIds.addAll(mobProfile.filterItems(currentMonsterId(), style));
				if (globalFilters != null)
				{
					highlightIds.addAll(globalFilters.alwaysFiltered().keySet());
				}
				for (TripSupplies.Option supply : supplies)
				{
					for (int id : supply.ids)
					{
						highlightIds.add(id);
					}
				}
				highlightIds.addAll(arcaneKit);
			}
			if (bankFiltering)
			{
				filterIds = new HashSet<>(setItemIds(best, specWeapon, loadedDart(best)));
				filterIds.addAll(inventoryIds(style));
				// The mob profile's supplies (food, antidotes...) join the
				// filtered bank view - they are part of THIS trip.
				filterIds.addAll(mobProfile.filterItems(currentMonsterId(), style));
				// The global always-filter list (teleport capes...) joins
				// every filtered view - the grey trio's global level.
				if (globalFilters != null)
				{
					filterIds.addAll(globalFilters.alwaysFiltered().keySet());
				}
				for (TripSupplies.Option supply : supplies)
				{
					for (int id : supply.ids)
					{
						filterIds.add(id);
					}
				}
				filterIds.addAll(arcaneKit);
				// Arrange the set in the bank like the in-game equipment +
				// inventory tabs. Pass the full membership so EVERY filtered
				// item is placed (an unplaced member would be shoved into the
				// cross's blank corners by the bank-tag layout) - minus the
				// supplies' non-display dose ids (ghost placeholders).
				// Utility supplies (spellbook capes - fight-relevant but
				// neither worn nor carried) get the layout's THIRD strip.
				Set<Integer> layoutMembers = new HashSet<>(filterIds);
				List<Integer> utilityIds = new ArrayList<>();
				for (TripSupplies.Option supply : supplies)
				{
					for (int i = 1; i < supply.ids.length; i++)
					{
						layoutMembers.remove(supply.ids[i]);
					}
					if (supply.utility)
					{
						layoutMembers.remove(supply.ids[0]);
						utilityIds.add(supply.ids[0]);
					}
				}
				// The assumption runes are staging gear: they ride the
				// utility strip beside the cape, not the inventory block
				// (the pouch carries them on the trip itself).
				for (int runeId : arcaneRuneIds(active))
				{
					if (layoutMembers.remove(runeId))
					{
						utilityIds.add(runeId);
					}
				}
				filterLayout = buildBankLayout(style, best, specWeapon,
					layoutMembers, utilityIds);
			}
		}
		if (!Objects.equals(highlightIds, lastHighlightIds))
		{
			lastHighlightIds = highlightIds;
			bankHighlighter.highlight(highlightIds);
		}
		if (!Objects.equals(filterIds, lastFilterIds))
		{
			lastFilterIds = filterIds;
			bankFilter.filter(filterIds, filterLayout);
		}
	}

	/** The set's bank-tag layout: the equipment cross (CLASSIC_ORDER on the
	 * 8-wide bank grid, cols 0-2, spec weapon in its slot) and the carried
	 * inventory 4-wide below a gap row, mirroring the in-game equipment and
	 * inventory tabs. Positions RuneLite doesn't fill stay -1 (blank). The
	 * matcher maps owned variants; unowned pieces render as faded placeholders. */
	private int[] buildBankLayout(CombatStyle style, DpsResult best, GearItem specWeapon,
		Set<Integer> members, List<Integer> utilityIds)
	{
		int specId = specWeapon != null ? specWeapon.getId() : -1;
		Map<Integer, Integer> place = new LinkedHashMap<>();
		Set<Integer> crossIds = new HashSet<>();
		for (int i = 0; i < CLASSIC_ORDER.length; i++)
		{
			int pos = (i / 3) * 8 + (i % 3);
			if (i == CLASSIC_SPEC_INDEX)
			{
				if (specId != -1)
				{
					place.put(pos, specId);
					crossIds.add(specId);
				}
			}
			else if (CLASSIC_ORDER[i] != null)
			{
				GearItem worn = best.getLoadout().get(CLASSIC_ORDER[i]);
				if (worn == null && CLASSIC_ORDER[i] == GearSlot.AMMO)
				{
					worn = loadedDart(best);
				}
				if (worn != null)
				{
					place.put(pos, worn.getId());
					crossIds.add(worn.getId());
				}
			}
		}
		// The inventory grid rides BESIDE the cross (field spec 2026-07-20:
		// the bank view is a fixed 8 wide, so cols 4-7 are free column
		// space): 4-wide from the top row, col 3 the gutter. It holds EVERY
		// remaining tag member: carried swaps first (bench order), then the
		// assumed consumables and the mob's supplies. Laying out all of them
		// is deliberate - an unplaced member gets shoved into the cross's
		// blank corners by RuneLite's bank-tag layout.
		LinkedHashSet<Integer> inv = new LinkedHashSet<>(orderedInventoryIds(style, specId));
		if (members != null)
		{
			inv.addAll(members);
		}
		inv.removeAll(crossIds);
		int k = 0;
		for (int id : inv)
		{
			place.put((k / 4) * 8 + 4 + (k % 4), id);
			k++;
		}
		// The THIRD strip (field spec 2026-07-20): fight-relevant gear that
		// is neither worn nor carried (spellbook capes), one blank row below
		// whichever of the cross / inventory reaches lower.
		if (utilityIds != null && !utilityIds.isEmpty())
		{
			int invBottom = k > 0 ? (k - 1) / 4 : 0;
			int stripRow = Math.max(4, invBottom) + 2;
			int u = 0;
			for (int id : utilityIds)
			{
				place.put((stripRow + u / 8) * 8 + (u % 8), id);
				u++;
			}
		}
		int maxPos = 0;
		for (int pos : place.keySet())
		{
			maxPos = Math.max(maxPos, pos);
		}
		int[] arr = new int[maxPos + 1];
		Arrays.fill(arr, -1);
		for (Map.Entry<Integer, Integer> e : place.entrySet())
		{
			arr[e.getKey()] = e.getValue();
		}
		return arr;
	}

	/** The carried inventory ids for the layout, in bench order then
	 * consumables, skipping the spec weapon (it sits in the equipment cross). */
	private List<Integer> orderedInventoryIds(CombatStyle style, int excludeId)
	{
		List<Integer> ids = new ArrayList<>();
		if (active == null || active.results == null)
		{
			return ids;
		}
		StyleResult r = active.results.get(style);
		if (r == null)
		{
			return ids;
		}
		for (GearItem item : active.viewingBis ? r.gameBench : r.bench)
		{
			if (item.getId() != excludeId)
			{
				ids.add(item.getId());
			}
		}
		ids.addAll(consumableIds(active, r, active.viewingBis));
		return ids;
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
			menuItem(menu, "No longer stored elsewhere: " + label, a ->
			{
				storedToggle.toggle(id);
				refreshStoredLabel();
				recompute();
			});
		}
		menu.addSeparator();
		menuItem(menu, "Add a stored-elsewhere item...", a -> showAddStoredDialog());
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

	/** Chatbox item search -> exclude any item from every suggestion
	 * (the chip's add path - right-clicking a suggested cell still works
	 * for items already on screen). */
	private void showAddExclusionDialog()
	{
		itemSearch.search("Exclude an item", (itemId, name) ->
		{
			GearItem gear = data.getGear(itemId);
			if (gear == null)
			{
				JOptionPane.showMessageDialog(this,
					name + " is not combat gear in the dataset - only equipment"
						+ " joins the loadout search.",
					"Exclude an item", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			if (!exclusionView.snapshot().contains(gear.getId()))
			{
				exclusionToggle.toggle(gear.getId());
			}
			refreshExclusionsLabel();
			recompute();
		});
	}

	/** Chatbox item search -> dream an unowned item into the owned pool
	 * (same green-border language as the right-click path). */
	private void showAddDreamDialog()
	{
		itemSearch.search("Sim an item (counts as owned)", (itemId, name) ->
		{
			GearItem gear = data.getGear(itemId);
			if (gear == null)
			{
				JOptionPane.showMessageDialog(this,
					name + " is not combat gear in the dataset - only equipment"
						+ " affects the loadout search.",
					"Sim an item", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			if (ownedCheck.owns(gear.getId()))
			{
				JOptionPane.showMessageDialog(this,
					"You already own " + gear.label() + " - no sim needed.",
					"Sim an item", JOptionPane.INFORMATION_MESSAGE);
				return;
			}
			if (!dreamView.snapshot().contains(gear.getId()))
			{
				dreamToggle.toggle(gear.getId());
			}
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
					JMenu excludeMenu = new JMenu("Exclude " + item.label());
					menuItem(excludeMenu, "All mobs", a ->
					{
						exclusionToggle.toggle(item.getId());
						refreshExclusionsLabel();
						recompute();
					});
					if (selectedMonster != null)
					{
						int monsterId = currentMonsterId();
						menuItem(excludeMenu, "Vs " + selectedMonster.getName() + " (all sets)", a ->
						{
							mobProfile.excludeForMob(monsterId, ALL_SETS, item.getId());
												recompute();
						});
						if (pinStyle != null)
						{
							menuItem(excludeMenu, "Vs " + selectedMonster.getName()
								+ " (" + scopeLabel(pinStyle.name()) + " only)", a ->
							{
								mobProfile.excludeForMob(monsterId, pinStyle.name(), item.getId());
														recompute();
							});
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
						menuItem(menu, flagged
							? "Allow " + item.label() + " unprotected again"
							: "Only bring " + item.label() + " if protected on death", a ->
						{
							protectOnlyToggle.toggle(item.getId());
							recompute();
						});
					}
					// Unowned items can be dreamed into the owned pool
					// (and undreamed).
					boolean stored = storedView.snapshot().contains(item.getId());
					if (!ownedCheck.owns(item.getId()))
					{
						boolean dreamed = dreamView.snapshot().contains(item.getId());
						menuItem(menu, dreamed
							? "Stop simming " + item.label()
							: "Sim: consider " + item.label() + " as owned", a ->
						{
							dreamToggle.toggle(item.getId());
							recompute();
						});
					}
					// Stored elsewhere: STASH, POH costume room, UIM cold or
					// nest storage - genuinely owned, just invisible to the
					// ledger. Once marked, owns() is true, so the un-mark
					// entry is what keeps the state reachable.
					if (stored || !ownedCheck.owns(item.getId()))
					{
						menuItem(menu, stored
							? "No longer stored elsewhere: " + item.label()
							: "Stored elsewhere: count " + item.label() + " as owned", a ->
						{
							storedToggle.toggle(item.getId());
							refreshStoredLabel();
							recompute();
						});
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

	/**
	 * Shared shape for the square painted icons: they all carry a single
	 * pixel size used as both width and height. Subclasses keep only their
	 * paintIcon body. Deliberately sets NO rendering hints - antialiasing
	 * stays exactly where each subclass sets it today, because some painted
	 * icons in this file draw aliased on purpose.
	 */
	private abstract static class SizedIcon implements Icon
	{
		/** Icon edge length in pixels; readable by subclass paint code. */
		protected final int size;

		SizedIcon(int size)
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
	}

	/** "No prayer helps" mark: a prohibition sign (circle + slash), painted so
	 * it inherits the incoming line's colour (glyphs tofu on macOS Tahoe). */
	private static final class NoPrayerIcon extends SizedIcon
	{
		NoPrayerIcon(int size)
		{
			super(size);
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

	/** Curved back/forward arrow, painted (Swing glyphs tofu on Tahoe): a
	 * 3/4 arc opening downward with an arrowhead at the top - mirrored
	 * (head upper-left) for back, plain (head upper-right) for forward.
	 * Greys out with the button's enabled state. */
	private static final class UndoArrowIcon implements Icon
	{
		private final int size;
		private final boolean mirrored;

		UndoArrowIcon(int size, boolean mirrored)
		{
			this.size = size;
			this.mirrored = mirrored;
		}

		@Override
		public int getIconWidth()
		{
			return size + 2;
		}

		@Override
		public int getIconHeight()
		{
			return size + 2;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
				Color color = c.isEnabled() ? new Color(180, 180, 220) : new Color(96, 96, 108);
				int cx = x + getIconWidth() / 2;
				int cy = y + getIconHeight() / 2 + 1;
				int r = (size - 2) / 2 + 1;
				if (mirrored)
				{
					g2.translate(2 * cx, 0);
					g2.scale(-1, 1);
				}
				g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.setColor(color);
				g2.drawArc(cx - r, cy - r, r * 2, r * 2, 60, 240);
				// Arrowhead at the arc's start (60 degrees, upper right).
				double a = Math.toRadians(60);
				int hx = (int) Math.round(cx + r * Math.cos(a));
				int hy = (int) Math.round(cy - r * Math.sin(a));
				int hs = Math.max(3, size / 3);
				java.awt.geom.Path2D head = new java.awt.geom.Path2D.Float();
				head.moveTo(hx, hy);
				head.lineTo(hx - hs, hy);
				head.lineTo(hx, hy - hs);
				head.closePath();
				g2.fill(head);
			}
			finally
			{
				g2.dispose();
			}
		}
	}

	/** A small painted X - the close affordance (glyphs tofu on Tahoe). */
	private static final class CloseIcon extends SizedIcon
	{
		CloseIcon(int size)
		{
			super(size);
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				g2.setColor(c.isEnabled() ? new Color(180, 180, 220) : new Color(96, 96, 108));
				g2.drawLine(x + 1, y + 1, x + size - 1, y + size - 1);
				g2.drawLine(x + size - 1, y + 1, x + 1, y + size - 1);
			}
			finally
			{
				g2.dispose();
			}
		}
	}

	/** Three-dots "more options" glyph, painted (Swing glyphs tofu on Tahoe). */
	private static final class DotsIcon extends SizedIcon
	{
		DotsIcon(int size)
		{
			super(size);
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
	private static final class ReloadIcon extends SizedIcon
	{
		ReloadIcon(int size)
		{
			super(size);
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

	/** Parameters changed: every entry on the page is stale - drop results
	 * (a previous answer under new settings reads as this one's), render
	 * the placeholders, and recompute each entry. */
	private void recompute()
	{
		if (selectedMonster == null)
		{
			return;
		}
		syncActiveFromControls();
		for (ResultEntry entry : page)
		{
			entry.results = null;
			// Stale roster bundles must go too: a lens click during the
			// pending window otherwise resurrects pre-recompute numbers.
			entry.perMobResults = null;
		}
		renderPage();
		statusLabel.setText(" ");
		for (ResultEntry entry : page)
		{
			computeEntry(entry);
		}
	}

	/** One entry's compute: every parameter comes from THE ENTRY's own
	 * parameter zone (card anatomy spec) - F2P stays global (a world
	 * fact, not a preference). */
	private void computeEntry(ResultEntry entry)
	{
		if (entry.mobs.size() > 1)
		{
			computeHook.computeRoster(new ArrayList<>(entry.mobs),
				f2pOnly.isSelected(), entry.onSlayerTask,
				effectiveWilderness(entry), spellbookLock(entry), riskCap(entry),
				parsedBudgetGp(entry.riskCap),
				entry.antifireMode == 2 && DragonfireRules.breathesFire(entry.mob()),
				entry.deathCharge ? (deathChargeUpgraded ? 2 : 1) : 0,
			copyPicks(entry.boostPicks), copyPicks(entry.prayerPicks),
				parsedBudgetGp(entry.upgradeBudget),
				com.loadoutlab.optimizer.OptimizerService.OptimizeMode.values()[entry.optimizeMode],
				entry.maxSwaps, entry.raidBoost,
				() -> statusLabel.setText(" "));
			return;
		}
		computeHook.compute(entry.mob(), f2pOnly.isSelected(), entry.onSlayerTask,
			effectiveWilderness(entry), spellbookLock(entry), riskCap(entry),
			parsedBudgetGp(entry.riskCap),
			entry.antifireMode == 2 && DragonfireRules.breathesFire(entry.mob()),
			entry.deathCharge ? (deathChargeUpgraded ? 2 : 1) : 0,
			copyPicks(entry.boostPicks), copyPicks(entry.prayerPicks),
			parsedBudgetGp(entry.upgradeBudget),
			com.loadoutlab.optimizer.OptimizerService.OptimizeMode.values()[entry.optimizeMode],
			entry.maxSwaps, entry.raidBoost,
			() -> statusLabel.setText(" "));
	}

	/** Account or profile switched: nothing on screen may survive. */
	public void resetForIdentityChange()
	{
		bankShowing = false;
		bankFiltering = false;
		lastHighlightIds = null;
		lastFilterIds = null;
		clearSelectionInternal();
		refreshExclusionsLabel();
		refreshStoredLabel();
		refreshNotePanel();
	}

	/** The clear button: a recorded step whose back restores the mob. */
	private void clearSelection()
	{
		MonsterStats previous = selectedMonster;
		if (historyControl == null || previous == null)
		{
			clearSelectionInternal();
			return;
		}
		final List<ResultEntry> pageBefore = new ArrayList<>(page);
		final ResultEntry activeBefore = active;
		historyControl.execute(new com.loadoutlab.command.Command()
		{
			@Override
			public boolean apply()
			{
				clearSelectionInternal();
				return true;
			}

			@Override
			public boolean revert()
			{
				restorePage(pageBefore, activeBefore);
				return true;
			}

			@Override
			public String getDescription()
			{
				return "Clear - " + previous.getName();
			}
		});
	}

	private void clearSelectionInternal()
	{
		selectedMonster = null;
		page.clear();
		active = null;
		refreshNotePanel();
		resultsPanel.removeAll();
		resultsPanel.revalidate();
		resultsPanel.repaint();
		statusLabel.setText("Search a monster to begin.");
		revalidate();
		searchField.requestFocusInWindow();
	}

	/** Render results (EDT). Called by the plugin once the optimizer returns.
	 * Routes into the matching page entry - a result for a monster no longer
	 * on the page is stale and dropped. */
	public void showResults(MonsterStats monster, Map<CombatStyle, StyleResult> results)
	{
		ResultEntry entry = entryFor(monster.getId());
		if (entry == null)
		{
			return; // stale result for a monster the user moved away from
		}
		if (entry.mobs.size() > 1)
		{
			// A single-mob map must never overwrite a roster's lensed view
			// (it detaches results from perMobResults and FREEZES the lens
			// - field bug: every form showed the same dps). The roster's
			// own delivery goes through showRosterResults; UI refresh
			// paths just need the repaint.
			renderPage();
			return;
		}
		entry.results = results;
		renderPage();
	}

	private ResultEntry entryFor(int monsterId)
	{
		// A mob can sit alone AND inside a roster: single-mob results must
		// deliver to the single-mob entry, never overwrite the roster's map.
		ResultEntry fallback = null;
		for (ResultEntry entry : page)
		{
			if (entry.hasMob(monsterId))
			{
				if (entry.mobs.size() == 1)
				{
					return entry;
				}
				if (fallback == null)
				{
					fallback = entry;
				}
			}
		}
		return fallback;
	}

	/** Roster results (EDT): routed by exact mob-id list match. */
	public void showRosterResults(List<MonsterStats> mobs,
		List<Map<CombatStyle, StyleResult>> perMob)
	{
		showRosterResults(mobs, perMob, null);
	}

	public void showRosterResults(List<MonsterStats> mobs,
		List<Map<CombatStyle, StyleResult>> perMob,
		OptimizerService.KitCurve curve)
	{
		ResultEntry entry = entryForRoster(mobs);
		if (entry == null || perMob == null || perMob.isEmpty())
		{
			return; // stale roster - the page moved on
		}
		entry.kitCurve = curve;
		entry.perMobResults = perMob;
		entry.lensIndex = Math.max(0, Math.min(entry.lensIndex, perMob.size() - 1));
		entry.results = perMob.get(entry.lensIndex);
		// Land on the RECOMMENDED tab (field spec 2026-07-17): the kit can
		// answer the lensed mob in any style - never open on a tab where
		// it is immune. One shared set per style is no longer the rule
		// once swaps are involved.
		DpsResult rowBest = mobRowResult(entry, entry.lensIndex,
			entry.selectedTab, entry.viewingBis);
		if (rowBest != null)
		{
			entry.selectedTab = resultStyle(rowBest, entry.selectedTab);
		}
		renderPage();
	}

	private ResultEntry entryForRoster(List<MonsterStats> mobs)
	{
		outer:
		for (ResultEntry entry : page)
		{
			if (entry.mobs.size() != mobs.size())
			{
				continue;
			}
			for (int i = 0; i < mobs.size(); i++)
			{
				if (entry.mobs.get(i).getId() != mobs.get(i).getId())
				{
					continue outer;
				}
			}
			return entry;
		}
		return null;
	}

	/** The mob-list LENS: flip whose numbers show - the shared set never
	 * changes (card anatomy #1). View state, like the style tabs. */
	private void lensTo(ResultEntry entry, int index)
	{
		if (index < 0 || index >= entry.mobs.size() || index == entry.lensIndex)
		{
			return;
		}
		entry.lensIndex = index;
		if (entry.perMobResults != null && index < entry.perMobResults.size())
		{
			entry.results = entry.perMobResults.get(index);
		}
		// Follow the row (field spec 2026-07-17): the kit can answer this
		// mob in another style - the viewed tab switches with it, on the
		// Yours and BiS side alike.
		DpsResult rowBest = mobRowResult(entry, index, entry.selectedTab, entry.viewingBis);
		if (rowBest != null)
		{
			entry.selectedTab = resultStyle(rowBest, entry.selectedTab);
		}
		if (entry == active)
		{
			selectedMonster = entry.mob();
			applyActiveMonsterUi(entry.mob());
			setNoteCollapsed(mobProfile.note(entry.mob().getId()).isEmpty());
			refreshNotePanel();
			}
		renderPage();
	}

	/** Rebuild the results area from the page: one card per entry - chrome
	 * (header with fold + close) only appears once the page holds more than
	 * one result, so the single view stays pixel-identical to 0.2.4.
	 * Entries still computing render a placeholder (mascot on the first). */
	private void renderPage()
	{
		// Every path that changes dreams/exclusions ends in a render.
		refreshCountChips();
		resultsPanel.removeAll();
		// Chrome on every result (field spec 2026-07-17): the fold/title
		// row now carries the per-result refresh + close, replacing the
		// old global reload/clear buttons beside the monster name.
		boolean chrome = true;
		boolean mascotShown = false;
		for (ResultEntry entry : page)
		{
			if (chrome)
			{
				resultsPanel.add(resultChrome(entry));
				resultsPanel.add(Box.createVerticalStrut(4));
			}
			if (chrome && entry.folded)
			{
				resultsPanel.add(Box.createVerticalStrut(4));
				continue;
			}
			if (entry.results != null)
			{
				resultsPanel.add(resultCard(entry));
			}
			else
			{
				resultsPanel.add(pendingCard(entry, !mascotShown));
				mascotShown = true;
			}
			if (chrome)
			{
				resultsPanel.add(Box.createVerticalStrut(8));
			}
		}
		if (selectedMonster != null)
		{
			// No summary line under the results (field spec 2026-07-17):
			// the cards carry their own identity now. The label keeps its
			// empty-state prompt ("Search a monster to begin.") only.
			statusLabel.setText(" ");
		}
		// Revalidate the PANEL, not just the results column: our new preferred
		// height has to reach RuneLite's outer scroll pane so the sidebar grows
		// to fit an expanded card (or shrinks back when one collapses).
		resultsPanel.revalidate();
		revalidate();
		repaint();
	}

	/** The per-set "..." menu (pins, bank filters, spellbook lock), now
	 * riding the result chrome beside reload/close (field spec). Scopes
	 * follow the SELECTED style tab. */
	private JButton setMenuButton(ResultEntry entry)
	{
		CombatStyle style = entry.selectedTab != null ? entry.selectedTab
			: entry.results == null && entry.perMobResults == null
				? CombatStyle.MELEE : defaultTab(entry);
		JButton setMenu = new JButton(new DotsIcon(11));
		setMenu.setToolTipText("Pins and bank-filter items for this set");
		setMenu.setMargin(new Insets(1, 5, 1, 5));
		setMenu.addActionListener(e ->
		{
			JPopupMenu menu = new JPopupMenu();
			// Compact labels (field spec - the long forms truncated in the
			// popup): the scope pair is "<style> set" vs "this result".
			menuItem(menu, "Pin item - " + scopeLabel(style.name()), ev -> searchAndPin(style.name()));
			menuItem(menu, "Pin item - this result", ev -> searchAndPin(ALL_SETS));
			menuItem(menu, "Bank filter - " + scopeLabel(style.name()), ev -> searchAndAddFilter(style.name()));
			menuItem(menu, "Bank filter - this result", ev -> searchAndAddFilter(ALL_SETS));
			if (style == CombatStyle.MAGIC && displayOptions.spellControls)
			{
				// The spellbook lock: the submenu drives the combo, which
				// records + recomputes.
				JMenu bookMenu = new JMenu("Spellbook lock");
				for (int b = 0; b < spellbook.getItemCount(); b++)
				{
					final int index = b;
					menuItem(bookMenu, (index == spellbook.getSelectedIndex()
						? "[x] " : "") + spellbook.getItemAt(b), ev -> spellbook.setSelectedIndex(index));
				}
				menu.add(bookMenu);
			}
			menu.show(setMenu, 0, setMenu.getHeight());
		});
		return setMenu;
	}

	/** A result's header row: fold chevron + "vs <name>" (+ one-line best
	 * summary when folded) filling the row, close (X) right. The title sits
	 * in CENTER so it ellipsizes instead of running under the X. The ACTIVE
	 * result (the one the global search affordances act on) renders white; the others
	 * muted. Only rendered on multi-result pages. */
	private JComponent resultChrome(ResultEntry entry)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		String summary = "";
		if (entry.folded && entry.results != null)
		{
			double best = 0;
			for (StyleResult r : entry.results.values())
			{
				if (r != null && r.owned != null && !r.owned.isEmpty())
				{
					best = Math.max(best, r.owned.get(0).getDps());
				}
			}
			summary = best > 0 ? String.format(" - %.2f DPS", best) : " - no set";
		}
		boolean isActive = entry == active;
		// Expanded cards drop the redundant name (the mob list right below
		// carries it - field spec); folded cards keep the one-line summary,
		// otherwise a folded result is unidentifiable.
		JLabel title = new JLabel(entry.folded
			? "> vs " + entry.mob().label()
				+ (entry.mobs.size() > 1 ? " +" + (entry.mobs.size() - 1) : "") + summary
			: "v");
		title.setForeground(isActive ? Color.WHITE : new Color(170, 170, 170));
		title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
		title.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		title.setToolTipText((entry.folded ? "Click to expand" : "Click to fold")
			+ (isActive ? " - the active result" : ""));
		onClick(title, e ->
		{
			entry.folded = !entry.folded;
			renderPage();
		});
		row.add(title, BorderLayout.CENTER);
		JButton reload = new JButton(new ReloadIcon(10));
		reload.setToolTipText("Recompute this result");
		reload.setMargin(new Insets(1, 5, 1, 5));
		reload.addActionListener(e ->
		{
			entry.results = null;
			entry.perMobResults = null;
			renderPage();
			statusLabel.setText(" ");
			computeEntry(entry);
		});
		JButton close = new JButton(new CloseIcon(9));
		close.setToolTipText("Close this result");
		close.setMargin(new Insets(1, 5, 1, 5));
		close.addActionListener(e -> closeResult(entry));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		buttons.setOpaque(false);
		buttons.add(setMenuButton(entry));
		buttons.add(reload);
		buttons.add(close);
		row.add(buttons, BorderLayout.EAST);
		return row;
	}

	/** The still-computing placeholder. The mascot performs only on a
	 * SINGLE-result page (the classic loading experience); on a multi-
	 * result page a pending entry is one compact line so the rendered
	 * results around it stay put. */
	private JComponent pendingCard(ResultEntry entry, boolean withMascot)
	{
		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setOpaque(false);
		column.setAlignmentX(LEFT_ALIGNMENT);
		// The mob list leads (field spec): build or trim the roster while
		// the optimizer runs - each edit supersedes the in-flight compute
		// via the service ticket. The mascot performs BELOW the list.
		column.add(mobLensRows(entry, null, false));
		column.add(paramChipRow(entry));
		// The roster picks today's mood (weighted by season); see MascotRoster.
		if (withMascot && page.size() == 1
			&& displayOptions.loadingAnimation && MascotArt.available())
		{
			Mascot mascot = MascotRoster.pick(java.time.LocalDate.now(), MASCOT_MOOD);
			if (mascot != null)
			{
				column.add(mascot);
			}
		}
		// A caption like the empty-state prompt: centered, italic (the
		// centerRow wrapper dodges the mixed-alignment BoxLayout gotcha).
		JLabel computing = new JLabel("<html>" + (entry.mobs.size() > 1
			? "Optimizing one shared set vs " + entry.mobs.size() + " mobs..."
			: "Optimizing vs " + entry.mob().getName() + "...") + "</html>");
		computing.setForeground(MUTED);
		computing.setFont(computing.getFont().deriveFont(Font.ITALIC));
		computing.setBorder(BorderFactory.createEmptyBorder(page.size() == 1 ? 8 : 2, 0, 0, 0));
		JPanel computingRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		computingRow.setOpaque(false);
		computingRow.setAlignmentX(LEFT_ALIGNMENT);
		computingRow.add(computing);
		computingRow.setMaximumSize(new Dimension(Integer.MAX_VALUE,
			computingRow.getPreferredSize().height));
		column.add(computingRow);
		return column;
	}

	/** The per-result PARAMETER ZONE (card anatomy #2, field spec
	 * 2026-07-17): compact chips between the mob list and the gear that
	 * READ the entry's own settings and DRIVE the hidden legacy controls
	 * - which still own recording (back/forward), write-through, and
	 * recompute - after focusing this entry. Two wrap rows: toggles,
	 * then pick-a-value chips. */
	private JComponent paramChipRow(ResultEntry entry)
	{
		MonsterStats mob = entry.mob();
		JPanel rows = new JPanel();
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setOpaque(false);
		rows.setAlignmentX(LEFT_ALIGNMENT);
		JPanel toggles = chipFlowRow();
		if (SlayerLockedMonsters.isTaskOnly(mob))
		{
			toggles.add(paramChip("On task", true, false,
				"Task-only boss - always on", null));
		}
		else if (mob.isSlayerMonster())
		{
			toggles.add(paramChip("On task", entry.onSlayerTask, true,
				"On task: slayer helmet bonuses apply",
				() -> asActive(entry, slayerTask::doClick)));
		}
		boolean listed = WildernessMonsters.isWilderness(mob);
		boolean exclusive = WildernessMonsters.isExclusive(mob);
		if (exclusive)
		{
			toggles.add(paramChip("Wildy", true, false,
				"Wilderness-exclusive - always in", null));
		}
		else if (listed)
		{
			toggles.add(paramChip("Wildy", entry.inWilderness, true,
				"Fighting inside the Wilderness: wilderness weapons +50%,"
					+ " risk options apply",
				() -> asActive(entry, inWilderness::doClick)));
		}
		boolean wild = (exclusive || (listed && entry.inWilderness)) && displayOptions.wildyRisk;
		if (wild)
		{
			toggles.add(paramChip("Protect item", entry.protectItem, true,
				"Protect Item keeps a 4th item (not while skulled)",
				() -> asActive(entry, protectItem::doClick)));
		}
		// Raid-supplied boost toggle (field spec 2026-07-18): overloads/
		// salts are the raid norm but not a promise - off falls back to
		// the bank's own potions.
		com.loadoutlab.engine.BoostProfile supplied =
			com.loadoutlab.engine.RaidBoosts.suppliedBoost(entry.mobs.get(0));
		for (MonsterStats m : entry.mobs)
		{
			if (com.loadoutlab.engine.RaidBoosts.suppliedBoost(m) != supplied)
			{
				supplied = null;
				break;
			}
		}
		if (supplied != null)
		{
			String suppliedLabel = supplied.toString();
			toggles.add(paramChip(suppliedLabel, entry.raidBoost, true,
				entry.raidBoost
					? "Assuming the raid's " + suppliedLabel + " - click to use your own potions instead"
					: "Using your own potions - click to assume the raid's " + suppliedLabel,
				() -> asActive(entry, () ->
			{
				boolean prev = entry.raidBoost;
				recordStep(prev ? "No raid boost" : "Raid boost",
					() -> setRaidBoost(!prev), () -> setRaidBoost(prev));
				if (historyControl == null)
				{
					setRaidBoost(!prev);
				}
			})));
		}
		boolean fiery = false;
		for (MonsterStats m : entry.mobs)
		{
			if (DragonfireRules.breathesFire(m))
			{
				fiery = true;
				break;
			}
		}
		if (fiery)
		{
			// Dragonfire protection mode (field spec), cycling gear ->
			// regular -> super. Regular keeps the shield REQUIRED (only
			// the combo fully blocks - the honesty rule refuses half
			// protection); its dps effect lands with antifire-aware
			// incoming, the chips admit the potion today. Super frees
			// the shield slot.
			int mode = entry.antifireMode;
			String[] antifireLabels = {"Antifire: gear", "Antifire: regular", "Super antifire"};
			String[] antifireTips = {
				"A dragonfire shield is required in the set; click to also"
					+ " assume a regular antifire (shield + potion = immune)",
				"Regular antifire assumed WITH the required shield - the"
					+ " combo fully blocks dragonfire; click for super"
					+ " antifire (no shield needed)",
				"Super antifire assumed - the shield slot is free; click to"
					+ " require the shield alone again"};
			String[] antifireSteps = {"Require dragonfire shield",
				"Assume regular antifire", "Assume super antifire"};
			toggles.add(paramChip(antifireLabels[mode], mode > 0, true,
				antifireTips[mode],
				() -> asActive(entry, () ->
				{
					int next = (entry.antifireMode + 1) % 3;
					int prev = entry.antifireMode;
					recordStep(antifireSteps[next],
						() -> setAntifireMode(next), () -> setAntifireMode(prev));
					if (historyControl == null)
					{
						setAntifireMode(next);
					}
				})));
		}
		// Thralls / Vengeance (field direction 2026-07-21): display-only
		// dps folds - the chips show only when usable (tier reachable +
		// book of the dead owned; Magic 94 for Veng).
		if (ExtraDps.thrallDps(magicLevel) > 0 && ownedCheck.owns(ExtraDps.BOOK_OF_THE_DEAD))
		{
			toggles.add(paramChip("Thralls", entry.thralls, true,
				entry.thralls
					? "Folding your " + ExtraDps.thrallTier(magicLevel)
						+ " thrall (" + String.format("%.2f", ExtraDps.thrallDps(magicLevel))
						+ " dps, always hits) into the shown numbers - click to exclude"
					: "Fold your " + ExtraDps.thrallTier(magicLevel)
						+ " thrall's dps into the shown numbers (Arceuus + book of the dead)",
				() -> asActive(entry, () ->
				{
					entry.thralls = !entry.thralls;
					renderPage();
				})));
		}
		if (magicLevel >= 80)
		{
			toggles.add(paramChip("D charge", entry.deathCharge, true,
				entry.deathCharge
					? (deathChargeUpgraded
						? "Death Charge assumed, UPGRADED by Yama's rite (two 15%"
							+ " refunds per cast) - feeds the spec model; click to exclude"
						: "Death Charge assumed (Arceuus, 15% spec energy per killing"
							+ " blow, 60s cast) - feeds the spec model; click to exclude")
					: "Assume Death Charge - more special attacks per trip"
						+ " (Arceuus, Magic 80)",
				() -> asActive(entry, () ->
				{
					entry.deathCharge = !entry.deathCharge;
					recompute();
				})));
		}
		// One continuous wrap row for ALL chips (field fix 2026-07-18:
		// the split rows left orphaned chips floating awkwardly).
		JPanel values = toggles;
		String modeText = String.valueOf(optimizeMode.getItemAt(
			Math.min(entry.optimizeMode, optimizeMode.getItemCount() - 1)));
		values.add(paramChip(modeText.replace("Optimize: ", ""),
			entry.optimizeMode > 0, true,
			"Balanced/Tanky trade dps for less damage taken - click to pick",
			() -> pickFromCombo(entry, optimizeMode, "Optimize")));
		if (displayOptions.upgradeBudget)
		{
			String budget = entry.upgradeBudget == null ? "" : entry.upgradeBudget.trim();
			boolean unlimited = budget.equals("-") || budget.equalsIgnoreCase("max");
			JComponent budgetChip = paramChip(
				budget.isEmpty() ? "Budget: off" : unlimited ? "Budget: " : "Budget: " + budget,
				!budget.isEmpty(), true,
				"Buyable-gear budget: 750k, 1m, 1.5b; - sets unlimited;"
					+ " empty = owned gear only",
				() -> editBudget(entry));
			if (unlimited && budgetChip instanceof JLabel)
			{
				// A painted infinity sign - a Unicode glyph risks tofu on
				// macOS Tahoe (the glyph gate would reject it anyway).
				JLabel label = (JLabel) budgetChip;
				label.setIcon(new InfinityIcon(label.getForeground()));
				label.setHorizontalTextPosition(JLabel.LEFT);
				label.setIconTextGap(2);
			}
			values.add(budgetChip);
		}
		if (wild)
		{
			String risk = entry.riskCap == null ? "" : entry.riskCap.trim();
			values.add(paramChip(risk.isEmpty() ? "Risk: off" : "Risk: " + risk,
				!risk.isEmpty(), true,
				"Low-risk mode: keep your 3 most valuable items (4 with"
					+ " Protect Item), everything else must total under this"
					+ " gp cap (25k, 1m...) - empty = unconstrained",
				() -> editRiskCap(entry)));
		}
		int pinCount = 0;
		int lensedId = entry.mob().getId();
		for (Map<com.loadoutlab.data.GearSlot, Integer> scoped
			: mobProfile.allPins(lensedId).values())
		{
			pinCount += scoped.size();
		}
		if (pinCount > 0)
		{
			values.add(pinFilterChip(entry, "Pins: " + pinCount,
				"Pinned items for this mob - click to manage"));
		}
		// The Filters chip retired 2026-07-20: filter items are managed by
		// the card's grey "Filter here" chip (the exclude/sim/filter trio).
		if (values.getComponentCount() > 0)
		{
			rows.add(values);
		}
		// The INVENTORY control is a slider on its OWN row below the
		// chips (field spec 2026-07-18), rosters only - a single mob has
		// nothing to swap between and keeps the default budget of 1.
		if (entry.mobs.size() > 1)
		{
			JPanel invRow = chipFlowRow();
			String invTip = entry.maxSwaps == 0
				? "Inventory: 0 - strictly one worn set, no spec weapon recommended"
				: "Inventory: " + entry.maxSwaps + " - up to " + entry.maxSwaps
					+ " carried item" + (entry.maxSwaps == 1 ? "" : "s")
					+ " including the spec weapon, cross-style included";
			JLabel invLabel = new JLabel("Inventory: " + entry.maxSwaps);
			invLabel.setForeground(entry.maxSwaps != entry.inventoryDefault
				? Color.WHITE : new Color(170, 170, 170));
			invLabel.setFont(invLabel.getFont().deriveFont(
				entry.maxSwaps != entry.inventoryDefault ? Font.BOLD : Font.PLAIN, 11f));
			invLabel.setToolTipText(invTip);
			JSlider invSlider = new JSlider(0, 20, entry.maxSwaps);
			invSlider.setOpaque(false);
			invSlider.setPreferredSize(new Dimension(110, 22));
			invSlider.setToolTipText(invTip);
			invSlider.addChangeListener(ev ->
			{
				invLabel.setText("Inventory: " + invSlider.getValue());
				if (invSlider.getValueIsAdjusting())
				{
					return;
				}
				final int pick = invSlider.getValue();
				asActive(entry, () ->
				{
					entry.inventoryTouched = true;
					int prev = entry.maxSwaps;
					if (pick == prev)
					{
						return;
					}
					recordStep("Inventory " + pick,
						() -> setMaxSwaps(pick), () -> setMaxSwaps(prev));
					if (historyControl == null)
					{
						setMaxSwaps(pick);
					}
				});
			});
			// Label + slider travel as ONE unit so the wrap can never
			// split them across lines (field bug).
			JPanel invGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			invGroup.setOpaque(false);
			invGroup.add(invLabel);
			invGroup.add(invSlider);
			invRow.add(invGroup);
			rows.add(invRow);
		}
		rows.add(Box.createVerticalStrut(4));
		return rows;
	}

	/**
	 * Attach a click-only mouse listener. The handler takes the MouseEvent
	 * because some callers anchor a popup on the click point.
	 */
	private static void onClick(Component target, Consumer<MouseEvent> handler)
	{
		target.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				handler.accept(e);
			}
		});
	}

	/**
	 * The one chip body behind pinFilterChip/paramChip/viewChip/
	 * localCountChip. Every colour, font and border decision stays in the
	 * thin wrappers below - this only assembles the label.
	 *
	 * NOTE: three of the old hand-written chips set the HAND cursor
	 * unconditionally; here it keys off onClick. That is unreachable in
	 * current code - all four wrappers pass a non-null handler whenever
	 * they used to set the cursor - so behaviour is unchanged.
	 */
	private static JLabel chip(String text, boolean opaque, Color foreground, int fontStyle,
		float fontSize, Color borderColor, int padding, int arc, String tooltip,
		Consumer<MouseEvent> onClick)
	{
		JLabel chip = new JLabel(text);
		chip.setOpaque(opaque);
		chip.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		chip.setForeground(foreground);
		chip.setFont(chip.getFont().deriveFont(fontStyle, fontSize));
		chip.setBorder(new RoundedBorder(borderColor, padding, arc));
		chip.setToolTipText(tooltip);
		if (onClick != null)
		{
			chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			onClick(chip, onClick);
		}
		return chip;
	}

	/** A pins/filters count chip: opens the manage menu anchored on the
	 * chip (the retired label's menu, source-anchored). */
	private JComponent pinFilterChip(ResultEntry entry, String text, String tooltip)
	{
		return chip(text, true, Color.WHITE, Font.BOLD, 11f,
			ACCENT, 2, 7, tooltip,
			e -> asActive(entry, () -> showPinnedMenu(e)));
	}

	/** FlowLayout whose preferred height accounts for wrapping at the
	 * current width - a plain FlowLayout reports one-row height, so a
	 * fourth chip silently wrapped into clipped space (field bug: the
	 * Risk chip vanished on wilderness cards). */
	private static final class WrapLayout extends FlowLayout
	{
		WrapLayout(int align, int hgap, int vgap)
		{
			super(align, hgap, vgap);
		}

		@Override
		public Dimension preferredLayoutSize(Container target)
		{
			synchronized (target.getTreeLock())
			{
				// A fresh card has no width yet - falling back to unlimited
				// meant one endless row, clipped after a few chips (field
				// bug: parameters "disappearing" mid-compute). Assume the
				// plugin panel's width until the real one exists.
				int targetWidth = target.getWidth() > 0 ? target.getWidth()
					: (target.getParent() != null && target.getParent().getWidth() > 0
						? target.getParent().getWidth() : 220);
				Insets insets = target.getInsets();
				int maxWidth = targetWidth - insets.left - insets.right - getHgap() * 2;
				int x = 0;
				int rowHeight = 0;
				Dimension dim = new Dimension(0, insets.top + getVgap());
				for (int i = 0; i < target.getComponentCount(); i++)
				{
					Component c = target.getComponent(i);
					if (!c.isVisible())
					{
						continue;
					}
					Dimension d = c.getPreferredSize();
					if (x == 0 || x + getHgap() + d.width <= maxWidth)
					{
						x += (x > 0 ? getHgap() : 0) + d.width;
						rowHeight = Math.max(rowHeight, d.height);
					}
					else
					{
						dim.width = Math.max(dim.width, x);
						dim.height += rowHeight + getVgap();
						x = d.width;
						rowHeight = d.height;
					}
				}
				dim.width = Math.max(dim.width, x) + insets.left + insets.right + getHgap() * 2;
				dim.height += rowHeight + getVgap() + insets.bottom;
				return dim;
			}
		}
	}

	private static JPanel chipFlowRow()
	{
		JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2))
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		return row;
	}

	/** One parameter chip: the Yours|BiS border language - orange edge
	 * when ON, quiet outline when off, muted + inert when forced. */
	private JComponent paramChip(String text, boolean selected,
		boolean enabled, String tooltip, Runnable onClick)
	{
		// A forced (disabled) chip stays inert: no cursor, no listener -
		// expressed here as a null handler.
		return chip(text, true,
			!enabled ? new Color(150, 150, 150)
				: selected ? Color.WHITE : new Color(170, 170, 170),
			selected ? Font.BOLD : Font.PLAIN, 11f,
			selected ? ACCENT : ColorScheme.MEDIUM_GRAY_COLOR, 2, 7, tooltip,
			enabled && onClick != null ? e -> onClick.run() : null);
	}

	/** Chip actions drive the hidden controls for THIS entry: focusing it
	 * first (no recompute) so the write-through, recording, and recompute
	 * listeners aim at the right card. */
	private void asActive(ResultEntry entry, Runnable action)
	{
		if (entry != active)
		{
			setActive(entry);
		}
		action.run();
	}

	private void pickFromCombo(ResultEntry entry, JComboBox<String> combo, String what)
	{
		JPopupMenu menu = new JPopupMenu();
		for (int i = 0; i < combo.getItemCount(); i++)
		{
			final int index = i;
			menuItem(menu, (i == combo.getSelectedIndex() ? "[x] " : "")
				+ combo.getItemAt(i), e -> asActive(entry, () -> combo.setSelectedIndex(index)));
		}
		Point at = getMousePosition();
		menu.show(this, at != null ? at.x : 20, at != null ? at.y : 20);
	}

	private void editRiskCap(ResultEntry entry)
	{
		String current = entry.riskCap == null ? "" : entry.riskCap;
		String edited = (String) JOptionPane.showInputDialog(this,
			"<html>Wilderness risk cap in gp (25k, 1m...; empty ="
				+ " unconstrained).<br>The default for new searches can be"
				+ " changed in the plugin settings panel.</html>",
			"Risk cap", JOptionPane.PLAIN_MESSAGE, null, null, current);
		if (edited != null && !edited.equals(current))
		{
			asActive(entry, () ->
			{
				riskCapField.setText(edited);
				riskCapEdited();
			});
		}
	}

	private void editBudget(ResultEntry entry)
	{
		String current = entry.upgradeBudget == null ? "" : entry.upgradeBudget;
		String edited = (String) JOptionPane.showInputDialog(this,
			"Buyable-gear budget (750k, 1m, 1.5b; - = unlimited; empty = off):",
			"Upgrade budget", JOptionPane.PLAIN_MESSAGE, null, null, current);
		if (edited != null && !edited.equals(current))
		{
			asActive(entry, () ->
			{
				upgradeBudget.setText(edited);
				budgetEdited();
			});
		}
	}

	/** The bench/backpack row: the carried items beyond the worn set -
	 * spec weapon and per-mob swaps. A swap WORN against the lensed mob
	 * wears the accent border; the rest sit quietly on the bench. */
	private JComponent inventoryRow(ResultEntry entry, StyleResult result, boolean bis)
	{
		// A wrapping grid, NOT a single flow line: at Inventory 20 the
		// carried items overflow one row, and a clipped weapon swap reads
		// as missing (field bug). Height follows the row count.
		JPanel row = new JPanel(new BorderLayout(4, 0))
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		JLabel prefix = new JLabel("Inventory:");
		prefix.setForeground(MUTED);
		prefix.setFont(prefix.getFont().deriveFont(11f));
		prefix.setVerticalAlignment(JLabel.TOP);
		prefix.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		prefix.setToolTipText("Carried items not currently worn vs this mob"
			+ " (the Inventory chip sets the swap budget)");
		row.add(prefix, BorderLayout.WEST);
		JPanel cells = new JPanel(new GridLayout(0, 6, 3, 3));
		cells.setOpaque(false);
		GearItem specWeapon = bis ? result.gameSpecWeapon : result.specWeapon;
		int specId = specWeapon != null ? specWeapon.getId() : -1;
		for (GearItem item : bis ? result.gameBench : result.bench)
		{
			boolean isSpec = item.getId() == specId;
			JLabel cell = new JLabel();
			cell.setOpaque(true);
			cell.setHorizontalAlignment(JLabel.CENTER);
			cell.setBackground(CELL_BG);
			cell.setBorder(new RoundedBorder(
				isSpec ? BORDER_SPEC : ColorScheme.MEDIUM_GRAY_COLOR, 2, 2));
			cell.setToolTipText(item.label()
				+ (isSpec ? " - the special attack swap"
					: " - in the inventory vs this mob"));
			AsyncBufferedImage img = itemManager.getImage(item.getId());
			Runnable set = () -> cell.setIcon(new ImageIcon(
				img.getScaledInstance(-1, 24, Image.SCALE_SMOOTH)));
			img.onLoaded(() -> SwingUtilities.invokeLater(set));
			set.run();
			cells.add(cell);
		}
		// Assumed consumables ride along for FREE (field spec 2026-07-18):
		// the boost potion and the antifire - never a swap slot, muted
		// border so they read as supplies rather than gear.
		for (int consumableId : consumableIds(entry, result, bis))
		{
			JLabel cell = new JLabel();
			cell.setOpaque(true);
			cell.setHorizontalAlignment(JLabel.CENTER);
			cell.setBackground(CELL_BG);
			cell.setBorder(new RoundedBorder(new Color(90, 90, 90), 2, 2));
			cell.setToolTipText("Potion the numbers assume - does not use an Inventory slot");
			AsyncBufferedImage img = itemManager.getImage(consumableId);
			Runnable set = () -> cell.setIcon(new ImageIcon(
				img.getScaledInstance(-1, 24, Image.SCALE_SMOOTH)));
			img.onLoaded(() -> SwingUtilities.invokeLater(set));
			set.run();
			cells.add(cell);
		}
		row.add(cells, BorderLayout.CENTER);
		return row;
	}

	/** The roster rows (card anatomy #1): name + hp per mob, an
	 * INFORMATIONAL LENS - clicking flips whose numbers display below;
	 * the shared set never changes. */
	private JComponent mobLensRows(ResultEntry entry,
		CombatStyle viewedStyle, boolean bis)
	{
		JPanel rows = new JPanel();
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setOpaque(false);
		rows.setAlignmentX(LEFT_ALIGNMENT);
		rows.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
		for (int i = 0; i < entry.mobs.size(); i++)
		{
			final int index = i;
			MonsterStats mob = entry.mobs.get(i);
			boolean lensed = i == entry.lensIndex;
			JPanel row = new JPanel(new BorderLayout(4, 0));
			row.setOpaque(true);
			row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			// Bordered + padded so every row reads as clickable (field
			// spec); the lensed row wears the bright edge like Yours|BiS.
			row.setBorder(new RoundedBorder(lensed
				? ACCENT : ColorScheme.MEDIUM_GRAY_COLOR, 3, 8));
			row.setAlignmentX(LEFT_ALIGNMENT);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
			row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			row.setToolTipText("Show this mob's numbers - the shared set stays");
			JLabel name = new JLabel(mob.label() + " - " + mob.getHitpoints() + " hp");
			name.setForeground(lensed ? Color.WHITE : new Color(150, 150, 150));
			name.setFont(name.getFont().deriveFont(lensed ? Font.BOLD : Font.PLAIN, 12f));
			if (monsterIcons != null)
			{
				// The mob's wiki render rides the row; text-only until it
				// loads (or when the wiki has no picture for it).
				ImageIcon mobIcon = monsterIcons.get(mob.getName(), mob.getVersion(), 20, () ->
				{
					ImageIcon ready = monsterIcons.get(mob.getName(), mob.getVersion(), 20, null);
					if (ready != null)
					{
						name.setIcon(ready);
						name.setIconTextGap(6);
						name.revalidate();
					}
				});
				if (mobIcon != null)
				{
					name.setIcon(mobIcon);
					name.setIconTextGap(6);
				}
			}
			row.add(name, BorderLayout.CENTER);
			JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
			east.setOpaque(false);
			// The kit's BEST dps vs THIS mob (field spec): the carried
			// swaps can answer different mobs with different styles - the
			// icon shows the style this mob's answer attacks with, and
			// lensing the row follows it to that style's tab.
			DpsResult rowResult = mobRowResult(entry, index, viewedStyle, bis);
			double rowDps = rowResult == null ? 0 : rowResult.getDps();
			if (rowDps > 0)
			{
				CombatStyle rowStyle = resultStyle(rowResult, viewedStyle);
				JLabel dps = new JLabel(String.format("%.2f", rowDps));
				dps.setForeground(lensed ? GOOD : new Color(150, 170, 150));
				dps.setFont(dps.getFont().deriveFont(Font.BOLD, 12f));
				dps.setToolTipText((bis
					? "The BiS ceiling against this mob - "
					: "The carried kit's best dps against this mob - ")
					+ rowStyle.toString().toLowerCase());
				attachSprite(dps, AssumeIcons.styleSprite(rowStyle));
				dps.setIconTextGap(3);
				east.add(dps);
			}
			MouseAdapter lens = new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					lensTo(entry, index);
				}
			};
			row.addMouseListener(lens);
			name.addMouseListener(lens);
			if (entry.mobs.size() > 1)
			{
				// Each mob can leave the roster (field spec); the last one
				// cannot - close the whole result via the chrome X instead.
				JLabel remove = new JLabel(new CloseIcon(8));
				remove.setToolTipText("Remove " + mob.getName() + " from this result");
				remove.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				onClick(remove, e -> removeMobFromEntry(entry, index));
				east.add(remove);
			}
			if (east.getComponentCount() > 0)
			{
				row.add(east, BorderLayout.EAST);
			}
			rows.add(row);
			rows.add(Box.createVerticalStrut(2));
		}
		// The growth affordance: add a mob straight to this result's roster.
		JLabel add = new JLabel("+ Add mob");
		add.setForeground(new Color(150, 150, 150));
		add.setFont(add.getFont().deriveFont(Font.BOLD, 12f));
		add.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
		add.setAlignmentX(LEFT_ALIGNMENT);
		add.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		add.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		add.setToolTipText("Add a mob to this result - ONE shared set optimized across the list");
		onClick(add, e -> showAddMobDialog(entry));
		rows.add(add);
		rows.add(Box.createVerticalStrut(4));
		return rows;
	}

	/** Antifire potion doses, best first (gameval-verified 2026-07-18:
	 * super 21978.., extended super 22209.., regular 2452.., extended
	 * 11951..; classic 2-step dose spacing). */
	private static final int[] SUPER_ANTIFIRE_IDS = {
		21978, 21981, 21984, 21987, 22209, 22212, 22215, 22218};
	private static final int[] REGULAR_ANTIFIRE_IDS = {
		2452, 2454, 2456, 2458, 11951, 11953, 11955, 11957};

	/** The default antifire mode for a fresh result (field spec
	 * 2026-07-18): only meaningful when the roster breathes fire;
	 * Detect scans the collection for the best potion you have. */
	private int resolveDefaultAntifire(ResultEntry entry)
	{
		boolean fiery = false;
		for (MonsterStats m : entry.mobs)
		{
			if (DragonfireRules.breathesFire(m))
			{
				fiery = true;
				break;
			}
		}
		if (!fiery)
		{
			return 0;
		}
		int mode = displayOptions.defaultAntifireMode;
		if (mode >= 0)
		{
			return Math.min(2, mode);
		}
		for (int id : SUPER_ANTIFIRE_IDS)
		{
			if (ownedCheck.owns(id))
			{
				return 2;
			}
		}
		for (int id : REGULAR_ANTIFIRE_IDS)
		{
			if (ownedCheck.owns(id))
			{
				return 1;
			}
		}
		return 0;
	}

	/** Assumed consumables (field spec 2026-07-18, refined): a SINGLE
	 * mob shows the potion behind the VIEWED tab (flip to ranged, the
	 * ranging potion replaces it); a roster shows the union across each
	 * mob's BEST answer - a melee/ranged plan carries both potions. The
	 * antifire mode's potion always rides along. */
	private List<Integer> consumableIds(ResultEntry entry, StyleResult viewed, boolean bis)
	{
		LinkedHashSet<Integer> ids = new LinkedHashSet<>();
		if (entry.mobs.size() <= 1 || entry.perMobResults == null)
		{
			if (viewed != null)
			{
				addBoostConsumables(bis ? viewed.gameBoostLabel : viewed.boostLabel, ids);
			}
		}
		else
		{
			for (Map<CombatStyle, StyleResult> map : entry.perMobResults)
			{
				StyleResult best = null;
				double bestDps = -1;
				for (StyleResult r : map.values())
				{
					if (r == null)
					{
						continue;
					}
					DpsResult shown = bis ? r.overallBest
						: r.owned == null || r.owned.isEmpty() ? null : r.owned.get(0);
					if (shown != null && shown.getDps() > bestDps)
					{
						bestDps = shown.getDps();
						best = r;
					}
				}
				if (best != null)
				{
					addBoostConsumables(bis ? best.gameBoostLabel : best.boostLabel, ids);
				}
			}
		}
		if (entry.antifireMode == 1)
		{
			ids.add(2452);
		}
		else if (entry.antifireMode == 2)
		{
			ids.add(21978);
		}
		// The persistent trip supplies (food, prayer restore...) ride the
		// same consumable cells: display id only - the bank filter carries
		// the full dose lists separately.
		for (TripSupplies.Option supply : activeSupplies(entry))
		{
			ids.add(supply.ids[0]);
		}
		// Arceuus casting dependencies keep the cells compact: the book of
		// the dead and the best owned rune pouch (the runes are filter/
		// layout only - they live inside the pouch).
		if (entry.thralls)
		{
			ids.add(ExtraDps.BOOK_OF_THE_DEAD);
		}
		if ((entry.thralls || entry.deathCharge || demonbaneCast(entry)))
		{
			int pouch = ownedRunePouch();
			if (pouch != -1)
			{
				ids.add(pouch);
			}
		}
		return new ArrayList<>(ids);
	}

	private static void addBoostConsumables(String label, Set<Integer> ids)
	{
		if (label == null)
		{
			return;
		}
		if (label.contains("Overload (+)"))
		{
			ids.add(20996);
		}
		else if (label.contains("Overload"))
		{
			ids.add(20992);
		}
		else if (label.contains("Super combat"))
		{
			ids.add(12695);
		}
		else if (label.contains("Attack & strength"))
		{
			ids.add(2428);
			ids.add(113);
		}
		else if (label.contains("Super ranging"))
		{
			ids.add(11722);
		}
		else if (label.contains("Ranging potion"))
		{
			ids.add(2444);
		}
		else if (label.contains("Super magic"))
		{
			ids.add(11726);
		}
		else if (label.contains("Magic potion"))
		{
			ids.add(3040);
		}
		else if (label.contains("Saturated heart"))
		{
			ids.add(27641);
		}
		else if (label.contains("Imbued heart"))
		{
			ids.add(20724);
		}
	}

	/** One mob's row result: the side's kit BEST across ALL styles - the
	 * carried swaps can answer different mobs with different styles, on
	 * the Yours and the BiS side alike. */
	private DpsResult mobRowResult(ResultEntry entry, int index, CombatStyle style, boolean bis)
	{
		Map<CombatStyle, StyleResult> map = null;
		if (entry.perMobResults != null && index < entry.perMobResults.size())
		{
			map = entry.perMobResults.get(index);
		}
		else if (entry.mobs.size() == 1)
		{
			map = entry.results;
		}
		if (map == null)
		{
			return null;
		}
		DpsResult best = null;
		for (StyleResult r : map.values())
		{
			DpsResult shown = r == null ? null
				: bis ? r.overallBest
				: r.owned == null || r.owned.isEmpty() ? null : r.owned.get(0);
			if (shown != null && (best == null || shown.getDps() > best.getDps()))
			{
				best = shown;
			}
		}
		return best;
	}

	/** The combat style a shown result actually attacks with - a carried
	 * cross-style swap can flip one mob off the viewed tab's style. */
	private static CombatStyle resultStyle(DpsResult result, CombatStyle fallback)
	{
		if (result == null || result.getAttackType() == null)
		{
			return fallback;
		}
		if (result.getAttackType().startsWith("ranged"))
		{
			return CombatStyle.RANGED;
		}
		if (result.getAttackType().startsWith("magic"))
		{
			return CombatStyle.MAGIC;
		}
		return CombatStyle.MELEE;
	}

	/** The + row's mob picker: incremental search, double-click/Enter adds
	 * the hit to the entry's roster (same history step as before). */
	private void showAddMobDialog(ResultEntry entry)
	{
		Window owner = SwingUtilities.getWindowAncestor(this);
		JDialog dialog = new JDialog(owner, "Add a mob to this result",
			Dialog.ModalityType.APPLICATION_MODAL);
		JPanel content = new JPanel(new BorderLayout(0, 4));
		content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		JTextField field = new JTextField();
		DefaultListModel<MonsterStats> model = new DefaultListModel<>();
		JList<MonsterStats> hits = new JList<>(model);
		hits.setVisibleRowCount(8);
		hits.setCellRenderer(new DefaultListCellRenderer()
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
		Runnable refresh = () ->
		{
			model.clear();
			String query = field.getText() == null ? "" : field.getText().trim();
			if (!query.isEmpty())
			{
				for (MonsterStats hit : data.searchMonsters(query, 12))
				{
					if (!entry.hasMob(hit.getId()))
					{
						model.addElement(hit);
					}
				}
			}
			if (!model.isEmpty())
			{
				hits.setSelectedIndex(0);
			}
		};
		field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
		{
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent e)
			{
				refresh.run();
			}

			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent e)
			{
				refresh.run();
			}

			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent e)
			{
				refresh.run();
			}
		});
		Runnable pick = () ->
		{
			MonsterStats sel = hits.getSelectedValue();
			if (sel != null)
			{
				dialog.dispose();
				addMobToEntry(entry, sel);
			}
		};
		field.addActionListener(e -> pick.run());
		// ONE click adds (field feedback: the double-click was hated).
		onClick(hits, e ->
		{
			int idx = hits.locationToIndex(e.getPoint());
			if (idx >= 0)
			{
				hits.setSelectedIndex(idx);
				pick.run();
			}
		});
		content.add(field, BorderLayout.NORTH);
		content.add(new JScrollPane(hits), BorderLayout.CENTER);
		dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
			KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
			JComponent.WHEN_IN_FOCUSED_WINDOW);
		dialog.setContentPane(content);
		dialog.setSize(260, 240);
		dialog.setLocationRelativeTo(this);
		SwingUtilities.invokeLater(field::requestFocusInWindow);
		dialog.setVisible(true);
	}

	/** One result's card: the style TAB STRIP (skill icon + dps per tab,
	 * fixed melee/ranged/magic positions, strongest selected by default)
	 * over ONE flipping detail body, then the source legend (M-2c: tabs
	 * replaced the stacked style cards - kits become more tabs at M-4). */
	private JComponent resultCard(ResultEntry entry)
	{
		Map<CombatStyle, StyleResult> results = entry.results;
		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setOpaque(false);
		column.setAlignmentX(LEFT_ALIGNMENT);
		usedSources.clear();
		// Static tab positions (field spec): melee / ranged / magic always
		// left to right, on both sides of the Yours|BiS toggle - the
		// strongest style is the DEFAULT SELECTION, not the first slot.
		CombatStyle[] styleOrder = {CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC};
		boolean hasBis = displayOptions.gameBest && hasAnyGameBest(results);
		boolean bis = hasBis && entry.viewingBis;
		// The default resolves from your OWNED dps regardless of the viewed
		// side, so flipping the toggle never changes which tab is open.
		CombatStyle selected = entry.selectedTab != null ? entry.selectedTab : defaultTab(entry);
		// Card order (field spec 2026-07-17): mob list, then the gear/stat
		// body, with the style tabs BETWEEN the gear view and Yours|BiS -
		// the strip is built here (it needs the roster-wide order) and
		// rides into the card body.
		column.add(mobLensRows(entry, selected, bis));
		column.add(paramChipRow(entry));
		column.add(styleCard(entry, selected, results.get(selected), hasBis, bis,
			styleTabs(entry, results, styleOrder, selected, bis)));
		column.add(Box.createVerticalStrut(6));
		JComponent legend = buildSourceLegend();
		if (legend != null)
		{
			column.add(legend);
		}
		column.add(Box.createVerticalStrut(6));
		column.add(guidanceNote(entry, selected, bis));
		return column;
	}

	/** A soft, muted footnote under EVERY result: a wrapped caption then two
	 * centered chips - "Copy report" drops a pre-filled issue report (target,
	 * active parameters, the shown sets + dps) on the clipboard, "Discord"
	 * opens the invite to paste it into. The caption is a non-editable
	 * wrapping JTextArea (the notes-editor pattern) so it wraps to the REAL
	 * panel width - an HTML pixel width was ignored here and clipped the
	 * sentence on both sides. */
	private JComponent guidanceNote(ResultEntry entry, CombatStyle selected, boolean bis)
	{
		JPanel note = new JPanel();
		note.setLayout(new BoxLayout(note, BoxLayout.Y_AXIS));
		note.setOpaque(false);
		note.setAlignmentX(LEFT_ALIGNMENT);

		JTextArea text = new JTextArea(
			"Aiming to be comprehensive - cross-check the official calculator "
				+ "and a guide, then report anything off:");
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setEditable(false);
		text.setFocusable(false);
		text.setOpaque(false);
		text.setForeground(MUTED);
		// JTextArea defaults to a monospaced font; borrow the UI label font so
		// the caption matches the rest of the panel. One size up (11f) per
		// field request.
		text.setFont(new JLabel().getFont().deriveFont(11f));
		// A row floor so the wrapped lines are never clipped on first paint;
		// it grows if a narrow panel needs a 5th line (getPreferredSize maxes
		// content vs rows).
		text.setRows(4);
		text.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
		text.setAlignmentX(LEFT_ALIGNMENT);
		note.add(text);

		JLabel copyChip = actionChip(COPY_REPORT_LABEL,
			"Copy a pre-filled issue report to paste in the Discord", null);
		copyChip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		onClick(copyChip, e -> copyIssueReport(entry, selected, bis, copyChip));
		JLabel discordChip = actionChip("Discord", "Open the Loadout Lab Discord",
			() -> LinkBrowser.browse(DISCORD_URL));

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
		actions.setOpaque(false);
		actions.setAlignmentX(LEFT_ALIGNMENT);
		actions.add(copyChip);
		actions.add(discordChip);
		actions.setMaximumSize(new Dimension(Integer.MAX_VALUE,
			actions.getPreferredSize().height));
		note.add(actions);
		return note;
	}

	/** A quiet outlined action chip (gray text + edge, non-opaque) for the
	 * guidance footnote's Discord / Copy report actions. */
	private static JLabel actionChip(String text, String tooltip, Runnable onClick)
	{
		return chip(text, false, new Color(170, 170, 170), Font.PLAIN, 10f,
			ColorScheme.MEDIUM_GRAY_COLOR, 3, 8, tooltip,
			onClick == null ? null : e -> onClick.run());
	}

	/** Put the issue report on the system clipboard and flash the chip so the
	 * user knows it landed (EDT-only; the click arrives on the EDT). */
	private void copyIssueReport(ResultEntry entry, CombatStyle selected, boolean bis,
		JLabel chip)
	{
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(buildIssueReport(entry, selected, bis)), null);
		}
		catch (IllegalStateException busy)
		{
			// Another app owns the clipboard this instant - leave the label
			// alone so the user can just click again.
			return;
		}
		chip.setText("Copied!");
		Timer revert = new Timer(1500, ev -> chip.setText(COPY_REPORT_LABEL));
		revert.setRepeats(false);
		revert.start();
	}

	/** The clipboard payload. Two parts: a reporter-fills-in section at the
	 * top (bold prompts with an obvious &lt;FILL IN HERE&gt;), then the plugin's
	 * context (target, the active query parameters, and the shown style's
	 * Yours/BiS sets with items + dps + boost + spec) fenced off under a "do
	 * not modify" note. The markers are Discord markdown - bold and a code
	 * fence - so it renders there; pasted raw elsewhere they still read as
	 * clear section breaks. All ASCII so it survives any paste target. */
	private String buildIssueReport(ResultEntry entry, CombatStyle selected, boolean bis)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("**What did I expect?**\n");
		sb.append("**<FILL IN HERE>**\n\n");
		sb.append("**What did I see?**\n");
		sb.append("**<FILL IN HERE>**\n\n");
		sb.append("*The block below is filled in by Loadout Lab - please do not modify it.*\n");
		sb.append("```\n");
		sb.append("Loadout Lab data (v").append(PLUGIN_VERSION).append(")\n");

		sb.append("Target: ");
		if (entry.mobs.size() > 1)
		{
			List<String> labels = new ArrayList<>();
			for (MonsterStats m : entry.mobs)
			{
				labels.add(m.label());
			}
			sb.append(entry.mobs.size()).append(" mobs - ")
				.append(String.join(", ", labels)).append('\n');
		}
		else
		{
			MonsterStats m = entry.mob();
			sb.append(m.label()).append(" - ").append(m.getHitpoints()).append(" hp\n");
		}
		sb.append("Viewing: ").append(selected).append(" / ")
			.append(bis ? "Best in game" : "Yours").append('\n');

		appendActiveParams(sb, entry);

		StyleResult r = entry.results.get(selected);
		sb.append("\n-- ").append(selected).append(" --\n");
		if (r != null && r.owned != null && !r.owned.isEmpty())
		{
			appendSet(sb, "Yours", r.owned.get(0), r.spec, r.specDpsAdded, r.boostLabel);
		}
		else
		{
			sb.append("Yours: no set\n");
		}
		if (r != null && r.overallBest != null && r.overallBest.getDps() > 0)
		{
			appendSet(sb, "Best in game", r.overallBest, r.gameSpec, r.gameSpecDpsAdded,
				r.gameBoostLabel);
		}

		sb.append("\nOther styles (your best dps):");
		for (CombatStyle s : CombatStyle.concreteValues())
		{
			if (s == selected)
			{
				continue;
			}
			StyleResult sr = entry.results.get(s);
			double dps = sr != null && sr.owned != null && !sr.owned.isEmpty()
				? sr.owned.get(0).getDps() : 0;
			sb.append(' ').append(s).append(' ')
				.append(dps > 0 ? String.format("%.2f", dps) : "-").append(';');
		}
		sb.append("\n```");
		return sb.toString();
	}

	/** The active query parameters, mirroring the card's chip row so a report
	 * says whether it was on task, what upgrade budget/risk cap was set, etc.
	 * Only the parameters that actually apply to this mob/result are listed,
	 * same as the chips. */
	private void appendActiveParams(StringBuilder sb, ResultEntry entry)
	{
		MonsterStats mob = entry.mob();
		List<String> params = new ArrayList<>();

		String[] modeNames = {"Max DPS", "Balanced", "Tanky"};
		params.add("Optimize: " + modeNames[Math.max(0,
			Math.min(entry.optimizeMode, modeNames.length - 1))]);

		if (SlayerLockedMonsters.isTaskOnly(mob))
		{
			params.add("On task: yes (task-only boss)");
		}
		else if (mob.isSlayerMonster())
		{
			params.add("On task: " + (entry.onSlayerTask ? "yes" : "no"));
		}

		boolean exclusive = WildernessMonsters.isExclusive(mob);
		boolean listed = WildernessMonsters.isWilderness(mob);
		boolean wild = (exclusive || (listed && entry.inWilderness)) && displayOptions.wildyRisk;
		if (exclusive)
		{
			params.add("Wilderness: yes (exclusive)");
		}
		else if (listed)
		{
			params.add("Wilderness: " + (entry.inWilderness ? "yes" : "no"));
		}
		if (wild)
		{
			params.add("Protect item: " + (entry.protectItem ? "yes" : "no"));
			String risk = entry.riskCap == null ? "" : entry.riskCap.trim();
			params.add("Risk cap: " + (risk.isEmpty() ? "off" : risk));
		}

		com.loadoutlab.engine.BoostProfile supplied =
			com.loadoutlab.engine.RaidBoosts.suppliedBoost(entry.mobs.get(0));
		for (MonsterStats m : entry.mobs)
		{
			if (com.loadoutlab.engine.RaidBoosts.suppliedBoost(m) != supplied)
			{
				supplied = null;
				break;
			}
		}
		if (supplied != null)
		{
			params.add(supplied + ": " + (entry.raidBoost ? "assumed" : "own potions"));
		}

		boolean fiery = false;
		for (MonsterStats m : entry.mobs)
		{
			if (DragonfireRules.breathesFire(m))
			{
				fiery = true;
				break;
			}
		}
		if (fiery)
		{
			String[] antifire = {"Antifire: gear only", "Antifire: regular assumed",
				"Antifire: super assumed"};
			params.add(antifire[Math.max(0, Math.min(entry.antifireMode, antifire.length - 1))]);
		}

		if (displayOptions.upgradeBudget)
		{
			String budget = entry.upgradeBudget == null ? "" : entry.upgradeBudget.trim();
			boolean unlimited = budget.equals("-") || budget.equalsIgnoreCase("max");
			params.add("Budget: " + (budget.isEmpty() ? "owned gear only"
				: unlimited ? "unlimited" : budget));
		}

		if (entry.thralls)
		{
			params.add("Thralls: " + ExtraDps.thrallTier(magicLevel)
				+ " (dps shown includes " + String.format("%.2f", ExtraDps.thrallDps(magicLevel)) + ")");
		}
		for (Map.Entry<CombatStyle, String> pick : entry.prayerPicks.entrySet())
		{
			params.add("Prayer (" + pick.getKey() + "): "
				+ ("NONE".equals(pick.getValue()) ? "none" : pick.getValue()));
		}
		for (Map.Entry<CombatStyle, String> pick : entry.boostPicks.entrySet())
		{
			params.add("Boost (" + pick.getKey() + "): "
				+ ("NONE".equals(pick.getValue()) ? "none" : pick.getValue()));
		}
		if (entry.deathCharge)
		{
			params.add("Death Charge: on"
				+ (deathChargeUpgraded ? " (upgraded - two refunds per cast)" : ""));
		}
		if (entry.mobs.size() > 1)
		{
			params.add("Inventory: " + entry.maxSwaps);
		}
		if (!entry.assumeBestPrayer)
		{
			params.add("Prayer: off (prayerless)");
		}
		if (!entry.assumeBoosts)
		{
			params.add("Boosts: off (unboosted)");
		}

		sb.append("Parameters:\n");
		for (String p : params)
		{
			sb.append("  ").append(p).append('\n');
		}
	}

	/** One set block in the issue report: dps, the worn items in slot order,
	 * the prayer/boost the numbers assume, and the carried spec if any. */
	private static void appendSet(StringBuilder sb, String label, DpsResult set,
		SpecialAttack spec, double specDpsAdded, String boostLabel)
	{
		sb.append(label).append(": ").append(String.format("%.2f", set.getDps()))
			.append(" dps\n");
		List<String> items = new ArrayList<>();
		for (GearItem item : set.getLoadout().getGear().values())
		{
			if (item != null)
			{
				items.add(item.getName());
			}
		}
		if (!items.isEmpty())
		{
			sb.append("  ").append(String.join(", ", items)).append('\n');
		}
		if (boostLabel != null && !boostLabel.isEmpty())
		{
			sb.append("  Assumes: ").append(boostLabel).append('\n');
		}
		if (spec != null)
		{
			sb.append("  Spec: ").append(spec.getDisplayName());
			if (specDpsAdded > 0)
			{
				sb.append(String.format(" (adds ~%.2f dps)", specDpsAdded));
			}
			sb.append('\n');
		}
	}

	/** Roster-aware default: the style whose SHARED set averages the best
	 * owned dps across the whole mob list - stable under lens flips, so
	 * clicking a mob row never changes which set you are looking at
	 * (field report: it read as "different sets for different mobs"). */
	private static CombatStyle defaultTab(ResultEntry entry)
	{
		if (entry.perMobResults == null || entry.perMobResults.size() < 2)
		{
			return defaultTab(entry.results);
		}
		CombatStyle best = null;
		double bestScore = 0.0;
		// concreteValues() is the same MELEE/RANGED/MAGIC triple and hands
		// back a fresh array per call, so there is no aliasing hazard.
		for (CombatStyle style : CombatStyle.concreteValues())
		{
			double sum = 0;
			for (int i = 0; i < entry.perMobResults.size(); i++)
			{
				Map<CombatStyle, StyleResult> perMob = entry.perMobResults.get(i);
				StyleResult r = perMob == null ? null : perMob.get(style);
				if (r != null && r.owned != null && !r.owned.isEmpty())
				{
					// HP-weighted, mirroring the optimizer's objective -
					// the default tab and the shared-set choice must agree.
					int hp = i < entry.mobs.size()
						? Math.max(1, entry.mobs.get(i).getHitpoints()) : 1;
					sum += r.owned.get(0).getDps() * hp;
				}
			}
			if (sum > bestScore)
			{
				bestScore = sum;
				best = style;
			}
		}
		return best != null ? best : defaultTab(entry.results);
	}

	/** The tab to open before the user has picked one: highest owned dps,
	 * falling back to highest BiS dps when nothing is owned at all. */
	private static CombatStyle defaultTab(Map<CombatStyle, StyleResult> results)
	{
		CombatStyle best = null;
		double bestDps = 0.0;
		for (Map.Entry<CombatStyle, StyleResult> e : results.entrySet())
		{
			StyleResult r = e.getValue();
			if (r != null && r.owned != null && !r.owned.isEmpty()
				&& r.owned.get(0).getDps() > bestDps)
			{
				bestDps = r.owned.get(0).getDps();
				best = e.getKey();
			}
		}
		if (best != null)
		{
			return best;
		}
		for (Map.Entry<CombatStyle, StyleResult> e : results.entrySet())
		{
			StyleResult r = e.getValue();
			if (r != null && r.overallBest != null && r.overallBest.getDps() > bestDps)
			{
				bestDps = r.overallBest.getDps();
				best = e.getKey();
			}
		}
		return best != null ? best : CombatStyle.MELEE;
	}

	private static boolean hasAnyGameBest(Map<CombatStyle, StyleResult> results)
	{
		for (StyleResult r : results.values())
		{
			if (r != null && r.overallBest != null && r.overallBest.getDps() > 0)
			{
				return true;
			}
		}
		return false;
	}

	/** The Yours | BiS chip pair (field spec: the BiS view is the SAME card
	 * through a toggle, not a separate section), with the gear-gap percent
	 * for the selected style beside it. */
	private JComponent viewToggleRow(ResultEntry entry, StyleResult selected, boolean bis)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
		JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		chips.setOpaque(false);
		chips.add(viewChip("Yours", !bis, () ->
		{
			entry.viewingBis = false;
			renderPage();
		}));
		chips.add(viewChip("BiS", bis, () ->
		{
			entry.viewingBis = true;
			renderPage();
		}));
		row.add(chips, BorderLayout.WEST);
		if (selected != null && selected.overallBest != null
			&& selected.owned != null && !selected.owned.isEmpty()
			&& selected.overallBest.getDps() > 0)
		{
			double pct = Math.min(100.0,
				100.0 * selected.owned.get(0).getDps() / selected.overallBest.getDps());
			JLabel gap = new JLabel(String.format("you are at %.0f%%", pct));
			gap.setForeground(MUTED);
			gap.setFont(gap.getFont().deriveFont(11f));
			gap.setToolTipText("Your best owned set vs the game-wide best, at your levels");
			row.add(gap, BorderLayout.EAST);
		}
		return row;
	}

	private JComponent viewChip(String text, boolean selected, Runnable onClick)
	{
		// Bordered buttons (field request): the selected side wears a
		// bright edge, the other stays a quiet outline. Note the opaque
		// flag tracks `selected` here, unlike the other three chips.
		return chip(text, selected,
			selected ? Color.WHITE : new Color(150, 150, 150),
			Font.BOLD, 14f,
			selected ? ACCENT : ColorScheme.MEDIUM_GRAY_COLOR, 5, 12,
			text.equals("BiS")
				? "The game-wide best set at your levels" : "Your best owned set",
			e -> onClick.run());
	}

	/** The tab strip: one equal-width tab per style - the skill sprite and
	 * the best owned dps, nothing else. The selected tab wears the detail
	 * card's background so the two read as one surface. */
	private JComponent styleTabs(ResultEntry entry,
		Map<CombatStyle, StyleResult> results, CombatStyle[] order, CombatStyle selected,
		boolean bis)
	{
		JPanel strip = new JPanel(new GridLayout(1, order.length, 2, 0));
		strip.setOpaque(false);
		strip.setAlignmentX(LEFT_ALIGNMENT);
		strip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		for (CombatStyle style : order)
		{
			StyleResult r = results.get(style);
			DpsResult shown = r == null ? null
				: bis ? r.overallBest
				: r.owned == null || r.owned.isEmpty() ? null : r.owned.get(0);
			boolean hasSet = shown != null && shown.getDps() > 0;
			double extra = hasSet ? extraShownDps(entry) : 0;
			boolean isSelected = style == selected;
			JPanel tab = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 3));
			tab.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			tab.setOpaque(isSelected);
			// The pick was background-only and read as subtle (field
			// report): the chip language's border makes it unmissable.
			tab.setBorder(new RoundedBorder(isSelected
				? ACCENT : ColorScheme.MEDIUM_GRAY_COLOR, 1, 2));
			JLabel icon = new JLabel();
			attachSprite(icon, AssumeIcons.styleSprite(style));
			JLabel dps = new JLabel(hasSet
				? String.format("%.2f", shown.getDps() + extra) : "-");
			dps.setForeground(isSelected ? Color.WHITE : new Color(160, 160, 160));
			dps.setFont(dps.getFont().deriveFont(Font.BOLD, 13f));
			tab.add(icon);
			tab.add(dps);
			tab.setToolTipText(style + (hasSet
				? " - " + dps.getText() + " DPS" + (extra > 0
					? String.format(" (%.2f gear + %.2f thrall)",
						shown.getDps(), extra)
					: "")
				: " - no set"));
			tab.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			onClick(tab, e ->
			{
				entry.selectedTab = style;
				renderPage();
			});
			strip.add(tab);
		}
		return strip;
	}

	private JPanel styleCard(ResultEntry entry, CombatStyle style, StyleResult result,
		boolean hasBis, boolean bis, JComponent styleStrip)
	{
		renderingStyle = style;
		renderingBis = bis;
		renderingMechanicsNote = MonsterNotes.noteFor(entry.mob());
		renderingProtectItem = entry.protectItem && effectiveWilderness(entry)
			&& displayOptions.wildyRisk;
		// The risk skull describes YOUR set's death mechanics - Yours view
		// only, like the old full-width line it replaces.
		renderingRiskLine = !bis && displayOptions.riskLine && effectiveWilderness(entry);
		renderingUpgradeLine = !bis;
		renderingRiskSpecWeapon = result == null ? null : result.specWeapon;
		renderingRiskKeep = entry.protectItem ? 4 : 3;
		renderingRiskConsumables = result == null || bis
			? Collections.emptyList() : consumableIds(entry, result, false);
		renderingIncoming = result == null ? null : bis ? result.gameIncoming : result.incoming;
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		card.setAlignmentX(LEFT_ALIGNMENT);

		boolean hasSet = bis
			? result != null && result.overallBest != null && result.overallBest.getDps() > 0
			: result != null && result.owned != null && !result.owned.isEmpty();

		// The set menu moved into the result chrome (field spec) - this
		// section now only STAGES the assume chips for the stat panel.
		renderingChips = null;
		String chipLabel = result == null ? null
			: bis ? result.gameBoostLabel : result.boostLabel;
		if (hasSet && displayOptions.assumes
			&& chipLabel != null && !chipLabel.isEmpty())
		{
			// Engine-forced honesty flag (no protective shield owned) OR the
			// user's own shield-cell flip - either way the chip must show.
			String antifireTooltip = null;
			if (result.owned != null && !result.owned.isEmpty()
				&& result.owned.get(0).isAntifireAssumed())
			{
				antifireTooltip = "Assumes a super antifire - you own no"
					+ " anti-dragon or dragonfire shield";
			}
			else if (superAntifireAssumed())
			{
				antifireTooltip = "Super antifire (right-click the shield cell to flip back)";
			}
			else if (active != null && active.antifireMode == 1
				&& DragonfireRules.breathesFire(entry.mob()))
			{
				antifireTooltip = "Regular antifire assumed - with the"
					+ " required shield, dragonfire is fully blocked";
			}
			// The chips render in the stat panel beside the gear, not here.
			JPanel chips = assumesChips(entry, style, chipLabel,
				bis ? "Best prayers + boost in the game" : "Assumed prayer + boost (you own these)",
				antifireTooltip);
			renderingChips = chips;
			if (renderingProtectItem)
			{
				// Protect Item rides the assume chips (field spec) - it is
				// an assumption like the prayers beside it.
				JLabel keepChip = new JLabel();
				keepChip.setToolTipText("Protect Item assumed - a 4th item is kept on death");
				attachSprite(keepChip, SpriteID.Prayeron.PROTECT_ITEM);
				chips.add(keepChip);
			}
		}
		if (!hasSet)
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
					: bis ? "No usable set exists."
					: effectiveWilderness(entry) && riskCap(entry) >= 0
						? "No owned set fits the risk cap."
						: "No usable owned set found.");
			none.setForeground(MUTED);
			none.setAlignmentX(LEFT_ALIGNMENT);
			if (!bis && effectiveWilderness(entry) && riskCap(entry) >= 0)
			{
				none.setToolTipText("Every combination for this style busts the"
					+ " wilderness risk cap - protecting a pricey required item"
					+ " (a dragonfire shield...) can evict another valuable from"
					+ " the kept slots. Raise the Risk cap, enable Protect item,"
					+ " or allow the excluded cheap alternative");
			}
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
			// The Gauntlet groups are tagged in the RESULTS (field spec
			// 2026-07-18), not the search list: the raid-crafted tier
			// modeling is still being tuned.
			if (entry.mobs.stream().allMatch(
				m -> com.loadoutlab.engine.GauntletRules.family(m) != null))
			{
				JLabel soon = new JLabel("Gauntlet support is coming soon.");
				soon.setForeground(MUTED);
				soon.setFont(soon.getFont().deriveFont(Font.ITALIC, 12f));
				soon.setAlignmentX(LEFT_ALIGNMENT);
				soon.setToolTipText("Fights inside use raid-crafted gear tiers -"
					+ " the tier modeling is still being tuned");
				card.add(soon);
			}
			if (entry == active)
			{
				// Keep the toggles, clear the bank displays - this style
				// has no set to show.
				applyBankViews(style, null, null);
			}
			card.add(Box.createVerticalStrut(6));
			card.add(styleStrip);
			if (hasBis)
			{
				card.add(Box.createVerticalStrut(4));
				card.add(viewToggleRow(entry, result, bis));
			}
			return card;
		}

		DpsResult best = bis ? result.overallBest : result.owned.get(0);
		if (entry == active)
		{
			// The bank views follow the viewed style/side (field spec) -
			// switching tabs or the Yours|BiS toggle retargets them.
			applyBankViews(style, best, bis ? result.gameSpecWeapon : result.specWeapon);
		}

		if (!bis && style == CombatStyle.MAGIC && displayOptions.spellControls)
		{
			// The BiS side's spell renders in the stat panel (field spec);
			// the auto-pick control only steers YOUR search.
			card.add(magicSpellRow(best));
		}

		// Max hit, accuracy, damage taken, style, prayer bonus and counted
		// bonuses live in the grid's stat panel (field spec) - no text rows.
		if (!bis)
		{
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
		}
		card.add(Box.createVerticalStrut(4));
		// The owned grid marks what you don't own (green) and what already
		// matches the game-best pick (gold); the BiS grid is the same
		// renderer over the other answer (field spec - just a toggle).
		if (bis)
		{
			card.add(iconGrid(best, result.gameSpec, result.gameSpecWeapon,
				result.gameSpecExpectedDamage, result.gameSpecDpsAdded,
				"Strongest special attack in the game vs this monster"));
		}
		else
		{
			card.add(iconGrid(best, result.spec, result.specWeapon, result.specExpectedDamage,
				result.specDpsAdded, "Swap in for the special attack",
				true, result.overallBest == null ? null : result.overallBest.getLoadout()));
		}
		if (result != null && (!(bis ? result.gameBench : result.bench).isEmpty()
			|| !consumableIds(entry, result, bis).isEmpty()))
		{
			// The INVENTORY (field spec): below the gear - what is carried
			// but not worn against the lensed mob, plus the assumed
			// consumables. Both sides have a kit.
			card.add(Box.createVerticalStrut(4));
			card.add(inventoryRow(entry, result, bis));
		}
		card.add(Box.createVerticalStrut(6));
		card.add(styleStrip);
		if (hasBis)
		{
			card.add(Box.createVerticalStrut(4));
			card.add(viewToggleRow(entry, result, bis));
		}
		if (!bis)
		{
			// LOCAL excludes/sims/filters (the trio, field spec 2026-07-20):
			// the per-mob twins of the global tools, scoped to the lensed
			// mob - between the bank buttons and the note. A WRAPPING row:
			// three chips overflow the sidebar's width, and a plain
			// FlowLayout reports one-row height, silently clipping the
			// wrapped chip (the vanishing-Risk-chip field bug - Filter here
			// was invisible on 2026-07-20's first test).
			JPanel localRow = new JPanel(new WrapLayout(FlowLayout.CENTER, 8, 2))
			{
				@Override
				public Dimension getMaximumSize()
				{
					return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
				}
			};
			localRow.setOpaque(false);
			localRow.setAlignmentX(LEFT_ALIGNMENT);
			int lensedProfileId = entry.mob().profileId();
			String mobName = entry.mob().getName();
			int excludedCount = 0;
			for (Set<Integer> scoped : mobProfile.allMobExclusions(lensedProfileId).values())
			{
				excludedCount += scoped.size();
			}
			int simCount = mobProfile.allMobSims(lensedProfileId).size();
			// Same color language as the global -N/+N chips above the
			// search bar: red for exclusions, green for sims.
			localRow.add(localCountChip(
				excludedCount > 0 ? "Exclude: " + excludedCount : "Exclude",
				excludedCount > 0, true,
				"Never use an item vs " + mobName + " (this mob only) - click to add or manage",
				() -> asActive(entry, () -> manageLocalExclusions(entry, lensedProfileId, mobName))));
			localRow.add(localCountChip(
				simCount > 0 ? "Sim: " + simCount : "Sim",
				simCount > 0, false,
				"Count an item as owned vs " + mobName + " (this mob only) - click to add or manage",
				() -> asActive(entry, () -> manageLocalSims(entry, lensedProfileId, mobName))));
			int filterHere = mobProfile.supplyOverrides(lensedProfileId).size();
			for (Map<Integer, String> scopedItems
				: mobProfile.allFilterItems(lensedProfileId).values())
			{
				filterHere += scopedItems.size();
			}
			localRow.add(localGreyChip(
				filterHere > 0 ? "Filter: " + filterHere : "Filter",
				filterHere > 0,
				"Bank filters vs " + mobName + " (this mob only) - supply"
					+ " overrides and always-at-hand items",
				() -> asActive(entry, () -> manageSupplyOverrides(entry, lensedProfileId, mobName))));
			card.add(Box.createVerticalStrut(4));
			card.add(localRow);
		}
		if (displayOptions.showInBank || displayOptions.filterBank)
		{
			// Centred like the exclude/sim chips (field spec); the green
			// toggle border carries the on state - the labels stay put.
			JPanel bankRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
			bankRow.setOpaque(false);
			bankRow.setAlignmentX(LEFT_ALIGNMENT);
			bankRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
			if (displayOptions.showInBank)
			{
				bankRow.add(bankButton(style, best, bis ? result.gameSpecWeapon : result.specWeapon));
			}
			if (displayOptions.filterBank)
			{
				bankRow.add(bankFilterButton(style, best, bis ? result.gameSpecWeapon : result.specWeapon));
			}
			card.add(bankRow);
		}
		if (entry == active)
		{
			// The per-mob note under the bank buttons (field spec) - one
			// shared component, so it renders on the active card only.
			card.add(Box.createVerticalStrut(4));
			card.add(notePanel);
		}
		return card;
	}

	/** The roster's distinct profile ids in mob order - synthetic phase
	 * variants (TD shields, KQ forms) collapse onto their real mob, so a
	 * whole-group write touches each underlying profile once. */
	private static java.util.List<Integer> groupProfileIds(ResultEntry entry)
	{
		java.util.LinkedHashSet<Integer> ids = new java.util.LinkedHashSet<>();
		for (MonsterStats mob : entry.mobs)
		{
			ids.add(mob.profileId());
		}
		return new java.util.ArrayList<>(ids);
	}

	/** A local exclude/sim chip in the GLOBAL chips' color language:
	 * red for exclusions, green for sims, muted when empty. */
	private JComponent localCountChip(String text, boolean active, boolean red,
		String tooltip, Runnable onClick)
	{
		return chip(text, true,
			red
				? (active ? new Color(220, 120, 120) : new Color(140, 110, 110))
				: (active ? new Color(130, 200, 130) : new Color(110, 140, 110)),
			Font.BOLD, 11f,
			active
				? (red ? new Color(170, 90, 90) : new Color(95, 160, 95))
				: ColorScheme.MEDIUM_GRAY_COLOR, 2, 7, tooltip,
			e -> onClick.run());
	}

	/** The grey twin of localCountChip for the trio's filter member. */
	private JComponent localGreyChip(String text, boolean active,
		String tooltip, Runnable onClick)
	{
		return chip(text, true,
			active ? new Color(190, 190, 190) : new Color(130, 130, 130),
			Font.BOLD, 11f,
			active ? new Color(150, 150, 150) : ColorScheme.MEDIUM_GRAY_COLOR,
			2, 7, tooltip, e -> onClick.run());
	}

	/** The local exclusions manager: current entries with click-to-allow,
	 * plus the add-by-search entry point. */
	private void manageLocalExclusions(ResultEntry entry, int profileId, String mobName)
	{
		JPopupMenu menu = new JPopupMenu();
		menuItem(menu, "Exclude an item vs " + mobName + "...",
			e -> itemSearch.search("Exclude vs " + mobName, (itemId, name) ->
		{
			mobProfile.excludeForMob(profileId, ALL_SETS, itemId);
			recompute();
		}));
		java.util.List<Integer> group = groupProfileIds(entry);
		if (group.size() > 1)
		{
			// Whole-group write (field request 2026-07-18): the same local
			// exclusion lands on EVERY member, one undo entry.
			menuItem(menu, "Exclude an item vs the whole group...",
				e -> itemSearch.search("Exclude vs the whole group", (itemId, name) ->
			{
				mobProfile.excludeForMobs(group, ALL_SETS, itemId);
				recompute();
			}));
		}
		Map<String, Set<Integer>> scopes = mobProfile.allMobExclusions(profileId);
		if (!scopes.isEmpty())
		{
			menu.addSeparator();
			for (Map.Entry<String, Set<Integer>> scope : scopes.entrySet())
			{
				for (int itemId : scope.getValue())
				{
					GearItem item = data.getGear(itemId);
					menuItem(menu, "Allow: "
						+ (item != null ? item.label() : "item " + itemId)
						+ " (" + scopeLabel(scope.getKey()) + ")", e ->
					{
						mobProfile.removeMobExclusion(profileId, scope.getKey(), itemId);
						recompute();
					});
				}
			}
		}
		Point at = getMousePosition();
		menu.show(this, at != null ? at.x : 20, at != null ? at.y : 20);
	}

	/** The local sims manager: the per-mob twin of the global simmed
	 * items - counted as owned vs this mob only. */
	private void manageLocalSims(ResultEntry entry, int profileId, String mobName)
	{
		JPopupMenu menu = new JPopupMenu();
		menuItem(menu, "Sim an item vs " + mobName + "...",
			e -> itemSearch.search("Sim vs " + mobName + " (counts as owned)",
			(itemId, name) ->
		{
			mobProfile.simForMob(profileId, itemId, name);
			recompute();
		}));
		java.util.List<Integer> group = groupProfileIds(entry);
		if (group.size() > 1)
		{
			menuItem(menu, "Sim an item vs the whole group...",
				e -> itemSearch.search("Sim vs the whole group (counts as owned)",
				(itemId, name) ->
			{
				mobProfile.simForMobs(group, itemId, name);
				recompute();
			}));
		}
		Map<Integer, String> sims = mobProfile.allMobSims(profileId);
		if (!sims.isEmpty())
		{
			menu.addSeparator();
			for (Map.Entry<Integer, String> sim : sims.entrySet())
			{
				menuItem(menu, "Unsim: " + sim.getValue(), e ->
				{
					mobProfile.removeMobSim(profileId, sim.getKey());
					recompute();
				});
			}
		}
		Point at = getMousePosition();
		menu.show(this, at != null ? at.x : 20, at != null ? at.y : 20);
	}

	/** The supply categories in menu order (shared by the global chip and
	 * the per-mob Filter here menus). */
	private static final String[][] SUPPLY_CATEGORIES = {
		{TripSupplies.FOOD, "Food"},
		{TripSupplies.FAST_FOOD, "Fast food"},
		{TripSupplies.PRAYER_RESTORE, "Prayer restore"},
		{TripSupplies.SURGE, "Surge potion"},
		{TripSupplies.SPELLBOOK_CAPE, "Spellbook cape"},
		{TripSupplies.ANTIVENOM, "Anti-venom"}};

	/** The GLOBAL grey chip's menu: the six supply defaults (writing the
	 * wrench config directly - the change loops back as a normal config
	 * refresh) plus the always-filter item list. */
	private void showGlobalFiltersMenu(MouseEvent e)
	{
		if (globalFilters == null)
		{
			return;
		}
		JPopupMenu menu = new JPopupMenu();
		for (String[] cat : SUPPLY_CATEGORIES)
		{
			String current = supplyDefaults.get(cat[0]);
			JMenu sub = new JMenu(cat[1]);
			supplyDefaultChoice(sub, cat[0], "DETECT_BEST",
				"Detect best", ("DETECT_BEST".equals(current) || "DETECT".equals(current)));
			supplyDefaultChoice(sub, cat[0], "NONE", "None", "NONE".equals(current));
			for (TripSupplies.Option option : TripSupplies.options(cat[0]))
			{
				supplyDefaultChoice(sub, cat[0], option.key, option.name,
					option.key.equals(current));
			}
			menu.add(sub);
		}
		JMenu access = new JMenu("Arceuus access");
		boolean swap = "SPELLBOOK_SWAP".equals(supplyDefaults.get("arceuusAccess"));
		supplyDefaultChoice(access, "arceuusAccess", "DETECT_BEST",
			"Direct (camp Arceuus)", !swap);
		supplyDefaultChoice(access, "arceuusAccess", "SPELLBOOK_SWAP",
			"Via Spellbook Swap (Lunar home, adds swap + Vengeance runes)", swap);
		menu.add(access);
		menu.addSeparator();
		menuItem(menu, "Always filter an item...", ev ->
			itemSearch.search("Always filter (every bank view)", (itemId, name) ->
		{
			globalFilters.addAlwaysFiltered(itemId, name);
			refreshCountChips();
			reapplyBankViews();
		}));
		Map<Integer, String> always = globalFilters.alwaysFiltered();
		for (Map.Entry<Integer, String> item : always.entrySet())
		{
			menuItem(menu, "Stop filtering: " + item.getValue(), ev ->
			{
				globalFilters.removeAlwaysFiltered(item.getKey());
				refreshCountChips();
				reapplyBankViews();
			});
		}
		menu.show((Component) e.getSource(), 0, ((Component) e.getSource()).getHeight());
	}

	private void supplyDefaultChoice(JMenu sub, String category, String enumName,
		String label, boolean selected)
	{
		JCheckBoxMenuItem item = new JCheckBoxMenuItem(label, selected);
		item.addActionListener(a -> globalFilters.setSupplyDefault(category, enumName));
		sub.add(item);
	}

	/** The per-mob grey menu (field spec 2026-07-20: the trio's third
	 * member, "works exactly like exclude/sim here"): supply overrides per
	 * category - Default (config) / Detect best / None / each tier - plus
	 * the free-form filter items for this mob. */
	private void manageSupplyOverrides(ResultEntry entry, int profileId, String mobName)
	{
		JPopupMenu menu = new JPopupMenu();
		Map<String, String> overrides = mobProfile.supplyOverrides(profileId);
		for (String[] cat : SUPPLY_CATEGORIES)
		{
			String current = overrides.get(cat[0]);
			JMenu sub = new JMenu(cat[1] + (current != null ? " (custom)" : ""));
			supplyChoice(sub, entry, profileId, cat[0], null,
				"Default (config)", current == null);
			supplyChoice(sub, entry, profileId, cat[0], "DETECT_BEST",
				"Detect best", "DETECT_BEST".equals(current));
			supplyChoice(sub, entry, profileId, cat[0], "NONE",
				"None", "NONE".equals(current));
			for (TripSupplies.Option option : TripSupplies.options(cat[0]))
			{
				supplyChoice(sub, entry, profileId, cat[0], option.key,
					option.name, option.key.equals(current));
			}
			menu.add(sub);
		}
		menu.addSeparator();
		menuItem(menu, "Filter an item vs " + mobName + "...",
			e -> itemSearch.search("Filter vs " + mobName + " (this mob's bank view)",
			(itemId, name) ->
		{
			mobProfile.addFilterItem(profileId, ALL_SETS, itemId, name);
			reapplyBankViews();
		}));
		Map<String, Map<Integer, String>> scoped = mobProfile.allFilterItems(profileId);
		for (Map.Entry<String, Map<Integer, String>> scope : scoped.entrySet())
		{
			for (Map.Entry<Integer, String> item : scope.getValue().entrySet())
			{
				menuItem(menu, "Stop filtering: " + item.getValue(), ev ->
				{
					mobProfile.removeFilterItem(profileId, scope.getKey(), item.getKey());
					reapplyBankViews();
				});
			}
		}
		Point at = getMousePosition();
		menu.show(this, at != null ? at.x : 20, at != null ? at.y : 20);
	}

	private void supplyChoice(JMenu sub, ResultEntry entry, int profileId,
		String category, String choice, String label, boolean selected)
	{
		JCheckBoxMenuItem item = new JCheckBoxMenuItem(label, selected);
		item.addActionListener(a -> asActive(entry, () ->
		{
			mobProfile.setSupplyOverride(profileId, category, choice);
			renderPage();
		}));
		sub.add(item);
	}

	private static final ImageIcon PRAYER_ICON = loadPrayerIcon();
	private static final ImageIcon SWORD_ICON = loadSkillIcon("attack");
	private static final ImageIcon SHIELD_ICON = loadSkillIcon("defence");
	private static final Icon NO_PRAYER_ICON = new NoPrayerIcon(13);

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
		row.add(tradeChip(SWORD_ICON, "-" + t.dpsLossPct + "%"));
		row.add(tradeChip(SHIELD_ICON, "+" + t.dmgCutPct + "%"));
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
	/** The attack style the numbers use: "Style: Slash (aggressive)". */
	/** Blowpipes: name the loaded dart the numbers assume. */
	/** Everything the old spec line said, as the spec cell's tooltip. */
	private static String specTooltip(SpecialAttack spec, double expectedDamage,
		double dpsAdded, String fallbackTooltip)
	{
		// The headline is the win-over-replacement: the DPS this spec adds to
		// the kill over just attacking (marginal, regen-aware, drain-inclusive).
		// The note explains the mechanism (e.g. the defence drain), so no
		// separate throughput line - it read as +0.00 for drain specs.
		String headline = String.format("Spec: %s - adds ~%.2f dps (avg %.0f dmg, %d%% energy)",
			spec.getDisplayName(), dpsAdded, expectedDamage, spec.getEnergyCost());
		String note = spec.getNote();
		return "<html>" + headline
			+ "<br>" + (note.isEmpty() ? fallbackTooltip : note) + "</html>";
	}

	/**
	 * Wilderness: what a PvP death costs in gp for this set. Worn
	 * tradeables plus the carried spec weapon compete for the kept-on-
	 * death slots by value; everything past them is the risk.
	 */
	/** The wilderness risk as a stat-panel skull line (field spec): the
	 * gp at stake compact on the line, the kept/lost/fees story in the
	 * tooltip. Green when under the entry's risk cap. */
	private void addRiskStatLine(JPanel panel, DpsResult best)
	{
		PvpRisk.Assessment risk =
			PvpRisk.assess(best.getLoadout(), renderingRiskSpecWeapon, renderingRiskKeep);
		// Assumed consumables are RISKED in the wilderness too (field spec
		// 2026-07-18: a heart is NOT a safe item) - live GE prices, added
		// on top of the worn set's risk. Protection ranking is not applied
		// to them (v1 approximation, called out in the tooltip).
		long consumableRisk = 0;
		for (int id : renderingRiskConsumables)
		{
			consumableRisk += Math.max(0, consumablePrices.getOrDefault(id, 0L));
		}
		long totalRisk = risk.riskGp + consumableRisk;
		JLabel line = statLine(PvpRisk.formatGp(totalRisk),
			"placeholder",
			active != null && !active.riskCap.trim().isEmpty()
				&& totalRisk <= parsedBudgetGp(active.riskCap)
				? GOOD : new Color(220, 140, 120),
			new FixedWidthIcon(new SkullIcon()));
		int keep = renderingRiskKeep;
		StringBuilder tip = new StringBuilder("<html>Risk: "
			+ PvpRisk.formatGp(totalRisk) + " gp - " + keep
			+ " kept on death.<br>Kept:");
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
		if (consumableRisk > 0)
		{
			tip.append("<br>Assumed consumables (risked, not ranked for protection):");
			for (int id : renderingRiskConsumables)
			{
				long price = Math.max(0, consumablePrices.getOrDefault(id, 0L));
				if (price > 0)
				{
					tip.append("<br>- ").append(PvpRisk.formatGp(price)).append(" gp");
				}
			}
		}
		tip.append("<br>Skulled: keep 0-1.");
		tip.append("</html>");
		line.setToolTipText(tip.toString());
		panel.add(line);
	}

	/** The death skull as a standalone icon - the grid badge's drawing at
	 * line size (glyph-safe). */
	private static final class SkullIcon implements Icon
	{
		@Override
		public int getIconWidth()
		{
			return 15;
		}

		@Override
		public int getIconHeight()
		{
			return 14;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.translate(x, y);
				g2.setColor(new Color(235, 235, 225));
				g2.fillOval(2, 1, 9, 8);
				g2.fillRect(4, 8, 5, 3);
				g2.setColor(new Color(40, 40, 40));
				g2.fillOval(4, 4, 2, 2);
				g2.fillOval(7, 4, 2, 2);
				g2.drawLine(5, 9, 5, 10);
				g2.drawLine(7, 9, 7, 10);
			}
			finally
			{
				g2.dispose();
			}
		}
	}

	/**
	 * What buying the unowned pieces in this set would cost. Quest rewards
	 * are excluded from the gp sum - they cost effort, not coins - and are
	 * listed by source quest in the tooltip instead; a set whose only
	 * unowned pieces are quest rewards shows a compact quest-only line.
	 */
	private void addUpgradeStatLine(JPanel panel, DpsResult best)
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
		JLabel line = statLine(cost > 0
			? "~" + PvpRisk.formatGp(cost)
			: "quests",
			cost > 0 ? "Upgrade cost of the unowned pieces" : "Unowned quest rewards",
			UNOWNED, new FixedWidthIcon(new CoinsIcon()));
		line.setToolTipText(tip.append("</html>").toString());
		panel.add(line);
	}

	/** The grid badge's gp pile as a standalone icon (glyph-safe). */
	private static final class CoinsIcon implements Icon
	{
		@Override
		public int getIconWidth()
		{
			return 15;
		}

		@Override
		public int getIconHeight()
		{
			return 14;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.translate(x, y);
				paintCoin(g2, 3, 8);
				paintCoin(g2, 2, 5);
				paintCoin(g2, 3, 2);
			}
			finally
			{
				g2.dispose();
			}
		}

		private static void paintCoin(Graphics2D g2, int x, int y)
		{
			g2.setColor(new Color(140, 100, 25));
			g2.fillOval(x, y, 9, 5);
			g2.setColor(new Color(255, 200, 60));
			g2.fillOval(x + 1, y + 1, 7, 3);
		}
	}

	/** What the boss does back to you in this set, protection prayer up. */
	/** The incoming info tile's rich tooltip: the pray call, each style
	 * threat, pierce notes, and the curated caveats. */
	private static String incomingTooltip(IncomingDpsCalculator.Result incoming)
	{
		boolean prayable = incoming.protectPrayer != null;
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
		return tip.append("</html>").toString();
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
		JMenuItem flip = new JMenuItem(superAntifireAssumed()
			? "Require a dragonfire shield (drop the super antifire)"
			: "Assume a super antifire (drop the shield)");
		flip.addActionListener(a ->
		{
			boolean assume = !superAntifireAssumed();
			recordStep(assume ? "Assume super antifire" : "Require dragonfire shield",
				() -> setAntifireTo(assume), () -> setAntifireTo(!assume));
			if (historyControl == null)
			{
				setAntifireTo(assume); // no history wired: still flip
			}
		});
		return List.of(flip);
	}

	private void setAntifireTo(boolean assume)
	{
		setAntifireMode(assume ? 2 : 0);
	}

	private void setRaidBoost(boolean assume)
	{
		if (active != null && active.raidBoost != assume)
		{
			active.raidBoost = assume;
			recompute();
		}
	}

	private void setMaxSwaps(int swaps)
	{
		if (active != null && active.maxSwaps != swaps)
		{
			active.maxSwaps = swaps;
			recompute();
		}
	}

	private void setAntifireMode(int mode)
	{
		if (active != null && active.antifireMode != mode)
		{
			active.antifireMode = mode;
			recompute();
		}
	}

	/** The active set's item ids: gear + loaded dart + spec weapon. */
	/** The lensed result's inventory item ids for the viewed style - bank
	 * show/filter treats carried swaps exactly like worn gear. */
	private Set<Integer> inventoryIds(CombatStyle style)
	{
		if (active == null || active.results == null)
		{
			return Collections.emptySet();
		}
		StyleResult r = active.results.get(style);
		List<GearItem> inv = r == null ? Collections.emptyList()
			: active.viewingBis ? r.gameBench : r.bench;
		Set<Integer> ids = new HashSet<>();
		for (GearItem item : inv)
		{
			ids.add(item.getId());
		}
		if (r != null)
		{
			ids.addAll(consumableIds(active, r, active.viewingBis));
		}
		return ids;
	}

	private static Set<Integer> setItemIds(DpsResult best, GearItem specWeapon, GearItem dart)
	{
		Set<Integer> ids = new HashSet<>();
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

	/** "Filter bank": a virtual bank tag showing only this set's items.
	 * The toggle follows the viewed style - applyBankViews retargets. */
	private JComponent bankFilterButton(CombatStyle style, DpsResult best, GearItem specWeapon)
	{
		return paramChip("Filter bank", bankFiltering, true,
			"Show only this set's items in the bank (needs Bank Tags enabled)", () ->
		{
			bankFiltering = !bankFiltering;
			applyBankViews(style, best, specWeapon);
			renderPage();
		});
	}

	/** "Show in bank": outline this set's items while the bank is open.
	 * The toggle follows the viewed style - applyBankViews retargets. */
	private JComponent bankButton(CombatStyle style, DpsResult best, GearItem specWeapon)
	{
		return paramChip("Show in bank", bankShowing, true,
			"Outline this set's items in the bank", () ->
		{
			bankShowing = !bankShowing;
			applyBankViews(style, best, specWeapon);
			renderPage();
		});
	}

	/** Just the prayer/boost icon chips - the card HEADER hosts these
	 * inline with the style title to reclaim a whole row of vertical
	 * space (field request); tooltips carry the words. */
	private JPanel assumesChips(ResultEntry entry, CombatStyle style, String label,
		String tooltip, String antifireTooltip)
	{
		JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
		chips.setOpaque(false);
		chips.setToolTipText(tooltip);
		if (antifireTooltip != null && DragonfireRules.breathesFire(selectedMonster))
		{
			JLabel potion = new JLabel();
			potion.setToolTipText(antifireTooltip);
			attachItemIcon(potion, SUPER_ANTIFIRE_ID);
			chips.add(potion);
		}
		// The spellbook the BUILD assumes rides every tab, not just magic
		// (field spec 2026-07-21): thralls / Death Charge mean Arceuus. A
		// warning border says the player is NOT currently on that book.
		if (entry != null && displayOptions.spellbookChip
			&& (entry.thralls || entry.deathCharge))
		{
			boolean viaSwap = arceuusViaSwap();
			int homeBook = viaSwap ? SPELLBOOK_LUNAR : SPELLBOOK_ARCEUUS;
			boolean offBook = currentSpellbook >= 0
				&& currentSpellbook != homeBook;
			JLabel bookChip = new JLabel(viaSwap ? "Lunar+swap" : "Arceuus");
			bookChip.setFont(bookChip.getFont().deriveFont(Font.BOLD, 11f));
			bookChip.setForeground(offBook
				? new Color(230, 170, 90) : MUTED);
			if (offBook)
			{
				bookChip.setBorder(new RoundedBorder(new Color(200, 140, 60), 1, 4));
			}
			bookChip.setToolTipText(viaSwap
				? (offBook
					? "This build assumes LUNAR home with Spellbook Swap into"
						+ " Arceuus for the summons - and you are NOT on Lunar"
					: "This build assumes Lunar home, Spellbook Swap into"
						+ " Arceuus for the summons (Vengeance stays available)")
				: (offBook
					? "This build assumes the ARCEUUS spellbook (thralls / Death"
						+ " Charge) and you are NOT on it - swap before the trip"
						+ " (a magic or max cape can swap anywhere)"
					: "This build assumes the Arceuus spellbook (thralls / Death"
						+ " Charge)"));
			// Clicking the chip toggles the access mode in place (the grey
			// chip menu holds the same choice).
			if (globalFilters != null)
			{
				bookChip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				final boolean wasSwap = viaSwap;
				onClick(bookChip, e -> asActive(entry, () ->
					globalFilters.setSupplyDefault("arceuusAccess",
						wasSwap ? "DETECT_BEST" : "SPELLBOOK_SWAP")));
			}
			chips.add(bookChip);
		}
		for (String part : label.split(" \\+ "))
		{
			JLabel chip = new JLabel();
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
			// The picker (field direction 2026-07-21): prayer chips open the
			// prayer menu, boost chips the boost menu - grey means detected,
			// the accent border an override.
			boolean isPrayer = sprite >= 0;
			boolean overridden = entry != null && style != null
				&& (isPrayer ? entry.prayerPicks.containsKey(style)
					: entry.boostPicks.containsKey(style));
			chip.setToolTipText(part + (entry != null
				? " - click to change this assumption" + (overridden ? " (custom)" : "") : ""));
			if (overridden)
			{
				chip.setBorder(new RoundedBorder(ACCENT, 1, 3));
			}
			if (entry != null && style != null)
			{
				chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				final boolean prayerChip = isPrayer;
				onClick(chip, e -> asActive(entry, () ->
				{
					if (prayerChip)
					{
						showPrayerPickMenu(entry, style);
					}
					else
					{
						showBoostPickMenu(entry, style);
					}
				}));
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

	/** Like attachItemIcon, but on a CELL_BG plate - dark item sprites in
	 * the stat panel need the same contrast lift as the gear cells - and
	 * inside the fixed stat column. */
	private void attachBackedItemIcon(JLabel label, int itemId)
	{
		AsyncBufferedImage img = itemManager.getImage(itemId);
		Runnable set = () -> label.setIcon(new FixedWidthIcon(new BackedIcon(new ImageIcon(
			img.getScaledInstance(-1, 14, Image.SCALE_SMOOTH)))));
		img.onLoaded(() -> SwingUtilities.invokeLater(set));
		set.run();
	}

	/** attachSprite for the stat panel: the sprite rides the fixed-width
	 * column so every line's text starts on the same edge. */
	private void attachStatSprite(JLabel label, int spriteId)
	{
		// 13px tall: wide sprites (stance swords) keep real side margins
		// inside the fixed 20px column instead of filling it flush-left.
		spriteManager.getSpriteAsync(spriteId, 0, img ->
			SwingUtilities.invokeLater(() -> label.setIcon(new FixedWidthIcon(
				new ImageIcon(img.getScaledInstance(-1, 13, Image.SCALE_SMOOTH))))));
		label.setIconTextGap(4);
	}

	/** A rounded outline + padding for the chip language (field spec:
	 * soften the square corners) - one border instead of the old
	 * line+empty compound. */
	private static final class RoundedBorder extends javax.swing.border.AbstractBorder
	{
		private final Color color;
		private final int vPad;
		private final int hPad;

		RoundedBorder(Color color, int vPad, int hPad)
		{
			this.color = color;
			this.vPad = vPad;
			this.hPad = hPad;
		}

		@Override
		public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(color);
				g2.drawRoundRect(x, y, width - 1, height - 1, 8, 8);
			}
			finally
			{
				g2.dispose();
			}
		}

		@Override
		public Insets getBorderInsets(Component c)
		{
			return new Insets(vPad + 1, hPad + 1, vPad + 1, hPad + 1);
		}

		@Override
		public Insets getBorderInsets(Component c, Insets insets)
		{
			insets.set(vPad + 1, hPad + 1, vPad + 1, hPad + 1);
			return insets;
		}
	}

	/** A small icon on a CELL_BG rounded plate (2px padding each side). */
	private static final class BackedIcon implements Icon
	{
		private final Icon delegate;

		BackedIcon(Icon delegate)
		{
			this.delegate = delegate;
		}

		@Override
		public int getIconWidth()
		{
			return delegate.getIconWidth() + 4;
		}

		@Override
		public int getIconHeight()
		{
			return delegate.getIconHeight() + 4;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setColor(CELL_BG);
				g2.fillRoundRect(x, y, getIconWidth(), getIconHeight(), 4, 4);
			}
			finally
			{
				g2.dispose();
			}
			delegate.paintIcon(c, g, x + 2, y + 2);
		}
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
		double specDpsAdded, String specFallbackTooltip)
	{
		return iconGrid(result, spec, specWeapon, specExpected, specDpsAdded,
			specFallbackTooltip, false, null);
	}

	private JPanel iconGrid(DpsResult result, SpecialAttack spec, GearItem specWeapon, double specExpected,
		double specDpsAdded, String specFallbackTooltip, boolean markUnowned,
		Loadout gameBest)
	{
		int cell = ICON_SIZE + 4;
		// Wilderness: badge every cell with its death fate.
		PvpRisk.Assessment fates = null;
		if (markUnowned && effectiveWilderness())
		{
			fates = PvpRisk.assess(result.getLoadout(), specWeapon,
				protectItem.isSelected() ? 4 : 3);
		}
		// One store lookup per grid, not one per cell.
		Map<GearSlot, Integer> pinnedSlots = renderingStyle == null
			? Collections.emptyMap() : mobProfile.pins(currentMonsterId(), renderingStyle);
		RiskDotLabel specCell = buildSpecCell(cell, spec, specWeapon, specExpected,
			specDpsAdded, specFallbackTooltip, fates);
		return centerRow(classicGrid(cell, result, fates, pinnedSlots,
			markUnowned, gameBest, specCell, renderingIncoming));
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
		Map<GearSlot, Integer> pinnedSlots, boolean markUnowned, Loadout gameBest,
		RiskDotLabel specCell, IncomingDpsCalculator.Result incoming)
	{
		// The item/stat view (field spec): the classic gear silhouette on
		// the left, and the right 2x5 FOOTPRINT as one CONNECTED stat panel,
		// not boxed cells: stat blocks flow top-down with their own
		// typography. Both sides of the Yours|BiS toggle share this layout.
		JPanel gear = new JPanel(new GridLayout(5, 3, 2, 2));
		gear.setOpaque(false);
		for (int i = 0; i < CLASSIC_ORDER.length; i++)
		{
			if (i == CLASSIC_SPEC_INDEX)
			{
				gear.add(specCell);
			}
			else if (CLASSIC_ORDER[i] != null)
			{
				gear.add(buildSlotCell(CLASSIC_ORDER[i], result, cell, fates, pinnedSlots, markUnowned, gameBest));
			}
			else
			{
				gear.add(blankCell(cell));
			}
		}
		int height = 5 * cell + 8;
		gear.setPreferredSize(new Dimension(3 * cell + 4, height));
		gear.setMaximumSize(new Dimension(3 * cell + 4, height));
		JPanel combo = new JPanel(new BorderLayout(8, 0));
		combo.setOpaque(false);
		combo.setAlignmentX(LEFT_ALIGNMENT);
		combo.add(gear, BorderLayout.WEST);
		JPanel stat = statPanel(result, incoming);
		stat.setPreferredSize(new Dimension(2 * cell + 6, height));
		combo.add(stat, BorderLayout.CENTER);
		combo.setMaximumSize(new Dimension(5 * cell + 24, height));
		return combo;
	}

	/** The connected stat panel filling the grid's right 2x5 footprint:
	 * one line per stat (field format spec), the assume chips at the top,
	 * each line honouring its display option. */
	private JPanel statPanel(DpsResult result, IncomingDpsCalculator.Result incoming)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		if (renderingChips != null)
		{
			renderingChips.setAlignmentX(LEFT_ALIGNMENT);
			panel.add(renderingChips);
			panel.add(Box.createVerticalStrut(4));
		}
		Color statText = new Color(200, 200, 200);
		if (displayOptions.maxHit)
		{
			panel.add(statLine(String.valueOf(result.getMaxHit()),
				"Max hit " + result.getMaxHit(), statText,
				new FixedWidthIcon(new HitsplatIcon(12))));
		}
		if (displayOptions.accuracy)
		{
			String acc = Math.round(result.getAccuracy() * 100) + "%";
			panel.add(statLine(acc, "Hit chance " + acc, statText,
				new FixedWidthIcon(new CrosshairIcon(13))));
		}
		if (incoming != null && displayOptions.damageTaken)
		{
			// The line always renders (field spec). A fully-blocked ~0.0 is
			// the best news it can carry - but a monster whose attacks are
			// beyond the model shows "?": silence must read as unknown,
			// not safe.
			boolean unmodeled = !incoming.fullyModeled
				&& incoming.totalDps <= 0 && incoming.unprayedDps <= 0;
			JLabel taken = statLine(
				unmodeled ? "DTPS: ?" : String.format("DTPS: ~%.1f", incoming.totalDps),
				unmodeled
					? "This monster's attacks are beyond the stat-sheet model"
						+ " - unknown, not zero"
					: incomingTooltip(incoming),
				statText, null);
			if (!unmodeled)
			{
				// The protect-prayer sprite IS the pray call.
int sprite = incoming.protectPrayer != null
					? AssumeIcons.prayerSprite(incoming.protectPrayer) : -1;
				if (sprite >= 0)
				{
					attachStatSprite(taken, sprite);
				}
				else
				{
					taken.setIcon(new FixedWidthIcon(NO_PRAYER_ICON));
					taken.setIconTextGap(4);
				}
			}
			panel.add(taken);
		}
		String styleText = attackStyleText(result);
		if (displayOptions.attackStyle && styleText != null)
		{
			// Compact type on the line; the stance ("controlled" - Defence
			// xp!) survives in the tooltip.
			JLabel styleLine = statLine(styleText,
				"Use this attack style: " + result.getAttackType(), statText, null);
			attachStatSprite(styleLine, SpriteID.SideIcons.COMBAT);
			panel.add(styleLine);
		}
		String type = result.getAttackType();
		int dartIdx = type.indexOf(" - ");
		if (dartIdx >= 0 && type.startsWith("ranged"))
		{
			// The blowpipe's loaded dart (field spec: in the stat panel,
			// wearing its item icon). The tier word alone fits the column;
			// the tooltip keeps the full story and the right-click keeps
			// the exclusion path.
			String dartName = type.substring(dartIdx + 3);
			String tier = dartName.toLowerCase().replace(" darts", "").replace(" dart", "");
			JLabel dart = statLine(capitalize(tier),
				"Loaded with " + dartName + " - included in the dps"
					+ " (right-click to exclude)", statText, null);
			GearItem dartItem = loadedDart(result);
			if (dartItem != null)
			{
				attachBackedItemIcon(dart, dartItem.getId());
				dart.setIconTextGap(4);
				attachExclusionMenu(dart, List.of(dartItem));
			}
			panel.add(dart);
		}
		if (renderingStyle == CombatStyle.MAGIC && displayOptions.spellControls)
		{
			String spellName = result.getSpellName();
			String book = spellBookText(result);
			if (spellName == null && renderingBis)
			{
				// A magic set without an autocast spell is a powered staff.
				JLabel builtIn = statLine("Built-in",
					"Powered staff - casts its own spell", statText, null);
				GearItem weapon = result.getLoadout().getWeapon();
				if (weapon != null)
				{
					attachBackedItemIcon(builtIn, weapon.getId());
					builtIn.setIconTextGap(4);
				}
				panel.add(builtIn);
			}
			else if (spellName != null)
			{
				// ONE row: <spellbook icon> <spell icon> (field spec), the
				// game's real book art; the BiS view appends the name (no
				// combo carries it there).
				JPanel spellRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
				spellRow.setOpaque(false);
				spellRow.setAlignmentX(LEFT_ALIGNMENT);
				spellRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
				if (book != null)
				{
					JLabel bookIcon = new JLabel();
					bookIcon.setToolTipText(book + " spellbook - the autocast spell's book");
					attachStatSprite(bookIcon, spellbookSprite(book));
					spellRow.add(bookIcon);
				}
				JLabel spellIcon = new JLabel();
				spellIcon.setToolTipText("Autocast " + spellName
					+ (spellName.contains("Demonbane") ? " - assumes Mark of Darkness" : ""));
				int sprite = AssumeIcons.spellSprite(spellName);
				if (sprite >= 0)
				{
					attachStatSprite(spellIcon, sprite);
					spellRow.add(spellIcon);
				}
				if (renderingBis)
				{
					JLabel name = new JLabel(spellName);
					name.setForeground(statText);
					name.setFont(name.getFont().deriveFont(Font.BOLD, 12f));
					spellRow.add(name);
				}
				panel.add(spellRow);
			}
		}
		int prayer = result.getLoadout().getBonuses().getPrayer();
		if (displayOptions.prayerBonus && prayer != 0)
		{
			JLabel pray = statLine(String.format("%+d", prayer),
				"Gear prayer bonus - slower prayer drain", statText, null);
			if (PRAYER_ICON != null)
			{
				pray.setIcon(new FixedWidthIcon(new BackedIcon(new ImageIcon(
					PRAYER_ICON.getImage().getScaledInstance(-1, 12, Image.SCALE_SMOOTH)))));
				pray.setIconTextGap(4);
			}
			panel.add(pray);
		}
		if (displayOptions.bonuses && !result.getCountedBonuses().isEmpty())
		{
			JLabel counting = statLine(String.valueOf(result.getCountedBonuses().size()),
				"<html>Conditional bonuses applied:<br>"
					+ String.join("<br>", result.getCountedBonuses()) + "</html>", statText, null);
			counting.setIcon(new FixedWidthIcon(new BackedIcon(new PlusStarIcon(11))));
			counting.setIconTextGap(4);
			panel.add(counting);
		}
		if (renderingMechanicsNote != null)
		{
			// Curated mechanics (recoil, finishing items) for the lensed
			// mob: a compact (i), the words in its tooltip (field spec -
			// it lives in the stat panel with the other set facts).
			JLabel noteLine = statLine("info",
				"<html><body style='width:220px'>" + renderingMechanicsNote
					+ "</body></html>", new Color(200, 170, 110),
				new FixedWidthIcon(new InfoIcon(11)));
			panel.add(noteLine);
		}
		if (renderingUpgradeLine)
		{
			// Upgrade cost with the gp pile (field spec) - just above the
			// wildy skull, money beside money.
			addUpgradeStatLine(panel, result);
		}
		if (renderingRiskLine)
		{
			// The wildy row sits last in the list (field spec).
			addRiskStatLine(panel, result);
		}
		panel.add(Box.createVerticalGlue());
		return panel;
	}

	/** One stat line: compact 12px, icon optional, tooltip carries the
	 * sentence. */
	private static JLabel statLine(String text, String tooltip, Color color, Icon icon)
	{
		JLabel line = new JLabel(text);
		line.setForeground(color);
		line.setFont(line.getFont().deriveFont(Font.BOLD, 12f));
		line.setAlignmentX(LEFT_ALIGNMENT);
		line.setToolTipText(tooltip);
		line.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		if (icon != null)
		{
			line.setIcon(icon);
			line.setIconTextGap(3);
		}
		return line;
	}

	/** A tiny painted infinity sign for the unlimited-budget chip
	 * (field spec 2026-07-18) - drawn, never a Unicode glyph. */
	private static final class InfinityIcon implements Icon
	{
		private final Color color;

		InfinityIcon(Color color)
		{
			this.color = color;
		}

		@Override
		public int getIconWidth()
		{
			return 15;
		}

		@Override
		public int getIconHeight()
		{
			return 10;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(color);
			g2.setStroke(new BasicStroke(1.6f));
			g2.drawOval(x, y + 2, 6, 6);
			g2.drawOval(x + 7, y + 2, 6, 6);
			g2.dispose();
		}
	}

	/** A fixed-width box that centres its delegate - every stat-panel
	 * line's icon occupies the SAME column width, so the values start on
	 * one hard left edge (field spec: a strong visual column). */
	private static final class FixedWidthIcon implements Icon
	{
		static final int WIDTH = 20;
		static final int HEIGHT = 16;
		private final Icon delegate;

		FixedWidthIcon(Icon delegate)
		{
			this.delegate = delegate;
		}

		@Override
		public int getIconWidth()
		{
			return WIDTH;
		}

		@Override
		public int getIconHeight()
		{
			return Math.max(HEIGHT, delegate.getIconHeight());
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			delegate.paintIcon(c,
				g,
				x + Math.max(0, (WIDTH - delegate.getIconWidth()) / 2),
				y + Math.max(0, (getIconHeight() - delegate.getIconHeight()) / 2));
		}
	}

	/** A painted crosshair for the accuracy line (glyph-safe) - the
	 * Attack staticon read as the same sword as the style icon. */
	private static final class CrosshairIcon extends SizedIcon
	{
		CrosshairIcon(int size)
		{
			super(size);
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(new Color(200, 170, 90));
				int pad = 2;
				g2.drawOval(x + pad, y + pad, size - 2 * pad - 1, size - 2 * pad - 1);
				int mid = size / 2;
				g2.drawLine(x + mid, y, x + mid, y + 3);
				g2.drawLine(x + mid, y + size - 4, x + mid, y + size - 1);
				g2.drawLine(x, y + mid, x + 3, y + mid);
				g2.drawLine(x + size - 4, y + mid, x + size - 1, y + mid);
				g2.fillOval(x + mid - 1, y + mid - 1, 3, 3);
			}
			finally
			{
				g2.dispose();
			}
		}
	}

	/** The game's own spellbook icons (SideIcons): standard = the magic
	 * tab star, the other books their dedicated sidebar art. */
	private static int spellbookSprite(String book)
	{
		switch (book.toLowerCase(Locale.ROOT))
		{
			case "ancient":
				return SpriteID.SideIcons.SPELLBOOK_ANCIENT_MAGICKS;
			case "lunar":
				return SpriteID.SideIcons.SPELLBOOK_LUNAR;
			case "arceuus":
				return SpriteID.SideIcons.SPELLBOOK_ARCEUUS;
			default:
				return SpriteID.SideIcons.MAGIC;
		}
	}

	/** A painted red hitsplat for the max-hit line (glyph-safe). */
	private static final class HitsplatIcon extends SizedIcon
	{
		HitsplatIcon(int size)
		{
			super(size);
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(new Color(150, 30, 30));
				g2.fillRoundRect(x, y + 1, size - 1, size - 2, 5, 5);
				g2.setColor(new Color(200, 60, 60));
				g2.drawRoundRect(x, y + 1, size - 2, size - 3, 5, 5);
			}
			finally
			{
				g2.dispose();
			}
		}
	}

	/** A painted circled-i for the mechanics note - amber like the note
	 * text it summarizes; painted, not a glyph (Tahoe tofu). */
	private static final class InfoIcon extends SizedIcon
	{
		InfoIcon(int size)
		{
			super(size);
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(new Color(200, 170, 110));
				g2.drawOval(x, y, size - 1, size - 1);
				int cx = x + size / 2;
				g2.fillRect(cx - 1, y + 3, 2, 2);
				g2.fillRect(cx - 1, y + 6, 2, size - 9);
			}
			finally
			{
				g2.dispose();
			}
		}
	}

	/** The amber plus-star (the mascots' signature), as a static icon for
	 * the counted-bonuses line. Painted - glyphs tofu on Tahoe. */
	private static final class PlusStarIcon extends SizedIcon
	{
		PlusStarIcon(int size)
		{
			super(size);
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setColor(new Color(208, 178, 102));
				int mid = size / 2;
				int arm = Math.max(3, size / 3);
				g2.fillRect(x + mid - 1, y + mid - arm, 3, 2 * arm + 1);
				g2.fillRect(x + mid - arm, y + mid - 1, 2 * arm + 1, 3);
			}
			finally
			{
				g2.dispose();
			}
		}
	}

	/** The resolved autocast spell's book ("Standard"), or null when the
	 * result has no autocast spell (melee/ranged/powered staves). */
	private String spellBookText(DpsResult result)
	{
		String name = result.getSpellName();
		if (name == null)
		{
			return null;
		}
		for (com.loadoutlab.data.SpellStats spell : data.getSpells())
		{
			if (spell.getName().equalsIgnoreCase(name))
			{
				String book = spell.getSpellbook();
				return book == null || book.isEmpty() ? null : capitalize(book);
			}
		}
		return null;
	}

	/** The short attack-style word ("Rapid", "Aggressive"); null for magic
	 * (the spell line covers it). */
	private static String attackStyleText(DpsResult result)
	{
		String type = result.getAttackType();
		if (type == null || type.startsWith("magic"))
		{
			return null;
		}
		if (type.startsWith("ranged"))
		{
			return type.contains("rapid") ? "Rapid" : "Accurate";
		}
		// Melee types read "stab (controlled)" or "slash - ..." - the line
		// carries the bare type; the caller's tooltip keeps the stance.
		int cut = type.indexOf(" (");
		if (cut < 0)
		{
			cut = type.indexOf(" - ");
		}
		return capitalize(cut > 0 ? type.substring(0, cut) : type);
	}

	/** A transparent placeholder holding a grid corner open (classic layout). */
	private static JComponent blankCell(int cell)
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
		slot.setOpaque(true);
		slot.setBackground(CELL_BG);
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
		double specDpsAdded, String specFallbackTooltip, PvpRisk.Assessment fates)
	{
		RiskDotLabel specCell = new RiskDotLabel();
		specCell.setPreferredSize(new Dimension(cell, cell));
		specCell.setHorizontalAlignment(SwingConstants.CENTER);
		specCell.setOpaque(true);
		specCell.setBackground(CELL_BG);
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
				specDpsAdded, specFallbackTooltip);
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
