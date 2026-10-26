// Derived from guccifurs/best-dps (BSD-2-Clause, Copyright (c) 2026, Noid) - see licenses/best-dps-LICENSE.
package com.loadoutlab.data;

import java.util.*;
import java.util.stream.Collectors;

import lombok.Getter;

public final class LoadoutData {
	@Getter
	private final List<GearItem> gearItems;
	@Getter
	private final List<MonsterStats> monsters;
	@Getter
	private final List<SpellStats> spells;
	private final Map<Integer, GearItem> gearById;
	private final Map<Integer, Integer> variantToBase;
	/** Corpus partitioned by equip slot, corpus order preserved - so slot
	 * scans (every optimizer pool build) skip the other ~5k rows. */
	private final Map<GearSlot, List<GearItem>> gearBySlot;

	LoadoutData(
		List<GearItem> gearItems,
		List<MonsterStats> monsters,
		List<SpellStats> spells,
		Map<Integer, GearItem> gearById,
		Map<Integer, Integer> variantToBase) {
		this.gearItems = Collections.unmodifiableList(gearItems);
		this.monsters = Collections.unmodifiableList(monsters);
		this.spells = Collections.unmodifiableList(spells);
		this.gearById = Collections.unmodifiableMap(gearById);
		this.variantToBase = Collections.unmodifiableMap(variantToBase);
		var bySlot = new EnumMap<GearSlot, List<GearItem>>(GearSlot.class);
		for (GearSlot slot : GearSlot.values()) {
			bySlot.put(slot, Collections.unmodifiableList(this.gearItems.stream()
				.filter(item -> item.getSlot() == slot).collect(Collectors.toList())));
		}
		this.gearBySlot = Collections.unmodifiableMap(bySlot);
	}

	/**
	 * A view of this dataset containing only free-to-play gear and standard-
	 * spellbook spells (monsters unchanged) - drives the non-members filter.
	 * Ancient/Arceuus books are members-only wholesale; the F2P card was
	 * recommending Arceuus Dark Demonbane (audit A3.5/A1.2). Members spells
	 * WITHIN the standard book still pass - a finer F2P spell table can
	 * tighten that later.
	 */
	public LoadoutData freeToPlayView() {
		List<GearItem> free = gearItems.stream().filter(g -> !g.isMembers()).collect(Collectors.toList());
		Map<Integer, GearItem> byId = new HashMap<>();
		free.forEach(g -> byId.put(g.getId(), g));
		return new LoadoutData(free, monsters, spells.stream()
			.filter(spell -> "standard".equalsIgnoreCase(spell.getSpellbook())).collect(Collectors.toList()),
			byId, variantToBase);
	}

	/**
	 * True for ornament/locked/degraded variants of a base item with the
	 * same stats - never suggested; the base version stands in for them.
	 */
	public boolean isVariant(int itemId) { return variantToBase.containsKey(itemId); }

	/**
	 * Owned quantities with every variant also credited to its base item,
	 * so an owned "Abyssal whip (or)" satisfies an "Abyssal whip" pick.
	 */
	public Map<Integer, Integer> canonicalizeOwned(Map<Integer, Integer> owned) {
		Map<Integer, Integer> result = new HashMap<>(owned);
		for (var entry : owned.entrySet()) {
			Integer base = variantToBase.get(entry.getKey());
			if (base != null) result.merge(base, entry.getValue(), Integer::sum);
		}
		return result;
	}

	/**
	 * Community nicknames and activity names -> the monster to show.
	 * Raids and wave activities map to their final boss - a defensible
	 * anchor for "what do I wear there". Keys and values are in
	 * normalizeQuery form.
	 */
	private static final Map<String, String> SEARCH_ALIASES = new HashMap<>();
	/** Extra search words for one version ("name|version" -> words): the
	 * Wardens' "p2 phase 2" (ISS-01M5HKWBZNJF0V8ZD58FH9XS5B). */
	private static final Map<String, String> VERSION_TERMS = new HashMap<>();

	static {
		com.google.gson.JsonObject aliases = JsonResources.objectOrThrow("search_aliases.json");
		JsonResources.stringMap(aliases, "aliases", SEARCH_ALIASES);
		JsonResources.stringMap(aliases, "version_terms", VERSION_TERMS);
	}

	public List<MonsterStats> searchMonsters(String query, int limit) {
		String text = query == null ? "" : MonsterStats.normalizeQuery(query.trim());
		text = SEARCH_ALIASES.getOrDefault(text, text);
		if (text.isEmpty()) return List.of();

		var exact = new ArrayList<MonsterStats>();
		var prefix = new ArrayList<MonsterStats>();
		var contains = new ArrayList<MonsterStats>();
		for (MonsterStats monster : monsters) {
			String name = MonsterStats.normalizeQuery(monster.getName());
			if (name.equals(text) || String.valueOf(monster.getId()).equals(text)) {
				exact.add(monster);
			} else if (name.startsWith(text)) {
				prefix.add(monster);
			} else if (monster.searchText().contains(text) || wordsMatch(text, monster)) contains.add(monster);
		}

		// Among same-name versions, surface the fight a player MEANS by the
		// bare name first: post-quest/normal over quest, Awakened, Enraged,
		// Entry/Hard mode or Deep Delve rows (which sort first alphabetically
		// and used to be the silent default for vorkath, the DT2 four, zuk
		// and verzik). Stable sort - corpus order breaks ties, and every
		// version stays reachable further down the hit list.
		// ...and the same rule holds when the query is PARTIAL (field
		// report 2026-08-16: "whisperer" kept returning the Awakened row -
		// "the whisperer" is not an exact match for it, so the version
		// sort never ran on the bucket it landed in).
		exact.sort(Comparator.comparingInt(m -> versionTier(m.getVersion())));
		prefix.sort(Comparator.comparingInt(m -> versionTier(m.getVersion())));
		contains.sort(Comparator.comparingInt(m -> versionTier(m.getVersion())));

		var result = new ArrayList<MonsterStats>(limit);
		addLimited(result, exact, limit);
		addLimited(result, prefix, limit);
		addLimited(result, contains, limit);
		return result;
	}

	/** Every typed word starts a word of the monster's name, version, id or
	 * version terms, a trailing "s" forgiven - "tumeken warden" ("tumekens
	 * warden"), "wardens", "elidinis active", "warden p2" (Andrew 2026-10-22:
	 * the phase-2 Warden was unsearchable without "toa"). */
	private static boolean wordsMatch(String text, MonsterStats monster) {
		String terms = VERSION_TERMS.get(MonsterStats.normalizeQuery(monster.getName())
			+ "|" + MonsterStats.normalizeQuery(monster.getVersion()));
		String[] words = (monster.searchText() + (terms == null ? "" : " " + terms)).split(" +");
		for (String typed : text.split(" +")) {
			if (typed.isEmpty()) continue;
			boolean found = false;
			for (String word : words) {
				if (word.startsWith(typed)
					|| typed.length() > 2 && typed.endsWith("s") && word.equals(typed.substring(0, typed.length() - 1))) {
					found = true;
					break;
				}
			}
			if (!found) return false;
		}
		return true;
	}

	/** Lower = a better default for a bare-name search. */
	private static int versionTier(String version) {
		if (version == null || version.isEmpty()) return 1;
		String v = Names.lower(version);
		if (v.contains("post-quest")) {
			return 0;   // the everyday fight
		}
		if (v.contains("normal") || v.contains("serpentine")) {
			return 1;   // normal mode / Zulrah's spawn form
		}
		if (v.contains("awakened") || v.contains("enraged") || v.contains("entry mode")
			|| v.contains("deep delve") || v.contains("quest")) {
			return 3;   // scaled/hard variants and quest-only rows: never the default
		}
		// "Hard mode" stays neutral (tier 2): where a true Normal row exists it
		// outranks hard at tier 1 anyway, and ToB's Normal rows stat-collapse
		// INTO the Hard-labeled ones (same defensive block), so for Verzik the
		// hard-labeled row IS the everyday fight's numbers.
		return 2;
	}

	private static <T> void addLimited(List<T> target, List<T> source, int limit) {
		for (T entry : source) {
			if (target.size() >= limit) return;
			target.add(entry);
		}
	}

	/**
	 * All ids interchangeable with this one for OWNERSHIP purposes: the
	 * base plus every variant that canonicalizes to it - so a bank
	 * highlight for "Abyssal whip" also lights the (or) version.
	 */
	public Set<Integer> equivalentIds(int itemId) {
		Integer base = variantToBase.getOrDefault(itemId, itemId);
		Set<Integer> ids = new HashSet<>(List.of(itemId, base));
		variantToBase.forEach((variant, b) -> {
			if (b.equals(base)) ids.add(variant);
		});
		return ids;
	}

	public GearItem getGear(int id) { return gearById.get(id); }


	/** The corpus items for one equip slot, in getGearItems() order. */
	public List<GearItem> getGearItems(GearSlot slot) { return gearBySlot.get(slot); }


}
