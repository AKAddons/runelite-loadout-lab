package com.loadoutlab.ui;

import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.DpsResult;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * v0.1 panel: search a monster, see your best OWNED set per combat style
 * with exact DPS.
 *
 * <p>EDT discipline: the search is debounced (150ms - typing must not run a
 * search per keystroke) and optimization runs off-thread via
 * OptimizerService; this panel only renders.
 */
public class LoadoutLabPanel extends PluginPanel
{
	private static final int SEARCH_DEBOUNCE_MS = 150;
	private static final int SEARCH_LIMIT = 25;

	private final LoadoutData data;
	/** (monster, resultsConsumer) - the plugin wires this to the optimizer. */
	private final BiConsumer<MonsterStats, Runnable> computeHook;

	private final JTextField searchField = new JTextField();
	private final DefaultListModel<MonsterStats> monsterModel = new DefaultListModel<>();
	private final JList<MonsterStats> monsterList = new JList<>(monsterModel);
	private final JScrollPane monsterScroll;
	private final JPanel selectedRow = new JPanel(new BorderLayout(4, 0));
	private final JLabel selectedLabel = new JLabel();
	private final JPanel resultsPanel = new JPanel();
	private final JLabel statusLabel = new JLabel(" ");
	private final Timer searchDebounce;

	/** Guards against programmatic search-field changes re-opening the list. */
	private boolean suppressSearchEvents;

	private Map<CombatStyle, List<DpsResult>> currentResults;

	public LoadoutLabPanel(LoadoutData data, BiConsumer<MonsterStats, Runnable> computeHook)
	{
		this.data = data;
		this.computeHook = computeHook;

		setLayout(new BorderLayout(0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel top = new JPanel(new BorderLayout(0, 4));
		top.setOpaque(false);
		JLabel title = new JLabel("Loadout Lab");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
		top.add(title, BorderLayout.NORTH);
		top.add(searchField, BorderLayout.CENTER);

		monsterList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		monsterList.setVisibleRowCount(6);
		monsterList.setCellRenderer(new javax.swing.DefaultListCellRenderer()
		{
			@Override
			public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
				int index, boolean isSelected, boolean cellHasFocus)
			{
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				setText(((MonsterStats) value).label());
				return this;
			}
		});
		monsterScroll = new JScrollPane(monsterList);
		monsterScroll.setPreferredSize(new java.awt.Dimension(0, 130));
		monsterScroll.setVisible(false);

		// Selected-monster row: replaces the dropdown once a pick is made;
		// the small x clears it and returns to searching.
		selectedRow.setOpaque(false);
		selectedLabel.setForeground(new Color(140, 200, 140));
		selectedLabel.setFont(selectedLabel.getFont().deriveFont(Font.BOLD, 12f));
		selectedRow.add(selectedLabel, BorderLayout.CENTER);
		javax.swing.JButton clearSelection = new javax.swing.JButton("x");
		clearSelection.setMargin(new java.awt.Insets(0, 6, 0, 6));
		clearSelection.setToolTipText("Choose a different monster");
		clearSelection.addActionListener(e -> clearSelection());
		selectedRow.add(clearSelection, BorderLayout.EAST);
		selectedRow.setVisible(false);

		JPanel pickerStack = new JPanel();
		pickerStack.setLayout(new BoxLayout(pickerStack, BoxLayout.Y_AXIS));
		pickerStack.setOpaque(false);
		selectedRow.setAlignmentX(LEFT_ALIGNMENT);
		monsterScroll.setAlignmentX(LEFT_ALIGNMENT);
		pickerStack.add(selectedRow);
		pickerStack.add(monsterScroll);
		top.add(pickerStack, BorderLayout.SOUTH);
		add(top, BorderLayout.NORTH);

		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
		resultsPanel.setOpaque(false);
		JScrollPane resultsScroll = new JScrollPane(resultsPanel,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		resultsScroll.setBorder(null);
		add(resultsScroll, BorderLayout.CENTER);

		statusLabel.setForeground(new Color(160, 160, 160));
		add(statusLabel, BorderLayout.SOUTH);

		searchDebounce = new Timer(SEARCH_DEBOUNCE_MS, e -> runSearch());
		searchDebounce.setRepeats(false);
		searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
		{
			public void insertUpdate(javax.swing.event.DocumentEvent e) { onSearchEdited(); }
			public void removeUpdate(javax.swing.event.DocumentEvent e) { onSearchEdited(); }
			public void changedUpdate(javax.swing.event.DocumentEvent e) { onSearchEdited(); }
		});
		monsterList.addListSelectionListener(e ->
		{
			if (!e.getValueIsAdjusting() && monsterList.getSelectedValue() != null)
			{
				select(monsterList.getSelectedValue());
			}
		});

		statusLabel.setText("Search a monster to begin.");
	}

	private void onSearchEdited()
	{
		if (!suppressSearchEvents)
		{
			searchDebounce.restart();
		}
	}

	private void runSearch()
	{
		String query = searchField.getText().trim();
		monsterModel.clear();
		if (query.length() < 2)
		{
			monsterScroll.setVisible(false);
			revalidate();
			return;
		}
		for (MonsterStats m : data.searchMonsters(query, SEARCH_LIMIT))
		{
			monsterModel.addElement(m);
		}
		// Typing reopens the picker; a previous selection stays shown until a
		// new pick replaces it.
		monsterScroll.setVisible(!monsterModel.isEmpty());
		statusLabel.setText(monsterModel.isEmpty() ? "No monsters match." : " ");
		revalidate();
		repaint();
	}

	/** A pick: collapse the dropdown, show the selection, clear the query. */
	private void select(MonsterStats monster)
	{
		suppressSearchEvents = true;
		try
		{
			searchField.setText("");
		}
		finally
		{
			suppressSearchEvents = false;
		}
		monsterModel.clear();
		monsterScroll.setVisible(false);
		selectedLabel.setText("vs " + monster.label());
		selectedRow.setVisible(true);
		revalidate();
		repaint();

		statusLabel.setText("Optimizing vs " + monster.getName() + "...");
		computeHook.accept(monster, () -> statusLabel.setText(" "));
	}

	private void clearSelection()
	{
		selectedRow.setVisible(false);
		selectedLabel.setText("");
		resultsPanel.removeAll();
		resultsPanel.revalidate();
		resultsPanel.repaint();
		statusLabel.setText("Search a monster to begin.");
		revalidate();
		searchField.requestFocusInWindow();
	}

	/** Render results (EDT). Called by the plugin once the optimizer returns. */
	public void showResults(MonsterStats monster, Map<CombatStyle, List<DpsResult>> results)
	{
		currentResults = results;
		resultsPanel.removeAll();
		for (CombatStyle style : new CombatStyle[]{CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC})
		{
			List<DpsResult> list = results.get(style);
			resultsPanel.add(styleCard(style, list));
			resultsPanel.add(Box.createVerticalStrut(6));
		}
		statusLabel.setText("Best owned sets vs " + monster.getName());
		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	private JPanel styleCard(CombatStyle style, List<DpsResult> list)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		card.setAlignmentX(LEFT_ALIGNMENT);

		JLabel header = new JLabel(style.toString());
		header.setForeground(Color.WHITE);
		header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
		card.add(header);

		if (list == null || list.isEmpty())
		{
			JLabel none = new JLabel("No usable owned set found.");
			none.setForeground(new Color(160, 160, 160));
			card.add(none);
			return card;
		}

		DpsResult best = list.get(0);
		JLabel dps = new JLabel(String.format("%.2f DPS  |  max %d  |  %.1f%% acc%s",
			best.getDps(), best.getMaxHit(), best.getAccuracy() * 100,
			best.getSpellName() != null ? "  |  " + best.getSpellName() : ""));
		dps.setForeground(new Color(140, 200, 140));
		card.add(dps);
		card.add(Box.createVerticalStrut(4));

		for (Map.Entry<GearSlot, GearItem> e : best.getLoadout().getGear().entrySet())
		{
			GearItem item = e.getValue();
			if (item == null)
			{
				continue;
			}
			JLabel line = new JLabel(e.getKey() + ": " + item.getName());
			line.setForeground(new Color(200, 200, 200));
			line.setFont(line.getFont().deriveFont(11f));
			card.add(line);
		}
		return card;
	}
}
