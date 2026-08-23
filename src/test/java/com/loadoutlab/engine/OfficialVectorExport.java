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
		// name | monster | version | style | weapon | ammo | forced spell | extra gear (slayer helm implies on-task)
		{"whip-goblin", "Goblin", "", "MELEE", "Abyssal whip", null},
		// Elemental weakness stacking (field dispute 2026-07-23, first Wiki
		// calc button catch): the +severity% adds from the BASE roll after
		// slayer helm + DHW multiply - Iron dragon is earth-weak 50%.
		{"dhw-irondragon", "Iron dragon", "Standard", "MAGIC", "Dragon hunter wand", null, "Earth Surge", "Slayer helmet (i)"},
		// Revenant conditionals: byName resolves the Charged versions
		// (they precede Uncharged in the corpus); the harness infers the
		// inWilderness buff from the Revenant monster name.
		{"craws-revdemon", "Revenant demon", "", "RANGED", "Craw's bow", null},
		{"ursine-revdemon", "Revenant demon", "", "MELEE", "Ursine chainmace", null},
		{"avarice-msb-revdemon", "Revenant demon", "", "RANGED", "Magic shortbow", "Amethyst arrow", null, "Amulet of avarice"},
		{"tentacle-goblin", "Goblin", "", "MELEE", "Abyssal tentacle", null},
		{"fang-goblin", "Goblin", "", "MELEE", "Osmumten's fang", null},
		{"tentacle-dusk1", "Dusk", "First form", "MELEE", "Abyssal tentacle", null},
		{"granitehammer-dusk1", "Dusk", "First form", "MELEE", "Granite hammer", null},
		{"granitehammer-gargoyle", "Gargoyle", "Basement", "MELEE", "Granite hammer", null},
		{"barronite-greygolem", "Grey golem", "", "MELEE", "Barronite mace", null},
		{"eldermaul-dusk2", "Dusk", "Second form", "MELEE", "Elder maul", null},
		{"tbow-zulrah", "Zulrah", "Serpentine", "RANGED", "Twisted bow", "Dragon arrow"},
		{"tbow-hydra", "Alchemical Hydra", "", "RANGED", "Twisted bow", "Dragon arrow"},
		{"tbowslayer-graardor", "General Graardor", "", "RANGED", "Twisted bow", "Dragon arrow", null, "Slayer helmet (i)"},
		{"bofaslayer-graardor", "General Graardor", "", "RANGED", "Bow of faerdhinen", null, null, "Slayer helmet (i)"},
		// Crystal armour scaling (crystal bow / bofa only): helm +5% acc +2.5% dmg,
		// legs +10%/+5%, body +15%/+7.5%. Applied to the BASE roll/max hit,
		// before salve/slayer (in-game verified by the wiki calc devs).
		{"bofa-graardor", "General Graardor", "", "RANGED", "Bow of faerdhinen", null},
		{"bofahelm-graardor", "General Graardor", "", "RANGED", "Bow of faerdhinen", null, null, "Crystal helm"},
		{"bofabody-graardor", "General Graardor", "", "RANGED", "Bow of faerdhinen", null, null, "Crystal body"},
		{"bofalegs-graardor", "General Graardor", "", "RANGED", "Bow of faerdhinen", null, null, "Crystal legs"},
		{"bofaset-graardor", "General Graardor", "", "RANGED", "Bow of faerdhinen", null, null, "Crystal helm", "Crystal body", "Crystal legs"},
		{"cbowset-graardor", "General Graardor", "", "RANGED", "Crystal bow", null, null, "Crystal helm", "Crystal body", "Crystal legs"},
		// Slayer helm + crystal body/legs: flooring order (crystal before slayer) matters.
		{"bofasetslayer-graardor", "General Graardor", "", "RANGED", "Bow of faerdhinen", null, null, "Slayer helmet (i)", "Crystal body", "Crystal legs"},
		// Bowfa+crystal vs the dragon-dart blowpipe at the Enraged Warden
		// (field report 2026-08-06). VERDICT: both engines agree at
		// invocation 0 (bp ahead by ~1%); the user-visible flip to bowfa
		// comes from the official calc's ToA invocation defence scaling,
		// which we do not model. DART: routes through the official side's
		// itemVars (a dart as gear is a thrown WEAPON and replaces the bp).
		{"bofaset-warden3", "Tumeken's Warden", "Enraged", "RANGED", "Bow of faerdhinen", null, null, "Crystal helm", "Crystal body", "Crystal legs"},
		{"bp-warden3", "Tumeken's Warden", "Enraged", "RANGED", "Toxic blowpipe#Charged", "DART:Dragon dart"},
		// The same pair at raid level 300: the invocation defence scaling
		// ((250+invo)/250, INVO: token) must match the official engine on
		// both sides of the flip.
		{"bofaset-warden3-i300", "Tumeken's Warden", "Enraged", "RANGED", "Bow of faerdhinen", null, null, "Crystal helm", "Crystal body", "Crystal legs", "INVO:300"},
		{"bp-warden3-i300", "Tumeken's Warden", "Enraged", "RANGED", "Toxic blowpipe#Charged", "DART:Dragon dart", null, "INVO:300"},
		{"msbi-goblin", "Goblin", "", "RANGED", "Magic shortbow (i)", "Amethyst arrow"},
		{"sang-goblin", "Goblin", "", "MAGIC", "Sanguinesti staff", null},
		{"shadow-zulrah", "Zulrah", "Serpentine", "MAGIC", "Tumeken's shadow", null},
		{"bonestaff-scurrius", "Scurrius", "", "MAGIC", "Bone staff", null},
		{"whip-abyssaldemon", "Abyssal demon", "Standard", "MELEE", "Abyssal whip", null},
		{"arclight-abyssaldemon", "Abyssal demon", "Standard", "MELEE", "Arclight", null},
		// Ring of shadows vs Lightbearer on a full melee set (field dispute
		// 2026-08-08: "lightbearer should always beat ring of shadows in a
		// melee setup") - the official calc prices the SET side of the
		// argument: RoS's +4 slash / +2 str must be worth the same ~0.37
		// dps in both engines; the spec-value side is ours alone.
		{"rosfull-abyssaldemon", "Abyssal demon", "Standard", "MELEE", "Emberlight", null, null,
			"Neitiznot faceguard", "Fire cape", "Amulet of torture", "Rada's blessing 4",
			"Bandos chestplate", "Dragon defender", "Blood moon tassets", "Ferocious gloves",
			"Primordial boots", "Ring of shadows#Charged"},
		{"lbfull-abyssaldemon", "Abyssal demon", "Standard", "MELEE", "Emberlight", null, null,
			"Neitiznot faceguard", "Fire cape", "Amulet of torture", "Rada's blessing 4",
			"Bandos chestplate", "Dragon defender", "Blood moon tassets", "Ferocious gloves",
			"Primordial boots", "Lightbearer"},
		// Blood Moon + Noxious halberd (field dispute 2026-08-21: our set
		// 10.45 vs the live calc's 7.746 - a 1.35x gap on the same items).
		// Full set mirrors Andrew's exact report; the bare-weapon vector
		// bounds whether the delta is mechanic- or gear-level.
		{"noxfull-bloodmoon", "Blood Moon", "", "MELEE", "Noxious halberd", null, null,
			"Neitiznot faceguard", "Fire cape", "Amulet of torture", "Rada's blessing 4",
			"Bandos chestplate", "Blood moon tassets", "Ferocious gloves",
			"Primordial boots", "Berserker ring (i)"},
		{"noxhalberd-bloodmoon", "Blood Moon", "", "MELEE", "Noxious halberd", null},
		{"noxhalberd-eclipsemoon", "Eclipse Moon", "Regular", "MELEE", "Noxious halberd", null},
		{"macuahuitl-bluemoon", "Blue Moon", "", "MELEE", "Dual macuahuitl", null},
		// Negative armour off-styles (2026-08-21 armour-semantics fix):
		// ranged keeps the flat bonus WITH its floor; magic gets neither.
		{"tbow-bloodmoon", "Blood Moon", "", "RANGED", "Twisted bow", "Dragon arrow"},
		// Inquisitor full set + mace (field dispute 2026-08-21: cow melee
		// BiS 12.65 vs the calc's 12.03 - a ~5% gap smelling of the set
		// bonus multiplier).
		{"inqfull-cow", "Cow", "1", "MELEE", "Inquisitor's mace", null, null,
			"Inquisitor's great helm", "Infernal cape", "Amulet of rancour",
			"Rada's blessing 4", "Inquisitor's hauberk", "Avernic defender",
			"Inquisitor's plateskirt", "Ferocious gloves", "Avernic treads",
			"Ultor ring"},
		// Bisecting the 1-point max-hit residual: same set on stable
		// items (torture + primordials) vs the newer rancour/treads.
		{"inqstable-cow", "Cow", "1", "MELEE", "Inquisitor's mace", null, null,
			"Inquisitor's great helm", "Infernal cape", "Amulet of torture",
			"Rada's blessing 4", "Inquisitor's hauberk", "Avernic defender",
			"Inquisitor's plateskirt", "Ferocious gloves", "Primordial boots",
			"Ultor ring"},
		{"inqrancour-cow", "Cow", "1", "MELEE", "Inquisitor's mace", null, null,
			"Inquisitor's great helm", "Infernal cape", "Amulet of rancour",
			"Rada's blessing 4", "Inquisitor's hauberk", "Avernic defender",
			"Inquisitor's plateskirt", "Ferocious gloves", "Primordial boots",
			"Ultor ring"},
		{"kodai-firesurge-bloodmoon", "Blood Moon", "", "MAGIC", "Kodai wand", null, "Fire Surge"},
		// Twinflame double-hit (field dispute 2026-08-21: Fire giant magic
		// set 9.293 vs the calc's 8.793): their second splat is
		// trunc(0.4 x roll) at cast speed 6.
		{"twinflame-firegiant", "Fire giant", "Level 109", "MAGIC", "Twinflame staff", null, "Water Wave"},
		{"kodai-waterwave-firegiant", "Fire giant", "Level 109", "MAGIC", "Kodai wand", null, "Water Wave"},
		// The July ordering pin re-referee'd under the corrected
		// twinflame model: which staff wins vs the air-weak Dharok?
		{"twinflame-dharok", "Dharok the Wretched", "", "MAGIC", "Twinflame staff", null, "Wind Wave"},
		// The dhw/slayer/weakness stack (field 2026-08-21: adamant dragon
		// magic +0.23%; dhw-irondragon has sat at +0.3% since July).
		{"dhwslayer-adamantdragon", "Adamant dragon", "", "MAGIC", "Dragon hunter wand", null, "Earth Surge", "Slayer helmet (i)"},
		{"salvedhw-vorkath", "Vorkath", "Post-quest", "MAGIC", "Dragon hunter wand", null, "Fire Surge", "Salve amulet(ei)", "Occult necklace"},
		// Tbow scaling vs the Awakened Duke (field 2026-08-21: our max 79
		// vs the calc's 81, set -4.9%) - the magic-input read.
		{"tbowseeking-dukeawakened", "Duke Sucellus", "Awakened, Awake", "RANGED", "Twisted bow", "Seeking amethyst arrow"},
		{"tbowquiver-zuk", "TzKal-Zuk", "", "RANGED", "Twisted bow", "Dragon arrow", null, "Blessed dizana's quiver"},
		// Leviathan (melee-immune both engines) - its two live styles.
		{"tbow-leviathan", "The Leviathan", "Post-quest", "RANGED", "Twisted bow", "Dragon arrow"},
		// The full field BiS ranged set (2026-08-22: set dps drifts).
		{"levranged-field", "The Leviathan", "Post-quest", "RANGED", "Twisted bow", "Dragon arrow#Poison++", null,
			"Masori mask (f)", "Blessed dizana's quiver", "Necklace of rupture",
			"Masori body (f)", "Masori chaps (f)", "Zaryte vambraces",
			"Avernic treads", "Venator ring"},
		{"shadow-leviathan", "The Leviathan", "Post-quest", "MAGIC", "Tumeken's shadow", null},
		{"trident-leviathan", "The Leviathan", "Post-quest", "MAGIC", "Trident of the seas", null},
		// Demonbane vulnerability, all three deliveries vs the 70%-
		// resistant post-quest Duke (field 2026-08-21: melee +24%,
		// ranged +31%, magic ~3x).
		{"emberlight-duke", "Duke Sucellus", "Post-quest, Awake", "MELEE", "Emberlight", null},
		// Andrew's exact field melee set (2026-08-21: +24% vs the calc
		// while the minimal emberlight vector is exact).
		{"dukemelee-field", "Duke Sucellus", "Post-quest, Awake", "MELEE", "Emberlight", null, null,
			"Neitiznot faceguard", "Fire cape", "Amulet of torture", "Rada's blessing 4",
			"Bandos chestplate", "Dragon defender", "Blood moon tassets", "Ferocious gloves",
			"Primordial boots", "Berserker ring (i)"},
		{"scorching-duke", "Duke Sucellus", "Post-quest, Awake", "RANGED", "Scorching bow", "Dragon arrow"},
		{"purging-darkdemonbane-duke", "Duke Sucellus", "Post-quest, Awake", "MAGIC", "Purging staff", null, "Dark Demonbane"},
		{"swamptrident-dharok", "Dharok the Wretched", "", "MAGIC", "Trident of the swamp", null},
		// Tormented demons: demonbane + elemental weakness (water 30)
		{"emberlight-td", "Tormented Demon", "1", "MELEE", "Emberlight", null},
		{"scorchingbow-td", "Tormented Demon", "1", "RANGED", "Scorching bow", "Dragon arrow"},
		{"bofa-td", "Tormented Demon", "1", "RANGED", "Bow of faerdhinen", null},
		{"eyeofayak-td", "Tormented Demon", "1", "MAGIC", "Eye of ayak", null},
		// Eye of ayak away from demons (field ask 2026-08-22: is the item
		// itself right, or only its demon interaction?).
		{"eyeofayak-zulrah", "Zulrah", "Serpentine", "MAGIC", "Eye of ayak", null},
		{"eyeofayak-goblin", "Goblin", "", "MAGIC", "Eye of ayak", null},
		// The exact field magic BiS at Zulrah (2026-08-22: -0.44%).
		{"ayakfull-zulrah", "Zulrah", "Serpentine", "MAGIC", "Eye of ayak", null, null,
			"Slayer helmet (i)", "Imbued zamorak cape", "Occult necklace",
			"Rada's blessing 4", "Ancestral robe top", "Elidinis' ward (f)",
			"Ancestral robe bottom", "Confliction gauntlets", "Echo boots", "Magus ring"},
		{"purging-demonbane-td", "Tormented Demon", "1", "MAGIC", "Purging staff", null, "Dark Demonbane"},
		{"kodai-demonbane-td", "Tormented Demon", "1", "MAGIC", "Kodai wand", null, "Dark Demonbane"},
		{"kodai-watersurge-td", "Tormented Demon", "1", "MAGIC", "Kodai wand", null, "Water Surge"},
		{"shadow-td", "Tormented Demon", "1", "MAGIC", "Tumeken's shadow", null},
	};

	/** Sweep battery: the official engine adjudicates OUR optimizer's own
	 * full game-best picks per style for each of these monsters. */
	private static final String[][] SWEEP_MONSTERS = {
		{"Goblin", ""},
		{"Zulrah", "Serpentine"},
		{"Alchemical Hydra", ""},
		{"Tormented Demon", "1"},
		{"General Graardor", ""},
		{"Kree'arra", ""},
		{"Vorkath", "Post-quest"},
		{"Cerberus", ""},
		{"Scurrius", ""},
		{"Dusk", "First form"},
		{"Abyssal demon", "Standard"},
		{"Corporeal Beast", ""},
		{"Aberrant spectre", ""},
		{"Kalphite Queen", "Airborne"},
	};

	@Test
	public void sweep() throws Exception
	{
		String dir = System.getenv("LOADOUT_LAB_VECTORS");
		Assume.assumeNotNull(dir);
		Assume.assumeNotNull(System.getenv("LOADOUT_LAB_SWEEP"));

		LoadoutData data = new DataService().load();
		LoadoutOptimizer optimizer = new LoadoutOptimizer();
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		List<Map<String, Object>> vectors = new ArrayList<>();
		List<Map<String, Object>> ours = new ArrayList<>();

		for (String[] m : SWEEP_MONSTERS)
		{
			MonsterStats monster = data.searchMonsters(m[0], 10).stream()
				.filter(x -> m[1].isEmpty() || m[1].equalsIgnoreCase(x.getVersion()))
				.findFirst()
				.orElse(data.searchMonsters(m[0], 1).stream().findFirst().orElse(null));
			if (monster == null)
			{
				continue;
			}
			for (CombatStyle style : new CombatStyle[]{CombatStyle.MELEE, CombatStyle.RANGED, CombatStyle.MAGIC})
			{
				OptimizationRequest request = new OptimizationRequest(
					monster, style, PlayerLevels.MAXED,
					prayersFor(style), null, 0,
					CandidateMode.ALL_STANDARD, true, false,
					OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
				List<DpsResult> results = optimizer.optimize(data, request);
				if (results.isEmpty())
				{
					continue;
				}
				DpsResult result = results.get(0);
				String name = (m[0] + "-" + style).toLowerCase().replace(" ", "").replace("'", "");

				List<Object> gearNames = new ArrayList<>();
				for (GearItem item : result.getLoadout().getGear().values())
				{
					if (item != null)
					{
						gearNames.add(gearRef(item));
					}
				}
				Map<String, Object> vector = new LinkedHashMap<>();
				vector.put("name", name);
				vector.put("monster", m[0]);
				vector.put("monsterVersion", m[1]);
				vector.put("gear", gearNames);
				vector.put("prayers", prayerNames(style));
				if (!result.getSpellName().isEmpty())
				{
					vector.put("spell", result.getSpellName());
					if (result.getSpellName().contains("Demonbane"))
					{
						vector.put("markOfDarkness", true);
					}
				}
				vectors.add(vector);

				Map<String, Object> mine = new LinkedHashMap<>();
				mine.put("name", name);
				mine.put("dps", result.getDps());
				mine.put("maxHit", result.getMaxHit());
				mine.put("accuracy", result.getAccuracy());
				mine.put("attackRoll", result.getAttackRoll());
				mine.put("weapon", result.getLoadout().getWeapon().getName());
				ours.add(mine);
			}
		}
		try (FileWriter w = new FileWriter(dir + "/vectors.json"))
		{
			gson.toJson(vectors, w);
		}
		try (FileWriter w = new FileWriter(dir + "/ours.json"))
		{
			gson.toJson(ours, w);
		}
		System.out.println("sweep exported " + vectors.size() + " vectors");
	}

	@Test
	public void export() throws Exception
	{
		String dir = System.getenv("LOADOUT_LAB_VECTORS");
		Assume.assumeNotNull(dir);
		Assume.assumeTrue(System.getenv("LOADOUT_LAB_SWEEP") == null);

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
			// Our corpus collapses combat-identical versions (and blanks the
			// label), so fall back to the first name match; s[2] stays in
			// the vector for the official side's exact-version lookup.
			// Version compare is FIRST-TOKEN both sides: our loader
			// normalizes the wiki's compound strings ("Awakened, Awake"
			// -> "Awakened") while the official data keeps them raw -
			// the vector carries the raw string for their lookup.
			MonsterStats monster = data.searchMonsters(s[1], 10).stream()
				.filter(m -> s[2] == null || s[2].isEmpty()
					|| firstToken(s[2]).equalsIgnoreCase(firstToken(m.getVersion())))
				.findFirst()
				.orElse(data.searchMonsters(s[1], 1).stream().findFirst().orElse(null));
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
			Integer blowpipeDartId = null;
			if (s[5] != null && s[5].startsWith("DART:"))
			{
				// The blowpipe's loaded dart: itemVars on the official side
				// (a dart as gear is a thrown WEAPON there and would replace
				// the blowpipe); our side keeps the BlowpipeDarts assumption
				// - game-best assumes dragon, so name a dragon dart here.
				GearItem dart = byName(data, s[5].substring(5));
				blowpipeDartId = dart.getId();
			}
			else if (s[5] != null)
			{
				GearItem ammo = byName(data, s[5]);
				gear.put(GearSlot.AMMO, ammo);
				gearNames.add(gearRef(ammo));
			}
			boolean onTask = false;
			int toaInvocation = 0;
			for (int i = 7; i < s.length; i++)
			{
				if (s[i] == null)
				{
					continue;
				}
				if (s[i].startsWith("INVO:"))
				{
					toaInvocation = Integer.parseInt(s[i].substring(5));
					continue;
				}
				GearItem extra = byName(data, s[i]);
				gear.put(extra.getSlot(), extra);
				gearNames.add(gearRef(extra));
				onTask |= s[i].toLowerCase().contains("slayer helmet");
			}
			monster = MonsterMechanics.atToaInvocation(monster, toaInvocation);

			OptimizationRequest request = new OptimizationRequest(
				monster, style, PlayerLevels.MAXED,
				prayersFor(style), forcedSpell, 0,
				CandidateMode.ALL_STANDARD, true, onTask,
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
			if (blowpipeDartId != null)
			{
				vector.put("blowpipeDartId", blowpipeDartId);
			}
			if (toaInvocation > 0)
			{
				vector.put("toaInvocationLevel", toaInvocation);
			}
			vector.put("prayers", prayerNames(style));
			if (onTask)
			{
				vector.put("onSlayerTask", true);
			}
			String spellName = result.getSpellName();
			if (spellName != null && !spellName.isEmpty())
			{
				vector.put("spell", spellName);
				if (spellName.contains("Demonbane"))
				{
					vector.put("markOfDarkness", true);
				}
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

	private static String firstToken(String version)
	{
		if (version == null)
		{
			return "";
		}
		int comma = version.indexOf(',');
		return (comma < 0 ? version : version.substring(0, comma)).trim();
	}

	private static GearItem byName(LoadoutData data, String name)
	{
		// "Name#Version" pins an exact variant (Toxic blowpipe#Charged);
		// plain names keep first-standard-match, where Empty can precede
		// Charged in corpus order.
		int hash = name.indexOf('#');
		String plain = hash < 0 ? name : name.substring(0, hash);
		String version = hash < 0 ? null : name.substring(hash + 1);
		return data.getGearItems().stream()
			.filter(g -> g.getName().equalsIgnoreCase(plain)
				&& (version == null || g.getVersion().equalsIgnoreCase(version))
				&& (version != null || g.isStandardGear()))
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
			// Augury only - the wiki engine stacks any prayers it is fed,
			// but the game's prayer groups forbid two magic prayers at once.
			case MAGIC: return List.of("AUGURY");
			default: return List.of("PIETY");
		}
	}
}
