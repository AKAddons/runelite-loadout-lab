package com.loadoutlab.optimizer;

import com.loadoutlab.data.DataService;
import com.loadoutlab.data.GearItem;
import com.loadoutlab.data.GearSlot;
import com.loadoutlab.data.LoadoutData;
import com.loadoutlab.data.MonsterGroups;
import com.loadoutlab.data.MonsterStats;
import com.loadoutlab.engine.CombatStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A group strip's worn number is the card's (ISS-01M5QJMTBRNAWX5WT7NRJD8JWC,
 * Andrew 2026-10-24: "melee dps is calcing to 4.44 with nox halberd but
 * ladder is showing 6.06"): the strip is built from what each member's card
 * wears for the style, not from the style's shared kit the cross-style kit
 * may have traded away.
 */
class GroupLadderMatchesCardTest {
	private static GearItem item(LoadoutData data, String name) {
		return data.getGearItems().stream()
			.filter(g -> g.getNameLower().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + name));
	}

	@Test
	@DisplayName("ToA at 300 with twelve slots and a three-style bank: every group strip's base for a member is that member's card dps")
	void stripBaseIsTheCardsNumber() throws Exception {
		LoadoutData data = new DataService().load();
		List<MonsterStats> toa = new ArrayList<>();
		for (MonsterStats mob : MonsterGroups.load(data).stream().filter(g -> g.getName().equals("Tombs of Amascut"))
			.findFirst().orElseThrow().getMobs()) {
			toa.add(mob.withToaInvocation(300));
		}
		Map<Integer, Integer> owned = new HashMap<>();
		for (String name : new String[]{"virtus mask", "imbued zamorak cape", "elemental amulet", "rada's blessing 4",
			"ahrim's robetop", "ahrim's robeskirt", "tormented bracelet", "aranea boots", "seers ring (i)",
			"ancient wyvern shield", "purging staff", "trident of the swamp", "neitiznot faceguard", "fire cape",
			"amulet of torture", "bandos chestplate", "berserker ring (i)", "noxious halberd", "ferocious gloves",
			"crystal helm", "crystal body", "crystal legs", "bow of faerdhinen", "necklace of anguish", "ava's assembler",
			"barrows gloves", "dragon dagger"}) {
			owned.put(item(data, name).getId(), 1);
		}
		for (int rune : new int[]{554, 555, 556, 557, 560, 565, 566, 21880}) {
			owned.put(rune, 10000);
		}
		OptimizerService service = new OptimizerService(data);
		try {
			OptimizerService.RosterResult roster = ServiceCalls.runRoster(service, toa, owned, 12, Collections.emptyMap());
			int checked = 0;
			for (int j = 0; j < toa.size(); j++) {
				for (CombatStyle style : CombatStyle.concreteValues()) {
					OptimizerService.StyleResult sr = roster.perMob.get(j).get(style);
					if (sr == null || sr.owned.isEmpty() || sr.ladder == null) {
						continue;
					}
					double card = sr.owned.get(0).getDps();
					for (Map.Entry<GearSlot, OptimizerService.Ladder> slot : sr.ladder.entrySet()) {
						assertEquals(card, slot.getValue().basePerMob[j], 1e-6,
							toa.get(j).label() + " " + style + " " + slot.getKey() + ": the strip's worn number is the card's");
						checked++;
					}
				}
			}
			assertTrue(checked > 10, "strips were checked: " + checked);
		} finally {
			service.shutdown();
		}
	}
}
