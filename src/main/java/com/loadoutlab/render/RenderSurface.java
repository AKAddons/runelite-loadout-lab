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
		{"Antifire", "antifirePotion"},
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
			for (String[] chip : CHIPS)
			{
				String key = chip[1];
				javax.swing.JToggleButton button =
					new javax.swing.JToggleButton(chip[0], Model.flag(params, key));
				button.setFocusable(false);
				button.setMargin(new java.awt.Insets(1, 6, 1, 6));
				button.addActionListener(e -> commands.send("set-param",
					Map.of("param", key, "value", button.isSelected())));
				chipRow.add(button);
			}
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
