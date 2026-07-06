package com.loadoutlab.engine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterStats;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assume;
import org.junit.Test;

/**
 * Exports verification vectors for the official-calculator harness. Not a
 * test of behavior - it only runs when the LOADOUT_LAB_VECTORS environment
 * variable names an output directory (env, not -D: Gradle's forked test JVM
 * inherits the environment but not launcher system properties):
 *
 *   LOADOUT_LAB_VECTORS=/tmp/x ./gradlew test --tests "*OfficialVectorExport"
 *
 * writes {path}/vectors.json (scenario inputs for the weirdgloop harness)
 * and {path}/ours.json (this engine's numbers). scripts/verify_official.py
 * orchestrates the full comparison.
 */
public class OfficialVectorExport
{
	private static final String[][] SCENARIOS = {
		// name | monster | version | style | weapon | ammo | forced spell (both optional)
		{"whip-goblin", "Goblin", "", "MELEE", "Abyssal whip", null},
		{"tentacle-goblin", "Goblin", "", "MELEE", "Abyssal tentacle", null},
		{"fang-goblin", "Goblin", "", "MELEE", "Osmumten's fang", null},
		{"tentacle-dusk1", "Dusk", "First form", "MELEE", "Abyssal tentacle", null},
		{"granitehammer-dusk1", "Dusk", "First form", "MELEE", "Granite hammer", null},
		{"granitehammer-gargoyle", "Gargoyle", "Basement", "MELEE", "Granite hammer", null},
		{"barronite-greygolem", "Grey golem", "", "MELEE", "Barronite mace", null},
		{"eldermaul-dusk2", "Dusk", "Second form", "MELEE", "Elder maul", null},
		{"tbow-zulrah", "Zulrah", "Serpentine", "RANGED", "Twisted bow", "Dragon arrow"},
		{"msbi-goblin", "Goblin", "", "RANGED", "Magic shortbow (i)", "Amethyst arrow"},
		{"sang-goblin", "Goblin", "", "MAGIC", "Sanguinesti staff", null},
		{"shadow-zulrah", "Zulrah", "Serpentine", "MAGIC", "Tumeken's shadow", null},
		{"bonestaff-scurrius", "Scurrius", "", "MAGIC", "Bone staff", null},
		{"whip-abyssaldemon", "Abyssal demon", "Standard", "MELEE", "Abyssal whip", null},
		{"arclight-abyssaldemon", "Abyssal demon", "Standard", "MELEE", "Arclight", null},
		// Tormented demons: demonbane + elemental weakness (water 30)
		{"emberlight-td", "Tormented Demon", "1", "MELEE", "Emberlight", null},
		{"scorchingbow-td", "Tormented Demon", "1", "RANGED", "Scorching bow", "Dragon arrow"},
		{"bofa-td", "Tormented Demon", "1", "RANGED", "Bow of faerdhinen", null},
		{"eyeofayak-td", "Tormented Demon", "1", "MAGIC", "Eye of ayak", null},
		{"purging-demonbane-td", "Tormented Demon", "1", "MAGIC", "Purging staff", null, "Dark Demonbane"},
		{"kodai-demonbane-td", "Tormented Demon", "1", "MAGIC", "Kodai wand", null, "Dark Demonbane"},
		{"kodai-watersurge-td", "Tormented Demon", "1", "MAGIC", "Kodai wand", null, "Water Surge"},
		{"shadow-td", "Tormented Demon", "1", "MAGIC", "Tumeken's shadow", null},
	};

	@Test
	public void export() throws Exception
	{
		String dir = System.getenv("LOADOUT_LAB_VECTORS");
		Assume.assumeNotNull(dir);

		LoadoutData data = new DataService().load();
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		List<Map<String, Object>> vectors = new ArrayList<>();
		List<Map<String, Object>> ours = new ArrayList<>();

		for (String[] s : SCENARIOS)
		{
			if (s[4] == null)
			{
				continue; // placeholder rows
			}
			String name = s[0];
			MonsterStats monster = data.searchMonsters(s[1], 10).stream()
				.filter(m -> s[2] == null || s[2].isEmpty() || s[2].equalsIgnoreCase(m.getVersion()))
				.findFirst().orElse(null);
			GearItem weapon = byName(data, s[4]);
			if (monster == null || weapon == null)
			{
				continue;
			}
			CombatStyle style = CombatStyle.valueOf(s[3]);
			com.loadoutlab.data.SpellStats forcedSpell = s.length > 6 && s[6] != null
				? data.getSpells().stream().filter(sp -> sp.getName().equalsIgnoreCase(s[6])).findFirst().orElse(null)
				: null;
			EnumMap<GearSlot, GearItem> gear = new EnumMap<>(GearSlot.class);
			gear.put(GearSlot.WEAPON, weapon);
			List<Object> gearNames = new ArrayList<>();
			gearNames.add(gearRef(weapon));
			if (s[5] != null)
			{
				GearItem ammo = byName(data, s[5]);
				gear.put(GearSlot.AMMO, ammo);
				gearNames.add(gearRef(ammo));
			}

			OptimizationRequest request = new OptimizationRequest(
				monster, style, PlayerLevels.MAXED,
				prayersFor(style), forcedSpell, 0,
				CandidateMode.ALL_STANDARD, true, false,
				OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
			DpsResult result = new DpsCalculator().calculate(request, new Loadout(gear));
			if (result == null)
			{
				continue;
			}

			Map<String, Object> vector = new LinkedHashMap<>();
			vector.put("name", name);
			vector.put("monster", s[1]);
			vector.put("monsterVersion", s[2] == null ? "" : s[2]);
			vector.put("gear", gearNames);
			vector.put("prayers", prayerNames(style));
			if (!result.getSpellName().isEmpty())
			{
				vector.put("spell", result.getSpellName());
			}
			vectors.add(vector);

			Map<String, Object> mine = new LinkedHashMap<>();
			mine.put("name", name);
			mine.put("dps", result.getDps());
			mine.put("maxHit", result.getMaxHit());
			mine.put("accuracy", result.getAccuracy());
			mine.put("attackRoll", result.getAttackRoll());
			mine.put("spell", result.getSpellName());
			ours.add(mine);
		}

		try (FileWriter w = new FileWriter(dir + "/vectors.json"))
		{
			gson.toJson(vectors, w);
		}
		try (FileWriter w = new FileWriter(dir + "/ours.json"))
		{
			gson.toJson(ours, w);
		}
		System.out.println("exported " + vectors.size() + " vectors to " + dir);
	}

	private static Object gearRef(GearItem item)
	{
		return item.getVersion().isEmpty()
			? item.getName()
			: new String[]{item.getName(), item.getVersion()};
	}

	private static GearItem byName(LoadoutData data, String name)
	{
		return data.getGearItems().stream()
			.filter(g -> g.getName().equalsIgnoreCase(name) && g.isStandardGear())
			.findFirst().orElse(null);
	}

	private static PrayerBonuses prayersFor(CombatStyle style)
	{
		return PrayerBonuses.bestAvailable(PlayerLevels.MAXED);
	}

	private static List<String> prayerNames(CombatStyle style)
	{
		switch (style)
		{
			case RANGED: return List.of("RIGOUR");
			case MAGIC: return List.of("AUGURY", "MYSTIC_VIGOUR");
			default: return List.of("PIETY");
		}
	}
}
