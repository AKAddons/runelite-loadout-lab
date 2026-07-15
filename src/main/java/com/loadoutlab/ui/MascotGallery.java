package com.loadoutlab.ui;

import java.awt.Color;
import java.awt.Font;
import java.time.LocalDate;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Developer-mode gallery: every mood in the roster rendered live at once,
 * ignoring date windows, so all animations can be eyeballed at rest without
 * waiting for a search or the right season. Each is labelled with its weight
 * and whether it is eligible today. Shown in the resting panel and reopened
 * from the header "..." menu, both only when RuneLite is in developer mode.
 */
class MascotGallery extends JPanel
{
	private static final Color LIVE = new Color(120, 200, 120);
	private static final Color DORMANT = new Color(150, 150, 150);

	MascotGallery()
	{
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setAlignmentX(LEFT_ALIGNMENT);
		setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

		JLabel header = new JLabel("Animation gallery (developer mode)");
		header.setForeground(Color.WHITE);
		header.setFont(header.getFont().deriveFont(Font.BOLD));
		header.setAlignmentX(LEFT_ALIGNMENT);
		add(header);
		add(Box.createVerticalStrut(6));

		LocalDate today = LocalDate.now();
		for (MascotRoster mood : MascotRoster.values())
		{
			boolean live = MascotRoster.activeOn(today).contains(mood);
			JLabel label = new JLabel(mood.name().toLowerCase()
				+ "  -  weight " + mood.weight()
				+ (live ? "  -  live today" : "  -  dormant"));
			label.setForeground(live ? LIVE : DORMANT);
			label.setAlignmentX(LEFT_ALIGNMENT);
			add(label);

			// A live, animating instance - its timer starts when the gallery
			// is shown and stops when it is replaced (Mascot lifecycle).
			add(mood.create());
			add(Box.createVerticalStrut(10));
		}
	}
}
