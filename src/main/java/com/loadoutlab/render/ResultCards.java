package com.loadoutlab.render;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

/**
 * The first rich renderer over the render-model: per mob, per style,
 * the Yours and Best-in-game sides as compact cards - dps headline,
 * equipment icon grid (icons resolved through the Companion's own
 * ItemManager; the model only carries ids), spec and incoming lines.
 * This view grows toward the ported panel card-by-card; the model
 * gains fields as this code asks for them.
 */
public class ResultCards
{
	private static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	private static final String[] STYLES = {"melee", "ranged", "magic"};

	private final ItemManager itemManager;
	private final CommandSink commands;
	private final ItemPicker picker;

	public ResultCards(ItemManager itemManager, CommandSink commands, ItemPicker picker)
	{
		this.itemManager = itemManager;
		this.commands = commands;
		this.picker = picker;
	}

	/** The tab the page is effectively showing: the explicit selection,
	 * or the first style the first mob answers. */
	static String effectiveTab(Map<String, Object> page)
	{
		Map<String, Object> params = firstParams(page);
		String selected = params == null ? null : Model.str(params, "selectedTab");
		for (Map<String, Object> entry : Model.list(page, "entries"))
		{
			for (Map<String, Object> mob : Model.list(entry, "mobs"))
			{
				Map<String, Object> styles = Model.map(mob, "styles");
				if (styles == null)
				{
					continue;
				}
				if (selected != null && !selected.isEmpty() && styles.get(selected) != null)
				{
					return selected;
				}
				for (String style : STYLES)
				{
					if (styles.get(style) != null)
					{
						return style;
					}
				}
			}
		}
		return selected == null || selected.isEmpty() ? "melee" : selected;
	}

	static Map<String, Object> firstParams(Map<String, Object> page)
	{
		for (Map<String, Object> entry : Model.list(page, "entries"))
		{
			Map<String, Object> params = Model.map(entry, "params");
			if (params != null)
			{
				return params;
			}
		}
		return null;
	}

	JPanel render(Map<String, Object> page)
	{
		String tab = effectiveTab(page);
		Map<String, Object> params = firstParams(page);
		boolean bis = params != null && Model.flag(params, "viewingBis");
		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setBackground(ColorScheme.DARK_GRAY_COLOR);
		int lens = params == null ? 0 : Model.id(params, "lensIndex");
		for (Map<String, Object> entry : Model.list(page, "entries"))
		{
			double thrallsDps = Model.num(Model.map(entry, "thralls"), "dps");
			List<Map<String, Object>> mobs = Model.list(entry, "mobs");
			if (mobs.size() <= 1)
			{
				for (Map<String, Object> mob : mobs)
				{
					column.add(mobCard(mob, tab, bis, thrallsDps));
					column.add(Box.createVerticalStrut(8));
				}
				continue;
			}
			// Roster lens: compact clickable rows, one mob expanded.
			int shownLens = Math.min(Math.max(lens, 0), mobs.size() - 1);
			for (int i = 0; i < mobs.size(); i++)
			{
				Map<String, Object> mob = mobs.get(i);
				if (i == shownLens)
				{
					column.add(mobCard(mob, tab, bis, thrallsDps));
				}
				else
				{
					column.add(lensRow(mob, tab, bis, i));
				}
				column.add(Box.createVerticalStrut(4));
			}
		}
		return column;
	}

	/** One collapsed roster row: label + the shown side's dps for the
	 * current tab; click to move the lens here. */
	private JPanel lensRow(Map<String, Object> mob, String tab, boolean bis, int index)
	{
		Map<String, Object> styles = Model.map(mob, "styles");
		Map<String, Object> node = styles == null ? null : Model.map(styles, tab);
		Map<String, Object> shown = node == null ? null : Model.map(node, bis ? "bis" : "yours");
		String dps = shown == null ? "-" : String.format("%.2f dps", Model.num(shown, "dps"));
		JLabel label = new JLabel(Model.str(mob, "label") + "  -  " + dps);
		label.setToolTipText("Click to expand");
		label.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		label.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				commands.send("set-param", Map.of("param", "lensIndex", "value", index));
			}
		});
		JPanel row = left(label);
		row.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
		return row;
	}

	private JPanel mobCard(Map<String, Object> mob, String tab, boolean bis, double thrallsDps)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(CARD);
		card.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		JLabel title = new JLabel(Model.str(mob, "label"));
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		card.add(left(title));
		Map<String, Object> styles = Model.map(mob, "styles");
		Map<String, Object> node = styles == null ? null : Model.map(styles, tab);
		if (node == null)
		{
			JLabel none = new JLabel("No " + tab + " set for this mob");
			card.add(left(none));
			return card;
		}
		card.add(Box.createVerticalStrut(6));
		card.add(left(styleHeader(tab, node)));
		Map<String, Object> shown = Model.map(node, bis ? "bis" : "yours");
		if (shown == null)
		{
			shown = Model.map(node, bis ? "yours" : "bis");
		}
		if (shown != null)
		{
			card.add(left(side(bis ? "Best in game" : "Yours", shown, bis, thrallsDps)));
		}
		return card;
	}

	private static JLabel styleHeader(String style, Map<String, Object> node)
	{
		String name = Character.toUpperCase(style.charAt(0)) + style.substring(1);
		JLabel label = new JLabel(name);
		label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize() - 1f));
		String boost = Model.str(node, "boostLabel");
		if (boost != null)
		{
			label.setToolTipText("Assumes: " + boost);
		}
		return label;
	}

	private JPanel side(String caption, Map<String, Object> card, boolean bis, double thrallsDps)
	{
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(CARD);
		Map<String, Object> specNode = Model.map(card, "spec");
		double setDps = Model.num(card, "dps");
		double specAdded = specNode == null ? 0 : Model.num(specNode, "dpsAdded");
		StringBuilder parts = new StringBuilder(String.format("%.2f set", setDps));
		double shownDps = setDps;
		if (thrallsDps > 0.0005)
		{
			parts.append(String.format(" + %.2f thralls", thrallsDps));
			shownDps += thrallsDps;
		}
		if (specAdded > 0.0005)
		{
			parts.append(String.format(" + %.2f spec", specAdded));
			shownDps += specAdded;
		}
		String headlineText = shownDps > setDps + 0.0005
			? String.format("%s: %.2f dps = %s (max %d, %.0f%% acc)",
				caption, shownDps, parts,
				(int) Model.num(card, "maxHit"), Model.num(card, "accuracy") * 100)
			: String.format("%s: %.2f dps (max %d, %.0f%% acc)",
				caption, setDps, (int) Model.num(card, "maxHit"),
				Model.num(card, "accuracy") * 100);
		JLabel headline = new JLabel(headlineText);
		String spell = Model.str(card, "spell");
		StringBuilder tip = new StringBuilder("<html>").append(Model.str(card, "attackType"));
		if (spell != null)
		{
			tip.append(" - ").append(spell);
		}
		Map<String, Object> incoming = Model.map(card, "incoming");
		if (incoming != null && Model.str(incoming, "protectPrayer") != null)
		{
			tip.append("<br>Pray ").append(Model.str(incoming, "protectPrayer"))
				.append(String.format(" - %.2f incoming dps", Model.num(incoming, "dps")));
		}
		Map<String, Object> spec = Model.map(card, "spec");
		if (spec != null)
		{
			tip.append("<br>Spec: ").append(Model.str(Model.map(spec, "weapon"), "name"))
				.append(String.format(" (+%.2f dps)", Model.num(spec, "dpsAdded")));
		}
		headline.setToolTipText(tip.append("</html>").toString());
		panel.add(left(headline));
		if (incoming != null && Model.str(incoming, "protectPrayer") != null)
		{
			String caveat = Model.flag(incoming, "fullyModeled") ? "" : " (partly modeled)";
			JLabel dtps = new JLabel(String.format("Pray %s - takes %.2f dps (%.2f unprayed)%s",
				Model.str(incoming, "protectPrayer"), Model.num(incoming, "dps"),
				Model.num(incoming, "unprayedDps"), caveat));
			dtps.setFont(dtps.getFont().deriveFont(dtps.getFont().getSize() - 1f));
			panel.add(left(dtps));
		}
		panel.add(left(gearGrid(card, bis)));
		Object counted = card.get("counted");
		if (counted instanceof List && !((List<?>) counted).isEmpty())
		{
			StringBuilder line = new StringBuilder("Counting: ");
			boolean first = true;
			for (Object bonus : (List<?>) counted)
			{
				if (!first)
				{
					line.append(", ");
				}
				first = false;
				line.append(bonus);
			}
			JLabel counting = new JLabel(line.toString());
			counting.setFont(counting.getFont().deriveFont(counting.getFont().getSize() - 2f));
			counting.setToolTipText("Situational bonuses the math actually counted for this set");
			panel.add(left(counting));
		}
		return panel;
	}

	private JPanel gearGrid(Map<String, Object> card, boolean bis)
	{
		JPanel grid = new JPanel(new GridLayout(0, 7, 2, 2));
		grid.setBackground(CARD);
		Map<String, Object> gear = Model.map(card, "gear");
		if (gear != null)
		{
			for (Map.Entry<String, Object> slot : gear.entrySet())
			{
				if (slot.getValue() instanceof Map)
				{
					@SuppressWarnings("unchecked")
					Map<String, Object> item = (Map<String, Object>) slot.getValue();
					grid.add(itemCell(item, slot.getKey(), bis));
				}
			}
		}
		Map<String, Object> quiver = Model.map(card, "quiverAmmo");
		if (quiver != null)
		{
			grid.add(itemCell(quiver, "quiver", bis));
		}
		return grid;
	}

	private JLabel itemCell(Map<String, Object> item, String slot, boolean bis)
	{
		JLabel cell = new JLabel();
		String name = Model.str(item, "name");
		int id = Model.id(item, "id");
		cell.setToolTipText(name + " (" + slot + ")");
		itemManager.getImage(id).addTo(cell);
		javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		javax.swing.JMenuItem exclude = new javax.swing.JMenuItem("Exclude " + name);
		exclude.addActionListener(e -> commands.send("toggle-exclusion", Map.of("itemId", id)));
		menu.add(exclude);
		if (bis)
		{
			javax.swing.JMenuItem sim = new javax.swing.JMenuItem("Sim as owned");
			sim.setToolTipText("Pretend you own " + name + " and recompute your side");
			sim.addActionListener(e -> commands.send("toggle-sim", Map.of("itemId", id)));
			menu.add(sim);
		}
		else if (!"quiver".equals(slot))
		{
			javax.swing.JMenuItem pin = new javax.swing.JMenuItem("Pin " + name);
			pin.setToolTipText("Force this item into the " + slot + " slot for this mob (all sets)");
			pin.addActionListener(e -> commands.send("pin", Map.of("slot", slot, "itemId", id)));
			menu.add(pin);
			javax.swing.JMenuItem pinOther = new javax.swing.JMenuItem("Pin another item (search)...");
			pinOther.addActionListener(e -> picker.search("Pin in " + slot,
				(pickedId, pickedName) -> commands.send("pin", Map.of("slot", slot, "itemId", pickedId))));
			menu.add(pinOther);
			javax.swing.JMenuItem unpin = new javax.swing.JMenuItem("Unpin " + slot);
			unpin.addActionListener(e -> commands.send("unpin", Map.of("slot", slot)));
			menu.add(unpin);
		}
		cell.setComponentPopupMenu(menu);
		return cell;
	}

	private static JPanel left(javax.swing.JComponent inner)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(CARD);
		row.add(inner, BorderLayout.WEST);
		return row;
	}
}
