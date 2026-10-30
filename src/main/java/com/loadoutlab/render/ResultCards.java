package com.loadoutlab.render;

import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import net.runelite.client.game.*;
import com.google.gson.JsonObject;
import com.loadoutlab.data.*;
import com.loadoutlab.ui.MonsterIcons;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.function.*;
import net.runelite.api.SpriteID;
import net.runelite.client.util.*;
import com.loadoutlab.data.NavalCombat.*;
import static com.loadoutlab.data.Names.*;
import static com.loadoutlab.data.NavalCombat.*;
import static com.loadoutlab.render.Model.*;
import static com.loadoutlab.render.Ui.*;
import static java.lang.Math.*;
import static javax.swing.BorderFactory.*;
import static net.runelite.client.ui.ColorScheme.*;
import javax.swing.plaf.basic.BasicLabelUI;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import static java.awt.BorderLayout.*;
import static java.lang.String.*;

// The first rich renderer over the render-model: per mob, per style,
// the Yours and Best-in-game sides as compact cards - dps headline,
// equipment icon grid (icons resolved through the Companion's own
// ItemManager; the model only carries ids), spec and incoming lines.
// This view grows toward the ported panel card-by-card; the model
// gains fields as this code asks for them.
@lombok.RequiredArgsConstructor
public class ResultCards {
    static final Color CARD = DARKER_GRAY_COLOR;
    final ItemManager itemManager;
    final SpriteManager spriteManager;
    final CommandSink commands;
    final ItemPicker picker;
    Map assumeOptions;
    Map pageWide;
    Map pageParams;
    // Small sailing skill icon for ship-eligible rows/markers ("naval" is
    // not player vernacular - the icon speaks instead).
    @lombok.Setter
    Supplier<BufferedImage> sailingIcon;

    // The trip breakdown's show/hide: a per-profile setting the plugin
    // owns; the card reads it and a click on the ledger flips it.
    BooleanSupplier breakdownShown = () -> true;
    Consumer<Boolean> breakdownToggle = shown -> {
    };

    public void setBreakdown(BooleanSupplier shown, Consumer<Boolean> toggle) {
        breakdownShown = shown;
        breakdownToggle = toggle;
    }

    @lombok.Setter
    BooleanSupplier degradableChip = () -> true;

    // The trio's fourth sigil (Mike, Discord 2026-09-07; Andrew 2026-09-08:
    // the text pill clipped): *N = N degrading items in the shown set,
    // amber when that gear is skipped for this mob.
    JButton degradesButton(Map mob, Map card) {
        int n = 0;
        Map gear = map(card, "gear");
        for (Object slot : gear == null ? List.of() : gear.values()) {
            if (slot instanceof Map && flag((Map) slot, "degrades")) n++;
        }
        boolean skip = flag(mob, "skipDegradable");
        if (!degradableChip.getAsBoolean() || (n == 0 && !skip)) return null;
        JButton button = sigilButton("*" + n, skip, skip ? new Color(0xE6B45A) : new Color(0x968C78),
            new Color(0xBE9646), skip ? text("degrade.skip") : n + " degrade or burn charges - click to skip them here");
        button.addActionListener(e -> commands.send("toggle-degradable"));
        return button;
    }

    // A trio sigil button: lit with its ring when it holds anything, muted
    // and ringless otherwise (the degrade sigil wears the same cut).
    static JButton sigilButton(String text, boolean lit, Color fg, Color ring, String tip) {
        var button = new JButton(text);
        button.setForeground(fg);
        button.setContentAreaFilled(false);
        button.setBorder(lit ? new RoundedBorder(ring, 2, 14) : createEmptyBorder(3, 15, 3, 15));
        tipText(button, tip);
        button.setFocusable(false);
        return button;
    }

    // A cannon-strip icon button: the item's image, a red ring when the
    // operate gate blocks it, the dark hover ring otherwise.
    JButton iconButton(int itemId, boolean blocked) {
        var button = new JButton(new ImageIcon(itemManager.getImage(itemId)));
        button.setContentAreaFilled(false);
        button.setFocusable(false);
        button.setBorder(new RoundedBorder(blocked ? new Color(0xC86464) : DARKER_GRAY_HOVER_COLOR, 2, 8));
        return button;
    }

    Icon sailingIconSmall() {
        Supplier<BufferedImage> supplier = sailingIcon;
        BufferedImage img = supplier == null ? null : supplier.get();
        return img == null ? null : icon(img, 14);
    }

    @lombok.Setter(lombok.AccessLevel.PACKAGE)
    JButton addMob;
    @lombok.Setter(lombok.AccessLevel.PACKAGE)
    JButton reload;

    // The tab the page is effectively showing: the explicit selection,
    // or the first style the first mob answers.
    static String effectiveTab(Map page) {
        String selected = str(firstParams(page), "selectedTab");
        for (Map entry : list(page, "entries")) {
            for (Map mob : list(entry, "mobs")) {
                Map styles = map(mob, "styles");
                if (styles == null) continue;
                if (selected != null && !selected.isEmpty() && styles.get(selected) != null) return selected;
                // No explicit pick: default to the RECOMMENDED style -
                // highest owned dps, kit-backed styles first (field report
                // 2026-08-14: Kraken opened on ranged with magic higher).
                String best = null;
                double bd = -1;
                boolean bkb = false;
                for (String style : (Iterable<String>) styles.keySet()) {
                    Map node = map(styles, style);
                    if (node == null) continue;
                    boolean kb = flag(map(node, "yours"), "kitBacked");
                    double dps = num(node, "tabDps");
                    if (best == null || (kb && !bkb) || (kb == bkb && dps > bd)) {
                        best = style;
                        bd = dps;
                        bkb = kb;
                    }
                }
                if (best != null) return best;
            }
        }
        return selected == null || selected.isEmpty() ? "melee" : selected;
    }

    static Map firstParams(Map page) {
        for (Map entry : list(page, "entries")) {
            Map params = map(entry, "params");
            if (params != null) return params;
        }
        return null;
    }

    List<Map> supplies = List.of();

    // The captures every render pass needs (page-level facts).
    void capture(Map page) {
        supplies = List.of();
        for (Map entryNode : list(page, "entries")) {
            List<Map> found = list(entryNode, "supplies");
            if (!found.isEmpty()) supplies = found;
        }
        assumeOptions = map(page, "assumeOptions");
        pageWide = page;
        pageParams = firstParams(page);
    }

    // The MOB LIST for the control section (Andrew's three-section
    // design, 2026-08-15): rounded padded rows + the +Mob button, alive
    // on pending pages too. Null when nothing is selected.
    JPanel renderRoster(Map page) {
        capture(page);
        String tab = effectiveTab(page);
        boolean bis = flag(pageParams, "viewingBis");
        int lens = id(pageParams, "lensIndex");
        for (Map entry : list(page, "entries")) {
            List<Map> mobs = list(entry, "mobs");
            if (mobs.isEmpty()) continue;
            double rosterThralls = num(map(entry, "thralls"), "dps");
            JPanel box = column(DARKER_GRAY_COLOR);
            int shownLens = min(max(lens, 0), mobs.size() - 1);
            for (int i = 0; i < mobs.size(); i++) {
                box.add(lensRow(mobs.get(i), tab, bis, i, mobs.size(), i == shownLens, rosterThralls));
                if (i < mobs.size() - 1) box.add(Box.createVerticalStrut(2));
            }
            if (addMob != null) {
                JPanel ar = clear(new FlowLayout(FlowLayout.LEFT, 0, 2));
                ar.add(addMob);
                if (reload != null) ar.add(reload);
                box.add(Box.createVerticalStrut(2));
                box.add(ar);
            }
            return box;
        }
        return null;
    }

    // The OUTPUT section: the shown card only - the mob list lives in
    // the control section now. Pending pages render nothing (the
    // animation owns the space).
    JPanel render(Map page) {
        capture(page);
        String tab = effectiveTab(page);
        boolean bis = flag(pageParams, "viewingBis");
        JPanel column = column(DARK_GRAY_COLOR);
        int lens = id(pageParams, "lensIndex");
        for (Map entry : list(page, "entries")) {
            if (flag(entry, "pending")) continue;
            double td2 = num(map(entry, "thralls"), "dps");
            List<Map> mobs = list(entry, "mobs");
            rosterView = mobs.size() > 1;
            trip = map(entry, bis ? "bisTripKillTime" : "tripKillTime");
            if (mobs.isEmpty()) continue;
            int shownLens = min(max(lens, 0), mobs.size() - 1);
            Map shown = mobs.get(shownLens);
            // Ship options follow the LENSED mob (REQ-SC-7): a land lens in
            // a mixed roster renders none of this; lensing back to the sea
            // mob brings it back with the params intact.
            Map ship = map(entry, "ship");
            boolean naval = flag(shown, "naval") && pageParams != null;
            // The card never hides (Andrew, v2): cannons live INSIDE it -
            // the 1x3 strip above the gear grid, the breakdown below.
            // Cannon dps folds into the shown numbers only while nobody
            // mans one; a manned trip's gear is not attacking.
            boolean manned = "cannon".equals(str(ship, "station"));
            double sd = naval && !manned ? num(ship, "dps") : 0;
            column.add(mobCard(shown, tab, bis, td2, sd, naval ? ship : null, naval));
            column.add(Box.createVerticalStrut(8));
        }
        return column;
    }

    // One switcher row, aligned in columns: the mob name (shortened to
    // fit) with its level, then a FIXED-WIDTH block - style icon, dps,
    // remove - so those line up down the whole list.
    JPanel lensRow(Map mob, String tab, boolean bis, int index,
        int rosterSize, boolean selected, double td2) {
        Map styles = map(mob, "styles");
        // The row reports ITS OWN recommendation (field report
        // 2026-08-14: rows showed the viewed tab's dps beside the
        // best-style icon) - the number belongs to the icon's style.
        String bestStyle = null;
        Map bs = null;
        double bd = 0;
        if (styles != null) {
            for (String style : (Iterable<String>) styles.keySet()) {
                Map styleNode = map(styles, style);
                Map side = map(styleNode, bis ? "bis" : "yours");
                if (side == null || !flag(side, "kitBacked")) continue;
                double value = num(styleNode, bis ? "bisTabDps" : "tabDps");
                if (value > bd) {
                    bd = value;
                    bestStyle = style;
                    bs = side;
                }
            }
        }
        Map shown = map(map(styles, tab), bis ? "bis" : "yours");
        boolean tabOnly = shown != null && !flag(shown, "kitBacked");
        // Rows and tab buttons COUNT THE SAME THINGS (field report
        // 2026-08-15): set + spec (in the tab facts) + thralls, and a
        // sea row's cannons (Andrew 2026-09-03).
        double ride = thrallsFor(bestStyle != null ? bs : shown, td2) + num(mob, "shipDps");
        String dps = bestStyle != null ? format("%.2f", bd + (bd > 0 ? ride : 0)) : shown == null ? "-"
            : format("%.2f%s", num(shown, "dps") + ride, tabOnly ? "*" : "");

        String rs = bestStyle;
        var row = new JPanel(new BorderLayout(4, 0));
        // Selection is an EDGE, not a box: the active mob wears a 3px
        // accent bar and a faint tint; the rest stay transparent.
        boolean sea = flag(mob, "naval");
        Color edge = sea ? SEA : ACCENT;
        row.setOpaque(selected);
        if (selected) {
            // A ship mob's selection wears the sea, not the accent green
            // (REQ-SC-16).
            row.setBackground(sea ? new Color(0x28323C) : new Color(0x2E382E));
        }
        row.setBorder(createCompoundBorder( createMatteBorder(0, selected ? 3 : 0, 0, 0, edge),
            createEmptyBorder(4, selected ? 7 : 10, 4, 4)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        String name = str(mob, "label");
        // The row carries NO numbers (field ask 2026-08-21: "too much
        // clutter") - the level-versioned label suffix strips, and the
        // card header below owns level and hp. The tooltip keeps the
        // full label for disambiguation.
        String cleanName = name == null ? "" : name.replaceFirst(" - lvl \\d+$", "");
        JLabel label = small(cleanName, selected ? edge : TEXT);
        if (monsterIcons != null && iconsEnabled.getAsBoolean()) {
            // The mob's wiki render rides the row; text-only until it
            // loads (or when the wiki has no picture for it).
            String mn = str(mob, "name");
            String mv = str(mob, "version");
            ImageIcon mi = monsterIcons.get(mn, mv, 20, () -> {
                ImageIcon ready = monsterIcons.get(mn, mv, 20, null);
                if (ready != null) {
                    label.setIcon(ready);
                    label.setIconTextGap(6);
                    label.revalidate();
                }
            });
            if (mi != null) {
                label.setIcon(mi);
                label.setIconTextGap(6);
            }
        }
        // The name yields first when the panel narrows - the columns to
        // its right keep their width, so they stay aligned.
        label.setMinimumSize(new Dimension(20, 18));
        tipText(label, "<html>" + name + (bestStyle != null ? "<br>" + dps + " dps with " + bestStyle + " (best in the trip kit)"
                : tabOnly ? text("roster.tabonly") : "") + text("roster.click"));
        Runnable pick = () -> {
            label.setForeground(ACCENT);
            // Switching mobs also switches to THAT mob's suggested
            // style (field ask 2026-08-15) - the card below always
            // opens on the row's own recommendation.
            commands.set("lensIndex", index);
            if (rs != null && !rs.equals(tab)) commands.set("selectedTab", rs);
        };
        onClick(label, pick);
        JLabel st = null;
        if (sea) {
            Icon sail = sailingIconSmall();
            if (sail != null) {
                st = new JLabel(sail);
                tipText(st, "Ship combat: fought from your boat");
            }
        }
        if (st != null) {
            JPanel ns = clear(new BorderLayout(4, 0));
            ns.add(label, CENTER);
            ns.add(st, EAST);
            row.add(ns, CENTER);
        } else row.add(label, CENTER);

        // The aligned right block: [icon dps] x - the icon rides right,
        // snug against its number.
        JPanel columns = clear(new BorderLayout(2, 0));

        JLabel icon = square(18);
        if (bestStyle != null && spriteManager != null) {
            seatSprite(icon, id(map(styles, bestStyle), "styleSprite"), 0);
            tipText(icon, "Best style: " + bestStyle);
        }
        JPanel id2 = clear(new FlowLayout(FlowLayout.RIGHT, 3, 0));
        id2.add(icon);

        JLabel dl2 = small(dps, selected ? Color.WHITE : new Color(0xAAAAAA));
        id2.add(dl2);
        columns.add(id2, CENTER);

        JLabel remove = xLabel("Remove " + name + " from this result", () -> commands.send("remove-mob", Map.of("index", index)));
        remove.setHorizontalAlignment(SwingConstants.CENTER);
        remove.setPreferredSize(new Dimension(14, 18));
        // Right-click on the row or its x: Move up / Move down (Andrew
        // 2026-09-02 - a menu, not drag handles: no Swing drag layer).
        var reorder = new MouseAdapter() {
            public void mousePressed(MouseEvent e) { maybeShow(e); }

            public void mouseReleased(MouseEvent e) { maybeShow(e); }

            void maybeShow(MouseEvent e) {
                if (!e.isPopupTrigger()) return;
                var menu = new JPopupMenu();
                if (index > 0) item(menu, "Move up", () -> commands.send("move-mob", Map.of("index", index, "delta", -1)));
                if (index < rosterSize - 1) item(menu, "Move down", () -> commands.send("move-mob", Map.of("index", index, "delta", 1)));
                if (menu.getComponentCount() > 0) menu.show(e.getComponent(), e.getX(), e.getY());
            }
        };
        row.addMouseListener(reorder);
        remove.addMouseListener(reorder);
        columns.add(remove, EAST);
        row.add(columns, EAST);
        return row;
    }

    // The brand accent (the mascot-era green lives on as a plain constant).
    static final Color ACCENT = new Color(0x8CC88C);
    // The card's greys, lightest to dimmest, and the sea-blue of ship rows.
    static final Color TEXT = new Color(0xC8C8C8);
    static final Color LIGHT = new Color(0xBEBEBE);
    static final Color MID = new Color(0xA0A0A0);
    static final Color DIM = new Color(0x8C8C8C);
    static final Color SEA = new Color(0x78AFD7);
    // The trio's sigil colours - {active, muted, active border} - shared
    // with the page-wide count pills: sims green, exclusions red, filters grey.
    static final Color[] SIM_PALETTE = {new Color(0x82C882), new Color(0x6E8C6E), new Color(0x5FA05F)};
    static final Color[] EXCLUDE_PALETTE = {new Color(0xDC7878), new Color(0x8C6E6E), new Color(0xAA5A5A)};
    static final Color[] FILTER_PALETTE = {new Color(0xBEBEBE), new Color(0x828282), new Color(0x969696)};

    // Async wiki thumbnails for the roster rows (null = text-only).
    MonsterIcons monsterIcons;
    BooleanSupplier iconsEnabled = () -> false;

    public void setMonsterIcons(MonsterIcons icons, BooleanSupplier enabled) {
        monsterIcons = icons;
        iconsEnabled = enabled;
    }

    boolean rosterView;
    // The group's combined kill time for the card being rendered; null on a single mob.
    Map trip;
    static final Color PIN_BADGE = new Color(0xD2B464);

    // Whether the mob's pins (slot + id) hold this item in this slot.
    static boolean pinnedOn(List<Map> pins, String slot, int id) {
        for (Map pin : pins) {
            if (slot.equals(str(pin, "slot")) && id(pin, "id") == id) return true;
        }
        return false;
    }

    // Whether a list of numbers (the mob's required gear) names the id.
    static boolean hasId(List<Object> ids, int id) {
        for (Object o : ids) {
            if (o instanceof Number && ((Number) o).intValue() == id) return true;
        }
        return false;
    }

    // thrallsDps = the page's thrall dps, shipDps = the lensed ship's: each
    // tab folds its OWN card's thralls (a stood-down card folds none) plus
    // the cannons.
    JPanel mobCard(Map mob, String tab, boolean bis, double td2,
        double sd, Map ship, boolean naval) {
        JPanel card = column(CARD);
        card.setBorder(createCompoundBorder( createLineBorder( bis ? ACCENT : DARKER_GRAY_HOVER_COLOR, 1, true),
            createEmptyBorder(8, 8, 8, 8)));
        var title = new JLabel(str(mob, "label"));
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        // Naval marker (REQ-SC-6): the output says this fight happens from
        // your boat. Rides the header's EAST seat beside hp, same muted
        // small-font treatment, sea-blue.
        JLabel nt = null;
        if (flag(mob, "naval")) {
            Icon sail = sailingIconSmall();
            nt = sail != null ? new JLabel(sail) : label("sea", SEA);
            tipText(nt, "Ship combat: fought from your boat");
        }
        int mobHp = id(mob, "hp");
        if (mobHp > 0 || nt != null) {
            // Title CENTER (yields when narrow), hp EAST (survives) -
            // WEST/CENTER clipped the title and pushed hp out entirely
            // (field screenshot 2026-08-21: 'Duke Sucellus (Awa... lvl 1').
            JPanel tr = darker(new BorderLayout(8, 0));
            title.setMinimumSize(new Dimension(20, 18));
            tr.add(title, CENTER);
            JPanel hpSeat = clear(new FlowLayout(FlowLayout.LEFT, 4, 2));
            if (nt != null) hpSeat.add(nt);
            if (mobHp > 0) {
                // hp rides the header, muted (field ask 2026-08-21).
                hpSeat.add(small(mobHp + "hp", DIM));
            }
            tr.add(hpSeat, EAST);
            card.add(tr);
        } else card.add(left(title));
        Map styles = map(mob, "styles");
        Map node = map(styles, tab);
        if (node == null) {
            card.add(left(new JLabel("No " + tab + " set for this mob")));
            return card;
        }
        // The side this card shows - every row below reads it.
        Map shown = map(node, bis ? "bis" : "yours");
        card.add(Box.createVerticalStrut(6));
        JPanel hr = darker(new FlowLayout(FlowLayout.CENTER, 4, 0));
        hr.add(styleHeader(tab, node));
        // Assume icons ride the header (the vertical-space rule): the
        // prayer sprite + boost potion icon, tooltip carries the words.
        Map assume = map(node, bis ? "bisAssume" : "assume");
        if (assume != null) {
            String text = str(assume, "text");
            // The icons ARE the pickers on the Yours side (field ask
            // 2026-08-14): click the wings to pick the prayer, the
            // potion to pick the boost. BiS icons stay informational.
            int ps = id(assume, "prayerSprite");
            var prayerIcon = new JLabel();
            prayerIcon.setPreferredSize(new Dimension(20, 20));
            tipText(prayerIcon, "Assumes: " + text + (bis ? "" : " - click to change the prayer"));
            // No prayer assumed -> the prayer book with a red X, so the
            // sentinel reads as "none" not "prayer on" (field ask
            // 2026-09-01). A real prayer shows its own sprite plainly.
            String prayerImage = str(assume, "prayerImage");
            boolean np = ps <= 0 && prayerImage == null;
            int shownPrayerSprite = np ? SpriteID.TAB_PRAYER : ps;
            if (prayerImage != null && ps <= 0) {
                prayerIcon.setIcon(icon(ImageUtil.loadImageResource(ResultCards.class, prayerImage), 18));
            } else onSprite(shownPrayerSprite, img -> prayerIcon.setIcon(np ? crossedOut(img, 18) : icon(img, 18)));
            if (!bis) onClick(prayerIcon, () -> show(prayerMenu(tab), prayerIcon));
            hr.add(prayerIcon);
            int boostItem = id(assume, "boostItem");
            var bic = new JLabel();
            bic.setPreferredSize(new Dimension(24, 24));
            tipText(bic, "Assumes: " + text + (bis ? "" : " - click to change the boost"));
            if (boostItem > 0) {
                // Scaled to the 24px frame - the raw 36x32 item image
                // overflowed it (field report 2026-08-21). The onLoaded
                // re-seat is the async-image contract.
                seatItem(bic, boostItem);
            } else {
                // No potion assumed (None/prayer-only): a muted vial
                // keeps the toggle reachable.
                bic.setText("-");
                bic.setForeground(DIM);
                bic.setHorizontalAlignment(SwingConstants.CENTER);
            }
            if (!bis) onClick(bic, () -> show(boostMenu(tab), bic));
            hr.add(bic);
            String a2 = str(mob, "antivenom");
            if (a2 != null && !a2.isEmpty()) hr.add(poisonPlate(mob, bis));
            if (flag(mob, "breathesFire")) hr.add(firePlate(bis));
            String cs = str(shown, "spell");
            if (cs != null && cs.startsWith("Fire ")) hr.add(sunfirePlate(bis));
        }
        hr.add(bookPlate(shown, mob));
        card.add(left(hr));
        // The cannon strip rides between the assume row and the gear grid
        // (Andrew's v2 vision): left cannon / shared ammo / right cannon,
        // each the boost-rack picker, operators inside the rack.
        if (naval && !bis && id(pageParams, "cannonCount") > 0) card.add(left(cannonStrip(ship)));
        if (shown == null) {
            // NEVER show the other side in this slot (field bug 2026-08-12:
            // a missing owned answer silently rendered the BiS set - "gear
            // that you don't have").
            card.add(left(new JLabel("No usable " + tab + " set")));
        } else {
            if (!flag(shown, "kitBacked") && rosterView) {
                // The vents rule (2026-08-06): a tab-only fallback must
                // never read as the trip's answer.
                card.add(left(small(text("card.tabonly"), new Color(0xDCAA5A))));
            }
            String standDown = str(shown, "arceuusStandDown");
            if (standDown != null) {
                // Andrew 2026-10-19: Water Surge with thralls on is not a
                // castable setup; the card says so and folds no thrall.
                JLabel caveat = small("Thralls and Death Charge stand down: " + standDown, new Color(0xDCAA5A));
                tipText(caveat, text("standdown.tip"));
                card.add(left(caveat));
            }
            card.add(left(side(shown, bis, thrallsFor(shown, td2) + sd, mob, tab, ship)));
        }
        if (naval && ship != null && !list(ship, "cannons").isEmpty()) card.add(left(shipBreakdown(ship, shown)));
        // The classic style strip sits BELOW the gear view: one tab per
        // style carrying its icon and that style's shown dps.
        JPanel ts = styleStrip();
        for (String style : (Iterable<String>) styles.keySet()) {
            Map styleNode = map(styles, style);
            double td = num(styleNode, bis ? "bisTabDps" : "tabDps");
            if (td > 0) {
                // Thralls fold into the shown number, like spec already
                // does - the tab IS the total (field ask 2026-08-14); a
                // card that stood down folds nothing.
                td += thrallsFor(map(styleNode, bis ? "bis" : "yours"), td2) + sd;
            }
            boolean hs = td > 0;
            boolean isSelected = style.equals(tab);
            JPanel tc = darker(new BorderLayout(2, 0));
            tc.setPreferredSize(new Dimension(64, 26));
            tc.setOpaque(isSelected);
            tc.setBorder(new RoundedBorder(isSelected ? ACCENT : MEDIUM_GRAY_COLOR, 1, 2));
            var icon = new JLabel();
            int styleItem = id(styleNode, "styleItem");
            if (styleItem > 0) {
                AsyncBufferedImage img = itemManager.getImage(styleItem);
                onImage(img, () -> icon.setIcon(icon(img, 16)));
            } else seatSprite(icon, id(styleNode, "styleSprite"), 16);
            JLabel dl2 = label(hs ? format("%.2f", td) : "-", isSelected ? Color.WHITE : MID);
            dl2.setFont(dl2.getFont().deriveFont(Font.BOLD, 12f));
            dl2.setHorizontalAlignment(SwingConstants.CENTER);
            tc.add(icon, WEST);
            tc.add(dl2, CENTER);
            tipText(tc, cap(style) + (hs ? format(" - %.2f DPS", td) : " - no set"));
            onClick(tc, () -> {
                // Instant feedback, reconciled by the arriving page.
                tc.setBorder(new RoundedBorder(ACCENT, 1, 2));
                tc.setOpaque(true);
                tc.repaint();
                commands.set("selectedTab", style);
            });
            ts.add(tc);
        }
        var present = new LinkedHashMap<String, Color>();
        Map sg = map(shown, "gear");
        if (sg != null) {
            for (Object slotItem : sg.values()) {
                if (slotItem instanceof Map) {
                    String where = str((Map) slotItem, "source");
                    Color colour = sourceColor(where);
                    if (colour != null) present.put(where, colour);
                }
            }
        }
        if (!present.isEmpty()) {
            // "When we show a legend for colors only show for the colors
            // shown" (the classic rule).
            JPanel legend = darker(new FlowLayout(FlowLayout.LEFT, 6, 0));
            legend.add(statLine("Stored:", "Gear that needs a fetch trip", MID));
            for (var source : present.entrySet()) {
                legend.add(statLine(source.getKey(), "Fetch from " + source.getKey(), source.getValue()));
            }
            card.add(Box.createVerticalStrut(4));
            card.add(left(legend));
        }
        card.add(Box.createVerticalStrut(6));
        card.add(ts);
        // The classic view swap sits directly UNDER the tabs: two
        // bordered sides, the active one bright, with the gap readout
        // pushed to the right.
        JPanel vr = darker(new BorderLayout());
        vr.setBorder(createEmptyBorder(4, 2, 2, 2));
        vr.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JPanel sides = darker(new FlowLayout(FlowLayout.CENTER, 6, 0));
        sides.add(viewSide("Yours", !bis, "Your best owned set", () -> commands.set("viewingBis", false)));
        sides.add(viewSide("BiS", bis, "The game-wide best set at your levels", () -> commands.set("viewingBis", true)));
        vr.add(sides, WEST);
        Map yoursNode = map(node, "yours");
        Map bisNode = map(node, "bis");
        if (yoursNode != null && bisNode != null) {
            double mine = num(node, "tabDps");
            double ceiling = num(node, "bisTabDps");
            if (ceiling > 0) {
                JLabel gap = small(format("%.0f%% of BiS", min(100.0, 100.0 * mine / ceiling)), MID);
                tipText(gap, text("gap.tip"));
                vr.add(gap, EAST);
            }
        }
        card.add(vr);
        List<Map> bench = list(shown, "bench");
        List<Map> runes = list(shown, "runes");
        List<Map> ur = list(mob, "utilityRunes");
        boolean pouchRides = id(mob, "castingPouch") > 0 && (!runes.isEmpty() || anyRune(ur));
        if (!bench.isEmpty() || pouchRides) {
            // 24px icons on a WRAPPING row - eight carried items fit
            // (field report: only 4 of 8 visible at full item size).
            JPanel ir = darker(new WrapLayout(FlowLayout.CENTER, 2, 1));
            ir.add(small("Inventory:", null));
            Map protection = map(shown, "protection");
            for (Map carried : bench) {
                // GWD protection is worn on the way in, not swapped mid-fight.
                Object gods = protection == null ? null : protection.get(String.valueOf(id(carried, "id")));
                ir.add(smallItemCell(id(carried, "id"), str(carried, "name") + (gods != null
                        ? " (GWD protection: " + gods + " - wear it through the dungeon)"
                        : " (" + RequiredGear.swapNote(id(carried, "id")) + ")")));
            }
            int pi = id(mob, "castingPouch");
            if (pi > 0 && (!runes.isEmpty() || !ur.isEmpty())) {
                ir.add(smallItemCell(pi, (pi == 27281 || pi == 27509
                        ? "Divine rune pouch" : "Rune pouch") + " - carries the trip's runes"));
            }
            card.add(Box.createVerticalStrut(4));
            card.add(left(ir));
        }
        // The potion this card assumes is a supply to bring (Andrew
        // 2026-09-02) - unless the raid hands it out.
        int bi = assume == null || flag(assume, "boostSupplied") ? -1 : id(assume, "boostItem");
        if (!supplies.isEmpty() || !runes.isEmpty() || !ur.isEmpty() || bi > 0) {
            JPanel supplyRow = darker(new WrapLayout(FlowLayout.CENTER, 2, 1));
            supplyRow.add(small("Supplies:", null));
            // One icon per rune TYPE (field call: no triple blood runes)
            // - the tooltip collects every reason it rides.
            Map<Integer, StringBuilder> rw = new LinkedHashMap<>();
            Map<Integer, String> runeNames = new HashMap<>();
            for (Map rune : runes) noteRune(runeNames, rw, rune, "the autocast spell");
            for (Map rune : ur) noteRune(runeNames, rw, rune, str(rune, "why"));
            for (var rune : rw.entrySet()) {
                supplyRow.add(smallItemCell(rune.getKey(), runeNames.get(rune.getKey()) + " - " + rune.getValue()));
            }
            int ci = id(mob, "castingCape");
            // Once, not twice (Andrew 2026-09-02): the spellbook-cape supply
            // already draws the cape.
            if (ci > 0 && byCategory(supplies, "spellbookCape") == null && (!runes.isEmpty() || !ur.isEmpty())) supplyRow.add(smallItemCell(ci, text("supply.cape")));
            if (bi > 0) {
                String text = str(assume, "text");
                int plus = text.indexOf(" + ");
                supplyRow.add(smallItemCell(bi, (plus > 0 ? text.substring(plus + 3) : text) + " " + text("supply.boost")));
            }
            for (Map supply : supplies) {
                JLabel cell = smallItemCell(id(supply, "itemId"), str(supply, "name") + " - right-click to change");
                cell.setComponentPopupMenu(supplyMenu(str(supply, "category"), list(supply, "options")));
                supplyRow.add(cell);
            }
            card.add(Box.createVerticalStrut(4));
            card.add(left(supplyRow));
        }
        JPanel trioRow = darker(new FlowLayout(FlowLayout.CENTER, 4, 0));
        trioRow.add(trioButton(mob, "+", "mobSims", SIM_PALETTE, "Simmed for this mob", "remove-mob-sim", "sim-for-mob",
            "Sim an item as owned for this mob"));
        trioRow.add(trioButton(mob, "-", "mobExclusions", EXCLUDE_PALETTE,
            "Excluded for this mob", "remove-mob-exclusion", "exclude-for-mob", "Exclude an item for this mob"));
        trioRow.add(trioButton(mob, "~", "mobFilters", FILTER_PALETTE,
            "Bank-filter supplies for this mob", "remove-mob-filter", "add-mob-filter", text("trio.filter.add")));
        // The pins, visible at last (ISS-01M5HJ84ADBHWB2302G66WW2G4): a
        // tried swap pins its item, and a hidden pin outlived its sim.
        JPanel tr2 = darker(new FlowLayout(FlowLayout.CENTER, 4, 0));
        tr2.add(trioButton(mob, "^", "mobPins", new Color[]{PIN_BADGE, new Color(0x8C8264), new Color(0xA89458)},
            "Pinned for this mob", "remove-mob-pin", "pin-for-mob", "Pin an item for this mob"));
        JButton degrades = degradesButton(mob, shown);
        if (degrades != null) tr2.add(degrades);
        card.add(Box.createVerticalStrut(4));
        // Centred, not left() - field ask 2026-08-20. Two rows (Andrew
        // 2026-10-22: "so those 5 options aren't all on the same line"):
        // sims, exclusions, filters; then pins and degrade.
        card.add(centre(trioRow));
        card.add(Box.createVerticalStrut(2));
        card.add(centre(tr2));
        card.add(Box.createVerticalStrut(4));
        JToggleButton sb = bankToggle("Show in bank", text("bank.show.tip"), "bank-show", () -> {
            List<Integer> ids = new ArrayList<>();
            Map gm = map(shown, "gear");
            if (gm != null) {
                for (Object slotItem : gm.values()) {
                    if (slotItem instanceof Map) ids.add(id((Map) slotItem, "id"));
                }
            }
            Map qi = map(shown, "quiverAmmo");
            if (qi != null) ids.add(id(qi, "id"));
            for (Map carried : bench) ids.add(id(carried, "id"));
            return Map.of("ids", ids);
        });
        sb.setMargin(new Insets(1, 6, 1, 6));
        JPanel bankRow = darker(new FlowLayout(FlowLayout.CENTER, 4, 0));
        bankRow.add(sb);
        Map bp = BankLayout.build(shown, BankLayout.tripStacks(mob, assume, supplies, runes, ur));
        if (bp != null) {
            bankRow.add(bankToggle("Filter bank", text("bank.filter.tip"), "bank-filter", () -> {
                List<Integer> ll = new ArrayList<>();
                for (int pos : (int[]) bp.get("layout")) ll.add(pos);
                return Map.of("ids", bp.get("ids"), "layout", ll);
            }));
        }
        card.add(centre(bankRow));
        String noteText = str(mob, "note");
        boolean hasNote = noteText != null && !noteText.trim().isEmpty();
        var note = new JTextField(noteText, 18);
        note.setBackground(new Color(0x4E4832));
        note.setForeground(new Color(0xD7CDA0));
        note.setCaretColor(new Color(0xD7CDA0));
        note.setBorder(createLineBorder(new Color(0x625A3E)));
        tipText(note, text("note.tip"));
        note.addActionListener(e -> commands.send("set-note", Map.of("text", note.getText())));
        card.add(Box.createVerticalStrut(4));
        if (hasNote) {
            card.add(centre(note));
        } else {
            // A ghost until there is something to say - the open post-it
            // field shouted for attention it rarely deserved.
            JLabel addNote = small("+ note", new Color(0x78735F));
            tipText(addNote, "Add a note for this mob");
            JPanel nh = centre(addNote);
            onClick(addNote, () -> {
                nh.removeAll();
                nh.add(note);
                note.requestFocusInWindow();
                nh.revalidate();
                nh.repaint();
            });
            card.add(nh);
        }
        return card;
    }

    // One classic local chip: "Exclude (2)" opens the mob-scoped list
    // (remove per entry) plus an add-by-search entry.
    // The in-card cannon strip (REQ-SC-8): left cannon / shared ammo /
    // right cannon, sized like the assume icons, each opening the boost-rack
    // picker. Operators live inside each cannon's rack.
    JPanel cannonStrip(Map ship) {
        int count = id(pageParams, "cannonCount");
        List<Map> cn = list(ship, "cannons");
        JPanel strip = darker(new FlowLayout(FlowLayout.CENTER, 6, 0));
        strip.add(cannonButton(0, cn.isEmpty() ? null : cn.get(0)));
        strip.add(ammoButton(ship));
        if (count > 1) strip.add(cannonButton(1, cn.size() > 1 ? cn.get(1) : null));
        return strip;
    }

    // The trip ledger under a sea card: every part, summing to the TAB's
    // number, with the roster row's number named on the way (Andrew
    // 2026-09-02: "too many different output numbers").
    // full = every line; collapsed = the total alone (the show/hide is a
    // per-profile setting, Andrew 2026-09-03).
    static String tripLedger(Map ship, Map side, boolean full) {
        boolean manned = "cannon".equals(str(ship, "station"));
        double set = num(side, "dps");
        double sd2 = num(map(side, "spec"), "dpsAdded");
        var t = new StringBuilder(text("ledger.head"));
        int body = t.length();
        double total = 0;
        if (manned) {
            t.append(ledgerRow("gear", "manning", "cannon 1", "#bebebe"));
        } else {
            t.append(ledgerRow("set", two(set), "", "#bebebe"));
            if (sd2 > 0) t.append(ledgerRow("+ spec", two(sd2), "", "#bebebe"));
            total = set + sd2;
            t.append(ledgerRow("= gear", two(total), "", "#bebebe"));
        }
        int i = 1;
        for (Map cannon : list(ship, "cannons")) {
            String blocked = str(cannon, "blocked");
            double dps = num(cannon, "dps");
            total += dps;
            String who = "player".equals(str(cannon, "firedBy")) ? "you" : "crew";
            t.append(ledgerRow("+ cannon " + i++, blocked != null ? blocked : two(dps),
                str(cannon, "tier") + ", " + who, blocked != null ? "#dc8c78" : "#96bedc"));
        }
        if (!full) t.setLength(body);
        t.append(ledgerRow("<b>= total</b>", "<b>" + two(total) + "</b>", "", "#82c882"));
        return t.append("</table></html>").toString();
    }

    static String ledgerRow(String key, String value, String note, String color) {
        return format(text("ledger.row"), color, key, value, note);
    }

    // A dps figure to two places, locale-proof (the ledger is read by tests).
    static String two(double value) { return format(Locale.ROOT, "%.2f", value); }

    JPanel shipBreakdown(Map ship, Map shownSide) {
        boolean full = breakdownShown.getAsBoolean();
        JLabel ledger = small(tripLedger(ship, shownSide, full), null);
        // Notes stay short (Andrew 2026-09-03: "player manning cannon causes
        // text cutoff") - the why lives here, with the show/hide.
        tipText(ledger, ("cannon".equals(str(ship, "station")) ? text("ledger.manning") : text("ledger.parts"))
            + " - click to " + (full ? "hide" : "show") + " the breakdown");
        onClick(ledger, () -> breakdownToggle.accept(!full));
        ledger.setBorder(createEmptyBorder(4, 0, 2, 0));
        return left(ledger);
    }

    // One cannon's icon button: the skill-guide item illustration, a red
    // ring + reason when the operate gate blocks it, and the boost-picker
    // rack of all seven materials on click.
    JButton cannonButton(int index, Map node) {
        String param = "cannon" + (index + 1) + "Material";
        String tier = str(pageParams, param);
        Cannon cannon = cannon(tier == null ? "bronze" : tier);
        String blocked = str(node, "blocked");
        JButton button = iconButton(cannon.itemId, blocked != null);
        String fired = str(node, "firedBy");
        tipText(button, "Cannon " + (index + 1) + ": " + cap(cannon.tier) + ("player".equals(fired) ? " - you" : " - crew")
            + (blocked != null ? ". " + blocked : ""));
        button.addActionListener(e -> {
            var menu = new JPopupMenu();
            JPanel rack = rack(4);
            for (Cannon option : cannons()) {
                rack.add(pickCell(menu, new ImageIcon(itemManager.getImage(option.itemId)),
                    cap(option.tier) + " cannon (Sailing " + option.sailing + ")", option.tier.equals(cannon.tier), 38,
                    () -> commands.set(param, option.tier)));
            }
            menu.add(rack);
            // The double rack (Andrew, v2): who fires it, right under the
            // materials. A crew pick below the cannon's gate auto-raises.
            menu.addSeparator();
            String op = "cannon" + (index + 1) + "Operator";
            String current = str(pageParams, op);
            menu.add(check("You man this cannon", "you".equals(current), () -> commands.set(op, "you")));
            for (int p = 1; p <= 4; p++) {
                String pick = "crew" + p;
                String label = "Crewmate - Privateering " + p + (p < cannon.privateering ? " (raises to " + cannon.privateering + ")" : "");
                menu.add(check(label, pick.equals(current), () -> commands.set(op, pick)));
            }
            show(menu, button);
        });
        return button;
    }

    // The shared ammo's icon button: only tiers EVERY carried cannon can
    // fire are offered (a mithril + dragon pair tops out at mithril).
    JButton ammoButton(Map ship) {
        String pick = str(pageParams, "cannonAmmo");
        boolean detect = pick == null || "detect".equals(pick);
        // Detect shows what it RESOLVED to (the boost idiom); a dry bank
        // shows the reason on a red ring.
        String resolved = str(ship, "ammo");
        String ab = str(ship, "ammoBlocked");
        String shownTier = resolved != null ? resolved : detect ? "bronze" : pick;
        Ball ball = ball(shownTier == null ? "bronze" : shownTier);
        JButton button = iconButton(ball.itemId, ab != null);
        tipText(button, "Cannonballs: " + (ab != null ? ab
                : detect ? "Detect best - " + cap(ball.tier) + " (best banked)" : cap(ball.tier)) + " - one tier for every cannon");
        button.addActionListener(e -> {
            int count = id(pageParams, "cannonCount");
            List<String> tiers = new ArrayList<>();
            for (int i = 0; i < count; i++) tiers.add(str(pageParams, "cannon" + (i + 1) + "Material"));
            var menu = new JPopupMenu();
            menu.add(check("Detect best in bank", detect, () -> commands.set("cannonAmmo", "detect")));
            menu.addSeparator();
            JPanel rack = rack(4);
            for (Ball option : balls()) {
                boolean everyone = true;
                for (String t : tiers) everyone &= canFire(t, option.tier);
                if (!everyone) continue;
                rack.add(pickCell(menu, new ImageIcon(itemManager.getImage(option.itemId)),
                    cap(option.tier) + " cannonball", !detect && option.tier.equals(ball.tier), 38,
                    () -> commands.set("cannonAmmo", option.tier)));
            }
            menu.add(rack);
            show(menu, button);
        });
        return button;
    }

    // palette = {active, muted, active border} (the trio's shared sigil colours).
    JButton trioButton(Map mob, String sigil, String lk, Color[] palette,
        String noun, String removeCommand, String addCommand, String ap) {
        List<Map> items = list(mob, lk);
        boolean any = !items.isEmpty();
        JButton button = sigilButton(sigil + items.size(), any, any ? palette[0] : palette[1], palette[2], noun + " - click to manage");
        button.addActionListener(e -> {
            var menu = new JPopupMenu();
            for (Map item : items) {
                int id = id(item, "id");
                String scope = str(item, "scope");
                String itemName = str(item, "name");
                item(menu, "Remove " + itemName + (scope == null || "ALL".equals(scope) ? "" : " (" + scope + ")"), () -> {
                    Map args = new HashMap<>();
                    args.put("itemId", id);
                    args.put("label", itemName);
                    if (str(item, "slot") != null) args.put("slot", str(item, "slot"));
                    if (scope != null) args.put("scope", scope);
                    commands.send(removeCommand, args);
                });
            }
            if (!items.isEmpty()) {
                menu.addSeparator();
                item(menu, "Clear all (" + items.size() + ")", () -> commands.send("clear-mob-list", Map.of("kind", lk)));
                menu.addSeparator();
            }
            item(menu, ap + " (search)...", searchItem(ap, addCommand));
            show(menu, button);
        });
        return button;
    }

    // The add-by-search menu action: the picker's choice goes out as
    // {itemId, label} on the given command.
    Runnable searchItem(String prompt, String command) {
        return () -> picker.search(prompt, (id, name) -> commands.send(command, Map.of("itemId", id, "label", name)));
    }

    // One side of the classic view swap: the active side wears a bright
    // accent edge and white text, the other a quiet outline.
    static JLabel viewSide(String text, boolean selected, String tooltip, Runnable onClick) {
        JLabel side = label(text, selected ? Color.WHITE : new Color(0x969696));
        side.setFont(side.getFont().deriveFont(Font.BOLD, 14f));
        side.setBorder(new RoundedBorder(selected ? ACCENT : MEDIUM_GRAY_COLOR, 5, 12));
        tipText(side, tooltip);
        onClick(side, () -> {
            // Instant feedback; the arriving page confirms.
            side.setForeground(Color.WHITE);
            side.setBorder(new RoundedBorder(ACCENT, 5, 12));
            side.repaint();
            onClick.run();
        });
        return side;
    }

    // The style strip: it wraps, so the Multi card's fourth tool (Hallowfell)
    // takes a second row instead of running past the panel's edge
    // (ISS-01M5TS5CBHSDS00XQSQYJ19XW8).
    static JPanel styleStrip() { return darker(new WrapLayout(FlowLayout.CENTER, 2, 2)); }

    // Whether the utility rows hold a rune - a seed box or a slayer item alone
    // brings no rune pouch (ISS-01M5VH8H5MBRK0BFA6B8E68Z7T, Andrew 2026-10-26: "the seed box is
    // showing as rune in a rune pouch").
    static boolean anyRune(List<Map> rows) {
        return rows.stream().anyMatch(r -> SpellRunes.isRune(id(r, "id")));
    }

    final Map<Integer, ImageIcon> spriteIconCache = new HashMap<>();

    // The set's spellbook plate, ALWAYS shown (field report x3): the
    // autocast spell's book, else arceuus/lunar when thralls or Death
    // Charge assume it, else your live book in neutral. RED when the
    // required book is not the one you are on.
    JLabel bookPlate(Map shownCard, Map mob) {
        String required = null;
        String reason = null;
        String spell = str(shownCard, "spell");
        Map spellbooks = map(pageWide, "spellbooks");
        if (spell != null && spellbooks != null) {
            required = str(spellbooks, spell);
            reason = "autocasts " + spell;
        }
        // The FIGHT's own book beats every gear consideration (wiki-
        // verified curation - the Sire wants Ancients for the Shadow
        // Barrage stun regardless of what the set casts).
        String fb = str(mob, "fightBook");
        if (fb != null && !fb.isEmpty()) {
            required = fb;
            reason = str(mob, "fightBookReason");
        }
        if (flag(mob, "cureMe")) {
            // Cure Me camps Lunar and outranks the thralls rule below.
            required = "lunar";
            reason = "Cure Me";
        }
        if (required == null && pageParams != null && (flag(pageParams, "thralls") || id(pageParams, "deathCharge") > 0)) {
            boolean vs = "SPELLBOOK_SWAP".equals(str( byCategory(list(pageWide, "supplyCatalog"), "arceuusAccess"), "current"));
            required = vs ? "lunar" : "arceuus";
            reason = vs ? text("book.swap") : text("book.arceuus");
        }
        if (required == null && (flag(mob, "wardOfArceuus") || flag(mob, "sinisterOffering") || flag(mob, "demonicOffering"))) {
            required = "arceuus";
            reason = flag(mob, "wardOfArceuus") ? text("book.ward")
                : flag(mob, "sinisterOffering") ? text("book.sinister") : text("book.offering");
        }
        // The book the fight/set asks for, before any swap rewrite - the
        // picker offers it as the "camp it directly" choice.
        String db = required;
        String directReason = reason;
        boolean swapOn = flag(pageParams, "spellbookSwap");
        // With the swap on, LUNAR is home: you cast Vengeance there and
        // swap into whatever the fight wants (field report 2026-08-22:
        // the Sire still demanded Ancients and said nothing about the
        // swap being on).
        if (swapOn) {
            required = "lunar";
            reason = db == null || "lunar".equalsIgnoreCase(db) ? "Lunar home for Vengeance"
                : format(text("book.swaphome"), db.toUpperCase());
        }
        String live = str(pageWide, "liveSpellbook");
        boolean ob = required != null && live != null && !live.isEmpty() && !live.equalsIgnoreCase(required);
        var plate = new JLabel();
        plate.setPreferredSize(new Dimension(24, 22));
        plate.setHorizontalAlignment(SwingConstants.CENTER);
        if (ob) {
            plate.setOpaque(true);
            plate.setBackground(new Color(0x782828));
        }
        // No required book = a DIMMED generic tab, never a real book's
        // sprite (field confusion 2026-08-14: the live book shown in
        // neutral read as a recommendation - a powered staff like the
        // Shadow needs no book at all).
        String bt = required == null ? text("book.none")
            : format(text("book.wants"), required.toUpperCase(), reason, ob ? "NOT " : "");
        // The swap+Vengeance option rides the plate, the way prayers and
        // boosts ride their icons (field ask 2026-08-22). Supplies only:
        // it changes which runes the trip carries, never the dps.
        boolean swapAvailable = assumeOptions != null && flag(assumeOptions, "spellbookSwapAvailable");
        if (swapAvailable) {
            bt = bt + text("book.choose");
            tipText(plate, "<html>" + bt + "</html>");
            if (swapOn) plate.setBorder(new RoundedBorder(ACCENT, 2, 6));
            String home = db;
            onClick(plate, () -> showBookMenu(plate, home, swapOn));
        } else tipText(plate, bt);
        int sprite = required == null ? SpriteID.TAB_MAGIC : bookSprite(required);
        boolean dim = required == null;
        if (sprite > 0) {
            onSprite(sprite, img -> {
                Image scaled = img.getScaledInstance(18, 18, Image.SCALE_SMOOTH);
                plate.setIcon(dim ? new ImageIcon(GrayFilter .createDisabledImage(toBuffered(scaled))) : new ImageIcon(scaled));
            });
        }
        return plate;
    }

    static BufferedImage toBuffered(Image image) {
        var out = new BufferedImage( 18, 18, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return out;
    }

    // The book tab sprites (SpriteID); -1 for a book that has none.
    static final Map<String, Integer> BOOK_SPRITES = Map.of(
        "ancient", SpriteID.TAB_MAGIC_SPELLBOOK_ANCIENT_MAGICKS, "lunar", SpriteID.TAB_MAGIC_SPELLBOOK_LUNAR,
        "arceuus", SpriteID.TAB_MAGIC_SPELLBOOK_ARCEUUS, "standard", SpriteID.TAB_MAGIC);

    static int bookSprite(String book) {
        return BOOK_SPRITES.getOrDefault(book == null ? "" : book.toLowerCase(), -1);
    }

    // The prayer picker popup (anchored to the wings icon): Detect /
    // None / each prayer with its sprite, checkmarked at the pick. The
    // PRAYER BOOK view (field ask 2026-08-14): the options render as the
    // game's prayer-tab grid - sprite cells, the current pick on the
    // accent plate - not a text list.
    JPopupMenu prayerMenu(String tab) {
        Map sprites = map(assumeOptions, "prayerSprites");
        Map images = map(assumeOptions, "prayerImages");
        List<String[]> cells = new ArrayList<>();
        for (Object option : list2(map(assumeOptions, "prayers"), tab)) {
            cells.add(new String[]{String.valueOf(option), String.valueOf(option)});
        }
        return pickerMenu(tab, "set-prayer-pick", "prayerPicks", "Detect best", "None (prayerless)", cells, 26, name -> {
                int sprite = id(sprites, name);
                String image = str(images, name);
                return sprite > 0 ? cachedSprite(sprite)
                    : image != null ? icon(ImageUtil.loadImageResource(ResultCards.class, image), 15) : null;
            });
    }

    // The boost picker popup - the SAME game-tab grid as the prayer
    // book (field ask 2026-08-20: one interface, the prayer one wins),
    // with potion icons for cells. Item images load ASYNC - the icon gets
    // the live image, never a getScaledInstance of it (blank until reopen).
    JPopupMenu boostMenu(String tab) {
        Map potionIds = map(assumeOptions, "boostItems");
        List<String[]> cells = new ArrayList<>();
        for (Map option : list(map(assumeOptions, "boosts"), tab)) {
            cells.add(new String[]{str(option, "label"), str(option, "key")});
        }
        return pickerMenu(tab, "set-boost-pick", "boostPicks", "Detect best in bank", "None (unboosted)", cells, 38, label -> {
                int itemId = id(potionIds, label);
                return itemId > 0 ? new ImageIcon(itemManager.getImage(itemId)) : null;
            });
    }

    // The two pickers' shared shape: the Detect and None check items, then
    // the game-tab grid of {label, key} cells (iconOf draws each by label),
    // the current pick (pageParams.picksKey[tab]) lit.
    JPopupMenu pickerMenu(String tab, String command, String picksKey, String detectLabel,
        String noneLabel, List<String[]> cells, int size, Function<String, Icon> iconOf) {
        String current = str(map(pageParams, picksKey), tab);
        var menu = new JPopupMenu();
        menu.add(check(detectLabel, current == null, () -> sendPick(command, tab, null)));
        menu.add(check(noneLabel, "NONE".equals(current), () -> sendPick(command, tab, "NONE")));
        if (!cells.isEmpty()) {
            menu.addSeparator();
            JPanel grid = rack(5);
            for (String[] cell : cells) {
                grid.add(pickCell(menu, iconOf.apply(cell[0]), cell[0], cell[1] != null && cell[1].equals(current),
                    size, () -> sendPick(command, tab, cell[1])));
            }
            menu.add(grid);
        }
        return menu;
    }

    // How you reach the book this fight wants: camp it, or keep Lunar
    // home and Spellbook Swap in (which also buys Vengeance). Wears the
    // same game-tab grid as the prayer book and the boost rack (field
    // ask 2026-08-22) - the runes follow the choice, the dps never does.
    void showBookMenu(Component anchor, String db, boolean swapOn) {
        var menu = new JPopupMenu();
        JPanel books = rack(2);
        boolean any = db == null || db.isEmpty();
        String direct = any ? "standard" : db;
        books.add(pickCell(menu, cachedSprite(bookSprite(direct)),
            any ? "Any book - nothing here needs one" : "Camp " + direct.toUpperCase(), !swapOn, 30,
            () -> commands.set("spellbookSwap", false)));
        boolean needsSwap = !any && !"lunar".equalsIgnoreCase(db);
        books.add(pickCell(menu, cachedSprite(bookSprite("lunar")), needsSwap
                ? format(text("book.swapinto"), direct.toUpperCase()) : text("book.lunar"), swapOn, 30,
            () -> commands.set("spellbookSwap", true)));
        menu.add(books);
        show(menu, anchor);
    }

    // One cell of the game-tab picker grid (the prayer-book pattern,
    // shared by the prayer and boost pickers): icon or initial, accent
    // plate on the pick, hover ring, click picks and closes.
    JLabel pickCell(JPopupMenu menu, Icon icon, String tooltip, boolean picked, int size, Runnable onPick) {
        JLabel cell = pickedCell(size, picked);
        if (icon != null) {
            cell.setIcon(icon);
        } else {
            cell.setText(tooltip.substring(0, 1));
            cell.setForeground(LIGHT);
        }
        tipText(cell, tooltip + (picked ? " (assumed)" : ""));
        cell.setCursor(Cursor.getPredefinedCursor( Cursor.HAND_CURSOR));
        cell.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                menu.setVisible(false);
                onPick.run();
            }

            public void mouseEntered(MouseEvent e) { cell.setBorder(createLineBorder(ACCENT)); }

            public void mouseExited(MouseEvent e) {
                if (!picked) cell.setBorder(createLineBorder( DARKER_GRAY_HOVER_COLOR));
            }
        });
        return cell;
    }

    void sendPick(String command, String tab, String value) {
        Map args = new HashMap<>();
        args.put("style", tab);
        args.put("value", value);
        commands.send(command, args);
    }

    // A sprite icon from the shared cache (primed async; a menu opened
    // before the load shows it on its next open).
    Icon cachedSprite(int spriteId) {
        ImageIcon icon = spriteIconCache.get(spriteId);
        if (icon != null) return icon;
        var fresh = new ImageIcon( new BufferedImage(15, 15, BufferedImage.TYPE_INT_ARGB));
        spriteIconCache.put(spriteId, fresh);
        onSprite(spriteId, img -> fresh.setImage(img.getScaledInstance(15, 15, Image.SCALE_SMOOTH)));
        return fresh;
    }

    // A stat line led by its game-sprite icon (async, 14px).
    // The ship damage line, sized for the stat column.
    static String shipDamageText(double dtps, int km) {
        return km == 0 ? "no damage" : format(Locale.ROOT, "~%.1f max %d", dtps, km);
    }

    // The damage-taken line and its tooltip. Coverage decides the text
    // (ISS-01M5FZ3KYCGEMMX1A45D3J9NQ9): unknown reads "?", never safe; a
    // number that leaves attacks out is starred and names them.
    static String[] dtpsLine(Map incoming) {
        String coverage = str(incoming, "coverage");
        if ("none".equals(coverage)) {
            String why = str(incoming, "overrideNote");
            return new String[]{"DTPS: ?", why == null || why.isEmpty() ? "No estimate for this monster's attacks" : why};
        }
        double dps = num(incoming, "dps");
        String tip = format(text("dtps.tip"), str(incoming, "protectPrayer") == null
            ? "No protection prayer helps" : "Pray " + str(incoming, "protectPrayer"), dps, num(incoming, "unprayedDps"));
        if (!"partial".equals(coverage)) {
            return new String[]{format("~%.1f", dps), tip};
        }
        List<String> missing = new ArrayList<>();
        for (Object style : list2(incoming, "unmodeled")) missing.add(String.valueOf(style));
        return new String[]{format("~%.1f*", dps), tip + ". Not counted: " + join(", ", missing)};
    }

    // The slot ladder as a strip under the gear grid (ISS-01M5GHXXEZKX8S68M5X9DHY6ME,
    // Andrew 2026-10-21: "Go with concept A"): left to right, the owned steps
    // down (farthest first), the worn item, the steps up (closest first). Each
    // column is a bar sized by its gain under the active mode, the item's icon
    // and its change. A click on a step sims it for this monster and wears it
    // (ISS-01M5GNMHHCD8M56JD4GAZBFV7Y: "Sim + pin for this mob"); its
    // right-click menu adds sim everywhere and Not Enough Runes.
    JPanel ladderStrip(Map ladder, Map worn, String slot, boolean nerAvailable) {
        return ladderStrip(ladder, worn, slot, nerAvailable, null);
    }

    // kind names this strip among the slot's several ("powered staves" /
    // "autocast staves", ISS-01M5PKSQBAP823XNC13QBW8830); null for the one strip.
    JPanel ladderStrip(Map ladder, Map worn, String slot, boolean nerAvailable, String kind) {
        return ladderStrip(ladder, worn, slot, nerAvailable, kind, 0);
    }

    // Steps up per page past the strip (ISS-01M5QD7PNMCMNRZD92RPRMHWRP).
    static final int PAGE = 5;

    // page 0 is the strip; page n draws the nth PAGE further steps up, no
    // worn column, every delta still against the worn item, with back and
    // more controls (Andrew 2026-10-24: "an option to 'show more' on the
    // ladder to see more alternatives" - the panel is too narrow to widen).
    JPanel ladderStrip(Map ladder, Map worn, String slot, boolean nerAvailable, String kind, int page) {
        List<Map> down = list(ladder, "down");
        List<Map> up = list(ladder, "up");
        List<Map> more = list(ladder, "upMore");
        int pages = (more.size() + PAGE - 1) / PAGE;
        page = max(0, min(page, pages));
        if (page > 0) {
            down = List.of();
            up = more.subList((page - 1) * PAGE, min(page * PAGE, more.size()));
        }
        double mg = 1e-9;
        for (Map rung : down) mg = max(mg, abs(num(rung, "gain")));
        for (Map rung : up) mg = max(mg, abs(num(rung, "gain")));
        boolean group = flag(ladder, "group");
        // No gaps between columns, so the bars' baselines join into one line.
        JPanel row = clear(new FlowLayout(FlowLayout.CENTER, 0, 0));
        if (page > 0) {
            row.add(pager("ladder-back", "<", "Back to the strip", page - 1));
        } else {
            for (int i = down.size() - 1; i >= 0; i--) {
                row.add(ladderColumn(down.get(i), slot, mg, LADDER_DOWN, nerAvailable, group));
            }
            JPanel wornColumn = ladderColumn(worn, slot, mg, LADDER_WORN, nerAvailable, group);
            // The weapon strip's worn column lists every option on right-click
            // (ISS-01M5PE4TZXTGJAQA1YXNRKPEDW, Andrew 2026-10-24: "id like to see
            // what a trident or toxic trident compares to the purging staff").
            List<Map> all = list(ladder, "all");
            if (!all.isEmpty()) wornColumn.setComponentPopupMenu(allWeaponsMenu(all, group));
            row.add(wornColumn);
        }
        for (Map rung : up) {
            row.add(ladderColumn(rung, slot, mg, LADDER_UP, nerAvailable, group));
        }
        if (page < pages) row.add(pager("ladder-more", ">", "More steps up", page + 1));

        JPanel strip = new RoundPanel(LADDER_INSET, 8);
        strip.setLayout(new BoxLayout(strip, BoxLayout.Y_AXIS));
        strip.setBorder(createEmptyBorder(4, 2, 4, 2));
        JLabel title = small(cap(slot) + " swaps" + (kind == null ? "" : " - " + kind) + (group ? " (group)" : ""), TEXT);
        tipText(title, group ? text("ladder.group.tip") : text("ladder.solo.tip"));
        JPanel legend = clear(new BorderLayout());
        legend.add(small(page > 0 ? "steps " + (stepsShown(ladder) + (page - 1) * PAGE + 1) + " up" : "your gear",
            LADDER_DOWN), WEST);
        legend.add(small("upgrades", LADDER_UP), EAST);
        legend.setMaximumSize(new Dimension(row.getPreferredSize().width, legend.getPreferredSize().height));
        for (JComponent part : new JComponent[]{title, row, legend}) {
            part.setAlignmentX(Component.CENTER_ALIGNMENT);
            strip.add(part);
        }
        return strip;
    }

    // How many steps up page zero shows: the pages count on from there.
    static int stepsShown(Map ladder) { return list(ladder, "up").size(); }

    // A page control at the strip's edge: a click turns the page, view state
    // like the open slot (never an action in the history).
    JLabel pager(String name, String glyph, String tip, int page) {
        JLabel label = label(glyph, TEXT);
        label.setName(name);
        tipText(label, tip);
        label.setBorder(createEmptyBorder(0, 4, 0, 4));
        onClick(label, () -> commands.send("set-param", Map.of("param", "ladderPage", "value", page)));
        return label;
    }

    // Every weapon the request may wear, best first, this mob's number on
    // each; an unowned one sims on click, like a step.
    JPopupMenu allWeaponsMenu(List<Map> all, boolean group) {
        var menu = new JPopupMenu();
        var list = new JMenu("All weapons (" + all.size() + ")");
        for (Map option : all) {
            String name = str(option, "name");
            boolean owned = flag(option, "owned");
            var item = new JMenuItem(format("%s - %.2f DPS (%s)%s", name,
                num(option, "dps"), str(option, "delta"), owned ? " - yours" : ""));
            if (!owned) {
                int id = id(option, "id");
                tipText(item, "Pretend you own " + name + (group ? " for every monster in this group" : " for this monster"));
                item.addActionListener(e -> commands.send(group ? "sim-for-group" : "sim-for-mob", Map.of("itemId", id, "label", name)));
            }
            list.add(item);
        }
        menu.add(list);
        return menu;
    }

    // The card-wide row that centres the ladder strip, under a divider.
    static JPanel centered(JComponent inner) {
        // Centre the strip in whatever width the card has - clipped evenly on
        // both sides if it ever overflows - and never ask the card for the
        // strip's width (Andrew 2026-10-22: a FlowLayout clipped it on one
        // side; GridBag pushed it right and cut it when the card was narrow).
        // A plain LayoutManager (FlowLayout's base: its add/remove hooks are
        // no-ops), so the row's maximum size stays Swing's default.
        JPanel row = darker(new FlowLayout() {
            public Dimension preferredLayoutSize(Container parent) {
                Insets in = parent.getInsets();
                int height = parent.getComponentCount() == 0 ? 0 : parent.getComponent(0).getPreferredSize().height;
                return new Dimension(1, height + in.top + in.bottom);
            }

            public Dimension minimumLayoutSize(Container parent) { return preferredLayoutSize(parent); }

            public void layoutContainer(Container parent) {
                if (parent.getComponentCount() == 0) return;
                Component child = parent.getComponent(0);
                Dimension size = child.getPreferredSize();
                Insets in = parent.getInsets();
                int left = in.left;
                int right = parent.getWidth() - in.right;
                // A card wider than the side panel's viewport would centre the
                // strip off screen to the right (Andrew 2026-10-22: "still seeing
                // the ladder view off-center"): centre on the visible span.
                JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, parent);
                if (viewport != null && viewport.getView() != null && viewport.getExtentSize().width > 0) {
                    Point at = SwingUtilities.convertPoint(parent, 0, 0, viewport.getView());
                    int vl = viewport.getViewPosition().x;
                    left = max(left, vl - at.x);
                    right = min(right, vl + viewport.getExtentSize().width - at.x);
                }
                child.setBounds(left + (right - left - size.width) / 2, in.top, size.width, size.height);
            }
        });
        row.setBorder(createCompoundBorder( createMatteBorder(1, 0, 0, 0, MEDIUM_GRAY_COLOR), createEmptyBorder(5, 0, 2, 0)));
        row.add(inner);
        return row;
    }

    static final Color LADDER_INSET = new Color(0x232323);
    static final Color LADDER_WORN_BG = new Color(0x343434);
    static final Color LADDER_BASELINE = new Color(0x505050);
    static final Color LADDER_DOWN = new Color(0x8A8A8A);
    static final Color LADDER_WORN = new Color(0xD2D2D2);
    static final Color LADDER_UP = new Color(0x78C878);
    static final Color LADDER_TAKEN = new Color(0xB4A078);
    static final int LADDER_BAR_MID = 24;
    // Five columns + padding stay under 180 px: the card body is about
    // 190 px inside RuneLite's 225 px side panel.
    static final int LADDER_COLUMN = 32;

    // A panel filled with a rounded rectangle (the strip's inset, the worn column).
    static class RoundPanel extends JPanel {
        final Color fill;
        final int arc;

        RoundPanel(Color fill, int arc) {
            this.fill = fill;
            this.arc = arc;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // A ladder bar: a rounded top, standing on the strip's baseline.
    static class LadderBar extends JComponent {
        final Color color;

        LadderBar(Color color, int height) {
            this.color = color;
            setPreferredSize(new Dimension(8, height));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = smooth(g);
            g2.setColor(color);
            // Overdraw past the bottom so only the top corners round.
            g2.fillRoundRect(0, 0, getWidth(), getHeight() + 6, 6, 6);
            g2.dispose();
        }
    }

    // One strip column: a bar on the baseline, the icon, the change. The
    // worn item's column sits on a lighter rounded ground and does nothing on
    // a click; any other sims-and-wears on a left click.
    JPanel ladderColumn(Map rung, String slot, double mg, Color color, boolean nerAvailable, boolean group) {
        boolean worn = color == LADDER_WORN;
        int id = id(rung, "id");
        int height = worn ? LADDER_BAR_MID : (int) max(4, min(2 * LADDER_BAR_MID - 4,
                round(LADDER_BAR_MID + (LADDER_BAR_MID - 6) * num(rung, "gain") / mg)));
        var bar = new LadderBar(color, height);
        bar.setName("bar:" + id);
        JPanel bars = clear(new BorderLayout());
        JPanel bottom = clear(new FlowLayout(FlowLayout.CENTER, 0, 0));
        bottom.add(bar);
        bars.add(bottom, SOUTH);
        bars.setPreferredSize(new Dimension(LADDER_COLUMN, 2 * LADDER_BAR_MID + 1));
        bars.setBorder(createMatteBorder(0, 0, 1, 0, LADDER_BASELINE));

        JLabel cell = square(32);
        cell.setName("rung:" + id);
        cell.setOpaque(true);
        cell.setBackground(CELL_BG);
        itemManager.getImage(id).addTo(cell);
        boolean owned = worn || flag(rung, "owned");
        String name = str(rung, "name");
        String change = worn ? "you" : str(rung, "delta");
        String price = owned ? "" : Gp.formatShort(id(rung, "price"));
        String with = str(rung, "with");
        String second = str(rung, worn ? "base2" : "delta2");
        boolean same = !worn && flag(rung, "same");
        // A group strip's numbers are this mob's; the group figure rides here.
        String gn = str(rung, worn ? "groupBase" : "groupDelta");
        String tip = name + (with == null ? "" : " with " + with)
            + (worn ? " - worn" : " " + change + (second == null ? "" : ", " + second))
            + (gn == null ? "" : " (" + gn + ")") + (same ? " - same result here, better stats" : "")
            + (price.isEmpty() ? "" : " - " + price + " gp");
        cell.setBorder(owned ? createLineBorder(color) : createDashedBorder(color, 1f, 3f, 2f, false));
        if (worn) {
            tipText(cell, tip);
        } else {
            Map tryArgs = Map.of("slot", slot, "itemId", id, "label", name);
            Map sa = Map.of("itemId", id, "label", name);
            var menu = new JPopupMenu();
            if (owned && group) {
                // A group strip pins nothing: one member's pin would force
                // the item on a mob that carries another style's swap.
                tipText(cell, tip + " - yours");
            } else if (owned) {
                // Yours already: a sim changes nothing, so no click - an
                // explicit pin stays in the menu for forcing it here.
                boolean spec = "spec".equals(slot);
                item(menu, spec ? "Pin as spec for this mob" : "Pin for this mob",
                    format(text("ladder.pin.tip"), name, slot), () -> commands.send("ladder-try", tryArgs));
                tipText(cell, tip + " - yours. Right-click to pin it here");
            } else {
                // A click sims it for this mob, nothing more (Andrew
                // 2026-10-22: "we are using "pin" for this mob instead of sim
                // for this mob"): a step up is better swapped in alone, so the
                // optimizer wears it - and removing the sim takes it off again.
                Runnable sim = () -> commands.send(group ? "sim-for-group" : "sim-for-mob", sa);
                onLeftClick(cell, sim);
                item(menu, group ? "Sim for this group" : "Sim for this mob",
                    "Pretend you own " + name + (group ? " for every monster in this group" : " for this monster"), sim);
                item(menu, "Sim everywhere", "Pretend you own " + name + " for every monster", () -> commands.send("toggle-sim", sa));
                // Both tooltips say a sim lets the optimizer re-fit the whole set
                // around the item, so the card can land above the step's own
                // number, never below.
                if (nerAvailable) {
                    item(menu, text("ladder.ner"), "Open " + name + " in Not Enough Runes",
                        () -> commands.send("ner-open", Map.of("itemId", id)));
                    tipText(cell, format(text("ladder.sim.ner"), tip, group ? "group" : "mob"));
                } else tipText(cell, format(text("ladder.sim.noner"), tip, group ? "group" : "mob"));
            }
            // Hide a step you never want suggested (Andrew 2026-10-22:
            // "stop showing this as a swap option (this mob/all mobs)") - an
            // exclusion, listed and undone from the - list like any other, so
            // the optimizer leaves the item out too. A group strip hides
            // everywhere only: "this mob" would be one member of many.
            if (menu.getComponentCount() > 0) menu.addSeparator();
            if (!group) {
                item(menu, "Hide for this mob", format(text("ladder.hide.mob"), name),
                    () -> commands.send("exclude-for-mob", Map.of("itemId", id, "scope", "ALL", "label", name)));
            }
            item(menu, "Hide for all mobs", format(text("ladder.hide.all"), name), () -> commands.send("toggle-exclusion", sa));
            cell.setComponentPopupMenu(menu);
        }

        JLabel label = small(worn ? shortDelta(str(rung, "base")) : same ? "=" : shortDelta(change), color);
        label.setName("value:" + id);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        // Balanced's second number: the damage-taken change (worn: its own).
        JLabel label2 = second == null ? null : small(shortDelta(second), LADDER_TAKEN);
        if (label2 != null) {
            label2.setName("value2:" + id);
            label2.setHorizontalAlignment(SwingConstants.CENTER);
        }

        JPanel column = worn ? new RoundPanel(LADDER_WORN_BG, 8) : new JPanel();
        column.setOpaque(false);
        if (worn) column.setName("worn-mark");
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        column.setBorder(createEmptyBorder(0, 0, 2, 0));
        for (JComponent part : label2 == null ? new JComponent[]{bars, cell, label}
            : new JComponent[]{bars, cell, label, label2}) {
            // BoxLayout stretches to the maximum size - pin the cell and the
            // bar column so they keep their square / full height.
            if (part == bars || part == cell) {
                part.setMinimumSize(part.getPreferredSize());
                part.setMaximumSize(part.getPreferredSize());
            }
            part.setAlignmentX(Component.CENTER_ALIGNMENT);
            column.add(part);
        }
        column.add(Box.createVerticalStrut(2));
        return column;
    }

    // The strip's narrow label: the number only ("+0.21", "4.20").
    static String shortDelta(String delta) {
        if (delta == null) return "";
        Matcher m = Pattern.compile("[+-]?[0-9.]+%?").matcher(delta);
        return m.find() ? m.group() : delta;
    }

    // The slot whose ladder strip is open ("" = none) - page view state.
    String ladderSlotOpen() {
        String open = str(pageParams, "ladderSlot");
        return open == null ? "" : open;
    }

    // Whether Not Enough Runes is enabled (the page params carry it).
    boolean nerAvailable() { return flag(pageParams, "nerAvailable"); }

    // The defensive mode's trade in words (the line's tooltip): the DPS
    // given up and the damage taken cut, or why it kept Max DPS.
    static String modeLine(Map trade) {
        if (trade == null) return null;
        String mode = "TANKY".equals(str(trade, "mode")) ? "Tanky" : "Balanced";
        String reason = str(trade, "reason");
        if (reason != null) return mode + ": kept Max DPS (" + reason + ")";
        return mode + ": " + cut(id(trade, "dpsLossPct")) + "% DPS, " + cut(id(trade, "dmgCutPct")) + "% damage taken";
    }

    // A cut in whole percents as the card says it: a loss "-", a gain "+"
    // (on a group's kit a member can gain, ISS-01M64GNB852CBYVVWE29A9HJK4).
    static String cut(int pct) {
        return (pct < 0 ? "+" : "-") + abs(pct);
    }

    // Seconds as m:ss; an infinite time reads never.
    static String mmss(double seconds) {
        if (Double.isInfinite(seconds)) return "never";
        long t = round(seconds);
        return format("%d:%02d", t / 60, t % 60);
    }

    // The line's short form: "~43 s" under a minute, "~2:13" above.
    static String about(double seconds) {
        return Double.isInfinite(seconds) ? "never" : seconds < 60 ? format("~%.0f s", seconds) : "~" + mmss(seconds);
    }

    // The hover behind a kill time (Andrew 2026-10-29: "Median: XX:XX / 90%
    // of runs fall between: XX:XX - XX:XX (P05-P95)"), Perfect and the notes.
    static String hover(String head, Map kill) {
        double heal = num(kill, "healDps");
        String notes = (flag(kill, "approximate") ? "<br>Band approximate: this hit model has no per-attack form" : "")
            + (heal > 0 ? format("<br>Heals ~%.1f/s off this set", heal) : "");
        if (Double.isInfinite(num(kill, "p50"))) return tip(head, "This set cannot outpace the heal, so it never kills", notes);
        return tip(head, format("Median %s<br>90%% of runs fall between %s and %s (P05-P95)<br>Perfect %s",
            mmss(num(kill, "p50")), mmss(num(kill, "p05")), mmss(num(kill, "p95")), mmss(num(kill, "perfect"))), notes);
    }

    // The card's Kill line (ISS-01M6415DTE84ZZ45C4YD3MZRBJ: "P50 is what is
    // displayed, the other stuff is shown when hovered").
    static String[] killLine(Map kill) { return new String[]{about(num(kill, "p50")), hover("", kill)}; }

    // A group member's line ("list individual and group kill times in the
    // card consecutively"), the name cut to the column.
    static String[] memberLine(Map member) {
        String name = str(member, "name").replaceFirst(" - lvl \\d+$", "");
        int space = name.indexOf(' ');
        String word = name.length() > 8 && space > 0 ? name.substring(0, space) : name;
        return new String[]{word + " " + about(num(member, "p50")), hover(name + "<br>", member)};
    }

    // A group's Trip line ("TTK for moons of peril, barrows should be the
    // combined kill time of all 3 bosses").
    static String[] tripLine(Map trip) {
        String never = str(trip, "never");
        if (never != null) return new String[]{"Trip never", never + " never dies in the set shown, so the trip never ends"};
        return new String[]{"Trip " + about(num(trip, "p50")), hover("The whole trip<br>", trip)};
    }

    JLabel statIconLine(String text, String tooltip, Color color, int spriteId) {
        JLabel line = statLine(text, tooltip, color);
        if (spriteId > 0) {
            onSprite(spriteId, img -> {
                line.setIcon(icon(img, 14));
                line.setIconTextGap(4);
                line.revalidate();
            });
        }
        return line;
    }

    // A stat line with a PAINTED classic icon in the fixed column.
    static JLabel paintedStatLine(String text, String tooltip, Color color, Icon icon) {
        JLabel line = statLine(text, tooltip, color);
        line.setIcon(new StatIcons.FixedWidthIcon(icon));
        line.setIconTextGap(2);
        return line;
    }

    // A column stat line led by an async item image, scaled to 16px and
    // seated in the fixed-width column (the coin stack rides this too).
    static JLabel statImageLine(AsyncBufferedImage img, String text, String tooltip, Color color) {
        JLabel line = statLine(text, tooltip, color);
        onImage(img, () -> {
            line.setIcon(new StatIcons.FixedWidthIcon(new ImageIcon( img.getScaledInstance(-1, 16, Image.SCALE_SMOOTH))));
            line.setIconTextGap(2);
            line.revalidate();
        });
        return line;
    }

    // The async-image contract: paint now with whatever the image holds,
    // and again on the EDT once it has loaded.
    static void onImage(AsyncBufferedImage img, Runnable paint) {
        Runnable seat = () -> SwingUtilities.invokeLater(paint);
        seat.run();
        img.onLoaded(seat);
    }

    // Seats a game sprite on the label once it loads (size 0 = as drawn).
    void seatSprite(JLabel target, int sprite, int size) {
        if (sprite > 0) onSprite(sprite, img -> target.setIcon(size > 0 ? icon(img, size) : new ImageIcon(img)));
    }

    // The async-sprite contract: the paint runs on the EDT once the
    // sprite has loaded (nothing without a sprite manager).
    void onSprite(int spriteId, Consumer<BufferedImage> paint) {
        if (spriteManager != null) spriteManager.getSpriteAsync(spriteId, 0, img -> SwingUtilities.invokeLater(() -> paint.accept(img)));
    }

    // A 24px inventory cell: the item image (quantity overlay when
    // qty > 0) scaled down so a full trip fits the row.
    JPopupMenu supplyMenu(String category, List<Map> options) {
        var menu = new JPopupMenu();
        item(menu, "Detect best", () -> commands.send("set-supply-override", Map.of("category", category, "choice", "DETECT")));
        item(menu, "None", () -> commands.send("set-supply-override", Map.of("category", category, "choice", "NONE")));
        for (Map option : options) {
            String key = str(option, "key");
            item(menu, str(option, "name"), () -> commands.send("set-supply-override", Map.of("category", category, "choice", key)));
        }
        return menu;
    }

    // The poison plan beside prayer and boost (Andrew 2026-09-08: "a more
    // obvious spot"): the Cure Me spell, the chosen potion or the prayer
    // book, "-" for none; click opens the anti-venom chooser.
    JLabel poisonPlate(Map mob, boolean bis) {
        String mode = str(mob, "antivenom");
        String category = str(mob, "cureCategory");
        Map chosen = byCategory(supplies, category);
        boolean none = "NONE".equals(mode);
        String name = chosen != null ? str(chosen, "name") : none ? "none" : "Detect best - none banked";
        JLabel plate = plate("Poison", name, bis,
            () -> supplyMenu(category, list(byCategory(list(pageWide, "supplyCatalog"), category), "options")));
        if ("CURE_ME".equals(mode)) {
            plate.setIcon(cachedSprite(SpriteID.SPELL_CURE_ME));
        } else if (!none) {
            // Detect best with nothing banked (field 2026-09-09: the grey
            // dash read as an empty box): the potion, crossed out.
            AsyncBufferedImage img = itemManager.getImage(chosen != null ? id(chosen, "itemId")
                : "antipoison".equals(category) ? 5952 : 12913);
            boolean missing = chosen == null;
            onImage(img, () -> plate.setIcon(missing
                ? crossedOut(img, 22) : new ImageIcon(img.getScaledInstance(-1, 22, Image.SCALE_SMOOTH))));
        } else {
            plate.setText("-");
            plate.setForeground(DIM);
        }
        return plate;
    }

    // Dragonfire protection beside the poison plan (Andrew 2026-09-09:
    // "follow suit"): the shield the set must carry, the antifire potion
    // or the super antifire; click picks. Replaces the chip-row pill.
    JLabel firePlate(boolean bis) {
        String[] names = {text("fire.shield"), "Antifire potion", "Super antifire"};
        return optionPlate("Dragonfire", names, names, new int[]{11283, 2452, 21978}, bis ? 2 : id(pageParams, "antifireMode"), bis,
            text("fire.detect.tip"), "detect-antifire", i -> commands.set("antifireMode", i));
    }

    // The fire-spell rune assumption (Kendyy, Discord 2026-10-19): sunfire
    // runes give the spell a minimum hit of a tenth of its max. Seeded
    // from ownership at each selection; the ceiling always assumes them.
    JLabel sunfirePlate(boolean bis) {
        return optionPlate("Fire spell runes", new String[]{text("sunfire.on"), "fire runes"},
            new String[]{"Sunfire runes", "Fire runes"}, new int[]{28929, 554}, bis || flag(pageParams, "sunfireRunes") ? 0 : 1, bis,
            "Sunfire runes when you own any", "detect-sunfire", i -> commands.set("sunfireRunes", i == 0));
    }

    // A plate that picks one of a few items: the picked item's image and
    // name in the tooltip, the menu "Detect best" (the detect command) then
    // every option by its label, the pick handed over as its index.
    JLabel optionPlate(String what, String[] names, String[] labels, int[] items, int picked,
        boolean bis, String detectTip, String detectCommand, IntConsumer pick) {
        JLabel plate = plate(what, names[picked], bis, () -> {
            var menu = new JPopupMenu();
            item(menu, "Detect best", detectTip, () -> commands.send(detectCommand));
            for (int i = 0; i < labels.length; i++) {
                int value = i;
                item(menu, labels[i], () -> pick.accept(value));
            }
            return menu;
        });
        seatItem(plate, items[picked]);
        return plate;
    }

    // A header plate: a 24px square, "<what>: <name>" with the click hint
    // on the Yours side, where its menu (built once) drops from it.
    static JLabel plate(String what, String name, boolean bis, Supplier<JPopupMenu> menu) {
        JLabel plate = square(24);
        tipText(plate, what + ": " + name + (bis ? "" : " - click to change"));
        if (!bis) {
            JPopupMenu built = menu.get();
            onClick(plate, () -> show(built, plate));
        }
        return plate;
    }

    // The first row of the given category, or null.
    static Map byCategory(List<Map> rows, String category) {
        for (Map row : rows) {
            if (category.equals(str(row, "category"))) return row;
        }
        return null;
    }

    // An item image scaled into a 24px frame (the raw 36x32 overflowed it,
    // field 2026-08-21); the onLoaded re-seat is the async-image contract.
    void seatItem(JLabel target, int itemId) {
        AsyncBufferedImage img = itemManager.getImage(itemId);
        onImage(img, () -> target.setIcon(new ImageIcon(img.getScaledInstance(-1, 22, Image.SCALE_SMOOTH))));
    }

    JLabel smallItemCell(int itemId, String tooltip) {
        JLabel cell = square(26);
        tipText(cell, tooltip);
        AsyncBufferedImage img = itemManager.getImage(itemId);
        onImage(img, () -> cell.setIcon(icon(img, 24)));
        return cell;
    }

    static JLabel statLine(String text, String tooltip, Color color) {
        JLabel line = small(text, color);
        tipText(line, tooltip);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        return line;
    }

    // A bank-action toggle in the classic look (flat, rounded, accent on
    // select): selecting it sends the command with the args built then;
    // deselecting sends it empty, which clears.
    JToggleButton bankToggle(String text, String tip, String command, Supplier<Map> args) {
        var button = new JToggleButton(text);
        tipText(button, tip);
        button.setFocusable(false);
        button.setContentAreaFilled(false);
        button.setForeground(LIGHT);
        button.setBorder(new RoundedBorder(MEDIUM_GRAY_COLOR, 3, 10));
        button.addItemListener(e -> {
            boolean on = button.isSelected();
            button.setForeground(on ? Color.WHITE : LIGHT);
            button.setBorder(new RoundedBorder(on ? ACCENT : MEDIUM_GRAY_COLOR, 3, 10));
        });
        button.addActionListener(e -> commands.send(command, button.isSelected() ? args.get() : Map.of()));
        return button;
    }

    static JLabel styleHeader(String style, Map node) {
        String name = cap(style);
        double factor = num(node, "multiFactor");
        if (factor > 0) name += factor == rint(factor) ? format(" x%d", (int) factor) : format(" x%.2f", factor);
        var label = new JLabel(name);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize() - 1f));
        String boost = str(node, "boostLabel");
        if (boost != null) tipText(label, "Assumes: " + boost);
        return label;
    }

    JPanel side(Map card, boolean bis, double td2,
        Map mob, String tab, Map ship) {
        JPanel panel = column(CARD);
        if ("magic".equals(tab) && !bis) {
            String pinnedSpell = str(mob, "pinnedSpell");
            boolean pinned = pinnedSpell != null && !pinnedSpell.isEmpty();
            String ss = pinned ? pinnedSpell : str(card, "spell");
            Map spellSprites = map(assumeOptions, "spellSprites");
            int shownSprite = ss == null ? -1 : id(spellSprites, ss);
            JPanel sr = darker(new FlowLayout(FlowLayout.CENTER, 4, 0));
            JLabel spellIcon = square(22);
            if (shownSprite > 0) {
                spellIcon.setIcon(cachedSprite(shownSprite));
            } else seatSprite(spellIcon, SpriteID.TAB_MAGIC, 18);
            String lock = str(pageParams, "spellbookLock");
            String lockText = flag(mob, "cureMe") ? " - lunar book (Cure Me)"
                : lock == null || lock.isEmpty() ? "" : " - " + lock + " book";
            JLabel spellName = small((ss == null ? "Auto spell" : ss + (pinned ? " (pinned)" : "")) + lockText, TEXT);
            var openSpells = new MouseAdapter() {
                public void mouseClicked(MouseEvent e) {
                    // The SPELLBOOK grid (the prayer-book pattern - the list
                    // form scrolled off the screen): sprite cells six wide,
                    // names in tooltips, the pick on the accent plate.
                    var menu = new JPopupMenu();
                    menu.add(check("Auto spell (best castable)", !pinned, () -> commands.send("set-pinned-spell", Map.of("name", ""))));
                    menu.addSeparator();
                    // The spellbook lock (Apathy, Discord 2026-09-06: "use to be
                    // a way to set a specific spellbook"): any book, or one
                    // of the four. A book with no autocast spell (Lunar) leaves
                    // the card to the powered staves.
                    JPanel books = rack(5);
                    String current = lock == null ? "" : lock;
                    for (String option : new String[]{"", "standard", "ancient", "lunar", "arceuus"}) {
                        boolean any = option.isEmpty();
                        books.add(pickCell(menu, any ? null : cachedSprite(bookSprite(option)),
                            any ? "Any book (auto)" : "Lock to the " + option + " book", option.equals(current), 26,
                            () -> commands.set("spellbookLock", option)));
                    }
                    menu.add(books);
                    menu.addSeparator();
                    JPanel book = rack(6);
                    for (Object spell : list2(pageWide, "spells")) {
                        String option = String.valueOf(spell);
                        boolean picked = option.equals(pinnedSpell);
                        int sprite = id(spellSprites, option);
                        // The picker cell, with "pinned" where the pickers say "assumed".
                        JLabel cell = pickCell(menu, sprite > 0 ? cachedSprite(sprite) : null, option, picked, 26,
                            () -> commands.send("set-pinned-spell", Map.of("name", option)));
                        tipText(cell, option + (picked ? " (pinned)" : ""));
                        book.add(cell);
                    }
                    menu.add(book);
                    show(menu, (Component) e.getSource());
                }
            };
            for (JLabel part : new JLabel[]{spellIcon, spellName}) {
                sr.add(part);
                tipText(part, "The autocast spell - click to pin one");
                part.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                part.addMouseListener(openSpells);
            }
            panel.add(left(sr));
        }
        // The classic card centre: the cross LEFT, the stat column RIGHT.
        JPanel center = darker(new FlowLayout(FlowLayout.LEFT, 8, 2));
        center.add(gearGrid(card, bis, mob, td2));
        JPanel sc = column(CARD);
        Map incoming = map(card, "incoming");
        Map si = map(ship, "incoming");
        int mh = id(card, "maxHit");
        sc.add(paintedStatLine("" + mh, "Max hit " + mh, TEXT, new StatIcons.SizedIcon("hitsplat", 12)));
        sc.add(paintedStatLine(round(num(card, "accuracy") * 100) + "%",
            "Hit chance", TEXT, new StatIcons.SizedIcon("crosshair", 13)));
        if (si != null) {
            // Sea mobs hit the BOAT (REQ-SC-15): the number is ship damage
            // taken for the picked keel, protection prayers do not apply,
            // and clicking the line picks the keel (Andrew: "a drop down
            // next to the dtps value").
            String keel = str(si, "keel");
            int km = id(si, "maxHit");
            String kn = "none".equals(keel) ? "no keel" : cap(keel) + " keel";
            // As terse as the land incoming line: the column fits ~12
            // characters (designer pass 2026-09-02: "~1.0 (max 5, Rune)"
            // clipped); the keel lives in the tooltip and the click menu.
            JLabel sl = statIconLine( shipDamageText(num(si, "dtps"), km), km == 0
                    ? format(text("ship.nohit"), kn) : format(text("ship.hit"), kn, km), TEXT, -1);
            sl.setForeground(SEA);
            Icon sail = sailingIconSmall();
            if (sail != null) {
                sl.setIcon(sail);
                sl.setIconTextGap(4);
            }
            onClick(sl, () -> {
                var menu = new JPopupMenu();
                for (String option : keels()) {
                    menu.add(check("none".equals(option) ? "No keel" : cap(option) + " keel", option.equals(keel),
                        () -> commands.set("shipKeel", option)));
                }
                show(menu, sl);
            });
            sc.add(sl);
        } else if (incoming != null) {
            // Classic honesty rule: an unmodeled monster's DTPS reads "?" -
            // silence must read as unknown, never safe.
            String[] line = dtpsLine(incoming);
            // The protect prayer's own sprite IS the pray call (classic).
            int protectSprite = "none".equals(str(incoming, "coverage")) ? -1 : id(incoming, "protectSprite");
            sc.add(statIconLine(line[0], line[1], TEXT, protectSprite));
            // The defensive mode's DPS cost, terse for the ~12-character
            // column; the full trade rides the tooltip.
            Map trade = map(card, "modeTrade");
            if (trade != null) {
                String mode = "TANKY".equals(str(trade, "mode")) ? "Tanky" : "Bal.";
                sc.add(statIconLine(str(trade, "reason") != null ? mode + " =max"
                        : mode + " " + cut(id(trade, "dpsLossPct")) + "%", modeLine(trade), new Color(0x9FD39F), -1));
            }
        }
        // How long the kill takes (ISS-01M6415DTE84ZZ45C4YD3MZRBJ): the median
        // on the line, the band on hover; a group lists every member, then
        // the trip (Andrew 2026-10-29: "in the output card itself").
        Map kill = map(card, "killTime");
        if (trip != null) {
            for (Map member : list(trip, "members")) {
                String[] ml = memberLine(member);
                sc.add(statLine(ml[0], ml[1], TEXT));
            }
            String[] tl = tripLine(trip);
            sc.add(statLine(tl[0], tl[1], TEXT));
        } else if (kill != null) {
            String[] kl = killLine(kill);
            sc.add(statLine("Kill " + kl[0], kl[1], TEXT));
        }
        Map stats = map(card, "stats");
        if (stats != null) sc.add(statIconLine("+" + id(stats, "prayer"), "Prayer bonus", TEXT, SpriteID.SKILL_PRAYER));
        String atkType = str(card, "attackType");
        if (atkType != null) {
            // "crush (controlled)" -> "Crush", stance in the hover.
            int paren = atkType.indexOf(" (");
            String styleWord = paren > 0 ? atkType.substring(0, paren) : atkType;
            String stance = paren > 0 ? atkType.substring(paren + 2).replace(")", "") : null;
            sc.add(statLine(cap(styleWord), stance == null ? "Attack style the numbers use"
                    : "Attack style the numbers use - " + stance + " stance", TEXT));
        }
        Map ia = map(card, "internalAmmo");
        if (ia != null) {
            JLabel dl = statImageLine(itemManager.getImage(id(ia, "id")),
                str(ia, "name"), text("dart.tip"), TEXT);
            Set<Integer> ei = new HashSet<>();
            Map pageCounts = map(pageWide, "counts");
            for (Map ex : list(pageCounts, "excludedItems")) ei.add(id(ex, "id"));
            int loadedId = id(ia, "id");
            var dm = new JPopupMenu();
            for (Map tier : list(pageWide, "dartTiers")) {
                int ti = id(tier, "id");
                String tierName = str(tier, "name");
                boolean excluded = ei.contains(ti);
                String label = (ti == loadedId ? "> " : "  ") + (excluded ? "Allow " : "Exclude ") + tierName;
                item(dm, label, () -> commands.send("toggle-exclusion", Map.of("itemId", ti)));
            }
            dl.setComponentPopupMenu(dm);
            sc.add(dl);
        }
        Map qan = map(card, "quiverAmmo");
        if (qan != null) {
            sc.add(statImageLine(itemManager.getImage(id(qan, "id")),
                str(qan, "name"), "Carried in the quiver", TEXT));
        }
        Map rn = map(card, "risk");
        if (rn != null) {
            JLabel rl = statLine("Risk " + Gp.formatShort( (int) min(Integer.MAX_VALUE, num(rn, "riskGp"))),
                "", new Color(0xDC7878));
            tipText(rl, tip( list("Kept on death:", list2(rn, "kept")), list("<br>Lost:", list2(rn, "lost"))));
            sc.add(rl);
        }
        int uc = id(card, "purchaseCost");
        if (uc > 0) {
            // The coin stack at COLUMN scale: the stack-tier sprite, but
            // scaled to the stat icons' 16px and seated in the same
            // fixed-width column so every value shares one left edge
            // (design note 2026-08-15: the raw 32px item image dwarfed
            // the painted icons).
            sc.add(statImageLine(itemManager.getImage(995, 10_000, false), Gp.formatShort(uc), text("cost.tip"), TEXT));
        }
        if (stats != null) {
            Map off = map(stats, "offensive");
            Map def = map(stats, "defensive");
            // One STATS icon, everything in the hover (field report
            // 2026-08-15: the compact lines were cut off and unreadable)
            // - a proper equipment-stats table, offensive and defensive
            // for every stat.
            String fb2 = format(text("stats.table"), id(off, "stab"), id(off, "slash"), id(off, "crush"),
                id(off, "magic"), id(off, "ranged"), id(def, "stab"), id(def, "slash"), id(def, "crush"),
                id(def, "magic"), id(def, "ranged"), id(stats, "strength"), id(stats, "rangedStrength"),
                id(stats, "magicDamage"), id(stats, "prayer"));
            JLabel statsLine = statIconLine("Stats", fb2, TEXT, SpriteID.TAB_COMBAT);
            // The line LOOKS like its clickable neighbours, so it answers a
            // click with the same table the hover shows (field report
            // 2026-08-31: "clicking the stats button doesn't do anything").
            String statsHtml = fb2;
            onClick(statsLine, () -> {
                var pop = new JPopupMenu();
                var body = new JLabel(statsHtml);
                body.setBorder(createEmptyBorder(6, 8, 6, 8));
                pop.add(body);
                show(pop, statsLine);
            });
            sc.add(statsLine);
        }
        List<Object> bonuses = list2(card, "counted");
        if (!bonuses.isEmpty()) {
            // Situational bonuses live IN the column (field report
            // 2026-08-14: 'slayer helm shown outside the 2x5') - the
            // count on the line, every bonus named in the tooltip.
            sc.add(statLine("+" + bonuses.size() + " bonus" + (bonuses.size() == 1 ? "" : "es"),
                tip(list("Counted for this set:", bonuses)), ACCENT));
        }
        center.add(sc);
        panel.add(left(center));
        // The open slot's ladder strip sits right under the grid (Yours only).
        String os = bis ? "" : ladderSlotOpen();
        Map openGear = map(card, "gear");
        // The spec cell's strip hangs off the card's spec node, not the gear.
        Map openNode = os.isEmpty() ? null
            : "spec".equals(os) ? map(map(card, "spec"), "weapon") : map(openGear, os);
        Map ol = map(openNode, "ladder");
        if (ol != null) {
            Map kinds = map(ol, "kinds");
            int page = id(pageParams, "ladderPage");
            if (kinds == null || kinds.isEmpty()) {
                panel.add(centered(ladderStrip(ol, wornColumn(openNode, ol), os, nerAvailable(), null, page)));
            } else {
                // The magic weapon slot shows two strips, powered staves and
                // autocast staves (ISS-01M5PKSQBAP823XNC13QBW8830, Andrew
                // 2026-10-24: "for magic just show two ladders: a powered and
                // a non powered one so we don't need to do the math of
                // combining them").
                for (String kind : (Iterable<String>) kinds.keySet()) {
                    Map kl = map(kinds, kind);
                    panel.add(centered(ladderStrip(kl, wornColumn(openNode, kl), os,
                        nerAvailable(), kind + " staves", page)));
                }
            }
        }
        return panel;
    }

    // A strip's worn column: the slot's own node with the ladder's base figures.
    static Map wornColumn(Map node, Map ladder) {
        Map worn = new LinkedHashMap<>(node);
        worn.put("base", str(ladder, "base"));
        worn.put("base2", str(ladder, "base2"));
        worn.put("groupBase", str(ladder, "groupBase"));
        return worn;
    }

    // The worn-equipment silhouette (5 rows x 3): blank corners, the
    // spec weapon in the empty cell left of the legs, quiver ammo in
    // the corner right of the legs - the classic panel's iconic grid
    // (BankLayout lays the bank out from the same rows).
    static final String[] CROSS = {
        null, "head", null, "cape", "neck", "ammo", "weapon", "body", "shield", "#spec", "legs", "#quiver", "hands", "feet", "ring", };

    JPanel gearGrid(Map card, boolean bis, Map mob, double td2) {
        JPanel grid = darker(new GridLayout(5, 3, 2, 2));
        Map gear = map(card, "gear");
        Map quiver = map(card, "quiverAmmo");
        for (String slot : CROSS) {
            Map item = "#spec".equals(slot) ? map(map(card, "spec"), "weapon")
                : "#quiver".equals(slot) ? quiver : slot == null ? null : map(gear, slot);
            if (item == null) {
                // Empty slots are truly EMPTY (field ask 2026-08-21,
                // superseding the 2026-08-15 faint dot): a sized blank
                // keeps the cross geometry, nothing draws.
                var blank = new JLabel();
                blank.setPreferredSize(new Dimension(36, 36));
                grid.add(blank);
            } else {
                String slotName = "#spec".equals(slot) ? "spec" : "#quiver".equals(slot) ? "quiver" : slot;
                grid.add(itemCell(item, slotName, bis, card, mob, td2));
            }
        }
        JPanel holder = darker(new FlowLayout(FlowLayout.LEFT, 0, 2));
        holder.add(grid);
        return holder;
    }

    // The classic storage palette - only FETCH-TRIP sources have an
    // entry, so at-hand gear can never draw a dot.
    static final Map<String, Color> SOURCE_COLORS = new LinkedHashMap<>();

    static {
        JsonObject colours = JsonResources.objectOrThrow("source_colours.json");
        for (String source : colours.keySet()) {
            if (!source.startsWith("_")) SOURCE_COLORS.put(source, new Color(Integer.parseInt(colours.get(source).getAsString(), 16)));
        }
    }

    static Color sourceColor(String source) {
        if (source == null) return null;
        for (var entry : SOURCE_COLORS.entrySet()) {
            if (source.startsWith(entry.getKey())) return entry.getValue();
        }
        return null;
    }

    // The classic palette (relocated with the border language).
    static final Color CELL_BG = new Color(0x323232);
    static final Color BORDER_BIS = new Color(0xA89458);
    static final Color BORDER_SPEC = new Color(0x78BEF0);
    static final Color BORDER_PLAIN = new Color(0x464646);
    static final Color BORDER_UNOWNED = new Color(0x649164);

    // card and mob feed the spec cell's reconciliation ledger and pin menu,
    // and every cell's pin badge and "needed" note.
    JLabel itemCell(Map item, String slot, boolean bis, Map card,
        Map mob, double td2) {
        JLabel cell = square(36);
        String name = str(item, "name");
        int id = id(item, "id");
        boolean spec = "spec".equals(slot);
        boolean assumed = flag(item, "assumed");
        boolean bm = flag(item, "bisMatch");
        // Border language (classic, field note 2026-08-05: every colour
        // has to EARN its cell): gold = owned and best-available, green =
        // assumed (simmed / unowned on Yours), blue = the spec cell,
        // grey otherwise.
        Color border = spec ? BORDER_SPEC : assumed ? BORDER_UNOWNED : bm ? BORDER_BIS : BORDER_PLAIN;
        int ip = id(item, "price");
        String pn = flag(item, "owned") || ip <= 0 ? "" : " - " + Gp.formatShort(ip) + " gp";
        // The reconciliation breakdown on the spec cell (field ask
        // 2026-08-21): our shown number folds spec + thralls; the wiki
        // calc shows the bare set - spell out the sum so the two always
        // reconcile. tipBody is shared with the storage/fate overlay.
        String tb;
        Map sn = map(card, "spec");
        int swi = id(map(sn, "weapon"), "id");
        boolean sp = swi != 0 && id(mob, "pinnedSpec") == swi;
        if (spec) {
            // The same ledger the Wiki calc chip's tooltip wears (field
            // ask 2026-08-21) - one fact per row. The spec row shows even
            // at zero here: this cell IS the spec weapon.
            tb = "<b>" + name + " (spec)</b>" + (sp ? " <font color='#969696'>pinned</font>" : "") + pn + "<br><br>"
                + ledger(num(card, "dps"), "what the wiki calc shows", num(sn, "dpsAdded"), true, td2);
        } else {
            tb = name + " (" + slot + ")" + (!bis && assumed && hasId(list2(mob, "requiredGear"), id)
                    ? " - " + text("cell.needed") : assumed ? " - assumed (not owned)" : bm ? " - best available" : "") + pn;
        }
        tipText(cell, "<html>" + tb + "</html>");
        Runnable exclude = () -> commands.send("toggle-exclusion", Map.of("itemId", id, "label", name));
        if (spec && !bis && swi != 0) {
            // The pin lives ON the cell now (the spec text line retired).
            var sm = new JPopupMenu();
            item(sm, sp ? "Unpin spec" : "Pin as spec for this mob",
                () -> commands.send("set-pinned-spec", Map.of("itemId", sp ? 0 : swi)));
            item(sm, "Exclude " + name, exclude);
            cell.setComponentPopupMenu(sm);
        }
        cell.setOpaque(true);
        cell.setBackground(CELL_BG);
        cell.setBorder(createLineBorder(border));
        itemManager.getImage(id).addTo(cell);
        Color dot = sourceColor(str(item, "source"));
        String fate = str(item, "fate");
        boolean pinnedHere = !bis && !spec && pinnedOn(list(mob, "mobPins"), slot, id);
        if (dot != null || fate != null || pinnedHere) {
            String where = str(item, "source");
            tipText(cell, "<html>" + tb + (pinnedHere ? text("cell.pinnedhere") : "")
                + (where == null ? "" : "<br>stored in " + where) + ("kept".equals(fate) ? "<br>protected on death"
                    : "lost".equals(fate) ? "<br>lost on death (" + Gp.format((int) min(Integer.MAX_VALUE,
                            num(item, "fateGp"))) + " gp)" : "") + "</html>");
            cell.setUI(new BasicLabelUI() {
                public void paint(Graphics g, JComponent c) {
                    super.paint(g, c);
                    Graphics2D g2 = smooth(g);
                    if (dot != null) {
                        // The classic source dot: bottom-right corner.
                        g2.setColor(dot);
                        g2.fillOval(c.getWidth() - 8, c.getHeight() - 8, 5, 5);
                    }
                    if (fate != null) {
                        // Fate mark, top-left: green = kept on death,
                        // red = dropped to the killer.
                        g2.setColor("kept".equals(fate) ? new Color(0x78C878) : new Color(0xDC6E6E));
                        g2.fillRect(3, 3, 4, 4);
                    }
                    if (pinnedHere) {
                        // The pin badge, top-right: a gold head and its point.
                        g2.setColor(PIN_BADGE);
                        g2.fillOval(c.getWidth() - 9, 3, 6, 6);
                        g2.drawLine(c.getWidth() - 7, 8, c.getWidth() - 9, 11);
                    }
                    g2.dispose();
                }
            });
        }
        if (!bis && map(item, "ladder") != null) {
            // A left click opens (or closes) this slot's ladder strip under
            // the grid; right click keeps its menu.
            boolean open = slot.equals(ladderSlotOpen());
            tipText(cell, cell.getToolTipText().replace("</html>", format(text("cell.swaps"), (open ? "hide" : "show"))));
            onLeftClick(cell, () -> commands.set("ladderSlot", open ? "" : slot));
        }
        if (spec && cell.getComponentPopupMenu() != null) {
            // The spec cell keeps its own menu: the slot menu below pins
            // and unpins GEAR slots, and "spec" is none (it replaced the
            // pin-as-spec menu before 2026-10-22).
            return cell;
        }
        var menu = new JPopupMenu();
        item(menu, "Exclude " + name, exclude);
        if (bis) {
            item(menu, "Sim as owned", "Pretend you own " + name + " and recompute your side",
                () -> commands.send("toggle-sim", Map.of("itemId", id, "label", name)));
        } else if (!"quiver".equals(slot)) {
            item(menu, "Exclude for this mob", "Exclude " + name + " for this mob only (all sets)",
                () -> commands.send("exclude-for-mob", Map.of("itemId", id, "scope", "ALL", "label", name)));
            item(menu, "Sim for this mob (search)...", text("menu.simmob.tip"), searchItem("Sim for this mob", "sim-for-mob"));
            // The classic GLOBAL sibling (field report 2026-08-21: it fell
            // off in the merge-back - only the per-mob scope survived).
            item(menu, "Sim for all mobs (search)...", text("menu.simall.tip"), searchItem("Sim as owned", "toggle-sim"));
            item(menu, "Pin " + name, "Force this item into the " + slot + " slot for this mob (all sets)",
                () -> commands.send("pin", Map.of("slot", slot, "itemId", id)));
            item(menu, "Pin another item (search)...", () -> picker.search("Pin in " + slot,
                (pickedId, pickedName) -> commands.send("pin", Map.of("slot", slot, "itemId", pickedId))));
            item(menu, "Unpin " + slot, () -> commands.send("unpin", Map.of("slot", slot)));
            if ("hands".equals(slot)) {
                // The slayer-task bracelet setting, reachable from the slot
                // it fills (miko 2026-10-17; the settings panel has it too).
                String current = str(map(pageWide, "counts"), "taskBracelet");
                for (String[] choice : new String[][]{{"NONE", "none"},
                    {"EXPEDITIOUS_BRACELET", "Expeditious bracelet"},
                    {"BRACELET_OF_SLAUGHTER", "Bracelet of slaughter"}}) {
                    menu.add(check("On slayer tasks: " + choice[1], choice[0].equals(current),
                        () -> commands.send("set-task-bracelet", Map.of("choice", choice[0]))));
                }
            }
        }
        cell.setComponentPopupMenu(menu);
        return cell;
    }

    // One rune stack's name and the reason it rides, merged per rune type.
    static void noteRune(Map<Integer, String> names, Map<Integer, StringBuilder> why, Map rune, String reason) {
        int id = id(rune, "id");
        names.put(id, str(rune, "name"));
        why.computeIfAbsent(id, k -> new StringBuilder()) .append(why.get(id).length() == 0 ? "" : "; ").append(reason);
    }

    // A fixed-size label with its content centred - the cell every icon
    // seat starts from.
    static JLabel square(int size) {
        var cell = new JLabel();
        cell.setPreferredSize(new Dimension(size, size));
        cell.setHorizontalAlignment(SwingConstants.CENTER);
        return cell;
    }

    // The picker grid's rack: n cells wide on the dark ground.
    static JPanel rack(int columns) {
        JPanel rack = darker(new GridLayout(0, columns, 2, 2));
        rack.setBorder(createEmptyBorder(4, 6, 4, 6));
        return rack;
    }

    // A square picker cell, lit when picked.
    static JLabel pickedCell(int size, boolean picked) {
        JLabel cell = square(size);
        cell.setOpaque(true);
        cell.setBackground(picked ? new Color(0x2e, 0x40, 0x2e) : DARKER_GRAY_COLOR);
        cell.setBorder(createLineBorder(picked ? ACCENT : DARKER_GRAY_HOVER_COLOR));
        return cell;
    }

    static JPanel left(JComponent inner) {
        JPanel row = darker(new BorderLayout());
        row.add(inner, WEST);
        return row;
    }

    // left()'s centred sibling (field asks 2026-08-20) - FlowLayout so
    // the inner keeps its preferred size instead of stretching.
    static JPanel centre(JComponent inner) {
        JPanel row = darker(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.add(inner);
        return row;
    }
}
