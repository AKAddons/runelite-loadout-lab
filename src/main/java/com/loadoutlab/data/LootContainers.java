package com.loadoutlab.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

import static com.loadoutlab.data.JsonResources.*;

/**
 * Loot containers worth carrying (ISS-01M5JJ0Z4M988DR0M0ZGJHD139, Andrew 2026-10-22: "for
 * monsters that are predomant droppers of things that can be bags, suggest
 * to bring bags in inventory" - "killerwatts have a predominant herb drop
 * table so i wish i had my herb sack before"). Which monsters drop enough
 * of what a container holds comes from loot_containers.json, generated from
 * the wiki's drop data by scripts/gen_loot_containers.py. Suggested only
 * when owned.
 */
public final class LootContainers {
	@lombok.AllArgsConstructor
	private static final class Container {
		final String name;
		final int[] ids;
		final String holds;
		final Map<String, Double> perKill;
	}

	private static final List<Container> CONTAINERS = new ArrayList<>();
	private static Container lootingBag;

	static {
		JsonObject root = objectOrThrow("loot_containers.json");
		for (JsonElement e : root.getAsJsonArray("containers")) {
			JsonObject row = e.getAsJsonObject();
			Map<String, Double> perKill = new HashMap<>();
			row.getAsJsonObject("monsters").entrySet()
				.forEach(m -> perKill.put(m.getKey(), m.getValue().getAsDouble()));
			CONTAINERS.add(new Container(string(row, "name"), ints(row.getAsJsonArray("ids")),
				string(row, "holds"), perKill));
		}
		JsonObject bag = root.getAsJsonObject("lootingBag");
		lootingBag = new Container(string(bag, "name"), ints(bag.getAsJsonArray("ids")), "loot", Map.of());
		if (CONTAINERS.isEmpty()) {
			throw new IllegalStateException("loot_containers.json loaded empty");
		}
	}

	private LootContainers() {
	}

	/** The containers this monster's drops are worth that you own, each a
	 * trip-kit row: id (the one you own), name, qty, why. The looting bag
	 * rides on a wilderness trip. */
	public static List<Map<String, Object>> picks(MonsterStats monster, IntPredicate owns, boolean wilderness) {
		List<Map<String, Object>> out = new ArrayList<>();
		if (monster == null || owns == null) {
			return out;
		}
		String key = Names.lower(monster.getName());
		for (Container container : CONTAINERS) {
			Double rate = container.perKill.get(key);
			int id = rate == null ? -1 : ownedId(container, owns);
			if (id > 0) {
				out.add(row(id, container.name, Names.cap(container.holds) + " drop here (" + often(rate) + ") - the "
					+ Names.lower(container.name) + " holds them"));
			}
		}
		if (wilderness || monster.isWildernessMonster()) {
			int id = ownedId(lootingBag, owns);
			if (id > 0) {
				out.add(row(id, lootingBag.name, "Wilderness: the looting bag stores loot as you fight"));
			}
		}
		return out;
	}

	private static int ownedId(Container container, IntPredicate owns) {
		return Arrays.stream(container.ids).filter(owns).findFirst().orElse(-1);
	}

	/** "about 1 per 4 kills", or "about 2 per kill" for the generous. */
	private static String often(double perKill) {
		return perKill >= 1 ? String.format("about %.0f per kill", perKill)
			: "about 1 per " + Math.max(1, Math.round(1 / perKill)) + " kills";
	}

	private static Map<String, Object> row(int id, String name, String why) {
		return map("id", id, "name", name, "qty", 1, "why", why, "owned", true);
	}
}
