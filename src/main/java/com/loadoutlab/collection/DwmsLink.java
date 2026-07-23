package com.loadoutlab.collection;

import lombok.Getter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Live link to "Dude, Where's My Stuff?" over RuneLite's PluginMessage
 * bus (DWMS ships the contract since 2.11.5 - the old best-effort config
 * read is retired).
 *
 * <p>Contract (version 1, authored upstream): we post a request
 * (namespace "dudewheresmystuff", name "storages-request", data
 * {"source": "Loadout Lab"}); DWMS replies with "storages-response"
 * carrying "target" (our source echoed back - responses meant for other
 * plugins are ignored), "version" and "storages", a list of maps with
 * "category"/"name"/"lastUpdated" plus "items" ({"id": canonical item
 * id, "quantity"}). Requests are fire-and-forget: an absent or older
 * DWMS never replies and {@link #isLive()} stays false - its storages
 * then simply do not count as owned.
 *
 * <p>Responses arrive on DWMS's client thread; parsing is defensive
 * (drop, never guess) and the snapshot swap is a volatile write, so no
 * marshalling is needed here.
 */
public class DwmsLink
{
	/** PluginMessage namespace = DWMS's config group, per RuneLite convention. */
	public static final String DWMS_NAMESPACE = "dudewheresmystuff";
	public static final String REQUEST_NAME = "storages-request";
	public static final String RESPONSE_NAME = "storages-response";
	/** The one contract version this consumer understands; anything else
	 * is rejected so the fallback under-counts rather than miscounts. */
	static final int SUPPORTED_VERSION = 1;
	/** Our display name - sent as "source", echoed back as "target". */
	static final String SOURCE = "Loadout Lab";

	private volatile Map<Integer, Integer> items = Collections.emptyMap();
	/** Response "category" (carryable/death/poh/stash/world...) -> items -
	 * the location-hint provenance families. */
	private volatile Map<String, Map<Integer, Integer>> families = Collections.emptyMap();
	/** True once DWMS has answered for the current identity. */
	@Getter
	private volatile boolean live;

	/** The request payload for PluginMessage(DWMS_NAMESPACE, REQUEST_NAME, ...). */
	public static Map<String, Object> request()
	{
		return Collections.singletonMap("source", SOURCE);
	}

	/**
	 * Consume a candidate storages-response payload (namespace/name gating
	 * happens at the caller). Returns true - and replaces the snapshot -
	 * only for a well-formed version-1 response addressed to us.
	 */
	public boolean accept(Map<String, Object> data)
	{
		if (data == null
			|| !SOURCE.equals(data.get("target"))
			|| !(data.get("version") instanceof Number)
			|| ((Number) data.get("version")).intValue() != SUPPORTED_VERSION
			|| !(data.get("storages") instanceof List))
		{
			return false;
		}
		Map<Integer, Integer> next = new HashMap<>();
		Map<String, Map<Integer, Integer>> nextFamilies = new HashMap<>();
		for (Object storage : (List<?>) data.get("storages"))
		{
			if (!(storage instanceof Map))
			{
				continue;
			}
			Object storageItems = ((Map<?, ?>) storage).get("items");
			if (!(storageItems instanceof List))
			{
				continue;
			}
			Object category = ((Map<?, ?>) storage).get("category");
			Map<Integer, Integer> family = nextFamilies.computeIfAbsent(
				category instanceof String ? (String) category : "world",
				k -> new HashMap<>());
			for (Object item : (List<?>) storageItems)
			{
				mergeItem(item, next);
				mergeItem(item, family);
			}
		}
		items = next.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(next);
		families = nextFamilies.isEmpty() ? Collections.emptyMap()
			: Collections.unmodifiableMap(nextFamilies);
		live = true;
		return true;
	}

	/** One {"id", "quantity"} entry; anything malformed is dropped. */
	private static void mergeItem(Object item, Map<Integer, Integer> into)
	{
		if (!(item instanceof Map))
		{
			return;
		}
		Object id = ((Map<?, ?>) item).get("id");
		Object quantity = ((Map<?, ?>) item).get("quantity");
		if (!(id instanceof Number) || !(quantity instanceof Number))
		{
			return;
		}
		int itemId = ((Number) id).intValue();
		long amount = ((Number) quantity).longValue();
		if (itemId <= 0 || amount <= 0)
		{
			return;
		}
		into.merge(itemId, (int) Math.min(amount, Integer.MAX_VALUE),
			(a, b) -> (int) Math.min((long) a + b, Integer.MAX_VALUE));
	}

	/** A different account/profile: nothing from the previous one survives. */
	public void reset()
	{
		items = Collections.emptyMap();
		families = Collections.emptyMap();
		live = false;
	}

	/** Category -> merged items, for the location-hint provenance. */
	public Map<String, Map<Integer, Integer>> families()
	{
		return families;
	}

	/** Item id -> quantity from the latest response, merged across storages. */
	public Map<Integer, Integer> snapshot()
	{
		return items;
	}

	/** Distinct live item count - the panel's provenance line. */
	public int count()
	{
		return items.size();
	}

	/** Fold the live items into an owned map (quantities sum). */
	public Map<Integer, Integer> mergeInto(Map<Integer, Integer> owned)
	{
		for (Map.Entry<Integer, Integer> e : items.entrySet())
		{
			owned.merge(e.getKey(), e.getValue(), Integer::sum);
		}
		return owned;
	}
}
