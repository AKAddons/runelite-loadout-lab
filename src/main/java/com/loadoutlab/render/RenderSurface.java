package com.loadoutlab.render;

import java.awt.BorderLayout;
import java.util.Map;
import java.util.function.Function;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.runelite.client.ui.ColorScheme;

/**
 * The renderer handed to Core by reference (one-surface ruling): Core
 * calls it on the EDT with the latest page and mounts what it returns
 * inside Core's own panel. We keep the mounted root and repaint it in
 * place when a new page arrives, so Core does not need to re-mount
 * per page.
 */
@lombok.extern.slf4j.Slf4j
public class RenderSurface
{
	private final ResultCards cards;
	private final java.util.function.Supplier<Map<String, Object>> page;
	private final CommandSink commands;
	private final ItemPicker picker;
	private final Searcher searcher;
	private net.runelite.client.game.SpriteManager spriteManager;

	/** The small sailing skill icon - ship-eligible rows wear it instead of
	 * any text: "naval" is not player vernacular (Andrew, v2 review). */
	private java.util.function.Supplier<java.awt.image.BufferedImage> sailingIcon;

	public void setSailingIcon(java.util.function.Supplier<java.awt.image.BufferedImage> icon)
	{
		this.sailingIcon = icon;
	}

	/** True when the LENSED mob of any entry is ship-eligible. */
	private static boolean lensedNaval(Map<String, Object> page)
	{
		Map<String, Object> params = ResultCards.firstParams(page);
		int lens = params == null ? 0 : Model.id(params, "lensIndex");
		for (Map<String, Object> entry : Model.list(page, "entries"))
		{
			java.util.List<Map<String, Object>> entryMobs = Model.list(entry, "mobs");
			if (!entryMobs.isEmpty())
			{
				int shown = Math.min(Math.max(lens, 0), entryMobs.size() - 1);
				if (Model.flag(entryMobs.get(shown), "naval"))
				{
					return true;
				}
			}
		}
		return false;
	}

		javax.swing.Icon sailingIconSmall()
	{
		java.util.function.Supplier<java.awt.image.BufferedImage> supplier = sailingIcon;
		java.awt.image.BufferedImage img = supplier == null ? null : supplier.get();
		return img == null ? null : Ui.icon(img, 14);
	}

	public void setSpriteManager(net.runelite.client.game.SpriteManager spriteManager)
	{
		this.spriteManager = spriteManager;
	}

	/** Config gates the renderer honours: the compute animation and the
	 * wilderness risk controls. Both settings described behaviour they
	 * did not control until 2026-08-22 - the merge-back dropped the
	 * DisplayOptions bridge and nothing replaced it. */
	private java.util.function.BooleanSupplier animationGate = () -> true;
	private java.util.function.BooleanSupplier wildyRiskGate = () -> true;

	public void setDisplayGates(java.util.function.BooleanSupplier animation,
		java.util.function.BooleanSupplier wildyRisk)
	{
		this.animationGate = animation;
		this.wildyRiskGate = wildyRisk;
	}
	private JPanel root;
	private JPanel matchesBox;
	private final javax.swing.Timer searchDebounce;
	private JTextField search;
	private JPanel cardArea;
	private JPanel waitingSlot;
	private AsciiLoader loader;
	private volatile boolean isComputing;

	/** Compute-in-flight: shows the static Computing... slot. */
	public void setComputing(boolean computing)
	{
		isComputing = computing;
		javax.swing.SwingUtilities.invokeLater(() ->
		{
			if (waitingSlot == null)
			{
				return;
			}
			if (isComputing && cardArea != null)
			{
				// The classic contract: a compute clears the RESULTS - the
				// controls stay up (Core publishes the params immediately)
				// so a wrong parameter can be fixed without waiting out
				// the compute.
				cardArea.removeAll();
				cardArea.revalidate();
				cardArea.repaint();
			}
			if (loader != null)
			{
				boolean animate = animationGate.getAsBoolean();
				loader.setVisible(animate);
				// A sea compute draws from the sea moods (REQ-SC-17): the
				// pending page already carries the selection, so peek it.
				loader.setSea(lensedNaval(page.get()));
				loader.setRunning(isComputing && animate);
			}
			waitingSlot.setVisible(isComputing);
			waitingSlot.revalidate();
			waitingSlot.repaint();
			if (!isComputing)
			{
				// Remount whatever page landed during the compute - and
				// reset the identical-page skip so the reveal cannot be
				// deduped away.
				lastRenderedPage = null;
				repaint();
			}
		});
	}

	public RenderSurface(ResultCards cards, java.util.function.Supplier<Map<String, Object>> page,
		CommandSink commands, ItemPicker picker, Searcher searcher)
	{
		this.cards = cards;
		this.page = page;
		this.commands = commands;
		this.picker = picker;
		this.searcher = searcher;
		// The classic search cadence: 150ms debounce, 2+ characters.
		this.searchDebounce = new javax.swing.Timer(150, e -> runSearch());
		this.searchDebounce.setRepeats(false);
	}

	private void runSearch()
	{
		if (search == null || matchesBox == null)
		{
			return;
		}
		String query = search.getText().trim();
		if (query.length() < 2)
		{
			matchesBox.removeAll();
			matchesBox.setVisible(false);
			root.revalidate();
			return;
		}
		searcher.search(query, matches -> javax.swing.SwingUtilities.invokeLater(() ->
		{
			matchesBox.removeAll();
			for (Map<String, Object> match : matches)
			{
				matchesBox.add(matchRow(match));
			}
			matchesBox.setVisible(!matches.isEmpty());
			root.revalidate();
			root.repaint();
		}));
	}

	/** One dropdown row: the label, group rows bold - click selects.
	 * Full width, so the hover highlight spans the whole view. */
	private javax.swing.JComponent matchRow(Map<String, Object> match)
	{
		javax.swing.JLabel row = new javax.swing.JLabel(Model.str(match, "label"));
		boolean group = match.get("group") != null;
		if (group)
		{
			row.setFont(row.getFont().deriveFont(java.awt.Font.BOLD));
		}
		// Ship-combat rows announce themselves before selection: sea-blue
		// with the sailing skill icon after the name (REQ-SC-1/16; an image
		// icon, so the ASCII glyph gate stays clean).
		if (Model.flag(match, "naval"))
		{
			row.setForeground(new java.awt.Color(120, 175, 215));
			row.setToolTipText("Ship combat: fought from your boat");
			javax.swing.Icon sail = sailingIconSmall();
			if (sail != null)
			{
				row.setIcon(sail);
				row.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
				row.setIconTextGap(5);
			}
		}
		row.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 8, 3, 8));
		row.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new java.awt.Dimension(
			Integer.MAX_VALUE, row.getPreferredSize().height));
		row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		row.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				pick(match);
			}

			@Override
			public void mouseEntered(java.awt.event.MouseEvent e)
			{
				row.setOpaque(true);
				row.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
				row.repaint();
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e)
			{
				row.setOpaque(false);
				row.repaint();
			}
		});
		return row;
	}

	private void pick(Map<String, Object> match)
	{
		Object group = match.get("group");
		if (group instanceof String)
		{
			commands.send("select", Map.of("query", group));
		}
		else
		{
			String pickedVersion = Model.str(match, "version");
			commands.send("select", pickedVersion == null
				? Map.of("id", Model.id(match, "id"))
				: Map.of("id", Model.id(match, "id"), "version", pickedVersion));
		}
		search.setText("");
		matchesBox.removeAll();
		matchesBox.setVisible(false);
		root.revalidate();
		root.repaint();
	}

	/** The reference Core stores; JDK types only cross the seam. */
	public Function<Map<String, Object>, JComponent> asFunction()
	{
		return page -> root();
	}

	/** A new page (or hello) arrived - repaint the mounted root. */
	/** Rebuilds are COALESCED (freeze reports 2026-08-14: each publish
	 * rebuilt the full tree synchronously, so any publish burst locked
	 * the EDT for their combined duration - the dumps' signature was an
	 * event-flooded EDT with huge cpu=). A 50ms debounce turns any storm
	 * into at most ~20 rebuilds a second, and logs bursts so a publish
	 * loop shows itself instead of freezing the client. */
	private final java.util.concurrent.atomic.AtomicInteger pendingRepaints =
		new java.util.concurrent.atomic.AtomicInteger();
	private javax.swing.Timer repaintDebounce;

	public void onModelChanged()
	{
		pendingRepaints.incrementAndGet();
		javax.swing.SwingUtilities.invokeLater(() ->
		{
			if (repaintDebounce == null)
			{
				repaintDebounce = new javax.swing.Timer(50, e ->
				{
					int burst = pendingRepaints.getAndSet(0);
					if (burst > 20)
					{
						log.warn("Loadout Lab UI: {} publishes coalesced into one rebuild"
							+ " - a publish loop upstream?", burst);
					}
					repaint();
				});
				repaintDebounce.setRepeats(false);
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
	javax.swing.JButton addMobButton;
	private JPanel chipRow;
	private JPanel invRow;
	private JPanel rosterArea;
	private JPanel countsRow;
	private JPanel footerRow;
	private javax.swing.JButton undoButton;
	private javax.swing.JButton redoButton;

	/** Uniform chip config: every control in the row shares it. */
	private static <T extends javax.swing.AbstractButton> T chip(T button, String tooltip, Runnable onClick)
	{
		button.setFocusable(false);
		button.setMargin(new java.awt.Insets(1, 6, 1, 6));
		if (tooltip != null)
		{
			button.setToolTipText(tooltip);
		}
		button.addActionListener(e -> onClick.run());
		return button;
	}

	private static final java.awt.Color CHIP_ON = ResultCards.ACCENT;
	private static final java.awt.Color CHIP_OFF = new java.awt.Color(150, 150, 150);

	/** The classic parameter pill: flat, rounded, accent when active. */
	static javax.swing.JLabel pill(String text, boolean on, String tooltip, Runnable onClick)
	{
		return pill(text, on, !on, tooltip, onClick);
	}

	/** A classic pill that paints its new state IMMEDIATELY on click
	 * (field report 2026-08-12: "tiles don't highlight until the search
	 * is complete") - the arriving page then rewrites every pill from
	 * Core's truth, so an optimistic guess that Core refuses corrects
	 * itself on the next publish. */
	static javax.swing.JLabel pill(String text, boolean on, boolean pendingOn,
		String tooltip, Runnable onClick)
	{
		javax.swing.JLabel label = new javax.swing.JLabel(text);
		paintPill(label, on);
		label.setToolTipText(tooltip);
		Ui.onClick(label, () ->
		{
			paintPill(label, pendingOn);
			label.repaint();
			onClick.run();
		});
		return label;
	}

	/** Async sprite into a plate label (18px). */
	private void spriteManagerPlate(javax.swing.JLabel plate, int spriteId)
	{
		if (spriteManager != null)
		{
			spriteManager.getSpriteAsync(spriteId, 0, img ->
				javax.swing.SwingUtilities.invokeLater(() ->
					plate.setIcon(Ui.icon(img, 18))));
		}
	}

	/** The Task chip: the slayer skill icon + "Task" (field ask
	 * 2026-08-15), always the FIRST chip. */
	private javax.swing.JLabel taskPill(boolean on, boolean pendingOn,
		String tooltip, Runnable onClick)
	{
		javax.swing.JLabel pill = pill("Task", on, pendingOn, tooltip, onClick);
		if (spriteManager != null)
		{
			spriteManager.getSpriteAsync(net.runelite.api.SpriteID.SKILL_SLAYER, 0, img ->
				javax.swing.SwingUtilities.invokeLater(() ->
				{
					pill.setIcon(Ui.icon(img, 14));
					pill.setIconTextGap(4);
				}));
		}
		return pill;
	}

	private javax.swing.JLabel taskPill(boolean on, boolean pendingOn,
		String tooltip)
	{
		return taskPill(on, pendingOn, tooltip, () ->
		{
		});
	}

	/** Paint a pill as inactive (a sibling losing the selection). */
	static void dimPill(javax.swing.JLabel label)
	{
		paintPill(label, false);
		label.repaint();
	}

	private static void paintPill(javax.swing.JLabel label, boolean on)
	{
		label.setForeground(on ? CHIP_ON : CHIP_OFF);
		label.setBorder(new RoundedBorder(on ? CHIP_ON : ColorScheme.MEDIUM_GRAY_COLOR, 2, 8));
	}

	private javax.swing.JLabel paramChip(String label, String key, boolean selected)
	{
		return pill(label, selected, label + (selected ? " on" : " off"),
			() -> commands.send("set-param", Map.of("param", key, "value", !selected)));
	}

	private static boolean anyBreathesFire(Map<String, Object> page)
	{
		for (Map<String, Object> entry : Model.list(page, "entries"))
		{
			for (Map<String, Object> mob : Model.list(entry, "mobs"))
			{
				if (Model.flag(mob, "breathesFire"))
				{
					return true;
				}
			}
		}
		return false;
	}

	private static boolean anyInvocationScaled(Map<String, Object> page)
	{
		for (Map<String, Object> entry : Model.list(page, "entries"))
		{
			for (Map<String, Object> mob : Model.list(entry, "mobs"))
			{
				if (Model.flag(mob, "invocationScaled"))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** A classic count pill: "-N" red / "+N" green / "~N" grey, muted at
	 * zero, always visible; click = the manage menu + add-by-search. */
	private void pillChip(Map<String, Object> counts, String listKey, String sigil,
		java.awt.Color active, java.awt.Color muted, java.awt.Color activeBorder,
		String removeVerb, String command, String addPrompt,
		java.util.List<Map<String, Object>> supplyCatalog)
	{
		java.util.List<Map<String, Object>> items = Model.list(counts, listKey);
		javax.swing.JLabel pill = new javax.swing.JLabel(sigil + items.size());
		boolean any = !items.isEmpty();
		pill.setForeground(any ? active : muted);
		pill.setBorder(new RoundedBorder(any ? activeBorder
			: ColorScheme.MEDIUM_GRAY_COLOR, 2, 22));
		Ui.onClick(pill, () ->
		{
			javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
			// The classic grey chip also edits the SUPPLY DEFAULTS -
			// one submenu per category, plus Arceuus access.
			if (supplyCatalog != null && !supplyCatalog.isEmpty())
			{
				for (Map<String, Object> category : supplyCatalog)
				{
					String key = Model.str(category, "category");
					String current = Model.str(category, "current");
					javax.swing.JMenu sub = new javax.swing.JMenu(Model.str(category, "label"));
					if (!"arceuusAccess".equals(key))
					{
						sub.add(supplyChoice(key, "DETECT_BEST", "Detect best",
							current == null || current.startsWith("DETECT")));
						sub.add(supplyChoice(key, "NONE", "None", "NONE".equals(current)));
					}
					for (Map<String, Object> option : Model.list(category, "options"))
					{
						String optionKey = Model.str(option, "key");
						sub.add(supplyChoice(key, optionKey, Model.str(option, "name"),
							optionKey.equals(current)
								|| ("arceuusAccess".equals(key) && "DETECT_BEST".equals(optionKey)
									&& (current == null || current.startsWith("DETECT")))));
					}
					menu.add(sub);
				}
				menu.addSeparator();
			}
			for (Map<String, Object> item : items)
			{
				int id = Model.id(item, "id");
				String itemName = Model.str(item, "name");
				Ui.item(menu, removeVerb + itemName,
					() -> commands.send(command, Map.of("itemId", id, "label", itemName)));
			}
			if (any)
			{
				menu.addSeparator();
			}
			Ui.item(menu, addPrompt, () -> picker.search(addPrompt,
				(id, name) -> commands.send(command, Map.of("itemId", id, "label", name))));
			menu.show(pill, 0, pill.getHeight());
		});
		countsRow.add(pill);
	}

	/** A pill that WEARS its value (field spec 2026-08-14: no bare text
	 * entries in the chip row) - clicking swaps in the entry field;
	 * Enter commits (the page repaint restores the pill), Escape just
	 * collapses. */
	private JComponent valueChip(String label, boolean active, String tooltip,
		String currentText, java.util.function.Consumer<String> onCommit)
	{
		JPanel holder = Ui.panel(new java.awt.CardLayout());
		javax.swing.JTextField field = new javax.swing.JTextField(currentText, 5);
		field.setToolTipText(tooltip);
		javax.swing.JLabel pill = pill(label, active, active, tooltip, () ->
		{
			((java.awt.CardLayout) holder.getLayout()).show(holder, "field");
			field.requestFocusInWindow();
			field.selectAll();
		});
		holder.add(pill, "pill");
		holder.add(field, "field");
		field.addActionListener(e -> onCommit.accept(field.getText().trim()));
		field.addKeyListener(new java.awt.event.KeyAdapter()
		{
			@Override
			public void keyPressed(java.awt.event.KeyEvent e)
			{
				if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE)
				{
					((java.awt.CardLayout) holder.getLayout()).show(holder, "pill");
				}
			}
		});
		((java.awt.CardLayout) holder.getLayout()).show(holder, "pill");
		return holder;
	}

	/** One radio-style supply-default choice (checked = current). */
	private javax.swing.JMenuItem supplyChoice(String category, String key,
		String label, boolean selected)
	{
		javax.swing.JCheckBoxMenuItem item = new javax.swing.JCheckBoxMenuItem(label, selected);
		item.addActionListener(e -> commands.send("set-supply-default",
			Map.of("category", category, "choice", key)));
		return item;
	}

	/** One store's count chip: click lists the entries, each removable. */
	private void storeChip(Map<String, Object> counts, String listKey, String sigil,
		String noun, String command, String verb)
	{
		java.util.List<Map<String, Object>> items = Model.list(counts, listKey);
		if (items.isEmpty())
		{
			return;
		}
		javax.swing.JButton button = new javax.swing.JButton(sigil + items.size());
		chip(button, items.size() + " " + noun + " - click to manage", () ->
		{
			javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
			for (Map<String, Object> item : items)
			{
				int id = Model.id(item, "id");
				Ui.item(menu, verb + " " + Model.str(item, "name"),
					() -> commands.send(command, Map.of("itemId", id)));
			}
			menu.show(button, 0, button.getHeight());
		});
		countsRow.add(button);
	}

	private synchronized JComponent root()
	{
		if (root == null)
		{
			root = Ui.panel(new BorderLayout(0, 6));
			JPanel top = new JPanel();
			top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
			top.setBackground(ColorScheme.DARK_GRAY_COLOR);
			countsRow = Ui.panel(new WrapLayout(java.awt.FlowLayout.CENTER, 4, 2));
			top.add(countsRow);
			search = new JTextField();
			search.setToolTipText("Search a monster or group");
			// Suggestions LIVE as you type (150ms debounce) - but nothing
			// selects or computes until an explicit pick: a click on a
			// row, or Enter (field refinement 2026-08-15).
			search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
			{
				public void insertUpdate(javax.swing.event.DocumentEvent e)
				{
					searchDebounce.restart();
				}

				public void removeUpdate(javax.swing.event.DocumentEvent e)
				{
					searchDebounce.restart();
				}

				public void changedUpdate(javax.swing.event.DocumentEvent e)
				{
					searchDebounce.restart();
				}
			});
			search.addActionListener(e ->
			{
				if (matchesBox.getComponentCount() > 0)
				{
					java.awt.Component first = matchesBox.getComponent(0);
					for (java.awt.event.MouseListener l : first.getMouseListeners())
					{
						l.mouseClicked(null);
						return;
					}
				}
				String query = search.getText().trim();
				if (query.length() < 2)
				{
					return;
				}
				searcher.search(query, matches -> javax.swing.SwingUtilities.invokeLater(() ->
				{
					matchesBox.removeAll();
					if (matches.size() == 1)
					{
						pick(matches.get(0));
						return;
					}
					for (Map<String, Object> match : matches)
					{
						matchesBox.add(matchRow(match));
					}
					matchesBox.setVisible(!matches.isEmpty());
					root.revalidate();
					root.repaint();
				}));
			});
			JPanel searchArea = Ui.panel(new BorderLayout(4, 0));
			// Compact history arrows ride BESIDE the search (the classic
			// header pattern), not loose in the chip row.

			undoButton = chip(new javax.swing.JButton("<"), "Undo",
				() -> commands.send("undo", Map.of()));
			redoButton = chip(new javax.swing.JButton(">"), "Redo",
				() -> commands.send("redo", Map.of()));
			javax.swing.JLabel clearSearch = Ui.label("x", new java.awt.Color(150, 150, 150));
			clearSearch.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 4, 0, 6));
			clearSearch.setToolTipText("Clear the search");
			clearSearch.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			clearSearch.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mouseClicked(java.awt.event.MouseEvent e)
				{
					search.setText("");
					matchesBox.removeAll();
					matchesBox.setVisible(false);
					root.revalidate();
					root.repaint();
				}

				@Override
				public void mouseEntered(java.awt.event.MouseEvent e)
				{
					clearSearch.setForeground(new java.awt.Color(220, 120, 120));
				}

				@Override
				public void mouseExited(java.awt.event.MouseEvent e)
				{
					clearSearch.setForeground(new java.awt.Color(150, 150, 150));
				}
			});
			JPanel searchBox = new JPanel(new BorderLayout());
			searchBox.setBackground(new java.awt.Color(43, 43, 43));
			searchBox.setBorder(new RoundedBorder(new java.awt.Color(80, 78, 70), 3, 4));
			search.setOpaque(false);
			search.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 4, 2, 2));
			searchBox.add(search, BorderLayout.CENTER);
			JPanel searchTools = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 2, 0));
			searchTools.setOpaque(false);
			searchTools.add(clearSearch);
			for (javax.swing.JButton arrow : new javax.swing.JButton[]{undoButton, redoButton})
			{
				arrow.setContentAreaFilled(false);
				arrow.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 4, 2, 4));
				arrow.setForeground(new java.awt.Color(150, 150, 150));
				searchTools.add(arrow);
			}
			searchBox.add(searchTools, BorderLayout.EAST);
			JPanel searchRow = Ui.darker(new BorderLayout(4, 0));
			searchRow.add(searchBox, BorderLayout.CENTER);
			searchArea.add(searchRow, BorderLayout.NORTH);
			matchesBox = new JPanel();
			matchesBox.setLayout(new BoxLayout(matchesBox, BoxLayout.Y_AXIS));
			matchesBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			matchesBox.setVisible(false);
			searchArea.add(matchesBox, BorderLayout.CENTER);
			top.add(searchArea, BorderLayout.NORTH);
			rosterArea = Ui.darker(new BorderLayout());
			rosterArea.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 0, 2, 0));
			top.add(rosterArea);
			chipRow = Ui.panel(new WrapLayout(java.awt.FlowLayout.LEFT, 4, 2));
			top.add(chipRow, BorderLayout.CENTER);
			// The inventory slider rides its own row under the chips.
			invRow = Ui.panel(new BorderLayout());
			top.add(invRow);
			root.add(top, BorderLayout.NORTH);
			JPanel resultsArea = new JPanel();
			resultsArea.setLayout(new BoxLayout(resultsArea, BoxLayout.Y_AXIS));
			resultsArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
			cardArea = new JPanel();
			cardArea.setLayout(new BoxLayout(cardArea, BoxLayout.Y_AXIS));
			cardArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
			resultsArea.add(cardArea);
			// The computing notice sits BELOW everything the pending page
			// shows (field report: between the chips and the mob list was weird).
			waitingSlot = Ui.panel(new BorderLayout());
			loader = new AsciiLoader();
			JPanel loaderRow = Ui.panel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 4));
			loaderRow.add(loader);
			waitingSlot.add(loaderRow, BorderLayout.CENTER);
			javax.swing.JLabel computing = Ui.label("Computing...",
				new java.awt.Color(150, 150, 150));
			computing.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
			waitingSlot.add(computing, BorderLayout.SOUTH);
			waitingSlot.setVisible(false);
			resultsArea.add(waitingSlot);
			root.add(resultsArea, BorderLayout.CENTER);
			// Footer actions (the classic position): below the results.
			footerRow = Ui.panel(new WrapLayout(java.awt.FlowLayout.CENTER, 4, 2));
			root.add(footerRow, BorderLayout.SOUTH);
		}
		repaint();
		return root;
	}

	/** The Wiki calc chip's tooltip: the base line, plus - when the
	 * viewed card folds spec or thralls into its number - the sum that
	 * reconciles our shown dps with what the calculator will display
	 * (the bare set). Mirrors the engine's view derivation. */
	static String wikiCalcTip(Map<String, Object> page)
	{
		String head = "<b>Open in the official wiki calculator</b><br>"
			+ "<font color='#969696'>shares this exact setup via the wiki's"
			+ " shortlink</font>";
		String base = Ui.tip(head);
		Map<String, Object> params = ResultCards.firstParams(page);
		java.util.List<Map<String, Object>> entries = Model.list(page, "entries");
		if (params == null || entries.isEmpty())
		{
			return base;
		}
		Map<String, Object> entry = entries.get(0);
		java.util.List<Map<String, Object>> mobs = Model.list(entry, "mobs");
		if (mobs.isEmpty())
		{
			return base;
		}
		int lens = Math.max(0, Math.min((int) Model.num(params, "lensIndex"), mobs.size() - 1));
		Map<String, Object> styles = Model.map(mobs.get(lens), "styles");
		if (styles == null)
		{
			return base;
		}
		boolean bis = Model.flag(params, "viewingBis");
		String tab = Model.str(params, "selectedTab");
		Map<String, Object> node = tab == null || tab.isEmpty() ? null : Model.map(styles, tab);
		if (node == null)
		{
			double best = -1;
			for (String style : new String[]{"melee", "ranged", "magic"})
			{
				Map<String, Object> candidate = Model.map(styles, style);
				double dps = candidate == null ? -1
					: Model.num(candidate, bis ? "bisTabDps" : "tabDps");
				if (candidate != null && dps > best)
				{
					best = dps;
					node = candidate;
				}
			}
		}
		Map<String, Object> card = node == null ? null : Model.map(node, bis ? "bis" : "yours");
		if (card == null)
		{
			return base;
		}
		double set = Model.num(card, "dps");
		Map<String, Object> spec = Model.map(card, "spec");
		double specDps = spec == null ? 0 : Model.num(spec, "dpsAdded");
		double thrallsDps = Model.num(Model.map(entry, "thralls"), "dps");
		// The breakdown reads as a little ledger, not a sentence (field
		// ask 2026-08-21: "less dense and more informative") - the same
		// table the spec cell wears, built once in Ui. ALWAYS shown
		// (Andrew 2026-09-02): the number they will see is the subtext.
		String ledger = Ui.ledger(set, "what the calc shows", specDps, specDps > 0, thrallsDps);
		// ToA: the link carries the card's raid level; say so, and where
		// their field lives for anyone who wants a different one.
		int invo = Model.id(params, "toaInvocation");
		String toa = Model.flag(mobs.get(lens), "invocationScaled") && invo > 0
			? "<br><font color='#969696'>ToA: opens at Invocation " + invo
				+ " (their Raid level field, top of the monster panel)</font>"
			: "";
		return Ui.tip(head, "<br><br>", ledger, toa);
	}

	private Map<String, Object> lastRenderedPage;
	private int skippedIdentical;

	private static boolean hasPendingEntry(Map<String, Object> page)
	{
		for (Map<String, Object> entry : Model.list(page, "entries"))
		{
			if (Model.flag(entry, "pending"))
			{
				return true;
			}
		}
		return false;
	}

	private synchronized void repaint()
	{
		if (cardArea == null)
		{
			return;
		}
		Map<String, Object> page = this.page.get();
		if (page != null && page.equals(lastRenderedPage))
		{
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
		if (counts != null)
		{
			// The classic trio: always visible, muted at zero.
			pillChip(counts, "simmedItems", "+",
				new java.awt.Color(130, 200, 130), new java.awt.Color(110, 140, 110),
				new java.awt.Color(95, 160, 95),
				"Stop simming ", "toggle-sim",
				"Sim an item as owned...", null);
			pillChip(counts, "excludedItems", "-",
				new java.awt.Color(220, 120, 120), new java.awt.Color(140, 110, 110),
				new java.awt.Color(170, 90, 90),
				"Allow again: ", "toggle-exclusion",
				"Exclude an item (search)...", null);
			pillChip(counts, "filteredItems", "~",
				new java.awt.Color(190, 190, 190), new java.awt.Color(130, 130, 130),
				new java.awt.Color(150, 150, 150),
				"Stop filtering: ", "toggle-always-filter",
				"Always filter an item...", Model.list(page, "supplyCatalog"));
		}
		countsRow.setVisible(true);
		chipRow.removeAll();
		chipRow.setVisible(params != null);
		// The inventory slider rides with the chips. It is a per-result
		// control - each mob, group and raid brings its own default swap
		// count - so on the idle panel it showed a number belonging to
		// nothing, and any change to it was overwritten by the next search
		// (field report 2026-08-27). Built inside the params branch below,
		// so without this it simply kept whatever it last drew.
		invRow.setVisible(params != null);
		if (params == null)
		{
			invRow.removeAll();
		}
		if (params != null)
		{
			// Members chips (task, thralls, D charge) disappear under the
			// F2P lock - the computeArgs veto already keeps them out of the
			// math, and a control that can do nothing is noise (field ask
			// 2026-08-27). Their params survive for the untick.
			boolean f2pLocked = Model.flag(params, "f2pOnly");
			boolean lensNaval = lensedNaval(page);
			Map<String, Object> history = Model.map(page, "history");
			if (history != null && undoButton != null)
			{
				undoButton.setEnabled(Model.flag(history, "canUndo"));
				undoButton.setToolTipText(Model.str(history, "undoLabel") == null
					? "Undo" : "Undo: " + Model.str(history, "undoLabel"));
				redoButton.setEnabled(Model.flag(history, "canRedo"));
				redoButton.setToolTipText(Model.str(history, "redoLabel") == null
					? "Redo" : "Redo: " + Model.str(history, "redoLabel"));
			}
			boolean anyTaskOnly = false;
			boolean anySlayer = false;
			boolean anyWilderness = false;
			boolean allExclusive = true;
			boolean sawMob = false;
			for (Map<String, Object> pageEntry : Model.list(page, "entries"))
			{
				for (Map<String, Object> mob : Model.list(pageEntry, "mobs"))
				{
					anyTaskOnly |= Model.flag(mob, "taskOnly");
					anySlayer |= Model.flag(mob, "slayerMonster");
					anyWilderness |= Model.flag(mob, "wilderness");
					allExclusive &= Model.flag(mob, "wildernessExclusive");
					sawMob = true;
				}
			// Raids are never a slayer-task context (Andrew, 2026-08-31:
			// Skeletal Mystic's corpus slayer flag lit the chip for CoX).
			boolean raidSelection = false;
			for (Map<String, Object> entry : Model.list(page, "entries"))
			{
				raidSelection |= Model.flag(entry, "raidSelection");
			}
			if (raidSelection)
			{
				anySlayer = false;
				anyTaskOnly = false;
			}
			if (anyTaskOnly && !f2pLocked)
			{
				chipRow.add(taskPill(true, true, "Task-only boss - always on", () ->
					{
					}));
			}
			else if (anySlayer && !f2pLocked)
			{
				chipRow.add(taskPill(Model.flag(params, "onTask"), !Model.flag(params, "onTask"),
					"On a slayer task: slayer helmet bonuses apply",
					() -> commands.send("set-param",
						Map.of("param", "onTask", "value", !Model.flag(params, "onTask")))));
			}
			for (String[] entry : CHIPS)
			{
				// Thralls hide under the F2P lock AND for a sea lens (thrall
				// resurrections cannot be cast on a boat - confirmed
				// 2026-08-31; a chip that does nothing is noise).
				if (("thralls".equals(entry[1])) && (f2pLocked || lensNaval))
				{
					continue;
				}
				chipRow.add(paramChip(entry[0], entry[1], Model.flag(params, entry[1])));
			}
			// F2P: shown on a non-members world so it can be turned OFF to
			// preview members gear (field report 2026-08-25 - the merge-back
			// dropped this chip, leaving the filter stuck on). Gated on
			// f2pWorld, NOT f2pOnly, or unticking it would hide the control.
			if (Model.flag(params, "f2pWorld"))
			{
				chipRow.add(paramChip("F2P", "f2pOnly", Model.flag(params, "f2pOnly")));
			}
			// The classic gates (field report 2026-08-14: Sire showed
			// Wildy + Raid boost): Wildy only for LISTED wilderness mobs
			// (exclusives pinned always-in), the raid chip only where the
			// raid supplies a boost - named after that potion.
			}
			if (anyWilderness && allExclusive && sawMob)
			{
				chipRow.add(pill("Wildy", true, true,
					"Wilderness-exclusive - always in", () ->
					{
					}));
			}
			else if (anyWilderness)
			{
				chipRow.add(paramChip("Wildy", "inWilderness",
					Model.flag(params, "inWilderness")));
			}
			if (anyBreathesFire(page))
			{
				int af = Model.id(params, "antifireMode");
				chipRow.add(pill(af == 0 ? "No antifire" : af == 1 ? "Antifire" : "Super antifire",
					af > 0, (af + 1) % 3 > 0,
					"Cycles: dragonfire shield required / regular / super antifire",
					() -> commands.send("set-param",
						Map.of("param", "antifireMode", "value", (af + 1) % 3))));
			}
			// Cannons ride the chips section (Andrew, v2) for the LENSED
			// ship-eligible mob; the per-cannon pickers live on the card.
			if (lensNaval)
			{
				int cannons = Model.id(params, "cannonCount");
				chipRow.add(pill("Cannons " + cannons, cannons > 0, (cannons + 1) % 3 > 0,
					"Ship cannons carried - cycles 0 / 1 / 2",
					() -> commands.send("set-param",
						Map.of("param", "cannonCount", "value", (cannons + 1) % 3))));
			}
			int dCharge = Model.id(params, "deathCharge");
			if (!f2pLocked)
			{
				chipRow.add(pill(dCharge == 0 ? "D-charge" : dCharge == 1 ? "D-charge on" : "D-charge+",
					dCharge > 0, (dCharge + 1) % 3 > 0, "Death Charge: off / on / upgraded - cycles",
					() -> commands.send("set-param",
						Map.of("param", "deathCharge", "value", (dCharge + 1) % 3))));
			}
			if (anyInvocationScaled(page))
			{
				int invo = Model.id(params, "toaInvocation");
				int nextInvo = invo >= 540 ? 0 : invo >= 300 ? 540 : invo >= 150 ? 300 : 150;
				chipRow.add(pill("Invo " + invo, invo > 0, nextInvo > 0,
					"ToA invocation level - cycles 0/150/300/540",
					() -> commands.send("set-param",
						Map.of("param", "toaInvocation", "value", nextInvo))));
			}
			int swaps = Model.id(params, "maxSwaps");
			// The classic inventory control is a SLIDER over the bench
			// size - every value reachable, not four presets.
			JPanel invBox = Ui.panel(new BorderLayout(4, 0));
			javax.swing.JLabel invLabel = Ui.label("Inv " + swaps, new java.awt.Color(190, 190, 190));
			invBox.add(invLabel, BorderLayout.WEST);
			javax.swing.JSlider invSlider =
				new javax.swing.JSlider(0, 16, Math.max(0, Math.min(16, swaps)));
			invSlider.setPreferredSize(new java.awt.Dimension(88, 18));
			invSlider.setBackground(ColorScheme.DARK_GRAY_COLOR);
			invSlider.setFocusable(false);
			invSlider.setToolTipText("Max gear swaps carried on the trip");
			invSlider.addChangeListener(e ->
			{
				invLabel.setText("Inv " + invSlider.getValue());
				// Recompute only when the drag settles, and only on a real
				// change - a slider fires continuously.
				if (!invSlider.getValueIsAdjusting() && invSlider.getValue() != swaps)
				{
					commands.send("set-param",
						Map.of("param", "maxSwaps", "value", invSlider.getValue()));
				}
			});
			invBox.add(invSlider, BorderLayout.CENTER);
			invBox.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 4, 2, 4));
			invRow.removeAll();
			invRow.add(invBox, BorderLayout.CENTER);
			int budgetGp = Model.id(params, "upgradeBudgetGp");
			chipRow.add(valueChip(
				budgetGp > 0 ? "Budget " + Gp.format(budgetGp) : "Budget",
				budgetGp > 0,
				"Upgrade budget (k/m/b, 'max'); empty = owned gear only",
				Gp.format(budgetGp), text ->
			{
				Map<String, Object> args = new java.util.HashMap<>();
				args.put("param", "upgradeBudgetGp");
				args.put("value", Gp.parse(text));
				commands.send("set-param", args);
			}));
			if (anyWilderness && wildyRiskGate.getAsBoolean()
				&& (allExclusive || Model.flag(params, "inWilderness")))
			{
				int riskGp = Model.id(params, "riskBudgetGp");
				// The CAP FLAG decides, never the number: the engine's
				// no-cap sentinel is itself 75k, so a raw > 0 test lit
				// this chip for a cap the compute ignored (field report
				// 2026-08-22).
				boolean capped = Model.flag(params, "riskCapped");
				chipRow.add(valueChip(
					capped ? "Risk " + Gp.format(riskGp) : "Risk cap",
					capped,
					"Wilderness risk cap in gp (k/m/b); empty = uncapped."
						+ " Caps tradeables carried to 3 (4 with Protect Item)",
					capped ? Gp.format(riskGp) : "", text ->
				{
					// Empty = clear: null falls back to uncapped.
					Map<String, Object> args = new java.util.HashMap<>();
					args.put("param", "riskBudgetGp");
					args.put("value", text.isEmpty() ? null : Gp.parse(text));
					commands.send("set-param", args);
				}));
				chipRow.add(paramChip("Protect item", "protectItem",
					Model.flag(params, "protectItem")));
			}
			addMobButton = chip(new javax.swing.JButton("+ Mob"),
				"Add another monster to this result (shared trip plan)", () ->
			{
				String query = javax.swing.JOptionPane.showInputDialog(root,
					"Add a monster to this result:", "Add mob",
					javax.swing.JOptionPane.PLAIN_MESSAGE);
				if (query != null && !query.trim().isEmpty())
				{
					commands.send("add-mob", Map.of("query", query.trim()));
				}
			});
			addMobButton.setContentAreaFilled(false);
			addMobButton.setOpaque(false);
			addMobButton.setForeground(new java.awt.Color(190, 190, 190));
			addMobButton.setBorder(new RoundedBorder(ColorScheme.DARKER_GRAY_HOVER_COLOR, 3, 10));
			javax.swing.JButton reload = chip(new javax.swing.JButton("Reload"),
				"Re-run with a fresh bank scan", () ->
					commands.send("recompute", Map.of()));
			reload.setContentAreaFilled(false);
			reload.setOpaque(false);
			reload.setForeground(new java.awt.Color(140, 140, 140));
			reload.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 8, 3, 8));
			cards.setReload(reload);
			cards.setAddMob(addMobButton);
		}
		footerRow.removeAll();
		String reportText = Model.str(page, "reportText");
		if (reportText != null)
		{
			footerRow.add(chip(new javax.swing.JButton("Copy report"),
				"Copy the shown result as text", () ->
			{
				try
				{
					java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
						new java.awt.datatransfer.StringSelection(reportText), null);
				}
				catch (IllegalStateException ex)
				{
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
			boolean wikiBis = params != null && Model.flag(params, "viewingBis");
			footerRow.add(chip(new javax.swing.JButton("Wiki calc"),
				wikiCalcTip(page), () -> commands.send("wiki-calc",
					Map.of("style", wikiTab, "bis", wikiBis))));
		}
		footerRow.add(chip(new javax.swing.JButton("Discord"),
			"Loadout Lab community - report issues, request features", () ->
				net.runelite.client.util.LinkBrowser.browse("https://discord.gg/6GuS6J8em3")));
		if (rosterArea != null)
		{
			rosterArea.removeAll();
			JPanel roster = page == null ? null : cards.renderRoster(page);
			if (roster != null)
			{
				rosterArea.add(roster, BorderLayout.CENTER);
			}
			rosterArea.setVisible(roster != null);
		}
		cardArea.removeAll();
		// Mid-compute publishes keep the stage CLEAR (field report
		// 2026-08-20: undo drew the stale result above the loader) -
		// the controls above still rebuild; the reveal happens on
		// setComputing(false).
		JPanel rendered = page == null || isComputing ? null : cards.render(page);
		if (rendered != null && rendered.getComponentCount() > 0)
		{
			cardArea.add(rendered);
		}
		else if (!isComputing && !hasPendingEntry(page))
		{
			// The empty state says what to do - never beside the
			// computing notice.
			javax.swing.JLabel hint = Ui.label(
				"Search a mob, group, or raid to begin.", new java.awt.Color(150, 150, 150));
			hint.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 8, 12, 8));
			hint.setAlignmentX(0.5f);
			cardArea.add(hint);
		}
		root.revalidate();
		root.repaint();
		long rebuildMs = (System.nanoTime() - rebuildStart) / 1_000_000;
		if (rebuildMs > 30)
		{
			log.debug("perf: UI rebuild took {}ms ({} identical pages skipped since last)",
				rebuildMs, skippedIdentical);
		}
		skippedIdentical = 0;
	}

}
