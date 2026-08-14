package com.loadoutlab.model;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.optimizer.OptimizerService;
import java.util.List;
import java.util.Map;

/**
 * The copy-report for the hosted view, built CORE-SIDE at page
 * assembly (the report is the QA loop's contract; renderers only put
 * it on the clipboard). Same vocabulary as the classic report; the
 * hosted format is marked so a pasted report says which surface it
 * came from. Grows toward full classic fidelity as PageState grows.
 */
final class ReportBuilder
{
	private ReportBuilder()
	{
	}

	static String build(String version, PageState state, List<MonsterStats> mobs,
		List<Map<CombatStyle, OptimizerService.StyleResult>> perMob, Map<String, Object> counts,
		Map<String, Object> thralls)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Loadout Lab data (v").append(version).append(", hosted view)\n");
		for (int i = 0; i < mobs.size(); i++)
		{
			MonsterStats mob = mobs.get(i);
			sb.append(mobs.size() == 1 ? "Target: " : "Mob: ").append(mob.label())
				.append(" - ").append(mob.getHitpoints()).append(" hp\n");
			if (i == 0)
			{
				appendParams(sb, state, counts);
				if (thralls != null)
				{
					sb.append("  Thralls: ").append(thralls.get("tier"))
						.append(String.format(" (dps shown includes it: %.2f)%n",
							((Number) thralls.get("dps")).doubleValue()));
				}
			}
			Map<CombatStyle, OptimizerService.StyleResult> results = perMob.get(i);
			// The kit contract (field report 2026-08-14, the Sire: "a
			// multi-mob result needs to return 1 set of gear and the
			// recommendations can't deviate"): full sets print ONLY for
			// KIT-BACKED styles - what the shared trip actually
			// assembles. Everything else compresses to the classic
			// one-liner so an informational view never reads as a
			// recommendation. Single-mob results are kit-backed by
			// definition and print exactly as before.
			StringBuilder others = new StringBuilder();
			for (CombatStyle style : CombatStyle.concreteValues())
			{
				OptimizerService.StyleResult result = results == null ? null : results.get(style);
				if (result == null)
				{
					continue;
				}
				if (result.ownedKitBacked || result.gameKitBacked)
				{
					sb.append("-- ").append(style).append(" --\n");
					if (result.ownedKitBacked)
					{
						appendSide(sb, "Yours", result, false);
					}
					if (result.gameKitBacked)
					{
						appendSide(sb, "Best in game", result, true);
					}
				}
				if (!result.ownedKitBacked && result.owned != null && !result.owned.isEmpty())
				{
					others.append(others.length() == 0 ? "" : " ")
						.append(style).append(String.format(" %.2f;",
							result.owned.get(0).getDps()));
				}
			}
			if (others.length() > 0)
			{
				sb.append("Other styles (your best dps, not part of the trip kit): ")
					.append(others).append('\n');
			}
		}
		return sb.toString();
	}

	private static void appendParams(StringBuilder sb, PageState state, Map<String, Object> counts)
	{
		Map<String, Object> params = state.paramsNode();
		String tab = String.valueOf(params.getOrDefault("selectedTab", ""));
		sb.append("Viewing: ").append(tab.isEmpty() ? "auto" : tab)
			.append(" / ").append(Boolean.TRUE.equals(params.get("viewingBis")) ? "BiS" : "Yours")
			.append('\n');
		sb.append("Parameters:\n");
		sb.append("  On task: ").append(yesNo(params.get("onTask")));
		sb.append("; Wilderness: ").append(yesNo(params.get("inWilderness")));
		sb.append("; Spec weapon: ").append(yesNo(params.get("specWeapon")));
		Object af = params.get("antifireMode");
		int afMode = af instanceof Number ? ((Number) af).intValue() : 0;
		sb.append("; Antifire: ").append(afMode == 2 ? "super" : afMode == 1 ? "regular" : "no");
		sb.append("; Raid boost: ").append(yesNo(params.get("raidBoost"))).append('\n');
		sb.append("  Inventory: ").append(params.get("maxSwaps")).append('\n');
		sb.append("  Death charge: ").append(params.get("deathCharge"));
		sb.append("; Invocation: ").append(params.get("toaInvocation"));
		Object lock = params.get("spellbookLock");
		sb.append("; Spellbook lock: ").append(lock == null || String.valueOf(lock).isEmpty()
			? "auto" : lock);
		sb.append("; Upgrade budget: ").append(params.get("upgradeBudgetGp")).append('\n');
		if (counts != null)
		{
			sb.append("  Stores: ").append(counts.getOrDefault("excluded", 0)).append(" excluded, ")
				.append(counts.getOrDefault("simmed", 0)).append(" simmed, ")
				.append(counts.getOrDefault("stored", 0)).append(" stored elsewhere\n");
			Object names = counts.get("simmedNames");
			if (names instanceof List && !((List<?>) names).isEmpty())
			{
				sb.append("  Simmed as owned:");
				for (Object name : (List<?>) names)
				{
					sb.append(' ').append(name).append(',');
				}
				sb.append('\n');
			}
		}
	}

	private static void appendSide(StringBuilder sb, String caption,
		OptimizerService.StyleResult result, boolean bis)
	{
		DpsResult shown = bis ? result.overallBest
			: result.owned == null || result.owned.isEmpty() ? null : result.owned.get(0);
		if (shown == null)
		{
			sb.append(caption).append(": no usable set\n");
			return;
		}
		double specAdded = bis ? result.gameSpecDpsAdded : result.specDpsAdded;
		GearItem specWeapon = bis ? result.gameSpecWeapon : result.specWeapon;
		if (specWeapon != null && specAdded > 0.0005)
		{
			sb.append(String.format("%s: %.2f dps as shown = %.2f set + %.2f spec%n",
				caption, shown.getDps() + specAdded, shown.getDps(), specAdded));
		}
		else
		{
			sb.append(String.format("%s: %.2f dps%n", caption, shown.getDps()));
		}
		sb.append(" ");
		for (GearSlot slot : GearSlot.values())
		{
			GearItem item = shown.getLoadout().get(slot);
			if (item != null)
			{
				sb.append(' ').append(item.label()).append(',');
			}
		}
		if (shown.getLoadout().getQuiverAmmo() != null)
		{
			sb.append(" [quiver: ").append(shown.getLoadout().getQuiverAmmo().label()).append(']');
		}
		sb.append('\n');
		String boost = bis ? result.gameBoostLabel : result.boostLabel;
		if (boost != null)
		{
			sb.append("  Assumes: ").append(boost).append('\n');
		}
		if (shown.getSpellName() != null)
		{
			sb.append("  Spell: ").append(shown.getSpellName()).append('\n');
		}
		if (specWeapon != null)
		{
			sb.append(String.format("  Spec: %s (adds ~%.2f dps)%n", specWeapon.label(), specAdded));
		}
	}

	private static String yesNo(Object value)
	{
		return Boolean.TRUE.equals(value) ? "yes" : "no";
	}
}
