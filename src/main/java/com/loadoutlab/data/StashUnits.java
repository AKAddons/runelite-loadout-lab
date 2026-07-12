package com.loadoutlab.data;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * STASH units: chart text -> the item ids a FILLED unit is assumed to
 * hold. These are each unit's default items - "any stole"-style
 * alternates resolve to the base variant, which is exactly what the
 * ownership canonicalizer wants. Vendored from Dude, Where's My Stuff's
 * StashUnit enum (BSD-2, licenses/dude-wheres-my-stuff-LICENSE) via
 * scripts/gen_stash_units.py; regenerate there, never edit the TSV.
 *
 * <p>Also hosts the STASH chart state machine (widget group 493, opened
 * from the noticeboard by Watson's house): each tier panel lists units
 * as a text cell (type 4) followed by up to two sprite cells (type 5) -
 * first sprite means built, second means filled.
 */
public final class StashUnits
{
	/** Widget child types on the chart, per the in-game interface. */
	public static final int TEXT_CELL = 4;
	public static final int SPRITE_CELL = 5;

	private final Map<String, int[]> byChartText;

	private StashUnits(Map<String, int[]> byChartText)
	{
		this.byChartText = byChartText;
	}

	/** Load the vendored table from resources; throws on a broken bundle. */
	public static StashUnits load()
	{
		Map<String, int[]> units = new HashMap<>();
		try (InputStream in = StashUnits.class.getResourceAsStream("stash-units.tsv"))
		{
			if (in == null)
			{
				throw new IOException("stash-units.tsv missing from resources");
			}
			BufferedReader reader = new BufferedReader(
				new InputStreamReader(in, StandardCharsets.UTF_8));
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				int tab = line.indexOf('\t');
				String[] tokens = line.substring(tab + 1).split(",");
				int[] ids = new int[tokens.length];
				for (int i = 0; i < tokens.length; i++)
				{
					ids[i] = Integer.parseInt(tokens[i]);
				}
				units.put(line.substring(0, tab), ids);
			}
		}
		catch (IOException | RuntimeException ex)
		{
			throw new IllegalStateException("could not load stash-units.tsv", ex);
		}
		return new StashUnits(units);
	}

	/** Default item ids for a chart entry, or null when unknown. */
	public int[] itemsFor(String chartText)
	{
		return byChartText.get(chartText);
	}

	public int size()
	{
		return byChartText.size();
	}

	/** One chart cell (widget child) as extracted from a tier panel. */
	public static final class Cell
	{
		final int type;
		final String text;

		public Cell(int type, String text)
		{
			this.type = type;
			this.text = text;
		}
	}

	/**
	 * Chart texts of FILLED units in one tier panel's cells. masterTier
	 * disambiguates the Warriors' Guild bank unit, which appears on two
	 * tiers under the same chart text.
	 */
	public static List<String> filledNames(List<Cell> cells, boolean masterTier)
	{
		List<String> filled = new ArrayList<>();
		String current = null;
		int sprites = 0;
		for (Cell cell : cells)
		{
			if (cell.type == TEXT_CELL)
			{
				flush(filled, current, sprites);
				current = rename(cell.text, masterTier);
				sprites = 0;
			}
			else if (cell.type == SPRITE_CELL && current != null)
			{
				sprites++;
			}
		}
		flush(filled, current, sprites);
		return filled;
	}

	private static void flush(List<String> filled, String name, int sprites)
	{
		if (name != null && sprites >= 2)
		{
			filled.add(name);
		}
	}

	private static String rename(String text, boolean masterTier)
	{
		return masterTier && "Warriors' Guild bank".equals(text)
			? "Warriors' Guild bank (master)" : text;
	}
}
