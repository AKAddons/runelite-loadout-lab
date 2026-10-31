package com.loadoutlab.engine;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.Monster;
import com.loadoutlab.data.SpellStats;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shown set's damage distribution (ISS-01M655THWG41VPTM1ZTRA5B3YE, Andrew
 * 2026-10-29: "it should be the statistical P90"; "Approximate models"): the
 * pmf built beside the expected-hit maths must have that expectation as its
 * mean, for every weapon and every rule, or the kill time would read a set
 * the card is not showing.
 */
class HitDistTest {
	private static LoadoutData data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static Loadout wearing(String... names) {
		Map<GearSlot, Gear> gear = new EnumMap<>(GearSlot.class);
		for (String name : names) {
			for (GearSlot slot : GearSlot.values()) {
				for (Gear g : data.getGearItems(slot)) {
					if (g.nameLower.equals(name) && g.isStandardGear() && !gear.containsKey(g.slot)) gear.put(g.slot, g);
				}
			}
		}
		return new Loadout(gear);
	}

	private static OptimizationRequest request(Monster mob, Style style, String spell) {
		SpellStats s = null;
		if (spell != null) {
			for (SpellStats candidate : data.spells) if (candidate.name.equalsIgnoreCase(spell)) s = candidate;
			assertNotNull(s, spell);
		}
		return new OptimizationRequest(mob, style, PlayerLevels.MAXED,
			PrayerBonuses.bestAvailable(PlayerLevels.MAXED, PrayerUnlocks.ALL),
			s, 0, CandidateMode.ALL_STANDARD, true, false, OwnedItems.EMPTY, RequirementProfile.MAXED, 1);
	}

	private static Monster mob(String name) {
		List<Monster> found = data.searchMonsters(name, 1);
		assertFalse(found.isEmpty(), name);
		return found.get(0);
	}

	/** Checks one set against one request: the pmf sums to one and its mean
	 * is the result's expected hit, or the calculator said the model has no
	 * per-attack form. Returns whether a distribution was checked. */
	private static boolean agrees(OptimizationRequest request, Loadout set, List<String> approximate) {
		DpsCalculator calc = new DpsCalculator();
		DpsResult result = calc.calculate(request, set);
		if (result == null || result.dps <= 0) return false;
		double[] dist = calc.hitDist(request, set);
		String label = set.getWeapon() == null ? "bare" : set.getWeapon().nameLower;
		String where = label + " vs " + request.monster.name + " (" + request.style + ")";
		if (dist == null) {
			assertTrue(calc.distApprox, where + ": no distribution and not flagged approximate");
			approximate.add(where);
			return false;
		}
		double sum = 0;
		for (double p : dist) {
			assertTrue(p >= -1e-12, where + ": negative mass");
			sum += p;
		}
		assertEquals(1, sum, 1e-9, where + ": mass");
		assertEquals(result.expectedHit, HitDist.mean(dist), 1e-9 * Math.max(1, result.expectedHit), where + ": mean");
		return true;
	}

	@Test
	@DisplayName("every standard weapon alone, at every style it can fight, against a spread of monsters: the distribution's mean is the calculator's expected hit")
	void everyWeapon() {
		List<Monster> mobs = List.of(mob("general graardor"), mob("zulrah"), mob("abyssal demon"), mob("blue moon"),
			mob("tormented demon"), mob("kalphite queen"), mob("grey golem"), mob("vorkath"));
		int checked = 0;
		List<String> approximate = new ArrayList<>();
		for (Gear weapon : data.getGearItems(GearSlot.WEAPON)) {
			if (!weapon.isStandardGear()) continue;
			Map<GearSlot, Gear> gear = new EnumMap<>(GearSlot.class);
			gear.put(GearSlot.WEAPON, weapon);
			Loadout set = new Loadout(gear);
			for (Monster mob : mobs) {
				for (Style style : Style.concreteValues()) {
					if (agrees(request(mob, style, null), set, approximate)) checked++;
				}
			}
		}
		assertTrue(checked > 300, "checked " + checked);
		for (String a : approximate) assertTrue(a.contains("Tormented Demon"), "a lone weapon needs the approximate flag only for sure hits on a multi-hit weapon: " + a);
	}

	@Test
	@DisplayName("the special rules agree too: scythe, fang, chained and twin two-hit weapons, Obligator, Verac, keris, dark bow, Tonalztics, seeking arrows, Eclipse, sunfire, twinflame, sanguinesti, frostweaver, Zulrah, Seren, nibblers, Salarin, the warden core, the vampyres; keris sun and the vents are the flagged exceptions")
	void specialRules() {
		List<String> approximate = new ArrayList<>();
		int checked = 0;
		Object[][] cases = {
			{"general graardor", Style.MELEE, null, new String[]{"scythe of vitur"}},
			{"general graardor", Style.MELEE, null, new String[]{"osmumten's fang"}},
			{"ba-ba", Style.MELEE, null, new String[]{"osmumten's fang"}},
			{"blue moon", Style.MELEE, null, new String[]{"dual macuahuitl"}},
			{"blue moon", Style.MELEE, null, new String[]{"sulphur blades"}},
			{"grey golem", Style.MELEE, null, new String[]{"torag's hammers"}},
			{"cerberus", Style.MELEE, null, new String[]{"obligator"}},
			{"general graardor", Style.MELEE, null, new String[]{"verac's flail", "verac's helm", "verac's brassard", "verac's plateskirt"}},
			{"kalphite queen", Style.MELEE, null, new String[]{"keris partisan"}},
			{"ba-ba", Style.MELEE, null, new String[]{"keris partisan of the sun"}},
			{"general graardor", Style.RANGED, null, new String[]{"dark bow", "dragon arrow"}},
			{"general graardor", Style.RANGED, null, new String[]{"tonalztics of ralos"}},
			{"general graardor", Style.RANGED, null, new String[]{"magic shortbow", "seeking arrow"}},
			{"general graardor", Style.RANGED, null, new String[]{"eclipse atlatl", "atlatl dart", "eclipse moon helm", "eclipse moon chestplate", "eclipse moon tassets"}},
			{"general graardor", Style.MAGIC, "Fire Surge", new String[]{"kodai wand"}},
			{"general graardor", Style.MAGIC, "Fire Blast", new String[]{"twinflame staff"}},
			{"general graardor", Style.MAGIC, null, new String[]{"sanguinesti staff"}},
			{"general graardor", Style.MAGIC, "Ice Barrage", new String[]{"blue moon spear", "blue moon helm", "blue moon chestplate", "blue moon tassets"}},
			{"zulrah", Style.RANGED, null, new String[]{"twisted bow", "dragon arrow"}},
			{"fragment of seren", Style.MAGIC, "Fire Surge", new String[]{"kodai wand"}},
			{"tormented demon", Style.MELEE, null, new String[]{"scythe of vitur"}},
			{"jal-nib", Style.MAGIC, "Ice Barrage", new String[]{"kodai wand"}},
			{"salarin the twisted", Style.MAGIC, "Fire Strike", new String[]{"kodai wand"}},
			{"vanstrom klause", Style.MELEE, null, new String[]{"blisterwood flail"}},
			{"vanstrom klause", Style.RANGED, null, new String[]{"twisted bow", "dragon arrow"}},
			{"abyssal sire", Style.RANGED, null, new String[]{"twisted bow", "dragon arrow"}},
		};
		for (Object[] c : cases) {
			OptimizationRequest request = request(mob((String) c[0]), (Style) c[1], (String) c[2]);
			if ("Fire Surge".equals(c[2])) request = request.withSunfireRunes(true);
			if (agrees(request, wearing((String[]) c[3]), approximate)) checked++;
		}
		assertTrue(checked >= 20, "checked " + checked + "; approximate " + approximate);
		for (String a : approximate) {
			assertTrue(a.startsWith("keris partisan of the sun") || a.contains("Abyssal Sire") || a.contains("Respiratory")
				|| a.contains("Tormented Demon"), "unexpected approximate model: " + a);
		}
	}
}
