package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.Gear;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterGroups;
import com.loadoutlab.data.Monster;
import com.loadoutlab.engine.Style;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trip carries ONE spec weapon, so it is chosen across every style's
 * candidates, not inside the primary style's own list (Andrew 2026-10-24,
 * Royal Titans: "it's recommending dark bow spec on royal titans i feel like
 * a chally spec would be much better"). Measured: the crystal halberd adds
 * 0.195 dps on each meleed titan against the dark bow's 0.034 there and
 * 0.128 on the out-of-reach pair - the chally wins the trip, and the kit
 * kept the dark bow only because the ranged primary never saw the chally.
 */
class TitansTripSpecTest {
	private static LoadoutData data;
	private static List<Monster> titans;

	@BeforeAll
	static void load() {
		data = new DataService().load();
		titans = MonsterGroups.load(data).stream().filter(g -> g.getName().equals("Royal Titans"))
			.findFirst().orElseThrow().getMobs().subList(0, 4);
	}

	private static Gear item(String name) {
		return data.getGearItems().stream()
			.filter(g -> g.getNameLower().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + name));
	}

	/** Andrew's titans bank, a crystal halberd beside the dark bow. */
	private static Map<Integer, Integer> bank() {
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"armadyl helmet", "armadyl chestplate", "armadyl chainskirt", "ava's assembler",
			"amulet of torture", "rada's blessing 4", "noxious halberd", "barrows gloves", "ranger boots", "archers ring (i)",
			"toxic blowpipe", "necklace of anguish", "dark bow", "rune arrow", "amethyst arrow", "crystal halberd",
			"dragon halberd", "dragon dagger", "dragon claws", "bandos godsword", "abyssal whip"}) {
			owned.put(item(name).getId(), 1);
		}
		return owned;
	}

	private static OptimizerService.StyleResult result(OptimizerService.RosterResult roster, int mob, Style style) {
		OptimizerService.StyleResult sr = roster.perMob.get(mob).get(style);
		assertNotNull(sr, titans.get(mob).label() + " " + style);
		return sr;
	}

	@Test
	@DisplayName("Royal Titans at four slots: the meleed pair carry the crystal halberd spec, worth more over the trip than the dark bow the out-of-reach pair could use")
	void theChallyWinsTheTrip() throws Exception {
		OptimizerService service = new OptimizerService(data);
		try {
			OptimizerService.RosterResult roster = ServiceCalls.runRoster(service, titans, bank(), 4, Collections.emptyMap());
			for (int mob : new int[]{0, 1}) {
				OptimizerService.StyleResult melee = result(roster, mob, Style.MELEE);
				assertNotNull(melee.specWeapon, titans.get(mob).label() + " carries a spec");
				assertTrue(melee.specWeapon.getNameLower().contains("crystal halberd"),
					titans.get(mob).label() + ": " + melee.specWeapon.getName() + " adds " + melee.specDpsAdded);
			}
			// One spec weapon per trip: out of reach, nothing melee can spec.
			assertNull(result(roster, 2, Style.RANGED).specWeapon, "out of reach: no melee spec to show");
		} finally {
			service.shutdown();
		}
	}
}
