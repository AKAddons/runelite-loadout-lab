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

		void toggleAlwaysFilter(int itemId);

		/** Write a wrench-panel supply default (the classic grey chip). */
		void setSupplyDefault(String category, String choice);

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

		/** Mob-scoped exclusion/sim (undoable Commands-factory backed). */
		void excludeForMob(int monsterId, String scope, int itemId);

		void simForMob(int monsterId, int itemId);

		/** The mob's scoped lists for the card trio menus:
		 * [{id, name, scope?}] built plugin-side with labels. */
		List<Map<String, Object>> mobExclusions(int monsterId);

		List<Map<String, Object>> mobSims(int monsterId);

		List<Map<String, Object>> mobFilters(int monsterId);

		void removeMobExclusion(int monsterId, String scope, int itemId);

		void removeMobSim(int monsterId, int itemId);

		void addMobFilter(int monsterId, int itemId);

		void removeMobFilter(int monsterId, String scope, int itemId);

		/** Per-mob supply overrides (profileId-keyed, like the classic). */
		Map<String, String> supplyOverrides(int profileId);

		void setSupplyOverride(int profileId, String category, String choice);
	}

	private volatile StoreOps stores;

	/** Opens the shown setup in the official wiki calculator - the
	 * plugin wires the network side (shortlink POST + browser). Unset,
	 * the command refuses and the button does nothing. */
	public interface WikiCalcOpener
	{
		void open(MonsterStats mob, com.loadoutlab.engine.DpsResult shown, int dartId,
			String assumes, boolean onSlayerTask, boolean inWilderness);
	}

	private volatile WikiCalcOpener wikiCalcOpener;

	public void setWikiCalcOpener(WikiCalcOpener opener)
	{
		this.wikiCalcOpener = opener;
	}

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

	/** Wrench-panel supply defaults (category -> config enum name),
	 * re-read per publish so config changes land immediately. */
	private volatile java.util.function.Supplier<Map<String, String>> supplyDefaults;

	public void setSupplyDefaults(java.util.function.Supplier<Map<String, String>> supplyDefaults)
	{
		this.supplyDefaults = supplyDefaults;
	}

	/** The player's LIVE spellbook name ("standard"/"ancient"/"lunar"/
	 * "arceuus"), pushed by the plugin's varbit subscriber - the book
	 * plate turns red when an answer assumes a different book. */
	private volatile String liveSpellbook = "";

	public void setLiveSpellbook(String liveSpellbook)
	{
		String next = liveSpellbook == null ? "" : liveSpellbook;
		boolean changed = !next.equals(this.liveSpellbook);
		this.liveSpellbook = next;
		if (changed)
		{
			republish();
		}
	}

	/** spellName -> its book, built once from the corpus. */
	private volatile Map<String, Object> spellbooksCache;

	private Map<String, Object> spellbooks()
	{
		Map<String, Object> cached = spellbooksCache;
		if (cached != null)
		{
			return cached;
		}
		Map<String, Object> books = new java.util.LinkedHashMap<>();
		for (com.loadoutlab.data.SpellStats spell : data.getSpells())
		{
			books.put(spell.getName(), spell.getSpellbook());
		}
		spellbooksCache = books;
		return books;
	}

	/** Combo-rune preference (config-fed) + casting-kit ownership. */


	/** Storage name for an item needing a fetch trip (source dots). */
	private volatile java.util.function.IntFunction<String> itemLocation;

	public void setItemLocation(java.util.function.IntFunction<String> itemLocation)
	{
		this.itemLocation = itemLocation;
	}

	/** Canonical ownership (dose variants collapse) for Detect scans. */
	private volatile java.util.function.IntPredicate ownedCheck;

	public void setOwnedCheck(java.util.function.IntPredicate ownedCheck)
	{
		this.ownedCheck = ownedCheck;
	}

	/** Classic seedAssumptionDefaults: config-driven params for a NEW
	 * selection (spec chip, thralls when reachable+book owned, Death
	 * Charge at Magic 80) - supplied by the plugin, which owns the
	 * config and the live levels. */
	private volatile java.util.function.Supplier<Map<String, Object>> assumptionSeeder;

	public void setAssumptionSeeder(java.util.function.Supplier<Map<String, Object>> assumptionSeeder)
	{
		this.assumptionSeeder = assumptionSeeder;
	}

	/** Classic Detect: the best owned antifire tier (0/1/2) for a
	 * fire-breathing selection - supplied by the plugin (owned scan). */
	private volatile java.util.function.Function<List<MonsterStats>, Integer> antifireResolver;

	public void setAntifireResolver(java.util.function.Function<List<MonsterStats>, Integer> antifireResolver)
	{
		this.antifireResolver = antifireResolver;
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
	/** Page snapshots aligned with the history stacks: undo restores
	 * the page you were LOOKING AT instead of recomputing it (field
	 * report 2026-08-20: undo flashed the stale result over the
	 * loader, then recomputed an answer that was already on screen).
	 * The empty map marks "no page yet"; deeper than the cap falls
	 * back to a recompute. */
	/** A restore point: the page AND the results it was rendered from.
	 * Carrying only the page left lastMobs/lastPerMob on the pre-undo
	 * answer, so the next republish (a tab click, a chip, a note) drew
	 * the WRONG mob's card - found by the pre-release adversarial pass
	 * 2026-08-22. A null page means "not restorable"; the entry still
	 * holds its place so the deques stay in phase with the history. */
	private static final class Snapshot
	{
		final Map<String, Object> page;
		final List<MonsterStats> mobs;
		final List<Map<CombatStyle, OptimizerService.StyleResult>> perMob;

		Snapshot(Map<String, Object> page, List<MonsterStats> mobs,
			List<Map<CombatStyle, OptimizerService.StyleResult>> perMob)
		{
			this.page = page;
			this.mobs = mobs;
			this.perMob = perMob;
		}
	}

	private final java.util.ArrayDeque<Snapshot> undoPages =
		new java.util.ArrayDeque<>();
	private final java.util.ArrayDeque<Snapshot> redoPages =
		new java.util.ArrayDeque<>();
	private static final int SNAPSHOT_CAP = 32;
	private boolean restoringSnapshot;

	/** A page the snapshot restore may publish: present, and NOT a
	 * pending (mid-compute) page - restoring one would freeze the
	 * loader with no compute to finish it. */
	private static boolean restorable(Map<String, Object> page)
	{
		if (page == null || page.isEmpty())
		{
			return false;
		}
		Object entries = page.get("entries");
		if (entries instanceof List)
		{
			for (Object entry : (List<?>) entries)
			{
				if (entry instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) entry).get("pending")))
				{
					return false;
				}
			}
		}
		return true;
	}

	/** history.execute plus the page snapshot - every recorded command
	 * routes through here. */
	private boolean record(com.loadoutlab.command.Command cmd)
	{
		Map<String, Object> before = link.lastPage();
		List<MonsterStats> beforeMobs = lastMobs;
		List<Map<CombatStyle, OptimizerService.StyleResult>> beforePerMob = lastPerMob;
		boolean ok = history.execute(cmd);
		if (ok)
		{
			undoPages.push(new Snapshot(restorable(before) ? before : null,
				beforeMobs, beforePerMob));
			while (undoPages.size() > SNAPSHOT_CAP)
			{
				undoPages.removeLast();
			}
			redoPages.clear();
		}
		return ok;
	}

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
				Object exactId = args == null ? null : args.get("id");
				if (exactId instanceof Number)
				{
					int wanted = ((Number) exactId).intValue();
					// Versions can SHARE an npc id (Duke's Awakened and
					// Post-quest are both 12191, Awakened first in corpus
					// order - field report 2026-08-21: the dropdown listed
					// Post-quest, the click selected Awakened). The
					// dropdown sends its row's version; a bare id (link-
					// ins) takes the best-ranked id match via search.
					Object versionArg = args.get("version");
					MonsterStats first = null;
					for (MonsterStats m : data.getMonsters())
					{
						if (m.getId() != wanted)
						{
							continue;
						}
						if (versionArg instanceof String
							&& ((String) versionArg).equalsIgnoreCase(m.getVersion()))
						{
							return selectResolved(m, null);
						}
						if (first == null)
						{
							first = m;
						}
					}
					if (first == null)
					{
						return false;
					}
					// No version, or a version this corpus no longer
					// spells the same way: take the row the SEARCH ranks
					// first. Falling back to corpus order handed over the
					// Awakened boss - strictly worse than sending no
					// version at all (adversarial pass 2026-08-22).
					for (MonsterStats ranked : data.searchMonsters(first.getName(), 10))
					{
						if (ranked.getId() == wanted)
						{
							return selectResolved(ranked, null);
						}
					}
					return selectResolved(first, null);
				}
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
				return selectResolved(next, group);
			}
			case "add-mob":
			{
				Object query = args == null ? null : args.get("query");
				if (!(query instanceof String) || ((String) query).isBlank() || !state.hasSelection())
				{
					return false;
				}
				List<MonsterStats> matches = data.searchMonsters((String) query, 1);
				if (matches.isEmpty())
				{
					return false;
				}
				MonsterStats added = matches.get(0);
				List<MonsterStats> current = state.rosterMobs() != null
					? state.rosterMobs() : List.of(state.mob());
				for (MonsterStats m : current)
				{
					if (m.getId() == added.getId())
					{
						return false;
					}
				}
				List<MonsterStats> combined = new java.util.ArrayList<>(current);
				combined.add(added);
				Object[] prevSel = state.selectionSnapshot();
				return record(new com.loadoutlab.command.Command()
				{
					@Override
					public boolean apply()
					{
						state.selectRoster(combined, "Custom roster");
						recompute();
						return true;
					}

					@Override
					public boolean revert()
					{
						state.restoreSelection(prevSel);
						recompute();
						return true;
					}

					@Override
					public String getDescription()
					{
						return "Add " + added.getName() + " to result";
					}
				});
			}
			case "remove-mob":
			{
				Object index = args == null ? null : args.get("index");
				// A single-mob view shows its own row with an X (field ask
				// 2026-08-22) - there is no roster behind it, so the lone
				// selection stands in as a one-mob list.
				List<MonsterStats> current = state.rosterMobs() != null
					? state.rosterMobs()
					: state.mob() == null ? null : List.of(state.mob());
				if (!(index instanceof Number) || current == null)
				{
					return false;
				}
				int at = ((Number) index).intValue();
				if (at < 0 || at >= current.size())
				{
					return false;
				}
				MonsterStats removed = current.get(at);
				List<MonsterStats> remaining = new java.util.ArrayList<>(current);
				remaining.remove(at);
				Object[] prevSel = state.selectionSnapshot();
				return record(new com.loadoutlab.command.Command()
				{
					@Override
					public boolean apply()
					{
						if (remaining.isEmpty())
						{
							// Removing the last mob returns to the start
							// state, exactly as if nothing were searched
							// yet (field ask 2026-08-22).
							state.select(null);
							clearResults();
							publishIdle();
							return true;
						}
						if (remaining.size() == 1)
						{
							state.select(remaining.get(0));
						}
						else
						{
							state.selectRoster(remaining, "Custom roster");
						}
						recompute();
						return true;
					}

					@Override
					public boolean revert()
					{
						state.restoreSelection(prevSel);
						recompute();
						return true;
					}

					@Override
					public String getDescription()
					{
						return "Remove " + removed.getName() + " from result";
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
				boolean unchanged = java.util.Objects.equals(prev, next);
				if (unchanged && "riskBudgetGp".equals(key))
				{
					// 75k is BOTH a legal cap and the engine's uncapped
					// sentinel, so the gp number alone cannot tell "set a
					// 75k cap" from "already uncapped" - typing 75k was
					// swallowed while the field kept showing it (found by
					// the pre-release adversarial pass 2026-08-22).
					boolean capped = Boolean.TRUE.equals(state.paramsNode().get("riskCapped"));
					unchanged = capped == (next != null);
				}
				if (unchanged)
				{
					return true;
				}
				if ("lensIndex".equals(key))
				{
					// Switching which roster row is expanded is a VIEW
					// gesture, not an action - Back must never land on it
					// (field ask 2026-08-20).
					return applyParam(key, next);
				}
				String label = PARAM_LABELS.getOrDefault(key, key);
				return record(new com.loadoutlab.command.Command()
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
			case "set-supply-default":
			{
				StoreOps ops = stores;
				Object category = args == null ? null : args.get("category");
				Object choice = args == null ? null : args.get("choice");
				if (ops == null || !(category instanceof String) || !(choice instanceof String))
				{
					return false;
				}
				ops.setSupplyDefault((String) category, (String) choice);
				recompute();
				return true;
			}
			case "wiki-calc":
			{
				WikiCalcOpener opener = wikiCalcOpener;
				List<MonsterStats> mobs = lastMobs;
				List<Map<CombatStyle, com.loadoutlab.optimizer.OptimizerService.StyleResult>> perMob = lastPerMob;
				if (opener == null || mobs == null || perMob == null)
				{
					return false;
				}
				MonsterStats mob = shownMob();
				int at = -1;
				for (int i = 0; mob != null && i < mobs.size(); i++)
				{
					if (mobs.get(i).label().equals(mob.label()))
					{
						at = i;
						break;
					}
				}
				if (at < 0 || perMob.size() <= at)
				{
					return false;
				}
				boolean bis = args != null && Boolean.TRUE.equals(args.get("bis"))
					|| Boolean.TRUE.equals(state.paramsNode().get("viewingBis"));
				Object styleArg = args == null ? null : args.get("style");
				String styleName = styleArg instanceof String ? (String) styleArg : null;
				if (styleName == null)
				{
					// No style given (the footer button): the VIEWED tab, or
					// on auto the strongest shown style - the card's own
					// auto-tab rule.
					Object tab = state.paramsNode().get("selectedTab");
					styleName = tab instanceof String && !((String) tab).isEmpty()
						? (String) tab : null;
				}
				com.loadoutlab.optimizer.OptimizerService.StyleResult result = null;
				if (styleName != null)
				{
					try
					{
						result = perMob.get(at).get(CombatStyle.valueOf(
							styleName.toUpperCase(java.util.Locale.ROOT)));
					}
					catch (IllegalArgumentException ex)
					{
						return false;
					}
				}
				else
				{
					double best = -1;
					for (com.loadoutlab.optimizer.OptimizerService.StyleResult candidate
						: perMob.get(at).values())
					{
						com.loadoutlab.engine.DpsResult side = candidate == null ? null
							: bis ? candidate.overallBest
							: candidate.owned == null || candidate.owned.isEmpty()
								? null : candidate.owned.get(0);
						if (side != null && side.getDps() > best)
						{
							best = side.getDps();
							result = candidate;
						}
					}
				}
				com.loadoutlab.engine.DpsResult shown = result == null ? null
					: bis ? result.overallBest
					: result.owned == null || result.owned.isEmpty() ? null : result.owned.get(0);
				if (shown == null)
				{
					return false;
				}
				// The blowpipe's loaded dart rides the attackType string
				// (same parse as the RenderModel fact).
				int dartId = -1;
				String attackType = shown.getAttackType();
				if (attackType != null && attackType.contains(" - "))
				{
					Integer parsed = com.loadoutlab.engine.BlowpipeDarts.baseIdForTierName(
						attackType.substring(attackType.indexOf(" - ") + 3));
					dartId = parsed == null ? -1 : parsed;
				}
				// A link-out is not an action - never recorded in history.
				opener.open(mob, shown, dartId,
					bis ? result.gameBoostLabel : result.boostLabel,
					membersFlag("onTask"),
					Boolean.TRUE.equals(state.paramsNode().get("inWilderness")));
				return true;
			}
			case "toggle-always-filter":
			{
				StoreOps ops = stores;
				Object itemId = args == null ? null : args.get("itemId");
				if (ops == null || !(itemId instanceof Number))
				{
					return false;
				}
				ops.toggleAlwaysFilter(((Number) itemId).intValue());
				republish();
				return true;
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
				boolean exclusion = "toggle-exclusion".equals(name);
				Object labelArg = args.get("label");
				String what = labelArg instanceof String ? (String) labelArg : "item";
				// Self-inverse: a toggle undoes itself. Recorded HERE (the
				// one live stack) - the classic recorded into the plugin's
				// stack, orphaned since the merge-back, so Back skipped
				// every exclude/sim (field report 2026-08-20).
				return record(new com.loadoutlab.command.Command()
				{
					@Override
					public boolean apply()
					{
						if (!(exclusion ? ops.toggleExclusion(id) : ops.toggleSim(id)))
						{
							return false;
						}
						refreshAfterStoreToggle();
						return true;
					}

					@Override
					public boolean revert()
					{
						return apply();
					}

					@Override
					public String getDescription()
					{
						return (exclusion ? "Exclude " : "Sim ") + what;
					}
				});
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
				boolean prayer = "set-prayer-pick".equals(name);
				String next = value instanceof String ? (String) value : null;
				Map<String, Object> picks = (Map<String, Object>) state.paramsNode()
					.get(prayer ? "prayerPicks" : "boostPicks");
				Object prevRaw = picks == null ? null : picks.get(combatStyle.name().toLowerCase());
				String prev = prevRaw instanceof String ? (String) prevRaw : null;
				if (java.util.Objects.equals(prev, next))
				{
					return true;
				}
				String kind = prayer ? "Prayer" : "Boost";
				return record(new com.loadoutlab.command.Command()
				{
					@Override
					public boolean apply()
					{
						state.setPick(prayer, combatStyle, next);
						recompute();
						return true;
					}

					@Override
					public boolean revert()
					{
						state.setPick(prayer, combatStyle, prev);
						recompute();
						return true;
					}

					@Override
					public String getDescription()
					{
						return kind + " " + (next == null ? "detect" : next.toLowerCase())
							+ " - " + combatStyle.name().toLowerCase();
					}
				});
			}
			case "exclude-for-mob":
			case "sim-for-mob":
			{
				StoreOps ops = stores;
				// Rosters have no state.mob() - the lensed row is the mob
				// (the set-note lesson, third member; field report
				// 2026-08-20: the card trio's adds no-oped on rosters).
				MonsterStats mob = shownMob();
				Object itemId = args == null ? null : args.get("itemId");
				if (ops == null || mob == null || !(itemId instanceof Number))
				{
					return false;
				}
				int id = ((Number) itemId).intValue();
				boolean exclude = "exclude-for-mob".equals(name);
				Object scopeArg = args.get("scope");
				String scope = scopeArg instanceof String ? (String) scopeArg : "ALL";
				Object labelArg = args.get("label");
				String what = labelArg instanceof String ? (String) labelArg : "item";
				int mobId = mob.getId();
				String mobName = mob.getName();
				return record(new com.loadoutlab.command.Command()
				{
					@Override
					public boolean apply()
					{
						if (exclude)
						{
							ops.excludeForMob(mobId, scope, id);
						}
						else
						{
							ops.simForMob(mobId, id);
						}
						recompute();
						return true;
					}

					@Override
					public boolean revert()
					{
						if (exclude)
						{
							ops.removeMobExclusion(mobId, scope, id);
						}
						else
						{
							ops.removeMobSim(mobId, id);
						}
						recompute();
						return true;
					}

					@Override
					public String getDescription()
					{
						return (exclude ? "Exclude " : "Sim ") + what + " - " + mobName;
					}
				});
			}
			case "remove-mob-exclusion":
			case "remove-mob-sim":
			case "add-mob-filter":
			case "remove-mob-filter":
			{
				StoreOps ops = stores;
				MonsterStats mob = shownMob();
				Object itemId = args == null ? null : args.get("itemId");
				if (ops == null || mob == null || !(itemId instanceof Number))
				{
					return false;
				}
				int id = ((Number) itemId).intValue();
				Object scope = args == null ? null : args.get("scope");
				String scopeKey = scope instanceof String ? (String) scope : "ALL";
				Object labelArg = args == null ? null : args.get("label");
				String what = labelArg instanceof String ? (String) labelArg : "item";
				int mobId = mob.getId();
				String mobName = mob.getName();
				String command = name;
				return record(new com.loadoutlab.command.Command()
				{
					private void run(String cmd)
					{
						switch (cmd)
						{
							case "remove-mob-exclusion":
								ops.removeMobExclusion(mobId, scopeKey, id);
								recompute();
								break;
							case "remove-mob-sim":
								ops.removeMobSim(mobId, id);
								recompute();
								break;
							case "add-mob-filter":
								ops.addMobFilter(mobId, id);
								republish();
								break;
							case "remove-mob-filter":
								ops.removeMobFilter(mobId, scopeKey, id);
								republish();
								break;
							case "exclude-for-mob":
								ops.excludeForMob(mobId, scopeKey, id);
								recompute();
								break;
							default:
								ops.simForMob(mobId, id);
								recompute();
								break;
						}
					}

					@Override
					public boolean apply()
					{
						run(command);
						return true;
					}

					@Override
					public boolean revert()
					{
						switch (command)
						{
							case "remove-mob-exclusion": run("exclude-for-mob"); break;
							case "remove-mob-sim": run("sim-for-mob"); break;
							case "add-mob-filter": run("remove-mob-filter"); break;
							default: run("add-mob-filter"); break;
						}
						return true;
					}

					@Override
					public String getDescription()
					{
						switch (command)
						{
							case "remove-mob-exclusion": return "Allow " + what + " - " + mobName;
							case "remove-mob-sim": return "Stop simming " + what + " - " + mobName;
							case "add-mob-filter": return "Filter " + what + " - " + mobName;
							default: return "Unfilter " + what + " - " + mobName;
						}
					}
				});
			}
			case "set-supply-override":
			{
				StoreOps ops = stores;
				Object category = args == null ? null : args.get("category");
				Object choice = args == null ? null : args.get("choice");
				MonsterStats lensed = lensedMob();
				if (ops == null || lensed == null || !(category instanceof String))
				{
					return false;
				}
				ops.setSupplyOverride(lensed.profileId(), (String) category,
					choice instanceof String ? (String) choice : "DETECT");
				republish();
				return true;
			}
			case "set-pinned-spell":
			case "set-pinned-spec":
			case "set-note":
			{
				StoreOps ops = stores;
				// The LENS mob on rosters (field report 2026-08-15: notes
				// never saved on a trip - state.mob() is null there).
				MonsterStats mob = shownMob();
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
			case "refresh":
			{
				// The ledger's background refresh - silent by contract.
				recompute(true);
				return true;
			}
			case "republish":
				// Re-draw from the held results (no compute): a display
				// setting changed and the renderer reads config at paint.
				republish();
				return true;
			case "recompute":
				recompute();
				return true;
			case "undo":
			case "redo":
			{
				boolean isUndo = "undo".equals(name);
				Map<String, Object> currentPage = link.lastPage();
				Snapshot current = new Snapshot(
					restorable(currentPage) ? currentPage : null, lastMobs, lastPerMob);
				// CommandHistory DROPS an entry when a revert refuses or
				// throws (fail-open) - so a refusal consumes history depth
				// our deques must follow, or every later restore is one
				// step out of phase for the rest of the session.
				boolean hadEntry = isUndo ? history.canUndo() : history.canRedo();
				// The revert/apply bodies call recompute()/republish() -
				// suppressed: the snapshot IS the result of the state we
				// are returning to, so nothing needs computing.
				restoringSnapshot = true;
				boolean ok;
				try
				{
					ok = isUndo ? history.undo() : history.redo();
				}
				finally
				{
					restoringSnapshot = false;
				}
				java.util.ArrayDeque<Snapshot> from = isUndo ? undoPages : redoPages;
				java.util.ArrayDeque<Snapshot> to = isUndo ? redoPages : undoPages;
				if (!ok)
				{
					if (hadEntry && !from.isEmpty())
					{
						from.pop();
					}
					return false;
				}
				Snapshot restored = from.isEmpty() ? null : from.pop();
				to.push(current);
				if (restored != null && restored.page != null)
				{
					// The results follow their page - anything that
					// republishes next must read the restored answer.
					lastMobs = restored.mobs;
					lastPerMob = restored.perMob;
					Map<String, Object> snapshot = restored.page;
					// Instant restore: the captured page, but the CURRENT
					// view params (lens/tab hops since are not actions and
					// must survive the jump) and a fresh history node.
					Object entries = snapshot.get("entries");
					if (entries instanceof List)
					{
						for (Object entryNode : (List<?>) entries)
						{
							if (entryNode instanceof Map)
							{
								((Map<String, Object>) entryNode).put("params", state.paramsNode());
							}
						}
					}
					link.publishPage(withHistory(snapshot));
				}
				else
				{
					// Older than the snapshot window (or pre-first-page):
					// the classic path - recompute and republish.
					recompute();
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
		recompute(false);
	}

	/** Recompute when there is something to compute; otherwise publish the
	 * idle page so the global count pills still move. recompute() returns
	 * early with no mob selected, which left a simmed item counted in the
	 * store but not on the "+N" pill (field report 2026-08-27). */
	private void refreshAfterStoreToggle()
	{
		if (state.mob() == null && (state.rosterMobs() == null || rosterCompute == null))
		{
			if (link != null)
			{
				link.publishPage(withHistory(
					RenderModel.page(java.util.Collections.emptyList())));
			}
			return;
		}
		recompute();
	}

	/** silent = a BACKGROUND refresh (ledger settles): no status, no
	 * stage clear, no mascot - the fresh answer swaps in when it lands
	 * (field report 2026-08-15: post-boss loot churn made the panel
	 * thrash through clear/mascot/rebuild on every settle). */
	private void recompute(boolean silent)
	{
		if (restoringSnapshot)
		{
			return;
		}
		MonsterStats mob = state.mob();
		List<MonsterStats> roster = state.rosterMobs();
		RosterCompute rosterPath = rosterCompute;
		if (mob == null && (roster == null || rosterPath == null))
		{
			return;
		}
		if (!silent)
		{
			link.publishStatus(true);
			// Publish the PARAMS immediately (field report 2026-08-12: the
			// chips used to appear the moment you searched, so a wrong
			// parameter could be fixed without waiting out the compute).
			publishPending();
		}
		Object[] a = state.computeArgs();
		boolean anyFire = false;
		for (MonsterStats m : roster != null ? roster : List.of(mob))
		{
			anyFire |= com.loadoutlab.engine.DragonfireRules.breathesFire(m);
		}
		a[6] = ((Boolean) a[6]) && anyFire;
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
		link.publishStatus(false);
		republish();
	}

	/** Route one param change: view params republish, the rest compute.
	 * Shared by the live command and its undo/redo replays. */
	/** A members mechanic is ON only when its param is on AND the F2P lock
	 * is off - the lock vetoes without clearing (field ask 2026-08-27). */
	private boolean membersFlag(String key)
	{
		Map<String, Object> params = state.paramsNode();
		return Boolean.TRUE.equals(params.get(key))
			&& !Boolean.TRUE.equals(params.get("f2pOnly"));
	}

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

	/** A different account logged in: nothing survives - selection,
	 * results, history all reset and an empty page publishes. */
	public void clearForIdentityChange()
	{
		state.select(null);
		// CLEAR, never replay: reverting each entry pushed it onto the
		// REDO stack, so Forward re-applied the previous account's
		// commands against the new one's stores - and the snapshot
		// deques were never touched at all (pre-release adversarial
		// pass 2026-08-22). CommandHistory's own doc says entries from
		// another profile "would corrupt this one".
		history.clear();
		undoPages.clear();
		redoPages.clear();
		clearResults();
	}

	/** Reverting the first select: no mob, no results - an empty page. */
	private void clearResults()
	{
		lastMobs = null;
		lastPerMob = null;
		link.publishPage(withHistory(RenderModel.page(List.of())));
	}

	/** An in-flight page: the current selection's mobs and the live
	 * params, with no style results yet - the controls render at once
	 * and the cards fill in when the answer lands. */
	private void publishPending()
	{
		if (!javax.swing.SwingUtilities.isEventDispatchThread())
		{
			javax.swing.SwingUtilities.invokeLater(this::publishPending);
			return;
		}
		List<MonsterStats> mobs = state.rosterMobs() != null
			? state.rosterMobs() : state.mob() == null ? null : List.of(state.mob());
		if (mobs == null)
		{
			return;
		}
		List<Map<CombatStyle, OptimizerService.StyleResult>> empty = new java.util.ArrayList<>();
		for (int i = 0; i < mobs.size(); i++)
		{
			empty.add(java.util.Collections.emptyMap());
		}
		Map<String, Object> entry = RenderModel.entry(mobs, empty);
		entry.put("params", state.paramsNode());
		entry.put("pending", true);
		decorateProfiles(entry);
		Map<String, Object> page = withHistory(RenderModel.page(List.of(entry)));
		page.put("assumeOptions", assumeOptions());
		page.put("liveSpellbook", liveSpellbook);
		page.put("spellbooks", spellbooks());
		page.put("supplyCatalog", supplyCatalog());
		page.put("dartTiers", com.loadoutlab.engine.BlowpipeDarts.tiers());
		link.publishPage(page);
	}

	/** An idle page: no selection, but counts, catalogs and history -
	 * so the global chips exist BEFORE the first search. */
	public void publishIdle()
	{
		if (!javax.swing.SwingUtilities.isEventDispatchThread())
		{
			javax.swing.SwingUtilities.invokeLater(this::publishIdle);
			return;
		}
		Map<String, Object> page = withHistory(
			RenderModel.page(java.util.Collections.emptyList()));
		page.put("assumeOptions", assumeOptions());
		page.put("liveSpellbook", liveSpellbook);
		page.put("spellbooks", spellbooks());
		page.put("supplyCatalog", supplyCatalog());
		page.put("dartTiers", com.loadoutlab.engine.BlowpipeDarts.tiers());
		link.publishPage(page);
	}

	private void republish()
	{
		if (restoringSnapshot)
		{
			return;
		}
		if (!javax.swing.SwingUtilities.isEventDispatchThread())
		{
			javax.swing.SwingUtilities.invokeLater(this::republish);
			return;
		}
		List<MonsterStats> mobs = lastMobs;
		List<Map<CombatStyle, OptimizerService.StyleResult>> perMob = lastPerMob;
		if (mobs == null || perMob == null)
		{
			return;
		}
		Map<String, Object> params = state.paramsNode();
		// The classic wilderness gate: exclusives are ALWAYS in (field
		// report 2026-08-15: Calvar'ion showed no risk - the pinned
		// always-in chip never set the param).
		boolean exclusive = false;
		for (MonsterStats m : mobs)
		{
			exclusive |= com.loadoutlab.data.WildernessMonsters.isExclusive(m);
		}
		boolean wildy = exclusive || Boolean.TRUE.equals(params.get("inWilderness"));
		int keptSlots = wildy
			? (Boolean.TRUE.equals(params.get("protectItem")) ? 4 : 3) : -1;
		java.util.Set<Integer> simmedIds = new java.util.HashSet<>();
		java.util.function.Supplier<Map<String, Object>> countSupplierForFlags = counts;
		if (countSupplierForFlags != null)
		{
			Object simmedItems = countSupplierForFlags.get().get("simmedItems");
			if (simmedItems instanceof List)
			{
				for (Object item : (List<?>) simmedItems)
				{
					if (item instanceof Map)
					{
						Object id = ((Map<?, ?>) item).get("id");
						if (id instanceof Number)
						{
							simmedIds.add(((Number) id).intValue());
						}
					}
				}
			}
		}
		Map<String, Object> entry = RenderModel.entry(mobs, perMob, keptSlots,
			ownedCheck, simmedIds, itemLocation);
		entry.put("params", params);
		decorateProfiles(entry);
		entry.put("supplies", suppliesNode(mobs));
		Map<String, Object> shipNode = shipNode(mobs, perMob);
		if (shipNode != null)
		{
			entry.put("ship", shipNode);
		}
		Map<String, Object> thrallsNode = null;
		if (membersFlag("thralls"))
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
		page.put("liveSpellbook", liveSpellbook);
		page.put("spellbooks", spellbooks());
		page.put("supplyCatalog", supplyCatalog());
		page.put("dartTiers", com.loadoutlab.engine.BlowpipeDarts.tiers());
		java.util.function.Supplier<Map<String, Object>> countSupplier = counts;
		page.put("reportText", ReportBuilder.build(coreVersion, state, mobs, perMob, keptSlots,
			countSupplier == null ? null : countSupplier.get(), thrallsNode));
		link.publishPage(page);
	}

	private volatile String coreVersion = "dev";
	/** The account's live Magic level - thrall dps scales off it. */
	private volatile int magicLevel = 99;
	private volatile int rangedLevel = 99;
	private volatile int sailingLevel = 1;

	public void setMagicLevel(int magicLevel)
	{
		this.magicLevel = magicLevel;
	}

	public void setRangedLevel(int rangedLevel)
	{
		this.rangedLevel = rangedLevel;
	}

	public void setSailingLevel(int sailingLevel)
	{
		this.sailingLevel = sailingLevel;
	}

	public void setCoreVersion(String coreVersion)
	{
		this.coreVersion = coreVersion;
	}

	/** Shared by query- and id-resolved selects: the undoable command
	 * with detect-antifire at selection, exactly like the classic. */
	private boolean selectResolved(MonsterStats mobPick,
		com.loadoutlab.data.MonsterGroups.MonsterGroup groupPick)
	{
		Object[] prev = state.selectionSnapshot();
		boolean hadSelection = state.hasSelection();
		return record(new com.loadoutlab.command.Command()
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
				java.util.function.Function<List<MonsterStats>, Integer> resolve = antifireResolver;
				List<MonsterStats> selected = groupPick != null
					? groupPick.getMobs() : List.of(mobPick);
				if (resolve != null)
				{
					state.setParam("antifireMode", resolve.apply(selected));
				}
				// A fresh selection resets the tab so the default (the
				// best style for THIS mob) applies - the classic made a
				// new entry per selection, which cleared it implicitly.
				state.setParam("selectedTab", "");
				// A fresh search opens on YOURS - the answer you can act
				// on now; BiS is the aspirational view you opt into
				// (field ask 2026-08-22).
				state.setParam("viewingBis", false);
				// Task-only bosses are ALWAYS on task (the classic rule:
				// the Sire cannot be fought off-task, so the search must
				// never show it that way).
				for (MonsterStats m : selected)
				{
					if (com.loadoutlab.data.SlayerLockedMonsters.isTaskOnly(m))
					{
						state.setParam("onTask", true);
						break;
					}
				}
				// Wilderness-exclusives set the PARAM, not just the chip
				// (field report 2026-08-15: a 75k risk cap produced a 21M
				// recommendation - the display honored the exclusive but
				// the COMPUTE derived tradeables=-1 from the raw param and
				// ran unconstrained).
				for (MonsterStats m : selected)
				{
					if (com.loadoutlab.data.WildernessMonsters.isExclusive(m))
					{
						state.setParam("inWilderness", true);
						break;
					}
				}
				// Recommended trip inventory per selection (Andrew
				// 2026-08-14): raids carry 8 swaps, other groups 3, a
				// single mob 1. A fresh selection re-recommends; the
				// slider overrides after.
				boolean raid = !selected.isEmpty();
				for (MonsterStats m : selected)
				{
					raid &= com.loadoutlab.engine.RaidBoosts.suppliedBoost(m) != null;
				}
				state.setParam("maxSwaps", raid ? 8 : selected.size() > 1 ? 3 : 1);
				java.util.function.Supplier<Map<String, Object>> seeder = assumptionSeeder;
				if (seeder != null)
				{
					for (Map.Entry<String, Object> seed : seeder.get().entrySet())
					{
						state.setParam(seed.getKey(), seed.getValue());
					}
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

	/** The classic search dropdown feed: under 2 chars nothing, then
	 * up to 3 matching groups followed by up to 25 monsters. */
	public List<Map<String, Object>> searchOptions(String query)
	{
		List<Map<String, Object>> options = new java.util.ArrayList<>();
		String q = query == null ? "" : query.trim();
		if (q.length() < 2)
		{
			return options;
		}
		List<com.loadoutlab.data.MonsterGroups.MonsterGroup> loaded = groups;
		if (loaded == null)
		{
			loaded = com.loadoutlab.data.MonsterGroups.load(data);
			groups = loaded;
		}
		for (com.loadoutlab.data.MonsterGroups.MonsterGroup group
			: com.loadoutlab.data.MonsterGroups.search(loaded, q, 3))
		{
			Map<String, Object> node = new java.util.LinkedHashMap<>();
			node.put("label", group.label());
			node.put("group", group.getName());
			options.add(node);
		}
		for (MonsterStats m : data.searchMonsters(q, 25))
		{
			Map<String, Object> node = new java.util.LinkedHashMap<>();
			node.put("label", m.label());
			node.put("id", m.getId());
			if (com.loadoutlab.data.NavalCombat.isNaval(m.getName()))
			{
				node.put("naval", true);
			}
			// Versions can share an npc id - the picker must say WHICH
			// row it means (Duke 12191, field report 2026-08-21).
			node.put("version", m.getVersion());
			options.add(node);
		}
		return options;
	}

	/** Every supply category with its label, current DEFAULT and option
	 * list - the grey chip's menu (global defaults, not per-mob). */
	private List<Map<String, Object>> supplyCatalog()
	{
		java.util.function.Supplier<Map<String, String>> supplier = supplyDefaults;
		List<Map<String, Object>> out = new java.util.ArrayList<>();
		if (supplier == null)
		{
			return out;
		}
		Map<String, String> defaults = supplier.get();
		String[][] categories = {
			{com.loadoutlab.data.TripSupplies.FOOD, "Food"},
			{com.loadoutlab.data.TripSupplies.FAST_FOOD, "Fast food"},
			{com.loadoutlab.data.TripSupplies.PRAYER_RESTORE, "Prayer restore"},
			{com.loadoutlab.data.TripSupplies.SURGE, "Surge potion"},
			{com.loadoutlab.data.TripSupplies.SPELLBOOK_CAPE, "Spellbook cape"},
			{com.loadoutlab.data.TripSupplies.ANTIVENOM, "Anti-venom"},
		};
		for (String[] category : categories)
		{
			Map<String, Object> node = new java.util.LinkedHashMap<>();
			node.put("category", category[0]);
			node.put("label", category[1]);
			node.put("current", defaults.getOrDefault(category[0], "DETECT_BEST"));
			List<Map<String, Object>> options = new java.util.ArrayList<>();
			for (com.loadoutlab.data.TripSupplies.Option option
				: com.loadoutlab.data.TripSupplies.options(category[0]))
			{
				Map<String, Object> opt = new java.util.LinkedHashMap<>();
				opt.put("key", option.key);
				opt.put("name", option.name);
				options.add(opt);
			}
			node.put("options", options);
			out.add(node);
		}
		// The Arceuus-access choice rides the same menu in the classic.
		Map<String, Object> access = new java.util.LinkedHashMap<>();
		access.put("category", "arceuusAccess");
		access.put("label", "Arceuus access");
		access.put("current", defaults.getOrDefault("arceuusAccess", "DETECT_BEST"));
		access.put("options", List.of(
			Map.of("key", "DETECT_BEST", "name", "Direct (camp Arceuus)"),
			Map.of("key", "SPELLBOOK_SWAP",
				"name", "Via Spellbook Swap (Lunar home, adds swap + Vengeance runes)")));
		out.add(access);
		// Combination runes are a PANEL preference now (field ask
		// 2026-08-22), defaulting to detection - a config toggle and a
		// panel control for one behaviour is the source-of-truth trap
		// this release already paid for once.
		Map<String, Object> combo = new java.util.LinkedHashMap<>();
		combo.put("category", "comboRunes");
		combo.put("label", "Combination runes");
		combo.put("current", defaults.getOrDefault("comboRunes", "DETECT_BEST"));
		combo.put("options", List.of(
			Map.of("key", "ALWAYS", "name", "Always combine")));
		out.add(combo);
		return out;
	}

	/** The combination-rune preference: the panel's choice, with
	 * DETECT meaning "combine when you own combination runes". */
	private boolean combineRunes()
	{
		java.util.function.Supplier<Map<String, String>> supplier = supplyDefaults;
		String choice = supplier == null ? null : supplier.get().get("comboRunes");
		if ("ALWAYS".equals(choice))
		{
			return true;
		}
		if ("NONE".equals(choice))
		{
			return false;
		}
		java.util.function.IntPredicate owns = ownedCheck;
		if (owns == null)
		{
			return false;
		}
		for (int id : com.loadoutlab.data.SpellRunes.comboRuneIds())
		{
			if (owns.test(id))
			{
				return true;
			}
		}
		return false;
	}

	/** The mob a card gesture acts on: the single selection when there is
	 * one, else the lensed roster row. Rosters have no state.mob() - the
	 * set-note lesson (field report 2026-08-15: notes never saved on a
	 * trip), which every per-mob command has since had to repeat. */
	private MonsterStats shownMob()
	{
		return state.mob() != null ? state.mob() : lensedMob();
	}

	/** The lens-selected mob (supply overrides key off its profile). */
	private MonsterStats lensedMob()
	{
		List<MonsterStats> roster = state.rosterMobs();
		if (roster == null)
		{
			return state.mob();
		}
		Object lens = state.paramsNode().get("lensIndex");
		int at = lens instanceof Number ? ((Number) lens).intValue() : 0;
		return roster.get(Math.min(Math.max(at, 0), roster.size() - 1));
	}

	/** The classic activeSupplies, core-side: config defaults overlaid
	 * by the lensed mob's overrides, Detect = best owned tier, antivenom
	 * only for venomous rosters. Each node carries the category's full
	 * option list for the override menu. */
	private List<Map<String, Object>> suppliesNode(List<MonsterStats> mobs)
	{
		java.util.function.Supplier<Map<String, String>> defaultsSupplier = supplyDefaults;
		StoreOps ops = stores;
		java.util.function.IntPredicate owns = ownedCheck;
		MonsterStats lensed = lensedMob();
		List<Map<String, Object>> nodes = new java.util.ArrayList<>();
		if (defaultsSupplier == null || ops == null || owns == null || lensed == null)
		{
			return nodes;
		}
		Map<String, String> defaults = defaultsSupplier.get();
		Map<String, String> overrides = ops.supplyOverrides(lensed.profileId());
		for (Map.Entry<String, String> e : defaults.entrySet())
		{
			String category = e.getKey();
			String mode = overrides.getOrDefault(category, e.getValue());
			if ("NONE".equals(mode))
			{
				continue;
			}
			if (com.loadoutlab.data.TripSupplies.ANTIVENOM.equals(category))
			{
				boolean venomous = false;
				for (MonsterStats m : mobs)
				{
					venomous |= com.loadoutlab.data.TripSupplies.inflictsVenom(m);
				}
				if (!venomous)
				{
					continue;
				}
			}
			com.loadoutlab.data.TripSupplies.Option pick =
				"DETECT_BEST".equals(mode) || "DETECT".equals(mode)
					? com.loadoutlab.data.TripSupplies.detectBest(category, owns)
					: com.loadoutlab.data.TripSupplies.option(category, mode);
			if (pick == null)
			{
				continue;
			}
			Map<String, Object> node = new java.util.LinkedHashMap<>();
			node.put("category", category);
			node.put("name", pick.name);
			node.put("itemId", pick.ids.length > 0 ? pick.ids[0] : 0);
			List<Map<String, Object>> options = new java.util.ArrayList<>();
			for (com.loadoutlab.data.TripSupplies.Option option
				: com.loadoutlab.data.TripSupplies.options(category))
			{
				Map<String, Object> opt = new java.util.LinkedHashMap<>();
				opt.put("key", option.key);
				opt.put("name", option.name);
				options.add(opt);
			}
			node.put("options", options);
			nodes.add(node);
		}
		return nodes;
	}

	/** Attach per-mob profile state (pinned spell/spec, note) to each
	 * mob node so renderers can show and edit it. */
	@SuppressWarnings("unchecked")
	/** The trip's UTILITY casts for one mob: the fight's own spell (the
	 * Sire's Shadow Barrage stun), the thrall resurrection at the live
	 * tier, and Death Charge - each cast's runes tagged with why
	 * (field report 2026-08-15: 'not seeing any runes' on a trip whose
	 * gear never autocasts but whose FIGHT casts plenty). */
	private List<Map<String, Object>> utilityRunesFor(Map<String, Object> mob)
	{
		List<Map<String, Object>> out = new java.util.ArrayList<>();
		Object name = mob.get("name");
		MonsterStats stats = null;
		for (MonsterStats m : data.getMonsters())
		{
			if (m.getName().equals(name))
			{
				stats = m;
				break;
			}
		}
		String fightSpell = com.loadoutlab.data.MonsterSpellbooks.spellFor(stats);
		if (fightSpell != null)
		{
			addUtilityRunes(out, fightSpell, fightSpell + " (fight mechanic)");
		}
		Map<String, Object> params = state.paramsNode();
		if (membersFlag("thralls"))
		{
			String tier = com.loadoutlab.engine.ExtraDps.thrallTier(magicLevel);
			if (tier != null)
			{
				addUtilityRunes(out, "Resurrect " + tier + " Ghost",
					tier + " thrall (per resurrection)");
			}
		}
		Object dCharge = params.get("deathCharge");
		boolean f2pLocked = Boolean.TRUE.equals(params.get("f2pOnly"));
		if (!f2pLocked && dCharge instanceof Number && ((Number) dCharge).intValue() > 0)
		{
			addUtilityRunes(out, "Death Charge", "Death Charge (per cast)");
		}
		if (Boolean.TRUE.equals(params.get("spellbookSwap")))
		{
			// Supplies only (field ask 2026-08-22): the trip carries the
			// runes to keep Vengeance up. Vengeance does return damage,
			// but modelling that is its own problem - this is purely
			// "bring more runes".
			addUtilityRunes(out, "Vengeance", "Vengeance (per cast)");
			// The SWAP is only needed when something else wants another
			// book. Vengeance alone - no thralls, no Death Charge, no
			// fight book - is a perfectly good setup and must not drag
			// swap runes along (field ask 2026-08-22).
			String fightBook = com.loadoutlab.data.MonsterSpellbooks.bookFor(stats);
			boolean elsewhere = fightBook != null && !fightBook.isEmpty()
				&& !"lunar".equalsIgnoreCase(fightBook);
			elsewhere |= membersFlag("thralls");
			Object charge = params.get("deathCharge");
			elsewhere |= !f2pLocked && charge instanceof Number
				&& ((Number) charge).intValue() > 0;
			if (elsewhere)
			{
				addUtilityRunes(out, "Spellbook Swap", "Spellbook Swap (per swap)");
			}
		}
		return combineRunes() ? com.loadoutlab.data.SpellRunes.combineCombos(out) : out;
	}

	private static void addUtilityRunes(List<Map<String, Object>> out,
		String spell, String why)
	{
		List<Map<String, Object>> runes =
			com.loadoutlab.data.SpellRunes.costFor(spell, null, null);
		if (runes == null)
		{
			return;
		}
		for (Map<String, Object> rune : runes)
		{
			rune.put("why", why);
			out.add(rune);
		}
	}

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
					List<Map<String, Object>> utility = utilityRunesFor(mob);
					mob.put("utilityRunes", utility);
					// The casting kit: divine rune pouch > rune pouch when
					// owned; the Magic cape (t preferred) for the book
					// swap perk - suggestions only when the trip casts.
					java.util.function.IntPredicate owns = ownedCheck;
					if (!utility.isEmpty() && owns != null)
					{
						mob.put("castingPouch", owns.test(27281) ? 27281
							: owns.test(12791) ? 12791 : 12791);
						mob.put("castingCape", owns.test(9763) ? 9763
							: owns.test(9762) ? 9762 : -1);
					}
					mob.put("mobExclusions", ops.mobExclusions(monsterId));
					mob.put("mobSims", ops.mobSims(monsterId));
					mob.put("mobFilters", ops.mobFilters(monsterId));
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
		// Icon facts for the pickers (additive): prayer name -> sprite id,
		// boost label -> potion item id. Renderers draw, never map.
		Map<String, Object> prayerSprites = new java.util.LinkedHashMap<>();
		for (Object list : prayers.values())
		{
			for (Object name : (List<?>) list)
			{
				prayerSprites.put(String.valueOf(name),
					com.loadoutlab.data.AssumeIcons.prayerSprite(String.valueOf(name)));
			}
		}
		options.put("prayerSprites", prayerSprites);
		// Spellbook Swap is 96 Magic (Vengeance 94) - offer it when the
		// player can actually reach the level, boosts included.
		options.put("spellbookSwapAvailable", magicLevel + 5 >= 96);
		Map<String, Object> boostItems = new java.util.LinkedHashMap<>();
		for (Object list : boosts.values())
		{
			for (Object option : (List<?>) list)
			{
				String label = String.valueOf(((Map<?, ?>) option).get("label"));
				boostItems.put(label, com.loadoutlab.data.AssumeIcons.boostItem(label));
			}
		}
		options.put("boostItems", boostItems);
		Map<String, Object> spellSprites = new java.util.LinkedHashMap<>();
		for (com.loadoutlab.data.SpellStats spell : data.getSpells())
		{
			spellSprites.put(spell.getName(),
				com.loadoutlab.data.AssumeIcons.spellSprite(spell.getName()));
		}
		options.put("spellSprites", spellSprites);
		assumeOptionsCache = options;
		return options;
	}

	/**
	 * The ship node (REQ-SC-2/3/3b/4/7): cannons priced for a NAVAL mob only
	 * - the params persist for land mobs but are never read, the same veto
	 * shape as the F2P lock. Post-compute additive like thralls: cannons
	 * never steer the gear search.
	 *
	 * <p>Cannon 1 is the player's when the station is "cannon" (visible
	 * ranged level, wiki formula); every other cannon is crew-fired - the
	 * documented-but-stale Privateering scaling over a Sailing-level base,
	 * marked estimated while NavalCombat.crewFormulaStale(). A crew below
	 * the cannon's Privateering gate, or a player below its Ranged gate,
	 * cannot operate it: that cannon prices at zero and says why. The
	 * player-fired cannon borrows the worn ranged bonuses of the engine's
	 * best owned ranged set (weapon/shield/ammo excluded, per the wiki);
	 * crew cannons price bare.
	 */
	private Map<String, Object> shipNode(List<MonsterStats> mobs,
		List<Map<CombatStyle, OptimizerService.StyleResult>> perMob)
	{
		if (mobs == null || mobs.isEmpty())
		{
			return null;
		}
		Map<String, Object> params = state.paramsNode();
		// The LENSED mob decides (REQ-SC-7): a mixed roster prices cannons
		// only while a sea mob is under the lens.
		Object lensObj = params.get("lensIndex");
		int lens = lensObj instanceof Number ? ((Number) lensObj).intValue() : 0;
		MonsterStats lensed = mobs.get(Math.min(Math.max(lens, 0), mobs.size() - 1));
		if (!com.loadoutlab.data.NavalCombat.isNaval(lensed.getName()))
		{
			return null;
		}
		int count = params.get("cannonCount") instanceof Number
			? ((Number) params.get("cannonCount")).intValue() : 0;
		if (count <= 0)
		{
			return null;
		}
		MonsterStats mob = lensed;
		String ammo = String.valueOf(params.get("cannonAmmo"));
		String station = String.valueOf(params.get("playerStation"));
		int priv = params.get("crewPrivateering") instanceof Number
			? ((Number) params.get("crewPrivateering")).intValue() : 1;
		// The wiki rule: the player's worn ranged accuracy and strength join
		// their cannon fire, EXCLUDING the weapon, shield and ammo slots.
		// The manned player still wears armour - the best owned ranged set
		// the engine already computed - so the cannon borrows its bonuses.
		// Crew gear is unknowable and stays at zero.
		int wornAcc = 0;
		int wornStr = 0;
		Object lensForGear = params.get("lensIndex");
		int gearLens = lensForGear instanceof Number ? ((Number) lensForGear).intValue() : 0;
		gearLens = Math.min(Math.max(gearLens, 0), (perMob == null ? 1 : perMob.size()) - 1);
		OptimizerService.StyleResult rangedResult = perMob == null || perMob.size() <= gearLens
			|| perMob.get(gearLens) == null ? null
			: perMob.get(gearLens).get(CombatStyle.RANGED);
		if (rangedResult != null && rangedResult.overallBest != null)
		{
			com.loadoutlab.engine.Loadout worn = rangedResult.overallBest.getLoadout();
			for (com.loadoutlab.data.GearSlot slot : com.loadoutlab.data.GearSlot.values())
			{
				if (slot == com.loadoutlab.data.GearSlot.WEAPON
					|| slot == com.loadoutlab.data.GearSlot.SHIELD
					|| slot == com.loadoutlab.data.GearSlot.AMMO)
				{
					continue;
				}
				com.loadoutlab.data.GearItem item = worn.get(slot);
				if (item != null)
				{
					wornAcc += item.getOffensive().getRanged();
					wornStr += item.getBonuses().getRangedStrength();
				}
			}
		}
		Map<String, Object> node = new java.util.LinkedHashMap<>();
		node.put("station", station);
		node.put("ammo", ammo);
		node.put("wornAccuracy", wornAcc);
		node.put("wornStrength", wornStr);
		List<Map<String, Object>> cannonNodes = new java.util.ArrayList<>();
		boolean anyCrew = false;
		double total = 0;
		for (int i = 0; i < count; i++)
		{
			String tier = String.valueOf(params.get(i == 0 ? "cannon1Material" : "cannon2Material"));
			com.loadoutlab.data.NavalCombat.Cannon cannon =
				com.loadoutlab.data.NavalCombat.cannon(tier);
			if (cannon == null)
			{
				continue;
			}
			// Shared ammo, clamped to what THIS cannon can fire (a mithril
			// cannon handed dragon balls fires mithril ones).
			String ballTier = com.loadoutlab.data.NavalCombat.canFire(tier, ammo)
				? ammo : com.loadoutlab.data.NavalCombat.bestSharedBall(List.of(tier));
			com.loadoutlab.data.NavalCombat.Ball ball =
				com.loadoutlab.data.NavalCombat.ball(ballTier);
			boolean playerFired = i == 0 && "cannon".equals(station);
			Map<String, Object> c = new java.util.LinkedHashMap<>();
			c.put("tier", tier);
			c.put("itemId", cannon.itemId);
			c.put("ball", ballTier);
			c.put("firedBy", playerFired ? "player" : "crew");
			String blocked = null;
			if (playerFired && rangedLevel < cannon.ranged)
			{
				blocked = "Needs " + cannon.ranged + " Ranged to operate";
			}
			if (!playerFired)
			{
				anyCrew = true;
				if (priv < cannon.privateering)
				{
					blocked = "Crew needs Privateering " + cannon.privateering;
				}
			}
			if (blocked != null)
			{
				c.put("blocked", blocked);
				c.put("maxHit", 0);
				c.put("dps", 0.0);
				cannonNodes.add(c);
				continue;
			}
			int maxHit;
			if (playerFired)
			{
				maxHit = com.loadoutlab.engine.ShipCannon.playerMaxHit(
					rangedLevel, cannon.strength, ball.strength, wornStr, false);
			}
			else
			{
				int base = com.loadoutlab.engine.ShipCannon.playerMaxHit(
					sailingLevel, cannon.strength, ball.strength, 0, false);
				maxHit = com.loadoutlab.engine.ShipCannon.crewMaxHit(base, priv);
			}
			double chance = com.loadoutlab.engine.ShipCannon.hitChance(
				playerFired ? rangedLevel : sailingLevel,
				com.loadoutlab.engine.ShipCannon.equipmentAccuracy(
					cannon.heavyAccuracy, ball.accuracy, playerFired ? wornAcc : 0),
				mob.getDefence(), mob.getDefensive().get("heavy"));
			double dps = com.loadoutlab.engine.ShipCannon.dps(maxHit, chance);
			c.put("maxHit", maxHit);
			c.put("dps", dps);
			total += dps;
			cannonNodes.add(c);
		}
		node.put("cannons", cannonNodes);
		node.put("dps", total);
		node.put("estimated", anyCrew && com.loadoutlab.data.NavalCombat.crewFormulaStale());
		return node;
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
