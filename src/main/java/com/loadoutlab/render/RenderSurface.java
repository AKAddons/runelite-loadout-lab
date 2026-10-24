package com.loadoutlab.render;

import java.awt.*;
import java.util.*;
import java.util.Map;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import java.awt.event.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.event.DocumentEvent;
import net.runelite.client.game.SpriteManager;

/**
 * The renderer handed to Core by reference (one-surface ruling): Core
 * calls it on the EDT with the latest page and mounts what it returns
 * inside Core's own panel. We keep the mounted root and repaint it in
 * place when a new page arrives, so Core does not need to re-mount
 * per page.
 */
@lombok.extern.slf4j.Slf4j
@lombok.RequiredArgsConstructor
public class RenderSurface {
	private final ResultCards cards;
	private final Supplier<Map<String, Object>> page;
	private final CommandSink commands;
	private final ItemPicker picker;
	private final Searcher searcher;
	@lombok.Setter
	private SpriteManager spriteManager;

	/** The compute animation's pool: a sea lens draws the sea moods; a
	 * raid MONSTER its raid's - they live nowhere else, so a single Zebak
	 * search charges the Obelisk (Andrew 2026-09-02); a Zulrah lens hers;
	 * anything else the land flask. */
	/** Monster-name prefix -> pool key, from mood_routes.tsv (Andrew
	 * 2026-09-03: ten bosses join Zulrah; the raids route by group data). */
	private static final Map<String, String> MOOD_ROUTES = Ui.tsv("mood_routes.tsv");

	static String moodKey(Map<String, Object> page) {
		Map<String, Object> mob = lensedMob(page);
		if (Model.flag(mob, "naval")) {
			return "sea";
		}
		String raid = Model.str(mob, "raid");
		if (raid != null) {
			return raid;
		}
		String name = Model.str(mob, "name");
		if (name != null) {
			for (Map.Entry<String, String> route : MOOD_ROUTES.entrySet()) {
				if (name.startsWith(route.getKey())) {
					return route.getValue();
				}
			}
		}
		return null;
	}

	/** The lensed mob of the first entry that has mobs, or null. */
	private static Map<String, Object> lensedMob(Map<String, Object> page) {
		int lens = Model.id(ResultCards.firstParams(page), "lensIndex");
		for (Map<String, Object> entry : Model.list(page, "entries")) {
			List<Map<String, Object>> entryMobs = Model.list(entry, "mobs");
			if (!entryMobs.isEmpty()) {
				return entryMobs.get(Math.min(Math.max(lens, 0), entryMobs.size() - 1));
			}
		}
		return null;
	}

	/** Config gates the renderer honours: the compute animation and the
	 * wilderness risk controls. Both settings described behaviour they
	 * did not control until 2026-08-22 - the merge-back dropped the
	 * DisplayOptions bridge and nothing replaced it. */
	private BooleanSupplier animationGate = () -> true;
	private BooleanSupplier wildyRiskGate = () -> true;
	/** The "PvP world chip" setting: show it on every card. */
	private BooleanSupplier pvpChipGate = () -> false;

	public void setDisplayGates(BooleanSupplier animation,
		BooleanSupplier wildyRisk, BooleanSupplier pvpChip) {
		this.animationGate = animation;
		this.wildyRiskGate = wildyRisk;
		this.pvpChipGate = pvpChip;
	}
	private JPanel root;
	private JPanel matchesBox;
	/** The classic search cadence: 150ms debounce, 2+ characters. */
	private final Timer searchDebounce = Ui.once(150, e -> runSearch());
	private JTextField search;
	private JPanel cardArea;
	private JPanel waitingSlot;
	private AsciiLoader loader;
	private volatile boolean isComputing;

	/** Compute-in-flight: shows the static Computing... slot. */
	public void setComputing(boolean computing) {
		isComputing = computing;
		SwingUtilities.invokeLater(() -> {
			if (waitingSlot == null) {
				return;
			}
			if (isComputing && cardArea != null) {
				// The classic contract: a compute clears the RESULTS - the
				// controls stay up (Core publishes the params immediately)
				// so a wrong parameter can be fixed without waiting out
				// the compute.
				cardArea.removeAll();
				cardArea.revalidate();
				cardArea.repaint();
			}
			if (loader != null) {
				boolean animate = animationGate.getAsBoolean();
				loader.setVisible(animate);
				// A sea compute draws from the sea moods (REQ-SC-17): the
				// pending page already carries the selection, so peek it.
				loader.setKey(moodKey(page.get()));
				loader.setRunning(isComputing && animate);
			}
			waitingSlot.setVisible(isComputing);
			waitingSlot.revalidate();
			waitingSlot.repaint();
			if (!isComputing) {
				// Remount whatever page landed during the compute - and
				// reset the identical-page skip so the reveal cannot be
				// deduped away.
				lastRenderedPage = null;
				repaint();
			}
		});
	}

	private void runSearch() {
		if (search == null || matchesBox == null) {
			return;
		}
		String query = search.getText().trim();
		if (query.length() < 2) {
			showMatches(List.of());
			return;
		}
		searcher.search(query, matches -> SwingUtilities.invokeLater(() -> showMatches(matches)));
	}

	/** The dropdown rebuilt from the matches - folded away when there are none. */
	private void showMatches(List<Map<String, Object>> matches) {
		matchesBox.removeAll();
		for (Map<String, Object> match : matches) {
			matchesBox.add(matchRow(match));
		}
		matchesBox.setVisible(!matches.isEmpty());
		root.revalidate();
		root.repaint();
	}

	/** One dropdown row: the label, group rows bold - click selects.
	 * Full width, so the hover highlight spans the whole view. */
	private JComponent matchRow(Map<String, Object> match) {
		JLabel row = new JLabel(Model.str(match, "label"));
		boolean group = match.get("group") != null;
		if (group) {
			row.setFont(row.getFont().deriveFont(java.awt.Font.BOLD));
		}
		// Ship-combat rows announce themselves before selection: sea-blue
		// with the sailing skill icon after the name (REQ-SC-1/16; an image
		// icon, so the ASCII glyph gate stays clean).
		if (Model.flag(match, "naval")) {
			row.setForeground(ResultCards.SEA);
			row.setToolTipText("Ship combat: fought from your boat");
			Icon sail = cards.sailingIconSmall();
			if (sail != null) {
				row.setIcon(sail);
				row.setHorizontalTextPosition(SwingConstants.LEADING);
				row.setIconTextGap(5);
			}
		}
		row.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(
			Integer.MAX_VALUE, row.getPreferredSize().height));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				pick(match);
			}

			public void mouseEntered(MouseEvent e) {
				row.setOpaque(true);
				row.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				row.repaint();
			}

			public void mouseExited(MouseEvent e) {
				row.setOpaque(false);
				row.repaint();
			}
		});
		return row;
	}

	private void pick(Map<String, Object> match) {
		Object group = match.get("group");
		if (group instanceof String) {
			commands.send("select", Map.of("query", group));
		} else {
			String pickedVersion = Model.str(match, "version");
			commands.send("select", pickedVersion == null
				? Map.of("id", Model.id(match, "id"))
				: Map.of("id", Model.id(match, "id"), "version", pickedVersion));
		}
		clearMatches();
	}

	/** The search box emptied and its dropdown folded away. */
	private void clearMatches() {
		search.setText("");
		showMatches(List.of());
	}

	/** The reference Core stores; JDK types only cross the seam. */
	public Function<Map<String, Object>, JComponent> asFunction() {
		return page -> root();
	}

	/** A new page (or hello) arrived - repaint the mounted root. */
	/** Rebuilds are COALESCED (freeze reports 2026-08-14: each publish
	 * rebuilt the full tree synchronously, so any publish burst locked
	 * the EDT for their combined duration - the dumps' signature was an
	 * event-flooded EDT with huge cpu=). A 50ms debounce turns any storm
	 * into at most ~20 rebuilds a second, and logs bursts so a publish
	 * loop shows itself instead of freezing the client. */
	private final AtomicInteger pendingRepaints =
		new AtomicInteger();
	private Timer repaintDebounce;

	public void onModelChanged() {
		pendingRepaints.incrementAndGet();
		SwingUtilities.invokeLater(() -> {
			if (repaintDebounce == null) {
				repaintDebounce = Ui.once(50, e -> {
					int burst = pendingRepaints.getAndSet(0);
					if (burst > 20) {
						log.warn("Loadout Lab UI: {} publishes coalesced into one rebuild"
							+ " - a publish loop upstream?", burst);
					}
					repaint();
				});
			}
			repaintDebounce.restart();
		});
	}

	/** Boolean param chips: label -> params-node key. Rendered from the
	 * model (never local state) - a chip is checked because Core says so. */
	private static final String[][] CHIPS = {
		{"Spec", "specWeapon"},
		{"Thralls", "thralls"},
	};

	/** Built per repaint; ResultCards mounts it under the mob list. */
	JButton addMobButton;
	private JPanel chipRow;
	/** The dev-mode Max DPS | Balanced | Tanky control's own row. */
	private JPanel modeRow;
	private JPanel invRow;
	private JPanel rosterArea;
	private JPanel countsRow;
	private JPanel footerRow;
	private JButton undoButton;
	private JButton redoButton;

	/** Uniform chip config: every control in the row shares it. */
	private static JButton chip(String text, String tooltip, Runnable onClick) {
		JButton button = new JButton(text);
		button.setFocusable(false);
		button.setMargin(new Insets(1, 6, 1, 6));
		if (tooltip != null) {
			button.setToolTipText(tooltip);
		}
		button.addActionListener(e -> onClick.run());
		return button;
	}

	private static final Color CHIP_ON = ResultCards.ACCENT;
	private static final Color CHIP_OFF = new Color(0x969696);

	/** The classic parameter pill: flat, rounded, accent when active. */
	static JLabel pill(String text, boolean on, String tooltip, Runnable onClick) {
		return pill(text, on, !on, tooltip, onClick);
	}

	/** A classic pill that paints its new state IMMEDIATELY on click
	 * (field report 2026-08-12: "tiles don't highlight until the search
	 * is complete") - the arriving page then rewrites every pill from
	 * Core's truth, so an optimistic guess that Core refuses corrects
	 * itself on the next publish. */
	static JLabel pill(String text, boolean on, boolean pendingOn,
		String tooltip, Runnable onClick) {
		JLabel label = new JLabel(text);
		paintPill(label, on);
		label.setToolTipText(tooltip);
		Ui.onClick(label, () -> {
			paintPill(label, pendingOn);
			label.repaint();
			onClick.run();
		});
		return label;
	}

	/** The Task chip: the slayer skill icon + "Task" (field ask
	 * 2026-08-15), always the FIRST chip. */
	private JLabel taskPill(boolean on, boolean pendingOn,
		String tooltip, Runnable onClick) {
		JLabel pill = pill("Task", on, pendingOn, tooltip, onClick);
		if (spriteManager != null) {
			spriteManager.getSpriteAsync(net.runelite.api.SpriteID.SKILL_SLAYER, 0, img ->
				SwingUtilities.invokeLater(() -> {
					pill.setIcon(Ui.icon(img, 14));
					pill.setIconTextGap(4);
				}));
		}
		return pill;
	}

	private static void paintPill(JLabel label, boolean on) {
		label.setForeground(on ? CHIP_ON : CHIP_OFF);
		label.setBorder(new RoundedBorder(on ? CHIP_ON : ColorScheme.MEDIUM_GRAY_COLOR, 2, 8));
	}

	/** The defensive mode as one 3-way control in the chip area (Andrew
	 * 2026-10-21: "a 3/way tab instead of a chip but in the same area"; a
	 * filled square tab "looks bad" - picked look B): one rounded pill
	 * outline like the chips, three words split by thin bars, the active
	 * word in the accent. */
	/** The mode control's row: centered, with a thin divider below that
	 * sets it apart from the chip row. */
	static JPanel modeRowPanel() {
		JPanel row = Ui.panel(new FlowLayout(FlowLayout.CENTER, 0, 4));
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createEmptyBorder(2, 8, 4, 8),
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR)));
		row.setVisible(false);
		return row;
	}

	static JPanel modeTabs(String mode, CommandSink commands) {
		// What each mode optimises and what the card and the swaps show in it
		// (Andrew 2026-10-22: "better descriptions to what the differences
		// are and what you'll see in regular/ladder results").
		String[][] options = {
			{"MAX_DPS", "Max DPS", Ui.text("mode.max")},
			{"BALANCED", "Balanced", Ui.text("mode.balanced")},
			{"TANKY", "Tanky", Ui.text("mode.tanky")}};
		JPanel tabs = Ui.clear(new FlowLayout(FlowLayout.LEFT, 0, 0));
		tabs.setBorder(new RoundedBorder(ColorScheme.MEDIUM_GRAY_COLOR, 2, 4));
		List<JLabel> words = new ArrayList<>();
		for (String[] option : options) {
			if (!words.isEmpty()) {
				JLabel bar = new JLabel("|");
				bar.setForeground(new Color(0x505050));
				tabs.add(bar);
			}
			JLabel word = new JLabel(option[1]);
			word.setName(option[0]);
			word.setFont(FontManager.getRunescapeBoldFont());
			word.setBorder(BorderFactory.createEmptyBorder(1, 7, 1, 7));
			word.setToolTipText(option[2]);
			word.setForeground(option[0].equals(mode) ? CHIP_ON : CHIP_OFF);
			Ui.onClick(word, () -> {
				// Instant feedback; the arriving page confirms.
				for (JLabel other : words) {
					other.setForeground(other == word ? CHIP_ON : CHIP_OFF);
					other.repaint();
				}
				commands.set("optimizeMode", option[0]);
			});
			words.add(word);
			tabs.add(word);
		}
		return tabs;
	}

	/** Why the card being viewed stands down its Arceuus casts (an
	 * autocast from another book), or null (ISS-01M5K9KFJH7EZ0GMAKNGZ4K2JG,
	 * Andrew 2026-10-22: the Whisperer's Earth Surge kept the chips lit). */
	static String viewedStandDown(Map<String, Object> page) {
		String tab = ResultCards.effectiveTab(page);
		Map<String, Object> styles = Model.map(lensedMob(page), "styles");
		Map<String, Object> node = tab == null ? null : Model.map(styles, tab);
		return Model.str(Model.map(node, "yours"), "arceuusStandDown");
	}

	/** A chip shown but off: greyed, the reason in its tooltip, no click. */
	static JLabel disabledPill(String text, String tooltip) {
		JLabel label = new JLabel(text);
		paintPill(label, false);
		label.setEnabled(false);
		label.setToolTipText(tooltip);
		return label;
	}

	private static final String[] SIM_KEYS = {"simAttack", "simStrength", "simDefence", "simRanged", "simMagic", "simPrayer", "simHitpoints"};
	private static final String[] SIM_ARGS = {"attack", "strength", "defence", "ranged", "magic", "prayer", "hitpoints"};
	private static final String[] SIM_NAMES = {"Att", "Str", "Def", "Rng", "Mag", "Pray", "HP"};
	/** Whether the stat sim's boxes are open - a view choice kept across publishes. */
	private boolean statsOpen;

	/** The stat sim chip (ISS-01M5QY13K6YHTYDW1CCYD30CSY, Andrew 2026-10-24:
	 * "sim as any stat, defaulted to your current ones"): "Stats", or the
	 * simmed levels with the cape marked; it opens seven boxes holding the
	 * simmed or your live level, a Max cape tick, Max (all 99 and the cape)
	 * and Mine. Every edit is one sim-stats command; a box left at your own
	 * level sends 0 = yours, so a level-up keeps following you. */
	private JComponent statsChip(Map<String, Object> params) {
		String mineText = Model.str(params, "myLevels");
		String[] mine = mineText == null ? new String[0] : mineText.split("/");
		String[] shown = new String[SIM_KEYS.length];
		boolean any = false;
		for (int i = 0; i < SIM_KEYS.length; i++) {
			int sim = Model.id(params, SIM_KEYS[i]);
			any |= sim > 0;
			shown[i] = sim > 0 ? String.valueOf(sim) : mine.length == 7 ? mine[i] : "?";
		}
		boolean cape = Model.flag(params, "simMaxCape");
		boolean on = any || cape;
		JPanel holder = Ui.panel(new BorderLayout(0, 2));
		JPanel editor = Ui.panel(new WrapLayout(FlowLayout.LEFT, 3, 1));
		JTextField[] boxes = new JTextField[SIM_KEYS.length];
		JCheckBox capeBox = new JCheckBox("Max cape", cape);
		Runnable send = () -> {
			Map<String, Object> args = new HashMap<>();
			for (int i = 0; i < boxes.length; i++) {
				int level = parseLevel(boxes[i].getText());
				args.put(SIM_ARGS[i], mine.length == 7 && String.valueOf(level).equals(mine[i]) ? 0 : level);
			}
			args.put("maxCape", capeBox.isSelected());
			commands.send("sim-stats", args);
		};
		for (int i = 0; i < boxes.length; i++) {
			boxes[i] = new JTextField(shown[i], 2);
			boxes[i].setToolTipText(SIM_NAMES[i] + " to sim (1-99)");
			boxes[i].addActionListener(e -> send.run());
			editor.add(Ui.small(SIM_NAMES[i], ResultCards.LIGHT));
			editor.add(boxes[i]);
		}
		capeBox.setOpaque(false);
		capeBox.setForeground(ResultCards.LIGHT);
		capeBox.setFocusable(false);
		capeBox.setToolTipText("Sim the max cape, and the combos you can make with the capes you own");
		capeBox.addActionListener(e -> send.run());
		editor.add(capeBox);
		JLabel max = Ui.small("Max", ResultCards.ACCENT);
		Ui.onClick(max, () -> {
			for (JTextField box : boxes) {
				box.setText("99");
			}
			capeBox.setSelected(true);
			send.run();
		});
		JLabel mineLabel = Ui.small("Mine", ResultCards.ACCENT);
		Ui.onClick(mineLabel, () -> {
			Map<String, Object> args = new HashMap<>();
			for (String arg : SIM_ARGS) {
				args.put(arg, 0);
			}
			args.put("maxCape", false);
			commands.send("sim-stats", args);
		});
		editor.add(max);
		editor.add(mineLabel);
		editor.setVisible(statsOpen);
		holder.add(pill(on ? "Stats " + String.join("/", shown) + (cape ? " +cape" : "") : "Stats", on, on,
			Ui.text("chip.stats.tip"), () -> {
				statsOpen = !statsOpen;
				editor.setVisible(statsOpen);
				holder.revalidate();
			}), BorderLayout.NORTH);
		holder.add(editor, BorderLayout.CENTER);
		return holder;
	}

	/** A box's level: 1-99, or 0 (= yours) for anything else. */
	private static int parseLevel(String text) {
		try {
			int level = Integer.parseInt(text.trim());
			return level >= 1 && level <= 99 ? level : 0;
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	private JLabel paramChip(String label, String key, boolean selected) {
		return pill(label, selected, label + (selected ? " on" : " off"),
			() -> commands.set(key, !selected));
	}

	/** True when any mob on the page passes the test. */
	private static boolean anyMob(Map<String, Object> page, Predicate<Map<String, Object>> test) {
		for (Map<String, Object> entry : Model.list(page, "entries")) {
			for (Map<String, Object> mob : Model.list(entry, "mobs")) {
				if (test.test(mob)) {
					return true;
				}
			}
		}
		return false;
	}

	/** A classic count pill: "-N" red / "+N" green / "~N" grey, muted at
	 * zero, always visible; click = the manage menu + add-by-search. */
	private void pillChip(Map<String, Object> counts, String listKey, String sigil,
		Color[] palette, String removeVerb, String command, String addPrompt,
		List<Map<String, Object>> supplyCatalog) {
		List<Map<String, Object>> items = Model.list(counts, listKey);
		JLabel pill = new JLabel(sigil + items.size());
		boolean any = !items.isEmpty();
		pill.setForeground(any ? palette[0] : palette[1]);
		pill.setBorder(new RoundedBorder(any ? palette[2]
			: ColorScheme.MEDIUM_GRAY_COLOR, 2, 22));
		Ui.onClick(pill, () -> {
			JPopupMenu menu = new JPopupMenu();
			// The classic grey chip also edits the SUPPLY DEFAULTS -
			// one submenu per category, plus Arceuus access.
			if (supplyCatalog != null && !supplyCatalog.isEmpty()) {
				for (Map<String, Object> category : supplyCatalog) {
					String key = Model.str(category, "category");
					String current = Model.str(category, "current");
					boolean detect = current == null || current.startsWith("DETECT");
					JMenu sub = new JMenu(Model.str(category, "label"));
					if (!"arceuusAccess".equals(key)) {
						sub.add(supplyChoice(key, "DETECT_BEST", "Detect best", detect));
						sub.add(supplyChoice(key, "NONE", "None", "NONE".equals(current)));
					}
					for (Map<String, Object> option : Model.list(category, "options")) {
						String optionKey = Model.str(option, "key");
						sub.add(supplyChoice(key, optionKey, Model.str(option, "name"), optionKey.equals(current)
							|| ("arceuusAccess".equals(key) && "DETECT_BEST".equals(optionKey) && detect)));
					}
					menu.add(sub);
				}
				menu.addSeparator();
			}
			for (Map<String, Object> item : items) {
				int id = Model.id(item, "id");
				String itemName = Model.str(item, "name");
				Ui.item(menu, removeVerb + itemName,
					() -> commands.send(command, Map.of("itemId", id, "label", itemName)));
			}
			if (any) {
				menu.addSeparator();
			}
			Ui.item(menu, addPrompt, cards.searchItem(addPrompt, command));
			Ui.show(menu, pill);
		});
		countsRow.add(pill);
	}

	/** A pill that WEARS its value (field spec 2026-08-14: no bare text
	 * entries in the chip row) - clicking swaps in the entry field;
	 * Enter commits (the page repaint restores the pill), Escape just
	 * collapses. */
	private JComponent valueChip(String label, boolean active, String tooltip,
		String currentText, Consumer<String> onCommit) {
		CardLayout flip = new CardLayout();
		JPanel holder = Ui.panel(flip);
		JTextField field = new JTextField(currentText, 5);
		field.setToolTipText(tooltip);
		JLabel pill = pill(label, active, active, tooltip, () -> {
			flip.show(holder, "field");
			field.requestFocusInWindow();
			field.selectAll();
		});
		holder.add(pill, "pill");
		holder.add(field, "field");
		field.addActionListener(e -> onCommit.accept(field.getText().trim()));
		field.addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent e) {
				if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
					flip.show(holder, "pill");
				}
			}
		});
		flip.show(holder, "pill");
		return holder;
	}

	/** One radio-style supply-default choice (checked = current). */
	private JMenuItem supplyChoice(String category, String key,
		String label, boolean selected) {
		return Ui.check(label, selected, () -> commands.send("set-supply-default",
			Map.of("category", category, "choice", key)));
	}

	private synchronized JComponent root() {
		if (root == null) {
			root = Ui.panel(new BorderLayout(0, 6));
			JPanel top = Ui.column(ColorScheme.DARK_GRAY_COLOR);
			countsRow = Ui.panel(new WrapLayout(FlowLayout.CENTER, 4, 2));
			top.add(countsRow);
			search = new JTextField();
			search.setToolTipText("Search a monster or group");
			// Suggestions LIVE as you type (150ms debounce) - but nothing
			// selects or computes until an explicit pick: a click on a
			// row, or Enter (field refinement 2026-08-15).
			search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
				public void insertUpdate(DocumentEvent e) {
					searchDebounce.restart();
				}

				public void removeUpdate(DocumentEvent e) {
					searchDebounce.restart();
				}

				public void changedUpdate(DocumentEvent e) {
					searchDebounce.restart();
				}
			});
			search.addActionListener(e -> {
				if (matchesBox.getComponentCount() > 0) {
					Component first = matchesBox.getComponent(0);
					for (MouseListener l : first.getMouseListeners()) {
						l.mouseClicked(null);
						return;
					}
				}
				String query = search.getText().trim();
				if (query.length() < 2) {
					return;
				}
				searcher.search(query, matches -> SwingUtilities.invokeLater(() -> {
					if (matches.size() == 1) {
						matchesBox.removeAll();
						pick(matches.get(0));
						return;
					}
					showMatches(matches);
				}));
			});
			JPanel searchArea = Ui.panel(new BorderLayout(4, 0));
			// Compact history arrows ride BESIDE the search (the classic
			// header pattern), not loose in the chip row.

			undoButton = chip("<", "Undo", () -> commands.send("undo"));
			redoButton = chip(">", "Redo", () -> commands.send("redo"));
			JLabel clearSearch = Ui.xLabel("Clear the search", this::clearMatches);
			clearSearch.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 6));
			JPanel searchBox = new JPanel(new BorderLayout());
			searchBox.setBackground(new Color(0x2B2B2B));
			searchBox.setBorder(new RoundedBorder(new Color(0x504E46), 3, 4));
			search.setOpaque(false);
			search.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 2));
			searchBox.add(search, BorderLayout.CENTER);
			JPanel searchTools = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 2, 0));
			searchTools.add(clearSearch);
			for (JButton arrow : new JButton[]{undoButton, redoButton}) {
				arrow.setContentAreaFilled(false);
				arrow.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
				arrow.setForeground(CHIP_OFF);
				searchTools.add(arrow);
			}
			searchBox.add(searchTools, BorderLayout.EAST);
			JPanel searchRow = Ui.darker(new BorderLayout(4, 0));
			searchRow.add(searchBox, BorderLayout.CENTER);
			searchArea.add(searchRow, BorderLayout.NORTH);
			matchesBox = Ui.column(ColorScheme.DARKER_GRAY_COLOR);
			matchesBox.setVisible(false);
			searchArea.add(matchesBox, BorderLayout.CENTER);
			top.add(searchArea);
			rosterArea = Ui.darker(new BorderLayout());
			rosterArea.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
			top.add(rosterArea);
			modeRow = modeRowPanel();
			top.add(modeRow);
			chipRow = Ui.panel(new WrapLayout(FlowLayout.LEFT, 4, 2));
			top.add(chipRow);
			// The inventory slider rides its own row under the chips.
			invRow = Ui.panel(new BorderLayout());
			top.add(invRow);
			root.add(top, BorderLayout.NORTH);
			JPanel resultsArea = Ui.column(ColorScheme.DARK_GRAY_COLOR);
			cardArea = Ui.column(ColorScheme.DARK_GRAY_COLOR);
			resultsArea.add(cardArea);
			// The computing notice sits BELOW everything the pending page
			// shows (field report: between the chips and the mob list was weird).
			waitingSlot = Ui.panel(new BorderLayout());
			loader = new AsciiLoader();
			JPanel loaderRow = Ui.panel(new FlowLayout(FlowLayout.CENTER, 0, 4));
			loaderRow.add(loader);
			waitingSlot.add(loaderRow, BorderLayout.CENTER);
			JLabel computing = Ui.label("Computing...", CHIP_OFF);
			computing.setHorizontalAlignment(SwingConstants.CENTER);
			waitingSlot.add(computing, BorderLayout.SOUTH);
			waitingSlot.setVisible(false);
			resultsArea.add(waitingSlot);
			root.add(resultsArea, BorderLayout.CENTER);
			// Footer actions (the classic position): below the results.
			footerRow = Ui.panel(new WrapLayout(FlowLayout.CENTER, 4, 2));
			root.add(footerRow, BorderLayout.SOUTH);
		}
		repaint();
		return root;
	}

	/** The Wiki calc chip's tooltip: the base line, plus - when the
	 * viewed card folds spec or thralls into its number - the sum that
	 * reconciles our shown dps with what the calculator will display
	 * (the bare set). Mirrors the engine's view derivation. */
	static String wikiCalcTip(Map<String, Object> page) {
		String head = Ui.text("wiki.head");
		String base = Ui.tip(head);
		Map<String, Object> params = ResultCards.firstParams(page);
		Map<String, Object> mob = lensedMob(page);
		Map<String, Object> styles = Model.map(mob, "styles");
		if (params == null || styles == null) {
			return base;
		}
		boolean bis = Model.flag(params, "viewingBis");
		String tab = Model.str(params, "selectedTab");
		Map<String, Object> node = tab == null || tab.isEmpty() ? null : Model.map(styles, tab);
		if (node == null) {
			double best = -1;
			for (String style : new String[]{"melee", "ranged", "magic"}) {
				Map<String, Object> candidate = Model.map(styles, style);
				double dps = candidate == null ? -1
					: Model.num(candidate, bis ? "bisTabDps" : "tabDps");
				if (candidate != null && dps > best) {
					best = dps;
					node = candidate;
				}
			}
		}
		Map<String, Object> card = Model.map(node, bis ? "bis" : "yours");
		if (card == null) {
			return base;
		}
		double set = Model.num(card, "dps");
		double specDps = Model.num(Model.map(card, "spec"), "dpsAdded");
		double thrallsDps = Model.thrallsFor(card,
			Model.num(Model.map(Model.list(page, "entries").get(0), "thralls"), "dps"));
		// The breakdown reads as a little ledger, not a sentence (field
		// ask 2026-08-21: "less dense and more informative") - the same
		// table the spec cell wears, built once in Ui. ALWAYS shown
		// (Andrew 2026-09-02): the number they will see is the subtext.
		String ledger = Ui.ledger(set, "what the calc shows", specDps, specDps > 0, thrallsDps);
		// ToA: the link carries the card's raid level; say so, and where
		// their field lives for anyone who wants a different one.
		int invo = Model.id(params, "toaInvocation");
		String toa = Model.flag(mob, "invocationScaled") && invo > 0
			? String.format(Ui.text("wiki.toa"), invo)
			: "";
		return Ui.tip(head, "<br><br>", ledger, toa);
	}

	private Map<String, Object> lastRenderedPage;
	private int skippedIdentical;

	/** True when any entry of the page carries the flag. */
	private static boolean anyEntry(Map<String, Object> page, String flag) {
		for (Map<String, Object> entry : Model.list(page, "entries")) {
			if (Model.flag(entry, flag)) {
				return true;
			}
		}
		return false;
	}

	private synchronized void repaint() {
		if (cardArea == null) {
			return;
		}
		Map<String, Object> page = this.page.get();
		if (page != null && page.equals(lastRenderedPage)) {
			skippedIdentical++;
			return;
		}
		lastRenderedPage = page;
		long rebuildStart = System.nanoTime();
		Map<String, Object> params = ResultCards.firstParams(page);
		// The global counts build from the PAGE alone (field report
		// 2026-08-14 x2: the trio hid until a search - the idle page has
		// no entries, so the params gate swallowed it).
		countsRow.removeAll();
		Map<String, Object> counts = Model.map(page, "counts");
		if (counts != null) {
			// The classic trio: always visible, muted at zero.
			pillChip(counts, "simmedItems", "+", ResultCards.SIM_PALETTE,
				"Stop simming ", "toggle-sim",
				"Sim an item as owned...", null);
			pillChip(counts, "excludedItems", "-", ResultCards.EXCLUDE_PALETTE,
				"Allow again: ", "toggle-exclusion",
				"Exclude an item (search)...", null);
			pillChip(counts, "filteredItems", "~", ResultCards.FILTER_PALETTE,
				"Stop filtering: ", "toggle-always-filter",
				"Always show in bank filter...", Model.list(page, "supplyCatalog"));
		}
		countsRow.setVisible(true);
		chipRow.removeAll();
		chipRow.setVisible(params != null);
		modeRow.removeAll();
		modeRow.setVisible(false);
		// The inventory slider rides with the chips. It is a per-result
		// control - each mob, group and raid brings its own default swap
		// count - so on the idle panel it showed a number belonging to
		// nothing, and any change to it was overwritten by the next search
		// (field report 2026-08-27). Built inside the params branch below,
		// so without this it simply kept whatever it last drew.
		invRow.setVisible(params != null);
		if (params == null) {
			invRow.removeAll();
		}
		if (params != null) {
			// Members chips (task, thralls, D charge) disappear under the
			// F2P lock - the computeArgs veto already keeps them out of the
			// math, and a control that can do nothing is noise (field ask
			// 2026-08-27). Their params survive for the untick.
			boolean f2pLocked = Model.flag(params, "f2pOnly");
			Map<String, Object> lensed = lensedMob(page);
			boolean lensNaval = Model.flag(lensed, "naval");
			// Defensive modes (ISS-01M5G5CZC9SK46499FACRSJ8ZT), for every
			// player since 2026-10-22 (ISS-01M5JBYYWHQNTM0CGNYWQBNYX6). Its own
			// centered row above the chips (Andrew 2026-10-21: "more centered
			// in screen and distinguished from the chips").
			modeRow.add(modeTabs(Model.str(params, "optimizeMode"), commands));
			modeRow.setVisible(true);
			// Arceuus casts blocked (Cure Me camps Lunar; a fight book elsewhere
			// with no swap - the Sire): the chips do nothing and hide, like on
			// a boat (Andrew 2026-09-09: "it should disable it").
			boolean lensLunar = Model.flag(lensed, "arceuusBlocked");
			String standDown = viewedStandDown(page);
			Map<String, Object> history = Model.map(page, "history");
			if (history != null && undoButton != null) {
				undoButton.setEnabled(Model.flag(history, "canUndo"));
				undoButton.setToolTipText(Model.str(history, "undoLabel") == null
					? "Undo" : "Undo: " + Model.str(history, "undoLabel"));
				redoButton.setEnabled(Model.flag(history, "canRedo"));
				redoButton.setToolTipText(Model.str(history, "redoLabel") == null
					? "Redo" : "Redo: " + Model.str(history, "redoLabel"));
			}
			// Raids are never a slayer-task context (Andrew, 2026-08-31:
			// Skeletal Mystic's corpus slayer flag lit the chip for CoX).
			boolean raid = anyEntry(page, "raidSelection");
			boolean anyTaskOnly = !raid && anyMob(page, m -> Model.flag(m, "taskOnly"));
			boolean anySlayer = !raid && anyMob(page, m -> Model.flag(m, "slayerMonster"));
			boolean anyWilderness = anyMob(page, m -> Model.flag(m, "wilderness"));
			boolean anyGwd = anyMob(page, m -> Model.flag(m, "gwd"));
			// Every mob (and at least one) is a wilderness exclusive.
			boolean allExclusive = anyMob(page, m -> true) && !anyMob(page, m -> !Model.flag(m, "wildernessExclusive"));
			if (anyTaskOnly && !f2pLocked) {
				chipRow.add(taskPill(true, true, "Task-only boss - always on", () -> {
					}));
			} else if (anySlayer && !f2pLocked) {
				chipRow.add(taskPill(Model.flag(params, "onTask"), !Model.flag(params, "onTask"),
					Ui.text("chip.task.tip"),
					() -> commands.set("onTask", !Model.flag(params, "onTask"))));
			}
			for (String[] entry : CHIPS) {
				// Thralls hide under the F2P lock AND for a sea lens (thrall
				// resurrections cannot be cast on a boat - confirmed
				// 2026-08-31; a chip that does nothing is noise).
				if (("thralls".equals(entry[1])) && (f2pLocked || lensNaval || lensLunar)) {
					continue;
				}
				if ("thralls".equals(entry[1]) && standDown != null) {
					chipRow.add(disabledPill(entry[0], "Stands down: " + standDown + " - thralls are cast from Arceuus"));
					continue;
				}
				chipRow.add(paramChip(entry[0], entry[1], Model.flag(params, entry[1])));
			}
			// F2P: shown on a non-members world so it can be turned OFF to
			// preview members gear (field report 2026-08-25 - the merge-back
			// dropped this chip, leaving the filter stuck on). Gated on
			// f2pWorld, NOT f2pOnly, or unticking it would hide the control.
			if (Model.flag(params, "f2pWorld")) {
				chipRow.add(paramChip("F2P", "f2pOnly", Model.flag(params, "f2pOnly")));
			}
			// The classic gates (field report 2026-08-14: Sire showed
			// Wildy + Raid boost): Wildy only for LISTED wilderness mobs
			// (exclusives pinned always-in), the raid chip only where the
			// raid supplies a boost - named after that potion.
			if (anyWilderness && allExclusive) {
				chipRow.add(pill("Wildy", true, true,
					"Wilderness-exclusive - always in", () -> {
					}));
			} else if (anyWilderness) {
				chipRow.add(paramChip("Wildy", "inWilderness",
					Model.flag(params, "inWilderness")));
			}
			// GWD (ISS-01M5G9KPDQWBR014Z10F79B29T): the Ancient chip adds a
			// Zaros item to the protection kit - the Nex respite area's banker.
			if (anyGwd) {
				chipRow.add(pill("Ancient", Model.flag(params, "gwdAncient"),
					Ui.text("chip.ancient.tip"),
					() -> commands.set("gwdAncient", !Model.flag(params, "gwdAncient"))));
			}
			// PvP world (Ragnarrok, 2026-10-20): any mob, cycles off / PvP /
			// High Risk. Shown on a PvP world, while on, or when the
			// settings ask for it (planning from a normal world).
			int pvp = Model.id(params, "pvpWorld");
			if (pvp > 0 || Model.flag(params, "pvpWorldHere") || pvpChipGate.getAsBoolean()) {
				chipRow.add(pill(pvp >= 2 ? "PvP: High Risk" : "PvP", pvp > 0, true,
					Ui.text("chip.pvp.tip"),
					() -> commands.set("pvpWorld", (pvp + 1) % 3)));
			}
			// Cannons ride the chips section (Andrew, v2) for the LENSED
			// ship-eligible mob; the per-cannon pickers live on the card.
			if (lensNaval) {
				int cannons = Model.id(params, "cannonCount");
				chipRow.add(pill("Cannons " + cannons, cannons > 0, (cannons + 1) % 3 > 0,
					Ui.text("chip.cannons.tip"),
					() -> commands.set("cannonCount", (cannons + 1) % 3)));
			}
			int dCharge = Model.id(params, "deathCharge");
			if (!f2pLocked && !lensLunar && standDown != null) {
				chipRow.add(disabledPill("D-charge",
					"Stands down: " + standDown + " - Death Charge is cast from Arceuus"));
			} else if (!f2pLocked && !lensLunar) {
				chipRow.add(pill(dCharge == 0 ? "D-charge" : dCharge == 1 ? "D-charge on" : "D-charge+",
					dCharge > 0, (dCharge + 1) % 3 > 0, Ui.text("chip.dcharge.tip"),
					() -> commands.set("deathCharge", (dCharge + 1) % 3)));
			}
			if (anyMob(page, m -> Model.flag(m, "multiAble"))) {
				// The Multi chip (Andrew 2026-10-19: "single -> multi x3 -> multi
				// x9", beside the other search chips): the AoE tools priced at a
				// stack of targets, cycling single / x3 / x9.
				boolean multi = Model.flag(params, "multiView");
				int stack = Model.id(params, "multiStack");
				boolean last = multi && stack >= 9;
				chipRow.add(pill(multi ? "Multi x" + stack : "Single", multi, !last,
					Ui.text("chip.multi.tip"),
					() -> {
						if (!multi) {
							commands.set("multiStack", 3);
							commands.set("multiView", true);
						} else if (!last) {
							commands.set("multiStack", 9);
						} else {
							commands.set("multiView", false);
						}
					}));
			}
			if (anyMob(page, m -> Model.flag(m, "invocationScaled"))) {
				int invo = Model.id(params, "toaInvocation");
				int nextInvo = invo >= 540 ? 0 : invo >= 300 ? 540 : invo >= 150 ? 300 : 150;
				chipRow.add(pill("Invo " + invo, invo > 0, nextInvo > 0,
					Ui.text("chip.invo.tip"),
					() -> commands.set("toaInvocation", nextInvo)));
			}
			int swaps = Model.id(params, "maxSwaps");
			// The classic inventory control is a SLIDER over the bench
			// size - every value reachable, not four presets.
			JPanel invBox = Ui.panel(new BorderLayout(4, 0));
			JLabel invLabel = Ui.label("Inv " + swaps, ResultCards.LIGHT);
			invBox.add(invLabel, BorderLayout.WEST);
			JSlider invSlider =
				new JSlider(0, 16, Math.max(0, Math.min(16, swaps)));
			invSlider.setPreferredSize(new Dimension(88, 18));
			invSlider.setBackground(ColorScheme.DARK_GRAY_COLOR);
			invSlider.setFocusable(false);
			invSlider.setToolTipText("Max gear swaps carried on the trip");
			invSlider.addChangeListener(e -> {
				invLabel.setText("Inv " + invSlider.getValue());
				// Recompute only when the drag settles, and only on a real
				// change - a slider fires continuously.
				if (!invSlider.getValueIsAdjusting() && invSlider.getValue() != swaps) {
					commands.set("maxSwaps", invSlider.getValue());
				}
			});
			invBox.add(invSlider, BorderLayout.CENTER);
			invBox.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
			invRow.removeAll();
			invRow.add(invBox, BorderLayout.CENTER);
			int budgetGp = Model.id(params, "upgradeBudgetGp");
			chipRow.add(valueChip(
				budgetGp > 0 ? "Budget " + Gp.format(budgetGp) : "Budget",
				budgetGp > 0,
				Ui.text("chip.budget.tip"),
				Gp.format(budgetGp), text -> commands.set("upgradeBudgetGp", Gp.parse(text))));
			chipRow.add(statsChip(params));
			if (wildyRiskGate.getAsBoolean() && (pvp > 0
				|| anyWilderness && (allExclusive || Model.flag(params, "inWilderness")))) {
				int riskGp = Model.id(params, "riskBudgetGp");
				// The CAP FLAG decides, never the number: the engine's
				// no-cap sentinel is itself 75k, so a raw > 0 test lit
				// this chip for a cap the compute ignored (field report
				// 2026-08-22).
				boolean capped = Model.flag(params, "riskCapped");
				chipRow.add(valueChip(
					capped ? "Risk " + Gp.format(riskGp) : "Risk cap",
					capped,
					Ui.text("chip.risk.tip")
						+ (pvp > 0 ? Ui.text("chip.risk.pvp") : ""),
					capped ? Gp.format(riskGp) : "", text -> {
					// Empty = clear: null falls back to uncapped.
					Map<String, Object> args = new HashMap<>();
					args.put("param", "riskBudgetGp");
					args.put("value", text.isEmpty() ? null : Gp.parse(text));
					commands.send("set-param", args);
				}));
				// High Risk worlds disable Protect Item (wiki).
				if (pvp < 2) {
					chipRow.add(paramChip("Protect item", "protectItem",
						Model.flag(params, "protectItem")));
				}
			}
			addMobButton = chip("+ Mob", Ui.text("chip.addmob.tip"), () -> {
				String query = JOptionPane.showInputDialog(root,
					"Add a monster to this result:", "Add mob",
					JOptionPane.PLAIN_MESSAGE);
				if (query != null && !query.trim().isEmpty()) {
					commands.send("add-mob", Map.of("query", query.trim()));
				}
			});
			addMobButton.setContentAreaFilled(false);
			addMobButton.setOpaque(false);
			addMobButton.setForeground(ResultCards.LIGHT);
			addMobButton.setBorder(new RoundedBorder(ColorScheme.DARKER_GRAY_HOVER_COLOR, 3, 10));
			JButton reload = chip("Reload", "Re-run with a fresh bank scan", () -> commands.send("recompute"));
			reload.setContentAreaFilled(false);
			reload.setOpaque(false);
			reload.setForeground(ResultCards.DIM);
			reload.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
			cards.setReload(reload);
			cards.setAddMob(addMobButton);
		}
		footerRow.removeAll();
		String reportText = Model.str(page, "reportText");
		if (reportText != null) {
			footerRow.add(chip("Copy report", "Copy the shown result as text", () -> {
				try {
					Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
						new java.awt.datatransfer.StringSelection(reportText), null);
				} catch (IllegalStateException ex) {
					// Clipboard busy - the classic panel guarded the same way.
				}
			}));
			// The exact-setup cross-check (field ask 2026-08-21: it lives
			// with the link-outs, not the bank tools): opens the VIEWED
			// setup - the engine derives tab and side from the view state.
			// The tooltip carries the reconciliation sum (field ask, same
			// day): the calc shows the bare SET; we fold spec + thralls.
			// The button CARRIES the card's resolved choice - it used to
			// send nothing and let the engine re-guess with a different
			// rule, so it could open a style the card was not showing.
			String wikiTab = ResultCards.effectiveTab(page);
			boolean wikiBis = Model.flag(params, "viewingBis");
			footerRow.add(chip("Wiki calc", wikiCalcTip(page),
				() -> commands.send("wiki-calc", Map.of("style", wikiTab, "bis", wikiBis))));
		}
		footerRow.add(chip("Discord", Ui.text("footer.discord.tip"),
			() -> net.runelite.client.util.LinkBrowser.browse("https://discord.gg/6GuS6J8em3")));
		if (isComputing) {
			// Reachable mid-compute, beside Discord (Andrew 2026-09-04).
			footerRow.add(chip("Cancel", Ui.text("footer.cancel.tip"), () -> commands.send("cancel")));
		}
		if (rosterArea != null) {
			rosterArea.removeAll();
			JPanel roster = cards.renderRoster(page);
			if (roster != null) {
				rosterArea.add(roster, BorderLayout.CENTER);
			}
			rosterArea.setVisible(roster != null);
		}
		cardArea.removeAll();
		// Mid-compute publishes keep the stage CLEAR (field report
		// 2026-08-20: undo drew the stale result above the loader) -
		// the controls above still rebuild; the reveal happens on
		// setComputing(false).
		JPanel rendered = isComputing ? null : cards.render(page);
		if (rendered != null && rendered.getComponentCount() > 0) {
			cardArea.add(rendered);
		} else if (!isComputing && !anyEntry(page, "pending")) {
			// The empty state says what to do - never beside the
			// computing notice.
			JLabel hint = Ui.label(Ui.text("hint.empty"), CHIP_OFF);
			hint.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));
			hint.setAlignmentX(0.5f);
			cardArea.add(hint);
		}
		root.revalidate();
		root.repaint();
		long rebuildMs = (System.nanoTime() - rebuildStart) / 1_000_000;
		if (rebuildMs > 30) {
			log.debug("perf: UI rebuild took {}ms ({} identical pages skipped since last)",
				rebuildMs, skippedIdentical);
		}
		skippedIdentical = 0;
	}

}
