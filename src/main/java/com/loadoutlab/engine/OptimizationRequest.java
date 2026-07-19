// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellStats;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * An optimizer request. Effectively immutable: the with- helpers never
 * mutate a live instance, they clone and overwrite one field on the fresh
 * copy before it escapes.
 *
 * <p>THREAD SAFETY: the fields are deliberately non-final so that
 * {@link #copy()} (a single {@code Object.clone()}) can serve all 14
 * with- helpers in O(1) instead of an O(fields) hand-written mirror.
 * Dropping {@code final} forfeits the JMM's final-field safe-publication
 * guarantee, so publication must be established some other way. It is:
 * requests are always built on the coordinating thread and reach worker
 * threads through {@code ExecutorService.invokeAll} or a single-thread
 * executor, both of which establish happens-before between the submitting
 * and the executing thread. There is no {@code parallelStream}, ForkJoin,
 * or {@code CompletableFuture} anywhere in src/main. If the optimizer
 * ever adopts those, this constraint becomes load-bearing and the fields
 * must go back to final (see DECISIONS.md).
 */
public final class OptimizationRequest implements Cloneable
{
	private MonsterStats monster;
	private CombatStyle style;
	private PlayerLevels levels;
	private PrayerBonuses prayers;
	private SpellStats spell;
	private int budget;
	private CandidateMode candidateMode;
	private boolean includeUntradeables;
	private boolean onSlayerTask;
	private OwnedItems ownedItems;
	private RequirementProfile requirementProfile;
	private int resultLimit;
	/** Item ids the player has excluded ("protect my dragon darts") -
	 * never suggested in any slot, ammo pick, dart tier, or spec. */
	private Set<Integer> excludedItems;
	/** Lock auto-spell selection to one spellbook ("standard"/"ancient"/
	 * "arceuus"); empty = any. Powered staves are unaffected. */
	private String spellbookLock;
	/** Wilderness risk cap: at most this many tradeable items in the set
	 * (they become the items kept on death); -1 = unconstrained. */
	private int maxTradeables;
	/** Wilderness risk budget in gp for THIS request (see
	 * DEFAULT_RISK_BUDGET_GP for the semantics); only consulted when
	 * risk-constrained. */
	private int riskBudgetGp;
	/** Dragonfire monsters: true = assume a super antifire (no shield
	 * forced); false = protection must come from a shield. */
	private boolean antifirePotion;
	/** Dream items: unowned gear considered as owned. */
	private Set<Integer> dreamItems;
	/** D-4 frontier: beam score = dps - defenseWeight * incoming dps;
	 * 0 = pure offense (default), higher trades damage for safety. */
	private double defenseWeight;
	/** Pinned items: slot -> item id the player ALWAYS brings (bracelet
	 * of slaughter class - value the model cannot price). A pinned slot
	 * has exactly one candidate; exclusions, mode, budget, and the risk
	 * vetoes all yield to the pin, while risk totals stay honest. */
	private Map<GearSlot, Integer> pinnedItems;
	/** Items the player will bring ONLY if protected on death - a low-risk
	 * set that leaves one in the lost pile is vetoed (same as the salve-line
	 * friction veto, but user-chosen). Empty = no such constraint. */
	private Set<Integer> protectOnlyItems;
	/** True when the fight happens IN the Wilderness - gates the wilderness
	 * weapon +50% passive. Defaults to whether the monster exists nowhere
	 * else (revs, the boss ring); shared-name monsters (Catacombs
	 * hellhounds...) take the user's panel toggle via the wither. */
	private boolean inWilderness;

	public OptimizationRequest(
		MonsterStats monster,
		CombatStyle style,
		PlayerLevels levels,
		PrayerBonuses prayers,
		SpellStats spell,
		int budget,
		CandidateMode candidateMode,
		boolean includeUntradeables,
		boolean onSlayerTask,
		OwnedItems ownedItems,
		RequirementProfile requirementProfile,
		int resultLimit)
	{
		this.monster = monster;
		this.style = style;
		this.levels = levels;
		this.prayers = prayers == null ? PrayerBonuses.NONE : prayers;
		this.spell = spell;
		this.budget = Math.max(0, budget);
		this.candidateMode = candidateMode == null ? CandidateMode.BUDGET : candidateMode;
		this.includeUntradeables = includeUntradeables;
		this.onSlayerTask = onSlayerTask;
		this.excludedItems = Collections.emptySet();
		this.spellbookLock = "";
		this.maxTradeables = -1;
		this.riskBudgetGp = DEFAULT_RISK_BUDGET_GP;
		this.antifirePotion = false;
		this.dreamItems = Collections.emptySet();
		this.defenseWeight = 0;
		this.pinnedItems = Collections.emptyMap();
		this.protectOnlyItems = Collections.emptySet();
		this.inWilderness = com.loadoutlab.data.WildernessMonsters.isExclusive(monster);
		this.ownedItems = ownedItems == null ? OwnedItems.EMPTY : ownedItems;
		this.requirementProfile = requirementProfile == null ? RequirementProfile.MAXED : requirementProfile;
		this.resultLimit = Math.max(1, Math.min(50, resultLimit));
	}

	/**
	 * Copy-on-write core for the with- helpers: clone captures every field
	 * at once, the helper overwrites the one it changes, and the fresh
	 * instance is what escapes. Adding a field costs nothing here.
	 */
	private OptimizationRequest copy()
	{
		try
		{
			return (OptimizationRequest) super.clone();
		}
		catch (CloneNotSupportedException e)
		{
			// Unreachable: the class implements Cloneable.
			throw new AssertionError(e);
		}
	}

	/** Null-normalisation for the set-valued withers (a null argument from
	 * the panel means "no constraint", never a null field). Both public
	 * constructors already set these non-null, so the withers are the only
	 * door a null can come through. */
	private static <T> Set<T> orEmpty(Set<T> set)
	{
		return set == null ? Collections.emptySet() : set;
	}

	public Set<Integer> getExcludedItems()
	{
		return excludedItems;
	}

	/** Items to bring only when protected on death (see the field doc). */
	public Set<Integer> getProtectOnlyItems()
	{
		return protectOnlyItems;
	}

	public OptimizationRequest withProtectOnlyItems(Set<Integer> ids)
	{
		OptimizationRequest c = copy();
		c.protectOnlyItems = orEmpty(ids);
		return c;
	}

	public boolean isExcluded(int itemId)
	{
		return excludedItems.contains(itemId);
	}

	public OptimizationRequest withExcludedItems(Set<Integer> excluded)
	{
		OptimizationRequest c = copy();
		c.excludedItems = orEmpty(excluded);
		return c;
	}

	public String getSpellbookLock()
	{
		return spellbookLock;
	}

	public OptimizationRequest withSpellbookLock(String spellbook)
	{
		OptimizationRequest c = copy();
		c.spellbookLock = spellbook == null ? "" : spellbook;
		return c;
	}

	public boolean isAntifirePotion()
	{
		return antifirePotion;
	}

	public OptimizationRequest withAntifirePotion(boolean antifirePotion)
	{
		OptimizationRequest c = copy();
		c.antifirePotion = antifirePotion;
		return c;
	}

	public int getMaxTradeables()
	{
		return maxTradeables;
	}

	public boolean isRiskConstrained()
	{
		return maxTradeables >= 0;
	}

	/**
	 * Default wilderness risk budget: the TOTAL gp the set may drop on a
	 * PvP death. The kept 3-4 highest-value items are immune and free -
	 * bring your crystal set, they ARE the kept items - and everything
	 * worn beyond them (glory, black d'hide, mystic class...) must SUM
	 * to at most this. Not per item: one 70k amulet plus a 60k body
	 * blows the budget together. A 0 budget means nothing droppable and
	 * no fees at all: kept-slot items plus free untradeables only.
	 */
	public static final int DEFAULT_RISK_BUDGET_GP = 75_000;

	public int getRiskBudgetGp()
	{
		return riskBudgetGp;
	}

	public OptimizationRequest withRiskBudgetGp(int riskBudgetGp)
	{
		OptimizationRequest c = copy();
		c.riskBudgetGp = riskBudgetGp;
		return c;
	}

	public OptimizationRequest withMaxTradeables(int maxTradeables)
	{
		OptimizationRequest c = copy();
		c.maxTradeables = maxTradeables;
		return c;
	}

	public boolean isDream(int itemId)
	{
		return dreamItems.contains(itemId);
	}

	public OptimizationRequest withDreamItems(Set<Integer> dreamItems)
	{
		OptimizationRequest c = copy();
		c.dreamItems = orEmpty(dreamItems);
		return c;
	}

	public Map<GearSlot, Integer> getPinnedItems()
	{
		return pinnedItems;
	}

	/** The pinned item id for this slot, or null when unpinned. */
	public Integer pinnedFor(GearSlot slot)
	{
		return pinnedItems.get(slot);
	}

	public boolean isPinned(int itemId)
	{
		return pinnedItems.containsValue(itemId);
	}

	public OptimizationRequest withPinnedItems(Map<GearSlot, Integer> pinnedItems)
	{
		OptimizationRequest c = copy();
		c.pinnedItems = pinnedItems == null ? Collections.emptyMap() : pinnedItems;
		return c;
	}

	public double getDefenseWeight()
	{
		return defenseWeight;
	}

	public OptimizationRequest withDefenseWeight(double defenseWeight)
	{
		OptimizationRequest c = copy();
		c.defenseWeight = defenseWeight;
		return c;
	}

	public MonsterStats getMonster()
	{
		return monster;
	}

	public CombatStyle getStyle()
	{
		return style;
	}

	public PlayerLevels getLevels()
	{
		return levels;
	}

	public PrayerBonuses getPrayers()
	{
		return prayers;
	}

	public SpellStats getSpell()
	{
		return spell;
	}

	public boolean isAutoSpell()
	{
		return spell == null;
	}

	public int getBudget()
	{
		return budget;
	}

	public CandidateMode getCandidateMode()
	{
		return candidateMode;
	}

	public boolean isIncludeUntradeables()
	{
		return includeUntradeables;
	}

	public boolean isOnSlayerTask()
	{
		return onSlayerTask;
	}

	public OwnedItems getOwnedItems()
	{
		return ownedItems;
	}

	public RequirementProfile getRequirementProfile()
	{
		return requirementProfile;
	}

	public int getResultLimit()
	{
		return resultLimit;
	}

	public OptimizationRequest withStyle(CombatStyle style)
	{
		OptimizationRequest c = copy();
		c.style = style;
		return c;
	}

	public OptimizationRequest withMonster(MonsterStats monster)
	{
		OptimizationRequest c = copy();
		c.monster = monster;
		return c;
	}

	public OptimizationRequest withSlayerTask(boolean onSlayerTask)
	{
		OptimizationRequest c = copy();
		c.onSlayerTask = onSlayerTask;
		return c;
	}

	public OptimizationRequest withSpell(SpellStats spell)
	{
		OptimizationRequest c = copy();
		c.spell = spell;
		return c;
	}


	public boolean isInWilderness()
	{
		return inWilderness;
	}

	/** Override the wilderness default (the panel toggle for shared-name
	 * monsters like Catacombs hellhounds). */
	public OptimizationRequest withInWilderness(boolean inWilderness)
	{
		OptimizationRequest c = copy();
		c.inWilderness = inWilderness;
		return c;
	}
}
