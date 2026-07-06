package com.loadoutlab.ui;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.optimizer.OptimizerService.StyleResult;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * v0.1 panel: search a monster, see your best OWNED set per combat style -
 * rendered as item icons like an equipment view - with exact DPS and how it
 * compares to the best set in the game.
 *
 * <p>EDT discipline: search is debounced, optimization runs off-thread via
 * OptimizerService, item images fill in asynchronously via ItemManager;
 * this panel only renders. All content is width-constrained - the sidebar
 * must never scroll horizontally.
 */
public class LoadoutLabPanel extends PluginPanel
{
	/** (monster, f2pOnly, onDone) - the plugin wires this to the optimizer. */
	public interface ComputeHook
	{
		void compute(MonsterStats monster, boolean f2pOnly, Runnable onDone);
	}

	private static final int SEARCH_DEBOUNCE_MS = 150;
	private static final int SEARCH_LIMIT = 25;
	private static final int ICON_SIZE = 32;

	private final LoadoutData data;
	private final ItemManager itemManager;
	private final ComputeHook computeHook;

	private final JTextField searchField = new JTextField();
	private final DefaultListModel<MonsterStats> monsterModel = new DefaultListModel<>();
	private final JList<MonsterStats> monsterList = new JList<>(monsterModel);
	private final JScrollPane monsterScroll;
	private final JPanel selectedRow = new JPanel(new BorderLayout(4, 0));
	private final JLabel selectedLabel = new JLabel();
	private final JCheckBox f2pOnly = new JCheckBox("Non-members gear only");
	private final JPanel resultsPanel = new JPanel();
	private final JLabel statusLabel = new JLabel(" ");
	private final Timer searchDebounce;

	/** Guards against programmatic search-field changes re-opening the list. */
	private boolean suppressSearchEvents;

	private MonsterStats selectedMonster;

	public LoadoutLabPanel(LoadoutData data, ItemManager itemManager, ComputeHook computeHook)
	{
		this.data = data;
		this.itemManager = itemManager;
		this.computeHook = computeHook;

		setLayout(new BorderLayout(0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		top.setOpaque(false);

		JLabel title = new JLabel("Loadout Lab");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 14f));
		title.setAlignmentX(LEFT_ALIGNMENT);
		top.add(title);
		top.add(Box.createVerticalStrut(4));

		searchField.setAlignmentX(LEFT_ALIGNMENT);
		searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		top.add(searchField);
		top.add(Box.createVerticalStrut(4));

		// Selected-monster row: replaces the dropdown once a pick is made.
		selectedRow.setOpaque(false);
		selectedRow.setAlignmentX(LEFT_ALIGNMENT);
		selectedRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		selectedLabel.setForeground(new Color(140, 200, 140));
		selectedLabel.setFont(selectedLabel.getFont().deriveFont(Font.BOLD, 12f));
		selectedRow.add(selectedLabel, BorderLayout.CENTER);
		JButton clearSelection = new JButton("x");
		clearSelection.setMargin(new java.awt.Insets(0, 6, 0, 6));
		clearSelection.setToolTipText("Choose a different monster");
		clearSelection.addActionListener(e -> clearSelection());
		selectedRow.add(clearSelection, BorderLayout.EAST);
		selectedRow.setVisible(false);
		top.add(selectedRow);

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
		monsterScroll.setPreferredSize(new Dimension(0, 130));
		monsterScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));
		monsterScroll.setAlignmentX(LEFT_ALIGNMENT);
		monsterScroll.setVisible(false);
		top.add(monsterScroll);

		f2pOnly.setOpaque(false);
		f2pOnly.setForeground(new Color(200, 200, 200));
		f2pOnly.setAlignmentX(LEFT_ALIGNMENT);
		f2pOnly.setToolTipText("Only consider free-to-play gear");
		f2pOnly.addActionListener(e -> recompute());
		f2pOnly.setVisible(false); // only shown on non-members worlds
		top.add(f2pOnly);

		add(top, BorderLayout.NORTH);

		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
		resultsPanel.setOpaque(false);
		JScrollPane resultsScroll = new JScrollPane(resultsPanel,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		resultsScroll.setBorder(null);
		resultsScroll.getViewport().setOpaque(false);
		resultsScroll.setOpaque(false);
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

	/**
	 * Set by the plugin on login. On a non-members world the filter appears
	 * and defaults on; on a members world it is hidden and forced off - the
	 * toggle only earns panel space where it can matter.
	 */
	public void setF2pWorld(boolean f2pWorld)
	{
		f2pOnly.setVisible(f2pWorld);
		if (f2pOnly.isSelected() != f2pWorld)
		{
			f2pOnly.setSelected(f2pWorld);
			recompute();
		}
		revalidate();
		repaint();
	}

	public boolean isF2pOnly()
	{
		return f2pOnly.isSelected();
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
		selectedMonster = monster;
		selectedLabel.setText("vs " + monster.label());
		selectedRow.setVisible(true);
		revalidate();
		repaint();
		recompute();
	}

	private void recompute()
	{
		if (selectedMonster == null)
		{
			return;
		}
		// Clear stale results immediately - showing the previous monster's
		// sets while the optimizer runs reads as an answer for this one.
		resultsPanel.removeAll();
		JLabel computing = new JLabel("Optimizing vs " + selectedMonster.getName() + "...");
		computing.setForeground(new Color(160, 160, 160));
		computing.setAlignmentX(LEFT_ALIGNMENT);
		computing.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		resultsPanel.add(computing);
		resultsPanel.revalidate();
		resultsPanel.repaint();
		statusLabel.setText("Optimizing vs " + selectedMonster.getName() + "...");
		computeHook.compute(selectedMonster, f2pOnly.isSelected(), () -> statusLabel.setText(" "));
	}

	private void clearSelection()
	{
		selectedMonster = null;
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
	public void showResults(MonsterStats monster, Map<CombatStyle, StyleResult> results)
	{
		if (selectedMonster == null || monster.getId() != selectedMonster.getId())
		{
			return; // stale result for a monster the user moved away from
		}
		resultsPanel.removeAll();
		for (CombatStyle style : new CombatStyle[]{CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC})
		{
			resultsPanel.add(styleCard(style, results.get(style)));
			resultsPanel.add(Box.createVerticalStrut(6));
		}
		statusLabel.setText("Best owned sets vs " + monster.getName());
		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	private JPanel styleCard(CombatStyle style, StyleResult result)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
		card.setAlignmentX(LEFT_ALIGNMENT);

		JLabel header = new JLabel(style.toString());
		header.setForeground(Color.WHITE);
		header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
		header.setAlignmentX(LEFT_ALIGNMENT);
		card.add(header);

		if (result == null || result.owned == null || result.owned.isEmpty())
		{
			boolean vyre = selectedMonster != null && selectedMonster.hasAttribute("vampyre3");
			JLabel none = new JLabel(vyre
				? "Immune - needs a vyre weapon"
				: "No usable owned set found.");
			none.setForeground(new Color(160, 160, 160));
			none.setAlignmentX(LEFT_ALIGNMENT);
			if (vyre)
			{
				none.setToolTipText("Only the Ivandis flail, blisterwood weapons,"
					+ " Sunspear, or Hallowed flail can damage this monster");
			}
			card.add(none);
			return card;
		}

		DpsResult best = result.owned.get(0);

		// Yours vs the game's ceiling.
		JLabel dps = new JLabel(String.format("Yours: %.2f DPS  (max %d, %.0f%% acc)",
			best.getDps(), best.getMaxHit(), best.getAccuracy() * 100));
		dps.setForeground(new Color(140, 200, 140));
		dps.setAlignmentX(LEFT_ALIGNMENT);
		card.add(dps);
		addSpellLine(card, style, best);
		if (result.spec != null && result.specExpectedDamage > 0)
		{
			JLabel spec = new JLabel(String.format("Spec: %s - avg %.0f dmg (%d%% energy)",
				result.spec.getDisplayName(), result.specExpectedDamage, result.spec.getEnergyCost()));
			spec.setForeground(new Color(220, 180, 120));
			spec.setFont(spec.getFont().deriveFont(11f));
			spec.setAlignmentX(LEFT_ALIGNMENT);
			String note = result.spec.getNote();
			spec.setToolTipText(note.isEmpty()
				? "Swapped into your best set for one special attack"
				: note);
			card.add(spec);
		}
		card.add(Box.createVerticalStrut(4));
		card.add(iconGrid(best));

		// The ceiling: the game-wide best set, so "off" numbers are inspectable.
		if (result.overallBest != null && result.overallBest.getDps() > 0)
		{
			card.add(Box.createVerticalStrut(6));
			double pct = 100.0 * best.getDps() / result.overallBest.getDps();
			JLabel ceiling = new JLabel(String.format("Game best: %.2f DPS - you are at %.0f%%",
				result.overallBest.getDps(), Math.min(100.0, pct)));
			ceiling.setForeground(new Color(160, 160, 160));
			ceiling.setFont(ceiling.getFont().deriveFont(11f));
			ceiling.setAlignmentX(LEFT_ALIGNMENT);
			card.add(ceiling);
			addSpellLine(card, style, result.overallBest);
			card.add(Box.createVerticalStrut(4));
			card.add(iconGrid(result.overallBest));
		}
		return card;
	}

	/**
	 * Magic only: name the spell and its spellbook explicitly - the weapon in
	 * the grid below is autocast-legal for that book (engine-gated).
	 */
	private void addSpellLine(JPanel card, CombatStyle style, DpsResult result)
	{
		if (style != CombatStyle.MAGIC || result.getSpellName() == null)
		{
			return;
		}
		String book = data.getSpells().stream()
			.filter(s -> result.getSpellName().equals(s.getName()))
			.map(s -> s.getSpellbook())
			.findFirst().orElse("");
		JLabel spell = new JLabel("Spell: " + result.getSpellName()
			+ (book.isEmpty() ? "" : " (" + capitalize(book) + " spellbook)"));
		spell.setForeground(new Color(150, 170, 230));
		spell.setFont(spell.getFont().deriveFont(11f));
		spell.setAlignmentX(LEFT_ALIGNMENT);
		card.add(spell);
	}

	/** A set as item icons (names on hover) - wraps, never overflows. */
	private JPanel iconGrid(DpsResult result)
	{
		JPanel icons = new JPanel(new WrapLayout(FlowLayout.LEFT, 2, 2));
		icons.setOpaque(false);
		icons.setAlignmentX(LEFT_ALIGNMENT);
		for (Map.Entry<GearSlot, GearItem> e : result.getLoadout().getGear().entrySet())
		{
			GearItem item = e.getValue();
			if (item == null)
			{
				continue;
			}
			JLabel slot = new JLabel();
			slot.setPreferredSize(new Dimension(ICON_SIZE + 4, ICON_SIZE + 4));
			slot.setHorizontalAlignment(SwingConstants.CENTER);
			slot.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70)));
			slot.setToolTipText(e.getKey() + ": " + item.label());
			AsyncBufferedImage img = itemManager.getImage(item.getId());
			img.addTo(slot);
			icons.add(slot);
		}
		return icons;
	}

	private static String capitalize(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
