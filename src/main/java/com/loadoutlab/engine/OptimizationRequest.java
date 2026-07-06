// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellStats;

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
		this.ownedItems = ownedItems == null ? OwnedItems.EMPTY : ownedItems;
		this.requirementProfile = requirementProfile == null ? RequirementProfile.MAXED : requirementProfile;
		this.resultLimit = Math.max(1, Math.min(50, resultLimit));
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
		return new OptimizationRequest(
			monster,
			style,
			levels,
			prayers,
			spell,
			budget,
			candidateMode,
			includeUntradeables,
			onSlayerTask,
			ownedItems,
			requirementProfile,
			resultLimit);
	}

	public OptimizationRequest withMonster(com.loadoutlab.data.MonsterStats monster)
	{
		return new OptimizationRequest(
			monster,
			style,
			levels,
			prayers,
			spell,
			budget,
			candidateMode,
			includeUntradeables,
			onSlayerTask,
			ownedItems,
			requirementProfile,
			resultLimit);
	}

	public OptimizationRequest withSpell(SpellStats spell)
	{
		return new OptimizationRequest(
			monster,
			style,
			levels,
			prayers,
			spell,
			budget,
			candidateMode,
			includeUntradeables,
			onSlayerTask,
			ownedItems,
			requirementProfile,
			resultLimit);
	}
}
