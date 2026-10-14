package com.loadoutlab.data;

import java.util.Locale;

/** Name matching is lower-case, locale-free, everywhere. */
public final class Names
{
	private Names()
	{
	}

	public static String lower(String s)
	{
		return s.toLowerCase(Locale.ROOT);
	}
}
