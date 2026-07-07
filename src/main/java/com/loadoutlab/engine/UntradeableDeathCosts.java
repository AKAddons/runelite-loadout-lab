package com.loadoutlab.engine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.StatBlock;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * What dying to another player above level 20 Wilderness costs per worn
 * UNTRADEABLE item, under the June 2026 Items Kept on Death rework
 * (wiki: Trouver parchment + Items Kept on Death, verified 2026-07-07):
 *
 * - Category 1 (no combat benefit): never lost, costs nothing.
 * - Category 2 (some combat bonuses): breaks; the victim pays the item's
 *   own repair/replacement fee (per-item, wiki-sourced). This bucket also
 *   carries the component-drop items (crystal armour reverts to a seed for
 *   the killer, slayer helm drops as a black mask, imbued rings drop the
 *   base ring, ...) priced at the component's value.
 * - Category 3 (strong combat bonuses): trouver-locked once (permanent);
 *   each PvP death above 20 Wilderness mangles the item and the victim
 *   pays 500,000 coins to restore it (the killer receives the fee). We
 *   assume the one-time lock has already been done, so the per-death cost
 *   is the flat 500k mangle fee.
 *
 * Entries in untradeable_death.json are keyed by lowercase item name
 * (GearItem.getName(), no version suffix). An UNLISTED untradeable that
 * carries any combat stats falls back to a conservative 500,000 default:
 * unknowns must not smuggle into "low-risk" sets for free - curation
 * whittles the list, the default keeps the risk budget honest meanwhile.
 * Untradeables with no combat stats (graceful class) cost nothing.
 */
public final class UntradeableDeathCosts
{
	private static final String RESOURCE = "/com/loadoutlab/data/untradeable_death.json";

	/** Conservative fallback for combat untradeables we have not curated. */
	public static final long UNKNOWN_COMBAT_UNTRADEABLE_GP = 500_000L;

	private static final Map<String, Long> COST_BY_NAME = new HashMap<>();
	private static final Map<String, Integer> CATEGORY_BY_NAME = new HashMap<>();

	static
	{
		try (InputStream stream = UntradeableDeathCosts.class.getResourceAsStream(RESOURCE);
			InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8))
		{
			JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
			for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet())
			{
				JsonObject row = entry.getValue().getAsJsonObject();
				String name = entry.getKey().toLowerCase(Locale.ROOT);
				COST_BY_NAME.put(name, row.get("costGp").getAsLong());
				CATEGORY_BY_NAME.put(name, row.get("category").getAsInt());
			}
		}
		catch (Exception ex)
		{
			throw new IllegalStateException("Could not load " + RESOURCE, ex);
		}
	}

	private UntradeableDeathCosts()
	{
	}

	/**
	 * Per-death gp cost of wearing this item into PvP above 20 Wilderness.
	 * Tradeables cost 0 here - their loss is priced by the kept-slot
	 * ranking in PvpRisk, not by this table.
	 */
	public static long costFor(GearItem item)
	{
		if (item == null || item.isTradeable())
		{
			return 0L;
		}
		Long listed = COST_BY_NAME.get(item.getName().toLowerCase(Locale.ROOT));
		if (listed != null)
		{
			return listed;
		}
		return hasCombatStats(item) ? UNKNOWN_COMBAT_UNTRADEABLE_GP : 0L;
	}

	/** Curated category (1, 2 or 3), or 0 when the item is not listed. */
	public static int categoryFor(GearItem item)
	{
		if (item == null)
		{
			return 0;
		}
		Integer listed = CATEGORY_BY_NAME.get(item.getName().toLowerCase(Locale.ROOT));
		return listed == null ? 0 : listed;
	}

	private static boolean hasCombatStats(GearItem item)
	{
		return nonZero(item.getOffensive()) || nonZero(item.getDefensive()) || nonZero(item.getBonuses());
	}

	private static boolean nonZero(StatBlock block)
	{
		return block.getStab() != 0 || block.getSlash() != 0 || block.getCrush() != 0
			|| block.getMagic() != 0 || block.getRanged() != 0
			|| block.getStrength() != 0 || block.getRangedStrength() != 0
			|| block.getMagicDamage() != 0 || block.getPrayer() != 0;
	}
}
