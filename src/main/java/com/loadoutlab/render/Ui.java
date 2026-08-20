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
}
