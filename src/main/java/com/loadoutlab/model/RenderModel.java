package com.loadoutlab.model;

import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.IncomingDpsCalculator;
import com.loadoutlab.optimizer.OptimizerService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The generic render-model of ADR-0008: everything a UI needs to draw,
 * as JSON-safe maps (the Companion loads in its OWN classloader - no
 * shared classes cross the seam, only this structure). Renderers - the
 * Companion's rich panel, Core's bare text surface, any future UI -
 * consume this and never re-derive game logic.
 *
 * <p>v1 is the vertical slice straight off the optimizer's results:
 * per-mob per-style cards for both sides, spec, incoming, bench.
 * Entry parameter state, chips, and the pre-resolved display strings
 * join field-by-field as the panel port peels them out of Swing
 * (docs/COMPANION_CONTRACT.md tracks the full shape).
 */
public final class RenderModel
{
	/** Contract version stamped on every page; additive evolution only. */
	public static final int VERSION = 1;

	private RenderModel()
	{
	}

	public static Map<String, Object> page(List<Map<String, Object>> entries)
	{
		Map<String, Object> page = new LinkedHashMap<>();
		page.put("v", VERSION);
		page.put("entries", entries);
		return page;
	}

	/** One result entry: its mobs and, per mob, the per-style cards.
	 * Single-mob pages are the one-mob case of the same shape. */
	public static Map<String, Object> entry(List<MonsterStats> mobs,
		List<Map<CombatStyle, OptimizerService.StyleResult>> perMob)
	{
		Map<String, Object> entry = new LinkedHashMap<>();
		List<Object> mobNodes = new ArrayList<>();
		for (int i = 0; i < mobs.size(); i++)
		{
			Map<String, Object> node = mob(mobs.get(i));
			node.put("styles", styles(perMob.get(i)));
			mobNodes.add(node);
		}
		entry.put("mobs", mobNodes);
		return entry;
	}

	private static Map<String, Object> mob(MonsterStats mob)
	{
		Map<String, Object> node = new LinkedHashMap<>();
		node.put("id", mob.getId());
		node.put("profileId", mob.profileId());
		node.put("name", mob.getName());
		node.put("label", mob.label());
		node.put("hp", mob.getHitpoints());
		node.put("invocationScaled",
			com.loadoutlab.engine.MonsterMechanics.isToaInvocationScaled(mob));
		return node;
	}

	private static Map<String, Object> styles(Map<CombatStyle, OptimizerService.StyleResult> results)
	{
		Map<String, Object> styles = new LinkedHashMap<>();
		for (CombatStyle style : CombatStyle.concreteValues())
		{
			OptimizerService.StyleResult result = results == null ? null : results.get(style);
			if (result == null)
			{
				continue;
			}
			Map<String, Object> node = new LinkedHashMap<>();
			node.put("yours", card(result, false));
			node.put("bis", card(result, true));
			node.put("boostLabel", result.boostLabel);
			node.put("bisBoostLabel", result.gameBoostLabel);
			styles.put(style.name().toLowerCase(), node);
		}
		return styles;
	}

	/** One side's card: the shown set and everything rendered around it. */
	static Map<String, Object> card(OptimizerService.StyleResult result, boolean bis)
	{
		DpsResult shown = bis ? result.overallBest
			: result.owned == null || result.owned.isEmpty() ? null : result.owned.get(0);
		if (shown == null)
		{
			return null;
		}
		Map<String, Object> card = new LinkedHashMap<>();
		card.put("dps", shown.getDps());
		card.put("maxHit", shown.getMaxHit());
		card.put("accuracy", shown.getAccuracy());
		card.put("attackType", shown.getAttackType());
		card.put("spell", shown.getSpellName());
		card.put("purchaseCost", shown.getPurchaseCost());
		card.put("antifireAssumed", shown.isAntifireAssumed());
		card.put("counted", new ArrayList<>(shown.getCountedBonuses()));
		Map<String, Object> gear = new LinkedHashMap<>();
		for (GearSlot slot : GearSlot.values())
		{
			GearItem item = shown.getLoadout().get(slot);
			if (item != null)
			{
				gear.put(slot.name().toLowerCase(), item(item));
			}
		}
		card.put("gear", gear);
		card.put("quiverAmmo", item(shown.getLoadout().getQuiverAmmo()));
		card.put("spec", spec(result, bis));
		card.put("incoming", incoming(bis ? result.gameIncoming : result.incoming));
		card.put("bench", items(bis ? result.gameBench : result.bench));
		card.put("kitBacked", bis ? result.gameKitBacked : result.ownedKitBacked);
		return card;
	}

	private static Map<String, Object> spec(OptimizerService.StyleResult result, boolean bis)
	{
		GearItem weapon = bis ? result.gameSpecWeapon : result.specWeapon;
		if (weapon == null)
		{
			return null;
		}
		Map<String, Object> node = new LinkedHashMap<>();
		node.put("weapon", item(weapon));
		node.put("dpsAdded", bis ? result.gameSpecDpsAdded : result.specDpsAdded);
		node.put("expectedDamage", bis ? result.gameSpecExpectedDamage : result.specExpectedDamage);
		return node;
	}

	private static Map<String, Object> incoming(IncomingDpsCalculator.Result incoming)
	{
		if (incoming == null)
		{
			return null;
		}
		Map<String, Object> node = new LinkedHashMap<>();
		node.put("dps", incoming.totalDps);
		node.put("unprayedDps", incoming.unprayedDps);
		node.put("protectPrayer", incoming.protectPrayer);
		node.put("fullyModeled", incoming.fullyModeled);
		node.put("overrideNote", incoming.overrideNote);
		List<Object> threats = new ArrayList<>();
		for (IncomingDpsCalculator.StyleThreat threat : incoming.threats)
		{
			Map<String, Object> t = new LinkedHashMap<>();
			t.put("style", threat.style);
			t.put("dps", threat.dps);
			t.put("maxHit", threat.maxHit);
			t.put("modeled", threat.modeled);
			t.put("blocked", threat.blocked);
			t.put("prayerFactor", threat.prayerFactor);
			threats.add(t);
		}
		node.put("threats", threats);
		return node;
	}

	private static List<Object> items(List<GearItem> items)
	{
		List<Object> nodes = new ArrayList<>();
		if (items != null)
		{
			for (GearItem item : items)
			{
				nodes.add(item(item));
			}
		}
		return nodes;
	}

	private static Map<String, Object> item(GearItem item)
	{
		if (item == null)
		{
			return null;
		}
		Map<String, Object> node = new LinkedHashMap<>();
		node.put("id", item.getId());
		node.put("name", item.label());
		return node;
	}
}
