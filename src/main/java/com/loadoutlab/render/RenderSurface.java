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
	private JPanel root;
	private JPanel cardArea;
	private javax.swing.JLabel computing;
	private volatile boolean isComputing;

	/** Compute-in-flight: dims the cards and shows the waiting line
	 * (the mascot animation's future mount point). */
	public void setComputing(boolean computing)
	{
		isComputing = computing;
		javax.swing.SwingUtilities.invokeLater(() ->
		{
			if (this.computing != null)
			{
				this.computing.setVisible(isComputing);
			}
		});
	}

	public RenderSurface(ResultCards cards, java.util.function.Supplier<Map<String, Object>> page,
		CommandSink commands, ItemPicker picker)
	{
		this.cards = cards;
		this.page = page;
		this.commands = commands;
		this.picker = picker;
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

	private synchronized JComponent root()
	{
		if (root == null)
		{
			root = new JPanel(new BorderLayout(0, 6));
			root.setBackground(ColorScheme.DARK_GRAY_COLOR);
			JPanel top = new JPanel(new BorderLayout(0, 4));
			top.setBackground(ColorScheme.DARK_GRAY_COLOR);
			JTextField search = new JTextField();
			search.setToolTipText("Search a monster");
			search.addActionListener(e ->
			{
				String query = search.getText().trim();
				if (!query.isEmpty())
				{
					commands.send("select", Map.of("query", query));
				}
			});
			top.add(search, BorderLayout.NORTH);
			chipRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
			chipRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
			top.add(chipRow, BorderLayout.CENTER);
			computing = new javax.swing.JLabel("Computing...");
			computing.setVisible(false);
			top.add(computing, BorderLayout.SOUTH);
			root.add(top, BorderLayout.NORTH);
			cardArea = new JPanel();
			cardArea.setLayout(new BoxLayout(cardArea, BoxLayout.Y_AXIS));
			cardArea.setBackground(ColorScheme.DARK_GRAY_COLOR);
			root.add(cardArea, BorderLayout.CENTER);
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
			if (history != null)
			{
				javax.swing.JButton undo = chip(new javax.swing.JButton("<"),
					Model.str(history, "undoLabel") == null ? "Undo"
						: "Undo: " + Model.str(history, "undoLabel"),
					() -> commands.send("undo", Map.of()));
				undo.setEnabled(Model.flag(history, "canUndo"));
				chipRow.add(undo);
				javax.swing.JButton redo = chip(new javax.swing.JButton(">"),
					Model.str(history, "redoLabel") == null ? "Redo"
						: "Redo: " + Model.str(history, "redoLabel"),
					() -> commands.send("redo", Map.of()));
				redo.setEnabled(Model.flag(history, "canRedo"));
				chipRow.add(redo);
			}
			String tab = ResultCards.effectiveTab(page);
			for (String style : new String[]{"melee", "ranged", "magic"})
			{
				javax.swing.JToggleButton button = new javax.swing.JToggleButton(
					Character.toUpperCase(style.charAt(0)) + style.substring(1),
					style.equals(tab));
				button.setFocusable(false);
				button.setMargin(new java.awt.Insets(1, 6, 1, 6));
				button.addActionListener(e -> commands.send("set-param",
					Map.of("param", "selectedTab", "value", style)));
				chipRow.add(button);
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
			Map<String, Object> counts = Model.map(page, "counts");
			if (counts != null)
			{
				int excluded = Model.id(counts, "excluded");
				int simmed = Model.id(counts, "simmed");
				int stored = Model.id(counts, "stored");
				if (excluded + simmed + stored > 0)
				{
					javax.swing.JLabel label = new javax.swing.JLabel(String.format(
						"-%d  +%d  ~%d", excluded, simmed, stored));
					label.setToolTipText(excluded + " excluded, " + simmed
						+ " simmed as owned, " + stored + " stored elsewhere"
						+ " - manage in the classic panel for now");
					chipRow.add(label);
				}
			}
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
