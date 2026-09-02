package com.loadoutlab.render;

import java.awt.Color;
import java.awt.Component;
import java.awt.Image;
import java.awt.LayoutManager;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import net.runelite.client.ui.ColorScheme;

/**
 * The shared UI kit (merge-back dedupe): the handful of Swing recipes
 * the surface repeats everywhere, factored once. Helpers are exact
 * equivalents of the inline forms they replace - same properties in
 * the same order - so the look cannot drift.
 */
final class Ui
{
	private Ui()
	{
	}

	/** A panel on the standard dark ground. */
	static JPanel panel(LayoutManager layout)
	{
		JPanel panel = new JPanel(layout);
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		return panel;
	}

	/** A panel on the darker inset ground. */
	static JPanel darker(LayoutManager layout)
	{
		JPanel panel = new JPanel(layout);
		panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		return panel;
	}

	/** A label with a fixed foreground. */
	static JLabel label(String text, Color fg)
	{
		JLabel label = new JLabel(text);
		label.setForeground(fg);
		return label;
	}

	/** Hand cursor + a click action (the classic clickable label). */
	static void onClick(Component component, Runnable action)
	{
		component.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		component.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				action.run();
			}
		});
	}

	/** A square smooth-scaled icon. */
	static ImageIcon icon(Image image, int size)
	{
		return new ImageIcon(image.getScaledInstance(size, size, Image.SCALE_SMOOTH));
	}

	/** The image scaled to size, dimmed, with a red X painted over it - the
	 * "none picked" sentinel (Andrew 2026-09-01: a plain prayer book read as
	 * a prayer being ON). Drawn graphics, so the ASCII glyph gate is not in
	 * play. */
	static ImageIcon crossedOut(Image image, int size)
	{
		java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(
			size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = out.createGraphics();
		g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
			java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
			java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		// The base icon, dimmed so the X reads as the foreground.
		java.awt.Composite base = g.getComposite();
		g.setComposite(java.awt.AlphaComposite.getInstance(
			java.awt.AlphaComposite.SRC_OVER, 0.55f));
		g.drawImage(image, 0, 0, size, size, null);
		g.setComposite(base);
		int m = Math.max(2, size / 6);
		g.setStroke(new java.awt.BasicStroke(Math.max(2f, size / 9f),
			java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
		g.setColor(new java.awt.Color(210, 70, 70));
		g.drawLine(m, m, size - m, size - m);
		g.drawLine(size - m, m, m, size - m);
		g.dispose();
		return new ImageIcon(out);
	}

	/** A menu item wired to an action, added to the menu. */
	static void item(JPopupMenu menu, String label, Runnable action)
	{
		javax.swing.JMenuItem item = new javax.swing.JMenuItem(label);
		item.addActionListener(e -> action.run());
		menu.add(item);
	}

	/** A tooltip body wrapped for Swing's html renderer. Six builders
	 * each wrote their own wrapper; this is the one. */
	static String tip(String... parts)
	{
		StringBuilder out = new StringBuilder("<html>");
		for (String part : parts)
		{
			out.append(part);
		}
		return out.append("</html>").toString();
	}

	/** A heading over one item per line - the shape the risk and
	 * counted-bonus tips each hand-rolled. The house rule: a breakdown
	 * is a list and numbers, never prose. */
	static String list(String heading, Iterable<?> items)
	{
		StringBuilder out = new StringBuilder(heading);
		for (Object item : items)
		{
			out.append("<br> ").append(item);
		}
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
		double thrallsDps)
	{
		StringBuilder sum = new StringBuilder();
		sum.append("<table cellpadding='1' cellspacing='0'>")
			.append("<tr><td>set</td><td align='right'>&nbsp;&nbsp;")
			.append(String.format("%.3f", set))
			.append("</td><td><font color='#969696'>&nbsp;&nbsp;").append(setNote)
			.append("</font></td></tr>");
		if (showSpec)
		{
			sum.append("<tr><td>+ spec</td><td align='right'>&nbsp;&nbsp;")
				.append(String.format("%.2f", specDps)).append("</td><td></td></tr>");
		}
		if (thrallsDps > 0)
		{
			sum.append("<tr><td>+ thralls</td><td align='right'>&nbsp;&nbsp;")
				.append(String.format("%.2f", thrallsDps)).append("</td><td></td></tr>");
		}
		return sum.append("<tr><td><b>= shown here</b></td><td align='right'>&nbsp;&nbsp;<b>")
			.append(String.format("%.2f", set + specDps + thrallsDps))
			.append("</b></td><td></td></tr></table>").toString();
	}
}
