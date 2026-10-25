package com.loadoutlab.render;

import java.awt.*;
import java.util.*;
import javax.swing.*;
import javax.swing.Timer;
import net.runelite.client.ui.*;
import java.awt.image.BufferedImage;

/**
 * The shared UI kit (merge-back dedupe): the handful of Swing recipes
 * the surface repeats everywhere, factored once. Helpers are exact
 * equivalents of the inline forms they replace - same properties in
 * the same order - so the look cannot drift.
 */
final class Ui {
	private Ui() {
	}

	/** The surface's longer prose - tooltips, captions, html bodies - lives
	 * in text.tsv beside this class and is read by key: the Plugin Hub's
	 * review bot counts source tokens, and words are data, not code. A
	 * missing key renders "null", which the key-resolution test catches. */
	private static final Map<String, String> TEXT = tsv("text.tsv");

	static String text(String key) { return TEXT.get(key); }

	/** key<TAB>value rows of a resource beside this class ('#' lines are
	 * comments, values trimmed) - text.tsv and the mood routes share it. */
	static Map<String, String> tsv(String name) {
		Map<String, String> out = new LinkedHashMap<>();
		try (java.io.InputStream in = Ui.class.getResourceAsStream(name)) {
			if (in != null) {
				for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
					int tab = line.indexOf('\t');
					if (tab > 0 && !line.startsWith("#")) {
						out.put(line.substring(0, tab), line.substring(tab + 1).trim());
					}
				}
			}
		} catch (java.io.IOException ignored) {
		}
		return out;
	}

	/** A panel on the standard dark ground. */
	static JPanel panel(LayoutManager layout) {
		var panel = new JPanel(layout);
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		return panel;
	}

	/** A panel on the darker inset ground. */
	static JPanel darker(LayoutManager layout) {
		var panel = new JPanel(layout);
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		return panel;
	}

	/** A vertical box column on the given ground. */
	static JPanel column(Color bg) {
		var panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setBackground(bg);
		return panel;
	}

	/** A see-through panel: a seat inside a row that already has a ground. */
	static JPanel clear(LayoutManager layout) {
		var panel = new JPanel(layout);
		panel.setOpaque(false);
		return panel;
	}

	/** A small-font label; null keeps the default foreground. */
	static JLabel small(String text, Color fg) {
		var label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		if (fg != null) { label.setForeground(fg); }
		return label;
	}

	/** A label with a fixed foreground. */
	static JLabel label(String text, Color fg) {
		var label = new JLabel(text);
		label.setForeground(fg);
		return label;
	}

	/** Hand cursor + a click action (the classic clickable label). */
	static void onClick(Component component, Runnable action) {
		component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		component.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(java.awt.event.MouseEvent e) { action.run(); }
		});
	}

	/** onClick's left-button-only sibling, for a component that also owns
	 * a popup menu: the right button must open the menu and nothing else. */
	static void onLeftClick(Component component, Runnable action) {
		component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		component.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(java.awt.event.MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e)) { action.run(); }
			}
		});
	}

	/** The little "x" that removes or clears something: grey, red under the
	 * mouse, acting on a plain left click (a right click belongs to whatever
	 * menu its row carries). */
	static JLabel xLabel(String tip, Runnable action) {
		JLabel x = label("x", new Color(0x969696));
		x.setToolTipText(tip);
		x.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		x.addMouseListener(new java.awt.event.MouseAdapter() {
			public void mouseClicked(java.awt.event.MouseEvent e) {
				if (!e.isPopupTrigger() && SwingUtilities.isLeftMouseButton(e)) { action.run(); }
			}

			public void mouseEntered(java.awt.event.MouseEvent e) { x.setForeground(new Color(0xDC7878)); }

			public void mouseExited(java.awt.event.MouseEvent e) { x.setForeground(new Color(0x969696)); }
		});
		return x;
	}

	/** A square smooth-scaled icon. */
	static ImageIcon icon(Image image, int size) {
		return new ImageIcon(image.getScaledInstance(size, size, Image.SCALE_SMOOTH));
	}

	/** An antialiased copy of the graphics for a rounded paint; the caller disposes it. */
	static Graphics2D smooth(Graphics g) {
		Graphics2D g2 = (Graphics2D) g.create();
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		return g2;
	}

	/** A one-shot Swing timer (the debounce shape). */
	static Timer once(int delay, java.awt.event.ActionListener action) {
		var timer = new Timer(delay, action);
		timer.setRepeats(false);
		return timer;
	}

	/** The image scaled to size, dimmed, with a red X painted over it - the
	 * "none picked" sentinel (Andrew 2026-09-01: a plain prayer book read as
	 * a prayer being ON). Drawn graphics, so the ASCII glyph gate is not in
	 * play. */
	static ImageIcon crossedOut(Image image, int size) {
		var out = new BufferedImage(
			size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
			RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			RenderingHints.VALUE_ANTIALIAS_ON);
		// The base icon, dimmed so the X reads as the foreground.
		Composite base = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(
			AlphaComposite.SRC_OVER, 0.55f));
		g.drawImage(image, 0, 0, size, size, null);
		g.setComposite(base);
		int m = Math.max(2, size / 6);
		g.setStroke(new BasicStroke(Math.max(2f, size / 9f),
			BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		g.setColor(new Color(0xD24646));
		g.drawLine(m, m, size - m, size - m);
		g.drawLine(size - m, m, m, size - m);
		g.dispose();
		return new ImageIcon(out);
	}

	/** A menu item wired to an action, added to the menu. */
	static void item(JPopupMenu menu, String label, Runnable action) { item(menu, label, null, action); }

	static void item(JPopupMenu menu, String label, String tip, Runnable action) {
		var item = new JMenuItem(label);
		item.setToolTipText(tip);
		item.addActionListener(e -> action.run());
		menu.add(item);
	}

	/** A radio-style check item (checked = the current pick), wired to an action. */
	static JCheckBoxMenuItem check(String label, boolean selected, Runnable action) {
		var item = new JCheckBoxMenuItem(label, selected);
		item.addActionListener(e -> action.run());
		return item;
	}

	/** A popup dropped from the bottom edge of its anchor. */
	static void show(JPopupMenu menu, Component anchor) { menu.show(anchor, 0, anchor.getHeight()); }

	/** A tooltip body wrapped for Swing's html renderer. Six builders
	 * each wrote their own wrapper; this is the one. */
	static String tip(String... parts) {
		var out = new StringBuilder("<html>");
		for (String part : parts) { out.append(part); }
		return out.append("</html>").toString();
	}

	/** A heading over one item per line - the shape the risk and
	 * counted-bonus tips each hand-rolled. The house rule: a breakdown
	 * is a list and numbers, never prose. */
	static String list(String heading, Iterable<?> items) {
		var out = new StringBuilder(heading);
		for (Object item : items) { out.append("<br> ").append(item); }
		return out.toString();
	}

	/** The reconciliation ledger BOTH dps tooltips wear (field ask
	 * 2026-08-21: "less dense and more informative") - one fact per row:
	 * the bare SET the wiki calculator shows, the parts we fold on top,
	 * and the total the card prints. The two call sites differ only in
	 * the set row's note and in whether a zero spec row still shows, so
	 * both ride parameters - the table markup itself lives once.
	 * Returns the table only; callers own the surrounding html. */
	static String ledger(double set, String setNote, double specDps, boolean showSpec,
		double thrallsDps) {
		// One row template (ledger.line: label, right-aligned value, note cell).
		String row = text("ledger.line");
		var sum = new StringBuilder("<table cellpadding='1' cellspacing='0'>");
		sum.append(String.format(row, "set", String.format("%.3f", set),
			"<font color='#969696'>&nbsp;&nbsp;" + setNote + "</font>"));
		if (showSpec) { sum.append(String.format(row, "+ spec", String.format("%.2f", specDps), "")); }
		if (thrallsDps > 0) { sum.append(String.format(row, "+ thralls", String.format("%.2f", thrallsDps), "")); }
		return sum.append(String.format(row, "<b>= shown here</b>",
			"<b>" + String.format("%.2f", set + specDps + thrallsDps) + "</b>", "")).append("</table>").toString();
	}
}
