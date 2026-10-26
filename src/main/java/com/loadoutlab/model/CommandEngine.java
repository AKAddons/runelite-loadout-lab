package com.loadoutlab.model;

import com.google.gson.JsonObject;
import com.loadoutlab.data.*;
import com.loadoutlab.engine.*;
import com.loadoutlab.optimizer.OptimizerService;
import java.util.*;
import java.util.stream.Collectors;
import com.loadoutlab.command.*;
import java.util.function.*;
import javax.swing.SwingUtilities;
import lombok.Setter;
import com.loadoutlab.data.MonsterGroups.MonsterGroup;
import com.loadoutlab.data.NavalCombat.*;
import com.loadoutlab.optimizer.OptimizerService.*;
import com.loadoutlab.data.TripSupplies.Option;
import static com.loadoutlab.data.MonsterSpellbooks.*;
import static com.loadoutlab.data.NavalCombat.*;
import static com.loadoutlab.data.SpellRunes.*;
import static com.loadoutlab.data.TripSupplies.*;
import static com.loadoutlab.engine.ShipCannon.*;
import static com.loadoutlab.model.RenderModel.*;

/**
 * Executes contract commands (docs/COMPANION_CONTRACT.md) against the
 * core-owned {@link PageState} and publishes the resulting page. This
 * is the layer BOTH UIs share: the Companion sends `command` messages
 * here; the bare text UI will call {@link #execute} directly. During
 * the transition the old Swing panel still drives computes its own
 * way - its results flow through {@link #onResults} too, so the
 * Companion mirrors whichever surface the user drives.
 */
public class CommandEngine {
	/** The plugin's compute path, in ComputeHook argument order. */
	public interface Compute {
		void compute(MonsterStats monster, ComputeParams p, Runnable onDone);
	}

	private final LoadoutData data;
	private final PageState state;
	private final Compute compute;
	private final CompanionLink link;
	/** Live global-store counts (excluded/simmed/stored) - supplied by
	 * the plugin, read at page-build time so chips are never stale. */
	@Setter
	private volatile Supplier<Map<String, Object>> counts;

	/** Global-store toggles and the per-mob writes, implemented by the
	 * plugin as direct store calls: the ENGINE's history is the one live
	 * stack since the merge-back (field report 2026-08-20: the plugin's
	 * own stack was an orphan nothing could undo, so Back skipped every
	 * exclude/sim). */
	public interface StoreOps {
		boolean toggleExclusion(int itemId);

		boolean toggleSim(int itemId);

		void toggleAlwaysFilter(int itemId);

		/** Write a wrench-panel supply default (the classic grey chip). */
		void setSupplyDefault(String category, String choice);

		/** The slayer-task bracelet setting, by TaskBracelet name. */
		void setTaskBracelet(String choice);

		/** Highlight these ids in the open bank; null clears. */
		void showInBank(Set<Integer> itemIds);

		/** Filter the open bank to these ids in this layout; nulls clear. */
		void filterBank(Set<Integer> itemIds, int[] layout);

		void setPinnedSpec(int monsterId, int itemId);

		/** Mob-scoped exclusion/sim; the engine records the step. */
		void excludeForMob(int monsterId, String scope, int itemId);

		void simForMob(int monsterId, int itemId);

		/** The mob's scoped lists for the card trio menus:
		 * [{id, name, scope?}] built plugin-side with labels. */
		List<Map<String, Object>> mobExclusions(int monsterId);

		List<Map<String, Object>> mobSims(int monsterId);

		List<Map<String, Object>> mobFilters(int monsterId);

		void addMobFilter(int monsterId, int itemId);

		boolean skipDegradable(int monsterId);

		/** The per-mob profile store, spoken to directly (the plugin wired
		 * eleven one-line delegations here before 2026-09-09). */
		MobProfiles mobs();
	}

	/** What MonsterProfileStore implements for the engine. */
	public interface MobProfiles {
		String pinnedSpell(int monsterId);

		void setPinnedSpell(int monsterId, String spellName);

		int pinnedSpec(int monsterId);

		String note(int monsterId);

		void setNote(int monsterId, String note);

		void removeMobExclusion(int monsterId, String scope, int itemId);

		void removeMobSim(int monsterId, int itemId);

		void removeMobFilter(int monsterId, String scope, int itemId);

		Map<String, String> supplyOverrides(int profileId);

		void setSupplyOverride(int profileId, String category, String choice);

		void setSkipDegradable(int monsterId, boolean skip);

		Map<String, Map<GearSlot, Integer>> allPins(int monsterId);

		/** Scoped pin writes - the pin list and the sim-drops-pin undo put
		 * a pin back in the scope it came from. */
		void pin(int monsterId, String scope, GearSlot slot, int itemId);

		void unpin(int monsterId, String scope, GearSlot slot);
	}

	@Setter
	private volatile StoreOps storeOps;

	/** Opens the shown setup in the official wiki calculator - the
	 * plugin wires the network side (shortlink POST + browser). Unset,
	 * the command refuses and the button does nothing. */
	public interface WikiCalcOpener {
		void open(MonsterStats mob, DpsResult shown, int dartId,
			String assumes, boolean onSlayerTask, boolean inWilderness);
	}

	@Setter
	private volatile WikiCalcOpener wikiCalcOpener;

	/** The plugin's roster compute path (ComputeHook.computeRoster). */
	public interface RosterCompute {
		void computeRoster(List<MonsterStats> mobs, ComputeParams p, Runnable onDone);
	}

	/** Wrench-panel supply defaults (category -> config enum name),
	 * re-read per publish so config changes land immediately. */
	@Setter
	private volatile Supplier<Map<String, String>> supplyDefaults;

	/** The player's LIVE spellbook name ("standard"/"ancient"/"lunar"/
	 * "arceuus"), pushed by the plugin's varbit subscriber - the book
	 * plate turns red when an answer assumes a different book. */
	private volatile String liveSpellbook = "";

	public void setLiveSpellbook(String liveSpellbook) {
		String next = liveSpellbook == null ? "" : liveSpellbook;
		boolean changed = !next.equals(this.liveSpellbook);
		this.liveSpellbook = next;
		if (changed) { republish(); }
	}

	private static final String[] SIM_ARGS = {"attack", "strength", "defence", "ranged", "magic", "prayer", "hitpoints"};

	/** Your live levels, for the stat sim's boxes and the report (myLevels). */
	public void setLevels(PlayerLevels real) {
		String next = real == null ? "" : real.getAttack() + "/" + real.getStrength() + "/" + real.getDefence() + "/"
			+ real.getRanged() + "/" + real.getMagic() + "/" + real.getPrayer() + "/" + real.getHitpoints();
		boolean changed = !next.equals(String.valueOf(state.paramsNode().get("myLevels")));
		state.setMyLevels(next);
		if (changed) { republish(); }
	}

	private boolean applyStats(int[] levels, boolean maxCape) {
		for (int i = 0; i < levels.length; i++) { state.setParam(PageState.SIM_KEYS[i], levels[i]); }
		state.setParam("simMaxCape", maxCape);
		recompute();
		return true;
	}

	/** "99/-/-/-/-/-/- + max cape", "mine" when nothing is simmed. */
	private static String statsLabel(int[] levels, boolean maxCape) {
		var sb = new StringBuilder();
		boolean any = false;
		for (int i = 0; i < levels.length; i++) {
			any |= levels[i] > 0;
			sb.append(i > 0 ? "/" : "").append(levels[i] > 0 ? String.valueOf(levels[i]) : "-");
		}
		return any || maxCape ? sb + (maxCape ? " + max cape" : "") : "mine";
	}

	/** spellName -> its book, built once from the corpus - every corpus
	 * name is distinct, so its keys are the page's spell list too. */
	private final Map<String, Object> spellbooks = new LinkedHashMap<>();

	/** Combo-rune preference (config-fed) + casting-kit ownership. */


	/** Storage name for an item needing a fetch trip (source dots). */
	@Setter
	private volatile IntFunction<String> itemLocation;

	/** Canonical ownership (dose variants collapse) for Detect scans. */
	@Setter
	private volatile IntPredicate ownedCheck;

	/** The supply unlock varbits that are set - the ring of shadows' tablets
	 * (ISS-01M5RMNK62507Q42KKCJ9A7AEE); the plugin reads them on the client thread. */
	@Setter
	private volatile IntPredicate unlocks = v -> false;

	/** Classic seedAssumptionDefaults: config-driven params for a NEW
	 * selection (spec chip, thralls when reachable+book owned, Death
	 * Charge at Magic 80) - supplied by the plugin, which owns the
	 * config and the live levels. */
	@Setter
	private volatile Supplier<Map<String, Object>> assumptionSeeder;

	/** Classic Detect: the best owned antifire tier (0/1/2) for a
	 * fire-breathing selection - supplied by the plugin (owned scan). */
	@Setter
	private volatile Function<List<MonsterStats>, Integer> antifireResolver;

	@Setter
	private volatile RosterCompute rosterCompute;
	private volatile List<MonsterGroup> groups;
	/** Engine-owned history for seam-driven actions. Separate from the
	 * old panel's stack during the transition (its commands re-sync
	 * Swing controls the engine does not know about); they merge when
	 * the panel leaves. */
	private final CommandHistory history =
		new CommandHistory();
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
	@lombok.AllArgsConstructor
	private static final class Snapshot {
		final Map<String, Object> page;
		final List<MonsterStats> mobs;
		final List<Map<CombatStyle, StyleResult>> perMob;
	}

	private final ArrayDeque<Snapshot> undoPages =
		new ArrayDeque<>();
	private final ArrayDeque<Snapshot> redoPages =
		new ArrayDeque<>();
	private static final int SNAPSHOT_CAP = 32;
	private boolean restoringSnapshot;

	/** A page the snapshot restore may publish: present, and NOT a
	 * pending (mid-compute) page - restoring one would freeze the
	 * loader with no compute to finish it. */
	private static boolean restorable(Map<String, Object> page) {
		if (page == null || page.isEmpty()) { return false; }
		Object entries = page.get("entries");
		return !(entries instanceof List) || ((List<?>) entries).stream()
			.noneMatch(entry -> entry instanceof Map && Boolean.TRUE.equals(((Map<?, ?>) entry).get("pending")));
	}

	/** history.execute plus the page snapshot - every recorded command
	 * routes through here. */
	/** The common shape of an undoable step: apply and revert each end in
	 * a recompute (ISS-01M5M31ZG7ZC5K1C3NXCBYC3S2, the slim). */
	private boolean recorded(String description, Runnable apply, Runnable revert) {
		return record(Commands.of(description, () -> { apply.run(); recompute(); return true; },
			() -> { revert.run(); recompute(); return true; }));
	}

	private boolean record(Command cmd) {
		Map<String, Object> before = link.lastPage();
		List<MonsterStats> beforeMobs = lastMobs;
		List<Map<CombatStyle, StyleResult>> beforePerMob = lastPerMob;
		boolean ok = history.execute(cmd);
		if (ok) {
			undoPages.push(new Snapshot(restorable(before) ? before : null,
				beforeMobs, beforePerMob));
			while (undoPages.size() > SNAPSHOT_CAP) { undoPages.removeLast(); }
			redoPages.clear();
		}
		return ok;
	}

	public CommandEngine(LoadoutData data, PageState state, Compute compute, CompanionLink link) {
		this.data = data;
		this.state = state;
		this.compute = compute;
		this.link = link;
		data.getSpells().forEach(spell -> spellbooks.put(spell.getName(), spell.getSpellbook()));
	}

	private static final Map<String, String> PARAM_LABELS = new LinkedHashMap<>();
	/** The per-mob list commands (the card trio's adds, the mob line's
	 * removes): each one's undo is the command that puts it back, so the
	 * six share one handler that replays whichever side history asks for.
	 * The pairs, and the verb each one's undo label opens with, ride
	 * param_labels.json beside the param labels. */
	private static final Map<String, String> INVERSE = new HashMap<>();
	private static final Map<String, String> VERB = new HashMap<>();

	static {
		JsonObject labels = JsonResources.objectOrThrow("param_labels.json");
		JsonResources.stringMap(labels, "labels", PARAM_LABELS);
		JsonResources.stringMap(labels, "inverse", INVERSE);
		JsonResources.stringMap(labels, "verbs", VERB);
	}

	/** Handle one contract command; returns false for an unknown or
	 * malformed command (refused loudly at the seam, never guessed). */
	@SuppressWarnings("unchecked")
	public boolean execute(String name, Map<String, Object> args) {
		StoreOps ops = storeOps;
		switch (name == null ? "" : name) {
			case "select": {
				Integer exactId = intArg(args, "id");
				if (exactId != null) {
					int wanted = exactId;
					// Versions can SHARE an npc id (Duke's Awakened and
					// Post-quest are both 12191, Awakened first in corpus
					// order - field report 2026-08-21: the dropdown listed
					// Post-quest, the click selected Awakened). The
					// dropdown sends its row's version; a bare id (link-
					// ins) takes the best-ranked id match via search.
					String version = strArg(args, "version", null);
					MonsterStats first = monster(m -> m.getId() == wanted);
					if (first == null) { return false; }
					MonsterStats exact = version == null ? null
						: monster(m -> m.getId() == wanted && version.equalsIgnoreCase(m.getVersion()));
					// No version, or a version this corpus no longer
					// spells the same way: take the row the SEARCH ranks
					// first. Falling back to corpus order handed over the
					// Awakened boss - strictly worse than sending no
					// version at all (adversarial pass 2026-08-22).
					return selectResolved(exact != null ? exact : data.searchMonsters(first.getName(), 10).stream()
						.filter(ranked -> ranked.getId() == wanted).findFirst().orElse(first), null);
				}
				String query = strArg(args, "query", null);
				if (query == null || query.isBlank()) { return false; }
				// Groups first on an exact-ish name hit, else single mobs -
				// mirrors the classic search's dropdown priorities loosely;
				// a group named like the query opens the roster.
				MonsterGroup group = groupFor(query);
				MonsterStats next = null;
				if (group == null) {
					List<MonsterStats> matches = data.searchMonsters(query, 1);
					if (matches.isEmpty()) { return false; }
					next = matches.get(0);
					if (next.getId() == (state.mob() == null ? -1 : state.mob().getId())) {
						recompute();
						return true;
					}
				}
				return selectResolved(next, group);
			}
			case "add-mob": {
				String query = strArg(args, "query", null);
				if (query == null || query.isBlank() || !state.hasSelection()) { return false; }
				List<MonsterStats> matches = data.searchMonsters(query, 1);
				if (matches.isEmpty()) { return false; }
				MonsterStats added = matches.get(0);
				List<MonsterStats> current = selectedMobs();
				if (current.stream().anyMatch(m -> m.getId() == added.getId())) { return false; }
				List<MonsterStats> combined = new ArrayList<>(current);
				combined.add(added);
				Object[] prevSel = state.selectionSnapshot();
				return recorded("Add " + added.getName() + " to result",
					() -> state.selectRoster(combined, "Custom roster", false),
					() -> state.restoreSelection(prevSel));
			}
			case "cancel": {
				// Back to the start state mid-compute (Andrew 2026-09-04: "it is
				// hard to reach the search box for a raid search"); a result
				// that lands afterwards is dropped.
				cancelled = true;
				state.select(null);
				clearResults();
				link.publishStatus(false);
				publishIdle();
				return true;
			}
			case "move-mob": {
				// Reorder a roster row (Andrew 2026-09-02): presentation
				// only - the shared set is the same kit - but the lens must
				// follow the row it was on, and undo puts both back.
				Integer index = intArg(args, "index");
				Object delta = arg(args, "delta");
				List<MonsterStats> current = state.rosterMobs();
				if (index == null || !(delta instanceof Number) || current == null) { return false; }
				int at = index;
				int to = at + ((Number) delta).intValue();
				if (at < 0 || at >= current.size() || to < 0 || to >= current.size() || to == at) { return false; }
				List<MonsterStats> reordered = new ArrayList<>(current);
				reordered.add(to, reordered.remove(at));
				Object[] prevSel = state.selectionSnapshot();
				int lens = intParam("lensIndex");
				int movedLens = lens == at ? to : lens == to ? at : lens;
				boolean raid = flag("raidSelection");
				return record(Commands.of("Move " + current.get(at).getName() + (to < at ? " up" : " down"), () -> {
					state.selectRoster(reordered, (String) prevSel[2], raid);
					state.setParam("lensIndex", movedLens);
					republishReordered();
					return true;
				}, () -> {
					state.restoreSelection(prevSel);
					state.setParam("lensIndex", lens);
					republishReordered();
					return true;
				}));
			}
			case "remove-mob": {
				Integer index = intArg(args, "index");
				// A single-mob view shows its own row with an X (field ask
				// 2026-08-22) - there is no roster behind it, so the lone
				// selection stands in as a one-mob list.
				List<MonsterStats> current = selectedMobs();
				if (index == null || current == null) { return false; }
				int at = index;
				if (at < 0 || at >= current.size()) { return false; }
				MonsterStats removed = current.get(at);
				List<MonsterStats> remaining = new ArrayList<>(current);
				remaining.remove(at);
				Object[] prevSel = state.selectionSnapshot();
				return record(Commands.of("Remove " + removed.getName() + " from result", () -> {
					if (remaining.isEmpty()) {
						// Removing the last mob returns to the start state, exactly as
						// if nothing were searched yet (field ask 2026-08-22).
						state.select(null);
						clearResults();
						publishIdle();
						return true;
					}
					if (remaining.size() == 1) {
						state.select(remaining.get(0));
					} else {
						state.selectRoster(remaining, "Custom roster", false);
					}
					recompute();
					return true;
				}, () -> {
					state.restoreSelection(prevSel);
					recompute();
					return true;
				}));
			}
			case "sim-stats": {
				// The stat sim (ISS-01M5QY13K6YHTYDW1CCYD30CSY, Andrew 2026-10-24:
				// "sim as any stat, defaulted to your current ones. lower or
				// higher should work too"): the seven levels (0 = your own) and
				// the max cape tick as ONE action - one recompute, one undo step.
				int[] next = new int[PageState.SIM_KEYS.length];
				int[] prev = new int[next.length];
				for (int i = 0; i < next.length; i++) {
					Integer level = intArg(args, SIM_ARGS[i]);
					next[i] = level == null ? 0 : Math.max(0, Math.min(99, level));
					prev[i] = (int) num(state.paramsNode().get(PageState.SIM_KEYS[i]));
				}
				boolean nextCape = Boolean.TRUE.equals(args.get("maxCape"));
				boolean prevCape = flag("simMaxCape");
				if (Arrays.equals(prev, next) && prevCape == nextCape) { return true; }
				return record(Commands.of("Stats " + statsLabel(next, nextCape),
					() -> applyStats(next, nextCape), () -> applyStats(prev, prevCape)));
			}
			case "set-param": {
				String key = strArg(args, "param", null);
				if (key == null) { return false; }
				Object next = args.get("value");
				Object prev = state.paramsNode().get(key);
				boolean unchanged = Objects.equals(prev, next);
				if (unchanged && "riskBudgetGp".equals(key)) {
					// 75k is BOTH a legal cap and the engine's uncapped
					// sentinel, so the gp number alone cannot tell "set a
					// 75k cap" from "already uncapped" - typing 75k was
					// swallowed while the field kept showing it (found by
					// the pre-release adversarial pass 2026-08-22).
					boolean capped = flag("riskCapped");
					unchanged = capped == (next != null);
				}
				if (unchanged) { return true; }
				if ("lensIndex".equals(key) || "ladderSlot".equals(key) || "ladderPage".equals(key)) {
					// Switching which roster row is expanded is a VIEW
					// gesture, not an action - Back must never land on it
					// (field ask 2026-08-20). Opening a slot's ladder strip,
					// or turning its page, is the same kind of gesture; a
					// fresh slot opens on its first page.
					if ("ladderSlot".equals(key)) { applyParam("ladderPage", 0); }
					return applyParam(key, next);
				}
				String label = PARAM_LABELS.getOrDefault(key, key);
				return record(Commands.of(Boolean.TRUE.equals(next) ? label + " on"
					: Boolean.FALSE.equals(next) ? label + " off" : label + " " + next,
					() -> applyParam(key, next), () -> applyParam(key, prev)));
			}
			case "ner-open": {
				// A link out, not an action - never recorded in history.
				Integer itemId = intArg(args, "itemId");
				if (itemId == null || !nerAvailable.getAsBoolean()) { return false; }
				nerOpen.accept(itemId);
				return true;
			}
			case "path-to": {
				// A link out, like ner-open - never recorded in history.
				MonsterStats mob = shownMob();
				if (mob == null || !pathable(mob)) { return false; }
				pathOpen.accept(mob.getName());
				return true;
			}
			case "toggle-degradable": {
				MonsterStats mob = shownMob();
				if (ops == null || mob == null) { return false; }
				ops.mobs().setSkipDegradable(mob.getId(), !ops.skipDegradable(mob.getId()));
				recompute();
				return true;
			}
			case "set-supply-default": {
				String category = strArg(args, "category", null);
				String choice = strArg(args, "choice", null);
				if (ops == null || category == null || choice == null) { return false; }
				ops.setSupplyDefault(category, choice);
				recompute();
				return true;
			}
			case "wiki-calc": {
				WikiCalcOpener opener = wikiCalcOpener;
				List<MonsterStats> mobs = lastMobs;
				List<Map<CombatStyle, StyleResult>> perMob = lastPerMob;
				if (opener == null || mobs == null || perMob == null) { return false; }
				MonsterStats mob = shownMob();
				int at = -1;
				for (int i = 0; mob != null && i < mobs.size(); i++) {
					if (mobs.get(i).label().equals(mob.label())) {
						at = i;
						break;
					}
				}
				if (at < 0 || perMob.size() <= at) { return false; }
				boolean bis = args != null && Boolean.TRUE.equals(args.get("bis"))
					|| flag("viewingBis");
				String styleName = strArg(args, "style", null);
				if (styleName == null) {
					// No style given (the footer button): the VIEWED tab, or
					// on auto the strongest shown style - the card's own
					// auto-tab rule.
					Object tab = state.paramsNode().get("selectedTab");
					styleName = tab instanceof String && !((String) tab).isEmpty()
						? (String) tab : null;
				}
				StyleResult result = null;
				if (styleName != null) {
					try {
						result = perMob.get(at).get(CombatStyle.valueOf(
							styleName.toUpperCase(Locale.ROOT)));
					} catch (IllegalArgumentException ex) { return false; }
				} else {
					double best = -1;
					for (StyleResult candidate
						: perMob.get(at).values()) {
						DpsResult side = shown(candidate, bis);
						if (side != null && side.getDps() > best) {
							best = side.getDps();
							result = candidate;
						}
					}
				}
				DpsResult shown = shown(result, bis);
				if (shown == null) { return false; }
				// The blowpipe's loaded dart rides the attackType string
				// (same parse as the RenderModel fact).
				int dartId = -1;
				String attackType = shown.getAttackType();
				if (attackType != null && attackType.contains(" - ")) {
					Integer parsed = BlowpipeDarts.baseIdForTierName(
						attackType.substring(attackType.indexOf(" - ") + 3));
					dartId = parsed == null ? -1 : parsed;
				}
				// A link-out is not an action - never recorded in history.
				// The card's raid level rides the exported mob (field dispute
				// 2026-09-02: the base row opened the calculator at 0 while
				// the card sat at 300 - the engine was byte-exact all along).
				opener.open(atInvocation(mob), shown, dartId,
					bis ? result.gameBoostLabel : result.boostLabel,
					membersFlag("onTask"),
					flag("inWilderness"));
				return true;
			}
			case "detect-antifire": {
				// The dragonfire plate's Detect best (Andrew 2026-10-18): the
				// selection-time detection, run again on demand.
				List<MonsterStats> selected = selectedMobs();
				if (antifireResolver == null || selected == null) { return false; }
				seedAntifire(selected);
				recompute();
				return true;
			}
			case "detect-sunfire": {
				// The sunfire plate's Detect best: the selection-time seed again.
				seedSunfire();
				recompute();
				return true;
			}
			case "set-task-bracelet": {
				if (ops == null) { return false; }
				ops.setTaskBracelet(String.valueOf(arg(args, "choice")));
				return true;
			}
			case "toggle-always-filter": {
				Integer itemId = intArg(args, "itemId");
				if (ops == null || itemId == null) { return false; }
				ops.toggleAlwaysFilter(itemId);
				republish();
				return true;
			}
			case "toggle-exclusion":
			case "toggle-sim": {
				Integer itemId = intArg(args, "itemId");
				if (ops == null || itemId == null) { return false; }
				int id = itemId;
				boolean exclusion = "toggle-exclusion".equals(name);
				String what = strArg(args, "label", "item");
				// Self-inverse: a toggle undoes itself. Recorded HERE (the
				// one live stack) - the classic recorded into the plugin's
				// stack, orphaned since the merge-back, so Back skipped
				// every exclude/sim (field report 2026-08-20).
				// Pins dropped when this toggle turned a sim off (by mob) - put back by
				// the next toggle (the undo).
				Map<Integer, List<Object[]>> dropped = new HashMap<>();
				BooleanSupplier toggle = () -> {
					if (!(exclusion ? ops.toggleExclusion(id) : ops.toggleSim(id))) { return false; }
					MonsterStats mob = shownMob();
					if (!exclusion && !dropped.isEmpty()) {
						dropped.forEach((mobId, pins) -> restorePins(ops, mobId, id, pins));
						dropped.clear();
					} else if (!exclusion && mob != null) {
						dropped.put(mob.getId(), dropPinsIfNotYours(ops, mob.getId(), id));
					}
					refreshAfterStoreToggle();
					return true;
				};
				return record(Commands.of((exclusion ? "Exclude " : "Sim ") + what, toggle, toggle));
			}
			case "set-prayer-pick":
			case "set-boost-pick": {
				Object style = arg(args, "style");
				if (!(style instanceof String)) { return false; }
				CombatStyle combatStyle;
				try {
					combatStyle = CombatStyle.valueOf(((String) style).toUpperCase());
				} catch (IllegalArgumentException ex) { return false; }
				boolean prayer = "set-prayer-pick".equals(name);
				String next = strArg(args, "value", null);
				String prev = (String) ((Map<String, Object>) state.paramsNode()
					.get(prayer ? "prayerPicks" : "boostPicks")).get(combatStyle.name().toLowerCase());
				if (Objects.equals(prev, next)) { return true; }
				String kind = prayer ? "Prayer" : "Boost";
				return recorded(kind + " " + (next == null ? "detect" : next.toLowerCase())
					+ " - " + combatStyle.name().toLowerCase(),
					() -> state.setPick(prayer, combatStyle, next),
					() -> state.setPick(prayer, combatStyle, prev));
			}
			case "ladder-try": {
				// A ladder step picked (ISS-01M5GNMHHCD8M56JD4GAZBFV7Y, Andrew
				// 2026-10-21: "Sim + pin for this mob"): sim it here when it is
				// not yours yet AND pin it into its slot - one undo step that
				// takes the sim back and restores the slot's earlier pin.
				MonsterStats mob = shownMob();
				String slot = strArg(args, "slot", null);
				Integer itemId = intArg(args, "itemId");
				if (ops == null || mob == null || slot == null || itemId == null) { return false; }
				int id = itemId;
				int mobId = mob.getId();
				if ("spec".equals(slot)) {
					// A spec swap tried (ISS-01M5HCS725N7XVVBC7CMK6Y43A): it
					// becomes this mob's pinned spec, the earlier one back on undo.
					int previousSpec = ops.mobs().pinnedSpec(mobId);
					return tryStep(ops, mob, id, "spec " + strArg(args, "label", "spec weapon"),
						() -> ops.setPinnedSpec(mobId, id), () -> ops.setPinnedSpec(mobId, previousSpec));
				}
				GearSlot gearSlot;
				try {
					gearSlot = GearSlot.valueOf(slot.toUpperCase());
				} catch (IllegalArgumentException ex) { return false; }
				Integer previousPin = ops.mobs().allPins(mobId)
					.getOrDefault("ALL", Map.of()).get(gearSlot);
				return tryStep(ops, mob, id, strArg(args, "label", "item"),
					() -> ops.mobs().pin(mobId, "ALL", gearSlot, id), () -> {
					if (previousPin != null) {
						ops.mobs().pin(mobId, "ALL", gearSlot, previousPin);
					} else {
						ops.mobs().unpin(mobId, "ALL", gearSlot);
					}
				});
			}
			case "clear-mob-list": {
				// Andrew, 2026-09-18: "for each of the exclude/sim/item filters can
				// you have a reset all in list option". One recompute, one undo step.
				MonsterStats mob = shownMob();
				String kind = strArg(args, "kind", null);
				if (ops == null || mob == null || kind == null) { return false; }
				int mobId = mob.getId();
				if ("mobPins".equals(kind)) { return clearPins(ops, mob); }
				boolean sims = "mobSims".equals(kind);
				boolean filters = "mobFilters".equals(kind);
				List<Map<String, Object>> entries = new ArrayList<>(
					sims ? ops.mobSims(mobId) : filters ? ops.mobFilters(mobId) : ops.mobExclusions(mobId));
				if (entries.isEmpty()) { return false; }
				// Each entry replays the mob line's own remove (sims take their
				// pins along when the item is not yours), the undo its inverse.
				String remove = sims ? "remove-mob-sim" : filters ? "remove-mob-filter" : "remove-mob-exclusion";
				Map<Integer, List<Object[]>> droppedPins = new HashMap<>();
				Consumer<String> run = cmd -> {
					eachEntry(entries, (id, scope) -> mobListStep(ops, mobId, cmd, id, scope, droppedPins));
					refreshAfter(cmd);
				};
				return record(Commands.of("Clear " + (sims ? "sims" : filters ? "filters" : "exclusions")
					+ " (" + entries.size() + ") - " + mob.getName(),
					() -> { run.accept(remove); return true; }, () -> { run.accept(INVERSE.get(remove)); return true; }));
			}
			case "sim-for-group": {
				// A group step (Andrew 2026-10-22: "if you upgrade your melee
				// chest in a group you should replace the melee chest for the
				// whole group but not pin a melee chest for mob that has a range
				// swap"): sim it on every member - each member's own answer
				// decides whether it wears it - one undo step, no pin.
				List<MonsterStats> group = state.rosterMobs();
				Integer itemId = intArg(args, "itemId");
				if (ops == null || group == null || group.isEmpty() || itemId == null) { return false; }
				int id = itemId;
				String what = strArg(args, "label", "item");
				List<Integer> added = group.stream().map(MonsterStats::getId).distinct()
					.filter(mobId -> !hasId(ops.mobSims(mobId), id)).collect(Collectors.toList());
				if (added.isEmpty()) { return false; }
				return recorded("Sim " + what + " - group", () -> {
					for (int mobId : added) { ops.simForMob(mobId, id); }
				}, () -> {
					for (int mobId : added) { ops.mobs().removeMobSim(mobId, id); }
				});
			}
			case "pin":
			case "unpin":
			case "pin-for-mob":
			case "remove-mob-pin": {
				// ONE recorded pin path (the slim, 2026-10-23): the card
				// menu's Pin/Unpin <slot> (ALL scope; Unpin clears the slot
				// in EVERY scope, so a style pin older versions wrote cannot
				// outlive it) and the mob line's pin list - pin a searched
				// item into its own slot, remove one pin in its scope
				// (ISS-01M5HJ84ADBHWB2302G66WW2G4) - each one undo step that
				// puts back whatever the slot held. The monster is the one
				// the card shows: a roster view has no state.mob(), and the
				// click used to do nothing there (ISS-01M5HANBV7VC08XCH850AW7Q39,
				// Andrew 2026-10-22: Unpin weapon on Araxxor - "Nothing
				// changed at all").
				MonsterStats mob = shownMob();
				Integer itemId = intArg(args, "itemId");
				boolean adding = "pin".equals(name) || "pin-for-mob".equals(name);
				if (ops == null || mob == null || adding && itemId == null) { return false; }
				int id = itemId == null ? 0 : itemId;
				GearSlot gearSlot;
				if ("pin-for-mob".equals(name)) {
					GearItem item = data.getGear(id);
					if (item == null || item.getSlot() == null) { return false; }
					gearSlot = item.getSlot();
				} else {
					try {
						gearSlot = GearSlot.valueOf(String.valueOf(arg(args, "slot")).toUpperCase());
					} catch (IllegalArgumentException ex) { return false; }
				}
				boolean everyScope = "unpin".equals(name);
				String scope = "remove-mob-pin".equals(name) ? strArg(args, "scope", "ALL") : "ALL";
				String what = strArg(args, "label", Names.lower(gearSlot.name()));
				int mobId = mob.getId();
				String mobName = mob.getName();
				// What the slot held, per scope - every scope for Unpin, the
				// one scope otherwise - so the undo puts each pin back.
				Map<String, Integer> previous = new LinkedHashMap<>();
				ops.mobs().allPins(mobId).forEach((s, slots) -> {
					if (slots.containsKey(gearSlot) && (everyScope || s.equals(scope))) {
						previous.put(s, slots.get(gearSlot));
					}
				});
				if (!adding && previous.isEmpty()) { return false; }
				return recorded((adding ? "Pin " : "Unpin ") + what + " - " + mobName, () -> {
					if (adding) {
						ops.mobs().pin(mobId, scope, gearSlot, id);
					} else {
						previous.keySet().forEach(s -> ops.mobs().unpin(mobId, s, gearSlot));
					}
				}, () -> {
					if (previous.isEmpty()) { ops.mobs().unpin(mobId, scope, gearSlot); }
					previous.forEach((s, prior) -> ops.mobs().pin(mobId, s, gearSlot, prior));
				});
			}
			case "exclude-for-mob":
			case "sim-for-mob":
			case "remove-mob-exclusion":
			case "remove-mob-sim":
			case "add-mob-filter":
			case "remove-mob-filter": {
				// The card trio's adds and the mob line's removes. Rosters
				// have no state.mob() - the lensed row is the mob (the
				// set-note lesson, third member; field report 2026-08-20:
				// the card trio's adds no-oped on rosters). Each command's
				// undo IS its INVERSE command, replayed through the one
				// switch below - so undoing "Sim here" runs remove-mob-sim,
				// which also drops the item's pins when it is not yours
				// (Andrew's 2026-10-22 rule), and redo puts them back with
				// the sim.
				MonsterStats mob = shownMob();
				Integer itemId = intArg(args, "itemId");
				if (ops == null || mob == null || itemId == null) { return false; }
				int id = itemId;
				String scopeKey = strArg(args, "scope", "ALL");
				int mobId = mob.getId();
				Map<Integer, List<Object[]>> droppedPins = new HashMap<>();
				Consumer<String> run = cmd -> {
					mobListStep(ops, mobId, cmd, id, scopeKey, droppedPins);
					refreshAfter(cmd);
				};
				return record(Commands.of(VERB.get(name) + strArg(args, "label", "item") + " - " + mob.getName(),
					() -> { run.accept(name); return true; }, () -> { run.accept(INVERSE.get(name)); return true; }));
			}
			case "set-supply-override": {
				String category = strArg(args, "category", null);
				MonsterStats lensed = lensedMob();
				if (ops == null || lensed == null || category == null) { return false; }
				ops.mobs().setSupplyOverride(lensed.profileId(), category, strArg(args, "choice", "DETECT"));
				// A cure choice can camp Lunar (Cure Me): the lock rides the compute.
				if (TripSupplies.ANTIVENOM.equals(category) || TripSupplies.ANTIPOISON.equals(category)) {
					recompute();
				} else {
					republish();
				}
				return true;
			}
			case "set-pinned-spell":
			case "set-pinned-spec":
			case "set-note": {
				// The LENS mob on rosters (field report 2026-08-15: notes
				// never saved on a trip - state.mob() is null there).
				MonsterStats mob = shownMob();
				if (ops == null || mob == null) { return false; }
				if ("set-pinned-spell".equals(name)) {
					ops.mobs().setPinnedSpell(mob.getId(), strArg(args, "name", ""));
					recompute();
				} else if ("set-pinned-spec".equals(name)) {
					Object itemId = arg(args, "itemId");
					ops.setPinnedSpec(mob.getId(),
						itemId instanceof Number ? ((Number) itemId).intValue() : 0);
					recompute();
				} else {
					ops.mobs().setNote(mob.getId(), strArg(args, "text", ""));
					republish();
				}
				return true;
			}
			case "bank-show": {
				if (ops == null) { return false; }
				Object ids = arg(args, "ids");
				if (ids instanceof List) {
					Set<Integer> itemIds = new HashSet<>();
					for (Object id : (List<?>) ids) {
						if (id instanceof Number) { itemIds.add(((Number) id).intValue()); }
					}
					ops.showInBank(itemIds);
				} else {
					ops.showInBank(null);
				}
				return true;
			}
			case "bank-filter": {
				if (ops == null) { return false; }
				Object ids = arg(args, "ids");
				Object layout = arg(args, "layout");
				if (ids instanceof List && layout instanceof List) {
					Set<Integer> itemIds = new LinkedHashSet<>();
					for (Object id : (List<?>) ids) {
						if (id instanceof Number) { itemIds.add(((Number) id).intValue()); }
					}
					List<?> slots = (List<?>) layout;
					int[] positions = new int[slots.size()];
					for (int i = 0; i < slots.size(); i++) {
						positions[i] = slots.get(i) instanceof Number
							? ((Number) slots.get(i)).intValue() : -1;
					}
					ops.filterBank(itemIds, positions);
				} else {
					ops.filterBank(null, null);
				}
				return true;
			}
			case "refresh": {
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
			case "redo": {
				boolean isUndo = "undo".equals(name);
				Map<String, Object> currentPage = link.lastPage();
				var current = new Snapshot(
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
				try {
					ok = isUndo ? history.undo() : history.redo();
				} finally {
					restoringSnapshot = false;
				}
				ArrayDeque<Snapshot> from = isUndo ? undoPages : redoPages;
				ArrayDeque<Snapshot> to = isUndo ? redoPages : undoPages;
				if (!ok) {
					if (hadEntry && !from.isEmpty()) { from.pop(); }
					return false;
				}
				Snapshot restored = from.isEmpty() ? null : from.pop();
				to.push(current);
				if (restored != null && restored.page != null) {
					// The results follow their page - anything that
					// republishes next must read the restored answer.
					lastMobs = restored.mobs;
					lastPerMob = restored.perMob;
					Map<String, Object> snapshot = restored.page;
					// Instant restore: the captured page, but the CURRENT
					// view params (lens/tab hops since are not actions and
					// must survive the jump) and a fresh history node.
					Object entries = snapshot.get("entries");
					if (entries instanceof List) {
						for (Object entryNode : (List<?>) entries) {
							if (entryNode instanceof Map) {
								((Map<String, Object>) entryNode).put("params", state.paramsNode());
							}
						}
					}
					link.publishPage(withHistory(snapshot));
				} else {
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
	private void recompute() { recompute(false); }

	/** Recompute when there is something to compute; otherwise publish the
	 * idle page so the global count pills still move. recompute() returns
	 * early with no mob selected, which left a simmed item counted in the
	 * store but not on the "+N" pill (field report 2026-08-27). */
	private void refreshAfterStoreToggle() {
		if (state.mob() == null && (state.rosterMobs() == null || rosterCompute == null)) {
			if (link != null) {
				link.publishPage(withHistory(
					page(List.of())));
			}
			return;
		}
		recompute();
	}

	/** silent = a BACKGROUND refresh (ledger settles): no status, no
	 * stage clear, no mascot - the fresh answer swaps in when it lands
	 * (field report 2026-08-15: post-boss loot churn made the panel
	 * thrash through clear/mascot/rebuild on every settle). */
	private void recompute(boolean silent) {
		cancelled = false;
		if (restoringSnapshot) { return; }
		MonsterStats mob = state.mob();
		List<MonsterStats> roster = state.rosterMobs();
		RosterCompute rosterPath = rosterCompute;
		if (mob == null && (roster == null || rosterPath == null)) { return; }
		if (!silent) {
			// Publish the PARAMS immediately (field report 2026-08-12: the
			// chips used to appear the moment you searched, so a wrong
			// parameter could be fixed without waiting out the compute) -
			// and BEFORE the status: the loader picks its animation pool
			// by peeking the page when computing starts (field report
			// 2026-09-02: raid moods played the flask, the peek saw the
			// previous page).
			publishPending();
			link.publishStatus(true);
		}
		ComputeParams p = state.computeParams();
		p.thralls = membersFlag("thralls");
		p.raid = flag("raidSelection");
		List<MonsterStats> computed = roster != null ? roster : List.of(mob);
		p.antifirePotion &= computed.stream().anyMatch(DragonfireRules::breathesFire);
		MonsterStats lensed = shownMob();
		String pinnedSpell = storeOps == null || lensed == null ? "" : storeOps.mobs().pinnedSpell(lensed.getId());
		if (cureMe(computed)) {
			p.spellbookLock = "lunar";
		} else if (p.spellbookLock.isEmpty() && (pinnedSpell == null || pinnedSpell.isEmpty())) {
			// The fight book is the autocast lock too (Andrew 2026-10-15: the
			// Sire's magic set cast Dark Demonbane while the plate said Ancients).
			// The player's own lock or pinned spell wins.
			for (MonsterStats m : computed) {
				String book = bookFor(m);
				if (book != null && !book.isEmpty()) {
					p.spellbookLock = book;
					break;
				}
			}
		}
		if (arceuusBlocked(computed)) { p.deathCharge = 0; }
		if (mob != null) {
			compute.compute(atInvocation(mob), p, () -> {
			});
		} else {
			rosterPath.computeRoster(roster.stream().map(this::atInvocation).collect(Collectors.toList()), p, () -> {
			});
		}
	}

	/** ToA mobs scale with the invocation param - the same transform
	 * the classic panel applies before every compute. */
	private MonsterStats atInvocation(MonsterStats mob) {
		return MonsterMechanics.isToaInvocationScaled(mob)
			? MonsterMechanics.atToaInvocation(mob, state.toaInvocation())
			: mob;
	}

	/** The curated groups, loaded once from the corpus. */
	private List<MonsterGroup> groups() {
		List<MonsterGroup> loaded = groups;
		if (loaded == null) {
			loaded = MonsterGroups.load(data);
			groups = loaded;
		}
		return loaded;
	}

	/** A curated group whose name matches the query (contains, both
	 * directions) - resolved like the classic dropdown. */
	private MonsterGroup groupFor(String query) {
		String q = query.toLowerCase();
		if (q.length() < 3) { return null; }
		for (MonsterGroup candidate : groups()) {
			String name = candidate.getName().toLowerCase();
			if (name.contains(q) || q.contains(name)) { return candidate; }
		}
		return null;
	}

	private volatile List<MonsterStats> lastMobs;
	private volatile List<Map<CombatStyle, StyleResult>> lastPerMob;
	/** The one book the last roster computed under ("" = none pinned) and
	 * why (ISS-01M5PB4R0MP149NXWNFSEEHVP8). */
	private volatile String groupBook = "";
	private volatile String groupBookReason = "";
	/** Set by cancel; a fresh compute clears it. Results arriving while set are dropped. */
	private volatile boolean cancelled;

	/** Every single-mob compute lands here (engine-driven or old-panel
	 * driven) - assemble the page, attach the params the state holds,
	 * and publish. */
	public void onResults(MonsterStats mob, Map<CombatStyle, StyleResult> results) {
		onRosterResults(List.of(mob), List.of(results), "", "");
	}

	/** Roster computes: published with the shared params node. */
	/** A roster reorder is presentation only (Andrew 2026-09-03: "should
	 * never change the result"): the results already in hand follow their
	 * mobs into the new order and the page republishes; only a roster
	 * whose results are missing or stale computes. */
	/** One command argument, null when the command carried none. */
	private static Object arg(Map<String, Object> args, String key) { return args == null ? null : args.get(key); }

	private void republishReordered() {
		List<MonsterStats> order = state.rosterMobs();
		List<MonsterStats> had = lastMobs;
		List<Map<CombatStyle, StyleResult>> perMob = lastPerMob;
		if (order == null || had == null || perMob == null || had.size() != order.size()
			|| perMob.size() != had.size() || !new HashSet<>(had).equals(new HashSet<>(order))) {
			recompute();
			return;
		}
		List<Map<CombatStyle, StyleResult>> moved = new ArrayList<>();
		for (MonsterStats mob : order) { moved.add(perMob.get(had.indexOf(mob))); }
		onRosterResults(order, moved);
	}

	/** A reorder republishes under the book the results were computed with. */
	public void onRosterResults(List<MonsterStats> mobs,
		List<Map<CombatStyle, StyleResult>> perMob) {
		onRosterResults(mobs, perMob, groupBook, groupBookReason);
	}

	public void onRosterResults(List<MonsterStats> mobs,
		List<Map<CombatStyle, StyleResult>> perMob, String book, String reason) {
		if (cancelled) { return; }
		lastMobs = mobs;
		lastPerMob = perMob;
		groupBook = book == null ? "" : book;
		groupBookReason = reason == null ? "" : reason;
		link.publishStatus(false);
		republish();
	}

	/** Route one param change: view params republish, the rest compute.
	 * Shared by the live command and its undo/redo replays. */
	/** A members mechanic is ON only when its param is on AND the F2P lock
	 * is off - the lock vetoes without clearing (field ask 2026-08-27). */
	/** Cure Me chosen for a venomous mob camps Lunar (Andrew 2026-09-08):
	 * the lock, thralls and Death Charge follow. A pinned spell or a lock
	 * to another book means the setup relies on that book - Cure Me
	 * steps aside there (and is not offered). */
	private boolean reliesOnAnotherBook(MonsterStats mob) {
		String pinned = storeOps == null || mob == null ? "" : storeOps.mobs().pinnedSpell(mob.getId());
		String lock = String.valueOf(state.paramsNode().get("spellbookLock"));
		String fightBook = mob == null ? "" : String.valueOf(bookFor(mob));
		List<String> others = List.of("standard", "ancient", "arceuus");
		return pinned != null && !pinned.isEmpty() || others.contains(lock) || others.contains(fightBook);
	}

	/** The cure category a mob needs: anti-venom, antipoison, or "". */
	private static String cureCategory(MonsterStats mob) {
		return inflictsVenom(mob) ? ANTIVENOM
			: inflictsPoison(mob) ? ANTIPOISON : "";
	}

	/** The mob's resolved cure choice ("" when it neither poisons nor
	 * envenoms); Cure Me under another book falls back to Detect best. */
	private String antivenomMode(MonsterStats mob) {
		Supplier<Map<String, String>> defaultsSupplier = supplyDefaults;
		StoreOps ops = storeOps;
		String category = cureCategory(mob);
		if (mob == null || ops == null || defaultsSupplier == null || category.isEmpty()) { return ""; }
		String mode = ops.mobs().supplyOverrides(mob.profileId()).getOrDefault(category,
			defaultsSupplier.get().getOrDefault(category, "DETECT_BEST"));
		return "CURE_ME".equals(mode) && reliesOnAnotherBook(mob) ? "DETECT_BEST" : mode;
	}

	private boolean cureMe(MonsterStats mob) { return "CURE_ME".equals(antivenomMode(mob)); }

	private boolean cureMe(List<MonsterStats> mobs) { return mobs != null && mobs.stream().anyMatch(this::cureMe); }

	/** Arceuus casts (thralls, Death Charge) stand down: Cure Me camps Lunar,
	 * or the fight book is elsewhere (the Sire's Ancients) with no Spellbook
	 * Swap to reach Arceuus (Andrew 2026-09-09). */
	private boolean arceuusBlocked(MonsterStats mob) {
		if (cureMe(mob)) { return true; }
		String fightBook = mob == null ? null : bookFor(mob);
		Supplier<Map<String, String>> defaults = supplyDefaults;
		boolean swap = flag("spellbookSwap")
			|| defaults != null && "SPELLBOOK_SWAP".equals(defaults.get().get("arceuusAccess"));
		return fightBook != null && !fightBook.isEmpty() && !"arceuus".equals(fightBook) && !swap;
	}

	private boolean fightBookElsewhere() {
		List<MonsterStats> mobs = lastMobs != null ? lastMobs : shownMob() == null ? List.of() : List.of(shownMob());
		return mobs.stream().map(MonsterSpellbooks::bookFor)
			.anyMatch(b -> b != null && !b.isEmpty() && !"arceuus".equals(b));
	}

	private boolean arceuusBlocked(List<MonsterStats> mobs) {
		return mobs != null && mobs.stream().anyMatch(this::arceuusBlocked);
	}

	/** The catalogs every published page carries. */
	private void decorate(Map<String, Object> page) {
		page.put("assumeOptions", assumeOptions());
		page.put("liveSpellbook", liveSpellbook);
		page.put("spellbooks", spellbooks);
		page.put("supplyCatalog", supplyCatalog());
		page.put("dartTiers", BlowpipeDarts.tiers());
	}

	/** Each cleared entry's id and scope (ALL when it carries none). */
	private static void eachEntry(List<Map<String, Object>> entries, BiConsumer<Integer, String> fn) {
		for (Map<String, Object> e : entries) {
			fn.accept(((Number) e.get("id")).intValue(),
				e.get("scope") instanceof String ? (String) e.get("scope") : "ALL");
		}
	}

	/** A numeric command argument, or null when absent or not a number. */
	private static Integer intArg(Map<String, Object> args, String key) {
		Object v = arg(args, key);
		return v instanceof Number ? ((Number) v).intValue() : null;
	}

	/** A string command argument, or the fallback when absent or not a string. */
	private static String strArg(Map<String, Object> args, String key, String fallback) {
		Object v = arg(args, key);
		return v instanceof String ? (String) v : fallback;
	}

	private boolean flag(String key) { return Boolean.TRUE.equals(state.paramsNode().get(key)); }

	/** An integer page param (every int param is always present). */
	private int intParam(String key) { return ((Number) state.paramsNode().get(key)).intValue(); }

	/** The selection as a list: the roster, the single mob, or null. */
	private List<MonsterStats> selectedMobs() {
		return state.rosterMobs() != null ? state.rosterMobs()
			: state.mob() == null ? null : List.of(state.mob());
	}

	/** Classic Detect: the best owned antifire tier for the selection,
	 * seeded at every selection and again on demand (nothing when the
	 * plugin has not wired a resolver). */
	private void seedAntifire(List<MonsterStats> selected) {
		Function<List<MonsterStats>, Integer> resolve = antifireResolver;
		if (resolve != null) { state.setParam("antifireMode", resolve.apply(selected)); }
	}

	/** Sunfire runes are assumed when the owned scan sees any (Kendyy,
	 * Discord 2026-10-19); the plate's pick holds until the next selection
	 * or Detect best. */
	private void seedSunfire() {
		IntPredicate owns = ownedCheck;
		state.setParam("sunfireRunes", owns != null && owns.test(SUNFIRE_RUNE)
			|| simmedIds().contains(SUNFIRE_RUNE));
	}

	/** The global sims' ids off the live counts. A sim is "count this as
	 * owned" (Andrew, client test 2026-10-19: a simmed sunfire rune must
	 * seed the plate), so the seeds and the page's simmed set share it. */
	private Set<Integer> simmedIds() {
		Set<Integer> ids = new HashSet<>();
		Supplier<Map<String, Object>> supplier = counts;
		Object simmed = supplier == null ? null : supplier.get().get("simmedItems");
		if (simmed instanceof List) {
			for (Object item : (List<?>) simmed) {
				Object id = item instanceof Map ? ((Map<?, ?>) item).get("id") : null;
				if (id instanceof Number) { ids.add(((Number) id).intValue()); }
			}
		}
		return ids;
	}

	/** Thralls and Death Charge are cast from Arceuus, so a card whose
	 * autocast is on another book cannot have them (Andrew, client test
	 * 2026-10-19: Drakes suggested Water Surge with thralls on). The rule
	 * he picked: that card stands down - it carries the reason here and
	 * the render shows its number without the thrall fold. Melee and
	 * ranged cards, powered staves and Arceuus spells are untouched. */
	/** A group book that is not Arceuus stands the summons down on EVERY
	 * card - the whole raid runs that book (ISS-01M5PB4R0MP149NXWNFSEEHVP8). */
	static void groupStandDown(List<?> mobNodes, String book, String reason) {
		if (book.isEmpty() || "arceuus".equals(book)) { return; }
		eachCard(mobNodes, (side, card) -> card.put("arceuusStandDown", reason));
	}

	/** Every mob plate in the group names the group's book; a mob's own
	 * fight book (the Sire's Ancients) keeps its own reason. */
	private static void groupBookPlates(List<?> mobNodes, String book, String reason) {
		for (Object node : mobNodes) {
			if (node instanceof Map && ((Map<?, ?>) node).get("fightBook") == null) {
				@SuppressWarnings("unchecked")
				Map<String, Object> mob = (Map<String, Object>) node;
				mob.put("fightBook", book);
				mob.put("fightBookReason", reason.isEmpty() ? "One book for the whole group" : reason);
			}
		}
	}

	static void arceuusStandDown(List<?> mobNodes, Map<String, Object> books, boolean wanted) {
		if (!wanted) { return; }
		eachCard(mobNodes, (side, card) -> {
			Object spell = card.get("spell");
			Object book = spell instanceof String ? books.get(spell) : null;
			if (book instanceof String && !"arceuus".equals(book)) {
				card.put("arceuusStandDown", spell + " autocasts from the " + book + " book");
			}
		});
	}

	/** Every yours/bis card under every style of every mob node - the
	 * walk the post-compute decorations share. */
	@SuppressWarnings("unchecked")
	private static void eachCard(List<?> mobNodes, BiConsumer<String, Map<String, Object>> fn) {
		for (Object m : mobNodes) {
			Object styles = ((Map<?, ?>) m).get("styles");
			if (!(styles instanceof Map)) { continue; }
			for (Object s : ((Map<?, ?>) styles).values()) {
				for (String side : new String[]{"yours", "bis"}) {
					Object card = s instanceof Map ? ((Map<?, ?>) s).get(side) : null;
					if (card instanceof Map) { fn.accept(side, (Map<String, Object>) card); }
				}
			}
		}
	}

	/** The Multi view (Andrew 2026-10-19): a mob's tool rows become its
	 * style tabs, every per-target number multiplied by the stack - area
	 * tools by the stack up to nine, the venator chain by its bounce factor
	 * from that side's max hit. A mob without rows keeps its styles. */
	@SuppressWarnings("unchecked")
	static void multiView(List<?> mobNodes, int stack) {
		for (Object m : mobNodes) {
			Map<String, Object> mob = (Map<String, Object>) m;
			Map<String, Object> multi = (Map<String, Object>) mob.get("multi");
			if (multi == null) { continue; }
			for (Object t : multi.values()) {
				Map<String, Object> node = (Map<String, Object>) t;
				String kind = String.valueOf(node.get("multiKind"));
				Map<String, Object> yoursCard = (Map<String, Object>) node.get("yours");
				Map<String, Object> bisCard = (Map<String, Object>) node.get("bis");
				double yoursSet = yoursCard == null ? 0 : num(yoursCard.get("dps"));
				double bisSet = bisCard == null ? 0 : num(bisCard.get("dps"));
				double yours = scaleCard(yoursCard, kind, stack);
				double bis = scaleCard(bisCard, kind, stack);
				// The tab is set + spec: the set scales, the spec line is
				// already priced at the stack by the compute.
				node.put("tabDps", num(node.get("tabDps")) + yoursSet * (yours - 1));
				node.put("bisTabDps", num(node.get("bisTabDps")) + bisSet * (bis - 1));
				node.put("multiFactor", yours > 0 ? yours : bis);
			}
			mob.put("styles", multi);
		}
	}

	/** Scales a card's dps by its tool factor; returns the factor (0 for no card). */
	private static double scaleCard(Map<String, Object> card, String kind, int stack) {
		if (card == null) { return 0; }
		double factor = MultiTool.factor(kind, stack, (int) num(card.get("maxHit")));
		card.put("dps", num(card.get("dps")) * factor);
		return factor;
	}

	private static double num(Object v) { return v instanceof Number ? ((Number) v).doubleValue() : 0; }

	/** With sunfire runes assumed, a fire spell's trip runes carry them in
	 * place of fire runes; the Best-in-game card always assumes them (the
	 * ceiling rule). The five standard fire spells are the "Fire " names. */
	@SuppressWarnings("unchecked")
	private void sunfireRunes(List<?> mobNodes) {
		boolean assumed = flag("sunfireRunes");
		eachCard(mobNodes, (side, card) -> {
			Object spell = card.get("spell");
			if ((assumed || "bis".equals(side)) && spell instanceof String
				&& ((String) spell).startsWith("Fire ")) {
				card.put("runes", withSunfire((List<Map<String, Object>>) card.get("runes")));
			}
		});
	}

	/** A death here goes to a player: the Wilderness, or any mob on a
	 * PvP world (Ragnarrok, 2026-10-20) - locked pouch, blighted food,
	 * the carried kit's fates. Not the wilderness weapon buff. */
	private boolean pvpDeathTrip() { return flag("inWilderness") || intParam("pvpWorld") > 0; }

	private boolean membersFlag(String key) {
		Map<String, Object> params = state.paramsNode();
		return Boolean.TRUE.equals(params.get(key))
			&& !flag("f2pOnly");
	}

	/** Ship-config params persist PER CHARACTER (Andrew 2026-08-31: "like
	 * sim/exclude... once you set your cannon/crew/cannonball type it always
	 * shows as that") - stored under ship.* in the scoped supply-defaults
	 * store and re-seeded on every new selection. */
	public static final Set<String> SHIP_PERSISTED = Set.of(
		"cannonCount", "cannon1Material", "cannon2Material",
		"cannon1Operator", "cannon2Operator", "cannonAmmo", "shipKeel");

	private boolean applyParam(String key, Object value) {
		if (!state.setParam(key, value)) { return false; }
		if (SHIP_PERSISTED.contains(key)) {
			StoreOps ops = storeOps;
			if (ops != null) {
				// Persist what the state SETTLED on (clamps applied), not the
				// raw input - undo re-applies through here too, so the store
				// always mirrors the live params.
				ops.setSupplyDefault("ship." + key,
					String.valueOf(state.paramsNode().get(key)));
			}
		}
		// The swap is supplies-only, except where it unlocks Arceuus casts
		// under a fight book elsewhere (the Sire): Death Charge rides the
		// compute there (Andrew 2026-09-09).
		if (PageState.isViewParam(key) && !("spellbookSwap".equals(key) && fightBookElsewhere())) {
			republish();
		} else {
			recompute();
		}
		return true;
	}

	/** A different account logged in: nothing survives - selection,
	 * results, history all reset and an empty page publishes. */
	public void clearForIdentityChange() {
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
	private void clearResults() {
		lastMobs = null;
		lastPerMob = null;
		link.publishPage(withHistory(page(List.of())));
	}

	/** An in-flight page: the current selection's mobs and the live
	 * params, with no style results yet - the controls render at once
	 * and the cards fill in when the answer lands. */
	private void publishPending() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(this::publishPending);
			return;
		}
		List<MonsterStats> mobs = selectedMobs();
		if (mobs == null) { return; }
		Map<String, Object> entry = entry(mobs, Collections.nCopies(mobs.size(), Map.of()));
		entry.put("params", state.paramsNode());
		entry.put("pending", true);
		decorateProfiles(entry, List.of());
		Map<String, Object> page = withHistory(page(List.of(entry)));
		decorate(page);
		link.publishPage(page);
	}

	/** An idle page: no selection, but counts, catalogs and history -
	 * so the global chips exist BEFORE the first search. */
	public void publishIdle() {
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(this::publishIdle);
			return;
		}
		Map<String, Object> page = withHistory(
			page(List.of()));
		decorate(page);
		link.publishPage(page);
	}

	private void republish() {
		if (restoringSnapshot) { return; }
		if (!SwingUtilities.isEventDispatchThread()) {
			SwingUtilities.invokeLater(this::republish);
			return;
		}
		List<MonsterStats> mobs = lastMobs;
		List<Map<CombatStyle, StyleResult>> perMob = lastPerMob;
		if (mobs == null || perMob == null) { return; }
		Map<String, Object> params = state.paramsNode();
		// The classic wilderness gate: exclusives are ALWAYS in (field
		// report 2026-08-15: Calvar'ion showed no risk - the pinned
		// always-in chip never set the param).
		boolean wildy = mobs.stream().anyMatch(WildernessMonsters::isExclusive) || flag("inWilderness");
		int pvp = intParam("pvpWorld");
		int keptSlots = PvpRisk.keptSlots(wildy ? Math.max(1, pvp) : pvp, flag("protectItem"));
		Map<String, Object> entry = entry(mobs, perMob, keptSlots,
			ownedCheck, simmedIds(), itemLocation);
		// One book for a group: on every plate, and - off Arceuus - the
		// summons stand down on every card (ISS-01M5PB4R0MP149NXWNFSEEHVP8).
		String book = mobs.size() > 1 ? groupBook : "";
		if (!book.isEmpty()) {
			groupBookPlates((List<?>) entry.get("mobs"), book, groupBookReason);
			groupStandDown((List<?>) entry.get("mobs"), book,
				groupBookReason.isEmpty() ? "the group runs the " + book + " book" : groupBookReason);
		}
		if (flag("multiView")) { multiView((List<?>) entry.get("mobs"), intParam("multiStack")); }
		sunfireRunes((List<?>) entry.get("mobs"));
		arceuusStandDown((List<?>) entry.get("mobs"), spellbooks,
			membersFlag("thralls") || !flag("f2pOnly") && intParam("deathCharge") > 0);
		entry.put("params", params);
		List<Map<String, Object>> supplies = suppliesNode(mobs);
		entry.put("supplies", supplies);
		decorateProfiles(entry, supplies);
		entry.put("raidSelection",
			flag("raidSelection"));
		Map<String, Object> shipNode = shipNode(mobs, perMob);
		if (shipNode != null) {
			entry.put("ship", shipNode);
			// Each sea row carries its own cannon dps (zero while the
			// player mans a cannon - the gear is not attacking then).
			boolean manned = "cannon".equals(shipNode.get("station"));
			List<?> byMob = (List<?>) shipNode.get("byMob");
			List<?> mobNodes = (List<?>) entry.get("mobs");
			for (int i = 0; byMob != null && i < mobNodes.size() && i < byMob.size(); i++) {
				@SuppressWarnings("unchecked")
				Map<String, Object> mobNode = (Map<String, Object>) mobNodes.get(i);
				mobNode.put("shipDps", manned ? 0.0 : byMob.get(i));
			}
		}
		Map<String, Object> thrallsNode = null;
		// Thralls cannot be cast on a sailing boat (confirmed 2026-08-31;
		// community-documented, not on the wiki's Boat combat page) - the
		// fold and the trip runes are permanently vetoed for a sea lens.
		// The param persists for the land half of a mixed roster.
		boolean seaLens = !mobs.isEmpty() && isNaval(lensOf(mobs).getName());
		if (!seaLens && membersFlag("thralls") && !arceuusBlocked(mobs)
			&& (book.isEmpty() || "arceuus".equals(book))) {
			double dps = ExtraDps.thrallDps(magicLevel);
			String tier = ExtraDps.thrallTier(magicLevel);
			if (dps > 0 && tier != null) {
				thrallsNode = map("dps", dps, "tier", tier);
				entry.put("thralls", thrallsNode);
			}
		}
		Map<String, Object> page = withHistory(page(List.of(entry)));
		page.put("spells", new ArrayList<>(spellbooks.keySet()));
		decorate(page);
		Supplier<Map<String, Object>> countSupplier = counts;
		page.put("reportText", ReportBuilder.build(coreVersion, state, mobs, perMob, keptSlots,
			countSupplier == null ? null : countSupplier.get(), thrallsNode, shipNode, supplies, cureMe(mobs),
			(List<Map<String, Object>>) entry.get("mobs"), book, groupBookReason));
		link.publishPage(page);
	}

	@Setter
	private volatile String coreVersion = "dev";
	/** The account's live Magic level - thrall dps scales off it. */
	@Setter
	private volatile int magicLevel = 99;
	@Setter
	private volatile int rangedLevel = 99;
	@Setter
	private volatile int sailingLevel = 1;

	/** The mode the optimizer should run - the player's tab, for every
	 * player since 2026-10-22 (ISS-01M5JBYYWHQNTM0CGNYWQBNYX6). */
	public OptimizeMode optimizeMode() {
		return OptimizeMode.valueOf(String.valueOf(state.paramsNode().get("optimizeMode")));
	}

	/** Not Enough Runes, for the slot ladder's "how to get it" (ISS-01M5GE9EJSX6YWCKMSYEWKZ132):
	 * whether it is enabled, and how to open it on an item (the plugin posts
	 * PluginMessage "notenoughrunes"/"displayItemById"). Re-set when plugins change. */
	private BooleanSupplier nerAvailable = () -> false;
	private IntConsumer nerOpen = id -> { };

	public void setNerLink(BooleanSupplier available, IntConsumer open) {
		this.nerAvailable = available;
		this.nerOpen = open;
		state.setParam("nerAvailable", available.getAsBoolean());
	}

	/** Shortest Path, for the path link above the footer (ISS-01M5TCXDV8HF2V5C627SZEY2W3,
	 * Andrew 2026-10-25: "card output", "all mobs if possible", "hidden"):
	 * whether it is enabled, and how to walk to a mob. Re-set when plugins change. */
	private BooleanSupplier pathAvailable = () -> false;
	private Consumer<String> pathOpen = name -> { };

	public void setPathLink(BooleanSupplier available, Consumer<String> open) {
		this.pathAvailable = available;
		this.pathOpen = open;
	}

	/** Demonic Offering castable - members, 84 Magic, A Kingdom Divided - from
	 * the plugin (ISS-01M5TQ977R0MXBMSQ3VBHS29GB). */
	@Setter
	private volatile BooleanSupplier offeringReady = () -> false;

	private boolean pathable(MonsterStats mob) {
		return mob != null && pathAvailable.getAsBoolean() && MobLocations.of(mob.getName()) != null;
	}

	/** The GWD Ancient chip (the Nex bank) - read by the plugin per compute. */
	public boolean gwdAncient() { return Boolean.TRUE.equals(state.paramsNode().get("gwdAncient")); }

	/** The page-param write the plugin's own events send (world type, diary). */
	public boolean set(String param, Object value) {
		return execute("set-param", Map.of("param", param, "value", value));
	}

	/** Shared by query- and id-resolved selects: the undoable command
	 * with detect-antifire at selection, exactly like the classic. */
	private boolean selectResolved(MonsterStats mobPick,
		MonsterGroup groupPick) {
		Object[] prev = state.selectionSnapshot();
		boolean hadSelection = state.hasSelection();
		return record(Commands.of("vs " + (groupPick != null ? groupPick.getName() : mobPick.label()), () -> {
			if (groupPick != null) {
				state.selectRoster(groupPick.getMobs(), groupPick.getName(),
					groupPick.isRaid());
			} else {
				state.select(mobPick);
			}
			List<MonsterStats> selected = groupPick != null
				? groupPick.getMobs() : List.of(mobPick);
			seedAntifire(selected);
			seedSunfire();
			// A fresh selection resets the tab so the default (the
			// best style for THIS mob) applies - the classic made a
			// new entry per selection, which cleared it implicitly.
			state.setParam("selectedTab", "");
			// A fresh search opens on YOURS - the answer you can act
			// on now; BiS is the aspirational view you opt into
			// (field ask 2026-08-22).
			state.setParam("viewingBis", false);
			// ...with no swaps strip open and its page back at the start
			// (Andrew 2026-10-24: "whenver a new search is done the ladder
			// view should be reset ... wait until an item slot is clicked").
			state.setParam("ladderSlot", "");
			state.setParam("ladderPage", 0);
			// A slayer monster opens ON task (Andrew 2026-10-19: most
			// people meet these through slayer); anything else opens
			// off. Task-only bosses are always on task (the classic
			// rule: the Sire cannot be fought off-task, so the search
			// must never show it that way). Seeded at every selection
			// like the antifire tier; the chip overrides afterwards.
			boolean slayer = false;
			// Wilderness-exclusives set the PARAM, not just the chip
			// (field report 2026-08-15: a 75k risk cap produced a 21M
			// recommendation - the display honored the exclusive but
			// the COMPUTE derived tradeables=-1 from the raw param and
			// ran unconstrained).
			boolean anyWilderness = false;
			for (MonsterStats m : selected) {
				slayer |= m.isSlayerMonster() || SlayerLockedMonsters.isTaskOnly(m);
				anyWilderness |= WildernessMonsters.isWilderness(m);
				if (WildernessMonsters.isExclusive(m)) { state.setParam("inWilderness", true); }
			}
			state.setParam("onTask", slayer);
			// A selection with NO wilderness-capable member cannot be a
			// wilderness trip: the param resets, or a stale wildy
			// session warps the next one (field report 2026-08-31: a
			// ToA roster under "Wilderness: yes" + a 75k risk cap chose
			// cheap-to-lose junk - Ring of shadows, Shayzien boots -
			// over real gear; the risk optimizer working in the wrong
			// context).
			if (!anyWilderness) { state.setParam("inWilderness", false); }
			// Raids are never a slayer-task context: the chip is
			// suppressed elsewhere, and the PARAM resets too so slayer
			// helm math never leaks in (same report: "On task: yes").
			if (groupPick != null && groupPick.isRaid()) { state.setParam("onTask", false); }
			// Recommended trip inventory per selection (Andrew
			// 2026-09-03: raids 10, other groups 4, singles 1 - "bad
			// results at 3/8"). The
			// GROUP DATA declares its inventory - deriving raid-ness
			// from supplied-boost coverage missed Theatre of Blood,
			// which has no supplied boost (field report 2026-08-31).
			// GWD (Andrew 2026-10-21: "add more inventory slot default for
			// GWD"): the spec + every route god carried + the Ancient.
			List<String> gwdGods = selected.size() == 1
				? GwdProtection.godsFor(selected.get(0), false) : List.of();
			state.setParam("maxSwaps",
				groupPick != null && groupPick.getInventory() > 0
					? groupPick.getInventory()
					: !gwdGods.isEmpty() ? 2 + gwdGods.size()
					: selected.size() > 1 ? 4 : 1);
			Supplier<Map<String, Object>> seeder = assumptionSeeder;
			if (seeder != null) {
				for (var seed : seeder.get().entrySet()) { state.setParam(seed.getKey(), seed.getValue()); }
			}
			recompute();
			return true;
		}, () -> {
			state.restoreSelection(prev);
			if (hadSelection) {
				recompute();
			} else {
				clearResults();
			}
			return true;
		}));
	}

	/** The classic search dropdown feed: under 2 chars nothing, then
	 * up to 3 matching groups followed by up to 25 monsters. */
	public List<Map<String, Object>> searchOptions(String query) {
		List<Map<String, Object>> options = new ArrayList<>();
		String q = query == null ? "" : query.trim();
		if (q.length() < 2) { return options; }
		for (MonsterGroup group
			: MonsterGroups.search(groups(), q, 3)) {
			options.add(map("label", group.label(), "group", group.getName()));
		}
		for (MonsterStats m : data.searchMonsters(q, 25)) {
			Map<String, Object> node = map("label", m.label(), "id", m.getId());
			if (isNaval(m.getName())) { node.put("naval", true); }
			// Versions can share an npc id - the picker must say WHICH
			// row it means (Duke 12191, field report 2026-08-21).
			node.put("version", m.getVersion());
			options.add(node);
		}
		return options;
	}

	/** Every supply category with its label, current DEFAULT and option
	 * list - the grey chip's menu (global defaults, not per-mob). */
	private List<Map<String, Object>> supplyCatalog() {
		Supplier<Map<String, String>> supplier = supplyDefaults;
		List<Map<String, Object>> out = new ArrayList<>();
		if (supplier == null) { return out; }
		Map<String, String> defaults = supplier.get();
		for (String category : CATEGORY_KEYS) {
			String label = label(category);
			if (label != null) { out.add(catalogNode(category, label, defaults, optionNodes(options(category), false))); }
		}
		// The Arceuus-access choice rides the same menu in the classic.
		out.add(catalogNode("arceuusAccess", "Arceuus access", defaults, List.of(
			Map.of("key", "DETECT_BEST", "name", "Direct (camp Arceuus)"),
			Map.of("key", "SPELLBOOK_SWAP",
				"name", "Via Spellbook Swap (Lunar home, adds swap + Vengeance runes)"))));
		// Combination runes are a PANEL preference now (field ask
		// 2026-08-22), defaulting to detection - a config toggle and a
		// panel control for one behaviour is the source-of-truth trap
		// this release already paid for once.
		out.add(catalogNode("comboRunes", "Combination runes", defaults,
			List.of(Map.of("key", "ALWAYS", "name", "Always combine"))));
		return out;
	}

	/** One catalog entry: the category, its label, its current default
	 * (DETECT_BEST until the menu changes it) and its option rows. */
	private static Map<String, Object> catalogNode(String category, String label,
		Map<String, String> defaults, List<?> options) {
		return map("category", category, "label", label,
			"current", defaults.getOrDefault(category, "DETECT_BEST"), "options", options);
	}

	/** {key, name} rows for a category's options - the catalog's and the
	 * per-mob override menu's; Cure Me is hidden where the setup relies
	 * on another book. */
	private static List<Map<String, Object>> optionNodes(List<Option> all, boolean hideCureMe) {
		List<Map<String, Object>> options = new ArrayList<>();
		for (Option option : all) {
			if (!(hideCureMe && "CURE_ME".equals(option.key))) {
				options.add(map("key", option.key, "name", option.name));
			}
		}
		return options;
	}

	/** The combination-rune preference: the panel's choice, with
	 * DETECT meaning "combine when you own combination runes". */
	private boolean combineRunes() {
		Supplier<Map<String, String>> supplier = supplyDefaults;
		String choice = supplier == null ? null : supplier.get().get("comboRunes");
		if ("ALWAYS".equals(choice)) { return true; }
		if ("NONE".equals(choice)) { return false; }
		IntPredicate owns = ownedCheck;
		return owns != null && Arrays.stream(comboRuneIds()).anyMatch(owns);
	}

	/** The mob a card gesture acts on: the single selection when there is
	 * one, else the lensed roster row. Rosters have no state.mob() - the
	 * set-note lesson (field report 2026-08-15: notes never saved on a
	 * trip), which every per-mob command has since had to repeat. */
	/** A ladder step tried: sim the item here when it is not yours yet AND
	 * put it in place (its slot's pin, or the mob's spec) - one undo step
	 * that takes the sim back and restores what was there. */
	private boolean tryStep(StoreOps ops, MonsterStats mob, int id, String what, Runnable put, Runnable restore) {
		int mobId = mob.getId();
		boolean sim = ownedCheck == null || !ownedCheck.test(id);
		return recorded("Try " + what + " - " + mob.getName(), () -> {
			if (sim) { ops.simForMob(mobId, id); }
			put.run();
		}, () -> {
			if (sim) { ops.mobs().removeMobSim(mobId, id); }
			restore.run();
		});
	}

	/** One per-mob list write, by command name - the card trio's adds and
	 * the mob line's removes, which clear-mob-list replays per entry. A
	 * removed sim's pins go with it when the item is not yours (Andrew's
	 * 2026-10-22 rule) and come back with the sim. */
	private void mobListStep(StoreOps ops, int mobId, String cmd, int id, String scope,
		Map<Integer, List<Object[]>> droppedPins) {
		switch (cmd) {
			case "remove-mob-exclusion": ops.mobs().removeMobExclusion(mobId, scope, id); break;
			case "remove-mob-sim":
				ops.mobs().removeMobSim(mobId, id);
				droppedPins.put(id, dropPinsIfNotYours(ops, mobId, id));
				break;
			case "add-mob-filter": ops.addMobFilter(mobId, id); break;
			case "remove-mob-filter": ops.mobs().removeMobFilter(mobId, scope, id); break;
			case "exclude-for-mob": ops.excludeForMob(mobId, scope, id); break;
			default:
				ops.simForMob(mobId, id);
				restorePins(ops, mobId, id, droppedPins.getOrDefault(id, List.of()));
				droppedPins.remove(id);
				break;
		}
	}

	/** Filters change the bank view only; the rest change the answer. */
	private void refreshAfter(String cmd) {
		if (cmd.endsWith("-filter")) {
			republish();
		} else {
			recompute();
		}
	}

	/** Unpins this item on the mob in every scope and slot it holds;
	 * what went, scope + slot, for the undo (ISS-01M5HJ84ADBHWB2302G66WW2G4). */
	private static List<Object[]> dropPinsOf(StoreOps ops, int mobId, int itemId) {
		List<Object[]> dropped = new ArrayList<>();
		ops.mobs().allPins(mobId).forEach((scope, slots) -> slots.forEach((slot, pinned) -> {
			if (pinned == itemId) {
				dropped.add(new Object[]{scope, slot});
			}
		}));
		for (Object[] pin : dropped) { ops.mobs().unpin(mobId, (String) pin[0], (GearSlot) pin[1]); }
		return dropped;
	}

	private static void restorePins(StoreOps ops, int mobId, int itemId, List<Object[]> dropped) {
		for (Object[] pin : dropped) { ops.mobs().pin(mobId, (String) pin[0], (GearSlot) pin[1], itemId); }
	}

	/** A sim went: the item's pins on this mob go with it unless the item
	 * is still yours - owned, simmed everywhere, or still simmed here
	 * (Andrew 2026-10-22: "it is showing me a fang, i dont have fang"). */
	private List<Object[]> dropPinsIfNotYours(StoreOps ops, int mobId, int itemId) {
		boolean yours = ownedCheck != null && ownedCheck.test(itemId) || hasId(ops.mobSims(mobId), itemId);
		return yours ? List.of() : dropPinsOf(ops, mobId, itemId);
	}

	/** Clear all pins on the mob - one undo step that puts each back in
	 * its scope. */
	private boolean clearPins(StoreOps ops, MonsterStats mob) {
		int mobId = mob.getId();
		String mobName = mob.getName();
		Map<String, Map<GearSlot, Integer>> pins = ops.mobs().allPins(mobId);
		int count = pins.values().stream().mapToInt(Map::size).sum();
		if (count == 0) { return false; }
		return recorded("Clear pins (" + count + ") - " + mobName,
			() -> pins.forEach((scope, slots) -> slots.keySet().forEach(slot -> ops.mobs().unpin(mobId, scope, slot))),
			() -> pins.forEach((scope, slots) -> slots.forEach((slot, id) -> ops.mobs().pin(mobId, scope, slot, id))));
	}

	private MonsterStats shownMob() { return state.mob() != null ? state.mob() : lensedMob(); }

	/** The lens-selected mob (supply overrides key off its profile). */
	private MonsterStats lensedMob() {
		List<MonsterStats> roster = state.rosterMobs();
		return roster == null ? state.mob() : lensOf(roster);
	}

	/** The row the lens is on, clamped to the list. */
	private MonsterStats lensOf(List<MonsterStats> mobs) {
		return mobs.get(Math.min(Math.max(intParam("lensIndex"), 0), mobs.size() - 1));
	}

	/** The corpus label for an item id, or the bare id when the corpus lacks it. */
	private String itemLabel(int itemId) {
		GearItem item = data.getGear(itemId);
		return item == null ? String.valueOf(itemId) : item.label();
	}

	/** The first of the ids the player owns, else the fallback. */
	private static int firstOwned(IntPredicate owns, int fallback, int... ids) {
		return Arrays.stream(ids).filter(owns).findFirst().orElse(fallback);
	}

	/** Whether a trio-list row list carries the item. */
	private static boolean hasId(List<Map<String, Object>> rows, int id) {
		return rows.stream().anyMatch(row -> row.get("id") instanceof Number && ((Number) row.get("id")).intValue() == id);
	}

	/** The first corpus monster matching, or null. */
	private MonsterStats monster(Predicate<MonsterStats> test) {
		return data.getMonsters().stream().filter(test).findFirst().orElse(null);
	}

	/** The classic activeSupplies, core-side: config defaults overlaid
	 * by the lensed mob's overrides, Detect = best owned tier, antivenom
	 * only for venomous rosters. Each node carries the category's full
	 * option list for the override menu. */
	/** Wilderness carried-kit fates for the risk lists (Andrew 2026-08-31:
	 * the inventory is part of what is lost/kept). Trip quantities are
	 * unknown, so items are listed by fate, not priced: a trouver-locked
	 * pouch and the untradeable cape are kept; the runes (a locked pouch
	 * does not protect its contents - wiki (l)) and every supply pick are
	 * lost. Keys "kept" and "lost". */
	static Map<String, List<String>> wildyCarriedFates(int pouch, boolean cape,
		List<String> supplies, boolean utilityRunes) {
		List<String> kept = new ArrayList<>();
		List<String> lost = new ArrayList<>();
		if (pouch == 27509) {
			kept.add("Divine rune pouch (locked - kept)");
		} else if (pouch == 24416) { kept.add("Rune pouch (locked - kept)"); }
		if (cape) { kept.add("Magic cape (kept)"); }
		if (utilityRunes) { lost.add("Trip runes (lost)"); }
		for (String supply : supplies) { lost.add(supply + " (lost)"); }
		return Map.of("kept", kept, "lost", lost);
	}

	private List<Map<String, Object>> suppliesNode(List<MonsterStats> mobs) {
		Supplier<Map<String, String>> defaultsSupplier = supplyDefaults;
		StoreOps ops = storeOps;
		IntPredicate owns = ownedCheck;
		MonsterStats lensed = lensedMob();
		List<Map<String, Object>> nodes = new ArrayList<>();
		// The dragonfire assumption is a supply too (Andrew 2026-10-18: "when
		// the super antifire is selected it should be put into the inventory").
		int antifire = intParam("antifireMode");
		if (antifire > 0 && mobs.stream().anyMatch(DragonfireRules::breathesFire)) {
			int[] doses = antifire == 2 ? DragonfireRules.SUPER_ANTIFIRE_IDS : DragonfireRules.REGULAR_ANTIFIRE_IDS;
			nodes.add(map("category", "antifire",
				"name", antifire == 2 ? "Super antifire potion" : "Antifire potion", "itemId", doses[0],
				"ids", Arrays.stream(doses).boxed().collect(Collectors.toList()), "options", List.of()));
		}
		if (defaultsSupplier == null || ops == null || owns == null || lensed == null) { return nodes; }
		Map<String, String> defaults = defaultsSupplier.get();
		Map<String, String> overrides = ops.mobs().supplyOverrides(lensed.profileId());
		// At sea the BOAT takes the hits (Andrew 2026-09-02): a sea-only
		// trip packs repair kits and nothing edible; a mixed roster keeps
		// the land categories for its land half.
		boolean seaOnly = !mobs.isEmpty() && mobs.stream().allMatch(m -> isNaval(m.getName()));
		boolean otherBook = reliesOnAnotherBook(lensed);
		for (var e : defaults.entrySet()) {
			String category = e.getKey();
			String mode = overrides.getOrDefault(category, e.getValue());
			if (otherBook && "CURE_ME".equals(mode)) { mode = "DETECT_BEST"; }
			if ("NONE".equals(mode)
				|| (seaOnly && !TripSupplies.SHIP_REPAIR_KIT.equals(category))
				|| (TripSupplies.CANNON.equals(category)
					&& flag("raidSelection"))) {
				continue;
			}
			if (TripSupplies.SHIP_REPAIR_KIT.equals(category)) {
				// Repair kits are the ship's food - recommended only for a
				// sea mob that can actually damage YOUR keel (Andrew,
				// 2026-08-31: ">0 damage"). A dragon keel at a hammerhead
				// carries nothing.
				String keel = String.valueOf(state.paramsNode().get("shipKeel"));
				if (keelMaxHit(lensed.getName(), keel) <= 0) { continue; }
			}
			if (TripSupplies.ANTIVENOM.equals(category) && mobs.stream().noneMatch(TripSupplies::inflictsVenom)
				|| TripSupplies.ANTIPOISON.equals(category) && mobs.stream().noneMatch(TripSupplies::inflictsPoison)
				|| TripSupplies.CANNON.equals(category) && mobs.stream().anyMatch(TripSupplies::noCannon)) {
				continue;
			}
			// A boss's own teleport (ISS-01M5RMNK62507Q42KKCJ9A7AEE) fits only a
			// trip with that boss: the pick and the menu come from the options
			// that fit, and a stored choice for another boss falls back to the
			// best fitting one you own. General categories fit every trip.
			List<Option> fit = fitting(category, mobs, unlocks);
			Option pick = option(category, mode);
			if (pick == null && mode.startsWith("DETECT") || pick != null && !fit.contains(pick)) {
				pick = detectBest(fit, owns, pvpDeathTrip());
			}
			if (pick == null) { continue; }
			List<Map<String, Object>> options = optionNodes(fit, otherBook);
			// A kit names every item and gets a cell each (field 2026-09-08:
			// the holy symbol was filter-only); doses share one cell.
			boolean kit = pick.names.length == pick.ids.length && pick.names.length > 1;
			for (int i = 0; i < (kit ? pick.ids.length : 1); i++) {
				int[] doses = kit ? pick.cells != null ? pick.cells[i] : new int[]{pick.ids[i]} : pick.ids;
				nodes.add(map("category", category, "name", kit ? pick.names[i] : pick.name,
					"itemId", pick.ids.length > 0 ? pick.ids[i] : 0,
					"ids", Arrays.stream(doses).boxed().collect(Collectors.toList()), "options", options));
			}
		}
		return nodes;
	}

	/** The trip's UTILITY casts for one mob: the fight's own spell (the
	 * Sire's Shadow Barrage stun), the thrall resurrection at the live
	 * tier, and Death Charge - each cast's runes tagged with why
	 * (field report 2026-08-15: 'not seeing any runes' on a trip whose
	 * gear never autocasts but whose FIGHT casts plenty). Looked up by
	 * NAME so a phase variant's synthetic id still finds its monster. */
	private List<Map<String, Object>> utilityRunesFor(Map<String, Object> mob) {
		List<Map<String, Object>> out = new ArrayList<>();
		Object name = mob.get("name");
		MonsterStats stats = monster(m -> m.getName().equals(name));
		String fightSpell = spellFor(stats);
		if (fightSpell != null) { addUtilityRunes(out, fightSpell, fightSpell + " (fight mechanic)"); }
		// The slayer item the kill needs - listed even when not owned
		// (Andrew 2026-10-22: "show them as necessary even when the player
		// doesn't own those items").
		IntPredicate owns = ownedCheck;
		out.addAll(RequiredGear.carriedPicks(stats, owns == null ? id -> false : owns));
		// The loot containers you own that this monster's drops warrant
		// (Andrew 2026-10-22: "killerwatts have a predominant herb drop table").
		out.addAll(LootContainers.picks(stats, owns, flag("inWilderness")));
		boolean seaMob = stats != null
			&& isNaval(stats.getName());
		boolean lunarCamp = arceuusBlocked(stats);
		if (!seaMob && membersFlag("thralls") && !lunarCamp) {
			String tier = ExtraDps.thrallTier(magicLevel);
			if (tier != null) {
				addUtilityRunes(out, "Resurrect " + tier + " Ghost",
					tier + " thrall (per resurrection)");
				// The book the thrall is cast from (mrapw 2026-10-18: the
				// kit had the runes but not the book; any weapon casts it).
				out.add(map("id", ExtraDps.BOOK_OF_THE_DEAD, "name", "Book of the Dead", "qty", 1,
					"why", tier + " thrall - cast from the book, any weapon"));
			}
		}
		int dCharge = intParam("deathCharge");
		boolean f2pLocked = flag("f2pOnly");
		if (!f2pLocked && !lunarCamp && dCharge > 0) {
			addUtilityRunes(out, "Death Charge", "Death Charge (per cast)");
		}
		if (flag("spellbookSwap")) {
			// Supplies only (field ask 2026-08-22): the trip carries the
			// runes to keep Vengeance up. Vengeance does return damage,
			// but modelling that is its own problem - this is purely
			// "bring more runes".
			addUtilityRunes(out, "Vengeance", "Vengeance (per cast)");
			// The SWAP is only needed when something else wants another
			// book. Vengeance alone - no thralls, no Death Charge, no
			// fight book - is a perfectly good setup and must not drag
			// swap runes along (field ask 2026-08-22).
			String fightBook = bookFor(stats);
			boolean elsewhere = fightBook != null && !fightBook.isEmpty()
				&& !"lunar".equalsIgnoreCase(fightBook);
			elsewhere |= !seaMob && !lunarCamp && membersFlag("thralls");
			elsewhere |= !f2pLocked && !lunarCamp && dCharge > 0;
			if (elsewhere) { addUtilityRunes(out, "Spellbook Swap", "Spellbook Swap (per swap)"); }
		}
		return combineRunes() ? combineCombos(out) : out;
	}

	private static void addUtilityRunes(List<Map<String, Object>> out,
		String spell, String why) {
		List<Map<String, Object>> runes =
			costFor(spell, null, null);
		if (runes == null) { return; }
		for (Map<String, Object> rune : runes) {
			rune.put("why", why);
			out.add(rune);
		}
	}

	/** Attach per-mob profile state (pinned spell/spec, note, the pins,
	 * the trio lists, the utility casts) to each mob node so renderers
	 * can show and edit it. */
	@SuppressWarnings("unchecked")
	private void decorateProfiles(Map<String, Object> entry, List<Map<String, Object>> supplies) {
		StoreOps ops = storeOps;
		Object mobsNode = entry.get("mobs");
		if (ops == null || !(mobsNode instanceof List)) { return; }
		MonsterStats lensed = lensedMob();
		for (Object node : (List<?>) mobsNode) {
			if (node instanceof Map) {
				Map<String, Object> mob = (Map<String, Object>) node;
				Object id = mob.get("id");
				if (id instanceof Number) {
					int monsterId = ((Number) id).intValue();
					mob.put("pinnedSpell", ops.mobs().pinnedSpell(monsterId));
					mob.put("pinnedSpec", ops.mobs().pinnedSpec(monsterId));
					mob.put("note", ops.mobs().note(monsterId));
					// An ash dropper with no book of its own suggests Arceuus
					// (ISS-01M5TQ977R0MXBMSQ3VBHS29GB, Andrew 2026-10-25: "default to arceuss for
					// the ability to break down the ashes into prayer").
					mob.put("demonicOffering", lensed != null && offeringReady.getAsBoolean()
						&& LootContainers.drops(lensed, "ashes"));
					mob.put("skipDegradable", ops.skipDegradable(monsterId));
					mob.put("cureMe", cureMe(lensed));
					mob.put("arceuusBlocked", arceuusBlocked(lensed));
					mob.put("antivenom", antivenomMode(lensed));
					mob.put("cureCategory", cureCategory(lensed));
					List<Map<String, Object>> utility = utilityRunesFor(mob);
					mob.put("utilityRunes", utility);
					// The casting kit: divine rune pouch > rune pouch when
					// owned; the Magic cape (t preferred) for the book
					// swap perk - suggestions only when the trip casts.
					// WILDERNESS (field report 2026-08-31 + wiki): an
					// unlocked pouch is LOST on a pvp death, so only the
					// trouver-locked variants are ever recommended there -
					// locked divine, locked regular, or no pouch at all.
					IntPredicate owns = ownedCheck;
					if (!utility.isEmpty() && owns != null) {
						boolean wildyTrip = pvpDeathTrip();
						int pouch = wildyTrip ? firstOwned(owns, -1, 27509, 24416)
							: firstOwned(owns, 12791, 27281, 27509, 12791, 24416);
						if (pouch > 0) { mob.put("castingPouch", pouch); }
						// Carried-kit fates (Andrew 2026-08-31): the inventory
						// joins the lost/kept lists - see wildyCarriedFates.
						if (wildyTrip) {
							Map<String, List<String>> fates = wildyCarriedFates(pouch, owns.test(9763) || owns.test(9762),
								supplies.stream().map(s -> String.valueOf(s.get("name"))).collect(Collectors.toList()),
								!utility.isEmpty());
							eachCard(List.of(mob), (side, card) -> {
								Object riskNode = card.get("risk");
								for (String fate : new String[]{"kept", "lost"}) {
									Object list = riskNode instanceof Map ? ((Map<?, ?>) riskNode).get(fate) : null;
									if (list instanceof List) { ((List<Object>) list).addAll(fates.get(fate)); }
								}
							});
						}
						mob.put("castingCape", firstOwned(owns, -1, 9763, 9762));
					}
					mob.put("mobExclusions", ops.mobExclusions(monsterId));
					// The pins by scope (slot -> label) for the card, and the
					// pin list's rows - one walk fills both.
					Map<String, Map<String, String>> pins = new LinkedHashMap<>();
					List<Map<String, Object>> pinRows = new ArrayList<>();
					ops.mobs().allPins(monsterId).forEach((scope, slots) -> {
						Map<String, String> named = new LinkedHashMap<>();
						slots.forEach((slot, itemId) -> {
							named.put(Names.lower(slot.name()), itemLabel(itemId));
							pinRows.add(map("id", itemId, "name", itemLabel(itemId), "scope", scope,
								"slot", Names.lower(slot.name())));
						});
						pins.put(scope, named);
					});
					mob.put("pins", pins);
					mob.put("mobPins", pinRows);
					// The worn slayer requirements, for the cells' "needed"
					// note - by id, so a phase variant keeps its own.
					mob.put("requiredGear", RequiredGear.requiredIds(monster(m -> m.getId() == monsterId), data,
						flag("kourendElite") ? Set.of(RequiredGear.KOUREND_ELITE) : Set.of())
						.values().stream().flatMap(Set::stream).collect(Collectors.toList()));
					mob.put("mobSims", ops.mobSims(monsterId));
					mob.put("mobFilters", ops.mobFilters(monsterId));
				}
			}
		}
	}

	private volatile Map<String, Object> assumeOptionsCache;

	/** Static per-style prayer/boost option lists (key + label),
	 * mirroring the classic pick menus. */
	private Map<String, Object> assumeOptions() {
		Map<String, Object> cached = assumeOptionsCache;
		if (cached != null) { return cached; }
		Map<String, Object> prayers = new LinkedHashMap<>();
		Map<String, Object> boosts = new LinkedHashMap<>();
		for (CombatStyle style : CombatStyle.concreteValues()) {
			prayers.put(style.name().toLowerCase(), Arrays.asList(
				PrayerBonuses.optionsFor(style)));
			List<Map<String, Object>> styleBoosts = new ArrayList<>();
			for (BoostProfile b : BoostProfile.values()) {
				if (b == BoostProfile.NONE
					|| b == BoostProfile.LIVE_CURRENT) {
					continue;
				}
				if (b.boosts(style == CombatStyle.MELEE ? 'a' : style == CombatStyle.RANGED ? 'r' : 'm')) {
					styleBoosts.add(map("key", b.name(), "label", b.toString()));
				}
			}
			boosts.put(style.name().toLowerCase(), styleBoosts);
		}
		// Icon facts for the pickers (additive): prayer name -> sprite id,
		// boost label -> potion item id. Renderers draw, never map.
		Map<String, Object> prayerSprites = new LinkedHashMap<>();
		Map<String, Object> prayerImages = new LinkedHashMap<>();
		for (Object list : prayers.values()) {
			for (Object o : (List<?>) list) {
				String name = String.valueOf(o);
				prayerSprites.put(name, AssumeIcons.prayerSprite(name));
				String image = AssumeIcons.prayerImage(name);
				if (image != null) { prayerImages.put(name, image); }
			}
		}
		Map<String, Object> boostItems = new LinkedHashMap<>();
		for (Object list : boosts.values()) {
			for (Object option : (List<?>) list) {
				String label = String.valueOf(((Map<?, ?>) option).get("label"));
				boostItems.put(label, AssumeIcons.boostItem(label));
			}
		}
		Map<String, Object> spellSprites = new LinkedHashMap<>();
		for (SpellStats spell : data.getSpells()) {
			spellSprites.put(spell.getName(),
				AssumeIcons.spellSprite(spell.getName()));
		}
		// Spellbook Swap is 96 Magic (Vengeance 94) - offered when the
		// player can actually reach the level, boosts included.
		Map<String, Object> options = map("prayers", prayers, "boosts", boosts,
			"prayerSprites", prayerSprites, "prayerImages", prayerImages,
			"spellbookSwapAvailable", magicLevel + 5 >= 96, "boostItems", boostItems, "spellSprites", spellSprites);
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
		List<Map<CombatStyle, StyleResult>> perMob) {
		if (mobs == null || mobs.isEmpty()) { return null; }
		Map<String, Object> params = state.paramsNode();
		// The LENSED mob decides (REQ-SC-7): a mixed roster prices cannons
		// only while a sea mob is under the lens.
		int lens = intParam("lensIndex");
		MonsterStats lensed = lensOf(mobs);
		if (!isNaval(lensed.getName())) { return null; }
		int count = intParam("cannonCount");
		String ammoPick = String.valueOf(params.get("cannonAmmo"));
		List<String> carriedTiers = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			carriedTiers.add(String.valueOf(
				params.get(i == 0 ? "cannon1Material" : "cannon2Material")));
		}
		boolean detect = "detect".equals(ammoPick);
		String ammo = ammoPick;
		String ammoBlocked = null;
		Predicate<String> everyone = ball -> carriedTiers.stream().allMatch(t -> canFire(t, ball));
		if (!detect && count > 0 && !everyone.test(ammoPick)) {
			// The RESOLUTION downranks, never the pick (field report
			// 2026-08-31: dragon -> rune swap still showed a dragon ball):
			// the effective tier is the pick when every carried cannon can
			// fire it, else the best they can all share - one tier for the
			// whole ship (REQ-SC-2). The pick itself stays put, so
			// upgrading the cannon back restores it.
			ammo = bestSharedBall(carriedTiers);
		}
		if (detect) {
			// Best BANKED ball every carried cannon can fire - never a ball
			// the bank does not hold (the 0.4.1 rule, applied to ammo).
			IntPredicate owns = ownedCheck;
			String best = null;
			for (Ball ball : balls()) {
				if (everyone.test(ball.tier) && owns != null && owns.test(ball.itemId)) { best = ball.tier; }
			}
			if (best == null) {
				String top = bestSharedBall(carriedTiers);
				ammoBlocked = "No cannonballs banked"
					+ (top == null ? "" : " (up to " + top + ")");
				ammo = null;
			} else {
				ammo = best;
			}
		}
		String op1 = String.valueOf(params.get("cannon1Operator"));
		String op2 = String.valueOf(params.get("cannon2Operator"));
		String station = "you".equals(op1) || "you".equals(op2) ? "cannon" : "gear";
		// The wiki rule: the player's worn ranged accuracy and strength join
		// their cannon fire, EXCLUDING the weapon, shield and ammo slots.
		// The manned player still wears armour - the best owned ranged set
		// the engine already computed - so the cannon borrows its bonuses.
		// Crew gear is unknowable and stays at zero.
		int wornAcc = 0;
		int wornStr = 0;
		int gearLens = Math.min(Math.max(lens, 0), (perMob == null ? 1 : perMob.size()) - 1);
		StyleResult rangedResult = perMob == null || perMob.size() <= gearLens
			|| perMob.get(gearLens) == null ? null
			: perMob.get(gearLens).get(CombatStyle.RANGED);
		if (rangedResult != null && rangedResult.overallBest != null) {
			Loadout worn = rangedResult.overallBest.getLoadout();
			for (GearSlot slot : GearSlot.values()) {
				if (slot == GearSlot.WEAPON
					|| slot == GearSlot.SHIELD
					|| slot == GearSlot.AMMO) {
					continue;
				}
				GearItem item = worn.get(slot);
				if (item != null) {
					wornAcc += item.getOffensive().getRanged();
					wornStr += item.getBonuses().getRangedStrength();
				}
			}
		}
		Map<String, Object> node = map("station", station, "ammo", ammo, "ammoDetected", detect);
		if (ammoBlocked != null) { node.put("ammoBlocked", ammoBlocked); }
		node.put("wornAccuracy", wornAcc);
		node.put("wornStrength", wornStr);
		List<Map<String, Object>> cannonNodes = new ArrayList<>();
		boolean anyCrew = false;
		double total = 0;
		double[] byMob = new double[mobs.size()];
		for (int i = 0; i < count; i++) {
			String tier = carriedTiers.get(i);
			Cannon cannon = cannon(tier);
			if (cannon == null) { continue; }
			String operator = i == 0 ? op1 : op2;
			boolean playerFired = "you".equals(operator);
			int priv = PageState.crewLevel(operator);
			Map<String, Object> c = map("tier", tier, "itemId", cannon.itemId);
			if (ammo != null) {
				// Shared ammo, clamped to what THIS cannon can fire (a mithril
				// cannon handed dragon balls fires mithril ones).
				c.put("ball", ammo);
			}
			c.put("firedBy", playerFired ? "player" : "crew");
			// A dry bank blocks every cannon with the detection's reason (no
			// ball, no crew estimate); a loaded one is gated per operator -
			// the player's Ranged, the crew's Privateering.
			String blocked = ammo == null ? ammoBlocked
				: playerFired && rangedLevel < cannon.ranged ? "Needs " + cannon.ranged + " Ranged to operate" : null;
			if (!playerFired && ammo != null) {
				anyCrew = true;
				if (priv < cannon.privateering) { blocked = "Crew needs Privateering " + cannon.privateering; }
			}
			if (blocked != null) {
				c.put("blocked", blocked);
				c.put("maxHit", 0);
				c.put("dps", 0.0);
				cannonNodes.add(c);
				continue;
			}
			Ball ball = ball(ammo);
			int maxHit;
			if (playerFired) {
				maxHit = playerMaxHit(
					rangedLevel, cannon.strength, ball.strength, wornStr, false);
			} else {
				int base = playerMaxHit(
					sailingLevel, cannon.strength, ball.strength, 0, false);
				maxHit = crewMaxHit(base, priv);
			}
			int level = playerFired ? rangedLevel : sailingLevel;
			int acc = equipmentAccuracy(
				cannon.heavyAccuracy, ball.accuracy, playerFired ? wornAcc : 0);
			// One roll per roster mob (Andrew 2026-09-03: sea rows show
			// player + cannon): the max hit is shared, the accuracy is not.
			for (int m = 0; m < mobs.size(); m++) {
				MonsterStats target = mobs.get(m);
				if (isNaval(target.getName())) {
					byMob[m] += dps(maxHit,
						hitChance(level, acc,
							target.getDefence(), target.getDefensive().get("heavy")));
				}
			}
			double chance = hitChance(level, acc,
				lensed.getDefence(), lensed.getDefensive().get("heavy"));
			double dps = dps(maxHit, chance);
			c.put("maxHit", maxHit);
			c.put("dps", dps);
			total += dps;
			cannonNodes.add(c);
		}
		node.put("cannons", cannonNodes);
		node.put("dps", total);
		node.put("byMob", Arrays.stream(byMob).boxed().collect(Collectors.toList()));
		node.put("estimated", anyCrew && crewFormulaStale());
		// Ship damage taken (REQ-SC-15): the mob attacks the BOAT, so the
		// number is per keel from the wiki table (armour pre-applied) and
		// protection prayers do not apply. Always-hit is assumed - ship
		// defence rolls are undocumented - so this reads as a ceiling.
		String keel = String.valueOf(params.get("shipKeel"));
		int keelMax = keelMaxHit(lensed.getName(), keel);
		if (keelMax >= 0) {
			int speed = Math.max(1, lensed.getOffence().getSpeedTicks());
			node.put("incoming", map("keel", keel, "maxHit", keelMax, "dtps", keelMax / 2.0 / (speed * 0.6)));
		}
		return node;
	}

	private Map<String, Object> withHistory(Map<String, Object> page) {
		// Every publish, the pending page too: the path link needs only the
		// mob, never its results (ISS-01M5TE7QAZYZQMTPA1JZ0BQN1Z).
		page.put("pathable", pathable(shownMob()));
		page.put("history", map("canUndo", history.canUndo(), "canRedo", history.canRedo(),
			"undoLabel", history.peekUndoDescription(), "redoLabel", history.peekRedoDescription()));
		Supplier<Map<String, Object>> supplier = counts;
		if (supplier != null) { page.put("counts", supplier.get()); }
		return page;
	}
}
