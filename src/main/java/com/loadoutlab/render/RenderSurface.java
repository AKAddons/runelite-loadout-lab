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
public class RenderSurface
{
	private final ResultCards cards;
	private final java.util.function.Supplier<Map<String, Object>> page;
	private final CommandSink commands;
	private final ItemPicker picker;
	private final Searcher searcher;
	private JPanel root;
	private JPanel matchesBox;
	private final javax.swing.Timer searchDebounce;
	private JTextField search;
	private JPanel cardArea;
	private JPanel waitingSlot;
	private volatile boolean isComputing;
	/** Optional rich waiting content (the Companion injects the mascot
	 * roster here); absent = the plain Computing... line. */
	private volatile java.util.function.Supplier<JComponent> waitingSupplier;

	public void setWaitingSupplier(java.util.function.Supplier<JComponent> waitingSupplier)
	{
		this.waitingSupplier = waitingSupplier;
	}

	/** Compute-in-flight: shows the waiting slot; a supplier gets a
	 * fresh component per compute (mascot moods re-roll). */
	public void setComputing(boolean computing)
	{
		isComputing = computing;
		javax.swing.SwingUtilities.invokeLater(() ->
		{
			if (waitingSlot == null)
			{
				return;
			}
			if (isComputing)
			{
				java.util.function.Supplier<JComponent> supplier = waitingSupplier;
				if (supplier != null)
				{
					waitingSlot.removeAll();
					waitingSlot.add(supplier.get(), BorderLayout.CENTER);
				}
				// The classic contract: a compute clears the stage - only
				// the loading animation shows until the new answer lands.
				if (cardArea != null)
				{
					cardArea.removeAll();
					cardArea.revalidate();
					cardArea.repaint();
				}
			}
			waitingSlot.setVisible(isComputing);
			waitingSlot.revalidate();
			waitingSlot.repaint();
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

	/** One dropdown row: the label, group rows bold - click selects. */
	private javax.swing.JComponent matchRow(Map<String, Object> match)
	{
		javax.swing.JLabel row = new javax.swing.JLabel(Model.str(match, "label"));
		boolean group = match.get("group") != null;
		if (group)
		{
			row.setFont(row.getFont().deriveFont(java.awt.Font.BOLD));
		}
		row.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 8, 3, 8));
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
			commands.send("select", Map.of("id", Model.id(match, "id")));
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
	public void onModelChanged()
	{
		javax.swing.SwingUtilities.invokeLater(this::repaint);
	}

	/** Boolean param chips: label -> params-node key. Rendered from the
	 * model (never local state) - a chip is checked because Core says so. */
	private static final String[][] CHIPS = {
		{"On task", "onTask"},
		{"Wildy", "inWilderness"},
		{"Spec", "specWeapon"},
		{"Raid boost", "raidBoost"},
		{"Thralls", "thralls"},
	};

	private JPanel chipRow;
	private JPanel countsRow;
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

	private javax.swing.JToggleButton paramChip(String label, String key, boolean selected)
	{
		javax.swing.JToggleButton button = new javax.swing.JToggleButton(label, selected);
		return chip(button, null, () -> commands.send("set-param",
			Map.of("param", key, "value", button.isSelected())));
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
				javax.swing.JMenuItem entry = new javax.swing.JMenuItem(
					verb + " " + Model.str(item, "name"));
				entry.addActionListener(e -> commands.send(command, Map.of("itemId", id)));
				menu.add(entry);
			}
			menu.show(button, 0, button.getHeight());
		});
		countsRow.add(button);
	}

	private synchronized JComponent root()
	{
		if (root == null)
		{
			root = new JPanel(new BorderLayout(0, 6));
			root.setBackground(ColorScheme.DARK_GRAY_COLOR);
			JPanel top = new JPanel();
			top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
			top.setBackground(ColorScheme.DARK_GRAY_COLOR);
			countsRow = new JPanel(new WrapLayout(java.awt.FlowLayout.LEFT, 4, 2));
			countsRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
			top.add(countsRow);
			search = new JTextField();
			search.setToolTipText("Search a monster or group");
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
				// Enter = the first match, exactly like clicking it.
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
				if (!query.isEmpty())
				{
					commands.send("select", Map.of("query", query));
					search.setText("");
				}
			});
			JPanel searchArea = new JPanel(new BorderLayout(4, 0));
			searchArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
			// Compact history arrows ride BESIDE the search (the classic
			// header pattern), not loose in the chip row.
			JPanel historyBox = new JPanel(new java.awt.GridLayout(1, 2, 2, 0));
			historyBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
			undoButton = chip(new javax.swing.JButton("<"), "Undo",
				() -> commands.send("undo", Map.of()));
			redoButton = chip(new javax.swing.JButton(">"), "Redo",
				() -> commands.send("redo", Map.of()));
			historyBox.add(undoButton);
			historyBox.add(redoButton);
			JPanel searchRow = new JPanel(new BorderLayout(4, 0));
			searchRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
			searchRow.add(search, BorderLayout.CENTER);
			searchRow.add(historyBox, BorderLayout.EAST);
			searchArea.add(searchRow, BorderLayout.NORTH);
			matchesBox = new JPanel();
			matchesBox.setLayout(new BoxLayout(matchesBox, BoxLayout.Y_AXIS));
			matchesBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			matchesBox.setVisible(false);
			searchArea.add(matchesBox, BorderLayout.CENTER);
			top.add(searchArea, BorderLayout.NORTH);
			chipRow = new JPanel(new WrapLayout(java.awt.FlowLayout.LEFT, 4, 2));
			chipRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
			top.add(chipRow, BorderLayout.CENTER);
			root.add(top, BorderLayout.NORTH);
			JPanel resultsArea = new JPanel(new BorderLayout());
			resultsArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
			waitingSlot = new JPanel(new BorderLayout());
			waitingSlot.setBackground(ColorScheme.DARK_GRAY_COLOR);
			waitingSlot.add(new javax.swing.JLabel("Computing..."), BorderLayout.CENTER);
			waitingSlot.setVisible(false);
			resultsArea.add(waitingSlot, BorderLayout.NORTH);
			cardArea = new JPanel();
			cardArea.setLayout(new BoxLayout(cardArea, BoxLayout.Y_AXIS));
			cardArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
			resultsArea.add(cardArea, BorderLayout.CENTER);
			root.add(resultsArea, BorderLayout.CENTER);
		}
		repaint();
		return root;
	}

	private synchronized void repaint()
	{
		if (cardArea == null)
		{
			return;
		}
		Map<String, Object> page = this.page.get();
		Map<String, Object> params = ResultCards.firstParams(page);
		chipRow.removeAll();
		chipRow.setVisible(params != null);
		if (params != null)
		{
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
			boolean bis = Model.flag(params, "viewingBis");
			javax.swing.JToggleButton view = new javax.swing.JToggleButton(
				bis ? "BiS" : "Yours", bis);
			view.setToolTipText("Toggle between your gear and the game ceiling");
			view.setFocusable(false);
			view.setMargin(new java.awt.Insets(1, 6, 1, 6));
			view.addActionListener(e -> commands.send("set-param",
				Map.of("param", "viewingBis", "value", view.isSelected())));
			chipRow.add(view);
			for (String[] entry : CHIPS)
			{
				chipRow.add(paramChip(entry[0], entry[1], Model.flag(params, entry[1])));
			}
			if (anyBreathesFire(page))
			{
				int af = Model.id(params, "antifireMode");
				javax.swing.JToggleButton afChip = new javax.swing.JToggleButton(
					af == 0 ? "No antifire" : af == 1 ? "Antifire" : "Super antifire", af > 0);
				chipRow.add(chip(afChip,
					"Cycles: dragonfire shield required / regular / super antifire",
					() -> commands.send("set-param",
						Map.of("param", "antifireMode", "value", (af + 1) % 3))));
			}
			int dCharge = Model.id(params, "deathCharge");
			javax.swing.JToggleButton dc = new javax.swing.JToggleButton(
				dCharge == 0 ? "D-charge" : dCharge == 1 ? "D-charge on" : "D-charge+",
				dCharge > 0);
			chipRow.add(chip(dc, "Death Charge: off / on / upgraded - cycles",
				() -> commands.send("set-param",
					Map.of("param", "deathCharge", "value", (dCharge + 1) % 3))));
			if (anyInvocationScaled(page))
			{
				int invo = Model.id(params, "toaInvocation");
				int nextInvo = invo >= 540 ? 0 : invo >= 300 ? 540 : invo >= 150 ? 300 : 150;
				javax.swing.JToggleButton invoChip =
					new javax.swing.JToggleButton("Invo " + invo, invo > 0);
				chipRow.add(chip(invoChip, "ToA invocation level - cycles 0/150/300/540",
					() -> commands.send("set-param",
						Map.of("param", "toaInvocation", "value", nextInvo))));
			}
			String lock = Model.str(params, "spellbookLock");
			javax.swing.JComboBox<String> book = new javax.swing.JComboBox<>(
				new String[]{"Auto book", "standard", "ancient", "lunar", "arceuus"});
			book.setSelectedItem(lock == null || lock.isEmpty() ? "Auto book" : lock);
			book.setToolTipText("Lock autocast picks to one spellbook");
			book.setFocusable(false);
			book.addActionListener(e -> commands.send("set-param",
				Map.of("param", "spellbookLock", "value",
					"Auto book".equals(book.getSelectedItem()) ? "" : book.getSelectedItem())));
			chipRow.add(book);
			int swaps = Model.id(params, "maxSwaps");
			javax.swing.JComboBox<String> inventory = new javax.swing.JComboBox<>(
				new String[]{"Inv 0", "Inv 1", "Inv 3", "Inv 8"});
			inventory.setSelectedItem("Inv " + swaps);
			inventory.setToolTipText("Carried gear swaps the trip plan may use (bench size)");
			inventory.setFocusable(false);
			inventory.addActionListener(e -> commands.send("set-param",
				Map.of("param", "maxSwaps", "value", Integer.parseInt(
					String.valueOf(inventory.getSelectedItem()).substring(4)))));
			chipRow.add(inventory);
			javax.swing.JTextField budget = new javax.swing.JTextField(
				Gp.format(Model.id(params, "upgradeBudgetGp")), 5);
			budget.setToolTipText("Upgrade budget (k/m/b, 'max'); empty = owned gear only");
			budget.addActionListener(e -> commands.send("set-param",
				Map.of("param", "upgradeBudgetGp", "value", Gp.parse(budget.getText()))));
			chipRow.add(budget);
			if (Model.flag(params, "inWilderness"))
			{
				javax.swing.JTextField risk = new javax.swing.JTextField(5);
				risk.setToolTipText("Wilderness risk cap in gp (k/m/b); empty = uncapped."
					+ " Caps tradeables carried to 3 (4 with Protect Item)");
				risk.addActionListener(e ->
				{
					String text = risk.getText().trim();
					// Empty = clear: a null value falls back to the engine
					// default (uncapped); Map.of refuses nulls.
					Map<String, Object> riskArgs = new java.util.HashMap<>();
					riskArgs.put("param", "riskBudgetGp");
					riskArgs.put("value", text.isEmpty() ? null : Gp.parse(text));
					commands.send("set-param", riskArgs);
				});
				chipRow.add(risk);
				chipRow.add(paramChip("Protect item", "protectItem",
					Model.flag(params, "protectItem")));
			}
			chipRow.add(chip(new javax.swing.JButton("+ Mob"),
				"Add another monster to this result (shared trip plan)", () ->
			{
				String query = javax.swing.JOptionPane.showInputDialog(root,
					"Add a monster to this result:", "Add mob",
					javax.swing.JOptionPane.PLAIN_MESSAGE);
				if (query != null && !query.trim().isEmpty())
				{
					commands.send("add-mob", Map.of("query", query.trim()));
				}
			}));
			chipRow.add(chip(new javax.swing.JButton("+ Sim"),
				"Search an item and sim it as owned", () ->
					picker.search("Sim as owned",
						(id, name) -> commands.send("toggle-sim", Map.of("itemId", id)))));
			chipRow.add(chip(new javax.swing.JButton("Discord"),
				"Loadout Lab community - report issues, request features", () ->
					net.runelite.client.util.LinkBrowser.browse(
						"https://discord.gg/6GuS6J8em3")));
			String report = Model.str(page, "reportText");
			if (report != null)
			{
				javax.swing.JButton copy = new javax.swing.JButton("Copy report");
				copy.setToolTipText("Copy the shown result as a text report (Core builds it)");
				copy.setFocusable(false);
				copy.setMargin(new java.awt.Insets(1, 6, 1, 6));
				copy.addActionListener(e ->
				{
					try
					{
						java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
							new java.awt.datatransfer.StringSelection(report), null);
					}
					catch (IllegalStateException ex)
					{
						// Clipboard busy - the classic panel guards the same way.
					}
				});
				chipRow.add(copy);
			}
			countsRow.removeAll();
			Map<String, Object> counts = Model.map(page, "counts");
			if (counts != null)
			{
				storeChip(counts, "excludedItems", "-", "excluded",
					"toggle-exclusion", "Re-include");
				storeChip(counts, "simmedItems", "+", "simmed as owned",
					"toggle-sim", "Stop simming");
				storeChip(counts, "storedItems", "~", "stored elsewhere",
					"toggle-stored", "Remove");
				countsRow.add(chip(new javax.swing.JButton("+ Stored"),
					"Search an item you own outside the tracked storages", () ->
						picker.search("Stored elsewhere",
							(id, name) -> commands.send("toggle-stored", Map.of("itemId", id)))));
			}
			countsRow.setVisible(countsRow.getComponentCount() > 0);
		}
		cardArea.removeAll();
		if (page != null)
		{
			cardArea.add(cards.render(page));
		}
		root.revalidate();
		root.repaint();
	}

}
