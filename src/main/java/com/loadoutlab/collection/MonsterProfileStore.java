package com.loadoutlab.collection;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.MonsterStats;
import java.util.*;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import net.runelite.client.config.ConfigManager;

/**
 * Per-monster user profiles: the preferences the optimizer cannot infer,
 * remembered per mob. Each profile carries PINS (slot -> item the player
 * always brings THERE), a free-text NOTE, and extra ITEMS unioned into
 * the Show-in-bank / Filter-bank sets (trip supplies no suggestion would
 * contain). Pins and filter items are SCOPED: "ALL" applies to every
 * style card, or a specific style ("MELEE"/"RANGED"/"MAGIC") applies to
 * that card only - a super combat for melee, a ranged potion for ranged.
 * The effective view overlays ALL with the style scope (style wins a
 * pin-slot collision).
 *
 * <p>Scope-free config in v1 like dreams/exclusions: trip preferences
 * follow the player. Empty profiles are pruned on save.
 */
public class MonsterProfileStore implements com.loadoutlab.model.CommandEngine.MobProfiles, ScopedStore {
	static final String CONFIG_GROUP = "loadoutlab";
	static final String KEY = "monsterProfiles";

	/** Per-character scope ("std"/"seasonal" plus ".<accountHash>"). Global
	 * before 0.4.1, which is why a main and a test account shared this list. */
	private String worldScope;

	private String key() {
		return ScopedKeys.key(worldScope, KEY);
	}

	public synchronized void loadScope(String scope) {
		this.worldScope = scope;
		load();
	}
	/** The every-style scope key. Style scopes use CombatStyle names. */
	public static final String ALL = "ALL";

	/** Serialized form: scope -> slot-name -> id for pins; scope -> id ->
	 * display name for filter items (names captured at add time - id
	 * resolution later needs the client thread). */
	private Stored stored(int monsterId) {
		return profiles.computeIfAbsent(MonsterStats.profileIdOf(monsterId), id -> new Stored());
	}

	private Stored get(int monsterId) {
		return profiles.get(MonsterStats.profileIdOf(monsterId));
	}

	private static final class Stored {
		Map<String, Map<String, Integer>> pins;
		String note;
		Map<String, Map<Integer, String>> filterItems;
		/** Per-mob simulated items (id -> add-time name) - counted as
		 * owned for THIS mob only. */
		Map<Integer, String> sims;
		/** Pinned autocast spell name for the magic card ("" / null = auto). */
		String spell;
		/** Pinned SPEC weapon id (0 / null = auto-pick). */
		Integer spec;
		/** Per-mob exclusions: scope -> item ids the suggestions here must
		 * never contain (the global exclusion list handles "everywhere"). */
		Map<String, Set<Integer>> exclusions;
		/** Per-mob trip-supply overrides: TripSupplies category ->
		 * mode/option key ("DETECT_BEST", "NONE", "SANFEW_SERUM"...).
		 * Absent category = the wrench-panel default applies. */
		Map<String, String> supplies;
		/** Degrades chip: skip degradable gear here (null = panel default). */
		Boolean skipDegradable;
	}

	private Set<Integer> degradableIds = Set.of();

	public synchronized void setDegradableIds(Set<Integer> ids) {
		degradableIds = ids;
	}

	public synchronized boolean skipDegradable(int monsterId, boolean fallback) {
		return read(monsterId, p -> p.skipDegradable, fallback);
	}

	public synchronized void setSkipDegradable(int monsterId, boolean skip) {
		stored(monsterId).skipDegradable = skip;
		save();
	}

	private final ConfigManager configManager;
	private final Gson gson;
	private final Map<Integer, Stored> profiles = new HashMap<>();

	public MonsterProfileStore(ConfigManager configManager, Gson gson) {
		this.configManager = configManager;
		this.gson = gson;
		load();
	}

	/** Re-read from config - the active RuneLite profile may have changed. */
	/** The reset-customisations control: every profile in this scope goes. */
	public synchronized void clear() {
		profiles.clear();
		save();
	}

	public synchronized void reload() {
		load();
	}

	private void load() {
		profiles.clear();
		Map<Integer, Stored> stored = ScopedKeys.parse(gson, ScopedKeys.read(configManager, CONFIG_GROUP, worldScope, KEY),
			new TypeToken<Map<Integer, Stored>>(){}.getType());
		if (stored != null) {
			// Entries saved under a phase variant's synthetic id (before
			// 2026-10-14 every write keyed the lensed card's raw id) fold
			// into the real monster's profile; its own entry wins.
			stored.forEach((id, profile) -> {
				if (id < MonsterStats.SYNTHETIC_ID_BASE) {
					profiles.put(id, profile);
				}
			});
			stored.forEach((id, profile) -> profiles.putIfAbsent(MonsterStats.profileIdOf(id), profile));
		}
	}

	/** A read-only copy, Map.of() for nothing. */
	private static <K, V> Map<K, V> copyOf(Map<K, V> m) {
		return m == null || m.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(m));
	}

	/** A profile field, or the fallback when the mob has no profile or the field is unset. */
	private <T> T read(int monsterId, Function<Stored, T> field, T fallback) {
		Stored profile = get(monsterId);
		T value = profile == null ? null : field.apply(profile);
		return value == null ? fallback : value;
	}

	/** One scope's inner collection of a scoped field, or null. */
	private <V> V inner(int monsterId, Function<Stored, Map<String, V>> field, String scope) {
		Map<String, V> scoped = read(monsterId, field, null);
		return scoped == null ? null : scoped.get(scope);
	}

	/** A scoped field for the manage menus: scope -> the copied inner
	 * collection, the empties dropped (the copy says null); Map.of() for none. */
	private static <V, W> Map<String, W> byScope(Map<String, V> scoped, Function<V, W> copy) {
		if (scoped == null || scoped.isEmpty()) {
			return Map.of();
		}
		Map<String, W> out = new LinkedHashMap<>();
		scoped.forEach((scope, value) -> {
			W copied = value == null ? null : copy.apply(value);
			if (copied != null) {
				out.put(scope, copied);
			}
		});
		return Collections.unmodifiableMap(out);
	}

	/** Effective pins for one style card: ALL overlaid by the style scope. */
	public synchronized Map<GearSlot, Integer> pinsFor(int monsterId, String style) {
		Map<String, Map<String, Integer>> scoped = read(monsterId, p -> p.pins, Map.of());
		EnumMap<GearSlot, Integer> pins = new EnumMap<>(GearSlot.class);
		copyPins(scoped.get(ALL), pins);
		copyPins(scoped.get(style), pins);
		return pins.isEmpty() ? Map.of() : Collections.unmodifiableMap(pins);
	}

	private static void copyPins(Map<String, Integer> from, EnumMap<GearSlot, Integer> into) {
		if (from == null) {
			return;
		}
		for (Map.Entry<String, Integer> entry : from.entrySet()) {
			try {
				into.put(GearSlot.valueOf(entry.getKey()), entry.getValue());
			} catch (IllegalArgumentException ignored) {
				// Unknown slot name in config: drop that pin.
			}
		}
	}

	/** Raw pins by scope, for the manage menu: scope -> slot -> id. */
	public synchronized Map<String, Map<GearSlot, Integer>> allPins(int monsterId) {
		return byScope(read(monsterId, p -> p.pins, null), slots -> {
			EnumMap<GearSlot, Integer> pins = new EnumMap<>(GearSlot.class);
			copyPins(slots, pins);
			return pins.isEmpty() ? null : Collections.unmodifiableMap(pins);
		});
	}

	public synchronized void pin(int monsterId, String scope, GearSlot slot, int itemId) {
		Stored profile = stored(monsterId);
		if (profile.pins == null) {
			profile.pins = new LinkedHashMap<>();
		}
		profile.pins.computeIfAbsent(scope, s -> new LinkedHashMap<>())
			.put(slot.name(), itemId);
		save();
	}

	public synchronized void unpin(int monsterId, String scope, GearSlot slot) {
		Map<String, Integer> slots = inner(monsterId, p -> p.pins, scope);
		if (slots != null) {
			slots.remove(slot.name());
			save();
		}
	}

	/** The user's note for this monster ("" when none). */
	public synchronized String note(int monsterId) {
		return read(monsterId, p -> p.note, "");
	}

	public synchronized void setNote(int monsterId, String note) {
		Stored profile = stored(monsterId);
		profile.note = note == null || note.trim().isEmpty() ? null : note.trim();
		save();
	}

	/** The pinned autocast spell for this monster ("" = auto-pick). */
	public synchronized String pinnedSpell(int monsterId) {
		return read(monsterId, p -> p.spell, "");
	}

	public synchronized void setPinnedSpell(int monsterId, String spellName) {
		Stored profile = stored(monsterId);
		profile.spell = spellName == null || spellName.trim().isEmpty()
			? null : spellName.trim();
		save();
	}

	/** The pinned SPEC weapon id for this monster (0 = auto-pick). */
	public synchronized int pinnedSpec(int monsterId) {
		return read(monsterId, p -> p.spec, 0);
	}

	public synchronized void setPinnedSpec(int monsterId, int itemId) {
		Stored profile = stored(monsterId);
		profile.spec = itemId <= 0 ? null : itemId;
		save();
	}


	/** Raw filter items by scope: scope -> id -> display name. */
	public synchronized Map<String, Map<Integer, String>> allFilterItems(int monsterId) {
		return byScope(read(monsterId, p -> p.filterItems, null), items -> items.isEmpty() ? null : copyOf(items));
	}

	/** Effective per-mob exclusions for one style card: ALL plus the style
	 * scope. The caller unions in the global exclusion list. */
	public synchronized Set<Integer> exclusionsFor(int monsterId, String style) {
		Stored profile = get(monsterId);
		if (profile == null) {
			return Set.of();
		}
		LinkedHashSet<Integer> merged = new LinkedHashSet<>();
		if (Boolean.TRUE.equals(profile.skipDegradable)) {
			merged.addAll(degradableIds);
		}
		for (String scope : new String[]{ALL, style}) {
			Set<Integer> ids = profile.exclusions == null ? null : profile.exclusions.get(scope);
			if (ids != null) {
				merged.addAll(ids);
			}
		}
		return merged.isEmpty() ? Set.of() : Collections.unmodifiableSet(merged);
	}

	/** Raw per-mob exclusions by scope, for the manage menu. */
	public synchronized Map<String, Set<Integer>> allExclusions(int monsterId) {
		return byScope(read(monsterId, p -> p.exclusions, null),
			ids -> ids.isEmpty() ? null : Collections.unmodifiableSet(new LinkedHashSet<>(ids)));
	}

	/** Per-mob sims: counted as owned for THIS mob only. */
	public synchronized Set<Integer> simsFor(int monsterId) {
		return allSims(monsterId).keySet();
	}

	/** Raw per-mob sims (id -> add-time name), for the manage menu. */
	public synchronized Map<Integer, String> allSims(int monsterId) {
		return copyOf(read(monsterId, p -> p.sims, null));
	}

	public synchronized void addSim(int monsterId, int itemId, String name) {
		Stored profile = stored(monsterId);
		if (profile.sims == null) {
			profile.sims = new LinkedHashMap<>();
		}
		profile.sims.put(itemId, name == null ? ("item " + itemId) : name);
		save();
	}

	public synchronized void removeMobSim(int monsterId, int itemId) {
		Map<Integer, String> sims = read(monsterId, p -> p.sims, null);
		if (sims != null) {
			sims.remove(itemId);
			save();
		}
	}

	/** Per-mob trip-supply overrides (category -> mode/option key);
	 * empty when the wrench-panel defaults apply untouched. */
	public synchronized Map<String, String> supplyOverrides(int monsterId) {
		return copyOf(read(monsterId, p -> p.supplies, null));
	}

	/** Set one category's override; null/empty choice returns the category
	 * to the wrench-panel default. */
	public synchronized void setSupplyOverride(int monsterId, String category, String choice) {
		if (choice == null || choice.isEmpty()) {
			Map<String, String> supplies = read(monsterId, p -> p.supplies, null);
			if (supplies != null) {
				supplies.remove(category);
				save();
			}
			return;
		}
		Stored profile = stored(monsterId);
		if (profile.supplies == null) {
			profile.supplies = new LinkedHashMap<>();
		}
		profile.supplies.put(category, choice);
		save();
	}

	public synchronized void exclude(int monsterId, String scope, int itemId) {
		Stored profile = stored(monsterId);
		if (profile.exclusions == null) {
			profile.exclusions = new LinkedHashMap<>();
		}
		profile.exclusions.computeIfAbsent(scope, s -> new LinkedHashSet<>()).add(itemId);
		save();
	}

	public synchronized void removeMobExclusion(int monsterId, String scope, int itemId) {
		Set<Integer> ids = inner(monsterId, p -> p.exclusions, scope);
		if (ids != null) {
			ids.remove(itemId);
			save();
		}
	}

	public synchronized void addFilterItem(int monsterId, String scope, int itemId, String name) {
		Stored profile = stored(monsterId);
		if (profile.filterItems == null) {
			profile.filterItems = new LinkedHashMap<>();
		}
		profile.filterItems.computeIfAbsent(scope, s -> new LinkedHashMap<>())
			.put(itemId, name == null ? ("item " + itemId) : name);
		save();
	}

	public synchronized void removeMobFilter(int monsterId, String scope, int itemId) {
		Map<Integer, String> items = inner(monsterId, p -> p.filterItems, scope);
		if (items != null) {
			items.remove(itemId);
			save();
		}
	}

	private void save() {
		Map<Integer, Stored> out = new LinkedHashMap<>();
		for (Map.Entry<Integer, Stored> entry : profiles.entrySet()) {
			Stored profile = entry.getValue();
			prune(profile.pins);
			prune(profile.filterItems);
			if (profile.exclusions != null) {
				profile.exclusions.values().removeIf(s -> s == null || s.isEmpty());
			}
			// EVERY field participates (field bug 2026-07-18: sims was
			// missing here, so a sims-only profile was judged empty and the
			// retainAll below erased the sim in the same call that added it
			// - "Sim here" appeared to do nothing).
			boolean empty = Stream.of(profile.pins, profile.filterItems, profile.exclusions, profile.sims, profile.supplies)
				.allMatch(m -> m == null || m.isEmpty())
				&& (profile.note == null || profile.note.isEmpty())
				&& (profile.spell == null || profile.spell.isEmpty())
				&& profile.spec == null && profile.skipDegradable == null;
			if (!empty) {
				out.put(entry.getKey(), profile);
			}
		}
		profiles.keySet().retainAll(out.keySet());
		configManager.setConfiguration(CONFIG_GROUP, key(), gson.toJson(out));
	}

	private static void prune(Map<String, ? extends Map<?, ?>> scoped) {
		if (scoped != null) {
			scoped.values().removeIf(m -> m == null || m.isEmpty());
		}
	}
}
