package com.loadoutlab.collection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Loadout Lab's half of the bidirectional storages contract: other
 * plugins can ask US for the owned-gear ledger the same way we ask
 * Dude, Where's My Stuff - identical request/response shape, each
 * plugin under its own namespace (reciprocity decided 2026-07-11;
 * the config keys in the README's "Data sharing" section stay as the
 * works-while-disabled fallback).
 *
 * <p>Request: namespace "loadoutlab", name "storages-request", data
 * {"source": requesting plugin's display name (required)}. Response:
 * name "storages-response" with "source" ("Loadout Lab"), "target"
 * (the requester's source echoed), "version" (1), and "storages" -
 * one entry per non-empty source with "category", "name",
 * "lastUpdated" (-1; the ledger keeps no timestamps) and "items"
 * ({"id": canonical item id, "quantity": Long}).
 *
 * <p>This class is the pure, testable part; the plugin gathers the
 * snapshots and posts on the client thread (ids canonicalize there).
 */
public final class StoragesApi
{
	public static final String NAMESPACE = "loadoutlab";
	public static final String REQUEST_NAME = "storages-request";
	public static final String RESPONSE_NAME = "storages-response";
	static final int VERSION = 1;
	static final String SOURCE = "Loadout Lab";

	private StoragesApi()
	{
	}

	/** The validated requester name from a storages-request payload,
	 * or null when the request carries no usable source. */
	public static String requester(Map<String, Object> data)
	{
		if (data == null)
		{
			return null;
		}
		Object source = data.get("source");
		return source instanceof String && !((String) source).isEmpty() ? (String) source : null;
	}

	/**
	 * One storage entry for the response: items canonicalized (variants
	 * that collapse to one id sum), non-positive entries dropped.
	 * Returns null when nothing survives - empty storages are omitted,
	 * matching the DWMS side of the contract.
	 */
	public static Map<String, Object> storage(String category, String name, long lastUpdated,
		Map<Integer, Integer> items, IntUnaryOperator canonicalize)
	{
		Map<Integer, Long> canonical = new LinkedHashMap<>();
		for (Map.Entry<Integer, Integer> e : items.entrySet())
		{
			if (e.getKey() <= 0 || e.getValue() <= 0)
			{
				continue;
			}
			canonical.merge(canonicalize.applyAsInt(e.getKey()), (long) e.getValue(), Long::sum);
		}
		if (canonical.isEmpty())
		{
			return null;
		}
		List<Map<String, Object>> itemList = new ArrayList<>(canonical.size());
		for (Map.Entry<Integer, Long> e : canonical.entrySet())
		{
			Map<String, Object> item = new HashMap<>();
			item.put("id", e.getKey());
			item.put("quantity", e.getValue());
			itemList.add(item);
		}
		Map<String, Object> entry = new HashMap<>();
		entry.put("category", category);
		entry.put("name", name);
		entry.put("lastUpdated", lastUpdated);
		entry.put("items", itemList);
		return entry;
	}

	/** The response envelope addressed back to the requester. */
	public static Map<String, Object> response(String target, List<Map<String, Object>> storages)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("source", SOURCE);
		data.put("target", target);
		data.put("version", VERSION);
		data.put("storages", storages);
		return data;
	}
}
