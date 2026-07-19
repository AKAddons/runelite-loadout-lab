package com.loadoutlab.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Drift guard for the generated assume_icons.json.
 *
 * build.gradle uses runeLiteVersion = 'latest.release', so the plugin is NOT
 * pinned: these sprite ids used to re-inline against whatever client version
 * was building. Now that they are frozen into a resource, a sprite
 * renumbering upstream would show the WRONG ICON forever with no error.
 *
 * assume_icons_symbols.json (test tree, so it costs no hub tokens) records
 * which SpriteID constant each frozen int came from. This test reflects that
 * exact field and asserts the stored int still equals the live constant, so a
 * renumbering - or a removed/renamed constant - becomes a red build.
 */
public class AssumeIconsDriftTest
{
	private static final String VALUES = "/com/loadoutlab/data/assume_icons.json";
	private static final String SYMBOLS = "/com/loadoutlab/data/assume_icons_symbols.json";

	@Test
	public void tableCountsAreTwentyOneEightAndSixtyOne()
	{
		JsonObject root = read(VALUES);
		assertEquals("prayer entries", 21, root.getAsJsonObject("prayers").size());
		assertEquals("boost item entries", 8, root.getAsJsonObject("boostItems").size());
		assertEquals("spell entries", 61, root.getAsJsonObject("spells").size());
	}

	/** Boost items are plain item ids, so only prayers and spells are symbolic. */
	@Test
	public void everySymbolicEntryStillMatchesTheLiveSpriteIdConstant()
	{
		JsonObject values = read(VALUES);
		JsonObject symbols = read(SYMBOLS);
		List<String> drifted = new ArrayList<>();
		int checked = 0;

		for (Map.Entry<String, JsonElement> table : symbols.entrySet())
		{
			JsonObject storedValues = values.getAsJsonObject(table.getKey());
			for (Map.Entry<String, JsonElement> entry : table.getValue().getAsJsonObject().entrySet())
			{
				String name = entry.getKey();
				String symbol = entry.getValue().getAsString();
				int stored = storedValues.get(name).getAsInt();
				int live = liveConstant(symbol);
				if (stored != live)
				{
					drifted.add(name + " (" + symbol + "): frozen " + stored + " but live " + live);
				}
				checked++;
			}
		}

		assertEquals("symbolic entries checked", 82, checked);
		assertTrue(
			"assume_icons.json has drifted from the live SpriteID constants; "
				+ "re-run scripts/gen_assume_icons.py. " + drifted,
			drifted.isEmpty());
	}

	/** Resolve "Magicon2.ICE_BARRAGE" against the SpriteID on today's classpath. */
	private static int liveConstant(String symbol)
	{
		int dot = symbol.indexOf('.');
		String inner = symbol.substring(0, dot);
		String constant = symbol.substring(dot + 1);
		try
		{
			Class<?> type = Class.forName("net.runelite.api.gameval.SpriteID$" + inner);
			Field field = type.getDeclaredField(constant);
			field.setAccessible(true);
			return field.getInt(null);
		}
		catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException ex)
		{
			throw new AssertionError("SpriteID." + symbol + " no longer exists upstream", ex);
		}
	}

	private static JsonObject read(String resource)
	{
		try (InputStream in = AssumeIconsDriftTest.class.getResourceAsStream(resource))
		{
			assertTrue("missing " + resource, in != null);
			return new JsonParser().parse(
				new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		catch (IOException ex)
		{
			throw new AssertionError(ex);
		}
	}
}
