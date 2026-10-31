package com.loadoutlab.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Gear-corpus search used only by the test suite. Kept out of main source
 * so it does not count against the Plugin Hub bot's 200k token budget; the
 * ranking is the production version verbatim, driven off the public
 * {@link LoadoutData#getGearItems()} corpus view.
 */
public final class GearSearchTestSupport {
	private GearSearchTestSupport() {
	}

	/**
	 * Item-name search: exact label/name (or id) first, then prefix, then
	 * substring - same ranking shape as {@link LoadoutData#searchMonsters}.
	 */
	public static List<Gear> searchGear(LoadoutData data, String query, int limit) {
		String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (text.isEmpty()) {
			return Collections.emptyList();
		}

		ArrayList<Gear> exact = new ArrayList<>();
		ArrayList<Gear> prefix = new ArrayList<>();
		ArrayList<Gear> contains = new ArrayList<>();
		for (Gear item : data.getGearItems()) {
			String label = item.labelLower();
			if (label.equals(text) || item.getNameLower().equals(text)
				|| String.valueOf(item.getId()).equals(text)) {
				exact.add(item);
			} else if (label.startsWith(text)) {
				prefix.add(item);
			} else if (label.contains(text)) {
				contains.add(item);
			}
		}

		ArrayList<Gear> result = new ArrayList<>(limit);
		addLimited(result, exact, limit);
		addLimited(result, prefix, limit);
		addLimited(result, contains, limit);
		return result;
	}

	private static <T> void addLimited(List<T> target, List<T> source, int limit) {
		for (T entry : source) {
			if (target.size() >= limit) {
				return;
			}
			target.add(entry);
		}
	}
}
