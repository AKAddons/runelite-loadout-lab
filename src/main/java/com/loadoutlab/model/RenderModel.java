package com.loadoutlab.model;

import com.loadoutlab.data.*;
import com.loadoutlab.engine.*;
import com.loadoutlab.optimizer.OptimizerService;
import java.util.*;
import java.util.List;
import java.util.Map;
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

	/** A node from key/value pairs, in argument order (the JSON stays
	 * canonical: every builder below lists its keys in the contract's order). */
	static Map<String, Object> map(Object... kv)
	{
		return JsonResources.map(kv);
	}

	/** The side a card shows: the game-wide best, or the first owned set
	 * (null for none, or for no result). */
	static DpsResult shown(OptimizerService.StyleResult result, boolean bis)
	{
		return result == null ? null : bis ? result.overallBest
			: result.owned == null || result.owned.isEmpty() ? null : result.owned.get(0);
	}

	public static Map<String, Object> page(List<Map<String, Object>> entries)
	{
		return map("v", VERSION, "entries", entries);
	}

	/** One result entry: its mobs and, per mob, the per-style cards.
	 * Single-mob pages are the one-mob case of the same shape. */
	public static Map<String, Object> entry(List<MonsterStats> mobs,
		List<Map<CombatStyle, OptimizerService.StyleResult>> perMob)
	{
		return entry(mobs, perMob, -1, null, Set.of(), null);
	}

	/** Per-build provenance lookup: an item's storage NAME when a fetch
	 * trip is needed (the classic source dots), "" when at hand.
	 * riskKeptSlots >= 0 adds a wilderness risk node per card; ownership +
	 * simmed ids flag every gear cell for the classic border language
	 * (gold owned-BiS / green assumed / grey). */
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
		List<Object> mobNodes = new ArrayList<>();
		for (int i = 0; i < mobs.size(); i++)
		{
			Map<String, Object> node = mob(mobs.get(i));
			node.put("styles", styles(perMob.get(i), riskKeptSlots, owned, simmed));
			Map<String, Object> multi = multi(perMob.get(i), riskKeptSlots, owned, simmed);
			if (multi != null)
			{
				node.put("multi", multi);
			}
			mobNodes.add(node);
		}
		return map("mobs", mobNodes);
	}

	static Map<String, Object> mob(MonsterStats mob)
	{
		return map("id", mob.getId(), "profileId", mob.profileId(), "name", mob.getName(),
			"version", mob.getVersion(), "label", mob.label(), "hp", mob.getHitpoints(),
			"level", mob.getCombatLevel(), "invocationScaled", MonsterMechanics.isToaInvocationScaled(mob),
			"breathesFire", DragonfireRules.breathesFire(mob), "wilderness", WildernessMonsters.isWilderness(mob),
			"naval", NavalCombat.isNaval(mob.getName()), "raid", RaidBoosts.raidKey(mob),
			"taskOnly", SlayerLockedMonsters.isTaskOnly(mob), "fightBook", MonsterSpellbooks.bookFor(mob),
			"fightBookReason", MonsterSpellbooks.reasonFor(mob), "slayerMonster", mob.isSlayerMonster(),
			"multiAble", MultiMobs.isMultiAble(mob), "multiLocations", MultiMobs.locations(mob),
			"wildernessExclusive", WildernessMonsters.isExclusive(mob), "gwd", GwdProtection.isGwd(mob));
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
			styles.put(style.name().toLowerCase(), styleNode(style, result, riskKeptSlots, owned, simmed));
		}
		return styles;
	}

	/** The Multi card rows as style nodes keyed by tool, unscaled (the
	 * engine's decoration applies the stack); null without the switch. */
	private static Map<String, Object> multi(Map<CombatStyle, OptimizerService.StyleResult> results,
		int riskKeptSlots, IntPredicate owned, Set<Integer> simmed)
	{
		Map<String, OptimizerService.StyleResult> tools = null;
		for (OptimizerService.StyleResult r : results == null ? List.<OptimizerService.StyleResult>of() : results.values())
		{
			if (r != null && r.multiTools != null)
			{
				tools = r.multiTools;
			}
		}
		if (tools == null)
		{
			return null;
		}
		Map<String, Object> out = new LinkedHashMap<>();
		for (MultiTool tool : MultiTool.values())
		{
			OptimizerService.StyleResult r = tools.get(tool.key);
			if (r != null)
			{
				Map<String, Object> node = styleNode(tool.style, r, riskKeptSlots, owned, simmed);
				node.put("multiKind", tool.kind);
				// The two ranged tools wear their own item icon on the strip
				// (Andrew 2026-10-19); the barrage keeps the magic sprite.
				node.put("styleItem", tool == MultiTool.CHINS ? MultiTool.CHIN_IDS[0]
					: tool == MultiTool.VENATOR ? MultiTool.VENATOR_BOW : 0);
				out.put(tool.key, node);
			}
		}
		return out;
	}

	private static Map<String, Object> styleNode(CombatStyle style, OptimizerService.StyleResult result,
		int riskKeptSlots, IntPredicate owned, Set<Integer> simmed)
	{
		DpsResult ownedShown = shown(result, false);
		// Tab facts after the sides: the sprite and each side's SHOWN dps
		// (set + spec) so a renderer can label the strip without re-deriving.
		return map("yours", card(result, false, riskKeptSlots, owned, simmed),
			"bis", card(result, true, riskKeptSlots, owned, simmed),
			"boostLabel", result.boostLabel, "bisBoostLabel", result.gameBoostLabel,
			"assume", assumeNode(result.boostLabel), "bisAssume", assumeNode(result.gameBoostLabel),
			"styleSprite", AssumeIcons.styleSprite(style),
			"tabDps", ownedShown == null ? 0 : ownedShown.getDps() + result.specDpsAdded,
			"bisTabDps", result.overallBest == null ? 0 : result.overallBest.getDps() + result.gameSpecDpsAdded);
	}

	/** A slot ladder for the card (ISS-01M5GE9EJSX6YWCKMSYEWKZ132): each rung
	 * with its change in the mode's own terms and a signed gain (positive =
	 * better under the mode) that sizes the strip's bars; base = the worn
	 * item's own value in the same terms. */
	public static Map<String, Object> ladderNode(OptimizerService.Ladder ladder)
	{
		Map<String, Object> node = map("base", ladder.mode == OptimizerService.OptimizeMode.TANKY
			? String.format("%.2f taken/s", ladder.baseDtps)
			: String.format("%.2f DPS", ladder.baseDps));
		// Balanced weighs both, so it shows both (Andrew 2026-10-22: "show
		// in the ladder view both the dps gain and the dtps change").
		if (ladder.mode == OptimizerService.OptimizeMode.BALANCED)
		{
			node.put("base2", String.format("%.2f taken/s", ladder.baseDtps));
		}
		node.putAll(map("group", ladder.group, "up", rungs(ladder, ladder.up), "down", rungs(ladder, ladder.down)));
		return node;
	}

	/** The rung's improvement over the worn item under the ladder's mode. */
	static double ladderGain(OptimizerService.Ladder ladder, OptimizerService.Rung rung)
	{
		switch (ladder.mode)
		{
			case TANKY:
				return ladder.baseDtps - rung.dtps;
			case BALANCED:
				double base = ladder.baseDps <= 0 ? 0 : ladder.baseDtps / ladder.baseDps;
				double ratio = rung.dps <= 0 ? base : rung.dtps / rung.dps;
				return base - ratio;
			default:
				return rung.dps - ladder.baseDps;
		}
	}

	private static List<Object> rungs(OptimizerService.Ladder ladder, List<OptimizerService.Rung> rungs)
	{
		List<Object> out = new ArrayList<>();
		for (OptimizerService.Rung rung : rungs)
		{
			Map<String, Object> node = map("id", rung.item.getId(), "name", rung.item.getName(),
				"owned", rung.owned, "price", rung.item.getPriceOrZero(), "delta", ladderDelta(ladder, rung));
			if (ladder.mode == OptimizerService.OptimizeMode.BALANCED)
			{
				node.put("delta2", String.format("%+.2f taken/s", rung.dtps - ladder.baseDtps));
			}
			node.put("gain", ladderGain(ladder, rung));
			// Same result as the worn item - ranked by its better stats.
			node.put("same", Math.abs(rung.dps - ladder.baseDps) <= 1e-9
				&& Math.abs(rung.dtps - ladder.baseDtps) <= 1e-9);
			if (rung.with != null || rung.offhand != null)
			{
				node.put("with", rung.with == null ? rung.offhand.getName()
					: rung.offhand == null ? rung.with.getName()
					: rung.offhand.getName() + " and " + rung.with.getName());
			}
			out.add(node);
		}
		return out;
	}

	/** "+0.12 DPS" (Max DPS and Balanced, whose damage taken rides as
	 * delta2), "-0.30 taken/s" (Tanky). */
	static String ladderDelta(OptimizerService.Ladder ladder, OptimizerService.Rung rung)
	{
		switch (ladder.mode)
		{
			case TANKY:
				return String.format("%+.2f taken/s", rung.dtps - ladder.baseDtps);
			default:
				return String.format("%+.2f DPS", rung.dps - ladder.baseDps);
		}
	}

	/** One side's card: the shown set and everything rendered around it. */
	static Map<String, Object> card(OptimizerService.StyleResult result, boolean bis, int riskKeptSlots,
		IntPredicate owned, Set<Integer> simmed)
	{
		DpsResult shown = shown(result, bis);
		if (shown == null)
		{
			return null;
		}
		Map<String, Object> card = map("dps", shown.getDps(), "maxHit", shown.getMaxHit(),
			"accuracy", shown.getAccuracy());
		// The blowpipe's loaded dart rides the attackType STRING
		// ("ranged rapid - dragon dart") - split it into facts: a clean
		// style line + an internalAmmo entry for the column.
		String attackType = shown.getAttackType();
		if (attackType != null && attackType.contains(" - "))
		{
			int dash = attackType.indexOf(" - ");
			String dartTier = attackType.substring(dash + 3);
			Integer dartId = BlowpipeDarts.baseIdForTierName(dartTier);
			if (dartId != null)
			{
				card.put("internalAmmo", map("id", dartId, "name", Names.cap(dartTier) + " (loaded)"));
				attackType = attackType.substring(0, dash);
			}
		}
		card.putAll(map("attackType", attackType, "spell", shown.getSpellName(),
			"purchaseCost", shown.getPurchaseCost(), "antifireAssumed", shown.isAntifireAssumed(),
			"counted", new ArrayList<>(shown.getCountedBonuses())));
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
					GearItem bisItem = bis || bisShown == null
						? null : bisShown.getLoadout().get(slot);
					node.putAll(map("owned", owns, "assumed", isSimmed || (!bis && !owns),
						"bisMatch", owns && bisItem != null && !result.beaten.contains(slot)
							&& (bisItem.getId() == item.getId() || statEquivalent(bisItem, item))));
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
				OptimizerService.Ladder ladder = bis ? null : result.ladder.get(slot);
				if (ladder != null)
				{
					node.put("ladder", ladderNode(ladder));
				}
				gear.put(slot.name().toLowerCase(), node);
			}
		}
		card.putAll(map("gear", gear, "quiverAmmo", item(shown.getLoadout().getQuiverAmmo()),
			"spec", spec(result, bis), "incoming", incoming(bis ? result.gameIncoming : result.incoming)));
		// The defensive mode's trade belongs to the Yours side only (BiS
		// stays the pure-DPS ceiling).
		OptimizerService.ModeTrade trade = bis ? null : result.modeTrade;
		if (trade != null)
		{
			card.put("modeTrade", map("mode", trade.mode.name(), "dpsLossPct", trade.dpsLossPct,
				"dmgCutPct", trade.dmgCutPct, "reason", trade.reason));
		}
		// Carried GWD protection: which gods each bench item soothes, for
		// the Inventory row's "wear through the dungeon" tooltip.
		Map<String, Object> protection = new LinkedHashMap<>();
		for (GearItem item : bis ? result.gameProtection : result.protection)
		{
			protection.put(String.valueOf(item.getId()), String.join(", ", GwdProtection.godsOf(item)));
		}
		GearItem runeWeapon = shown.getLoadout().getWeapon();
		GearItem runeShield = shown.getLoadout().get(GearSlot.SHIELD);
		StatBlock bonuses = shown.getLoadout().getBonuses();
		card.putAll(map("bench", items(bis ? result.gameBench : result.bench), "protection", protection,
			"runes", SpellRunes.costFor(shown.getSpellName(),
				runeWeapon == null ? null : runeWeapon.getNameLower(),
				runeShield == null ? null : runeShield.getNameLower()),
			"kitBacked", bis ? result.gameKitBacked : result.ownedKitBacked,
			"stats", map("offensive", statBlock(shown.getLoadout().getOffensive()),
				"defensive", statBlock(shown.getLoadout().getDefensive()),
				"strength", bonuses.getStrength(), "rangedStrength", bonuses.getRangedStrength(),
				"magicDamage", bonuses.getMagicDamage(), "prayer", bonuses.getPrayer())));
		if (riskKeptSlots >= 0)
		{
			PvpRisk.Assessment risk = PvpRisk.assess(
				shown.getLoadout(), bis ? result.gameSpecWeapon : result.specWeapon, riskKeptSlots);
			card.put("risk", map("riskGp", risk.riskGp, "kept", labels(risk.kept), "lost", labels(risk.lost)));
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

	/** The items' labels, for the risk node's kept/lost lists. */
	private static List<Object> labels(List<GearItem> items)
	{
		List<Object> out = new ArrayList<>();
		for (GearItem item : items)
		{
			out.add(item.label());
		}
		return out;
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
		return Arrays.equals(values(a), values(b));
	}

	/** Every stat of the block, in one array to compare. */
	private static int[] values(StatBlock b)
	{
		return new int[]{b.getStab(), b.getSlash(), b.getCrush(), b.getMagic(), b.getRanged(),
			b.getStrength(), b.getRangedStrength(), b.getMagicDamage(), b.getPrayer()};
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
			return map("text", "No prayer or boost", "prayerSprite", -1, "boostItem", -1, "boostSupplied", false);
		}
		int plus = boostLabel.indexOf(" + ");
		String prayerPart = plus > 0 ? boostLabel.substring(0, plus) : boostLabel;
		String boostPart = plus > 0 ? boostLabel.substring(plus + 3) : null;
		// boostSupplied: the raid hands these out (CoX overloads, ToA salts) -
		// assumed, never packed (Andrew 2026-09-02: potions are supplies to bring).
		return map("text", boostLabel, "prayerSprite", AssumeIcons.prayerSprite(prayerPart),
			"prayerImage", AssumeIcons.prayerImage(prayerPart),
			"boostItem", boostPart == null ? -1 : AssumeIcons.boostItem(boostPart),
			"boostSupplied", boostPart != null
				&& (boostPart.equals(BoostProfile.OVERLOAD.toString())
				|| boostPart.equals(BoostProfile.OVERLOAD_PLUS.toString())
				|| boostPart.equals(BoostProfile.SMELLING_SALTS.toString())));
	}

	private static Map<String, Object> statBlock(StatBlock block)
	{
		return map("stab", block.getStab(), "slash", block.getSlash(), "crush", block.getCrush(),
			"magic", block.getMagic(), "ranged", block.getRanged());
	}

	private static Map<String, Object> spec(OptimizerService.StyleResult result, boolean bis)
	{
		GearItem weapon = bis ? result.gameSpecWeapon : result.specWeapon;
		if (weapon == null)
		{
			return null;
		}
		Map<String, Object> weaponNode = item(weapon);
		// The spec cell's swaps strip (ISS-01M5HCS725N7XVVBC7CMK6Y43A).
		if (!bis && result.specLadder != null && weaponNode != null)
		{
			weaponNode.put("ladder", ladderNode(result.specLadder));
		}
		return map("weapon", weaponNode, "dpsAdded", bis ? result.gameSpecDpsAdded : result.specDpsAdded,
			"expectedDamage", bis ? result.gameSpecExpectedDamage : result.specExpectedDamage);
	}

	private static Map<String, Object> incoming(IncomingDpsCalculator.Result incoming)
	{
		if (incoming == null)
		{
			return null;
		}
		List<Object> threats = new ArrayList<>();
		for (IncomingDpsCalculator.StyleThreat threat : incoming.threats)
		{
			threats.add(map("style", threat.style, "dps", threat.dps, "maxHit", threat.maxHit,
				"modeled", threat.modeled, "blocked", threat.blocked, "prayerFactor", threat.prayerFactor));
		}
		return map("dps", incoming.totalDps, "unprayedDps", incoming.unprayedDps,
			"protectSprite", incoming.protectPrayer == null ? -1
				: AssumeIcons.prayerSprite(String.valueOf(incoming.protectPrayer)),
			"protectPrayer", incoming.protectPrayer, "fullyModeled", incoming.fullyModeled,
			"coverage", incoming.coverage, "unmodeled", new ArrayList<>(incoming.unmodeled),
			"overrideNote", incoming.overrideNote, "threats", threats);
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
		Map<String, Object> node = map("id", item.getId(), "name", item.label());
		if (Degradable.matches(item.getNameLower()))
		{
			node.put("degrades", true);
		}
		node.put("price", item.getPriceOrZero());
		return node;
	}
}
