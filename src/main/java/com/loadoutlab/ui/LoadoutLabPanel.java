package com.loadoutlab.ui;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.SpecialAttack;
import com.loadoutlab.optimizer.OptimizerService.StyleResult;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
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
import javax.swing.SwingUtilities;
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
		void compute(MonsterStats monster, boolean f2pOnly, boolean onSlayerTask,
			String spellbookLock, Runnable onDone);
	}

	/** Toggle an item's excluded state; returns true when now excluded. */
	public interface ExclusionToggle
	{
		boolean toggle(int itemId);
	}

	/** The current excluded item ids. */
	public interface ExclusionView
	{
		java.util.Set<Integer> snapshot();
	}

	private static final int SEARCH_DEBOUNCE_MS = 150;
	private static final int SEARCH_LIMIT = 25;
	private static final int ICON_SIZE = 32;

	private final LoadoutData data;
	private final ItemManager itemManager;
	private final net.runelite.client.game.SpriteManager spriteManager;
	private final com.loadoutlab.UsageLog usageLog = com.loadoutlab.UsageLog.defaultLog();
	private final ComputeHook computeHook;
	private final ExclusionToggle exclusionToggle;
	private final ExclusionView exclusionView;
	private final JLabel exclusionsLabel = new JLabel();

	private final JTextField searchField = new JTextField();
	private final DefaultListModel<MonsterStats> monsterModel = new DefaultListModel<>();
	private final JList<MonsterStats> monsterList = new JList<>(monsterModel);
	private final JScrollPane monsterScroll;
	private final JPanel selectedRow = new JPanel(new BorderLayout(4, 0));
	private final JLabel selectedLabel = new JLabel();
	private final JLabel monsterNote = new JLabel();
	private final JCheckBox f2pOnly = new JCheckBox("Non-members gear only");
	private final JCheckBox slayerTask = new JCheckBox("On slayer task");
	private final javax.swing.JComboBox<String> spellbook =
		new javax.swing.JComboBox<>(new String[]{"Any spellbook", "Standard", "Ancient", "Arceuus"});
	private final JPanel resultsPanel = new JPanel();
	private final JLabel statusLabel = new JLabel(" ");
	private final Timer searchDebounce;

	/** Guards against programmatic search-field changes re-opening the list. */
	private boolean suppressSearchEvents;

	private MonsterStats selectedMonster;
	/** Per-style expanded game-best (BiS) sections - hidden by default,
	 * each card's header toggles only its own section. */
	private final java.util.Set<CombatStyle> gameBestExpanded = java.util.EnumSet.noneOf(CombatStyle.class);
	private Map<CombatStyle, StyleResult> lastResults;

	public LoadoutLabPanel(LoadoutData data, ItemManager itemManager,
		net.runelite.client.game.SpriteManager spriteManager, ComputeHook computeHook,
		ExclusionToggle exclusionToggle, ExclusionView exclusionView)
	{
		this.data = data;
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
		this.computeHook = computeHook;
		this.exclusionToggle = exclusionToggle;
		this.exclusionView = exclusionView;

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

		// Curated mechanics note (finishing items, immunities) for the
		// selected monster - so a correct suggestion doesn't look wrong.
		monsterNote.setForeground(new Color(200, 170, 110));
		monsterNote.setFont(monsterNote.getFont().deriveFont(11f));
		monsterNote.setAlignmentX(LEFT_ALIGNMENT);
		monsterNote.setVisible(false);
		top.add(monsterNote);

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

		slayerTask.setOpaque(false);
		slayerTask.setForeground(new Color(200, 200, 200));
		slayerTask.setAlignmentX(LEFT_ALIGNMENT);
		slayerTask.setToolTipText("Fighting this monster on a slayer task (enables slayer helmet bonuses)");
		slayerTask.addActionListener(e -> recompute());
		top.add(slayerTask);

		// Lock the magic card's auto-spell to one spellbook.
		spellbook.setAlignmentX(LEFT_ALIGNMENT);
		spellbook.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		spellbook.setToolTipText("Limit magic suggestions to spells from one spellbook"
			+ " (powered staves are always considered)");
		spellbook.addActionListener(e -> recompute());
		top.add(spellbook);

		// Excluded items ("protected" from suggestions) - click to manage.
		exclusionsLabel.setForeground(new Color(200, 140, 140));
		exclusionsLabel.setFont(exclusionsLabel.getFont().deriveFont(11f));
		exclusionsLabel.setAlignmentX(LEFT_ALIGNMENT);
		exclusionsLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		exclusionsLabel.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				showExclusionsMenu(e);
			}
		});
		top.add(exclusionsLabel);
		refreshExclusionsLabel();

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
		usageLog.record(monster.label());
		selectedLabel.setText("vs " + monster.label());
		selectedRow.setVisible(true);
		String note = com.loadoutlab.data.MonsterNotes.noteFor(monster);
		monsterNote.setText(note == null ? "" : "<html>" + note + "</html>");
		monsterNote.setVisible(note != null);
		revalidate();
		repaint();
		recompute();
	}

	private String spellbookLock()
	{
		int index = spellbook.getSelectedIndex();
		return index <= 0 ? "" : ((String) spellbook.getSelectedItem()).toLowerCase();
	}

	private void refreshExclusionsLabel()
	{
		int count = exclusionView.snapshot().size();
		exclusionsLabel.setText(count == 0 ? "" : "Excluded items: " + count + " (click to manage)");
		exclusionsLabel.setVisible(count > 0);
	}

	private void showExclusionsMenu(java.awt.event.MouseEvent e)
	{
		javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
		for (Integer id : exclusionView.snapshot())
		{
			GearItem item = data.getGear(id);
			String label = item == null ? ("item " + id) : item.label();
			javax.swing.JMenuItem entry = new javax.swing.JMenuItem("Allow again: " + label);
			entry.addActionListener(a ->
			{
				exclusionToggle.toggle(id);
				refreshExclusionsLabel();
				recompute();
			});
			menu.add(entry);
		}
		menu.show(exclusionsLabel, e.getX(), e.getY());
	}

	/** Right-click menu on a suggested item: exclude it and recompute. A
	 * container weapon (blowpipe) also offers its loaded ammo. */
	private void attachExclusionMenu(JLabel cell, List<GearItem> items)
	{
		cell.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mousePressed(java.awt.event.MouseEvent e)
			{
				maybeShow(e);
			}

			@Override
			public void mouseReleased(java.awt.event.MouseEvent e)
			{
				maybeShow(e);
			}

			private void maybeShow(java.awt.event.MouseEvent e)
			{
				if (!e.isPopupTrigger())
				{
					return;
				}
				javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
				for (GearItem item : items)
				{
					javax.swing.JMenuItem exclude = new javax.swing.JMenuItem("Exclude " + item.label() + " from suggestions");
					exclude.addActionListener(a ->
					{
						exclusionToggle.toggle(item.getId());
						refreshExclusionsLabel();
						recompute();
					});
					menu.add(exclude);
				}
				menu.show(cell, e.getX(), e.getY());
			}
		});
	}

	/** The dart loaded in a blowpipe result, resolved for exclusion menus. */
	private GearItem loadedDart(DpsResult result)
	{
		String type = result.getAttackType();
		int idx = type.indexOf(" - ");
		if (idx < 0 || !type.startsWith("ranged"))
		{
			return null;
		}
		Integer dartId = com.loadoutlab.engine.BlowpipeDarts.baseIdForTierName(type.substring(idx + 3));
		return dartId == null ? null : data.getGear(dartId);
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
		if (MascotSpinner.available())
		{
			resultsPanel.add(new MascotSpinner());
		}
		JLabel computing = new JLabel("Optimizing vs " + selectedMonster.getName() + "...");
		computing.setForeground(new Color(160, 160, 160));
		computing.setAlignmentX(LEFT_ALIGNMENT);
		computing.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
		resultsPanel.add(computing);
		resultsPanel.revalidate();
		resultsPanel.repaint();
		statusLabel.setText(" ");
		computeHook.compute(selectedMonster, f2pOnly.isSelected(), slayerTask.isSelected(),
			spellbookLock(), () -> statusLabel.setText(" "));
	}

	private void clearSelection()
	{
		selectedMonster = null;
		selectedRow.setVisible(false);
		selectedLabel.setText("");
		monsterNote.setText("");
		monsterNote.setVisible(false);
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
		lastResults = results;
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
			boolean flying = style == CombatStyle.MELEE
				&& selectedMonster != null && selectedMonster.hasAttribute("flying");
			boolean immune = com.loadoutlab.engine.MonsterMechanics.styleImmune(selectedMonster, style);
			boolean leafy = selectedMonster != null && selectedMonster.hasAttribute("leafy");
			JLabel none = new JLabel(immune
				? "Immune to " + style.toString().toLowerCase()
				: vyre
				? "Immune - needs a vyre weapon"
				: leafy
				? "Needs leaf-bladed / broad / Magic Dart"
				: flying
					? "Flying - needs a halberd"
					: "No usable owned set found.");
			none.setForeground(new Color(160, 160, 160));
			none.setAlignmentX(LEFT_ALIGNMENT);
			if (vyre)
			{
				none.setToolTipText("Only the Ivandis flail, blisterwood weapons,"
					+ " Sunspear, or Hallowed flail can damage this monster");
			}
			else if (flying)
			{
				none.setToolTipText("Melee cannot reach this monster"
					+ " except with halberds or salamanders");
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
		addAssumesRow(card, result.boostLabel,
			"You own this boost - the numbers assume you drink it"
				+ " (never assumed below your live boosted levels)");
		addPrayerLine(card, best);
		addStyleLine(card, style, best);
		addSpellLine(card, style, best);
		addDartLine(card, best);
		card.add(Box.createVerticalStrut(4));
		card.add(iconGrid(best, result.spec, result.specWeapon, result.specExpectedDamage,
			result.specDrainValue, best.getExpectedHit(),
			"Swapped into your best set for one special attack"));

		// The ceiling: the game-wide best set, so "off" numbers are inspectable.
		// The header always shows the summary; clicking it shows/hides the rest.
		if (result.overallBest != null && result.overallBest.getDps() > 0)
		{
			card.add(Box.createVerticalStrut(6));
			boolean expanded = gameBestExpanded.contains(style);
			double pct = 100.0 * best.getDps() / result.overallBest.getDps();
			JLabel ceiling = new JLabel(String.format("%s Game best: %.2f DPS - you are at %.0f%%",
				expanded ? "v" : ">",
				result.overallBest.getDps(), Math.min(100.0, pct)));
			ceiling.setForeground(new Color(160, 160, 160));
			ceiling.setFont(ceiling.getFont().deriveFont(11f));
			ceiling.setAlignmentX(LEFT_ALIGNMENT);
			ceiling.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			ceiling.setToolTipText(expanded ? "Click to hide the game-best set" : "Click to show the game-best set");
			ceiling.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mouseClicked(java.awt.event.MouseEvent e)
				{
					if (!gameBestExpanded.remove(style))
					{
						gameBestExpanded.add(style);
					}
					if (selectedMonster != null && lastResults != null)
					{
						showResults(selectedMonster, lastResults);
					}
				}
			});
			card.add(ceiling);
			if (expanded)
			{
				addAssumesRow(card, result.gameBoostLabel,
					"The ceiling assumes the best prayers and boost"
						+ " in the game, regardless of your unlocks");
				addSpellLine(card, style, result.overallBest);
				addDartLine(card, result.overallBest);
				card.add(Box.createVerticalStrut(4));
				card.add(iconGrid(result.overallBest, result.gameSpec, result.gameSpecWeapon, result.gameSpecExpectedDamage,
					result.gameSpecDrainValue, result.overallBest.getExpectedHit(),
					"The strongest special attack that exists vs this monster"));
			}
		}
		return card;
	}

	private static final javax.swing.ImageIcon PRAYER_ICON = loadPrayerIcon();

	private static javax.swing.ImageIcon loadPrayerIcon()
	{
		try
		{
			java.awt.image.BufferedImage img = net.runelite.client.util.ImageUtil.loadImageResource(
				net.runelite.client.game.SkillIconManager.class, "/skill_icons_small/prayer.png");
			return new javax.swing.ImageIcon(img.getScaledInstance(14, 14, java.awt.Image.SCALE_SMOOTH));
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}

	/** The set's total prayer bonus - just the prayer icon and the number. */
	private void addPrayerLine(JPanel card, DpsResult result)
	{
		int prayer = result.getLoadout().getBonuses().getPrayer();
		if (prayer == 0)
		{
			return;
		}
		JLabel line = new JLabel(PRAYER_ICON == null ? String.format("Prayer %+d", prayer)
			: String.format("%+d", prayer));
		if (PRAYER_ICON != null)
		{
			line.setIcon(PRAYER_ICON);
			line.setIconTextGap(4);
		}
		line.setForeground(new Color(160, 160, 160));
		line.setFont(line.getFont().deriveFont(11f));
		line.setAlignmentX(LEFT_ALIGNMENT);
		line.setToolTipText("Gear prayer bonus - slower prayer drain");
		card.add(line);
	}

	/** The attack style the numbers use: "Style: Slash (aggressive)". */
	private void addStyleLine(JPanel card, CombatStyle style, DpsResult result)
	{
		if (style == CombatStyle.MAGIC)
		{
			return; // the spell / powered-staff line already covers magic
		}
		String type = result.getAttackType();
		String text;
		if (style == CombatStyle.RANGED)
		{
			text = type.contains("rapid") ? "Rapid" : "Accurate";
		}
		else
		{
			text = capitalize(type);
		}
		JLabel line = new JLabel("Style: " + text);
		line.setForeground(new Color(150, 170, 230));
		line.setFont(line.getFont().deriveFont(11f));
		line.setAlignmentX(LEFT_ALIGNMENT);
		line.setToolTipText("Set your combat style to this for the shown numbers");
		card.add(line);
	}

	/** Blowpipes: name the loaded dart the numbers assume. */
	private void addDartLine(JPanel card, DpsResult result)
	{
		String type = result.getAttackType();
		int idx = type.indexOf(" - ");
		if (idx < 0 || !type.startsWith("ranged"))
		{
			return;
		}
		JLabel dart = new JLabel("Loaded with: " + type.substring(idx + 3));
		dart.setForeground(new Color(150, 170, 230));
		dart.setFont(dart.getFont().deriveFont(11f));
		dart.setAlignmentX(LEFT_ALIGNMENT);
		dart.setToolTipText("The blowpipe's numbers include this dart's ranged"
			+ " strength (right-click to exclude the darts)");
		GearItem dartItem = loadedDart(result);
		if (dartItem != null)
		{
			attachExclusionMenu(dart, List.of(dartItem));
		}
		card.add(dart);
	}

	/** Everything the old spec line said, as the spec cell's tooltip. */
	private static String specTooltip(SpecialAttack spec, double expectedDamage,
		double drainValue, double replacedAutoExpected, String fallbackTooltip)
	{
		String headline = drainValue > 0.5
			? String.format("Spec: %s - %.0f dmg + drain ~%.0f (%d%% energy)",
				spec.getDisplayName(), expectedDamage, drainValue, spec.getEnergyCost())
			: String.format("Spec: %s - avg %.0f dmg (%d%% energy)",
				spec.getDisplayName(), expectedDamage, spec.getEnergyCost());
		String note = spec.getNote();
		// Spec throughput: weaving this spec on cooldown adds sustained dps
		// (energy regen 10% per 30s; the Lightbearer doubles it).
		String sustained = String.format("Weaving on cooldown: about +%.2f dps"
				+ " (+%.2f with a Lightbearer)",
			spec.sustainedDpsBonus(expectedDamage, replacedAutoExpected, false),
			spec.sustainedDpsBonus(expectedDamage, replacedAutoExpected, true));
		String drain = drainValue > 0.5
			? String.format("<br>Defence drain worth about %.0f extra damage over"
				+ " the kill (your set hits harder once it lands - bigger"
				+ " payoff on high-HP targets).", drainValue)
			: "";
		return "<html>" + headline
			+ "<br>" + (note.isEmpty() ? fallbackTooltip : note)
			+ "<br>" + sustained + drain + "</html>";
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
		String name = result.getSpellName();
		String book = data.getSpells().stream()
			.filter(s -> name.equals(s.getName()))
			.map(s -> s.getSpellbook())
			.findFirst().orElse("");
		JPanel row = iconRow(card);
		JLabel spell = new JLabel(name);
		spell.setForeground(new Color(150, 170, 230));
		spell.setFont(spell.getFont().deriveFont(11f));
		spell.setToolTipText(book.isEmpty() ? "Autocast this spell"
			: "Autocast this spell (" + capitalize(book) + " spellbook)");
		int sprite = AssumeIcons.spellSprite(name);
		if (sprite >= 0)
		{
			attachSprite(spell, sprite);
			spell.setIconTextGap(4);
		}
		row.add(spell);
		if (name.contains("Demonbane"))
		{
			JLabel mod = new JLabel();
			mod.setToolTipText("Assumes Mark of Darkness is active");
			attachSprite(mod, AssumeIcons.MARK_OF_DARKNESS);
			row.add(mod);
		}
	}

	/** A left-aligned, height-capped flow row added to the card. */
	private JPanel iconRow(JPanel card)
	{
		JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		card.add(row);
		return row;
	}

	/**
	 * The assumed prayer + boost as icons - prayer-book sprite plus the
	 * potion/heart item icon; names live in the tooltips. Unmapped parts
	 * (e.g. "Current boosted levels") stay as text.
	 */
	private void addAssumesRow(JPanel card, String label, String tooltip)
	{
		if (label == null || label.isEmpty())
		{
			return;
		}
		JPanel row = iconRow(card);
		JLabel prefix = new JLabel("Assumes:");
		prefix.setForeground(new Color(160, 160, 160));
		prefix.setFont(prefix.getFont().deriveFont(11f));
		prefix.setToolTipText(tooltip);
		row.add(prefix);
		for (String part : label.split(" \\+ "))
		{
			JLabel chip = new JLabel();
			chip.setToolTipText(part + " - " + tooltip);
			int sprite = AssumeIcons.prayerSprite(part);
			int item = AssumeIcons.boostItem(part);
			if (sprite >= 0)
			{
				attachSprite(chip, sprite);
			}
			else if (item >= 0)
			{
				attachItemIcon(chip, item);
			}
			else
			{
				chip.setText(part);
				chip.setForeground(new Color(160, 160, 160));
				chip.setFont(chip.getFont().deriveFont(11f));
			}
			row.add(chip);
		}
	}

	/** Game-cache sprite, scaled to line height, set async. */
	private void attachSprite(JLabel label, int spriteId)
	{
		spriteManager.getSpriteAsync(spriteId, 0, img ->
			SwingUtilities.invokeLater(() -> label.setIcon(new javax.swing.ImageIcon(
				img.getScaledInstance(-1, 16, java.awt.Image.SCALE_SMOOTH)))));
	}

	/** Item icon scaled to line height (the native 36x32 dwarfs a text row). */
	private void attachItemIcon(JLabel label, int itemId)
	{
		net.runelite.client.util.AsyncBufferedImage img = itemManager.getImage(itemId);
		Runnable set = () -> label.setIcon(new javax.swing.ImageIcon(
			img.getScaledInstance(-1, 18, java.awt.Image.SCALE_SMOOTH)));
		img.onLoaded(() -> SwingUtilities.invokeLater(set));
		set.run();
	}

	/**
	 * The set as a fixed 3x4 equipment grid - 11 explicit slots (empty =
	 * empty box) plus the spec weapon as the 12th cell, amber-bordered.
	 * Fixed rows x cols means the preferred height is always right (the
	 * old wrapping grid clipped its second row).
	 */
	private JPanel iconGrid(DpsResult result, SpecialAttack spec, GearItem specWeapon, double specExpected,
		double specDrainValue, double replacedAutoExpected, String specFallbackTooltip)
	{
		JPanel icons = new JPanel(new java.awt.GridLayout(3, 4, 2, 2));
		icons.setOpaque(false);
		icons.setAlignmentX(LEFT_ALIGNMENT);
		int cell = ICON_SIZE + 4;
		for (GearSlot slotType : GearSlot.values())
		{
			GearItem item = result.getLoadout().get(slotType);
			JLabel slot = new JLabel();
			slot.setPreferredSize(new Dimension(cell, cell));
			slot.setHorizontalAlignment(SwingConstants.CENTER);
			if (item != null)
			{
				slot.setBorder(BorderFactory.createLineBorder(new Color(70, 70, 70)));
				slot.setToolTipText(slotName(slotType) + ": " + item.label() + " (right-click to exclude)");
				AsyncBufferedImage img = itemManager.getImage(item.getId());
				img.addTo(slot);
				List<GearItem> menuItems = new java.util.ArrayList<>();
				menuItems.add(item);
				GearItem dart = slotType == GearSlot.WEAPON ? loadedDart(result) : null;
				if (dart != null)
				{
					menuItems.add(dart);
				}
				attachExclusionMenu(slot, menuItems);
			}
			else
			{
				slot.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 50)));
				slot.setToolTipText(slotName(slotType) + ": empty");
			}
			icons.add(slot);
		}
		// Cell 12: the special-attack weapon to swap in.
		JLabel specCell = new JLabel();
		specCell.setPreferredSize(new Dimension(cell, cell));
		specCell.setHorizontalAlignment(SwingConstants.CENTER);
		if (spec != null && specWeapon != null && specExpected > 0)
		{
			specCell.setBorder(BorderFactory.createLineBorder(new Color(220, 180, 120)));
			specCell.setToolTipText(specTooltip(spec, specExpected,
				specDrainValue, replacedAutoExpected, specFallbackTooltip));
			itemManager.getImage(specWeapon.getId()).addTo(specCell);
			attachExclusionMenu(specCell, List.of(specWeapon));
		}
		else
		{
			specCell.setBorder(BorderFactory.createLineBorder(new Color(50, 50, 50)));
			specCell.setToolTipText("Spec: none");
		}
		icons.add(specCell);
		// Stretch past the minimum so there is no dead right margin, but cap
		// the width - unbounded stretch made cells balloon on wide layouts.
		int height = 3 * cell + 4;
		icons.setPreferredSize(new Dimension(4 * cell + 6, height));
		icons.setMaximumSize(new Dimension(4 * (cell + 8) + 6, height));
		return icons;
	}

	private static String slotName(GearSlot slot)
	{
		String name = slot.name().toLowerCase();
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	private static String capitalize(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
