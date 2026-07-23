package com.loadoutlab;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.BoostProfile;
import com.loadoutlab.engine.DpsResult;
import com.loadoutlab.engine.PlayerLevels;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.LinkBrowser;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * One-click "open this exact setup in the official wiki calculator":
 * builds the calculator's ImportableData shape from a shown result, POSTs
 * it to the wiki's shortlink service (the same endpoint the calculator's
 * own Share button uses - the ONLY way a full setup can ride a URL), and
 * opens {@code ?id=<shortlink>}. Strictly user-initiated; shapes verified
 * against weirdgloop/osrs-dps-calc source 2026-07-23 (IMPORT_VERSION 10).
 */
@Slf4j
public class WikiCalcLink
{
	static final String SHORTLINK_API = "https://tools.runescape.wiki/osrs-dps/shortlink";
	static final String CALC_URL = "https://tools.runescape.wiki/osrs-dps/?id=";

	/** Their Prayer enum ordinals, by the tier names our labels use. */
	private static final Map<String, Integer> PRAYER_IDS = Map.ofEntries(
		Map.entry("Burst of Strength", 0), Map.entry("Clarity of Thought", 1),
		Map.entry("Sharp Eye", 2), Map.entry("Mystic Will", 3),
		Map.entry("Superhuman Strength", 4), Map.entry("Improved Reflexes", 5),
		Map.entry("Hawk Eye", 6), Map.entry("Mystic Lore", 7),
		Map.entry("Ultimate Strength", 8), Map.entry("Incredible Reflexes", 9),
		Map.entry("Eagle Eye", 10), Map.entry("Mystic Might", 11),
		Map.entry("Chivalry", 12), Map.entry("Piety", 13),
		Map.entry("Rigour", 14), Map.entry("Augury", 15),
		Map.entry("Deadeye", 19), Map.entry("Mystic Vigour", 20));

	/** Their Potion enum ordinals by OUR boost labels. The calculator has
	 * no divine variants (identical numbers), so divines map to the base. */
	private static final Map<String, int[]> POTION_IDS = Map.ofEntries(
		Map.entry("Super combat", new int[]{14}),
		Map.entry("Divine super combat", new int[]{14}),
		Map.entry("Ranging potion", new int[]{7}),
		Map.entry("Divine ranging potion", new int[]{7}),
		Map.entry("Magic potion", new int[]{4}),
		Map.entry("Divine magic potion", new int[]{4}),
		Map.entry("Saturated heart", new int[]{8}),
		Map.entry("Imbued heart", new int[]{3}),
		Map.entry("Super ranging", new int[]{13}),
		Map.entry("Super magic", new int[]{15}),
		Map.entry("Overload", new int[]{5}),
		Map.entry("Overload (+)", new int[]{6}),
		Map.entry("Smelling salts", new int[]{9}),
		Map.entry("Attack & strength potions", new int[]{1, 10}));

	private final OkHttpClient http;
	private final Gson gson;

	public WikiCalcLink(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	/** POST the setup to the shortlink service, then open the calculator
	 * on the returned id. Fire-and-forget; failures only log. */
	public void open(MonsterStats mob, DpsResult shown, int dartId, String assumes,
		PlayerLevels real, PlayerLevels live, boolean onSlayerTask, boolean inWilderness)
	{
		Map<String, Object> data = payload(mob, shown, dartId, assumes,
			real, live, onSlayerTask, inWilderness);
		Request request = new Request.Builder()
			.url(SHORTLINK_API)
			.post(RequestBody.create(MediaType.parse("application/json"), gson.toJson(data)))
			.build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.warn("wiki calc shortlink failed", e);
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (Response r = response)
				{
					if (!r.isSuccessful() || r.body() == null)
					{
						log.warn("wiki calc shortlink HTTP {}", r.code());
						return;
					}
					JsonObject body = gson.fromJson(r.body().string(), JsonObject.class);
					if (body == null || !body.has("data"))
					{
						log.warn("wiki calc shortlink: unexpected response shape");
						return;
					}
					LinkBrowser.browse(CALC_URL + body.get("data").getAsString());
				}
			}
		});
	}

	/** The ImportableData document, built pure for testability. */
	static Map<String, Object> payload(MonsterStats mob, DpsResult shown, int dartId,
		String assumes, PlayerLevels real, PlayerLevels live,
		boolean onSlayerTask, boolean inWilderness)
	{
		List<Integer> prayers = new ArrayList<>();
		BoostProfile boost = null;
		List<Integer> potions = new ArrayList<>();
		for (String token : (assumes == null ? "" : assumes).split(" \\+ "))
		{
			Integer prayerId = PRAYER_IDS.get(token);
			if (prayerId != null)
			{
				prayers.add(prayerId);
				continue;
			}
			int[] potionIds = POTION_IDS.get(token);
			if (potionIds != null)
			{
				for (int id : potionIds)
				{
					potions.add(id);
				}
			}
			for (BoostProfile profile : BoostProfile.values())
			{
				if (profile.toString().equals(token))
				{
					boost = profile;
				}
			}
		}

		Map<String, Object> equipment = new LinkedHashMap<>();
		for (GearSlot slot : GearSlot.values())
		{
			GearItem worn = shown.getLoadout().get(slot);
			int id = worn != null ? worn.getId()
				: slot == GearSlot.AMMO && dartId > 0 ? dartId : -1;
			if (id > 0)
			{
				// Plain maps/lists ONLY in this document: gson's map adapter
				// reflects the runtime type, and the JDK's private
				// Collections$*/ImmutableCollections classes throw
				// InaccessibleObjectException under the module system
				// (field-found 2026-07-23 - the button silently died).
				Map<String, Object> idMap = new LinkedHashMap<>();
				idMap.put("id", id);
				equipment.put(slot.getJsonName(), idMap);
			}
		}

		Map<String, Object> loadout = new LinkedHashMap<>();
		loadout.put("name", "Loadout Lab");
		loadout.put("skills", skillsMap(real));
		loadout.put("boosts", boostsMap(boost, real, live));
		loadout.put("equipment", equipment);
		loadout.put("prayers", prayers);
		Map<String, Object> buffs = new LinkedHashMap<>();
		buffs.put("potions", potions);
		buffs.put("onSlayerTask", onSlayerTask);
		buffs.put("inWilderness", inWilderness);
		loadout.put("buffs", buffs);
		Map<String, String> style = styleOf(shown.getAttackType());
		if (style != null)
		{
			loadout.put("style", style);
		}
		String spell = shown.getSpellName();
		if (spell != null && !spell.isEmpty())
		{
			Map<String, Object> spellMap = new LinkedHashMap<>();
			spellMap.put("name", spell);
			loadout.put("spell", spellMap);
		}

		Map<String, Object> monster = new LinkedHashMap<>();
		monster.put("id", mob.getId());
		monster.put("version", mob.getVersion());
		Map<String, Object> inputs = new LinkedHashMap<>();
		inputs.put("defenceReductions", new LinkedHashMap<>());
		monster.put("inputs", inputs);

		Map<String, Object> data = new LinkedHashMap<>();
		data.put("serializationVersion", 10);
		List<Map<String, Object>> loadouts = new ArrayList<>();
		loadouts.add(loadout);
		data.put("loadouts", loadouts);
		data.put("selectedLoadout", 0);
		data.put("monster", monster);
		return data;
	}

	/** Their PlayerSkills shape. Mining/herblore are not tracked here -
	 * 99 keeps the rare pickaxe-dependent checks permissive. */
	private static Map<String, Integer> skillsMap(PlayerLevels levels)
	{
		Map<String, Integer> skills = new LinkedHashMap<>();
		skills.put("atk", levels.getAttack());
		skills.put("str", levels.getStrength());
		skills.put("def", levels.getDefence());
		skills.put("hp", levels.getHitpoints());
		skills.put("ranged", levels.getRanged());
		skills.put("magic", levels.getMagic());
		skills.put("prayer", levels.getPrayer());
		skills.put("mining", 99);
		skills.put("herblore", 99);
		return skills;
	}

	/** The per-skill boost DELTAS the calculator adds to the base skills -
	 * computed from our own boost model so the math matches exactly. */
	private static Map<String, Integer> boostsMap(BoostProfile boost,
		PlayerLevels real, PlayerLevels live)
	{
		PlayerLevels boosted = boost == null ? real : boost.apply(real, live);
		Map<String, Integer> deltas = new LinkedHashMap<>();
		deltas.put("atk", boosted.getAttack() - real.getAttack());
		deltas.put("str", boosted.getStrength() - real.getStrength());
		deltas.put("def", boosted.getDefence() - real.getDefence());
		deltas.put("hp", 0);
		deltas.put("ranged", boosted.getRanged() - real.getRanged());
		deltas.put("magic", boosted.getMagic() - real.getMagic());
		deltas.put("prayer", 0);
		deltas.put("mining", 0);
		deltas.put("herblore", 0);
		return deltas;
	}

	/** Their PlayerCombatStyle {type, stance} from our attack-type strings:
	 * "slash (aggressive)", "ranged rapid - dragon dart", "magic: Fire
	 * Surge". The style NAME is omitted - the calculator's math reads only
	 * type and stance. */
	static Map<String, String> styleOf(String attackType)
	{
		if (attackType == null || attackType.isEmpty())
		{
			return null;
		}
		Map<String, String> style = new LinkedHashMap<>();
		if (attackType.startsWith("magic"))
		{
			style.put("type", "magic");
			style.put("stance", attackType.startsWith("magic:") ? "Autocast" : "Accurate");
			return style;
		}
		if (attackType.startsWith("ranged"))
		{
			style.put("type", "ranged");
			style.put("stance", attackType.contains("rapid") ? "Rapid" : "Accurate");
			return style;
		}
		int cut = attackType.indexOf(" (");
		style.put("type", cut < 0 ? attackType : attackType.substring(0, cut));
		String stance = cut < 0 ? "aggressive"
			: attackType.substring(cut + 2, attackType.indexOf(')', cut));
		style.put("stance", Character.toUpperCase(stance.charAt(0)) + stance.substring(1));
		return style;
	}
}
