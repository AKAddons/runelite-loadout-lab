package com.loadoutlab.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;

/**
 * Loaders for the bundled JSON data tables (the hub token cap keeps data
 * in resources, not source). All fail SOFT - a malformed table must never
 * take the client down; the table's tests fail loudly on emptiness instead.
 */
public final class JsonResources
{
	private JsonResources()
	{
	}

	/** The parsed root object, or null when missing/malformed. */
	public static JsonObject object(String resource)
	{
		try (InputStream in = JsonResources.class.getResourceAsStream(resource))
		{
			return new JsonParser().parse(
				new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		catch (Exception e)
		{
			return null;
		}
	}

	/** The parsed root array, or an empty one when missing/malformed. */
	public static com.google.gson.JsonArray array(String resource)
	{
		try (InputStream in = JsonResources.class.getResourceAsStream(resource))
		{
			return new JsonParser().parse(
				new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
		}
		catch (Exception e)
		{
			return new com.google.gson.JsonArray();
		}
	}

	/** Copy the string array at root[key] into the collection. */
	public static void strings(JsonObject root, String key, Collection<String> into)
	{
		if (root == null || !root.has(key))
		{
			return;
		}
		for (JsonElement e : root.getAsJsonArray(key))
		{
			into.add(e.getAsString());
		}
	}

	/** Copy the int array at root[key] into the collection. */
	public static void ints(JsonObject root, String key, Collection<Integer> into)
	{
		if (root == null || !root.has(key))
		{
			return;
		}
		for (JsonElement e : root.getAsJsonArray(key))
		{
			into.add(e.getAsInt());
		}
	}

	/** Copy the {"id": int} object at root[key] into the map. */
	public static void intMap(JsonObject root, String key, Map<Integer, Integer> into)
	{
		if (root == null || !root.has(key))
		{
			return;
		}
		for (Map.Entry<String, JsonElement> e : root.getAsJsonObject(key).entrySet())
		{
			into.put(Integer.parseInt(e.getKey()), e.getValue().getAsInt());
		}
	}
}
