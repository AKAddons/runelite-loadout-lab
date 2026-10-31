// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import static java.lang.Math.*;
import lombok.*;
import com.loadoutlab.data.*;
import java.util.*;

// An optimizer request. Effectively immutable: the with- helpers never
// mutate a live instance, they build a fresh copy with one field changed
// before it escapes - Lombok's {@code @With} (over the private all-args
// constructor) for the plain fields, {@link #copy()} for the ones that
// normalise their argument.
// <p>THREAD SAFETY: the fields are deliberately non-final so that
// {@link #copy()} (a single {@code Object.clone()}) can serve the
// normalising with- helpers in O(1) instead of an O(fields) hand-written mirror.
// Dropping {@code final} forfeits the JMM's final-field safe-publication
// guarantee, so publication must be established some other way. It is:
// requests are always built on the coordinating thread and reach worker
// threads through {@code ExecutorService.invokeAll} or a single-thread
// executor, both of which establish happens-before between the submitting
// and the executing thread. There is no {@code parallelStream}, ForkJoin,
// or {@code CompletableFuture} anywhere in src/main. If the optimizer
// ever adopts those, this constraint becomes load-bearing and the fields
// must go back to final (see DECISIONS.md).
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class OptimizationRequest implements Cloneable {
@With
public Monster monster;
@With
public Style style;
PlayerLevels levels;
// The assumed prayer bonuses; withPrayers overrides them (the
// assume-chip picker), the default is bestAvailable at the request's
// levels/unlocks.
@With
PrayerBonuses prayers;
@With
SpellStats spell;
int budget;
public CandidateMode candidateMode;
boolean includeUntradeables;
boolean onSlayerTask;
public OwnedItems ownedItems;
public RequirementProfile requirementProfile;
int resultLimit;
// Item ids the player has excluded ("protect my dragon darts") -
// never suggested in any slot, ammo pick, dart tier, or spec.
public Set<Integer> excludedItems = Set.of();
// Lock auto-spell selection to one spellbook ("standard"/"ancient"/
// "arceuus"); empty = any. Powered staves are unaffected.
String spellbookLock = "";
// Wilderness risk cap: at most this many tradeable items in the set
// (they become the items kept on death); -1 = unconstrained.
@With
int maxTradeables = -1;
// Wilderness risk budget in gp for THIS request (see
// DEFAULT_RISK_BUDGET_GP for the semantics); only consulted when
// risk-constrained.
@With
int riskBudgetGp = DEFAULT_RISK_BUDGET_GP;
// The defensive modes' frontier weight (ISS-01M5G5CZC9SK46499FACRSJ8ZT):
// the beam scores dps - defenseWeight * incoming dps; 0 = pure offence.
@With
double defenseWeight;
// Dragonfire monsters: true = assume a super antifire (no shield
// forced); false = protection must come from a shield.
@With
boolean antifirePotion;
// The page's antifireMode (0 none, 1 antifire, 2 super antifire): the
// curated dragonfire layers read it (Vorkath, ISS-01M5WVMVD4MZMWC7GRQDV3AFSY).
@With
int antifireTier;
// Rules the set must keep - a combat achievement's or the player's own
// (ISS-01M5XA7NVZYGVR3EVTM05XHXKP); null = none.
@With
public SetRules setRules;
// Sunfire runes in place of fire runes: a standard fire spell lands
// no lower than a tenth of its max hit (wiki Sunfire rune).
@With
boolean sunfireRunes;
// Targets stacked for the Multi card rows: a spec hits min(stack, its
// targets); 1 = single target.
public int multiStack = 1;
// Death Charge (Arceuus, Magic 80): 15% spec energy back on a killing
// blow per cast window - an extra energy stream for the spec model.
// 0 = off, 1 = base (60s window), 2 = upgraded by Yama's rite of vile
// transference (two refunds per cast = an effective 30s window).
@With
public int deathCharge;
// Dream items: unowned gear considered as owned.
Set<Integer> dreamItems = Set.of();
// Pinned items: slot -> item id the player ALWAYS brings (bracelet
// of slaughter class - value the model cannot price). A pinned slot
// has exactly one candidate; exclusions, mode, budget, and the risk
// vetoes all yield to the pin, while risk totals stay honest.
public Map<GearSlot, Integer> pinnedItems = Map.of();
// Items the player will bring ONLY if protected on death - a low-risk
// set that leaves one in the lost pile is vetoed (same as the salve-line
// friction veto, but user-chosen). Empty = no such constraint.
Set<Integer> protectOnlyItems = Set.of();
// Waiver keys that lift slayer requirements (RequiredGear.KOUREND_ELITE).
Set<String> waivers = Set.of();
// True when the fight happens IN the Wilderness - gates the wilderness
// weapon +50% passive. Defaults to whether the monster exists nowhere
// else (revs, the boss ring); shared-name monsters (Catacombs
// hellhounds...) take the user's panel toggle via the wither.
@With
boolean inWilderness;

public OptimizationRequest( Monster monster, Style style, PlayerLevels levels, PrayerBonuses prayers,
    SpellStats spell, int budget, CandidateMode candidateMode, boolean includeUntradeables, boolean onSlayerTask,
    OwnedItems ownedItems, RequirementProfile requirementProfile, int resultLimit) {
    this.monster = monster;
    this.style = style;
    this.levels = levels;
    this.prayers = prayers == null ? PrayerBonuses.NONE : prayers;
    this.spell = spell;
    this.budget = max(0, budget);
    this.candidateMode = candidateMode == null ? CandidateMode.BUDGET : candidateMode;
    this.includeUntradeables = includeUntradeables;
    this.onSlayerTask = onSlayerTask;
    inWilderness = WildernessMonsters.isExclusive(monster);
    this.ownedItems = ownedItems == null ? OwnedItems.EMPTY : ownedItems;
    this.requirementProfile = requirementProfile == null ? RequirementProfile.MAXED : requirementProfile;
    this.resultLimit = max(1, min(50, resultLimit));
}

// Copy-on-write core for the with- helpers: clone captures every field
// at once, the helper overwrites the one it changes, and the fresh
// instance is what escapes. Adding a field costs nothing here.
OptimizationRequest copy() {
    try {
        return (OptimizationRequest) super.clone();
    } catch (CloneNotSupportedException e) {
        // Unreachable: the class implements Cloneable.
        throw new AssertionError(e);
    }
}

// Null-normalisation for the set-valued withers (a null argument from
// the panel means "no constraint", never a null field). Both public
// constructors already set these non-null, so the withers are the only
// door a null can come through.
static <T> Set<T> orEmpty(Set<T> set) { return set == null ? Set.of() : set; }

public OptimizationRequest withProtectOnlyItems(Set<Integer> ids) {
    OptimizationRequest c = copy();
    c.protectOnlyItems = orEmpty(ids);
    return c;
}

public boolean isExcluded(int itemId) { return excludedItems.contains(itemId); }

public OptimizationRequest withExcludedItems(Set<Integer> excluded) {
    OptimizationRequest c = copy();
    c.excludedItems = orEmpty(excluded);
    return c;
}

public OptimizationRequest withSpellbookLock(String spellbook) {
    OptimizationRequest c = copy();
    c.spellbookLock = spellbook == null ? "" : spellbook;
    return c;
}

public OptimizationRequest withMultiStack(int multiStack) {
    OptimizationRequest c = copy();
    c.multiStack = max(1, multiStack);
    return c;
}

public boolean isRiskConstrained() { return maxTradeables >= 0; }

// Default wilderness risk budget: the TOTAL gp the set may drop on a
// PvP death. The kept 3-4 highest-value items are immune and free -
// bring your crystal set, they ARE the kept items - and everything
// worn beyond them (glory, black d'hide, mystic class...) must SUM
// to at most this. Not per item: one 70k amulet plus a 60k body
// blows the budget together. A 0 budget means nothing droppable and
// no fees at all: kept-slot items plus free untradeables only.
public static final int DEFAULT_RISK_BUDGET_GP = 75_000;

public OptimizationRequest withWaivers(Set<String> waivers) {
    OptimizationRequest c = copy();
    c.waivers = waivers == null ? Set.of() : waivers;
    return c;
}

public boolean isDream(int itemId) { return dreamItems.contains(itemId); }

public OptimizationRequest withDreamItems(Set<Integer> dreamItems) {
    OptimizationRequest c = copy();
    c.dreamItems = orEmpty(dreamItems);
    return c;
}

// The pinned item id for this slot, or null when unpinned.
public Integer pinnedFor(GearSlot slot) { return pinnedItems.get(slot); }

public boolean isPinned(int itemId) { return pinnedItems.containsValue(itemId); }

public OptimizationRequest withPinnedItems(Map<GearSlot, Integer> pinnedItems) {
    OptimizationRequest c = copy();
    c.pinnedItems = pinnedItems == null ? Map.of() : pinnedItems;
    return c;
}

public boolean isAutoSpell() { return spell == null; }

OptimizationRequest withSlayerTask(boolean onSlayerTask) {
    OptimizationRequest c = copy();
    c.onSlayerTask = onSlayerTask;
    return c;
}
}
