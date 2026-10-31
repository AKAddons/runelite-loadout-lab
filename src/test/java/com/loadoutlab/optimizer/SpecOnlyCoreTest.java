package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterGroups;
import com.loadoutlab.data.Monster;
import com.loadoutlab.engine.Style;
import com.loadoutlab.engine.MonsterMechanics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Warden's Core is spec-only (ISS-01M5QEFMBSZXAHV84ZXF1YZHWY, Andrew
 * 2026-10-24: "warden's core is basically only taking dragon dagger spec
 * hits if timed right ... we should only be measuring the spec hits of DDS.
 * we dont care about he backup melee weapon for it"): no say in the shared
 * kit, its dagger dump still counted for the trip's spec.
 */
class SpecOnlyCoreTest {
	private static LoadoutData data;
	private static List<Monster> meleeRows;
	private static Monster core;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		List<Monster> toa = MonsterGroups.load(data).stream().filter(g -> g.getName().equals("Tombs of Amascut"))
			.findFirst().orElseThrow().getMobs();
		meleeRows = new ArrayList<>();
		for (Monster mob : toa) {
			String label = mob.label();
			if (label.contains("Ba-Ba") || label.contains("Kephri") || label.contains("Melee only")
				|| label.contains("Final stand") || label.contains("Core")) {
				meleeRows.add(mob.withToaInvocation(300));
			}
		}
		core = meleeRows.stream().filter(m -> m.label().contains("Core")).findFirst().orElseThrow();
	}

	private static Gear item(String name) {
		return data.getGearItems().stream()
			.filter(g -> g.getNameLower().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + name));
	}

	@Test
	@DisplayName("the Core weighs nothing in a kit and its full 4500 for a spec")
	void weights() {
		assertEquals(0.0, MonsterMechanics.kitWeight(core), 1e-9, "no say in the shared kit");
		assertEquals(4500.0, MonsterMechanics.specWeight(core), 1e-9, "the dagger dump still counts");
		Monster baba = meleeRows.get(0);
		assertEquals(MonsterMechanics.specWeight(baba), MonsterMechanics.kitWeight(baba), 1e-9, "every other row: the same weight both ways");
	}

	@Test
	@DisplayName("ToA melee rows at 300 with the halberd and the fang in the bank: Ba-Ba wears the fang (the Core no longer outvotes it), the Core keeps the dragon dagger spec")
	void theFangLeadsTheMeleeKit() throws Exception {
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"neitiznot faceguard", "fire cape", "amulet of torture", "rada's blessing 4",
			"noxious halberd", "osmumten's fang", "avernic defender", "bandos chestplate", "bandos tassets", "ferocious gloves",
			"primordial boots", "berserker ring (i)", "dragon dagger"}) {
			owned.put(item(name).getId(), 1);
		}
		OptimizerService service = new OptimizerService(data);
		try {
			OptimizerService.RosterResult roster = ServiceCalls.runRoster(service, meleeRows, owned, 12, Collections.emptyMap());
			OptimizerService.StyleResult baba = roster.perMob.get(0).get(Style.MELEE);
			assertNotNull(baba);
			assertTrue(baba.owned.get(0).getLoadout().getWeapon().getNameLower().contains("fang"),
				"Ba-Ba wears " + baba.owned.get(0).getLoadout().getWeapon().getName() + " at " + baba.owned.get(0).getDps());
			int coreIndex = meleeRows.indexOf(core);
			OptimizerService.StyleResult coreMelee = roster.perMob.get(coreIndex).get(Style.MELEE);
			assertNotNull(coreMelee.specWeapon, "the Core card carries the dump");
			assertTrue(coreMelee.specWeapon.getNameLower().contains("dragon dagger"), coreMelee.specWeapon.getName());
		} finally {
			service.shutdown();
		}
	}
}
