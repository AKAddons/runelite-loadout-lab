package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Tables;
import net.runelite.api.Skill;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stat simulation (ISS-01M5QY13K6YHTYDW1CCYD30CSY, Andrew 2026-10-24: "you
 * should be able to sim as any stat, defaulted to your current ones. lower
 * or higher should work too"; "max cape should automatically unlock the
 * fire/inferno, mage/ rage cape combo versions").
 */
class StatSimTest {
	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static Gear item(String name) {
		return data.getGearItems().stream()
			.filter(g -> g.getLname().equals(name) && g.isStandardGear() && !data.isVariant(g.getId()))
			.findFirst().orElseThrow(() -> new AssertionError("corpus is missing " + name));
	}

	private static RequirementProfile profile(int attack) {
		Map<Skill, Integer> levels = new EnumMap<>(Skill.class);
		levels.put(Skill.ATTACK, attack);
		levels.put(Skill.STRENGTH, 70);
		levels.put(Skill.DEFENCE, 70);
		levels.put(Skill.RANGED, 80);
		levels.put(Skill.MAGIC, 85);
		levels.put(Skill.PRAYER, 60);
		levels.put(Skill.HITPOINTS, 80);
		return new RequirementProfile(levels, Set.of());
	}

	@Test
	@DisplayName("a sim of 99 Attack over 70/70/70/80/85/60/80 keeps the other six; zero means your own")
	void aSimReplacesOnlyTheLevelsItNames() {
		Levels mine = new Levels(70, 70, 70, 80, 85, 60, 80);
		Levels simmed = mine.overridden(new int[]{99, 0, 0, 0, 0, 0, 0});
		assertEquals(99, simmed.getAttack());
		assertEquals(70, simmed.getStrength());
		assertEquals(80, simmed.getHitpoints());
		assertEquals(mine.toString(), mine.overridden(null).toString(), "no sim: your own levels");
		assertEquals(50, mine.overridden(new int[]{0, 0, 0, 0, 0, 0, 50}).getHitpoints(), "lower works too");
	}

	@Test
	@DisplayName("the requirement profile follows the sim: 60 Attack cannot wear the fang, simmed to 99 it can, back to 60 it cannot")
	void gearFollowsTheSim() {
		Gear fang = item("osmumten's fang");
		RequirementProfile sixty = profile(60);
		assertFalse(sixty.canEquip(fang.getRequirements()));
		Levels ninetyNine = Levels.from(sixty).overridden(new int[]{99, 0, 0, 0, 0, 0, 0});
		assertTrue(sixty.withLevels(ninetyNine).canEquip(fang.getRequirements()), "99 Attack admits the fang");
		assertFalse(sixty.withLevels(ninetyNine).withLevels(Levels.from(sixty)).canEquip(fang.getRequirements()), "and 60 drops it again");
		assertEquals(85, sixty.withLevels(ninetyNine).getLevels().get(Skill.MAGIC), "the other skills ride along unchanged");
	}

	@Test
	@DisplayName("max cape sims: with a fire cape owned the max cape and the fire max cape; with nothing, the max cape alone; an imbued Zamorak cape brings its imbued max cape")
	void maxCapeCombos() {
		int fireCape = item("fire cape").getId();
		int imbuedZamorak = item("imbued zamorak cape").getId();
		Set<String> withFire = names(MaxCapeSims.ids(data, id -> id == fireCape));
		assertEquals(Set.of("max cape", "fire max cape"), withFire);
		assertEquals(Set.of("max cape"), names(MaxCapeSims.ids(data, id -> false)));
		assertTrue(names(MaxCapeSims.ids(data, id -> id == imbuedZamorak)).contains("imbued zamorak max cape"));
	}

	private static Set<String> names(Set<Integer> ids) {
		return ids.stream().map(id -> data.getGear(id).getLname()).collect(Collectors.toSet());
	}
}
