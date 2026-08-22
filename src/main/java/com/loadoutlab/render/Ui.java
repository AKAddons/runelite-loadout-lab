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

	/** A menu item wired to an action, added to the menu. */
	static void item(JPopupMenu menu, String label, Runnable action)
	{
		javax.swing.JMenuItem item = new javax.swing.JMenuItem(label);
		item.addActionListener(e -> action.run());
		menu.add(item);
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
