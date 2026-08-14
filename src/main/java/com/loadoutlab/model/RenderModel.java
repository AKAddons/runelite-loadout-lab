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
		return entry(mobs, perMob, -1);
	}

	/** riskKeptSlots >= 0 adds a wilderness risk node per card. */
	public static Map<String, Object> entry(List<MonsterStats> mobs,
		List<Map<CombatStyle, OptimizerService.StyleResult>> perMob, int riskKeptSlots)
	{
		return entry(mobs, perMob, riskKeptSlots, null, java.util.Collections.emptySet());
	}

	/** Full form: ownership + simmed ids flag every gear cell for the
	 * classic border language (gold owned-BiS / green assumed / grey). */
	public static Map<String, Object> entry(List<MonsterStats> mobs,
		List<Map<CombatStyle, OptimizerService.StyleResult>> perMob, int riskKeptSlots,
		java.util.function.IntPredicate owned, java.util.Set<Integer> simmed)
	{
		return entry(mobs, perMob, riskKeptSlots, owned, simmed, null);
	}

	/** Per-build provenance lookup: an item's storage NAME when a fetch
	 * trip is needed (the classic source dots), "" when at hand. */
	private static final ThreadLocal<java.util.function.IntFunction<String>> LOCATION =
		new ThreadLocal<>();

	public static Map<String, Object> entry(List<MonsterStats> mobs,
		List<Map<CombatStyle, OptimizerService.StyleResult>> perMob, int riskKeptSlots,
		java.util.function.IntPredicate owned, java.util.Set<Integer> simmed,
		java.util.function.IntFunction<String> locationOf)
	{
		LOCATION.set(locationOf);
		try
		{
			return buildEntry(mobs, perMob, riskKeptSlots, owned, simmed);
		}
		finally
		{
			LOCATION.remove();
		}
	}

	private static Map<String, Object> buildEntry(List<MonsterStats> mobs,
		List<Map<CombatStyle, OptimizerService.StyleResult>> perMob, int riskKeptSlots,
		java.util.function.IntPredicate owned, java.util.Set<Integer> simmed)
	{
		Map<String, Object> entry = new LinkedHashMap<>();
		List<Object> mobNodes = new ArrayList<>();
		for (int i = 0; i < mobs.size(); i++)
		{
			Map<String, Object> node = mob(mobs.get(i));
			node.put("styles", styles(perMob.get(i), riskKeptSlots, owned, simmed));
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
		node.put("level", mob.getCombatLevel());
		node.put("invocationScaled",
			com.loadoutlab.engine.MonsterMechanics.isToaInvocationScaled(mob));
		node.put("breathesFire", com.loadoutlab.engine.DragonfireRules.breathesFire(mob));
		return node;
	}

	private static Map<String, Object> styles(Map<CombatStyle, OptimizerService.StyleResult> results,
		int riskKeptSlots, java.util.function.IntPredicate owned, java.util.Set<Integer> simmed)
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
			node.put("yours", card(result, false, riskKeptSlots, owned, simmed));
			node.put("bis", card(result, true, riskKeptSlots, owned, simmed));
			node.put("boostLabel", result.boostLabel);
			node.put("bisBoostLabel", result.gameBoostLabel);
			node.put("assume", assumeNode(result.boostLabel));
			node.put("bisAssume", assumeNode(result.gameBoostLabel));
			// Tab facts: the sprite and each side's SHOWN dps (set + spec)
			// so a renderer can label the strip without re-deriving.
			node.put("styleSprite", com.loadoutlab.data.AssumeIcons.styleSprite(style));
			DpsResult ownedShown = result.owned == null || result.owned.isEmpty()
				? null : result.owned.get(0);
			node.put("tabDps", ownedShown == null ? 0
				: ownedShown.getDps() + result.specDpsAdded);
			node.put("bisTabDps", result.overallBest == null ? 0
				: result.overallBest.getDps() + result.gameSpecDpsAdded);
			styles.put(style.name().toLowerCase(), node);
		}
		return styles;
	}

	static Map<String, Object> card(OptimizerService.StyleResult result, boolean bis)
	{
		return card(result, bis, -1, null, java.util.Collections.emptySet());
	}

	/** One side's card: the shown set and everything rendered around it. */
	static Map<String, Object> card(OptimizerService.StyleResult result, boolean bis, int riskKeptSlots,
		java.util.function.IntPredicate owned, java.util.Set<Integer> simmed)
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
		DpsResult bisShown = result.overallBest;
		for (GearSlot slot : GearSlot.values())
		{
			GearItem item = shown.getLoadout().get(slot);
			if (item != null)
			{
				Map<String, Object> node = item(item);
				if (owned != null)
				{
					// The classic border language: gold = owned AND best-
					// available (exact or stat-equivalent analog); green =
					// gear the answer ASSUMES (simmed, or unowned on the
					// Yours side); everything else stays quiet grey.
					boolean owns = owned.test(item.getId());
					boolean isSimmed = !owns && simmed.contains(item.getId());
					node.put("owned", owns);
					node.put("assumed", isSimmed || (!bis && !owns));
					GearItem bisItem = bis || bisShown == null
						? null : bisShown.getLoadout().get(slot);
					node.put("bisMatch", owns && bisItem != null
						&& (bisItem.getId() == item.getId() || statEquivalent(bisItem, item)));
				}
				java.util.function.IntFunction<String> locationOf = LOCATION.get();
				if (locationOf != null)
				{
					// Only FETCH-TRIP storages carry a dot (the classic
					// rule: at-hand gear stays unmarked).
					String where = locationOf.apply(item.getId());
					if (where != null && !where.isEmpty())
					{
						node.put("source", where);
					}
				}
				gear.put(slot.name().toLowerCase(), node);
			}
		}
		card.put("gear", gear);
		card.put("quiverAmmo", item(shown.getLoadout().getQuiverAmmo()));
		card.put("spec", spec(result, bis));
		card.put("incoming", incoming(bis ? result.gameIncoming : result.incoming));
		card.put("bench", items(bis ? result.gameBench : result.bench));
		card.put("kitBacked", bis ? result.gameKitBacked : result.ownedKitBacked);
		Map<String, Object> stats = new LinkedHashMap<>();
		stats.put("offensive", statBlock(shown.getLoadout().getOffensive()));
		stats.put("defensive", statBlock(shown.getLoadout().getDefensive()));
		com.loadoutlab.data.StatBlock bonuses = shown.getLoadout().getBonuses();
		stats.put("strength", bonuses.getStrength());
		stats.put("rangedStrength", bonuses.getRangedStrength());
		stats.put("magicDamage", bonuses.getMagicDamage());
		stats.put("prayer", bonuses.getPrayer());
		card.put("stats", stats);
		if (riskKeptSlots >= 0)
		{
			com.loadoutlab.engine.PvpRisk.Assessment risk = com.loadoutlab.engine.PvpRisk.assess(
				shown.getLoadout(), bis ? result.gameSpecWeapon : result.specWeapon, riskKeptSlots);
			Map<String, Object> riskNode = new LinkedHashMap<>();
			riskNode.put("riskGp", risk.riskGp);
			List<Object> kept = new ArrayList<>();
			for (GearItem item : risk.kept)
			{
				kept.add(item.label());
			}
			riskNode.put("kept", kept);
			List<Object> lost = new ArrayList<>();
			for (GearItem item : risk.lost)
			{
				lost.add(item.label());
			}
			riskNode.put("lost", lost);
			card.put("risk", riskNode);
			// Per-cell fate (the classic risk dots): kept / dropped.
			for (Object slotNode : gear.values())
			{
				if (!(slotNode instanceof Map))
				{
					continue;
				}
				@SuppressWarnings("unchecked")
				Map<String, Object> node = (Map<String, Object>) slotNode;
				int id = ((Number) node.get("id")).intValue();
				for (GearItem k : risk.kept)
				{
					if (k.getId() == id)
					{
						node.put("fate", "kept");
					}
				}
				for (GearItem l : risk.lost)
				{
					if (l.getId() == id)
					{
						node.put("fate", "lost");
						node.put("fateGp", risk.valueOf(l));
					}
				}
			}
		}
		return card;
	}

	/** Analogs count as best-available (any god's d'hide coif). */
	private static boolean statEquivalent(GearItem a, GearItem b)
	{
		return a.getSlot() == b.getSlot()
			&& a.getSpeed() == b.getSpeed()
			&& a.isTwoHanded() == b.isTwoHanded()
			&& a.getCategory().equals(b.getCategory())
			&& sameBlock(a.getOffensive(), b.getOffensive())
			&& sameBlock(a.getDefensive(), b.getDefensive())
			&& sameBlock(a.getBonuses(), b.getBonuses());
	}

	private static boolean sameBlock(com.loadoutlab.data.StatBlock a, com.loadoutlab.data.StatBlock b)
	{
		return a.getStab() == b.getStab() && a.getSlash() == b.getSlash()
			&& a.getCrush() == b.getCrush() && a.getMagic() == b.getMagic()
			&& a.getRanged() == b.getRanged() && a.getStrength() == b.getStrength()
			&& a.getRangedStrength() == b.getRangedStrength()
			&& a.getMagicDamage() == b.getMagicDamage()
			&& a.getPrayer() == b.getPrayer();
	}

	/** Facts for the header assume icons: the label "Piety + Divine
	 * super combat" resolves to a prayer sprite id and a boost potion
	 * item id (renderers draw, never map). */
	private static Map<String, Object> assumeNode(String boostLabel)
	{
		if (boostLabel == null || boostLabel.isEmpty())
		{
			return null;
		}
		Map<String, Object> node = new LinkedHashMap<>();
		node.put("text", boostLabel);
		int plus = boostLabel.indexOf(" + ");
		String prayerPart = plus > 0 ? boostLabel.substring(0, plus) : boostLabel;
		String boostPart = plus > 0 ? boostLabel.substring(plus + 3) : null;
		node.put("prayerSprite", com.loadoutlab.data.AssumeIcons.prayerSprite(prayerPart));
		node.put("boostItem", boostPart == null ? -1
			: com.loadoutlab.data.AssumeIcons.boostItem(boostPart));
		return node;
	}

	private static Map<String, Object> statBlock(com.loadoutlab.data.StatBlock block)
	{
		Map<String, Object> node = new LinkedHashMap<>();
		node.put("stab", block.getStab());
		node.put("slash", block.getSlash());
		node.put("crush", block.getCrush());
		node.put("magic", block.getMagic());
		node.put("ranged", block.getRanged());
		return node;
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
		node.put("protectSprite", incoming.protectPrayer == null ? -1
			: com.loadoutlab.data.AssumeIcons.prayerSprite(String.valueOf(incoming.protectPrayer)));
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
