package com.ll.engine;

import com.ll.data.DataService;
import com.ll.data.Gear;
import com.ll.data.Slot;
import com.ll.data.Tables;
import com.ll.data.Monster;
import com.ll.data.Spell;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quest-boss engine fixes (ISS-01M5RVDTMR5XG16HM6G2JTF7FF, Andrew 2026-10-24:
 * "do the engine bugs"), each against the reference calculator or the wiki.
 */
class QuestBossEngineTest {
	private static Tables data;

	@BeforeAll
	static void load() {
		data = new DataService().load();
	}

	private static Monster mob(String name) {
		return data.searchMonsters(name, 5).stream().filter(m -> m.getName().equalsIgnoreCase(name)).findFirst()
			.orElseThrow(() -> new AssertionError("corpus is missing " + name));
	}

	private static Req request(Monster mob, Style style) {
		return new Req(mob, style, Levels.MAXED, PrayerBonuses.bestAvailable(Levels.MAXED),
			null, 0, CandidateMode.ALL_STANDARD, true, false, Owned.EMPTY, RequirementProfile.MAXED, 1);
	}

	private static List<Dps> best(String name, Style style) {
		return new LoadoutOptimizer().optimize(data, request(mob(name), style));
	}

	private static String spellElement(Dps result) {
		Spell spell = data.getSpells().stream().filter(s -> s.getName().equals(result.getSpellName()))
			.findFirst().orElse(null);
		return spell == null ? "none" : spell.getElement();
	}

	private static String ammo(Dps result) {
		Gear ammo = result.getLoadout().get(Slot.AMMO);
		return ammo == null ? "" : ammo.getLname();
	}

	@Test
	@DisplayName("the Fragment of Seren takes at most 24 a hit in every style")
	void serenCap() {
		for (Style style : Style.concreteValues()) {
			Dps set = best("fragment of seren", style).get(0);
			assertTrue(set.getMaxHit() <= 24, style + " max " + set.getMaxHit());
		}
	}

	@Test
	@DisplayName("Fareed: magic casts a water spell, ranged fires arrows, and Tumeken's shadow cannot hurt him")
	void fareed() {
		assertEquals("water", spellElement(best("fareed", Style.MAGIC).get(0)));
		assertTrue(ammo(best("fareed", Style.RANGED).get(0)).contains("arrow"));
		Map<Slot, Gear> gear = new EnumMap<>(Slot.class);
		gear.put(Slot.WEAPON, data.getGearItems().stream().filter(g -> g.getLname().equals("tumeken's shadow"))
			.findFirst().get());
		assertNull(new Calc().calculate(request(mob("fareed"), Style.MAGIC), new Kit(gear)));
	}

	@Test
	@DisplayName("Kamil: the magic set casts a fire spell")
	void kamil() {
		assertEquals("fire", spellElement(best("kamil", Style.MAGIC).get(0)));
	}

	@Test
	@DisplayName("the Fire Warrior of Lesarkus has no melee or magic set; ranged fires ice arrows")
	void fireWarrior() {
		assertTrue(best("fire warrior of lesarkus", Style.MELEE).isEmpty());
		assertTrue(best("fire warrior of lesarkus", Style.MAGIC).isEmpty());
		assertEquals("ice arrows", ammo(best("fire warrior of lesarkus", Style.RANGED).get(0)));
	}

	@Test
	@DisplayName("zogres and Slash Bash: the ranged set wields the comp ogre bow with brutal arrows")
	void zogres() {
		for (String name : List.of("zogre", "slash bash")) {
			Dps set = best(name, Style.RANGED).get(0);
			assertEquals("comp ogre bow", set.getLoadout().getWeapon().getLname(), name);
			assertTrue(ammo(set).contains("brutal"), name + " ammo " + ammo(set));
		}
	}

	@Test
	@DisplayName("Slagilith: the melee set wields a pickaxe")
	void slagilith() {
		assertEquals("Pickaxe", best("slagilith", Style.MELEE).get(0).getLoadout().getWeapon().getCategory());
	}
}
