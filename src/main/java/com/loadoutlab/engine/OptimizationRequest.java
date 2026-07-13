// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.engine;

import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.data.SpellStats;
import java.util.Collections;
import java.util.Map;
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
	/** Wilderness risk budget in gp for THIS request (see
	 * DEFAULT_RISK_BUDGET_GP for the semantics); only consulted when
	 * risk-constrained. */
	private final int riskBudgetGp;
	/** Dragonfire monsters: true = assume a super antifire (no shield
	 * forced); false = protection must come from a shield. */
	private final boolean antifirePotion;
	/** Dream items: unowned gear considered as owned. */
	private final Set<Integer> dreamItems;
	/** D-4 frontier: beam score = dps - defenseWeight * incoming dps;
	 * 0 = pure offense (default), higher trades damage for safety. */
	private final double defenseWeight;
	/** Pinned items: slot -> item id the player ALWAYS brings (bracelet
	 * of slaughter class - value the model cannot price). A pinned slot
	 * has exactly one candidate; exclusions, mode, budget, and the risk
	 * vetoes all yield to the pin, while risk totals stay honest. */
	private final Map<GearSlot, Integer> pinnedItems;

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
		this.riskBudgetGp = DEFAULT_RISK_BUDGET_GP;
		this.antifirePotion = false;
		this.dreamItems = Collections.emptySet();
		this.defenseWeight = 0;
		this.pinnedItems = Collections.emptyMap();
		this.ownedItems = ownedItems == null ? OwnedItems.EMPTY : ownedItems;
		this.requirementProfile = requirementProfile == null ? RequirementProfile.MAXED : requirementProfile;
		this.resultLimit = Math.max(1, Math.min(50, resultLimit));
	}

	/**
	 * Copy-on-write core for the with- helpers: a Copy captures every
	 * field of a base request, the helper overwrites the one it changes,
	 * and build() re-normalizes exactly what the old copy constructor
	 * normalized (null lock/sets). Adding a field means one line here and
	 * one in the constructor below - not re-threading every helper.
	 */
	private static final class Copy
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
		private Set<Integer> excludedItems;
		private String spellbookLock;
		private int maxTradeables;
		private int riskBudgetGp;
		private boolean antifirePotion;
		private Set<Integer> dreamItems;
		private double defenseWeight;
		private Map<GearSlot, Integer> pinnedItems;

		private Copy(OptimizationRequest base)
		{
			monster = base.monster;
			style = base.style;
			levels = base.levels;
			prayers = base.prayers;
			spell = base.spell;
			budget = base.budget;
			candidateMode = base.candidateMode;
			includeUntradeables = base.includeUntradeables;
			onSlayerTask = base.onSlayerTask;
			ownedItems = base.ownedItems;
			requirementProfile = base.requirementProfile;
			resultLimit = base.resultLimit;
			excludedItems = base.excludedItems;
			spellbookLock = base.spellbookLock;
			maxTradeables = base.maxTradeables;
			riskBudgetGp = base.riskBudgetGp;
			antifirePotion = base.antifirePotion;
			dreamItems = base.dreamItems;
			defenseWeight = base.defenseWeight;
			pinnedItems = base.pinnedItems;
		}

		private OptimizationRequest build()
		{
			return new OptimizationRequest(this);
		}
	}

	private OptimizationRequest(Copy copy)
	{
		this.monster = copy.monster;
		this.style = copy.style;
		this.levels = copy.levels;
		this.prayers = copy.prayers;
		this.spell = copy.spell;
		this.budget = copy.budget;
		this.candidateMode = copy.candidateMode;
		this.includeUntradeables = copy.includeUntradeables;
		this.onSlayerTask = copy.onSlayerTask;
		this.ownedItems = copy.ownedItems;
		this.requirementProfile = copy.requirementProfile;
		this.resultLimit = copy.resultLimit;
		this.excludedItems = copy.excludedItems == null ? Collections.emptySet() : copy.excludedItems;
		this.spellbookLock = copy.spellbookLock == null ? "" : copy.spellbookLock;
		this.maxTradeables = copy.maxTradeables;
		this.riskBudgetGp = copy.riskBudgetGp;
		this.antifirePotion = copy.antifirePotion;
		this.dreamItems = copy.dreamItems == null ? Collections.emptySet() : copy.dreamItems;
		this.defenseWeight = copy.defenseWeight;
		this.pinnedItems = copy.pinnedItems == null ? Collections.emptyMap() : copy.pinnedItems;
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
		Copy copy = new Copy(this);
		copy.excludedItems = excluded;
		return copy.build();
	}

	public String getSpellbookLock()
	{
		return spellbookLock;
	}

	public OptimizationRequest withSpellbookLock(String spellbook)
	{
		Copy copy = new Copy(this);
		copy.spellbookLock = spellbook;
		return copy.build();
	}

	public boolean isAntifirePotion()
	{
		return antifirePotion;
	}

	public OptimizationRequest withAntifirePotion(boolean antifirePotion)
	{
		Copy copy = new Copy(this);
		copy.antifirePotion = antifirePotion;
		return copy.build();
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
		Copy copy = new Copy(this);
		copy.riskBudgetGp = riskBudgetGp;
		return copy.build();
	}

	public OptimizationRequest withMaxTradeables(int maxTradeables)
	{
		Copy copy = new Copy(this);
		copy.maxTradeables = maxTradeables;
		return copy.build();
	}

	public boolean isDream(int itemId)
	{
		return dreamItems.contains(itemId);
	}

	public OptimizationRequest withDreamItems(Set<Integer> dreamItems)
	{
		Copy copy = new Copy(this);
		copy.dreamItems = dreamItems;
		return copy.build();
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
		Copy copy = new Copy(this);
		copy.pinnedItems = pinnedItems;
		return copy.build();
	}

	public double getDefenseWeight()
	{
		return defenseWeight;
	}

	public OptimizationRequest withDefenseWeight(double defenseWeight)
	{
		Copy copy = new Copy(this);
		copy.defenseWeight = defenseWeight;
		return copy.build();
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
		Copy copy = new Copy(this);
		copy.style = style;
		return copy.build();
	}

	public OptimizationRequest withMonster(MonsterStats monster)
	{
		Copy copy = new Copy(this);
		copy.monster = monster;
		return copy.build();
	}

	public OptimizationRequest withSlayerTask(boolean onSlayerTask)
	{
		Copy copy = new Copy(this);
		copy.onSlayerTask = onSlayerTask;
		return copy.build();
	}

	public OptimizationRequest withSpell(SpellStats spell)
	{
		Copy copy = new Copy(this);
		copy.spell = spell;
		return copy.build();
	}
}
