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
	private final net.runelite.client.game.SpriteManager spriteManager;
	private final CommandSink commands;
	private final ItemPicker picker;
	private List<String> spellOptions = List.of();
	private Map<String, Object> assumeOptions;
	private String liveSpellbook;
	private Map<String, Object> spellbooks;
	private List<Map<String, Object>> supplyCatalog = List.of();
	private List<Map<String, Object>> dartTiers = List.of();
	private Map<String, Object> pageWide;
	private Map<String, Object> pageParams;
	private javax.swing.JButton addMob;

	void setAddMob(javax.swing.JButton addMob)
	{
		this.addMob = addMob;
	}

	private javax.swing.JButton reload;

	void setReload(javax.swing.JButton reload)
	{
		this.reload = reload;
	}

	public ResultCards(ItemManager itemManager,
		net.runelite.client.game.SpriteManager spriteManager,
		CommandSink commands, ItemPicker picker)
	{
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
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
				// No explicit pick: default to the RECOMMENDED style -
				// highest owned dps, kit-backed styles first (field report
				// 2026-08-14: Kraken opened on ranged with magic higher).
				String best = null;
				double bestDps = -1;
				boolean bestKitBacked = false;
				for (String style : STYLES)
				{
					Map<String, Object> node = Model.map(styles, style);
					if (node == null)
					{
						continue;
					}
					Map<String, Object> yours = Model.map(node, "yours");
					boolean kitBacked = yours != null && Model.flag(yours, "kitBacked");
					double dps = Model.num(node, "tabDps");
					if (best == null || (kitBacked && !bestKitBacked)
						|| (kitBacked == bestKitBacked && dps > bestDps))
					{
						best = style;
						bestDps = dps;
						bestKitBacked = kitBacked;
					}
				}
				if (best != null)
				{
					return best;
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

	/** The captures every render pass needs (page-level facts). */
	private void capture(Map<String, Object> page)
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
		liveSpellbook = Model.str(page, "liveSpellbook");
		spellbooks = Model.map(page, "spellbooks");
		supplyCatalog = Model.list(page, "supplyCatalog");
		dartTiers = Model.list(page, "dartTiers");
		pageWide = page;
		pageParams = firstParams(page);
	}

	/** The MOB LIST for the control section (Andrew's three-section
	 * design, 2026-08-15): rounded padded rows + the +Mob button, alive
	 * on pending pages too. Null when nothing is selected. */
	JPanel renderRoster(Map<String, Object> page)
	{
		capture(page);
		String tab = effectiveTab(page);
		Map<String, Object> params = firstParams(page);
		boolean bis = params != null && Model.flag(params, "viewingBis");
		int lens = params == null ? 0 : Model.id(params, "lensIndex");
		for (Map<String, Object> entry : Model.list(page, "entries"))
		{
			List<Map<String, Object>> mobs = Model.list(entry, "mobs");
			if (mobs.isEmpty())
			{
				continue;
			}
			double rosterThralls = Model.num(Model.map(entry, "thralls"), "dps");
			JPanel box = new JPanel();
			box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
			box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			int shownLens = Math.min(Math.max(lens, 0), mobs.size() - 1);
			for (int i = 0; i < mobs.size(); i++)
			{
				box.add(lensRow(mobs.get(i), tab, bis, i, i == shownLens, rosterThralls));
				if (i < mobs.size() - 1)
				{
					box.add(Box.createVerticalStrut(2));
				}
			}
			if (addMob != null)
			{
				JPanel addRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 2));
				addRow.setOpaque(false);
				addRow.add(addMob);
				if (reload != null)
				{
					addRow.add(reload);
				}
				box.add(Box.createVerticalStrut(2));
				box.add(addRow);
			}
			return box;
		}
		return null;
	}

	/** The OUTPUT section: the shown card only - the mob list lives in
	 * the control section now. Pending pages render nothing (the
	 * animation owns the space). */
	JPanel render(Map<String, Object> page)
	{
		capture(page);
		String tab = effectiveTab(page);
		Map<String, Object> params = firstParams(page);
		boolean bis = params != null && Model.flag(params, "viewingBis");
		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setBackground(ColorScheme.DARK_GRAY_COLOR);
		int lens = params == null ? 0 : Model.id(params, "lensIndex");
		for (Map<String, Object> entry : Model.list(page, "entries"))
		{
			if (Model.flag(entry, "pending"))
			{
				continue;
			}
			double thrallsDps = Model.num(Model.map(entry, "thralls"), "dps");
			List<Map<String, Object>> mobs = Model.list(entry, "mobs");
			rosterView = mobs.size() > 1;
			if (mobs.isEmpty())
			{
				continue;
			}
			int shownLens = Math.min(Math.max(lens, 0), mobs.size() - 1);
			column.add(mobCard(mobs.get(shownLens), tab, bis, thrallsDps));
			column.add(Box.createVerticalStrut(8));
		}
		return column;
	}

	/** One switcher row, aligned in columns: the mob name (shortened to
	 * fit) with its level, then a FIXED-WIDTH block - style icon, dps,
	 * remove - so those line up down the whole list. */
	private JPanel lensRow(Map<String, Object> mob, String tab, boolean bis, int index,
		boolean selected, double thrallsDps)
	{
		Map<String, Object> styles = Model.map(mob, "styles");
		// The row reports ITS OWN recommendation (field report
		// 2026-08-14: rows showed the viewed tab's dps beside the
		// best-style icon) - the number belongs to the icon's style.
		String bestStyle = null;
		double bestDps = 0;
		if (styles != null)
		{
			for (String style : STYLES)
			{
				Map<String, Object> styleNode = Model.map(styles, style);
				Map<String, Object> side = styleNode == null ? null
					: Model.map(styleNode, bis ? "bis" : "yours");
				if (side == null || !Model.flag(side, "kitBacked"))
				{
					continue;
				}
				double value = Model.num(styleNode, bis ? "bisTabDps" : "tabDps");
				if (value > bestDps)
				{
					bestDps = value;
					bestStyle = style;
				}
			}
		}
		Map<String, Object> node = styles == null ? null : Model.map(styles, tab);
		Map<String, Object> shown = node == null ? null : Model.map(node, bis ? "bis" : "yours");
		boolean tabOnly = shown != null && !Model.flag(shown, "kitBacked");
		// Rows and tab buttons COUNT THE SAME THINGS (field report
		// 2026-08-15): set + spec (in the tab facts) + thralls.
		String dps = bestStyle != null
			? String.format("%.2f", bestDps + (bestDps > 0 ? thrallsDps : 0))
			: shown == null ? "-"
			: String.format("%.2f%s",
				Model.num(shown, "dps") + thrallsDps, tabOnly ? "*" : "");

		String rowStyle = bestStyle;
		JPanel row = new JPanel(new BorderLayout(4, 0));
		// Selection is an EDGE, not a box: the active mob wears a 3px
		// accent bar and a faint tint; the rest stay transparent.
		row.setOpaque(selected);
		if (selected)
		{
			row.setBackground(new Color(46, 56, 46));
		}
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, selected ? 3 : 0, 0, 0, ACCENT),
			BorderFactory.createEmptyBorder(4, selected ? 7 : 10, 4, 4)));
		row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 28));

		String name = Model.str(mob, "label");
		int level = Model.id(mob, "level");
		JLabel label = new JLabel(name + (level > 0 ? "  " + level : ""));
		label.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
		label.setForeground(selected ? ACCENT : new Color(200, 200, 200));
		// The name yields first when the panel narrows - the columns to
		// its right keep their width, so they stay aligned.
		label.setMinimumSize(new java.awt.Dimension(20, 18));
		label.setToolTipText("<html>" + Model.str(mob, "label")
			+ (bestStyle != null
				? "<br>" + dps + " dps with " + bestStyle + " (this mob's best in the trip kit)"
				: tabOnly ? "<br>* tab-only - the shared kit does not carry this style here" : "")
			+ "<br>Click to show this mob</html>");
		Ui.onClick(label, () ->
		{
			label.setForeground(ACCENT);
			// Switching mobs also switches to THAT mob's suggested
			// style (field ask 2026-08-15) - the card below always
			// opens on the row's own recommendation.
			if (rowStyle != null && !rowStyle.equals(tab))
			{
				commands.send("set-param",
					Map.of("param", "selectedTab", "value", rowStyle));
			}
			commands.send("set-param", Map.of("param", "lensIndex", "value", index));
		});
		row.add(label, BorderLayout.CENTER);

		// The aligned right block: [icon dps] x - the icon rides right,
		// snug against its number.
		JPanel columns = new JPanel(new BorderLayout(2, 0));
		columns.setOpaque(false);

		JLabel icon = new JLabel();
		icon.setPreferredSize(new java.awt.Dimension(18, 18));
		icon.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
		if (bestStyle != null && spriteManager != null)
		{
			int sprite = (int) Model.num(Model.map(styles, bestStyle), "styleSprite");
			String best = bestStyle;
			if (sprite > 0)
			{
				spriteManager.getSpriteAsync(sprite, 0, img ->
					javax.swing.SwingUtilities.invokeLater(() ->
						icon.setIcon(new javax.swing.ImageIcon(img))));
			}
			icon.setToolTipText("Best answered with " + best);
		}
		JPanel iconDps = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 3, 0));
		iconDps.setOpaque(false);
		iconDps.add(icon);

		JLabel dpsLabel = new JLabel(dps);
		dpsLabel.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
		dpsLabel.setForeground(selected ? Color.WHITE : new Color(170, 170, 170));
		iconDps.add(dpsLabel);
		columns.add(iconDps, BorderLayout.CENTER);

		JLabel remove = new JLabel("x", javax.swing.SwingConstants.CENTER);
		remove.setPreferredSize(new java.awt.Dimension(14, 18));
		remove.setForeground(new Color(150, 150, 150));
		remove.setToolTipText("Remove " + name + " from this result");
		remove.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		remove.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				commands.send("remove-mob", Map.of("index", index));
			}

			@Override
			public void mouseEntered(java.awt.event.MouseEvent e)
			{
				remove.setForeground(new Color(220, 120, 120));
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e)
			{
				remove.setForeground(new Color(150, 150, 150));
			}
		});
		columns.add(remove, BorderLayout.EAST);
		row.add(columns, BorderLayout.EAST);
		return row;
	}

	/** The brand accent (the mascot-era green lives on as a plain constant). */
	static final Color ACCENT = new Color(140, 200, 140);

	private boolean rosterView;
	/** Set per side() render for the #spec cell's tooltip + pin menu. */
	private double cellSpecDps;
	private int cellSpecWeaponId;
	private boolean cellSpecPinned;

	private JPanel mobCard(Map<String, Object> mob, String tab, boolean bis, double thrallsDps)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(CARD);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(
				bis ? ACCENT : ColorScheme.DARKER_GRAY_HOVER_COLOR, 1, true),
			BorderFactory.createEmptyBorder(8, 8, 8, 8)));
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
		JPanel headerRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 4, 0));
		headerRow.setBackground(CARD);
		headerRow.add(styleHeader(tab, node));
		// Assume icons ride the header (the vertical-space rule): the
		// prayer sprite + boost potion icon, tooltip carries the words.
		Map<String, Object> assume = Model.map(node, bis ? "bisAssume" : "assume");
		if (assume != null)
		{
			String text = Model.str(assume, "text");
			// The icons ARE the pickers on the Yours side (field ask
			// 2026-08-14): click the wings to pick the prayer, the
			// potion to pick the boost. BiS icons stay informational.
			int prayerSprite = (int) Model.num(assume, "prayerSprite");
			JLabel prayerIcon = new JLabel();
			prayerIcon.setPreferredSize(new java.awt.Dimension(20, 20));
			prayerIcon.setToolTipText(bis ? "Assumes: " + text
				: "Assumes: " + text + " - click to change the prayer");
			int shownPrayerSprite = prayerSprite > 0 ? prayerSprite
				: net.runelite.api.SpriteID.TAB_PRAYER;
			if (spriteManager != null)
			{
				spriteManager.getSpriteAsync(shownPrayerSprite, 0, img ->
					javax.swing.SwingUtilities.invokeLater(() ->
						prayerIcon.setIcon(Ui.icon(img, 18))));
			}
			if (!bis)
			{
				String tabKey = tab;
				Ui.onClick(prayerIcon, () -> showPrayerMenu(prayerIcon, tabKey));
			}
			headerRow.add(prayerIcon);
			int boostItem = (int) Model.num(assume, "boostItem");
			JLabel boostIconCell = new JLabel();
			boostIconCell.setPreferredSize(new java.awt.Dimension(24, 24));
			boostIconCell.setToolTipText(bis ? "Assumes: " + text
				: "Assumes: " + text + " - click to change the boost");
			if (boostItem > 0)
			{
				itemManager.getImage(boostItem).addTo(boostIconCell);
			}
			else
			{
				// No potion assumed (None/prayer-only): a muted vial
				// keeps the toggle reachable.
				boostIconCell.setText("-");
				boostIconCell.setForeground(new Color(140, 140, 140));
				boostIconCell.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
			}
			if (!bis)
			{
				String tabKey = tab;
				Ui.onClick(boostIconCell, () -> showBoostMenu(boostIconCell, tabKey));
			}
			headerRow.add(boostIconCell);
		}
		headerRow.add(bookPlate(Model.map(node, bis ? "bis" : "yours"), mob));
		card.add(left(headerRow));
		Map<String, Object> shown = Model.map(node, bis ? "bis" : "yours");
		if (shown == null)
		{
			// NEVER show the other side in this slot (field bug 2026-08-12:
			// a missing owned answer silently rendered the BiS set - "gear
			// that you don't have").
			JLabel none = new JLabel("No usable " + tab + " set");
			card.add(left(none));
		}
		else
		{
			if (!Model.flag(shown, "kitBacked") && rosterView)
			{
				// The vents rule (2026-08-06): a tab-only fallback must
				// never read as the trip's answer.
				JLabel caveat = Ui.label(
					"Tab-only view - the shared kit does not carry this style here",
					new Color(220, 170, 90));
				caveat.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
				card.add(left(caveat));
			}
			card.add(left(side(bis ? "Best in game" : "Yours", shown, bis, thrallsDps, mob, tab)));
		}
		// The classic style strip sits BELOW the gear view: one tab per
		// style carrying its icon and that style's shown dps.
		JPanel tabStrip = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 2, 0));
		tabStrip.setBackground(CARD);
		for (String style : STYLES)
		{
			Map<String, Object> styleNode = Model.map(styles, style);
			double tabDps = styleNode == null ? 0
				: Model.num(styleNode, bis ? "bisTabDps" : "tabDps");
			if (tabDps > 0)
			{
				// Thralls fold into the shown number, like spec already
				// does - the tab IS the total (field ask 2026-08-14).
				tabDps += thrallsDps;
			}
			boolean hasSet = tabDps > 0;
			boolean isSelected = style.equals(tab);
			JPanel tabCell = new JPanel(new BorderLayout(2, 0));
			tabCell.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
			tabCell.setPreferredSize(new java.awt.Dimension(64, 26));
			tabCell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			tabCell.setOpaque(isSelected);
			tabCell.setBorder(new RoundedBorder(isSelected
				? ACCENT : ColorScheme.MEDIUM_GRAY_COLOR, 1, 2));
			JLabel icon = new JLabel();
			int sprite = styleNode == null ? -1 : (int) Model.num(styleNode, "styleSprite");
			if (sprite > 0 && spriteManager != null)
			{
				spriteManager.getSpriteAsync(sprite, 0, img ->
					javax.swing.SwingUtilities.invokeLater(() ->
						icon.setIcon(Ui.icon(img, 16))));
			}
			JLabel dpsLabel = Ui.label(hasSet ? String.format("%.2f", tabDps) : "-",
				isSelected ? Color.WHITE : new Color(160, 160, 160));
			dpsLabel.setFont(dpsLabel.getFont().deriveFont(Font.BOLD, 12f));
			dpsLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
			tabCell.add(icon, BorderLayout.WEST);
			tabCell.add(dpsLabel, BorderLayout.CENTER);
			tabCell.setToolTipText(Character.toUpperCase(style.charAt(0)) + style.substring(1)
				+ (hasSet ? String.format(" - %.2f DPS", tabDps) : " - no set"));
			String styleKey = style;
			Ui.onClick(tabCell, () ->
			{
				// Instant feedback, reconciled by the arriving page.
				tabCell.setBorder(new RoundedBorder(ACCENT, 1, 2));
				tabCell.setOpaque(true);
				tabCell.repaint();
				commands.send("set-param",
					Map.of("param", "selectedTab", "value", styleKey));
			});
			tabStrip.add(tabCell);
		}
		java.util.LinkedHashMap<String, Color> present = new java.util.LinkedHashMap<>();
		Map<String, Object> shownGear = Model.map(
			Model.map(node, bis ? "bis" : "yours"), "gear");
		if (shownGear != null)
		{
			for (Object slotItem : shownGear.values())
			{
				if (slotItem instanceof Map)
				{
					@SuppressWarnings("unchecked")
					String where = Model.str((Map<String, Object>) slotItem, "source");
					Color colour = sourceColor(where);
					if (colour != null)
					{
						present.put(where, colour);
					}
				}
			}
		}
		if (!present.isEmpty())
		{
			// "When we show a legend for colors only show for the colors
			// shown" (the classic rule).
			JPanel legend = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
			legend.setBackground(CARD);
			legend.add(statLine("Stored:", "Gear that needs a fetch trip",
				new Color(160, 160, 160)));
			for (Map.Entry<String, Color> source : present.entrySet())
			{
				JLabel tag = statLine(source.getKey(), "Fetch from " + source.getKey(),
					source.getValue());
				legend.add(tag);
			}
			card.add(Box.createVerticalStrut(4));
			card.add(left(legend));
		}
		card.add(Box.createVerticalStrut(6));
		card.add(tabStrip);
		// The classic view swap sits directly UNDER the tabs: two
		// bordered sides, the active one bright, with the gap readout
		// pushed to the right.
		JPanel viewRow = new JPanel(new BorderLayout());
		viewRow.setBackground(CARD);
		viewRow.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
		viewRow.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 32));
		JPanel sides = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 6, 0));
		sides.setBackground(CARD);
		sides.add(viewSide("Yours", !bis, "Your best owned set",
			() -> commands.send("set-param", Map.of("param", "viewingBis", "value", false))));
		sides.add(viewSide("BiS", bis, "The game-wide best set at your levels",
			() -> commands.send("set-param", Map.of("param", "viewingBis", "value", true))));
		viewRow.add(sides, BorderLayout.WEST);
		Map<String, Object> yoursNode = Model.map(node, "yours");
		Map<String, Object> bisNode = Model.map(node, "bis");
		if (yoursNode != null && bisNode != null)
		{
			double mine = Model.num(node, "tabDps");
			double ceiling = Model.num(node, "bisTabDps");
			if (ceiling > 0)
			{
				JLabel gap = Ui.label(String.format("you are at %.0f%%",
					Math.min(100.0, 100.0 * mine / ceiling)), new Color(160, 160, 160));
				gap.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
				gap.setToolTipText("Your best owned set vs the game-wide best, at your levels");
				viewRow.add(gap, BorderLayout.EAST);
			}
		}
		card.add(viewRow);
		Map<String, Object> shownForInv = Model.map(node, bis ? "bis" : "yours");
		List<Map<String, Object>> bench = Model.list(shownForInv, "bench");
		List<Map<String, Object>> runes = Model.list(shownForInv, "runes");
		List<Map<String, Object>> utilityRunes = Model.list(mob, "utilityRunes");
		boolean pouchRides = Model.id(mob, "castingPouch") > 0
			&& (!runes.isEmpty() || !utilityRunes.isEmpty());
		if (!bench.isEmpty() || pouchRides)
		{
			// 24px icons on a WRAPPING row - eight carried items fit
			// (field report: only 4 of 8 visible at full item size).
			JPanel invRow = new JPanel(new WrapLayout(java.awt.FlowLayout.CENTER, 2, 1));
			invRow.setBackground(CARD);
			JLabel invLabel = new JLabel("Inventory:");
			invLabel.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
			invRow.add(invLabel);
			for (Map<String, Object> carried : bench)
			{
				invRow.add(smallItemCell(Model.id(carried, "id"), 0,
					Model.str(carried, "name") + " (carried swap)"));
			}
			int pouchId = Model.id(mob, "castingPouch");
			if (pouchId > 0 && (!runes.isEmpty() || !utilityRunes.isEmpty()))
			{
				invRow.add(smallItemCell(pouchId, 0,
					(pouchId == 27281 ? "Divine rune pouch" : "Rune pouch")
						+ " - carries the trip's runes"));
			}
			card.add(Box.createVerticalStrut(4));
			card.add(left(invRow));
		}
		List<Map<String, Object>> castRunes = Model.list(
			Model.map(node, bis ? "bis" : "yours"), "runes");
		List<Map<String, Object>> tripRunes = Model.list(mob, "utilityRunes");
		if (!supplies.isEmpty() || !castRunes.isEmpty() || !tripRunes.isEmpty())
		{
			JPanel supplyRow = new JPanel(new WrapLayout(java.awt.FlowLayout.CENTER, 2, 1));
			supplyRow.setBackground(CARD);
			JLabel supplyLabel = new JLabel("Supplies:");
			supplyLabel.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
			supplyRow.add(supplyLabel);
			// One icon per rune TYPE (field call: no triple blood runes)
			// - the tooltip collects every reason it rides.
			java.util.Map<Integer, StringBuilder> runeWhy = new java.util.LinkedHashMap<>();
			java.util.Map<Integer, String> runeNames = new java.util.HashMap<>();
			for (Map<String, Object> rune : castRunes)
			{
				int id = Model.id(rune, "id");
				runeNames.put(id, Model.str(rune, "name"));
				runeWhy.computeIfAbsent(id, k -> new StringBuilder())
					.append(runeWhy.get(id).length() == 0 ? "" : "; ")
					.append("the autocast spell");
			}
			for (Map<String, Object> rune : tripRunes)
			{
				int id = Model.id(rune, "id");
				runeNames.put(id, Model.str(rune, "name"));
				runeWhy.computeIfAbsent(id, k -> new StringBuilder())
					.append(runeWhy.get(id).length() == 0 ? "" : "; ")
					.append(Model.str(rune, "why"));
			}
			for (java.util.Map.Entry<Integer, StringBuilder> rune : runeWhy.entrySet())
			{
				supplyRow.add(smallItemCell(rune.getKey(), 0,
					runeNames.get(rune.getKey()) + " - " + rune.getValue()));
			}
			int capeId = Model.id(mob, "castingCape");
			if (capeId > 0 && (!castRunes.isEmpty() || !tripRunes.isEmpty()))
			{
				supplyRow.add(smallItemCell(capeId, 0,
					"Magic cape - swap spellbooks up to 5x daily (owned)"));
			}
			for (Map<String, Object> supply : supplies)
			{
				JLabel cell = smallItemCell(Model.id(supply, "itemId"), 0,
					Model.str(supply, "name")
						+ " - right-click to change this category");
				javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
				String category = Model.str(supply, "category");
				Ui.item(menu, "Detect best", () -> commands.send("set-supply-override",
					Map.of("category", category, "choice", "DETECT")));
				Ui.item(menu, "None", () -> commands.send("set-supply-override",
					Map.of("category", category, "choice", "NONE")));
				for (Map<String, Object> option : Model.list(supply, "options"))
				{
					String key = Model.str(option, "key");
					Ui.item(menu, Model.str(option, "name"),
						() -> commands.send("set-supply-override",
							Map.of("category", category, "choice", key)));
				}
				cell.setComponentPopupMenu(menu);
				supplyRow.add(cell);
			}
			card.add(Box.createVerticalStrut(4));
			card.add(left(supplyRow));
		}
		JPanel trioRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 4, 0));
		trioRow.setBackground(CARD);
		trioRow.add(trioButton(mob, "+", "mobSims",
			new Color(130, 200, 130), new Color(110, 140, 110), new Color(95, 160, 95),
			"Simmed for this mob", "remove-mob-sim", "sim-for-mob",
			"Sim an item as owned for this mob"));
		trioRow.add(trioButton(mob, "-", "mobExclusions",
			new Color(220, 120, 120), new Color(140, 110, 110), new Color(170, 90, 90),
			"Excluded for this mob", "remove-mob-exclusion", "exclude-for-mob",
			"Exclude an item for this mob"));
		trioRow.add(trioButton(mob, "~", "mobFilters",
			new Color(190, 190, 190), new Color(130, 130, 130), new Color(150, 150, 150),
			"Bank-filter supplies for this mob", "remove-mob-filter", "add-mob-filter",
			"Add a supply to this mob's bank filter"));
		card.add(Box.createVerticalStrut(4));
		// Centred, not left() - field ask 2026-08-20.
		JPanel trioHost = new JPanel(new BorderLayout());
		trioHost.setBackground(CARD);
		trioHost.add(trioRow, BorderLayout.CENTER);
		card.add(trioHost);
		card.add(Box.createVerticalStrut(4));
		javax.swing.JToggleButton showBank = new javax.swing.JToggleButton("Show in bank");
		showBank.setToolTipText("Highlight this set (and its inventory) in your open bank");
		showBank.setFocusable(false);
		showBank.setMargin(new java.awt.Insets(1, 6, 1, 6));
		showBank.addActionListener(e ->
		{
			if (showBank.isSelected())
			{
				List<Integer> ids = new java.util.ArrayList<>();
				Map<String, Object> gearMap = Model.map(shownForInv, "gear");
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
				Map<String, Object> quiverItem = Model.map(shownForInv, "quiverAmmo");
				if (quiverItem != null)
				{
					ids.add(Model.id(quiverItem, "id"));
				}
				for (Map<String, Object> carried : Model.list(shownForInv, "bench"))
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
		JPanel bankRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 4, 0));
		bankRow.setBackground(CARD);
		styleBankButton(showBank);
		bankRow.add(showBank);
		Map<String, Object> bankPlan = BankLayout.build(shownForInv, utilityRunes);
		if (bankPlan != null)
		{
			javax.swing.JToggleButton filterBank = new javax.swing.JToggleButton("Filter bank");
			filterBank.setToolTipText("Filter your open bank to this set, laid out as the"
				+ " equipment cross with the inventory beside it");
			styleBankButton(filterBank);
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
			bankRow.add(filterBank);
		}
		card.add(left(bankRow));
		String noteText = Model.str(mob, "note");
		boolean hasNote = noteText != null && !noteText.trim().isEmpty();
		javax.swing.JTextField note = new javax.swing.JTextField(noteText, 18);
		note.setBackground(new Color(78, 72, 50));
		note.setForeground(new Color(215, 205, 160));
		note.setCaretColor(new Color(215, 205, 160));
		note.setBorder(BorderFactory.createLineBorder(new Color(98, 90, 62)));
		note.setToolTipText("Your note for this mob - saved on Enter");
		note.addActionListener(e -> commands.send("set-note", Map.of("text", note.getText())));
		card.add(Box.createVerticalStrut(4));
		if (hasNote)
		{
			card.add(left(note));
		}
		else
		{
			// A ghost until there is something to say - the open post-it
			// field shouted for attention it rarely deserved.
			JLabel addNote = Ui.label("+ note", new Color(120, 115, 95));
			addNote.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
			addNote.setToolTipText("Add a note for this mob");
			JPanel noteHost = left(addNote);
			Ui.onClick(addNote, () ->
			{
				noteHost.removeAll();
				noteHost.add(note);
				note.requestFocusInWindow();
				noteHost.revalidate();
				noteHost.repaint();
			});
			card.add(noteHost);
		}
		return card;
	}

	/** One classic local chip: "Exclude (2)" opens the mob-scoped list
	 * (remove per entry) plus an add-by-search entry. */
	private javax.swing.JButton trioButton(Map<String, Object> mob, String sigil,
		String listKey, Color active, Color muted, Color activeBorder,
		String noun, String removeCommand, String addCommand, String addPrompt)
	{
		List<Map<String, Object>> items = Model.list(mob, listKey);
		javax.swing.JButton button = new javax.swing.JButton(sigil + items.size());
		boolean any = !items.isEmpty();
		button.setForeground(any ? active : muted);
		button.setContentAreaFilled(false);
		button.setBorder(any ? new RoundedBorder(activeBorder, 2, 14)
			: javax.swing.BorderFactory.createEmptyBorder(3, 15, 3, 15));
		button.setToolTipText(noun + " - click to manage");
		button.setFocusable(false);
		button.addActionListener(e ->
		{
			javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
			for (Map<String, Object> item : items)
			{
				int id = Model.id(item, "id");
				String scope = Model.str(item, "scope");
				Ui.item(menu, "Remove " + Model.str(item, "name")
					+ (scope == null || "ALL".equals(scope) ? "" : " (" + scope + ")"), () ->
				{
					Map<String, Object> args = new java.util.HashMap<>();
					args.put("itemId", id);
					if (scope != null)
					{
						args.put("scope", scope);
					}
					commands.send(removeCommand, args);
				});
			}
			if (!items.isEmpty())
			{
				menu.addSeparator();
			}
			Ui.item(menu, addPrompt + " (search)...", () -> picker.search(addPrompt,
				(id, name) -> commands.send(addCommand, Map.of("itemId", id))));
			menu.show(button, 0, button.getHeight());
		});
		return button;
	}

	/** One side of the classic view swap: the active side wears a bright
	 * accent edge and white text, the other a quiet outline. */
	private static JLabel viewSide(String text, boolean selected, String tooltip, Runnable onClick)
	{
		JLabel side = Ui.label(text, selected ? Color.WHITE : new Color(150, 150, 150));
		side.setFont(side.getFont().deriveFont(Font.BOLD, 14f));
		side.setBorder(new RoundedBorder(selected
			? ACCENT : ColorScheme.MEDIUM_GRAY_COLOR, 5, 12));
		side.setToolTipText(tooltip);
		Ui.onClick(side, () ->
		{
			// Instant feedback; the arriving page confirms.
			side.setForeground(Color.WHITE);
			side.setBorder(new RoundedBorder(ACCENT, 5, 12));
			side.repaint();
			onClick.run();
		});
		return side;
	}

	private final java.util.Map<Integer, javax.swing.ImageIcon> spriteIconCache =
		new java.util.HashMap<>();

	/** The set's spellbook plate, ALWAYS shown (field report x3): the
	 * autocast spell's book, else arceuus/lunar when thralls or Death
	 * Charge assume it, else your live book in neutral. RED when the
	 * required book is not the one you are on. */
	private JLabel bookPlate(Map<String, Object> shownCard, Map<String, Object> mob)
	{
		String required = null;
		String reason = null;
		String spell = shownCard == null ? null : Model.str(shownCard, "spell");
		if (spell != null && spellbooks != null)
		{
			required = Model.str(spellbooks, spell);
			reason = "autocasts " + spell;
		}
		// The FIGHT's own book beats every gear consideration (wiki-
		// verified curation - the Sire wants Ancients for the Shadow
		// Barrage stun regardless of what the set casts).
		String fightBook = mob == null ? null : Model.str(mob, "fightBook");
		if (fightBook != null && !fightBook.isEmpty())
		{
			required = fightBook;
			reason = Model.str(mob, "fightBookReason");
		}
		if (required == null && pageParams != null
			&& (Model.flag(pageParams, "thralls") || Model.id(pageParams, "deathCharge") > 0))
		{
			boolean viaSwap = false;
			for (Map<String, Object> category : supplyCatalog)
			{
				if ("arceuusAccess".equals(Model.str(category, "category")))
				{
					viaSwap = "SPELLBOOK_SWAP".equals(Model.str(category, "current"));
				}
			}
			required = viaSwap ? "lunar" : "arceuus";
			reason = viaSwap
				? "Lunar home, Spellbook Swap into Arceuus for the summons"
				: "thralls / Death Charge live on Arceuus";
		}
		boolean offBook = required != null && liveSpellbook != null
			&& !liveSpellbook.isEmpty() && !liveSpellbook.equalsIgnoreCase(required);
		JLabel plate = new JLabel();
		plate.setPreferredSize(new java.awt.Dimension(24, 22));
		plate.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
		if (offBook)
		{
			plate.setOpaque(true);
			plate.setBackground(new Color(120, 40, 40));
		}
		// No required book = a DIMMED generic tab, never a real book's
		// sprite (field confusion 2026-08-14: the live book shown in
		// neutral read as a recommendation - a powered staff like the
		// Shadow needs no book at all).
		plate.setToolTipText(required == null
			? "No specific spellbook required - a powered staff casts on any book"
			: "This set wants the " + required.toUpperCase() + " book (" + reason + ")"
				+ (offBook ? " - you are NOT on it" : " - you are on it"));
		int sprite = required == null
			? net.runelite.api.SpriteID.TAB_MAGIC : bookSprite(required);
		boolean dim = required == null;
		if (sprite > 0 && spriteManager != null)
		{
			spriteManager.getSpriteAsync(sprite, 0, img ->
				javax.swing.SwingUtilities.invokeLater(() ->
				{
					java.awt.Image scaled =
						img.getScaledInstance(18, 18, java.awt.Image.SCALE_SMOOTH);
					plate.setIcon(dim
						? new javax.swing.ImageIcon(javax.swing.GrayFilter
							.createDisabledImage(toBuffered(scaled)))
						: new javax.swing.ImageIcon(scaled));
				}));
		}
		return plate;
	}

	private static java.awt.image.BufferedImage toBuffered(java.awt.Image image)
	{
		java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
			18, 18, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = out.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return out;
	}

	/** The book tab sprites (net.runelite.api.SpriteID). */
	private static int bookSprite(String book)
	{
		switch (book == null ? "" : book.toLowerCase())
		{
			case "ancient": return net.runelite.api.SpriteID.TAB_MAGIC_SPELLBOOK_ANCIENT_MAGICKS;
			case "lunar": return net.runelite.api.SpriteID.TAB_MAGIC_SPELLBOOK_LUNAR;
			case "arceuus": return net.runelite.api.SpriteID.TAB_MAGIC_SPELLBOOK_ARCEUUS;
			case "standard": return net.runelite.api.SpriteID.TAB_MAGIC;
			default: return -1;
		}
	}

	/** The prayer picker popup (anchored to the wings icon): Detect /
	 * None / each prayer with its sprite, checkmarked at the pick. */
	private void showPrayerMenu(java.awt.Component anchor, String tab)
	{
		Map<String, Object> prayerPicks = Model.map(pageParams, "prayerPicks");
		String current = prayerPicks == null ? null : Model.str(prayerPicks, tab);
		javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		menu.add(pickChoice("Detect best", current == null,
			() -> sendPick("set-prayer-pick", tab, null)));
		menu.add(pickChoice("None (prayerless)", "NONE".equals(current),
			() -> sendPick("set-prayer-pick", tab, "NONE")));
		Map<String, Object> prayerLists = Model.map(assumeOptions, "prayers");
		Map<String, Object> sprites = Model.map(assumeOptions, "prayerSprites");
		if (prayerLists != null)
		{
			// The PRAYER BOOK view (field ask 2026-08-14): the options
			// render as the game's prayer-tab grid - sprite cells, the
			// current pick on the accent plate - not a text list.
			List<Object> names = Model.list2(prayerLists, tab);
			if (!names.isEmpty())
			{
				menu.addSeparator();
				JPanel book = Ui.darker(new GridLayout(0, 5, 2, 2));
				book.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
				for (Object option : names)
				{
					String name = String.valueOf(option);
					boolean picked = name.equals(current);
					JLabel cell = new JLabel();
					cell.setPreferredSize(new java.awt.Dimension(26, 26));
					cell.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
					cell.setOpaque(true);
					cell.setBackground(picked ? new Color(0x2e, 0x40, 0x2e)
						: ColorScheme.DARKER_GRAY_COLOR);
					cell.setBorder(BorderFactory.createLineBorder(picked
						? ACCENT : ColorScheme.DARKER_GRAY_HOVER_COLOR));
					int sprite = sprites == null ? -1 : (int) Model.num(sprites, name);
					if (sprite > 0)
					{
						cell.setIcon(cachedSprite(sprite));
					}
					else
					{
						cell.setText(name.substring(0, 1));
						cell.setForeground(new Color(190, 190, 190));
					}
					cell.setToolTipText(name + (picked ? " (assumed)" : ""));
					cell.setCursor(java.awt.Cursor.getPredefinedCursor(
						java.awt.Cursor.HAND_CURSOR));
					cell.addMouseListener(new java.awt.event.MouseAdapter()
					{
						@Override
						public void mouseClicked(java.awt.event.MouseEvent e)
						{
							menu.setVisible(false);
							sendPick("set-prayer-pick", tab, name);
						}

						@Override
						public void mouseEntered(java.awt.event.MouseEvent e)
						{
							cell.setBorder(BorderFactory.createLineBorder(ACCENT));
						}

						@Override
						public void mouseExited(java.awt.event.MouseEvent e)
						{
							if (!picked)
							{
								cell.setBorder(BorderFactory.createLineBorder(
									ColorScheme.DARKER_GRAY_HOVER_COLOR));
							}
						}
					});
					book.add(cell);
				}
				menu.add(book);
			}
		}
		menu.show(anchor, 0, anchor.getHeight());
	}

	/** The boost picker popup (anchored to the potion icon). */
	private void showBoostMenu(java.awt.Component anchor, String tab)
	{
		Map<String, Object> boostPicks = Model.map(pageParams, "boostPicks");
		String current = boostPicks == null ? null : Model.str(boostPicks, tab);
		javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		menu.add(pickChoice("Detect best in bank", current == null,
			() -> sendPick("set-boost-pick", tab, null)));
		menu.add(pickChoice("None (unboosted)", "NONE".equals(current),
			() -> sendPick("set-boost-pick", tab, "NONE")));
		Map<String, Object> boostLists = Model.map(assumeOptions, "boosts");
		Map<String, Object> potionIds = Model.map(assumeOptions, "boostItems");
		if (boostLists != null)
		{
			for (Map<String, Object> option : Model.list(boostLists, tab))
			{
				String label = Model.str(option, "label");
				String key = Model.str(option, "key");
				javax.swing.JMenuItem item = pickChoice(label, key != null && key.equals(current),
					() -> sendPick("set-boost-pick", tab, key));
				int itemId = potionIds == null ? -1 : (int) Model.num(potionIds, label);
				if (itemId > 0)
				{
					item.setIcon(new javax.swing.ImageIcon(itemManager.getImage(itemId)));
				}
				menu.add(item);
			}
		}
		menu.show(anchor, 0, anchor.getHeight());
	}

	private javax.swing.JMenuItem pickChoice(String label, boolean selected, Runnable onPick)
	{
		javax.swing.JCheckBoxMenuItem item = new javax.swing.JCheckBoxMenuItem(label, selected);
		item.addActionListener(e -> onPick.run());
		return item;
	}

	private void sendPick(String command, String tab, String value)
	{
		Map<String, Object> args = new java.util.HashMap<>();
		args.put("style", tab);
		args.put("value", value);
		commands.send(command, args);
	}

	/** A sprite icon from the shared cache (primed async; a menu opened
	 * before the load shows it on its next open). */
	private javax.swing.Icon cachedSprite(int spriteId)
	{
		javax.swing.ImageIcon icon = spriteIconCache.get(spriteId);
		if (icon != null)
		{
			return icon;
		}
		javax.swing.ImageIcon fresh = new javax.swing.ImageIcon(
			new java.awt.image.BufferedImage(15, 15, java.awt.image.BufferedImage.TYPE_INT_ARGB));
		spriteIconCache.put(spriteId, fresh);
		if (spriteManager != null)
		{
			spriteManager.getSpriteAsync(spriteId, 0, img ->
				javax.swing.SwingUtilities.invokeLater(() -> fresh.setImage(
					img.getScaledInstance(15, 15, java.awt.Image.SCALE_SMOOTH))));
		}
		return fresh;
	}

	/** A stat line led by its game-sprite icon (async, 14px). */
	private JLabel statIconLine(String text, String tooltip, Color color, int spriteId)
	{
		JLabel line = statLine(text, tooltip, color);
		if (spriteId > 0 && spriteManager != null)
		{
			spriteManager.getSpriteAsync(spriteId, 0, img ->
				javax.swing.SwingUtilities.invokeLater(() ->
				{
					line.setIcon(Ui.icon(img, 14));
					line.setIconTextGap(4);
					line.revalidate();
				}));
		}
		return line;
	}

	/** A stat line with a PAINTED classic icon in the fixed column. */
	private static JLabel paintedStatLine(String text, String tooltip, Color color,
		javax.swing.Icon icon)
	{
		JLabel line = statLine(text, tooltip, color);
		line.setIcon(new StatIcons.FixedWidthIcon(icon));
		line.setIconTextGap(2);
		return line;
	}

	/** A column stat line led by a 16px ITEM icon. */
	private JLabel statItemLine(int itemId, String text, String tooltip, Color color)
	{
		JLabel line = statLine(text, tooltip, color);
		net.runelite.client.util.AsyncBufferedImage img = itemManager.getImage(itemId);
		Runnable seat = () -> javax.swing.SwingUtilities.invokeLater(() ->
		{
			line.setIcon(new StatIcons.FixedWidthIcon(new javax.swing.ImageIcon(
				img.getScaledInstance(-1, 16, java.awt.Image.SCALE_SMOOTH))));
			line.setIconTextGap(2);
		});
		seat.run();
		img.onLoaded(seat);
		return line;
	}

	/** A 24px inventory cell: the item image (quantity overlay when
	 * qty > 0) scaled down so a full trip fits the row. */
	private JLabel smallItemCell(int itemId, int qty, String tooltip)
	{
		JLabel cell = new JLabel();
		cell.setPreferredSize(new java.awt.Dimension(26, 26));
		cell.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
		cell.setToolTipText(tooltip);
		net.runelite.client.util.AsyncBufferedImage img = qty > 0
			? itemManager.getImage(itemId, qty, true) : itemManager.getImage(itemId);
		Runnable seat = () -> javax.swing.SwingUtilities.invokeLater(() ->
			cell.setIcon(Ui.icon(img, 24)));
		seat.run();
		img.onLoaded(seat);
		return cell;
	}

	private static JLabel statLine(String text, String tooltip, Color color)
	{
		JLabel line = Ui.label(text, color);
		line.setToolTipText(tooltip);
		line.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
		line.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
		return line;
	}

	/** The classic bank-action look: flat, rounded, accent on select. */
	private static void styleBankButton(javax.swing.JToggleButton button)
	{
		button.setFocusable(false);
		button.setContentAreaFilled(false);
		button.setForeground(new Color(190, 190, 190));
		button.setBorder(new RoundedBorder(ColorScheme.MEDIUM_GRAY_COLOR, 3, 10));
		button.addItemListener(e ->
		{
			boolean on = button.isSelected();
			button.setForeground(on ? Color.WHITE : new Color(190, 190, 190));
			button.setBorder(new RoundedBorder(on ? ACCENT
				: ColorScheme.MEDIUM_GRAY_COLOR, 3, 10));
		});
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
		if ("magic".equals(tab) && !bis)
		{
			String pinnedSpell = Model.str(mob, "pinnedSpell");
			String shownSpell = pinnedSpell != null && !pinnedSpell.isEmpty()
				? pinnedSpell : Model.str(card, "spell");
			Map<String, Object> spellSprites = Model.map(assumeOptions, "spellSprites");
			int shownSprite = shownSpell == null || spellSprites == null ? -1
				: (int) Model.num(spellSprites, shownSpell);
			JPanel spellRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 4, 0));
			spellRow.setBackground(CARD);
			JLabel spellIcon = new JLabel();
			spellIcon.setPreferredSize(new java.awt.Dimension(22, 22));
			spellIcon.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
			if (shownSprite > 0)
			{
				spellIcon.setIcon(cachedSprite(shownSprite));
			}
			else if (spriteManager != null)
			{
				spriteManager.getSpriteAsync(net.runelite.api.SpriteID.TAB_MAGIC, 0, img ->
					javax.swing.SwingUtilities.invokeLater(() ->
						spellIcon.setIcon(Ui.icon(img, 18))));
			}
			JLabel spellName = new JLabel(shownSpell == null ? "Auto spell"
				: shownSpell + (pinnedSpell != null && !pinnedSpell.isEmpty()
					? " (pinned)" : ""));
			spellName.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
			spellName.setForeground(new Color(200, 200, 200));
			spellRow.add(spellIcon);
			spellRow.add(spellName);
			String tip = "The autocast spell - click to pin one (icons from the book)";
			spellIcon.setToolTipText(tip);
			spellName.setToolTipText(tip);
			java.awt.event.MouseAdapter openSpells = new java.awt.event.MouseAdapter()
			{
				@Override
				public void mouseClicked(java.awt.event.MouseEvent e)
				{
					// The SPELLBOOK grid (the prayer-book pattern - the list
					// form scrolled off the screen): sprite cells six wide,
					// names in tooltips, the pick on the accent plate.
					javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
					javax.swing.JCheckBoxMenuItem auto = new javax.swing.JCheckBoxMenuItem(
						"Auto spell (best castable)",
						pinnedSpell == null || pinnedSpell.isEmpty());
					auto.addActionListener(ev ->
						commands.send("set-pinned-spell", Map.of("name", "")));
					menu.add(auto);
					menu.addSeparator();
					JPanel book = Ui.darker(new GridLayout(0, 6, 2, 2));
					book.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
					for (String option : spellOptions)
					{
						boolean picked = option.equals(pinnedSpell);
						JLabel cell = new JLabel();
						cell.setPreferredSize(new java.awt.Dimension(26, 26));
						cell.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
						cell.setOpaque(true);
						cell.setBackground(picked ? new Color(0x2e, 0x40, 0x2e)
							: ColorScheme.DARKER_GRAY_COLOR);
						cell.setBorder(BorderFactory.createLineBorder(picked
							? ACCENT : ColorScheme.DARKER_GRAY_HOVER_COLOR));
						int sprite = spellSprites == null ? -1
							: (int) Model.num(spellSprites, option);
						if (sprite > 0)
						{
							cell.setIcon(cachedSprite(sprite));
						}
						else
						{
							cell.setText(option.substring(0, 1));
							cell.setForeground(new Color(190, 190, 190));
						}
						cell.setToolTipText(option + (picked ? " (pinned)" : ""));
						cell.setCursor(java.awt.Cursor.getPredefinedCursor(
							java.awt.Cursor.HAND_CURSOR));
						cell.addMouseListener(new java.awt.event.MouseAdapter()
						{
							@Override
							public void mouseClicked(java.awt.event.MouseEvent ev)
							{
								menu.setVisible(false);
								commands.send("set-pinned-spell", Map.of("name", option));
							}

							@Override
							public void mouseEntered(java.awt.event.MouseEvent ev)
							{
								cell.setBorder(BorderFactory.createLineBorder(ACCENT));
							}

							@Override
							public void mouseExited(java.awt.event.MouseEvent ev)
							{
								if (!picked)
								{
									cell.setBorder(BorderFactory.createLineBorder(
										ColorScheme.DARKER_GRAY_HOVER_COLOR));
								}
							}
						});
						book.add(cell);
					}
					menu.add(book);
					menu.show((java.awt.Component) e.getSource(), 0,
						((java.awt.Component) e.getSource()).getHeight());
				}
			};
			spellIcon.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			spellName.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			spellIcon.addMouseListener(openSpells);
			spellName.addMouseListener(openSpells);
			panel.add(left(spellRow));
		}
		// The classic card centre: the cross LEFT, the stat column RIGHT.
		JPanel center = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 2));
		center.setBackground(CARD);
		Map<String, Object> specForCell = Model.map(card, "spec");
		Map<String, Object> specWeaponForCell = specForCell == null
			? null : Model.map(specForCell, "weapon");
		cellSpecDps = specForCell == null ? 0 : Model.num(specForCell, "dpsAdded");
		cellSpecWeaponId = specWeaponForCell == null ? 0 : Model.id(specWeaponForCell, "id");
		cellSpecPinned = cellSpecWeaponId != 0
			&& Model.id(mob, "pinnedSpec") == cellSpecWeaponId;
		center.add(gearGrid(card, bis));
		JPanel statColumn = new JPanel();
		statColumn.setLayout(new BoxLayout(statColumn, BoxLayout.Y_AXIS));
		statColumn.setBackground(CARD);
		Color statText = new Color(200, 200, 200);
		Map<String, Object> incoming = Model.map(card, "incoming");
		statColumn.add(paintedStatLine("" + (int) Model.num(card, "maxHit"),
			"Max hit " + (int) Model.num(card, "maxHit"), statText,
			new StatIcons.HitsplatIcon(12)));
		statColumn.add(paintedStatLine(Math.round(Model.num(card, "accuracy") * 100) + "%",
			"Hit chance", statText, new StatIcons.CrosshairIcon(13)));
		if (incoming != null)
		{
			// Classic honesty rule: an unmodeled monster's DTPS reads "?" -
			// silence must read as unknown, never safe.
			boolean unmodeled = !Model.flag(incoming, "fullyModeled")
				&& Model.num(incoming, "dps") <= 0 && Model.num(incoming, "unprayedDps") <= 0;
			// The protect prayer's own sprite IS the pray call (classic).
			int protectSprite = unmodeled ? -1 : (int) Model.num(incoming, "protectSprite");
			statColumn.add(statIconLine(
				unmodeled ? "DTPS: ?" : String.format("~%.1f", Model.num(incoming, "dps")),
				unmodeled ? "This monster's attacks are beyond the stat-sheet model"
					: "Pray " + Model.str(incoming, "protectPrayer")
						+ String.format(" - takes ~%.1f dps (%.2f unprayed)",
							Model.num(incoming, "dps"), Model.num(incoming, "unprayedDps")),
				statText, protectSprite));
		}
		Map<String, Object> statsNode = Model.map(card, "stats");
		if (statsNode != null)
		{
			statColumn.add(statIconLine("+" + Model.id(statsNode, "prayer"),
				"Prayer bonus", statText, net.runelite.api.SpriteID.SKILL_PRAYER));
		}
		String atkType = Model.str(card, "attackType");
		if (atkType != null)
		{
			// "crush (controlled)" -> "Crush", stance in the hover.
			int paren = atkType.indexOf(" (");
			String styleWord = paren > 0 ? atkType.substring(0, paren) : atkType;
			String stance = paren > 0
				? atkType.substring(paren + 2).replace(")", "") : null;
			styleWord = Character.toUpperCase(styleWord.charAt(0)) + styleWord.substring(1);
			statColumn.add(statLine(styleWord,
				stance == null ? "Attack style the numbers use"
					: "Attack style the numbers use - " + stance + " stance",
				statText));
		}
		Map<String, Object> internalAmmo = Model.map(card, "internalAmmo");
		if (internalAmmo != null)
		{
			JLabel dartLine = statItemLine(Model.id(internalAmmo, "id"),
				Model.str(internalAmmo, "name"),
				"Loaded in the blowpipe - right-click to pin or exclude dart tiers",
				statText);
			java.util.Set<Integer> excludedIds = new java.util.HashSet<>();
			Map<String, Object> pageCounts = Model.map(pageWide, "counts");
			for (Map<String, Object> ex : Model.list(pageCounts, "excludedItems"))
			{
				excludedIds.add(Model.id(ex, "id"));
			}
			int loadedId = Model.id(internalAmmo, "id");
			javax.swing.JPopupMenu dartMenu = new javax.swing.JPopupMenu();
			for (Map<String, Object> tier : dartTiers)
			{
				int tierId = Model.id(tier, "id");
				String tierName = Model.str(tier, "name");
				boolean excluded = excludedIds.contains(tierId);
				String label = (tierId == loadedId ? "> " : "  ")
					+ (excluded ? "Allow " : "Exclude ") + tierName;
				Ui.item(dartMenu, label, () ->
					commands.send("toggle-exclusion", Map.of("itemId", tierId)));
			}
			dartLine.setComponentPopupMenu(dartMenu);
			statColumn.add(dartLine);
		}
		Map<String, Object> quiverAmmoNode = Model.map(card, "quiverAmmo");
		if (quiverAmmoNode != null)
		{
			statColumn.add(statItemLine(Model.id(quiverAmmoNode, "id"),
				Model.str(quiverAmmoNode, "name"),
				"Carried in the quiver", statText));
		}
		Map<String, Object> riskNode = Model.map(card, "risk");
		if (riskNode != null)
		{
			JLabel riskLine = statLine("Risk " + Gp.formatShort(
				(int) Math.min(Integer.MAX_VALUE, Model.num(riskNode, "riskGp"))),
				"", new Color(220, 120, 120));
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
			statColumn.add(riskLine);
		}
		int upgradeCost = (int) Model.num(card, "purchaseCost");
		if (upgradeCost > 0)
		{
			JLabel costLine = new JLabel(Gp.formatShort(upgradeCost));
			// The coin stack at COLUMN scale: the stack-tier sprite, but
			// scaled to the stat icons' 16px and seated in the same
			// fixed-width column so every value shares one left edge
			// (design note 2026-08-15: the raw 32px item image dwarfed
			// the painted icons).
			net.runelite.client.util.AsyncBufferedImage coinImg =
				itemManager.getImage(995, 10_000, false);
			Runnable seatCoin = () -> javax.swing.SwingUtilities.invokeLater(() ->
			{
				javax.swing.ImageIcon scaled = new javax.swing.ImageIcon(
					coinImg.getScaledInstance(-1, 16, java.awt.Image.SCALE_SMOOTH));
				costLine.setIcon(new StatIcons.FixedWidthIcon(scaled));
				costLine.revalidate();
			});
			seatCoin.run();
			coinImg.onLoaded(seatCoin);
			costLine.setIconTextGap(2);
			costLine.setHorizontalAlignment(javax.swing.SwingConstants.LEADING);
			costLine.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
			costLine.setFont(net.runelite.client.ui.FontManager.getRunescapeSmallFont());
			costLine.setForeground(statText);
			costLine.setToolTipText("Upgrade cost of unowned pieces in this set");
			costLine.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
			statColumn.add(costLine);
		}
		Map<String, Object> stats = Model.map(card, "stats");
		if (stats != null)
		{
			Map<String, Object> off = Model.map(stats, "offensive");
			Map<String, Object> def = Model.map(stats, "defensive");
			// One STATS icon, everything in the hover (field report
			// 2026-08-15: the compact lines were cut off and unreadable)
			// - a proper equipment-stats table, offensive and defensive
			// for every stat.
			String fullBonuses = String.format(
				"<html><table cellpadding='2'>"
					+ "<tr><th></th><th>Stab</th><th>Slash</th><th>Crush</th>"
					+ "<th>Magic</th><th>Ranged</th></tr>"
					+ "<tr><td><b>Attack</b></td><td>%+d</td><td>%+d</td><td>%+d</td>"
					+ "<td>%+d</td><td>%+d</td></tr>"
					+ "<tr><td><b>Defence</b></td><td>%+d</td><td>%+d</td><td>%+d</td>"
					+ "<td>%+d</td><td>%+d</td></tr>"
					+ "</table>"
					+ "Strength %+d &nbsp; Ranged str %+d &nbsp; Magic dmg %+d%%"
					+ " &nbsp; Prayer %+d</html>",
				Model.id(off, "stab"), Model.id(off, "slash"), Model.id(off, "crush"),
				Model.id(off, "magic"), Model.id(off, "ranged"),
				Model.id(def, "stab"), Model.id(def, "slash"), Model.id(def, "crush"),
				Model.id(def, "magic"), Model.id(def, "ranged"),
				Model.id(stats, "strength"), Model.id(stats, "rangedStrength"),
				Model.id(stats, "magicDamage"), Model.id(stats, "prayer"));
			statColumn.add(statIconLine("Stats", fullBonuses, statText,
				net.runelite.api.SpriteID.TAB_COMBAT));
		}
		Object counted = card.get("counted");
		if (counted instanceof List && !((List<?>) counted).isEmpty())
		{
			// Situational bonuses live IN the column (field report
			// 2026-08-14: 'slayer helm shown outside the 2x5') - the
			// count on the line, every bonus named in the tooltip.
			List<?> bonuses = (List<?>) counted;
			StringBuilder tip = new StringBuilder("<html>Counted for this set:");
			for (Object bonus : bonuses)
			{
				tip.append("<br> ").append(bonus);
			}
			statColumn.add(statLine("+" + bonuses.size() + " bonus"
				+ (bonuses.size() == 1 ? "" : "es"),
				tip.append("</html>").toString(), ACCENT));
		}
		center.add(statColumn);
		panel.add(left(center));
		return panel;
	}

	/** The worn-equipment silhouette (5 rows x 3): blank corners, the
	 * spec weapon in the empty cell left of the legs, quiver ammo in
	 * the corner right of the legs - the classic panel's iconic grid. */
	private static final String[] CROSS = {
		null, "head", null,
		"cape", "neck", "ammo",
		"weapon", "body", "shield",
		"#spec", "legs", "#quiver",
		"hands", "feet", "ring",
	};

	private JPanel gearGrid(Map<String, Object> card, boolean bis)
	{
		JPanel grid = new JPanel(new GridLayout(5, 3, 2, 2));
		grid.setBackground(CARD);
		Map<String, Object> gear = Model.map(card, "gear");
		Map<String, Object> specNode = Model.map(card, "spec");
		Map<String, Object> quiver = Model.map(card, "quiverAmmo");
		for (String slot : CROSS)
		{
			Map<String, Object> item =
				"#spec".equals(slot) ? (specNode == null ? null : Model.map(specNode, "weapon"))
				: "#quiver".equals(slot) ? quiver
				: slot == null || gear == null ? null : Model.map(gear, slot);
			if (item == null)
			{
				// Empty slots stay QUIET (field ask 2026-08-15: no hard
				// blank squares) - a faint centred dot marks the position
				// without drawing a box. Painted, not a glyph (Tahoe tofu).
				JLabel blank = new JLabel(new javax.swing.Icon()
				{
					public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y)
					{
						g.setColor(new Color(70, 70, 70));
						g.fillOval(x, y, 3, 3);
					}

					public int getIconWidth()
					{
						return 3;
					}

					public int getIconHeight()
					{
						return 3;
					}
				});
				blank.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
				blank.setPreferredSize(new java.awt.Dimension(36, 36));
				grid.add(blank);
			}
			else
			{
				String slotName = "#spec".equals(slot) ? "spec"
					: "#quiver".equals(slot) ? "quiver" : slot;
				grid.add(itemCell(item, slotName, bis));
			}
		}
		JPanel holder = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 2));
		holder.setBackground(CARD);
		holder.add(grid);
		return holder;
	}

	/** The classic storage palette - only FETCH-TRIP sources have an
	 * entry, so at-hand gear can never draw a dot. */
	private static final Map<String, Color> SOURCE_COLORS = Map.of(
		"looting bag", new Color(180, 130, 80),
		"POH costume room", new Color(190, 130, 230),
		"STASH", new Color(230, 120, 120),
		"cargo hold", new Color(100, 200, 190),
		"stored elsewhere", new Color(180, 210, 110),
		"DWMS", new Color(160, 160, 210));

	private static Color sourceColor(String source)
	{
		if (source == null)
		{
			return null;
		}
		for (Map.Entry<String, Color> entry : SOURCE_COLORS.entrySet())
		{
			if (source.startsWith(entry.getKey()))
			{
				return entry.getValue();
			}
		}
		return null;
	}

	/** The classic palette (relocated with the border language). */
	private static final Color CELL_BG = new Color(50, 50, 50);
	private static final Color BORDER_BIS = new Color(168, 148, 88);
	private static final Color BORDER_SPEC = new Color(120, 190, 240);
	private static final Color BORDER_PLAIN = new Color(70, 70, 70);
	private static final Color BORDER_UNOWNED = new Color(100, 145, 100);

	private JLabel itemCell(Map<String, Object> item, String slot, boolean bis)
	{
		JLabel cell = new JLabel();
		String name = Model.str(item, "name");
		int id = Model.id(item, "id");
		// Border language (classic, field note 2026-08-05: every colour
		// has to EARN its cell): gold = owned and best-available, green =
		// assumed (simmed / unowned on Yours), blue = the spec cell,
		// grey otherwise.
		Color border = "spec".equals(slot) ? BORDER_SPEC
			: Model.flag(item, "assumed") ? BORDER_UNOWNED
			: Model.flag(item, "bisMatch") ? BORDER_BIS
			: BORDER_PLAIN;
		int itemPrice = (int) Model.num(item, "price");
		String priceNote = Model.flag(item, "owned") || itemPrice <= 0
			? "" : " - " + Gp.formatShort(itemPrice) + " gp";
		String flagNote = ("spec".equals(slot)
			? " - spec weapon (adds ~" + String.format("%.2f", cellSpecDps)
				+ " dps)" + (cellSpecPinned ? " - pinned" : "")
			: Model.flag(item, "assumed") ? " - assumed (not owned)"
			: Model.flag(item, "bisMatch") ? " - best available" : "") + priceNote;
		cell.setToolTipText(name + " (" + slot + ")" + flagNote);
		if ("spec".equals(slot) && !bis && cellSpecWeaponId != 0)
		{
			// The pin lives ON the cell now (the spec text line retired).
			javax.swing.JPopupMenu specMenu = new javax.swing.JPopupMenu();
			boolean pinnedNow = cellSpecPinned;
			int weaponId = cellSpecWeaponId;
			Ui.item(specMenu, cellSpecPinned ? "Unpin spec" : "Pin as spec for this mob",
				() -> commands.send("set-pinned-spec",
					Map.of("itemId", pinnedNow ? 0 : weaponId)));
			cell.setComponentPopupMenu(specMenu);
		}
		cell.setPreferredSize(new java.awt.Dimension(36, 36));
		cell.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
		cell.setOpaque(true);
		cell.setBackground(CELL_BG);
		cell.setBorder(BorderFactory.createLineBorder(border));
		itemManager.getImage(id).addTo(cell);
		Color dot = sourceColor(Model.str(item, "source"));
		String fate = Model.str(item, "fate");
		if (dot != null || fate != null)
		{
			String where = Model.str(item, "source");
			cell.setToolTipText("<html>" + name + " (" + slot + ")" + flagNote
				+ (where == null ? "" : "<br>stored in " + where)
				+ ("kept".equals(fate) ? "<br>protected on death"
					: "lost".equals(fate)
						? "<br>lost on death (" + Gp.format((int) Math.min(Integer.MAX_VALUE,
							Model.num(item, "fateGp"))) + " gp)" : "")
				+ "</html>");
			cell.setUI(new javax.swing.plaf.basic.BasicLabelUI()
			{
				@Override
				public void paint(java.awt.Graphics g, javax.swing.JComponent c)
				{
					super.paint(g, c);
					java.awt.Graphics2D g2 = (java.awt.Graphics2D) g.create();
					g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
						java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
					if (dot != null)
					{
						// The classic source dot: bottom-right corner.
						g2.setColor(dot);
						g2.fillOval(c.getWidth() - 8, c.getHeight() - 8, 5, 5);
					}
					if (fate != null)
					{
						// Fate mark, top-left: green = kept on death,
						// red = dropped to the killer.
						g2.setColor("kept".equals(fate)
							? new Color(120, 200, 120) : new Color(220, 110, 110));
						g2.fillRect(3, 3, 4, 4);
					}
					g2.dispose();
				}
			});
		}
		javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		Ui.item(menu, "Exclude " + name,
			() -> commands.send("toggle-exclusion", Map.of("itemId", id)));
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
			Ui.item(menu, "Pin another item (search)...", () -> picker.search("Pin in " + slot,
				(pickedId, pickedName) -> commands.send("pin", Map.of("slot", slot, "itemId", pickedId))));
			Ui.item(menu, "Unpin " + slot, () -> commands.send("unpin", Map.of("slot", slot)));
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
