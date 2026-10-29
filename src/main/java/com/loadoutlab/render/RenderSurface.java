package com.loadoutlab.render;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.*;
import javax.swing.*;
import javax.swing.Timer;
import net.runelite.client.ui.*;
import java.awt.event.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.event.DocumentEvent;
import net.runelite.client.game.SpriteManager;
import static com.loadoutlab.render.Model.*;
import static com.loadoutlab.render.ResultCards.*;
import static com.loadoutlab.render.Ui.*;
import static javax.swing.BorderFactory.*;
import static net.runelite.client.ui.ColorScheme.*;
import net.runelite.client.util.LinkBrowser;
import net.runelite.api.SpriteID;
import javax.swing.event.DocumentListener;
import java.util.regex.Pattern;
import java.awt.image.BufferedImage;
import java.awt.datatransfer.StringSelection;
import static java.awt.BorderLayout.*;

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
    final ResultCards cards;
    final Supplier<Map> page;
    final CommandSink commands;
    final ItemPicker picker;
    final Searcher searcher;
    @lombok.Setter
    SpriteManager spriteManager;

    /** The compute animation's pool: a sea lens draws the sea moods; a
     * raid MONSTER its raid's - they live nowhere else, so a single Zebak
     * search charges the Obelisk (Andrew 2026-09-02); a Zulrah lens hers;
     * anything else the land flask. */
    /** Monster-name prefix -> pool key, from mood_routes.tsv (Andrew
     * 2026-09-03: ten bosses join Zulrah; the raids route by group data). */
    static final Map<String, String> MOOD_ROUTES = tsv("mood_routes.tsv");

    static String moodKey(Map page) {
        Map mob = lensedMob(page);
        if (flag(mob, "naval")) return "sea";
        String raid = str(mob, "raid");
        if (raid != null) return raid;
        String name = str(mob, "name");
        if (name != null) {
            for (var route : MOOD_ROUTES.entrySet()) {
                if (name.startsWith(route.getKey())) return route.getValue();
            }
        }
        return null;
    }

    /** The lensed mob of the first entry that has mobs, or null. */
    static Map lensedMob(Map page) {
        int lens = id(firstParams(page), "lensIndex");
        for (Map entry : list(page, "entries")) {
            List<Map> em = list(entry, "mobs");
            if (!em.isEmpty()) return em.get(Math.min(Math.max(lens, 0), em.size() - 1));
        }
        return null;
    }

    /** Config gates the renderer honours: the compute animation and the
     * wilderness risk controls. Both settings described behaviour they
     * did not control until 2026-08-22 - the merge-back dropped the
     * DisplayOptions bridge and nothing replaced it. */
    BooleanSupplier animationGate = () -> true;
    BooleanSupplier wildyRiskGate = () -> true;
    /** The "PvP world chip" setting: show it on every card. */
    BooleanSupplier pvpChipGate = () -> false;

    public void setDisplayGates(BooleanSupplier animation, BooleanSupplier wildyRisk, BooleanSupplier pvpChip) {
        animationGate = animation;
        wildyRiskGate = wildyRisk;
        pvpChipGate = pvpChip;
    }
    JPanel root;
    JPanel matchesBox;
    /** The classic search cadence: 150ms debounce, 2+ characters. */
    final Timer searchDebounce = once(150, e -> runSearch());
    JTextField search;
    JPanel cardArea;
    JPanel waitingSlot;
    AsciiLoader loader;
    volatile boolean isComputing;

    /** Compute-in-flight: shows the static Computing... slot. */
    public void setComputing(boolean computing) {
        isComputing = computing;
        SwingUtilities.invokeLater(() -> {
            if (waitingSlot == null) return;
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

    void runSearch() {
        if (search == null || matchesBox == null) return;
        String query = search.getText().trim();
        if (query.length() < 2) {
            showMatches(List.of());
            return;
        }
        searcher.search(query, matches -> SwingUtilities.invokeLater(() -> showMatches(matches)));
    }

    /** The dropdown rebuilt from the matches - folded away when there are none. */
    void showMatches(List<Map> matches) {
        matchesBox.removeAll();
        for (Map match : matches) matchesBox.add(matchRow(match));
        matchesBox.setVisible(!matches.isEmpty());
        root.revalidate();
        root.repaint();
    }

    /** One dropdown row: the label, group rows bold - click selects.
     * Full width, so the hover highlight spans the whole view. */
    JComponent matchRow(Map match) {
        var row = new JLabel(str(match, "label"));
        boolean group = match.get("group") != null;
        if (group) row.setFont(row.getFont().deriveFont(Font.BOLD));
        // Ship-combat rows announce themselves before selection: sea-blue
        // with the sailing skill icon after the name (REQ-SC-1/16; an image
        // icon, so the ASCII glyph gate stays clean).
        if (flag(match, "naval")) {
            row.setForeground(SEA);
            tipText(row, "Ship combat: fought from your boat");
            Icon sail = cards.sailingIconSmall();
            if (sail != null) {
                row.setIcon(sail);
                row.setHorizontalTextPosition(SwingConstants.LEADING);
                row.setIconTextGap(5);
            }
        }
        row.setBorder(createEmptyBorder(3, 8, 3, 8));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension( Integer.MAX_VALUE, row.getPreferredSize().height));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        row.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { pick(match); }

            public void mouseEntered(MouseEvent e) {
                row.setOpaque(true);
                row.setBackground(DARKER_GRAY_HOVER_COLOR);
                row.repaint();
            }

            public void mouseExited(MouseEvent e) {
                row.setOpaque(false);
                row.repaint();
            }
        });
        return row;
    }

    void pick(Map match) {
        Object group = match.get("group");
        if (group instanceof String) {
            commands.send("select", Map.of("query", group));
        } else {
            String pv = str(match, "version");
            commands.send("select", pv == null ? Map.of("id", id(match, "id"))
                : Map.of("id", id(match, "id"), "version", pv));
        }
        clearMatches();
    }

    /** The search box emptied and its dropdown folded away. */
    void clearMatches() {
        search.setText("");
        showMatches(List.of());
    }

    /** The reference Core stores; JDK types only cross the seam. */
    public Function<Map, JComponent> asFunction() { return page -> root(); }

    /** A new page (or hello) arrived - repaint the mounted root. */
    /** Rebuilds are COALESCED (freeze reports 2026-08-14: each publish
     * rebuilt the full tree synchronously, so any publish burst locked
     * the EDT for their combined duration - the dumps' signature was an
     * event-flooded EDT with huge cpu=). A 50ms debounce turns any storm
     * into at most ~20 rebuilds a second, and logs bursts so a publish
     * loop shows itself instead of freezing the client. */
    final AtomicInteger pendingRepaints = new AtomicInteger();
    Timer repaintDebounce;

    public void onModelChanged() {
        pendingRepaints.incrementAndGet();
        SwingUtilities.invokeLater(() -> {
            if (repaintDebounce == null) {
                repaintDebounce = once(50, e -> {
                    int burst = pendingRepaints.getAndSet(0);
                    if (burst > 20) {
                        log.warn("Loadout Lab UI: {} publishes coalesced into one rebuild" + " - a publish loop upstream?", burst);
                    }
                    repaint();
                });
            }
            repaintDebounce.restart();
        });
    }

    /** Boolean param chips: label -> params-node key. Rendered from the
     * model (never local state) - a chip is checked because Core says so. */
    static final String[][] CHIPS = {
        {"Spec", "specWeapon"},
        {"Thralls", "thralls"}, };

    /** Built per repaint; ResultCards mounts it under the mob list. */
    JButton addMobButton;
    JPanel chipRow;
    /** The dev-mode Max DPS | Balanced | Tanky control's own row. */
    JPanel modeRow;
    JPanel invRow;
    /** The stat sim's editor row, under the chips (ISS-01M5S1MXR1R6BV5FCWBREV6471). */
    JPanel statsRow;
    JPanel rosterArea;
    JPanel countsRow;
    JPanel footerRow;
    /** The Shortest Path link, above the footer (ISS-01M5TE7QAZYZQMTPA1JZ0BQN1Z). */
    JPanel pathRow;
    JButton undoButton;
    JButton redoButton;

    /** Uniform chip config: every control in the row shares it. */
    static JButton chip(String text, String tooltip, Runnable onClick) {
        var button = new JButton(text);
        button.setFocusable(false);
        button.setMargin(new Insets(1, 6, 1, 6));
        if (tooltip != null) tipText(button, tooltip);
        button.addActionListener(e -> onClick.run());
        return button;
    }

    static final Color CHIP_ON = ACCENT;
    static final Color CHIP_OFF = new Color(0x969696);

    /** The classic parameter pill: flat, rounded, accent when active. */
    static JLabel pill(String text, boolean on, String tooltip, Runnable onClick) {
        return pill(text, on, !on, tooltip, onClick);
    }

    /** A classic pill that paints its new state IMMEDIATELY on click
     * (field report 2026-08-12: "tiles don't highlight until the search
     * is complete") - the arriving page then rewrites every pill from
     * Core's truth, so an optimistic guess that Core refuses corrects
     * itself on the next publish. */
    static JLabel pill(String text, boolean on, boolean po, String tooltip, Runnable onClick) {
        var label = new JLabel(text);
        paintPill(label, on);
        tipText(label, tooltip);
        onClick(label, () -> {
            paintPill(label, po);
            label.repaint();
            onClick.run();
        });
        return label;
    }

    /** The Task chip: the slayer skill icon + "Task" (field ask
     * 2026-08-15), always the FIRST chip. */
    JLabel taskPill(boolean on, boolean po, String tooltip, Runnable onClick) {
        JLabel pill = pill("Task", on, po, tooltip, onClick);
        if (spriteManager != null) {
            spriteManager.getSpriteAsync(SpriteID.SKILL_SLAYER, 0, img -> SwingUtilities.invokeLater(() -> {
                    pill.setIcon(icon(img, 14));
                    pill.setIconTextGap(4);
                }));
        }
        return pill;
    }

    static void paintPill(JLabel label, boolean on) {
        label.setForeground(on ? CHIP_ON : CHIP_OFF);
        label.setBorder(new RoundedBorder(on ? CHIP_ON : MEDIUM_GRAY_COLOR, 2, 8));
    }

    /** The defensive mode as one 3-way control in the chip area (Andrew
     * 2026-10-21: "a 3/way tab instead of a chip but in the same area"; a
     * filled square tab "looks bad" - picked look B): one rounded pill
     * outline like the chips, three words split by thin bars, the active
     * word in the accent. */
    /** The mode control's row: centered, with a thin divider below that
     * sets it apart from the chip row. */
    static JPanel modeRowPanel() {
        JPanel row = panel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        row.setBorder(createCompoundBorder( createEmptyBorder(2, 8, 4, 8), createMatteBorder(0, 0, 1, 0, MEDIUM_GRAY_COLOR)));
        row.setVisible(false);
        return row;
    }

    static JPanel modeTabs(String mode, CommandSink commands) {
        // What each mode optimises and what the card and the swaps show in it
        // (Andrew 2026-10-22: "better descriptions to what the differences
        // are and what you'll see in regular/ladder results").
        String[][] options = {
            {"MAX_DPS", "Max DPS", text("mode.max")},
            {"BALANCED", "Balanced", text("mode.balanced")},
            {"TANKY", "Tanky", text("mode.tanky")}};
        JPanel tabs = clear(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabs.setBorder(new RoundedBorder(MEDIUM_GRAY_COLOR, 2, 4));
        List<JLabel> words = new ArrayList<>();
        for (String[] option : options) {
            if (!words.isEmpty()) {
                var bar = new JLabel("|");
                bar.setForeground(new Color(0x505050));
                tabs.add(bar);
            }
            var word = new JLabel(option[1]);
            word.setName(option[0]);
            word.setFont(FontManager.getRunescapeBoldFont());
            word.setBorder(createEmptyBorder(1, 7, 1, 7));
            tipText(word, option[2]);
            word.setForeground(option[0].equals(mode) ? CHIP_ON : CHIP_OFF);
            onClick(word, () -> {
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
    static String viewedStandDown(Map page) {
        String tab = effectiveTab(page);
        Map styles = map(lensedMob(page), "styles");
        Map node = tab == null ? null : map(styles, tab);
        return str(map(node, "yours"), "arceuusStandDown");
    }

    /** A chip shown but off: greyed, the reason in its tooltip, no click. */
    static JLabel disabledPill(String text, String tooltip) {
        var label = new JLabel(text);
        paintPill(label, false);
        label.setEnabled(false);
        tipText(label, tooltip);
        return label;
    }

    static final String[] SIM_KEYS = {"simAttack", "simStrength", "simDefence", "simRanged", "simMagic", "simPrayer", "simHitpoints"};
    static final String[] SIM_ARGS = {"attack", "strength", "defence", "ranged", "magic", "prayer", "hitpoints"};
    static final String[] SIM_NAMES = {"Att", "Str", "Def", "Rng", "Mag", "Pray", "HP"};
    /** Whether the stat sim's row is open - a view choice kept across publishes. */
    boolean statsOpen;
    /** The "+ Requirement" editor row and whether it is open (ISS-01M5XC7XYMMEN3Q4QC6CGZFVPK). */
    JPanel reqRow;
    /** The requirements as last published: Add and the chips' remove read it at
     * click time, so a second Add before the redraw appends instead of replacing. */
    String reqLive = "";
    static final String[] REQ_OPS = {">=", ">", "<=", "<"};
    /** Each stat's game icon and its badge (-1 none): the combat-style and
     * stat icons, a defence shield on the defence stats, strength on the
     * damage stats (Andrew 2026-10-27: "more symbol based"). */
    static final int[][] REQ_ICONS = {{240, -1}, {238, -1}, {255, -1}, {202, -1}, {200, -1}, {240, 199}, {238, 199},
        {255, 199}, {202, 199}, {200, 199}, {198, -1}, {200, 198}, {202, 198}, {201, -1}, {649, -1}};
    /** Requirement stats (the engine's SetRules.STATS keys) and their chip labels, from text.tsv. */
    static final String[] REQ_KEYS = text("req.keys").split(","), REQ_NAMES = text("req.stats").split(",");

    /** The stat sim chip's label (ISS-01M5T4Y0N8S474ACHVFBBCQTZJ, Andrew
     * 2026-10-25: "by default it should say stats: yours and if it's running
     * modified stats it should say stats: custom"); the levels ride the tooltip. */
    static String statsLabel(int simmed, boolean cape) {
        return simmed > 0 || cape ? "Stats: custom" : "Stats: yours";
    }

    /** The stat sim chip (ISS-01M5QY13K6YHTYDW1CCYD30CSY, Andrew 2026-10-24:
     * "sim as any stat, defaulted to your current ones"): it toggles the stat
     * row under the Inv slider (ISS-01M5S1MXR1R6BV5FCWBREV6471) - seven boxes in a
     * fixed grid holding the simmed or your live level, a Max cape tick, Max
     * (all 99 and the cape) and Mine. Every edit is one sim-stats command; a
     * box left at your own level sends 0 = yours, so a level-up keeps
     * following you. */
    /** "prayer>=12" -> "Prayer >= 12" (ASCII: the chips render in the client's font). */
    static String reqLabel(String part) {
        var m = Pattern.compile("([a-z_]+)(>=|<=|>|<)(.*)").matcher(part);
        int i = m.matches() ? Arrays.asList(REQ_KEYS).indexOf(m.group(1)) : -1;
        return i < 0 ? part : REQ_NAMES[i] + " " + m.group(2) + " " + m.group(3);
    }

    /** The "+ Requirement" row: a stat, at least / at most, a number, Add. */
    List<String> reqParts() {
        return reqLive.isEmpty() ? new ArrayList<>() : new ArrayList<>(List.of(reqLive.split(";")));
    }

    /** Sends the list and keeps reqLive ahead of the redraw. */
    void setRequirements(List<String> parts) {
        reqLive = String.join(";", parts);
        commands.set("requirements", reqLive);
    }

    JComponent requirementEditor() {
        // The stat, the comparison and the number on one line, Add below it
        // (Andrew 2026-10-27: "the free-form add section numerical part is not
        // able to be seen ... put the add button below the line").
        JPanel row = panel(new GridBagLayout());
        JComboBox<String> stat = wideCombo(REQ_NAMES);
        stat.setPrototypeDisplayValue("Stab");
        tipText(stat, text("req.stat"));
        ImageIcon[] icons = new ImageIcon[REQ_NAMES.length];
        stat.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object v, int i, boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, v, i, sel, focus);
                int at = i >= 0 ? i : stat.getSelectedIndex();
                if (at >= 0 && icons[at] != null) {
                    setIcon(icons[at]);
                    // The list spells the name out; the closed box is the icon alone.
                    setText(i >= 0 ? String.valueOf(v) : "");
                }
                return this;
            }
        });
        for (int k = 0; k < REQ_ICONS.length; k++) statIcon(k, icons, stat);
        JComboBox<String> op = new JComboBox<>(REQ_OPS);
        op.setPrototypeDisplayValue(">=");
        tipText(op, text("req.op"));
        JTextField value = new JTextField(3);
        tipText(value, text("req.value"));
        Runnable send = () -> {
            String v = value.getText().trim();
            if (!v.matches("-?[0-9]+(\\.[0-9]+)?")) return;
            List<String> next = reqParts();
            next.add(REQ_KEYS[stat.getSelectedIndex()] + REQ_OPS[op.getSelectedIndex()] + v);
            setRequirements(next);
            value.setText("");
        };
        value.addActionListener(e -> send.run());
        JLabel add = small("Add", ACCENT);
        onClick(add, send);
        var c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 2, 3);
        row.add(stat, c);
        row.add(op, c);
        Dimension box = new Dimension(40, value.getPreferredSize().height);
        value.setPreferredSize(box);
        value.setMinimumSize(box);
        c.weightx = 1;
        c.insets = new Insets(0, 0, 2, 0);
        row.add(value, c);
        // Add centred under the whole line (Andrew 2026-10-27).
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = GridBagConstraints.REMAINDER;
        c.weightx = 0;
        c.anchor = GridBagConstraints.CENTER;
        c.fill = GridBagConstraints.NONE;
        row.add(add, c);
        return row;
    }

    /** A stat's icon, its badge drawn small in the corner, into icons[k]. */
    void statIcon(int k, ImageIcon[] icons, JComponent box) {
        if (spriteManager == null) return;
        spriteManager.getSpriteAsync(REQ_ICONS[k][0], 0, base -> {
            if (REQ_ICONS[k][1] < 0) {
                SwingUtilities.invokeLater(() -> { icons[k] = icon(base, 16); box.repaint(); });
                return;
            }
            spriteManager.getSpriteAsync(REQ_ICONS[k][1], 0, badge -> {
                var img = new BufferedImage(18, 18, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(icon(base, 15).getImage(), 0, 0, null);
                g.drawImage(icon(badge, 10).getImage(), 8, 8, null);
                g.dispose();
                SwingUtilities.invokeLater(() -> { icons[k] = new ImageIcon(img); box.repaint(); });
            });
        });
    }

    JComponent statsChip(Map params) {
        String mt = str(params, "myLevels");
        String[] mine = mt == null ? new String[0] : mt.split("/");
        String[] shown = new String[SIM_KEYS.length];
        var levels = new StringBuilder();
        int simmed = 0;
        for (int i = 0; i < SIM_KEYS.length; i++) {
            int sim = id(params, SIM_KEYS[i]);
            simmed += sim > 0 ? 1 : 0;
            shown[i] = sim > 0 ? String.valueOf(sim) : mine.length == 7 ? mine[i] : "?";
            levels.append(SIM_NAMES[i]).append(' ').append(shown[i]).append(i < 6 ? ", " : "");
        }
        boolean cape = flag(params, "simMaxCape");
        boolean on = simmed > 0 || cape;
        // Three columns (Att Str Def / Rng Mag Pray / HP) with every label as
        // wide as "Pray", so each box holds two digits and the boxes line up.
        JPanel grid = panel(new GridLayout(0, 3, 6, 2));
        int labelWidth = small("Pray", LIGHT).getPreferredSize().width;
        JTextField[] boxes = new JTextField[SIM_KEYS.length];
        var cb = new JCheckBox("Max cape", cape);
        Runnable send = () -> {
            Map args = new HashMap<>();
            for (int i = 0; i < boxes.length; i++) {
                int level = parseLevel(boxes[i].getText());
                args.put(SIM_ARGS[i], mine.length == 7 && String.valueOf(level).equals(mine[i]) ? 0 : level);
            }
            args.put("maxCape", cb.isSelected());
            commands.send("sim-stats", args);
        };
        for (int i = 0; i < boxes.length; i++) {
            boxes[i] = new JTextField(shown[i], 2);
            boxes[i].setToolTipText(SIM_NAMES[i] + " to sim (1-99)");
            boxes[i].addActionListener(e -> send.run());
            JPanel cell = panel(new BorderLayout(3, 0));
            JLabel name = small(SIM_NAMES[i], LIGHT);
            name.setPreferredSize(new Dimension(labelWidth, name.getPreferredSize().height));
            cell.add(name, WEST);
            cell.add(boxes[i], CENTER);
            grid.add(cell);
        }
        cb.setOpaque(false);
        cb.setForeground(LIGHT);
        cb.setFocusable(false);
        tipText(cb, "Sim the max cape, and the combos you can make with the capes you own");
        cb.addActionListener(e -> send.run());
        JLabel max = small("Max", ACCENT);
        onClick(max, () -> {
            for (JTextField box : boxes) box.setText("99");
            cb.setSelected(true);
            send.run();
        });
        JLabel ml = small("Mine", ACCENT);
        onClick(ml, () -> {
            Map args = new HashMap<>();
            for (String arg : SIM_ARGS) args.put(arg, 0);
            args.put("maxCape", false);
            commands.send("sim-stats", args);
        });
        JPanel foot = panel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        foot.add(cb);
        foot.add(max);
        foot.add(ml);
        statsRow.removeAll();
        statsRow.add(grid, NORTH);
        statsRow.add(foot, CENTER);
        statsRow.setVisible(statsOpen);
        return pill(statsLabel(simmed, cape), on, on,
            (on ? "Simmed " + levels + (cape ? " + max cape. " : ". ") : "") + text("chip.stats.tip"), () -> {
                statsOpen = !statsOpen;
                statsRow.setVisible(statsOpen);
                statsRow.getParent().revalidate();
            });
    }

    /** A box's level: 1-99, or 0 (= yours) for anything else. */
    static int parseLevel(String text) {
        try {
            int level = Integer.parseInt(text.trim());
            return level >= 1 && level <= 99 ? level : 0;
        } catch (NumberFormatException ex) { return 0; }
    }

    JLabel paramChip(String label, String key, boolean selected) {
        return pill(label, selected, label + (selected ? " on" : " off"), () -> commands.set(key, !selected));
    }

    /** True when any mob on the page passes the test. */
    static boolean anyMob(Map page, Predicate<Map> test) {
        for (Map entry : list(page, "entries")) {
            for (Map mob : list(entry, "mobs")) {
                if (test.test(mob)) return true;
            }
        }
        return false;
    }

    /** A classic count pill: "-N" red / "+N" green / "~N" grey, muted at
     * zero, always visible; click = the manage menu + add-by-search. */
    void pillChip(Map counts, String listKey, String sigil,
        Color[] palette, String removeVerb, String command, String ap, List<Map> sc) {
        List<Map> items = list(counts, listKey);
        var pill = new JLabel(sigil + items.size());
        boolean any = !items.isEmpty();
        pill.setForeground(any ? palette[0] : palette[1]);
        pill.setBorder(new RoundedBorder(any ? palette[2] : MEDIUM_GRAY_COLOR, 2, 22));
        onClick(pill, () -> {
            var menu = new JPopupMenu();
            // The classic grey chip also edits the SUPPLY DEFAULTS -
            // one submenu per category, plus Arceuus access.
            if (sc != null && !sc.isEmpty()) {
                for (Map category : sc) {
                    String key = str(category, "category");
                    String current = str(category, "current");
                    boolean detect = current == null || current.startsWith("DETECT");
                    var sub = new JMenu(str(category, "label"));
                    if (!"arceuusAccess".equals(key)) {
                        sub.add(supplyChoice(key, "DETECT_BEST", "Detect best", detect));
                        sub.add(supplyChoice(key, "NONE", "None", "NONE".equals(current)));
                    }
                    for (Map option : list(category, "options")) {
                        String ok = str(option, "key");
                        sub.add(supplyChoice(key, ok, str(option, "name"), ok.equals(current)
                            || ("arceuusAccess".equals(key) && "DETECT_BEST".equals(ok) && detect)));
                    }
                    menu.add(sub);
                }
                menu.addSeparator();
            }
            for (Map item : items) {
                int id = id(item, "id");
                String itemName = str(item, "name");
                item(menu, removeVerb + itemName, () -> commands.send(command, Map.of("itemId", id, "label", itemName)));
            }
            if (any) menu.addSeparator();
            item(menu, ap, cards.searchItem(ap, command));
            show(menu, pill);
        });
        countsRow.add(pill);
    }

    /** A pill that WEARS its value (field spec 2026-08-14: no bare text
     * entries in the chip row) - clicking swaps in the entry field;
     * Enter commits (the page repaint restores the pill), Escape just
     * collapses. */
    JComponent valueChip(String label, boolean active, String tooltip, String currentText, Consumer<String> onCommit) {
        var flip = new CardLayout();
        JPanel holder = panel(flip);
        var field = new JTextField(currentText, 5);
        tipText(field, tooltip);
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
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) flip.show(holder, "pill");
            }
        });
        flip.show(holder, "pill");
        return holder;
    }

    /** One radio-style supply-default choice (checked = current). */
    JMenuItem supplyChoice(String category, String key, String label, boolean selected) {
        return check(label, selected, () -> commands.send("set-supply-default", Map.of("category", category, "choice", key)));
    }

    synchronized JComponent root() {
        if (root == null) {
            root = panel(new BorderLayout(0, 6));
            JPanel top = column(DARK_GRAY_COLOR);
            countsRow = panel(new WrapLayout(FlowLayout.CENTER, 4, 2));
            top.add(countsRow);
            search = new JTextField();
            tipText(search, "Search a monster or group");
            // Suggestions LIVE as you type (150ms debounce) - but nothing
            // selects or computes until an explicit pick: a click on a
            // row, or Enter (field refinement 2026-08-15).
            search.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { searchDebounce.restart(); }

                public void removeUpdate(DocumentEvent e) { searchDebounce.restart(); }

                public void changedUpdate(DocumentEvent e) { searchDebounce.restart(); }
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
                if (query.length() < 2) return;
                searcher.search(query, matches -> SwingUtilities.invokeLater(() -> {
                    if (matches.size() == 1) {
                        matchesBox.removeAll();
                        pick(matches.get(0));
                        return;
                    }
                    showMatches(matches);
                }));
            });
            JPanel sa = panel(new BorderLayout(4, 0));
            // Compact history arrows ride BESIDE the search (the classic
            // header pattern), not loose in the chip row.

            undoButton = chip("<", "Undo", () -> commands.send("undo"));
            redoButton = chip(">", "Redo", () -> commands.send("redo"));
            JLabel cs = xLabel("Clear the search", this::clearMatches);
            cs.setBorder(createEmptyBorder(0, 4, 0, 6));
            var sb = new JPanel(new BorderLayout());
            sb.setBackground(new Color(0x2B2B2B));
            sb.setBorder(new RoundedBorder(new Color(0x504E46), 3, 4));
            search.setOpaque(false);
            search.setBorder(createEmptyBorder(2, 4, 2, 2));
            sb.add(search, CENTER);
            JPanel st = clear(new FlowLayout(FlowLayout.RIGHT, 2, 0));
            st.add(cs);
            for (JButton arrow : new JButton[]{undoButton, redoButton}) {
                arrow.setContentAreaFilled(false);
                arrow.setBorder(createEmptyBorder(2, 4, 2, 4));
                arrow.setForeground(CHIP_OFF);
                st.add(arrow);
            }
            sb.add(st, EAST);
            JPanel sr = darker(new BorderLayout(4, 0));
            sr.add(sb, CENTER);
            sa.add(sr, NORTH);
            matchesBox = column(DARKER_GRAY_COLOR);
            matchesBox.setVisible(false);
            sa.add(matchesBox, CENTER);
            top.add(sa);
            rosterArea = darker(new BorderLayout());
            rosterArea.setBorder(createEmptyBorder(4, 0, 2, 0));
            top.add(rosterArea);
            modeRow = modeRowPanel();
            top.add(modeRow);
            chipRow = panel(new WrapLayout(FlowLayout.LEFT, 4, 2));
            top.add(chipRow);
            // The inventory slider rides its own row under the chips.
            invRow = panel(new BorderLayout());
            top.add(invRow);
            statsRow = panel(new BorderLayout(0, 2));
            statsRow.setBorder(createEmptyBorder(2, 6, 4, 6));
            top.add(statsRow);
            reqRow = panel(new BorderLayout());
            reqRow.setBorder(createEmptyBorder(2, 6, 4, 6));
            top.add(reqRow);
            root.add(top, NORTH);
            JPanel ra = column(DARK_GRAY_COLOR);
            cardArea = column(DARK_GRAY_COLOR);
            ra.add(cardArea);
            // The computing notice sits BELOW everything the pending page
            // shows (field report: between the chips and the mob list was weird).
            waitingSlot = panel(new BorderLayout());
            loader = new AsciiLoader();
            JPanel lr = panel(new FlowLayout(FlowLayout.CENTER, 0, 4));
            lr.add(loader);
            waitingSlot.add(lr, CENTER);
            JLabel computing = label("Computing...", CHIP_OFF);
            computing.setHorizontalAlignment(SwingConstants.CENTER);
            waitingSlot.add(computing, SOUTH);
            waitingSlot.setVisible(false);
            ra.add(waitingSlot);
            root.add(ra, CENTER);
            // Footer actions (the classic position): below the results.
            footerRow = panel(new WrapLayout(FlowLayout.CENTER, 4, 2));
            pathRow = panel(new FlowLayout(FlowLayout.CENTER, 4, 2));
            JPanel south = panel(new BorderLayout());
            south.add(pathRow, NORTH);
            south.add(footerRow, CENTER);
            root.add(south, SOUTH);
        }
        repaint();
        return root;
    }

    /** The Wiki calc chip's tooltip: the base line, plus - when the
     * viewed card folds spec or thralls into its number - the sum that
     * reconciles our shown dps with what the calculator will display
     * (the bare set). Mirrors the engine's view derivation. */
    static String wikiCalcTip(Map page) {
        String head = text("wiki.head");
        String base = tip(head);
        Map params = firstParams(page);
        Map mob = lensedMob(page);
        Map styles = map(mob, "styles");
        if (params == null || styles == null) return base;
        boolean bis = flag(params, "viewingBis");
        String tab = str(params, "selectedTab");
        Map node = tab == null || tab.isEmpty() ? null : map(styles, tab);
        if (node == null) {
            double best = -1;
            for (String style : new String[]{"melee", "ranged", "magic"}) {
                Map candidate = map(styles, style);
                double d = candidate == null ? -1 : num(candidate, bis ? "bisTabDps" : "tabDps");
                if (candidate != null && d > best) {
                    best = d;
                    node = candidate;
                }
            }
        }
        Map card = map(node, bis ? "bis" : "yours");
        if (card == null) return base;
        double set = num(card, "dps");
        double sd = num(map(card, "spec"), "dpsAdded");
        double thrallsDps = thrallsFor(card, num(map(list(page, "entries").get(0), "thralls"), "dps"));
        // The breakdown reads as a little ledger, not a sentence (field
        // ask 2026-08-21: "less dense and more informative") - the same
        // table the spec cell wears, built once in Ui. ALWAYS shown
        // (Andrew 2026-09-02): the number they will see is the subtext.
        String ledger = ledger(set, "what the calc shows", sd, sd > 0, thrallsDps);
        // ToA: the link carries the card's raid level; say so, and where
        // their field lives for anyone who wants a different one.
        int invo = id(params, "toaInvocation");
        String toa = flag(mob, "invocationScaled") && invo > 0 ? String.format(text("wiki.toa"), invo) : "";
        return tip(head, "<br><br>", ledger, toa);
    }

    Map lastRenderedPage;
    int skippedIdentical;

    /** True when any entry of the page carries the flag. */
    static boolean anyEntry(Map page, String flag) {
        for (Map entry : list(page, "entries")) {
            if (flag(entry, flag)) return true;
        }
        return false;
    }

    synchronized void repaint() {
        if (cardArea == null) return;
        Map page = this.page.get();
        if (page != null && page.equals(lastRenderedPage)) {
            skippedIdentical++;
            return;
        }
        lastRenderedPage = page;
        long rebuildStart = System.nanoTime();
        Map params = firstParams(page);
        // The global counts build from the PAGE alone (field report
        // 2026-08-14 x2: the trio hid until a search - the idle page has
        // no entries, so the params gate swallowed it).
        countsRow.removeAll();
        Map counts = map(page, "counts");
        if (counts != null) {
            // The classic trio: always visible, muted at zero.
            pillChip(counts, "simmedItems", "+", SIM_PALETTE, "Stop simming ", "toggle-sim", "Sim an item as owned...", null);
            pillChip(counts, "excludedItems", "-", EXCLUDE_PALETTE, "Allow again: ", "toggle-exclusion",
                "Exclude an item (search)...", null);
            pillChip(counts, "filteredItems", "~", FILTER_PALETTE, "Stop filtering: ", "toggle-always-filter",
                "Always show in bank filter...", list(page, "supplyCatalog"));
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
        statsRow.setVisible(false);
        reqRow.setVisible(false);
        if (params == null) invRow.removeAll();
        if (params != null) {
            // Members chips (task, thralls, D charge) disappear under the
            // F2P lock - the computeArgs veto already keeps them out of the
            // math, and a control that can do nothing is noise (field ask
            // 2026-08-27). Their params survive for the untick.
            boolean f2pl = flag(params, "f2pOnly");
            Map lensed = lensedMob(page);
            boolean ln = flag(lensed, "naval");
            // Defensive modes (ISS-01M5G5CZC9SK46499FACRSJ8ZT), for every
            // player since 2026-10-22 (ISS-01M5JBYYWHQNTM0CGNYWQBNYX6). Its own
            // centered row above the chips (Andrew 2026-10-21: "more centered
            // in screen and distinguished from the chips").
            modeRow.add(modeTabs(str(params, "optimizeMode"), commands));
            modeRow.setVisible(true);
            // Arceuus casts blocked (Cure Me camps Lunar; a fight book elsewhere
            // with no swap - the Sire): the chips do nothing and hide, like on
            // a boat (Andrew 2026-09-09: "it should disable it").
            boolean ll2 = flag(lensed, "arceuusBlocked");
            String standDown = viewedStandDown(page);
            Map history = map(page, "history");
            if (history != null && undoButton != null) {
                undoButton.setEnabled(flag(history, "canUndo"));
                tipText(undoButton, str(history, "undoLabel") == null ? "Undo" : "Undo: " + str(history, "undoLabel"));
                redoButton.setEnabled(flag(history, "canRedo"));
                tipText(redoButton, str(history, "redoLabel") == null ? "Redo" : "Redo: " + str(history, "redoLabel"));
            }
            // Raids are never a slayer-task context (Andrew, 2026-08-31:
            // Skeletal Mystic's corpus slayer flag lit the chip for CoX).
            boolean raid = anyEntry(page, "raidSelection");
            boolean anyTaskOnly = !raid && anyMob(page, m -> flag(m, "taskOnly"));
            boolean anySlayer = !raid && anyMob(page, m -> flag(m, "slayerMonster"));
            boolean aw = anyMob(page, m -> flag(m, "wilderness"));
            boolean anyGwd = anyMob(page, m -> flag(m, "gwd"));
            // Every mob (and at least one) is a wilderness exclusive.
            boolean ae = anyMob(page, m -> true) && !anyMob(page, m -> !flag(m, "wildernessExclusive"));
            if (anyTaskOnly && !f2pl) {
                chipRow.add(taskPill(true, true, "Task-only boss - always on", () -> {
                    }));
            } else if (anySlayer && !f2pl) {
                chipRow.add(taskPill(flag(params, "onTask"), !flag(params, "onTask"), text("chip.task.tip"),
                    () -> commands.set("onTask", !flag(params, "onTask"))));
            }
            for (String[] entry : CHIPS) {
                // Thralls hide under the F2P lock AND for a sea lens (thrall
                // resurrections cannot be cast on a boat - confirmed
                // 2026-08-31; a chip that does nothing is noise).
                if (("thralls".equals(entry[1])) && (f2pl || ln || ll2)) continue;
                if ("thralls".equals(entry[1]) && standDown != null) {
                    chipRow.add(disabledPill(entry[0], "Stands down: " + standDown + " - thralls are cast from Arceuus"));
                    continue;
                }
                chipRow.add(paramChip(entry[0], entry[1], flag(params, entry[1])));
            }
            // F2P: shown on a non-members world so it can be turned OFF to
            // preview members gear (field report 2026-08-25 - the merge-back
            // dropped this chip, leaving the filter stuck on). Gated on
            // f2pWorld, NOT f2pOnly, or unticking it would hide the control.
            if (flag(params, "f2pWorld")) chipRow.add(paramChip("F2P", "f2pOnly", flag(params, "f2pOnly")));
            // The classic gates (field report 2026-08-14: Sire showed
            // Wildy + Raid boost): Wildy only for LISTED wilderness mobs
            // (exclusives pinned always-in), the raid chip only where the
            // raid supplies a boost - named after that potion.
            if (aw && ae) {
                chipRow.add(pill("Wildy", true, true, "Wilderness-exclusive - always in", () -> {
                    }));
            } else if (aw) {
                chipRow.add(paramChip("Wildy", "inWilderness", flag(params, "inWilderness")));
            }
            // GWD (ISS-01M5G9KPDQWBR014Z10F79B29T): the Ancient chip adds a
            // Zaros item to the protection kit - the Nex respite area's banker.
            if (anyGwd) {
                chipRow.add(pill("Ancient", flag(params, "gwdAncient"), text("chip.ancient.tip"),
                    () -> commands.set("gwdAncient", !flag(params, "gwdAncient"))));
            }
            // PvP world (Ragnarrok, 2026-10-20): any mob, cycles off / PvP /
            // High Risk. Shown on a PvP world, while on, or when the
            // settings ask for it (planning from a normal world).
            int p = id(params, "pvpWorld");
            if (p > 0 || flag(params, "pvpWorldHere") || pvpChipGate.getAsBoolean()) {
                chipRow.add(pill(p >= 2 ? "PvP: High Risk" : "PvP", p > 0, true, text("chip.pvp.tip"),
                    () -> commands.set("pvpWorld", (p + 1) % 3)));
            }
            // Cannons ride the chips section (Andrew, v2) for the LENSED
            // ship-eligible mob; the per-cannon pickers live on the card.
            if (ln) {
                int c = id(params, "cannonCount");
                chipRow.add(pill("Cannons " + c, c > 0, (c + 1) % 3 > 0, text("chip.cannons.tip"),
                    () -> commands.set("cannonCount", (c + 1) % 3)));
            }
            int dc = id(params, "deathCharge");
            if (!f2pl && !ll2 && standDown != null) {
                chipRow.add(disabledPill("D-charge", "Stands down: " + standDown + " - Death Charge is cast from Arceuus"));
            } else if (!f2pl && !ll2) {
                chipRow.add(pill(dc == 0 ? "D-charge" : dc == 1 ? "D-charge on" : "D-charge+",
                    dc > 0, (dc + 1) % 3 > 0, text("chip.dcharge.tip"), () -> commands.set("deathCharge", (dc + 1) % 3)));
            }
            if (anyMob(page, m -> flag(m, "multiAble"))) {
                // The Multi chip (Andrew 2026-10-19: "single -> multi x3 -> multi
                // x9", beside the other search chips): the AoE tools priced at a
                // stack of targets, cycling single / x3 / x9.
                boolean multi = flag(params, "multiView");
                int stack = id(params, "multiStack");
                boolean last = multi && stack >= 9;
                chipRow.add(pill(multi ? "Multi x" + stack : "Single", multi, !last, text("chip.multi.tip"), () -> {
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
            if (anyMob(page, m -> flag(m, "invocationScaled"))) {
                int invo = id(params, "toaInvocation");
                int ni = invo >= 540 ? 0 : invo >= 300 ? 540 : invo >= 150 ? 300 : 150;
                chipRow.add(pill("Invo " + invo, invo > 0, ni > 0, text("chip.invo.tip"),
                    () -> commands.set("toaInvocation", ni)));
            }
            int swaps = id(params, "maxSwaps");
            // The classic inventory control is a SLIDER over the bench
            // size - every value reachable, not four presets.
            JPanel ib = panel(new BorderLayout(4, 0));
            JLabel il = label("Inv " + swaps, LIGHT);
            ib.add(il, WEST);
            JSlider invSlider = new JSlider(0, 16, Math.max(0, Math.min(16, swaps)));
            invSlider.setPreferredSize(new Dimension(88, 18));
            invSlider.setBackground(DARK_GRAY_COLOR);
            invSlider.setFocusable(false);
            tipText(invSlider, "Max gear swaps carried on the trip");
            invSlider.addChangeListener(e -> {
                il.setText("Inv " + invSlider.getValue());
                // Recompute only when the drag settles, and only on a real
                // change - a slider fires continuously.
                if (!invSlider.getValueIsAdjusting() && invSlider.getValue() != swaps) {
                    commands.set("maxSwaps", invSlider.getValue());
                }
            });
            ib.add(invSlider, CENTER);
            ib.setBorder(createEmptyBorder(2, 4, 2, 4));
            invRow.removeAll();
            invRow.add(ib, CENTER);
            int bg = id(params, "upgradeBudgetGp");
            chipRow.add(valueChip( bg > 0 ? "Budget " + Gp.format(bg) : "Budget", bg > 0,
                text("chip.budget.tip"), Gp.format(bg), text -> commands.set("upgradeBudgetGp", Gp.parse(text))));
            chipRow.add(statsChip(params));
            // Combat achievement tasks for the mobs shown, and the player's own
            // requirements (ISS-01M5XC7XYMMEN3Q4QC6CGZFVPK): their own section under
            // the inventory slider, not the main chips (Andrew 2026-10-27: "we
            // shouldn't show the featherweight figher in the main chips but rather
            // in the requirements area below the slider").
            JPanel reqChips = panel(new WrapLayout(FlowLayout.LEFT, 4, 2));
            // Several tasks at once, ANDed (Andrew 2026-10-27).
            String picked = str(params, "caPicks");
            List<String> picks = new ArrayList<>(picked == null || picked.isEmpty() ? List.of() : List.of(picked.split(",")));
            for (Map t : list(page, "caTasks")) {
                String tid = String.valueOf(id(t, "id"));
                boolean on = picks.contains(tid);
                reqChips.add(pill(str(t, "name"), on, str(t, "tier") + " combat achievement: " + str(t, "desc")
                    + " " + (on ? text("ca.drop") : text("ca.pick")), () -> {
                        List<String> next = new ArrayList<>(picks);
                        if (!next.remove(tid)) next.add(tid);
                        commands.set("caPicks", String.join(",", next));
                    }));
            }
            String req = str(params, "requirements");
            reqLive = req == null ? "" : req;
            for (String part : reqParts()) {
                // A red x, so removing reads apart from the green "on" (Andrew 2026-10-27).
                reqChips.add(pill("<html>" + reqLabel(part).replace("<", "&lt;").replace(">", "&gt;")
                    + " <font color='#e05050'>x</font></html>", true, "Requirement - click to remove it", () -> {
                    List<String> rest = reqParts();
                    rest.remove(part);
                    setRequirements(rest);
                }));
            }
            boolean reqOpen = flag(params, "reqOpen");
            JComponent editor = requirementEditor();
            editor.setVisible(reqOpen);
            // Opens at once, mid-compute too: the page republish waits on the
            // compute (Andrew 2026-10-27: "can't add a requirement mid search").
            JLabel[] plus = new JLabel[1];
            plus[0] = pill("+ Requirement", reqOpen, "Ask for a set with a stat at least or at most a number", () -> {
                boolean open = !editor.isVisible();
                editor.setVisible(open);
                paintPill(plus[0], open);
                reqRow.revalidate();
                reqRow.repaint();
                commands.set("reqOpen", open);
            });
            reqChips.add(plus[0]);
            reqRow.removeAll();
            reqRow.add(reqChips, NORTH);
            reqRow.add(editor, CENTER);
            reqRow.setVisible(true);
            if (wildyRiskGate.getAsBoolean() && (p > 0 || aw && (ae || flag(params, "inWilderness")))) {
                int rg = id(params, "riskBudgetGp");
                // The CAP FLAG decides, never the number: the engine's
                // no-cap sentinel is itself 75k, so a raw > 0 test lit
                // this chip for a cap the compute ignored (field report
                // 2026-08-22).
                boolean capped = flag(params, "riskCapped");
                chipRow.add(valueChip( capped ? "Risk " + Gp.format(rg) : "Risk cap", capped, text("chip.risk.tip")
                        + (p > 0 ? text("chip.risk.pvp") : ""), capped ? Gp.format(rg) : "", text -> {
                    // Empty = clear: null falls back to uncapped.
                    Map args = new HashMap<>();
                    args.put("param", "riskBudgetGp");
                    args.put("value", text.isEmpty() ? null : Gp.parse(text));
                    commands.send("set-param", args);
                }));
                // High Risk worlds disable Protect Item (wiki).
                if (p < 2) {
                    chipRow.add(paramChip("Protect item", "protectItem", flag(params, "protectItem")));
                }
            }
            addMobButton = chip("+ Mob", text("chip.addmob.tip"), () -> {
                String query = JOptionPane.showInputDialog(root, "Add a monster to this result:", "Add mob", JOptionPane.PLAIN_MESSAGE);
                if (query != null && !query.trim().isEmpty()) {
                    commands.send("add-mob", Map.of("query", query.trim()));
                }
            });
            addMobButton.setContentAreaFilled(false);
            addMobButton.setOpaque(false);
            addMobButton.setForeground(LIGHT);
            addMobButton.setBorder(new RoundedBorder(DARKER_GRAY_HOVER_COLOR, 3, 10));
            JButton reload = chip("Reload", "Re-run with a fresh bank scan", () -> commands.send("recompute"));
            reload.setContentAreaFilled(false);
            reload.setOpaque(false);
            reload.setForeground(DIM);
            reload.setBorder(createEmptyBorder(3, 8, 3, 8));
            cards.setReload(reload);
            cards.setAddMob(addMobButton);
        }
        footerRow.removeAll();
        // Shortest Path walks you to the nearest spawn (ISS-01M5TCXDV8HF2V5C627SZEY2W3,
        // Andrew 2026-10-25: "above the discord/cancel/wiki calc/copy report
        // buttons so it can be displayed statically while loading").
        pathRow.removeAll();
        pathRow.setVisible(flag(page, "pathable"));
        pathRow.add(chip("Path here", "Shortest Path: walk to the nearest spawn", () -> commands.send("path-to")));
        String rt = str(page, "reportText");
        if (rt != null) {
            footerRow.add(chip("Copy report", "Copy the shown result as text", () -> {
                try {
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                        new StringSelection(rt), null);
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
            String wikiTab = effectiveTab(page);
            boolean wikiBis = flag(params, "viewingBis");
            footerRow.add(chip("Wiki calc", wikiCalcTip(page), () -> commands.send("wiki-calc", Map.of("style", wikiTab, "bis", wikiBis))));
        }
        footerRow.add(chip("Discord", text("footer.discord.tip"),
            () -> LinkBrowser.browse("https://discord.gg/6GuS6J8em3")));
        if (isComputing) {
            // Reachable mid-compute, beside Discord (Andrew 2026-09-04).
            footerRow.add(chip("Cancel", text("footer.cancel.tip"), () -> commands.send("cancel")));
        }
        if (rosterArea != null) {
            rosterArea.removeAll();
            JPanel roster = cards.renderRoster(page);
            if (roster != null) rosterArea.add(roster, CENTER);
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
            JLabel hint = label(text("hint.empty"), CHIP_OFF);
            hint.setBorder(createEmptyBorder(12, 8, 12, 8));
            hint.setAlignmentX(0.5f);
            cardArea.add(hint);
        }
        root.revalidate();
        root.repaint();
        long rm = (System.nanoTime() - rebuildStart) / 1_000_000;
        if (rm > 30) {
            log.debug("perf: UI rebuild took {}ms ({} identical pages skipped since last)", rm, skippedIdentical);
        }
        skippedIdentical = 0;
    }

}
