package com.loadoutlab.render;

import java.awt.BorderLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;

/**
 * The BARE fallback surface (ADR-0008's "full capability, clunky"):
 * what Core shows when the Loadout Lab UI companion is not installed.
 * A search box, the key parameter toggles, undo/redo, and the answer
 * as TEXT (the same report Copy report produces) - every capability,
 * zero beauty, minimum tokens. The companion's rich renderer replaces
 * this whole surface when present.
 */
public class BareSurface
{
	private final java.util.function.Supplier<Map<String, Object>> page;
	private final CommandSink commands;
	private JPanel root;
	private JTextArea output;
	private JPanel params;
	private JLabel status;
	private volatile Runnable installAction;

	public void setInstallAction(Runnable installAction)
	{
		this.installAction = installAction;
	}

	public BareSurface(java.util.function.Supplier<Map<String, Object>> page,
		CommandSink commands)
	{
		this.page = page;
		this.commands = commands;
	}

	public java.util.function.Function<Map<String, Object>, JComponent> asFunction()
	{
		return ignored -> component();
	}

	public void onModelChanged()
	{
		SwingUtilities.invokeLater(this::repaint);
	}

	public void setComputing(boolean computing)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (status != null)
			{
				status.setVisible(computing);
			}
		});
	}

	private synchronized JComponent component()
	{
		if (root == null)
		{
			root = new JPanel(new BorderLayout(0, 4));
			root.setBackground(ColorScheme.DARK_GRAY_COLOR);
			root.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
			JPanel top = new JPanel();
			top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
			top.setBackground(ColorScheme.DARK_GRAY_COLOR);
			JLabel nudge = new JLabel("<html>This is the plain fallback - the"
				+ " <b>Loadout Lab UI</b> plugin has the full interface.</html>");
			nudge.setForeground(new java.awt.Color(160, 160, 160));
			nudge.setAlignmentX(0f);
			top.add(nudge);
			JButton install = new JButton("Get Loadout Lab UI");
			install.setToolTipText("Install (or enable) the companion plugin"
				+ " from the Plugin Hub");
			install.setFocusable(false);
			install.setAlignmentX(0f);
			install.addActionListener(e ->
			{
				Runnable action = installAction;
				if (action != null)
				{
					action.run();
				}
			});
			top.add(install);
			top.add(Box.createVerticalStrut(4));
			top.add(Box.createVerticalStrut(4));
			JPanel searchRow = new JPanel(new BorderLayout(4, 0));
			searchRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
			searchRow.setAlignmentX(0f);
			JTextField search = new JTextField();
			search.setToolTipText("Search a monster or group - Enter selects");
			search.addActionListener(e ->
			{
				String query = search.getText().trim();
				if (query.length() >= 2)
				{
					commands.send("select", Map.of("query", query));
					search.setText("");
				}
			});
			searchRow.add(search, BorderLayout.CENTER);
			JPanel history = new JPanel(new java.awt.GridLayout(1, 2, 2, 0));
			history.setBackground(ColorScheme.DARK_GRAY_COLOR);
			JButton undo = new JButton("<");
			undo.setToolTipText("Undo");
			undo.addActionListener(e -> commands.send("undo", Map.of()));
			JButton redo = new JButton(">");
			redo.setToolTipText("Redo");
			redo.addActionListener(e -> commands.send("redo", Map.of()));
			history.add(undo);
			history.add(redo);
			searchRow.add(history, BorderLayout.EAST);
			top.add(searchRow);
			top.add(Box.createVerticalStrut(4));
			params = new JPanel(new java.awt.GridLayout(0, 2, 4, 0));
			params.setBackground(ColorScheme.DARK_GRAY_COLOR);
			params.setAlignmentX(0f);
			top.add(params);
			status = new JLabel("Computing...");
			status.setVisible(false);
			status.setAlignmentX(0f);
			top.add(status);
			root.add(top, BorderLayout.NORTH);
			output = new JTextArea();
			output.setEditable(false);
			output.setLineWrap(true);
			output.setWrapStyleWord(true);
			output.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			output.setForeground(new java.awt.Color(200, 200, 200));
			JScrollPane scroll = new JScrollPane(output);
			scroll.setBorder(BorderFactory.createEmptyBorder());
			root.add(scroll, BorderLayout.CENTER);
			repaint();
		}
		return root;
	}

	/** Booleans the bare surface exposes; cycles collapse to on/off. */
	private static final String[][] TOGGLES = {
		{"On task", "onTask"}, {"Wilderness", "inWilderness"},
		{"Spec weapon", "specWeapon"}, {"Thralls", "thralls"},
	};

	private void repaint()
	{
		if (root == null)
		{
			return;
		}
		Map<String, Object> current = page.get();
		Map<String, Object> firstParams = null;
		Object entries = current == null ? null : current.get("entries");
		if (entries instanceof List)
		{
			for (Object entry : (List<?>) entries)
			{
				if (entry instanceof Map && ((Map<?, ?>) entry).get("params") instanceof Map)
				{
					firstParams = (Map<String, Object>) ((Map<?, ?>) entry).get("params");
					break;
				}
			}
		}
		params.removeAll();
		if (firstParams != null)
		{
			for (String[] toggle : TOGGLES)
			{
				boolean on = Boolean.TRUE.equals(firstParams.get(toggle[1]));
				JCheckBox box = new JCheckBox(toggle[0], on);
				box.setBackground(ColorScheme.DARK_GRAY_COLOR);
				box.setForeground(new java.awt.Color(190, 190, 190));
				box.setFocusable(false);
				String key = toggle[1];
				box.addActionListener(e ->
				{
					Map<String, Object> args = new HashMap<>();
					args.put("param", key);
					args.put("value", box.isSelected());
					commands.send("set-param", args);
				});
				params.add(box);
			}
		}
		params.setVisible(firstParams != null);
		Object report = current == null ? null : current.get("reportText");
		output.setText(report instanceof String ? (String) report
			: "Search a monster to begin.");
		output.setCaretPosition(0);
		root.revalidate();
		root.repaint();
	}
}
