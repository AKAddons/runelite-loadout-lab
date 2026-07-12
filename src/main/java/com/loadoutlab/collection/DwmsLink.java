package com.loadoutlab.collection;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Live link to "Dude, Where's My Stuff?" over RuneLite's PluginMessage
 * bus - the durable successor to the best-effort config read in
 * {@link DwmsImport}.
 *
 * <p>Contract (version 1, authored upstream): we post a request
 * (namespace "dudewheresmystuff", name "storages-request", data
 * {"source": "Loadout Lab"}); DWMS replies with "storages-response"
 * carrying "target" (our source echoed back - responses meant for other
 * plugins are ignored), "version" and "storages", a list of maps with
 * "category"/"name"/"lastUpdated" plus "items" ({"id": canonical item
 * id, "quantity"}). Requests are fire-and-forget: an absent or older
 * DWMS never replies, {@link #isLive()} stays false, and the config
 * read remains in charge.
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
			for (Object item : (List<?>) storageItems)
			{
				mergeItem(item, next);
			}
		}
		items = next.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(next);
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

	/** True once DWMS has answered for the current identity. */
	public boolean isLive()
	{
		return live;
	}

	/** A different account/profile: nothing from the previous one survives. */
	public void reset()
	{
		items = Collections.emptyMap();
		live = false;
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
