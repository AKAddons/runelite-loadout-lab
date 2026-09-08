package com.loadoutlab.ui;

import java.awt.BorderLayout;
import javax.swing.JComponent;
import net.runelite.client.ui.PluginPanel;

/**
 * The one-surface ruling (ADR-0008): Core owns the single sidebar
 * panel; when the Companion has registered a renderer, its content
 * mounts HERE - same icon, same slot, prettier inside. EDT only.
 */
public class CompanionHost extends PluginPanel
{
	public CompanionHost()
	{
		setLayout(new BorderLayout());
	}

	public void mount(JComponent component)
	{
		removeAll();
		if (component != null)
		{
			add(component, BorderLayout.NORTH);
		}
		revalidate();
		repaint();
	}
}
