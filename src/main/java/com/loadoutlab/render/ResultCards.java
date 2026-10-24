package com.loadoutlab.render;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.ColorScheme;
import com.google.gson.JsonObject;
import com.loadoutlab.data.JsonResources;
import com.loadoutlab.data.Names;
import com.loadoutlab.data.NavalCombat;
import com.loadoutlab.ui.MonsterIcons;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.*;
import net.runelite.api.SpriteID;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * The first rich renderer over the render-model: per mob, per style,
 * the Yours and Best-in-game sides as compact cards - dps headline,
 * equipment icon grid (icons resolved through the Companion's own
 * ItemManager; the model only carries ids), spec and incoming lines.
 * This view grows toward the ported panel card-by-card; the model
 * gains fields as this code asks for them.
 */
@lombok.RequiredArgsConstructor
public class ResultCards
{
	private static final Color CARD = ColorScheme.DARKER_GRAY_COLOR;
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final CommandSink commands;
	private final ItemPicker picker;
	private Map<String, Object> assumeOptions;
	private Map<String, Object> pageWide;
	private Map<String, Object> pageParams;
	/** Small sailing skill icon for ship-eligible rows/markers ("naval" is
	 * not player vernacular - the icon speaks instead). */
	@lombok.Setter
	private Supplier<BufferedImage> sailingIcon;

	/** The trip breakdown's show/hide: a per-profile setting the plugin
	 * owns; the card reads it and a click on the ledger flips it. */
	private BooleanSupplier breakdownShown = () -> true;
	private Consumer<Boolean> breakdownToggle = shown ->
	{
	};

	public void setBreakdown(BooleanSupplier shown, Consumer<Boolean> toggle)
	{
		this.breakdownShown = shown;
		this.breakdownToggle = toggle;
	}

	@lombok.Setter
	private BooleanSupplier degradableChip = () -> true;

	/** The trio's fourth sigil (Mike, Discord 2026-09-07; Andrew 2026-09-08:
	 * the text pill clipped): *N = N degrading items in the shown set,
	 * amber when that gear is skipped for this mob. */
	private JButton degradesButton(Map<String, Object> mob, Map<String, Object> card)
	{
		int n = 0;
		Map<String, Object> gear = Model.map(card, "gear");
		for (Object slot : gear == null ? List.of() : gear.values())
		{
			if (slot instanceof Map && Model.flag((Map<String, Object>) slot, "degrades"))
			{
				n++;
			}
		}
		boolean skip = Model.flag(mob, "skipDegradable");
		if (!degradableChip.getAsBoolean() || (n == 0 && !skip))
		{
			return null;
		}
		JButton button = sigilButton("*" + n, skip, skip ? new Color(0xE6B45A) : new Color(0x968C78),
			new Color(0xBE9646), skip ? Ui.text("degrade.skip")
				: n + " degrade or burn charges - click to skip them here");
		button.addActionListener(e -> commands.send("toggle-degradable"));
		return button;
	}

	/** A trio sigil button: lit with its ring when it holds anything, muted
	 * and ringless otherwise (the degrade sigil wears the same cut). */
	private static JButton sigilButton(String text, boolean lit, Color fg, Color ring, String tip)
	{
		JButton button = new JButton(text);
		button.setForeground(fg);
		button.setContentAreaFilled(false);
		button.setBorder(lit ? new RoundedBorder(ring, 2, 14) : BorderFactory.createEmptyBorder(3, 15, 3, 15));
		button.setToolTipText(tip);
		button.setFocusable(false);
		return button;
	}

	/** A cannon-strip icon button: the item's image, a red ring when the
	 * operate gate blocks it, the dark hover ring otherwise. */
	private JButton iconButton(int itemId, boolean blocked)
	{
		JButton button = new JButton(new ImageIcon(itemManager.getImage(itemId)));
		button.setContentAreaFilled(false);
		button.setFocusable(false);
		button.setBorder(new RoundedBorder(blocked
			? new Color(0xC86464) : ColorScheme.DARKER_GRAY_HOVER_COLOR, 2, 8));
		return button;
	}

	Icon sailingIconSmall()
	{
		Supplier<BufferedImage> supplier = sailingIcon;
		BufferedImage img = supplier == null ? null : supplier.get();
		return img == null ? null : Ui.icon(img, 14);
	}

	@lombok.Setter(lombok.AccessLevel.PACKAGE)
	private JButton addMob;
	@lombok.Setter(lombok.AccessLevel.PACKAGE)
	private JButton reload;

	/** The tab the page is effectively showing: the explicit selection,
	 * or the first style the first mob answers. */
	static String effectiveTab(Map<String, Object> page)
	{
		String selected = Model.str(firstParams(page), "selectedTab");
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
				for (String style : styles.keySet())
				{
					Map<String, Object> node = Model.map(styles, style);
					if (node == null)
					{
						continue;
					}
					boolean kitBacked = Model.flag(Model.map(node, "yours"), "kitBacked");
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
		assumeOptions = Model.map(page, "assumeOptions");
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
		boolean bis = Model.flag(pageParams, "viewingBis");
		int lens = Model.id(pageParams, "lensIndex");
		for (Map<String, Object> entry : Model.list(page, "entries"))
		{
			List<Map<String, Object>> mobs = Model.list(entry, "mobs");
			if (mobs.isEmpty())
			{
				continue;
			}
			double rosterThralls = Model.num(Model.map(entry, "thralls"), "dps");
			JPanel box = Ui.column(ColorScheme.DARKER_GRAY_COLOR);
			int shownLens = Math.min(Math.max(lens, 0), mobs.size() - 1);
			for (int i = 0; i < mobs.size(); i++)
			{
				box.add(lensRow(mobs.get(i), tab, bis, i, mobs.size(), i == shownLens, rosterThralls));
				if (i < mobs.size() - 1)
				{
					box.add(Box.createVerticalStrut(2));
				}
			}
			if (addMob != null)
			{
				JPanel addRow = Ui.clear(new FlowLayout(FlowLayout.LEFT, 0, 2));
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
		boolean bis = Model.flag(pageParams, "viewingBis");
		JPanel column = Ui.column(ColorScheme.DARK_GRAY_COLOR);
		int lens = Model.id(pageParams, "lensIndex");
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
			Map<String, Object> shown = mobs.get(shownLens);
			// Ship options follow the LENSED mob (REQ-SC-7): a land lens in
			// a mixed roster renders none of this; lensing back to the sea
			// mob brings it back with the params intact.
			Map<String, Object> ship = Model.map(entry, "ship");
			boolean naval = Model.flag(shown, "naval") && pageParams != null;
			// The card never hides (Andrew, v2): cannons live INSIDE it -
			// the 1x3 strip above the gear grid, the breakdown below.
			// Cannon dps folds into the shown numbers only while nobody
			// mans one; a manned trip's gear is not attacking.
			boolean manned = "cannon".equals(Model.str(ship, "station"));
			double shipDps = naval && !manned ? Model.num(ship, "dps") : 0;
			column.add(mobCard(shown, tab, bis, thrallsDps, shipDps, naval ? ship : null, naval));
			column.add(Box.createVerticalStrut(8));
		}
		return column;
	}

	/** One switcher row, aligned in columns: the mob name (shortened to
	 * fit) with its level, then a FIXED-WIDTH block - style icon, dps,
	 * remove - so those line up down the whole list. */
	private JPanel lensRow(Map<String, Object> mob, String tab, boolean bis, int index,
		int rosterSize, boolean selected, double thrallsDps)
	{
		Map<String, Object> styles = Model.map(mob, "styles");
		// The row reports ITS OWN recommendation (field report
		// 2026-08-14: rows showed the viewed tab's dps beside the
		// best-style icon) - the number belongs to the icon's style.
		String bestStyle = null;
		Map<String, Object> bestSide = null;
		double bestDps = 0;
		if (styles != null)
		{
			for (String style : styles.keySet())
			{
				Map<String, Object> styleNode = Model.map(styles, style);
				Map<String, Object> side = Model.map(styleNode, bis ? "bis" : "yours");
				if (side == null || !Model.flag(side, "kitBacked"))
				{
					continue;
				}
				double value = Model.num(styleNode, bis ? "bisTabDps" : "tabDps");
				if (value > bestDps)
				{
					bestDps = value;
					bestStyle = style;
					bestSide = side;
				}
			}
		}
		Map<String, Object> shown = Model.map(Model.map(styles, tab), bis ? "bis" : "yours");
		boolean tabOnly = shown != null && !Model.flag(shown, "kitBacked");
		// Rows and tab buttons COUNT THE SAME THINGS (field report
		// 2026-08-15): set + spec (in the tab facts) + thralls, and a
		// sea row's cannons (Andrew 2026-09-03).
		double ride = Model.thrallsFor(bestStyle != null ? bestSide : shown, thrallsDps)
			+ Model.num(mob, "shipDps");
		String dps = bestStyle != null
			? String.format("%.2f", bestDps + (bestDps > 0 ? ride : 0))
			: shown == null ? "-"
			: String.format("%.2f%s",
				Model.num(shown, "dps") + ride, tabOnly ? "*" : "");

		String rowStyle = bestStyle;
		JPanel row = new JPanel(new BorderLayout(4, 0));
		// Selection is an EDGE, not a box: the active mob wears a 3px
		// accent bar and a faint tint; the rest stay transparent.
		boolean sea = Model.flag(mob, "naval");
		Color edge = sea ? SEA : ACCENT;
		row.setOpaque(selected);
		if (selected)
		{
			// A ship mob's selection wears the sea, not the accent green
			// (REQ-SC-16).
			row.setBackground(sea ? new Color(0x28323C) : new Color(0x2E382E));
		}
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, selected ? 3 : 0, 0, 0, edge),
			BorderFactory.createEmptyBorder(4, selected ? 7 : 10, 4, 4)));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

		String name = Model.str(mob, "label");
		// The row carries NO numbers (field ask 2026-08-21: "too much
		// clutter") - the level-versioned label suffix strips, and the
		// card header below owns level and hp. The tooltip keeps the
		// full label for disambiguation.
		String cleanName = name == null ? ""
			: name.replaceFirst(" - lvl \\d+$", "");
		JLabel label = Ui.small(cleanName, selected ? edge : TEXT);
		if (monsterIcons != null && iconsEnabled.getAsBoolean())
		{
			// The mob's wiki render rides the row; text-only until it
			// loads (or when the wiki has no picture for it).
			String mobName = Model.str(mob, "name");
			String mobVersion = Model.str(mob, "version");
			ImageIcon mobIcon = monsterIcons.get(mobName, mobVersion, 20, () ->
			{
				ImageIcon ready = monsterIcons.get(mobName, mobVersion, 20, null);
				if (ready != null)
				{
					label.setIcon(ready);
					label.setIconTextGap(6);
					label.revalidate();
				}
			});
			if (mobIcon != null)
			{
				label.setIcon(mobIcon);
				label.setIconTextGap(6);
			}
		}
		// The name yields first when the panel narrows - the columns to
		// its right keep their width, so they stay aligned.
		label.setMinimumSize(new Dimension(20, 18));
		label.setToolTipText("<html>" + name
			+ (bestStyle != null
				? "<br>" + dps + " dps with " + bestStyle + " (best in the trip kit)"
				: tabOnly ? Ui.text("roster.tabonly") : "")
			+ Ui.text("roster.click"));
		Runnable pick = () ->
		{
			label.setForeground(ACCENT);
			// Switching mobs also switches to THAT mob's suggested
			// style (field ask 2026-08-15) - the card below always
			// opens on the row's own recommendation.
			commands.set("lensIndex", index);
			if (rowStyle != null && !rowStyle.equals(tab))
			{
				commands.set("selectedTab", rowStyle);
			}
		};
		Ui.onClick(label, pick);
		JLabel sailTag = null;
		if (sea)
		{
			Icon sail = sailingIconSmall();
			if (sail != null)
			{
				sailTag = new JLabel(sail);
				sailTag.setToolTipText("Ship combat: fought from your boat");
			}
		}
		if (sailTag != null)
		{
			JPanel nameSeat = Ui.clear(new BorderLayout(4, 0));
			nameSeat.add(label, BorderLayout.CENTER);
			nameSeat.add(sailTag, BorderLayout.EAST);
			row.add(nameSeat, BorderLayout.CENTER);
		}
		else
		{
			row.add(label, BorderLayout.CENTER);
		}

		// The aligned right block: [icon dps] x - the icon rides right,
		// snug against its number.
		JPanel columns = Ui.clear(new BorderLayout(2, 0));

		JLabel icon = square(18);
		if (bestStyle != null && spriteManager != null)
		{
			seatSprite(icon, Model.id(Model.map(styles, bestStyle), "styleSprite"), 0);
			icon.setToolTipText("Best style: " + bestStyle);
		}
		JPanel iconDps = Ui.clear(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		iconDps.add(icon);

		JLabel dpsLabel = Ui.small(dps, selected ? Color.WHITE : new Color(0xAAAAAA));
		iconDps.add(dpsLabel);
		columns.add(iconDps, BorderLayout.CENTER);

		JLabel remove = Ui.xLabel("Remove " + name + " from this result",
			() -> commands.send("remove-mob", Map.of("index", index)));
		remove.setHorizontalAlignment(SwingConstants.CENTER);
		remove.setPreferredSize(new Dimension(14, 18));
		// Right-click on the row or its x: Move up / Move down (Andrew
		// 2026-09-02 - a menu, not drag handles: no Swing drag layer).
		MouseAdapter reorder = new MouseAdapter()
		{
			public void mousePressed(MouseEvent e)
			{
				maybeShow(e);
			}

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
				if (index > 0)
				{
					Ui.item(menu, "Move up", () -> commands.send("move-mob",
						Map.of("index", index, "delta", -1)));
				}
				if (index < rosterSize - 1)
				{
					Ui.item(menu, "Move down", () -> commands.send("move-mob",
						Map.of("index", index, "delta", 1)));
				}
				if (menu.getComponentCount() > 0)
				{
					menu.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		};
		row.addMouseListener(reorder);
		remove.addMouseListener(reorder);
		columns.add(remove, BorderLayout.EAST);
		row.add(columns, BorderLayout.EAST);
		return row;
	}

	/** The brand accent (the mascot-era green lives on as a plain constant). */
	static final Color ACCENT = new Color(0x8CC88C);
	/** The card's greys, lightest to dimmest, and the sea-blue of ship rows. */
	static final Color TEXT = new Color(0xC8C8C8);
	static final Color LIGHT = new Color(0xBEBEBE);
	static final Color MID = new Color(0xA0A0A0);
	static final Color DIM = new Color(0x8C8C8C);
	static final Color SEA = new Color(0x78AFD7);
	/** The trio's sigil colours - {active, muted, active border} - shared
	 * with the page-wide count pills: sims green, exclusions red, filters grey. */
	static final Color[] SIM_PALETTE = {new Color(0x82C882), new Color(0x6E8C6E), new Color(0x5FA05F)};
	static final Color[] EXCLUDE_PALETTE = {new Color(0xDC7878), new Color(0x8C6E6E), new Color(0xAA5A5A)};
	static final Color[] FILTER_PALETTE = {new Color(0xBEBEBE), new Color(0x828282), new Color(0x969696)};

	/** Async wiki thumbnails for the roster rows (null = text-only). */
	private MonsterIcons monsterIcons;
	private BooleanSupplier iconsEnabled = () -> false;

	public void setMonsterIcons(MonsterIcons icons,
		BooleanSupplier enabled)
	{
		this.monsterIcons = icons;
		this.iconsEnabled = enabled;
	}

	private boolean rosterView;
	private static final Color PIN_BADGE = new Color(0xD2B464);

	/** Whether the mob's pins (slot + id) hold this item in this slot. */
	private static boolean pinnedOn(List<Map<String, Object>> pins, String slot, int id)
	{
		for (Map<String, Object> pin : pins)
		{
			if (slot.equals(Model.str(pin, "slot")) && Model.id(pin, "id") == id)
			{
				return true;
			}
		}
		return false;
	}

	/** Whether a list of numbers (the mob's required gear) names the id. */
	private static boolean hasId(List<Object> ids, int id)
	{
		for (Object o : ids)
		{
			if (o instanceof Number && ((Number) o).intValue() == id)
			{
				return true;
			}
		}
		return false;
	}

	/** thrallsDps = the page's thrall dps, shipDps = the lensed ship's: each
	 * tab folds its OWN card's thralls (a stood-down card folds none) plus
	 * the cannons. */
	private JPanel mobCard(Map<String, Object> mob, String tab, boolean bis, double thrallsDps,
		double shipDps, Map<String, Object> ship, boolean naval)
	{
		JPanel card = Ui.column(CARD);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(
				bis ? ACCENT : ColorScheme.DARKER_GRAY_HOVER_COLOR, 1, true),
			BorderFactory.createEmptyBorder(8, 8, 8, 8)));
		JLabel title = new JLabel(Model.str(mob, "label"));
		title.setFont(title.getFont().deriveFont(Font.BOLD));
		// Naval marker (REQ-SC-6): the output says this fight happens from
		// your boat. Rides the header's EAST seat beside hp, same muted
		// small-font treatment, sea-blue.
		JLabel navalTag = null;
		if (Model.flag(mob, "naval"))
		{
			Icon sail = sailingIconSmall();
			navalTag = sail != null ? new JLabel(sail)
				: Ui.label("sea", SEA);
			navalTag.setToolTipText("Ship combat: fought from your boat");
		}
		int mobHp = Model.id(mob, "hp");
		if (mobHp > 0 || navalTag != null)
		{
			// Title CENTER (yields when narrow), hp EAST (survives) -
			// WEST/CENTER clipped the title and pushed hp out entirely
			// (field screenshot 2026-08-21: 'Duke Sucellus (Awa... lvl 1').
			JPanel titleRow = Ui.darker(new BorderLayout(8, 0));
			title.setMinimumSize(new Dimension(20, 18));
			titleRow.add(title, BorderLayout.CENTER);
			JPanel hpSeat = Ui.clear(new FlowLayout(FlowLayout.LEFT, 4, 2));
			if (navalTag != null)
			{
				hpSeat.add(navalTag);
			}
			if (mobHp > 0)
			{
				// hp rides the header, muted (field ask 2026-08-21).
				hpSeat.add(Ui.small(mobHp + "hp", DIM));
			}
			titleRow.add(hpSeat, BorderLayout.EAST);
			card.add(titleRow);
		}
		else
		{
			card.add(left(title));
		}
		Map<String, Object> styles = Model.map(mob, "styles");
		Map<String, Object> node = Model.map(styles, tab);
		if (node == null)
		{
			card.add(left(new JLabel("No " + tab + " set for this mob")));
			return card;
		}
		// The side this card shows - every row below reads it.
		Map<String, Object> shown = Model.map(node, bis ? "bis" : "yours");
		card.add(Box.createVerticalStrut(6));
		JPanel headerRow = Ui.darker(new FlowLayout(FlowLayout.CENTER, 4, 0));
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
			int prayerSprite = Model.id(assume, "prayerSprite");
			JLabel prayerIcon = new JLabel();
			prayerIcon.setPreferredSize(new Dimension(20, 20));
			prayerIcon.setToolTipText("Assumes: " + text + (bis ? "" : " - click to change the prayer"));
			// No prayer assumed -> the prayer book with a red X, so the
			// sentinel reads as "none" not "prayer on" (field ask
			// 2026-09-01). A real prayer shows its own sprite plainly.
			String prayerImage = Model.str(assume, "prayerImage");
			boolean noPrayer = prayerSprite <= 0 && prayerImage == null;
			int shownPrayerSprite = noPrayer
				? SpriteID.TAB_PRAYER : prayerSprite;
			if (prayerImage != null && prayerSprite <= 0)
			{
				prayerIcon.setIcon(Ui.icon(ImageUtil.loadImageResource(ResultCards.class, prayerImage), 18));
			}
			else
			{
				onSprite(shownPrayerSprite, img -> prayerIcon.setIcon(noPrayer
					? Ui.crossedOut(img, 18) : Ui.icon(img, 18)));
			}
			if (!bis)
			{
				Ui.onClick(prayerIcon, () -> Ui.show(prayerMenu(tab), prayerIcon));
			}
			headerRow.add(prayerIcon);
			int boostItem = Model.id(assume, "boostItem");
			JLabel boostIconCell = new JLabel();
			boostIconCell.setPreferredSize(new Dimension(24, 24));
			boostIconCell.setToolTipText("Assumes: " + text + (bis ? "" : " - click to change the boost"));
			if (boostItem > 0)
			{
				// Scaled to the 24px frame - the raw 36x32 item image
				// overflowed it (field report 2026-08-21). The onLoaded
				// re-seat is the async-image contract.
				seatItem(boostIconCell, boostItem);
			}
			else
			{
				// No potion assumed (None/prayer-only): a muted vial
				// keeps the toggle reachable.
				boostIconCell.setText("-");
				boostIconCell.setForeground(DIM);
				boostIconCell.setHorizontalAlignment(SwingConstants.CENTER);
			}
			if (!bis)
			{
				Ui.onClick(boostIconCell, () -> Ui.show(boostMenu(tab), boostIconCell));
			}
			headerRow.add(boostIconCell);
			String antivenom = Model.str(mob, "antivenom");
			if (antivenom != null && !antivenom.isEmpty())
			{
				headerRow.add(poisonPlate(mob, bis));
			}
			if (Model.flag(mob, "breathesFire"))
			{
				headerRow.add(firePlate(bis));
			}
			String cardSpell = Model.str(shown, "spell");
			if (cardSpell != null && cardSpell.startsWith("Fire "))
			{
				headerRow.add(sunfirePlate(bis));
			}
		}
		headerRow.add(bookPlate(shown, mob));
		card.add(left(headerRow));
		// The cannon strip rides between the assume row and the gear grid
		// (Andrew's v2 vision): left cannon / shared ammo / right cannon,
		// each the boost-rack picker, operators inside the rack.
		if (naval && !bis && Model.id(pageParams, "cannonCount") > 0)
		{
			card.add(left(cannonStrip(ship)));
		}
		if (shown == null)
		{
			// NEVER show the other side in this slot (field bug 2026-08-12:
			// a missing owned answer silently rendered the BiS set - "gear
			// that you don't have").
			card.add(left(new JLabel("No usable " + tab + " set")));
		}
		else
		{
			if (!Model.flag(shown, "kitBacked") && rosterView)
			{
				// The vents rule (2026-08-06): a tab-only fallback must
				// never read as the trip's answer.
				card.add(left(Ui.small(Ui.text("card.tabonly"), new Color(0xDCAA5A))));
			}
			String standDown = Model.str(shown, "arceuusStandDown");
			if (standDown != null)
			{
				// Andrew 2026-10-19: Water Surge with thralls on is not a
				// castable setup; the card says so and folds no thrall.
				JLabel caveat = Ui.small("Thralls and Death Charge stand down: " + standDown,
					new Color(0xDCAA5A));
				caveat.setToolTipText(Ui.text("standdown.tip"));
				card.add(left(caveat));
			}
			card.add(left(side(shown, bis, Model.thrallsFor(shown, thrallsDps) + shipDps, mob, tab, ship)));
		}
		if (naval && ship != null && !Model.list(ship, "cannons").isEmpty())
		{
			card.add(left(shipBreakdown(ship, shown)));
		}
		// The classic style strip sits BELOW the gear view: one tab per
		// style carrying its icon and that style's shown dps.
		JPanel tabStrip = Ui.darker(new FlowLayout(FlowLayout.CENTER, 2, 0));
		for (String style : styles.keySet())
		{
			Map<String, Object> styleNode = Model.map(styles, style);
			double tabDps = Model.num(styleNode, bis ? "bisTabDps" : "tabDps");
			if (tabDps > 0)
			{
				// Thralls fold into the shown number, like spec already
				// does - the tab IS the total (field ask 2026-08-14); a
				// card that stood down folds nothing.
				tabDps += Model.thrallsFor(Model.map(styleNode, bis ? "bis" : "yours"), thrallsDps) + shipDps;
			}
			boolean hasSet = tabDps > 0;
			boolean isSelected = style.equals(tab);
			JPanel tabCell = Ui.darker(new BorderLayout(2, 0));
			tabCell.setPreferredSize(new Dimension(64, 26));
			tabCell.setOpaque(isSelected);
			tabCell.setBorder(new RoundedBorder(isSelected
				? ACCENT : ColorScheme.MEDIUM_GRAY_COLOR, 1, 2));
			JLabel icon = new JLabel();
			int styleItem = Model.id(styleNode, "styleItem");
			if (styleItem > 0)
			{
				AsyncBufferedImage img = itemManager.getImage(styleItem);
				onImage(img, () -> icon.setIcon(Ui.icon(img, 16)));
			}
			else
			{
				seatSprite(icon, Model.id(styleNode, "styleSprite"), 16);
			}
			JLabel dpsLabel = Ui.label(hasSet ? String.format("%.2f", tabDps) : "-",
				isSelected ? Color.WHITE : MID);
			dpsLabel.setFont(dpsLabel.getFont().deriveFont(Font.BOLD, 12f));
			dpsLabel.setHorizontalAlignment(SwingConstants.CENTER);
			tabCell.add(icon, BorderLayout.WEST);
			tabCell.add(dpsLabel, BorderLayout.CENTER);
			tabCell.setToolTipText(Names.cap(style)
				+ (hasSet ? String.format(" - %.2f DPS", tabDps) : " - no set"));
			Ui.onClick(tabCell, () ->
			{
				// Instant feedback, reconciled by the arriving page.
				tabCell.setBorder(new RoundedBorder(ACCENT, 1, 2));
				tabCell.setOpaque(true);
				tabCell.repaint();
				commands.set("selectedTab", style);
			});
			tabStrip.add(tabCell);
		}
		LinkedHashMap<String, Color> present = new LinkedHashMap<>();
		Map<String, Object> shownGear = Model.map(shown, "gear");
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
			JPanel legend = Ui.darker(new FlowLayout(FlowLayout.LEFT, 6, 0));
			legend.add(statLine("Stored:", "Gear that needs a fetch trip", MID));
			for (Map.Entry<String, Color> source : present.entrySet())
			{
				legend.add(statLine(source.getKey(), "Fetch from " + source.getKey(), source.getValue()));
			}
			card.add(Box.createVerticalStrut(4));
			card.add(left(legend));
		}
		card.add(Box.createVerticalStrut(6));
		card.add(tabStrip);
		// The classic view swap sits directly UNDER the tabs: two
		// bordered sides, the active one bright, with the gap readout
		// pushed to the right.
		JPanel viewRow = Ui.darker(new BorderLayout());
		viewRow.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
		viewRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
		JPanel sides = Ui.darker(new FlowLayout(FlowLayout.CENTER, 6, 0));
		sides.add(viewSide("Yours", !bis, "Your best owned set",
			() -> commands.set("viewingBis", false)));
		sides.add(viewSide("BiS", bis, "The game-wide best set at your levels",
			() -> commands.set("viewingBis", true)));
		viewRow.add(sides, BorderLayout.WEST);
		Map<String, Object> yoursNode = Model.map(node, "yours");
		Map<String, Object> bisNode = Model.map(node, "bis");
		if (yoursNode != null && bisNode != null)
		{
			double mine = Model.num(node, "tabDps");
			double ceiling = Model.num(node, "bisTabDps");
			if (ceiling > 0)
			{
				JLabel gap = Ui.small(String.format("%.0f%% of BiS",
					Math.min(100.0, 100.0 * mine / ceiling)), MID);
				gap.setToolTipText(Ui.text("gap.tip"));
				viewRow.add(gap, BorderLayout.EAST);
			}
		}
		card.add(viewRow);
		List<Map<String, Object>> bench = Model.list(shown, "bench");
		List<Map<String, Object>> runes = Model.list(shown, "runes");
		List<Map<String, Object>> utilityRunes = Model.list(mob, "utilityRunes");
		boolean pouchRides = Model.id(mob, "castingPouch") > 0
			&& (!runes.isEmpty() || !utilityRunes.isEmpty());
		if (!bench.isEmpty() || pouchRides)
		{
			// 24px icons on a WRAPPING row - eight carried items fit
			// (field report: only 4 of 8 visible at full item size).
			JPanel invRow = Ui.darker(new WrapLayout(FlowLayout.CENTER, 2, 1));
			invRow.add(Ui.small("Inventory:", null));
			Map<String, Object> protection = Model.map(shown, "protection");
			for (Map<String, Object> carried : bench)
			{
				// GWD protection is worn on the way in, not swapped mid-fight.
				Object gods = protection == null ? null
					: protection.get(String.valueOf(Model.id(carried, "id")));
				invRow.add(smallItemCell(Model.id(carried, "id"),
					Model.str(carried, "name") + (gods != null
						? " (GWD protection: " + gods + " - wear it through the dungeon)"
						: " (carried swap)")));
			}
			int pouchId = Model.id(mob, "castingPouch");
			if (pouchId > 0 && (!runes.isEmpty() || !utilityRunes.isEmpty()))
			{
				invRow.add(smallItemCell(pouchId,
					(pouchId == 27281 || pouchId == 27509
						? "Divine rune pouch" : "Rune pouch")
						+ " - carries the trip's runes"));
			}
			card.add(Box.createVerticalStrut(4));
			card.add(left(invRow));
		}
		// The potion this card assumes is a supply to bring (Andrew
		// 2026-09-02) - unless the raid hands it out.
		int boostId = assume == null || Model.flag(assume, "boostSupplied") ? -1
			: Model.id(assume, "boostItem");
		if (!supplies.isEmpty() || !runes.isEmpty() || !utilityRunes.isEmpty() || boostId > 0)
		{
			JPanel supplyRow = Ui.darker(new WrapLayout(FlowLayout.CENTER, 2, 1));
			supplyRow.add(Ui.small("Supplies:", null));
			// One icon per rune TYPE (field call: no triple blood runes)
			// - the tooltip collects every reason it rides.
			Map<Integer, StringBuilder> runeWhy = new LinkedHashMap<>();
			Map<Integer, String> runeNames = new HashMap<>();
			for (Map<String, Object> rune : runes)
			{
				noteRune(runeNames, runeWhy, rune, "the autocast spell");
			}
			for (Map<String, Object> rune : utilityRunes)
			{
				noteRune(runeNames, runeWhy, rune, Model.str(rune, "why"));
			}
			for (Map.Entry<Integer, StringBuilder> rune : runeWhy.entrySet())
			{
				supplyRow.add(smallItemCell(rune.getKey(),
					runeNames.get(rune.getKey()) + " - " + rune.getValue()));
			}
			int capeId = Model.id(mob, "castingCape");
			// Once, not twice (Andrew 2026-09-02): the spellbook-cape supply
			// already draws the cape.
			if (capeId > 0 && byCategory(supplies, "spellbookCape") == null
				&& (!runes.isEmpty() || !utilityRunes.isEmpty()))
			{
				supplyRow.add(smallItemCell(capeId, Ui.text("supply.cape")));
			}
			if (boostId > 0)
			{
				String text = Model.str(assume, "text");
				int plus = text.indexOf(" + ");
				supplyRow.add(smallItemCell(boostId, (plus > 0 ? text.substring(plus + 3) : text)
					+ " " + Ui.text("supply.boost")));
			}
			for (Map<String, Object> supply : supplies)
			{
				JLabel cell = smallItemCell(Model.id(supply, "itemId"),
					Model.str(supply, "name")
						+ " - right-click to change");
				cell.setComponentPopupMenu(supplyMenu(Model.str(supply, "category"), Model.list(supply, "options")));
				supplyRow.add(cell);
			}
			card.add(Box.createVerticalStrut(4));
			card.add(left(supplyRow));
		}
		JPanel trioRow = Ui.darker(new FlowLayout(FlowLayout.CENTER, 4, 0));
		trioRow.add(trioButton(mob, "+", "mobSims", SIM_PALETTE,
			"Simmed for this mob", "remove-mob-sim", "sim-for-mob",
			"Sim an item as owned for this mob"));
		trioRow.add(trioButton(mob, "-", "mobExclusions", EXCLUDE_PALETTE,
			"Excluded for this mob", "remove-mob-exclusion", "exclude-for-mob",
			"Exclude an item for this mob"));
		trioRow.add(trioButton(mob, "~", "mobFilters", FILTER_PALETTE,
			"Bank-filter supplies for this mob", "remove-mob-filter", "add-mob-filter",
			Ui.text("trio.filter.add")));
		// The pins, visible at last (ISS-01M5HJ84ADBHWB2302G66WW2G4): a
		// tried swap pins its item, and a hidden pin outlived its sim.
		JPanel trioRow2 = Ui.darker(new FlowLayout(FlowLayout.CENTER, 4, 0));
		trioRow2.add(trioButton(mob, "^", "mobPins",
			new Color[]{PIN_BADGE, new Color(0x8C8264), new Color(0xA89458)},
			"Pinned for this mob", "remove-mob-pin", "pin-for-mob",
			"Pin an item for this mob"));
		JButton degrades = degradesButton(mob, shown);
		if (degrades != null)
		{
			trioRow2.add(degrades);
		}
		card.add(Box.createVerticalStrut(4));
		// Centred, not left() - field ask 2026-08-20. Two rows (Andrew
		// 2026-10-22: "so those 5 options aren't all on the same line"):
		// sims, exclusions, filters; then pins and degrade.
		card.add(centre(trioRow));
		card.add(Box.createVerticalStrut(2));
		card.add(centre(trioRow2));
		card.add(Box.createVerticalStrut(4));
		JToggleButton showBank = bankToggle("Show in bank", Ui.text("bank.show.tip"), "bank-show", () ->
		{
			List<Integer> ids = new ArrayList<>();
			Map<String, Object> gearMap = Model.map(shown, "gear");
			if (gearMap != null)
			{
				for (Object slotItem : gearMap.values())
				{
					if (slotItem instanceof Map)
					{
						ids.add(Model.id((Map<String, Object>) slotItem, "id"));
					}
				}
			}
			Map<String, Object> quiverItem = Model.map(shown, "quiverAmmo");
			if (quiverItem != null)
			{
				ids.add(Model.id(quiverItem, "id"));
			}
			for (Map<String, Object> carried : bench)
			{
				ids.add(Model.id(carried, "id"));
			}
			return Map.of("ids", ids);
		});
		showBank.setMargin(new Insets(1, 6, 1, 6));
		JPanel bankRow = Ui.darker(new FlowLayout(FlowLayout.CENTER, 4, 0));
		bankRow.add(showBank);
		Map<String, Object> bankPlan = BankLayout.build(shown,
			BankLayout.tripStacks(mob, assume, supplies, runes, utilityRunes));
		if (bankPlan != null)
		{
			bankRow.add(bankToggle("Filter bank", Ui.text("bank.filter.tip"), "bank-filter", () ->
			{
				List<Integer> layoutList = new ArrayList<>();
				for (int pos : (int[]) bankPlan.get("layout"))
				{
					layoutList.add(pos);
				}
				return Map.of("ids", bankPlan.get("ids"), "layout", layoutList);
			}));
		}
		card.add(centre(bankRow));
		String noteText = Model.str(mob, "note");
		boolean hasNote = noteText != null && !noteText.trim().isEmpty();
		JTextField note = new JTextField(noteText, 18);
		note.setBackground(new Color(0x4E4832));
		note.setForeground(new Color(0xD7CDA0));
		note.setCaretColor(new Color(0xD7CDA0));
		note.setBorder(BorderFactory.createLineBorder(new Color(0x625A3E)));
		note.setToolTipText(Ui.text("note.tip"));
		note.addActionListener(e -> commands.send("set-note", Map.of("text", note.getText())));
		card.add(Box.createVerticalStrut(4));
		if (hasNote)
		{
			card.add(centre(note));
		}
		else
		{
			// A ghost until there is something to say - the open post-it
			// field shouted for attention it rarely deserved.
			JLabel addNote = Ui.small("+ note", new Color(0x78735F));
			addNote.setToolTipText("Add a note for this mob");
			JPanel noteHost = centre(addNote);
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
	/** The in-card cannon strip (REQ-SC-8): left cannon / shared ammo /
	 * right cannon, sized like the assume icons, each opening the boost-rack
	 * picker. Operators live inside each cannon's rack. */
	private JPanel cannonStrip(Map<String, Object> ship)
	{
		int count = Model.id(pageParams, "cannonCount");
		List<Map<String, Object>> cannonNodes = Model.list(ship, "cannons");
		JPanel strip = Ui.darker(new FlowLayout(FlowLayout.CENTER, 6, 0));
		strip.add(cannonButton(0, cannonNodes.isEmpty() ? null : cannonNodes.get(0)));
		strip.add(ammoButton(ship));
		if (count > 1)
		{
			strip.add(cannonButton(1, cannonNodes.size() > 1 ? cannonNodes.get(1) : null));
		}
		return strip;
	}

	/** The trip ledger under a sea card: every part, summing to the TAB's
	 * number, with the roster row's number named on the way (Andrew
	 * 2026-09-02: "too many different output numbers"). */
	/** full = every line; collapsed = the total alone (the show/hide is a
	 * per-profile setting, Andrew 2026-09-03). */
	static String tripLedger(Map<String, Object> ship, Map<String, Object> side, boolean full)
	{
		boolean manned = "cannon".equals(Model.str(ship, "station"));
		double set = Model.num(side, "dps");
		double specDps = Model.num(Model.map(side, "spec"), "dpsAdded");
		StringBuilder t = new StringBuilder(Ui.text("ledger.head"));
		int body = t.length();
		double total = 0;
		if (manned)
		{
			t.append(ledgerRow("gear", "manning", "cannon 1", "#bebebe"));
		}
		else
		{
			t.append(ledgerRow("set", two(set), "", "#bebebe"));
			if (specDps > 0)
			{
				t.append(ledgerRow("+ spec", two(specDps), "", "#bebebe"));
			}
			total = set + specDps;
			t.append(ledgerRow("= gear", two(total), "", "#bebebe"));
		}
		int i = 1;
		for (Map<String, Object> cannon : Model.list(ship, "cannons"))
		{
			String blocked = Model.str(cannon, "blocked");
			double dps = Model.num(cannon, "dps");
			total += dps;
			String who = "player".equals(Model.str(cannon, "firedBy")) ? "you" : "crew";
			t.append(ledgerRow("+ cannon " + i++, blocked != null ? blocked : two(dps),
				Model.str(cannon, "tier") + ", " + who, blocked != null ? "#dc8c78" : "#96bedc"));
		}
		if (!full)
		{
			t.setLength(body);
		}
		t.append(ledgerRow("<b>= total</b>", "<b>" + two(total) + "</b>", "", "#82c882"));
		return t.append("</table></html>").toString();
	}

	private static String ledgerRow(String key, String value, String note, String color)
	{
		return String.format(Ui.text("ledger.row"), color, key, value, note);
	}

	/** A dps figure to two places, locale-proof (the ledger is read by tests). */
	private static String two(double value)
	{
		return String.format(Locale.ROOT, "%.2f", value);
	}

	private JPanel shipBreakdown(Map<String, Object> ship, Map<String, Object> shownSide)
	{
		boolean full = breakdownShown.getAsBoolean();
		JLabel ledger = Ui.small(tripLedger(ship, shownSide, full), null);
		// Notes stay short (Andrew 2026-09-03: "player manning cannon causes
		// text cutoff") - the why lives here, with the show/hide.
		ledger.setToolTipText(("cannon".equals(Model.str(ship, "station"))
			? Ui.text("ledger.manning") : Ui.text("ledger.parts"))
			+ " - click to " + (full ? "hide" : "show") + " the breakdown");
		Ui.onClick(ledger, () -> breakdownToggle.accept(!full));
		ledger.setBorder(BorderFactory.createEmptyBorder(4, 0, 2, 0));
		return left(ledger);
	}

	/** One cannon's icon button: the skill-guide item illustration, a red
	 * ring + reason when the operate gate blocks it, and the boost-picker
	 * rack of all seven materials on click. */
	private JButton cannonButton(int index, Map<String, Object> node)
	{
		String param = "cannon" + (index + 1) + "Material";
		String tier = Model.str(pageParams, param);
		NavalCombat.Cannon cannon =
			NavalCombat.cannon(tier == null ? "bronze" : tier);
		String blocked = Model.str(node, "blocked");
		JButton button = iconButton(cannon.itemId, blocked != null);
		String fired = Model.str(node, "firedBy");
		button.setToolTipText("Cannon " + (index + 1) + ": "
			+ Names.cap(cannon.tier) + ("player".equals(fired) ? " - you" : " - crew")
			+ (blocked != null ? ". " + blocked : ""));
		button.addActionListener(e ->
		{
			JPopupMenu menu = new JPopupMenu();
			JPanel rack = rack(4);
			for (NavalCombat.Cannon option
				: NavalCombat.cannons())
			{
				rack.add(pickCell(menu,
					new ImageIcon(itemManager.getImage(option.itemId)),
					Names.cap(option.tier) + " cannon (Sailing " + option.sailing + ")",
					option.tier.equals(cannon.tier), 38,
					() -> commands.set(param, option.tier)));
			}
			menu.add(rack);
			// The double rack (Andrew, v2): who fires it, right under the
			// materials. A crew pick below the cannon's gate auto-raises.
			menu.addSeparator();
			String opParam = "cannon" + (index + 1) + "Operator";
			String current = Model.str(pageParams, opParam);
			menu.add(Ui.check("You man this cannon", "you".equals(current),
				() -> commands.set(opParam, "you")));
			for (int p = 1; p <= 4; p++)
			{
				final String pick = "crew" + p;
				String label = "Crewmate - Privateering " + p
					+ (p < cannon.privateering ? " (raises to " + cannon.privateering + ")" : "");
				menu.add(Ui.check(label, pick.equals(current),
					() -> commands.set(opParam, pick)));
			}
			Ui.show(menu, button);
		});
		return button;
	}

	/** The shared ammo's icon button: only tiers EVERY carried cannon can
	 * fire are offered (a mithril + dragon pair tops out at mithril). */
	private JButton ammoButton(Map<String, Object> ship)
	{
		String pick = Model.str(pageParams, "cannonAmmo");
		boolean detect = pick == null || "detect".equals(pick);
		// Detect shows what it RESOLVED to (the boost idiom); a dry bank
		// shows the reason on a red ring.
		String resolved = Model.str(ship, "ammo");
		String ammoBlocked = Model.str(ship, "ammoBlocked");
		String shownTier = resolved != null ? resolved : detect ? "bronze" : pick;
		NavalCombat.Ball ball =
			NavalCombat.ball(shownTier == null ? "bronze" : shownTier);
		JButton button = iconButton(ball.itemId, ammoBlocked != null);
		button.setToolTipText("Cannonballs: "
			+ (ammoBlocked != null ? ammoBlocked
				: detect ? "Detect best - " + Names.cap(ball.tier) + " (best banked)"
					: Names.cap(ball.tier))
			+ " - one tier for every cannon");
		button.addActionListener(e ->
		{
			int count = Model.id(pageParams, "cannonCount");
			List<String> tiers = new ArrayList<>();
			for (int i = 0; i < count; i++)
			{
				tiers.add(Model.str(pageParams, "cannon" + (i + 1) + "Material"));
			}
			JPopupMenu menu = new JPopupMenu();
			menu.add(Ui.check("Detect best in bank", detect,
				() -> commands.set("cannonAmmo", "detect")));
			menu.addSeparator();
			JPanel rack = rack(4);
			for (NavalCombat.Ball option
				: NavalCombat.balls())
			{
				boolean everyone = true;
				for (String t : tiers)
				{
					everyone &= NavalCombat.canFire(t, option.tier);
				}
				if (!everyone)
				{
					continue;
				}
				rack.add(pickCell(menu,
					new ImageIcon(itemManager.getImage(option.itemId)),
					Names.cap(option.tier) + " cannonball",
					!detect && option.tier.equals(ball.tier), 38,
					() -> commands.set("cannonAmmo", option.tier)));
			}
			menu.add(rack);
			Ui.show(menu, button);
		});
		return button;
	}

	/** palette = {active, muted, active border} (the trio's shared sigil colours). */
	private JButton trioButton(Map<String, Object> mob, String sigil,
		String listKey, Color[] palette,
		String noun, String removeCommand, String addCommand, String addPrompt)
	{
		List<Map<String, Object>> items = Model.list(mob, listKey);
		boolean any = !items.isEmpty();
		JButton button = sigilButton(sigil + items.size(), any, any ? palette[0] : palette[1], palette[2],
			noun + " - click to manage");
		button.addActionListener(e ->
		{
			JPopupMenu menu = new JPopupMenu();
			for (Map<String, Object> item : items)
			{
				int id = Model.id(item, "id");
				String scope = Model.str(item, "scope");
				String itemName = Model.str(item, "name");
				Ui.item(menu, "Remove " + itemName
					+ (scope == null || "ALL".equals(scope) ? "" : " (" + scope + ")"), () ->
				{
					Map<String, Object> args = new HashMap<>();
					args.put("itemId", id);
					args.put("label", itemName);
					if (Model.str(item, "slot") != null)
					{
						args.put("slot", Model.str(item, "slot"));
					}
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
				Ui.item(menu, "Clear all (" + items.size() + ")",
					() -> commands.send("clear-mob-list", Map.of("kind", listKey)));
				menu.addSeparator();
			}
			Ui.item(menu, addPrompt + " (search)...", searchItem(addPrompt, addCommand));
			Ui.show(menu, button);
		});
		return button;
	}

	/** The add-by-search menu action: the picker's choice goes out as
	 * {itemId, label} on the given command. */
	Runnable searchItem(String prompt, String command)
	{
		return () -> picker.search(prompt,
			(id, name) -> commands.send(command, Map.of("itemId", id, "label", name)));
	}

	/** One side of the classic view swap: the active side wears a bright
	 * accent edge and white text, the other a quiet outline. */
	private static JLabel viewSide(String text, boolean selected, String tooltip, Runnable onClick)
	{
		JLabel side = Ui.label(text, selected ? Color.WHITE : new Color(0x969696));
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

	private final Map<Integer, ImageIcon> spriteIconCache =
		new HashMap<>();

	/** The set's spellbook plate, ALWAYS shown (field report x3): the
	 * autocast spell's book, else arceuus/lunar when thralls or Death
	 * Charge assume it, else your live book in neutral. RED when the
	 * required book is not the one you are on. */
	private JLabel bookPlate(Map<String, Object> shownCard, Map<String, Object> mob)
	{
		String required = null;
		String reason = null;
		String spell = Model.str(shownCard, "spell");
		Map<String, Object> spellbooks = Model.map(pageWide, "spellbooks");
		if (spell != null && spellbooks != null)
		{
			required = Model.str(spellbooks, spell);
			reason = "autocasts " + spell;
		}
		// The FIGHT's own book beats every gear consideration (wiki-
		// verified curation - the Sire wants Ancients for the Shadow
		// Barrage stun regardless of what the set casts).
		String fightBook = Model.str(mob, "fightBook");
		if (fightBook != null && !fightBook.isEmpty())
		{
			required = fightBook;
			reason = Model.str(mob, "fightBookReason");
		}
		if (Model.flag(mob, "cureMe"))
		{
			// Cure Me camps Lunar and outranks the thralls rule below.
			required = "lunar";
			reason = "Cure Me";
		}
		if (required == null && pageParams != null
			&& (Model.flag(pageParams, "thralls") || Model.id(pageParams, "deathCharge") > 0))
		{
			boolean viaSwap = "SPELLBOOK_SWAP".equals(Model.str(
				byCategory(Model.list(pageWide, "supplyCatalog"), "arceuusAccess"), "current"));
			required = viaSwap ? "lunar" : "arceuus";
			reason = viaSwap ? Ui.text("book.swap") : Ui.text("book.arceuus");
		}
		// The book the fight/set asks for, before any swap rewrite - the
		// picker offers it as the "camp it directly" choice.
		String directBook = required;
		String directReason = reason;
		boolean swapOn = Model.flag(pageParams, "spellbookSwap");
		// With the swap on, LUNAR is home: you cast Vengeance there and
		// swap into whatever the fight wants (field report 2026-08-22:
		// the Sire still demanded Ancients and said nothing about the
		// swap being on).
		if (swapOn)
		{
			required = "lunar";
			reason = directBook == null || "lunar".equalsIgnoreCase(directBook)
				? "Lunar home for Vengeance"
				: String.format(Ui.text("book.swaphome"), directBook.toUpperCase());
		}
		String live = Model.str(pageWide, "liveSpellbook");
		boolean offBook = required != null && live != null && !live.isEmpty() && !live.equalsIgnoreCase(required);
		JLabel plate = new JLabel();
		plate.setPreferredSize(new Dimension(24, 22));
		plate.setHorizontalAlignment(SwingConstants.CENTER);
		if (offBook)
		{
			plate.setOpaque(true);
			plate.setBackground(new Color(0x782828));
		}
		// No required book = a DIMMED generic tab, never a real book's
		// sprite (field confusion 2026-08-14: the live book shown in
		// neutral read as a recommendation - a powered staff like the
		// Shadow needs no book at all).
		String bookTip = required == null
			? Ui.text("book.none")
			: String.format(Ui.text("book.wants"), required.toUpperCase(), reason, offBook ? "NOT " : "");
		// The swap+Vengeance option rides the plate, the way prayers and
		// boosts ride their icons (field ask 2026-08-22). Supplies only:
		// it changes which runes the trip carries, never the dps.
		boolean swapAvailable = assumeOptions != null
			&& Model.flag(assumeOptions, "spellbookSwapAvailable");
		if (swapAvailable)
		{
			bookTip = bookTip + Ui.text("book.choose");
			plate.setToolTipText("<html>" + bookTip + "</html>");
			if (swapOn)
			{
				plate.setBorder(new RoundedBorder(ACCENT, 2, 6));
			}
			String home = directBook;
			Ui.onClick(plate, () -> showBookMenu(plate, home, swapOn));
		}
		else
		{
			plate.setToolTipText(bookTip);
		}
		int sprite = required == null
			? SpriteID.TAB_MAGIC : bookSprite(required);
		boolean dim = required == null;
		if (sprite > 0)
		{
			onSprite(sprite, img ->
			{
				Image scaled =
					img.getScaledInstance(18, 18, Image.SCALE_SMOOTH);
				plate.setIcon(dim
					? new ImageIcon(GrayFilter
						.createDisabledImage(toBuffered(scaled)))
					: new ImageIcon(scaled));
			});
		}
		return plate;
	}

	private static BufferedImage toBuffered(Image image)
	{
		BufferedImage out = new BufferedImage(
			18, 18, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(image, 0, 0, null);
		g.dispose();
		return out;
	}

	/** The book tab sprites (SpriteID); -1 for a book that has none. */
	private static final Map<String, Integer> BOOK_SPRITES = Map.of(
		"ancient", SpriteID.TAB_MAGIC_SPELLBOOK_ANCIENT_MAGICKS, "lunar", SpriteID.TAB_MAGIC_SPELLBOOK_LUNAR,
		"arceuus", SpriteID.TAB_MAGIC_SPELLBOOK_ARCEUUS, "standard", SpriteID.TAB_MAGIC);

	private static int bookSprite(String book)
	{
		return BOOK_SPRITES.getOrDefault(book == null ? "" : book.toLowerCase(), -1);
	}

	/** The prayer picker popup (anchored to the wings icon): Detect /
	 * None / each prayer with its sprite, checkmarked at the pick. The
	 * PRAYER BOOK view (field ask 2026-08-14): the options render as the
	 * game's prayer-tab grid - sprite cells, the current pick on the
	 * accent plate - not a text list. */
	JPopupMenu prayerMenu(String tab)
	{
		Map<String, Object> sprites = Model.map(assumeOptions, "prayerSprites");
		Map<String, Object> images = Model.map(assumeOptions, "prayerImages");
		List<String[]> cells = new ArrayList<>();
		for (Object option : Model.list2(Model.map(assumeOptions, "prayers"), tab))
		{
			cells.add(new String[]{String.valueOf(option), String.valueOf(option)});
		}
		return pickerMenu(tab, "set-prayer-pick", "prayerPicks", "Detect best", "None (prayerless)", cells, 26,
			name ->
			{
				int sprite = Model.id(sprites, name);
				String image = Model.str(images, name);
				return sprite > 0 ? cachedSprite(sprite)
					: image != null ? Ui.icon(ImageUtil.loadImageResource(ResultCards.class, image), 15) : null;
			});
	}

	/** The boost picker popup - the SAME game-tab grid as the prayer
	 * book (field ask 2026-08-20: one interface, the prayer one wins),
	 * with potion icons for cells. Item images load ASYNC - the icon gets
	 * the live image, never a getScaledInstance of it (blank until reopen). */
	JPopupMenu boostMenu(String tab)
	{
		Map<String, Object> potionIds = Model.map(assumeOptions, "boostItems");
		List<String[]> cells = new ArrayList<>();
		for (Map<String, Object> option : Model.list(Model.map(assumeOptions, "boosts"), tab))
		{
			cells.add(new String[]{Model.str(option, "label"), Model.str(option, "key")});
		}
		return pickerMenu(tab, "set-boost-pick", "boostPicks", "Detect best in bank", "None (unboosted)", cells, 38,
			label ->
			{
				int itemId = Model.id(potionIds, label);
				return itemId > 0 ? new ImageIcon(itemManager.getImage(itemId)) : null;
			});
	}

	/** The two pickers' shared shape: the Detect and None check items, then
	 * the game-tab grid of {label, key} cells (iconOf draws each by label),
	 * the current pick (pageParams.picksKey[tab]) lit. */
	private JPopupMenu pickerMenu(String tab, String command, String picksKey, String detectLabel,
		String noneLabel, List<String[]> cells, int size, Function<String, Icon> iconOf)
	{
		String current = Model.str(Model.map(pageParams, picksKey), tab);
		JPopupMenu menu = new JPopupMenu();
		menu.add(Ui.check(detectLabel, current == null, () -> sendPick(command, tab, null)));
		menu.add(Ui.check(noneLabel, "NONE".equals(current), () -> sendPick(command, tab, "NONE")));
		if (!cells.isEmpty())
		{
			menu.addSeparator();
			JPanel grid = rack(5);
			for (String[] cell : cells)
			{
				grid.add(pickCell(menu, iconOf.apply(cell[0]), cell[0], cell[1] != null && cell[1].equals(current),
					size, () -> sendPick(command, tab, cell[1])));
			}
			menu.add(grid);
		}
		return menu;
	}

	/** How you reach the book this fight wants: camp it, or keep Lunar
	 * home and Spellbook Swap in (which also buys Vengeance). Wears the
	 * same game-tab grid as the prayer book and the boost rack (field
	 * ask 2026-08-22) - the runes follow the choice, the dps never does. */
	private void showBookMenu(Component anchor, String directBook, boolean swapOn)
	{
		JPopupMenu menu = new JPopupMenu();
		JPanel books = rack(2);
		boolean any = directBook == null || directBook.isEmpty();
		String direct = any ? "standard" : directBook;
		books.add(pickCell(menu, cachedSprite(bookSprite(direct)),
			any ? "Any book - nothing here needs one" : "Camp " + direct.toUpperCase(),
			!swapOn, 30,
			() -> commands.set("spellbookSwap", false)));
		boolean needsSwap = !any && !"lunar".equalsIgnoreCase(directBook);
		books.add(pickCell(menu, cachedSprite(bookSprite("lunar")),
			needsSwap
				? String.format(Ui.text("book.swapinto"), direct.toUpperCase())
				: Ui.text("book.lunar"),
			swapOn, 30,
			() -> commands.set("spellbookSwap", true)));
		menu.add(books);
		Ui.show(menu, anchor);
	}

	/** One cell of the game-tab picker grid (the prayer-book pattern,
	 * shared by the prayer and boost pickers): icon or initial, accent
	 * plate on the pick, hover ring, click picks and closes. */
	private JLabel pickCell(JPopupMenu menu, Icon icon,
		String tooltip, boolean picked, int size, Runnable onPick)
	{
		JLabel cell = pickedCell(size, picked);
		if (icon != null)
		{
			cell.setIcon(icon);
		}
		else
		{
			cell.setText(tooltip.substring(0, 1));
			cell.setForeground(LIGHT);
		}
		cell.setToolTipText(tooltip + (picked ? " (assumed)" : ""));
		cell.setCursor(Cursor.getPredefinedCursor(
			Cursor.HAND_CURSOR));
		cell.addMouseListener(new MouseAdapter()
		{
			public void mouseClicked(MouseEvent e)
			{
				menu.setVisible(false);
				onPick.run();
			}

			public void mouseEntered(MouseEvent e)
			{
				cell.setBorder(BorderFactory.createLineBorder(ACCENT));
			}

			public void mouseExited(MouseEvent e)
			{
				if (!picked)
				{
					cell.setBorder(BorderFactory.createLineBorder(
						ColorScheme.DARKER_GRAY_HOVER_COLOR));
				}
			}
		});
		return cell;
	}

	private void sendPick(String command, String tab, String value)
	{
		Map<String, Object> args = new HashMap<>();
		args.put("style", tab);
		args.put("value", value);
		commands.send(command, args);
	}

	/** A sprite icon from the shared cache (primed async; a menu opened
	 * before the load shows it on its next open). */
	private Icon cachedSprite(int spriteId)
	{
		ImageIcon icon = spriteIconCache.get(spriteId);
		if (icon != null)
		{
			return icon;
		}
		ImageIcon fresh = new ImageIcon(
			new BufferedImage(15, 15, BufferedImage.TYPE_INT_ARGB));
		spriteIconCache.put(spriteId, fresh);
		onSprite(spriteId, img -> fresh.setImage(img.getScaledInstance(15, 15, Image.SCALE_SMOOTH)));
		return fresh;
	}

	/** A stat line led by its game-sprite icon (async, 14px). */
	/** The ship damage line, sized for the stat column. */
	static String shipDamageText(double dtps, int keelMax)
	{
		return keelMax == 0 ? "no damage"
			: String.format(Locale.ROOT, "~%.1f max %d", dtps, keelMax);
	}

	/** The damage-taken line and its tooltip. Coverage decides the text
	 * (ISS-01M5FZ3KYCGEMMX1A45D3J9NQ9): unknown reads "?", never safe; a
	 * number that leaves attacks out is starred and names them. */
	static String[] dtpsLine(Map<String, Object> incoming)
	{
		String coverage = Model.str(incoming, "coverage");
		if ("none".equals(coverage))
		{
			return new String[]{"DTPS: ?", "No estimate for this monster's attacks"};
		}
		double dps = Model.num(incoming, "dps");
		String tip = String.format(Ui.text("dtps.tip"), Model.str(incoming, "protectPrayer") == null
			? "No protection prayer helps" : "Pray " + Model.str(incoming, "protectPrayer"),
			dps, Model.num(incoming, "unprayedDps"));
		if (!"partial".equals(coverage))
		{
			return new String[]{String.format("~%.1f", dps), tip};
		}
		List<String> missing = new ArrayList<>();
		for (Object style : Model.list2(incoming, "unmodeled"))
		{
			missing.add(String.valueOf(style));
		}
		return new String[]{String.format("~%.1f*", dps),
			tip + ". Not counted: " + String.join(", ", missing)};
	}

	/**
	 * The slot ladder as a strip under the gear grid (ISS-01M5GHXXEZKX8S68M5X9DHY6ME,
	 * Andrew 2026-10-21: "Go with concept A"): left to right, the owned steps
	 * down (farthest first), the worn item, the steps up (closest first). Each
	 * column is a bar sized by its gain under the active mode, the item's icon
	 * and its change. A click on a step sims it for this monster and wears it
	 * (ISS-01M5GNMHHCD8M56JD4GAZBFV7Y: "Sim + pin for this mob"); its
	 * right-click menu adds sim everywhere and Not Enough Runes.
	 */
	JPanel ladderStrip(Map<String, Object> ladder, Map<String, Object> worn, String slot, boolean nerAvailable)
	{
		List<Map<String, Object>> down = Model.list(ladder, "down");
		List<Map<String, Object>> up = Model.list(ladder, "up");
		double maxGain = 1e-9;
		for (Map<String, Object> rung : down)
		{
			maxGain = Math.max(maxGain, Math.abs(Model.num(rung, "gain")));
		}
		for (Map<String, Object> rung : up)
		{
			maxGain = Math.max(maxGain, Math.abs(Model.num(rung, "gain")));
		}
		boolean group = Model.flag(ladder, "group");
		// No gaps between columns, so the bars' baselines join into one line.
		JPanel row = Ui.clear(new FlowLayout(FlowLayout.CENTER, 0, 0));
		for (int i = down.size() - 1; i >= 0; i--)
		{
			row.add(ladderColumn(down.get(i), slot, maxGain, LADDER_DOWN, nerAvailable, group));
		}
		row.add(ladderColumn(worn, slot, maxGain, LADDER_WORN, nerAvailable, group));
		for (Map<String, Object> rung : up)
		{
			row.add(ladderColumn(rung, slot, maxGain, LADDER_UP, nerAvailable, group));
		}

		JPanel strip = new RoundPanel(LADDER_INSET, 8);
		strip.setLayout(new BoxLayout(strip, BoxLayout.Y_AXIS));
		strip.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
		JLabel title = Ui.small(Names.cap(slot) + " swaps" + (group ? " (group)" : ""), TEXT);
		title.setToolTipText(group
			? Ui.text("ladder.group.tip")
			: Ui.text("ladder.solo.tip"));
		JPanel legend = Ui.clear(new BorderLayout());
		legend.add(Ui.small("your gear", LADDER_DOWN), BorderLayout.WEST);
		legend.add(Ui.small("upgrades", LADDER_UP), BorderLayout.EAST);
		legend.setMaximumSize(new Dimension(row.getPreferredSize().width, legend.getPreferredSize().height));
		for (JComponent part : new JComponent[]{title, row, legend})
		{
			part.setAlignmentX(Component.CENTER_ALIGNMENT);
			strip.add(part);
		}
		return strip;
	}

	/** The card-wide row that centres the ladder strip, under a divider. */
	static JPanel centered(JComponent inner)
	{
		// Centre the strip in whatever width the card has - clipped evenly on
		// both sides if it ever overflows - and never ask the card for the
		// strip's width (Andrew 2026-10-22: a FlowLayout clipped it on one
		// side; GridBag pushed it right and cut it when the card was narrow).
		// A plain LayoutManager (FlowLayout's base: its add/remove hooks are
		// no-ops), so the row's maximum size stays Swing's default.
		JPanel row = Ui.darker(new FlowLayout()
		{
			public Dimension preferredLayoutSize(Container parent)
			{
				Insets in = parent.getInsets();
				int height = parent.getComponentCount() == 0 ? 0 : parent.getComponent(0).getPreferredSize().height;
				return new Dimension(1, height + in.top + in.bottom);
			}

			public Dimension minimumLayoutSize(Container parent)
			{
				return preferredLayoutSize(parent);
			}

			public void layoutContainer(Container parent)
			{
				if (parent.getComponentCount() == 0)
				{
					return;
				}
				Component child = parent.getComponent(0);
				Dimension size = child.getPreferredSize();
				Insets in = parent.getInsets();
				int left = in.left;
				int right = parent.getWidth() - in.right;
				// A card wider than the side panel's viewport would centre the
				// strip off screen to the right (Andrew 2026-10-22: "still seeing
				// the ladder view off-center"): centre on the visible span.
				JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, parent);
				if (viewport != null && viewport.getView() != null && viewport.getExtentSize().width > 0)
				{
					Point at = SwingUtilities.convertPoint(parent, 0, 0, viewport.getView());
					int viewLeft = viewport.getViewPosition().x;
					left = Math.max(left, viewLeft - at.x);
					right = Math.min(right, viewLeft + viewport.getExtentSize().width - at.x);
				}
				child.setBounds(left + (right - left - size.width) / 2, in.top, size.width, size.height);
			}
		});
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 0, 0, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(5, 0, 2, 0)));
		row.add(inner);
		return row;
	}

	private static final Color LADDER_INSET = new Color(0x232323);
	private static final Color LADDER_WORN_BG = new Color(0x343434);
	private static final Color LADDER_BASELINE = new Color(0x505050);
	private static final Color LADDER_DOWN = new Color(0x8A8A8A);
	private static final Color LADDER_WORN = new Color(0xD2D2D2);
	private static final Color LADDER_UP = new Color(0x78C878);
	private static final Color LADDER_TAKEN = new Color(0xB4A078);
	private static final int LADDER_BAR_MID = 24;
	/** Five columns + padding stay under 180 px: the card body is about
	 * 190 px inside RuneLite's 225 px side panel. */
	private static final int LADDER_COLUMN = 32;

	/** A panel filled with a rounded rectangle (the strip's inset, the worn column). */
	private static final class RoundPanel extends JPanel
	{
		private final Color fill;
		private final int arc;

		RoundPanel(Color fill, int arc)
		{
			this.fill = fill;
			this.arc = arc;
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = Ui.smooth(g);
			g2.setColor(fill);
			g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
			g2.dispose();
			super.paintComponent(g);
		}
	}

	/** A ladder bar: a rounded top, standing on the strip's baseline. */
	private static final class LadderBar extends JComponent
	{
		private final Color color;

		LadderBar(Color color, int height)
		{
			this.color = color;
			setPreferredSize(new Dimension(8, height));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = Ui.smooth(g);
			g2.setColor(color);
			// Overdraw past the bottom so only the top corners round.
			g2.fillRoundRect(0, 0, getWidth(), getHeight() + 6, 6, 6);
			g2.dispose();
		}
	}

	/** One strip column: a bar on the baseline, the icon, the change. The
	 * worn item's column sits on a lighter rounded ground and does nothing on
	 * a click; any other sims-and-wears on a left click. */
	private JPanel ladderColumn(Map<String, Object> rung, String slot, double maxGain, Color color,
		boolean nerAvailable, boolean group)
	{
		boolean worn = color == LADDER_WORN;
		int id = Model.id(rung, "id");
		int height = worn ? LADDER_BAR_MID
			: (int) Math.max(4, Math.min(2 * LADDER_BAR_MID - 4,
				Math.round(LADDER_BAR_MID + (LADDER_BAR_MID - 6) * Model.num(rung, "gain") / maxGain)));
		LadderBar bar = new LadderBar(color, height);
		bar.setName("bar:" + id);
		JPanel bars = Ui.clear(new BorderLayout());
		JPanel bottom = Ui.clear(new FlowLayout(FlowLayout.CENTER, 0, 0));
		bottom.add(bar);
		bars.add(bottom, BorderLayout.SOUTH);
		bars.setPreferredSize(new Dimension(LADDER_COLUMN, 2 * LADDER_BAR_MID + 1));
		bars.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, LADDER_BASELINE));

		JLabel cell = square(32);
		cell.setName("rung:" + id);
		cell.setOpaque(true);
		cell.setBackground(CELL_BG);
		itemManager.getImage(id).addTo(cell);
		boolean owned = worn || Model.flag(rung, "owned");
		String name = Model.str(rung, "name");
		String change = worn ? "you" : Model.str(rung, "delta");
		String price = owned ? "" : Gp.formatShort(Model.id(rung, "price"));
		String with = Model.str(rung, "with");
		String second = Model.str(rung, worn ? "base2" : "delta2");
		boolean same = !worn && Model.flag(rung, "same");
		String tip = name + (with == null ? "" : " with " + with)
			+ (worn ? " - worn" : " " + change + (second == null ? "" : ", " + second))
			+ (same ? " - same result here, better stats" : "")
			+ (price.isEmpty() ? "" : " - " + price + " gp");
		cell.setBorder(owned ? BorderFactory.createLineBorder(color)
			: BorderFactory.createDashedBorder(color, 1f, 3f, 2f, false));
		if (worn)
		{
			cell.setToolTipText(tip);
		}
		else
		{
			Map<String, Object> tryArgs = Map.of("slot", slot, "itemId", id, "label", name);
			Map<String, Object> simArgs = Map.of("itemId", id, "label", name);
			JPopupMenu menu = new JPopupMenu();
			if (owned && group)
			{
				// A group strip pins nothing: one member's pin would force
				// the item on a mob that carries another style's swap.
				cell.setToolTipText(tip + " - yours");
			}
			else if (owned)
			{
				// Yours already: a sim changes nothing, so no click - an
				// explicit pin stays in the menu for forcing it here.
				boolean spec = "spec".equals(slot);
				Ui.item(menu, spec ? "Pin as spec for this mob" : "Pin for this mob",
					String.format(Ui.text("ladder.pin.tip"), name, slot),
					() -> commands.send("ladder-try", tryArgs));
				cell.setToolTipText(tip + " - yours. Right-click to pin it here");
			}
			else
			{
				// A click sims it for this mob, nothing more (Andrew
				// 2026-10-22: "we are using "pin" for this mob instead of sim
				// for this mob"): a step up is better swapped in alone, so the
				// optimizer wears it - and removing the sim takes it off again.
				Runnable sim = () -> commands.send(group ? "sim-for-group" : "sim-for-mob", simArgs);
				Ui.onLeftClick(cell, sim);
				Ui.item(menu, group ? "Sim for this group" : "Sim for this mob",
					"Pretend you own " + name + (group ? " for every monster in this group" : " for this monster"),
					sim);
				Ui.item(menu, "Sim everywhere", "Pretend you own " + name + " for every monster",
					() -> commands.send("toggle-sim", simArgs));
				// Both tooltips say a sim lets the optimizer re-fit the whole set
				// around the item, so the card can land above the step's own
				// number, never below.
				if (nerAvailable)
				{
					Ui.item(menu, Ui.text("ladder.ner"), "Open " + name + " in Not Enough Runes",
						() -> commands.send("ner-open", Map.of("itemId", id)));
					cell.setToolTipText(String.format(Ui.text("ladder.sim.ner"), tip, group ? "group" : "mob"));
				}
				else
				{
					cell.setToolTipText(String.format(Ui.text("ladder.sim.noner"), tip, group ? "group" : "mob"));
				}
			}
			// Hide a step you never want suggested (Andrew 2026-10-22:
			// "stop showing this as a swap option (this mob/all mobs)") - an
			// exclusion, listed and undone from the - list like any other, so
			// the optimizer leaves the item out too. A group strip hides
			// everywhere only: "this mob" would be one member of many.
			if (menu.getComponentCount() > 0)
			{
				menu.addSeparator();
			}
			if (!group)
			{
				Ui.item(menu, "Hide for this mob",
					String.format(Ui.text("ladder.hide.mob"), name),
					() -> commands.send("exclude-for-mob", Map.of("itemId", id, "scope", "ALL", "label", name)));
			}
			Ui.item(menu, "Hide for all mobs",
				String.format(Ui.text("ladder.hide.all"), name),
				() -> commands.send("toggle-exclusion", simArgs));
			cell.setComponentPopupMenu(menu);
		}

		JLabel label = Ui.small(worn ? shortDelta(Model.str(rung, "base")) : same ? "=" : shortDelta(change), color);
		label.setName("value:" + id);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		// Balanced's second number: the damage-taken change (worn: its own).
		JLabel label2 = second == null ? null : Ui.small(shortDelta(second), LADDER_TAKEN);
		if (label2 != null)
		{
			label2.setName("value2:" + id);
			label2.setHorizontalAlignment(SwingConstants.CENTER);
		}

		JPanel column = worn ? new RoundPanel(LADDER_WORN_BG, 8) : new JPanel();
		column.setOpaque(false);
		if (worn)
		{
			column.setName("worn-mark");
		}
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
		for (JComponent part : label2 == null ? new JComponent[]{bars, cell, label}
			: new JComponent[]{bars, cell, label, label2})
		{
			// BoxLayout stretches to the maximum size - pin the cell and the
			// bar column so they keep their square / full height.
			if (part == bars || part == cell)
			{
				part.setMinimumSize(part.getPreferredSize());
				part.setMaximumSize(part.getPreferredSize());
			}
			part.setAlignmentX(Component.CENTER_ALIGNMENT);
			column.add(part);
		}
		column.add(Box.createVerticalStrut(2));
		return column;
	}

	/** The strip's narrow label: the number only ("+0.21", "4.20"). */
	private static String shortDelta(String delta)
	{
		if (delta == null)
		{
			return "";
		}
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("[+-]?[0-9.]+%?").matcher(delta);
		return m.find() ? m.group() : delta;
	}

	/** The slot whose ladder strip is open ("" = none) - page view state. */
	private String ladderSlotOpen()
	{
		String open = Model.str(pageParams, "ladderSlot");
		return open == null ? "" : open;
	}

	/** Whether Not Enough Runes is enabled (the page params carry it). */
	private boolean nerAvailable()
	{
		return Model.flag(pageParams, "nerAvailable");
	}

	/** The defensive mode's trade in words (the line's tooltip): the DPS
	 * given up and the damage taken cut, or why it kept Max DPS. */
	static String modeLine(Map<String, Object> trade)
	{
		if (trade == null)
		{
			return null;
		}
		String mode = "TANKY".equals(Model.str(trade, "mode")) ? "Tanky" : "Balanced";
		String reason = Model.str(trade, "reason");
		if (reason != null)
		{
			return mode + ": kept Max DPS (" + reason + ")";
		}
		return mode + ": -" + Model.id(trade, "dpsLossPct") + "% DPS, -"
			+ Model.id(trade, "dmgCutPct") + "% damage taken";
	}

	private JLabel statIconLine(String text, String tooltip, Color color, int spriteId)
	{
		JLabel line = statLine(text, tooltip, color);
		if (spriteId > 0)
		{
			onSprite(spriteId, img ->
			{
				line.setIcon(Ui.icon(img, 14));
				line.setIconTextGap(4);
				line.revalidate();
			});
		}
		return line;
	}

	/** A stat line with a PAINTED classic icon in the fixed column. */
	private static JLabel paintedStatLine(String text, String tooltip, Color color,
		Icon icon)
	{
		JLabel line = statLine(text, tooltip, color);
		line.setIcon(new StatIcons.FixedWidthIcon(icon));
		line.setIconTextGap(2);
		return line;
	}

	/** A column stat line led by an async item image, scaled to 16px and
	 * seated in the fixed-width column (the coin stack rides this too). */
	private static JLabel statImageLine(AsyncBufferedImage img, String text, String tooltip, Color color)
	{
		JLabel line = statLine(text, tooltip, color);
		onImage(img, () ->
		{
			line.setIcon(new StatIcons.FixedWidthIcon(new ImageIcon(
				img.getScaledInstance(-1, 16, Image.SCALE_SMOOTH))));
			line.setIconTextGap(2);
			line.revalidate();
		});
		return line;
	}

	/** The async-image contract: paint now with whatever the image holds,
	 * and again on the EDT once it has loaded. */
	private static void onImage(AsyncBufferedImage img, Runnable paint)
	{
		Runnable seat = () -> SwingUtilities.invokeLater(paint);
		seat.run();
		img.onLoaded(seat);
	}

	/** Seats a game sprite on the label once it loads (size 0 = as drawn). */
	private void seatSprite(JLabel target, int sprite, int size)
	{
		if (sprite > 0)
		{
			onSprite(sprite, img -> target.setIcon(size > 0 ? Ui.icon(img, size) : new ImageIcon(img)));
		}
	}

	/** The async-sprite contract: the paint runs on the EDT once the
	 * sprite has loaded (nothing without a sprite manager). */
	private void onSprite(int spriteId, Consumer<BufferedImage> paint)
	{
		if (spriteManager != null)
		{
			spriteManager.getSpriteAsync(spriteId, 0, img -> SwingUtilities.invokeLater(() -> paint.accept(img)));
		}
	}

	/** A 24px inventory cell: the item image (quantity overlay when
	 * qty > 0) scaled down so a full trip fits the row. */
	private JPopupMenu supplyMenu(String category, List<Map<String, Object>> options)
	{
		JPopupMenu menu = new JPopupMenu();
		Ui.item(menu, "Detect best", () -> commands.send("set-supply-override",
			Map.of("category", category, "choice", "DETECT")));
		Ui.item(menu, "None", () -> commands.send("set-supply-override",
			Map.of("category", category, "choice", "NONE")));
		for (Map<String, Object> option : options)
		{
			String key = Model.str(option, "key");
			Ui.item(menu, Model.str(option, "name"),
				() -> commands.send("set-supply-override",
					Map.of("category", category, "choice", key)));
		}
		return menu;
	}

	/** The poison plan beside prayer and boost (Andrew 2026-09-08: "a more
	 * obvious spot"): the Cure Me spell, the chosen potion or the prayer
	 * book, "-" for none; click opens the anti-venom chooser. */
	private JLabel poisonPlate(Map<String, Object> mob, boolean bis)
	{
		String mode = Model.str(mob, "antivenom");
		String category = Model.str(mob, "cureCategory");
		Map<String, Object> chosen = byCategory(supplies, category);
		boolean none = "NONE".equals(mode);
		String name = chosen != null ? Model.str(chosen, "name")
			: none ? "none" : "Detect best - none banked";
		JLabel plate = plate("Poison", name, bis,
			() -> supplyMenu(category, Model.list(byCategory(Model.list(pageWide, "supplyCatalog"), category), "options")));
		if ("CURE_ME".equals(mode))
		{
			plate.setIcon(cachedSprite(SpriteID.SPELL_CURE_ME));
		}
		else if (!none)
		{
			// Detect best with nothing banked (field 2026-09-09: the grey
			// dash read as an empty box): the potion, crossed out.
			AsyncBufferedImage img = itemManager.getImage(chosen != null ? Model.id(chosen, "itemId")
				: "antipoison".equals(category) ? 5952 : 12913);
			boolean missing = chosen == null;
			onImage(img, () -> plate.setIcon(missing
				? Ui.crossedOut(img, 22) : new ImageIcon(img.getScaledInstance(-1, 22, Image.SCALE_SMOOTH))));
		}
		else
		{
			plate.setText("-");
			plate.setForeground(DIM);
		}
		return plate;
	}

	/** Dragonfire protection beside the poison plan (Andrew 2026-09-09:
	 * "follow suit"): the shield the set must carry, the antifire potion
	 * or the super antifire; click picks. Replaces the chip-row pill. */
	private JLabel firePlate(boolean bis)
	{
		String[] names = {Ui.text("fire.shield"), "Antifire potion", "Super antifire"};
		return optionPlate("Dragonfire", names, names, new int[]{11283, 2452, 21978},
			bis ? 2 : Model.id(pageParams, "antifireMode"), bis,
			Ui.text("fire.detect.tip"), "detect-antifire", i -> commands.set("antifireMode", i));
	}

	/** The fire-spell rune assumption (Kendyy, Discord 2026-10-19): sunfire
	 * runes give the spell a minimum hit of a tenth of its max. Seeded
	 * from ownership at each selection; the ceiling always assumes them. */
	private JLabel sunfirePlate(boolean bis)
	{
		return optionPlate("Fire spell runes", new String[]{Ui.text("sunfire.on"), "fire runes"},
			new String[]{"Sunfire runes", "Fire runes"}, new int[]{28929, 554},
			bis || Model.flag(pageParams, "sunfireRunes") ? 0 : 1, bis,
			"Sunfire runes when you own any", "detect-sunfire", i -> commands.set("sunfireRunes", i == 0));
	}

	/** A plate that picks one of a few items: the picked item's image and
	 * name in the tooltip, the menu "Detect best" (the detect command) then
	 * every option by its label, the pick handed over as its index. */
	private JLabel optionPlate(String what, String[] names, String[] labels, int[] items, int picked,
		boolean bis, String detectTip, String detectCommand, IntConsumer pick)
	{
		JLabel plate = plate(what, names[picked], bis, () ->
		{
			JPopupMenu menu = new JPopupMenu();
			Ui.item(menu, "Detect best", detectTip, () -> commands.send(detectCommand));
			for (int i = 0; i < labels.length; i++)
			{
				int value = i;
				Ui.item(menu, labels[i], () -> pick.accept(value));
			}
			return menu;
		});
		seatItem(plate, items[picked]);
		return plate;
	}

	/** A header plate: a 24px square, "<what>: <name>" with the click hint
	 * on the Yours side, where its menu (built once) drops from it. */
	private static JLabel plate(String what, String name, boolean bis, Supplier<JPopupMenu> menu)
	{
		JLabel plate = square(24);
		plate.setToolTipText(what + ": " + name + (bis ? "" : " - click to change"));
		if (!bis)
		{
			JPopupMenu built = menu.get();
			Ui.onClick(plate, () -> Ui.show(built, plate));
		}
		return plate;
	}

	/** The first row of the given category, or null. */
	private static Map<String, Object> byCategory(List<Map<String, Object>> rows, String category)
	{
		for (Map<String, Object> row : rows)
		{
			if (category.equals(Model.str(row, "category")))
			{
				return row;
			}
		}
		return null;
	}

	/** An item image scaled into a 24px frame (the raw 36x32 overflowed it,
	 * field 2026-08-21); the onLoaded re-seat is the async-image contract. */
	private void seatItem(JLabel target, int itemId)
	{
		AsyncBufferedImage img = itemManager.getImage(itemId);
		onImage(img, () -> target.setIcon(new ImageIcon(img.getScaledInstance(-1, 22, Image.SCALE_SMOOTH))));
	}

	private JLabel smallItemCell(int itemId, String tooltip)
	{
		JLabel cell = square(26);
		cell.setToolTipText(tooltip);
		AsyncBufferedImage img = itemManager.getImage(itemId);
		onImage(img, () -> cell.setIcon(Ui.icon(img, 24)));
		return cell;
	}

	private static JLabel statLine(String text, String tooltip, Color color)
	{
		JLabel line = Ui.small(text, color);
		line.setToolTipText(tooltip);
		line.setAlignmentX(Component.LEFT_ALIGNMENT);
		return line;
	}

	/** A bank-action toggle in the classic look (flat, rounded, accent on
	 * select): selecting it sends the command with the args built then;
	 * deselecting sends it empty, which clears. */
	private JToggleButton bankToggle(String text, String tip, String command, Supplier<Map<String, Object>> args)
	{
		JToggleButton button = new JToggleButton(text);
		button.setToolTipText(tip);
		button.setFocusable(false);
		button.setContentAreaFilled(false);
		button.setForeground(LIGHT);
		button.setBorder(new RoundedBorder(ColorScheme.MEDIUM_GRAY_COLOR, 3, 10));
		button.addItemListener(e ->
		{
			boolean on = button.isSelected();
			button.setForeground(on ? Color.WHITE : LIGHT);
			button.setBorder(new RoundedBorder(on ? ACCENT
				: ColorScheme.MEDIUM_GRAY_COLOR, 3, 10));
		});
		button.addActionListener(e -> commands.send(command, button.isSelected() ? args.get() : Map.of()));
		return button;
	}

	private static JLabel styleHeader(String style, Map<String, Object> node)
	{
		String name = Names.cap(style);
		double factor = Model.num(node, "multiFactor");
		if (factor > 0)
		{
			name += factor == Math.rint(factor) ? String.format(" x%d", (int) factor) : String.format(" x%.2f", factor);
		}
		JLabel label = new JLabel(name);
		label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize() - 1f));
		String boost = Model.str(node, "boostLabel");
		if (boost != null)
		{
			label.setToolTipText("Assumes: " + boost);
		}
		return label;
	}

	private JPanel side(Map<String, Object> card, boolean bis, double thrallsDps,
		Map<String, Object> mob, String tab, Map<String, Object> ship)
	{
		JPanel panel = Ui.column(CARD);
		if ("magic".equals(tab) && !bis)
		{
			String pinnedSpell = Model.str(mob, "pinnedSpell");
			boolean pinned = pinnedSpell != null && !pinnedSpell.isEmpty();
			String shownSpell = pinned ? pinnedSpell : Model.str(card, "spell");
			Map<String, Object> spellSprites = Model.map(assumeOptions, "spellSprites");
			int shownSprite = shownSpell == null ? -1 : Model.id(spellSprites, shownSpell);
			JPanel spellRow = Ui.darker(new FlowLayout(FlowLayout.CENTER, 4, 0));
			JLabel spellIcon = square(22);
			if (shownSprite > 0)
			{
				spellIcon.setIcon(cachedSprite(shownSprite));
			}
			else
			{
				seatSprite(spellIcon, SpriteID.TAB_MAGIC, 18);
			}
			String lock = Model.str(pageParams, "spellbookLock");
			String lockText = Model.flag(mob, "cureMe") ? " - lunar book (Cure Me)"
				: lock == null || lock.isEmpty() ? "" : " - " + lock + " book";
			JLabel spellName = Ui.small((shownSpell == null ? "Auto spell"
				: shownSpell + (pinned ? " (pinned)" : "")) + lockText, TEXT);
			MouseAdapter openSpells = new MouseAdapter()
			{
				public void mouseClicked(MouseEvent e)
				{
					// The SPELLBOOK grid (the prayer-book pattern - the list
					// form scrolled off the screen): sprite cells six wide,
					// names in tooltips, the pick on the accent plate.
					JPopupMenu menu = new JPopupMenu();
					menu.add(Ui.check("Auto spell (best castable)", !pinned,
						() -> commands.send("set-pinned-spell", Map.of("name", ""))));
					menu.addSeparator();
					// The spellbook lock (Apathy, Discord 2026-09-06: "use to be
					// a way to set a specific spellbook"): any book, or one
					// of the four. A book with no autocast spell (Lunar) leaves
					// the card to the powered staves.
					JPanel books = rack(5);
					String current = lock == null ? "" : lock;
					for (String option : new String[]{"", "standard", "ancient", "lunar", "arceuus"})
					{
						boolean any = option.isEmpty();
						books.add(pickCell(menu, any ? null : cachedSprite(bookSprite(option)),
							any ? "Any book (auto)" : "Lock to the " + option + " book",
							option.equals(current), 26,
							() -> commands.set("spellbookLock", option)));
					}
					menu.add(books);
					menu.addSeparator();
					JPanel book = rack(6);
					for (Object spell : Model.list2(pageWide, "spells"))
					{
						String option = String.valueOf(spell);
						boolean picked = option.equals(pinnedSpell);
						int sprite = Model.id(spellSprites, option);
						// The picker cell, with "pinned" where the pickers say "assumed".
						JLabel cell = pickCell(menu, sprite > 0 ? cachedSprite(sprite) : null, option, picked, 26,
							() -> commands.send("set-pinned-spell", Map.of("name", option)));
						cell.setToolTipText(option + (picked ? " (pinned)" : ""));
						book.add(cell);
					}
					menu.add(book);
					Ui.show(menu, (Component) e.getSource());
				}
			};
			for (JLabel part : new JLabel[]{spellIcon, spellName})
			{
				spellRow.add(part);
				part.setToolTipText("The autocast spell - click to pin one");
				part.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				part.addMouseListener(openSpells);
			}
			panel.add(left(spellRow));
		}
		// The classic card centre: the cross LEFT, the stat column RIGHT.
		JPanel center = Ui.darker(new FlowLayout(FlowLayout.LEFT, 8, 2));
		center.add(gearGrid(card, bis, mob, thrallsDps));
		JPanel statColumn = Ui.column(CARD);
		Map<String, Object> incoming = Model.map(card, "incoming");
		Map<String, Object> shipIncoming = Model.map(ship, "incoming");
		int maxHit = Model.id(card, "maxHit");
		statColumn.add(paintedStatLine("" + maxHit, "Max hit " + maxHit, TEXT,
			new StatIcons.SizedIcon("hitsplat", 12)));
		statColumn.add(paintedStatLine(Math.round(Model.num(card, "accuracy") * 100) + "%",
			"Hit chance", TEXT, new StatIcons.SizedIcon("crosshair", 13)));
		if (shipIncoming != null)
		{
			// Sea mobs hit the BOAT (REQ-SC-15): the number is ship damage
			// taken for the picked keel, protection prayers do not apply,
			// and clicking the line picks the keel (Andrew: "a drop down
			// next to the dtps value").
			String keel = Model.str(shipIncoming, "keel");
			int keelMax = Model.id(shipIncoming, "maxHit");
			String keelName = "none".equals(keel) ? "no keel" : Names.cap(keel) + " keel";
			// As terse as the land incoming line: the column fits ~12
			// characters (designer pass 2026-09-02: "~1.0 (max 5, Rune)"
			// clipped); the keel lives in the tooltip and the click menu.
			JLabel shipLine = statIconLine(
				shipDamageText(Model.num(shipIncoming, "dtps"), keelMax),
				keelMax == 0
					? String.format(Ui.text("ship.nohit"), keelName)
					: String.format(Ui.text("ship.hit"), keelName, keelMax),
				TEXT, -1);
			shipLine.setForeground(SEA);
			Icon sail = sailingIconSmall();
			if (sail != null)
			{
				shipLine.setIcon(sail);
				shipLine.setIconTextGap(4);
			}
			Ui.onClick(shipLine, () ->
			{
				JPopupMenu menu = new JPopupMenu();
				for (String option : NavalCombat.keels())
				{
					menu.add(Ui.check("none".equals(option) ? "No keel" : Names.cap(option) + " keel",
						option.equals(keel),
						() -> commands.set("shipKeel", option)));
				}
				Ui.show(menu, shipLine);
			});
			statColumn.add(shipLine);
		}
		else if (incoming != null)
		{
			// Classic honesty rule: an unmodeled monster's DTPS reads "?" -
			// silence must read as unknown, never safe.
			String[] line = dtpsLine(incoming);
			// The protect prayer's own sprite IS the pray call (classic).
			int protectSprite = "none".equals(Model.str(incoming, "coverage"))
				? -1 : Model.id(incoming, "protectSprite");
			statColumn.add(statIconLine(line[0], line[1], TEXT, protectSprite));
			// The defensive mode's DPS cost, terse for the ~12-character
			// column; the full trade rides the tooltip.
			Map<String, Object> trade = Model.map(card, "modeTrade");
			if (trade != null)
			{
				String mode = "TANKY".equals(Model.str(trade, "mode")) ? "Tanky" : "Bal.";
				statColumn.add(statIconLine(Model.str(trade, "reason") != null ? mode + " =max"
						: mode + " -" + Model.id(trade, "dpsLossPct") + "%",
					modeLine(trade), new Color(0x9FD39F), -1));
			}
		}
		Map<String, Object> stats = Model.map(card, "stats");
		if (stats != null)
		{
			statColumn.add(statIconLine("+" + Model.id(stats, "prayer"),
				"Prayer bonus", TEXT, SpriteID.SKILL_PRAYER));
		}
		String atkType = Model.str(card, "attackType");
		if (atkType != null)
		{
			// "crush (controlled)" -> "Crush", stance in the hover.
			int paren = atkType.indexOf(" (");
			String styleWord = paren > 0 ? atkType.substring(0, paren) : atkType;
			String stance = paren > 0
				? atkType.substring(paren + 2).replace(")", "") : null;
			statColumn.add(statLine(Names.cap(styleWord),
				stance == null ? "Attack style the numbers use"
					: "Attack style the numbers use - " + stance + " stance",
				TEXT));
		}
		Map<String, Object> internalAmmo = Model.map(card, "internalAmmo");
		if (internalAmmo != null)
		{
			JLabel dartLine = statImageLine(itemManager.getImage(Model.id(internalAmmo, "id")),
				Model.str(internalAmmo, "name"), Ui.text("dart.tip"), TEXT);
			Set<Integer> excludedIds = new HashSet<>();
			Map<String, Object> pageCounts = Model.map(pageWide, "counts");
			for (Map<String, Object> ex : Model.list(pageCounts, "excludedItems"))
			{
				excludedIds.add(Model.id(ex, "id"));
			}
			int loadedId = Model.id(internalAmmo, "id");
			JPopupMenu dartMenu = new JPopupMenu();
			for (Map<String, Object> tier : Model.list(pageWide, "dartTiers"))
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
			statColumn.add(statImageLine(itemManager.getImage(Model.id(quiverAmmoNode, "id")),
				Model.str(quiverAmmoNode, "name"), "Carried in the quiver", TEXT));
		}
		Map<String, Object> riskNode = Model.map(card, "risk");
		if (riskNode != null)
		{
			JLabel riskLine = statLine("Risk " + Gp.formatShort(
				(int) Math.min(Integer.MAX_VALUE, Model.num(riskNode, "riskGp"))),
				"", new Color(0xDC7878));
			riskLine.setToolTipText(Ui.tip(
				Ui.list("Kept on death:", Model.list2(riskNode, "kept")),
				Ui.list("<br>Lost:", Model.list2(riskNode, "lost"))));
			statColumn.add(riskLine);
		}
		int upgradeCost = Model.id(card, "purchaseCost");
		if (upgradeCost > 0)
		{
			// The coin stack at COLUMN scale: the stack-tier sprite, but
			// scaled to the stat icons' 16px and seated in the same
			// fixed-width column so every value shares one left edge
			// (design note 2026-08-15: the raw 32px item image dwarfed
			// the painted icons).
			statColumn.add(statImageLine(itemManager.getImage(995, 10_000, false), Gp.formatShort(upgradeCost),
				Ui.text("cost.tip"), TEXT));
		}
		if (stats != null)
		{
			Map<String, Object> off = Model.map(stats, "offensive");
			Map<String, Object> def = Model.map(stats, "defensive");
			// One STATS icon, everything in the hover (field report
			// 2026-08-15: the compact lines were cut off and unreadable)
			// - a proper equipment-stats table, offensive and defensive
			// for every stat.
			String fullBonuses = String.format(Ui.text("stats.table"),
				Model.id(off, "stab"), Model.id(off, "slash"), Model.id(off, "crush"),
				Model.id(off, "magic"), Model.id(off, "ranged"),
				Model.id(def, "stab"), Model.id(def, "slash"), Model.id(def, "crush"),
				Model.id(def, "magic"), Model.id(def, "ranged"),
				Model.id(stats, "strength"), Model.id(stats, "rangedStrength"),
				Model.id(stats, "magicDamage"), Model.id(stats, "prayer"));
			JLabel statsLine = statIconLine("Stats", fullBonuses, TEXT,
				SpriteID.TAB_COMBAT);
			// The line LOOKS like its clickable neighbours, so it answers a
			// click with the same table the hover shows (field report
			// 2026-08-31: "clicking the stats button doesn't do anything").
			final String statsHtml = fullBonuses;
			Ui.onClick(statsLine, () ->
			{
				JPopupMenu pop = new JPopupMenu();
				JLabel body = new JLabel(statsHtml);
				body.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
				pop.add(body);
				Ui.show(pop, statsLine);
			});
			statColumn.add(statsLine);
		}
		List<Object> bonuses = Model.list2(card, "counted");
		if (!bonuses.isEmpty())
		{
			// Situational bonuses live IN the column (field report
			// 2026-08-14: 'slayer helm shown outside the 2x5') - the
			// count on the line, every bonus named in the tooltip.
			statColumn.add(statLine("+" + bonuses.size() + " bonus"
				+ (bonuses.size() == 1 ? "" : "es"),
				Ui.tip(Ui.list("Counted for this set:", bonuses)), ACCENT));
		}
		center.add(statColumn);
		panel.add(left(center));
		// The open slot's ladder strip sits right under the grid (Yours only).
		String openSlot = bis ? "" : ladderSlotOpen();
		Map<String, Object> openGear = Model.map(card, "gear");
		// The spec cell's strip hangs off the card's spec node, not the gear.
		Map<String, Object> openNode = openSlot.isEmpty() ? null
			: "spec".equals(openSlot) ? Model.map(Model.map(card, "spec"), "weapon")
			: Model.map(openGear, openSlot);
		Map<String, Object> openLadder = Model.map(openNode, "ladder");
		if (openLadder != null)
		{
			Map<String, Object> wornColumn = new LinkedHashMap<>(openNode);
			wornColumn.put("base", Model.str(openLadder, "base"));
			wornColumn.put("base2", Model.str(openLadder, "base2"));
			panel.add(centered(ladderStrip(openLadder, wornColumn, openSlot, nerAvailable())));
		}
		return panel;
	}

	/** The worn-equipment silhouette (5 rows x 3): blank corners, the
	 * spec weapon in the empty cell left of the legs, quiver ammo in
	 * the corner right of the legs - the classic panel's iconic grid
	 * (BankLayout lays the bank out from the same rows). */
	static final String[] CROSS = {
		null, "head", null,
		"cape", "neck", "ammo",
		"weapon", "body", "shield",
		"#spec", "legs", "#quiver",
		"hands", "feet", "ring",
	};

	private JPanel gearGrid(Map<String, Object> card, boolean bis, Map<String, Object> mob, double thrallsDps)
	{
		JPanel grid = Ui.darker(new GridLayout(5, 3, 2, 2));
		Map<String, Object> gear = Model.map(card, "gear");
		Map<String, Object> quiver = Model.map(card, "quiverAmmo");
		for (String slot : CROSS)
		{
			Map<String, Object> item =
				"#spec".equals(slot) ? Model.map(Model.map(card, "spec"), "weapon")
				: "#quiver".equals(slot) ? quiver
				: slot == null ? null : Model.map(gear, slot);
			if (item == null)
			{
				// Empty slots are truly EMPTY (field ask 2026-08-21,
				// superseding the 2026-08-15 faint dot): a sized blank
				// keeps the cross geometry, nothing draws.
				JLabel blank = new JLabel();
				blank.setPreferredSize(new Dimension(36, 36));
				grid.add(blank);
			}
			else
			{
				String slotName = "#spec".equals(slot) ? "spec"
					: "#quiver".equals(slot) ? "quiver" : slot;
				grid.add(itemCell(item, slotName, bis, card, mob, thrallsDps));
			}
		}
		JPanel holder = Ui.darker(new FlowLayout(FlowLayout.LEFT, 0, 2));
		holder.add(grid);
		return holder;
	}

	/** The classic storage palette - only FETCH-TRIP sources have an
	 * entry, so at-hand gear can never draw a dot. */
	private static final Map<String, Color> SOURCE_COLORS = new LinkedHashMap<>();

	static
	{
		JsonObject colours = JsonResources.objectOrThrow("source_colours.json");
		for (String source : colours.keySet())
		{
			if (!source.startsWith("_"))
			{
				SOURCE_COLORS.put(source, new Color(Integer.parseInt(colours.get(source).getAsString(), 16)));
			}
		}
	}

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
	private static final Color CELL_BG = new Color(0x323232);
	private static final Color BORDER_BIS = new Color(0xA89458);
	private static final Color BORDER_SPEC = new Color(0x78BEF0);
	private static final Color BORDER_PLAIN = new Color(0x464646);
	private static final Color BORDER_UNOWNED = new Color(0x649164);

	/** card and mob feed the spec cell's reconciliation ledger and pin menu,
	 * and every cell's pin badge and "needed" note. */
	private JLabel itemCell(Map<String, Object> item, String slot, boolean bis, Map<String, Object> card,
		Map<String, Object> mob, double thrallsDps)
	{
		JLabel cell = square(36);
		String name = Model.str(item, "name");
		int id = Model.id(item, "id");
		boolean spec = "spec".equals(slot);
		boolean assumed = Model.flag(item, "assumed");
		boolean bisMatch = Model.flag(item, "bisMatch");
		// Border language (classic, field note 2026-08-05: every colour
		// has to EARN its cell): gold = owned and best-available, green =
		// assumed (simmed / unowned on Yours), blue = the spec cell,
		// grey otherwise.
		Color border = spec ? BORDER_SPEC : assumed ? BORDER_UNOWNED : bisMatch ? BORDER_BIS : BORDER_PLAIN;
		int itemPrice = Model.id(item, "price");
		String priceNote = Model.flag(item, "owned") || itemPrice <= 0
			? "" : " - " + Gp.formatShort(itemPrice) + " gp";
		// The reconciliation breakdown on the spec cell (field ask
		// 2026-08-21): our shown number folds spec + thralls; the wiki
		// calc shows the bare set - spell out the sum so the two always
		// reconcile. tipBody is shared with the storage/fate overlay.
		String tipBody;
		Map<String, Object> specNode = Model.map(card, "spec");
		int specWeaponId = Model.id(Model.map(specNode, "weapon"), "id");
		boolean specPinned = specWeaponId != 0 && Model.id(mob, "pinnedSpec") == specWeaponId;
		if (spec)
		{
			// The same ledger the Wiki calc chip's tooltip wears (field
			// ask 2026-08-21) - one fact per row. The spec row shows even
			// at zero here: this cell IS the spec weapon.
			tipBody = "<b>" + name + " (spec)</b>"
				+ (specPinned ? " <font color='#969696'>pinned</font>" : "") + priceNote
				+ "<br><br>"
				+ Ui.ledger(Model.num(card, "dps"), "what the wiki calc shows", Model.num(specNode, "dpsAdded"),
					true, thrallsDps);
		}
		else
		{
			tipBody = name + " (" + slot + ")"
				+ (!bis && assumed && hasId(Model.list2(mob, "requiredGear"), id)
					? " - " + Ui.text("cell.needed")
				: assumed ? " - assumed (not owned)"
				: bisMatch ? " - best available" : "") + priceNote;
		}
		cell.setToolTipText("<html>" + tipBody + "</html>");
		Runnable exclude = () -> commands.send("toggle-exclusion", Map.of("itemId", id, "label", name));
		if (spec && !bis && specWeaponId != 0)
		{
			// The pin lives ON the cell now (the spec text line retired).
			JPopupMenu specMenu = new JPopupMenu();
			Ui.item(specMenu, specPinned ? "Unpin spec" : "Pin as spec for this mob",
				() -> commands.send("set-pinned-spec",
					Map.of("itemId", specPinned ? 0 : specWeaponId)));
			Ui.item(specMenu, "Exclude " + name, exclude);
			cell.setComponentPopupMenu(specMenu);
		}
		cell.setOpaque(true);
		cell.setBackground(CELL_BG);
		cell.setBorder(BorderFactory.createLineBorder(border));
		itemManager.getImage(id).addTo(cell);
		Color dot = sourceColor(Model.str(item, "source"));
		String fate = Model.str(item, "fate");
		boolean pinnedHere = !bis && !spec && pinnedOn(Model.list(mob, "mobPins"), slot, id);
		if (dot != null || fate != null || pinnedHere)
		{
			String where = Model.str(item, "source");
			cell.setToolTipText("<html>" + tipBody
				+ (pinnedHere ? Ui.text("cell.pinnedhere") : "")
				+ (where == null ? "" : "<br>stored in " + where)
				+ ("kept".equals(fate) ? "<br>protected on death"
					: "lost".equals(fate)
						? "<br>lost on death (" + Gp.format((int) Math.min(Integer.MAX_VALUE,
							Model.num(item, "fateGp"))) + " gp)" : "")
				+ "</html>");
			cell.setUI(new javax.swing.plaf.basic.BasicLabelUI()
			{
				public void paint(Graphics g, JComponent c)
				{
					super.paint(g, c);
					Graphics2D g2 = Ui.smooth(g);
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
							? new Color(0x78C878) : new Color(0xDC6E6E));
						g2.fillRect(3, 3, 4, 4);
					}
					if (pinnedHere)
					{
						// The pin badge, top-right: a gold head and its point.
						g2.setColor(PIN_BADGE);
						g2.fillOval(c.getWidth() - 9, 3, 6, 6);
						g2.drawLine(c.getWidth() - 7, 8, c.getWidth() - 9, 11);
					}
					g2.dispose();
				}
			});
		}
		if (!bis && Model.map(item, "ladder") != null)
		{
			// A left click opens (or closes) this slot's ladder strip under
			// the grid; right click keeps its menu.
			boolean open = slot.equals(ladderSlotOpen());
			cell.setToolTipText(cell.getToolTipText().replace("</html>",
				String.format(Ui.text("cell.swaps"), (open ? "hide" : "show"))));
			Ui.onLeftClick(cell, () -> commands.set("ladderSlot", open ? "" : slot));
		}
		if (spec && cell.getComponentPopupMenu() != null)
		{
			// The spec cell keeps its own menu: the slot menu below pins
			// and unpins GEAR slots, and "spec" is none (it replaced the
			// pin-as-spec menu before 2026-10-22).
			return cell;
		}
		JPopupMenu menu = new JPopupMenu();
		Ui.item(menu, "Exclude " + name, exclude);
		if (bis)
		{
			Ui.item(menu, "Sim as owned", "Pretend you own " + name + " and recompute your side",
				() -> commands.send("toggle-sim",
				Map.of("itemId", id, "label", name)));
		}
		else if (!"quiver".equals(slot))
		{
			Ui.item(menu, "Exclude for this mob", "Exclude " + name + " for this mob only (all sets)",
				() -> commands.send("exclude-for-mob",
				Map.of("itemId", id, "scope", "ALL", "label", name)));
			Ui.item(menu, "Sim for this mob (search)...", Ui.text("menu.simmob.tip"),
				searchItem("Sim for this mob", "sim-for-mob"));
			// The classic GLOBAL sibling (field report 2026-08-21: it fell
			// off in the merge-back - only the per-mob scope survived).
			Ui.item(menu, "Sim for all mobs (search)...", Ui.text("menu.simall.tip"),
				searchItem("Sim as owned", "toggle-sim"));
			Ui.item(menu, "Pin " + name, "Force this item into the " + slot + " slot for this mob (all sets)",
				() -> commands.send("pin", Map.of("slot", slot, "itemId", id)));
			Ui.item(menu, "Pin another item (search)...", () -> picker.search("Pin in " + slot,
				(pickedId, pickedName) -> commands.send("pin", Map.of("slot", slot, "itemId", pickedId))));
			Ui.item(menu, "Unpin " + slot, () -> commands.send("unpin", Map.of("slot", slot)));
			if ("hands".equals(slot))
			{
				// The slayer-task bracelet setting, reachable from the slot
				// it fills (miko 2026-10-17; the settings panel has it too).
				String current = Model.str(Model.map(pageWide, "counts"), "taskBracelet");
				for (String[] choice : new String[][]{{"NONE", "none"},
					{"EXPEDITIOUS_BRACELET", "Expeditious bracelet"},
					{"BRACELET_OF_SLAUGHTER", "Bracelet of slaughter"}})
				{
					menu.add(Ui.check("On slayer tasks: " + choice[1], choice[0].equals(current),
						() -> commands.send("set-task-bracelet", Map.of("choice", choice[0]))));
				}
			}
		}
		cell.setComponentPopupMenu(menu);
		return cell;
	}

	/** One rune stack's name and the reason it rides, merged per rune type. */
	private static void noteRune(Map<Integer, String> names, Map<Integer, StringBuilder> why,
		Map<String, Object> rune, String reason)
	{
		int id = Model.id(rune, "id");
		names.put(id, Model.str(rune, "name"));
		why.computeIfAbsent(id, k -> new StringBuilder())
			.append(why.get(id).length() == 0 ? "" : "; ").append(reason);
	}

	/** A fixed-size label with its content centred - the cell every icon
	 * seat starts from. */
	private static JLabel square(int size)
	{
		JLabel cell = new JLabel();
		cell.setPreferredSize(new Dimension(size, size));
		cell.setHorizontalAlignment(SwingConstants.CENTER);
		return cell;
	}

	/** The picker grid's rack: n cells wide on the dark ground. */
	private static JPanel rack(int columns)
	{
		JPanel rack = Ui.darker(new GridLayout(0, columns, 2, 2));
		rack.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		return rack;
	}

	/** A square picker cell, lit when picked. */
	private static JLabel pickedCell(int size, boolean picked)
	{
		JLabel cell = square(size);
		cell.setOpaque(true);
		cell.setBackground(picked ? new Color(0x2e, 0x40, 0x2e) : ColorScheme.DARKER_GRAY_COLOR);
		cell.setBorder(BorderFactory.createLineBorder(picked ? ACCENT : ColorScheme.DARKER_GRAY_HOVER_COLOR));
		return cell;
	}

	private static JPanel left(JComponent inner)
	{
		JPanel row = Ui.darker(new BorderLayout());
		row.add(inner, BorderLayout.WEST);
		return row;
	}

	/** left()'s centred sibling (field asks 2026-08-20) - FlowLayout so
	 * the inner keeps its preferred size instead of stretching. */
	private static JPanel centre(JComponent inner)
	{
		JPanel row = Ui.darker(new FlowLayout(FlowLayout.CENTER, 0, 0));
		row.add(inner);
		return row;
	}
}
