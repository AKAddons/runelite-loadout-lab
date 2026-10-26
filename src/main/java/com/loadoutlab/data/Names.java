package com.loadoutlab.data;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Name matching is lower-case, locale-free, everywhere. */
public final class Names {
	private Names() {
	}

	public static String lower(String s) { return s.toLowerCase(Locale.ROOT); }

	private static final Map<String, String> KEYS = new ConcurrentHashMap<>();

	/** lower() memoised, for the fixed markers the per-set checks match on
	 * (gear names, monster attributes): the profile had toLowerCase at 29% of
	 * the optimizer's samples, the same few hundred strings every trial. A
	 * plain get first: computeIfAbsent can lock a bin under the four workers. */
	public static String key(String s) {
		String k = KEYS.get(s);
		if (k == null) { KEYS.put(s, k = lower(s)); }
		return k;
	}

	/** The first letter upper-cased, the rest as it was; null and "" read "". */
	public static String cap(String s) {
		return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
