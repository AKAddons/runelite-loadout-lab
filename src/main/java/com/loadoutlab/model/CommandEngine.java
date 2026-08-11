package com.loadoutlab.model;

import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.optimizer.OptimizerService;
import java.util.List;
import java.util.Map;

/**
 * Executes contract commands (docs/COMPANION_CONTRACT.md) against the
 * core-owned {@link PageState} and publishes the resulting page. This
 * is the layer BOTH UIs share: the Companion sends `command` messages
 * here; the bare text UI will call {@link #execute} directly. During
 * the transition the old Swing panel still drives computes its own
 * way - its results flow through {@link #onResults} too, so the
 * Companion mirrors whichever surface the user drives.
 */
public class CommandEngine
{
	/** The plugin's compute path, in ComputeHook argument order. */
	public interface Compute
	{
		void compute(MonsterStats monster, boolean f2pOnly, boolean onSlayerTask,
			boolean inWilderness, String spellbookLock, int maxTradeables, int riskBudgetGp,
			boolean antifirePotion, int deathCharge, boolean specWeapon,
			Map<CombatStyle, String> boostPicks, Map<CombatStyle, String> prayerPicks,
			int upgradeBudgetGp, int maxSwaps, boolean raidBoost, Runnable onDone);
	}

	private final LoadoutData data;
	private final PageState state;
	private final Compute compute;
	private final CompanionLink link;
	/** Live global-store counts (excluded/simmed/stored) - supplied by
	 * the plugin, read at page-build time so chips are never stale. */
	private volatile java.util.function.Supplier<Map<String, Object>> counts;

	public void setCounts(java.util.function.Supplier<Map<String, Object>> counts)
	{
		this.counts = counts;
	}

	/** Global-store toggles, implemented by the plugin over the same
	 * Commands factories the classic panel uses - so store history
	 * stays in ONE place (the plugin's stack) during the transition. */
	public interface StoreOps
	{
		boolean toggleExclusion(int itemId);

		boolean toggleSim(int itemId);

		/** ALL-sets-scope pin/unpin on the given mob's profile. */
		void pin(int monsterId, String slot, int itemId);

		void unpin(int monsterId, String slot);

		/** Highlight these ids in the open bank; null clears. */
		void showInBank(java.util.Set<Integer> itemIds);

		/** Filter the open bank to these ids in this layout; nulls clear. */
		void filterBank(java.util.Set<Integer> itemIds, int[] layout);

		/** Per-mob profile reads/writes (MonsterProfileStore-backed). */
		String pinnedSpell(int monsterId);

		void setPinnedSpell(int monsterId, String spellName);

		int pinnedSpec(int monsterId);

		void setPinnedSpec(int monsterId, int itemId);

		String note(int monsterId);

		void setNote(int monsterId, String note);
	}

	private volatile StoreOps stores;

	public void setStoreOps(StoreOps stores)
	{
		this.stores = stores;
	}

	/** The plugin's roster compute path (ComputeHook.computeRoster). */
	public interface RosterCompute
	{
		void computeRoster(List<MonsterStats> mobs, boolean f2pOnly, boolean onSlayerTask,
			boolean inWilderness, String spellbookLock, int maxTradeables, int riskBudgetGp,
			boolean antifirePotion, int deathCharge, boolean specWeapon,
			Map<CombatStyle, String> boostPicks, Map<CombatStyle, String> prayerPicks,
			int upgradeBudgetGp, int maxSwaps, boolean raidBoost, Runnable onDone);
	}

	private volatile RosterCompute rosterCompute;
	private volatile List<com.loadoutlab.data.MonsterGroups.MonsterGroup> groups;

	public void setRosterCompute(RosterCompute rosterCompute)
	{
		this.rosterCompute = rosterCompute;
	}
	/** Engine-owned history for seam-driven actions. Separate from the
	 * old panel's stack during the transition (its commands re-sync
	 * Swing controls the engine does not know about); they merge when
	 * the panel leaves. */
	private final com.loadoutlab.command.CommandHistory history =
		new com.loadoutlab.command.CommandHistory();

	public CommandEngine(LoadoutData data, PageState state, Compute compute, CompanionLink link)
	{
		this.data = data;
		this.state = state;
		this.compute = compute;
		this.link = link;
	}

	private static final Map<String, String> PARAM_LABELS = Map.ofEntries(
		Map.entry("onTask", "On task"), Map.entry("inWilderness", "Wilderness"),
		Map.entry("f2pOnly", "F2P"), Map.entry("specWeapon", "Spec weapon"),
		Map.entry("antifirePotion", "Antifire"), Map.entry("raidBoost", "Raid boost"),
		Map.entry("deathCharge", "Death charge"), Map.entry("viewingBis", "View"),
		Map.entry("selectedTab", "Tab"), Map.entry("spellbookLock", "Spellbook"),
		Map.entry("riskBudgetGp", "Risk cap"), Map.entry("upgradeBudgetGp", "Upgrade budget"),
		Map.entry("maxSwaps", "Inventory"), Map.entry("toaInvocation", "Invocation"));

	/** Handle one contract command; returns false for an unknown or
	 * malformed command (refused loudly at the seam, never guessed). */
	@SuppressWarnings("unchecked")
	public boolean execute(String name, Map<String, Object> args)
	{
		switch (name == null ? "" : name)
		{
			case "select":
			{
				Object query = args == null ? null : args.get("query");
				if (!(query instanceof String) || ((String) query).isBlank())
				{
					return false;
				}
				// Groups first on an exact-ish name hit, else single mobs -
				// mirrors the classic search's dropdown priorities loosely;
				// a group named like the query opens the roster.
				com.loadoutlab.data.MonsterGroups.MonsterGroup group = groupFor((String) query);
				MonsterStats next = null;
				if (group == null)
				{
					List<MonsterStats> matches = data.searchMonsters((String) query, 1);
					if (matches.isEmpty())
					{
						return false;
					}
					next = matches.get(0);
					if (next.getId() == (state.mob() == null ? -1 : state.mob().getId()))
					{
						recompute();
						return true;
					}
				}
				Object[] prev = state.selectionSnapshot();
				boolean hadSelection = state.hasSelection();
				MonsterStats mobPick = next;
				com.loadoutlab.data.MonsterGroups.MonsterGroup groupPick = group;
				return history.execute(new com.loadoutlab.command.Command()
				{
					@Override
					public boolean apply()
					{
						if (groupPick != null)
						{
							state.selectRoster(groupPick.getMobs(), groupPick.getName());
						}
						else
						{
							state.select(mobPick);
						}
						recompute();
						return true;
					}

					@Override
					public boolean revert()
					{
						state.restoreSelection(prev);
						if (hadSelection)
						{
							recompute();
						}
						else
						{
							clearResults();
						}
						return true;
					}

					@Override
					public String getDescription()
					{
						return "vs " + (groupPick != null ? groupPick.getName() : mobPick.label());
					}
				});
			}
			case "set-param":
			{
				Object param = args == null ? null : args.get("param");
				if (!(param instanceof String))
				{
					return false;
				}
				String key = (String) param;
				Object next = args.get("value");
				Object prev = state.paramsNode().get(key);
				if (java.util.Objects.equals(prev, next))
				{
					return true;
				}
				String label = PARAM_LABELS.getOrDefault(key, key);
				return history.execute(new com.loadoutlab.command.Command()
				{
					@Override
					public boolean apply()
					{
						return applyParam(key, next);
					}

					@Override
					public boolean revert()
					{
						return applyParam(key, prev);
					}

					@Override
					public String getDescription()
					{
						return Boolean.TRUE.equals(next) ? label + " on"
							: Boolean.FALSE.equals(next) ? label + " off"
							: label + " " + next;
					}
				});
			}
			case "toggle-exclusion":
			case "toggle-sim":
			{
				StoreOps ops = stores;
				Object itemId = args == null ? null : args.get("itemId");
				if (ops == null || !(itemId instanceof Number))
				{
					return false;
				}
				int id = ((Number) itemId).intValue();
				if ("toggle-exclusion".equals(name) ? !ops.toggleExclusion(id) : !ops.toggleSim(id))
				{
					return false;
				}
				recompute();
				return true;
			}
			case "pin":
			case "unpin":
			{
				StoreOps ops = stores;
				MonsterStats mob = state.mob();
				Object slot = args == null ? null : args.get("slot");
				if (ops == null || mob == null || !(slot instanceof String))
				{
					return false;
				}
				if ("pin".equals(name))
				{
					Object itemId = args.get("itemId");
					if (!(itemId instanceof Number))
					{
						return false;
					}
					ops.pin(mob.getId(), (String) slot, ((Number) itemId).intValue());
				}
				else
				{
					ops.unpin(mob.getId(), (String) slot);
				}
				recompute();
				return true;
			}
			case "set-prayer-pick":
			case "set-boost-pick":
			{
				Object style = args == null ? null : args.get("style");
				if (!(style instanceof String))
				{
					return false;
				}
				CombatStyle combatStyle;
				try
				{
					combatStyle = CombatStyle.valueOf(((String) style).toUpperCase());
				}
				catch (IllegalArgumentException ex)
				{
					return false;
				}
				Object value = args.get("value");
				state.setPick("set-prayer-pick".equals(name), combatStyle,
					value instanceof String ? (String) value : null);
				recompute();
				return true;
			}
			case "set-pinned-spell":
			case "set-pinned-spec":
			case "set-note":
			{
				StoreOps ops = stores;
				MonsterStats mob = state.mob();
				if (ops == null || mob == null)
				{
					return false;
				}
				if ("set-pinned-spell".equals(name))
				{
					Object spell = args == null ? null : args.get("name");
					ops.setPinnedSpell(mob.getId(), spell instanceof String ? (String) spell : "");
					recompute();
				}
				else if ("set-pinned-spec".equals(name))
				{
					Object itemId = args == null ? null : args.get("itemId");
					ops.setPinnedSpec(mob.getId(),
						itemId instanceof Number ? ((Number) itemId).intValue() : 0);
					recompute();
				}
				else
				{
					Object text = args == null ? null : args.get("text");
					ops.setNote(mob.getId(), text instanceof String ? (String) text : "");
					republish();
				}
				return true;
			}
			case "bank-show":
			{
				StoreOps ops = stores;
				if (ops == null)
				{
					return false;
				}
				Object ids = args == null ? null : args.get("ids");
				if (ids instanceof List)
				{
					java.util.Set<Integer> itemIds = new java.util.HashSet<>();
					for (Object id : (List<?>) ids)
					{
						if (id instanceof Number)
						{
							itemIds.add(((Number) id).intValue());
						}
					}
					ops.showInBank(itemIds);
				}
				else
				{
					ops.showInBank(null);
				}
				return true;
			}
			case "bank-filter":
			{
				StoreOps ops = stores;
				if (ops == null)
				{
					return false;
				}
				Object ids = args == null ? null : args.get("ids");
				Object layout = args == null ? null : args.get("layout");
				if (ids instanceof List && layout instanceof List)
				{
					java.util.Set<Integer> itemIds = new java.util.LinkedHashSet<>();
					for (Object id : (List<?>) ids)
					{
						if (id instanceof Number)
						{
							itemIds.add(((Number) id).intValue());
						}
					}
					List<?> slots = (List<?>) layout;
					int[] positions = new int[slots.size()];
					for (int i = 0; i < slots.size(); i++)
					{
						positions[i] = slots.get(i) instanceof Number
							? ((Number) slots.get(i)).intValue() : -1;
					}
					ops.filterBank(itemIds, positions);
				}
				else
				{
					ops.filterBank(null, null);
				}
				return true;
			}
			case "recompute":
				recompute();
				return true;
			case "undo":
			case "redo":
			{
				// The command's own publish runs BEFORE CommandHistory moves
				// it between stacks - republish after, so the page's history
				// node (canUndo/canRedo/labels) reflects the settled stacks.
				boolean ok = "undo".equals(name) ? history.undo() : history.redo();
				if (ok)
				{
					republish();
				}
				return ok;
			}
			default:
				return false;
		}
	}

	@SuppressWarnings("unchecked")
	private void recompute()
	{
		MonsterStats mob = state.mob();
		List<MonsterStats> roster = state.rosterMobs();
		RosterCompute rosterPath = rosterCompute;
		if (mob == null && (roster == null || rosterPath == null))
		{
			return;
		}
		Object[] a = state.computeArgs();
		if (mob != null)
		{
			mob = atInvocation(mob);
			compute.compute(mob, (Boolean) a[0], (Boolean) a[1], (Boolean) a[2],
				(String) a[3], (Integer) a[4], (Integer) a[5], (Boolean) a[6],
				(Integer) a[7], (Boolean) a[8], (Map<CombatStyle, String>) a[9],
				(Map<CombatStyle, String>) a[10], (Integer) a[11], (Integer) a[12],
				(Boolean) a[13], () ->
				{
				});
		}
		else
		{
			List<MonsterStats> scaled = new java.util.ArrayList<>();
			for (MonsterStats m : roster)
			{
				scaled.add(atInvocation(m));
			}
			roster = scaled;
			rosterPath.computeRoster(roster, (Boolean) a[0], (Boolean) a[1], (Boolean) a[2],
				(String) a[3], (Integer) a[4], (Integer) a[5], (Boolean) a[6],
				(Integer) a[7], (Boolean) a[8], (Map<CombatStyle, String>) a[9],
				(Map<CombatStyle, String>) a[10], (Integer) a[11], (Integer) a[12],
				(Boolean) a[13], () ->
				{
				});
		}
	}

	/** ToA mobs scale with the invocation param - the same transform
	 * the classic panel applies before every compute. */
	private MonsterStats atInvocation(MonsterStats mob)
	{
		return com.loadoutlab.engine.MonsterMechanics.isToaInvocationScaled(mob)
			? com.loadoutlab.engine.MonsterMechanics.atToaInvocation(mob, state.toaInvocation())
			: mob;
	}

	/** A curated group whose name matches the query (contains, both
	 * directions) - loaded once, resolved like the classic dropdown. */
	private com.loadoutlab.data.MonsterGroups.MonsterGroup groupFor(String query)
	{
		List<com.loadoutlab.data.MonsterGroups.MonsterGroup> loaded = groups;
		if (loaded == null)
		{
			loaded = com.loadoutlab.data.MonsterGroups.load(data);
			groups = loaded;
		}
		String q = query.toLowerCase();
		if (q.length() < 3)
		{
			return null;
		}
		for (com.loadoutlab.data.MonsterGroups.MonsterGroup candidate : loaded)
		{
			String name = candidate.getName().toLowerCase();
			if (name.contains(q) || q.contains(name))
			{
				return candidate;
			}
		}
		return null;
	}

	private volatile List<MonsterStats> lastMobs;
	private volatile List<Map<CombatStyle, OptimizerService.StyleResult>> lastPerMob;

	/** Every single-mob compute lands here (engine-driven or old-panel
	 * driven) - assemble the page, attach the params the state holds,
	 * and publish. */
	public void onResults(MonsterStats mob, Map<CombatStyle, OptimizerService.StyleResult> results)
	{
		onRosterResults(List.of(mob), List.of(results));
	}

	/** Roster computes: published with the shared params node. */
	public void onRosterResults(List<MonsterStats> mobs,
		List<Map<CombatStyle, OptimizerService.StyleResult>> perMob)
	{
		lastMobs = mobs;
		lastPerMob = perMob;
		republish();
	}

	/** Route one param change: view params republish, the rest compute.
	 * Shared by the live command and its undo/redo replays. */
	private boolean applyParam(String key, Object value)
	{
		if (!state.setParam(key, value))
		{
			return false;
		}
		if (PageState.isViewParam(key))
		{
			republish();
		}
		else
		{
			recompute();
		}
		return true;
	}

	/** Reverting the first select: no mob, no results - an empty page. */
	private void clearResults()
	{
		lastMobs = null;
		lastPerMob = null;
		link.publishPage(withHistory(RenderModel.page(List.of())));
	}

	private void republish()
	{
		List<MonsterStats> mobs = lastMobs;
		List<Map<CombatStyle, OptimizerService.StyleResult>> perMob = lastPerMob;
		if (mobs == null || perMob == null)
		{
			return;
		}
		Map<String, Object> entry = RenderModel.entry(mobs, perMob);
		entry.put("params", state.paramsNode());
		decorateProfiles(entry);
		Map<String, Object> thrallsNode = null;
		if (Boolean.TRUE.equals(state.paramsNode().get("thralls")))
		{
			double dps = com.loadoutlab.engine.ExtraDps.thrallDps(magicLevel);
			String tier = com.loadoutlab.engine.ExtraDps.thrallTier(magicLevel);
			if (dps > 0 && tier != null)
			{
				thrallsNode = new java.util.LinkedHashMap<>();
				thrallsNode.put("dps", dps);
				thrallsNode.put("tier", tier);
				entry.put("thralls", thrallsNode);
			}
		}
		Map<String, Object> page = withHistory(RenderModel.page(List.of(entry)));
		List<String> spellNames = new java.util.ArrayList<>();
		for (com.loadoutlab.data.SpellStats spell : data.getSpells())
		{
			spellNames.add(spell.getName());
		}
		page.put("spells", spellNames);
		page.put("assumeOptions", assumeOptions());
		java.util.function.Supplier<Map<String, Object>> countSupplier = counts;
		page.put("reportText", ReportBuilder.build(coreVersion, state, mobs, perMob,
			countSupplier == null ? null : countSupplier.get(), thrallsNode));
		link.publishPage(page);
	}

	private volatile String coreVersion = "dev";
	/** The account's live Magic level - thrall dps scales off it. */
	private volatile int magicLevel = 99;

	public void setMagicLevel(int magicLevel)
	{
		this.magicLevel = magicLevel;
	}

	public void setCoreVersion(String coreVersion)
	{
		this.coreVersion = coreVersion;
	}

	/** Attach per-mob profile state (pinned spell/spec, note) to each
	 * mob node so renderers can show and edit it. */
	@SuppressWarnings("unchecked")
	private void decorateProfiles(Map<String, Object> entry)
	{
		StoreOps ops = stores;
		Object mobsNode = entry.get("mobs");
		if (ops == null || !(mobsNode instanceof List))
		{
			return;
		}
		for (Object node : (List<?>) mobsNode)
		{
			if (node instanceof Map)
			{
				Map<String, Object> mob = (Map<String, Object>) node;
				Object id = mob.get("id");
				if (id instanceof Number)
				{
					int monsterId = ((Number) id).intValue();
					mob.put("pinnedSpell", ops.pinnedSpell(monsterId));
					mob.put("pinnedSpec", ops.pinnedSpec(monsterId));
					mob.put("note", ops.note(monsterId));
				}
			}
		}
	}

	private volatile Map<String, Object> assumeOptionsCache;

	/** Static per-style prayer/boost option lists (key + label),
	 * mirroring the classic pick menus. */
	private Map<String, Object> assumeOptions()
	{
		Map<String, Object> cached = assumeOptionsCache;
		if (cached != null)
		{
			return cached;
		}
		Map<String, Object> prayers = new java.util.LinkedHashMap<>();
		Map<String, Object> boosts = new java.util.LinkedHashMap<>();
		for (CombatStyle style : CombatStyle.concreteValues())
		{
			prayers.put(style.name().toLowerCase(), java.util.Arrays.asList(
				com.loadoutlab.engine.PrayerBonuses.optionsFor(style)));
			List<Map<String, Object>> styleBoosts = new java.util.ArrayList<>();
			for (com.loadoutlab.engine.BoostProfile b : com.loadoutlab.engine.BoostProfile.values())
			{
				if (b == com.loadoutlab.engine.BoostProfile.NONE
					|| b == com.loadoutlab.engine.BoostProfile.LIVE_CURRENT)
				{
					continue;
				}
				boolean universal = b.boosts('a') && b.boosts('r') && b.boosts('m');
				boolean forStyle = style == CombatStyle.MELEE ? b.boosts('a')
					: style == CombatStyle.RANGED ? b.boosts('r') : b.boosts('m');
				if (universal || forStyle)
				{
					Map<String, Object> option = new java.util.LinkedHashMap<>();
					option.put("key", b.name());
					option.put("label", b.toString());
					styleBoosts.add(option);
				}
			}
			boosts.put(style.name().toLowerCase(), styleBoosts);
		}
		Map<String, Object> options = new java.util.LinkedHashMap<>();
		options.put("prayers", prayers);
		options.put("boosts", boosts);
		assumeOptionsCache = options;
		return options;
	}

	private Map<String, Object> withHistory(Map<String, Object> page)
	{
		Map<String, Object> node = new java.util.LinkedHashMap<>();
		node.put("canUndo", history.canUndo());
		node.put("canRedo", history.canRedo());
		node.put("undoLabel", history.peekUndoDescription());
		node.put("redoLabel", history.peekRedoDescription());
		page.put("history", node);
		java.util.function.Supplier<Map<String, Object>> supplier = counts;
		if (supplier != null)
		{
			page.put("counts", supplier.get());
		}
		return page;
	}
}
