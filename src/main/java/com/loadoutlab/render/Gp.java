package com.loadoutlab.render;

/** The classic gp field vocabulary (ported from the panel's
 * parsedBudgetGp): k/m/b suffixes, "max" or "-" = 2b, empty or
 * unparseable = 0, clamped to [0, 2b]. */
public final class Gp
{
	private Gp()
	{
	}

	public static int parse(String text)
	{
		String raw = text == null ? "" : text.trim().toLowerCase();
		if (raw.isEmpty())
		{
			return 0;
		}
		if (raw.equals("-") || raw.equals("max"))
		{
			return 2_000_000_000;
		}
		double multiplier = 1;
		if (raw.endsWith("k") || raw.endsWith("m") || raw.endsWith("b"))
		{
			multiplier = raw.endsWith("k") ? 1_000 : raw.endsWith("m") ? 1_000_000 : 1_000_000_000;
			raw = raw.substring(0, raw.length() - 1);
		}
		try
		{
			double value = Double.parseDouble(raw) * multiplier;
			return (int) Math.max(0, Math.min(value, 2_000_000_000));
		}
		catch (NumberFormatException ex)
		{
			return 0;
		}
	}

	/** The compact display form: rounded, never the raw integer -
	 * "25m", "7.9m", "480k". For chips and stat lines. */
	static String formatShort(int gp)
	{
		if (gp <= 0)
		{
			return "";
		}
		if (gp >= 10_000_000)
		{
			return Math.round(gp / 1_000_000.0) + "m";
		}
		if (gp >= 1_000_000)
		{
			return String.format("%.1fm", gp / 1_000_000.0).replace(".0m", "m");
		}
		if (gp >= 10_000)
		{
			return Math.round(gp / 1_000.0) + "k";
		}
		if (gp >= 1_000)
		{
			return String.format("%.1fk", gp / 1_000.0).replace(".0k", "k");
		}
		return String.valueOf(gp);
	}

	static String format(int gp)
	{
		if (gp <= 0)
		{
			return "";
		}
		if (gp >= 1_000_000 && gp % 1_000_000 == 0)
		{
			return gp / 1_000_000 + "m";
		}
		if (gp >= 1_000 && gp % 1_000 == 0)
		{
			return gp / 1_000 + "k";
		}
		return String.valueOf(gp);
	}
}
