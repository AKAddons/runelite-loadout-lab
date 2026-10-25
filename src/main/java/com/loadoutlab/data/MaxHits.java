package com.loadoutlab.data;

import java.util.Arrays;
import java.util.regex.*;

/**
 * The wiki infobox's listed max hits, per attack family (ISS-01M5FZ3KYCGEMMX1A45D3J9NQ9).
 * The corpus field is free text - "13 (Ranged)", "30-37 (Melee)", "7x2 (Magic)",
 * "26 (melee)<br/> 15x2 (ranged)<br/> 65 (special attack)", "32 total 4+8+20" -
 * so each entry is read for its value and labels: ranges and alternatives take
 * the larger, multi-hits count their total (same expected damage), specials and
 * dragonfire are left to curated data, and an unlabelled value is GENERAL.
 */
public final class MaxHits {
	public enum Family { MELEE, RANGED, MAGIC, GENERAL }

	public static final MaxHits NONE = new MaxHits(new int[]{-1, -1, -1, -1});

	private static final Pattern ENTRY = Pattern.compile(
		"(\\d+)(?:\\s*([-/+])\\s*(\\d+))?(?:\\s*x\\s*(\\d+))?(?!\\s*%)\\+?\\??\\s*((?:\\([^)]*\\)\\s*)*)"
			+ "(melee|ranged|magic)?", Pattern.CASE_INSENSITIVE);
	private static final Pattern LABEL = Pattern.compile("\\(([^)]*)\\)");
	private static final Pattern TIMES = Pattern.compile("x\\s*(\\d+)");
	private static final Pattern MELEE = Pattern.compile("\\b(melee|slash|crush|stab|bite)\\b");
	private static final Pattern RANGED = Pattern.compile("\\b(ranged|range|flies|missiles?)\\b");
	private static final Pattern MAGIC = Pattern.compile("\\b(magic|magical|fire blast|fire wave|wind strike)\\b");
	private static final Pattern SPECIAL = Pattern.compile(
		"special|charge|dragonfire|recoil|stomp|phase|aoe|knockback|whirlwind|cloud|crash|draining|typeless|not defeated|attack\\b(?<!magic attack)");
	private static final Pattern GENERAL = Pattern.compile(
		"normal|standard|default|regular|auto|approx|unconfirmed");

	private final int[] max;

	private MaxHits(int[] max) { this.max = max; }

	/** The listed max for this family, or -1 when the wiki lists none. */
	public int of(Family family) { return max[family.ordinal()]; }

	public boolean isEmpty() { return Arrays.stream(max).allMatch(v -> v < 0); }

	public static MaxHits parse(String raw) {
		if (raw == null || raw.isEmpty()) { return NONE; }
		int[] max = {-1, -1, -1, -1};
		String text = raw.replace('\u007f', ' ').replace("&thinsp;", " ").replace("&thinsp", " ")
			.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]*>", "\n").replace('*', '\n');
		for (String line : text.split("\n")) {
			// "32 total 4+8+20": the total is the hit; the parts are its pieces.
			line = line.trim().replaceAll("(?i)^(\\d+)\\s*total.*", "$1");
			Matcher m = ENTRY.matcher(line);
			while (m.find()) { record(max, m); }
		}
		return new MaxHits(max);
	}

	private static void record(int[] max, Matcher m) {
		int value = Integer.parseInt(m.group(1));
		if (m.group(3) != null) {
			int other = Integer.parseInt(m.group(3));
			value = "+".equals(m.group(2)) ? value + other : Math.max(value, other);
		}
		if (m.group(4) != null) { value *= Integer.parseInt(m.group(4)); }
		boolean[] families = new boolean[4];
		boolean labelled = false;
		Matcher label = LABEL.matcher(m.group(5));
		while (label.find()) {
			String text = label.group(1).trim().toLowerCase();
			Matcher times = TIMES.matcher(text);
			if (times.matches()) {
				value *= Integer.parseInt(times.group(1));
				continue;
			}
			if (text.contains("attack") && text.contains("not") || text.contains("doesn't attack")
				|| SPECIAL.matcher(text).find()) {
				return; // a special, dragonfire or a non-attacker - not a regular max
			}
			boolean melee = MELEE.matcher(text).find();
			boolean ranged = RANGED.matcher(text).find();
			boolean magic = MAGIC.matcher(text).find();
			if (!melee && !ranged && !magic && !GENERAL.matcher(text).find()) {
				return; // a named attack we cannot place
			}
			families[Family.MELEE.ordinal()] |= melee;
			families[Family.RANGED.ordinal()] |= ranged;
			families[Family.MAGIC.ordinal()] |= magic;
			labelled |= melee || ranged || magic;
		}
		if (m.group(6) != null) {
			Matcher word = MELEE.matcher(m.group(6).toLowerCase());
			families[word.find() ? 0 : "ranged".equalsIgnoreCase(m.group(6)) ? 1 : 2] = true;
			labelled = true;
		}
		if (value <= 0) { return; }
		if (!labelled) { families[Family.GENERAL.ordinal()] = true; }
		for (int f = 0; f < 4; f++) {
			if (families[f]) { max[f] = Math.max(max[f], value); }
		}
	}
}
