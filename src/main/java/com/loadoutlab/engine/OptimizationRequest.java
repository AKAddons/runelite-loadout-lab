// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellStats;
import java.util.Collections;
import java.util.Set;

public final class OptimizationRequest
{
	private final MonsterStats monster;
	private final CombatStyle style;
	private final PlayerLevels levels;
	private final PrayerBonuses prayers;
	private final SpellStats spell;
	private final int budget;
	private final CandidateMode candidateMode;
	private final boolean includeUntradeables;
	private final boolean onSlayerTask;
	private final OwnedItems ownedItems;
	private final RequirementProfile requirementProfile;
	private final int resultLimit;
	/** Item ids the player has excluded ("protect my dragon darts") -
	 * never suggested in any slot, ammo pick, dart tier, or spec. */
	private final Set<Integer> excludedItems;
	/** Lock auto-spell selection to one spellbook ("standard"/"ancient"/
	 * "arceuus"); empty = any. Powered staves are unaffected. */
	private final String spellbookLock;
	/** Wilderness risk cap: at most this many tradeable items in the set
	 * (they become the items kept on death); -1 = unconstrained. */
	private final int maxTradeables;

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
		int resultLimit)
	{
		this(monster, style, levels, prayers, spell, budget, candidateMode, includeUntradeables, onSlayerTask, ownedItems, RequirementProfile.MAXED, resultLimit);
	}

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
		this.ownedItems = ownedItems == null ? OwnedItems.EMPTY : ownedItems;
		this.requirementProfile = requirementProfile == null ? RequirementProfile.MAXED : requirementProfile;
		this.resultLimit = Math.max(1, Math.min(50, resultLimit));
	}

	private OptimizationRequest(OptimizationRequest base, MonsterStats monster, CombatStyle style,
		SpellStats spell, Set<Integer> excludedItems)
	{
		this(base, monster, style, spell, excludedItems, base.spellbookLock, base.maxTradeables);
	}

	private OptimizationRequest(OptimizationRequest base, MonsterStats monster, CombatStyle style,
		SpellStats spell, Set<Integer> excludedItems, String spellbookLock, int maxTradeables)
	{
		this.spellbookLock = spellbookLock == null ? "" : spellbookLock;
		this.maxTradeables = maxTradeables;
		this.monster = monster;
		this.style = style;
		this.levels = base.levels;
		this.prayers = base.prayers;
		this.spell = spell;
		this.budget = base.budget;
		this.candidateMode = base.candidateMode;
		this.includeUntradeables = base.includeUntradeables;
		this.onSlayerTask = base.onSlayerTask;
		this.ownedItems = base.ownedItems;
		this.requirementProfile = base.requirementProfile;
		this.resultLimit = base.resultLimit;
		this.excludedItems = excludedItems == null ? Collections.emptySet() : excludedItems;
	}

	public Set<Integer> getExcludedItems()
	{
		return excludedItems;
	}

	public boolean isExcluded(int itemId)
	{
		return excludedItems.contains(itemId);
	}

	public OptimizationRequest withExcludedItems(Set<Integer> excluded)
	{
		return new OptimizationRequest(this, monster, style, spell, excluded);
	}

	public String getSpellbookLock()
	{
		return spellbookLock;
	}

	public OptimizationRequest withSpellbookLock(String spellbook)
	{
		return new OptimizationRequest(this, monster, style, spell, excludedItems, spellbook, maxTradeables);
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
	 * Wilderness risk budget: the TOTAL gp the set may drop on a PvP
	 * death. The kept 3-4 highest-value items are immune and free -
	 * bring your crystal set, they ARE the kept items - and everything
	 * worn beyond them (glory, black d'hide, mystic class...) must SUM
	 * to at most this. Not per item: one 70k amulet plus a 60k body
	 * blows the budget together.
	 */
	public static final int RISK_BUDGET_GP = 75_000;

	public OptimizationRequest withMaxTradeables(int maxTradeables)
	{
		return new OptimizationRequest(this, monster, style, spell, excludedItems, spellbookLock, maxTradeables);
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
		return new OptimizationRequest(this, monster, style, spell, excludedItems);
	}

	public OptimizationRequest withMonster(MonsterStats monster)
	{
		return new OptimizationRequest(this, monster, style, spell, excludedItems);
	}

	public OptimizationRequest withSpell(SpellStats spell)
	{
		return new OptimizationRequest(this, monster, style, spell, excludedItems);
	}
}
