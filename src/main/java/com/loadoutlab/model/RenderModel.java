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
import com.loadoutlab.data.AssumeIcons;
import com.loadoutlab.data.MonsterSpellbooks;
import com.loadoutlab.data.StatBlock;
import com.loadoutlab.data.WildernessMonsters;
import com.loadoutlab.engine.BoostProfile;
import com.loadoutlab.engine.PvpRisk;
import java.util.Collections;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

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
		return entry(mobs, perMob, riskKeptSlots, null, Collections.emptySet());
	}

	/** Full form: ownership + simmed ids flag every gear cell for the
	 * classic border language (gold owned-BiS / green assumed / grey). */
	public static Map<String, Object> entry(List<MonsterStats> mobs,
		List<Map<CombatStyle, OptimizerService.StyleResult>> perMob, int riskKeptSlots,
		IntPredicate owned, Set<Integer> simmed)
	{
		return entry(mobs, perMob, riskKeptSlots, owned, simmed, null);
	}

	/** Per-build provenance lookup: an item's storage NAME when a fetch
	 * trip is needed (the classic source dots), "" when at hand. */
	private static final ThreadLocal<IntFunction<String>> LOCATION =
		new ThreadLocal<>();

	public static Map<String, Object> entry(List<MonsterStats> mobs,
		List<Map<CombatStyle, OptimizerService.StyleResult>> perMob, int riskKeptSlots,
		IntPredicate owned, Set<Integer> simmed,
		IntFunction<String> locationOf)
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
		IntPredicate owned, Set<Integer> simmed)
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

	static Map<String, Object> mob(MonsterStats mob)
	{
		Map<String, Object> node = new LinkedHashMap<>();
		node.put("id", mob.getId());
		node.put("profileId", mob.profileId());
		node.put("name", mob.getName());
		node.put("version", mob.getVersion());
		node.put("label", mob.label());
		node.put("hp", mob.getHitpoints());
		node.put("level", mob.getCombatLevel());
		node.put("invocationScaled",
			com.loadoutlab.engine.MonsterMechanics.isToaInvocationScaled(mob));
		node.put("breathesFire", com.loadoutlab.engine.DragonfireRules.breathesFire(mob));
		node.put("wilderness", WildernessMonsters.isWilderness(mob));
		node.put("naval", com.loadoutlab.data.NavalCombat.isNaval(mob.getName()));
		node.put("raid", com.loadoutlab.engine.RaidBoosts.raidKey(mob));
		node.put("taskOnly", com.loadoutlab.data.SlayerLockedMonsters.isTaskOnly(mob));
		node.put("fightBook", MonsterSpellbooks.bookFor(mob));
		node.put("fightBookReason", MonsterSpellbooks.reasonFor(mob));
		node.put("slayerMonster", mob.isSlayerMonster());
		node.put("wildernessExclusive", WildernessMonsters.isExclusive(mob));
		return node;
	}

	private static Map<String, Object> styles(Map<CombatStyle, OptimizerService.StyleResult> results,
		int riskKeptSlots, IntPredicate owned, Set<Integer> simmed)
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
			node.put("styleSprite", AssumeIcons.styleSprite(style));
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
		return card(result, bis, -1, null, Collections.emptySet());
	}

	/** One side's card: the shown set and everything rendered around it. */
	static Map<String, Object> card(OptimizerService.StyleResult result, boolean bis, int riskKeptSlots,
		IntPredicate owned, Set<Integer> simmed)
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
		// The blowpipe's loaded dart rides the attackType STRING
		// ("ranged rapid - dragon dart") - split it into facts: a clean
		// style line + an internalAmmo entry for the column.
		String attackType = shown.getAttackType();
		if (attackType != null && attackType.contains(" - "))
		{
			int dash = attackType.indexOf(" - ");
			String dartTier = attackType.substring(dash + 3);
			Integer dartId = com.loadoutlab.engine.BlowpipeDarts.baseIdForTierName(dartTier);
			if (dartId != null)
			{
				Map<String, Object> dartNode = new LinkedHashMap<>();
				dartNode.put("id", dartId);
				dartNode.put("name", Character.toUpperCase(dartTier.charAt(0))
					+ dartTier.substring(1) + " (loaded)");
				card.put("internalAmmo", dartNode);
				attackType = attackType.substring(0, dash);
			}
		}
		card.put("attackType", attackType);
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
				IntFunction<String> locationOf = LOCATION.get();
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
		GearItem runeWeapon = shown.getLoadout().getWeapon();
		GearItem runeShield = shown.getLoadout().get(GearSlot.SHIELD);
		card.put("runes", com.loadoutlab.data.SpellRunes.costFor(shown.getSpellName(),
			runeWeapon == null ? null : runeWeapon.getNameLower(),
			runeShield == null ? null : runeShield.getNameLower()));
		card.put("kitBacked", bis ? result.gameKitBacked : result.ownedKitBacked);
		Map<String, Object> stats = new LinkedHashMap<>();
		stats.put("offensive", statBlock(shown.getLoadout().getOffensive()));
		stats.put("defensive", statBlock(shown.getLoadout().getDefensive()));
		StatBlock bonuses = shown.getLoadout().getBonuses();
		stats.put("strength", bonuses.getStrength());
		stats.put("rangedStrength", bonuses.getRangedStrength());
		stats.put("magicDamage", bonuses.getMagicDamage());
		stats.put("prayer", bonuses.getPrayer());
		card.put("stats", stats);
		if (riskKeptSlots >= 0)
		{
			PvpRisk.Assessment risk = PvpRisk.assess(
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

	private static boolean sameBlock(StatBlock a, StatBlock b)
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
	static Map<String, Object> assumeNode(String boostLabel)
	{
		if (boostLabel == null || boostLabel.isEmpty())
		{
			// "None + none" must still be a node: the assume icons ARE the
			// pickers, and a null here erased both - picking None removed
			// the only control that could undo it (field report 2026-08-31,
			// Not on Hand). The sentinel sprites render from -1: the prayer
			// cell falls back to the prayer-book wings, the boost cell to
			// the muted dash, and both stay clickable.
			Map<String, Object> none = new LinkedHashMap<>();
			none.put("text", "No prayer or boost");
			none.put("prayerSprite", -1);
			none.put("boostItem", -1);
			none.put("boostSupplied", false);
			return none;
		}
		Map<String, Object> node = new LinkedHashMap<>();
		node.put("text", boostLabel);
		int plus = boostLabel.indexOf(" + ");
		String prayerPart = plus > 0 ? boostLabel.substring(0, plus) : boostLabel;
		String boostPart = plus > 0 ? boostLabel.substring(plus + 3) : null;
		node.put("prayerSprite", AssumeIcons.prayerSprite(prayerPart));
		node.put("boostItem", boostPart == null ? -1
			: AssumeIcons.boostItem(boostPart));
		// The raid hands these out (CoX overloads, ToA salts): assumed, never
		// packed (Andrew 2026-09-02: potions are supplies to bring).
		node.put("boostSupplied", boostPart != null
			&& (boostPart.equals(BoostProfile.OVERLOAD.toString())
			|| boostPart.equals(BoostProfile.OVERLOAD_PLUS.toString())
			|| boostPart.equals(BoostProfile.SMELLING_SALTS.toString())));
		return node;
	}

	private static Map<String, Object> statBlock(StatBlock block)
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
			: AssumeIcons.prayerSprite(String.valueOf(incoming.protectPrayer)));
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
		node.put("price", item.getPriceOrZero());
		return node;
	}
}
