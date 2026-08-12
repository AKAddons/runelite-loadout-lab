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
	private List<String> spellOptions = List.of();
	private Map<String, Object> assumeOptions;
	private Map<String, Object> pageParams;

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

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> supplies = List.of();

	JPanel render(Map<String, Object> page)
	{
		supplies = List.of();
		for (Map<String, Object> entryNode : Model.list(page, "entries"))
		{
			List<Map<String, Object>> found = Model.list(entryNode, "supplies");
			if (!found.isEmpty())
			{
				supplies = found;
			}
		}
		Object spells = page == null ? null : page.get("spells");
		spellOptions = spells instanceof List ? (List<String>) spells : List.of();
		assumeOptions = Model.map(page, "assumeOptions");
		pageParams = firstParams(page);
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
		javax.swing.JPopupMenu rowMenu = new javax.swing.JPopupMenu();
		javax.swing.JMenuItem remove = new javax.swing.JMenuItem(
			"Remove " + Model.str(mob, "name") + " from result");
		remove.addActionListener(e -> commands.send("remove-mob", Map.of("index", index)));
		rowMenu.add(remove);
		label.setComponentPopupMenu(rowMenu);
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
			card.add(left(side(bis ? "Best in game" : "Yours", shown, bis, thrallsDps, mob, tab)));
		}
		if (!supplies.isEmpty())
		{
			JPanel supplyRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 2, 0));
			supplyRow.setBackground(CARD);
			supplyRow.add(new JLabel("Supplies:"));
			for (Map<String, Object> supply : supplies)
			{
				JLabel cell = new JLabel();
				cell.setToolTipText(Model.str(supply, "name")
					+ " - right-click to change this category");
				itemManager.getImage(Model.id(supply, "itemId")).addTo(cell);
				javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
				String category = Model.str(supply, "category");
				javax.swing.JMenuItem detect = new javax.swing.JMenuItem("Detect best");
				detect.addActionListener(e -> commands.send("set-supply-override",
					Map.of("category", category, "choice", "DETECT")));
				menu.add(detect);
				javax.swing.JMenuItem none = new javax.swing.JMenuItem("None");
				none.addActionListener(e -> commands.send("set-supply-override",
					Map.of("category", category, "choice", "NONE")));
				menu.add(none);
				for (Map<String, Object> option : Model.list(supply, "options"))
				{
					String key = Model.str(option, "key");
					javax.swing.JMenuItem item = new javax.swing.JMenuItem(Model.str(option, "name"));
					item.addActionListener(e -> commands.send("set-supply-override",
						Map.of("category", category, "choice", key)));
					menu.add(item);
				}
				cell.setComponentPopupMenu(menu);
				supplyRow.add(cell);
			}
			card.add(Box.createVerticalStrut(4));
			card.add(left(supplyRow));
		}
		javax.swing.JTextField note = new javax.swing.JTextField(Model.str(mob, "note"), 18);
		note.setToolTipText("Your note for this mob - saved on Enter");
		note.addActionListener(e -> commands.send("set-note", Map.of("text", note.getText())));
		card.add(Box.createVerticalStrut(4));
		card.add(left(note));
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

	private JPanel side(String caption, Map<String, Object> card, boolean bis, double thrallsDps,
		Map<String, Object> mob, String tab)
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
		Map<String, Object> stats = Model.map(card, "stats");
		if (stats != null)
		{
			Map<String, Object> off = Model.map(stats, "offensive");
			Map<String, Object> def = Model.map(stats, "defensive");
			JLabel statLine = new JLabel(String.format(
				"Atk %d/%d/%d m%d r%d   Str %d RStr %d MDmg %d%% Pray %d",
				Model.id(off, "stab"), Model.id(off, "slash"), Model.id(off, "crush"),
				Model.id(off, "magic"), Model.id(off, "ranged"),
				Model.id(stats, "strength"), Model.id(stats, "rangedStrength"),
				Model.id(stats, "magicDamage"), Model.id(stats, "prayer")));
			statLine.setFont(statLine.getFont().deriveFont(statLine.getFont().getSize() - 2f));
			statLine.setToolTipText(String.format(
				"<html>Offence: stab %d, slash %d, crush %d, magic %d, ranged %d"
					+ "<br>Defence: stab %d, slash %d, crush %d, magic %d, ranged %d</html>",
				Model.id(off, "stab"), Model.id(off, "slash"), Model.id(off, "crush"),
				Model.id(off, "magic"), Model.id(off, "ranged"),
				Model.id(def, "stab"), Model.id(def, "slash"), Model.id(def, "crush"),
				Model.id(def, "magic"), Model.id(def, "ranged")));
			panel.add(left(statLine));
		}
		javax.swing.JToggleButton showBank = new javax.swing.JToggleButton("Show in bank");
		showBank.setToolTipText("Highlight this set (and its inventory) in your open bank");
		showBank.setFocusable(false);
		showBank.setMargin(new java.awt.Insets(1, 6, 1, 6));
		showBank.addActionListener(e ->
		{
			if (showBank.isSelected())
			{
				List<Integer> ids = new java.util.ArrayList<>();
				Map<String, Object> gearMap = Model.map(card, "gear");
				if (gearMap != null)
				{
					for (Object slotItem : gearMap.values())
					{
						if (slotItem instanceof Map)
						{
							ids.add((int) Model.num((Map<String, Object>) slotItem, "id"));
						}
					}
				}
				Map<String, Object> quiverItem = Model.map(card, "quiverAmmo");
				if (quiverItem != null)
				{
					ids.add(Model.id(quiverItem, "id"));
				}
				for (Map<String, Object> carried : Model.list(card, "bench"))
				{
					ids.add(Model.id(carried, "id"));
				}
				commands.send("bank-show", Map.of("ids", ids));
			}
			else
			{
				commands.send("bank-show", Map.of());
			}
		});
		panel.add(left(showBank));
		if (specNode != null && !bis)
		{
			Map<String, Object> specWeapon = Model.map(specNode, "weapon");
			int specId = specWeapon == null ? 0 : Model.id(specWeapon, "id");
			boolean pinned = specId != 0 && Model.id(mob, "pinnedSpec") == specId;
			JLabel specCell = new JLabel("Spec: " + Model.str(specWeapon, "name")
				+ (pinned ? " (pinned)" : ""));
			specCell.setToolTipText(pinned
				? "Pinned - right-click to unpin"
				: "Right-click to pin this spec weapon for this mob");
			javax.swing.JPopupMenu specMenu = new javax.swing.JPopupMenu();
			javax.swing.JMenuItem pinSpec = new javax.swing.JMenuItem(
				pinned ? "Unpin spec" : "Pin as spec");
			pinSpec.addActionListener(e -> commands.send("set-pinned-spec",
				Map.of("itemId", pinned ? 0 : specId)));
			specMenu.add(pinSpec);
			specCell.setComponentPopupMenu(specMenu);
			panel.add(left(specCell));
		}
		if (!bis && assumeOptions != null)
		{
			Map<String, Object> prayerPicks = Model.map(pageParams, "prayerPicks");
			Map<String, Object> boostPicks = Model.map(pageParams, "boostPicks");
			Map<String, Object> prayerLists = Model.map(assumeOptions, "prayers");
			JPanel pickRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
			pickRow.setBackground(CARD);
			javax.swing.JComboBox<String> prayer = new javax.swing.JComboBox<>();
			prayer.addItem("Detect best");
			prayer.addItem("None (prayerless)");
			if (prayerLists != null)
			{
				for (Object option : Model.list2(prayerLists, tab))
				{
					prayer.addItem(String.valueOf(option));
				}
			}
			String prayerPick = prayerPicks == null ? null : Model.str(prayerPicks, tab);
			prayer.setSelectedItem(prayerPick == null ? "Detect best"
				: "NONE".equals(prayerPick) ? "None (prayerless)" : prayerPick);
			prayer.setToolTipText("The prayer the numbers assume for this style");
			prayer.setFocusable(false);
			prayer.addActionListener(e ->
			{
				String picked = String.valueOf(prayer.getSelectedItem());
				Map<String, Object> args = new java.util.HashMap<>();
				args.put("style", tab);
				args.put("value", "Detect best".equals(picked) ? null
					: "None (prayerless)".equals(picked) ? "NONE" : picked);
				commands.send("set-prayer-pick", args);
			});
			pickRow.add(prayer);
			javax.swing.JComboBox<String> boost = new javax.swing.JComboBox<>();
			boost.addItem("Detect best in bank");
			boost.addItem("None (unboosted)");
			java.util.Map<String, String> boostKeyByLabel = new java.util.LinkedHashMap<>();
			Map<String, Object> boostLists = Model.map(assumeOptions, "boosts");
			if (boostLists != null)
			{
				for (Map<String, Object> option : Model.list(boostLists, tab))
				{
					String label = Model.str(option, "label");
					boostKeyByLabel.put(label, Model.str(option, "key"));
					boost.addItem(label);
				}
			}
			String boostPick = boostPicks == null ? null : Model.str(boostPicks, tab);
			String boostLabel = "Detect best in bank";
			if ("NONE".equals(boostPick))
			{
				boostLabel = "None (unboosted)";
			}
			else if (boostPick != null)
			{
				for (Map.Entry<String, String> entry : boostKeyByLabel.entrySet())
				{
					if (entry.getValue().equals(boostPick))
					{
						boostLabel = entry.getKey();
					}
				}
			}
			boost.setSelectedItem(boostLabel);
			boost.setToolTipText("The boost potion the numbers assume for this style");
			boost.setFocusable(false);
			boost.addActionListener(e ->
			{
				String picked = String.valueOf(boost.getSelectedItem());
				Map<String, Object> args = new java.util.HashMap<>();
				args.put("style", tab);
				args.put("value", "Detect best in bank".equals(picked) ? null
					: "None (unboosted)".equals(picked) ? "NONE" : boostKeyByLabel.get(picked));
				commands.send("set-boost-pick", args);
			});
			pickRow.add(boost);
			panel.add(left(pickRow));
		}
		if ("magic".equals(tab) && !bis)
		{
			String pinnedSpell = Model.str(mob, "pinnedSpell");
			javax.swing.JComboBox<String> spellBox = new javax.swing.JComboBox<>();
			spellBox.addItem("Auto spell");
			for (String spellName : spellOptions)
			{
				spellBox.addItem(spellName);
			}
			spellBox.setSelectedItem(pinnedSpell == null || pinnedSpell.isEmpty()
				? "Auto spell" : pinnedSpell);
			spellBox.setToolTipText("Pin the autocast spell for this mob");
			spellBox.setFocusable(false);
			spellBox.addActionListener(e -> commands.send("set-pinned-spell",
				Map.of("name", "Auto spell".equals(spellBox.getSelectedItem())
					? "" : String.valueOf(spellBox.getSelectedItem()))));
			panel.add(left(spellBox));
		}
		Map<String, Object> bankPlan = BankLayout.build(card);
		if (bankPlan != null)
		{
			javax.swing.JToggleButton filterBank = new javax.swing.JToggleButton("Filter bank");
			filterBank.setToolTipText("Filter your open bank to this set, laid out as the"
				+ " equipment cross with the inventory beside it");
			filterBank.setFocusable(false);
			filterBank.setMargin(new java.awt.Insets(1, 6, 1, 6));
			filterBank.addActionListener(e ->
			{
				if (filterBank.isSelected())
				{
					List<Integer> layoutList = new java.util.ArrayList<>();
					for (int pos : (int[]) bankPlan.get("layout"))
					{
						layoutList.add(pos);
					}
					commands.send("bank-filter",
						Map.of("ids", bankPlan.get("ids"), "layout", layoutList));
				}
				else
				{
					commands.send("bank-filter", Map.of());
				}
			});
			panel.add(left(filterBank));
		}
		Map<String, Object> riskNode = Model.map(card, "risk");
		if (riskNode != null)
		{
			JLabel riskLine = new JLabel(String.format("Risk: %s gp",
				Gp.format((int) Math.min(Integer.MAX_VALUE, Model.num(riskNode, "riskGp")))));
			StringBuilder riskTip = new StringBuilder("<html>Kept on death:");
			for (Object kept : Model.list2(riskNode, "kept"))
			{
				riskTip.append("<br> ").append(kept);
			}
			riskTip.append("<br>Lost:");
			for (Object lost : Model.list2(riskNode, "lost"))
			{
				riskTip.append("<br> ").append(lost);
			}
			riskLine.setToolTipText(riskTip.append("</html>").toString());
			riskLine.setFont(riskLine.getFont().deriveFont(riskLine.getFont().getSize() - 1f));
			panel.add(left(riskLine));
		}
		int upgradeCost = (int) Model.num(card, "purchaseCost");
		if (upgradeCost > 0)
		{
			JLabel costLine = new JLabel("Upgrade cost: " + Gp.format(upgradeCost) + " gp");
			costLine.setFont(costLine.getFont().deriveFont(costLine.getFont().getSize() - 1f));
			panel.add(left(costLine));
		}
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
			javax.swing.JMenuItem excludeMob = new javax.swing.JMenuItem("Exclude for this mob");
			excludeMob.setToolTipText("Exclude " + name + " for this mob only (all sets)");
			excludeMob.addActionListener(e -> commands.send("exclude-for-mob",
				Map.of("itemId", id, "scope", "ALL")));
			menu.add(excludeMob);
			javax.swing.JMenuItem simMob = new javax.swing.JMenuItem("Sim for this mob (search)...");
			simMob.setToolTipText("Search an item and sim it as owned for this mob only");
			simMob.addActionListener(e -> picker.search("Sim for this mob",
				(pickedId, pickedName) -> commands.send("sim-for-mob", Map.of("itemId", pickedId))));
			menu.add(simMob);
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
